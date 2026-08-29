package com.kite.zmusic.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.chrome.itemChrome
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.theme.MainPalette

@Composable
fun AlbumOverlay(
    overlay: MainOverlay.Album,
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onPlay: (List<TrackRow>, Int) -> Unit,
) {
    var tracks by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    LaunchedEffect(overlay.id, cookie) {
        tracks = runCatching {
            NcmLibraryParse.tracksFromSongDetail(userClient.album(overlay.id, cookie))
        }.getOrDefault(emptyList())
    }
    OverlayScaffold(overlay.title, onBack) {
        TrackList(tracks, onPlay)
    }
}

@Composable
fun ArtistOverlay(
    overlay: MainOverlay.Artist,
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onPlay: (List<TrackRow>, Int) -> Unit,
) {
    var tracks by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    LaunchedEffect(overlay.id, cookie) {
        tracks = runCatching {
            val json = userClient.artist(overlay.id, cookie)
            val hot = json.optJSONArray("hotSongs") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until hot.length()) {
                    val o = hot.optJSONObject(i) ?: continue
                    NcmLibraryParse.trackFromSongObject(o)?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }
    OverlayScaffold(overlay.title, onBack) {
        TrackList(tracks, onPlay)
    }
}

@Composable
fun UserOverlay(
    overlay: MainOverlay.User,
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
) {
    var lists by remember { mutableStateOf<List<PlaylistSummary>>(emptyList()) }
    LaunchedEffect(overlay.id, cookie) {
        lists = runCatching {
            NcmLibraryParse.playlistsFromUserPlaylist(
                userClient.userPlaylist(overlay.id, cookie),
                overlay.id,
            )
        }.getOrDefault(emptyList())
    }
    OverlayScaffold(overlay.title, onBack) {
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
private fun TrackList(tracks: List<TrackRow>, onPlay: (List<TrackRow>, Int) -> Unit) {
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
            }
        }
    }
}
