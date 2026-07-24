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
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.LyricRepository
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmPlaybackParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImageCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Service 内播放协调：队列、NCM URL 按需解析、歌词、模式。
 * 不负责通知 / FGS（由 MediaSessionService 独占）。
 */
@UnstableApi
class PlaylistCoordinator(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val stateStore: PlaybackStateStore,
    private val lyricRepository: LyricRepository,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val userClient: NcmUserClient = NcmUserClient(),
    private val onClearAndStopService: (() -> Unit)? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _spectrum = MutableStateFlow(AudioSpectrumBands.ZERO)
    val spectrum: StateFlow<AudioSpectrumBands> = _spectrum.asStateFlow()

    private val spectrumProcessor = SpectrumTapAudioProcessor { bands ->
        // StateFlow 线程安全；音频线程直写，少一帧主线程 post 延迟
        _spectrum.value = bands
    }

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean,
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessors(arrayOf(spectrumProcessor))
                .build()
        }
    }

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        setWakeMode(C.WAKE_MODE_NETWORK)
        pauseAtEndOfMediaItems = false
    }

    val player: Player = QueueAwarePlayer(exoPlayer)

    private val _ui = MutableStateFlow(PlaybackUiState())
    val ui: StateFlow<PlaybackUiState> = _ui.asStateFlow()

    private var playbackMode: PlaybackMode = PlaybackMode.ORDER

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
            _ui.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) persistSnapshot()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
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
        // 从快照恢复模式与进度，不联网冲队列
        stateStore.load()?.let { snap ->
            playbackMode = snap.playbackMode
            val mem = snap.currentTrack?.id?.let { lyricRepository.peekMemory(it) }
            _ui.value = if (mem != null && mem.isNotEmpty()) {
                snap.copy(lyricLines = mem)
            } else {
                snap
            }
            snap.currentTrack?.id?.let { songId ->
                scope.launch {
                    val cookie = sessionRepository.session.value?.cookie.orEmpty()
                    val lines = lyricRepository.loadBestEffort(songId, cookie)
                    if (_ui.value.currentTrack?.id == songId && lines.isNotEmpty()) {
                        _ui.update { it.copy(lyricLines = lines) }
                    }
                }
            }
            applyRepeatMode()
            refreshPeeksAndPrefetch()
        }
        applyRepeatMode()
        startTicker()
    }

    fun playQueue(
        tracks: List<TrackRow>,
        startIndex: Int,
        sourcePlaylistId: Long? = null,
        sourcePlaylistTitle: String? = null,
    ) {
        if (tracks.isEmpty()) return
        val idx = startIndex.coerceIn(0, tracks.lastIndex)
        cancelLoads()
        urlCache.clear()
        unplayableUntil.clear()
        preparedShuffleNext = null
        shuffleHistory.clear()
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
                sourcePlaylistId = sourcePlaylistId,
                sourcePlaylistTitle = sourcePlaylistTitle,
                playbackMode = playbackMode,
            )
        }
        persistSnapshot()
        loadAndPlayIndex(idx)
    }

    /** 在当前队列内跳转到指定索引（保留队列与随机历史策略）。 */
    fun playIndex(index: Int) {
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
        if (playlistId <= 0L || tracks.isEmpty()) return
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
        cancelLoads()
        urlCache.clear()
        unplayableUntil.clear()
        preparedShuffleNext = null
        shuffleHistory.clear()
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

    private var volumeFadeJob: Job? = null
    /** Exo 逻辑音量目标（与系统音量无关）；seek 渐起后回到此值。 */
    private var playbackVolume = 1f

    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        val from = exoPlayer.currentPosition.coerceAtLeast(0L)
        val jumped = kotlin.math.abs(target - from) > 400L
        exoPlayer.seekTo(target)
        _ui.update { it.copy(positionMs = target) }
        // 大幅跳转且正在播放：1s 声音渐起，避免位置硬切
        if (jumped && exoPlayer.isPlaying) {
            fadeVolumeIn(durationMs = 1_000L)
        }
    }

    private fun fadeVolumeIn(durationMs: Long) {
        volumeFadeJob?.cancel()
        val end = playbackVolume.coerceIn(0f, 1f)
        volumeFadeJob = scope.launch {
            exoPlayer.volume = 0f
            val steps = 24
            val stepDelay = (durationMs / steps).coerceAtLeast(1L)
            for (i in 1..steps) {
                if (!isActive) return@launch
                delay(stepDelay)
                // ease-out：前段更快可闻，尾段柔和贴合
                val t = i / steps.toFloat()
                val eased = 1f - (1f - t) * (1f - t)
                exoPlayer.volume = end * eased
            }
            exoPlayer.volume = end
        }
    }

    fun skipNext() {
        val i = _ui.value.index
        if (!_ui.value.hasQueue || i < 0) return
        // 单曲循环：手动下一首与列表模式相同；仅播完自动重播（见 onEnded）
        val ni = nextPlayableIndex(i) ?: return
        loadAndPlayIndex(ni, recordShuffleHistory = true)
    }

    fun skipPrevious() {
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
        playbackMode = when (playbackMode) {
            PlaybackMode.ORDER -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.ORDER
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
        cancelLoads()
        tickerJob?.cancel()
        volumeFadeJob?.cancel()
        volumeFadeJob = null
        exoPlayer.volume = playbackVolume
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
                val pos = exoPlayer.currentPosition
                _ui.update { it.copy(positionMs = pos) }
            }
        }
    }

    private fun onEnded() {
        when (playbackMode) {
            PlaybackMode.REPEAT_ONE -> {
                exoPlayer.seekTo(0L)
                exoPlayer.play()
            }
            PlaybackMode.ORDER, PlaybackMode.SHUFFLE -> {
                val ni = nextPlayableIndex(_ui.value.index)
                if (ni != null) {
                    loadAndPlayIndex(ni, recordShuffleHistory = true)
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

    /** 跳过预检失败曲目后的下一首（切歌 / 播完 / 预览一致）。 */
    private fun nextPlayableIndex(from: Int): Int? {
        val q = _ui.value.queue
        if (q.isEmpty()) return null
        return when (playbackMode) {
            PlaybackMode.ORDER, PlaybackMode.REPEAT_ONE -> {
                var i = from + 1
                while (i <= q.lastIndex) {
                    if (!isUnplayable(q[i].id)) return i
                    i++
                }
                null
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
        val next = nextPlayableIndex(i)?.let { ui.queue.getOrNull(it) }
        val prev = prevPlayableIndex(i)?.let { ui.queue.getOrNull(it) }
        _ui.update { it.copy(peekNextTrack = next, peekPrevTrack = prev) }
        pruneUrlCache(
            buildSet {
                ui.currentTrack?.id?.let { add(it) }
                next?.id?.let { add(it) }
                prev?.id?.let { add(it) }
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
                val neighbors = listOfNotNull(ui.peekNextTrack, ui.peekPrevTrack)
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
                            if (lyricRepository.peekMemory(t.id) == null) {
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
                    val url = resolvePlayUrl(t.id, cookie)
                    if (url.isNullOrBlank()) {
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
    ) {
        val track = _ui.value.queue.getOrNull(idx) ?: return
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
        val cachedLyrics = lyricRepository.peekMemory(track.id)
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
                lyricLines = cachedLyrics.orEmpty(),
                isPlaying = false,
                playWhenReady = false,
            )
        }
        refreshPeeksAndPrefetch()
        val loadGen = track.id
        loadJob = scope.launch {
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            try {
                // 磁盘歌词优先填上，避免等网络
                if (_ui.value.lyricLines.isEmpty()) {
                    val diskOrNet = lyricRepository.loadBestEffort(track.id, cookie)
                    if (diskOrNet.isNotEmpty() && _ui.value.currentTrack?.id == track.id) {
                        _ui.update { it.copy(lyricLines = diskOrNet) }
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
                val url = cached ?: resolvePlayUrl(track.id, cookie)?.also {
                    urlCache[track.id] = CachedUrl(it, System.currentTimeMillis())
                }
                if (_ui.value.currentTrack?.id != loadGen) return@launch
                if (url.isNullOrBlank()) {
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
                    exoPlayer.playWhenReady = true
                }
                if (_ui.value.currentTrack?.id != loadGen) return@launch
                persistSnapshot()
                loadLyricsAsync(track.id, cookie)
            } catch (e: Exception) {
                if (_ui.value.currentTrack?.id != loadGen) return@launch
                Log.w(TAG, "loadAndPlayIndex failed", e)
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
                val ni = nextPlayableIndex(failedIndex)
                if (ni != null) {
                    loadAndPlayIndex(ni, recordShuffleHistory = false)
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

    private fun buildMediaItem(track: TrackRow, url: String): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(track.name)
            .setArtist(track.artists)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(url)
            .setMediaMetadata(meta)
            .build()
    }

    private fun loadLyricsAsync(songId: Long, cookie: String) {
        lyricJob?.cancel()
        lyricJob = scope.launch {
            val lines = lyricRepository.loadBestEffort(songId, cookie)
            if (_ui.value.currentTrack?.id == songId) {
                _ui.update { it.copy(lyricLines = lines) }
            }
        }
    }

    private fun cancelLoads(keepPrefetch: Boolean = false) {
        loadJob?.cancel()
        lyricJob?.cancel()
        errorRetryJob?.cancel()
        if (!keepPrefetch) prefetchJob?.cancel()
        volumeFadeJob?.cancel()
        volumeFadeJob = null
        exoPlayer.volume = playbackVolume
    }

    private fun persistSnapshot() {
        val s = _ui.value
        if (s.hasQueue && s.queue.isNotEmpty()) stateStore.save(s)
    }

    private suspend fun resolvePlayUrl(trackId: Long, cookie: String): String? {
        val primary = withContext(Dispatchers.IO) { userClient.songUrl(listOf(trackId), cookie) }
        NcmPlaybackParse.songUrlForId(primary, trackId)?.let { return it }
        val v1 = withContext(Dispatchers.IO) {
            userClient.songUrlV1(listOf(trackId), cookie, level = "exhigh")
        }
        return NcmPlaybackParse.songUrlForId(v1, trackId)
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
        return nextPlayableIndex(i) != null
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
    }
}
