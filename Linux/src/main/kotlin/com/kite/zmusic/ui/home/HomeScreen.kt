package com.kite.zmusic.ui.home

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.RecommendPlaylistCard
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.chrome.itemChrome
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.main.LandscapeHomeSearchHeight
import com.kite.zmusic.ui.main.MainContentPadTop
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.main.mainContentPadH
import com.kite.zmusic.ui.theme.MainPalette

@Composable
fun HomeScreen(
    cookie: String,
    uid: Long,
    userClient: NcmUserClient,
    onOpenOverlay: (MainOverlay) -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var banners by remember { mutableStateOf<List<HomeBanner>>(emptyList()) }
    var cards by remember { mutableStateOf<List<RecommendPlaylistCard>>(emptyList()) }
    var heartTracks by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    var heart by remember { mutableStateOf<PlaylistSummary?>(null) }
    LaunchedEffect(cookie, uid) {
        if (cookie.isBlank()) return@LaunchedEffect
        runCatching {
            banners = NcmHomeParse.banners(userClient.banner(cookie))
            cards = NcmHomeParse.personalizedPlaylists(userClient.personalizedPlaylists(cookie))
            if (uid > 0L) {
                val lists = NcmLibraryParse.playlistsFromUserPlaylist(userClient.userPlaylist(uid, cookie), uid)
                heart = lists.firstOrNull { it.isHeartPlaylist }
                heart?.let { pl ->
                    heartTracks = NcmLibraryParse.tracksFromPlaylistDetail(
                        userClient.playlistDetail(pl.id, cookie),
                    ).take(20)
                }
            }
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = mainContentPadH())
            .padding(top = MainContentPadTop, bottom = 100.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        HomeSearchEntry(onClick = { onOpenOverlay(MainOverlay.Search) })
        Spacer(Modifier.height(18.dp))
        if (banners.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(banners, key = { it.picUrl + it.targetId }) { b ->
                    Box(
                        Modifier
                            .width(280.dp)
                            .aspectRatio(2.35f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MainPalette.Placeholder)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (b.targetType == 1000 && b.targetId > 0L) {
                                        onOpenOverlay(MainOverlay.Playlist(b.targetId, b.title ?: "歌单"))
                                    }
                                },
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
            }
            Spacer(Modifier.height(20.dp))
        }
        Text("推荐歌单", style = TextStyle(color = MainPalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(cards, key = { it.id }) { c ->
                Column(
                    Modifier
                        .width(140.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOpenOverlay(MainOverlay.Playlist(c.id, c.name, c.coverUrl)) },
                        ),
                ) {
                    UrlImage(
                        c.coverUrl,
                        Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        c.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = MainPalette.Ink, fontSize = 13.sp),
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        val pl = heart
        if (pl != null && heartTracks.isNotEmpty()) {
            Text(pl.name, style = TextStyle(color = MainPalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            heartTracks.forEachIndexed { i, t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .itemChrome(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onPlayTracks(heartTracks, i, pl.id, pl.name) },
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrlImage(
                        t.coverUrl,
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, color = MainPalette.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artists, color = MainPalette.Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
