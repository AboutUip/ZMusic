package com.kite.zmusic.listen

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import com.kite.zmusic.R
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.SongRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.ZMusicListenLink
import com.kite.zmusic.playback.PlaybackBridge
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import com.kite.zmusic.workshop.WorkshopApiError
import com.kite.zmusic.workshop.WorkshopAuthStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong
import com.kite.zmusic.i18n.t

/**
 * 一起听：房间状态走 LWW/HLC；进度只按 origin 插值，不传实时秒数。
 */
class ListenTogetherController(
    private val app: Application,
    private val client: ListenTogetherClient,
    private val auth: WorkshopAuthStore,
    private val playback: PlaybackBridge,
    private val songs: SongRepository,
    private val session: SessionRepository,
    private val notices: IslandNoticeCenter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val opMutex = Mutex()
    private val _ui = MutableStateFlow(ListenTogetherUi())
    val ui: StateFlow<ListenTogetherUi> = _ui.asStateFlow()

    @Volatile private var pollJob: Job? = null
    @Volatile private var started = false
    @Volatile private var suppressLocalUntil = 0L
    @Volatile private var appliedHlc = 0L
    @Volatile private var lastPostedHlc = 0L
    @Volatile private var recvElapsed = 0L
    @Volatile private var applyingRemote = false
    private val postMutex = Mutex()
    private val pendingLock = Any()
    private var pendingOp: ListenPostedOp? = null
    @Volatile private var localMemory = ListenLocalMemory()
    @Volatile private var lastDriftAt = 0L
    @Volatile private var lastNotifiedChatId = 0L
    @Volatile private var playerForeground = false
    @Volatile private var chatForeground = false
    @Volatile private var appForeground = true
    @Volatile private var applyGen = 0
    @Volatile private var applyingHlc = 0L
    @Volatile private var pollAfter = 0L
    @Volatile private var leavingRoom = false
    private var matchJob: Job? = null
    private val matchSkipUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var incomingQuietUntil = 0L
    private val localChatSeq = AtomicLong(0L)

    fun start() {
        if (started) return
        started = true
        scope.launch {
            auth.session.collectLatest { sess ->
                _ui.update { it.copy(selfUid = sess?.uid.orEmpty()) }
                val pending = _ui.value.pendingJoinId
                if (sess != null && pending != null && !_ui.value.inRoom) {
                    join(pending, notice = true)
                }
                if (sess == null) {
                    if (_ui.value.inRoom) dropLocal(t("社区登录已失效"))
                    clearMatchState(clearIncoming = true)
                    matchSkipUntil.clear()
                    incomingQuietUntil = 0L
                    runCatching { client.leavePresence() }
                    return@collectLatest
                }
                coroutineScope {
                    launch { presenceLoop() }
                    launch { inboxLoop() }
                    awaitCancellation()
                }
            }
        }
        scope.launch {
            playback.ui.collect { snap ->
                onPlayback(snap)
            }
        }
        scope.launch {
            _ui.map { it.inRoom }.distinctUntilChanged().collect { locked ->
                playback.setPlaybackClockLocked(locked)
            }
        }
        scope.launch {
            _ui.map { ListenTogetherClock.followRemoteAdvance(it.inRoom, it.hosting) }
                .distinctUntilChanged()
                .collect { playback.setListenFollowRemoteAdvance(it) }
        }
    }

    fun rememberPendingJoin(roomId: String) {
        val id = roomId.trim()
        if (!ZMusicListenLink.validId(id)) return
        _ui.update { it.copy(pendingJoinId = id) }
    }

    fun clearPendingJoin() {
        _ui.update { it.copy(pendingJoinId = null) }
    }

    fun setPlayerForeground(held: Boolean) {
        playerForeground = held
    }

    fun setChatForeground(open: Boolean) {
        val wasOpen = chatForeground
        chatForeground = open
        if (open) {
            markChatRead()
            return
        }
        if (!wasOpen) return
        _ui.update { cur ->
            val mine = cur.room?.chat.orEmpty().lastOrNull { listenChatIsSelf(it, cur.selfUid) }
                ?: return@update cur
            val age = if (mine.at > 0L) System.currentTimeMillis() - mine.at else Long.MAX_VALUE
            if (age > 12_000L) return@update cur
            cur.copy(chatToast = mine)
        }
    }

    fun setAppForeground(held: Boolean) {
        appForeground = held
    }

    fun toggleMatch() {
        if (_ui.value.matching) {
            stopMatch(t("已停止匹配"))
        } else {
            startMatch()
        }
    }

    fun startMatch() {
        if (!auth.hasToken()) {
            notices.show(t("需要先登录社区"))
            return
        }
        if (!_ui.value.hosting) {
            notices.show(t("开启一起听后再匹配"))
            return
        }
        _ui.update { it.copy(matching = true, matchPeer = null, rejectedInvite = null) }
        if (matchJob?.isActive == true) return
        matchJob = scope.launch {
            while (isActive && _ui.value.matching) {
                if (_ui.value.matchPeer != null ||
                    _ui.value.outgoingPending ||
                    _ui.value.rejectedInvite != null
                ) {
                    delay(300)
                    continue
                }
                try {
                    val peer = client.match(wait = true, skipUids = activeSkipUids())
                    if (!_ui.value.matching) return@launch
                    if (listenUidSkipped(peer.uid, matchSkipUntil, nowElapsed())) {
                        rememberSkip(peer.uid)
                        runCatching { client.skipMatch(peer.uid) }
                        continue
                    }
                    _ui.update { it.copy(matchPeer = peer) }
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: IOException) {
                    delay(1_200)
                } catch (e: WorkshopApiError.Unauthorized) {
                    notices.show(t("需要先登录社区"))
                    stopMatch(null)
                    return@launch
                } catch (e: WorkshopApiError.Message) {
                    when (e.message) {
                        "nobody" -> continue
                        "busy" -> delay(800)
                        "full" -> {
                            notices.show(t("一起听人数已满"))
                            stopMatch(null)
                            return@launch
                        }
                        "forbidden" -> {
                            notices.show(t("开启一起听后再匹配"))
                            stopMatch(null)
                            return@launch
                        }
                        else -> {
                            fail(e)
                            stopMatch(null)
                            return@launch
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "match", e)
                    delay(1_200)
                }
            }
        }
    }

    fun stopMatch(notice: String?) {
        matchJob?.cancel()
        matchJob = null
        _ui.update {
            it.copy(
                matching = false,
                matchPeer = null,
                outgoingPending = false,
            )
        }
        if (!notice.isNullOrBlank()) notices.show(notice)
    }

    fun dismissMatchPeer() {
        val uid = _ui.value.matchPeer?.uid.orEmpty()
        val gate = listenHostCancel(uid, nowElapsed(), matchSkipUntil)
        matchJob?.cancel()
        matchJob = null
        matchSkipUntil.clear()
        matchSkipUntil.putAll(gate.skipUntil)
        _ui.update {
            it.copy(
                matching = gate.matching,
                matchPeer = null,
            )
        }
        if (uid.isNotBlank()) {
            scope.launch { runCatching { client.skipMatch(uid) } }
        }
    }

    fun skipMatchPeerAndContinue() {
        val uid = _ui.value.matchPeer?.uid.orEmpty()
        val gate = listenHostContinue(uid, nowElapsed(), matchSkipUntil)
        matchSkipUntil.clear()
        matchSkipUntil.putAll(gate.skipUntil)
        _ui.update {
            it.copy(
                matching = true,
                matchPeer = null,
            )
        }
        if (uid.isNotBlank()) {
            scope.launch { runCatching { client.skipMatch(uid) } }
        }
        if (matchJob?.isActive != true) startMatch()
    }

    fun inviteMatchedPeer() {
        val peer = _ui.value.matchPeer ?: return
        scope.launch {
            try {
                client.invite(peer.uid)
                _ui.update { it.copy(matchPeer = null, outgoingPending = true) }
            } catch (e: WorkshopApiError.Message) {
                when (e.message) {
                    "nobody" -> {
                        notices.show(t("对方已离线"))
                        _ui.update { it.copy(matchPeer = null) }
                    }
                    "busy" -> {
                        notices.show(t("对方正忙"))
                        _ui.update { it.copy(matchPeer = null) }
                    }
                    else -> fail(e)
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun continueMatch() {
        _ui.update { it.copy(rejectedInvite = null, matching = true) }
        if (matchJob?.isActive != true) startMatch()
    }

    fun dismissRejected() {
        matchJob?.cancel()
        matchJob = null
        _ui.update {
            it.copy(
                rejectedInvite = null,
                matching = false,
                outgoingPending = false,
                matchPeer = null,
            )
        }
    }

    fun respondInvite(accept: Boolean, today: Boolean) {
        val inv = _ui.value.incomingInvite ?: return
        scope.launch {
            try {
                if (accept) {
                    val snap = client.acceptInvite(inv.id)
                    _ui.update { it.copy(incomingInvite = null) }
                    adopt(snap, seedClock = false)
                    notices.show(t("已加入一起听"))
                } else {
                    client.declineInvite(inv.id, today)
                    val gate = listenGuestReject(inv.from.uid, nowElapsed(), matchSkipUntil)
                    matchSkipUntil.clear()
                    matchSkipUntil.putAll(gate.skipUntil)
                    incomingQuietUntil = maxOf(incomingQuietUntil, gate.quietUntilMs)
                    _ui.update { it.copy(incomingInvite = null) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(incomingInvite = null) }
                fail(e)
            }
        }
    }

    private suspend fun presenceLoop() {
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    client.presence()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                delay(20_000)
            }
        } finally {
            runCatching { client.leavePresence() }
        }
    }

    private suspend fun inboxLoop() {
        var backoffMs = INVITE_BACKOFF_MIN_MS
        while (currentCoroutineContext().isActive) {
            if (_ui.value.incomingInvite != null || _ui.value.rejectedInvite != null) {
                delay(400)
                continue
            }
            try {
                val hidden = applyInviteBox(client.getInvites(wait = true))
                backoffMs = INVITE_BACKOFF_MIN_MS
                if (hidden) delay(800)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                backoffMs = INVITE_BACKOFF_MIN_MS
                continue
            } catch (e: IOException) {
                delay(1_200)
            } catch (e: WorkshopApiError.Unauthorized) {
                return
            } catch (_: WorkshopApiError.RateLimited) {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(INVITE_BACKOFF_MAX_MS)
            } catch (e: Exception) {
                Log.w(TAG, "invites", e)
                delay(1_600)
            }
        }
    }

    private fun applyInviteBox(box: ListenInviteBox): Boolean {
        val incoming = listenIncomingVisible(
            box.incoming,
            nowElapsed(),
            incomingQuietUntil,
            matchSkipUntil,
        )
        var hiddenIncoming = box.incoming != null &&
            box.incoming.status == "pending" &&
            incoming == null
        if (incoming != null) {
            val first = _ui.value.incomingInvite?.id != incoming.id
            _ui.update { it.copy(incomingInvite = incoming) }
            if (first && !appForeground) playChatPing()
            hiddenIncoming = false
        } else if (hiddenIncoming) {
            val hid = box.incoming
            if (hid != null) {
                rememberSkip(hid.from.uid)
                scope.launch { runCatching { client.declineInvite(hid.id, today = false) } }
            }
        }
        val outgoing = box.outgoing ?: return hiddenIncoming
        when {
            listenInviteIsRejected(outgoing.status) -> {
                rememberSkip(outgoing.to.uid)
                _ui.update {
                    it.copy(
                        outgoingPending = false,
                        matching = false,
                        matchPeer = null,
                        rejectedInvite = outgoing,
                    )
                }
            }
            outgoing.status == "accepted" -> {
                _ui.update {
                    it.copy(
                        outgoingPending = false,
                        matching = false,
                        matchPeer = null,
                        rejectedInvite = null,
                    )
                }
                notices.show(t("对方已加入一起听"))
            }
        }
        return hiddenIncoming
    }

    private fun nowElapsed(): Long = SystemClock.elapsedRealtime()

    private fun rememberSkip(uid: String) {
        val id = uid.trim()
        if (id.isEmpty()) return
        matchSkipUntil[id] = listenSkipUntil(nowElapsed())
    }

    private fun activeSkipUids(): List<String> =
        listenActiveSkipUids(matchSkipUntil, nowElapsed())

    private fun clearMatchState(clearIncoming: Boolean) {
        matchJob?.cancel()
        matchJob = null
        _ui.update {
            it.copy(
                matching = false,
                matchPeer = null,
                outgoingPending = false,
                rejectedInvite = null,
                incomingInvite = if (clearIncoming) null else it.incomingInvite,
            )
        }
    }

    fun markChatRead() {
        val maxId = _ui.value.room?.chat?.maxOfOrNull { it.id } ?: return
        lastNotifiedChatId = maxOf(lastNotifiedChatId, maxId)
        _ui.update { cur ->
            cur.copy(
                lastReadChatId = maxOf(cur.lastReadChatId, maxId),
                chatToast = listenChatKeepToastWhileReading(cur.chatToast, cur.selfUid),
            )
        }
    }

    fun clearChatToast(id: Long) {
        _ui.update { cur ->
            if (cur.chatToast?.id == id) cur.copy(chatToast = null) else cur
        }
    }

    fun sendChat(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val room = _ui.value.room ?: return
        val self = auth.current()?.uid.orEmpty().ifBlank { _ui.value.selfUid }.trim()
        val me = room.members.firstOrNull { listenChatUidEquals(it.uid, self) }
        val localId = -localChatSeq.incrementAndGet()
        val local = ListenChatMsg(
            id = localId,
            uid = self.ifBlank { me?.uid.orEmpty() },
            nickname = me?.nickname.orEmpty(),
            avatarUrl = me?.avatarUrl.orEmpty(),
            text = body,
            at = System.currentTimeMillis(),
        )
        _ui.update { cur ->
            val current = cur.room ?: return@update cur
            if (current.id != room.id) return@update cur
            cur.copy(
                room = current.copy(chat = current.chat + local),
                chatToast = local,
            )
        }
        scope.launch {
            try {
                val snap = client.postChat(room.id, body)
                applySnapshot(snap, applyPlayer = false)
            } catch (e: WorkshopApiError.Unauthorized) {
                dropPendingChat(room.id, localId)
                notices.show(t("社区登录已失效"))
            } catch (e: WorkshopApiError.RateLimited) {
                dropPendingChat(room.id, localId)
                notices.show(t("发送太快了"))
            } catch (e: WorkshopApiError.Message) {
                dropPendingChat(room.id, localId)
                when (e.message) {
                    "closed", "missing" -> dropLocal(t("一起听已结束"))
                    "forbidden" -> notices.show(t("无法发送"))
                    else -> notices.show(e.message?.ifBlank { t("发送失败") } ?: t("发送失败"))
                }
            } catch (e: Exception) {
                dropPendingChat(room.id, localId)
                Log.w(TAG, "chat", e)
                notices.show(t("发送失败"))
            }
        }
    }

    private fun dropPendingChat(roomId: String, localId: Long) {
        _ui.update { cur ->
            val room = cur.room ?: return@update cur
            if (room.id != roomId) return@update cur
            cur.copy(
                room = room.copy(chat = room.chat.filter { it.id != localId }),
                chatToast = if (cur.chatToast?.id == localId) null else cur.chatToast,
            )
        }
    }

    fun setDraftSeats(n: Int) {
        _ui.update { it.copy(draftSeats = n.coerceIn(2, 8)) }
    }

    suspend fun enable(): Boolean {
        if (!auth.hasToken()) return false
        val seats = _ui.value.draftSeats
        return opMutex.withLock {
            _ui.update { it.copy(busy = true) }
            try {
                val snap = client.create(seats)
                adopt(snap, seedClock = true)
                notices.show(t("一起听已开启"))
                true
            } catch (e: Exception) {
                fail(e)
                false
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    suspend fun join(roomId: String, notice: Boolean = true): Boolean {
        val id = ZMusicListenLink.parse(roomId) ?: roomId.trim().takeIf { ZMusicListenLink.validId(it) }
            ?: return false
        if (!auth.hasToken()) {
            rememberPendingJoin(id)
            return false
        }
        if (_ui.value.inRoom && _ui.value.room?.id == id) {
            if (notice) notices.show(t("你已经在这间一起听"))
            return true
        }
        return opMutex.withLock {
            _ui.update { it.copy(busy = true) }
            try {
                val snap = client.join(id)
                adopt(snap, seedClock = false)
                _ui.update { it.copy(pendingJoinId = null) }
                if (notice) notices.show(t("已加入一起听"))
                true
            } catch (e: Exception) {
                fail(e)
                false
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    suspend fun stop(hostEnd: Boolean = _ui.value.hosting) {
        val room = _ui.value.room ?: return
        freezeLocalPlayback()
        opMutex.withLock {
            _ui.update { it.copy(busy = true) }
            try {
                if (hostEnd || room.hostUid == _ui.value.selfUid) {
                    runCatching { client.close(room.id) }
                } else {
                    runCatching { client.leave(room.id) }
                }
            } finally {
                clearMatchState(clearIncoming = false)
                dropLocal(if (hostEnd) t("一起听已结束") else t("已离开一起听"))
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun adopt(snap: ListenRoomSnapshot, seedClock: Boolean) {
        applySnapshot(snap, applyPlayer = !seedClock)
        if (seedClock) {
            val ui = playback.ui.value
            val track = ui.currentTrack
            if (track != null && track.id > 0L) {
                enqueueOp(
                    ListenPostedOp.track(
                        trackId = track.id,
                        title = track.name,
                        artists = track.artists,
                        coverUrl = track.coverUrl.orEmpty(),
                        durationMs = track.durationMs.coerceAtLeast(0L),
                        originMs = ui.positionMs.coerceAtLeast(0L),
                        playing = ui.playWhenReady,
                    ),
                )
            }
        }
        restartPoll()
    }

    private fun applySnapshot(snap: ListenRoomSnapshot, applyPlayer: Boolean) {
        if (leavingRoom) return
        if (snap.id.isBlank()) return
        recvElapsed = SystemClock.elapsedRealtime()
        val prevId = _ui.value.room?.id
        var ping = false
        _ui.update { cur ->
            val self = auth.current()?.uid.orEmpty().ifBlank { cur.selfUid }
            val merged = mergeListenRoomChat(
                previous = cur.room?.chat.orEmpty(),
                incoming = snap.chat,
                incomingIncluded = snap.chatIncluded,
                selfUid = self,
            )
            var lastRead = cur.lastReadChatId
            var toast = cur.chatToast
            if (prevId != snap.id) {
                lastRead = merged.maxOfOrNull { it.id } ?: 0L
                lastNotifiedChatId = lastRead
                toast = null
            } else if (chatForeground) {
                lastRead = maxOf(lastRead, merged.maxOfOrNull { it.id } ?: 0L)
                lastNotifiedChatId = maxOf(lastNotifiedChatId, lastRead)
                toast = listenChatKeepToastWhileReading(
                    retargetListenChatToast(toast, merged),
                    self,
                )
            } else {
                val newest = merged.lastOrNull { it.id > lastNotifiedChatId }
                if (newest != null) {
                    lastNotifiedChatId = newest.id
                    toast = newest
                    ping = !playerForeground && !listenChatIsSelf(newest, self)
                } else {
                    toast = retargetListenChatToast(toast, merged)
                }
            }
            cur.copy(
                room = snap.copy(chat = merged),
                selfUid = self,
                lastReadChatId = lastRead,
                chatToast = toast,
            )
        }
        if (ping) playChatPing()
        if (snap.closed) {
            dropLocal(t("一起听已结束"))
            return
        }
        if (!applyPlayer) {
            appliedHlc = maxOf(appliedHlc, snap.clock.hlc)
            return
        }
        val self = auth.current()?.uid.orEmpty().ifBlank { _ui.value.selfUid }
        val local = playback.ui.value
        val mismatch = ListenTogetherClock.playerNeedsClock(
            snap.clock,
            local.currentTrack?.id ?: 0L,
            local.playWhenReady,
        )
        val isMine = ListenTogetherClock.isOwnClock(snap.clock.actor, self)
        if (!ListenTogetherClock.takeRemoteClock(
                remoteHlc = snap.clock.hlc,
                appliedHlc = appliedHlc,
                isMine = isMine,
                mismatch = mismatch,
                applyingRemote = applyingRemote,
            )
        ) {
            appliedHlc = maxOf(appliedHlc, snap.clock.hlc)
            return
        }
        appliedHlc = maxOf(appliedHlc, snap.clock.hlc)
        applyClockToPlayer(snap)
    }

    private fun applyClockToPlayer(snap: ListenRoomSnapshot) {
        if (leavingRoom) return
        val clock = snap.clock
        if (clock.trackId <= 0L) return
        if (applyingRemote && applyingHlc == clock.hlc) return
        applyingHlc = clock.hlc
        val localId = playback.ui.value.currentTrack?.id ?: 0L
        if (localId == clock.trackId) {
            applyGen++
            applyingRemote = true
            suppressLocalUntil = SystemClock.elapsedRealtime() + 1_200L
            val nowElapsed = SystemClock.elapsedRealtime()
            val pos = ListenTogetherClock.positionMs(clock, snap.serverNow, recvElapsed, nowElapsed)
            playback.seekTo(pos)
            playback.setPlayWhenReady(clock.playing)
            rememberAppliedClock(clock.trackId, clock.playing, pos, nowElapsed)
            applyingRemote = false
            suppressLocalUntil = SystemClock.elapsedRealtime() + 400L
            return
        }
        val gen = ++applyGen
        applyingRemote = true
        suppressLocalUntil = SystemClock.elapsedRealtime() + 15_000L
        val nowElapsed = SystemClock.elapsedRealtime()
        val pos = ListenTogetherClock.positionMs(clock, snap.serverNow, recvElapsed, nowElapsed)
        scope.launch {
            try {
                val track = resolveTrack(clock)
                if (gen != applyGen || leavingRoom) return@launch
                playback.playListenTrack(track, pos, clock.playing)
                awaitPlayerAligned(clock.trackId, clock.playing, gen)
            } catch (t: Throwable) {
                Log.w(TAG, "apply clock", t)
            } finally {
                if (gen == applyGen) {
                    val ui = playback.ui.value
                    rememberAppliedClock(
                        ui.currentTrack?.id ?: 0L,
                        ui.playWhenReady,
                        ui.positionMs,
                        SystemClock.elapsedRealtime(),
                    )
                    applyingRemote = false
                    suppressLocalUntil = SystemClock.elapsedRealtime() + 500L
                }
            }
        }
    }

    private suspend fun awaitPlayerAligned(trackId: Long, playing: Boolean, gen: Int) {
        val deadline = SystemClock.elapsedRealtime() + 12_000L
        while (SystemClock.elapsedRealtime() < deadline && gen == applyGen) {
            val ui = playback.ui.value
            val id = ui.currentTrack?.id ?: 0L
            if (id == trackId && !ui.loadPending && ui.playWhenReady == playing) {
                return
            }
            delay(50)
        }
    }

    private suspend fun resolveTrack(clock: ListenPlaybackClock): TrackRow {
        val cookie = session.session.value?.cookie.orEmpty()
        val fetched = withContext(Dispatchers.IO) {
            runCatching { songs.trackById(clock.trackId, cookie) }.getOrNull()
        }
        if (fetched != null) return fetched
        return TrackRow(
            id = clock.trackId,
            name = clock.title.ifBlank { t("一起听") },
            artists = clock.artists,
            album = null,
            durationMs = clock.durationMs,
            coverUrl = clock.coverUrl.ifBlank { null },
        )
    }

    private fun restartPoll() {
        pollJob?.cancel()
        val id = _ui.value.room?.id ?: return
        pollJob = scope.launch {
            var after = maxOf(_ui.value.room?.rev ?: 0L, pollAfter)
            var failStreak = 0
            while (isActive && _ui.value.room?.id == id) {
                try {
                    val snap = client.get(id, after, wait = true)
                    if (_ui.value.room?.id != id) return@launch
                    failStreak = 0
                    after = maxOf(after, snap.rev, pollAfter)
                    applySnapshot(snap, applyPlayer = true)
                    maybeCorrectDrift(snap)
                    if (snap.closed) return@launch
                } catch (e: SocketTimeoutException) {
                    failStreak = 0
                    continue
                } catch (e: IOException) {
                    failStreak = (failStreak + 1).coerceAtMost(4)
                    delay(1_000L shl (failStreak - 1))
                } catch (e: WorkshopApiError.Unauthorized) {
                    dropLocal(t("社区登录已失效"))
                    return@launch
                } catch (e: WorkshopApiError.Missing) {
                    dropLocal(t("一起听已结束"))
                    return@launch
                } catch (e: WorkshopApiError.Message) {
                    if (e.message == "closed" || e.message == "forbidden") {
                        dropLocal(t("一起听已结束"))
                        return@launch
                    }
                    failStreak = (failStreak + 1).coerceAtMost(4)
                    delay(1_000L shl (failStreak - 1))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "poll", e)
                    failStreak = (failStreak + 1).coerceAtMost(4)
                    delay(1_000L shl (failStreak - 1))
                }
            }
        }
    }

    private fun maybeCorrectDrift(snap: ListenRoomSnapshot) {
        if (leavingRoom) return
        val clock = snap.clock
        if (clock.trackId <= 0L || applyingRemote) return
        val self = auth.current()?.uid.orEmpty().ifBlank { _ui.value.selfUid }
        val isMine = ListenTogetherClock.isOwnClock(clock.actor, self)
        if (isMine || clock.hlc < appliedHlc) return
        val ui = playback.ui.value
        if (ListenTogetherClock.playerNeedsClock(clock, ui.currentTrack?.id ?: 0L, ui.playWhenReady)) {
            applyClockToPlayer(snap)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastDriftAt < ListenTogetherClock.DRIFT_CHECK_MS) return
        lastDriftAt = now
        val expect = ListenTogetherClock.positionMs(
            clock,
            snap.serverNow,
            recvElapsed,
            now,
        )
        if (kotlin.math.abs(ui.positionMs - expect) <= ListenTogetherClock.DRIFT_MS) return
        applyingRemote = true
        suppressLocalUntil = now + 800L
        playback.seekTo(expect)
        val mem = localMemory
        localMemory = mem.copy(lastPosMs = expect, lastPosAt = now)
        applyingRemote = false
    }

    private fun onPlayback(snap: PlaybackUiState) {
        val now = SystemClock.elapsedRealtime()
        val local = snap.toListenLocalSnap()
        if (leavingRoom) {
            localMemory = localMemory.copy(
                lastTrackId = local.trackId,
                lastPlayWhenReady = local.playWhenReady,
                lastPosMs = local.positionMs,
                lastPosAt = now,
                lastHasQueue = local.hasQueue,
            )
            return
        }
        val room = _ui.value.room
        val (next, effect) = decideLocalPlayback(
            inRoom = room != null && !room.closed,
            applyingRemote = applyingRemote,
            nowElapsed = now,
            suppressUntil = suppressLocalUntil,
            hosting = _ui.value.hosting,
            memory = localMemory,
            snap = local,
        )
        localMemory = next
        when (effect) {
            ListenLocalEffect.Hold -> Unit
            ListenLocalEffect.EndHostRoom -> scope.launch { stop(hostEnd = true) }
            is ListenLocalEffect.Post -> enqueueOp(effect.op)
        }
    }

    private fun rememberAppliedClock(trackId: Long, playing: Boolean, pos: Long, at: Long) {
        val mem = localMemory
        localMemory = mem.copy(
            lastTrackId = trackId,
            lastPlayWhenReady = playing,
            lastPosMs = pos,
            lastPosAt = at,
            carryOverMs = -1L,
        )
    }

    private fun enqueueOp(op: ListenPostedOp) {
        if (leavingRoom) return
        synchronized(pendingLock) {
            pendingOp = mergeListenOp(pendingOp, op)
        }
        scope.launch { drainOps() }
    }

    private suspend fun drainOps() {
        postMutex.withLock {
            while (!leavingRoom) {
                val next = synchronized(pendingLock) {
                    val o = pendingOp
                    pendingOp = null
                    o
                } ?: break
                postOpUnlocked(next)
            }
        }
    }

    private suspend fun postOpUnlocked(op: ListenPostedOp) {
        if (leavingRoom) return
        val id = _ui.value.room?.id ?: return
        val body = op.toJson()
        var last: Exception? = null
        repeat(3) { attempt ->
            if (leavingRoom || _ui.value.room?.id != id) return
            try {
                val snap = client.postOp(id, body)
                lastPostedHlc = snap.clock.hlc
                appliedHlc = maxOf(appliedHlc, snap.clock.hlc)
                pollAfter = maxOf(pollAfter, snap.rev)
                recvElapsed = SystemClock.elapsedRealtime()
                _ui.update { it.copy(room = snap) }
                return
            } catch (e: WorkshopApiError.Message) {
                if (e.message == "closed" || e.message == "missing") {
                    dropLocal(t("一起听已结束"))
                    return
                }
                last = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
            delay(400L * (attempt + 1))
        }
        if (last != null) Log.w(TAG, "op", last)
    }

    private fun dropLocal(message: String?) {
        freezeLocalPlayback()
        pollJob?.cancel()
        pollJob = null
        appliedHlc = 0L
        lastPostedHlc = 0L
        recvElapsed = 0L
        localMemory = ListenLocalMemory()
        synchronized(pendingLock) { pendingOp = null }
        applyingRemote = false
        suppressLocalUntil = 0L
        lastDriftAt = 0L
        lastNotifiedChatId = 0L
        applyGen++
        applyingHlc = 0L
        pollAfter = 0L
        val had = _ui.value.inRoom
        clearMatchState(clearIncoming = false)
        _ui.update { it.copy(room = null, lastReadChatId = 0L, chatToast = null) }
        leavingRoom = false
        if (had && !message.isNullOrBlank()) {
            notices.show(message)
        }
    }

    /**
     * 结束一起听：立刻停掉远端对齐，本机只暂停，不 seek、不换歌。
     * 须在关房网络请求之前调用，避免最后一次 poll/pause 再把进度或播放状态推出去。
     */
    private fun freezeLocalPlayback() {
        leavingRoom = true
        applyGen++
        applyingRemote = false
        synchronized(pendingLock) { pendingOp = null }
        pollJob?.cancel()
        pollJob = null
        suppressLocalUntil = SystemClock.elapsedRealtime() + 8_000L
        playback.setPlayWhenReady(false)
    }

    private fun fail(e: Exception) {
        val msg = when (e) {
            is WorkshopApiError.Unauthorized -> t("需要先登录社区")
            is WorkshopApiError.RateLimited -> t("操作太快，请稍后再试")
            is WorkshopApiError.Missing -> t("一起听不存在或已结束")
            is WorkshopApiError.Message -> when (e.message) {
                "full" -> t("一起听人数已满")
                "closed" -> t("一起听已结束")
                "forbidden" -> t("无法加入该一起听")
                "bad_request" -> t("邀请已失效")
                "nobody" -> t("暂时没有可匹配的用户")
                "busy" -> t("对方正忙")
                else -> t("一起听暂时不可用")
            }
            else -> t("一起听暂时不可用")
        }
        notices.show(msg)
        Log.w(TAG, msg, e)
    }

    private fun playChatPing() {
        scope.launch(Dispatchers.IO) {
            var player: MediaPlayer? = null
            try {
                val mp = MediaPlayer()
                player = mp
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                app.resources.openRawResourceFd(R.raw.listen_chat_ping).use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                mp.setOnCompletionListener { it.release() }
                mp.setOnErrorListener { p, _, _ ->
                    p.release()
                    true
                }
                mp.prepare()
                mp.start()
            } catch (t: Throwable) {
                Log.w(TAG, "ping", t)
                runCatching { player?.release() }
            }
        }
    }

    companion object {
        private const val TAG = "ZMusicListen"
        private const val INVITE_BACKOFF_MIN_MS = 8_000L
        private const val INVITE_BACKOFF_MAX_MS = 60_000L
    }
}

private fun PlaybackUiState.toListenLocalSnap(): ListenLocalSnap {
    val track = currentTrack
    return ListenLocalSnap(
        trackId = track?.id ?: 0L,
        title = track?.name.orEmpty(),
        artists = track?.artists.orEmpty(),
        coverUrl = track?.coverUrl.orEmpty(),
        durationMs = (track?.durationMs ?: durationMs).coerceAtLeast(0L),
        positionMs = positionMs,
        playWhenReady = playWhenReady,
        loadPending = loadPending,
        hasQueue = hasQueue,
    )
}

private fun ListenPostedOp.toJson(): JSONObject {
    val o = JSONObject().put("kind", kind)
    if (trackId > 0L) o.put("track_id", trackId)
    when (kind) {
        "track" -> {
            o.put("title", title)
            o.put("artists", artists)
            o.put("cover_url", coverUrl)
            o.put("duration_ms", durationMs.coerceAtLeast(0L))
            o.put("origin_ms", originMs.coerceAtLeast(0L))
            o.put("playing", playing)
        }
        "play", "pause" -> o.put("origin_ms", originMs.coerceAtLeast(0L))
        "seek" -> {
            o.put("origin_ms", originMs.coerceAtLeast(0L))
            o.put("playing", playing)
        }
    }
    return o
}
