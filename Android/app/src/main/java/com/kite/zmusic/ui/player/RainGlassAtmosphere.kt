package com.kite.zmusic.ui.player

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 窗户雨效：窗外斜雨（朝向 = 速度向量，全屏覆盖）→ 磨砂玻璃。
 */
@Composable
fun RainGlassAtmosphere(modifier: Modifier = Modifier) {
    val density = LocalDensity.current

    val windAngleRad = Math.toRadians(18.0).toFloat()
    val windX = -sin(windAngleRad)
    val windY = cos(windAngleRad)

    val farStreaks = remember {
        List(140) { i -> makeStreak(Random(i * 131 + 3), far = true) }
    }
    val nearStreaks = remember {
        List(64) { i -> makeStreak(Random(i * 211 + 17), far = false) }
    }

    var tick by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
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
                stepStreaks(farStreaks, 0.72f)
                stepStreaks(nearStreaks, 1.15f)
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val frame = tick

    Box(modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= 31) {
            Canvas(Modifier.fillMaxSize().blur(2.5.dp)) {
                drawStreakLayer(
                    streaks = farStreaks,
                    windX = windX,
                    windY = windY,
                    densityScale = density.density,
                    alphaMul = 0.55f,
                )
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawStreakLayer(
                    streaks = farStreaks,
                    windX = windX,
                    windY = windY,
                    densityScale = density.density,
                    alphaMul = 0.45f,
                )
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            drawStreakLayer(
                streaks = nearStreaks,
                windX = windX,
                windY = windY,
                densityScale = density.density,
                alphaMul = 1f,
            )
        }

        FrostedWindowPane()
    }
}

@Composable
private fun FrostedWindowPane() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF9BB0C4).copy(alpha = 0.11f),
                        Color(0xFF6E849A).copy(alpha = 0.15f),
                        Color(0xFF3E4E60).copy(alpha = 0.18f),
                    ),
                ),
            )
            .background(Color.White.copy(alpha = 0.05f)),
    )
    if (Build.VERSION.SDK_INT < 31) {
        Canvas(Modifier.fillMaxSize()) {
            val rnd = Random(42)
            repeat(720) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.012f + rnd.nextFloat() * 0.024f),
                    radius = 0.6f + rnd.nextFloat() * 2.0f,
                    center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                )
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.26f),
                ),
                center = Offset(size.width * 0.5f, size.height * 0.45f),
                radius = size.maxDimension * 0.78f,
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
) {
    val w = size.width
    val h = size.height
    streaks.forEach { s ->
        val near = s.depth
        val a = s.alpha * alphaMul * (0.4f + near * 0.75f)
        val thick = s.thickness * (0.5f + near * 1.15f) * densityScale
        val lenPx = s.length * h * (0.75f + near * 0.7f)
        val x0 = s.x * w
        val y0 = s.y * h
        val x1 = x0 + windX * lenPx
        val y1 = y0 + windY * lenPx
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = a * 0.08f),
                    Color.White.copy(alpha = a),
                    Color.White.copy(alpha = a * 0.12f),
                ),
                start = Offset(x0, y0),
                end = Offset(x1, y1),
            ),
            start = Offset(x0, y0),
            end = Offset(x1, y1),
            strokeWidth = thick,
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
