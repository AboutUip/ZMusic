package com.kite.zmusic.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.HomeBanner
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.RecommendPlaylistCard
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.LandscapeHomeSearchHeight
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.theme.MainPalette
import java.util.Calendar
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val BannerLoopCopies = 10_000

@Composable
fun HomeScreen(
    homeFeed: HomeFeedRepository,
    cookie: String,
    userClient: NcmUserClient,
    onOpenOverlay: (MainOverlay) -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by homeFeed.feed.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(cookie) {
        if (cookie.isNotBlank()) homeFeed.ensureLoaded()
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            HomeSearchEntry(
                onClick = { onOpenOverlay(MainOverlay.Search) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
        if (ui.loading && !ui.isWarm) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("加载中…", color = MainPalette.Secondary, fontSize = 14.sp)
            }
        }
        ui.error?.let { err ->
            Text(
                err,
                style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 18.sp),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { scope.launch { homeFeed.refresh(force = true) } },
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
                        onBanner = { b ->
                            when {
                                b.targetType == 1 && b.targetId > 0L -> scope.launch {
                                    val tracks = runCatching {
                                        NcmLibraryParse.tracksFromSongDetail(
                                            userClient.songDetail(listOf(b.targetId), cookie),
                                        )
                                    }.getOrDefault(emptyList())
                                    if (tracks.isNotEmpty()) {
                                        onPlayTracks(tracks, 0, null, b.title)
                                    }
                                }
                                b.targetType == 10 && b.targetId > 0L ->
                                    onOpenOverlay(MainOverlay.Album(b.targetId, b.title ?: "专辑"))
                                b.targetType == 100 && b.targetId > 0L ->
                                    onOpenOverlay(MainOverlay.Artist(b.targetId, b.title ?: "歌手", b.picUrl))
                                b.targetType == 1000 && b.targetId > 0L ->
                                    onOpenOverlay(MainOverlay.Playlist(b.targetId, b.title ?: "歌单", b.picUrl))
                                b.targetType == 1004 && b.targetId > 0L ->
                                    onOpenOverlay(MainOverlay.Mv(b.targetId, b.title ?: "MV", b.picUrl))
                            }
                        },
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp)),
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
            PlaylistGrid(ui.playlists.take(10), columns = 5) { c ->
                onOpenOverlay(MainOverlay.Playlist(c.id, c.name, c.coverUrl))
            }
        }
        if (ui.newSongs.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            SectionTitle("新歌")
            Spacer(Modifier.height(14.dp))
            PlaylistGrid(
                ui.newSongs.take(12).map {
                    RecommendPlaylistCard(it.id, it.name, it.coverUrl, 0L)
                },
                columns = 6,
            ) { card ->
                val i = ui.newSongs.indexOfFirst { it.id == card.id }.coerceAtLeast(0)
                onPlayTracks(ui.newSongs, i, null, "新歌")
            }
        }
        if (ui.mvs.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            SectionTitle("推荐 MV")
            Spacer(Modifier.height(14.dp))
            LandscapeMvGrid(ui.mvs.take(6)) { mv ->
                onOpenOverlay(MainOverlay.Mv(mv.id, mv.name, mv.coverUrl))
            }
        }
        if (ui.dailyPlaylists.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            SectionTitle("每日歌单")
            Spacer(Modifier.height(14.dp))
            PlaylistGrid(ui.dailyPlaylists.take(10), columns = 5) { c ->
                onOpenOverlay(MainOverlay.Playlist(c.id, c.name, c.coverUrl))
            }
        }
        if (!ui.loading && !ui.isWarm && ui.error == null) {
            Text("暂无推荐，点错误提示或稍后重试。", color = MainPalette.Secondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = TextStyle(color = MainPalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    )
}

private fun bannerLoopPage(page: Int, count: Int): Int {
    if (count <= 0) return 0
    val m = page % count
    return if (m < 0) m + count else m
}

@Composable
private fun HomeBannerPager(
    banners: List<HomeBanner>,
    onBanner: (HomeBanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = banners.size
    val looped = count > 1
    val pager = rememberPagerState(
        initialPage = if (looped) count * (BannerLoopCopies / 2) else 0,
        pageCount = {
            when {
                count <= 0 -> 1
                looped -> count * BannerLoopCopies
                else -> count
            }
        },
    )
    val realPage = bannerLoopPage(pager.currentPage, count)
    val dragScope = rememberCoroutineScope()
    LaunchedEffect(count, looped) {
        if (looped && pager.currentPage < count) {
            pager.scrollToPage(count * (BannerLoopCopies / 2))
        }
    }
    LaunchedEffect(pager.settledPage, count, looped) {
        if (!looped) return@LaunchedEffect
        if (System.getProperty("zmusic.test") == "true") return@LaunchedEffect
        delay(4200)
        pager.animateScrollToPage(pager.currentPage + 1)
    }
    Box(modifier) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            userScrollEnabled = true,
            modifier = Modifier
                .fillMaxSize()
                .horizontalPagerMouseDrag(pager, dragScope) { page ->
                    banners.getOrNull(bannerLoopPage(page, count))?.let(onBanner)
                },
        ) { page ->
            val b = banners[bannerLoopPage(page, count)]
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MainPalette.Placeholder),
            ) {
                UrlImage(b.picUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                b.title?.let { title ->
                    Text(
                        title,
                        color = androidx.compose.ui.graphics.Color.White,
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
        if (looped) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(count) { i ->
                    val on = realPage == i
                    val tint by animateColorAsState(
                        if (on) MainPalette.Accent else MainPalette.Ink.copy(alpha = 0.28f),
                        tween(220),
                        label = "bannerDot",
                    )
                    Box(
                        Modifier
                            .size(if (on) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(tint),
                    )
                }
            }
        }
    }
}

private fun Modifier.horizontalPagerMouseDrag(
    pager: PagerState,
    scope: CoroutineScope,
    onClick: (page: Int) -> Unit,
): Modifier = pointerInput(pager) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragged = false
        var total = 0f
        val slop = viewConfiguration.touchSlop
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUpIgnoreConsumed()) break
            val dx = change.positionChange().x
            if (dx == 0f) continue
            total += dx
            if (abs(total) > slop) {
                dragged = true
                pager.dispatchRawDelta(-dx)
                change.consume()
            }
        }
        if (!dragged) {
            onClick(pager.currentPage)
            return@awaitEachGesture
        }
        val last = (pager.pageCount - 1).coerceAtLeast(0)
        val frac = pager.currentPageOffsetFraction
        val target = when {
            frac > 0.18f -> pager.currentPage + 1
            frac < -0.18f -> pager.currentPage - 1
            else -> pager.currentPage
        }.coerceIn(0, last)
        scope.launch { runCatching { pager.animateScrollToPage(target) } }
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
            .background(MainPalette.Surface)
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
                "每日推荐",
                style = TextStyle(color = MainPalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            Text(
                "${day}日 · ${songs.size} 首",
                style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            songs.forEachIndexed { i, t ->
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
                        t.coverUrl,
                        Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = MainPalette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        )
                        Text(
                            t.artists.ifBlank { "—" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = MainPalette.Secondary, fontSize = 11.sp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGrid(
    cards: List<RecommendPlaylistCard>,
    columns: Int,
    onOpen: (RecommendPlaylistCard) -> Unit,
) {
    val rows = (cards.size + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(columns) { col ->
                    val i = row * columns + col
                    if (i < cards.size) {
                        val c = cards[i]
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onOpen(c) },
                                ),
                        ) {
                            UrlImage(
                                c.coverUrl,
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                c.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(color = MainPalette.Ink, fontSize = 12.sp),
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(columns) { col ->
                    val i = row * columns + col
                    if (i < mvs.size) {
                        val mv = mvs[i]
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onOpen(mv) },
                                ),
                        ) {
                            UrlImage(
                                mv.coverUrl,
                                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(mv.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MainPalette.Ink, fontSize = 13.sp)
                            Text(mv.artist ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MainPalette.Secondary, fontSize = 11.sp)
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeSearchEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Row(
        modifier
            .height(LandscapeHomeSearchHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(MainPalette.Placeholder)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
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
