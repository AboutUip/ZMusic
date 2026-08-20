package com.kite.zmusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.AppAppearance
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome
import com.kite.zmusic.ui.theme.MainColors

@Composable
fun AppearanceSettingsPage(
    selected: AppAppearance,
    onSelect: (AppAppearance) -> Unit,
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
            text = "播放页本身仍按封面走氛围。这里改的是首页、设置、歌单和「更多」这些界面的底色。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppearancePreviewCard(
                title = AppAppearance.Light.title,
                colors = MainColors.Light,
                selected = selected == AppAppearance.Light,
                onClick = { onSelect(AppAppearance.Light) },
                modifier = Modifier.weight(1f),
            )
            AppearancePreviewCard(
                title = AppAppearance.Dark.title,
                colors = MainColors.Dark,
                selected = selected == AppAppearance.Dark,
                onClick = { onSelect(AppAppearance.Dark) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        AppearanceSystemRow(
            selected = selected == AppAppearance.System,
            onClick = { onSelect(AppAppearance.System) },
        )
    }
}

@Composable
private fun AppearancePreviewCard(
    title: String,
    colors: MainColors,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .wallpaperItemChrome(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MainPalette.Accent else MainPalette.Hairline,
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
    ) {
        AppearanceMiniUi(colors = colors)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = ZIcons.Check,
                    contentDescription = null,
                    tint = MainPalette.Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AppearanceMiniUi(colors: MainColors) {
    val frame = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(frame)
            .background(colors.page)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ink.copy(alpha = 0.88f)),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .padding(10.dp),
        ) {
            Column {
                Box(
                    Modifier
                        .width(48.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.ink.copy(alpha = 0.78f)),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.secondary.copy(alpha = 0.55f)),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .width(36.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.accent),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.surface)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.hint))
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.hint))
            }
        }
    }
}

@Composable
private fun AppearanceSystemRow(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MainPalette.Accent else MainPalette.Hairline,
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(MainColors.Light.page, MainColors.Dark.page),
                    ),
                )
                .border(1.dp, MainPalette.Hairline, RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = AppAppearance.System.title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = AppAppearance.System.subtitle,
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
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
