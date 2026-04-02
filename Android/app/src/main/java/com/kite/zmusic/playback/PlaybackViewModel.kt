package com.kite.zmusic.playback

import android.app.Application
import android.content.Context
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LrcParser
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmPlaybackParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.kite.zmusic.data.TrackRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.SharedPreferences
import java.io.File
import kotlin.random.Random

data class PlaybackUiState(
    val queue: List<TrackRow> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val playbackMode: PlaybackMode = PlaybackMode.ORDER,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lyricLines: List<LrcLine> = emptyList(),
    val error: String? = null,
    val loadPending: Boolean = false,
    /** 当前队列来自的歌单（从「我的」点播时写入，用于联动与高亮） */
    val sourcePlaylistId: Long? = null,
    val sourcePlaylistTitle: String? = null,
) {
    val currentTrack: TrackRow?
        get() = queue.getOrNull(index)
}

enum class PlaybackMode {
    /** 顺序播放（队列播放完毕停止） */
    ORDER,
    /** 单曲循环（仅当前曲目重复播放） */
    REPEAT_ONE,
    /** 随机播放（结束/下一首时从队列随机取一首） */
    SHUFFLE,
}

class PlaybackViewModelFactory(
    private val application: Application,
    private val sessionRepository: SessionRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaybackViewModel::class.java)) {
            return PlaybackViewModel(application, sessionRepository) as T
        }
        error("Unknown ViewModel class")
    }
}

