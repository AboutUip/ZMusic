package com.kite.zmusic.ui.player

data class SeekDisplayClock(
    val positionMs: Long,
    val durationMs: Long,
)

internal const val SeekFollowEpsilonMs = 480f
internal const val SeekRewindNearZeroMs = 48f
internal const val SeekRewindFromMinMs = 64f
internal const val SeekFakeZeroFromMinMs = 200f

fun seekProgressFraction(positionMs: Long, durationMs: Long): Float {
    val dur = durationMs.toFloat()
    if (dur <= 0f) return 0f
    return (positionMs.toFloat() / dur).coerceIn(0f, 1f)
}

/**
 * 切歌归零动画仍用上一首的毫秒位置，duration 却已换成下一首。
 * 下一首更短时，比值会先顶到 100% 再掉回 0%。归零途中继续用上一首时长做分母。
 *
 * 时长往往比 trackId 先到：即使还没标成切歌，位置已经装不进新时长时也必须锁分母，
 * 否则动画还没起跑就闪一帧 100%。
 */
fun seekRewindDisplayDurationMs(
    incomingDurationMs: Long,
    animPositionMs: Float,
    previousDurationMs: Long,
    holdDurationMs: Long,
    trackChanged: Boolean,
): Long {
    val incoming = incomingDurationMs.coerceAtLeast(1L)
    val from = animPositionMs.coerceAtLeast(0f)
    if (from <= SeekRewindNearZeroMs) return incoming
    val held = maxOf(holdDurationMs, previousDurationMs).coerceAtLeast(1L)
    if (from > incoming) {
        return maxOf(held, from.toLong())
    }
    if ((holdDurationMs > 0L || trackChanged) && held > incoming) {
        return held
    }
    return incoming
}

fun seekRewindCaptureHoldMs(previousDurationMs: Long, animPositionMs: Float): Long =
    maxOf(previousDurationMs.coerceAtLeast(1L), animPositionMs.toLong().coerceAtLeast(1L))

/** 归零还在跑时不要把旧时长覆盖成下一首，否则分母丢失、动画盖不住。 */
fun seekShouldKeepPreviousDuration(
    animPositionMs: Float,
    incomingDurationMs: Long,
    holdDurationMs: Long,
    trackChanged: Boolean,
): Boolean {
    val from = animPositionMs.coerceAtLeast(0f)
    if (from <= SeekRewindNearZeroMs) return false
    if (holdDurationMs > 0L || trackChanged) return true
    return from > incomingDurationMs.coerceAtLeast(1L)
}

/**
 * 切歌 / 曲末回绕必须用动画盖住归位；加载超过 800ms 也不能改成 snap。
 * 续播假 0 不走这里，由 [seekShouldPreservePositionOnFakeZero] 拦住。
 */
fun seekShouldAnimateToTarget(
    from: Float,
    target: Float,
    trackChanged: Boolean,
    stale: Boolean,
    durationMs: Long,
): Boolean {
    val distance = kotlin.math.abs(target - from)
    if (distance <= SeekRewindFromMinMs) return false
    if (trackChanged) return true
    val rewindToStart = target <= SeekRewindNearZeroMs && from - target > SeekRewindFromMinMs
    if (rewindToStart && seekNearEnd(from, durationMs)) return true
    if (stale) return false
    return distance > SeekFollowEpsilonMs
}

fun seekShouldPreservePositionOnFakeZero(
    from: Float,
    target: Float,
    loadPending: Boolean,
    trackChanged: Boolean,
): Boolean {
    if (trackChanged || !loadPending) return false
    return target <= SeekRewindNearZeroMs && from > SeekFakeZeroFromMinMs
}

fun seekNearEnd(from: Float, durationMs: Long): Boolean {
    val dur = durationMs.toFloat()
    if (dur <= 0f) return false
    return from >= dur * 0.75f || dur - from <= 5_000f
}
