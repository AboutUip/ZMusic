package com.kite.zmusic.ui.scifi

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HudCyan = Color(0xFF00FFD1)
private val HudDim = Color(0xFF8FA8B8)
private val HudDeep = Color(0xFF0A1628)

/**
 * 登录方式等小标签：直角 HUD 角标 + 选中微光，替代默认 OutlinedButton。
 */
@Composable
fun SciFiHudChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 等宽 Tab 栅格内使用：更小的内边距与字号，避免「手机密码」等挤版。 */
    tabDense: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pulse = rememberInfiniteTransition(label = "chip_pulse")
    val pulseA by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "a",
    )
    val glowAlpha = when {
        selected -> 0.42f + 0.18f * pulseA
        pressed -> 0.35f
        else -> 0.22f
    }
    val shape = RoundedCornerShape(3.dp)
    val hPad = if (tabDense) 6.dp else 14.dp
    val vPad = if (tabDense) 11.dp else 10.dp
    val fontSize = if (tabDense) 9.sp else 10.sp
    val letter = if (tabDense) 0.2.sp else 0.6.sp
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val r = 3.dp.toPx()
                val w = size.width
                val h = size.height
                val L = (10.dp.toPx()).coerceAtMost(w * 0.28f).coerceAtMost(h * 0.45f)
                val t = 1.25.dp.toPx()
                val edge = if (selected) HudCyan.copy(alpha = glowAlpha) else HudCyan.copy(alpha = glowAlpha * 0.65f)
                val fill = if (selected) {
                    Brush.verticalGradient(
                        listOf(
                            HudCyan.copy(alpha = 0.14f),
                            HudDeep.copy(alpha = 0.5f),
                            HudCyan.copy(alpha = 0.06f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            HudDeep.copy(alpha = 0.38f),
                        ),
                    )
                }
                drawRoundRect(brush = fill, cornerRadius = CornerRadius(r, r))
                drawRoundRect(
                    color = edge,
                    style = Stroke(width = t),
                    cornerRadius = CornerRadius(r, r),
                )
                fun cornerL(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
                    drawLine(edge, Offset(x0, y0), Offset(x1, y1), strokeWidth = t * 1.35f)
                    drawLine(edge, Offset(x0, y0), Offset(x2, y2), strokeWidth = t * 1.35f)
                }
                val inset = 2.2.dp.toPx()
                cornerL(inset, inset, inset + L, inset, inset, inset + L)
                cornerL(w - inset, inset, w - inset - L, inset, w - inset, inset + L)
                cornerL(inset, h - inset, inset + L, h - inset, inset, h - inset - L)
                cornerL(w - inset, h - inset, w - inset - L, h - inset, w - inset, h - inset - L)
            }
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = hPad, vertical = vPad)
            .defaultMinSize(minHeight = if (tabDense) 40.dp else 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = if (selected) HudCyan else HudDim.copy(alpha = 0.92f),
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = letter,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

/**
 * 主操作 / 全宽按钮：顶底细线 + 角标，层次比 Chip 更强。
 */
@Composable
fun SciFiHudPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressedRaw by interaction.collectIsPressedAsState()
    val pressed = pressedRaw && enabled
    val pulse = rememberInfiniteTransition(label = "pri_pulse")
    val pulseA by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pa",
    )
    val rim = when {
        !enabled -> 0.22f
        pressed -> 0.75f
        else -> 0.5f + 0.2f * pulseA
    }
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .drawBehind {
                val r = 4.dp.toPx()
                val w = size.width
                val h = size.height
                val t = 1.35.dp.toPx()
                val L = (14.dp.toPx()).coerceAtMost(w * 0.12f)
                val cyan = HudCyan.copy(alpha = rim)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            HudCyan.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.55f),
                            HudCyan.copy(alpha = 0.05f),
                        ),
                    ),
                    cornerRadius = CornerRadius(r, r),
                )
                drawRoundRect(
                    color = cyan,
                    style = Stroke(t),
                    cornerRadius = CornerRadius(r, r),
                )
                val scanY = h * (0.25f + 0.5f * pulseA)
                drawLine(
                    color = HudCyan.copy(alpha = 0.12f),
                    start = Offset(8.dp.toPx(), scanY),
                    end = Offset(w - 8.dp.toPx(), scanY),
                    strokeWidth = 0.85.dp.toPx(),
                )
                val inset = 3.dp.toPx()
                fun tick(x0: Float, y0: Float, xh: Float, yv: Float) {
                    drawLine(cyan, Offset(x0, y0), Offset(xh, y0), strokeWidth = t * 1.45f)
                    drawLine(cyan, Offset(x0, y0), Offset(x0, yv), strokeWidth = t * 1.45f)
                }
                tick(inset, inset, inset + L, inset + L)
                tick(w - inset, inset, w - inset - L, inset + L)
                tick(inset, h - inset, inset + L, h - inset - L)
                tick(w - inset, h - inset, w - inset - L, h - inset - L)
            }
            .semantics { role = Role.Button }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = if (enabled) HudCyan else HudDim.copy(alpha = 0.55f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** 验证码「发送」等紧凑操作。 */
@Composable
fun SciFiHudCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minWidth: Dp = 72.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressedRaw by interaction.collectIsPressedAsState()
    val pressed = pressedRaw && enabled
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .defaultMinSize(minWidth = minWidth, minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(shape)
            .drawBehind {
                val r = 3.dp.toPx()
                val w = size.width
                val h = size.height
                val t = 1.2.dp.toPx()
                val L = 8.dp.toPx()
                val edge = HudCyan.copy(
                    alpha = when {
                        !enabled -> 0.18f
                        pressed -> 0.65f
                        else -> 0.4f
                    },
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.4f),
                    cornerRadius = CornerRadius(r, r),
                )
                drawRoundRect(
                    color = edge,
                    style = Stroke(t),
                    cornerRadius = CornerRadius(r, r),
                )
                val inset = 2.dp.toPx()
                drawLine(edge, Offset(inset, inset), Offset(inset + L, inset), t * 1.3f)
                drawLine(edge, Offset(inset, inset), Offset(inset, inset + L), t * 1.3f)
                drawLine(edge, Offset(w - inset, h - inset), Offset(w - inset - L, h - inset), t * 1.3f)
                drawLine(edge, Offset(w - inset, h - inset), Offset(w - inset, h - inset - L), t * 1.3f)
            }
            .semantics { role = Role.Button }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = if (enabled) HudCyan else HudDim.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            ),
        )
    }
}

@Composable
fun SciFiHudTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text = "› $text",
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        style = TextStyle(
            color = if (pressed) HudCyan else HudDim.copy(alpha = 0.85f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        ),
    )
}
