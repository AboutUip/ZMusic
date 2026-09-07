package com.kite.zmusic.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kite.zmusic.data.MiniQuickSkipAxis
import com.kite.zmusic.data.TrackRow
import kotlin.math.abs

internal fun Modifier.miniQuickSkipDetect(
    enabled: Boolean,
    axis: MiniQuickSkipAxis,
    busy: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
): Modifier = composed {
    val nextRef = rememberUpdatedState(onNext)
    val prevRef = rememberUpdatedState(onPrev)
    val busyRef = rememberUpdatedState(busy)
    val enabledRef = rememberUpdatedState(enabled)
    val axisRef = rememberUpdatedState(axis)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabledRef.value) return@awaitEachGesture
            var total = Offset.Zero
            var locked: Boolean? = null
            var fired = false
            val pointerId = down.id
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (change.changedToUpIgnoreConsumed()) break
                val delta = change.positionChange()
                total += delta
                if (locked == null) {
                    if (total.getDistance() < viewConfiguration.touchSlop) continue
                    val horizontal = abs(total.x) >= abs(total.y)
                    locked = if (axisRef.value == MiniQuickSkipAxis.Horizontal) {
                        horizontal
                    } else {
                        !horizontal
                    }
                }
                if (locked != true) break
                change.consume()
                if (fired || busyRef.value) continue
                val main = if (axisRef.value == MiniQuickSkipAxis.Horizontal) {
                    total.x
                } else {
                    total.y
                }
                val cross = if (axisRef.value == MiniQuickSkipAxis.Horizontal) {
                    total.y
                } else {
                    total.x
                }
                val threshold = viewConfiguration.touchSlop * 2.6f
                if (abs(main) < threshold || abs(main) <= abs(cross) * 1.12f) continue
                fired = true
                if (main < 0f) nextRef.value() else prevRef.value()
            }
        }
    }
}

@Composable
internal fun MiniSkipVisual(
    current: TrackRow,
    outgoing: TrackRow?,
    progress: Float,
    vertical: Boolean,
    toNext: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (TrackRow) -> Unit,
) {
    val from = outgoing
    if (from == null || (from.id == current.id && progress >= 0.995f)) {
        Box(modifier) { content(current) }
        return
    }
    val sign = if (toNext) 1f else -1f
    Box(modifier.clipToBounds()) {
        if (vertical) {
            MiniFlipLayers(
                from = from,
                to = current,
                progress = progress,
                sign = sign,
                content = content,
            )
        } else {
            MiniTrailLayers(
                from = from,
                to = current,
                progress = progress,
                sign = sign,
                content = content,
            )
        }
    }
}

@Composable
private fun MiniFlipLayers(
    from: TrackRow,
    to: TrackRow,
    progress: Float,
    sign: Float,
    content: @Composable (TrackRow) -> Unit,
) {
    val outT = (progress / 0.5f).coerceIn(0f, 1f)
    val inT = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
    if (outT < 0.999f) {
        Box(
            Modifier.graphicsLayer {
                cameraDistance = 18f
                transformOrigin = TransformOrigin.Center
                rotationX = sign * 90f * outT
                alpha = 1f - outT * 0.12f
            },
        ) {
            content(from)
        }
    }
    if (inT > 0.001f) {
        Box(
            Modifier.graphicsLayer {
                cameraDistance = 18f
                transformOrigin = TransformOrigin.Center
                rotationX = sign * 90f * (inT - 1f)
                alpha = inT
            },
        ) {
            content(to)
        }
    }
}

@Composable
private fun MiniTrailLayers(
    from: TrackRow,
    to: TrackRow,
    progress: Float,
    sign: Float,
    content: @Composable (TrackRow) -> Unit,
) {
    val travel = 52.dp
    val outAlpha = (1f - progress).coerceIn(0f, 1f)
    val trailSteps = 4
    for (i in trailSteps - 1 downTo 0) {
        val trail = i / (trailSteps - 1).toFloat()
        Box(
            Modifier.graphicsLayer {
                val extra = trail * 16.dp.toPx()
                translationX = -sign * (progress * travel.toPx() + extra)
                alpha = outAlpha * lerp(0.9f, 0.12f, trail)
            },
        ) {
            content(from)
        }
    }
    Box(
        Modifier.graphicsLayer {
            translationX = sign * (1f - progress) * travel.toPx()
            alpha = progress
        },
    ) {
        content(to)
    }
}
