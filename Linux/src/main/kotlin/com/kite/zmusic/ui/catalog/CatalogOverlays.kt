package com.kite.zmusic.ui.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.kite.zmusic.ui.icons.ZIcons
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.LocalLibrary
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.chrome.itemChrome
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun OverlayScaffold(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                ZIcons.Back,
                contentDescription = "返回",
                tint = MainPalette.Ink,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            )
            Text(
                title,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = MainPalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            )
            trailing?.invoke()
        }
        content()
    }
}

@Composable
internal fun OverlayAction(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (accent) MainPalette.Accent else MainPalette.Ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
fun PlaylistOverlay(
    overlay: MainOverlay.Playlist,
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onPlay: (List<TrackRow>, Int) -> Unit,
    onInsertNext: (List<TrackRow>) -> Unit = {},
    onNotice: (String) -> Unit = {},
    uid: Long = 0L,
) {
    var tracks by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    var subscribed by remember { mutableStateOf(false) }
    var owned by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(overlay.id, cookie, uid) {
        val json = runCatching { userClient.playlistDetail(overlay.id, cookie) }.getOrNull()
        tracks = json?.let { NcmLibraryParse.tracksFromPlaylistDetail(it) }.orEmpty()
        if (tracks.size >= 200 || tracks.isEmpty()) {
            tracks = runCatching { loadPlaylistTracks(userClient, overlay.id, cookie) }.getOrDefault(tracks)
        }
        val pl = json?.optJSONObject("playlist")
        subscribed = pl?.optBoolean("subscribed", false) == true
        val creatorId = pl?.optJSONObject("creator")?.optLong("userId", 0L) ?: 0L
        owned = uid > 0L && creatorId == uid
        val dyn = runCatching { userClient.playlistDetailDynamic(overlay.id, cookie) }.getOrNull()
        if (dyn != null) subscribed = NcmLibraryParse.isSubscribed(dyn) || subscribed
    }
    OverlayScaffold(
        overlay.title,
        onBack,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tracks.isNotEmpty()) {
                    OverlayAction("播放全部", accent = true) { onPlay(tracks, 0) }
                }
                if (!owned) {
                    OverlayAction(if (subscribed) "已收藏" else "收藏") {
                        scope.launch {
                            val next = !subscribed
                            val ack = runCatching { userClient.playlistSubscribe(overlay.id, next, cookie) }.getOrNull()
                            val ok = ack != null && NcmJson.apiCode(ack) == 200
                            if (ok) {
                                subscribed = next
                                onNotice(if (next) "已收藏歌单" else "已取消收藏")
                            } else {
                                onNotice(ack?.let { NcmJson.userFacingMessage(it, "操作失败") } ?: "操作失败")
                            }
                        }
                    }
                }
            }
        },
    ) {
        LazyColumn(Modifier.padding(top = 16.dp)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                TrackLine(
                    t,
                    onClick = { onPlay(tracks, i) },
                    onPlayNext = { onInsertNext(listOf(t)) },
                )
            }
        }
    }
}

