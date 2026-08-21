package com.kite.zmusic.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.kite.zmusic.R
import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.AudioQualityStore
import com.kite.zmusic.data.DownloadAccelHit
import com.kite.zmusic.data.DownloadAccelIndex
import com.kite.zmusic.data.DownloadAccelStore
import com.kite.zmusic.data.RealtimeCacheController
import com.kite.zmusic.data.RealtimeCacheMode
import com.kite.zmusic.data.RealtimeCacheStore
import com.kite.zmusic.data.PersistentPlaybackStore
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.LyricRepository
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlayUrlResolver
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.isNetworkOnline
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.notice.showIslandNotice
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Service 内播放协调：队列、NCM URL 按需解析、歌词、模式。
 * 不负责通知 / FGS（由 MediaSessionService 独占）。
 *
 * 本阶段保持单类：起播语义（空队列发布、URL 解析、FM 续播）没有单元测试护栏，
 * 不要按“文件太大”再拆实现。边界：
 * - 对外：`playQueue` / `playIndex` / skip / mode / FM
 * - 对内：按需 `PlayUrlResolver`、短 TTL 预取、歌词加载
 * - 不：Compose、repository 组装（由 Application / Service 注入）
 */
@UnstableApi
class PlaylistCoordinator(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val stateStore: PlaybackStateStore,
    private val lyricRepository: LyricRepository,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val audioQualityStore: AudioQualityStore,
    private val downloadAccelStore: DownloadAccelStore,
    private val downloadAccelIndex: DownloadAccelIndex,
    private val realtimeCacheStore: RealtimeCacheStore,
    private val realtimeCache: RealtimeCacheController,
    private val persistentPlaybackStore: PersistentPlaybackStore,
    private val userClient: NcmUserClient,
    private val audioOutputController: AudioOutputController,
    private val onClearAndStopService: (() -> Unit)? = null,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            Log.w(TAG, "uncaught", e)
        },
    )

    private val _spectrum = MutableStateFlow(AudioSpectrumBands.ZERO)
    val spectrum: StateFlow<AudioSpectrumBands> = _spectrum.asStateFlow()

    private var lastSpectrumPublishMs = 0L
    private val spectrumProcessor = SpectrumTapAudioProcessor { bands ->
        // 音频 hop ~6ms；UI 只需约一帧一次。量化后相等则跳过，避免掀整棵 Compose 树。
        val now = android.os.SystemClock.elapsedRealtime()
        val quantized = bands.quantized()
        if (quantized == _spectrum.value) return@SpectrumTapAudioProcessor
        if (now - lastSpectrumPublishMs < 16L && quantized != AudioSpectrumBands.ZERO) {
            return@SpectrumTapAudioProcessor
        }
        lastSpectrumPublishMs = now
        _spectrum.value = quantized
    }

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean,
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(true)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessors(arrayOf(spectrumProcessor))
                .build()
        }
    }.apply {
        setEnableDecoderFallback(true)
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    }

    private val musicAudioAttrs = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).build().apply {
        setAudioAttributes(musicAudioAttrs, true)
        setWakeMode(C.WAKE_MODE_NETWORK)
        pauseAtEndOfMediaItems = false
    }

    val player: Player = QueueAwarePlayer(exoPlayer)

    private val persistentFocus = PersistentPlaybackFocus(
        context = context,
        store = persistentPlaybackStore,
        scope = scope,
        player = exoPlayer,
        musicAudioAttrs = musicAudioAttrs,
    )

    private val _ui = MutableStateFlow(PlaybackUiState())
    val ui: StateFlow<PlaybackUiState> = _ui.asStateFlow()

    private var playbackMode: PlaybackMode = PlaybackMode.ORDER
    private var fmActive: Boolean = false
    private var intelligenceActive: Boolean = false
    private var fmHydrateJob: Job? = null
    private var pendingFmAdvance: Boolean = false
    private var fmKickExtra: Boolean = false
    private val radioActive: Boolean get() = fmActive || intelligenceActive

    private var loadJob: Job? = null
    private var lyricJob: Job? = null
    private var prefetchJob: Job? = null
    private var tickerJob: Job? = null
    private var noticeJob: Job? = null
    private var errorRetryJob: Job? = null

    /**
     * 音源短缓存：当前曲 + 邻曲常驻；切走后仍可作为「上一首」命中（不 remove-on-play）。
     * 仅保留 keep 集合，过期与超限一并淘汰。
     */
    private val urlCache = mutableMapOf<Long, CachedUrl>()
    /** 预检失败 / 播放失败：短时拉黑，切歌直接跳过 */
    private val unplayableUntil = mutableMapOf<Long, Long>()
    private var retryCount = 0
    private var retryIndex = -1
    /** 随机模式预选下一首，保证预览封面与真实切歌一致 */
    private var preparedShuffleNext: Int? = null
    /** 随机模式真实播放历史（队列下标），用于上一首回退 */
    private val shuffleHistory = ArrayDeque<Int>()
    /**
     * UI（如竖屏评论）打开时挂起曲末自动切歌：先停在曲末，关闭后再进下一首。
     */
    private var holdAutoAdvance = false
    private var pendingAdvanceAfterHold = false
    /** 定时停止与 STATE_ENDED 竞态：已暂停则勿再自动切歌。 */
    private var suppressAutoAdvanceOnce = false

    /** 由 [PlaybackBridge] 在 attach 时注入；倒计时本身不挂在本类上。 */
    @Volatile
    var sleepTimer: SleepTimer? = null
    private var appliedQuality: AudioQuality = audioQualityStore.current()
    /** 下载加速命中提示：同一首歌连播不重复弹。 */
    private var accelNoticeTrackId = 0L
    private var sampleTrackId = 0L
    private var sampleQuality = ""
    private var sampleListenedMs = 0L
    private var sampleDurationMs = 0L
    private var sampleStartedAt = 0L
    private var sampleAccumAt = 0L
    private var sampleLastPos = -1L

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val d = exoPlayer.duration
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    _ui.update {
                        it.copy(
                            buffering = false,
                            loadPending = false,
                            positionMs = pos,
                            durationMs = if (d > 0 && d != C.TIME_UNSET) d else it.durationMs,
                        )
                    }
                    persistSnapshot()
                }
                Player.STATE_BUFFERING -> _ui.update { it.copy(buffering = true) }
                Player.STATE_ENDED -> {
                    // 手动切歌已进入 loadPending 时忽略，避免曲末再 onEnded 连跳下一首
                    if (!_ui.value.loadPending) onEnded()
                }
                Player.STATE_IDLE -> _ui.update { it.copy(buffering = false) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            persistentFocus.onIsPlayingChanged(isPlaying)
            _ui.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) persistSnapshot()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (persistentFocus.consumePlayWhenReadyChange(playWhenReady, reason)) return
            _ui.update { it.copy(playWhenReady = playWhenReady) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "player error ${error.errorCodeName}", error)
            val idx = _ui.value.index
            val trackId = _ui.value.currentTrack?.id
            // 失效坏链，进入加载态后台重试；不展示 Source error 文案
            if (trackId != null) urlCache.remove(trackId)
            _ui.update {
                it.copy(
                    error = null,
                    buffering = true,
                    loadPending = true,
                    isPlaying = false,
                    playWhenReady = false,
                    transportWakeToken = it.transportWakeToken + 1,
                )
            }
            errorRetryJob?.cancel()
            errorRetryJob = scope.launch {
                delay(280)
                if (idx == _ui.value.index && _ui.value.hasQueue) {
                    loadAndPlayIndex(idx, isRetry = true)
                }
            }
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        audioOutputController.attachPlayer(exoPlayer)
        // 从快照恢复模式与进度，不联网冲队列
        stateStore.load()?.let { snap ->
            playbackMode = snap.playbackMode
            fmActive = snap.fmActive
            intelligenceActive = snap.intelligenceActive
            fmKickExtra = radioActive
            if (radioActive && playbackMode == PlaybackMode.SHUFFLE) {
                playbackMode = PlaybackMode.ORDER
            }
            val mem = snap.currentTrack?.id?.let { lyricRepository.peekPack(it) }
            _ui.value = if (mem != null && mem.original.isNotEmpty()) {
                snap.copy(
                    lyricLines = mem.original,
                    translatedLyricLines = mem.translated,
                    wordLyricLines = mem.wordOriginal,
                    translatedWordLyricLines = mem.wordTranslated,
                    playbackMode = playbackMode,
                    fmActive = fmActive,
                    intelligenceActive = intelligenceActive,
                )
            } else {
                snap.copy(
                    playbackMode = playbackMode,
                    fmActive = fmActive,
                    intelligenceActive = intelligenceActive,
                )
            }
            if (radioActive) ensureRadioLookahead()
            snap.currentTrack?.id?.let { songId ->
                scope.launch {
                    val cookie = sessionRepository.session.value?.cookie.orEmpty()
                    val pack = lyricRepository.loadBestEffort(songId, cookie)
                    if (_ui.value.currentTrack?.id == songId && pack.original.isNotEmpty()) {
                        _ui.update { it.withLyricPack(pack) }
                    }
                }
            }
            applyRepeatMode()
            refreshPeeksAndPrefetch()
        }
        applyRepeatMode()
        startTicker()
        persistentFocus.start()
        scope.launch {
            audioQualityStore.quality.drop(1).collect { next ->
                if (next == appliedQuality) return@collect
                appliedQuality = next
                val cur = _ui.value.currentTrack
                if (cur != null && realtimeCache.playUri(cur.id, next) != null) {
                    reloadCurrentForQuality()
                    return@collect
                }
                if (cur != null && downloadAccelIndex.audioUri(cur.id) != null) return@collect
                reloadCurrentForQuality()
            }
        }
        scope.launch {
            downloadAccelStore.enabled.drop(1).collect { on ->
                if (!on) reloadCurrentForQuality()
            }
        }
        scope.launch {
            realtimeCacheStore.state.drop(1).collect { prefs ->
                if (!prefs.enabled) {
                    discardSample()
                    reloadCurrentForQuality()
                } else {
                    switchCurrentToRealtimeCacheIfNeeded()
                }
            }
        }
        scope.launch {
            downloadAccelIndex.hits.collect { map ->
                switchCurrentToLocalIfNeeded(map)
            }
        }
        scope.launch {
            realtimeCache.indexRevision.drop(1).collect {
                switchCurrentToRealtimeCacheIfNeeded()
            }
        }
    }

    fun playQueue(
        tracks: List<TrackRow>,
        startIndex: Int,
        sourcePlaylistId: Long? = null,
        sourcePlaylistTitle: String? = null,
        fmSession: Boolean = false,
        intelligenceSession: Boolean = false,
    ) {
        if (sleepTimer?.onLeavingTrack() == true) return
        if (tracks.isEmpty()) return
        val idx = startIndex.coerceIn(0, tracks.lastIndex)
        val radio = fmSession || intelligenceSession
        if (!radio) {
            fmHydrateJob?.cancel()
            fmHydrateJob = null
            pendingFmAdvance = false
        }
        fmActive = fmSession
        intelligenceActive = intelligenceSession
        fmKickExtra = radio
        if (radio && playbackMode == PlaybackMode.SHUFFLE) {
            playbackMode = PlaybackMode.ORDER
            preparedShuffleNext = null
            shuffleHistory.clear()
        }
        cancelLoads()
        urlCache.clear()
        unplayableUntil.clear()
        preparedShuffleNext = null
        shuffleHistory.clear()
        pendingAdvanceAfterHold = false
        retryCount = 0
        retryIndex = -1
        _ui.update {
            it.copy(
                queue = tracks,
                index = idx,
                error = null,
                loadPending = true,
                hasQueue = true,
                lyricLines = emptyList(),
                translatedLyricLines = emptyList(),
                wordLyricLines = emptyList(),
                translatedWordLyricLines = emptyList(),
                sourcePlaylistId = sourcePlaylistId,
                sourcePlaylistTitle = sourcePlaylistTitle,
                playbackMode = playbackMode,
                fmActive = fmSession,
                intelligenceActive = intelligenceSession,
            )
        }
        persistSnapshot()
        flushSample()
        loadAndPlayIndex(idx)
        if (radio) ensureRadioLookahead()
    }

    fun startPersonalFm(onStarted: () -> Unit = {}) {
        fmHydrateJob?.cancel()
        scope.launch {
            val tracks = fetchPersonalFmBatch()
            if (tracks.isEmpty()) {
                context.showIslandNotice("暂时没有漫游歌曲")
                return@launch
            }
            playQueue(tracks, 0, null, "私人漫游", fmSession = true)
            context.showIslandNotice("已开启私人漫游")
            onStarted()
        }
    }

    fun startIntelligence(
        songId: Long,
        playlistId: Long,
        playlistTitle: String? = null,
        startSongId: Long = songId,
        onStarted: () -> Unit = {},
    ) {
        if (songId <= 0L || playlistId <= 0L) {
            context.showIslandNotice("先在我喜欢的音乐里收藏几首歌")
            return
        }
        fmHydrateJob?.cancel()
        scope.launch {
            val tracks = fetchIntelligenceBatch(songId, playlistId, startSongId)
            if (tracks.isEmpty()) {
                context.showIslandNotice("暂时没有心动歌曲")
                return@launch
            }
            val start = tracks.indexOfFirst { it.id == startSongId }.takeIf { it >= 0 } ?: 0
            playQueue(
                tracks,
                start,
                playlistId,
                "心动模式",
                intelligenceSession = true,
            )
            context.showIslandNotice("已开启心动模式")
            onStarted()
        }
    }

    /** 功能页 / 播放页更多：固定用「我喜欢的音乐」当种子歌单。 */
    fun startIntelligenceFromContext(onStarted: () -> Unit = {}) {
        scope.launch {
            val liked = resolveLikedForIntelligence()
            if (liked == null || liked.playlistId <= 0L) {
                context.showIslandNotice("先在我喜欢的音乐里收藏几首歌")
                return@launch
            }
            val playing = _ui.value.currentTrack
            val seed = playing?.id?.takeIf { id ->
                liked.likedIds.contains(id) || liked.tracks.any { it.id == id }
            } ?: liked.tracks.firstOrNull()?.id
                ?: liked.displayIds.firstOrNull()
                ?: liked.allLikedIds.firstOrNull()
                ?: 0L
            if (seed <= 0L) {
                context.showIslandNotice("先在我喜欢的音乐里收藏几首歌")
                return@launch
            }
            startIntelligence(
                songId = seed,
                playlistId = liked.playlistId,
                playlistTitle = liked.title.ifBlank { "心动模式" },
                onStarted = onStarted,
            )
        }
    }

    private suspend fun resolveLikedForIntelligence(): LikedPlaylistRepository.Snapshot? {
        val cached = likedPlaylistRepository.peek()
        if (cached != null && cached.playlistId > 0L &&
            (cached.tracks.isNotEmpty() || cached.displayIds.isNotEmpty() || cached.allLikedIds.isNotEmpty())
        ) {
            return cached
        }
        return runCatching { likedPlaylistRepository.forceRefresh() }.getOrNull()
            ?: likedPlaylistRepository.peek()?.takeIf { it.playlistId > 0L }
    }

    /** 在当前队列内跳转到指定索引（保留队列与随机历史策略）。 */
    fun playIndex(index: Int) {
        pendingAdvanceAfterHold = false
        val q = _ui.value.queue
        if (index !in q.indices) return
        if (index == _ui.value.index) {
            if (!exoPlayer.playWhenReady) {
                exoPlayer.play()
            }
            return
        }
        loadAndPlayIndex(index, recordShuffleHistory = true)
    }

    /**
     * 同源歌单后台补全后扩展播放队列（曲谱 / 切歌 peek 与完整列表一致）。
     * 按当前曲 id 重定位索引，不打断正在播的媒体。
     */
    fun expandQueueFromSourcePlaylist(playlistId: Long, tracks: List<TrackRow>) {
        if (intelligenceActive || playlistId <= 0L || tracks.isEmpty()) return
        val ui = _ui.value
        if (!ui.hasQueue || ui.sourcePlaylistId != playlistId) return
        if (tracks.size <= ui.queue.size) {
            // 同长度但内容更完整（封面等）仍可替换；更短则忽略防回退
            if (tracks.size < ui.queue.size) return
            val sameIds = ui.queue.size == tracks.size &&
                ui.queue.indices.all { ui.queue[it].id == tracks[it].id }
            if (sameIds) return
        }
        val currentId = ui.currentTrack?.id
        val newIndex = when {
            currentId != null -> {
                val i = tracks.indexOfFirst { it.id == currentId }
                if (i >= 0) i else ui.index.coerceIn(0, tracks.lastIndex)
            }
            else -> ui.index.coerceIn(0, tracks.lastIndex)
        }
        _ui.update {
            it.copy(
                queue = tracks,
                index = newIndex,
            )
        }
        refreshPeeksAndPrefetch()
        persistSnapshot()
    }

    fun clearQueue() {
        sleepTimer?.cancel()
        flushSample()
        cancelLoads()
        urlCache.clear()
        unplayableUntil.clear()
        preparedShuffleNext = null
        shuffleHistory.clear()
        holdAutoAdvance = false
        pendingAdvanceAfterHold = false
        fmHydrateJob?.cancel()
        fmHydrateJob = null
        pendingFmAdvance = false
        fmActive = false
        intelligenceActive = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        playbackMode = _ui.value.playbackMode
        stateStore.clear()
        _ui.value = PlaybackUiState(playbackMode = playbackMode, hasQueue = false)
        onClearAndStopService?.invoke()
    }

    fun togglePlayPause() {
        if (exoPlayer.playWhenReady) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.mediaItemCount == 0) {
                val i = _ui.value.index
                if (i >= 0 && _ui.value.hasQueue) {
                    // 从快照中止位置续播，勿从头开始
                    loadAndPlayIndex(i, resumeAtMs = _ui.value.positionMs)
                }
            } else {
                exoPlayer.play()
            }
        }
    }

    /**
     * 冷启动：有队列快照时以暂停态装入当前曲，让 Media3 挂上歌曲通知
     *（不自动开播）。已有 MediaItem 时不重复装载。
     */
    fun preparePausedForNotification() {
        if (exoPlayer.mediaItemCount > 0) return
        val ui = _ui.value
        if (!ui.hasQueue || ui.index !in ui.queue.indices) return
        val track = ui.queue[ui.index]
        if (!hasLocalAudio(track) && !context.isNetworkOnline()) return
        loadAndPlayIndex(
            idx = ui.index,
            resumeAtMs = ui.positionMs.coerceAtLeast(0L),
            resumePlayWhenReady = false,
        )
    }

    /** MV 占用焦点时：暂停歌曲且不要在 MV 暂停时被系统焦点抢回去。 */
    fun yieldToForeignPlayback(active: Boolean) {
        persistentFocus.setForeignYield(active)
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        val from = exoPlayer.currentPosition.coerceAtLeast(0L)
        val jumped = kotlin.math.abs(target - from) > 400L
        exoPlayer.seekTo(target)
        _ui.update { it.copy(positionMs = target) }
        // 大幅跳转且正在播放：1s 声音渐起，避免位置硬切
        if (jumped && exoPlayer.isPlaying) {
            persistentFocus.fadeInFromSilence(durationMs = 1_000L)
        }
    }

    fun skipNext() {
        pendingAdvanceAfterHold = false
        val i = _ui.value.index
        if (!_ui.value.hasQueue || i < 0) return
        val ni = nextPlayableIndex(i, wrap = !radioActive)
        if (ni != null) {
            loadAndPlayIndex(ni, recordShuffleHistory = true)
            if (radioActive) ensureRadioLookahead()
            return
        }
        if (radioActive) {
            pendingFmAdvance = true
            ensureRadioLookahead()
        }
    }

    fun skipPrevious() {
        pendingAdvanceAfterHold = false
        val i = _ui.value.index
        if (!_ui.value.hasQueue || i < 0) return
        if (playbackMode == PlaybackMode.SHUFFLE) {
            // 随机模式：回退到真实听过的上一首；无历史则从头
            while (shuffleHistory.isNotEmpty()) {
                val pi = shuffleHistory.removeLast()
                val t = _ui.value.queue.getOrNull(pi) ?: continue
                if (pi == i) continue
                if (isUnplayable(t.id)) continue
                // 回退后下一首重新随机，不再回到刚才那首
                preparedShuffleNext = null
                loadAndPlayIndex(pi, recordShuffleHistory = false)
                return
            }
            seekTo(0L)
            return
        }
        val pi = prevPlayableIndex(i)
        if (pi != null) {
            loadAndPlayIndex(pi, recordShuffleHistory = false)
        } else {
            seekTo(0L)
        }
    }

    fun cyclePlaybackMode() {
        playbackMode = if (radioActive) {
            when (playbackMode) {
                PlaybackMode.ORDER -> PlaybackMode.REPEAT_ONE
                PlaybackMode.REPEAT_ONE, PlaybackMode.SHUFFLE -> PlaybackMode.ORDER
            }
        } else {
            when (playbackMode) {
                PlaybackMode.ORDER -> PlaybackMode.REPEAT_ONE
                PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
                PlaybackMode.SHUFFLE -> PlaybackMode.ORDER
            }
        }
        preparedShuffleNext = null
        if (playbackMode != PlaybackMode.SHUFFLE) {
            shuffleHistory.clear()
        }
        applyRepeatMode()
        _ui.update { it.copy(playbackMode = playbackMode) }
        refreshPeeksAndPrefetch()
        persistSnapshot()
    }

    fun release() {
        flushSample()
        cancelLoads()
        tickerJob?.cancel()
        persistentFocus.release()
        audioOutputController.detachPlayer(exoPlayer)
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        _spectrum.value = AudioSpectrumBands.ZERO
    }

    private fun applyRepeatMode() {
        exoPlayer.repeatMode = when (playbackMode) {
            PlaybackMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(200)
                val ui = _ui.value
                // 切歌加载中 / 播放器仍是旧曲时，勿回写进度（否则会 0→旧进度→0 闪烁）
                if (ui.loadPending) continue
                val expectedId = ui.currentTrack?.id?.toString() ?: continue
                if (exoPlayer.currentMediaItem?.mediaId != expectedId) continue
                val st = exoPlayer.playbackState
                if (st == Player.STATE_IDLE || st == Player.STATE_ENDED) continue
                // seek 时 BUFFERING，currentPosition 可能短暂为 0，勿把进度条打回开头
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (st == Player.STATE_BUFFERING && pos <= 48L && ui.positionMs > 200L) {
                    continue
                }
                _ui.update { it.copy(positionMs = pos) }
                val dur = when {
                    exoPlayer.duration > 0L && exoPlayer.duration != C.TIME_UNSET ->
                        exoPlayer.duration
                    else -> ui.durationMs
                }
                sampleTick(
                    playing = exoPlayer.isPlaying && !ui.loadPending,
                    track = ui.currentTrack,
                    durationMs = dur,
                    quality = audioQualityStore.current(),
                    positionMs = pos,
                )
            }
        }
    }

    /** 倒计时到点且不等本首结束：立即暂停。 */
    fun pauseForSleepTimer() {
        sleepTimer?.cancel()
        pendingAdvanceAfterHold = false
        pendingFmAdvance = false
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            suppressAutoAdvanceOnce = true
        }
        exoPlayer.pause()
        exoPlayer.playWhenReady = false
        flushSample()
        _ui.update {
            it.copy(
                isPlaying = false,
                playWhenReady = false,
                buffering = false,
                loadPending = false,
            )
        }
        persistSnapshot()
        applyRepeatMode()
        context.showIslandNotice("已定时停止", _ui.value.currentTrack?.coverUrl)
    }

    fun restoreRepeatAfterSleepCancel() {
        applyRepeatMode()
    }

    /** 到点且选择等本首结束：关掉单曲循环，若已停住则立刻结束。 */
    fun onSleepWaitDeadline() {
        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        val st = exoPlayer.playbackState
        if (!exoPlayer.playWhenReady ||
            st == Player.STATE_ENDED ||
            st == Player.STATE_IDLE
        ) {
            pauseForSleepTimer()
        }
    }

    /**
     * 竖屏评论等 UI 打开时：曲末先停住，不自动切歌；关闭后再进下一首。
     */
    fun setHoldAutoAdvance(hold: Boolean) {
        if (holdAutoAdvance == hold) {
            if (!hold && pendingAdvanceAfterHold) {
                pendingAdvanceAfterHold = false
                advanceAfterCommentsHold()
            }
            return
        }
        holdAutoAdvance = hold
        if (hold) {
            // 单曲循环时 Exo 可能不进 STATE_ENDED；评论打开期间强制 OFF
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        } else {
            applyRepeatMode()
            if (pendingAdvanceAfterHold) {
                pendingAdvanceAfterHold = false
                advanceAfterCommentsHold()
            }
        }
    }

    private fun onEnded() {
        if (suppressAutoAdvanceOnce) {
            suppressAutoAdvanceOnce = false
            return
        }
        if (sleepTimer?.onTrackEnded() == true) return
        if (holdAutoAdvance) {
            pendingAdvanceAfterHold = true
            exoPlayer.pause()
            _ui.update {
                it.copy(
                    isPlaying = false,
                    playWhenReady = false,
                    buffering = false,
                    loadPending = false,
                )
            }
            persistSnapshot()
            return
        }
        when (playbackMode) {
            PlaybackMode.REPEAT_ONE -> {
                exoPlayer.seekTo(0L)
                exoPlayer.play()
            }
            PlaybackMode.ORDER, PlaybackMode.SHUFFLE -> {
                val wrapList = playbackMode == PlaybackMode.ORDER && !radioActive
                val ni = nextPlayableIndex(_ui.value.index, wrap = wrapList)
                if (ni != null) {
                    loadAndPlayIndex(ni, recordShuffleHistory = true)
                    if (radioActive) ensureRadioLookahead()
                } else if (radioActive) {
                    pendingFmAdvance = true
                    ensureRadioLookahead()
                } else {
                    exoPlayer.pause()
                    _ui.update {
                        it.copy(
                            isPlaying = false,
                            playWhenReady = false,
                            buffering = false,
                            loadPending = false,
                        )
                    }
                    persistSnapshot()
                }
            }
        }
    }

    /** 评论关闭后：按「下一首」切歌并播放（含原单曲循环模式下的挂起曲末）。 */
    private fun advanceAfterCommentsHold() {
        val ni = nextPlayableIndex(_ui.value.index, wrap = !radioActive)
        if (ni != null) {
            loadAndPlayIndex(ni, recordShuffleHistory = true)
        } else {
            applyRepeatMode()
            persistSnapshot()
        }
    }

    /** 跳过预检失败曲目后的下一首（切歌 / 播完 / 预览一致）。 */
    private fun nextPlayableIndex(from: Int, wrap: Boolean = false): Int? {
        val q = _ui.value.queue
        if (q.isEmpty()) return null
        return when (playbackMode) {
            PlaybackMode.ORDER, PlaybackMode.REPEAT_ONE -> {
                var i = from + 1
                while (i <= q.lastIndex) {
                    if (!isUnplayable(q[i].id)) return i
                    i++
                }
                if (!wrap) return null
                i = 0
                while (i < from) {
                    if (!isUnplayable(q[i].id)) return i
                    i++
                }
                from.takeIf { it in q.indices && !isUnplayable(q[it].id) }
            }
            PlaybackMode.SHUFFLE -> {
                repeat(q.size.coerceAtMost(12)) {
                    val n = ensureShuffleNext(from) ?: return null
                    if (!isUnplayable(q[n].id)) return n
                    preparedShuffleNext = null
                }
                null
            }
        }
    }

    private fun prevPlayableIndex(from: Int): Int? {
        val q = _ui.value.queue
        if (q.isEmpty()) return null
        return when (playbackMode) {
            PlaybackMode.ORDER, PlaybackMode.REPEAT_ONE -> {
                var i = from - 1
                while (i >= 0) {
                    if (!isUnplayable(q[i].id)) return i
                    i--
                }
                null
            }
            PlaybackMode.SHUFFLE -> {
                // 预览用：历史栈顶即为真实上一首（不弹出）
                shuffleHistory.lastOrNull()?.takeIf { idx ->
                    idx != from &&
                        idx in q.indices &&
                        !isUnplayable(q[idx].id)
                }
            }
        }
    }

    private fun ensureShuffleNext(from: Int): Int? {
        val size = _ui.value.queue.size
        if (size <= 1) return null
        val existing = preparedShuffleNext
        if (existing != null && existing != from && existing in 0 until size) return existing
        val n = pickShuffle(from, size)
        preparedShuffleNext = n
        return n
    }

    private fun refreshPeeksAndPrefetch() {
        refreshPeeks()
        scheduleNeighborPrefetch()
    }

    private fun refreshPeeks() {
        val ui = _ui.value
        val i = ui.index
        if (!ui.hasQueue || i < 0) {
            _ui.update { it.copy(peekNextTrack = null, peekPrevTrack = null) }
            return
        }
        val next = nextPlayableIndex(i, wrap = !radioActive)?.let { ui.queue.getOrNull(it) }
        val prev = prevPlayableIndex(i)?.let { ui.queue.getOrNull(it) }
        _ui.update { it.copy(peekNextTrack = next, peekPrevTrack = prev) }
        pruneUrlCache(
            buildSet {
                ui.currentTrack?.id?.let { add(it) }
                next?.id?.let { add(it) }
                prev?.id?.let { add(it) }
                if (ui.radioActive) {
                    ui.queue.drop(ui.index + 1).take(FM_AHEAD).forEach { add(it.id) }
                }
            },
        )
    }

    /**
     * 始终预热邻曲：音源 URL + 歌词 + 封面。
     * URL 失败则拉黑并重选 peek，使切歌可直接跳过无法播放的曲。
     */
    private fun scheduleNeighborPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            var rounds = 0
            while (rounds++ < 4 && isActive) {
                val ui = _ui.value
                if (!ui.hasQueue) return@launch
                val cookie = sessionRepository.session.value?.cookie.orEmpty()
                val neighbors = buildList {
                    ui.peekNextTrack?.let { add(it) }
                    ui.peekPrevTrack?.let { add(it) }
                    if (ui.radioActive) {
                        addAll(ui.queue.drop(ui.index + 1).take(FM_AHEAD))
                    }
                }.distinctBy { it.id }
                val current = ui.currentTrack
                var marked = false

                coroutineScope {
                    val jobs = buildList {
                        for (t in neighbors) {
                            add(async { lyricRepository.prefetch(t.id, cookie) })
                            add(async { UrlImageCache.prefetch(context, t.coverUrl) })
                        }
                        // 当前封面也预热（通知/返回页）
                        current?.coverUrl?.let { url ->
                            add(async { UrlImageCache.prefetch(context, url) })
                        }
                        current?.let { t ->
                            if (lyricRepository.peekPack(t.id)?.translationResolved != true) {
                                add(async { lyricRepository.prefetch(t.id, cookie) })
                            }
                        }
                        // 邻曲 + 当前：预热 like 状态（有完整红心歌单缓存则跳过网络）
                        if (cookie.isNotEmpty()) {
                            val likeTargets = buildList {
                                current?.let { add(it) }
                                addAll(neighbors)
                            }.distinctBy { it.id }
                            val needCheck = likeTargets.filter {
                                likedPlaylistRepository.isLiked(it.id) == null
                            }
                            if (needCheck.isNotEmpty()) {
                                add(
                                    async {
                                        runCatching {
                                            val json = userClient.songLikeCheck(
                                                needCheck.map { it.id },
                                                cookie,
                                            )
                                            val likedIds =
                                                NcmLibraryParse.likedIdsFromLikeCheck(json)
                                            likedPlaylistRepository.recordLikeStatuses(
                                                needCheck,
                                                likedIds,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    jobs.awaitAll()
                }

                for (t in neighbors) {
                    if (isUnplayable(t.id)) continue
                    if (urlCache[t.id]?.isFresh() == true) continue
                    if (!hasLocalAudio(t) && !context.isNetworkOnline()) continue
                    val url = resolvePlayUrl(t, cookie)
                    if (url.isNullOrBlank()) {
                        if (!context.isNetworkOnline()) continue
                        Log.d(TAG, "prefetch url miss → unplayable id=${t.id}")
                        markUnplayable(t.id)
                        clearPreparedIfTrack(t.id)
                        marked = true
                    } else {
                        urlCache[t.id] = CachedUrl(url, System.currentTimeMillis())
                        Log.d(TAG, "prefetched url id=${t.id}")
                    }
                }
                if (!marked) break
                refreshPeeks()
            }
        }
    }

    private fun isUnplayable(trackId: Long): Boolean {
        val until = unplayableUntil[trackId] ?: return false
        if (System.currentTimeMillis() >= until) {
            unplayableUntil.remove(trackId)
            return false
        }
        return true
    }

    private fun markUnplayable(trackId: Long) {
        unplayableUntil[trackId] = System.currentTimeMillis() + UNPLAYABLE_TTL_MS
        urlCache.remove(trackId)
        if (unplayableUntil.size > UNPLAYABLE_MAX) {
            unplayableUntil.entries
                .sortedBy { it.value }
                .take(unplayableUntil.size - UNPLAYABLE_MAX + 16)
                .map { it.key }
                .forEach { unplayableUntil.remove(it) }
        }
    }

    private fun clearPreparedIfTrack(trackId: Long) {
        val q = _ui.value.queue
        preparedShuffleNext?.let { idx ->
            if (q.getOrNull(idx)?.id == trackId) preparedShuffleNext = null
        }
        shuffleHistory.removeAll { q.getOrNull(it)?.id == trackId }
    }

    /** 只保留当前 + 邻曲的新鲜 URL；上一首切走后仍可作下一轮 prev。 */
    private fun pruneUrlCache(keep: Set<Long>) {
        val now = System.currentTimeMillis()
        urlCache.entries.removeAll { (id, cached) ->
            id !in keep || !cached.isFresh(now)
        }
    }

    private fun loadAndPlayIndex(
        idx: Int,
        isRetry: Boolean = false,
        resumeAtMs: Long = 0L,
        recordShuffleHistory: Boolean = false,
        resumePlayWhenReady: Boolean = true,
    ) {
        if (!isRetry && idx != _ui.value.index && sleepTimer?.onLeavingTrack() == true) {
            return
        }
        val track = _ui.value.queue.getOrNull(idx) ?: return
        val nextQuality = audioQualityStore.current()
        if (sampleTrackId > 0L &&
            (sampleTrackId != track.id || sampleQuality != nextQuality.level)
        ) {
            flushSample()
        }
        val livePrefs = realtimeCacheStore.current()
        if (livePrefs.liveDownloadEnabled) {
            realtimeCache.notifyRealtimePlayStarted(track.id, nextQuality)
        }
        if (!isRetry) {
            if (retryIndex != idx) {
                retryIndex = idx
                retryCount = 0
            }
        } else {
            retryCount++
            if (retryCount > MAX_RETRIES) {
                markUnplayable(track.id)
                postUnplayableNotice()
                advanceAfterFailure(idx)
                return
            }
        }
        val fromIndex = _ui.value.index
        if (recordShuffleHistory &&
            playbackMode == PlaybackMode.SHUFFLE &&
            fromIndex >= 0 &&
            fromIndex != idx &&
            fromIndex in _ui.value.queue.indices
        ) {
            shuffleHistory.addLast(fromIndex)
            while (shuffleHistory.size > MAX_SHUFFLE_HISTORY) {
                shuffleHistory.removeFirst()
            }
        }
        // 快切：取消旧曲加载，立即切到目标索引并只加载当前曲
        cancelLoads(keepPrefetch = false)
        // 切走后下一首预选作废，保证之后「下一首」重新随机
        preparedShuffleNext = null
        val startPos = resumeAtMs.coerceAtLeast(0L)
        val cachedLyrics = lyricRepository.peekPack(track.id)
        // 切歌：只暂停旧曲，勿 stop/clearMediaItems —— 清空播放列表会拆掉 Media3 通知再重建，触发系统 FGS 警告
        exoPlayer.playWhenReady = false
        // 同步更新索引 / peek，黑胶可继续滑；音源在后台加载
        _ui.update {
            it.copy(
                index = idx,
                loadPending = true,
                buffering = true,
                hasQueue = true,
                error = null,
                positionMs = startPos,
                durationMs = track.durationMs.coerceAtLeast(0L),
                lyricLines = cachedLyrics?.original.orEmpty(),
                translatedLyricLines = cachedLyrics?.translated.orEmpty(),
                wordLyricLines = cachedLyrics?.wordOriginal.orEmpty(),
                translatedWordLyricLines = cachedLyrics?.wordTranslated.orEmpty(),
                isPlaying = false,
                playWhenReady = false,
            )
        }
        refreshPeeksAndPrefetch()
        if (radioActive) ensureRadioLookahead()
        val loadGen = track.id
        loadJob = scope.launch {
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            try {
                // 磁盘歌词优先填上，避免等网络；并补齐译文
                val accelHit = downloadAccelIndex.lookup(track.id)
                val lyricUri = track.localLyricUri ?: accelHit?.lyricUri
                val transLyricUri = track.localTransLyricUri ?: accelHit?.transLyricUri
                val localLyrics = if (!lyricUri.isNullOrBlank() || !transLyricUri.isNullOrBlank()) {
                    lyricRepository.loadFromLocalUris(lyricUri, transLyricUri)
                } else {
                    null
                }
                if (localLyrics != null && localLyrics.original.isNotEmpty() &&
                    _ui.value.currentTrack?.id == track.id
                ) {
                    _ui.update { it.withLyricPack(localLyrics) }
                } else if (_ui.value.lyricLines.isEmpty() ||
                    cachedLyrics?.translationResolved != true
                ) {
                    val diskOrNet = lyricRepository.loadBestEffort(track.id, cookie)
                    if (diskOrNet.original.isNotEmpty() && _ui.value.currentTrack?.id == track.id) {
                        _ui.update { it.withLyricPack(diskOrNet) }
                    }
                }
                // 已被更新的快切目标取代则放弃
                if (_ui.value.currentTrack?.id != loadGen) return@launch
                // 重试时强制重新解析；否则可命中邻曲预热缓存
                val cached = if (isRetry) {
                    null
                } else {
                    urlCache[track.id]?.takeIf { it.isFresh() }?.url
                }
                if (isRetry) urlCache.remove(track.id)
                val url = cached ?: resolvePlayUrl(track, cookie)?.also {
                    urlCache[track.id] = CachedUrl(it, System.currentTimeMillis())
                }
                if (_ui.value.currentTrack?.id != loadGen) return@launch
                if (url.isNullOrBlank()) {
                    if (!hasLocalAudio(track) && !context.isNetworkOnline()) {
                        _ui.update {
                            it.copy(
                                loadPending = false,
                                buffering = false,
                                isPlaying = false,
                                playWhenReady = false,
                            )
                        }
                        return@launch
                    }
                    markUnplayable(track.id)
                    postUnplayableNotice()
                    advanceAfterFailure(idx)
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    if (_ui.value.currentTrack?.id != loadGen) return@withContext
                    // 直接替换当前 MediaItem，通知原地更新，不经历「无曲目 → 撤通知」
                    exoPlayer.setMediaItem(buildMediaItem(track, url), startPos)
                    applyRepeatMode()
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = resumePlayWhenReady
                }
                if (_ui.value.currentTrack?.id != loadGen) return@launch
                if (!isRetry && resumePlayWhenReady) {
                    maybeNoticeCacheAccel(track, url)
                }
                persistSnapshot()
                if (localLyrics == null || localLyrics.original.isEmpty()) {
                    loadLyricsAsync(track.id, cookie)
                }
            } catch (e: Exception) {
                if (_ui.value.currentTrack?.id != loadGen) return@launch
                Log.w(TAG, "loadAndPlayIndex failed", e)
                if (!context.isNetworkOnline()) {
                    _ui.update {
                        it.copy(
                            loadPending = false,
                            buffering = false,
                            isPlaying = false,
                            playWhenReady = false,
                        )
                    }
                    return@launch
                }
                markUnplayable(track.id)
                postUnplayableNotice()
                advanceAfterFailure(idx)
            }
        }
    }

    /** 右上角短通知 + 唤醒底部控件；3 秒后自动清除。 */
    private fun postUnplayableNotice() {
        val token = System.currentTimeMillis()
        noticeJob?.cancel()
        _ui.update {
            it.copy(
                error = null,
                notice = PlaybackNotice(token = token, message = "该歌曲不可播放"),
                transportWakeToken = it.transportWakeToken + 1,
                loadPending = true,
                buffering = true,
            )
        }
        noticeJob = scope.launch {
            delay(NOTICE_VISIBLE_MS)
            _ui.update { cur ->
                if (cur.notice?.token == token) cur.copy(notice = null) else cur
            }
        }
    }

    private fun advanceAfterFailure(failedIndex: Int) {
        val failedId = _ui.value.queue.getOrNull(failedIndex)?.id
        if (failedId != null) markUnplayable(failedId)
        when (playbackMode) {
            PlaybackMode.ORDER -> {
                val ni = nextPlayableIndex(failedIndex, wrap = !radioActive)
                if (ni != null) {
                    loadAndPlayIndex(ni, recordShuffleHistory = false)
                    if (radioActive) ensureRadioLookahead()
                } else if (radioActive) {
                    pendingFmAdvance = true
                    ensureRadioLookahead()
                } else {
                    _ui.update { it.copy(loadPending = false, isPlaying = false, playWhenReady = false, buffering = false) }
                }
            }
            PlaybackMode.SHUFFLE -> {
                preparedShuffleNext = null
                val ni = nextPlayableIndex(failedIndex)
                if (ni != null) {
                    loadAndPlayIndex(ni, recordShuffleHistory = false)
                } else {
                    _ui.update { it.copy(loadPending = false, isPlaying = false, playWhenReady = false, buffering = false) }
                }
            }
            PlaybackMode.REPEAT_ONE -> {
                if (retryCount <= MAX_RETRIES) loadAndPlayIndex(failedIndex, isRetry = true)
                else _ui.update { it.copy(loadPending = false, isPlaying = false, playWhenReady = false, buffering = false) }
            }
        }
    }

    private val fallbackArtworkData: ByteArray by lazy {
        // 预读 256px RGBA PNG；禁止用过大原图塞进 MediaMetadata
        context.resources.openRawResource(R.drawable.ic_notification_artwork).use { it.readBytes() }
    }

    private fun buildMediaItem(track: TrackRow, url: String): MediaItem {
        val cover = track.coverUrl?.takeIf { it.isNotBlank() }
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(track.name)
            .setArtist(track.artists)
            .setAlbumTitle(track.album)
        if (cover != null) {
            metaBuilder.setArtworkUri(Uri.parse(cover))
        } else {
            metaBuilder.setArtworkData(
                fallbackArtworkData,
                MediaMetadata.PICTURE_TYPE_FRONT_COVER,
            )
        }
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(url)
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    private fun loadLyricsAsync(songId: Long, cookie: String) {
        lyricJob?.cancel()
        lyricJob = scope.launch {
            val pack = lyricRepository.loadBestEffort(songId, cookie)
            if (_ui.value.currentTrack?.id == songId) {
                _ui.update { it.withLyricPack(pack) }
            }
        }
    }

    private fun ensureRadioLookahead() {
        if (!radioActive) return
        if (fmHydrateJob?.isActive == true) return
        val remaining = _ui.value.queue.size - _ui.value.index - 1
        if (remaining >= FM_AHEAD && !fmKickExtra) return
        fmHydrateJob = scope.launch { hydrateRadioLookahead() }
    }

    private suspend fun hydrateRadioLookahead() {
        var requests = 0
        while (currentCoroutineContext().isActive && radioActive && requests < FM_MAX_REQUESTS) {
            val remaining = _ui.value.queue.size - _ui.value.index - 1
            if (remaining >= FM_AHEAD) break
            val batch = fetchRadioBatch()
            requests++
            if (batch.isEmpty()) break
            if (!appendRadioTracks(batch)) break
        }
        if (currentCoroutineContext().isActive && radioActive && requests < FM_MAX_REQUESTS) {
            val extra = fetchRadioBatch()
            requests++
            if (extra.isNotEmpty()) appendRadioTracks(extra)
        }
        fmKickExtra = false
        refreshPeeksAndPrefetch()
        if (pendingFmAdvance && radioActive) {
            val ni = nextPlayableIndex(_ui.value.index)
            if (ni != null) {
                pendingFmAdvance = false
                loadAndPlayIndex(ni, recordShuffleHistory = false)
            }
        }
    }

    private fun appendRadioTracks(tracks: List<TrackRow>): Boolean {
        if (!radioActive || tracks.isEmpty()) return false
        val seen = _ui.value.queue.mapTo(HashSet()) { it.id }
        val extra = tracks.filter { it.id > 0L && seen.add(it.id) }
        if (extra.isEmpty()) return false
        _ui.update { it.copy(queue = it.queue + extra) }
        persistSnapshot()
        return true
    }

    private suspend fun fetchRadioBatch(): List<TrackRow> =
        if (intelligenceActive) fetchIntelligenceBatch() else fetchPersonalFmBatch()

    private suspend fun fetchIntelligenceBatch(
        songId: Long = _ui.value.currentTrack?.id ?: 0L,
        playlistId: Long = _ui.value.sourcePlaylistId ?: 0L,
        startSongId: Long = songId,
    ): List<TrackRow> {
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isBlank() || songId <= 0L || playlistId <= 0L) return emptyList()
        return runCatching {
            val json = withContext(Dispatchers.IO) {
                userClient.playmodeIntelligenceList(
                    songId = songId,
                    playlistId = playlistId,
                    cookie = cookie,
                    startSongId = startSongId,
                )
            }
            NcmHomeParse.intelligenceTracks(json)
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchPersonalFmBatch(): List<TrackRow> {
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isBlank()) return emptyList()
        return runCatching {
            val json = withContext(Dispatchers.IO) { userClient.personalFm(cookie) }
            NcmHomeParse.personalFmTracks(json)
        }.getOrDefault(emptyList())
    }

    private fun cancelLoads(keepPrefetch: Boolean = false) {
        loadJob?.cancel()
        lyricJob?.cancel()
        errorRetryJob?.cancel()
        if (!keepPrefetch) prefetchJob?.cancel()
        persistentFocus.snapVolume()
    }

    private fun persistSnapshot() {
        val s = _ui.value
        if (s.hasQueue && s.queue.isNotEmpty()) stateStore.save(s)
    }

    private fun reloadCurrentForQuality() {
        urlCache.clear()
        retryCount = 0
        val idx = _ui.value.index
        if (!_ui.value.hasQueue || idx !in _ui.value.queue.indices) return
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L).let { cur ->
            if (cur > 0L) cur else _ui.value.positionMs
        }
        val play = exoPlayer.playWhenReady || _ui.value.playWhenReady
        loadAndPlayIndex(
            idx = idx,
            isRetry = false,
            resumeAtMs = pos,
            resumePlayWhenReady = play,
        )
    }

    private fun switchCurrentToLocalIfNeeded(map: Map<Long, DownloadAccelHit>) {
        val ui = _ui.value
        val track = ui.currentTrack ?: return
        val local = map[track.id]?.audioUri ?: return
        val playing = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        if (playing == local) {
            urlCache[track.id] = CachedUrl(local, System.currentTimeMillis())
            if (exoPlayer.playWhenReady || ui.playWhenReady) {
                maybeNoticeCacheAccel(track, local)
            }
            return
        }
        if (!ui.hasQueue || ui.index !in ui.queue.indices) return
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L).let { cur ->
            if (cur > 0L) cur else ui.positionMs
        }
        val play = exoPlayer.playWhenReady || ui.playWhenReady
        loadAndPlayIndex(
            idx = ui.index,
            isRetry = false,
            resumeAtMs = pos,
            resumePlayWhenReady = play,
        )
    }

    private fun switchCurrentToRealtimeCacheIfNeeded() {
        val ui = _ui.value
        val track = ui.currentTrack ?: return
        val local = realtimeCache.playUri(track.id, audioQualityStore.current()) ?: return
        val playing = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        if (playing == local) {
            urlCache[track.id] = CachedUrl(local, System.currentTimeMillis())
            return
        }
        if (!ui.hasQueue || ui.index !in ui.queue.indices) return
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L).let { cur ->
            if (cur > 0L) cur else ui.positionMs
        }
        val play = exoPlayer.playWhenReady || ui.playWhenReady
        loadAndPlayIndex(
            idx = ui.index,
            isRetry = false,
            resumeAtMs = pos,
            resumePlayWhenReady = play,
        )
    }

    private fun sampleTick(
        playing: Boolean,
        track: TrackRow?,
        durationMs: Long,
        quality: AudioQuality,
        positionMs: Long,
    ) {
        val prefs = realtimeCacheStore.current()
        if (!prefs.enabled || !prefs.mode.available) {
            discardSample()
            return
        }
        val id = track?.id ?: 0L
        if (id <= 0L) {
            flushSample()
            return
        }
        val q = quality.level
        val wrapped = sampleTrackId == id &&
            sampleQuality == q &&
            sampleLastPos > 0L &&
            durationMs > 0L &&
            sampleLastPos > durationMs * 3 / 4 &&
            positionMs < durationMs / 10 &&
            sampleListenedMs >= 1_000L
        if (wrapped || sampleTrackId != id || sampleQuality != q) {
            if (wrapped || sampleTrackId > 0L) flushSample()
            sampleTrackId = id
            sampleQuality = q
            sampleListenedMs = 0L
            sampleDurationMs = durationMs.coerceAtLeast(0L)
            sampleStartedAt = System.currentTimeMillis()
            sampleAccumAt = 0L
            sampleLastPos = positionMs
        }
        if (durationMs > sampleDurationMs) sampleDurationMs = durationMs
        sampleLastPos = positionMs
        if (playing && prefs.liveDownloadEnabled) {
            realtimeCache.notifyRealtimePlayStarted(id, quality)
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (playing) {
            if (sampleAccumAt > 0L) {
                sampleListenedMs += (now - sampleAccumAt).coerceIn(0L, 500L)
            }
            sampleAccumAt = now
        } else {
            sampleAccumAt = 0L
        }
    }

    private fun flushSample() {
        val id = sampleTrackId
        val quality = sampleQuality
        val listened = sampleListenedMs
        val duration = sampleDurationMs
        val started = sampleStartedAt
        val mode = realtimeCacheStore.current().mode
        discardSample()
        if (id <= 0L || quality.isBlank()) return
        val q = AudioQuality.fromLevel(quality)
        if (mode == RealtimeCacheMode.Realtime) {
            realtimeCache.notifyRealtimePlayEnded(id, q, listened, duration)
            return
        }
        if (mode == RealtimeCacheMode.Aggressive) {
            realtimeCache.resetAggressiveListenSession()
            return
        }
        if (listened < 200L) return
        realtimeCache.recordSession(
            trackId = id,
            quality = q,
            listenedMs = listened,
            durationMs = duration,
            startedAt = if (started > 0L) started else System.currentTimeMillis() - listened,
            endedAt = System.currentTimeMillis(),
        )
    }

    private fun discardSample() {
        sampleTrackId = 0L
        sampleQuality = ""
        sampleListenedMs = 0L
        sampleDurationMs = 0L
        sampleStartedAt = 0L
        sampleAccumAt = 0L
        sampleLastPos = -1L
    }

    private fun maybeNoticeCacheAccel(track: TrackRow, playUrl: String) {
        if (!downloadAccelStore.current()) {
            accelNoticeTrackId = 0L
            return
        }
        val local = downloadAccelIndex.audioUri(track.id)
        if (local.isNullOrBlank() || playUrl != local) {
            accelNoticeTrackId = 0L
            return
        }
        if (accelNoticeTrackId == track.id) return
        accelNoticeTrackId = track.id
        val cover = track.coverUrl ?: downloadAccelIndex.lookup(track.id)?.coverUri
        context.showIslandNotice("此歌曲已进行缓存加速", cover)
    }

    private fun hasLocalAudio(track: TrackRow): Boolean =
        realtimeCache.playUri(track.id, audioQualityStore.current()) != null ||
            !track.localAudioUri.isNullOrBlank() ||
            downloadAccelIndex.audioUri(track.id) != null

    private suspend fun resolvePlayUrl(track: TrackRow, cookie: String): String? {
        realtimeCache.playUri(track.id, audioQualityStore.current())?.let { return it }
        track.localAudioUri?.takeIf { it.isNotBlank() }?.let { return it }
        downloadAccelIndex.audioUri(track.id)?.let { return it }
        if (track.id <= 0L) return null
        if (!context.isNetworkOnline()) return null
        return runCatching {
            PlayUrlResolver.resolve(
                userClient = userClient,
                trackId = track.id,
                cookie = cookie,
                quality = audioQualityStore.current(),
            )
        }.onFailure { Log.w(TAG, "resolvePlayUrl failed id=${track.id}", it) }
            .getOrNull()
    }

    private fun pickShuffle(current: Int, size: Int): Int {
        if (size <= 1) return current
        var n = current
        while (n == current) n = Random.nextInt(0, size)
        return n
    }

    private fun canSeekNext(): Boolean {
        val i = _ui.value.index
        if (!_ui.value.hasQueue || i < 0) return false
        if (radioActive) return true
        return nextPlayableIndex(i, wrap = true) != null
    }

    private inner class QueueAwarePlayer(player: ExoPlayer) : ForwardingPlayer(player) {
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
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                _ui.value.hasQueue && _ui.value.index >= 0
            else -> super.isCommandAvailable(command)
        }

        override fun seekToNext() = skipNext()
        override fun seekToNextMediaItem() = skipNext()
        override fun seekToPrevious() = skipPrevious()
        override fun seekToPreviousMediaItem() = skipPrevious()
        override fun hasNextMediaItem(): Boolean = canSeekNext()
        override fun hasPreviousMediaItem(): Boolean = _ui.value.hasQueue && _ui.value.index >= 0
    }

    private data class CachedUrl(val url: String, val atMs: Long) {
        fun isFresh(now: Long = System.currentTimeMillis()) = now - atMs < URL_TTL_MS
    }

    companion object {
        private const val TAG = "PlaylistCoordinator"
        private const val MAX_RETRIES = 2
        private const val NOTICE_VISIBLE_MS = 3_000L
        private const val URL_TTL_MS = 10 * 60 * 1000L
        private const val UNPLAYABLE_TTL_MS = 15 * 60 * 1000L
        private const val UNPLAYABLE_MAX = 64
        private const val MAX_SHUFFLE_HISTORY = 64
        private const val FM_AHEAD = 4
        private const val FM_MAX_REQUESTS = 8
    }
}
