package com.kite.zmusic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 窗户雨效：窗外斜雨 → 磨砂玻璃雾气。从 Android [RainGlassAtmosphere] 复制（无 API 31 分支）。
 */
@Composable
internal fun RainGlassAtmosphere(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    val t = intensity.coerceIn(0f, 1f)
    if (t <= 0.001f) return

    val density = LocalDensity.current
    val windAngleRad = Math.toRadians(18.0).toFloat()
    val windX = -sin(windAngleRad)
    val windY = cos(windAngleRad)
    val farStreaks = remember { List(80) { i -> makeStreak(Random(i * 131 + 3), far = true) } }
    val nearStreaks = remember { List(36) { i -> makeStreak(Random(i * 211 + 17), far = false) } }
    val dissipateDots = remember {
        List(24) { i ->
            val rnd = Random(7 + i * 19)
            floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat())
        }
    }
    var tick by remember { mutableFloatStateOf(0f) }
    val intensityRef = rememberUpdatedState(t)
    LaunchedEffect(Unit) {
        if (System.getProperty("zmusic.test") == "true") return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                    return@withFrameNanos
                }
                val dt = ((now - last).coerceAtMost(33_000_000L) / 1_000_000_000f)
                last = now
                tick += dt
                val speedMul = 0.22f + 0.78f * intensityRef.value
                fun stepStreaks(list: List<RainStreak>, speedScale: Float) {
                    list.forEach { s ->
                        val v = s.speed * speedScale * dt
                        s.x += windX * v
                        s.y += windY * v
                        if (s.y > 1.25f || s.x < -0.25f || s.y < -0.35f || s.x > 1.4f) {
                            respawnStreak(s)
                        }
                    }
                }
                stepStreaks(farStreaks, 0.72f * speedMul)
                stepStreaks(nearStreaks, 1.15f * speedMul)
            }
        }
    }
    val lengthMul = 0.15f + 0.85f * t.pow(0.85f)
    val rainAlpha = t.pow(1.15f)
    val fogAlpha = t.pow(0.75f)
    Box(
        modifier
            .fillMaxSize()
            .alpha(0.15f + 0.85f * t),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            tick
            drawStreakLayer(farStreaks, windX, windY, density.density, 0.45f * rainAlpha, lengthMul, 0.35f + 0.65f * t)
        }
        Canvas(Modifier.fillMaxSize()) {
            tick
            drawStreakLayer(nearStreaks, windX, windY, density.density, rainAlpha, lengthMul, 0.35f + 0.65f * t)
        }
        FrostedWindowPane(fogAlpha.coerceIn(0f, 1f))
        if (t < 0.92f) {
            val dissipate = (1f - t).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFD8E4F0).copy(alpha = 0.10f * dissipate),
                            Color(0xFFA8BBC8).copy(alpha = 0.06f * dissipate),
                            Color.Transparent,
                        ),
                    ),
                )
                dissipateDots.forEach { d ->
                    val px = d[0] * size.width
                    val py = d[1] * size.height * (0.35f + 0.65f * dissipate)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.018f * dissipate + d[2] * 0.02f),
                        radius = (3f + d[2] * 14f) * (0.6f + dissipate),
                        center = Offset(px, py - dissipate * size.height * 0.08f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FrostedWindowPane(intensity: Float) {
    val a = intensity.coerceIn(0f, 1f)
    if (a <= 0.001f) return
    val grain = remember {
        List(180) { i ->
            val rnd = Random(42 + i)
            floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat())
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF9BB0C4).copy(alpha = 0.11f * a),
                        Color(0xFF6E849A).copy(alpha = 0.15f * a),
                        Color(0xFF3E4E60).copy(alpha = 0.18f * a),
                    ),
                ),
            )
            .background(Color.White.copy(alpha = 0.05f * a)),
    )
    Canvas(Modifier.fillMaxSize()) {
        grain.forEach { g ->
            drawCircle(
                color = Color.White.copy(alpha = (0.012f + g[2] * 0.024f) * a),
                radius = 0.6f + g[2] * 2.0f,
                center = Offset(g[0] * size.width, g[1] * size.height),
            )
        }
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xFF1A2430).copy(alpha = 0.12f * a)),
            ),
        )
    }
}

private fun DrawScope.drawStreakLayer(
    streaks: List<RainStreak>,
    windX: Float,
    windY: Float,
    densityScale: Float,
    alphaMul: Float,
    lengthMul: Float,
    thicknessMul: Float,
) {
    val w = size.width
    val h = size.height
    streaks.forEach { s ->
        val near = s.depth
        val a = s.alpha * alphaMul * (0.4f + near * 0.75f)
        if (a < 0.004f) return@forEach
        val thick = s.thickness * (0.5f + near * 1.15f) * densityScale * thicknessMul
        val lenPx = s.length * h * (0.75f + near * 0.7f) * lengthMul
        val x0 = s.x * w
        val y0 = s.y * h
        drawLine(
            color = Color.White.copy(alpha = a),
            start = Offset(x0, y0),
            end = Offset(x0 + windX * lenPx, y0 + windY * lenPx),
            strokeWidth = thick.coerceAtLeast(0.35f),
        )
    }
}

private class RainStreak(
    var x: Float,
    var y: Float,
    val length: Float,
    val speed: Float,
    val thickness: Float,
    val alpha: Float,
    val depth: Float,
)

private fun makeStreak(rnd: Random, far: Boolean): RainStreak {
    val x = -0.15f + rnd.nextFloat() * 1.35f
    val y = -0.15f + rnd.nextFloat() * 1.35f
    return RainStreak(
        x = x,
        y = y,
        length = if (far) 0.028f + rnd.nextFloat() * 0.05f else 0.045f + rnd.nextFloat() * 0.08f,
        speed = if (far) 0.18f + rnd.nextFloat() * 0.22f else 0.28f + rnd.nextFloat() * 0.32f,
        thickness = if (far) 0.45f + rnd.nextFloat() * 0.7f else 0.9f + rnd.nextFloat() * 1.5f,
        alpha = if (far) 0.05f + rnd.nextFloat() * 0.1f else 0.1f + rnd.nextFloat() * 0.16f,
        depth = if (far) rnd.nextFloat() * 0.45f else 0.55f + rnd.nextFloat() * 0.45f,
    )
}

private fun respawnStreak(s: RainStreak) {
    val fromTop = Random.nextFloat() < 0.62f
    if (fromTop) {
        s.x = -0.2f + Random.nextFloat() * 1.45f
        s.y = -0.08f - Random.nextFloat() * 0.28f
    } else {
        s.x = 1.02f + Random.nextFloat() * 0.28f
        s.y = -0.1f + Random.nextFloat() * 1.15f
    }
}
