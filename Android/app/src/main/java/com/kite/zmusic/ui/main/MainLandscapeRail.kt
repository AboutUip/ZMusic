package com.kite.zmusic.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.R
import com.kite.zmusic.ui.icons.ZIcons

internal val LandscapeRailWidth = 208.dp

/**
 * 横屏桌面式侧栏：Logo + 主导航 + 设置。实底，不走悬浮 Dock。
 */
@Composable
fun LandscapeNavRail(
    selected: MainDestination,
    settingsSelected: Boolean,
    onDestination: (MainDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.displayCutout.only(WindowInsetsSides.Start)
        .union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))

    Row(
        modifier
            .width(LandscapeRailWidth)
            .fillMaxHeight()
            .wallpaperItemChrome(RectangleShape)
            .windowInsetsPadding(insets),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 18.dp, bottom = 12.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo_vinyl_z),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "ZMusic",
                    style = TextStyle(
                        color = TextTheme.PageHeader,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                    ),
                )
            }
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MainDestination.entries.forEach { dest ->
                    val on = !settingsSelected && dest == selected
                    RailItem(
                        label = dest.titleZh,
                        selected = on,
                        onClick = { onDestination(dest) },
                    ) {
                        Icon(
                            imageVector = ZIcons.dock(dest),
                            contentDescription = null,
                            tint = if (on) TextTheme.DockActive else TextTheme.DockInactive,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RailItem(
                    label = "设置",
                    selected = settingsSelected,
                    onClick = onOpenSettings,
                ) {
                    Icon(
                        imageVector = ZIcons.Settings,
                        contentDescription = null,
                        tint = if (settingsSelected) TextTheme.DockActive else TextTheme.DockInactive,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .width(0.5.dp)
                .fillMaxHeight()
                .background(MainPalette.Hairline),
        )
    }
}

@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val bg = if (selected) MainPalette.Accent.copy(alpha = 0.10f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = TextStyle(
                color = if (selected) TextTheme.DockActive else TextTheme.DockInactive,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}
