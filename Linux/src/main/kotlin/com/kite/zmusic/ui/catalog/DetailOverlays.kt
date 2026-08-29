package com.kite.zmusic.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.CollectedAlbum
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.chrome.itemChrome
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.launch

private enum class ArtistTab { Hot, Albums, Mv }

@Composable
fun AlbumOverlay(
    overlay: MainOverlay.Album,
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onPlay: (List<TrackRow>, Int) -> Unit,
    onInsertNext: (List<TrackRow>) -> Unit = {},
    onNotice: (String) -> Unit = {},
) {
    var tracks by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    var subscribed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(overlay.id, cookie) {
        tracks = runCatching {
            NcmLibraryParse.tracksFromSongDetail(userClient.album(overlay.id, cookie))
        }.getOrDefault(emptyList())
        val dyn = runCatching { userClient.albumDetailDynamic(overlay.id, cookie) }.getOrNull()
        if (dyn != null) subscribed = NcmLibraryParse.isSubscribed(dyn)
    }
    OverlayScaffold(
        overlay.title,
        onBack,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tracks.isNotEmpty()) OverlayAction("播放全部", accent = true) { onPlay(tracks, 0) }
                OverlayAction(if (subscribed) "已收藏" else "收藏") {
                    scope.launch {
                        val next = !subscribed
                        val ack = runCatching { userClient.albumSub(overlay.id, next, cookie) }.getOrNull()
                        val ok = ack != null && NcmJson.apiCode(ack) == 200
                        if (ok) {
                            subscribed = next
                            onNotice(if (next) "已收藏专辑" else "已取消收藏")
                        } else {
                            onNotice(ack?.let { NcmJson.userFacingMessage(it, "操作失败") } ?: "操作失败")
                        }
                    }
                }
            }
        },
    ) {
        TrackList(tracks, onPlay, onInsertNext)
    }
}

