package com.kite.zmusic.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.MvPlayback
import com.kite.zmusic.playback.MvUiState
import com.kite.zmusic.playback.PlaybackViewModel
import com.kite.zmusic.ui.artist.resolveTrackArtists
import com.kite.zmusic.ui.catalog.CatalogOverlayHost
import com.kite.zmusic.ui.catalog.MainOverlay
import com.kite.zmusic.ui.catalog.PlaylistManageBar
import com.kite.zmusic.ui.catalog.PlaylistManageBridge
import com.kite.zmusic.ui.library.SpaceDarkBarsProgress
import com.kite.zmusic.ui.library.spaceChromeLeave
import com.kite.zmusic.ui.mv.MvPlayerScreen
import com.kite.zmusic.ui.player.MiniPlayerBar
import com.kite.zmusic.ui.player.NowPlayingScreen
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MainPagerDestinations = MainDestination.entries

private val DockSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 220f,
)

private const val CompactRangePx = 280f
private const val CompactMaxStep = 0.045f
private const val CompactActivatePx = 56f
/** Dock 松手切页：轻滑过约 1/4 格即切；滑得够远可一次到个人，不限一页。 */
private const val DockCommitFraction = 0.22f
private const val DockFlingTabsPerSec = 3.2f

/**
 * 浅色主壳：内容全幅滚动，底部悬浮 Dock + 迷你播放条叠在内容之上。
 */