@Composable
fun ChartsOverlay(
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
) {
    var charts by remember { mutableStateOf(emptyList<com.kite.zmusic.data.ChartSummary>()) }
    LaunchedEffect(cookie) {
        charts = runCatching { NcmHomeParse.charts(userClient.toplistDetail(cookie)) }.getOrDefault(emptyList())
    }
    OverlayScaffold("排行榜", onBack) {
        LazyColumn(Modifier.padding(top = 16.dp)) {
            itemsIndexed(charts, key = { _, c -> c.id }) { _, c ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .itemChrome(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOpenPlaylist(c.id, c.name) },
                        )
                        .padding(14.dp),
                ) {
                    Text(c.name, color = MainPalette.Ink, fontWeight = FontWeight.Medium)
                    if (!c.updateFrequency.isNullOrBlank()) {
                        Text(c.updateFrequency.orEmpty(), color = MainPalette.Secondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DailyOverlay(
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onPlay: (List<TrackRow>, Int) -> Unit,
    onInsertNext: (List<TrackRow>) -> Unit = {},
) {
    var tracks by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    LaunchedEffect(cookie) {
        tracks = runCatching { parseDailySongs(userClient.recommendSongs(cookie)) }.getOrDefault(emptyList())
    }
    OverlayScaffold(
        "每日推荐",
        onBack,
        trailing = {
            if (tracks.isNotEmpty()) OverlayAction("播放全部", accent = true) { onPlay(tracks, 0) }
        },
    ) {
        LazyColumn(Modifier.padding(top = 16.dp)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                TrackLine(t, onClick = { onPlay(tracks, i) }, onPlayNext = { onInsertNext(listOf(t)) })
            }
        }
    }
}

@Composable
fun SearchOverlay(
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onPlay: (List<TrackRow>, Int) -> Unit,
    onOpenArtist: (Long, String) -> Unit = { _, _ -> },
    onOpenAlbum: (Long, String) -> Unit = { _, _ -> },
    onOpenPlaylist: (Long, String) -> Unit = { _, _ -> },
    onOpenUser: (Long, String) -> Unit = { _, _ -> },
    onOpenMv: (Long, String) -> Unit = { _, _ -> },
) {
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    var hot by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchType by remember { mutableStateOf(1) }
    OverlayScaffold("搜索", onBack) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .itemChrome(RoundedCornerShape(14.dp))
                .padding(14.dp),
            textStyle = TextStyle(color = MainPalette.Ink, fontSize = 16.sp),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("搜索歌曲、歌单、MV、歌手", color = MainPalette.Hint)
                inner()
            },
        )
        Row(
            Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                1 to "歌曲",
                1000 to "歌单",
                100 to "歌手",
                10 to "专辑",
                1004 to "MV",
                1002 to "用户",
            ).forEach { (type, label) ->
                val on = searchType == type
                Text(
                    label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) MainPalette.Accent.copy(alpha = 0.12f) else MainPalette.Card)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { searchType = type },
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (on) MainPalette.Accent else MainPalette.Ink,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
        LaunchedEffect(cookie) {
            hot = runCatching { NcmLibraryParse.searchHotTerms(userClient.searchHotDetail(cookie)) }
                .getOrDefault(emptyList())
        }
        LaunchedEffect(query, searchType) {
            if (query.isBlank()) {
                tracks = emptyList()
                return@LaunchedEffect
            }
            tracks = runCatching {
                val json = userClient.search(query, cookie, type = searchType)
                parseSearchHits(json, searchType)
            }.getOrDefault(emptyList())
        }
        LazyColumn(Modifier.padding(top = 12.dp)) {
            if (query.isBlank()) {
                itemsIndexed(hot, key = { i, t -> "$i-$t" }) { _, term ->
                    Text(
                        term,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .itemChrome(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { query = term },
                            )
                            .padding(14.dp),
                        color = MainPalette.Ink,
                    )
                }
            } else {
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                    TrackLine(t) {
                        when (searchType) {
                            1000 -> onOpenPlaylist(t.id, t.name)
                            100 -> onOpenArtist(t.id, t.name)
                            10 -> onOpenAlbum(t.id, t.name)
                            1004 -> onOpenMv(t.id, t.name)
                            1002 -> onOpenUser(t.id, t.name)
                            else -> onPlay(tracks, i)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CachedSongsOverlay(
    cookie: String,
    userClient: NcmUserClient,
    onBack: () -> Unit,
    onPlay: (List<TrackRow>, Int) -> Unit,
) {
    var tracks by remember { mutableStateOf(LocalLibrary.listCachedTracks()) }
    var occupancy by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        fun refresh() {
            val listed = LocalLibrary.listCachedTracks()
            val keep = tracks.associateBy { it.id }
            tracks = listed.map { t -> keep[t.id]?.copy(localAudioUri = t.localAudioUri) ?: t }
            val occ = LocalLibrary.occupancy(512)
            occupancy = if (listed.isEmpty()) {
                ""
            } else {
                "${listed.size} 首 · ${LocalLibrary.formatBytes(occ.usedBytes)}"
            }
        }
        refresh()
        if (System.getProperty("zmusic.test") == "true") return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1500)
            refresh()
        }
    }
    LaunchedEffect(cookie, tracks.map { it.id }) {
        val ids = tracks.map { it.id }.filter { it > 0L }
        if (cookie.isBlank() || ids.isEmpty()) return@LaunchedEffect
        val detailed = runCatching {
            NcmLibraryParse.tracksFromSongDetail(userClient.songDetail(ids.take(50), cookie))
        }.getOrDefault(emptyList())
        if (detailed.isEmpty()) return@LaunchedEffect
        val byId = detailed.associateBy { it.id }
        tracks = tracks.map { t ->
            val d = byId[t.id] ?: return@map t
            d.copy(localAudioUri = t.localAudioUri)
        }
    }
    OverlayScaffold(
        "缓存的歌曲",
        onBack,
        trailing = {
            if (occupancy.isNotEmpty()) {
                Text(occupancy, color = MainPalette.Secondary, fontSize = 12.sp)
            }
        },
    ) {
        if (tracks.isEmpty()) {
            Text("还没有缓存曲目。", color = MainPalette.Secondary, modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn(Modifier.padding(top = 16.dp)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                    TrackLine(t) { onPlay(tracks, i) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackLine(
    track: TrackRow,
    onPlayNext: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .itemChrome(RoundedCornerShape(12.dp))
            .then(
                if (onPlayNext != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                        onLongClick = onPlayNext,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                },
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            track.coverUrl,
            Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.name, color = MainPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artists, color = MainPalette.Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal suspend fun loadPlaylistTracks(
    userClient: NcmUserClient,
    playlistId: Long,
    cookie: String,
): List<TrackRow> {
    val fromDetail = NcmLibraryParse.tracksFromPlaylistDetail(userClient.playlistDetail(playlistId, cookie))
    if (fromDetail.size < 200) return fromDetail
    val all = mutableListOf<TrackRow>()
    var offset = 0
    while (offset < 8_000) {
        val page = NcmLibraryParse.tracksFromSongDetail(
            userClient.playlistTrackAll(playlistId, cookie, limit = 200, offset = offset),
        )
        if (page.isEmpty()) break
        all.addAll(page)
        offset += page.size
        if (page.size < 200) break
    }
    return all.ifEmpty { fromDetail }
}

internal fun parseDailySongs(json: JSONObject): List<TrackRow> = NcmHomeParse.dailySongs(json)

internal fun parseSearchHits(json: JSONObject, type: Int): List<TrackRow> {
    val result = json.optJSONObject("result") ?: json
    val arr = when (type) {
        1000 -> result.optJSONArray("playlists")
        100 -> result.optJSONArray("artists")
        10 -> result.optJSONArray("albums")
        1004 -> result.optJSONArray("mvs")
        1002 -> result.optJSONArray("userprofiles") ?: result.optJSONArray("users")
        else -> result.optJSONArray("songs")
    } ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            when (type) {
                1 -> NcmLibraryParse.trackFromSongObject(o)?.let { add(it) }
                1002 -> {
                    val id = o.optLong("userId", 0L).takeIf { it > 0L } ?: o.optLong("id", 0L)
                    if (id <= 0L) continue
                    val name = o.optString("nickname", o.optString("name", "用户")).ifBlank { "用户" }
                    add(
                        TrackRow(
                            id = id,
                            name = name,
                            artists = o.optString("signature", "用户").ifBlank { "用户" },
                            album = null,
                            durationMs = 0L,
                            coverUrl = o.optString("avatarUrl").takeIf { it.startsWith("http") },
                        ),
                    )
                }
                else -> {
                    val id = o.optLong("id", 0L)
                    if (id <= 0L) continue
                    val name = o.optString("name", o.optString("title", ""))
                    if (name.isBlank()) continue
                    val cover = o.optString("coverImgUrl").ifBlank {
                        o.optString("picUrl").ifBlank {
                            o.optString("imgurl").ifBlank {
                                o.optString("avatarUrl").ifBlank {
                                    o.optJSONObject("album")?.optString("picUrl").orEmpty()
                                }
                            }
                        }
                    }
                    add(
                        TrackRow(
                            id = id,
                            name = name,
                            artists = o.optString("artistName").ifBlank {
                                o.optJSONObject("artist")?.optString("name").orEmpty().ifBlank { "—" }
                            },
                            album = null,
                            durationMs = 0L,
                            coverUrl = cover.takeIf { it.startsWith("http") },
                        ),
                    )
                }
            }
        }
    }
}
