package com.kite.zmusic.ui.catalog

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.ChartSummary
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.UserProfileBrief
import com.kite.zmusic.data.formatAlbumType
import com.kite.zmusic.data.formatAlbumYear
import com.kite.zmusic.ui.artist.ArtistAlbumsScreen
import com.kite.zmusic.ui.artist.ArtistMvsScreen
import com.kite.zmusic.ui.artist.ArtistScreen
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.PlayingEqualizer
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.library.LikedArtistsScreen
import com.kite.zmusic.ui.library.LikedArtistsSearchScreen
import com.kite.zmusic.ui.main.LandscapeCoverEnter
import com.kite.zmusic.ui.main.LandscapeCoverExit
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.mv.MvPlayerScreen
import com.kite.zmusic.ui.search.SearchScreen
import com.kite.zmusic.ui.search.SearchViewModel
import com.kite.zmusic.ui.search.SearchViewModelFactory
import com.kite.zmusic.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.kite.zmusic.ui.main.MainOverlay
@Composable
internal fun TrackCollectionScreen(
    state: CatalogListState,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRetry: () -> Unit,
    extraActionLabel: String? = null,
    extraActionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onExtraAction: (() -> Unit)? = null,
    onLoadMore: () -> Unit = {},
    playingTrackId: Long = 0L,
    playingSourceId: Long = 0L,
    isPlaying: Boolean = false,
    onSubscribe: (() -> Unit)? = null,
    onUnsubscribe: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onRemoveTrack: ((TrackRow) -> Unit)? = null,
    onRemoveTracks: ((List<TrackRow>, (Boolean) -> Unit) -> Unit)? = null,
    manageBridge: PlaylistManageBridge? = null,
    onOpenCreator: (() -> Unit)? = null,
    onOpenArtist: ((Long, String, String?) -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val scope = rememberCoroutineScope()
    val home by app.libraryHomeRepository.snapshot.collectAsStateWithLifecycle()
    var confirmUncollect by remember { mutableStateOf(false) }
    var confirmRemoveSelected by remember { mutableStateOf(false) }
    var exportTracks by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    var moreTrack by remember { mutableStateOf<TrackRow?>(null) }
    val canRemove = onRemoveTrack != null && (state.isOwnedPlaylist || state.isHeartPlaylist)
    val managing = manageBridge?.active == true
    val selected = remember(state.playlistId) { mutableStateSetOf<Long>() }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 1 && last >= total - 6
        }
    }
    LaunchedEffect(nearEnd, state.tracks.size, state.complete, state.playlistId, state.creatorId, state.refreshing) {
        if (nearEnd && !state.complete && !state.refreshing && state.tracks.isNotEmpty() &&
            (state.playlistId > 0L || state.creatorId > 0L)
        ) {
            onLoadMore()
        }
    }
    fun exitManage() {
        selected.clear()
        manageBridge?.exit()
    }
    fun selectedTracks(): List<TrackRow> = state.tracks.filter { it.id in selected }
    if (manageBridge != null) {
        SideEffect {
            val liveIds = state.tracks.mapTo(HashSet()) { it.id }
            selected.removeAll { it !in liveIds }
            manageBridge.selectedCount = selected.size
            manageBridge.totalCount = state.tracks.size
            manageBridge.canRemove = canRemove
            manageBridge.onSelectAll = {
                if (selected.size >= state.tracks.size && state.tracks.isNotEmpty()) {
                    selected.clear()
                } else {
                    selected.addAll(state.tracks.map { it.id })
                }
            }
            manageBridge.onCancel = { exitManage() }
            manageBridge.onRemove = {
                if (!canRemove) {
                    app.islandNoticeCenter.show("只能从自己创建的歌单移除歌曲")
                } else if (selected.isEmpty()) {
                    app.islandNoticeCenter.show("请先选择歌曲")
                } else {
                    confirmRemoveSelected = true
                }
            }
            manageBridge.onDownload = {
                val list = selectedTracks()
                if (list.isEmpty()) {
                    app.islandNoticeCenter.show("请先选择歌曲")
                } else {
                    exportTracks = list
                }
            }
        }
        DisposableEffect(state.playlistId) {
            onDispose { manageBridge.exit() }
        }
        BackHandler(enabled = managing) { exitManage() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding(),
    ) {
        CatalogTopBar(
            title = if (managing) {
                if (selected.isEmpty()) "管理" else "已选 ${selected.size} 首"
            } else {
                state.title
            },
            onBack = { if (managing) exitManage() else onBack() },
            extraLabel = extraActionLabel,
            extraIcon = extraActionIcon,
            onExtra = if (managing) null else onExtraAction,
            onSearch = if (managing) null else onSearch,
            onManage = if (manageBridge != null && !managing) {
                { manageBridge.enter() }
            } else {
                null
            },
            onSelectAll = if (managing) manageBridge?.onSelectAll else null,
            allSelected = managing && state.tracks.isNotEmpty() && selected.size >= state.tracks.size,
        )
        when {
            state.error != null && state.tracks.isEmpty() -> {
                Text(
                    text = state.error,
                    color = MainPalette.Secondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRetry,
                        ),
                )
            }
            else -> {
                ZPullRefresh(
                    refreshing = state.refreshing && !managing,
                    onRefresh = { if (!managing) onRetry() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = contentBottomInset + 16.dp,
                        ),
                    ) {
                    item(key = "collection-header") {
                        CollectionHeader(
                            state = state,
                            selfProfile = home.profile,
                            onPlayAll = {
                                if (!managing && state.tracks.isNotEmpty()) onPlayAt(0)
                            },
                            onOpenCreator = if (managing) null else onOpenCreator,
                            onToggleSubscribe = if (managing) {
                                null
                            } else {
                                onSubscribe?.let { subscribe ->
                                    {
                                        if (state.subscribed) {
                                            confirmUncollect = true
                                        } else {
                                            subscribe()
                                        }
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    if (state.loading && state.tracks.isEmpty()) {
                        item {
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
                    }
                    itemsIndexed(state.tracks, key = { _, t -> t.id }) { idx, t ->
                        val current = isPlaybackCurrent(
                            trackId = t.id,
                            contextId = state.playlistId,
                            playingTrackId = playingTrackId,
                            playingSourceId = playingSourceId,
                        )
                        CatalogTrackRow(
                            index = idx + 1,
                            track = t,
                            current = current,
                            playing = current && isPlaying,
                            onClick = {
                                if (managing) {
                                    if (!selected.add(t.id)) selected.remove(t.id)
                                } else {
                                    onPlayAt(idx)
                                }
                            },
                            onMore = { moreTrack = t },
                            managing = managing,
                            checked = t.id in selected,
                        )
                    }
                    if (!state.complete && state.tracks.isNotEmpty()) {
                        item(key = "playlist-load-more") {
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
    }
    if (confirmUncollect) {
        GlassAlertDialog(
            title = if (state.isAlbum) "取消收藏这张专辑？" else "取消收藏此歌单？",
            message = if (state.isAlbum) {
                "「${state.title}」会从收藏的专辑里拿掉。"
            } else {
                "「${state.title}」将从你的收藏中移除"
            },
            confirmLabel = "取消收藏",
            confirmDestructive = true,
            onConfirm = {
                confirmUncollect = false
                onUnsubscribe?.invoke()
            },
            onDismiss = { confirmUncollect = false },
        )
    }
    if (confirmRemoveSelected) {
        val count = selected.size
        GlassAlertDialog(
            title = if (state.isHeartPlaylist) "从我喜欢的音乐移除？" else "从歌单移除这些歌？",
            message = "将移除已选的 $count 首，不会删除已下载的文件。",
            confirmLabel = "全部移出",
            confirmDestructive = true,
            onConfirm = {
                confirmRemoveSelected = false
                val list = selectedTracks()
                manageBridge?.busy = true
                val finish: (Boolean) -> Unit = {
                    manageBridge?.busy = false
                    if (it) exitManage()
                }
                if (onRemoveTracks != null) {
                    onRemoveTracks(list, finish)
                } else {
                    finish(false)
                }
            },
            onDismiss = { confirmRemoveSelected = false },
        )
    }
    if (exportTracks.isNotEmpty()) {
        val pending = exportTracks
        TrackExportOptionsDialog(
            title = "下载 ${pending.size} 首",
            onConfirm = { options ->
                exportTracks = emptyList()
                scope.launch {
                    manageBridge?.busy = true
                    try {
                        launchTrackDownloads(app, pending, options)
                    } finally {
                        manageBridge?.busy = false
                    }
                }
            },
            onDismiss = { exportTracks = emptyList() },
        )
    }
    TrackOverflowMenu(
        track = moreTrack,
        canRemove = canRemove,
        onDismiss = { moreTrack = null },
        onDownload = { track, options -> launchTrackDownload(scope, app, track, options) },
        onRemove = { onRemoveTrack?.invoke(it) },
        removeConfirmTitle = if (state.isHeartPlaylist) {
            "从我喜欢的音乐移除？"
        } else {
            "从歌单移除这首歌？"
        },
        currentPlaylistId = state.playlistId,
        onOpenArtist = onOpenArtist,
    )
}

@Composable
private fun CollectionHeader(
    state: CatalogListState,
    onPlayAll: () -> Unit,
    onToggleSubscribe: (() -> Unit)? = null,
    selfProfile: UserProfileBrief? = null,
    onOpenCreator: (() -> Unit)? = null,
) {
    val stats = remember(
        state.isAlbum,
        state.subtitle,
        state.playCount,
        state.subscribedCount,
        state.albumPublishTime,
        state.albumType,
        state.albumCompany,
        state.commentCount,
    ) {
        if (state.isAlbum) {
            buildList {
                formatAlbumYear(state.albumPublishTime)?.let { add(it) }
                formatAlbumType(state.albumType)?.let { add(it) }
                state.subtitle?.takeIf { it.isNotBlank() }?.let { add(it) }
                state.albumCompany?.let { add(it) }
                if (state.subscribedCount > 0) {
                    add("${NcmHomeParse.formatPlayCount(state.subscribedCount.toLong())}收藏")
                }
                if (state.commentCount > 0) {
                    add("${NcmHomeParse.formatPlayCount(state.commentCount.toLong())}评论")
                }
            }.joinToString("  ·  ").takeIf { it.isNotBlank() }
        } else {
            buildList {
                state.subtitle?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (state.playCount > 0L) {
                    add("${NcmHomeParse.formatPlayCount(state.playCount)}次播放")
                }
                if (state.subscribedCount > 0) {
                    add("${NcmHomeParse.formatPlayCount(state.subscribedCount.toLong())}收藏")
                }
            }.joinToString("  ·  ").takeIf { it.isNotBlank() }
        }
    }
    val coverSize = if (state.isAlbum) 120.dp else 104.dp
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(if (state.isAlbum) 10.dp else 12.dp))
                .background(MainPalette.Placeholder),
        ) {
            UrlImage(
                url = state.coverUrl
                    ?: state.tracks.firstOrNull()?.coverUrl,
                contentDescription = state.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val useSelf = state.isHeartPlaylist ||
                state.isOwnedPlaylist ||
                state.title.contains("喜欢的音乐")
            val creator = if (useSelf) {
                selfProfile?.nickname?.takeIf { it.isNotBlank() && it != "null" } ?: "我"
            } else {
                state.creatorName?.takeIf { it.isNotBlank() && it != "null" }
                    ?: if (state.isAlbum) "歌手" else "歌单"
            }
            val avatarUrl = if (useSelf) {
                selfProfile?.avatarUrl?.takeIf { it.isNotBlank() && it != "null" }
            } else {
                state.creatorAvatarUrl?.takeIf { it.isNotBlank() && it != "null" }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onOpenCreator != null && !useSelf) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenCreator,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MainPalette.Placeholder),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUrl != null) {
                        UrlImage(
                            url = avatarUrl,
                            contentDescription = creator,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else if (useSelf || state.isAlbum) {
                        Text(
                            text = creator.take(1),
                            color = MainPalette.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_logo_vinyl_z),
                            contentDescription = creator,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = creator,
                    color = MainPalette.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            state.albumAlias?.takeIf { state.isAlbum }?.let { alias ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = alias,
                    color = MainPalette.Hint,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (stats != null) {
                Text(
                    text = stats,
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
            } else {
                Spacer(Modifier.height(4.dp))
            }
            state.albumDescription?.takeIf { state.isAlbum && it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
            }
            val sub = onToggleSubscribe
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CollectionPlayAllButton(onClick = onPlayAll)
                if (sub != null && state.canSubscribe) {
                    CollectionSubscribeButton(
                        subscribed = state.subscribed,
                        busy = state.subscribeBusy,
                        isAlbum = state.isAlbum,
                        onClick = sub,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionPlayAllButton(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MainPalette.Accent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Play,
            contentDescription = "播放全部",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("播放全部", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CollectionSubscribeButton(
    subscribed: Boolean,
    busy: Boolean,
    isAlbum: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (subscribed) {
                    MainPalette.Accent.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .border(
                width = 1.dp,
                color = MainPalette.Accent.copy(alpha = if (subscribed) 0f else 0.55f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (subscribed) {
                ZIcons.CollectedPlaylist
            } else {
                ZIcons.CollectPlaylist
            },
            contentDescription = if (subscribed) {
                "取消收藏"
            } else if (isAlbum) {
                "收藏专辑"
            } else {
                "收藏歌单"
            },
            tint = MainPalette.Accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (subscribed) {
                "已收藏"
            } else if (isAlbum) {
                "收藏专辑"
            } else {
                "收藏歌单"
            },
            color = MainPalette.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun CatalogTrackRow(
    index: Int,
    track: TrackRow,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    managing: Boolean = false,
    checked: Boolean = false,
) {
    val rowClick = remember { MutableInteractionSource() }
    val moreClick = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (current) MainPalette.Accent.copy(alpha = 0.08f) else Color.Transparent)
            .then(
                if (managing) {
                    Modifier.clickable(
                        interactionSource = rowClick,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .then(
                    if (managing) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            interactionSource = rowClick,
                            indication = null,
                            onClick = onClick,
                        )
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (managing) {
                    TrackSelectMark(checked = checked)
                } else if (current) {
                    PlayingEqualizer(
                        playing = playing,
                        color = MainPalette.Accent,
                        modifier = Modifier.size(16.dp, 14.dp),
                    )
                } else {
                    Text(
                        text = index.toString(),
                        color = MainPalette.Hint,
                        fontSize = 13.sp,
                    )
                }
            }
            UrlImage(
                url = track.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = if (current) MainPalette.Accent else MainPalette.Ink,
                        fontSize = 15.sp,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
                Text(
                    text = track.artists,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = if (current) MainPalette.Accent.copy(alpha = 0.72f) else MainPalette.Secondary,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
        if (!managing) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = moreClick,
                        indication = null,
                        onClick = onMore,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.More,
                    contentDescription = "更多",
                    tint = if (current) MainPalette.Accent.copy(alpha = 0.72f) else MainPalette.Hint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TrackSelectMark(checked: Boolean) {
    Box(
        Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (checked) MainPalette.Accent else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (checked) MainPalette.Accent else MainPalette.Hint,
                shape = RoundedCornerShape(5.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = ZIcons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
