package com.kite.zmusic.ui.podcast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kite.zmusic.ui.home.homeStageHorizontalContent
import com.kite.zmusic.ui.theme.MainPalette
import kotlin.math.roundToInt

@Composable
internal fun PodcastBubble(
    coverUrl: String?,
    playing: Boolean,
    progress: Float,
    originX: Float,
    originY: Float,
    onMove: (Float, Float) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val bubble = 58.dp
        val edge = 10.dp
        val spin = remember { Animatable(0f) }
        LaunchedEffect(playing) {
            if (!playing) return@LaunchedEffect
            while (true) {
                spin.animateTo(spin.value + 360f, tween(durationMillis = 18_000, easing = LinearEasing))
            }
        }
        val fraction = progress.coerceIn(0f, 1f)
        val maxX = with(density) { (maxWidth - bubble - edge).toPx().coerceAtLeast(0f) }
        val maxY = with(density) { (maxHeight - bubble - edge).toPx().coerceAtLeast(0f) }
        var px by remember(maxX, maxY) {
            mutableStateOf(
                Offset(
                    (originX.coerceIn(0f, 1f) * maxX),
                    (originY.coerceIn(0f, 1f) * maxY),
                ),
            )
        }
        Box(
            Modifier
                .offset { IntOffset(px.x.roundToInt(), px.y.roundToInt()) }
                .size(bubble)
                .homeStageHorizontalContent()
                .shadow(10.dp, CircleShape)
                .pointerInput(maxX, maxY) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var moved = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (moved < slop) onOpen()
                                else {
                                    val fx = if (maxX <= 1f) 0f else (px.x / maxX).coerceIn(0f, 1f)
                                    val fy = if (maxY <= 1f) 0f else (px.y / maxY).coerceIn(0f, 1f)
                                    onMove(fx, fy)
                                }
                                break
                            }
                            val delta = change.positionChange()
                            moved += kotlin.math.abs(delta.x) + kotlin.math.abs(delta.y)
                            if (moved > slop) {
                                change.consume()
                                px = Offset(
                                    (px.x + delta.x).coerceIn(0f, maxX),
                                    (px.y + delta.y).coerceIn(0f, maxY),
                                )
                            }
                        }
                    }
                },
        ) {
            PodcastCover(
                coverUrl,
                bubble - 8.dp,
                modifier = Modifier
                    .padding(4.dp)
                    .graphicsLayer { rotationZ = spin.value },
                shape = CircleShape,
            )
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2f
                val arc = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = MainPalette.Hairline,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (fraction > 0.001f) {
                    drawArc(
                        color = MainPalette.Accent,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arc,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}
