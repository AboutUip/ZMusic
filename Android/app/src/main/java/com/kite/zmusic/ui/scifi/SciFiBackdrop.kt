package com.kite.zmusic.ui.scifi

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val GridCyan = Color(0xFF00E5CC).copy(alpha = 0.06f)
private val VignetteCore = Color(0xFF0A1628)
private val AccentMagenta = Color(0xFFFF00AA).copy(alpha = 0.04f)

private data class DataParticle(
    val baseX: Float,
    val baseY: Float,
    val vx: Float,
    val vy: Float,
    val phase: Float,
    val size: Float,
)

/**
 * 科幻风底层：深空渐变、淡网格、数据粒子（供启动页与占位主界面复用）。
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun SciFiBackdrop(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "scifi")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val h = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
        val particles = remember(w, h) {
            val rnd = Random(7)
            List(96) {
                DataParticle(
                    baseX = rnd.nextFloat() * w,
                    baseY = rnd.nextFloat() * h,
                    vx = (rnd.nextFloat() - 0.5f) * 0.35f,
                    vy = (rnd.nextFloat() - 0.5f) * 0.28f,
                    phase = rnd.nextFloat() * (Math.PI * 2).toFloat(),
                    size = 1f + rnd.nextFloat() * 2.2f,
                )
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val cw = size.width
            val ch = size.height
            drawRect(Color.Black)
            val cx = cw / 2f
            val cy = ch * 0.38f
            val r = hypot(cw, ch) * 0.65f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        VignetteCore.copy(alpha = 0.95f),
                        Color(0xFF020810),
                        Color.Black,
                    ),
                    center = Offset(cx, cy),
                    radius = r,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(AccentMagenta, Color.Transparent),
                    center = Offset(cw * 0.15f, ch * 0.85f),
                    radius = ch * 0.5f,
                ),
            )

            val step = 56.dp.toPx()
            var x = 0f
            while (x <= cw) {
                drawLine(GridCyan, Offset(x, 0f), Offset(x, ch), strokeWidth = 0.8f)
                x += step
            }
            var y = 0f
            while (y <= ch) {
                drawLine(GridCyan, Offset(0f, y), Offset(cw, y), strokeWidth = 0.8f)
                y += step
            }

            val t = drift * (Math.PI * 2).toFloat()
            particles.forEach { p ->
                val ox = sin(t * 1.2f + p.phase) * 24f + p.vx * t * 40f
                val oy = cos(t * 0.9f + p.phase * 1.3f) * 20f + p.vy * t * 40f
                val px = wrapCoord(p.baseX + ox, cw)
                val py = wrapCoord(p.baseY + oy, ch)
                val pulse = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 4f + p.phase))
                val c = Color(0xFF00FFD1).copy(alpha = (0.06f + 0.14f * pulse).coerceIn(0f, 0.22f))
                drawCircle(c, radius = p.size, center = Offset(px, py))
                drawRect(
                    color = Color(0xFF66B3FF).copy(alpha = c.alpha * 0.35f),
                    topLeft = Offset(px - p.size * 0.4f, py - p.size * 0.4f),
                    size = Size(p.size * 0.8f, p.size * 0.8f),
                    style = Stroke(width = 0.6f),
                )
            }
        }
    }
}

private fun wrapCoord(v: Float, max: Float): Float {
    val r = v % max
    return if (r < 0f) r + max else r
}
