package com.kite.zmusic.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.HomeBanner
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.RecommendPlaylistCard
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.isLikedMusicPlaylistName
import com.kite.zmusic.ui.catalog.parseDailySongs
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.main.LandscapeHomeSearchHeight
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.theme.MainPalette
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun HomeScreen(
    cookie: String,
    userClient: NcmUserClient,
    onOpenOverlay: (MainOverlay) -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var banners by remember { mutableStateOf<List<HomeBanner>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<RecommendPlaylistCard>>(emptyList()) }
    var dailySongs by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    var newSongs by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    var mvs by remember { mutableStateOf<List<RecommendMvCard>>(emptyList()) }
    var dailyPlaylists by remember { mutableStateOf<List<RecommendPlaylistCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cookie) {
        if (cookie.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        runCatching {
            coroutineScope {
                val bannerDef = async { runCatching { NcmHomeParse.banners(userClient.banner(cookie)) }.getOrDefault(emptyList()) }
                val plDef = async {
                    runCatching { NcmHomeParse.personalizedPlaylists(userClient.personalizedPlaylists(cookie)) }
                        .getOrDefault(emptyList())
                }
                val dailyDef = async {
                    runCatching { parseDailySongs(userClient.recommendSongs(cookie)) }.getOrDefault(emptyList())
                }
                val newDef = async {
                    runCatching { NcmHomeParse.personalizedNewSongs(userClient.personalizedNewsong(cookie)) }
                        .getOrDefault(emptyList())
                }
                val mvDef = async {
                    runCatching { NcmHomeParse.personalizedMvs(userClient.personalizedMv(cookie)) }
                        .getOrDefault(emptyList())
                }
                val dailyPlDef = async {
                    runCatching { NcmHomeParse.recommendResourcePlaylists(userClient.recommendResource(cookie)) }
                        .getOrDefault(emptyList())
                }
                banners = bannerDef.await()
                playlists = plDef.await().filter { !isLikedMusicPlaylistName(it.name) }
                dailySongs = dailyDef.await()
                newSongs = newDef.await()
                mvs = mvDef.await()
                dailyPlaylists = dailyPlDef.await().filter { !isLikedMusicPlaylistName(it.name) }
            }
        }.onFailure { error = it.message }
        loading = false
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
        if (loading &&
            banners.isEmpty() &&
            playlists.isEmpty() &&
            dailySongs.isEmpty() &&
            newSongs.isEmpty() &&
            mvs.isEmpty() &&
            dailyPlaylists.isEmpty()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("加载中…", color = MainPalette.Secondary, fontSize = 14.sp)
            }
        }
        error?.let { err ->
            Text(
                err,
                style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 18.sp),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        if (banners.isNotEmpty() || dailySongs.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (banners.isNotEmpty()) {
                    HomeBannerPager(
                        banners = banners,
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
                if (dailySongs.isNotEmpty()) {
                    LandscapeDailyPanel(
                        songs = dailySongs,
                        onOpenDaily = { onOpenOverlay(MainOverlay.Daily) },
                        onPlayAt = { i -> onPlayTracks(dailySongs, i, null, "每日推荐") },
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight(),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        if (playlists.isNotEmpty()) {
            SectionTitle("推荐歌单")
            Spacer(Modifier.height(14.dp))
            PlaylistGrid(playlists.take(10), columns = 5) { c ->
                onOpenOverlay(MainOverlay.Playlist(c.id, c.name, c.coverUrl))
            }
        }
        if (newSongs.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            SectionTitle("新歌")
            Spacer(Modifier.height(14.dp))
            PlaylistGrid(
                newSongs.take(12).map {
                    RecommendPlaylistCard(it.id, it.name, it.coverUrl, 0L)
                },
                columns = 6,
            ) { card ->
                val i = newSongs.indexOfFirst { it.id == card.id }.coerceAtLeast(0)
                onPlayTracks(newSongs, i, null, "新歌")
            }
        }
        if (mvs.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            SectionTitle("推荐 MV")
            Spacer(Modifier.height(14.dp))
            LandscapeMvGrid(mvs.take(6)) { mv ->
                onOpenOverlay(MainOverlay.Mv(mv.id, mv.name, mv.coverUrl))
            }
        }
        if (dailyPlaylists.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            SectionTitle("每日歌单")
            Spacer(Modifier.height(14.dp))
            PlaylistGrid(dailyPlaylists.take(10), columns = 5) { c ->
                onOpenOverlay(MainOverlay.Playlist(c.id, c.name, c.coverUrl))
            }
        }
        if (!loading && banners.isEmpty() && playlists.isEmpty() && dailySongs.isEmpty()) {
            Text("暂无推荐，下拉或稍后重试。", color = MainPalette.Secondary, fontSize = 14.sp)
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

@Composable
private fun HomeBannerPager(
    banners: List<HomeBanner>,
    onBanner: (HomeBanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { banners.size.coerceAtLeast(1) })
    LaunchedEffect(banners.size) {
        if (banners.size < 2) return@LaunchedEffect
        if (System.getProperty("zmusic.test") == "true") return@LaunchedEffect
        while (true) {
            delay(5_000)
            val next = (pager.currentPage + 1) % banners.size
            runCatching { pager.animateScrollToPage(next) }
        }
    }
    Box(modifier) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val b = banners.getOrNull(page) ?: return@HorizontalPager
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MainPalette.Placeholder)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onBanner(b) },
                    ),
            ) {
                UrlImage(b.picUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MainPalette.Page.copy(alpha = 0.08f))
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        b.title ?: "推荐",
                        color = MainPalette.Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (banners.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(banners.size) { i ->
                    val on = pager.currentPage == i
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
            imageVector = Icons.Outlined.Search,
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

fun uidFromCookieSession(statusJsonCookie: org.json.JSONObject?): Long =
    statusJsonCookie?.let { NcmJson.userIdFromLoginStatus(it) } ?: 0L
