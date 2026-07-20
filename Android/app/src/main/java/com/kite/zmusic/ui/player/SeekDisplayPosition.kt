package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** 歌词点选 / 大幅跳转：进度条过渡时长区间。 */
private const val SeekJumpMinMs = 200
private const val SeekJumpMaxMs = 480

/** 小于此差值视为跟播步进，瞬时贴合，不播跳转动画。 */
private const val SeekFollowEpsilonMs = 480f

private val SeekJumpEasing = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f)

private fun seekJumpDurationMs(distanceMs: Float): Int =
    (180f + distanceMs / 40f).toInt().coerceIn(SeekJumpMinMs, SeekJumpMaxMs)

/**
 * 播放进度展示位：
 * - 同曲小步进：瞬时跟随
 * - 切歌、归零、歌词点选等大幅跳转（前进 / 后退均）：动画过渡到目标
 */
@Composable
fun rememberSeekDisplayPositionMs(
    trackId: Long,
    positionMs: Long,
    loadPending: Boolean,
    seeking: Boolean = false,
): Long {
    val anim = remember { Animatable(positionMs.toFloat().coerceAtLeast(0f)) }
    var boundTrackId by remember { mutableLongStateOf(trackId) }

    LaunchedEffect(trackId, positionMs, loadPending, seeking) {
        if (seeking) return@LaunchedEffect

        val target = positionMs.toFloat().coerceAtLeast(0f)
        val from = anim.value
        val distance = abs(target - from)
        val trackChanged = trackId != boundTrackId
        if (trackChanged) {
            boundTrackId = trackId
        }

        // 前进 / 后退的大幅跳转都动画；小步进（跟播）仍 snap
        val shouldAnimate = distance > SeekFollowEpsilonMs ||
            (trackChanged && distance > 64f) ||
            (target <= 48f && from - target > 64f)

        if (shouldAnimate) {
            anim.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = seekJumpDurationMs(distance),
                    easing = SeekJumpEasing,
                ),
            )
            return@LaunchedEffect
        }

        // 归零动画途中勿被 loadPending 的 0 位 snap 打断
        if (loadPending && target <= 0f && anim.value > 1f && abs(anim.value - target) > 1f) {
            return@LaunchedEffect
        }

        anim.snapTo(target)
    }

    return anim.value.toLong().coerceAtLeast(0L)
}
