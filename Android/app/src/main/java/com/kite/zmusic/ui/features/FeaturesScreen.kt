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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainContentPadTop
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.main.MainPageHeader
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.mainContentPadH
import com.kite.zmusic.ui.main.wallpaperItemChrome

@Composable
fun FeaturesScreen(
    contentBottomInset: Dp,
    onOpenOverlay: (MainOverlay) -> Unit,
    onStartFm: () -> Unit,
    onStartIntelligence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val padH = mainContentPadH(landscape)
    val modes = remember { builtInListenModes() }
    val enterListenMode: (ListenMode) -> Unit = { mode ->
        when (mode.id) {
            ListenModeId.Fm -> onStartFm()
            ListenModeId.Heart -> onStartIntelligence()
        }
    }
    val tools = listOf(
        FeatureItem("每日推荐", "今天的三十首", MainPalette.Accent, ZIcons.Daily) {
            onOpenOverlay(MainOverlay.Daily)
        },
        FeatureItem("排行榜", "官方与热歌榜", Color(0xFFFF9500), ZIcons.Charts) {
            onOpenOverlay(MainOverlay.Charts)
        },
    )

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = padH)
            .padding(top = MainContentPadTop),
    ) {
        if (!landscape) {
            MainPageHeader(
                title = "功能",
                landscape = false,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentBottomInset + 12.dp),
        ) {
            Spacer(Modifier.height(if (landscape) 8.dp else 14.dp))
            FeatureSectionTitle("听歌模式")
            Spacer(Modifier.height(10.dp))
            FeatureCardGrid(
                items = modes.map { mode ->
                    FeatureItem(mode.title, mode.caption, mode.accent, mode.icon) {
                        enterListenMode(mode)
                    }
                },
            )
            Spacer(Modifier.height(if (landscape) 22.dp else 26.dp))
            FeatureSectionTitle("功能")
            Spacer(Modifier.height(10.dp))
            FeatureCardGrid(items = tools)
        }
    }
}

@Composable
private fun FeatureSectionTitle(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = MainPalette.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

private data class FeatureItem(
    val title: String,
    val subtitle: String,
    val color: Color,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun FeatureCardGrid(items: List<FeatureItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { item ->
                    FeatureCard(item, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FeatureCard(item: FeatureItem, modifier: Modifier = Modifier) {
    Row(
        modifier
            .wallpaperItemChrome(RoundedCornerShape(14.dp))
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
                .background(item.color.copy(alpha = if (MainPalette.isDark) 0.22f else 0.16f)),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
