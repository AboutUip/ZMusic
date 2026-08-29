package com.kite.zmusic.playback

import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.LrcParser
import com.kite.zmusic.data.LyricPack
import com.kite.zmusic.data.NcmPlaybackParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlayUrlResolver
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
    private val downloadAccelPath: (TrackRow) -> String? = { it.localAudioUri },
    private val realtimeCache: () -> Boolean = { false },
    private val realtimeCacheMb: () -> Int = { 512 },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _ui = MutableStateFlow(PlaybackUiState())
    val ui: StateFlow<PlaybackUiState> = _ui.asStateFlow()
    var musicWillPlay: (() -> Unit)? = null
    var mvWillPlay: (() -> Unit)? = null
    private var ticker: Job? = null
    private val shuffleHistory = ArrayDeque<Int>()
    private var prefetch: PrefetchedUrl? = null

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
                _ui.update {
                    it.copy(
                        isPlaying = playing,
                        positionMs = engine.positionMs(),
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
        val next = nextIndex(s) ?: return
        _ui.update { it.copy(index = next, playWhenReady = true, loadPending = true) }
        shuffleHistory.addLast(next)
        scope.launch { startCurrent() }
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
        _ui.update { it.copy(index = prev, playWhenReady = true, loadPending = true) }
        scope.launch { startCurrent() }
    }

    fun stopAndClear() {
        engine.stop()
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
        val s = _ui.value
        val track = s.currentTrack ?: return
        val local = downloadAccelPath(track)
        val url = local ?: run {
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
        if (realtimeCache() && url.startsWith("http")) {
            com.kite.zmusic.data.LocalLibrary.startDownload(track.id, url)
            com.kite.zmusic.data.LocalLibrary.trimToMaxMb(realtimeCacheMb())
        }
        engine.play(url, 0L)
        _ui.update {
            it.copy(
                loadPending = false,
                isPlaying = true,
                playWhenReady = true,
                durationMs = track.durationMs,
                positionMs = 0L,
            )
        }
        loadLyrics(track.id)
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

    private fun maybePrefetch(s: PlaybackUiState) {
        if (!s.isPlaying || s.durationMs <= 0L) return
        if (s.durationMs - s.positionMs > 30_000L) return
        val next = nextIndex(s) ?: return
        val track = s.queue.getOrNull(next) ?: return
        val hit = prefetch
        if (hit != null && hit.trackId == track.id && System.currentTimeMillis() - hit.atMs < 120_000L) {
            return
        }
        scope.launch {
            val local = downloadAccelPath(track)
            val url = local ?: run {
                val c = cookie().orEmpty()
                if (c.isEmpty()) return@launch
                PlayUrlResolver.resolve(userClient, track.id, c, quality())
            } ?: return@launch
            prefetch = PrefetchedUrl(track.id, url, System.currentTimeMillis())
            if (realtimeCache() && url.startsWith("http")) {
                com.kite.zmusic.data.LocalLibrary.startDownload(track.id, url)
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
    fun stopAndClear() = coordinator.stopAndClear()
}
