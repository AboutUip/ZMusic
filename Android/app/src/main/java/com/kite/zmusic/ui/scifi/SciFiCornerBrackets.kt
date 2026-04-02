package com.kite.zmusic.ui.scifi

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BracketCyan = Color(0xFF00FFD1).copy(alpha = 0.35f)

/**
 * 全屏四角装饰线（主壳、占位等复用）。
 */
@Composable
fun CornerBrackets(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val L = 36.dp.toPx()
        val w = size.width
        val h = size.height
        val t = 1.5.dp.toPx()
        val c = BracketCyan
        drawLine(c, Offset(0f, 0f), Offset(L, 0f), strokeWidth = t)
        drawLine(c, Offset(0f, 0f), Offset(0f, L), strokeWidth = t)
        drawLine(c, Offset(w, 0f), Offset(w - L, 0f), strokeWidth = t)
        drawLine(c, Offset(w, 0f), Offset(w, L), strokeWidth = t)
        drawLine(c, Offset(0f, h), Offset(L, h), strokeWidth = t)
        drawLine(c, Offset(0f, h), Offset(0f, h - L), strokeWidth = t)
        drawLine(c, Offset(w, h), Offset(w - L, h), strokeWidth = t)
        drawLine(c, Offset(w, h), Offset(w, h - L), strokeWidth = t)
    }
}
