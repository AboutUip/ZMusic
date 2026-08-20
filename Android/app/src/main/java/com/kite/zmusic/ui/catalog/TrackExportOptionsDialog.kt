package com.kite.zmusic.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.TrackExportOptions
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.main.MainControls
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.player.AudioQualityGrid

@Composable
internal fun TrackExportOptionsDialog(
    onConfirm: (TrackExportOptions) -> Unit,
    onDismiss: () -> Unit,
    title: String = "下载",
    message: String? = null,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val initial = remember { app.trackExportRepository.lastOptions() }
    var quality by remember { mutableStateOf(initial.quality) }
    var includeCover by remember { mutableStateOf(initial.includeCover) }
    var includeLyrics by remember { mutableStateOf(initial.includeLyrics) }
    var includeMetadata by remember { mutableStateOf(initial.includeMetadata) }
    val switchColors = MainControls.switchColors()
    GlassAlertDialog(
        title = title,
        message = message,
        confirmLabel = "下载",
        onConfirm = {
            val options = TrackExportOptions(
                quality = quality,
                includeCover = includeCover,
                includeLyrics = includeLyrics,
                includeMetadata = includeMetadata,
            )
            app.trackExportRepository.rememberOptions(options)
            onConfirm(options)
        },
        onDismiss = onDismiss,
        extraContent = {
            Text(
                text = "音质",
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            AudioQualityGrid(
                selected = quality,
                onSelect = { quality = it },
                compact = true,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${quality.title} · ${quality.caption}",
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                ),
            )
            Spacer(Modifier.height(14.dp))
            ExportToggleRow(
                title = "封面",
                subtitle = "封面图单独存一份",
                checked = includeCover,
                switchColors = switchColors,
                onCheckedChange = { includeCover = it },
            )
            ExportToggleRow(
                title = "歌词",
                subtitle = "原文和翻译各一份 .lrc",
                checked = includeLyrics,
                switchColors = switchColors,
                onCheckedChange = { includeLyrics = it },
            )
            ExportToggleRow(
                title = "元数据",
                subtitle = "歌名、歌手、专辑写入 music.json",
                checked = includeMetadata,
                switchColors = switchColors,
                onCheckedChange = { includeMetadata = it },
            )
        },
    )
}

@Composable
private fun ExportToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    switchColors: SwitchColors,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = switchColors,
        )
    }
}
