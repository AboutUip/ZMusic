package com.kite.zmusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.CollectedAlbum
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.preferRecent
import com.kite.zmusic.plugin.PluginCollections
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.notice.showIslandNotice
import kotlinx.coroutines.launch

@Composable
internal fun LibraryCollectionAllScreen(
    albums: Boolean,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onOpenAlbum: (CollectedAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: LibraryViewModel = viewModel(
        key = "library-home",
        factory = LibraryViewModelFactory(
            app.sessionRepository,
            app.likedPlaylistRepository,
            app.playlistTracksCache,
            app.playlistCollectionRepository,
            app.libraryHomeRepository,
        ),
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val recentPlaylists by app.recentCollectionStore.playlistIds.collectAsStateWithLifecycle()
    val recentAlbums by app.recentCollectionStore.albumIds.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var morePlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var confirmUnsub by remember { mutableStateOf<PlaylistSummary?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val title = if (albums) "收藏的专辑" else "收藏的歌单"
    val q = query.trim()

    LaunchedEffect(albums, ui.albumsHasMore, ui.albumsLoadingMore, ui.albumsLoading, ui.albums.size) {
        if (!albums) return@LaunchedEffect
        if (ui.albumsHasMore && !ui.albumsLoadingMore && !ui.albumsLoading) {
            vm.loadMoreAlbums()
        }
    }

    val collected = remember(ui.playlists) {
        ui.playlists.filter { !it.isOwned }
    }
    val rankedPlaylists = remember(collected, recentPlaylists) {
        collected.preferRecent(recentPlaylists) { it.id }
    }
    val rankedAlbums = remember(ui.albums, recentAlbums) {
        ui.albums.preferRecent(recentAlbums) { it.id }
    }
    val playlistHits = remember(q, rankedPlaylists) {
        if (q.isEmpty()) rankedPlaylists
        else rankedPlaylists.filter { it.name.contains(q, ignoreCase = true) }
    }
    val albumHits = remember(q, rankedAlbums) {
        if (q.isEmpty()) rankedAlbums
        else rankedAlbums.filter { album ->
            album.name.contains(q, ignoreCase = true) ||
                album.artist.orEmpty().contains(q, ignoreCase = true)
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 1 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd, albums, ui.albumsHasMore, ui.albumsLoadingMore) {
        if (albums && nearEnd && ui.albumsHasMore && !ui.albumsLoadingMore) {
            vm.loadMoreAlbums()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Back,
                    contentDescription = "返回",
                    tint = MainPalette.Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (albums) {
                    val total = ui.albumsTotal.takeIf { it > 0 } ?: ui.albums.size
                    "$total 张"
                } else {
                    "${collected.size} 个"
                },
                style = TextStyle(
                    color = MainPalette.Hint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        CollectionAllSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = if (albums) "搜索专辑" else "搜索歌单",
            focusRequester = searchFocus,
            onClearFocus = {
                focus.clearFocus(force = true)
                keyboard?.hide()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        when {
            albums && ui.albumsLoading && ui.albums.isEmpty() -> {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            albums && ui.albumsError != null && ui.albums.isEmpty() -> {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ui.albumsError ?: "",
                        style = TextStyle(color = MainPalette.Hint, fontSize = 14.sp),
                    )
                }
            }
            !albums && playlistHits.isEmpty() -> {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (q.isEmpty()) "还没有收藏的歌单" else "没有匹配的歌单",
                        style = TextStyle(color = MainPalette.Hint, fontSize = 14.sp),
                    )
                }
            }
            albums && albumHits.isEmpty() -> {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (q.isEmpty()) "还没有收藏的专辑" else "没有匹配的专辑",
                        style = TextStyle(color = MainPalette.Hint, fontSize = 14.sp),
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = contentBottomInset + 24.dp,
                    ),
                ) {
                    item(key = "collection-body") {
                        if (!albums) {
                            LibraryCollectionItems(
                                region = PluginCollections.LIBRARY_COLLECTED_PLAYLISTS,
                                entries = playlistHits.map { pl ->
                                    LibraryCollectionEntry(
                                        title = pl.name,
                                        subtitle = "${pl.trackCount} 首 · 播放 ${formatPlayCount(pl.playCount)}",
                                        coverUrl = pl.resolvedCoverUrl(),
                                        onOpen = {
                                            app.recentCollectionStore.touchPlaylist(pl.id)
                                            onOpenPlaylist(pl)
                                        },
                                        onMore = { morePlaylist = pl },
                                    )
                                },
                            )
                        } else {
                            LibraryCollectionItems(
                                region = PluginCollections.LIBRARY_COLLECTED_ALBUMS,
                                entries = albumHits.map { album ->
                                    val sub = buildList {
                                        album.yearLabel?.let { add(it) }
                                        if (album.size > 0) add("${album.size}首")
                                        album.artist?.takeIf { it.isNotBlank() }?.let { add(it) }
                                    }.joinToString(" · ")
                                    LibraryCollectionEntry(
                                        title = album.name,
                                        subtitle = sub,
                                        coverUrl = album.coverUrl,
                                        onOpen = {
                                            app.recentCollectionStore.touchAlbum(album.id)
                                            onOpenAlbum(album)
                                        },
                                    )
                                },
                            )
                        }
                    }
                    if (albums && ui.albumsLoadingMore) {
                        item(key = "loading-more") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = MainPalette.Accent.copy(alpha = 0.7f),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    morePlaylist?.let { pl ->
        GlassActionSheet(
            title = pl.name,
            message = "${pl.trackCount} 首",
            coverUrl = pl.resolvedCoverUrl(),
            onDismiss = { morePlaylist = null },
            actions = listOf(
                GlassSheetAction("取消收藏", destructive = true) {
                    confirmUnsub = pl
                    morePlaylist = null
                },
            ),
        )
    }
    confirmUnsub?.let { pl ->
        GlassAlertDialog(
            title = "取消收藏？",
            message = "不再收藏「${pl.name}」。",
            confirmLabel = "取消收藏",
            confirmDestructive = true,
            onConfirm = {
                confirmUnsub = null
                scope.launch {
                    val msg = app.playlistEditor.unsubscribe(pl)
                    context.showIslandNotice(msg, pl.resolvedCoverUrl())
                    vm.refresh()
                }
            },
            onDismiss = { confirmUnsub = null },
        )
    }
}

@Composable
private fun CollectionAllSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onClearFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MainPalette.Placeholder.copy(alpha = 0.65f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Search,
            contentDescription = null,
            tint = MainPalette.Hint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MainPalette.Ink,
                fontSize = 15.sp,
            ),
            cursorBrush = SolidColor(MainPalette.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onClearFocus() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                color = MainPalette.Hint,
                                fontSize = 15.sp,
                            ),
                        )
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onQueryChange("") },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Close,
                    contentDescription = "清除",
                    tint = MainPalette.Hint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
