package com.kite.zmusic.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.data.HomeBanner
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.RecommendPlaylistCard
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.ui.catalog.MainOverlay
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainContentPadTop
import com.kite.zmusic.ui.main.MainPageHeader
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.mainContentPadH
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun HomeScreen(
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onOpenOverlay: (MainOverlay) -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onPlaySong: (Long) -> Unit,
    onHint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val vm: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(sessionRepository, app.homeFeedRepository),
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthDp = LocalConfiguration.current.screenWidthDp
    val padH = mainContentPadH(landscape)
    val playlistCols = when {
        widthDp >= 840 -> 4
        widthDp >= 600 -> 3
        else -> 3
    }

    val onBanner: (HomeBanner) -> Unit = { b ->
        when (b.targetType) {
            1 -> if (b.targetId > 0L) onPlaySong(b.targetId) else onHint("暂时无法打开")
            10 -> if (b.targetId > 0L) {
                onOpenOverlay(MainOverlay.Album(b.targetId, b.title ?: "专辑"))
            } else {
                onHint("暂时无法打开该专辑")
            }
            1000 -> if (b.targetId > 0L) {
                onOpenOverlay(MainOverlay.Playlist(b.targetId, b.title ?: "歌单", b.picUrl))
            } else {
                onHint("暂时无法打开该歌单")
            }
            1004 -> if (b.targetId > 0L) {
                onOpenOverlay(MainOverlay.Mv(b.targetId, b.title ?: "MV", b.picUrl))
            } else {
                onHint("暂时无法打开")
            }
            100 -> if (b.targetId > 0L) {
                onOpenOverlay(MainOverlay.Artist(b.targetId, b.title ?: "歌手", b.picUrl))
            } else {
                onHint("暂时无法打开这位歌手")
            }
            else -> onHint("该内容暂未支持打开")
        }
    }

    if (landscape) {
        HomeLandscapeBody(
            ui = ui,
            contentBottomInset = contentBottomInset,
            onRefresh = { vm.refresh() },
            onOpenOverlay = onOpenOverlay,
            onPlayTracks = onPlayTracks,
            onBanner = onBanner,
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        MainPageHeader(
            title = "ZMusic",
            landscape = landscape,
            showLogo = true,
            modifier = Modifier
                .padding(horizontal = padH)
                .padding(top = MainContentPadTop),
            trailing = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOpenOverlay(MainOverlay.Settings) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ZIcons.Settings,
                        contentDescription = "设置",
                        tint = MainPalette.Ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
        ZPullRefresh(
            refreshing = ui.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = padH)
                    .padding(bottom = contentBottomInset + 12.dp),
            ) {
        Spacer(Modifier.height(14.dp))
        HomeSearchEntry(onClick = { onOpenOverlay(MainOverlay.Search) })
        Spacer(Modifier.height(18.dp))

        if (ui.loading &&
            ui.playlists.isEmpty() &&
            ui.dailySongs.isEmpty() &&
            ui.newSongs.isEmpty() &&
            ui.mvs.isEmpty() &&
            ui.dailyPlaylists.isEmpty()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MainPalette.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        ui.error?.let { err ->
            Text(
                text = err,
                style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 18.sp),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { vm.refresh() },
                    )
                    .padding(vertical = 8.dp),
            )
        }

        if (ui.banners.isNotEmpty()) {
            HomeBannerPager(
                banners = ui.banners,
                onBanner = onBanner,
            )
            Spacer(Modifier.height(20.dp))
        }

        if (landscape && ui.dailySongs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DailyRecommendCard(
                    songs = ui.dailySongs,
                    onPlay = { onOpenOverlay(MainOverlay.Daily) },
                    modifier = Modifier.width(120.dp),
                )
                DailySongStrip(
                    songs = ui.dailySongs,
                    onPlayAt = { i -> onPlayTracks(ui.dailySongs, i, null, "每日推荐") },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(22.dp))
        } else if (ui.dailySongs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DailyRecommendCard(
                    songs = ui.dailySongs,
                    onPlay = { onOpenOverlay(MainOverlay.Daily) },
                )
                DailySongStrip(
                    songs = ui.dailySongs,
                    onPlayAt = { i -> onPlayTracks(ui.dailySongs, i, null, "每日推荐") },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        if (ui.playlists.isNotEmpty()) {
            SectionTitle("推荐歌单")
            Spacer(Modifier.height(12.dp))
            HomePlaylistGrid(
                playlists = ui.playlists,
                columns = playlistCols,
                onOpen = { onOpenOverlay(MainOverlay.Playlist(it.id, it.name, it.coverUrl)) },
            )
        }

        if (ui.newSongs.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            SectionTitle("新歌")
            Spacer(Modifier.height(12.dp))
            CoverStrip(
                items = ui.newSongs.map {
                    CoverStripItem(it.id, it.name, it.coverUrl, it.artists)
                },
                onOpen = { i -> onPlayTracks(ui.newSongs, i, null, "新歌") },
            )
        }

        if (ui.mvs.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            SectionTitle("推荐 MV")
            Spacer(Modifier.height(12.dp))
            MvStrip(
                mvs = ui.mvs,
                onOpen = {
                    onOpenOverlay(
                        MainOverlay.Mv(it.id, it.name, it.coverUrl, it.artist),
                    )
                },
            )
        }

        if (ui.dailyPlaylists.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            SectionTitle("每日歌单")
            Spacer(Modifier.height(12.dp))
            CoverStrip(
                items = ui.dailyPlaylists.map {
                    CoverStripItem(it.id, it.name, it.coverUrl, null)
                },
                cardWidth = 100.dp,
                onOpen = { i ->
                    val pl = ui.dailyPlaylists[i]
                    onOpenOverlay(MainOverlay.Playlist(pl.id, pl.name, pl.coverUrl))
                },
            )
        }
        }
    }
    }
}

@Composable
private fun HomeSearchEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF0F0F2))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .focusProperties { canFocus = false }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Search,
            contentDescription = null,
            tint = MainPalette.Secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "搜索歌曲、歌单、MV、歌手",
            style = TextStyle(color = MainPalette.Hint, fontSize = 14.sp),
        )
    }
}

