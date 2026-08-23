package com.kite.zmusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.ChangelogEntry
import com.kite.zmusic.data.ChangelogItem
import com.kite.zmusic.data.ChangelogItemType
import com.kite.zmusic.ui.main.MainPalette

internal val ChangelogKindTestColor = Color(0xFFC9A227)
internal val ChangelogKindReleaseColor = Color(0xFF3D9B6E)
private val TypeAddColor = Color(0xFF8B5CF6)
private val TypeSupportColor = Color(0xFF3478F6)
private val TypeImproveColor = Color(0xFF2A9D8F)
private val TypeFixColor = Color(0xFFE85D75)

@Composable
internal fun ChangelogPreviewBody(
    title: String,
    entry: ChangelogEntry,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        ChangelogHairline()
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
        )
        Spacer(Modifier.height(10.dp))
        ChangelogHairline()
        Spacer(Modifier.height(10.dp))
        ChangelogMetaRow(label = "版本", value = entry.versionLabel)
        Spacer(Modifier.height(8.dp))
        ChangelogKindRow(entry)
        ChangelogNotice(entry)
        Spacer(Modifier.height(12.dp))
        ChangelogHairline()
        Spacer(Modifier.height(10.dp))
        ChangelogItems(entry)
    }
}

@Composable
fun ChangelogUpdateDialogBody(entry: ChangelogEntry) {
    Column(Modifier.fillMaxWidth()) {
        ChangelogKindRow(entry)
        ChangelogNotice(entry)
        Spacer(Modifier.height(12.dp))
        ChangelogHairline()
        Spacer(Modifier.height(10.dp))
        ChangelogItems(entry)
    }
}

@Composable
internal fun ChangelogChip(
    label: String,
    color: Color,
) {
    Text(
        text = label,
        style = TextStyle(
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ChangelogKindRow(entry: ChangelogEntry) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "性质",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier.width(40.dp),
        )
        ChangelogChip(
            label = entry.kind,
            color = if (entry.kind.equals("Test", ignoreCase = true)) {
                ChangelogKindTestColor
            } else {
                ChangelogKindReleaseColor
            },
        )
    }
}

@Composable
private fun ChangelogNotice(entry: ChangelogEntry) {
    if (entry.notice.isBlank()) return
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "声明",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.notice,
            style = TextStyle(
                color = MainPalette.Ink.copy(alpha = 0.78f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
        )
    }
}

@Composable
private fun ChangelogItems(entry: ChangelogEntry) {
    if (entry.items.isEmpty()) {
        Text(
            text = "暂无更新说明",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
        )
        return
    }
    entry.items.forEachIndexed { index, item ->
        if (index > 0) Spacer(Modifier.height(8.dp))
        ChangelogChangeRow(item)
    }
}

@Composable
private fun ChangelogMetaRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier.width(40.dp),
        )
        Text(
            text = value,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun ChangelogChangeRow(item: ChangelogItem) {
    val (label, color) = when (item.type) {
        ChangelogItemType.Add -> "新增" to TypeAddColor
        ChangelogItemType.Support -> "支持" to TypeSupportColor
        ChangelogItemType.Improve -> "优化" to TypeImproveColor
        ChangelogItemType.Fix -> "修复" to TypeFixColor
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        ChangelogChip(label = label, color = color)
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.text,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChangelogHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MainPalette.Hairline),
    )
}
