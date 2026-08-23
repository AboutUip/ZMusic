package com.kite.zmusic.ui.user

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.catalog.CatalogTopBar
import com.kite.zmusic.ui.catalog.CatalogTrackRow
import com.kite.zmusic.ui.catalog.TrackOverflowMenu
import com.kite.zmusic.ui.catalog.isPlaybackCurrent
import com.kite.zmusic.ui.catalog.launchTrackDownload
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.TwoPanePagerState
import com.kite.zmusic.ui.common.TwoPaneSwipe
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun UserScreen(
    overlay: MainOverlay.User,
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onOpenPlaylist: (Long, String, String?) -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
    onOpenRelations: (fans: Boolean) -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    playingTrackId: Long,
    playingSourceId: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val vm: UserViewModel = viewModel(
        key = "user-${overlay.id}",
        factory = UserViewModelFactory(
            userId = overlay.id,
            seedName = overlay.name,
            seedAvatar = overlay.avatarUrl,
            sessionRepository = sessionRepository,
            islandNotices = app.islandNoticeCenter,
            users = app.userRepository,
        ),
    )
    LaunchedEffect(overlay.id) { vm.load() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var moreTrack by remember { mutableStateOf<TrackRow?>(null) }
    var confirmUnfollow by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(ui.playlistsMore, ui.playlistsLoading, ui.loading) {
        if (!ui.loading && ui.playlistsMore && !ui.playlistsLoading) {
            vm.loadMorePlaylists()
        }
    }
    val pager = remember(scope) {
        TwoPanePagerState(
            scope,
            if (ui.shelf == UserPlaylistShelf.Collected) 1f else 0f,
        )
    }
    val listens = ui.listens

    Column(
        modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding(),
    ) {
        CatalogTopBar(title = ui.name, onBack = onBack)
        when {
            ui.error != null && ui.created.isEmpty() && ui.collected.isEmpty() && !ui.loading -> {
                Text(
                    text = ui.error ?: "暂时无法打开这位用户",
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = contentBottomInset + 16.dp,
                        ),
                    ) {
                        item(key = "user-header") {
                            UserRoomHeader(
                                ui = ui,
                                onFollow = {
                                    if (ui.followed) confirmUnfollow = true else vm.toggleFollow()
                                },
                                onOpenFollows = { onOpenRelations(false) },
                                onOpenFans = { onOpenRelations(true) },
                                onOpenArtist = {
                                    if (ui.artistId > 0L) {
                                        onOpenArtist(ui.artistId, ui.name, ui.avatarUrl)
                                    }
                                },
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                        if (ui.loading && ui.created.isEmpty() && ui.collected.isEmpty()) {
                            item(key = "user-loading") {
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
                        item(key = "user-shelf") {
                            UserShelfTabs(
                                progress = pager.offset,
                                createdCount = ui.created.size,
                                collectedCount = ui.collected.size,
                                onSelect = { page ->
                                    pager.goTo(page.toFloat())
                                    vm.setShelf(
                                        if (page == 1) {
                                            UserPlaylistShelf.Collected
                                        } else {
                                            UserPlaylistShelf.Created
                                        },
                                    )
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            TwoPaneSwipe(
                                progress = pager.offset,
                                pager = pager,
                                onSettled = { page ->
                                    vm.setShelf(
                                        if (page == 1) {
                                            UserPlaylistShelf.Collected
                                        } else {
                                            UserPlaylistShelf.Created
                                        },
                                    )
                                },
                                left = {
                                    UserPlaylistPane(
                                        playlists = ui.created,
                                        emptyText = "还没有公开的歌单",
                                        loading = ui.loading,
                                        onOpen = onOpenPlaylist,
                                    )
                                },
                                right = {
                                    UserPlaylistPane(
                                        playlists = ui.collected,
                                        emptyText = "还没有收藏的歌单",
                                        loading = ui.loading,
                                        onOpen = onOpenPlaylist,
                                    )
                                },
                            )
                        }
                        if (ui.playlistsLoading) {
                            item(key = "user-shelf-more") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
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
                        if (listens.isNotEmpty()) {
                            item(key = "user-listen-title") {
                                Spacer(Modifier.height(10.dp))
                                UserListenTabs(
                                    shelf = ui.listenShelf,
                                    hasWeek = ui.weekListens.isNotEmpty(),
                                    hasAll = ui.allListens.isNotEmpty(),
                                    onShelf = vm::setListenShelf,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            itemsIndexed(
                                listens.take(10),
                                key = { _, hit -> "listen-${hit.track.id}" },
                            ) { i, hit ->
                                val current = isPlaybackCurrent(
                                    trackId = hit.track.id,
                                    contextId = 0L,
                                    playingTrackId = playingTrackId,
                                    playingSourceId = playingSourceId,
                                )
                                CatalogTrackRow(
                                    index = i + 1,
                                    track = hit.track,
                                    current = current,
                                    playing = current && isPlaying,
                                    onClick = {
                                        val list = listens.take(10).map { it.track }
                                        onPlayTracks(list, i, null, "${ui.name}的听歌排行")
                                    },
                                    onMore = { moreTrack = hit.track },
                                )
                                Text(
                                    text = "听了 ${NcmHomeParse.formatPlayCount(hit.playCount.toLong())} 次",
                                    color = MainPalette.Hint,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 36.dp, bottom = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmUnfollow) {
        GlassAlertDialog(
            title = "取消关注？",
            message = "将不再关注「${ui.name}」",
            confirmLabel = "取消关注",
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
private fun UserRoomHeader(
    ui: UserUiState,
    onFollow: () -> Unit,
    onOpenFollows: () -> Unit,
    onOpenFans: () -> Unit,
    onOpenArtist: () -> Unit,
) {
    val appear by animateFloatAsState(
        targetValue = if (ui.loading && ui.backgroundUrl == null) 0.72f else 1f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "user-room-appear",
    )
    val banner = ui.backgroundUrl?.takeIf { it.isNotBlank() } ?: ui.avatarUrl
    Column(Modifier.graphicsLayer { alpha = appear }) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MainPalette.Placeholder),
        ) {
            UrlImage(
                url = banner,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.58f),
                        ),
                    ),
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.92f), CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MainPalette.Placeholder),
                ) {
                    UrlImage(
                        url = ui.avatarUrl,
                        contentDescription = ui.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ui.name,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        genderMark(ui.gender)?.let { mark ->
                            Spacer(Modifier.width(6.dp))
                            Text(text = mark, color = genderTint(ui.gender), fontSize = 14.sp)
                        }
                    }
                    ui.signature?.let { sig ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sig,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        UserStatRow(ui = ui, onOpenFollows = onOpenFollows, onOpenFans = onOpenFans)
        Spacer(Modifier.height(12.dp))
        UserActionRow(
            ui = ui,
            onFollow = onFollow,
            onOpenArtist = onOpenArtist,
        )
    }
}

@Composable
private fun UserStatRow(
    ui: UserUiState,
    onOpenFollows: () -> Unit,
    onOpenFans: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserStatCell(
            value = ui.follows?.let { NcmHomeParse.formatPlayCount(it) } ?: "—",
            label = "关注",
            onClick = onOpenFollows,
        )
        UserStatCell(
            value = ui.followeds?.let { NcmHomeParse.formatPlayCount(it) } ?: "—",
            label = "粉丝",
            onClick = onOpenFans,
        )
        UserStatCell(
            value = ui.listenSongs?.let { NcmHomeParse.formatPlayCount(it) } ?: "—",
            label = "听歌",
            onClick = null,
        )
        ui.level?.let { lv ->
            UserStatCell(value = "Lv.$lv", label = "等级", onClick = null)
        }
    }
}

@Composable
private fun UserStatCell(
    value: String,
    label: String,
    onClick: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = value,
            color = MainPalette.Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, color = MainPalette.Secondary, fontSize = 11.sp)
    }
}

@Composable
private fun UserActionRow(
    ui: UserUiState,
    onFollow: () -> Unit,
    onOpenArtist: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ui.isSelf) {
            Text(
                text = "这是你",
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            )
        } else {
            val followed = ui.followed
            val followBg by animateColorAsState(
                targetValue = if (followed) {
                    MainPalette.Accent.copy(alpha = 0.12f)
                } else {
                    MainPalette.Accent
                },
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "user-follow-bg",
            )
            val followFg by animateColorAsState(
                targetValue = if (followed) MainPalette.Accent else Color.White,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "user-follow-fg",
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(followBg)
                    .then(
                        if (followed) {
                            Modifier.border(
                                1.dp,
                                MainPalette.Accent.copy(alpha = 0.35f),
                                RoundedCornerShape(20.dp),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        enabled = !ui.followBusy,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onFollow,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (followed) ZIcons.Check else ZIcons.Add,
                    contentDescription = if (followed) "已关注" else "关注",
                    tint = followFg,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (followed) "已关注" else "关注",
                    color = followFg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (ui.artistId > 0L) {
            Text(
                text = "歌手页",
                color = MainPalette.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MainPalette.Accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenArtist,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        ui.medalCount?.takeIf { it > 0 }?.let { n ->
            Text(
                text = "${n}枚徽章",
                color = MainPalette.Secondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun UserShelfTabs(
    progress: Float,
    createdCount: Int,
    collectedCount: Int,
    onSelect: (page: Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val t = progress.coerceIn(0f, 1f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UserChip(
            label = if (createdCount > 0) "创建的歌单 $createdCount" else "创建的歌单",
            active = 1f - t,
            onClick = {
                if (t >= 0.5f) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onSelect(0)
            },
        )
        UserChip(
            label = if (collectedCount > 0) "收藏的歌单 $collectedCount" else "收藏的歌单",
            active = t,
            onClick = {
                if (t < 0.5f) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onSelect(1)
            },
        )
    }
}

@Composable
private fun UserListenTabs(
    shelf: UserListenShelf,
    hasWeek: Boolean,
    hasAll: Boolean,
    onShelf: (UserListenShelf) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "听歌排行",
            modifier = Modifier.weight(1f),
            color = MainPalette.Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (hasWeek) {
            UserChip(
                label = "近一周",
                active = if (shelf == UserListenShelf.Week || !hasAll) 1f else 0f,
                onClick = { onShelf(UserListenShelf.Week) },
            )
            Spacer(Modifier.width(8.dp))
        }
        if (hasAll) {
            UserChip(
                label = "所有时间",
                active = if (shelf == UserListenShelf.All || !hasWeek) 1f else 0f,
                onClick = { onShelf(UserListenShelf.All) },
            )
        }
    }
}

@Composable
private fun UserChip(
    label: String,
    active: Float,
    onClick: () -> Unit,
) {
    val t = active.coerceIn(0f, 1f)
    Text(
        text = label,
        color = lerp(MainPalette.Secondary, MainPalette.Accent, t),
        fontSize = 13.sp,
        fontWeight = if (t >= 0.5f) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MainPalette.Accent.copy(alpha = 0.14f * t))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun UserPlaylistPane(
    playlists: List<PlaylistSummary>,
    emptyText: String,
    loading: Boolean,
    onOpen: (Long, String, String?) -> Unit,
) {
    when {
        playlists.isEmpty() && loading -> Spacer(Modifier.height(8.dp))
        playlists.isEmpty() -> {
            Text(
                text = emptyText,
                color = MainPalette.Hint,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            )
        }
        else -> {
            Column {
                playlists.forEach { pl ->
                    Box(Modifier.padding(vertical = 5.dp)) {
                        UserPlaylistRow(
                            pl = pl,
                            onClick = { onOpen(pl.id, pl.name, pl.coverUrl) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserPlaylistRow(
    pl: PlaylistSummary,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            UrlImage(
                url = pl.resolvedCoverUrl(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                maxPx = UrlImageCache.THUMB_MAX_PX,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = pl.name,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${pl.trackCount} 首 · 播放 ${NcmHomeParse.formatPlayCount(pl.playCount)}",
                style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
            )
        }
    }
}

private fun genderMark(gender: Int): String? = when (gender) {
    1 -> "♂"
    2 -> "♀"
    else -> null
}

private fun genderTint(gender: Int): Color = when (gender) {
    1 -> Color(0xFF8EC8FF)
    2 -> Color(0xFFFFB7C8)
    else -> Color.White
}
