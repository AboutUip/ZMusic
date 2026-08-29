package com.kite.zmusic.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.AppAppearance
import com.kite.zmusic.data.ChromeGlassMode
import com.kite.zmusic.data.GlassStyle
import com.kite.zmusic.ui.chrome.chromeGlassSurface
import com.kite.zmusic.ui.theme.MainColors
import com.kite.zmusic.ui.theme.MainPalette

@Composable
internal fun AppearanceSettingsPage(
    selected: AppAppearance,
    onSelect: (AppAppearance) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        SettingsBack(onBack)
        Text("外观", style = TextStyle(color = MainPalette.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text(
            "播放页本身仍按封面走氛围。这里改的是首页、设置、歌单这些界面的底色。",
            style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 18.sp),
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MainPalette.Surface)
                .border(
                    width = if (selected == AppAppearance.System) 2.dp else 1.dp,
                    color = if (selected == AppAppearance.System) MainPalette.Accent else MainPalette.Hairline,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSelect(AppAppearance.System) },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(AppAppearance.System.title, color = MainPalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(AppAppearance.System.subtitle, color = MainPalette.Secondary, fontSize = 12.sp)
            }
        }
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
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MainPalette.Accent else MainPalette.Hairline,
                shape = shape,
            )
            .clip(shape)
            .background(MainPalette.Surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.page)
                .padding(10.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.ink.copy(alpha = 0.88f)))
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
                    Box(Modifier.width(48.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.ink.copy(alpha = 0.78f)))
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth(0.72f).height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(colors.secondary.copy(alpha = 0.55f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = MainPalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun LiquidGlassStylePage(
    style: GlassStyle,
    onMode: (ChromeGlassMode) -> Unit,
    onRefraction: (Float) -> Unit,
    onBlur: (Float) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val refractionEnabled = style.mode == ChromeGlassMode.Liquid
    val blurEnabled = style.mode != ChromeGlassMode.Solid
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .chromeGlassSurface(RoundedCornerShape(16.dp), style),
            contentAlignment = Alignment.Center,
        ) {
            Text(style.mode.title, color = MainPalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(18.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsBack(onBack)
            Text("液态玻璃样式", style = TextStyle(color = MainPalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Text(
                "液态会折射背后的画面；磨砂只做模糊；纯色不再透底。磨砂和纯色更省绘制。",
                style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 18.sp),
            )
            Spacer(Modifier.height(16.dp))
            GlassModePicker(style.mode, onMode)
            Spacer(Modifier.height(16.dp))
            Text("折射率", color = MainPalette.Ink.copy(alpha = if (refractionEnabled) 1f else 0.38f), fontSize = 14.sp)
            Slider(
                value = style.refraction,
                onValueChange = { if (refractionEnabled) onRefraction(it.coerceIn(0f, 2f)) },
                enabled = refractionEnabled,
                valueRange = 0f..2f,
            )
            Text("模糊程度", color = MainPalette.Ink.copy(alpha = if (blurEnabled) 1f else 0.38f), fontSize = 14.sp)
            Slider(
                value = style.blur,
                onValueChange = { if (blurEnabled) onBlur(it.coerceIn(0f, 1f)) },
                enabled = blurEnabled,
                valueRange = 0f..1f,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "恢复默认",
                color = MainPalette.Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onReset,
                    )
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun GlassModePicker(selected: ChromeGlassMode, onSelect: (ChromeGlassMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MainPalette.TrackOff)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChromeGlassMode.entries.forEach { mode ->
            val on = selected == mode
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) MainPalette.Surface else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(mode) },
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(mode.title, color = MainPalette.Ink, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                Text(mode.caption, color = MainPalette.Secondary, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun SettingsBack(onBack: () -> Unit) {
    Text(
        "返回",
        color = MainPalette.Accent,
        modifier = Modifier
            .padding(top = 8.dp, bottom = 12.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            ),
    )
}

private object AppreciateQr {
    fun load(): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
        val bytes = javaClass.getResourceAsStream("/drawable/img_wechat_appreciate.png")?.readBytes()
            ?: return@runCatching null
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}

@Composable
internal fun AppreciateSettingsPage(onBack: () -> Unit) {
    val bitmap = remember { AppreciateQr.load() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SettingsBack(onBack)
        Text(
            "赞赏",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(color = MainPalette.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "扫一扫这份微信赞赏码，就像把热乎的奶茶递到小萱手边。不投喂也没关系，你愿意听，他就已经很开心了。",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 18.sp),
        )
        Spacer(Modifier.height(24.dp))
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "小萱baibai 的微信赞赏码",
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("赞赏码暂时无法显示", color = MainPalette.Secondary, fontSize = 13.sp)
        }
    }
}
