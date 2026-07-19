package com.kite.zmusic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kite.zmusic.playback.PlaybackMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val IconTint = Color(0xFFB8C5D4)

/**
 * 简约矢量播放模式图标：列表循环 / 单曲循环 / 随机。
 */
@Composable
fun PlaybackModeControl(
    mode: PlaybackMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    circleSize: Dp = 32.dp,
    tint: Color = IconTint,
) {
    Box(
        modifier = modifier
            .size(circleSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(circleSize * 0.52f)) {
            val stroke = Stroke(
                width = size.minDimension * 0.11f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            when (mode) {
                PlaybackMode.ORDER -> drawRepeatAll(tint, stroke)
                PlaybackMode.REPEAT_ONE -> drawRepeatOne(tint, stroke)
                PlaybackMode.SHUFFLE -> drawShuffle(tint, stroke)
            }
        }
    }
}

private fun DrawScope.drawRepeatAll(color: Color, stroke: Stroke) {
    val r = size.minDimension * 0.38f
    val c = Offset(size.width / 2f, size.height / 2f)
    // 上弧 + 右箭头
    drawArc(
        color = color,
        startAngle = 200f,
        sweepAngle = 200f,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2f, r * 2f),
        style = stroke,
    )
    val tip = Offset(c.x + r * 0.15f, c.y - r)
    drawArrowHead(tip, angleDeg = -20f, color, stroke.width * 2.2f)
}

private fun DrawScope.drawRepeatOne(color: Color, stroke: Stroke) {
    drawRepeatAll(color, stroke)
    // 中心「1」
    val cx = size.width / 2f
    val cy = size.height / 2f
    val h = size.minDimension * 0.28f
    val w = size.minDimension * 0.06f
    drawLine(
        color = color,
        start = Offset(cx - w * 0.8f, cy - h * 0.35f),
        end = Offset(cx, cy - h * 0.55f),
        strokeWidth = w,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(cx, cy - h * 0.55f),
        end = Offset(cx, cy + h * 0.55f),
        strokeWidth = w,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawShuffle(color: Color, stroke: Stroke) {
    val pad = size.minDimension * 0.08f
    val left = pad
    val right = size.width - pad
    val top = size.height * 0.28f
    val bot = size.height * 0.72f
    val midX = size.width * 0.5f

    // 上交叉
    drawLine(color, Offset(left, top), Offset(midX - pad * 0.4f, top), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(midX + pad * 0.4f, bot), Offset(right - pad, bot), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(midX - pad * 0.4f, top), Offset(midX + pad * 0.4f, bot), stroke.width, StrokeCap.Round)
    drawArrowHead(Offset(right - pad * 0.15f, bot), 0f, color, stroke.width * 2.1f)

    // 下交叉
    drawLine(color, Offset(left, bot), Offset(midX - pad * 0.4f, bot), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(midX + pad * 0.4f, top), Offset(right - pad, top), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(midX - pad * 0.4f, bot), Offset(midX + pad * 0.4f, top), stroke.width, StrokeCap.Round)
    drawArrowHead(Offset(right - pad * 0.15f, top), 0f, color, stroke.width * 2.1f)
}

private fun DrawScope.drawArrowHead(
    tip: Offset,
    angleDeg: Float,
    color: Color,
    sizePx: Float,
) {
    val rad = angleDeg * (PI.toFloat() / 180f)
    val back = sizePx * 0.85f
    val spread = sizePx * 0.55f
    val dir = Offset(cos(rad), sin(rad))
    val perp = Offset(-dir.y, dir.x)
    val base = tip - dir * back
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + perp.x * spread, base.y + perp.y * spread)
        lineTo(base.x - perp.x * spread, base.y - perp.y * spread)
        close()
    }
    drawPath(path, color)
}

/** 上一首 / 下一首简约三角图标 */
@Composable
fun TransportSkipIcon(
    forward: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = IconTint,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bar = w * 0.14f
        val path = Path()
        if (forward) {
            path.moveTo(w * 0.12f, h * 0.18f)
            path.lineTo(w * 0.12f, h * 0.82f)
            path.lineTo(w * 0.58f, h * 0.5f)
            path.close()
            drawPath(path, tint)
            drawRect(
                color = tint,
                topLeft = Offset(w * 0.68f, h * 0.18f),
                size = Size(bar, h * 0.64f),
            )
        } else {
            path.moveTo(w * 0.88f, h * 0.18f)
            path.lineTo(w * 0.88f, h * 0.82f)
            path.lineTo(w * 0.42f, h * 0.5f)
            path.close()
            drawPath(path, tint)
            drawRect(
                color = tint,
                topLeft = Offset(w * 0.18f, h * 0.18f),
                size = Size(bar, h * 0.64f),
            )
        }
    }
}

/** 播放 / 暂停简约图标 */
@Composable
fun TransportPlayPauseIcon(
    playing: Boolean,
    buffering: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = Color(0xFFF2F5F8),
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (buffering) {
            val r = min(w, h) * 0.12f
            val cy = h / 2f
            drawCircle(tint.copy(alpha = 0.45f), r, Offset(w * 0.28f, cy))
            drawCircle(tint.copy(alpha = 0.75f), r, Offset(w * 0.5f, cy))
            drawCircle(tint, r, Offset(w * 0.72f, cy))
            return@Canvas
        }
        if (playing) {
            val barW = w * 0.18f
            val gap = w * 0.16f
            val left = (w - barW * 2 - gap) / 2f
            drawRect(tint, Offset(left, h * 0.18f), Size(barW, h * 0.64f))
            drawRect(tint, Offset(left + barW + gap, h * 0.18f), Size(barW, h * 0.64f))
        } else {
            val path = Path().apply {
                moveTo(w * 0.32f, h * 0.16f)
                lineTo(w * 0.32f, h * 0.84f)
                lineTo(w * 0.82f, h * 0.5f)
                close()
            }
            drawPath(path, tint)
        }
    }
}
