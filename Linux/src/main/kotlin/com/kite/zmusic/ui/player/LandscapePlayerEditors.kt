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
import com.kite.zmusic.data.LyricColorSlot
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TitleLineStyle
import kotlin.math.roundToInt

private val PanelShape = RoundedCornerShape(18.dp)
private val PanelFill = Color(0xE6111218)

@Composable
internal fun LyricStyleEditorOverlay(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onDismiss: () -> Unit,
    onBackToSettings: () -> Unit,
    progress: Float = 1f,
) {
    SideEditorScaffold(title = "歌词样式", progress = progress, onDismiss = onDismiss, onBack = onBackToSettings) {
        RoleEditor("正在播放", prefs.lyricPlayingStyle, LyricRoleStyle.DEFAULT_PLAYING_ARGB) {
            onPrefsChange(prefs.copy(lyricPlayingStyle = it))
        }
        RoleEditor("已播放", prefs.lyricPlayedStyle, LyricRoleStyle.DEFAULT_PLAYED_ARGB) {
            onPrefsChange(prefs.copy(lyricPlayedStyle = it))
        }
        RoleEditor("未播放", prefs.lyricUnplayedStyle, LyricRoleStyle.DEFAULT_UNPLAYED_ARGB) {
            onPrefsChange(prefs.copy(lyricUnplayedStyle = it))
        }
    }
}

@Composable
internal fun TitleStyleEditorOverlay(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onDismiss: () -> Unit,
    onBackToSettings: () -> Unit,
    progress: Float = 1f,
) {
    SideEditorScaffold(title = "标题样式", progress = progress, onDismiss = onDismiss, onBack = onBackToSettings) {
        TitleLineEditor("歌名", prefs.titleNameStyle, TitleLineStyle.DEFAULT_NAME_ARGB) {
            onPrefsChange(prefs.copy(titleNameStyle = it))
        }
        TitleLineEditor("制作人", prefs.titleArtistStyle, TitleLineStyle.DEFAULT_ARTIST_ARGB) {
            onPrefsChange(prefs.copy(titleArtistStyle = it))
        }
        TitleLineEditor("歌单", prefs.titleSourceStyle, TitleLineStyle.DEFAULT_SOURCE_ARGB) {
            onPrefsChange(prefs.copy(titleSourceStyle = it))
        }
    }
}

@Composable
internal fun VinylColorEditorOverlay(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onDismiss: () -> Unit,
    onBackToSettings: () -> Unit,
    progress: Float = 1f,
) {
    val base = Color(prefs.vinylCustomBaseArgb)
    val groove = Color(prefs.vinylCustomGrooveArgb)
    SideEditorScaffold(title = "自选配色", progress = progress, onDismiss = onDismiss, onBack = onBackToSettings) {
        Text("底色", color = LyricCurrent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        RgbSliders(base) { next ->
            onPrefsChange(prefs.withActiveCustomColors(next.toArgbPacked(), prefs.vinylCustomGrooveArgb))
        }
        Spacer(Modifier.height(12.dp))
        Text("纹路", color = LyricCurrent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        RgbSliders(groove) { next ->
            onPrefsChange(prefs.withActiveCustomColors(prefs.vinylCustomBaseArgb, next.toArgbPacked()))
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(base)
                .border(2.dp, groove, CircleShape)
                .align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun SideEditorScaffold(
    title: String,
    progress: Float,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    LandscapeSideSheet(progress = progress, onDismiss = onDismiss, zIndex = 90f) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(PanelShape)
                .background(PanelFill)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹",
                    color = LyricCurrent,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack,
                        )
                        .padding(end = 10.dp),
                )
                Text(title, color = LyricCurrent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun RoleEditor(
    title: String,
    style: LyricRoleStyle,
    defaultArgb: Int,
    onChange: (LyricRoleStyle) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(title, color = LyricCurrent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ToggleChip("斜体", style.italic) { onChange(style.copy(italic = it)) }
            ToggleChip("粗体", style.bold) { onChange(style.copy(bold = it)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LyricColorSlot.entries.forEach { slot ->
                val color = Color(style.copy(colorSlot = slot).resolvedArgb(defaultArgb))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            if (style.colorSlot == slot) 2.dp else 1.dp,
                            if (style.colorSlot == slot) AccentRose else Color.White.copy(alpha = 0.28f),
                            CircleShape,
                        )
                        .clickable { onChange(style.copy(colorSlot = slot)) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("字号 ${String.format("%.0f%%", style.fontScale * 100f)}", color = LyricDim, fontSize = 12.sp)
        Slider(
            value = style.fontScale.coerceIn(PlayerDisplayPrefs.FONT_MIN, PlayerDisplayPrefs.FONT_MAX),
            onValueChange = { onChange(style.copy(fontScale = it)) },
            valueRange = PlayerDisplayPrefs.FONT_MIN..PlayerDisplayPrefs.FONT_MAX,
            colors = SliderDefaults.colors(thumbColor = AccentRose, activeTrackColor = AccentRose),
        )
    }
}

@Composable
private fun TitleLineEditor(
    title: String,
    style: TitleLineStyle,
    defaultArgb: Int,
    onChange: (TitleLineStyle) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(title, color = LyricCurrent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1, 2).forEach { slot ->
                val color = Color(style.copy(colorSlot = slot).resolvedArgb(defaultArgb))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            if (style.colorSlot == slot) 2.dp else 1.dp,
                            if (style.colorSlot == slot) AccentRose else Color.White.copy(alpha = 0.28f),
                            CircleShape,
                        )
                        .clickable { onChange(style.copy(colorSlot = slot)) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("字号 ${String.format("%.0f%%", style.fontScale * 100f)}", color = LyricDim, fontSize = 12.sp)
        Slider(
            value = style.fontScale.coerceIn(PlayerDisplayPrefs.FONT_MIN, PlayerDisplayPrefs.FONT_MAX),
            onValueChange = { onChange(style.copy(fontScale = it)) },
            valueRange = PlayerDisplayPrefs.FONT_MIN..PlayerDisplayPrefs.FONT_MAX,
            colors = SliderDefaults.colors(thumbColor = AccentRose, activeTrackColor = AccentRose),
        )
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (on) LyricCurrent else LyricDim, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
        Switch(checked = on, onCheckedChange = onToggle)
    }
}

@Composable
private fun RgbSliders(color: Color, onChange: (Color) -> Unit) {
    ChannelSlider("R", color.red) { onChange(color.copy(red = it)) }
    ChannelSlider("G", color.green) { onChange(color.copy(green = it)) }
    ChannelSlider("B", color.blue) { onChange(color.copy(blue = it)) }
}

@Composable
private fun ChannelSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LyricDim, fontSize = 12.sp, modifier = Modifier.size(16.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = AccentRose, activeTrackColor = AccentRose),
        )
        Text((value * 255f).roundToInt().toString(), color = LyricDim, fontSize = 11.sp)
    }
}

private fun Color.toArgbPacked(): Int {
    val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
