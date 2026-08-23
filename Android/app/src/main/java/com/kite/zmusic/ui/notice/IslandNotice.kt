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

    fun setSticky(message: String) {
        val text = message.trim()
        if (text.isEmpty()) return
        _sticky.value = IslandNotice(id = StickyId, message = text, coverUrl = null)
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
