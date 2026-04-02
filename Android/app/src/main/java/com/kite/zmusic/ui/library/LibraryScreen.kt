package com.kite.zmusic.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.SubcountBrief
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.UserProfileBrief
import com.kite.zmusic.ui.scifi.SciFiBackdrop
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.scifi.SciFiHudTextAction

private val Cyan = Color(0xFF00FFD1)
private val Dim = Color(0xFF8FA8B8)
private val Green = Color(0xFF39FF9C)
private val PanelBg = Color(0xFF0C1522)
private val CardInner = Color(0xFF111C2E)
private val MutedLine = Cyan.copy(alpha = 0.14f)

@Composable
private fun LibraryHomeHeader(
    isLandscape: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "我的",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.92f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isLandscape) 18.sp else 22.sp,
                    letterSpacing = 0.5.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isLandscape) "PROFILE // 资料与统计" else "资料 · 歌单 · 红心",
                style = TextStyle(
                    color = Dim.copy(alpha = 0.65f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (isLandscape) 9.sp else 10.sp,
                    letterSpacing = if (isLandscape) 1.4.sp else 1.2.sp,
                ),
            )
        }
        SciFiHudTextAction(text = "刷新", onClick = onRefresh)
    }
}

@Composable
private fun LibraryLoadingBlock() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = Cyan.copy(alpha = 0.85f),
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun LibraryErrorText(err: String) {
    Text(
        text = err,
        style = TextStyle(
            color = Color(0xFFFFB86C),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        ),
    )
}

