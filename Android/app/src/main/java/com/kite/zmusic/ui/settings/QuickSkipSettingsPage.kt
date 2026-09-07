package com.kite.zmusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.MiniQuickSkipAxis
import com.kite.zmusic.data.MiniQuickSkipPrefs
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainControls
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

@Composable
fun QuickSkipSettingsPage(
    prefs: MiniQuickSkipPrefs,
    onEnabledChange: (Boolean) -> Unit,
    onAxisChange: (MiniQuickSkipAxis) -> Unit,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val switchColors = MainControls.switchColors()
    val enabled = prefs.enabled
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "开启后，在底部迷你播放条的封面和歌名上滑动就能切歌。点一下仍会进入播放页。动画只作用在封面和歌名歌手，进度条和播放键不动，也不跟手：方向够了就会切。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "左右切歌时，封面和歌名带着拖影横移。上下切歌时，它们做九十度立体翻转。默认关闭。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .wallpaperItemChrome(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onEnabledChange(!enabled) },
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "快速切歌",
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = quickSkipSubtitle(prefs),
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = switchColors,
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = "滑动方向",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.42f)
                .wallpaperItemChrome(RoundedCornerShape(16.dp)),
        ) {
            AxisRow(
                title = "左右切歌",
                subtitle = "左右滑动，封面和歌名带着拖影横移",
                selected = prefs.axis == MiniQuickSkipAxis.Horizontal,
                enabled = enabled,
                onClick = { onAxisChange(MiniQuickSkipAxis.Horizontal) },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp)
                    .height(0.5.dp)
                    .background(MainPalette.Hairline),
            )
            AxisRow(
                title = "上下切歌",
                subtitle = "上下滑动，封面和歌名立体翻转九十度",
                selected = prefs.axis == MiniQuickSkipAxis.Vertical,
                enabled = enabled,
                onClick = { onAxisChange(MiniQuickSkipAxis.Vertical) },
            )
        }
    }
}

internal fun quickSkipSubtitle(prefs: MiniQuickSkipPrefs): String =
    when {
        !prefs.enabled -> "已关闭 · 仅点击进入播放页（默认）"
        prefs.axis == MiniQuickSkipAxis.Vertical -> "已开启 · 上下滑动切歌"
        else -> "已开启 · 左右滑动切歌"
    }

@Composable
private fun AxisRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = if (selected && enabled) MainPalette.Accent else MainPalette.Ink,
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
        if (selected) {
            Icon(
                imageVector = ZIcons.Check,
                contentDescription = null,
                tint = if (enabled) MainPalette.Accent else MainPalette.Secondary,
            )
        }
    }
}
