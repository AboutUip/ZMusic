package com.kite.zmusic.ui.settings

import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.i18n.AppLanguage
import com.kite.zmusic.i18n.LanguageChoice
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

@Composable
fun LanguageSettingsPage(
    selected: LanguageChoice,
    resolved: AppLanguage,
    onSelect: (LanguageChoice) -> Unit,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    var pending by remember { mutableStateOf<LanguageChoice?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = t("切换后会立刻重启应用。部分机型可能需要再打开一次。歌曲名、歌词和评论仍按来源显示。"),
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
        LanguageChoice.entries.forEachIndexed { index, choice ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            LanguageRow(
                title = choice.title(),
                subtitle = choice.subtitle(resolved),
                selected = selected == choice,
                onClick = {
                    if (choice != selected) pending = choice
                },
            )
        }
    }
    pending?.let { next ->
        GlassAlertDialog(
            title = t("切换语言"),
            message = t(
                "将切换到 %s。确认后会立刻重启应用。部分机型重启后不会自动回到前台，需要再打开一次。",
                next.title(),
            ),
            confirmLabel = t("切换并重启"),
            onConfirm = {
                pending = null
                onSelect(next)
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun LanguageChoice.title(): String = when (this) {
    LanguageChoice.Auto -> t("自动")
    LanguageChoice.Chinese -> AppLanguage.Chinese.nativeName
    LanguageChoice.English -> AppLanguage.English.nativeName
    LanguageChoice.Japanese -> AppLanguage.Japanese.nativeName
}

@Composable
private fun LanguageChoice.subtitle(resolved: AppLanguage): String = when (this) {
    LanguageChoice.Auto -> t("按所在国家切换 · 当前 %s", resolved.nativeName)
    LanguageChoice.Chinese -> AppLanguage.Chinese.subtitle
    LanguageChoice.English -> AppLanguage.English.subtitle
    LanguageChoice.Japanese -> AppLanguage.Japanese.subtitle
}

@Composable
private fun LanguageRow(
    title: String,
    subtitle: String,
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
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(2.dp))
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
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
