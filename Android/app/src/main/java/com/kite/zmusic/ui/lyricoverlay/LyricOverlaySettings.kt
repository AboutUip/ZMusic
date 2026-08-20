package com.kite.zmusic.ui.lyricoverlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.LyricOverlayPrefs
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.theme.MainColors
import com.kite.zmusic.ui.theme.MainPalette

internal val OverlayColorPresets = intArrayOf(
    0xFFFFFFFF.toInt(),
    0xB3FFFFFF.toInt(),
    0x80FFFFFF.toInt(),
    0xFFFFE082.toInt(),
    0xFF90CAF9.toInt(),
    0xFF80CBC4.toInt(),
    0xFFEF9A9A.toInt(),
    0xFFCE93D8.toInt(),
    0xFFA5D6A7.toInt(),
    0xFFEC4141.toInt(),
)

private val SectionAnim = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
private val SectionExpand = expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing))
private val SectionShrink = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing))

@Composable
internal fun LyricOverlaySettingsPanel(
    prefs: LyricOverlayPrefs,
    onChange: (LyricOverlayPrefs) -> Unit,
    onCenterHorizontally: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = MainPalette.Accent,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = MainColors.Dark.trackOff,
        uncheckedBorderColor = Color.Transparent,
        checkedBorderColor = Color.Transparent,
    )
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = MainPalette.Accent,
        inactiveTrackColor = MainColors.Dark.trackOff,
    )
    var openFolder by remember { mutableStateOf<OverlayFolder?>(OverlayFolder.Lyrics) }
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF2141418))
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        SettingsFolder(
            title = "歌词",
            expanded = openFolder == OverlayFolder.Lyrics,
            onToggle = { openFolder = if (openFolder == OverlayFolder.Lyrics) null else OverlayFolder.Lyrics },
        ) {
            StepperRow(
                title = "已播行",
                value = prefs.playedLines,
                onChange = {
                    onChange(prefs.copy(playedLines = it.coerceIn(LyricOverlayPrefs.LINES_MIN, LyricOverlayPrefs.LINES_MAX)))
                },
            )
            StepperRow(
                title = "未播行",
                value = prefs.upcomingLines,
                onChange = {
                    onChange(prefs.copy(upcomingLines = it.coerceIn(LyricOverlayPrefs.LINES_MIN, LyricOverlayPrefs.LINES_MAX)))
                },
            )
            Label("窗内对齐")
            AlignPicker(selected = prefs.textAlign) { onChange(prefs.copy(textAlign = it)) }
            Label("字号 ${prefs.fontSizeSp.toInt()} sp")
            Slider(
                value = prefs.fontSizeSp,
                onValueChange = { onChange(prefs.copy(fontSizeSp = it)) },
                valueRange = LyricOverlayPrefs.FONT_MIN..LyricOverlayPrefs.FONT_MAX,
                colors = sliderColors,
            )
        }
        SettingsFolder(
            title = "颜色",
            expanded = openFolder == OverlayFolder.Color,
            onToggle = { openFolder = if (openFolder == OverlayFolder.Color) null else OverlayFolder.Color },
        ) {
            ColorRow("已播", prefs.playedColorArgb) { onChange(prefs.copy(playedColorArgb = it)) }
            ColorRow("当前", prefs.currentColorArgb) { onChange(prefs.copy(currentColorArgb = it)) }
            ColorRow("未播", prefs.upcomingColorArgb) { onChange(prefs.copy(upcomingColorArgb = it)) }
        }
        SettingsFolder(
            title = "窗口",
            expanded = openFolder == OverlayFolder.Window,
            onToggle = { openFolder = if (openFolder == OverlayFolder.Window) null else OverlayFolder.Window },
        ) {
            ActionRow("窗口居中", ZIcons.AlignHorizontalCenter, onCenterHorizontally)
            SwitchRow("悬浮窗背景", prefs.windowBackground, switchColors) {
                onChange(prefs.copy(windowBackground = it))
            }
            AnimatedVisibility(
                visible = prefs.windowBackground,
                enter = fadeIn(SectionAnim) + SectionExpand,
                exit = fadeOut(SectionAnim) + SectionShrink,
            ) {
                Column {
                    val blurPct = (prefs.blurRadiusPx * 100f / LyricOverlayPrefs.BLUR_MAX).toInt()
                    Label("磨砂 $blurPct%")
                    Slider(
                        value = prefs.blurRadiusPx.toFloat(),
                        onValueChange = { onChange(prefs.copy(blurRadiusPx = it.toInt())) },
                        valueRange = LyricOverlayPrefs.BLUR_MIN.toFloat()..LyricOverlayPrefs.BLUR_MAX.toFloat(),
                        colors = sliderColors,
                    )
                }
            }
            SwitchRow("歌词背景", prefs.lyricBackground, switchColors) {
                onChange(prefs.copy(lyricBackground = it))
            }
            SwitchRow("动态宽度", prefs.dynamicWidth, switchColors) {
                onChange(prefs.copy(dynamicWidth = it))
            }
            AnimatedVisibility(
                visible = !prefs.dynamicWidth,
                enter = fadeIn(SectionAnim) + SectionExpand,
                exit = fadeOut(SectionAnim) + SectionShrink,
            ) {
                Column {
                    Label("宽度 ${prefs.widthDp} dp")
                    Slider(
                        value = prefs.widthDp.toFloat(),
                        onValueChange = { onChange(prefs.copy(widthDp = it.toInt())) },
                        valueRange = LyricOverlayPrefs.WIDTH_MIN_DP.toFloat()..LyricOverlayPrefs.WIDTH_MAX_DP.toFloat(),
                        colors = sliderColors,
                    )
                }
            }
            SwitchRow("侵入状态栏 / 摄像头", prefs.ignoreCutout, switchColors) {
                onChange(prefs.copy(ignoreCutout = it))
            }
        }
    }
}

