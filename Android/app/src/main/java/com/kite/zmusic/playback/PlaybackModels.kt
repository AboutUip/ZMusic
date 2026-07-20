package com.kite.zmusic.playback

import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.TrackRow

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
    /** 有队列且未主动清空（UI：迷你条可见）。不驱动 FGS。 */
    val hasQueue: Boolean = false,
    val sourcePlaylistId: Long? = null,
    val sourcePlaylistTitle: String? = null,
    /** 手势/预览用：与真实 skipNext/Prev 目标一致（尤其随机模式） */
    val peekNextTrack: TrackRow? = null,
    val peekPrevTrack: TrackRow? = null,
) {
    val currentTrack: TrackRow?
        get() = queue.getOrNull(index)
}

enum class PlaybackMode {
    ORDER,
    REPEAT_ONE,
    SHUFFLE,
}
