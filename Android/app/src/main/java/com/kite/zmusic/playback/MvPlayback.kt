package com.kite.zmusic.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kite.zmusic.data.MvArtist
import com.kite.zmusic.data.MvDetail
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmMvParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class MvUiState(
    val mvId: Long = 0L,
    val active: Boolean = false,
    val title: String = "",
    val coverUrl: String? = null,
    val artists: List<MvArtist> = emptyList(),
    val playCount: Long = 0L,
    val publishTime: String? = null,
    val desc: String? = null,
    val related: List<RecommendMvCard> = emptyList(),
    val relatedHasMore: Boolean = false,
    val relatedLoading: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val boosting: Boolean = false,
    val hint: String? = null,
    val error: String? = null,
    val loading: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** MV 独立播放模式，与歌曲队列互不影响。 */
    val playbackMode: PlaybackMode = PlaybackMode.ORDER,
) {
    val artistLine: String
        get() = artists.joinToString(" / ") { it.name }.ifBlank { "MV" }
}

/**
 * MV 独立 ExoPlayer（与歌曲队列分开），产品上按「带画面的音乐」：
 * 退出播放页不暂停，通知/后台走现有 MediaSession。
 * 播放模式（列表循环 / 单曲循环 / 随机）单独持久化，不复用歌曲模式。
 */
