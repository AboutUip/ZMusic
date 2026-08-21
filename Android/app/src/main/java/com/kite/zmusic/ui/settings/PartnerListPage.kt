package com.kite.zmusic.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.PartnerEntry
import com.kite.zmusic.data.PartnerRoster
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

/** 官网链接用蓝，区别于设置里条款/赞赏的品牌红。 */
private val PartnerLinkBlue = Color(0xFF3478F6)
private val LogoShape = RoundedCornerShape(12.dp)

@Composable
fun PartnerListPage(
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<PartnerEntry>?>(null) }
    LaunchedEffect(Unit) {
        entries = PartnerRoster.load(context)
    }
    val list = entries.orEmpty()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "感谢支持本应用的伙伴。名单随版本更新。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        if (entries != null && list.isEmpty()) {
            Text(
                text = "暂时还没有赞助商。",
                style = TextStyle(
                    color = MainPalette.Hint,
                    fontSize = 14.sp,
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        } else if (list.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .wallpaperItemChrome(RoundedCornerShape(16.dp)),
            ) {
                list.forEachIndexed { index, entry ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp)
                                .height(0.5.dp)
                                .background(MainPalette.Hairline),
                        )
                    }
                    PartnerRow(
                        entry = entry,
                        onOpenUrl = { href ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(href)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PartnerRow(
    entry: PartnerEntry,
    onOpenUrl: (String) -> Unit,
) {
    val href = PartnerRoster.browseUrl(entry.url)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        UrlImage(
            url = entry.logo.takeIf { it.isNotBlank() },
            contentDescription = entry.name,
            modifier = Modifier
                .size(48.dp)
                .clip(LogoShape),
            contentScale = ContentScale.Fit,
            showPlaceholder = true,
            maxPx = UrlImageCache.THUMB_MAX_PX,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.bio.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.bio,
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.content,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                )
            }
            if (href != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.url,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpenUrl(href) },
                    ),
                    style = TextStyle(
                        color = PartnerLinkBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
