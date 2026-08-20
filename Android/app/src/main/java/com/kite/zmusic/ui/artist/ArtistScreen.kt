package com.kite.zmusic.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.ArtistAlbumCard
import com.kite.zmusic.data.ArtistBio
import com.kite.zmusic.data.ArtistSimilar
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.catalog.CatalogTopBar
import com.kite.zmusic.ui.catalog.CatalogTrackRow
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.catalog.TrackOverflowMenu
import com.kite.zmusic.ui.catalog.isPlaybackCurrent
import com.kite.zmusic.ui.catalog.launchTrackDownload
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette

private const val HotPreviewCount = 5
private const val AlbumPreviewCount = 8
private const val MvPreviewCount = 6

@Composable
fun ArtistScreen(
    overlay: MainOverlay.Artist,
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    onOpenMv: (Long, String, String?, String?) -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
    onOpenSongs: (String, String?) -> Unit,
    onOpenAlbums: (String, String?) -> Unit,
    onOpenMvs: (String, String?) -> Unit,
    playingTrackId: Long,
    playingSourceId: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val vm = rememberArtistViewModel(
        artistId = overlay.id,
        seedName = overlay.name,
        seedCover = overlay.coverUrl,
        sessionRepository = sessionRepository,
    )
    LaunchedEffect(overlay.id) { vm.load() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var moreTrack by remember { mutableStateOf<TrackRow?>(null) }
    var confirmUnfollow by remember { mutableStateOf(false) }
    var bioExpanded by remember(overlay.id) { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding(),
    ) {
        CatalogTopBar(title = ui.name, onBack = onBack)
        when {
            ui.error != null && ui.songs.isEmpty() && ui.albums.isEmpty() && !ui.loading -> {
                Text(
                    text = ui.error ?: "暂时无法打开这位歌手",
                    color = MainPalette.Secondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { vm.load(force = true) },
                        ),
                )
            }
            else -> {
                ZPullRefresh(
                    refreshing = ui.refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    ArtistBody(
                        ui = ui,
                        contentBottomInset = contentBottomInset,
                        bioExpanded = bioExpanded,
                        playingTrackId = playingTrackId,
                        playingSourceId = playingSourceId,
                        isPlaying = isPlaying,
                        onToggleBio = { bioExpanded = !bioExpanded },
                        onPlayAt = { i ->
                            if (ui.songs.isNotEmpty()) {
                                onPlayTracks(ui.songs, i, null, ui.name)
                            }
                        },
                        onMoreTrack = { moreTrack = it },
                        onFollow = {
                            if (ui.followed) confirmUnfollow = true else vm.toggleFollow()
                        },
                        onOpenAlbum = onOpenAlbum,
                        onOpenMv = onOpenMv,
                        onOpenArtist = onOpenArtist,
                        onOpenSongs = { onOpenSongs(ui.name, ui.coverUrl) },
                        onOpenAlbums = { onOpenAlbums(ui.name, ui.coverUrl) },
                        onOpenMvs = { onOpenMvs(ui.name, ui.coverUrl) },
                    )
                }
            }
        }
    }

    if (confirmUnfollow) {
        GlassAlertDialog(
            title = "取消收藏这位歌手？",
            message = "「${ui.name}」将从你的收藏中移除",
            confirmLabel = "取消收藏",
            confirmDestructive = true,
            onConfirm = {
                confirmUnfollow = false
                vm.toggleFollow()
            },
            onDismiss = { confirmUnfollow = false },
        )
    }
    TrackOverflowMenu(
        track = moreTrack,
        canRemove = false,
        onDismiss = { moreTrack = null },
        onDownload = { track, options -> launchTrackDownload(scope, app, track, options) },
        onRemove = {},
        showAddToPlaylist = true,
        onOpenArtist = { id, name, cover -> onOpenArtist(id, name, cover) },
    )
}