class PlaybackViewModel(
    application: Application,
    private val sessionRepository: SessionRepository,
    private val userClient: NcmUserClient = NcmUserClient(),
) : AndroidViewModel(application) {

    private val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            val d = duration
                            _ui.update {
                                it.copy(
                                    buffering = false,
                                    durationMs = if (d > 0 && d != C.TIME_UNSET) d else it.durationMs,
                                )
                            }
                        }
                        Player.STATE_BUFFERING -> _ui.update { it.copy(buffering = true) }
                        Player.STATE_ENDED -> onEnded()
                        Player.STATE_IDLE -> _ui.update { it.copy(buffering = false) }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _ui.update { it.copy(isPlaying = isPlaying) }
                }
            },
        )
    }

    private val _ui = MutableStateFlow(PlaybackUiState())
    val ui: StateFlow<PlaybackUiState> = _ui.asStateFlow()

    private val prefs: SharedPreferences = createPrefs(application.applicationContext)
    private var playbackMode: PlaybackMode = readMode()

    // 歌词缓存：内存 + 简单磁盘（避免重复拉取/解析 LRC）
    private val lyricMemoryCache = object : LruCache<String, List<LrcLine>>(30) {}
    private val lyricCacheDir = File(application.cacheDir, "zmusic_lyrics_cache").apply { mkdirs() }

    private fun lyricCacheKey(songId: Long, cookie: String): String = "${songId}_${cookie.hashCode()}"

    private suspend fun loadLyricsLinesFromCache(songId: Long, cookie: String): List<LrcLine>? {
        val key = lyricCacheKey(songId, cookie)
        lyricMemoryCache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(lyricCacheDir, "$key.lrc")
                if (!file.exists()) return@runCatching null
                val raw = file.readText(Charsets.UTF_8)
                val lines = LrcParser.parse(raw)
                lyricMemoryCache.put(key, lines)
                lines
            }.getOrNull()
        }
    }

    private suspend fun saveLyricsToCache(songId: Long, cookie: String, raw: String, lines: List<LrcLine>) {
        val key = lyricCacheKey(songId, cookie)
        lyricMemoryCache.put(key, lines)
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(lyricCacheDir, "$key.lrc")
                file.writeText(raw, Charsets.UTF_8)
            }
        }
    }

    private var loadJob: Job? = null
    private var tickerJob: Job? = null

    init {
        startTicker()
        _ui.update { it.copy(playbackMode = playbackMode) }
        restoreLastTrack()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(90)
                val st = player.playbackState
                if (st != Player.STATE_IDLE && st != Player.STATE_ENDED) {
                    val p = player.currentPosition
                    _ui.update { it.copy(positionMs = p) }
                }
            }
        }
    }

    private fun onEnded() {
        val q = _ui.value.queue
        val i = _ui.value.index
        if (q.isEmpty() || i < 0) return

        when (playbackMode) {
            PlaybackMode.ORDER -> {
                if (i in 0 until q.lastIndex) {
                    _ui.update { it.copy(index = i + 1, loadPending = true, lyricLines = emptyList()) }
                    loadAndPlayIndex(i + 1)
                } else {
                    player.pause()
                    _ui.update { it.copy(isPlaying = false, buffering = false, loadPending = false) }
                }
            }
            PlaybackMode.REPEAT_ONE -> {
                // 单曲循环：不换曲，只重播
                player.seekTo(0L)
                player.play()
                _ui.update { it.copy(positionMs = 0L, isPlaying = true, buffering = false, loadPending = false) }
            }
            PlaybackMode.SHUFFLE -> {
                val next = pickShuffleIndex(currentIndex = i, size = q.size)
                _ui.update { it.copy(index = next, loadPending = true, lyricLines = emptyList()) }
                loadAndPlayIndex(next)
            }
        }
    }

    fun playQueue(
        tracks: List<TrackRow>,
        startIndex: Int,
        sourcePlaylistId: Long? = null,
        sourcePlaylistTitle: String? = null,
    ) {
        if (tracks.isEmpty()) return
        val idx = startIndex.coerceIn(0, tracks.lastIndex)
        loadJob?.cancel()
        _ui.update {
            it.copy(
                queue = tracks,
                index = idx,
                error = null,
                loadPending = true,
                lyricLines = emptyList(),
                sourcePlaylistId = sourcePlaylistId,
                sourcePlaylistTitle = sourcePlaylistTitle,
            )
        }
        loadAndPlayIndex(idx)
    }

    fun clearQueue() {
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _ui.value = PlaybackUiState(playbackMode = playbackMode)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.mediaItemCount == 0) {
                val i = _ui.value.index
                if (i >= 0) loadAndPlayIndex(i)
            } else {
                player.play()
            }
        }
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms.coerceAtLeast(0L))
        _ui.update { it.copy(positionMs = ms) }
    }

    fun skipNext() {
        val q = _ui.value.queue
        val i = _ui.value.index
        if (q.isEmpty() || i < 0) return
        when (playbackMode) {
            PlaybackMode.ORDER -> {
                if (i < q.lastIndex) {
                    val ni = i + 1
                    _ui.update { it.copy(index = ni, loadPending = true, lyricLines = emptyList()) }
                    loadAndPlayIndex(ni)
                }
            }
            PlaybackMode.REPEAT_ONE -> {
                player.seekTo(0L)
                player.play()
                _ui.update { it.copy(positionMs = 0L, isPlaying = true, buffering = false, loadPending = false) }
            }
            PlaybackMode.SHUFFLE -> {
                val ni = pickShuffleIndex(currentIndex = i, size = q.size)
                _ui.update { it.copy(index = ni, loadPending = true, lyricLines = emptyList()) }
                loadAndPlayIndex(ni)
            }
        }
    }

    fun skipPrevious() {
        val i = _ui.value.index
        val q = _ui.value.queue
        if (q.isEmpty() || i < 0) return
        when (playbackMode) {
            PlaybackMode.ORDER -> {
                if (i > 0) {
                    val pi = i - 1
                    _ui.update { it.copy(index = pi, loadPending = true, lyricLines = emptyList()) }
                    loadAndPlayIndex(pi)
                } else {
                    seekTo(0L)
                }
            }
            PlaybackMode.REPEAT_ONE -> {
                player.seekTo(0L)
                player.play()
                _ui.update { it.copy(positionMs = 0L, isPlaying = true, buffering = false, loadPending = false) }
            }
            PlaybackMode.SHUFFLE -> {
                val pi = pickShuffleIndex(currentIndex = i, size = q.size)
                _ui.update { it.copy(index = pi, loadPending = true, lyricLines = emptyList()) }
                loadAndPlayIndex(pi)
            }
        }
    }

    private fun loadAndPlayIndex(idx: Int) {
        val track = _ui.value.queue.getOrNull(idx) ?: return
        persistLastTrackId(track.id)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(error = null) }
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            try {
                val urlJson = withContext(Dispatchers.IO) {
                    userClient.songUrl(listOf(track.id), cookie)
                }
                val url = NcmPlaybackParse.songUrlForId(urlJson, track.id)
                if (url.isNullOrBlank()) {
                    _ui.update {
                        it.copy(
                            error = "暂无播放链接",
                            loadPending = false,
                            buffering = false,
                        )
                    }
                    return@launch
                }

                // 歌词：优先读缓存，没命中才请求解析
                val cachedLines = loadLyricsLinesFromCache(songId = track.id, cookie = cookie)
                if (cachedLines != null) {
                    _ui.update {
                        it.copy(
                            lyricLines = cachedLines,
                            error = null,
                            loadPending = false,
                        )
                    }
                } else {
                    val lyricJson = withContext(Dispatchers.IO) {
                        userClient.lyric(track.id, cookie)
                    }
                    val raw = NcmPlaybackParse.lrcText(lyricJson)
                    val lines = raw?.let(LrcParser::parse).orEmpty()
                    if (raw != null) {
                        saveLyricsToCache(songId = track.id, cookie = cookie, raw = raw, lines = lines)
                    }
                    _ui.update {
                        it.copy(
                            lyricLines = lines,
                            error = null,
                            loadPending = false,
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                    player.playWhenReady = true
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        error = e.message ?: "加载失败",
                        loadPending = false,
                        buffering = false,
                    )
                }
            }
        }
    }

    fun cyclePlaybackMode() {
        playbackMode = when (playbackMode) {
            PlaybackMode.ORDER -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.ORDER
        }
        persistMode(playbackMode)
        _ui.update { it.copy(playbackMode = playbackMode) }
    }

    private fun pickShuffleIndex(currentIndex: Int, size: Int): Int {
        if (size <= 1) return currentIndex
        var next = currentIndex
        while (next == currentIndex) {
            next = Random.nextInt(0, size)
        }
        return next
    }

    private fun restoreLastTrack() {
        val lastId = prefs.getLong(KEY_LAST_TRACK_ID, -1L)
        if (lastId <= 0L) return
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isBlank()) return

        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    userClient.songDetail(listOf(lastId), cookie)
                }
                val tracks = NcmLibraryParse.tracksFromSongDetail(json)
                val t = tracks.firstOrNull() ?: return@launch
                _ui.update {
                    it.copy(
                        queue = listOf(t),
                        index = 0,
                        isPlaying = false,
                        buffering = false,
                        positionMs = 0L,
                        durationMs = t.durationMs,
                        lyricLines = emptyList(),
                        error = null,
                        loadPending = false,
                        sourcePlaylistId = null,
                        sourcePlaylistTitle = null,
                    )
                }
            } catch (_: Exception) {
                // ignore restore failure
            }
        }
    }

    private fun persistLastTrackId(id: Long) {
        prefs.edit().putLong(KEY_LAST_TRACK_ID, id).apply()
    }

    private fun readMode(): PlaybackMode {
        return when (prefs.getInt(KEY_MODE, KEY_MODE_ORDER)) {
            KEY_MODE_REPEAT_ONE -> PlaybackMode.REPEAT_ONE
            KEY_MODE_SHUFFLE -> PlaybackMode.SHUFFLE
            else -> PlaybackMode.ORDER
        }
    }

    private fun persistMode(mode: PlaybackMode) {
        val v = when (mode) {
            PlaybackMode.ORDER -> KEY_MODE_ORDER
            PlaybackMode.REPEAT_ONE -> KEY_MODE_REPEAT_ONE
            PlaybackMode.SHUFFLE -> KEY_MODE_SHUFFLE
        }
        prefs.edit().putInt(KEY_MODE, v).apply()
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        loadJob?.cancel()
        player.release()
    }

    companion object {
        private const val PREFS_NAME = "zmusic_playback"
        private const val KEY_LAST_TRACK_ID = "last_track_id"
        private const val KEY_MODE = "playback_mode"
        private const val KEY_MODE_ORDER = 0
        private const val KEY_MODE_REPEAT_ONE = 1
        private const val KEY_MODE_SHUFFLE = 2

        private fun createPrefs(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }
}
