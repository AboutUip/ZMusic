package com.kite.zmusic.listen

import android.os.SystemClock
import android.util.Log
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.SongRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.ZMusicListenLink
import com.kite.zmusic.playback.PlaybackBridge
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import com.kite.zmusic.workshop.WorkshopApiError
import com.kite.zmusic.workshop.WorkshopAuthStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * 一起听：房间状态走 LWW/HLC；进度只按 origin 插值，不传实时秒数。
 */
class ListenTogetherController(
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
    @Volatile private var lastPosMs = 0L
    @Volatile private var lastPosAt = 0L
    @Volatile private var lastTrackId = 0L
    @Volatile private var lastPlayWhenReady = false
    @Volatile private var lastHasQueue = false
    @Volatile private var applyingRemote = false
    @Volatile private var lastDriftAt = 0L

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
                if (sess == null && _ui.value.inRoom) {
                    dropLocal("社区登录已失效")
                }
            }
        }
        scope.launch {
            playback.ui.collect { snap ->
                onPlayback(snap)
            }
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
                notices.show("一起听已开启")
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
        return opMutex.withLock {
            _ui.update { it.copy(busy = true) }
            try {
                val snap = client.join(id)
                adopt(snap, seedClock = false)
                _ui.update { it.copy(pendingJoinId = null) }
                if (notice) notices.show("已加入一起听")
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
        opMutex.withLock {
            _ui.update { it.copy(busy = true) }
            try {
                if (hostEnd || room.hostUid == _ui.value.selfUid) {
                    runCatching { client.close(room.id) }
                } else {
                    runCatching { client.leave(room.id) }
                }
            } finally {
                dropLocal(if (hostEnd) "一起听已结束" else "已离开一起听")
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
                postOp(
                    JSONObject()
                        .put("kind", "track")
                        .put("track_id", track.id)
                        .put("title", track.name)
                        .put("artists", track.artists)
                        .put("cover_url", track.coverUrl.orEmpty())
                        .put("duration_ms", track.durationMs.coerceAtLeast(0L))
                        .put("origin_ms", ui.positionMs.coerceAtLeast(0L))
                        .put("playing", ui.playWhenReady),
                )
            }
        }
        restartPoll()
    }

    private fun applySnapshot(snap: ListenRoomSnapshot, applyPlayer: Boolean) {
        recvElapsed = SystemClock.elapsedRealtime()
        _ui.update {
            it.copy(
                room = snap,
                selfUid = auth.current()?.uid.orEmpty(),
            )
        }
        if (snap.closed) {
            dropLocal("一起听已结束")
            return
        }
        if (!applyPlayer) {
            appliedHlc = maxOf(appliedHlc, snap.clock.hlc)
            return
        }
        val self = auth.current()?.uid.orEmpty()
        val mine = snap.clock.actor == self && snap.clock.hlc <= lastPostedHlc
        if (mine || !ListenTogetherClock.shouldApply(snap.clock.hlc, appliedHlc)) {
            appliedHlc = maxOf(appliedHlc, snap.clock.hlc)
            return
        }
        appliedHlc = snap.clock.hlc
        applyClockToPlayer(snap)
    }

    private fun applyClockToPlayer(snap: ListenRoomSnapshot) {
        val clock = snap.clock
        if (clock.trackId <= 0L) return
        applyingRemote = true
        suppressLocalUntil = SystemClock.elapsedRealtime() + 1_200L
        val nowElapsed = SystemClock.elapsedRealtime()
        val pos = ListenTogetherClock.positionMs(clock, snap.serverNow, recvElapsed, nowElapsed)
        scope.launch {
            try {
                val current = playback.ui.value.currentTrack
                if (current?.id != clock.trackId) {
                    val track = resolveTrack(clock)
                    playback.playInsertAfterCurrent(track)
                    delay(80)
                }
                playback.seekTo(pos)
                val wantPlay = clock.playing
                val ready = playback.ui.value.playWhenReady
                if (wantPlay != ready) {
                    playback.togglePlayPause()
                }
            } finally {
                lastTrackId = playback.ui.value.currentTrack?.id ?: clock.trackId
                lastPlayWhenReady = playback.ui.value.playWhenReady
                lastPosMs = playback.ui.value.positionMs
                lastPosAt = SystemClock.elapsedRealtime()
                applyingRemote = false
            }
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
            name = clock.title.ifBlank { "一起听" },
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
            var after = _ui.value.room?.rev ?: 0L
            while (isActive && _ui.value.room?.id == id) {
                try {
                    val snap = client.get(id, after, wait = true)
                    if (_ui.value.room?.id != id) return@launch
                    after = maxOf(after, snap.rev)
                    applySnapshot(snap, applyPlayer = true)
                    maybeCorrectDrift(snap)
                    if (snap.closed) return@launch
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: IOException) {
                    delay(1_200)
                } catch (e: WorkshopApiError.Unauthorized) {
                    dropLocal("社区登录已失效")
                    return@launch
                } catch (e: WorkshopApiError.Missing) {
                    dropLocal("一起听已结束")
                    return@launch
                } catch (e: WorkshopApiError.Message) {
                    if (e.message == "closed" || e.message == "forbidden") {
                        dropLocal("一起听已结束")
                        return@launch
                    }
                    delay(1_200)
                } catch (e: Exception) {
                    Log.w(TAG, "poll", e)
                    delay(1_600)
                }
            }
        }
    }

    private fun maybeCorrectDrift(snap: ListenRoomSnapshot) {
        if (_ui.value.hosting) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDriftAt < ListenTogetherClock.DRIFT_CHECK_MS) return
        lastDriftAt = now
        val clock = snap.clock
        if (clock.trackId <= 0L) return
        val ui = playback.ui.value
        if (ui.currentTrack?.id != clock.trackId) return
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
        lastPosMs = expect
        lastPosAt = now
        applyingRemote = false
    }

    private fun onPlayback(snap: PlaybackUiState) {
        val room = _ui.value.room
        val now = SystemClock.elapsedRealtime()
        val trackId = snap.currentTrack?.id ?: 0L
        if (room != null && _ui.value.hosting && !snap.hasQueue && lastHasQueue) {
            lastHasQueue = false
            scope.launch { stop(hostEnd = true) }
            return
        }
        lastHasQueue = snap.hasQueue
        if (room == null || room.closed || applyingRemote || now < suppressLocalUntil) {
            lastTrackId = trackId
            lastPlayWhenReady = snap.playWhenReady
            lastPosMs = snap.positionMs
            lastPosAt = now
            return
        }
        val track = snap.currentTrack
        if (track != null && track.id > 0L && track.id != lastTrackId) {
            lastTrackId = track.id
            lastPlayWhenReady = snap.playWhenReady
            lastPosMs = snap.positionMs
            lastPosAt = now
            scope.launch {
                postOp(
                    JSONObject()
                        .put("kind", "track")
                        .put("track_id", track.id)
                        .put("title", track.name)
                        .put("artists", track.artists)
                        .put("cover_url", track.coverUrl.orEmpty())
                        .put("duration_ms", track.durationMs.coerceAtLeast(0L))
                        .put("origin_ms", snap.positionMs.coerceAtLeast(0L))
                        .put("playing", snap.playWhenReady),
                )
            }
            return
        }
        if (snap.playWhenReady != lastPlayWhenReady) {
            val kind = if (snap.playWhenReady) "play" else "pause"
            lastPlayWhenReady = snap.playWhenReady
            lastPosMs = snap.positionMs
            lastPosAt = now
            scope.launch {
                postOp(
                    JSONObject()
                        .put("kind", kind)
                        .put("origin_ms", snap.positionMs.coerceAtLeast(0L)),
                )
            }
            return
        }
        val elapsed = if (lastPlayWhenReady) now - lastPosAt else 0L
        if (ListenTogetherClock.isSeekJump(lastPosMs, elapsed, snap.positionMs)) {
            lastPosMs = snap.positionMs
            lastPosAt = now
            scope.launch {
                postOp(
                    JSONObject()
                        .put("kind", "seek")
                        .put("origin_ms", snap.positionMs.coerceAtLeast(0L))
                        .put("playing", snap.playWhenReady),
                )
            }
            return
        }
        lastPosMs = snap.positionMs
        lastPosAt = now
    }

    private suspend fun postOp(body: JSONObject) {
        val id = _ui.value.room?.id ?: return
        try {
            val snap = client.postOp(id, body)
            lastPostedHlc = snap.clock.hlc
            appliedHlc = maxOf(appliedHlc, snap.clock.hlc)
            recvElapsed = SystemClock.elapsedRealtime()
            _ui.update { it.copy(room = snap) }
        } catch (e: WorkshopApiError.Message) {
            if (e.message == "closed" || e.message == "missing") {
                dropLocal("一起听已结束")
            } else {
                Log.w(TAG, "op ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "op", e)
        }
    }

    private fun dropLocal(message: String?) {
        pollJob?.cancel()
        pollJob = null
        appliedHlc = 0L
        lastPostedHlc = 0L
        val had = _ui.value.inRoom
        _ui.update { it.copy(room = null) }
        if (had && !message.isNullOrBlank()) {
            notices.show(message)
        }
    }

    private fun fail(e: Exception) {
        val msg = when (e) {
            is WorkshopApiError.Unauthorized -> "需要先登录社区"
            is WorkshopApiError.RateLimited -> "操作太快，请稍后再试"
            is WorkshopApiError.Missing -> "一起听不存在或已结束"
            is WorkshopApiError.Message -> when (e.message) {
                "full" -> "一起听人数已满"
                "closed" -> "一起听已结束"
                "forbidden" -> "无法加入该一起听"
                "bad_request" -> "邀请已失效"
                else -> "一起听暂时不可用"
            }
            else -> "一起听暂时不可用"
        }
        notices.show(msg)
        Log.w(TAG, msg, e)
    }

    companion object {
        private const val TAG = "ZMusicListen"
    }
}
