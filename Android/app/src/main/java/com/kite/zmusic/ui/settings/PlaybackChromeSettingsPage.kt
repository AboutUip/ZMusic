package com.kite.zmusic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.PlaybackChromeMode
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

@Composable
fun PlaybackChromeSettingsPage(
    mode: PlaybackChromeMode,
    onSelect: (PlaybackChromeMode) -> Unit,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = t("默认用底部播放条。换成灵动岛后，底部音乐条不再显示。平时只露出圆形封面，点封面展开到最大宽度，再点进入播放页；几秒后自动收回封面，左右滑动也会顺着滑动方向收回。通知在同一座岛上展开，结束后回到刚才的播放岛。竖屏上滑退出、下滑打开百科。横屏没有百科，下滑仍然退出。"),
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
        PlaybackChromeModeRow(
            title = t("默认组件"),
            subtitle = t("底部播放条"),
            selected = mode == PlaybackChromeMode.Default,
            onClick = { onSelect(PlaybackChromeMode.Default) },
        )
        Spacer(Modifier.height(10.dp))
        PlaybackChromeModeRow(
            title = t("灵动岛"),
            subtitle = t("常驻在顶部，代替底部播放条"),
            selected = mode == PlaybackChromeMode.Island,
            onClick = { onSelect(PlaybackChromeMode.Island) },
        )
    }
}

internal fun playbackChromeSubtitle(mode: PlaybackChromeMode): String =
    when (mode) {
        PlaybackChromeMode.Default -> t("默认组件 · 底部播放条")
        PlaybackChromeMode.Island -> t("灵动岛 · 代替底部播放条")
    }

@Composable
private fun PlaybackChromeModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
            .clickable(
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
                    color = if (selected) MainPalette.Accent else MainPalette.Ink,
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
                tint = MainPalette.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
