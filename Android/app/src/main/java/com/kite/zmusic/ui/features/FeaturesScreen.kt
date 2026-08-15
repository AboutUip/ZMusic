package com.kite.zmusic.ui.features

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.catalog.MainOverlay
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainContentPadTop
import com.kite.zmusic.ui.main.MainPageHeader
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.mainContentPadH

@Composable
fun FeaturesScreen(
    contentBottomInset: Dp,
    onOpenOverlay: (MainOverlay) -> Unit,
    onStartFm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val padH = mainContentPadH(landscape)

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = padH)
            .padding(top = MainContentPadTop),
    ) {
        if (!landscape) {
            MainPageHeader(
                title = "功能",
                landscape = landscape,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentBottomInset + 12.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            ToolsGrid(
                onDaily = { onOpenOverlay(MainOverlay.Daily) },
                onFm = onStartFm,
                onCharts = { onOpenOverlay(MainOverlay.Charts) },
            )
        }
    }
}

@Composable
private fun ToolsGrid(
    onDaily: () -> Unit,
    onFm: () -> Unit,
    onCharts: () -> Unit,
) {
    val tools = listOf(
        ToolItem("每日推荐", "今天的三十首", MainPalette.Accent, ZIcons.Daily, onDaily),
        ToolItem("私人漫游", "按口味连续听", Color(0xFF5070F0), ZIcons.Radio, onFm),
        ToolItem("排行榜", "官方与热歌榜", Color(0xFFFF9500), ZIcons.Charts, onCharts),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { item ->
                    ToolCard(item, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class ToolItem(
    val title: String,
    val subtitle: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun ToolCard(item: ToolItem, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = item.onClick,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(item.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = item.color,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = item.subtitle,
                style = TextStyle(color = MainPalette.Secondary, fontSize = 11.sp),
            )
        }
    }
}
