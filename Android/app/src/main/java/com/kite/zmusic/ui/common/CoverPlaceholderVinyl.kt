package com.kite.zmusic.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

import com.kite.zmusic.ui.theme.MainPalette

private val VinylInk = Color(0xFF1A1A1C)
private val VinylMid = Color(0xFF2A2A2E)
private val VinylEdge = Color(0xFF121214)
private val VinylGroove = Color(0xFF000000)
private val VinylLabel get() = MainPalette.Accent

/** 封面未到位时的黑胶占位，锁住方格，避免歌名先挤进封面再跳回来。 */
@Composable
fun CoverPlaceholderVinyl(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        if (r <= 1f) return@Canvas
        drawRect(VinylEdge)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to VinylMid,
                    0.42f to VinylInk,
                    1f to VinylEdge,
                ),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )
        val rings = 9
        for (i in 1 until rings) {
            val t = i / rings.toFloat()
            drawCircle(
                color = VinylGroove.copy(alpha = 0.18f + (i % 2) * 0.06f),
                radius = r * (0.22f + t * 0.72f),
                center = c,
                style = Stroke(width = 1.1f),
            )
        }
        drawCircle(
            color = VinylLabel,
            radius = r * 0.18f,
            center = c,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = r * 0.045f,
            center = c,
        )
    }
}
