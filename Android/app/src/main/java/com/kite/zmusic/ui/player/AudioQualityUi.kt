package com.kite.zmusic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome
import kotlin.math.min

/**
 * 竖屏底栏音质钮：与曲谱同框线语言，内里是四柱电平。
 */
@Composable
fun TransportQualityIcon(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    tint: Color = Color(0xFFD5DEE8),
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = min(w, h) * 0.10f,
            cap = StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        val left = w * 0.14f
        val right = w * 0.86f
        val top = h * 0.18f
        val bottom = h * 0.82f
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = stroke,
        )
        drawQualityMeter(
            color = tint,
            left = left + w * 0.12f,
            right = right - w * 0.12f,
            top = top + h * 0.14f,
            bottom = bottom - h * 0.12f,
            lit = 4,
            total = 4,
        )
    }
}

@Composable
fun NowPlayingQualityIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(width = 32.dp, height = 28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TransportQualityIcon()
    }
}

@Composable
fun AudioQualityGrid(
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val qualities = AudioQuality.entries
    val rows = qualities.chunked(3)
    val gap = if (compact) 6.dp else 8.dp
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { q ->
                    AudioQualityTile(
                        quality = q,
                        selected = q == selected,
                        compact = compact,
                        onClick = { onSelect(q) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun AudioQualityGroupedList(
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        AudioQualityGroup(
            label = "有损",
            items = listOf(AudioQuality.STANDARD, AudioQuality.HIGHER, AudioQuality.EXHIGH),
            selected = selected,
            onSelect = onSelect,
        )
        Spacer(Modifier.height(18.dp))
        AudioQualityGroup(
            label = "无损",
            items = listOf(AudioQuality.LOSSLESS, AudioQuality.HIRES),
            selected = selected,
            onSelect = onSelect,
        )
        Spacer(Modifier.height(18.dp))
        AudioQualityGroup(
            label = "空间与母带",
            items = listOf(
                AudioQuality.JYEFFECT,
                AudioQuality.SKY,
                AudioQuality.DOLBY,
                AudioQuality.JYMASTER,
            ),
            selected = selected,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AudioQualityGroup(
    label: String,
    items: List<AudioQuality>,
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
) {
    Text(
        text = label,
        style = TextStyle(
            color = MainPalette.Secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        ),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp), MainPalette.Card),
    ) {
        items.forEachIndexed { index, q ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp)
                        .height(0.5.dp)
                        .background(MainPalette.Hairline),
                )
            }
            AudioQualitySettingsRow(
                quality = q,
                selected = q == selected,
                onClick = { onSelect(q) },
            )
        }
    }
}

@Composable
private fun AudioQualitySettingsRow(
    quality: AudioQuality,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MainPalette.Accent else MainPalette.Ink
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .background(if (selected) MainPalette.Accent.copy(alpha = if (MainPalette.isDark) 0.16f else 0.06f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) MainPalette.Accent.copy(alpha = 0.14f)
                    else MainPalette.Placeholder,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AudioQualityMark(quality = quality, tint = tint, size = 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = quality.title,
                style = TextStyle(
                    color = tint,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = quality.caption,
                style = TextStyle(
                    color = if (selected) MainPalette.Accent.copy(alpha = 0.72f) else MainPalette.Secondary,
                    fontSize = 12.sp,
                ),
            )
        }
        if (selected) {
            Text(
                text = "当前",
                style = TextStyle(
                    color = MainPalette.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun AudioQualityTile(
    quality: AudioQuality,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    val titleColor = if (selected) MainPalette.Accent else MainPalette.Ink
    val tileBg = if (selected) {
        MainPalette.Accent.copy(alpha = if (MainPalette.isDark) 0.22f else 0.10f)
    } else {
        MainPalette.Card
    }
    Column(
        modifier
            .clip(shape)
            .background(tileBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(
                horizontal = if (compact) 4.dp else 8.dp,
                vertical = if (compact) 8.dp else 12.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AudioQualityMark(
            quality = quality,
            tint = titleColor,
            size = if (compact) 16.dp else 18.dp,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            text = if (compact) quality.compactTitle else quality.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = titleColor,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
        if (!compact) {
            Text(
                text = quality.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = if (selected) MainPalette.Accent.copy(alpha = 0.72f) else MainPalette.Secondary,
                    fontSize = 10.sp,
                ),
            )
        }
    }
}

@Composable
private fun AudioQualityMark(
    quality: AudioQuality,
    tint: Color,
    size: Dp,
) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = min(w, h) * 0.11f,
            cap = StrokeCap.Round,
        )
        when (quality) {
            AudioQuality.STANDARD,
            AudioQuality.HIGHER,
            AudioQuality.EXHIGH,
            AudioQuality.LOSSLESS,
            -> {
                val lit = when (quality) {
                    AudioQuality.STANDARD -> 1
                    AudioQuality.HIGHER -> 2
                    AudioQuality.EXHIGH -> 3
                    else -> 4
                }
                drawQualityMeter(tint, w * 0.12f, w * 0.88f, h * 0.18f, h * 0.86f, lit, 4)
            }
            AudioQuality.HIRES -> {
                drawQualityMeter(tint, w * 0.08f, w * 0.72f, h * 0.22f, h * 0.86f, 4, 4)
                drawCircle(tint, min(w, h) * 0.09f, Offset(w * 0.84f, h * 0.22f))
            }
            AudioQuality.JYEFFECT,
            AudioQuality.SKY,
            -> {
                val cx = w / 2f
                val cy = h * 0.58f
                val rings = if (quality == AudioQuality.SKY) 3 else 2
                for (i in 1..rings) {
                    val r = min(w, h) * (0.16f + i * 0.14f)
                    drawArc(
                        color = tint.copy(alpha = 0.45f + i * 0.18f),
                        startAngle = 210f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = stroke,
                    )
                }
                drawCircle(tint, min(w, h) * 0.08f, Offset(cx, cy))
            }
            AudioQuality.DOLBY -> {
                val r = min(w, h) * 0.22f
                drawCircle(
                    color = tint,
                    radius = r,
                    center = Offset(w * 0.38f, h * 0.5f),
                    style = stroke,
                )
                drawCircle(
                    color = tint,
                    radius = r,
                    center = Offset(w * 0.62f, h * 0.5f),
                    style = stroke,
                )
            }
            AudioQuality.JYMASTER -> {
                val cx = w / 2f
                val cy = h / 2f
                val r = min(w, h) * 0.38f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r * 0.72f, cy)
                    lineTo(cx, cy + r)
                    lineTo(cx - r * 0.72f, cy)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawCircle(tint, min(w, h) * 0.08f, Offset(cx, cy))
            }
        }
    }
}

private fun DrawScope.drawQualityMeter(
    color: Color,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    lit: Int,
    total: Int,
) {
    val n = total.coerceAtLeast(1)
    val gap = (right - left) * 0.12f
    val barW = ((right - left) - gap * (n - 1)) / n
    val span = (bottom - top).coerceAtLeast(1f)
    for (i in 0 until n) {
        val t = (i + 1) / n.toFloat()
        val barH = span * (0.38f + 0.62f * t)
        val x = left + i * (barW + gap)
        val y = bottom - barH
        val alpha = if (i < lit) 1f else 0.22f
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(x, y),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barW * 0.35f, barW * 0.35f),
        )
    }
}
