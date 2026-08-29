package com.kite.zmusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.CollectedAlbum
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.UserProfileBrief
import com.kite.zmusic.ui.chrome.itemChrome
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.main.mainContentPadH
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.launch

private enum class LibraryCollectionKind { Playlist, Album }

@Composable
fun ProfileScreen(
    cookie: String,
    uid: Long,
    userClient: NcmUserClient,
    onOpenOverlay: (MainOverlay) -> Unit,
    onOpenLikedArtists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var profile by remember { mutableStateOf<UserProfileBrief?>(null) }
    var playlists by remember { mutableStateOf<List<PlaylistSummary>>(emptyList()) }
    var albums by remember { mutableStateOf<List<CollectedAlbum>>(emptyList()) }
    var collectionKind by remember { mutableStateOf(LibraryCollectionKind.Playlist) }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        if (cookie.isBlank() || uid <= 0L) {
            profile = null
            playlists = emptyList()
            albums = emptyList()
            return
        }
        profile = NcmLibraryParse.profileFromUserDetail(userClient.userDetail(uid, cookie))
        playlists = NcmLibraryParse.playlistsFromUserPlaylist(userClient.userPlaylist(uid, cookie), uid)
        albums = NcmHomeParse.collectedAlbums(userClient.albumSublist(cookie))
    }

    LaunchedEffect(cookie, uid) {
        loading = true
        runCatching { reload() }
        loading = false
    }

    val liked = playlists.filter { it.isHeartPlaylist && it.isOwned }
    val created = playlists.filter { it.isOwned && !it.isHeartPlaylist }
    val collected = playlists.filter { !it.isOwned }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        ProfileLandscapeBanner(
            profile = profile,
            loading = loading && profile == null,
            onEnterSpace = {
                val p = profile
                if (p != null) onOpenOverlay(MainOverlay.User(p.userId, p.nickname, p.avatarUrl))
            },
            onOpenFollows = onOpenLikedArtists,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = mainContentPadH()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            PlaylistSection("我喜欢的音乐", liked, "还没有喜欢的音乐", onOpenOverlay)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "创建的歌单",
                    style = TextStyle(color = MainPalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "新建",
                    color = MainPalette.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { creating = true },
                    ),
                )
            }
            if (creating) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .itemChrome(RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = MainPalette.Ink, fontSize = 15.sp),
                        decorationBox = { inner ->
                            if (newName.isEmpty()) Text("歌单名称", color = MainPalette.Hint, fontSize = 15.sp)
                            inner()
                        },
                    )
                    Text(
                        "确定",
                        color = MainPalette.Accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val name = newName.trim()
                                if (name.isEmpty()) return@clickable
                                scope.launch {
                                    val json = runCatching { userClient.playlistCreate(name, cookie) }.getOrNull()
                                    if (json != null && NcmJson.apiCode(json) == 200) {
                                        newName = ""
                                        creating = false
                                        runCatching { reload() }
                                    }
                                }
                            },
                        ),
                    )
                }
            }
            if (created.isEmpty() && !creating) {
                Text("还没有创建的歌单", color = MainPalette.Hint, fontSize = 13.sp)
            } else {
                Text("${created.size} 个", color = MainPalette.Hint, fontSize = 12.sp)
                created.forEach { pl -> PlaylistRow(pl, onOpenOverlay) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "收藏",
                    style = TextStyle(color = MainPalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                CollectionKindSwitch(collectionKind) { collectionKind = it }
            }
            if (collectionKind == LibraryCollectionKind.Playlist) {
                if (collected.isEmpty()) {
                    Text("还没有收藏的歌单", color = MainPalette.Hint, fontSize = 13.sp)
                } else {
                    Text("${collected.size} 个", color = MainPalette.Hint, fontSize = 12.sp)
                    collected.forEach { pl -> PlaylistRow(pl, onOpenOverlay) }
                }
            } else {
                if (albums.isEmpty()) {
                    Text("还没有收藏的专辑", color = MainPalette.Hint, fontSize = 13.sp)
                } else {
                    Text("${albums.size} 个", color = MainPalette.Hint, fontSize = 12.sp)
                    albums.forEach { al -> AlbumRow(al, onOpenOverlay) }
                }
            }
        }
    }
}

@Composable
private fun ProfileLandscapeBanner(
    profile: UserProfileBrief?,
    loading: Boolean,
    onEnterSpace: () -> Unit,
    onOpenFollows: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clipToBounds(),
    ) {
        if (!profile?.backgroundUrl.isNullOrBlank()) {
            UrlImage(
                profile?.backgroundUrl,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(MainPalette.Placeholder))
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val p = profile
            if (loading && p == null) {
                Text("加载中…", color = Color.White, fontSize = 14.sp)
                return@Row
            }
            if (p != null) {
                UrlImage(
                    p.avatarUrl,
                    Modifier.size(64.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.nickname,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildString {
                        p.follows?.let { append("关注 $it") }
                        p.followeds?.let {
                            if (isNotEmpty()) append("  ·  ")
                            append("粉丝 $it")
                        }
                    }
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenFollows,
                            ),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.94f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onEnterSpace,
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "进入用户空间",
                        style = TextStyle(
                            color = MainPalette.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSection(
    title: String,
    playlists: List<PlaylistSummary>,
    empty: String,
    onOpenOverlay: (MainOverlay) -> Unit,
    showCount: Boolean = false,
) {
    Text(title, style = TextStyle(color = MainPalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
    if (playlists.isEmpty()) {
        Text(empty, color = MainPalette.Hint, fontSize = 13.sp)
    } else {
        if (showCount) {
            Text("${playlists.size} 个", color = MainPalette.Hint, fontSize = 12.sp)
        }
        playlists.forEach { pl -> PlaylistRow(pl, onOpenOverlay) }
    }
}

@Composable
private fun PlaylistRow(pl: PlaylistSummary, onOpenOverlay: (MainOverlay) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .itemChrome(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onOpenOverlay(MainOverlay.Playlist(pl.id, pl.name, pl.coverUrl)) },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            pl.coverUrl,
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pl.name, color = MainPalette.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${pl.trackCount} 首",
                color = MainPalette.Secondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun AlbumRow(al: CollectedAlbum, onOpenOverlay: (MainOverlay) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .itemChrome(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onOpenOverlay(MainOverlay.Album(al.id, al.name)) },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            al.coverUrl,
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(al.name, color = MainPalette.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                al.artist ?: "专辑",
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CollectionKindSwitch(
    selected: LibraryCollectionKind,
    onSelect: (LibraryCollectionKind) -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MainPalette.TrackOff)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(LibraryCollectionKind.Playlist to "歌单", LibraryCollectionKind.Album to "专辑").forEach { (kind, label) ->
            val on = selected == kind
            Text(
                label,
                color = if (on) MainPalette.Ink else MainPalette.Secondary,
                fontSize = 12.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) MainPalette.Surface else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(kind) },
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}
