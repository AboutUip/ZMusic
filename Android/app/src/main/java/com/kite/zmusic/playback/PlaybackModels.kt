package com.kite.zmusic.playback

import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.TrackRow

/** 播放页非阻塞短通知（如不可播放）。 */
data class PlaybackNotice(
    val token: Long,
    val message: String,
)

data class PlaybackUiState(
    val queue: List<TrackRow> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    /** 用户/系统播放意图；seek 缓冲时 isPlaying 可能短暂为 false，图标应跟此字段。 */
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val playbackMode: PlaybackMode = PlaybackMode.ORDER,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lyricLines: List<LrcLine> = emptyList(),
    /** 已弃用展示：Source error 等不再写入，避免挡画面。 */
    val error: String? = null,
    val loadPending: Boolean = false,
    /** 有队列且未主动清空（UI：迷你条可见）。不驱动 FGS。 */
    val hasQueue: Boolean = false,
    val sourcePlaylistId: Long? = null,
    val sourcePlaylistTitle: String? = null,
    /** 手势/预览用：与真实 skipNext/Prev 目标一致（随机模式：下一首预选随机，上一首为播放历史） */
    val peekNextTrack: TrackRow? = null,
    val peekPrevTrack: TrackRow? = null,
    /** 右上角短通知；token 变化触发重新展示。 */
    val notice: PlaybackNotice? = null,
    /** 递增以唤醒全屏播放页底部控件。 */
    val transportWakeToken: Int = 0,
) {
    val currentTrack: TrackRow?
        get() = queue.getOrNull(index)

    /**
     * 无 Coordinator 时（冷启动仅 hydrate 快照）补齐邻曲预览，否则黑胶手势因 peek 为空无法切入切歌态。
     * 顺序/单曲：严格按列表相邻；随机：下一首用稳定占位（非历史上一首仍为空，与 Service 未起时一致）。
     */
    fun withHydratedPeeks(): PlaybackUiState {
        if (!hasQueue || queue.isEmpty() || index !in queue.indices) {
            return copy(peekNextTrack = null, peekPrevTrack = null)
        }
        if (peekNextTrack != null || peekPrevTrack != null) return this
        val next = when (playbackMode) {
            PlaybackMode.ORDER, PlaybackMode.REPEAT_ONE -> queue.getOrNull(index + 1)
            PlaybackMode.SHUFFLE -> {
                if (queue.size <= 1) null
                else queue[(index + 1) % queue.size]
            }
        }
        val prev = when (playbackMode) {
            PlaybackMode.ORDER, PlaybackMode.REPEAT_ONE -> queue.getOrNull(index - 1)
            PlaybackMode.SHUFFLE -> null
        }
        return copy(peekNextTrack = next, peekPrevTrack = prev)
    }
}

enum class PlaybackMode {
    ORDER,
    REPEAT_ONE,
    SHUFFLE,
}
