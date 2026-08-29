package com.kite.zmusic.playback

import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.LrcParser
import com.kite.zmusic.data.LocalLibrary
import com.kite.zmusic.data.LyricPack
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmPlaybackParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlayUrlResolver
import com.kite.zmusic.data.RealtimeCacheMode
import com.kite.zmusic.data.RealtimeCachePrefs
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.YrcParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class PlaylistCoordinator(
    private val userClient: NcmUserClient,
    private val engine: PlaybackEngine,
    private val mpris: MprisExporter,
    private val cookie: () -> String?,
    private val quality: () -> AudioQuality,
    private val persistentPlayback: () -> Boolean,
    private val downloadAccel: () -> Boolean = { false },
    private val cachePrefs: () -> RealtimeCachePrefs = { RealtimeCachePrefs() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _ui = MutableStateFlow(PlaybackUiState())
    val ui: StateFlow<PlaybackUiState> = _ui.asStateFlow()
    var musicWillPlay: (() -> Unit)? = null
    var mvWillPlay: (() -> Unit)? = null
    private var ticker: Job? = null
    private val shuffleHistory = ArrayDeque<Int>()
    private var prefetch: PrefetchedUrl? = null
    private var radioFetch: Job? = null
    private var radioFetchedAt = 0L
    private var sampleTrackId = 0L
    private var sampleListened = 0L
    private var sampleDuration = 0L
    private var sampleLastPos = -1L
    private var sampleUrl: String? = null
    private var accelNoticeId = 0L

    private data class PrefetchedUrl(val trackId: Long, val url: String, val atMs: Long)

    init {
        engine.setOnEnded { scope.launch { onEnded() } }
        mpris.bind(
            onPlayPause = { togglePlay() },
            onNext = { skipNext() },
            onPrev = { skipPrev() },
        )
        ticker = scope.launch {
            while (isActive) {
                delay(200)
                engine.pump()
                val playing = engine.isPlaying()
                val pos = engine.positionMs()
                if (playing && sampleTrackId > 0L && sampleLastPos >= 0L && pos > sampleLastPos) {
                    sampleListened += pos - sampleLastPos
                }
                sampleLastPos = pos
                val cache = cachePrefs()
                if (
                    playing &&
                    cache.enabled &&
                    cache.mode == RealtimeCacheMode.Cautious &&
                    sampleTrackId > 0L &&
                    sampleDuration > 0L &&
                    sampleListened * 10L >= sampleDuration * 6L
                ) {
                    sampleUrl?.let { LocalLibrary.startDownload(sampleTrackId, it, cache.maxMb) }
                }
                _ui.update {
                    it.copy(
                        isPlaying = playing,
                        positionMs = pos,
                        durationMs = engine.durationMs().takeIf { d -> d > 0 } ?: it.durationMs,
                    )
                }
                mpris.publish(_ui.value)
                maybePrefetch(_ui.value)
            }
        }
    }

    fun playQueue(
        tracks: List<TrackRow>,
        index: Int,
        playlistId: Long? = null,
        playlistTitle: String? = null,
        fm: Boolean = false,
        intelligence: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        val i = index.coerceIn(0, tracks.lastIndex)
        musicWillPlay?.invoke()
        _ui.update {
            it.copy(
                queue = tracks,
                index = i,
                hasQueue = true,
                playWhenReady = true,
                loadPending = true,
                sourcePlaylistId = playlistId,
                sourcePlaylistTitle = playlistTitle,
                fmActive = fm,
                intelligenceActive = intelligence,
                playbackMode = if (fm || intelligence) PlaybackMode.ORDER else it.playbackMode,
            )
        }
        shuffleHistory.clear()
        shuffleHistory.add(i)
        scope.launch { startCurrent() }
    }

    fun togglePlay() {
        val s = _ui.value
        if (!s.hasQueue) return
        if (engine.isPlaying()) {
            engine.pause()
            _ui.update { it.copy(isPlaying = false, playWhenReady = false) }
        } else {
            engine.resume()
            _ui.update { it.copy(playWhenReady = true) }
        }
    }

    fun seek(ms: Long) {
        engine.seek(ms)
        _ui.update { it.copy(positionMs = ms) }
    }

    fun cycleMode() {
        if (_ui.value.radioActive) return
        _ui.update { it.copy(playbackMode = it.playbackMode.next()) }
    }

    fun skipNext() {
        val s = _ui.value
        val next = nextIndex(s)
        if (next != null) {
            _ui.update { it.copy(index = next, playWhenReady = true, loadPending = true, skipDir = 1, skipSeq = it.skipSeq + 1) }
            shuffleHistory.addLast(next)
            if (s.radioActive) ensureRadioLookahead()
            scope.launch { startCurrent() }
            return
        }
        if (!s.radioActive) return
        scope.launch {
            appendRadioBatch()
            val n = nextIndex(_ui.value) ?: return@launch
            _ui.update { it.copy(index = n, playWhenReady = true, loadPending = true, skipDir = 1, skipSeq = it.skipSeq + 1) }
            shuffleHistory.addLast(n)
            startCurrent()
        }
    }

    fun skipPrev() {
        val s = _ui.value
        if (s.positionMs > 3000L) {
            seek(0)
            return
        }
        val prev = when (s.playbackMode) {
            PlaybackMode.SHUFFLE -> shuffleHistory.removeLastOrNull()?.let {
                shuffleHistory.lastOrNull()
            } ?: ((s.index - 1).takeIf { it >= 0 } ?: s.queue.lastIndex)
            else -> (s.index - 1).takeIf { it >= 0 } ?: s.queue.lastIndex
        }
        _ui.update { it.copy(index = prev, playWhenReady = true, loadPending = true, skipDir = -1, skipSeq = it.skipSeq + 1) }
        scope.launch { startCurrent() }
    }

    fun stopAndClear() {
        engine.stop()
        flushSample()
        _ui.value = PlaybackUiState()
        mpris.publish(_ui.value)
    }

    fun duckForOthers(active: Boolean) {
        if (!persistentPlayback()) {
            if (active) engine.pause()
            return
        }
        engine.setVolume(if (active) 0.35f else 1f)
    }

    fun stopForMv() {
        engine.pause()
        _ui.update { it.copy(isPlaying = false, playWhenReady = false) }
    }

    private suspend fun startCurrent() {
        flushSample()
        val s = _ui.value
        val track = s.currentTrack ?: return
        val cache = cachePrefs()
        val accel = downloadAccel()
        val hint = track.localAudioUri?.takeIf { it.isNotBlank() }
        val realtimeUri = LocalLibrary.playUri(track.id, null, cache.enabled, accel = false)
        val accelUri = LocalLibrary.playUri(track.id, null, realtime = false, accel)
        val local = hint ?: realtimeUri ?: accelUri
        val prefetched = prefetch?.takeIf { it.trackId == track.id }?.url
        val url = local ?: prefetched ?: run {
            val c = cookie().orEmpty()
            if (c.isEmpty()) {
                notice("未登录")
                return
            }
            PlayUrlResolver.resolve(userClient, track.id, c, quality())
        }
        if (url.isNullOrBlank()) {
            notice("暂时无法播放")
            _ui.update { it.copy(loadPending = false) }
            return
        }
        if (local != null && local == accelUri && hint == null && realtimeUri == null && accelNoticeId != track.id) {
            accelNoticeId = track.id
            notice("此歌曲已进行缓存加速")
        }
        if (cache.liveDownload && url.startsWith("http")) {
            LocalLibrary.startDownload(track.id, url, cache.maxMb)
        }
        engine.play(url, 0L)
        sampleTrackId = track.id
        sampleListened = 0L
        sampleDuration = track.durationMs
        sampleLastPos = 0L
        sampleUrl = url.takeIf { it.startsWith("http") }
        _ui.update {
            it.copy(
                loadPending = false,
                isPlaying = true,
                playWhenReady = true,
                durationMs = track.durationMs,
                positionMs = 0L,
            )
        }
        refreshPeeks()
        refreshLike(track.id)
        loadLyrics(track.id)
    }

    fun playAt(index: Int) {
        val s = _ui.value
        if (index !in s.queue.indices) return
        _ui.update { it.copy(index = index, playWhenReady = true, loadPending = true, skipDir = if (index > s.index) 1 else -1, skipSeq = it.skipSeq + 1) }
        scope.launch { startCurrent() }
    }

    fun insertNext(tracks: List<TrackRow>) {
        if (tracks.isEmpty()) return
        _ui.update { s ->
            val q = s.queue.toMutableList()
            val insertAt = (s.index + 1).coerceIn(0, q.size)
            tracks.forEach { t ->
                if (q.none { it.id == t.id }) q.add(insertAt, t)
            }
            s.copy(queue = q, hasQueue = q.isNotEmpty())
        }
        refreshPeeks()
        notice("已加入下一首播放")
    }

    fun toggleLike() {
        val track = _ui.value.currentTrack ?: return
        val c = cookie().orEmpty()
        if (c.isBlank()) {
            notice("未登录")
            return
        }
        val next = !_ui.value.trackLiked
        scope.launch {
            val json = runCatching { userClient.likeSong(track.id, next, c) }.getOrNull()
            val ok = json != null && NcmJson.apiCode(json) == 200
            if (ok) {
                _ui.update { it.copy(trackLiked = next) }
                notice(if (next) "已喜欢" else "已取消喜欢")
            } else {
                notice(json?.let { NcmJson.userFacingMessage(it, "操作失败") } ?: "操作失败")
            }
        }
    }

    private fun refreshPeeks() {
        val s = _ui.value
        val next = nextIndex(s)
        val prevIdx = when {
            s.queue.isEmpty() -> null
            s.index > 0 -> s.index - 1
            else -> s.queue.lastIndex.takeIf { it != s.index }
        }
        _ui.update {
            it.copy(
                peekNextTrack = next?.let { n -> s.queue.getOrNull(n) },
                peekPrevTrack = prevIdx?.let { p -> s.queue.getOrNull(p) },
            )
        }
    }

    private fun refreshLike(trackId: Long) {
        val c = cookie().orEmpty()
        if (c.isBlank() || trackId <= 0L) {
            _ui.update { it.copy(trackLiked = false) }
            return
        }
        scope.launch {
            val liked = runCatching {
                NcmLibraryParse.isTrackLiked(userClient.songLikeCheck(listOf(trackId), c), trackId)
            }.getOrDefault(false)
            _ui.update { it.copy(trackLiked = liked) }
        }
    }

    private suspend fun loadLyrics(songId: Long) {
        val c = cookie().orEmpty()
        val pack = runCatching {
            val json = userClient.lyric(songId, c)
            val original = NcmPlaybackParse.lrcText(json)?.let(LrcParser::parse).orEmpty()
            val translated = NcmPlaybackParse.translatedLrcText(json)?.let(LrcParser::parse).orEmpty()
            val word = NcmPlaybackParse.yrcText(json)?.let(YrcParser::parse).orEmpty()
            val wordTr = NcmPlaybackParse.ytlrcText(json)?.let(YrcParser::parse).orEmpty()
            LyricPack(
                original = original.ifEmpty { word.map { it.copy(words = emptyList()) } },
                translated = translated,
                wordOriginal = word,
                translatedWordLyricLines = wordTr,
                translationResolved = true,
            )
        }.getOrDefault(LyricPack.Empty)
        _ui.update { it.withLyricPack(pack) }
    }

    private fun onEnded() {
        val s = _ui.value
        when (s.playbackMode) {
            PlaybackMode.REPEAT_ONE -> scope.launch { startCurrent() }
            else -> skipNext()
        }
    }

    private fun flushSample() {
        val id = sampleTrackId
        val listened = sampleListened
        val duration = sampleDuration
        val url = sampleUrl
        sampleTrackId = 0L
        sampleListened = 0L
        sampleDuration = 0L
        sampleLastPos = -1L
        sampleUrl = null
        if (id <= 0L) return
        val cache = cachePrefs()
        if (!cache.enabled) return
        LocalLibrary.finishListen(id, listened, duration, cache.mode, url, cache.maxMb)
        LocalLibrary.trimToMaxMb(cache.maxMb)
    }

    private fun nextIndex(s: PlaybackUiState): Int? {
        if (s.queue.isEmpty()) return null
        return when (s.playbackMode) {
            PlaybackMode.REPEAT_ONE, PlaybackMode.ORDER -> {
                val n = s.index + 1
                if (n <= s.queue.lastIndex) n else if (s.radioActive) null else 0
            }
            PlaybackMode.SHUFFLE -> {
                if (s.queue.size == 1) 0
                else {
                    var pick: Int
                    do {
                        pick = Random.nextInt(s.queue.size)
                    } while (pick == s.index && s.queue.size > 1)
                    pick
                }
            }
        }
    }

    private fun ensureRadioLookahead() {
        val s = _ui.value
        if (!s.radioActive) return
        if (s.index < s.queue.lastIndex - 1) return
        if (radioFetch?.isActive == true) return
        if (System.currentTimeMillis() - radioFetchedAt < 8_000L) return
        radioFetch = scope.launch { appendRadioBatch() }
    }

    private suspend fun appendRadioBatch() {
        val s = _ui.value
        if (!s.radioActive) return
        val c = cookie().orEmpty()
        if (c.isBlank()) return
        val extra = runCatching {
            if (s.intelligenceActive) {
                val seed = s.currentTrack?.id ?: return
                val pid = s.sourcePlaylistId ?: 0L
                NcmHomeParse.intelligenceTracks(
                    userClient.intelligenceList(seed, c, playlistId = pid, startSongId = seed),
                )
            } else {
                NcmHomeParse.personalFmTracks(userClient.personalFm(c))
            }
        }.getOrDefault(emptyList())
        radioFetchedAt = System.currentTimeMillis()
        if (extra.isEmpty()) return
        _ui.update { cur ->
            val seen = cur.queue.mapTo(HashSet()) { it.id }
            val add = extra.filter { it.id > 0L && seen.add(it.id) }
            if (add.isEmpty()) cur else cur.copy(queue = cur.queue + add, hasQueue = true)
        }
        refreshPeeks()
    }

    private fun maybePrefetch(s: PlaybackUiState) {
        if (s.radioActive) ensureRadioLookahead()
        if (!s.isPlaying || s.durationMs <= 0L) return
        if (s.durationMs - s.positionMs > 30_000L) return
        val next = nextIndex(s) ?: return
        val track = s.queue.getOrNull(next) ?: return
        val hit = prefetch
        if (hit != null && hit.trackId == track.id && System.currentTimeMillis() - hit.atMs < 120_000L) {
            return
        }
        scope.launch {
            val cache = cachePrefs()
            val local = LocalLibrary.playUri(track.id, track.localAudioUri, cache.enabled, downloadAccel())
            val url = local ?: run {
                val c = cookie().orEmpty()
                if (c.isEmpty()) return@launch
                PlayUrlResolver.resolve(userClient, track.id, c, quality())
            } ?: return@launch
            prefetch = PrefetchedUrl(track.id, url, System.currentTimeMillis())
            if (cache.liveDownload && url.startsWith("http")) {
                LocalLibrary.startDownload(track.id, url, cache.maxMb)
            }
        }
    }

    private fun notice(msg: String) {
        _ui.update {
            it.copy(notice = PlaybackNotice(System.currentTimeMillis(), msg))
        }
    }

    fun close() {
        ticker?.cancel()
        flushSample()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        engine.stop()
        mpris.close()
    }
}

class PlaybackBridge(
    val coordinator: PlaylistCoordinator,
) {
    val ui: StateFlow<PlaybackUiState> = coordinator.ui

    fun playQueue(
        tracks: List<TrackRow>,
        index: Int,
        playlistId: Long? = null,
        playlistTitle: String? = null,
        fm: Boolean = false,
        intelligence: Boolean = false,
    ) = coordinator.playQueue(tracks, index, playlistId, playlistTitle, fm, intelligence)

    fun togglePlay() = coordinator.togglePlay()
    fun seek(ms: Long) = coordinator.seek(ms)
    fun cycleMode() = coordinator.cycleMode()
    fun skipNext() = coordinator.skipNext()
    fun skipPrev() = coordinator.skipPrev()
    fun playAt(index: Int) = coordinator.playAt(index)
    fun insertNext(tracks: List<TrackRow>) = coordinator.insertNext(tracks)
    fun toggleLike() = coordinator.toggleLike()
    fun stopAndClear() = coordinator.stopAndClear()
}
