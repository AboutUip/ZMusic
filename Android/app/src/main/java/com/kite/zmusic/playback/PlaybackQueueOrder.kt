package com.kite.zmusic.playback

/**
 * 播放队列改序。约定是先从 [from] 取出，再插入到 [to]（插入下标按取出后的列表计）。
 * 正在播的曲只跟着下标走，调用方不能因此重载媒体。
 */
internal fun <T> reorderQueue(items: List<T>, from: Int, to: Int): List<T>? {
    if (from == to || from !in items.indices || to !in items.indices) return null
    val next = items.toMutableList()
    val item = next.removeAt(from)
    next.add(to, item)
    return next
}

/** 改序后，原先位于 [index] 的那一项现在的下标。 */
internal fun queueIndexAfterMove(index: Int, from: Int, to: Int): Int = when {
    from == to -> index
    index == from -> to
    from < to && index in (from + 1)..to -> index - 1
    to < from && index in to until from -> index + 1
    else -> index
}

/**
 * 列表尚未提交时，静止行要让出的方向：上移 -1、下移 +1、不动 0。
 * 被拖的那行返回 0，位移由手指单独画。
 */
internal fun queueDragRowShift(index: Int, from: Int, to: Int): Int = when {
    from < 0 || from == to || index == from -> 0
    from < to && index in (from + 1)..to -> -1
    to < from && index in to until from -> 1
    else -> 0
}

/** 把一首歌排到当前曲的后面。正在播的那首留在原位，不因此重载。 */
internal sealed class QueuePlayNext<out T> {
    data object AlreadyPlaying : QueuePlayNext<Nothing>()
    data object AlreadyNext : QueuePlayNext<Nothing>()
    data class Queued<T>(
        val queue: List<T>,
        val index: Int,
        val insertedAt: Int,
    ) : QueuePlayNext<T>()
}

enum class PlayNextResult {
    Started,
    Queued,
    AlreadyPlaying,
    AlreadyNext,
    Unavailable,
}

internal fun <T, I> queuePlayNext(
    items: List<T>,
    index: Int,
    track: T,
    idOf: (T) -> I,
): QueuePlayNext<T>? {
    if (items.isEmpty() || index !in items.indices) return null
    val trackId = idOf(track)
    if (idOf(items[index]) == trackId) return QueuePlayNext.AlreadyPlaying
    val existing = items.indexOfFirst { idOf(it) == trackId }
    if (existing == index + 1) return QueuePlayNext.AlreadyNext
    val next = items.toMutableList()
    var current = index
    if (existing >= 0) {
        next.removeAt(existing)
        if (existing < current) current -= 1
    }
    val insertAt = (current + 1).coerceAtMost(next.size)
    next.add(insertAt, track)
    return QueuePlayNext.Queued(next, current, insertAt)
}
