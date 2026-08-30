package com.kite.zmusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import kotlinx.coroutines.isActive

@Composable
internal fun VinylDiscBase(
    modifier: Modifier = Modifier,
    plate: VinylPlateColors = VinylPlateColors.Black,
) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        drawCircle(color = plate.baseEdge, radius = r, center = c)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(plate.baseInner, plate.baseMid, plate.baseOuter, plate.baseEdge),
                center = c,
                radius = r,
            ),
            radius = r * 0.995f,
            center = c,
        )
        drawCircle(
            color = plate.rim.copy(alpha = 0.12f),
            radius = r * 0.985f,
            center = c,
            style = Stroke(width = r * 0.018f),
        )
        for (i in 1..11) {
            val rr = r * (0.26f + i * 0.055f)
            drawCircle(
                color = plate.groove.copy(alpha = 0.035f + (i % 2) * 0.018f),
                radius = rr,
                center = c,
                style = Stroke(width = 1.1f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VinylWithCoverArt(
    track: TrackRow,
    spinning: Boolean,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    skipDir: Int = 0,
    skipSeq: Int = 0,
    plate: VinylPlateColors = VinylPlateColors.Black,
    outerScale: Float = 1f,
    centerRadiusFrac: Float = 0.20f,
    fullCover: Boolean = false,
    gestureDamping: Float = 0.5f,
    onLongPress: (() -> Unit)? = null,
    gesturesEnabled: Boolean = true,
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
    val trip = 72f / gestureDamping.coerceIn(0.15f, 1f)
    Box(
        modifier
            .graphicsLayer { translationX = slide.value }
            .shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.55f))
            .then(
                if (onLongPress == null) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = onLongPress,
                    )
                },
            )
            .then(
                if (!gesturesEnabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(track.id, trip) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    dragAcc > trip -> onSkipPrev()
                                    dragAcc < -trip -> onSkipNext()
                                }
                                dragAcc = 0f
                            },
                            onDragCancel = { dragAcc = 0f },
                            onHorizontalDrag = { _, d -> dragAcc += d },
                        )
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle.value },
            contentAlignment = Alignment.Center,
        ) {
            VinylDiscBase(Modifier.fillMaxSize().graphicsLayer { scaleX = outerScale; scaleY = outerScale }, plate)
            val coverFrac = if (fullCover) 1f else 0.76f
            val centerFrac = centerRadiusFrac.coerceIn(0.10f, 0.42f)
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
                            Icon(
                                ZIcons.MusicNote,
                                contentDescription = null,
                                tint = LyricDim.copy(alpha = 0.45f),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
                if (!fullCover) {
                Box(
                    Modifier
                        .fillMaxSize(centerFrac / coverFrac)
                        .clip(CircleShape)
                        .background(plate.holeDark),
                )
                }
            }
            if (!fullCover) {
            Box(
                Modifier
                    .fillMaxSize(centerRadiusFrac.coerceIn(0.10f, 0.42f))
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(plate.holeLight.copy(alpha = 0.35f), plate.holeDark)))
                    .border(1.dp, plate.rim.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize(0.22f)
                        .clip(CircleShape)
                        .background(plate.holeDark),
                )
            }
            }
        }
    }
}
