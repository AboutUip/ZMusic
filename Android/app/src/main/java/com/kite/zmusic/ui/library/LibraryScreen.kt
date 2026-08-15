package com.kite.zmusic.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.UserProfileBrief
import com.kite.zmusic.data.VipKind
import com.kite.zmusic.ui.catalog.MainOverlay
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassPromptField
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.mainContentPadH
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val ProfileBlankBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFFFBFBFC), MainPalette.Page),
)
private val VipGold = Color(0xFFFFD789)
private val SvipPlate = Color(0xFF1A120C)
private val VipPlate = Color(0xFFEC4141)
internal val ProfileAvatarBadgeHang = 6.dp

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
) {
    val listState = rememberLazyListState()
    val hasPhoto = !customBgPath.isNullOrBlank() || !ui.profile?.backgroundUrl.isNullOrBlank()
    val fade = (1f - spaceProgress / 0.28f).coerceIn(0f, 1f)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MainPalette.Page)
            .graphicsLayer { alpha = fade },
        userScrollEnabled = !pullState.isOpen && !pullState.dragging && spaceProgress < 0.98f,
        contentPadding = PaddingValues(bottom = contentBottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "profile-banner") {
            ProfileLandscapeBanner(
                profile = ui.profile,
                loading = ui.loading && ui.profile == null,
                hasPhoto = hasPhoto,
                customBgPath = customBgPath,
                spaceProgress = spaceProgress,
                onEnterSpace = { pullState.open() },
                onAvatarPositioned = onAvatarPositioned,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
            )
        }
        if (ui.loading && ui.playlists.isEmpty()) {
            item { Box(Modifier.padding(horizontal = padH)) { LibraryLoadingBlock() } }
        }
        ui.error?.let { err ->
            item { Box(Modifier.padding(horizontal = padH)) { LibraryErrorText(err) } }
        }
        if (ui.isGuest) {
            item { Box(Modifier.padding(horizontal = padH)) { LibraryGuestBanner() } }
        }
        libraryPlaylistSections(
            playlists = ui.playlists,
            onOpenPlaylist = onOpenPlaylist,
            onMorePlaylist = onMorePlaylist,
            onCreatePlaylist = onCreatePlaylist,
            padH = padH,
        )
    }
}