@OptIn(UnstableApi::class)
class MvPlayback(
    context: Context,
    private val sessionRepository: SessionRepository,
    private val playbackBridge: PlaybackBridge,
    private val userClient: NcmUserClient,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var playbackMode: PlaybackMode = intToMode(prefs.getInt(KEY_MODE, 0))

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://music.163.com/",
                "Origin" to "https://music.163.com",
            ),
        )

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(appContext).setDataSourceFactory(httpFactory))
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setWakeMode(C.WAKE_MODE_NETWORK)
            pauseAtEndOfMediaItems = false
            playWhenReady = false
        }

    /** 交给 MediaSession：上一首 / 下一首切相关 MV，而不是 Exo 单条目。 */
    val sessionPlayer: Player = MvSessionPlayer(player)

    private val _ui = MutableStateFlow(MvUiState(playbackMode = playbackMode))
    val ui: StateFlow<MvUiState> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var tickerJob: Job? = null
    private var hintJob: Job? = null
    private var relatedJob: Job? = null
    private val relatedMutex = Mutex()
    private var baseSpeed = 1f
    private var relatedStage = 0
    private var artistCursor = 0
    private var artistOffset = 0
    private var catalogOffset = 0
    private var exclusiveOffset = 0
    private val relatedIds = mutableSetOf<Long>()
    private val playHistory = ArrayDeque<RecommendMvCard>()
    private val sessionQueue = ArrayList<RecommendMvCard>()
    private var sessionIndex = 0
    private var seedMvId = 0L
    private var pendingAdvance = false
    private var advancing = false
    /** 换源后一段时间内，旧片 ENDED / MediaSession 下一首都不能当真。 */
    private var itemSetAt = 0L
    private var userSwitchAt = 0L
    private var playGen = 0
    private var handledEndGen = -1

    init {
        applyRepeatMode()
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    publishPosition()
                    val mine = mediaMatchesCurrent()
                    _ui.update {
                        it.copy(
                            buffering = playbackState == Player.STATE_BUFFERING ||
                                (playbackState == Player.STATE_READY && player.isLoading),
                        )
                    }
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!mine) return
                            advancing = false
                            if (_ui.value.loading) {
                                _ui.update {
                                    it.copy(
                                        loading = false,
                                        error = null,
                                        playWhenReady = player.playWhenReady,
                                    )
                                }
                            }
                        }
                        Player.STATE_ENDED -> maybeAutoAdvance()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _ui.update { it.copy(playWhenReady = player.playWhenReady) }
                    if (isPlaying) startTicker() else publishPosition()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    _ui.update { it.copy(playWhenReady = playWhenReady) }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    _ui.update {
                        it.copy(
                            videoWidth = videoSize.width,
                            videoHeight = videoSize.height,
                        )
                    }
                }
            },
        )
    }

    fun play(
        mvId: Long,
        title: String,
        coverUrl: String?,
        artist: String?,
        fromSkip: Boolean = false,
        fromUser: Boolean = true,
    ) {
        if (mvId <= 0L) return
        val current = _ui.value
        if (current.active && current.mvId == mvId) {
            if (current.loading || advancing) return
            if (player.mediaItemCount > 0) {
                if (!player.playWhenReady) togglePlayPause()
                return
            }
        }
        val now = SystemClock.elapsedRealtime()
        itemSetAt = now
        if (fromUser) userSwitchAt = now
        playGen += 1
        advancing = true
        if (player.mediaItemCount > 0) {
            player.pause()
        }
        val skip = fromSkip || (current.active && sessionQueue.any { it.id == mvId })
        playbackBridge.ensureService()
        playbackBridge.pauseForForeignPlayback()
        playbackBridge.bindSessionPlayer(sessionPlayer)
        loadJob?.cancel()
        pendingAdvance = false
        if (!skip) {
            relatedJob?.cancel()
            playHistory.clear()
            resetRelatedPaging()
            seedMvId = mvId
            sessionQueue.clear()
            sessionQueue.add(
                RecommendMvCard(mvId, title.ifBlank { "MV" }, coverUrl, artist, 0L),
            )
            sessionIndex = 0
            baseSpeed = 1f
            player.setPlaybackSpeed(1f)
        } else {
            val i = sessionQueue.indexOfFirst { it.id == mvId }
            if (i >= 0) {
                sessionIndex = i
            } else {
                sessionQueue.add(
                    RecommendMvCard(mvId, title.ifBlank { "MV" }, coverUrl, artist, 0L),
                )
                sessionIndex = sessionQueue.lastIndex
            }
        }
        val keep = if (skip) current else null
        _ui.value = MvUiState(
            mvId = mvId,
            active = true,
            title = title.ifBlank { "MV" },
            coverUrl = coverUrl,
            artists = artist?.takeIf { it.isNotBlank() }?.let { listOf(MvArtist(0L, it)) }.orEmpty(),
            loading = true,
            playWhenReady = true,
            related = keep?.related.orEmpty(),
            relatedHasMore = keep?.relatedHasMore ?: true,
            relatedLoading = keep?.relatedLoading ?: false,
            playbackMode = playbackMode,
            speed = if (skip) baseSpeed else 1f,
            videoWidth = keep?.videoWidth ?: 0,
            videoHeight = keep?.videoHeight ?: 0,
        )
        if (skip) player.setPlaybackSpeed(baseSpeed)
        val loadingId = mvId
        loadJob = scope.launch {
            loadAndStart(loadingId, title, coverUrl, artist, fetchRelated = !skip)
        }
    }

    fun togglePlayPause() {
        if (!_ui.value.active) return
        if (player.playWhenReady) player.pause() else player.play()
        _ui.update { it.copy(playWhenReady = player.playWhenReady) }
    }

    fun seekTo(ms: Long) {
        if (!_ui.value.active) return
        val dur = player.duration.takeIf { it > 0 } ?: _ui.value.durationMs
        val target = ms.coerceIn(0L, dur.coerceAtLeast(0L))
        player.seekTo(target)
        _ui.update { it.copy(positionMs = target) }
    }

    fun skipBy(deltaMs: Long) {
        if (!_ui.value.active) return
        val dur = player.duration.takeIf { it > 0 } ?: _ui.value.durationMs
        val from = player.currentPosition.coerceAtLeast(0L)
        val target = (from + deltaMs).coerceIn(0L, if (dur > 0L) dur else Long.MAX_VALUE)
        player.seekTo(target)
        _ui.update { it.copy(positionMs = target) }
        flashHint(if (deltaMs >= 0L) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s")
    }

    fun skipNext() {
        if (!_ui.value.active || advancing || inReplaceGrace()) return
        val next = pickNext()
        if (next == null) {
            if (_ui.value.relatedHasMore || _ui.value.relatedLoading) {
                pendingAdvance = true
                loadMoreRelated()
            } else {
                player.pause()
                _ui.update { it.copy(playWhenReady = false) }
            }
            return
        }
        advancing = true
        if (playbackMode == PlaybackMode.SHUFFLE) {
            playHistory.addLast(currentCard())
            while (playHistory.size > MAX_HISTORY) playHistory.removeFirst()
        }
        play(next.id, next.name, next.coverUrl, next.artist, fromSkip = true, fromUser = false)
    }

    fun skipPrevious() {
        if (!_ui.value.active || advancing || inReplaceGrace()) return
        if (playbackMode == PlaybackMode.SHUFFLE) {
            val prev = playHistory.removeLastOrNull()
            if (prev != null) {
                advancing = true
                play(prev.id, prev.name, prev.coverUrl, prev.artist, fromSkip = true, fromUser = false)
            } else {
                seekTo(0L)
            }
            return
        }
        val i = sessionIndex - 1
        if (i in sessionQueue.indices) {
            advancing = true
            val card = sessionQueue[i]
            play(card.id, card.name, card.coverUrl, card.artist, fromSkip = true, fromUser = false)
        } else {
            seekTo(0L)
        }
    }

    fun cyclePlaybackMode() {
        playbackMode = when (playbackMode) {
            PlaybackMode.ORDER -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.ORDER
        }
        prefs.edit().putInt(KEY_MODE, modeToInt(playbackMode)).apply()
        applyRepeatMode()
        _ui.update { it.copy(playbackMode = playbackMode) }
        flashHint(
            when (playbackMode) {
                PlaybackMode.ORDER -> "列表循环"
                PlaybackMode.REPEAT_ONE -> "单曲循环"
                PlaybackMode.SHUFFLE -> "随机播放"
            },
        )
    }

    fun setSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 3f)
        baseSpeed = s
        if (!_ui.value.boosting) player.setPlaybackSpeed(s)
        _ui.update { it.copy(speed = s) }
    }

    fun setBoosting(on: Boolean) {
        if (!_ui.value.active) return
        if (on) {
            player.setPlaybackSpeed(BOOST_SPEED)
            _ui.update { it.copy(boosting = true, hint = "${BOOST_SPEED}x") }
        } else {
            player.setPlaybackSpeed(baseSpeed)
            _ui.update { it.copy(boosting = false, hint = null, speed = baseSpeed) }
        }
    }

    /** 退出播放页不暂停；只有切回歌曲或登出才停。 */
    fun stop() {
        if (!_ui.value.active && player.mediaItemCount == 0) return
        loadJob?.cancel()
        relatedJob?.cancel()
        tickerJob?.cancel()
        hintJob?.cancel()
        pendingAdvance = false
        advancing = false
        itemSetAt = SystemClock.elapsedRealtime()
        playHistory.clear()
        sessionQueue.clear()
        sessionIndex = 0
        seedMvId = 0L
        player.pause()
        player.stop()
        player.clearMediaItems()
        _ui.value = MvUiState(playbackMode = playbackMode)
        playbackBridge.restoreMusicSessionPlayer()
        playbackBridge.resumeFromForeignPlayback()
    }

    val exo: ExoPlayer get() = player

    private fun mediaMatchesCurrent(): Boolean {
        val id = _ui.value.mvId
        return id > 0L && player.currentMediaItem?.mediaId == "mv-$id"
    }

    private fun inReplaceGrace(): Boolean {
        val t = SystemClock.elapsedRealtime()
        return _ui.value.loading ||
            advancing ||
            t - itemSetAt < REPLACE_GRACE_MS ||
            t - userSwitchAt < REPLACE_GRACE_MS
    }

    private fun isRealEnd(): Boolean {
        if (!_ui.value.active || inReplaceGrace()) return false
        if (playbackMode == PlaybackMode.REPEAT_ONE) return false
        if (!mediaMatchesCurrent()) return false
        val dur = player.duration.takeIf { it > 0 } ?: _ui.value.durationMs.takeIf { it > 0 } ?: return false
        val pos = player.currentPosition.coerceAtLeast(0L)
        if (pos < END_MIN_PLAYED_MS) return false
        return pos >= dur - END_NEAR_MS || pos >= dur * 97 / 100
    }

    private fun maybeAutoAdvance() {
        if (!isRealEnd()) return
        if (handledEndGen == playGen) return
        handledEndGen = playGen
        onEnded()
    }

    private fun onEnded() {
        if (!_ui.value.active || inReplaceGrace()) return
        if (!mediaMatchesCurrent()) return
        when (playbackMode) {
            PlaybackMode.REPEAT_ONE -> {
                player.seekTo(0L)
                player.play()
            }
            PlaybackMode.ORDER, PlaybackMode.SHUFFLE -> skipNext()
        }
    }

    private fun pickNext(): RecommendMvCard? {
        if (sessionQueue.size <= 1) return null
        val currentId = _ui.value.mvId
        return when (playbackMode) {
            PlaybackMode.ORDER -> {
                val i = sessionIndex + 1
                if (i in sessionQueue.indices) sessionQueue[i]
                else sessionQueue.firstOrNull { it.id != currentId }
            }
            PlaybackMode.REPEAT_ONE -> sessionQueue.getOrNull(sessionIndex + 1)
            PlaybackMode.SHUFFLE -> {
                val recent = buildSet {
                    add(currentId)
                    playHistory.takeLast(8).forEach { add(it.id) }
                }
                val others = sessionQueue.filter { it.id != currentId }
                val fresh = others.filter { it.id !in recent }
                (fresh.ifEmpty { others }).randomOrNull()
            }
        }
    }

    private fun currentCard(): RecommendMvCard {
        val u = _ui.value
        return RecommendMvCard(
            id = u.mvId,
            name = u.title.ifBlank { "MV" },
            coverUrl = u.coverUrl,
            artist = u.artistLine,
            playCount = u.playCount,
        )
    }

    private fun applyRepeatMode() {
        player.repeatMode = when (playbackMode) {
            PlaybackMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.pauseAtEndOfMediaItems = false
    }

    private fun canSeekNext(): Boolean {
        if (!_ui.value.active || inReplaceGrace()) return false
        return sessionQueue.size > 1 ||
            _ui.value.relatedHasMore ||
            _ui.value.relatedLoading
    }

    private fun canSeekPrevious(): Boolean =
        _ui.value.active && (_ui.value.mvId > 0L)

    private suspend fun loadAndStart(
        mvId: Long,
        title: String,
        coverUrl: String?,
        artist: String?,
        fetchRelated: Boolean,
    ) {
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        try {
            val detailJson = withContext(Dispatchers.IO) { userClient.mvDetail(mvId, cookie) }
            val detail = NcmMvParse.detail(detailJson, mvId)
            if (detail != null) {
                _ui.update {
                    it.copy(
                        title = detail.name.ifBlank { title.ifBlank { it.title } },
                        coverUrl = detail.coverUrl ?: coverUrl ?: it.coverUrl,
                        artists = detail.artists.ifEmpty {
                            artist?.takeIf { n -> n.isNotBlank() }?.let { n -> listOf(MvArtist(0L, n)) }
                                .orEmpty()
                        },
                        playCount = detail.playCount,
                        publishTime = detail.publishTime,
                        desc = detail.desc,
                        durationMs = detail.durationMs.takeIf { d -> d > 0L } ?: it.durationMs,
                    )
                }
            }
            val url = resolveUrl(mvId, cookie, detail)
            if (url.isNullOrBlank()) {
                advancing = false
                _ui.update {
                    it.copy(
                        loading = false,
                        error = NcmJson.userFacingMessage(detailJson, "暂时无法播放该 MV"),
                    )
                }
                return
            }
            if (_ui.value.mvId != mvId) return
            coroutineContext.ensureActive()
            val meta = _ui.value
            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaId("mv-$mvId")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(meta.title)
                        .setArtist(meta.artistLine)
                        .setArtworkUri(meta.coverUrl?.let { Uri.parse(it) })
                        .build(),
                )
                .build()
            applyRepeatMode()
            itemSetAt = SystemClock.elapsedRealtime()
            player.setMediaItem(item, true)
            player.prepare()
            player.play()
            startTicker()
            if (fetchRelated) {
                relatedJob?.cancel()
                relatedJob = scope.launch { pullRelated(seedMvId.takeIf { it > 0L } ?: mvId) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_ui.value.mvId != mvId) return
            advancing = false
            _ui.update {
                it.copy(
                    loading = false,
                    error = NcmJson.userFacingThrowable(e, "暂时无法播放该 MV"),
                )
            }
        }
    }

    private suspend fun resolveUrl(mvId: Long, cookie: String, detail: MvDetail?): String? {
        val preferred = buildList {
            val fromDetail = detail?.brs.orEmpty().sortedDescending()
            if (720 in fromDetail) add(720)
            addAll(fromDetail.filter { it != 720 })
            listOf(720, 480, 1080, 240).forEach { if (it !in this) add(it) }
        }.distinct()
        for (r in preferred) {
            val json = withContext(Dispatchers.IO) { userClient.mvUrl(mvId, cookie, r) }
            val url = NcmMvParse.playUrl(json)
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    fun loadMoreRelated() {
        val id = seedMvId.takeIf { it > 0L } ?: _ui.value.mvId
        if (id <= 0L || !_ui.value.relatedHasMore || _ui.value.relatedLoading) return
        if (relatedJob?.isActive == true) return
        relatedJob = scope.launch { pullRelated(id) }
    }

    private fun resetRelatedPaging() {
        relatedStage = 0
        artistCursor = 0
        artistOffset = 0
        catalogOffset = 0
        exclusiveOffset = 0
        relatedIds.clear()
    }

    private suspend fun pullRelated(mvId: Long) {
        if (!relatedMutex.tryLock()) return
        var shouldAdvance = false
        var pauseAtEnd = false
        try {
            if (mvId != seedMvId) return
            _ui.update { it.copy(relatedLoading = true) }
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            var guard = 0
            while (guard++ < 8 && mvId == seedMvId) {
                val chunk = nextRelatedChunk(mvId, cookie)
                if (chunk.isNotEmpty()) {
                    appendSession(chunk)
                    _ui.update {
                        it.copy(
                            related = it.related + chunk,
                            relatedLoading = false,
                            relatedHasMore = relatedStage < RELATED_DONE,
                        )
                    }
                    if (pendingAdvance) {
                        pendingAdvance = false
                        shouldAdvance = true
                    }
                    return
                }
                if (relatedStage >= RELATED_DONE) break
            }
            if (mvId == seedMvId) {
                _ui.update { it.copy(relatedLoading = false, relatedHasMore = false) }
                if (pendingAdvance) {
                    pendingAdvance = false
                    if (sessionQueue.size > 1) shouldAdvance = true
                    else pauseAtEnd = true
                }
            }
        } catch (e: CancellationException) {
            pendingAdvance = false
            if (mvId == seedMvId) {
                _ui.update { it.copy(relatedLoading = false) }
            }
            throw e
        } catch (_: Exception) {
            if (mvId == seedMvId) {
                _ui.update { it.copy(relatedLoading = false) }
                pendingAdvance = false
            }
        } finally {
            relatedMutex.unlock()
        }
        if (!coroutineContext.isActive) return
        if (shouldAdvance) skipNext()
        else if (pauseAtEnd) {
            player.pause()
            _ui.update { it.copy(playWhenReady = false) }
        }
    }

    private suspend fun nextRelatedChunk(mvId: Long, cookie: String): List<RecommendMvCard> {
        when (relatedStage) {
            0 -> {
                val json = withContext(Dispatchers.IO) {
                    runCatching { userClient.simiMv(mvId, cookie) }.getOrNull()
                }
                relatedStage = 1
                return acceptRelated(json?.let { NcmMvParse.similar(it) }.orEmpty(), mvId)
            }
            1 -> {
                val artists = _ui.value.artists.filter { it.id > 0L }
                while (artistCursor < artists.size) {
                    val artistId = artists[artistCursor].id
                    val json = withContext(Dispatchers.IO) {
                        runCatching {
                            userClient.artistMv(artistId, cookie, RELATED_PAGE, artistOffset)
                        }.getOrNull()
                    }
                    val raw = json?.let { NcmMvParse.similar(it) }.orEmpty()
                    val got = acceptRelated(raw, mvId)
                    val more = json?.let { NcmMvParse.hasMore(it, raw.size, RELATED_PAGE) } ?: false
                    if (!more || raw.isEmpty()) {
                        artistCursor += 1
                        artistOffset = 0
                    } else {
                        artistOffset += RELATED_PAGE
                    }
                    if (got.isNotEmpty()) return got
                }
                relatedStage = 2
                return emptyList()
            }
            2 -> {
                val json = withContext(Dispatchers.IO) {
                    runCatching {
                        userClient.mvAll(cookie, RELATED_PAGE, catalogOffset)
                    }.getOrNull()
                }
                val raw = json?.let { NcmMvParse.similar(it) }.orEmpty()
                val got = acceptRelated(raw, mvId)
                catalogOffset += RELATED_PAGE
                val more = json?.let { NcmMvParse.hasMore(it, raw.size, RELATED_PAGE) } ?: false
                if (!more || raw.isEmpty()) relatedStage = 3
                return got
            }
            else -> {
                if (relatedStage >= RELATED_DONE) return emptyList()
                val json = withContext(Dispatchers.IO) {
                    runCatching {
                        userClient.mvExclusive(cookie, RELATED_PAGE, exclusiveOffset)
                    }.getOrNull()
                }
                val raw = json?.let { NcmMvParse.similar(it) }.orEmpty()
                val got = acceptRelated(raw, mvId)
                exclusiveOffset += RELATED_PAGE
                val more = json?.let { NcmMvParse.hasMore(it, raw.size, RELATED_PAGE) } ?: false
                if (!more || raw.isEmpty()) relatedStage = RELATED_DONE
                return got
            }
        }
    }

    private fun acceptRelated(list: List<RecommendMvCard>, mvId: Long): List<RecommendMvCard> =
        list.filter { it.id != mvId && relatedIds.add(it.id) }

    private fun appendSession(cards: List<RecommendMvCard>) {
        cards.forEach { c ->
            if (sessionQueue.none { it.id == c.id }) sessionQueue.add(c)
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                publishPosition()
                maybeAutoAdvance()
                delay(200)
            }
        }
    }

    private fun publishPosition() {
        val dur = player.duration.takeIf { it > 0 } ?: _ui.value.durationMs
        _ui.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = dur.coerceAtLeast(0L),
                buffering = player.playbackState == Player.STATE_BUFFERING || player.isLoading,
                playWhenReady = player.playWhenReady,
            )
        }
    }

    private fun flashHint(text: String) {
        hintJob?.cancel()
        _ui.update { it.copy(hint = text) }
        hintJob = scope.launch {
            delay(700)
            _ui.update { if (it.hint == text) it.copy(hint = null) else it }
        }
    }

    private inner class MvSessionPlayer(player: ExoPlayer) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands()
                .buildUpon()
                .add(COMMAND_SEEK_TO_NEXT)
                .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(COMMAND_SEEK_TO_PREVIOUS)
                .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

        override fun isCommandAvailable(command: @Player.Command Int): Boolean = when (command) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> canSeekNext()
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> canSeekPrevious()
            else -> super.isCommandAvailable(command)
        }

        override fun seekToNext() = skipNext()
        override fun seekToNextMediaItem() = skipNext()
        override fun seekToPrevious() = skipPrevious()
        override fun seekToPreviousMediaItem() = skipPrevious()
        override fun hasNextMediaItem(): Boolean = canSeekNext()
        override fun hasPreviousMediaItem(): Boolean = canSeekPrevious()
    }

    companion object {
        private const val REPLACE_GRACE_MS = 2_000L
        private const val END_NEAR_MS = 1_200L
        private const val END_MIN_PLAYED_MS = 800L
        private const val BOOST_SPEED = 2f
        private const val RELATED_PAGE = 30
        private const val RELATED_DONE = 4
        private const val MAX_HISTORY = 64
        private const val PREFS = "zmusic_mv_mode"
        private const val KEY_MODE = "mode"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"

        private fun modeToInt(mode: PlaybackMode): Int = when (mode) {
            PlaybackMode.ORDER -> 0
            PlaybackMode.REPEAT_ONE -> 1
            PlaybackMode.SHUFFLE -> 2
        }

        private fun intToMode(v: Int): PlaybackMode = when (v) {
            1 -> PlaybackMode.REPEAT_ONE
            2 -> PlaybackMode.SHUFFLE
            else -> PlaybackMode.ORDER
        }
    }
}
