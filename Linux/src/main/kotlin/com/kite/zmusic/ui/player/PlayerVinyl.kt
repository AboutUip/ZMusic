package com.kite.zmusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImage
import kotlinx.coroutines.isActive

@Composable
internal fun VinylDiscBase(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        drawCircle(color = Color(0xFF0E0E10), radius = r, center = c)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF2C2C30), Color(0xFF141416), Color(0xFF080809)),
                center = c,
                radius = r,
            ),
            radius = r * 0.995f,
            center = c,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = r * 0.985f,
            center = c,
            style = Stroke(width = r * 0.018f),
        )
        for (i in 1..11) {
            val rr = r * (0.26f + i * 0.055f)
            drawCircle(
                color = Color.White.copy(alpha = 0.035f + (i % 2) * 0.018f),
                radius = rr,
                center = c,
                style = Stroke(width = 1.1f),
            )
        }
    }
}

@Composable
internal fun VinylWithCoverArt(
    track: TrackRow,
    spinning: Boolean,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    skipDir: Int = 0,
    skipSeq: Int = 0,
    modifier: Modifier = Modifier,
) {
    val angle = remember { Animatable(0f) }
    val slide = remember { Animatable(0f) }
    val slidePx = with(LocalDensity.current) { 48.dp.toPx() }
    LaunchedEffect(track.id) {
        angle.snapTo(0f)
    }
    LaunchedEffect(skipSeq, skipDir) {
        if (skipSeq == 0 || skipDir == 0) return@LaunchedEffect
        slide.snapTo(if (skipDir > 0) slidePx else -slidePx)
        slide.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        if (System.getProperty("zmusic.test") == "true") return@LaunchedEffect
        while (isActive) {
            val next = angle.value + 360f
            angle.animateTo(next, tween(durationMillis = 28_000, easing = LinearEasing))
        }
    }
    var dragAcc by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .graphicsLayer { translationX = slide.value }
            .shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.55f))
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragAcc > 72f -> onSkipPrev()
                            dragAcc < -72f -> onSkipNext()
                        }
                        dragAcc = 0f
                    },
                    onDragCancel = { dragAcc = 0f },
                    onHorizontalDrag = { _, d -> dragAcc += d },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle.value },
            contentAlignment = Alignment.Center,
        ) {
            VinylDiscBase(Modifier.fillMaxSize())
            val coverFrac = 0.76f
            val centerFrac = 0.20f
            Box(
                Modifier
                    .fillMaxSize(coverFrac)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = track.id, animationSpec = tween(420), label = "vinylCover") { id ->
                    if (id == Long.MIN_VALUE) return@Crossfade
                    val u = track.coverUrl
                    if (!u.isNullOrBlank()) {
                        UrlImage(u, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(Color(0xFF1A2230)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("♪", style = TextStyle(color = LyricDim.copy(alpha = 0.45f), fontSize = 36.sp))
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize(centerFrac / coverFrac)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1E28)),
                )
            }
            Box(
                Modifier
                    .fillMaxSize(0.20f)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF2A3344), Color(0xFF12161E))))
                    .border(1.dp, CyanSoft.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize(0.22f)
                        .clip(CircleShape)
                        .background(Color(0xFF050508)),
                )
            }
        }
    }
}