@Composable
fun ArtistOverlay(
    overlay: MainOverlay.Artist,
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onPlay: (List<TrackRow>, Int) -> Unit,
    onInsertNext: (List<TrackRow>) -> Unit = {},
    onOpenAlbum: (Long, String) -> Unit = { _, _ -> },
    onOpenMv: (Long, String) -> Unit = { _, _ -> },
    onNotice: (String) -> Unit = {},
) {
    var tracks by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    var albums by remember { mutableStateOf<List<CollectedAlbum>>(emptyList()) }
    var mvs by remember { mutableStateOf<List<RecommendMvCard>>(emptyList()) }
    var followed by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(ArtistTab.Hot) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(overlay.id, cookie) {
        tracks = runCatching {
            val json = userClient.artist(overlay.id, cookie)
            followed = json.optJSONObject("artist")?.optBoolean("followed", false) == true
            val hot = json.optJSONArray("hotSongs") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until hot.length()) {
                    val o = hot.optJSONObject(i) ?: continue
                    NcmLibraryParse.trackFromSongObject(o)?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
        albums = runCatching {
            NcmHomeParse.collectedAlbums(userClient.artistAlbums(overlay.id, cookie))
        }.getOrDefault(emptyList())
        mvs = runCatching {
            NcmHomeParse.artistMvs(userClient.artistMv(overlay.id, cookie))
        }.getOrDefault(emptyList())
    }
    OverlayScaffold(
        overlay.title,
        onBack,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tracks.isNotEmpty()) OverlayAction("播放热门", accent = true) { onPlay(tracks, 0) }
                OverlayAction(if (followed) "已收藏" else "收藏") {
                    scope.launch {
                        val next = !followed
                        val ack = runCatching { userClient.artistSub(overlay.id, next, cookie) }.getOrNull()
                        val ok = ack != null && NcmJson.apiCode(ack) == 200
                        if (ok) {
                            followed = next
                            onNotice(if (next) "已收藏歌手" else "已取消收藏")
                        } else {
                            onNotice(ack?.let { NcmJson.userFacingMessage(it, "操作失败") } ?: "操作失败")
                        }
                    }
                }
            }
        },
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(ArtistTab.Hot to "热门", ArtistTab.Albums to "专辑", ArtistTab.Mv to "MV").forEach { (kind, label) ->
                OverlayAction(label, accent = tab == kind) { tab = kind }
            }
        }
        when (tab) {
            ArtistTab.Hot -> TrackList(tracks, onPlay, onInsertNext)
            ArtistTab.Albums -> LazyColumn(Modifier.padding(top = 8.dp)) {
                itemsIndexed(albums, key = { _, a -> a.id }) { _, a ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .itemChrome(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onOpenAlbum(a.id, a.name) },
                            )
                            .padding(14.dp),
                    ) {
                        Text(a.name, color = MainPalette.Ink, fontWeight = FontWeight.Medium)
                        Text(a.artist ?: "专辑", color = MainPalette.Secondary, fontSize = 12.sp)
                    }
                }
            }
            ArtistTab.Mv -> LazyColumn(Modifier.padding(top = 8.dp)) {
                itemsIndexed(mvs, key = { _, m -> m.id }) { _, m ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .itemChrome(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onOpenMv(m.id, m.name) },
                            )
                            .padding(14.dp),
                    ) {
                        Text(m.name, color = MainPalette.Ink, fontWeight = FontWeight.Medium)
                        Text(m.artist ?: "MV", color = MainPalette.Secondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun UserOverlay(
    overlay: MainOverlay.User,
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    onNotice: (String) -> Unit = {},
) {
    var lists by remember { mutableStateOf<List<PlaylistSummary>>(emptyList()) }
    var followed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(overlay.id, cookie) {
        lists = runCatching {
            NcmLibraryParse.playlistsFromUserPlaylist(
                userClient.userPlaylist(overlay.id, cookie),
                overlay.id,
            )
        }.getOrDefault(emptyList())
        val detail = runCatching { userClient.userDetail(overlay.id, cookie) }.getOrNull()
        followed = detail?.optJSONObject("profile")?.optBoolean("followed", false) == true
    }
    OverlayScaffold(
        overlay.title,
        onBack,
        trailing = {
            OverlayAction(if (followed) "已关注" else "关注") {
                scope.launch {
                    val next = !followed
                    val ack = runCatching { userClient.userFollow(overlay.id, next, cookie) }.getOrNull()
                    val ok = ack != null && NcmJson.apiCode(ack) == 200
                    if (ok) {
                        followed = next
                        onNotice(if (next) "已关注" else "已取消关注")
                    } else {
                        onNotice(ack?.let { NcmJson.userFacingMessage(it, "操作失败") } ?: "操作失败")
                    }
                }
            }
        },
    ) {
        LazyColumn(Modifier.padding(top = 16.dp)) {
            itemsIndexed(lists, key = { _, p -> p.id }) { _, p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .itemChrome(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOpenPlaylist(p.id, p.name) },
                        )
                        .padding(14.dp),
                ) {
                    Text(p.name, color = MainPalette.Ink, fontWeight = FontWeight.Medium)
                    Text("${p.trackCount} 首", color = MainPalette.Secondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun LikedArtistsOverlay(
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onOpenArtist: (Long, String) -> Unit,
) {
    data class ArtistCard(val id: Long, val name: String)
    var artists by remember { mutableStateOf<List<ArtistCard>>(emptyList()) }
    LaunchedEffect(cookie) {
        artists = runCatching {
            val json = userClient.artistSublist(cookie)
            val arr = json.optJSONArray("data") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    val name = o.optString("name", "")
                    if (id > 0L && name.isNotBlank()) add(ArtistCard(id, name))
                }
            }
        }.getOrDefault(emptyList())
    }
    OverlayScaffold("喜欢的歌手", onBack) {
        LazyColumn(Modifier.padding(top = 16.dp)) {
            itemsIndexed(artists, key = { _, a -> a.id }) { _, a ->
                Text(
                    a.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .itemChrome(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOpenArtist(a.id, a.name) },
                        )
                        .padding(14.dp),
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<TrackRow>,
    onPlay: (List<TrackRow>, Int) -> Unit,
    onInsertNext: (List<TrackRow>) -> Unit = {},
) {
    LazyColumn(Modifier.padding(top = 16.dp)) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .itemChrome(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPlay(tracks, i) },
                    )
                    .padding(14.dp),
            ) {
                Text(t.name, color = MainPalette.Ink)
                Text(t.artists, color = MainPalette.Secondary, fontSize = 12.sp)
                Text(
                    "下一首播放",
                    color = MainPalette.Accent,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onInsertNext(listOf(t)) },
                    ),
                )
            }
        }
    }
}
