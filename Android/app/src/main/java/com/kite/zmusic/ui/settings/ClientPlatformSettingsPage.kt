package com.kite.zmusic.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import com.kite.zmusic.data.platform.MusicPlatform
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.common.GlassPromptField
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

@Composable
fun ClientPlatformSettingsPage(
    selected: MusicPlatform,
    sourceUrl: String,
    onSelect: (MusicPlatform) -> Unit,
    onSourceUrl: (String) -> Unit,
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
            text = t("网易云和汽水有自己的登录，切过去会重启。酷我、酷狗、QQ 音乐不登录、不重启，播放要填自定义音源。"),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .wallpaperItemChrome(RoundedCornerShape(16.dp)),
        ) {
            MusicPlatform.entries.forEachIndexed { index, item ->
                PlatformChoiceRow(
                    platform = item,
                    title = platformLabel(item),
                    subtitle = if (item.reloginOnSwitch) t("登录后使用") else t("无需登录"),
                    selected = item == selected,
                    onClick = { onSelect(item) },
                )
                if (index != MusicPlatform.entries.lastIndex) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = t("自定义音源"),
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = t("酷我、酷狗、QQ 音乐共用这一条，用来换播放直链。"),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        GlassPromptField(
            value = sourceUrl,
            onValueChange = onSourceUrl,
            placeholder = t("https:// 自定义音源地址"),
            maxLength = 2048,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
        )
    }
}

@Composable
private fun PlatformChoiceRow(
    platform: MusicPlatform,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformMark(platform, Modifier.size(40.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
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
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun PlatformMark(platform: MusicPlatform, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(platform) { loadPlatformIcon(context, platform) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
        )
    }
}

internal fun loadPlatformIcon(context: android.content.Context, platform: MusicPlatform) =
    runCatching {
        context.assets.open("platform/${platform.id}.svg").use { input ->
            val svg = SVG.getFromInputStream(input)
            val size = 192
            svg.setDocumentViewBox(0f, 0f, 1024f, 1024f)
            svg.setDocumentWidth(size.toFloat())
            svg.setDocumentHeight(size.toFloat())
            svg.documentPreserveAspectRatio = PreserveAspectRatio.LETTERBOX
            val picture = svg.renderToPicture(size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(AndroidColor.TRANSPARENT)
            Canvas(bmp).apply {
                clipRect(0f, 0f, size.toFloat(), size.toFloat())
                drawPicture(picture)
            }
            bmp.asImageBitmap()
        }
    }.getOrNull()

internal fun platformLabel(platform: MusicPlatform): String = when (platform) {
    MusicPlatform.NETEASE -> t("网易云音乐")
    MusicPlatform.QISHUI -> t("汽水音乐")
    MusicPlatform.KUWO -> t("酷我音乐")
    MusicPlatform.KUGOU -> t("酷狗音乐")
    MusicPlatform.QQ -> t("QQ音乐")
}