@Composable
fun MainShell(
    sessionRepository: SessionRepository,
    playback: PlaybackViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playingTrackId by remember(playback) {
        playback.ui.map { it.currentTrack?.id ?: 0L }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(playback.ui.value.currentTrack?.id ?: 0L)
    val playingSourceId by remember(playback) {
        playback.ui.map { it.sourcePlaylistId ?: 0L }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(playback.ui.value.sourcePlaylistId ?: 0L)
    val playWhenReady by remember(playback) {
        playback.ui.map { it.playWhenReady }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(playback.ui.value.playWhenReady)
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    var overlayStack by remember { mutableStateOf<List<MainOverlay>>(emptyList()) }
    val playlistManage = remember { PlaylistManageBridge() }
    var userSpaceProgress by remember { mutableFloatStateOf(0f) }
    val overlay = overlayStack.lastOrNull()
    fun pushOverlay(next: MainOverlay) {
        if (next is MainOverlay.Mv) {
            val cur = overlayStack.lastOrNull() as? MainOverlay.Mv
            if (cur?.id == next.id) return
            overlayStack = overlayStack.filter { it !is MainOverlay.Mv } + next
            return
        }
        if (overlayStack.lastOrNull()?.stackKey() == next.stackKey()) return
        overlayStack = overlayStack + next
    }
    fun popOverlay() {
        overlayStack = overlayStack.dropLast(1)
    }

    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val compactAnim = remember { Animatable(0f) }
    val compactDrag = remember { mutableFloatStateOf(0f) }
    val compactDragging = remember { mutableStateOf(false) }
    val compactSlop = remember { mutableFloatStateOf(0f) }
    val dockHiddenRef = remember { mutableStateOf(false) }
    val dockCompactHold = remember { mutableFloatStateOf(0f) }
    val dockInsetHold = remember { mutableStateOf(0.dp) }
    val dockRestBottomHold = remember { mutableStateOf(0.dp) }
    val shellHpx = remember { mutableIntStateOf(0) }
    val shellTopPx = remember { mutableFloatStateOf(0f) }
    /** 迷你播放条顶边距屏幕底的距离；进出播放页的滑动原点，不是 dock 下边距。 */
    val playerHomePx = remember { mutableIntStateOf(0) }
    var playerLayerVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val liveCompact =
        if (compactDragging.value) compactDrag.floatValue else compactAnim.value
    val compactProgress =
        if (showFullPlayer) dockCompactHold.floatValue else liveCompact

    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (dockHiddenRef.value) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = consumed.y
                if (abs(dy) < 1f) return Offset.Zero
                if (abs(consumed.x) > abs(dy) * 1.25f) return Offset.Zero
                if (!compactDragging.value) {
                    compactSlop.floatValue += -dy
                    val pullingIn = compactAnim.value > 0.02f && dy > 0f
                    if (!pullingIn && compactSlop.floatValue < CompactActivatePx) {
                        return Offset.Zero
                    }
                    compactDragging.value = true
                    compactDrag.floatValue = compactAnim.value
                    compactSlop.floatValue = 0f
                }
                val step = (-dy / CompactRangePx).coerceIn(-CompactMaxStep, CompactMaxStep)
                compactDrag.floatValue =
                    (compactDrag.floatValue + step).coerceIn(0f, 1f)
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dockHiddenRef.value) return Velocity.Zero
                compactSlop.floatValue = 0f
                if (!compactDragging.value) return Velocity.Zero
                val current = compactDrag.floatValue
                val target = when {
                    current >= 0.62f -> 1f
                    current <= 0.38f -> 0f
                    else -> if (current >= 0.5f) 1f else 0f
                }
                compactDragging.value = false
                compactAnim.snapTo(current)
                compactAnim.animateTo(target, DockSpring)
                return Velocity.Zero
            }
        }
    }

    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val mvUi by app.mvPlayback.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val navBarLive = remember { mutableStateOf(0.dp) }

    fun rememberDockRestBottom(navBottom: Dp) {
        dockRestBottomHold.value = navBottom + FloatingChromeBottom
    }

    fun formulaPlayerHomePx(): Int {
        val bottomGap = navBarLive.value + FloatingChromeBottom
        val dockPart = if (landscape || overlay != null) {
            0.dp
        } else {
            lerp(FloatingDockHeight, FloatingDockCompactHeight, liveCompact) +
                FloatingChromeGap
        }
        return with(density) {
            (bottomGap + dockPart + MiniPlayerStackHeight).roundToPx()
        }
    }

    fun rememberPlayerHomeFromBottom(fromBottomPx: Int) {
        if (fromBottomPx > 0) playerHomePx.intValue = fromBottomPx
    }

    fun captureDockForPlayer() {
        dockCompactHold.floatValue = liveCompact
        compactDragging.value = false
        rememberDockRestBottom(navBarLive.value)
        if (playerHomePx.intValue <= 0) {
            rememberPlayerHomeFromBottom(formulaPlayerHomePx())
        }
    }

    fun openFullPlayer() {
        captureDockForPlayer()
        showFullPlayer = true
    }

    fun closeFullPlayer() {
        compactDragging.value = false
        scope.launch {
            compactAnim.snapTo(dockCompactHold.floatValue)
        }
        showFullPlayer = false
    }
    var pendingPlay by remember { mutableStateOf<PendingPlayRequest?>(null) }
    var pendingFm by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingPlay
        pendingPlay = null
        val startFm = pendingFm
        pendingFm = false
        if (pending != null) {
            playback.playQueue(pending.tracks, pending.startIndex, pending.playlistId, pending.playlistTitle)
            openFullPlayer()
            if (!granted) {
                context.showIslandNotice("未开启通知时，系统可能在息屏后限制后台播放")
            }
        } else if (startFm) {
            playback.startPersonalFm { openFullPlayer() }
            if (!granted) {
                context.showIslandNotice("未开启通知时，系统可能在息屏后限制后台播放")
            }
        }
    }

    fun playTracksWithNotificationPermission(
        list: List<TrackRow>,
        idx: Int,
        plId: Long?,
        plTitle: String?,
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingPlay = PendingPlayRequest(list, idx, plId, plTitle)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        playback.playQueue(list, idx, plId, plTitle)
        openFullPlayer()
    }

    fun startFmWithPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingFm = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        playback.startPersonalFm { openFullPlayer() }
    }

    fun hint(msg: String) {
        context.showIslandNotice(msg)
    }

    LaunchedEffect(playingTrackId, mvUi.active) {
        if (mvUi.active) {
            showFullPlayer = false
        } else if (playingTrackId <= 0L) {
            closeFullPlayer()
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { MainPagerDestinations.size },
    )
    var landscapePage by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(landscape) {
        if (landscape) {
            landscapePage = pagerState.currentPage
        } else if (pagerState.currentPage != landscapePage) {
            pagerState.scrollToPage(landscapePage)
        }
    }

    fun expandDock() {
        scope.launch {
            val current =
                if (compactDragging.value) compactDrag.floatValue else compactAnim.value
            compactDragging.value = false
            compactAnim.snapTo(current)
            compactAnim.animateTo(0f, DockSpring)
        }
    }

    fun goTo(dest: MainDestination) {
        val target = MainPagerDestinations.indexOf(dest)
        if (target < 0) return
        if (landscape) {
            landscapePage = target
            return
        }
        expandDock()
        if (target != pagerState.targetPage) {
            scope.launch {
                pagerState.animateScrollToPage(
                    target,
                    animationSpec = spring(
                        dampingRatio = 0.92f,
                        stiffness = 520f,
                    ),
                )
            }
        }
    }

    fun goToFromRail(dest: MainDestination) {
        if (overlayStack.isNotEmpty()) overlayStack = emptyList()
        goTo(dest)
    }

    fun dragDockByTabs(deltaTabs: Float) {
        if (deltaTabs == 0f) return
        val info = pagerState.layoutInfo
        val stride = (info.pageSize + info.pageSpacing).toFloat()
        if (stride <= 0f) return
        pagerState.dispatchRawDelta(deltaTabs * stride)
    }

    fun settleDockPager(velocityTabsPerSec: Float, startPage: Int) {
        expandDock()
        val last = MainPagerDestinations.lastIndex
        val origin = startPage.coerceIn(0, last)
        val pos = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, last.toFloat())
        val travel = pos - origin
        val direction = when {
            abs(travel) > 0.001f -> sign(travel).toInt()
            abs(velocityTabsPerSec) > 0.001f -> sign(velocityTabsPerSec).toInt()
            else -> 0
        }
        val distanceSteps = if (abs(travel) < DockCommitFraction) {
            0
        } else {
            floor(abs(travel) + (1f - DockCommitFraction)).toInt()
        }
        val flingStep =
            if (distanceSteps == 0 && abs(velocityTabsPerSec) >= DockFlingTabsPerSec) 1 else 0
        val steps = (distanceSteps + flingStep).coerceAtLeast(0)
        val target = if (direction == 0 || steps == 0) {
            origin
        } else {
            (origin + direction * steps).coerceIn(0, last)
        }
        if (target == pagerState.currentPage &&
            abs(pagerState.currentPageOffsetFraction) < 0.002f
        ) {
            return
        }
        scope.launch {
            pagerState.animateScrollToPage(
                target,
                animationSpec = spring(
                    dampingRatio = 0.92f,
                    stiffness = 520f,
                ),
            )
        }
    }

    val backdrop = rememberLayerBackdrop()
    val overlayOpen = overlay != null
    val spaceOpen = userSpaceProgress > 0.18f
    val holdChrome = showFullPlayer || playerLayerVisible
    val showDock = !overlayOpen || overlay is MainOverlay.Mv
    val showManage = playlistManage.active && overlay is MainOverlay.Playlist && !showFullPlayer
    val showMini = (mvUi.active || playingTrackId > 0L) && !showManage
    dockHiddenRef.value = landscape || !showDock
    LaunchedEffect(overlay) {
        if (overlay !is MainOverlay.Playlist) playlistManage.exit()
        if (overlay is MainOverlay.Mv && Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val dockReveal by animateFloatAsState(
        targetValue = if (showDock) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "dockReveal",
    )
    val navBarDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val freezeChromePad = holdChrome || compactDragging.value
    val chromeBottomGap =
        if ((holdChrome || compactDragging.value) && dockRestBottomHold.value > 0.dp) {
            dockRestBottomHold.value
        } else {
            navBarDp + FloatingChromeBottom
        }
    val dockExpandedH = lerp(FloatingDockHeight, FloatingDockCompactHeight, compactProgress)
    val dockH = dockExpandedH * dockReveal
    val accessoryH = if (showMini || showManage) MiniPlayerStackHeight else 0.dp
    val chromeGap = if (showMini || showManage) FloatingChromeGap * dockReveal else 0.dp
    val liveChromeInset =
        if (landscape) {
            chromeBottomGap + accessoryH + chromeGap + 8.dp
        } else {
            chromeBottomGap +
                dockH +
                accessoryH +
                chromeGap +
                8.dp
        }
    SideEffect {
        navBarLive.value = navBarDp
        if (!holdChrome) {
            rememberDockRestBottom(navBarDp)
            if (!showMini) {
                rememberPlayerHomeFromBottom(formulaPlayerHomePx())
            }
        }
        if (!freezeChromePad) {
            dockCompactHold.floatValue = liveCompact
            dockInsetHold.value = liveChromeInset
        }
    }
    val chromeInset =
        if (freezeChromePad && dockInsetHold.value > 0.dp) dockInsetHold.value else liveChromeInset

    val sectionContent: @Composable (MainDestination) -> Unit = { dest ->
        MainSectionContent(
            destination = dest,
            isLandscape = landscape,
            sessionRepository = sessionRepository,
            onPlayTracks = { list, idx, plId, plTitle ->
                playTracksWithNotificationPermission(list, idx, plId, plTitle)
            },
            onOpenOverlay = { pushOverlay(it) },
            onOpenProfile = { goTo(MainDestination.Profile) },
            onStartFm = { startFmWithPermission() },
            onPlaySong = { songId ->
                scope.launch {
                    val cookie = sessionRepository.session.value?.cookie.orEmpty()
                    val track = withContext(Dispatchers.IO) {
                        runCatching {
                            val json = NcmUserClient().songDetail(listOf(songId), cookie)
                            NcmLibraryParse.tracksFromSongDetail(json).firstOrNull()
                        }.getOrNull()
                    }
                    if (track != null) {
                        playTracksWithNotificationPermission(listOf(track), 0, null, track.name)
                    } else {
                        hint("暂时无法打开这首歌")
                    }
                }
            },
            onHint = ::hint,
            contentBottomInset = chromeInset,
            onUserSpaceProgress = { userSpaceProgress = it },
            modifier = Modifier.fillMaxSize(),
        )
    }

    when {
        overlay is MainOverlay.Mv && landscape -> MainDarkSystemBars()
        overlay is MainOverlay.Mv -> MainMvPortraitSystemBars()
        showFullPlayer || userSpaceProgress > SpaceDarkBarsProgress -> MainDarkSystemBars()
        else -> MainLightSystemBars()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MainPalette.Page)
            .onGloballyPositioned {
                shellHpx.intValue = it.size.height
                shellTopPx.floatValue = it.positionInWindow().y
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            if (landscape) {
                val spaceT = spaceChromeLeave(userSpaceProgress)
                val railLayoutW = LandscapeRailWidth * (1f - spaceT)
                if (railLayoutW > 0.5.dp) {
                    val slidePx = with(density) { (LandscapeRailWidth - railLayoutW).toPx() }
                    Box(
                        Modifier
                            .width(railLayoutW)
                            .fillMaxHeight()
                            .clipToBounds(),
                    ) {
                        LandscapeNavRail(
                            selected = MainPagerDestinations.getOrElse(landscapePage) {
                                MainDestination.Home
                            },
                            settingsSelected = overlay is MainOverlay.Settings,
                            onDestination = ::goToFromRail,
                            onOpenSettings = {
                                if (overlay is MainOverlay.Settings) {
                                    popOverlay()
                                } else {
                                    pushOverlay(MainOverlay.Settings)
                                }
                            },
                            modifier = Modifier.graphicsLayer {
                                translationX = -slidePx
                                alpha = (1f - spaceT).coerceIn(0f, 1f)
                            },
                        )
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            if (landscape) {
                LandscapeCoverPages(
                    currentIndex = landscapePage,
                    clipLayer = userSpaceProgress < 0.02f,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MainPalette.Page),
                ) { index ->
                    sectionContent(MainPagerDestinations[index])
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MainPalette.Page)
                        .nestedScroll(nestedScroll),
                    beyondViewportPageCount = 2,
                    userScrollEnabled = !showFullPlayer && !overlayOpen && !spaceOpen,
                ) { page ->
                    sectionContent(MainPagerDestinations[page])
                }
            }

            CatalogOverlayHost(
                overlayStack = overlayStack,
                searchInStack = overlayStack.any { it is MainOverlay.Search },
                sessionRepository = sessionRepository,
                contentBottomInset = chromeInset,
                onBack = { popOverlay() },
                onPlayTracks = { list, idx, plId, plTitle ->
                    playTracksWithNotificationPermission(list, idx, plId, plTitle)
                },
                onOpenPlaylist = { id, title, cover ->
                    pushOverlay(MainOverlay.Playlist(id, title, cover))
                },
                onPushOverlay = { pushOverlay(it) },
                onHint = ::hint,
                onLogout = onLogout,
                playingTrackId = playingTrackId,
                playingSourceId = playingSourceId,
                isPlaying = playWhenReady,
                manageBridge = playlistManage,
                includeMv = false,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = if (landscape) 20.dp else FloatingChromeSide,
                    end = if (landscape) 20.dp else FloatingChromeSide,
                    bottom = chromeBottomGap,
                )
                .zIndex(40f)
                .then(
                    if (overlay is MainOverlay.Mv) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .graphicsLayer {
                    val leave = spaceChromeLeave(userSpaceProgress)
                    translationY = leave * 220f
                    alpha = (1f - leave).coerceIn(0f, 1f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showMini || showManage) {
                Box(
                    Modifier
                        .then(
                            if (landscape) Modifier.fillMaxWidth()
                            else Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                        )
                        .height(MiniPlayerStackHeight),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showMini,
                        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)),
                        exit = fadeOut(tween(180)),
                    ) {
                        MiniPlayerSlot(
                            playback = playback,
                            mv = mvUi,
                            mvPlayback = app.mvPlayback,
                            backdrop = backdrop,
                            onOpenFull = {
                                if (mvUi.active) {
                                    if (overlay !is MainOverlay.Mv) {
                                        pushOverlay(
                                            MainOverlay.Mv(
                                                id = mvUi.mvId,
                                                title = mvUi.title,
                                                coverUrl = mvUi.coverUrl,
                                                artist = mvUi.artistLine,
                                            ),
                                        )
                                    }
                                } else {
                                    openFullPlayer()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    if (holdChrome) return@onGloballyPositioned
                                    val topInShell =
                                        coords.positionInWindow().y - shellTopPx.floatValue
                                    rememberPlayerHomeFromBottom(
                                        (shellHpx.intValue - topInShell).roundToInt(),
                                    )
                                },
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showManage,
                        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInVertically(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                                initialOffsetY = { it / 3 },
                            ),
                        exit = fadeOut(tween(180)) +
                            slideOutVertically(
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                targetOffsetY = { it / 3 },
                            ),
                    ) {
                        PlaylistManageBar(
                            selectedCount = playlistManage.selectedCount,
                            canRemove = playlistManage.canRemove,
                            busy = playlistManage.busy,
                            onRemove = {
                                if (playlistManage.selectedCount <= 0) {
                                    hint("请先选择歌曲")
                                } else {
                                    playlistManage.onRemove()
                                }
                            },
                            onDownload = {
                                if (playlistManage.selectedCount <= 0) {
                                    hint("请先选择歌曲")
                                } else {
                                    playlistManage.onDownload()
                                }
                            },
                            onCancel = { playlistManage.onCancel() },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (!landscape) {
                if ((showMini || showManage) && dockReveal > 0.001f) {
                    Spacer(Modifier.height(FloatingChromeGap * dockReveal))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(dockH)
                        .graphicsLayer {
                            alpha = dockReveal.coerceIn(0f, 1f)
                            // 展开时不要裁剪：玻璃默认阴影/lens 溢出被矩形切开后，胶囊四角会留下水平黑线。
                            clip = dockReveal < 0.999f
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (dockReveal > 0.001f) {
                        FloatingTabDock(
                            pagerState = pagerState,
                            onDestination = { dest -> goTo(dest) },
                            onDragByTabs = ::dragDockByTabs,
                            onDragSettled = ::settleDockPager,
                            compactProgress = compactProgress,
                            landscape = landscape,
                            backdrop = backdrop,
                        )
                    }
                }
            }
            }
        }
        }

        val liveMv = overlay as? MainOverlay.Mv
        var heldMv by remember { mutableStateOf<MainOverlay.Mv?>(null) }
        if (liveMv != null) heldMv = liveMv
        val renderMv = liveMv ?: heldMv
        val mvVisible = remember { MutableTransitionState(false) }
        mvVisible.targetState = liveMv != null
        LaunchedEffect(liveMv, mvVisible.currentState, mvVisible.targetState) {
            if (liveMv == null && !mvVisible.currentState && !mvVisible.targetState) {
                heldMv = null
            }
        }
        if (renderMv != null) {
            androidx.compose.animation.AnimatedVisibility(
                visibleState = mvVisible,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(50f),
                enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        animationSpec = tween(420, easing = FastOutSlowInEasing),
                        initialOffsetY = { it },
                    ),
                exit = fadeOut(tween(220)) +
                    slideOutVertically(
                        animationSpec = tween(340, easing = FastOutSlowInEasing),
                        targetOffsetY = { it },
                    ),
            ) {
                MvPlayerScreen(
                    overlay = renderMv,
                    playback = app.mvPlayback,
                    ui = mvUi,
                    onBack = { popOverlay() },
                    onOpenMv = { pushOverlay(it) },
                    onOpenArtist = { artist ->
                        if (artist.id > 0L) {
                            pushOverlay(MainOverlay.Artist(artist.id, artist.name, artist.avatarUrl))
                        } else {
                            hint("暂时无法打开这位歌手")
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedVisibility(
            visible = showFullPlayer && playingTrackId > 0L && !mvUi.active,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(130f),
            enter = fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    animationSpec = tween(380, easing = FastOutSlowInEasing),
                    initialOffsetY = { full ->
                        (full - playerHomePx.intValue).coerceAtLeast(0)
                    },
                ),
            exit = fadeOut(tween(260)) +
                slideOutVertically(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    targetOffsetY = { full ->
                        (full - playerHomePx.intValue).coerceAtLeast(0)
                    },
                ),
        ) {
            DisposableEffect(Unit) {
                playerLayerVisible = true
                onDispose { playerLayerVisible = false }
            }
            FullPlayerSlot(
                playback = playback,
                landscape = landscape,
                onDismiss = { closeFullPlayer() },
                onOpenSourcePlaylist = { id, title, cover ->
                    pushOverlay(MainOverlay.Playlist(id, title, cover))
                    closeFullPlayer()
                },
                onOpenArtist = { id, name, cover ->
                    pushOverlay(MainOverlay.Artist(id, name, cover))
                    closeFullPlayer()
                },
            )
        }
    }
}

private data class PendingPlayRequest(
    val tracks: List<TrackRow>,
    val startIndex: Int,
    val playlistId: Long?,
    val playlistTitle: String?,
)

@Composable
private fun MiniPlayerSlot(
    playback: PlaybackViewModel,
    mv: MvUiState,
    mvPlayback: MvPlayback,
    backdrop: Backdrop,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (mv.active) {
        MiniPlayerBar(
            track = TrackRow(
                id = -mv.mvId,
                name = mv.title.ifBlank { "MV" },
                artists = mv.artistLine,
                album = null,
                durationMs = mv.durationMs,
                coverUrl = mv.coverUrl,
            ),
            isPlaying = mv.playWhenReady,
            buffering = mv.buffering || mv.loading,
            positionMs = mv.positionMs,
            durationMs = mv.durationMs,
            loadPending = mv.loading,
            onOpenFull = onOpenFull,
            onTogglePlay = { mvPlayback.togglePlayPause() },
            backdrop = backdrop,
            modifier = modifier,
        )
        return
    }
    val playbackState by playback.ui.collectAsStateWithLifecycle()
    val tr = playbackState.currentTrack ?: return
    MiniPlayerBar(
        track = tr,
        isPlaying = playbackState.playWhenReady,
        buffering = playbackState.loadPending,
        positionMs = playbackState.positionMs,
        durationMs = playbackState.durationMs,
        loadPending = playbackState.loadPending,
        onOpenFull = onOpenFull,
        onTogglePlay = { playback.togglePlayPause() },
        backdrop = backdrop,
        modifier = modifier,
    )
}

@Composable
private fun FullPlayerSlot(
    playback: PlaybackViewModel,
    landscape: Boolean,
    onDismiss: () -> Unit,
    onOpenSourcePlaylist: (Long, String, String?) -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
) {
    val st by playback.ui.collectAsStateWithLifecycle()
    val spectrum by playback.spectrum.collectAsStateWithLifecycle()
    if (st.currentTrack == null) return
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onDismiss)
    NowPlayingScreen(
        state = st,
        isLandscape = landscape,
        onDismiss = onDismiss,
        onTogglePlay = { playback.togglePlayPause() },
        onSeek = playback::seekTo,
        onSkipNext = { playback.skipNext() },
        onSkipPrev = { playback.skipPrevious() },
        onCyclePlaybackMode = playback::cyclePlaybackMode,
        onPlayQueueIndex = playback::playIndex,
        onHoldAutoAdvanceChange = playback::setHoldAutoAdvance,
        spectrum = spectrum,
        modifier = Modifier.fillMaxSize(),
        landscapeStartInset = 0.dp,
        onOpenSourcePlaylist = st.sourcePlaylistId?.let { plId ->
            {
                val title = st.sourcePlaylistTitle ?: "歌单"
                onOpenSourcePlaylist(plId, title, st.currentTrack?.coverUrl)
            }
        },
        onOpenArtist = {
            val track = st.currentTrack ?: return@NowPlayingScreen
            scope.launch {
                val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
                val found = resolveTrackArtists(track, cookie)
                val a = found.firstOrNull()
                if (a == null) {
                    app.islandNoticeCenter.show("暂时无法打开这位歌手", track.coverUrl)
                } else {
                    onOpenArtist(a.id, a.name, track.coverUrl)
                    onDismiss()
                }
            }
        },
    )
}

@Composable
private fun LandscapeCoverPages(
    currentIndex: Int,
    modifier: Modifier = Modifier,
    clipLayer: Boolean = true,
    page: @Composable (Int) -> Unit,
) {
    Box(modifier) {
        MainPagerDestinations.indices.forEach { index ->
            val visible = remember { MutableTransitionState(index == currentIndex) }
            visible.targetState = index == currentIndex
            androidx.compose.animation.AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (index == currentIndex) 1f else 0f),
                enter = LandscapeCoverEnter,
                exit = LandscapeCoverExit,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (clipLayer) {
                                Modifier.graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                    clip = true
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    page(index)
                }
            }
        }
    }
}
