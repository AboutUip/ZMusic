package com.kite.zmusic.ui.notice

import android.content.Context
import com.kite.zmusic.ZMusicApplication
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * 一条应用内灵动岛通知。
 *
 * @param coverUrl 功能封面（歌单/歌曲封面等）；空则展开后仍用 ZMusic Logo。
 */
data class IslandNotice(
    val id: Long,
    val message: String,
    val coverUrl: String? = null,
)

/** 通知胶囊当前这一帧。播放岛据此把同一座岛变成通知，再变回去。 */
data class IslandNoticeFrame(
    val message: String = "",
    val coverUrl: String? = null,
    val expansion: Float = 0f,
    val squashY: Float = 1f,
    val coverBlend: Float = 0f,
    val restWidthDp: Float = 0f,
    val overflowing: Boolean = false,
    val mounted: Boolean = false,
)

internal sealed class IslandWork {
    data class Sticky(val notice: IslandNotice) : IslandWork()
    data class Queued(val notice: IslandNotice) : IslandWork()
}

/**
 * 进程内通知队列。宿主 `IslandNoticeRoot` 是唯一消费者：
 * 入队立即展示；已有展示时下一条在收缩过半后换内容再展开。
 * 进度岛 [setSticky] 钉住直到 [clearSticky]，期间短通知排队。
 *
 * 不替代 Media3 系统媒体通知。
 */
class IslandNoticeCenter {
    private val seq = AtomicLong(0L)
    private val lock = Any()
    private val pending = ArrayDeque<IslandNotice>(8)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val _sticky = MutableStateFlow<IslandNotice?>(null)
    val sticky: StateFlow<IslandNotice?> = _sticky.asStateFlow()
    private val _presenting = MutableStateFlow(false)
    val presenting: StateFlow<Boolean> = _presenting.asStateFlow()
    private val _playbackClaim = MutableStateFlow(false)
    val playbackClaim: StateFlow<Boolean> = _playbackClaim.asStateFlow()
    private val _frame = MutableStateFlow(IslandNoticeFrame())
    val frame: StateFlow<IslandNoticeFrame> = _frame.asStateFlow()

    fun setPresenting(showing: Boolean) {
        if (_presenting.value == showing) return
        _presenting.value = showing
    }

    /** 播放岛在屏上时由它接管通知外形，避免两座胶囊叠在一起。 */
    fun setPlaybackClaim(claim: Boolean) {
        if (_playbackClaim.value == claim) return
        _playbackClaim.value = claim
    }

    fun setFrame(frame: IslandNoticeFrame) {
        if (_frame.value == frame) return
        _frame.value = frame
    }

    fun show(message: String, coverUrl: String? = null) {
        val text = message.trim()
        if (text.isEmpty()) return
        val notice = IslandNotice(
            id = seq.incrementAndGet(),
            message = text,
            coverUrl = coverUrl?.trim()?.takeIf { it.isNotEmpty() },
        )
        synchronized(lock) {
            while (pending.size >= MaxQueued) {
                pending.removeFirst()
            }
            pending.addLast(notice)
        }
        signal.trySend(Unit)
    }

    fun setSticky(message: String, coverUrl: String? = null) {
        val text = message.trim()
        if (text.isEmpty()) return
        _sticky.value = IslandNotice(
            id = StickyId,
            message = text,
            coverUrl = coverUrl?.trim()?.takeIf { it.isNotEmpty() },
        )
        signal.trySend(Unit)
    }

    fun clearSticky() {
        if (_sticky.value == null) return
        _sticky.value = null
        signal.trySend(Unit)
    }

    fun clear() {
        synchronized(lock) { pending.clear() }
        signal.trySend(Unit)
    }

    internal suspend fun awaitWork(): IslandWork {
        while (true) {
            _sticky.value?.let { return IslandWork.Sticky(it) }
            takePending()?.let { return IslandWork.Queued(it) }
            signal.receive()
        }
    }

    internal suspend fun awaitQueuedOrStickyOrTimeout(timeoutMs: Long): IslandWork? {
        _sticky.value?.let { return IslandWork.Sticky(it) }
        takePending()?.let { return IslandWork.Queued(it) }
        return withTimeoutOrNull(timeoutMs) { awaitWork() }
    }

    internal suspend fun awaitStickyChange(current: IslandNotice): IslandNotice? =
        sticky.first { it != current }

    private fun takePending(): IslandNotice? = synchronized(lock) {
        pending.removeFirstOrNull()
    }

    companion object {
        private const val MaxQueued = 8
        private const val StickyId = -1L
    }
}

fun Context.showIslandNotice(message: String, coverUrl: String? = null) {
    val app = applicationContext as? ZMusicApplication ?: return
    app.islandNoticeCenter.show(message, coverUrl)
}
