package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.LrcLine
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal fun LyricSelectOverlay(
    lines: List<LrcLine>,
    selected: SnapshotStateSet<Int>,
    onDismiss: () -> Unit,
    progress: Float = 1f,
) {
    LandscapeSideSheet(progress = progress, onDismiss = onDismiss, zIndex = 85f) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE6111218))
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text("选句", color = LyricCurrent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("长按歌词进入 · 点行勾选 · 复制到剪贴板", color = LyricDim, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(lines, key = { i, l -> "${l.timeMs}_$i" }) { index, line ->
                    val on = index in selected
                    Text(
                        line.text,
                        color = if (on) LyricCurrent else LyricDim,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable {
                                if (on) selected.remove(index) else selected.add(index)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "取消",
                    color = LyricDim,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
                Text(
                    "复制 ${selected.size}",
                    color = AccentRose,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        val body = selected.sorted().mapNotNull { lines.getOrNull(it)?.text }
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                        if (body.isNotBlank()) {
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(body), null)
                        }
                        onDismiss()
                    },
                )
            }
        }
    }
}
