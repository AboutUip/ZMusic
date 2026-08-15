package com.kite.zmusic.ui.notice

import android.content.Context
import com.kite.zmusic.ZMusicApplication
import kotlinx.coroutines.channels.Channel
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

/**
 * 进程内通知队列。宿主 `IslandNoticeRoot` 是唯一消费者：
 * 入队立即展示；已有展示时下一条在收缩过半后换内容再展开。
 *
 * 不替代 Media3 系统媒体通知。
 */
class IslandNoticeCenter {
    private val seq = AtomicLong(0L)
    private val lock = Any()
    private val pending = ArrayDeque<IslandNotice>(8)
    private val signal = Channel<Unit>(Channel.CONFLATED)

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

    fun clear() {
        synchronized(lock) { pending.clear() }
        signal.trySend(Unit)
    }

    internal suspend fun awaitNext(): IslandNotice {
        while (true) {
            takePending()?.let { return it }
            signal.receive()
        }
    }

    /** 驻留期内若有下一条则立即返回，否则等到超时（无新通知）。 */
    internal suspend fun awaitNextOrTimeout(timeoutMs: Long): IslandNotice? {
        takePending()?.let { return it }
        return withTimeoutOrNull(timeoutMs) { awaitNext() } ?: takePending()
    }

    private fun takePending(): IslandNotice? = synchronized(lock) {
        pending.removeFirstOrNull()
    }

    companion object {
        private const val MaxQueued = 8
    }
}

fun Context.showIslandNotice(message: String, coverUrl: String? = null) {
    val app = applicationContext as? ZMusicApplication ?: return
    app.islandNoticeCenter.show(message, coverUrl)
}
