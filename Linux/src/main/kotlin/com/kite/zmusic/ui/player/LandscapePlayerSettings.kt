package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TitleAlignMode
import com.kite.zmusic.data.VinylColorStyle
import kotlin.math.roundToInt

@Composable
internal fun LandscapePlayerSettingsOverlay(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onDismiss: () -> Unit,
    onOpenLyricStyleEditor: () -> Unit = {},
    onOpenTitleStyleEditor: () -> Unit = {},
    onOpenVinylColorEditor: () -> Unit = {},
    progress: Float = 1f,
) {
    LandscapeSideSheet(progress = progress, onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE6111218))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text("播放页", color = LyricCurrent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsCategory("氛围") {
                    SettingsSwitchRow("雨夜效果", "斜雨磨砂玻璃氛围", prefs.rainNightEnabled) {
                        onPrefsChange(prefs.copy(rainNightEnabled = it))
                    }
                    SettingsSwitchRow("活跃光晕", "低/中/高互斥高亮，同时仅一球发光", prefs.activeHalo) {
                        onPrefsChange(prefs.copy(activeHalo = it))
                    }
                }
                SettingsCategory("文字") {
                    SettingsSwitchRow("动态歌词", "宽度避开黑胶，左右对称保持中心", prefs.dynamicLyrics) {
                        onPrefsChange(prefs.copy(dynamicLyrics = it))
                    }
                    SettingsSwitchRow("自动播放", "点选歌词跳转后自动开始播放", prefs.lyricTapAutoPlay) {
                        onPrefsChange(prefs.copy(lyricTapAutoPlay = it))
                    }
                    SettingsActionRow("歌词样式", "斜体 / 粗体 / 颜色 / 字号", "编辑", onOpenLyricStyleEditor)
                    SettingsActionRow("标题样式", "歌名 / 制作人 / 歌单 · 颜色与字号", "编辑", onOpenTitleStyleEditor)
                    SettingsTitleAlignRow(prefs.titleAlign) {
                        onPrefsChange(prefs.copy(titleAlign = it))
                    }
                    SettingsSliderRow("标题垂直位置", String.format("%+.0f", prefs.titleOffsetYDp), prefs.titleOffsetYDp, PlayerDisplayPrefs.TITLE_OFFSET_Y_MIN..PlayerDisplayPrefs.TITLE_OFFSET_Y_MAX) {
                        onPrefsChange(prefs.copy(titleOffsetYDp = it))
                    }
                    SettingsSliderRow("歌词行间距", String.format("%.0f", prefs.lyricLineSpacingDp), prefs.lyricLineSpacingDp, PlayerDisplayPrefs.LINE_SPACING_MIN..PlayerDisplayPrefs.LINE_SPACING_MAX) {
                        onPrefsChange(prefs.copy(lyricLineSpacingDp = it))
                    }
                    SettingsSliderRow("已播放歌词数", prefs.lyricPlayedCount.toString(), prefs.lyricPlayedCount.toFloat(), PlayerDisplayPrefs.LYRIC_AROUND_MIN.toFloat()..PlayerDisplayPrefs.LYRIC_AROUND_MAX.toFloat()) {
                        onPrefsChange(prefs.copy(lyricPlayedCount = it.roundToInt().coerceIn(0, 3)))
                    }
                    SettingsSliderRow("待播放歌词数", prefs.lyricUpcomingCount.toString(), prefs.lyricUpcomingCount.toFloat(), PlayerDisplayPrefs.LYRIC_AROUND_MIN.toFloat()..PlayerDisplayPrefs.LYRIC_AROUND_MAX.toFloat()) {
                        onPrefsChange(prefs.copy(lyricUpcomingCount = it.roundToInt().coerceIn(0, 3)))
                    }
                    SettingsSliderRow("歌词水平位置", String.format("%+.0f", prefs.lyricOffsetXDp), prefs.lyricOffsetXDp, PlayerDisplayPrefs.LYRIC_OFFSET_MIN..PlayerDisplayPrefs.LYRIC_OFFSET_MAX) {
                        onPrefsChange(prefs.copy(lyricOffsetXDp = it))
                    }
                }
                SettingsCategory("布局") {
                    SettingsSliderRow("整体 UI 缩放", String.format("%.0f%%", prefs.uiScale * 100f), prefs.uiScale, PlayerDisplayPrefs.UI_MIN..PlayerDisplayPrefs.UI_MAX) {
                        onPrefsChange(prefs.copy(uiScale = it))
                    }
                    SettingsSwitchRow("播放组件常显", "底部控件保持展开", prefs.transportAlwaysVisible) {
                        onPrefsChange(prefs.copy(transportAlwaysVisible = it))
                    }
                    SettingsSwitchRow("吸附式播放组件", "贴底吸附；关闭后悬浮并四角圆角", prefs.transportDocked) {
                        onPrefsChange(prefs.copy(transportDocked = it))
                    }
                    SettingsSliderRow("播放组件离底距离", String.format("%.0f", prefs.transportBottomInsetDp), prefs.transportBottomInsetDp, PlayerDisplayPrefs.TRANSPORT_BOTTOM_INSET_MIN..PlayerDisplayPrefs.TRANSPORT_BOTTOM_INSET_MAX, enabled = !prefs.transportDocked) {
                        onPrefsChange(prefs.copy(transportBottomInsetDp = it))
                    }
                    SettingsSwitchRow("黑胶选歌", "横屏长按黑胶进入扑克牌式选歌", prefs.vinylSongPickEnabled) {
                        onPrefsChange(prefs.copy(vinylSongPickEnabled = it))
                    }
                    SettingsSwitchRow("黑胶绝对居中", "垂直对齐屏幕中心，忽略垂直偏移", prefs.vinylAbsoluteCenter) {
                        onPrefsChange(prefs.copy(vinylAbsoluteCenter = it))
                    }
                    SettingsSwitchRow("完整封面", "封面铺满中心，隐藏轴心镂空", prefs.vinylFullCover) {
                        onPrefsChange(prefs.copy(vinylFullCover = it))
                    }
                    SettingsSliderRow("黑胶大小（整体）", String.format("%.0f%%", prefs.vinylSizeScale * 100f), prefs.vinylSizeScale, PlayerDisplayPrefs.VINYL_SIZE_SCALE_MIN..PlayerDisplayPrefs.VINYL_SIZE_SCALE_MAX) {
                        onPrefsChange(prefs.copy(vinylSizeScale = it))
                    }
                    SettingsSliderRow("外圈黑胶半径", String.format("%.0f%%", prefs.vinylOuterScale * 100f), prefs.vinylOuterScale, PlayerDisplayPrefs.VINYL_OUTER_SCALE_MIN..PlayerDisplayPrefs.VINYL_OUTER_SCALE_MAX) {
                        onPrefsChange(prefs.copy(vinylOuterScale = it))
                    }
                    SettingsSliderRow("中心黑胶半径", String.format("基准 %.0f%%", prefs.vinylCenterRadiusFrac * 100f), prefs.vinylCenterRadiusFrac, PlayerDisplayPrefs.VINYL_CENTER_RADIUS_MIN..PlayerDisplayPrefs.VINYL_CENTER_RADIUS_MAX, enabled = !prefs.vinylFullCover) {
                        onPrefsChange(prefs.copy(vinylCenterRadiusFrac = it))
                    }
                    SettingsVinylColorRow(prefs, onPrefsChange, onOpenVinylColorEditor)
                    SettingsSliderRow("黑胶阻尼", String.format("%.2f", prefs.vinylGestureDamping), prefs.vinylGestureDamping, PlayerDisplayPrefs.VINYL_GESTURE_DAMPING_MIN..PlayerDisplayPrefs.VINYL_GESTURE_DAMPING_MAX) {
                        onPrefsChange(prefs.copy(vinylGestureDamping = it))
                    }
                    SettingsSliderRow("黑胶水平位置", String.format("%+.0f", prefs.vinylOffsetXDp), prefs.vinylOffsetXDp, PlayerDisplayPrefs.VINYL_OFFSET_MIN..PlayerDisplayPrefs.VINYL_OFFSET_MAX) {
                        onPrefsChange(prefs.copy(vinylOffsetXDp = it))
                    }
                    SettingsSliderRow("黑胶垂直位置", String.format("%+.0f", prefs.vinylOffsetYDp), prefs.vinylOffsetYDp, PlayerDisplayPrefs.VINYL_OFFSET_Y_MIN..PlayerDisplayPrefs.VINYL_OFFSET_Y_MAX, enabled = !prefs.vinylAbsoluteCenter) {
                        onPrefsChange(prefs.copy(vinylOffsetYDp = it))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(title: String, subtitle: String, actionLabel: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LyricCurrent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = LyricDim, fontSize = 11.sp)
        }
        Text(actionLabel, color = AccentRose, fontSize = 13.sp)
    }
}