@Composable
private fun ProfileLandscapeBanner(
    profile: UserProfileBrief?,
    loading: Boolean,
    hasPhoto: Boolean,
    customBgPath: String?,
    spaceProgress: Float,
    onEnterSpace: () -> Unit,
    onAvatarPositioned: (Offset, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clipToBounds()) {
        ProfileFixedBackground(
            backgroundUrl = profile?.backgroundUrl,
            localPath = customBgPath,
            modifier = Modifier.fillMaxSize(),
        )
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = p.nickname,
                            style = identityTitleStyle(hasPhoto).copy(fontSize = 20.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        p.level?.let { lv ->
                            ProfileLevelMark(level = lv, onPhoto = hasPhoto)
                        }
                    }
                    p.signature?.let { sig ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sig,
                            style = identityCaptionStyle(hasPhoto),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    listenMetaLine(p)?.let { meta ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = meta,
                            style = identityCaptionStyle(hasPhoto).copy(fontSize = 12.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
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
    val uid = ui.profile?.userId ?: 0L
    var customBgPath by remember(uid) { mutableStateOf(app.userSpaceBackgroundStore.pathFor(uid)) }

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

    LaunchedEffect(pullState) {
        snapshotFlow { pullState.progress }
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
                customBgPath = customBgPath,
                onAvatarPositioned = ::captureAvatar,
                onMorePlaylist = { morePlaylist = it },
                onCreatePlaylist = {
                    createDraft = ""
                    createOpen = true
                },
            )
        } else {
            LibraryHomePortrait(
                ui = ui,
                padH = padH,
                contentBottomInset = contentBottomInset,
                onOpenPlaylist = ::openPlaylist,
                pullState = pullState,
                spaceProgress = spaceProgress,
                customBgPath = customBgPath,
                onAvatarPositioned = ::captureAvatar,
                onMorePlaylist = { morePlaylist = it },
                onCreatePlaylist = {
                    createDraft = ""
                    createOpen = true
                },
            )
        }
        UserSpaceOverlay(
            progress = spaceProgress,
            profile = ui.profile,
            playlists = ui.playlists,
            likedTrackCount = ui.likedTrackCount,
            subcount = ui.subcount,
            customBgPath = customBgPath,
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
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = statusTop + maxOf(252.dp, screenH * 0.30f)
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
    val identityAlpha = (1f - scrollPx / (heroPx * 0.48f)).coerceIn(0f, 1f) *
        (1f - spaceProgress).coerceIn(0f, 1f)
    val hasPhoto = !customBgPath.isNullOrBlank() || !ui.profile?.backgroundUrl.isNullOrBlank()
    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 2
        }
    }
    SideEffect { pullState.atTop = atTop }

    val p = spaceProgress.coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MainPalette.Page),
    ) {
        val viewportH = maxHeight
        val sheetMinH = (viewportH - contentBottomInset).coerceAtLeast(0.dp)
        val photoH = lerp(heroHeight, viewportH, p)
        val sheetA = (1f - p / 0.28f).coerceIn(0f, 1f)
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
                        .height(heroHeight - overlap)
                        .zIndex(2f),
                ) {
                    if (sheetA > 0.01f) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(64.dp)
                                .graphicsLayer { alpha = sheetA }
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
                    ProfileIdentity(
                        profile = ui.profile,
                        loading = ui.loading && ui.profile == null,
                        onPhoto = hasPhoto,
                        fade = identityAlpha,
                        hideAvatar = spaceProgress > SpaceAvatarHandoffProgress,
                        showSpaceHint = atTop && p < 0.12f,
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
                            .padding(horizontal = padH)
                            .padding(bottom = 28.dp),
                    )
                }
            }
            item(key = "profile-sheet") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = sheetMinH)
                        .graphicsLayer { alpha = sheetA }
                        .background(MainPalette.Page),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = padH)
                            .padding(top = 8.dp, bottom = 8.dp),
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
                            onOpenPlaylist = onOpenPlaylist,
                            onMorePlaylist = onMorePlaylist,
                            onCreatePlaylist = onCreatePlaylist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFixedBackground(
    backgroundUrl: String?,
    localPath: String? = null,
    modifier: Modifier = Modifier,
) {
    val custom = !localPath.isNullOrBlank()
    val remote = !backgroundUrl.isNullOrBlank()
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .background(ProfileBlankBrush),
        )
        if (custom) {
            ProfileLocalImage(
                path = localPath,
                modifier = Modifier.matchParentSize(),
            )
        } else if (remote) {
            UrlImage(
                url = backgroundUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                showPlaceholder = false,
            )
        }
    }
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
    hideAvatar: Boolean = false,
    showSpaceHint: Boolean = false,
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
            if (onPhoto && profile != null) {
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
                Modifier.fillMaxWidth(),
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
                Row(
                    modifier = Modifier.widthIn(max = 280.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = p.nickname,
                        style = identityTitleStyle(onPhoto),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    p.level?.let { lv ->
                        ProfileLevelMark(level = lv, onPhoto = onPhoto)
                    }
                }
                p.signature?.let { sig ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sig,
                        style = identityCaptionStyle(onPhoto),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 280.dp),
                    )
                }
                ProfileListenProgress(profile = p, onPhoto = onPhoto)
            }
        }
        if (showSpaceHint) {
            Text(
                text = "下拉进入用户空间",
                style = identityCaptionStyle(onPhoto).copy(
                    fontSize = 11.sp,
                    color = if (onPhoto) Color.White.copy(alpha = 0.55f) else MainPalette.Hint,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
            )
        }
    }
}

private fun identityTitleStyle(onPhoto: Boolean) = TextStyle(
    color = if (onPhoto) Color.White else MainPalette.Ink,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    shadow = if (onPhoto) {
        Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 1f), blurRadius = 10f)
    } else {
        null
    },
)

private fun identityCaptionStyle(onPhoto: Boolean) = TextStyle(
    color = if (onPhoto) Color.White.copy(alpha = 0.88f) else MainPalette.Secondary,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    shadow = if (onPhoto) {
        Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(0f, 1f), blurRadius = 8f)
    } else {
        null
    },
)

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
                .background(Color(0xFFF0F0F2)),
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
                        color = MainPalette.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = placeholderSp,
                    ),
                )
            }
        }
        if (profile.vipKind != VipKind.None) {
            ProfileVipBadge(
                kind = profile.vipKind,
                iconUrl = profile.vipIconUrl,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(1.dp),
            )
        }
    }
}

