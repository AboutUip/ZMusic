package com.kite.zmusic.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.RealtimeCacheMode
import com.kite.zmusic.data.RealtimeCacheOccupancy
import com.kite.zmusic.data.RealtimeCacheSpaceUnit
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassPromptField
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainControls
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome
import java.util.Locale
import kotlin.math.min

private val CardShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(12.dp)

@Composable
fun RealtimeCacheSettingsPage(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    mode: RealtimeCacheMode,
    onModeChange: (RealtimeCacheMode) -> Unit,
    spaceValue: Double,
    spaceUnit: RealtimeCacheSpaceUnit,
    onSpaceChange: (Double, RealtimeCacheSpaceUnit) -> Unit,
    occupancy: RealtimeCacheOccupancy,
    onClearCache: () -> Unit,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val switchColors = MainControls.switchColors()
    var spaceText by remember { mutableStateOf(formatSpaceValue(spaceValue)) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(spaceValue, spaceUnit) {
        spaceText = formatSpaceValue(spaceValue)
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "谨慎模式按一周播放采样，满一周后把高于 6.5 分的歌下载到应用本地。实时模式边听边下载，只看这一次：听满 60%（6 分）就留下，不到就删。激进模式一首就边听边下、不评分；占满后按累计听次淘汰听得少的；每周清掉一周没听过的。音质不同算不同条目。不是 Download 目录。关闭后不再采样、也不再用这套缓存播放。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .wallpaperItemChrome(CardShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onEnabledChange(!enabled) },
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "实时缓存",
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = if (enabled) {
                        "已开启 · ${mode.title}模式"
                    } else {
                        "已关闭 · 不采样不走本地缓存"
                    },
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = switchColors,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "模式",
            style = sectionTitle(),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RealtimeCacheMode.entries.forEach { item ->
                ModeChip(
                    title = item.title,
                    selected = mode == item,
                    enabled = item.available,
                    onClick = { onModeChange(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "占用",
            style = sectionTitle(),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        OccupancyCard(occupancy = occupancy, mode = mode)
        Spacer(Modifier.height(18.dp))
        Text(
            text = "空间上限",
            style = sectionTitle(),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .wallpaperItemChrome(CardShape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = "数值和单位都可以改。写入时按这个上限实时卡住，超出就从评分低的开始排除。",
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
            Spacer(Modifier.height(12.dp))
            GlassPromptField(
                value = spaceText,
                onValueChange = { raw ->
                    val next = raw.filter { it.isDigit() || it == '.' }
                    spaceText = next
                    parseSpaceValue(next)?.let { onSpaceChange(it, spaceUnit) }
                },
                placeholder = "512",
                maxLength = 8,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RealtimeCacheSpaceUnit.entries.forEach { unit ->
                    UnitChip(
                        title = unit.title,
                        selected = spaceUnit == unit,
                        onClick = {
                            if (unit == spaceUnit) return@UnitChip
                            val bytes = occupancy.limitBytes
                            val converted = bytes.toDouble() / unit.multiplier.toDouble()
                            onSpaceChange(converted.coerceAtLeast(0.0), unit)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .wallpaperItemChrome(CardShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (occupancy.fileCount > 0) confirmClear = true },
                )
                .alpha(if (occupancy.fileCount > 0) 1f else 0.45f)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "清空缓存",
                style = TextStyle(
                    color = MainPalette.Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (occupancy.fileCount > 0) "${occupancy.fileCount} 首" else "空",
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 13.sp,
                ),
            )
        }
        Text(
            text = "只清应用内音频文件，播放原数据不会删。",
            style = TextStyle(
                color = MainPalette.Hint,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
    if (confirmClear) {
        GlassAlertDialog(
            title = "清空实时缓存",
            message = "会删除应用本地已下载的缓存音频，播放记录仍保留。",
            confirmLabel = "清空",
            confirmDestructive = true,
            onConfirm = {
                confirmClear = false
                onClearCache()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun OccupancyCard(occupancy: RealtimeCacheOccupancy, mode: RealtimeCacheMode) {
    val ratio = if (occupancy.limitBytes <= 0L) {
        0f
    } else {
        min(1.0, occupancy.usedBytes.toDouble() / occupancy.limitBytes.toDouble()).toFloat()
    }
    val track = MainPalette.TrackOff
    val accent = MainPalette.Accent
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(CardShape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(72.dp)) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (ratio > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * ratio,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Storage,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${formatBytes(occupancy.usedBytes)} / ${formatBytes(occupancy.limitBytes)}",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = when {
                    occupancy.downloading &&
                        (mode == RealtimeCacheMode.Realtime || mode == RealtimeCacheMode.Aggressive) ->
                        "正在边听边缓存"
                    occupancy.downloading -> "正在补下载"
                    occupancy.enabled && mode == RealtimeCacheMode.Aggressive ->
                        "激进缓存 · ${occupancy.fileCount} 首"
                    occupancy.enabled && mode == RealtimeCacheMode.Realtime ->
                        "边听边缓存 · ${occupancy.fileCount} 首"
                    occupancy.enabled && occupancy.canDownload ->
                        "已用 ${(ratio * 100).toInt()}% · ${occupancy.fileCount} 首"
                    occupancy.enabled -> "采样未满一周，暂不下载"
                    else -> "功能已关闭 · 占用仍显示"
                },
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
    }
}

@Composable
private fun ModeChip(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .then(
                if (selected && enabled) {
                    Modifier.clip(ChipShape).background(MainPalette.Accent.copy(alpha = 0.18f))
                } else {
                    Modifier.wallpaperItemChrome(ChipShape, MainPalette.Card)
                },
            )
            .alpha(if (enabled) 1f else 0.42f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = if (selected && enabled) MainPalette.Accent else MainPalette.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
        )
        if (!enabled) {
            Text(
                text = "暂不可用",
                style = TextStyle(
                    color = MainPalette.Hint,
                    fontSize = 10.sp,
                ),
            )
        }
    }
}

@Composable
private fun UnitChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = title,
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier.clip(ChipShape).background(MainPalette.Accent.copy(alpha = 0.18f))
                } else {
                    Modifier.wallpaperItemChrome(ChipShape, MainPalette.Card)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = TextStyle(
            color = if (selected) MainPalette.Accent else MainPalette.Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        ),
    )
}

@Composable
private fun sectionTitle() = TextStyle(
    color = MainPalette.Secondary,
    fontSize = 13.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.4.sp,
)

private fun formatBytes(bytes: Long): String {
    val gb = 1024.0 * 1024.0 * 1024.0
    val mb = 1024.0 * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatSpaceValue(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString()
    else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

private fun parseSpaceValue(raw: String): Double? {
    val t = raw.trim()
    if (t.isEmpty() || t == ".") return null
    return t.toDoubleOrNull()?.takeIf { it >= 0.0 }
}
