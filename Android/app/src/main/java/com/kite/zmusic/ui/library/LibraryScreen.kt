package com.kite.zmusic.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.ChromeWallpaperSurface
import com.kite.zmusic.data.CollectedAlbum
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.UserProfileBrief
import com.kite.zmusic.data.VipKind
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.chrome.LocalChromeWallpaperPainted
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassPromptField
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.common.rememberUrlImageBitmap
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.theme.TextTheme
import com.kite.zmusic.ui.main.mainContentPadH
import com.kite.zmusic.ui.main.wallpaperItemChrome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private fun profileBlankBrush() = Brush.verticalGradient(
    colors = listOf(MainPalette.Surface, MainPalette.Page),
)
private val VipGold = Color(0xFFFFD789)
private val SvipPlate = Color(0xFF1A120C)
private val VipPlate = Color(0xFFEC4141)
internal val ProfileAvatarBadgeHang = 0.dp

private enum class LibraryCollectionKind { Playlist, Album }

@Stable
private class CollectionPagerState(
    private val scope: CoroutineScope,
    initial: Float = 0f,
) {
    var offset by mutableFloatStateOf(initial)
        private set

    private val anim = Animatable(initial)
    private var job: Job? = null
    private var gen: Int = 0
    private var dragging: Boolean = false
    private var target: Float = initial

    fun dragDelta(deltaPx: Float, widthPx: Float): Float {
        if (!dragging) {
            cancelAnim()
            dragging = true
        }
        val w = widthPx.coerceAtLeast(1f)
        val old = offset
        offset = (old - deltaPx / w).coerceIn(0f, 1f)
        return (old - offset) * w
    }

    fun settle(velocityPx: Float): Float {
        dragging = false
        val dest = when {
            velocityPx < -680f -> 1f
            velocityPx > 680f -> 0f
            else -> if (offset >= 0.5f) 1f else 0f
        }
        animateTo(dest)
        return dest
    }

    fun goTo(dest: Float) {
        dragging = false
        val d = dest.coerceIn(0f, 1f)
        if (abs(offset - d) < 0.002f && job?.isActive != true) {
            offset = d
            target = d
            return
        }
        if (abs(target - d) < 0.002f && job?.isActive == true) return
        animateTo(d)
    }

    private fun animateTo(dest: Float) {
        val my = ++gen
        dragging = false
        target = dest
        job?.cancel()
        job = scope.launch {
            anim.snapTo(offset)
            anim.animateTo(
                dest,
                spring(dampingRatio = 0.82f, stiffness = 420f),
            ) {
                if (my == gen) offset = value
            }
            if (my != gen) return@launch
            offset = dest
            anim.snapTo(dest)
        }
    }

    private fun cancelAnim() {
        gen += 1
        job?.cancel()
        job = null
    }
}

@Composable
private fun LibraryLoadingBlock() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MainPalette.Accent,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun LibraryErrorText(err: String) {
    Text(
        text = err,
        style = TextStyle(
            color = MainPalette.Secondary,
            fontSize = 14.sp,
        ),
    )
}

@Composable
private fun LibraryGuestBanner() {
    Text(
        text = "游客模式 · 数据与正式账号可能不一致",
        style = TextStyle(
            color = MainPalette.Secondary,
            fontSize = 13.sp,
        ),
    )
}

@Composable
private fun LibrarySectionEmpty(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = MainPalette.Hint,
            fontSize = 13.sp,
        ),
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun LibraryHomeLandscape(
    ui: LibraryUiState,
    padH: Dp,
    contentBottomInset: Dp,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    pullState: UserSpaceRevealState,
    spaceProgress: Float,
    customBgPath: String?,
    onAvatarPositioned: (Offset, Float) -> Unit,
    onMorePlaylist: (PlaylistSummary) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenLikedArtists: () -> Unit,
    collectionKind: LibraryCollectionKind,
    onCollectionKind: (LibraryCollectionKind) -> Unit,
    onOpenAlbum: (CollectedAlbum) -> Unit,
    onLoadMoreAlbums: () -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 1 && last >= total - 2
        }
    }
    LaunchedEffect(
        nearEnd,
        collectionKind,
        ui.albums.size,
        ui.albumsHasMore,
        ui.albumsLoadingMore,
    ) {
        if (collectionKind == LibraryCollectionKind.Album &&
            nearEnd &&
            ui.albumsHasMore &&
            !ui.albumsLoadingMore &&
            ui.albums.isNotEmpty()
        ) {
            onLoadMoreAlbums()
        }
    }
    val hasPhoto = LocalChromeWallpaperPainted.current ||
        !customBgPath.isNullOrBlank() ||
        !ui.profile?.backgroundUrl.isNullOrBlank()
    val p = spaceProgress.coerceIn(0f, 1f)
    val pulling = p > 0.001f
    val sheetA = spaceSheetAlpha(p)
    val bannerH = 168.dp
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .chromePage()
            .clipToBounds(),
    ) {
        val viewportH = maxHeight
        val photoH = lerp(bannerH, viewportH, p)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !pullState.isOpen && !pullState.dragging && spaceProgress < 0.98f,
            contentPadding = PaddingValues(bottom = contentBottomInset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "profile-banner") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(photoH)
                        .clipToBounds(),
                ) {
                    ProfileFixedBackground(
                        backgroundUrl = ui.profile?.backgroundUrl,
                        localPath = customBgPath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .requiredHeight(viewportH)
                            .align(Alignment.TopCenter),
                    )
                    ProfileLandscapeBanner(
                        profile = ui.profile,
                        loading = ui.loading && ui.profile == null,
                        hasPhoto = hasPhoto,
                        spaceProgress = spaceProgress,
                        onEnterSpace = { pullState.open() },
                        onOpenFollows = onOpenLikedArtists,
                        onAvatarPositioned = onAvatarPositioned,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bannerH)
                            .align(Alignment.TopStart),
                    )
                }
            }
            item(key = "profile-sheet") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .chromePage()
                        .padding(horizontal = padH)
                        .then(
                            if (pulling) {
                                Modifier.graphicsLayer { alpha = sheetA }
                            } else {
                                Modifier
                            },
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (ui.loading && ui.playlists.isEmpty()) {
                        LibraryLoadingBlock()
                    }
                    ui.error?.let { err ->
                        LibraryErrorText(err)
                    }
                    if (ui.isGuest) {
                        LibraryGuestBanner()
                    }
                        LibraryPlaylistBody(
                            playlists = ui.playlists,
                            albums = ui.albums,
                            albumsTotal = ui.albumsTotal,
                            albumsHasMore = ui.albumsHasMore,
                            albumsLoading = ui.albumsLoading,
                            albumsLoadingMore = ui.albumsLoadingMore,
                            albumsError = ui.albumsError,
                            isGuest = ui.isGuest,
                            collectionKind = collectionKind,
                            onCollectionKind = onCollectionKind,
                            onOpenPlaylist = onOpenPlaylist,
                            onMorePlaylist = onMorePlaylist,
                            onCreatePlaylist = onCreatePlaylist,
                            onOpenAlbum = onOpenAlbum,
                        )
                }
            }
        }
    }
}

