package com.kite.zmusic.ui.splash

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.kite.zmusic.R
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val EasingBrush = CubicBezierEasing(0.12f, 0f, 0.2f, 1f)

private fun measureTotalPathLength(path: AndroidPath): Float {
    val pm = AndroidPathMeasure(path, false)
    var total = 0f
    do {
        total += pm.length
    } while (pm.nextContour())
    return total
}

private fun drawCalligraphyProgress(
    canvas: AndroidCanvas,
    glyphPath: AndroidPath,
    progress: Float,
    totalLength: Float,
    strokes: Array<AndroidPaint>,
) {
    if (totalLength <= 0f) return
    val pm = AndroidPathMeasure(glyphPath, false)
    var distanceRemaining = (progress * totalLength).coerceIn(0f, totalLength)
    while (distanceRemaining > 0f) {
        val cl = pm.length
        if (cl <= 0f) {
            if (!pm.nextContour()) break
            continue
        }
        val segmentDraw = distanceRemaining.coerceAtMost(cl)
        val segment = AndroidPath()
        pm.getSegment(0f, segmentDraw, segment, true)
        strokes.forEach { canvas.drawPath(segment, it) }
        distanceRemaining -= segmentDraw
        if (segmentDraw < cl) break
        if (!pm.nextContour()) break
    }
}

private data class Particle(
    val baseX: Float,
    val baseY: Float,
    val orbit: Float,
    val speed: Float,
    val phase: Float,
    val radius: Float,
    val twinkle: Float,
)

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val typeface = remember {
        ResourcesCompat.getFont(context, R.font.great_vibes)
            ?: error("great_vibes font missing in res/font")
    }

    val writeProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        writeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3600, easing = EasingBrush),
        )
    }

    val infinite = rememberInfiniteTransition(label = "particles")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val particles = remember(w, h) {
            val rnd = Random(42)
            List(140) {
                Particle(
                    baseX = rnd.nextFloat() * w,
                    baseY = rnd.nextFloat() * h,
                    orbit = 12f + rnd.nextFloat() * 48f,
                    speed = 0.4f + rnd.nextFloat() * 1.2f,
                    phase = rnd.nextFloat() * (Math.PI * 2).toFloat(),
                    radius = 0.6f + rnd.nextFloat() * 1.8f,
                    twinkle = rnd.nextFloat() * (Math.PI * 2).toFloat(),
                )
            }
        }

        val textSizePx = with(density) { 108.sp.toPx() }
        val strokeMainPx = with(density) { 2.8.dp.toPx() }
        val strokeGlowPx = with(density) { 10.dp.toPx() }

        val textPath = remember(w, h, textSizePx, typeface) {
            val p = AndroidPaint().apply {
                isAntiAlias = true
                textSize = textSizePx
                this.typeface = typeface
                style = AndroidPaint.Style.FILL
            }
            val path = AndroidPath()
            val text = "ZMusic"
            val tw = p.measureText(text)
            val cx = w / 2f
            val cy = h / 2f
            val x = cx - tw / 2f
            val fm = p.fontMetrics
            val y = cy - (fm.ascent + fm.descent) / 2f
            p.getTextPath(text, 0, text.length, x, y, path)
            path
        }

        val pathLength = remember(textPath) { measureTotalPathLength(textPath) }

        val glowPaint = remember(strokeGlowPx) {
            AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.STROKE
                strokeWidth = strokeGlowPx
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                color = android.graphics.Color.argb(55, 255, 255, 255)
            }
        }
        val corePaint = remember(strokeMainPx) {
            AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.STROKE
                strokeWidth = strokeMainPx
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                color = android.graphics.Color.argb(245, 235, 235, 245)
            }
        }
        val hairlinePaint = remember {
            AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.STROKE
                strokeWidth = 0.85f
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                color = android.graphics.Color.argb(200, 255, 255, 255)
            }
        }

        // Ambient iOS-like depth: soft cool vignette + center lift
        Canvas(Modifier.fillMaxSize()) {
            drawAmbientGlow(size.width, size.height)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .padding(bottom = with(density) { 48.dp }),
        ) {
            val t = drift * (Math.PI * 2).toFloat()
            particles.forEach { p ->
                val ox = sin(t * p.speed + p.phase) * p.orbit * 0.35f
                val oy = cos(t * p.speed * 0.87f + p.phase) * p.orbit * 0.28f
                val x = (p.baseX + ox).coerceIn(0f, size.width)
                val y = (p.baseY + oy).coerceIn(0f, size.height)
                val tw = 0.5f + 0.5f * sin(t * 3f + p.twinkle)
                val alpha = (0.04f + 0.1f * tw).coerceIn(0f, 0.18f)
                drawCircle(
                    color = Color(0xFF9BB5FF).copy(alpha = alpha),
                    radius = p.radius * (1f + 0.35f * tw),
                    center = Offset(x, y),
                )
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.35f),
                    radius = p.radius * 0.45f,
                    center = Offset(x, y),
                )
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .align(Alignment.Center),
        ) {
            val canvas = drawContext.canvas.nativeCanvas
            val prog = writeProgress.value
            val strokes = arrayOf(glowPaint, corePaint, hairlinePaint)
            drawCalligraphyProgress(
                canvas = canvas,
                glyphPath = textPath,
                progress = prog,
                totalLength = pathLength,
                strokes = strokes,
            )
        }
    }
}

private fun DrawScope.drawAmbientGlow(width: Float, height: Float) {
    val cx = width / 2f
    val cy = height * 0.42f
    val r = hypot(width, height) * 0.55f
    drawRect(brush = Brush.radialGradient(
        colors = listOf(
            Color(0xFF1C1C2E).copy(alpha = 0.85f),
            Color(0xFF050508).copy(alpha = 0.55f),
            Color.Black,
        ),
        center = Offset(cx, cy),
        radius = r,
    ))
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF2A3A52).copy(alpha = 0.12f),
                Color.Transparent,
            ),
            center = Offset(width * 0.85f, height * 0.2f),
            radius = height * 0.4f,
        ),
    )
}
