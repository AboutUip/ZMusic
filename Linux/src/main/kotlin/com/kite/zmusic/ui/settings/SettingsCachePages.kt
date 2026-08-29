package com.kite.zmusic.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.GlassStyle
import com.kite.zmusic.data.LocalLibrary
import com.kite.zmusic.data.RealtimeCacheMode
import com.kite.zmusic.data.RealtimeCacheOccupancy
import com.kite.zmusic.data.RealtimeCachePrefs
import com.kite.zmusic.ui.chrome.chromeGlassSurface
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.delay

@Composable
internal fun DownloadAccelSettingsPage(
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    glass: GlassStyle,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        SettingsBack(onBack)
        Text("下载加速", style = TextStyle(color = MainPalette.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text(
            "开启后会扫描本机 exports 与 Music/ZMusic、Downloads/ZMusic。播放时若已有对应缓存，就直接用这份文件，不再向网络拉取音源。",
            style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 20.sp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "命中本地缓存时会无视当前音质设置，以这份已下载的文件为准，用来换取起播速度。关闭后仍按音质档位在线拉取。",
            style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 20.sp),
        )
        Spacer(Modifier.height(20.dp))
        CacheSwitchCard(
            title = "下载加速",
            subtitle = if (enabled) "已开启 · 命中本机缓存则跳过网络" else "已关闭 · 始终按音质在线拉取",
            checked = enabled,
            onChange = onEnabled,
            glass = glass,
        )
    }
}

@Composable
internal fun RealtimeCacheSettingsPage(
    prefs: RealtimeCachePrefs,
    glass: GlassStyle,
    onUpdate: (RealtimeCachePrefs) -> Unit,
    onBack: () -> Unit,
) {
    var occ by remember { mutableStateOf(LocalLibrary.occupancy(prefs.maxMb)) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.maxMb, prefs.enabled) {
        occ = LocalLibrary.occupancy(prefs.maxMb)
        if (System.getProperty("zmusic.test") == "true") return@LaunchedEffect
        while (true) {
            delay(800)
            occ = LocalLibrary.occupancy(prefs.maxMb)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        SettingsBack(onBack)
        Text("实时缓存", style = TextStyle(color = MainPalette.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text(
            "谨慎模式听满 60% 后再写入。实时模式边听边下，听不满就删。激进模式边听边下、不评分，占满后按最旧文件淘汰。关闭后不再采样、也不再用这套缓存播放。",
            style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 20.sp),
        )
        Spacer(Modifier.height(20.dp))
        CacheSwitchCard(
            title = "实时缓存",
            subtitle = prefs.settingsSubtitle,
            checked = prefs.enabled,
            onChange = { onUpdate(prefs.copy(enabled = it)) },
            glass = glass,
        )
        Spacer(Modifier.height(18.dp))
        Text("模式", color = MainPalette.Secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RealtimeCacheMode.entries.forEach { mode ->
                val on = prefs.mode == mode
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .chromeGlassSurface(RoundedCornerShape(12.dp), glass)
                        .background(if (on) MainPalette.Accent.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onUpdate(prefs.copy(mode = mode)) },
                        )
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        mode.title,
                        color = if (on) MainPalette.Accent else MainPalette.Ink,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    Text(mode.caption, color = MainPalette.Secondary, fontSize = 10.sp, maxLines = 2)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("占用", color = MainPalette.Secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OccupancyCard(occ, glass)
        Spacer(Modifier.height(18.dp))
        Text("空间上限", color = MainPalette.Secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .chromeGlassSurface(RoundedCornerShape(16.dp), glass)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text("${prefs.maxMb} MB", color = MainPalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = prefs.maxMb.toFloat(),
                onValueChange = { onUpdate(prefs.copy(maxMb = it.toInt().coerceIn(64, 4096))) },
                valueRange = 64f..4096f,
            )
            Text("写入时按这个上限卡住，超出就从最旧的文件开始排除。", color = MainPalette.Secondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (confirmClear) "再点一次确认清除" else "清除实时缓存",
            color = MainPalette.Accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!confirmClear) {
                            confirmClear = true
                        } else {
                            LocalLibrary.clearCache()
                            occ = LocalLibrary.occupancy(prefs.maxMb)
                            confirmClear = false
                        }
                    },
                )
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CacheSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    glass: GlassStyle,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .chromeGlassSurface(RoundedCornerShape(16.dp), glass)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onChange(!checked) },
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MainPalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MainPalette.Secondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OccupancyCard(occ: RealtimeCacheOccupancy, glass: GlassStyle) {
    Column(
        Modifier
            .fillMaxWidth()
            .chromeGlassSurface(RoundedCornerShape(16.dp), glass)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            "${LocalLibrary.formatBytes(occ.usedBytes)} / ${LocalLibrary.formatBytes(occ.limitBytes)}",
            color = MainPalette.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MainPalette.TrackOff),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(occ.ratio)
                    .height(6.dp)
                    .background(MainPalette.Accent),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (occ.downloading) "${occ.fileCount} 首 · 正在写入" else "${occ.fileCount} 首",
            color = MainPalette.Secondary,
            fontSize = 12.sp,
        )
    }
}