private enum class OverlayFolder { Lyrics, Color, Window }

@Composable
private fun SettingsFolder(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rot by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "folderChevron",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = ZIcons.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = Color(0xCCFFFFFF),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rot),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(SectionAnim) + SectionExpand,
            exit = fadeOut(SectionAnim) + SectionShrink,
        ) {
            Column(Modifier.padding(bottom = 8.dp)) { content() }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = TextStyle(color = Color(0xCCFFFFFF), fontSize = 12.sp),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(color = Color.White, fontSize = 13.sp),
            modifier = Modifier.weight(1f),
        )
        Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    colors: androidx.compose.material3.SwitchColors,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(color = Color.White, fontSize = 13.sp),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = colors)
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(color = Color.White, fontSize = 13.sp),
            modifier = Modifier.weight(1f),
        )
        StepperButton(ZIcons.Remove, "减少") { onChange(value - 1) }
        Text(
            text = "$value",
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        StepperButton(ZIcons.Add, "增加") { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0x33FFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun AlignPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AlignChip(ZIcons.FormatAlignLeft, "左对齐", selected == LyricOverlayPrefs.ALIGN_LEFT) {
            onSelect(LyricOverlayPrefs.ALIGN_LEFT)
        }
        AlignChip(ZIcons.FormatAlignCenter, "居中", selected == LyricOverlayPrefs.ALIGN_CENTER) {
            onSelect(LyricOverlayPrefs.ALIGN_CENTER)
        }
        AlignChip(ZIcons.FormatAlignRight, "右对齐", selected == LyricOverlayPrefs.ALIGN_RIGHT) {
            onSelect(LyricOverlayPrefs.ALIGN_RIGHT)
        }
    }
}

@Composable
private fun RowScope.AlignChip(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Color(0x33FFFFFF) else Color(0x14FFFFFF))
            .then(if (on) Modifier.border(1.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ColorRow(
    title: String,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, style = TextStyle(color = Color(0xCCFFFFFF), fontSize = 12.sp))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OverlayColorPresets.forEach { argb ->
                val on = argb == selected
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .then(
                            if (on) Modifier.border(1.5.dp, Color.White, CircleShape)
                            else Modifier.border(0.5.dp, Color(0x66FFFFFF), CircleShape),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(argb) },
                        ),
                )
            }
        }
    }
}
