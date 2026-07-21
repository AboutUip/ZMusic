package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.VinylPlateColors
import com.kite.zmusic.ui.common.UrlImage
import kotlin.math.hypot
import kotlinx.coroutines.delay

/** 曲谱点选飞入：从卡片黑胶沿曲线落到主黑胶。 */
data class ScoreVinylFlight(
    val track: TrackRow,
    val queueIndex: Int,
    val startCenter: Offset,
    val startSizePx: Float,
)

private val FlightEase = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)
private const val FlightMs = 720
private const val CoverTrigger = 0.88f
private const val CoverFrac = 0.76f

@Composable
fun ScoreVinylFlightLayer(
    flight: ScoreVinylFlight,
    targetCenter: Offset,
    targetSizePx: Float,
    plateColors: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    onCoverTarget: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val progress = remember(flight) { Animatable(0f) }
    var coverFired by remember(flight) { mutableStateOf(false) }
    var ready by remember(flight) { mutableStateOf(false) }
    var endCenter by remember(flight) { mutableStateOf(Offset.Zero) }
    var endSize by remember(flight) { mutableFloatStateOf(0f) }
    val onCoverUpdated by rememberUpdatedState(onCoverTarget)
    val onFinishedUpdated by rememberUpdatedState(onFinished)
    val targetCenterUpdated by rememberUpdatedState(targetCenter)
    val targetSizeUpdated by rememberUpdatedState(targetSizePx)

    val startValid = flight.startCenter.x > 1f && flight.startCenter.y > 1f &&
        flight.startSizePx >= 8f
    if (!startValid) {
        LaunchedEffect(flight) { onFinishedUpdated() }
        return
    }

    val start = flight.startCenter

    LaunchedEffect(flight) {
        coverFired = false
        ready = false
        // 等待主黑胶坐标就绪，禁止终点落在 (0,0)
        var frames = 0
        var tc = targetCenterUpdated
        var ts = targetSizeUpdated
        while ((tc.x < 1f || tc.y < 1f || ts < 8f) && frames < 48) {
            delay(16)
            tc = targetCenterUpdated
            ts = targetSizeUpdated
            frames++
        }
        if (tc.x < 1f || tc.y < 1f || ts < 8f) {
            onFinishedUpdated()
            return@LaunchedEffect
        }
        endCenter = tc
        endSize = ts
        ready = true
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = FlightMs, easing = FlightEase),
        )
        if (!coverFired) {
            coverFired = true
            onCoverUpdated()
        }
        onFinishedUpdated()
    }

    LaunchedEffect(progress.value, flight, ready) {
        if (!ready) return@LaunchedEffect
        if (!coverFired && progress.value >= CoverTrigger) {
            coverFired = true
            onCoverUpdated()
        }
    }

    if (!ready) return

    val end = endCenter
    val dist = hypot(end.x - start.x, end.y - start.y)
    val nearThresh = with(density) { 48.dp.toPx() }
    val useCurve = dist > nearThresh
    val control = remember(flight.queueIndex, start, end, useCurve) {
        if (!useCurve) return@remember start
        val mid = Offset((start.x + end.x) * 0.5f, (start.y + end.y) * 0.5f)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val nx = -dy / len
        val ny = dx / len
        val bulge = (dist * 0.28f).coerceIn(
            with(density) { 36.dp.toPx() },
            with(density) { 160.dp.toPx() },
        )
        val up = if (ny <= 0f) 1f else -1f
        Offset(mid.x + nx * bulge * up, mid.y + ny * bulge * up - bulge * 0.35f)
    }

    val t = progress.value.coerceIn(0f, 1f)
    val pos = if (useCurve) {
        val u = 1f - t
        Offset(
            u * u * start.x + 2f * u * t * control.x + t * t * end.x,
            u * u * start.y + 2f * u * t * control.y + t * t * end.y,
        )
    } else {
        Offset(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
        )
    }
    val startSize = flight.startSizePx.coerceAtLeast(1f)
    val targetSize = endSize.coerceAtLeast(startSize)
    val extractBoost = 1f + 0.10f * (1f - (t / 0.18f).coerceIn(0f, 1f))
    val sizePx = (startSize + (targetSize - startSize) * t) * extractBoost
    val extractPull = with(density) { 12.dp.toPx() } * (1f - (t / 0.22f).coerceIn(0f, 1f))
    val drawX = pos.x - extractPull
    val drawY = pos.y

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX = drawX - sizePx / 2f
                    translationY = drawY - sizePx / 2f
                }
                .size(with(density) { sizePx.toDp() }),
            contentAlignment = Alignment.Center,
        ) {
            FlightVinylFace(
                track = flight.track,
                plateColors = plateColors,
                fullCover = fullCover,
                centerRadiusFrac = centerRadiusFrac,
                outerScale = outerScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FlightVinylFace(
    track: TrackRow,
    plateColors: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    modifier: Modifier = Modifier,
) {
    val coverT = if (fullCover) 1f else 0f
    val outer = outerScale.coerceIn(0.5f, 1.6f)
    val coverHoleFrac = (centerRadiusFrac / CoverFrac).coerceIn(0.08f, 0.95f) * (1f - coverT)
    val spindleFrac = (0.048f / outer).coerceIn(0.02f, 0.35f) * (1f - coverT)

    Box(modifier, contentAlignment = Alignment.Center) {
        VinylDiscPlate(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = outer
                    scaleY = outer
                    transformOrigin = TransformOrigin.Center
                },
            spindleHoleFrac = spindleFrac,
            colors = plateColors,
        )
        Box(
            Modifier
                .fillMaxSize(CoverFrac)
                .clip(
                    if (coverHoleFrac < 0.012f) {
                        CircleShape
                    } else {
                        VinylAnnulusShape(holeFrac = coverHoleFrac)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val u = track.coverUrl
            if (!u.isNullOrBlank()) {
                UrlImage(
                    url = u,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121214)),
                )
            }
        }
    }
}