@Composable
private fun ArtistBody(
    ui: ArtistUiState,
    contentBottomInset: Dp,
    bioExpanded: Boolean,
    playingTrackId: Long,
    playingSourceId: Long,
    isPlaying: Boolean,
    onToggleBio: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onMoreTrack: (TrackRow) -> Unit,
    onFollow: () -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    onOpenMv: (Long, String, String?, String?) -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
    onOpenSongs: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenMvs: () -> Unit,
) {
    val visibleSongs = ui.songs.take(HotPreviewCount)
    val previewAlbums = ui.albums.take(AlbumPreviewCount)
    val previewMvs = ui.mvs.take(MvPreviewCount)
    val showAlbums = previewAlbums.isNotEmpty() || ui.albumSize > 0
    val showMvs = previewMvs.isNotEmpty() || ui.mvSize > 0
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = contentBottomInset + 16.dp,
        ),
    ) {
        item(key = "artist-header") {
            ArtistHeader(ui = ui, onPlayAll = { onPlayAt(0) }, onFollow = onFollow)
            Spacer(Modifier.height(18.dp))
        }
        ui.bio?.let { bio ->
            item(key = "artist-bio") {
                ArtistSectionTitle(
                    title = "简介",
                    action = if (bioNeedsExpand(bio)) {
                        if (bioExpanded) "收起" else "展开"
                    } else {
                        null
                    },
                    onAction = if (bioNeedsExpand(bio)) onToggleBio else null,
                )
                Spacer(Modifier.height(8.dp))
                ArtistBioBlock(bio = bio, expanded = bioExpanded)
                Spacer(Modifier.height(18.dp))
            }
        }
        if (ui.loading && ui.songs.isEmpty() && ui.albums.isEmpty()) {
            item(key = "artist-loading") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        if (ui.songs.isNotEmpty()) {
            item(key = "artist-songs-title") {
                ArtistSectionTitle(
                    title = "热门歌曲",
                    action = "查看全部",
                    onAction = onOpenSongs,
                )
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(
                visibleSongs,
                key = { _, track -> "hot-${track.id}" },
            ) { i, track ->
                val current = isPlaybackCurrent(
                    trackId = track.id,
                    contextId = 0L,
                    playingTrackId = playingTrackId,
                    playingSourceId = playingSourceId,
                )
                CatalogTrackRow(
                    index = i + 1,
                    track = track,
                    current = current,
                    playing = current && isPlaying,
                    onClick = { onPlayAt(i) },
                    onMore = { onMoreTrack(track) },
                )
            }
            item(key = "artist-songs-gap") { Spacer(Modifier.height(18.dp)) }
        }
        if (showAlbums) {
            item(key = "artist-albums") {
                ArtistSectionTitle(
                    title = "专辑",
                    action = "查看全部",
                    onAction = onOpenAlbums,
                )
                if (previewAlbums.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ArtistAlbumStrip(
                        albums = previewAlbums,
                        onOpen = { onOpenAlbum(it.id, it.name) },
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }
        if (showMvs) {
            item(key = "artist-mvs") {
                ArtistSectionTitle(
                    title = "MV",
                    action = "查看全部",
                    onAction = onOpenMvs,
                )
                if (previewMvs.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ArtistMvStrip(
                        mvs = previewMvs,
                        onOpen = { onOpenMv(it.id, it.name, it.coverUrl, it.artist) },
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }
        if (ui.similar.isNotEmpty()) {
            item(key = "artist-similar") {
                ArtistSectionTitle(title = "相似歌手")
                Spacer(Modifier.height(12.dp))
                ArtistSimilarStrip(
                    artists = ui.similar,
                    onOpen = { onOpenArtist(it.id, it.name, it.coverUrl) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistHeader(
    ui: ArtistUiState,
    onPlayAll: () -> Unit,
    onFollow: () -> Unit,
) {
    val stats = remember(
        ui.musicSize,
        ui.albumSize,
        ui.mvSize,
        ui.fansCount,
        ui.rankLabel,
    ) {
        buildList {
            if (ui.musicSize > 0) add("${ui.musicSize}首")
            if (ui.albumSize > 0) add("${ui.albumSize}张专辑")
            if (ui.mvSize > 0) add("${ui.mvSize}个MV")
            if (ui.fansCount > 0L) add("${NcmHomeParse.formatPlayCount(ui.fansCount)}粉丝")
            ui.rankLabel?.let { add(it) }
        }.joinToString("  ·  ").takeIf { it.isNotBlank() }
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MainPalette.Placeholder),
        ) {
            UrlImage(
                url = ui.coverUrl,
                contentDescription = ui.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = ui.name,
                color = MainPalette.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ui.aliasLine?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ui.identify?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (stats != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stats,
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val canPlay = ui.songs.isNotEmpty()
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (canPlay) MainPalette.Accent else MainPalette.Accent.copy(alpha = 0.35f),
                        )
                        .clickable(
                            enabled = canPlay,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayAll,
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = ZIcons.Play,
                        contentDescription = "播放热门",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "播放热门",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val followed = ui.followed
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (followed) MainPalette.Accent.copy(alpha = 0.12f) else Color.Transparent,
                        )
                        .border(
                            width = 1.dp,
                            color = MainPalette.Accent.copy(alpha = if (followed) 0f else 0.55f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable(
                            enabled = !ui.followBusy,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onFollow,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (followed) {
                            ZIcons.CollectedPlaylist
                        } else {
                            ZIcons.CollectPlaylist
                        },
                        contentDescription = if (followed) "取消收藏" else "收藏歌手",
                        tint = MainPalette.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (followed) "已收藏" else "收藏歌手",
                        color = MainPalette.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistSectionTitle(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                color = MainPalette.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAction,
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ArtistAlbumStrip(
    albums: List<ArtistAlbumCard>,
    onOpen: (ArtistAlbumCard) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        itemsIndexed(albums, key = { _, it -> it.id }) { _, album ->
            Column(
                Modifier
                    .width(112.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpen(album) },
                    ),
            ) {
                UrlImage(
                    url = album.coverUrl,
                    contentDescription = album.name,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = album.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = MainPalette.Ink, fontSize = 12.sp),
                )
                val sub = buildList {
                    album.year?.let { add(it) }
                    if (album.size > 0) add("${album.size}首")
                }.joinToString(" · ")
                if (sub.isNotEmpty()) {
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
private fun ArtistMvStrip(
    mvs: List<RecommendMvCard>,
    onOpen: (RecommendMvCard) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        itemsIndexed(mvs, key = { _, mv -> mv.id }) { _, mv ->
            Column(
                Modifier
                    .width(148.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpen(mv) },
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MainPalette.Placeholder),
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
            }
        }
    }
}

@Composable
private fun ArtistSimilarStrip(
    artists: List<ArtistSimilar>,
    onOpen: (ArtistSimilar) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        itemsIndexed(artists, key = { _, it -> it.id }) { _, artist ->
            Column(
                Modifier
                    .width(72.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpen(artist) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UrlImage(
                    url = artist.coverUrl,
                    contentDescription = artist.name,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = artist.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ArtistBioBlock(bio: ArtistBio, expanded: Boolean) {
    val brief = bio.brief.orEmpty()
    if (brief.isNotBlank()) {
        Text(
            text = brief,
            color = MainPalette.Secondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (expanded) {
        bio.blocks.forEach { block ->
            if (block.body == brief) return@forEach
            Spacer(Modifier.height(14.dp))
            Text(
                text = block.title,
                color = MainPalette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = block.body,
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

private fun bioNeedsExpand(bio: ArtistBio): Boolean {
    val brief = bio.brief.orEmpty()
    return bio.blocks.isNotEmpty() || brief.length > 80 || brief.count { it == '\n' } >= 2
}