private const val BannerVirtualPages = 50_000

@Composable
private fun HomeBannerPager(
    banners: List<HomeBanner>,
    onBanner: (HomeBanner) -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(2.35f)
        .clip(RoundedCornerShape(12.dp)),
    dotsOverlay: Boolean = false,
) {
    val count = banners.size
    val looped = count > 1
    val startPage = remember(count) {
        if (!looped) 0
        else {
            val mid = BannerVirtualPages / 2
            mid - mid % count
        }
    }
    val pager = rememberPagerState(
        initialPage = startPage,
        pageCount = { if (looped) BannerVirtualPages else count.coerceAtLeast(1) },
    )
    val realPage = if (count == 0) 0 else pager.currentPage % count

    LaunchedEffect(pager.settledPage, count) {
        if (!looped) return@LaunchedEffect
        delay(4200)
        val next = pager.currentPage + 1
        if (next >= BannerVirtualPages - count) {
            val mid = BannerVirtualPages / 2
            pager.scrollToPage(mid - mid % count + (pager.currentPage % count) + 1)
        } else {
            pager.animateScrollToPage(next)
        }
    }
    Column {
        Box(modifier) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val b = banners[page % count]
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onBanner(b) },
                    ),
            ) {
                UrlImage(
                    url = b.picUrl,
                    contentDescription = b.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                b.title?.let { title ->
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MainPalette.Accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (dotsOverlay && looped) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(count) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == realPage) 12.dp else 5.dp, 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == realPage) Color.White else Color.White.copy(alpha = 0.38f),
                            ),
                    )
                }
            }
        }
        }
        if (!dotsOverlay && looped) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(count) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == realPage) 12.dp else 5.dp, 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == realPage) MainPalette.Accent
                                else MainPalette.Hint,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyRecommendCard(
    songs: List<TrackRow>,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier.width(92.dp),
) {
    val day = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFF6B6B), MainPalette.Accent),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPlay,
            )
            .padding(10.dp),
    ) {
        Text(
            text = "每日推荐",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = day.toString(),
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        Text(
            text = "${songs.size} 首",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun DailySongStrip(
    songs: List<TrackRow>,
    onPlayAt: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        itemsIndexed(songs.take(12), key = { _, t -> t.id }) { i, t ->
            Column(
                Modifier
                    .width(72.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPlayAt(i) },
                    ),
            ) {
                UrlImage(
                    url = t.coverUrl,
                    contentDescription = t.name,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = t.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = MainPalette.Ink, fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = MainPalette.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun HomePlaylistGrid(
    playlists: List<RecommendPlaylistCard>,
    columns: Int,
    onOpen: (RecommendPlaylistCard) -> Unit,
) {
    val rows = (playlists.size + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(rows) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(columns) { col ->
                    val i = row * columns + col
                    if (i < playlists.size) {
                        val pl = playlists[i]
                        PlaylistTile(
                            pl = pl,
                            onOpen = { onOpen(pl) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class CoverStripItem(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val subtitle: String?,
)

@Composable
private fun CoverStrip(
    items: List<CoverStripItem>,
    onOpen: (Int) -> Unit,
    cardWidth: Dp = 86.dp,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
            Column(
                Modifier
                    .width(cardWidth)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpen(i) },
                    ),
            ) {
                UrlImage(
                    url = item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(cardWidth)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = MainPalette.Ink, fontSize = 12.sp),
                )
                item.subtitle?.let { sub ->
                    Text(
                        text = sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = MainPalette.Secondary, fontSize = 10.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MvStrip(
    mvs: List<RecommendMvCard>,
    onOpen: (RecommendMvCard) -> Unit,
) {
    val rows = remember(mvs) {
        val top = mvs.filterIndexed { i, _ -> i % 2 == 0 }
        val bottom = mvs.filterIndexed { i, _ -> i % 2 == 1 }
        listOfNotNull(
            top.takeIf { it.isNotEmpty() },
            bottom.takeIf { it.isNotEmpty() },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            MvRow(mvs = row, onOpen = onOpen)
        }
    }
}

@Composable
private fun MvRow(
    mvs: List<RecommendMvCard>,
    onOpen: (RecommendMvCard) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        itemsIndexed(mvs, key = { _, mv -> mv.id }) { _, mv ->
            Box(Modifier.width(168.dp)) {
                MvTeaser(mv = mv, onOpen = { onOpen(mv) })
            }
        }
    }
}

@Composable
private fun MvTeaser(
    mv: RecommendMvCard,
    onOpen: () -> Unit,
) {
    Column(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpen,
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEDEDED)),
        ) {
            UrlImage(
                url = mv.coverUrl,
                contentDescription = mv.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (mv.playCount > 0L) {
                Text(
                    text = NcmHomeParse.formatPlayCount(mv.playCount),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = mv.name,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = mv.artist.orEmpty(),
            maxLines = 1,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
        )
    }
}

@Composable
private fun PlaylistTile(
    pl: RecommendPlaylistCard,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpen,
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEDEDED)),
        ) {
            UrlImage(
                url = pl.coverUrl,
                contentDescription = pl.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (pl.playCount > 0L) {
                Text(
                    text = NcmHomeParse.formatPlayCount(pl.playCount),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = pl.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        )
    }
}

@Composable
private fun HomeLandscapeBody(
    ui: HomeUiState,
    contentBottomInset: Dp,
    onRefresh: () -> Unit,
    onOpenOverlay: (MainOverlay) -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onBanner: (HomeBanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    ZPullRefresh(
        refreshing = ui.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = contentBottomInset + 16.dp),
        ) {
            HomeSearchEntry(
                onClick = { onOpenOverlay(MainOverlay.Search) },
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            if (ui.loading &&
                ui.playlists.isEmpty() &&
                ui.dailySongs.isEmpty() &&
                ui.newSongs.isEmpty() &&
                ui.mvs.isEmpty() &&
                ui.dailyPlaylists.isEmpty()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            ui.error?.let { err ->
                Text(
                    text = err,
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 18.sp),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRefresh,
                        )
                        .padding(vertical = 8.dp),
                )
            }

            if (ui.banners.isNotEmpty() || ui.dailySongs.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (ui.banners.isNotEmpty()) {
                        HomeBannerPager(
                            banners = ui.banners,
                            onBanner = onBanner,
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp)),
                            dotsOverlay = true,
                        )
                    }
                    if (ui.dailySongs.isNotEmpty()) {
                        LandscapeDailyPanel(
                            songs = ui.dailySongs,
                            onOpenDaily = { onOpenOverlay(MainOverlay.Daily) },
                            onPlayAt = { i -> onPlayTracks(ui.dailySongs, i, null, "每日推荐") },
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight(),
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }

            if (ui.playlists.isNotEmpty()) {
                SectionTitle("推荐歌单")
                Spacer(Modifier.height(14.dp))
                HomePlaylistGrid(
                    playlists = ui.playlists,
                    columns = 5,
                    onOpen = { onOpenOverlay(MainOverlay.Playlist(it.id, it.name, it.coverUrl)) },
                )
            }

            if (ui.newSongs.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionTitle("新歌")
                Spacer(Modifier.height(14.dp))
                HomePlaylistGrid(
                    playlists = ui.newSongs.map {
                        RecommendPlaylistCard(
                            id = it.id,
                            name = it.name,
                            coverUrl = it.coverUrl,
                            playCount = 0L,
                        )
                    },
                    columns = 6,
                    onOpen = { card ->
                        val i = ui.newSongs.indexOfFirst { it.id == card.id }.coerceAtLeast(0)
                        onPlayTracks(ui.newSongs, i, null, "新歌")
                    },
                )
            }

            if (ui.mvs.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionTitle("推荐 MV")
                Spacer(Modifier.height(14.dp))
                LandscapeMvGrid(
                    mvs = ui.mvs,
                    onOpen = {
                        onOpenOverlay(MainOverlay.Mv(it.id, it.name, it.coverUrl, it.artist))
                    },
                )
            }

            if (ui.dailyPlaylists.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionTitle("每日歌单")
                Spacer(Modifier.height(14.dp))
                HomePlaylistGrid(
                    playlists = ui.dailyPlaylists,
                    columns = 5,
                    onOpen = { onOpenOverlay(MainOverlay.Playlist(it.id, it.name, it.coverUrl)) },
                )
            }
        }
    }
}

@Composable
private fun LandscapeDailyPanel(
    songs: List<TrackRow>,
    onOpenDaily: () -> Unit,
    onPlayAt: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenDaily,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "每日推荐",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${day}日 · ${songs.size} 首",
                style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
            )
        }
        Spacer(Modifier.height(10.dp))
        songs.take(5).forEachIndexed { i, t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPlayAt(i) },
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlImage(
                    url = t.coverUrl,
                    contentDescription = t.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = t.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = MainPalette.Ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = t.artists.ifBlank { "—" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = MainPalette.Secondary, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeMvGrid(
    mvs: List<RecommendMvCard>,
    onOpen: (RecommendMvCard) -> Unit,
) {
    val columns = 3
    val rows = (mvs.size + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(rows) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                repeat(columns) { col ->
                    val i = row * columns + col
                    if (i < mvs.size) {
                        Box(Modifier.weight(1f)) {
                            MvTeaser(mv = mvs[i], onOpen = { onOpen(mvs[i]) })
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
