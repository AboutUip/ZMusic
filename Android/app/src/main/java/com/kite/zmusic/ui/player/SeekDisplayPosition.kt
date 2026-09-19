package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs

/** 歌词点选 / 大幅跳转：进度条过渡时长区间。 */
private const val SeekJumpMinMs = 200
private const val SeekJumpMaxMs = 480

/** 进度 UI 超过此时长没跟上（后台 / 离屏），下一次直接贴合，不播跳转动画。 */
internal const val ProgressStaleUiGapMs = 800L

private val SeekJumpEasing = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f)

internal fun progressElapsedRealtimeMs(): Long = android.os.SystemClock.elapsedRealtime()

private fun seekJumpDurationMs(distanceMs: Float): Int =
    (180f + distanceMs / 40f).toInt().coerceIn(SeekJumpMinMs, SeekJumpMaxMs)

private data class SeekDisplayTick(
    val trackId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val loadPending: Boolean,
    val seeking: Boolean,
)

/**
 * 播放进度展示位：
 * - 同曲小步进：瞬时跟随
 * - 拖进度条：跟手贴合，松手后不再从旧点 / 0 重播一遍
 * - 切歌、歌词点选等大幅跳转：动画过渡到目标
 * - 切歌归零：动画盖住回退，不因 loadPending 超过 800ms 改成 snap
 * - 后台回前台等 UI 长时间没跟上：直接贴合，不播跳转动画
 */
@Composable
fun rememberSeekDisplayClock(
    trackId: Long,
    positionMs: Long,
    durationMs: Long,
    loadPending: Boolean,
    seeking: Boolean = false,
    scrubPositionMs: Long = positionMs,
): SeekDisplayClock {
    val incomingDur = durationMs.coerceAtLeast(1L)
    val anim = remember { Animatable(positionMs.toFloat().coerceAtLeast(0f)) }
    var boundTrackId by remember { mutableLongStateOf(trackId) }
    var holdAfterScrub by remember { mutableStateOf(false) }
    var lastApplyElapsed by remember { mutableLongStateOf(progressElapsedRealtimeMs()) }
    var prevDurationMs by remember { mutableLongStateOf(incomingDur) }
    var holdDurationMs by remember { mutableLongStateOf(0L) }
    val scrubRef = rememberUpdatedState(scrubPositionMs)
    val trackRef = rememberUpdatedState(trackId)
    val posRef = rememberUpdatedState(positionMs)
    val durRef = rememberUpdatedState(incomingDur)
    val loadRef = rememberUpdatedState(loadPending)
    val seekingRef = rememberUpdatedState(seeking)
    val trackChangedNow = trackId != boundTrackId
    val displayDurationMs = seekRewindDisplayDurationMs(
        incomingDurationMs = incomingDur,
        animPositionMs = anim.value,
        previousDurationMs = prevDurationMs,
        holdDurationMs = holdDurationMs,
        trackChanged = trackChangedNow,
    )

    LaunchedEffect(seeking) {
        if (!seeking) return@LaunchedEffect
        holdAfterScrub = true
        snapshotFlow { scrubRef.value }.collect { ms ->
            anim.snapTo(ms.toFloat().coerceAtLeast(0f))
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            SeekDisplayTick(
                trackId = trackRef.value,
                positionMs = posRef.value,
                durationMs = durRef.value,
                loadPending = loadRef.value,
                seeking = seekingRef.value,
            )
        }.collect { tick ->
            val now = progressElapsedRealtimeMs()
            if (tick.seeking) {
                lastApplyElapsed = now
                return@collect
            }
            val stale = now - lastApplyElapsed >= ProgressStaleUiGapMs
            lastApplyElapsed = now

            val target = tick.positionMs.toFloat().coerceAtLeast(0f)
            val from = anim.value
            val trackChanged = tick.trackId != boundTrackId
            if (trackChanged) {
                holdDurationMs = seekRewindCaptureHoldMs(prevDurationMs, from)
                boundTrackId = tick.trackId
                holdAfterScrub = false
            }

            if (holdAfterScrub) {
                holdAfterScrub = false
                if (!trackChanged &&
                    (target <= SeekRewindNearZeroMs && from > SeekRewindFromMinMs ||
                        from - target > SeekFollowEpsilonMs)
                ) {
                    return@collect
                }
                holdDurationMs = 0L
                prevDurationMs = tick.durationMs
                anim.snapTo(target)
                return@collect
            }

            if (seekShouldPreservePositionOnFakeZero(
                    from = from,
                    target = target,
                    loadPending = tick.loadPending,
                    trackChanged = trackChanged,
                )
            ) {
                return@collect
            }

            if (seekShouldAnimateToTarget(
                    from = from,
                    target = target,
                    trackChanged = trackChanged,
                    stale = stale,
                    durationMs = tick.durationMs,
                )
            ) {
                anim.animateTo(
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = seekJumpDurationMs(abs(target - from)),
                        easing = SeekJumpEasing,
                    ),
                )
                if (target <= SeekRewindNearZeroMs) {
                    holdDurationMs = 0L
                    prevDurationMs = tick.durationMs
                }
                return@collect
            }

            if (stale) {
                holdDurationMs = 0L
                prevDurationMs = tick.durationMs
                anim.snapTo(target)
                return@collect
            }

            if (anim.value <= SeekRewindNearZeroMs) {
                holdDurationMs = 0L
                prevDurationMs = tick.durationMs
            }
            anim.snapTo(target)
        }
    }

    SideEffect {
        if (!seekShouldKeepPreviousDuration(
                animPositionMs = anim.value,
                incomingDurationMs = incomingDur,
                holdDurationMs = holdDurationMs,
                trackChanged = trackChangedNow,
            )
        ) {
            prevDurationMs = incomingDur
        }
    }

    return SeekDisplayClock(
        positionMs = anim.value.toLong().coerceAtLeast(0L),
        durationMs = displayDurationMs,
    )
}

@Composable
fun rememberSeekDisplayPositionMs(
    trackId: Long,
    positionMs: Long,
    loadPending: Boolean,
    seeking: Boolean = false,
    scrubPositionMs: Long = positionMs,
    durationMs: Long = 1L,
): Long = rememberSeekDisplayClock(
    trackId = trackId,
    positionMs = positionMs,
    durationMs = durationMs,
    loadPending = loadPending,
    seeking = seeking,
    scrubPositionMs = scrubPositionMs,
).positionMs
