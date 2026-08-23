package com.kite.zmusic.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.SponsorEntry
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

private const val SponsorQueryLimit = 64

@Composable
fun SponsorListPage(
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as ZMusicApplication }
    val vm: CommunityCatalogViewModel<SponsorEntry> = viewModel(
        key = "settings-sponsors",
        factory = CommunityCatalogViewModelFactory(app.container.sponsorRepository, SponsorQueryLimit),
    )
    val ui by vm.state.collectAsStateWithLifecycle()
    CommunityCatalogScaffold(
        ui = ui,
        onQueryChange = vm::onQueryChange,
        onLoadMore = vm::loadMore,
        onRefresh = vm::refresh,
        intro = "谢谢每一位投喂。",
        searchPlaceholder = "搜索名字、金额或日期",
        queryLimit = SponsorQueryLimit,
        emptyQueryMessage = "暂时还没有记录。",
        emptySearchMessage = "没有匹配的记录。",
        contentBottomInset = contentBottomInset,
        modifier = modifier,
        itemKey = { it.listKey },
    ) { entry ->
        SponsorCard(entry)
    }
}

@Composable
private fun SponsorCard(entry: SponsorEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            if (entry.time.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.time,
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.amount.isNotBlank()) {
            Text(
                text = entry.amount,
                style = TextStyle(
                    color = MainPalette.Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}