@Composable
private fun LibraryGuestBanner() {
    Text(
        text = "GUEST // 游客模式 · 数据与正式账号可能不一致",
        style = TextStyle(
            color = Dim.copy(alpha = 0.75f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
        ),
    )
}

@Composable
private fun LibraryFooterHint() {
    Text(
        text = "在歌单详情中点击曲目即可播放",
        style = TextStyle(
            color = Dim.copy(alpha = 0.38f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 0.5.sp,
        ),
    )
}

/** 横屏：左栏资料、右栏歌单列表，内容区最大宽度居中，避免整屏拉满 */
@Composable
private fun LibraryHomeLandscape(
    ui: LibraryUiState,
    vm: LibraryViewModel,
    padH: Dp,
    padV: Dp,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(min = 520.dp, max = 960.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = padH, vertical = padV),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                Modifier
                    .weight(0.36f)
                    .fillMaxHeight()
                    .widthIn(min = 268.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                LibraryHomeHeader(isLandscape = true, onRefresh = { vm.refresh() })
                Spacer(Modifier.height(14.dp))
                if (ui.loading && ui.playlists.isEmpty()) {
                    LibraryLoadingBlock()
                    Spacer(Modifier.height(12.dp))
                }
                ui.error?.let { err ->
                    LibraryErrorText(err)
                    Spacer(Modifier.height(10.dp))
                }
                if (ui.isGuest) {
                    LibraryGuestBanner()
                    Spacer(Modifier.height(10.dp))
                }
                ui.profile?.let { p ->
                    ProfileCard(
                        profile = p,
                        likedCount = ui.likedTrackCount,
                        subcount = ui.subcount,
                        sideColumn = true,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MutedLine.copy(alpha = 0.85f)),
            )
            LazyColumn(
                Modifier
                    .weight(0.64f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    LibrarySectionTitle("歌单 · ${ui.playlists.size}")
                    Spacer(Modifier.height(4.dp))
                }
                items(ui.playlists, key = { it.id }) { pl ->
                    PlaylistRow(pl = pl, onClick = { vm.openPlaylist(pl) })
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    LibraryFooterHint()
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    sessionRepository: SessionRepository,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    onPlaylistFullScreenChange: (Boolean) -> Unit = {},
    playbackState: PlaybackUiState = PlaybackUiState(),
    pendingLibraryOpen: Pair<Long, String>? = null,
    onConsumePendingLibraryOpen: () -> Unit = {},
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit = { _, _, _, _ -> },
) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModelFactory(sessionRepository))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val padH = if (isLandscape) 24.dp else 28.dp
    val padV = if (isLandscape) 16.dp else 22.dp

    LaunchedEffect(pendingLibraryOpen?.first, pendingLibraryOpen?.second) {
        val p = pendingLibraryOpen ?: return@LaunchedEffect
        vm.openPlaylistFromId(p.first, p.second)
        onConsumePendingLibraryOpen()
    }

    val sheetOpen = ui.sheet !is LibrarySheet.Hidden
    BackHandler(enabled = sheetOpen) { vm.dismissSheet() }

    DisposableEffect(Unit) {
        onDispose { onPlaylistFullScreenChange(false) }
    }
    SideEffect {
        onPlaylistFullScreenChange(ui.sheet !is LibrarySheet.Hidden)
    }

    AnimatedContent(
        targetState = ui.sheet,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val toList = targetState is LibrarySheet.Hidden
            val enter =
                fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { w -> if (toList) -w / 5 else w },
                    )
            val exit =
                fadeOut(tween(220)) +
                    slideOutHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        targetOffsetX = { w -> if (toList) w / 5 else -w },
                    )
            enter togetherWith exit
        },
        contentKey = { s ->
            when (s) {
                is LibrarySheet.Hidden -> "list"
                else -> "detail"
            }
        },
        label = "libraryListOrDetail",
    ) { sheet ->
        when (sheet) {
            is LibrarySheet.Hidden -> {
                Box(Modifier.fillMaxSize()) {
                    if (isLandscape) {
                        LibraryHomeLandscape(
                            ui = ui,
                            vm = vm,
                            padH = padH,
                            padV = padV,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = padH, vertical = padV),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            item {
                                LibraryHomeHeader(
                                    isLandscape = false,
                                    onRefresh = { vm.refresh() },
                                )
                            }

                            if (ui.loading && ui.playlists.isEmpty()) {
                                item {
                                    LibraryLoadingBlock()
                                }
                            }

                            ui.error?.let { err ->
                                item {
                                    LibraryErrorText(err)
                                }
                            }

                            if (ui.isGuest) {
                                item {
                                    LibraryGuestBanner()
                                }
                            }

                            ui.profile?.let { p ->
                                item {
                                    ProfileCard(
                                        profile = p,
                                        likedCount = ui.likedTrackCount,
                                        subcount = ui.subcount,
                                        sideColumn = false,
                                    )
                                }
                            }

                            item {
                                LibrarySectionTitle("歌单 · ${ui.playlists.size}")
                            }

                            items(ui.playlists, key = { it.id }) { pl ->
                                PlaylistRow(pl = pl, onClick = { vm.openPlaylist(pl) })
                            }

                            item {
                                Spacer(Modifier.height(20.dp))
                                LibraryFooterHint()
                            }
                        }
                    }

                    if (ui.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(padH + 8.dp, padV)
                                .size(22.dp),
                            color = Cyan.copy(alpha = 0.6f),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
            else -> {
                LibraryPlaylistDetailPage(
                    sheet = sheet,
                    onBack = vm::dismissSheet,
                    isLandscape = isLandscape,
                    horizontalPadding = padH,
                    verticalPadding = padV,
                    playbackState = playbackState,
                    onPlayTrack = { index ->
                        when (sheet) {
                            is LibrarySheet.Ready -> onPlayTracks(
                                sheet.tracks,
                                index,
                                sheet.id,
                                sheet.title,
                            )
                            else -> Unit
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibrarySectionTitle(text: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = text.uppercase(),
            style = TextStyle(
                color = Dim.copy(alpha = 0.72f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Cyan.copy(alpha = 0.45f), Cyan.copy(alpha = 0.08f), Color.Transparent),
                    ),
                ),
        )
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfileBrief,
    likedCount: Int,
    subcount: SubcountBrief?,
    sideColumn: Boolean = false,
) {
    val ring = Brush.linearGradient(
        colors = listOf(Cyan.copy(alpha = 0.55f), Color(0xFF2A6BFF).copy(alpha = 0.35f), Green.copy(alpha = 0.4f)),
    )
    val avatarSize = if (sideColumn) 64.dp else 76.dp
    val placeholderSp = if (sideColumn) 24.sp else 28.sp

    @Composable
    fun AvatarBox() {
        Box(
            Modifier
                .size(avatarSize)
                .border(2.dp, ring, CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(Color(0xFF152535)),
            contentAlignment = Alignment.Center,
        ) {
            val url = profile.avatarUrl
            if (!url.isNullOrBlank()) {
                UrlImage(
                    url = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = profile.nickname.take(1).uppercase(),
                    style = TextStyle(
                        color = Cyan.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = placeholderSp,
                    ),
                )
            }
        }
    }

    @Composable
    fun StatBlock() {
        if (sideColumn) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                profile.level?.let { lv -> StatChip("LV $lv") }
                profile.listenSongs?.let { n -> StatChip("累计听歌 $n") }
                StatChip("红心曲 $likedCount")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                profile.level?.let { lv -> StatChip("LV $lv") }
                profile.listenSongs?.let { n -> StatChip("累计听歌 $n") }
                StatChip("红心曲 $likedCount")
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(CardInner.copy(alpha = 0.98f), PanelBg.copy(alpha = 0.88f)),
                ),
            )
            .border(1.dp, MutedLine, RoundedCornerShape(18.dp))
            .padding(if (sideColumn) 14.dp else 18.dp),
    ) {
        if (sideColumn) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvatarBox()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = profile.nickname,
                    style = TextStyle(
                        color = Cyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.8.sp,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                profile.signature?.let { sig ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sig,
                        style = TextStyle(
                            color = Dim.copy(alpha = 0.88f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                StatBlock()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBox()
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.nickname,
                        style = TextStyle(
                            color = Cyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    profile.signature?.let { sig ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sig,
                            style = TextStyle(
                                color = Dim.copy(alpha = 0.88f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    StatBlock()
                }
            }
        }
        subcount?.let { s ->
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MutedLine)
            Spacer(Modifier.height(12.dp))
            Text(
                text = buildString {
                    append("收藏歌单 ${s.subPlaylistCount}")
                    append(" · 创建 ${s.createdPlaylistCount}")
                    append(" · 关注歌手 ${s.subArtistCount}")
                    append(" · 收藏专辑 ${s.subAlbumCount}")
                },
                style = TextStyle(
                    color = Dim.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    letterSpacing = 0.4.sp,
                    textAlign = if (sideColumn) TextAlign.Center else TextAlign.Start,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Cyan.copy(alpha = 0.08f))
            .border(1.dp, Cyan.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = TextStyle(
            color = Green.copy(alpha = 0.88f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 0.4.sp,
        ),
    )
}

@Composable
private fun PlaylistRow(pl: PlaylistSummary, onClick: () -> Unit) {
    val tag = when {
        pl.isHeartPlaylist -> "HEART"
        pl.isOwned -> "创建"
        pl.isSubscribed -> "收藏"
        else -> "歌单"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardInner.copy(alpha = 0.72f))
            .border(1.dp, MutedLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0E1624))
                .border(1.dp, Cyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
        ) {
            val cover = pl.coverUrl
            if (!cover.isNullOrBlank()) {
                UrlImage(
                    url = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = pl.name,
                style = TextStyle(
                    color = Cyan.copy(alpha = 0.92f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.6.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$tag · ${pl.trackCount} 首 · 播放 ${formatPlayCount(pl.playCount)}",
                style = TextStyle(
                    color = Dim.copy(alpha = 0.65f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 0.3.sp,
                ),
            )
        }
        Text(
            text = "›",
            style = TextStyle(
                color = Cyan.copy(alpha = 0.35f),
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
            ),
        )
    }
}

@Composable
private fun LibraryPlaylistTracksPanel(
    sheet: LibrarySheet,
    onDismiss: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    playbackState: PlaybackUiState,
    modifier: Modifier = Modifier,
) {
    val dismissFn by rememberUpdatedState(onDismiss)
    Box(modifier.padding(top = 4.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF050A12).copy(alpha = 0.42f))
                .border(1.dp, Cyan.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = sheet,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (
                            fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                                slideInVertically { h -> h / 10 }
                            ).togetherWith(fadeOut(tween(160)))
                    },
                    contentKey = { s ->
                        when (s) {
                            is LibrarySheet.Loading -> "l"
                            is LibrarySheet.Failed -> "f"
                            is LibrarySheet.Ready -> "r"
                            else -> "x"
                        }
                    },
                    label = "playlistBody",
                ) { s ->
                    when (s) {
                        is LibrarySheet.Loading -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    CircularProgressIndicator(
                                        color = Cyan.copy(alpha = 0.85f),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(40.dp),
                                    )
                                    Text(
                                        text = "FETCHING // TRACKS",
                                        style = TextStyle(
                                            color = Dim.copy(alpha = 0.55f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            letterSpacing = 1.6.sp,
                                        ),
                                    )
                                }
                            }
                        }
                        is LibrarySheet.Failed -> {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    text = "ERR // ${s.message}",
                                    style = TextStyle(
                                        color = Color(0xFFFFB86C),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        letterSpacing = 0.4.sp,
                                    ),
                                )
                                Text(
                                    text = "使用系统返回或手势返回",
                                    style = TextStyle(
                                        color = Dim.copy(alpha = 0.5f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.5.sp,
                                    ),
                                )
                            }
                        }
                        is LibrarySheet.Ready -> {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    Text(
                                        text = "TRACKS // ${s.tracks.size} · 点击播放",
                                        style = TextStyle(
                                            color = Dim.copy(alpha = 0.48f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            letterSpacing = 1.2.sp,
                                        ),
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }
                                val plId = s.id
                                val sameSource = playbackState.sourcePlaylistId == plId
                                val playingId = playbackState.currentTrack?.id
                                itemsIndexed(
                                    s.tracks,
                                    key = { _, t -> t.id },
                                ) { idx, t ->
                                    val isPlayingRow = sameSource && playingId == t.id
                                    TrackRowItem(
                                        t,
                                        trackIndex = idx + 1,
                                        isPlaying = isPlayingRow,
                                        onClick = { onPlayTrack(idx) },
                                    )
                                }
                            }
                        }
                        is LibrarySheet.Hidden -> Spacer(Modifier.height(0.dp))
                    }
                }
            }
        }
    }
}

/** 歌单曲目全屏页（非弹层）；横屏为左标题栏 + 右曲目区，内容宽度居中 */
@Composable
private fun LibraryPlaylistDetailPage(
    sheet: LibrarySheet,
    onBack: () -> Unit,
    isLandscape: Boolean,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    playbackState: PlaybackUiState,
    onPlayTrack: (Int) -> Unit,
) {
    val dismissFn by rememberUpdatedState(onBack)
    val title = when (sheet) {
        is LibrarySheet.Loading -> sheet.title
        is LibrarySheet.Ready -> sheet.title
        is LibrarySheet.Failed -> sheet.title
        is LibrarySheet.Hidden -> ""
    }

    val scrim = Color(0xFF030810).copy(alpha = 0.78f)

    @Composable
    fun PlaylistDetailHeader(modifier: Modifier = Modifier) {
        Column(
            modifier
                .fillMaxWidth()
                .pointerInput(dismissFn) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dy ->
                            if (dy > 0) acc += dy
                        },
                        onDragEnd = {
                            if (acc > 110f) dismissFn()
                            acc = 0f
                        },
                        onDragCancel = { acc = 0f },
                    )
                },
        ) {
                Text(
                    text = "PLAYLIST // DETAIL",
                    style = TextStyle(
                        color = Dim.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = title.uppercase(),
                    style = TextStyle(
                        color = Cyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isLandscape) 13.sp else 14.sp,
                        letterSpacing = 1.2.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
        }
    }

    if (isLandscape) {
        Box(Modifier.fillMaxSize()) {
            SciFiBackdrop(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(scrim))
            Row(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(min = 560.dp, max = 960.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(
                    Modifier
                        .weight(0.34f)
                        .fillMaxHeight(),
                ) {
                    PlaylistDetailHeader()
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MutedLine)
                }
                Box(
                    Modifier
                        .weight(0.66f)
                        .fillMaxHeight(),
                ) {
                    LibraryPlaylistTracksPanel(
                        sheet = sheet,
                        onDismiss = dismissFn,
                        onPlayTrack = onPlayTrack,
                        playbackState = playbackState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            SciFiBackdrop(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(scrim))
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding)
                    .padding(top = 6.dp, bottom = verticalPadding),
            ) {
            PlaylistDetailHeader()
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MutedLine)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LibraryPlaylistTracksPanel(
                    sheet = sheet,
                    onDismiss = dismissFn,
                    onPlayTrack = onPlayTrack,
                    playbackState = playbackState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            }
        }
    }
}

@Composable
private fun TrackRowItem(
    t: TrackRow,
    trackIndex: Int? = null,
    isPlaying: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isPlaying) Cyan.copy(alpha = 0.75f) else Cyan.copy(alpha = 0.12f),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "trackBorder",
    )
    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) Color(0xFF0A1828).copy(alpha = 0.94f) else Color(0xFF060D18).copy(alpha = 0.88f),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "trackBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trackIndex != null) {
            Text(
                text = "%02d".format(trackIndex),
                style = TextStyle(
                    color = Dim.copy(alpha = 0.42f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                ),
                modifier = Modifier.width(28.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = t.name,
                style = TextStyle(
                    color = Cyan.copy(alpha = 0.92f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${t.artists}${t.album?.let { " · $it" } ?: ""} · ${formatDuration(t.durationMs)}",
                style = TextStyle(
                    color = Dim.copy(alpha = 0.62f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    lineHeight = 11.sp,
                    letterSpacing = 0.25.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPlaying) {
            Text(
                text = "▶",
                style = TextStyle(
                    color = Green.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                ),
            )
        } else {
            Text(
                text = "›",
                style = TextStyle(
                    color = Cyan.copy(alpha = 0.35f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun formatPlayCount(n: Long): String = when {
    n >= 100_000_000 -> "%.1f亿".format(n / 100_000_000.0)
    n >= 10_000 -> "%.1f万".format(n / 10_000.0)
    else -> n.toString()
}
