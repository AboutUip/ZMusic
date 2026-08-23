package com.kite.zmusic.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.PartnerEntry
import com.kite.zmusic.data.PartnerRoster
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

/** 官网链接用蓝，区别于设置里条款/赞赏的品牌红。 */
private val PartnerLinkBlue = Color(0xFF3478F6)
private val LogoShape = RoundedCornerShape(12.dp)
private const val PartnerQueryLimit = 64

@Composable
fun PartnerListPage(
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as ZMusicApplication }
    val vm: CommunityCatalogViewModel<PartnerEntry> = viewModel(
        key = "settings-partners",
        factory = CommunityCatalogViewModelFactory(app.container.partnerRepository, PartnerQueryLimit),
    )
    val ui by vm.state.collectAsStateWithLifecycle()
    CommunityCatalogScaffold(
        ui = ui,
        onQueryChange = vm::onQueryChange,
        onLoadMore = vm::loadMore,
        onRefresh = vm::refresh,
        intro = "感谢支持本应用的伙伴。",
        searchPlaceholder = "搜索名称或简介",
        queryLimit = PartnerQueryLimit,
        emptyQueryMessage = "暂时还没有赞助商。",
        emptySearchMessage = "没有匹配的赞助商。",
        contentBottomInset = contentBottomInset,
        modifier = modifier,
        itemKey = { it.listKey },
    ) { entry ->
        PartnerCard(
            entry = entry,
            onOpenUrl = { href ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(href)))
                }
            },
        )
    }
}

@Composable
private fun PartnerCard(
    entry: PartnerEntry,
    onOpenUrl: (String) -> Unit,
) {
    val href = PartnerRoster.browseUrl(entry.url)
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
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
