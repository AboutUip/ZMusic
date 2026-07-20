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

private val IconTint = Color(0xFFE8EEF5)

/**
 * 1:1 复刻 Apple Music（SF Symbols `repeat` / `repeat.1` / `shuffle`）。
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
        Canvas(Modifier.size(circleSize * 0.70f)) {
            val sw = size.minDimension * 0.100f
            val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (mode) {
                PlaybackMode.ORDER -> drawAppleRepeat(tint, stroke, showOne = false)
                PlaybackMode.REPEAT_ONE -> drawAppleRepeat(tint, stroke, showOne = true)
                PlaybackMode.SHUFFLE -> drawAppleShuffle(tint, stroke)
            }
        }
    }
}

/**
 * 列表循环 / 单曲循环：四向箭头闭环（上→ 右↓ 下← 左↑），角上留缝不粘连。
 * 单曲循环仅在中心加更小的「1」。
 */
private fun DrawScope.drawAppleRepeat(color: Color, stroke: Stroke, showOne: Boolean) {
    val m = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val sw = stroke.width
    val hw = m * 0.32f
    val hh = m * 0.26f
    // 箭头缩短 + 四角留缝，避免首尾相连成闭环实线
    val arrow = sw * 1.45f
    val gap = m * 0.11f

    val left = cx - hw
    val right = cx + hw
    val top = cy - hh
    val bot = cy + hh

    // 上：左 → 右
    drawLine(
        color,
        Offset(left + gap, top),
        Offset(right - gap - arrow * 0.35f, top),
        sw,
        StrokeCap.Round,
    )
    drawFilledChevron(Offset(right - gap * 0.22f, top), 0f, color, arrow)

    // 右：上 → 下
    drawLine(
        color,
        Offset(right, top + gap),
        Offset(right, bot - gap - arrow * 0.35f),
        sw,
        StrokeCap.Round,
    )
    drawFilledChevron(Offset(right, bot - gap * 0.22f), 90f, color, arrow)

    // 下：右 → 左
    drawLine(
        color,
        Offset(right - gap, bot),
        Offset(left + gap + arrow * 0.35f, bot),
        sw,
        StrokeCap.Round,
    )
    drawFilledChevron(Offset(left + gap * 0.22f, bot), 180f, color, arrow)

    // 左：下 → 上
    drawLine(
        color,
        Offset(left, bot - gap),
        Offset(left, top + gap + arrow * 0.35f),
        sw,
        StrokeCap.Round,
    )
    drawFilledChevron(Offset(left, top + gap * 0.22f), 270f, color, arrow)

    if (showOne) {
        val oneSw = sw * 0.88f
        val ox = cx + m * 0.01f
        val top1 = cy - m * 0.11f
        val bot1 = cy + m * 0.125f
        drawLine(color, Offset(ox - m * 0.045f, top1 + m * 0.04f), Offset(ox, top1), oneSw, StrokeCap.Round)
        drawLine(color, Offset(ox, top1), Offset(ox, bot1), oneSw, StrokeCap.Round)
        drawLine(
            color,
            Offset(ox - m * 0.055f, bot1),
            Offset(ox + m * 0.055f, bot1),
            oneSw * 0.92f,
            StrokeCap.Round,
        )
    }
}

/**
 * Apple Music `shuffle`：左两水平 stub，中段交叉换轨，右端两实心箭头。
 */
private fun DrawScope.drawAppleShuffle(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val sw = stroke.width
    val arrow = sw * 2.45f

    fun x(v: Float) = v / 24f * w
    fun y(v: Float) = v / 24f * h

    val topY = y(7f)
    val botY = y(17f)
    val left = x(2.5f)
    val midA = x(8.5f)
    val midB = x(15.5f)
    val right = x(18.5f)

    // 上路 → 右下
    val upper = Path().apply {
        moveTo(left, topY)
        lineTo(midA, topY)
        cubicTo(
            midA + (midB - midA) * 0.28f, topY,
            midB - (midB - midA) * 0.28f, botY,
            midB, botY,
        )
        lineTo(right, botY)
    }
    drawPath(upper, color, style = stroke)
    drawFilledChevron(Offset(x(21.8f), botY), 0f, color, arrow)

    // 下路 → 右上
    val lower = Path().apply {
        moveTo(left, botY)
        lineTo(midA, botY)
        cubicTo(
            midA + (midB - midA) * 0.28f, botY,
            midB - (midB - midA) * 0.28f, topY,
            midB, topY,
        )
        lineTo(right, topY)
    }
    drawPath(lower, color, style = stroke)
    drawFilledChevron(Offset(x(21.8f), topY), 0f, color, arrow)
}

private fun DrawScope.drawFilledChevron(
    tip: Offset,
    angleDeg: Float,
    color: Color,
    sizePx: Float,
) {
    val rad = angleDeg * (PI.toFloat() / 180f)
    val back = sizePx * 0.92f
    val spread = sizePx * 0.56f
    val dir = Offset(cos(rad), sin(rad))
    val perp = Offset(-dir.y, dir.x)
    val base = Offset(tip.x - dir.x * back, tip.y - dir.y * back)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + perp.x * spread, base.y + perp.y * spread)
        lineTo(base.x - perp.x * spread, base.y - perp.y * spread)
        close()
    }
    drawPath(path, color)
}

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
            drawRect(tint, Offset(w * 0.68f, h * 0.18f), Size(bar, h * 0.64f))
        } else {
            path.moveTo(w * 0.88f, h * 0.18f)
            path.lineTo(w * 0.88f, h * 0.82f)
            path.lineTo(w * 0.42f, h * 0.5f)
            path.close()
            drawPath(path, tint)
            drawRect(tint, Offset(w * 0.18f, h * 0.18f), Size(bar, h * 0.64f))
        }
    }
}

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
