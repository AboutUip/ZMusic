package com.kite.zmusic.playback

import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricPack
import com.kite.zmusic.data.TrackRow

data class PlaybackNotice(
    val token: Long,
    val message: String,
)

data class PlaybackUiState(
    val queue: List<TrackRow> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val playbackMode: PlaybackMode = PlaybackMode.ORDER,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lyricLines: List<LrcLine> = emptyList(),
    val translatedLyricLines: List<LrcLine> = emptyList(),
    val wordLyricLines: List<LrcLine> = emptyList(),
    val translatedWordLyricLines: List<LrcLine> = emptyList(),
    val error: String? = null,
    val loadPending: Boolean = false,
    val hasQueue: Boolean = false,
    val sourcePlaylistId: Long? = null,
    val sourcePlaylistTitle: String? = null,
    val fmActive: Boolean = false,
    val intelligenceActive: Boolean = false,
    val peekNextTrack: TrackRow? = null,
    val peekPrevTrack: TrackRow? = null,
    val notice: PlaybackNotice? = null,
    val vinylHue: Float = 0.02f,
    val lyricAlignVinyl: Boolean = false,
) {
    val currentTrack: TrackRow?
        get() = queue.getOrNull(index)

    val radioActive: Boolean
        get() = fmActive || intelligenceActive

    fun withLyricPack(pack: LyricPack): PlaybackUiState = copy(
        lyricLines = pack.original,
        translatedLyricLines = pack.translated,
        wordLyricLines = pack.wordOriginal,
        translatedWordLyricLines = pack.translatedWordLyricLines,
    )
}

enum class PlaybackMode {
    ORDER,
    REPEAT_ONE,
    SHUFFLE,
    ;

    fun next(): PlaybackMode = when (this) {
        ORDER -> REPEAT_ONE
        REPEAT_ONE -> SHUFFLE
        SHUFFLE -> ORDER
    }
}

interface PlaybackEngine {
    fun play(url: String, startMs: Long = 0L)
    fun pause()
    fun resume()
    fun seek(ms: Long)
    fun stop()
    fun setVolume(volume: Float)
    fun positionMs(): Long
    fun durationMs(): Long
    fun isPlaying(): Boolean
    fun setOnEnded(block: () -> Unit)
    fun pump() {}
}

interface MprisExporter {
    fun publish(state: PlaybackUiState)
    fun bind(onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit)
    fun close()
}
