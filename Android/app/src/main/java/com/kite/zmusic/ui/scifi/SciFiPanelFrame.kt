package com.kite.zmusic.ui.scifi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val FrameCyan = Color(0xFF00FFD1)

/**
 * 内容区 HUD 面板：淡底 + 四角装饰线，用于横屏登录等居中内容区。
 */
@Composable
fun SciFiPanelFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .drawBehind {
                val w = size.width
                val h = size.height
                val L = 22.dp.toPx()
                val t = 1.2.dp.toPx()
                val c = FrameCyan.copy(alpha = 0.38f)
                drawRect(Color(0xFF050D18).copy(alpha = 0.55f))
                drawRect(c, style = Stroke(t))
                fun corner(x0: Float, y0: Float, dx: Float, dy: Float) {
                    drawLine(c, Offset(x0, y0), Offset(x0 + dx * L, y0), strokeWidth = t * 1.2f)
                    drawLine(c, Offset(x0, y0), Offset(x0, y0 + dy * L), strokeWidth = t * 1.2f)
                }
                corner(0f, 0f, 1f, 1f)
                corner(w, 0f, -1f, 1f)
                corner(0f, h, 1f, -1f)
                corner(w, h, -1f, -1f)
            }
            .padding(20.dp),
    ) {
        content()
    }
}
