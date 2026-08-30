package com.kite.zmusic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val IconTint = Color(0xFFD5DEE8)
private val ChromeBarBg = Color.Black.copy(alpha = 0.22f)
private val ChromeShape = RoundedCornerShape(8.dp)

internal val NowPlayingChromeIconWidth = 40.dp
internal val NowPlayingChromeIconHeight = 34.dp
internal val NowPlayingChromeIconGap = NowPlayingChromeIconWidth / 3

@Composable
private fun ChromeIconShell(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(width = NowPlayingChromeIconWidth, height = NowPlayingChromeIconHeight)
            .semantics { contentDescription = description }
            .clip(ChromeShape)
            .background(ChromeBarBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
internal fun NowPlayingSettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChromeIconShell(onClick = onClick, description = "设置", modifier = modifier) {
        Canvas(Modifier.size(18.dp)) {
            val strokeW = size.minDimension * 0.11f
            val w = size.width
            val h = size.height
            val trackLen = w * 0.72f
            val left = (w - trackLen) / 2f
            val knobR = size.minDimension * 0.11f
            val rows = floatArrayOf(0.22f, 0.50f, 0.78f)
            val knobs = floatArrayOf(0.62f, 0.32f, 0.74f)
            for (i in rows.indices) {
                val y = h * rows[i]
                drawLine(
                    color = IconTint,
                    start = Offset(left, y),
                    end = Offset(left + trackLen, y),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = IconTint, radius = knobR, center = Offset(left + trackLen * knobs[i], y))
            }
        }
    }
}

@Composable
internal fun NowPlayingDismissIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChromeIconShell(onClick = onClick, description = "返回", modifier = modifier) {
        Canvas(Modifier.size(18.dp)) {
            val sw = size.minDimension * 0.12f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val halfRad = Math.toRadians(48.0)
            val arm = size.minDimension * 0.34f
            val tipY = cy + arm * 0.42f
            val topY = tipY - arm * cos(halfRad).toFloat()
            val dx = arm * sin(halfRad).toFloat()
            drawLine(
                color = IconTint,
                start = Offset(cx - dx, topY),
                end = Offset(cx, tipY),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = IconTint,
                start = Offset(cx + dx, topY),
                end = Offset(cx, tipY),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
        }
    }
}