@Composable
private fun ProfileLandscapeBanner(
    profile: UserProfileBrief?,
    loading: Boolean,
    hasPhoto: Boolean,
    spaceProgress: Float,
    onEnterSpace: () -> Unit,
    onOpenFollows: () -> Unit,
    onAvatarPositioned: (Offset, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clipToBounds()) {
        if (!LocalChromeWallpaperPainted.current) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(120.dp)
                    .graphicsLayer { alpha = (1f - spaceProgress).coerceIn(0f, 1f) }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        ),
                    ),
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading && profile == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = if (hasPhoto) Color.White else MainPalette.Accent,
                    strokeWidth = 2.dp,
                )
                return@Row
            }
            val p = profile
            if (p != null) {
                ProfileAvatar(
                    profile = p,
                    size = 64.dp,
                    placeholderSp = 22.sp,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        if (spaceProgress <= SpaceAvatarHandoffProgress) {
                            onAvatarPositioned(
                                coords.positionInWindow(),
                                coords.size.width.toFloat(),
                            )
                        }
                    }.then(
                        if (spaceProgress > SpaceAvatarHandoffProgress) {
                            Modifier.graphicsLayer {
                                alpha = 0f
                                clip = false
                            }
                        } else {
                            Modifier
                        },
                    ),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    ProfileNameRow(
                        profile = p,
                        onPhoto = hasPhoto,
                        titleSize = 20.sp,
                        modifier = Modifier.spaceIdentityLeave(spaceProgress, 0),
                    )
                    ProfileIdentityMeta(
                        profile = p,
                        onPhoto = hasPhoto,
                        spaceProgress = spaceProgress,
                        center = false,
                        compact = true,
                        onOpenFollows = onOpenFollows,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .spaceIdentityLeave(spaceProgress, 1)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (hasPhoto) Color.White.copy(alpha = 0.94f)
                            else MainPalette.Accent.copy(alpha = 0.12f),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onEnterSpace,
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "进入用户空间",
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
fun LibraryScreen(
    sessionRepository: SessionRepository,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    onOpenOverlay: (MainOverlay) -> Unit = {},
    contentBottomInset: Dp = 0.dp,
    onUserSpaceProgress: (Float) -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel(
        factory = LibraryViewModelFactory(
            sessionRepository,
            app.likedPlaylistRepository,
            app.playlistTracksCache,
            app.playlistCollectionRepository,
            app.libraryHomeRepository,
        ),
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val padH = mainContentPadH(isLandscape)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pullState = rememberUserSpacePullState(scope)
    val spaceProgress = pullState.progress
    val screenHpx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var avatarStart by remember { mutableStateOf(Offset.Zero) }
    var avatarStartSize by remember { mutableFloatStateOf(0f) }
    var avatarFlightLocked by remember { mutableStateOf(false) }
    var morePlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var renameTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var createOpen by remember { mutableStateOf(false) }
    var createDraft by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<PlaylistSummary?>(null) }
    var confirmUnsub by remember { mutableStateOf<PlaylistSummary?>(null) }
    var collectionKind by remember { mutableStateOf(LibraryCollectionKind.Playlist) }
    val uid = ui.profile?.userId ?: 0L
    var customBgPath by remember(uid) { mutableStateOf(app.userSpaceBackgroundStore.pathFor(uid)) }
    val wallpaper by app.chromeWallpaperStore.state.collectAsStateWithLifecycle()
    val profileChrome = wallpaper.frame(ChromeWallpaperSurface.Profile, isLandscape) != null
    val heroBgPath = if (profileChrome) null else customBgPath

    fun openPlaylist(pl: PlaylistSummary) {
        onOpenOverlay(
            MainOverlay.Playlist(
                id = pl.id,
                title = pl.name,
                coverUrl = pl.resolvedCoverUrl(),
                owned = pl.isOwned,
                heart = pl.isHeartPlaylist,
                collected = pl.isSubscribed,
            ),
        )
    }

    fun openAlbum(album: CollectedAlbum) {
        onOpenOverlay(MainOverlay.Album(album.id, album.name))
    }

    LaunchedEffect(pullState) {
        snapshotFlow {
            val raw = pullState.progress
            when {
                raw <= 0.001f -> 0f
                raw >= 0.999f -> 1f
                else -> (raw * 40f).toInt() / 40f
            }
        }
            .distinctUntilChanged()
            .collect { onUserSpaceProgress(it) }
    }
    LaunchedEffect(uid) {
        customBgPath = app.userSpaceBackgroundStore.pathFor(uid)
    }

    val pickBg = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (uid <= 0L) {
            context.showIslandNotice("登录后可设置空间背景")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val path = app.userSpaceBackgroundStore.import(uid, uri)
            if (path != null) {
                customBgPath = path
                context.showIslandNotice("已设置空间背景")
            } else {
                context.showIslandNotice("背景设置失败")
            }
        }
    }

    SideEffect {
        pullState.enabled = ui.profile != null
        pullState.rangePx = (screenHpx * 0.78f).coerceAtLeast(1f)
        pullState.activationPx = with(density) { 48.dp.toPx() }
        if (spaceProgress <= SpaceAvatarHandoffProgress) {
            avatarFlightLocked = false
        } else if (!avatarFlightLocked && avatarStartSize > 1f) {
            avatarFlightLocked = true
        }
    }

    fun captureAvatar(pos: Offset, size: Float) {
        if (avatarFlightLocked) return
        avatarStart = pos
        avatarStartSize = size
    }

    Box(modifier.fillMaxSize()) {
        if (isLandscape) {
            LibraryHomeLandscape(
                ui = ui,
                padH = padH,
                contentBottomInset = contentBottomInset,
                onOpenPlaylist = ::openPlaylist,
                pullState = pullState,
                spaceProgress = spaceProgress,
                customBgPath = heroBgPath,
                onAvatarPositioned = ::captureAvatar,
                onMorePlaylist = { morePlaylist = it },
                onCreatePlaylist = {
                    createDraft = ""
                    createOpen = true
                },
                onOpenLikedArtists = { onOpenOverlay(MainOverlay.LikedArtists) },
                collectionKind = collectionKind,
                onCollectionKind = { collectionKind = it },
                onOpenAlbum = ::openAlbum,
                onLoadMoreAlbums = vm::loadMoreAlbums,
            )
        } else {
            LibraryHomePortrait(
                ui = ui,
                padH = padH,
                contentBottomInset = contentBottomInset,
                onOpenPlaylist = ::openPlaylist,
                pullState = pullState,
                spaceProgress = spaceProgress,
                customBgPath = heroBgPath,
                onAvatarPositioned = ::captureAvatar,
                onMorePlaylist = { morePlaylist = it },
                onCreatePlaylist = {
                    createDraft = ""
                    createOpen = true
                },
                onOpenLikedArtists = { onOpenOverlay(MainOverlay.LikedArtists) },
                collectionKind = collectionKind,
                onCollectionKind = { collectionKind = it },
                onOpenAlbum = ::openAlbum,
                onLoadMoreAlbums = vm::loadMoreAlbums,
            )
        }
        UserSpaceOverlay(
            progress = spaceProgress,
            profile = ui.profile,
            playlists = ui.playlists,
            likedTrackCount = ui.likedTrackCount,
            subcount = ui.subcount,
            customBgPath = customBgPath,
            backgroundUrl = ui.profile?.backgroundUrl,
            avatarStart = avatarStart,
            avatarStartSize = avatarStartSize,
            reveal = pullState,
            onClose = { pullState.close() },
            onPickBackground = {
                pickBg.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onClearBackground = {
                if (uid <= 0L) return@UserSpaceOverlay
                app.userSpaceBackgroundStore.clear(uid)
                customBgPath = null
                context.showIslandNotice("已恢复默认背景")
            },
            modifier = Modifier.fillMaxSize(),
        )
        morePlaylist?.let { pl ->
            GlassActionSheet(
                title = pl.name,
                message = "${pl.trackCount} 首",
                coverUrl = pl.resolvedCoverUrl(),
                onDismiss = { morePlaylist = null },
                actions = buildList {
                    if (pl.isOwned && !pl.isHeartPlaylist) {
                        add(
                            GlassSheetAction("重命名") {
                                renameDraft = pl.name
                                renameTarget = pl
                                morePlaylist = null
                            },
                        )
                        add(
                            GlassSheetAction("删除", destructive = true) {
                                confirmDelete = pl
                                morePlaylist = null
                            },
                        )
                    } else if (!pl.isOwned) {
                        if (pl.isSubscribed) {
                            add(
                                GlassSheetAction("取消收藏", destructive = true) {
                                    confirmUnsub = pl
                                    morePlaylist = null
                                },
                            )
                        } else {
                            add(
                                GlassSheetAction("收藏") {
                                    val target = pl
                                    morePlaylist = null
                                    scope.launch {
                                        context.showIslandNotice(
                                            app.playlistEditor.subscribe(target),
                                            target.coverUrl,
                                        )
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
        if (createOpen) {
            GlassAlertDialog(
                title = "新建歌单",
                confirmLabel = "创建",
                onConfirm = {
                    val name = createDraft
                    if (name.trim().isEmpty()) {
                        context.showIslandNotice("请输入歌单名称")
                        return@GlassAlertDialog
                    }
                    if (app.playlistEditor.hasCreatedName(name)) {
                        context.showIslandNotice("已有同名歌单")
                        return@GlassAlertDialog
                    }
                    createOpen = false
                    scope.launch {
                        context.showIslandNotice(app.playlistEditor.create(name))
                    }
                },
                onDismiss = { createOpen = false },
                extraContent = {
                    GlassPromptField(
                        value = createDraft,
                        onValueChange = { createDraft = it },
                        placeholder = "歌单名称",
                    )
                },
            )
        }
        renameTarget?.let { pl ->
            GlassAlertDialog(
                title = "重命名歌单",
                confirmLabel = "保存",
                onConfirm = {
                    val name = renameDraft
                    if (name.trim().isEmpty()) {
                        context.showIslandNotice("请输入歌单名称")
                        return@GlassAlertDialog
                    }
                    if (app.playlistEditor.hasCreatedName(name, exceptId = pl.id)) {
                        context.showIslandNotice("已有同名歌单")
                        return@GlassAlertDialog
                    }
                    renameTarget = null
                    scope.launch {
                        context.showIslandNotice(
                            app.playlistEditor.rename(pl, name),
                            pl.coverUrl,
                        )
                    }
                },
                onDismiss = { renameTarget = null },
                extraContent = {
                    GlassPromptField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        placeholder = "歌单名称",
                    )
                },
            )
        }
        confirmDelete?.let { pl ->
            GlassAlertDialog(
                title = "删除歌单？",
                message = "「${pl.name}」会被删除，歌曲文件不会动。",
                confirmLabel = "删除",
                confirmDestructive = true,
                onConfirm = {
                    confirmDelete = null
                    scope.launch {
                        context.showIslandNotice(
                            app.playlistEditor.deleteOwned(pl),
                            pl.coverUrl,
                        )
                    }
                },
                onDismiss = { confirmDelete = null },
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
                        context.showIslandNotice(
                            app.playlistEditor.unsubscribe(pl),
                            pl.coverUrl,
                        )
                    }
                },
                onDismiss = { confirmUnsub = null },
            )
        }
    }
}

@Composable
private fun LibraryHomePortrait(
    ui: LibraryUiState,
    padH: Dp,
    contentBottomInset: Dp,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    pullState: UserSpaceRevealState,
    spaceProgress: Float,
    customBgPath: String?,
    onAvatarPositioned: (Offset, Float) -> Unit,
    onMorePlaylist: (PlaylistSummary) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenLikedArtists: () -> Unit,
    collectionKind: LibraryCollectionKind,
    onCollectionKind: (LibraryCollectionKind) -> Unit,
    onOpenAlbum: (CollectedAlbum) -> Unit,
    onLoadMoreAlbums: () -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = statusTop + maxOf(312.dp, screenH * 0.36f)
    val overlap = 18.dp
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val heroPx = with(density) { heroHeight.toPx() }
    val scrollPx by remember(heroPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                heroPx
            }
        }
    }
    val identityAlpha = (1f - scrollPx / (heroPx * 0.48f)).coerceIn(0f, 1f)
    val sheetPadT = lerp(4.dp, statusTop + 6.dp, (scrollPx / (heroPx * 0.52f)).coerceIn(0f, 1f))
    val hasPhoto = LocalChromeWallpaperPainted.current ||
        !customBgPath.isNullOrBlank() ||
        !ui.profile?.backgroundUrl.isNullOrBlank()
    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 2
        }
    }
    SideEffect { pullState.atTop = atTop }
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 1 && last >= total - 2
        }
    }
    LaunchedEffect(
        nearEnd,
        collectionKind,
        ui.albums.size,
        ui.albumsHasMore,
        ui.albumsLoadingMore,
    ) {
        if (collectionKind == LibraryCollectionKind.Album &&
            nearEnd &&
            ui.albumsHasMore &&
            !ui.albumsLoadingMore &&
            ui.albums.isNotEmpty()
        ) {
            onLoadMoreAlbums()
        }
    }

    val p = spaceProgress.coerceIn(0f, 1f)
    val pulling = p > 0.001f
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .chromePage()
            .clipToBounds(),
    ) {
        val viewportH = maxHeight
        val sheetMinH = (viewportH - contentBottomInset).coerceAtLeast(0.dp)
        val photoH = lerp(heroHeight, viewportH, p)
        val sheetA = spaceSheetAlpha(p)
        Box(
            Modifier
                .fillMaxWidth()
                .height(photoH)
                .align(Alignment.TopCenter)
                .clipToBounds(),
        ) {
            ProfileFixedBackground(
                backgroundUrl = ui.profile?.backgroundUrl,
                localPath = customBgPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(viewportH)
                    .align(Alignment.TopCenter),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .userSpaceRevealGesture(pullState),
            userScrollEnabled = !pullState.isOpen && !pullState.dragging && spaceProgress < 0.98f,
            contentPadding = PaddingValues(bottom = contentBottomInset),
        ) {
            item(key = "profile-identity") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(photoH - overlap)
                        .zIndex(2f),
                ) {
                    ProfileSheetBlend(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .then(
                                if (pulling) {
                                    Modifier.graphicsLayer { alpha = sheetA }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    ProfileIdentity(
                        profile = ui.profile,
                        loading = ui.loading && ui.profile == null,
                        onPhoto = hasPhoto,
                        fade = identityAlpha,
                        spaceProgress = p,
                        hideAvatar = spaceProgress > SpaceAvatarHandoffProgress,
                        showSpaceHint = false,
                        onOpenFollows = onOpenLikedArtists,
                        avatarModifier = Modifier.onGloballyPositioned { coords ->
                            if (spaceProgress <= SpaceAvatarHandoffProgress) {
                                onAvatarPositioned(
                                    coords.positionInWindow(),
                                    coords.size.width.toFloat(),
                                )
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(heroHeight - overlap)
                            .statusBarsPadding()
                            .padding(horizontal = padH),
                    )
                    if (atTop && p < 0.12f) {
                        ProfileSpaceHint(
                            onPhoto = hasPhoto,
                            spaceProgress = p,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(4f)
                                .padding(bottom = 48.dp),
                        )
                    }
                }
            }
            item(key = "profile-sheet") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = sheetMinH)
                        .then(
                            if (pulling) {
                                Modifier.graphicsLayer { alpha = sheetA }
                            } else {
                                Modifier
                            },
                        )
                        .chromePage(),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = padH)
                            .padding(top = sheetPadT, bottom = 8.dp),
                    ) {
                        if (ui.loading && ui.playlists.isEmpty() && ui.profile != null) {
                            LibraryLoadingBlock()
                        }
                        ui.error?.let { err ->
                            LibraryErrorText(err)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (ui.isGuest) {
                            LibraryGuestBanner()
                            Spacer(Modifier.height(8.dp))
                        }
                        LibraryPlaylistBody(
                            playlists = ui.playlists,
                            albums = ui.albums,
                            albumsTotal = ui.albumsTotal,
                            albumsHasMore = ui.albumsHasMore,
                            albumsLoading = ui.albumsLoading,
                            albumsLoadingMore = ui.albumsLoadingMore,
                            albumsError = ui.albumsError,
                            isGuest = ui.isGuest,
                            collectionKind = collectionKind,
                            onCollectionKind = onCollectionKind,
                            onOpenPlaylist = onOpenPlaylist,
                            onMorePlaylist = onMorePlaylist,
                            onCreatePlaylist = onCreatePlaylist,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSheetBlend(modifier: Modifier = Modifier) {
    if (LocalChromeWallpaperPainted.current) return
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.4f to MainPalette.Page.copy(alpha = 0.28f),
                        0.72f to MainPalette.Page.copy(alpha = 0.78f),
                        1f to MainPalette.Page,
                    ),
                ),
            ),
    )
}

@Composable
internal fun ProfileFixedBackground(
    backgroundUrl: String?,
    localPath: String? = null,
    modifier: Modifier = Modifier,
    ignoreChrome: Boolean = false,
) {
    val painted = !ignoreChrome && LocalChromeWallpaperPainted.current
    if (painted) {
        Box(modifier)
        return
    }
    val custom = !localPath.isNullOrBlank()
    val localBmp = rememberFileImageBitmap(if (custom) localPath else null)
    val remoteBmp = rememberUrlImageBitmap(if (custom) null else backgroundUrl)
    val bmp = localBmp ?: remoteBmp
    val painter = remember(bmp) { bmp?.let { BitmapPainter(it) } }
    Box(
        modifier
            .clipToBounds()
            .background(profileBlankBrush())
            .then(
                if (painter != null) {
                    Modifier.paint(
                        painter = painter,
                        sizeToIntrinsics = false,
                        alignment = Alignment.Center,
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun ProfileHeroBlock(
    profile: UserProfileBrief?,
    loading: Boolean,
    padH: Dp,
    bottomInset: Dp,
    fillHeight: Boolean,
    spaceProgress: Float = 0f,
    customBgPath: String? = null,
    onAvatarPositioned: (Offset, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = if (fillHeight) {
        0.dp
    } else {
        statusTop + maxOf(400.dp, screenH * 0.46f)
    }
    val hasPhoto = !customBgPath.isNullOrBlank() || !profile?.backgroundUrl.isNullOrBlank()

    Box(
        modifier
            .then(
                if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(heroHeight),
            )
            .clipToBounds(),
    ) {
        ProfileFixedBackground(
            backgroundUrl = profile?.backgroundUrl,
            localPath = customBgPath,
            modifier = Modifier.fillMaxSize(),
        )
        ProfileIdentity(
            profile = profile,
            loading = loading,
            onPhoto = hasPhoto,
            fade = (1f - spaceProgress).coerceIn(0f, 1f),
            hideAvatar = spaceProgress > SpaceAvatarHandoffProgress,
            showSpaceHint = true,
            onOpenFollows = {},
            avatarModifier = Modifier.onGloballyPositioned { coords ->
                if (spaceProgress <= SpaceAvatarHandoffProgress) {
                    onAvatarPositioned(
                        coords.positionInWindow(),
                        coords.size.width.toFloat(),
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    start = padH,
                    end = padH,
                    bottom = if (fillHeight) bottomInset.coerceAtLeast(20.dp) else 16.dp,
                ),
        )
    }
}

@Composable
private fun ProfileIdentity(
    profile: UserProfileBrief?,
    loading: Boolean,
    onPhoto: Boolean,
    fade: Float,
    modifier: Modifier = Modifier,
    spaceProgress: Float = 0f,
    hideAvatar: Boolean = false,
    showSpaceHint: Boolean = false,
    onOpenFollows: () -> Unit = {},
    avatarModifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade },
            contentAlignment = Alignment.Center,
        ) {
            if (onPhoto && profile != null && !LocalChromeWallpaperPainted.current) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.22f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (-6).dp)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (loading && profile == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = if (onPhoto) Color.White else MainPalette.Accent,
                        strokeWidth = 2.dp,
                    )
                    return@Column
                }
                val p = profile ?: return@Column
                ProfileAvatar(
                    profile = p,
                    size = 80.dp,
                    placeholderSp = 28.sp,
                    modifier = avatarModifier.then(
                        if (hideAvatar) {
                            Modifier.graphicsLayer {
                                alpha = 0f
                                clip = false
                            }
                        } else {
                            Modifier
                        }
                    ),
                )
                Spacer(Modifier.height(12.dp))
                ProfileNameRow(
                    profile = p,
                    onPhoto = onPhoto,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .spaceIdentityLeave(spaceProgress, 0),
                )
                ProfileIdentityMeta(
                    profile = p,
                    onPhoto = onPhoto,
                    spaceProgress = spaceProgress,
                    center = true,
                    onOpenFollows = onOpenFollows,
                )
            }
        }
        if (showSpaceHint) {
            ProfileSpaceHint(
                onPhoto = onPhoto,
                spaceProgress = spaceProgress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(4f)
                    .padding(bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun ProfileSpaceHint(
    onPhoto: Boolean,
    spaceProgress: Float,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "下拉进入用户空间",
        style = identityCaptionStyle(onPhoto).copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.8.sp,
            color = if (onPhoto) TextTheme.OnPhotoMeta else MainPalette.Hint,
            shadow = if (onPhoto) {
                Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1f), blurRadius = 8f)
            } else {
                null
            },
        ),
        modifier = modifier.graphicsLayer {
            alpha = (1f - spaceProgress / 0.10f).coerceIn(0f, 1f)
        },
    )
}

private fun identityTitleStyle(onPhoto: Boolean) = TextStyle(
    color = if (onPhoto) TextTheme.OnPhotoTitle else MainPalette.Ink,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    shadow = if (onPhoto) {
        Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 1f), blurRadius = 10f)
    } else {
        null
    },
)

private fun identityCaptionStyle(onPhoto: Boolean) = TextStyle(
    color = if (onPhoto) TextTheme.OnPhotoSubtitle else MainPalette.Secondary,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    shadow = if (onPhoto) {
        Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(0f, 1f), blurRadius = 8f)
    } else {
        null
    },
)

private fun identityTagStyle(onPhoto: Boolean) = TextStyle(
    color = if (onPhoto) TextTheme.OnPhotoMeta else MainPalette.Hint,
    fontSize = 12.sp,
    letterSpacing = 0.2.sp,
    lineHeight = 16.sp,
    shadow = if (onPhoto) {
        Shadow(color = Color.Black.copy(alpha = 0.32f), offset = Offset(0f, 1f), blurRadius = 6f)
    } else {
        null
    },
)

private fun identityStatStyle(onPhoto: Boolean) = TextStyle(
    color = if (onPhoto) TextTheme.OnPhotoTitle.copy(alpha = 0.92f) else MainPalette.Ink,
    fontSize = 13.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.2.sp,
    shadow = if (onPhoto) {
        Shadow(color = Color.Black.copy(alpha = 0.35f), offset = Offset(0f, 1f), blurRadius = 6f)
    } else {
        null
    },
)

private data class IdentityTag(
    val text: String,
    val icon: ImageVector? = null,
)

private data class IdentityStat(
    val value: String,
    val label: String = "",
    val opensFollows: Boolean = false,
)

private fun identityTagsOf(profile: UserProfileBrief): List<IdentityTag> {
    return profile.expertTags.filter(::isReadableIdentityTag).map { IdentityTag(it) }
}

private fun genderMark(gender: Int): String? = when (gender) {
    1 -> "♂"
    2 -> "♀"
    else -> null
}

private fun genderTint(onPhoto: Boolean, gender: Int): Color = when (gender) {
    1 -> if (onPhoto) Color(0xFF8EC8FF) else Color(0xFF3D8BD9)
    2 -> if (onPhoto) Color(0xFFFFB7C8) else Color(0xFFD45D7A)
    else -> if (onPhoto) Color.White.copy(alpha = 0.88f) else MainPalette.Secondary
}

private fun isReadableIdentityTag(raw: String): Boolean {
    val t = raw.trim()
    if (t.length < 2) return false
    if (t.all { it.isDigit() || it == '.' }) return false
    return t.any { it.isLetter() || it in '\u4e00'..'\u9fff' }
}

private fun identityStatsOf(profile: UserProfileBrief): List<IdentityStat> {
    val stats = mutableListOf<IdentityStat>()
    val followTotal = (profile.follows ?: 0L) + profile.artistFollows.coerceAtLeast(0L)
    if (profile.follows != null || profile.artistFollows > 0L) {
        stats += IdentityStat(formatPlayCount(followTotal), "关注", opensFollows = true)
    }
    profile.followeds?.let { stats += IdentityStat(formatPlayCount(it), "粉丝") }
    profile.level?.let { stats += IdentityStat("Lv.$it") }
    profile.listenSongs?.let { stats += IdentityStat(formatPlayCount(it), "首") }
    return stats
}

@Composable
private fun ProfileNameRow(
    profile: UserProfileBrief,
    onPhoto: Boolean,
    modifier: Modifier = Modifier,
    titleSize: TextUnit? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Text(
            text = profile.nickname,
            style = if (titleSize != null) {
                identityTitleStyle(onPhoto).copy(fontSize = titleSize)
            } else {
                identityTitleStyle(onPhoto)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (profile.vipKind != VipKind.None) {
            ProfileVipMark(
                kind = profile.vipKind,
                iconUrl = profile.vipIconUrl,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileIdentityMeta(
    profile: UserProfileBrief,
    onPhoto: Boolean,
    spaceProgress: Float,
    center: Boolean,
    compact: Boolean = false,
    onOpenFollows: () -> Unit = {},
) {
    val tags = remember(profile) { identityTagsOf(profile) }
    val stats = remember(profile) { identityStatsOf(profile) }
    val mark = genderMark(profile.gender)
    val sig = profile.signature
    if (mark != null || sig != null) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .then(if (center) Modifier.widthIn(max = 300.dp) else Modifier)
                .spaceIdentityLeave(spaceProgress, 1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                6.dp,
                if (center) Alignment.CenterHorizontally else Alignment.Start,
            ),
        ) {
            if (mark != null) {
                Text(
                    text = mark,
                    style = identityCaptionStyle(onPhoto).copy(
                        color = genderTint(onPhoto, profile.gender),
                        fontSize = 14.sp,
                    ),
                )
            }
            if (sig != null) {
                Text(
                    text = sig,
                    style = identityCaptionStyle(onPhoto),
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (center && mark == null) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
    if (tags.isNotEmpty()) {
        FlowRow(
            modifier = Modifier
                .padding(top = 8.dp)
                .then(if (center) Modifier.widthIn(max = 320.dp) else Modifier)
                .spaceIdentityLeave(spaceProgress, 1),
            horizontalArrangement = Arrangement.spacedBy(
                6.dp,
                if (center) Alignment.CenterHorizontally else Alignment.Start,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tags.forEachIndexed { index, tag ->
                if (index > 0) {
                    Text("·", style = identityTagStyle(onPhoto))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    tag.icon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = identityTagStyle(onPhoto).color,
                            modifier = Modifier
                                .padding(end = 3.dp)
                                .size(12.dp),
                        )
                    }
                    Text(text = tag.text, style = identityTagStyle(onPhoto))
                }
            }
        }
    }
    if (stats.isNotEmpty()) {
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .spaceIdentityLeave(spaceProgress, 2),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stats.forEach { stat ->
                if (stat.opensFollows && stat.label.isNotEmpty()) {
                    Text(
                        text = "${stat.value} ${stat.label}",
                        style = identityStatStyle(onPhoto),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenFollows,
                        ),
                    )
                } else {
                    Text(
                        text = if (stat.label.isEmpty()) stat.value else "${stat.value} ${stat.label}",
                        style = identityStatStyle(onPhoto),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProfileAvatar(
    profile: UserProfileBrief,
    size: Dp,
    placeholderSp: TextUnit,
    modifier: Modifier = Modifier,
) {
    val ring = Brush.linearGradient(
        colors = listOf(MainPalette.Accent.copy(alpha = 0.85f), Color(0xFFFF8A80)),
    )
    val hang = ProfileAvatarBadgeHang
    Box(
        modifier
            .size(size + hang)
            .graphicsLayer { clip = false },
    ) {
        Box(
            Modifier
                .size(size)
                .align(Alignment.TopStart)
                .border(2.dp, ring, CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(MainPalette.Placeholder),
            contentAlignment = Alignment.Center,
        ) {
            val url = profile.avatarUrl
            if (!url.isNullOrBlank()) {
                UrlImage(
                    url = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    maxPx = UrlImageCache.THUMB_MAX_PX,
                )
            } else {
                Text(
                    text = profile.nickname.take(1).uppercase(),
                    style = TextStyle(
                        color = MainPalette.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = placeholderSp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ProfileVipMark(
    kind: VipKind,
    iconUrl: String?,
) {
    val remote = rememberUrlImageBitmap(iconUrl)
    if (remote != null) {
        val aspect = remote.width.toFloat() / remote.height.coerceAtLeast(1).toFloat()
        val h = 18.dp
        val w = (h * aspect).coerceIn(18.dp, 72.dp)
        Image(
            bitmap = remote,
            contentDescription = if (kind == VipKind.Svip) "SVIP" else "VIP",
            modifier = Modifier
                .height(h)
                .width(w),
            contentScale = ContentScale.Fit,
        )
        return
    }
    if (kind == VipKind.Svip) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(VipGold),
            )
            Box(
                Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(SvipPlate),
            )
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(VipGold),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(VipPlate),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.MusicNote,
                contentDescription = "VIP",
                tint = Color.White,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun LibrarySectionTitle(
    text: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun LibraryPlaylistBody(
    playlists: List<PlaylistSummary>,
    albums: List<CollectedAlbum>,
    albumsTotal: Int,
    albumsHasMore: Boolean,
    albumsLoading: Boolean,
    albumsLoadingMore: Boolean,
    albumsError: String?,
    isGuest: Boolean,
    collectionKind: LibraryCollectionKind,
    onCollectionKind: (LibraryCollectionKind) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onMorePlaylist: (PlaylistSummary) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenAlbum: (CollectedAlbum) -> Unit,
) {
    val liked = playlists.filter { it.isHeartPlaylist && it.isOwned }
    val created = playlists.filter { it.isOwned && !it.isHeartPlaylist }
    val collected = playlists.filter { !it.isOwned }
    val scope = rememberCoroutineScope()
    val pager = remember(scope) { CollectionPagerState(scope, collectionKind.ordinal.toFloat()) }

    PlaylistSectionColumn(
        title = "我喜欢的音乐",
        playlists = liked,
        emptyText = "还没有喜欢的音乐",
        onOpenPlaylist = onOpenPlaylist,
        onMorePlaylist = onMorePlaylist,
    )
    PlaylistSectionColumn(
        title = "创建的歌单",
        playlists = created,
        emptyText = "还没有创建的歌单",
        onOpenPlaylist = onOpenPlaylist,
        onMorePlaylist = onMorePlaylist,
        onCreate = onCreatePlaylist,
        showCount = true,
    )
    LibrarySectionTitle(
        text = "收藏",
        trailing = {
            CollectionKindSwitch(
                progress = pager.offset,
                onSelect = { kind ->
                    pager.goTo(kind.ordinal.toFloat())
                    onCollectionKind(kind)
                },
            )
        },
    )
    CollectionSwipePages(
        progress = pager.offset,
        pager = pager,
        onSettled = onCollectionKind,
        playlist = {
            if (collected.isEmpty()) {
                LibrarySectionEmpty("还没有收藏的歌单")
            } else {
                Column {
                    Text(
                        text = "${collected.size} 个",
                        style = TextStyle(
                            color = MainPalette.Hint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                    collected.forEach { pl ->
                        Box(Modifier.padding(vertical = 6.dp)) {
                            PlaylistRow(
                                pl = pl,
                                onClick = { onOpenPlaylist(pl) },
                                onMore = { onMorePlaylist(pl) },
                            )
                        }
                    }
                }
            }
        },
        album = {
            CollectedAlbumPane(
                albums = albums,
                total = albumsTotal,
                hasMore = albumsHasMore,
                loading = albumsLoading,
                loadingMore = albumsLoadingMore,
                error = albumsError,
                isGuest = isGuest,
                onOpen = onOpenAlbum,
            )
        },
    )
}

@Composable
private fun CollectionSwipePages(
    progress: Float,
    pager: CollectionPagerState,
    onSettled: (LibraryCollectionKind) -> Unit,
    playlist: @Composable () -> Unit,
    album: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragFrom by remember { mutableStateOf(0) }
    val nestedDispatcher = remember { NestedScrollDispatcher() }
    val nestedConnection = remember { object : NestedScrollConnection {} }
    val drag = rememberDraggableState { delta ->
        val parentPre = nestedDispatcher.dispatchPreScroll(
            Offset(delta, 0f),
            NestedScrollSource.UserInput,
        )
        val remaining = delta - parentPre.x
        val self = pager.dragDelta(remaining, widthPx)
        nestedDispatcher.dispatchPostScroll(
            consumed = Offset(self, 0f),
            available = Offset(remaining - self, 0f),
            source = NestedScrollSource.UserInput,
        )
    }
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .nestedScroll(nestedConnection, nestedDispatcher)
            .draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    dragFrom = if (pager.offset >= 0.5f) 1 else 0
                },
                onDragStopped = { velocity ->
                    val atStart = pager.offset <= 0.001f
                    val atEnd = pager.offset >= 0.999f
                    val passParent = (atStart && velocity > 0f) || (atEnd && velocity < 0f)
                    if (passParent) {
                        val available = Velocity(velocity, 0f)
                        val consumed = nestedDispatcher.dispatchPreFling(available)
                        nestedDispatcher.dispatchPostFling(consumed, available - consumed)
                        pager.settle(0f)
                    } else {
                        val dest = pager.settle(velocity)
                        val destPage = if (dest >= 0.5f) 1 else 0
                        val kind = if (destPage == 1) {
                            LibraryCollectionKind.Album
                        } else {
                            LibraryCollectionKind.Playlist
                        }
                        if (destPage != dragFrom) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSettled(kind)
                    }
                },
            ),
        content = {
            Box(Modifier.fillMaxWidth()) { playlist() }
            Box(Modifier.fillMaxWidth()) { album() }
        },
    ) { measurables, constraints ->
        val pageW = constraints.maxWidth.coerceAtLeast(0)
        val pageConstraints = constraints.copy(
            minWidth = pageW,
            maxWidth = pageW,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        )
        val playlistPlaceable = measurables[0].measure(pageConstraints)
        val albumPlaceable = measurables[1].measure(pageConstraints)
        val t = progress.coerceIn(0f, 1f)
        val height = (
            playlistPlaceable.height +
                (albumPlaceable.height - playlistPlaceable.height) * t
            ).roundToInt().coerceAtLeast(0)
        val x = (-t * pageW).roundToInt()
        layout(pageW, height) {
            playlistPlaceable.placeRelative(x, 0)
            albumPlaceable.placeRelative(x + pageW, 0)
        }
    }
}

@Composable
private fun CollectionKindSwitch(
    progress: Float,
    onSelect: (LibraryCollectionKind) -> Unit,
) {
    val kinds = LibraryCollectionKind.entries
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val t = progress.coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier
            .width(148.dp)
            .height(32.dp)
            .wallpaperItemChrome(RoundedCornerShape(10.dp), MainPalette.Placeholder),
    ) {
        val segW = maxWidth / kinds.size
        Box(
            Modifier
                .offset {
                    IntOffset(
                        with(density) { (segW * t + 3.dp).roundToPx() },
                        0,
                    )
                }
                .padding(vertical = 3.dp)
                .width(segW - 6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(MainPalette.Surface),
        )
        Row(Modifier.fillMaxSize()) {
            kinds.forEach { kind ->
                val active = if (kind == LibraryCollectionKind.Playlist) 1f - t else t
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val current = if (t < 0.5f) {
                                    LibraryCollectionKind.Playlist
                                } else {
                                    LibraryCollectionKind.Album
                                }
                                if (kind != current) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(kind)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (kind == LibraryCollectionKind.Playlist) "歌单" else "专辑",
                        style = TextStyle(
                            color = androidx.compose.ui.graphics.lerp(
                                MainPalette.Secondary,
                                MainPalette.Accent,
                                active,
                            ),
                            fontWeight = if (active >= 0.5f) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Medium
                            },
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectedAlbumPane(
    albums: List<CollectedAlbum>,
    total: Int,
    hasMore: Boolean,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    isGuest: Boolean,
    onOpen: (CollectedAlbum) -> Unit,
) {
    when {
        isGuest && albums.isEmpty() -> LibrarySectionEmpty("登录后查看收藏的专辑")
        loading && albums.isEmpty() -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MainPalette.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        error != null && albums.isEmpty() -> LibrarySectionEmpty(error)
        albums.isEmpty() -> LibrarySectionEmpty("还没有收藏的专辑")
        else -> {
            Column {
                if (total > 0) {
                    Text(
                        text = "$total 张",
                        style = TextStyle(
                            color = MainPalette.Hint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                val cols = 3
                val rows = (albums.size + cols - 1) / cols
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    repeat(rows) { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            repeat(cols) { col ->
                                val i = row * cols + col
                                if (i < albums.size) {
                                    CollectedAlbumTile(
                                        album = albums[i],
                                        onOpen = { onOpen(albums[i]) },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                if (loadingMore) {
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
                } else if (hasMore) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun CollectedAlbumTile(
    album: CollectedAlbum,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sub = buildList {
        album.yearLabel?.let { add(it) }
        if (album.size > 0) add("${album.size}首")
    }.joinToString(" · ")
    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpen,
        ),
    ) {
        UrlImage(
            url = album.coverUrl,
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            maxPx = UrlImageCache.THUMB_MAX_PX,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
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

@Composable
private fun PlaylistSectionColumn(
    title: String,
    playlists: List<PlaylistSummary>,
    emptyText: String,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onMorePlaylist: (PlaylistSummary) -> Unit,
    onCreate: (() -> Unit)? = null,
    showCount: Boolean = false,
) {
    LibrarySectionTitle(
        text = if (showCount) "$title · ${playlists.size}" else title,
        trailing = onCreate?.let { create ->
            {
                Box(
                    Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = create,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ZIcons.Add,
                        contentDescription = "新建歌单",
                        tint = MainPalette.Accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
    )
    if (playlists.isEmpty()) {
        LibrarySectionEmpty(emptyText)
    } else {
        playlists.forEach { pl ->
            Box(Modifier.padding(vertical = 6.dp)) {
                PlaylistRow(
                    pl = pl,
                    onClick = { onOpenPlaylist(pl) },
                    onMore = if (pl.isHeartPlaylist) null else ({ onMorePlaylist(pl) }),
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    pl: PlaylistSummary,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
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
                    text = "${pl.trackCount} 首 · 播放 ${formatPlayCount(pl.playCount)}",
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
        if (onMore != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMore,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.More,
                    contentDescription = "更多",
                    tint = MainPalette.Hint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

