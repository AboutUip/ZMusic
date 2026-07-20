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

/** 切歌 / 同曲重头：进度归零动画的时长区间。 */
private const val SeekResetMinMs = 180
private const val SeekResetMaxMs = 420

private val SeekResetEasing = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f)

private fun seekResetDurationMs(distanceMs: Float): Int =
    (160f + distanceMs / 36f).toInt().coerceIn(SeekResetMinMs, SeekResetMaxMs)

/**
 * 播放进度展示位：
 * - 同曲跟播
 * - 切歌、同曲重头（上一首 >3s）、大幅回退：从当前展示值动画落到新目标（非瞬间跳变）
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
        val dropped = from - target
        val trackChanged = trackId != boundTrackId
        if (trackChanged) {
            boundTrackId = trackId
        }

        // 切歌、归零（上一首重头）、大幅回退 → 动画；否则瞬时跟随
        val shouldAnimate = dropped > 64f && (
            trackChanged ||
                target <= 48f ||
                dropped > 1_200f
            )

        if (shouldAnimate) {
            anim.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = seekResetDurationMs(dropped),
                    easing = SeekResetEasing,
                ),
            )
            return@LaunchedEffect
        }

        // 归零动画途中勿被 loadPending 的 0 位 snap 打断（同曲已在上面 animate）
        if (loadPending && target <= 0f && anim.value > 1f && abs(anim.value - target) > 1f) {
            return@LaunchedEffect
        }

        anim.snapTo(target)
    }

    return anim.value.toLong().coerceAtLeast(0L)
}