@Composable
private fun SettingsCategory(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = LyricDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LyricCurrent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = LyricDim, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = if (enabled) LyricCurrent else LyricDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueLabel, color = LyricDim, fontSize = 12.sp)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AccentRose,
                activeTrackColor = AccentRose,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun SettingsTitleAlignRow(selected: TitleAlignMode, onSelect: (TitleAlignMode) -> Unit) {
    val labels = listOf("左", "唱片", "居中", "歌词")
    Column {
        Text("标题对齐", color = LyricCurrent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TitleAlignMode.entries.forEachIndexed { i, mode ->
                val on = selected == mode
                Text(
                    labels[i],
                    color = if (on) LyricCurrent else LyricDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsVinylColorRow(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onOpenCustomEditor: () -> Unit,
) {
    val labels = listOf("黑色", "金色", "白色", "自选")
    val chips = listOf(Color(0xFF121214), Color(0xFFB8860B), Color(0xFFE8E8EC), Color(prefs.vinylCustomBaseArgb))
    Column {
        Text("唱片配色", color = LyricCurrent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            VinylColorStyle.entries.forEachIndexed { i, style ->
                val on = prefs.vinylColorStyle == style
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(chips[i])
                            .border(if (on) 2.dp else 1.dp, if (on) AccentRose else Color.White.copy(alpha = 0.28f), CircleShape)
                            .clickable {
                                onPrefsChange(prefs.copy(vinylColorStyle = style))
                                if (style == VinylColorStyle.CUSTOM) onOpenCustomEditor()
                            },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(labels[i], color = if (on) LyricCurrent else LyricDim, fontSize = 10.sp)
                }
            }
        }
    }
}