@Composable
private fun ProfileVipBadge(
    kind: VipKind,
    iconUrl: String?,
    modifier: Modifier = Modifier,
) {
    val plate = if (kind == VipKind.Svip) SvipPlate else VipPlate
    val tint = if (kind == VipKind.Svip) VipGold else Color.White
    Box(
        modifier
            .size(22.dp)
            .border(1.5.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .background(plate),
        contentAlignment = Alignment.Center,
    ) {
        if (!iconUrl.isNullOrBlank()) {
            UrlImage(
                url = iconUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                showPlaceholder = false,
            )
        } else {
            Icon(
                imageVector = ZIcons.Vip,
                contentDescription = if (kind == VipKind.Svip) "SVIP" else "VIP",
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ProfileLevelMark(level: Int, onPhoto: Boolean) {
    val fg = if (onPhoto) Color.White else MainPalette.Accent
    Text(
        text = "Lv.$level",
        style = TextStyle(
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            shadow = if (onPhoto) {
                Shadow(color = Color.Black.copy(alpha = 0.35f), offset = Offset(0f, 1f), blurRadius = 6f)
            } else {
                null
            },
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(fg.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ProfileListenProgress(profile: UserProfileBrief, onPhoto: Boolean) {
    val meta = listenMetaLine(profile)
    val progress = profile.levelProgress?.takeIf { (profile.level ?: 0) < 10 }
    if (meta == null && progress == null) return
    Spacer(Modifier.height(12.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress != null) {
            val track = if (onPhoto) Color.White.copy(alpha = 0.22f) else MainPalette.Hairline
            val fill = if (onPhoto) Color.White.copy(alpha = 0.92f) else MainPalette.Accent
            Box(
                Modifier
                    .width(128.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(track),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(fill, RoundedCornerShape(50)),
                )
            }
            if (meta != null) {
                Spacer(Modifier.height(6.dp))
            }
        }
        if (meta != null) {
            Text(
                text = meta,
                style = identityCaptionStyle(onPhoto).copy(
                    fontSize = 12.sp,
                    color = if (onPhoto) {
                        Color.White.copy(alpha = 0.78f)
                    } else {
                        MainPalette.Secondary
                    },
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun listenMetaLine(profile: UserProfileBrief): String? {
    val parts = mutableListOf<String>()
    val level = profile.level
    if (level != null && level >= 10) {
        parts += "听歌满级"
        profile.listenSongs?.let { parts += "累计 ${formatPlayCount(it)}" }
        return parts.joinToString(" · ")
    }
    profile.listenSongs?.let { parts += "听歌 ${formatPlayCount(it)}" }
    val now = profile.nowPlayCount
    val next = profile.nextPlayCount
    if (now != null && next != null && next > now) {
        parts += "差 ${formatPlayCount(next - now)} 首"
    } else if (parts.isEmpty()) {
        profile.levelProgress?.let { progress ->
            parts += "听歌 ${(progress * 100f).toInt().coerceIn(0, 100)}%"
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
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
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onMorePlaylist: (PlaylistSummary) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    val liked = playlists.filter { it.isHeartPlaylist }
    val created = playlists.filter { it.isOwned && !it.isHeartPlaylist }
    val collected = playlists.filter { !it.isOwned && !it.isHeartPlaylist }

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
    PlaylistSectionColumn(
        title = "收藏的歌单",
        playlists = collected,
        emptyText = "还没有收藏的歌单",
        onOpenPlaylist = onOpenPlaylist,
        onMorePlaylist = onMorePlaylist,
        showCount = true,
    )
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

private fun LazyListScope.libraryPlaylistSections(
    playlists: List<PlaylistSummary>,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onMorePlaylist: (PlaylistSummary) -> Unit,
    onCreatePlaylist: () -> Unit,
    padH: Dp = 0.dp,
) {
    val liked = playlists.filter { it.isHeartPlaylist }
    val created = playlists.filter { it.isOwned && !it.isHeartPlaylist }
    val collected = playlists.filter { !it.isOwned && !it.isHeartPlaylist }

    playlistSection(
        title = "我喜欢的音乐",
        playlists = liked,
        emptyText = "还没有喜欢的音乐",
        onOpenPlaylist = onOpenPlaylist,
        onMorePlaylist = onMorePlaylist,
        padH = padH,
    )
    playlistSection(
        title = "创建的歌单",
        playlists = created,
        emptyText = "还没有创建的歌单",
        onOpenPlaylist = onOpenPlaylist,
        onMorePlaylist = onMorePlaylist,
        onCreate = onCreatePlaylist,
        showCount = true,
        padH = padH,
    )
    playlistSection(
        title = "收藏的歌单",
        playlists = collected,
        emptyText = "还没有收藏的歌单",
        onOpenPlaylist = onOpenPlaylist,
        onMorePlaylist = onMorePlaylist,
        showCount = true,
        padH = padH,
    )
}

private fun LazyListScope.playlistSection(
    title: String,
    playlists: List<PlaylistSummary>,
    emptyText: String,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onMorePlaylist: (PlaylistSummary) -> Unit,
    onCreate: (() -> Unit)? = null,
    showCount: Boolean = false,
    padH: Dp = 0.dp,
) {
    item(key = "h-$title") {
        Box(Modifier.padding(horizontal = padH)) {
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
        }
    }
    if (playlists.isEmpty()) {
        item(key = "e-$title") {
            Box(Modifier.padding(horizontal = padH)) {
                LibrarySectionEmpty(emptyText)
            }
        }
    } else {
        items(playlists, key = { "${title}-${it.id}-${it.coverUrl}" }) { pl ->
            Box(Modifier.padding(horizontal = padH, vertical = 6.dp)) {
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
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
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

