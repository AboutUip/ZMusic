package com.kite.zmusic.ui.main

import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.kite.zmusic.data.ChromeGlassMode
import com.kite.zmusic.data.ChromeWallpaperState
import com.kite.zmusic.data.NetworkCommand
import com.kite.zmusic.data.NetworkPhase
import com.kite.zmusic.data.NetworkPhaseLogic
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.MvPlayback
import com.kite.zmusic.playback.PlaybackViewModel
import com.kite.zmusic.ui.artist.resolveTrackArtists
import com.kite.zmusic.ui.chrome.ChromeWallpaperLayer
import com.kite.zmusic.ui.chrome.LocalChromeWallpaperFrame
import com.kite.zmusic.ui.chrome.LocalChromeWallpaperPainted
import com.kite.zmusic.ui.chrome.LocalWallpaperViewport
import com.kite.zmusic.ui.chrome.WallpaperViewport
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.chrome.chromeWallpaperSurface
import com.kite.zmusic.ui.chrome.pagerPageKeepsOwnWallpaperLayer
import com.kite.zmusic.ui.chrome.pagerPageShowsOwnWallpaper
import com.kite.zmusic.ui.chrome.preloadWallpaperBitmap
import com.kite.zmusic.ui.chrome.wallpaperSurface
import com.kite.zmusic.ui.common.PredictiveBackAxis
import com.kite.zmusic.ui.common.predictiveBackLayer
import com.kite.zmusic.ui.common.rememberPredictiveBackUi
import com.kite.zmusic.ui.main.CatalogOverlayHost
import com.kite.zmusic.ui.main.MainOverlay
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val net by app.networkMode.state.collectAsStateWithLifecycle()
    fun pushOverlay(next: MainOverlay) {
        val phase = net.phase
        val online = net.online
        if (!online && next is MainOverlay.Search) {
            context.showIslandNotice("搜索需要网络")
            return
        }
        if (phase == NetworkPhase.Offline &&
            next !is MainOverlay.CachedSongs &&
            next !is MainOverlay.Settings
        ) {
            return
        }
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
    val compactSettling = remember { mutableStateOf(false) }
    val mvActive by remember(app.mvPlayback) {
        app.mvPlayback.ui.map { it.active }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(app.mvPlayback.ui.value.active)
    val scope = rememberCoroutineScope()
    // 布局用的压缩进度：滚动跟手时不读每帧 drag，避免掀开 Pager 里的首页。
    val compactProgress =
        if (showFullPlayer || compactDragging.value || compactSettling.value) {
            dockCompactHold.floatValue
        } else {
            compactAnim.value
        }

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
                compactSettling.value = true
                compactAnim.snapTo(current)
                compactAnim.animateTo(target, DockSpring)
                dockCompactHold.floatValue = target
                compactSettling.value = false
                return Velocity.Zero
            }
        }
    }

    val navBarLive = remember { mutableStateOf(0.dp) }

    fun rememberDockRestBottom(navBottom: Dp) {
        dockRestBottomHold.value = navBottom + FloatingChromeBottom
    }

    fun formulaPlayerHomePx(): Int {
        val bottomGap = navBarLive.value + FloatingChromeBottom
        val dockPart = if (landscape || overlay != null) {
            0.dp
        } else {
            lerp(FloatingDockHeight, FloatingDockCompactHeight, compactProgress) +
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
        dockCompactHold.floatValue =
            if (compactDragging.value) compactDrag.floatValue else compactAnim.value
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

    val pendingOpenPlayer by playback.pendingOpenPlayer.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenPlayer, playingTrackId, mvActive) {
        if (!pendingOpenPlayer) return@LaunchedEffect
        if (mvActive) {
            playback.consumeOpenPlayerRequest()
            return@LaunchedEffect
        }
        if (playingTrackId > 0L) {
            playback.consumeOpenPlayerRequest()
            openFullPlayer()
        }
    }
    var pendingPlay by remember { mutableStateOf<PendingPlayRequest?>(null) }
    var pendingFm by remember { mutableStateOf(false) }
    var pendingIntelligenceFromContext by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingPlay
        pendingPlay = null
        val startFm = pendingFm
        pendingFm = false
        val intelCtx = pendingIntelligenceFromContext
        pendingIntelligenceFromContext = false
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
        } else if (intelCtx) {
            playback.startIntelligenceFromContext { openFullPlayer() }
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
        if (!net.online) {
            context.showIslandNotice("当前无网络")
            return
        }
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

    fun startIntelligenceFromContextWithPermission() {
        if (!net.online) {
            context.showIslandNotice("当前无网络")
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingIntelligenceFromContext = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        playback.startIntelligenceFromContext { openFullPlayer() }
    }

    fun hint(msg: String) {
        context.showIslandNotice(msg)
    }

    LaunchedEffect(playingTrackId, mvActive) {
        if (mvActive) {
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

    val landscapeNow = rememberUpdatedState(landscape)
    LaunchedEffect(Unit) {
        launch {
            delay(520)
            var last: NetworkPhase? = null
            app.networkMode.state.collect { ui ->
                val to = ui.phase
                if (to == last) return@collect
                val from = last
                last = to
                NetworkPhaseLogic.islandNotice(from, to)?.let { context.showIslandNotice(it) }
            }
        }
        app.networkMode.commands.collect { cmd ->
            when (cmd) {
                NetworkCommand.ForceHome -> {
                    overlayStack = emptyList()
                    app.mvPlayback.stop()
                    if (landscapeNow.value) {
                        landscapePage = 0
                    } else {
                        goTo(MainDestination.Home)
                    }
                }
            }
        }
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
    val dockHaze = remember { HazeState() }
    val itemHaze = remember { HazeState() }
    var wallpaperViewport by remember { mutableStateOf<WallpaperViewport?>(null) }
    val overlayOpen = overlay != null
    val spaceOpen = userSpaceProgress > 0.18f
    val holdChrome = showFullPlayer || playerLayerVisible
    val showDock = !overlayOpen || overlay is MainOverlay.Mv
    val showManage = playlistManage.active &&
        (overlay is MainOverlay.Playlist || overlay is MainOverlay.CachedSongs) &&
        !showFullPlayer
    val showMini = (mvActive || playingTrackId > 0L) && !showManage
    dockHiddenRef.value = landscape || !showDock
    LaunchedEffect(overlay) {
        if (overlay !is MainOverlay.Playlist && overlay !is MainOverlay.CachedSongs) {
            playlistManage.exit()
        }
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
    val currentDest = MainPagerDestinations.getOrElse(
        if (landscape) landscapePage else pagerState.currentPage,
    ) { MainDestination.Home }
    val wallpaper by app.chromeWallpaperStore.state.collectAsStateWithLifecycle()
    val wallpaperFrame = wallpaper.frame(
        chromeWallpaperSurface(
            overlay = overlay,
            destination = currentDest,
        ),
        landscape,
    )
    LaunchedEffect(
        currentDest,
        overlay,
        wallpaperFrame?.imagePath,
        wallpaperFrame?.offsetX,
        wallpaperFrame?.scale,
    ) {
        Log.i(
            "ZMusicWallpaper",
            "shell dest=$currentDest overlay=${overlay?.javaClass?.simpleName} " +
                "painted=${wallpaperFrame != null} path=${wallpaperFrame?.imagePath.orEmpty()} " +
                "ox=${wallpaperFrame?.offsetX} scale=${wallpaperFrame?.scale}",
        )
    }
    LaunchedEffect(wallpaper, landscape) {
        MainPagerDestinations.forEach { dest ->
            val path = wallpaper.frame(dest.wallpaperSurface(), landscape)?.imagePath
            if (!path.isNullOrBlank()) preloadWallpaperBitmap(path)
        }
    }
    val navBarDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val freezeChromePad = holdChrome || compactDragging.value || compactSettling.value
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
            dockCompactHold.floatValue = compactProgress
            dockInsetHold.value = liveChromeInset
        }
    }
    val chromeInset =
        if (freezeChromePad && dockInsetHold.value > 0.dp) dockInsetHold.value else liveChromeInset

    val glassMode = LocalChromeGlassStyle.current.mode
    val itemChrome = if (wallpaperFrame != null) wallpaper.itemChrome else ChromeGlassMode.Solid
    val needLiquid = glassMode == ChromeGlassMode.Liquid || itemChrome == ChromeGlassMode.Liquid
    val needItemFrosted = wallpaperFrame != null && itemChrome == ChromeGlassMode.Frosted
    val needDockFrosted = glassMode == ChromeGlassMode.Frosted

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
            onStartIntelligence = { startIntelligenceFromContextWithPermission() },
            onPlaySong = { songId ->
                scope.launch {
                    val cookie = sessionRepository.session.value?.cookie.orEmpty()
                    val track = withContext(Dispatchers.IO) {
                        runCatching {
                            app.songRepository.trackById(songId, cookie)
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
                wallpaperViewport = WallpaperViewport(
                    width = it.size.width.toFloat(),
                    height = it.size.height.toFloat(),
                    originInWindow = it.positionInWindow(),
                )
            },
    ) {
        CompositionLocalProvider(
            LocalWallpaperViewport provides wallpaperViewport,
            LocalChromeWallpaperPainted provides (wallpaperFrame != null),
            LocalChromeWallpaperFrame provides wallpaperFrame,
            LocalChromeHaze provides itemHaze,
            LocalChromeBackdrop provides backdrop,
            LocalWallpaperItemChrome provides if (wallpaperFrame != null) wallpaper.itemChrome else null,
        ) {
        Box(
            Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (needLiquid && wallpaperFrame != null) {
                            Modifier.layerBackdrop(backdrop)
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (needItemFrosted || needDockFrosted) {
                            Modifier.hazeSource(state = itemHaze, zIndex = 0f)
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (needDockFrosted && wallpaperFrame != null) {
                            Modifier.hazeSource(state = dockHaze, zIndex = 0f)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (wallpaperFrame != null) {
                    ChromeWallpaperLayer(frame = wallpaperFrame)
                }
            }
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
                .then(
                    if (needLiquid && wallpaperFrame == null) {
                        Modifier.layerBackdrop(backdrop)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (needDockFrosted) {
                        Modifier.hazeSource(state = dockHaze, zIndex = 1f)
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (landscape) {
                LandscapeCoverPages(
                    currentIndex = landscapePage,
                    clipLayer = userSpaceProgress < 0.02f,
                    modifier = Modifier
                        .fillMaxSize()
                        .chromePage(),
                ) { index ->
                    val dest = MainPagerDestinations[index]
                    PagerDestinationPane(
                        destination = dest,
                        currentDestination = currentDest,
                        wallpaper = wallpaper,
                        landscape = true,
                    ) {
                        sectionContent(dest)
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .chromePage()
                        .nestedScroll(nestedScroll),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !showFullPlayer && !overlayOpen && !spaceOpen,
                ) { page ->
                    val dest = MainPagerDestinations[page]
                    PagerDestinationPane(
                        destination = dest,
                        currentDestination = currentDest,
                        wallpaper = wallpaper,
                        landscape = false,
                    ) {
                        sectionContent(dest)
                    }
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

        CompositionLocalProvider(
            LocalChromeHaze provides dockHaze,
        ) {
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
                            mvPlayback = app.mvPlayback,
                            backdrop = backdrop,
                            onOpenFull = { openFullPlayer() },
                            onOpenMv = { overlayMv ->
                                if (overlay !is MainOverlay.Mv) {
                                    pushOverlay(overlayMv)
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
                            canDownload = playlistManage.canDownload,
                            removeLabel = if (overlay is MainOverlay.CachedSongs) "删除所选" else "全部移出歌单",
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
                            compactProgress = {
                                when {
                                    showFullPlayer -> dockCompactHold.floatValue
                                    compactDragging.value -> compactDrag.floatValue
                                    else -> compactAnim.value
                                }
                            },
                            landscape = landscape,
                            backdrop = backdrop,
                        )
                    }
                }
            }
            }
        }
        } // weight 内容格
        } // 横竖 Row
        } // 整屏 backdrop / 壁纸采样
        } // 壁纸 CompositionLocal

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
                val mvUi by app.mvPlayback.ui.collectAsStateWithLifecycle()
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

        androidx.compose.animation.AnimatedVisibility(
            visible = showFullPlayer && playingTrackId > 0L && !mvActive,
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
                onOpenUser = { id, name, cover ->
                    pushOverlay(MainOverlay.User(id, name, cover))
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

private data class MiniMusicChrome(
    val track: TrackRow,
    val playWhenReady: Boolean,
    val loadPending: Boolean,
    val durationMs: Long,
)

private data class MiniMvChrome(
    val active: Boolean,
    val mvId: Long,
    val title: String,
    val artistLine: String,
    val coverUrl: String?,
    val playWhenReady: Boolean,
    val buffering: Boolean,
    val durationMs: Long,
    val loading: Boolean,
)

@Composable
private fun MiniPlayerSlot(
    playback: PlaybackViewModel,
    mvPlayback: MvPlayback,
    backdrop: Backdrop,
    onOpenFull: () -> Unit,
    onOpenMv: (MainOverlay.Mv) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mvChrome by remember(mvPlayback) {
        mvPlayback.ui.map {
            MiniMvChrome(
                active = it.active,
                mvId = it.mvId,
                title = it.title,
                artistLine = it.artistLine,
                coverUrl = it.coverUrl,
                playWhenReady = it.playWhenReady,
                buffering = it.buffering,
                durationMs = it.durationMs,
                loading = it.loading,
            )
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        MiniMvChrome(
            active = false,
            mvId = 0L,
            title = "",
            artistLine = "",
            coverUrl = null,
            playWhenReady = false,
            buffering = false,
            durationMs = 0L,
            loading = false,
        ),
    )
    if (mvChrome.active) {
        val mvPositions = remember(mvPlayback) {
            mvPlayback.ui.map { it.positionMs }.distinctUntilChanged()
        }
        MiniPlayerBar(
            track = TrackRow(
                id = -mvChrome.mvId,
                name = mvChrome.title.ifBlank { "MV" },
                artists = mvChrome.artistLine,
                album = null,
                durationMs = mvChrome.durationMs,
                coverUrl = mvChrome.coverUrl,
            ),
            isPlaying = mvChrome.playWhenReady,
            buffering = mvChrome.buffering || mvChrome.loading,
            durationMs = mvChrome.durationMs,
            positions = mvPositions,
            initialPositionMs = mvPlayback.ui.value.positionMs,
            loadPending = mvChrome.loading,
            onOpenFull = {
                onOpenMv(
                    MainOverlay.Mv(
                        id = mvChrome.mvId,
                        title = mvChrome.title,
                        coverUrl = mvChrome.coverUrl,
                        artist = mvChrome.artistLine,
                    ),
                )
            },
            onTogglePlay = { mvPlayback.togglePlayPause() },
            backdrop = backdrop,
            modifier = modifier,
        )
        return
    }
    val music by remember(playback) {
        playback.ui.map { st ->
            st.currentTrack?.let { t ->
                MiniMusicChrome(
                    track = t,
                    playWhenReady = st.playWhenReady,
                    loadPending = st.loadPending,
                    durationMs = st.durationMs,
                )
            }
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(null)
    val chrome = music ?: return
    val positions = remember(playback) {
        playback.ui.map { it.positionMs }.distinctUntilChanged()
    }
    MiniPlayerBar(
        track = chrome.track,
        isPlaying = chrome.playWhenReady,
        buffering = chrome.loadPending,
        durationMs = chrome.durationMs,
        positions = positions,
        initialPositionMs = playback.ui.value.positionMs,
        loadPending = chrome.loadPending,
        onOpenFull = onOpenFull,
        onTogglePlay = { playback.togglePlayPause() },
        backdrop = backdrop,
        modifier = modifier,
    )
}

@Composable
private fun PagerDestinationPane(
    destination: MainDestination,
    currentDestination: MainDestination,
    wallpaper: ChromeWallpaperState,
    landscape: Boolean,
    content: @Composable () -> Unit,
) {
    val pageFrame = wallpaper.frame(destination.wallpaperSurface(), landscape)
    val showOwn = pagerPageShowsOwnWallpaper(
        pageIndex = MainPagerDestinations.indexOf(destination),
        currentPage = MainPagerDestinations.indexOf(currentDestination),
    )
    LaunchedEffect(destination, currentDestination, showOwn, pageFrame?.imagePath) {
        if (pageFrame != null && pagerPageKeepsOwnWallpaperLayer()) {
            Log.i(
                "ZMusicWallpaper",
                "page-layer dest=$destination current=$currentDestination " +
                    "showOwn=$showOwn path=${pageFrame.imagePath}",
            )
        }
    }
    CompositionLocalProvider(
        LocalChromeWallpaperPainted provides (pageFrame != null),
        LocalChromeWallpaperFrame provides pageFrame,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (pageFrame != null && pagerPageKeepsOwnWallpaperLayer()) {
                ChromeWallpaperLayer(
                    frame = pageFrame,
                    placeholder = Color.Transparent,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (showOwn) 1f else 0f
                    },
                )
            }
            content()
        }
    }
}

@Composable
private fun FullPlayerSlot(
    playback: PlaybackViewModel,
    landscape: Boolean,
    onDismiss: () -> Unit,
    onOpenSourcePlaylist: (Long, String, String?) -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
    onOpenUser: (Long, String, String?) -> Unit,
) {
    val st by playback.ui.collectAsStateWithLifecycle()
    if (st.currentTrack == null) return
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val scope = rememberCoroutineScope()
    val backUi = rememberPredictiveBackUi(enabled = true, onBack = onDismiss)
    Box(Modifier.fillMaxSize().predictiveBackLayer(backUi, PredictiveBackAxis.Vertical)) {
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
                val found = resolveTrackArtists(track, cookie, app.songRepository)
                val a = found.firstOrNull()
                if (a == null) {
                    app.islandNoticeCenter.show("暂时无法打开这位歌手", track.coverUrl)
                } else {
                    onOpenArtist(a.id, a.name, track.coverUrl)
                    onDismiss()
                }
            }
        },
        onOpenUser = { id, name, cover ->
            onOpenUser(id, name, cover)
            onDismiss()
        },
    )
    }
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
