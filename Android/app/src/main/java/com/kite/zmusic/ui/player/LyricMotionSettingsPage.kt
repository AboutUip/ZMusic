package com.kite.zmusic.ui.player

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
import com.kite.zmusic.data.LyricMotion
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

@Composable
internal fun LyricMotionSettingsPage(
    enter: LyricMotion,
    exit: LyricMotion,
    onEnterChange: (LyricMotion) -> Unit,
    onExitChange: (LyricMotion) -> Unit,
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
            text = t("入场管一行歌词怎么出现，离场管它怎么离开。选保留时，两边都维持现在的样子。粒子飘散只改离场：播放结束的那句化成粒子飘走，不再显示；滑动歌词时粒子再聚回成字。"),
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
        MotionHeading(t("入场"))
        Spacer(Modifier.height(8.dp))
        MotionChoice(
            title = lyricMotionLabel(LyricMotion.Keep),
            subtitle = t("歌词出现的方式和现在一样"),
            selected = enter == LyricMotion.Keep,
            onClick = { onEnterChange(LyricMotion.Keep) },
        )
        Spacer(Modifier.height(20.dp))
        MotionHeading(t("离场"))
        Spacer(Modifier.height(8.dp))
        MotionChoice(
            title = lyricMotionLabel(LyricMotion.Keep),
            subtitle = t("歌词离开的方式和现在一样"),
            selected = exit == LyricMotion.Keep,
            onClick = { onExitChange(LyricMotion.Keep) },
        )
        Spacer(Modifier.height(8.dp))
        MotionChoice(
            title = lyricMotionLabel(LyricMotion.Scatter),
            subtitle = t("播放结束的歌词化成粒子飘走，不再留在原处。滑动时粒子聚回成字。"),
            selected = exit == LyricMotion.Scatter,
            onClick = { onExitChange(LyricMotion.Scatter) },
        )
    }
}

internal fun lyricMotionListSubtitle(enter: LyricMotion, exit: LyricMotion): String =
    t("入场%s · 离场%s", lyricMotionLabel(enter), lyricMotionLabel(exit))

internal fun lyricMotionLabel(mode: LyricMotion): String = when (mode) {
    LyricMotion.Keep -> t("保留")
    LyricMotion.Scatter -> t("粒子飘散")
}

@Composable
private fun MotionHeading(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = MainPalette.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun MotionChoice(
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
