package com.kite.zmusic.ui.main

import androidx.activity.compose.BackHandler
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.PlaybackViewModel
import com.kite.zmusic.ui.common.ImmersiveSystemUi
import com.kite.zmusic.ui.player.MiniPlayerBar
import com.kite.zmusic.ui.player.NowPlayingScreen
import com.kite.zmusic.ui.scifi.SciFiBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay

private val MainPagerDestinations = MainDestination.entries

/**
 * 主界面壳：科幻背景 + 按方向的 Dock + 内容区；展开 HUD 与登录页 SciFiPanelFrame 一致。
 * 竖屏与横屏均支持水平跟手滑动切换模块（与 HUD 选页同步）。
 */
@Composable
fun MainShell(
    displayLabel: String?,
    sessionRepository: SessionRepository,
    playback: PlaybackViewModel,
    modifier: Modifier = Modifier,
) {
    val playbackState by playback.ui.collectAsStateWithLifecycle()
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    val playingTrack = playbackState.currentTrack
    var pendingLibraryOpen by remember { mutableStateOf<Pair<Long, String>?>(null) }

    LaunchedEffect(playingTrack) {
        if (playingTrack == null) showFullPlayer = false
    }

    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var destination by rememberSaveable { mutableStateOf(MainDestination.Home) }
    var dockExpanded by rememberSaveable { mutableStateOf(false) }
    var quickSwitchPreview by remember { mutableStateOf<MainDestination?>(null) }
    var dockMenuIdleCompact by remember { mutableStateOf(false) }
    var dockIdleSeq by remember { mutableIntStateOf(0) }

    /** 「我的」歌单全屏详情：隐藏主导航、保留迷你播放条 */
    var libraryPlaylistFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(libraryPlaylistFullScreen) {
        if (libraryPlaylistFullScreen) dockExpanded = false
    }

    BackHandler(enabled = dockExpanded && !showFullPlayer && !libraryPlaylistFullScreen) {
        dockExpanded = false
    }

    val dockMenuLayoutExpanded = !dockMenuIdleCompact || dockExpanded

    val dockActivityHolder = remember {
        object {
            var bump: () -> Unit = {}
        }
    }
    SideEffect {
        dockActivityHolder.bump = {
            dockMenuIdleCompact = false
            dockIdleSeq++
        }
    }

    LaunchedEffect(dockExpanded) {
        if (dockExpanded) {
            dockMenuIdleCompact = false
            quickSwitchPreview = null
        }
    }

    LaunchedEffect(dockIdleSeq, dockExpanded) {
        if (dockExpanded) return@LaunchedEffect
        delay(2_000)
        if (!dockExpanded) dockMenuIdleCompact = true
    }

    val initialPage = MainPagerDestinations.indexOf(destination).coerceIn(0, MainPagerDestinations.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { MainPagerDestinations.size },
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val d = MainPagerDestinations[page]
                if (d != destination) destination = d
            }
    }

    LaunchedEffect(destination) {
        val target = MainPagerDestinations.indexOf(destination)
        if (target != pagerState.currentPage) {
            pagerState.animateScrollToPage(target)
        }
    }

    val dockFaceDestination = quickSwitchPreview ?: destination

    Box(modifier.fillMaxSize()) {
        val immersive =
            // 横屏全程隐藏状态栏/导航栏；竖屏全程显示
            landscape
        ImmersiveSystemUi(enabled = immersive)

        SciFiBackdrop(Modifier.fillMaxSize())
        // 角框与主内容共用 safeDrawing；仅收纳键交互会重置 2s 空闲（滑动 Pager 不展开 Dock）
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (landscape) {
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!libraryPlaylistFullScreen) {
                        MainDockLandscape(
                            expanded = dockExpanded,
                            onExpandedChange = { dockExpanded = it },
                            faceDestination = dockFaceDestination,
                            committedDestination = destination,
                            onQuickSwitchPreview = { quickSwitchPreview = it },
                            onQuickSwitchCommit = { destination = it },
                            layoutExpanded = dockMenuLayoutExpanded,
                            onUserActivity = { dockActivityHolder.bump() },
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(top = 4.dp, bottom = 4.dp, end = 6.dp),
                        )
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !dockExpanded && !libraryPlaylistFullScreen,
                    ) { page ->
                        MainSectionContent(
                            destination = MainPagerDestinations[page],
                            displayLabel = displayLabel,
                            isLandscape = true,
                            sessionRepository = sessionRepository,
                            playbackState = playbackState,
                            pendingLibraryOpen = pendingLibraryOpen,
                            onConsumePendingLibraryOpen = { pendingLibraryOpen = null },
                            onLibraryPlaylistFullScreenChange = { libraryPlaylistFullScreen = it },
                            onPlayTracks = { list: List<TrackRow>, idx: Int, plId: Long?, plTitle: String? ->
                                playback.playQueue(list, idx, plId, plTitle)
                                showFullPlayer = true
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !dockExpanded && !libraryPlaylistFullScreen,
                    ) { page ->
                        MainSectionContent(
                            destination = MainPagerDestinations[page],
                            displayLabel = displayLabel,
                            isLandscape = false,
                            sessionRepository = sessionRepository,
                            playbackState = playbackState,
                            pendingLibraryOpen = pendingLibraryOpen,
                            onConsumePendingLibraryOpen = { pendingLibraryOpen = null },
                            onLibraryPlaylistFullScreenChange = { libraryPlaylistFullScreen = it },
                            onPlayTracks = { list: List<TrackRow>, idx: Int, plId: Long?, plTitle: String? ->
                                playback.playQueue(list, idx, plId, plTitle)
                                showFullPlayer = true
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = playingTrack != null,
                        enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                            slideInVertically(
                                animationSpec = tween(340, easing = FastOutSlowInEasing),
                                initialOffsetY = { it / 2 },
                            ),
                        exit = fadeOut(tween(200)) +
                            slideOutVertically(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                                targetOffsetY = { it / 3 },
                            ),
                    ) {
                        val tr = playingTrack ?: return@AnimatedVisibility
                        MiniPlayerBar(
                            track = tr,
                            isPlaying = playbackState.isPlaying,
                            buffering = playbackState.buffering || playbackState.loadPending,
                            positionMs = playbackState.positionMs,
                            durationMs = playbackState.durationMs,
                            onOpenFull = { showFullPlayer = true },
                            onTogglePlay = { playback.togglePlayPause() },
                            onClose = { playback.clearQueue() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    if (!libraryPlaylistFullScreen) {
                        MainDockPortrait(
                            expanded = dockExpanded,
                            onExpandedChange = { dockExpanded = it },
                            faceDestination = dockFaceDestination,
                            committedDestination = destination,
                            onQuickSwitchPreview = { quickSwitchPreview = it },
                            onQuickSwitchCommit = { destination = it },
                            layoutExpanded = dockMenuLayoutExpanded,
                            onUserActivity = { dockActivityHolder.bump() },
                        )
                    }
                }
            }
        }

        val tipDest = quickSwitchPreview
        if (tipDest != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .zIndex(70f),
                contentAlignment = if (landscape) Alignment.TopStart else Alignment.BottomCenter,
            ) {
                val landDockTopPad =
                    (if (dockMenuLayoutExpanded) 96.dp else MainDockCompactCircleDp) + 12.dp
                MainDockQuickSwitchTip(
                    destination = tipDest,
                    modifier = Modifier.padding(
                        start = if (landscape) {
                            (if (dockMenuLayoutExpanded) MainDockRailWidth else MainDockCompactCircleDp) + 16.dp
                        } else {
                            20.dp
                        },
                        top = if (landscape) landDockTopPad else 0.dp,
                        end = 20.dp,
                        bottom = if (!landscape) {
                            (if (dockMenuLayoutExpanded) MainDockPortraitBarHeight else MainDockCompactCircleDp) + 28.dp
                        } else {
                            0.dp
                        },
                    ),
                )
            }
        }

        MainDockExpandedOverlay(
            visible = dockExpanded && !libraryPlaylistFullScreen,
            isLandscape = landscape,
            destination = destination,
            onDestination = { destination = it },
            onDismiss = { dockExpanded = false },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = landscape && playingTrack != null,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(24f),
            enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    animationSpec = tween(340, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 2 },
                ),
            exit = fadeOut(tween(200)) +
                slideOutVertically(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    targetOffsetY = { it / 3 },
                ),
        ) {
            val tr = playingTrack ?: return@AnimatedVisibility
            Box(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                        MiniPlayerBar(
                            track = tr,
                            isPlaying = playbackState.isPlaying,
                            buffering = playbackState.buffering || playbackState.loadPending,
                            positionMs = playbackState.positionMs,
                            durationMs = playbackState.durationMs,
                            onOpenFull = { showFullPlayer = true },
                            onTogglePlay = { playback.togglePlayPause() },
                            onClose = { playback.clearQueue() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = if (libraryPlaylistFullScreen) {
                                        14.dp
                                    } else {
                                        (if (dockMenuLayoutExpanded) MainDockRailWidth else MainDockCompactCircleDp) + 12.dp
                                    },
                                    end = 14.dp,
                                ),
                        )
            }
        }

        AnimatedVisibility(
            visible = showFullPlayer && playingTrack != null,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(130f),
            enter = fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    animationSpec = tween(380, easing = FastOutSlowInEasing),
                    initialOffsetY = { full -> full },
                ),
            exit = fadeOut(tween(260)) +
                slideOutVertically(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    targetOffsetY = { full -> full },
                ),
        ) {
            val st = playbackState
            val tr = st.currentTrack
            if (tr != null) {
                BackHandler { showFullPlayer = false }
                NowPlayingScreen(
                    state = st,
                    isLandscape = landscape,
                    onDismiss = { showFullPlayer = false },
                    onTogglePlay = { playback.togglePlayPause() },
                    onSeek = playback::seekTo,
                    onSkipNext = { playback.skipNext() },
                    onSkipPrev = { playback.skipPrevious() },
                    onCyclePlaybackMode = playback::cyclePlaybackMode,
                    modifier = Modifier.fillMaxSize(),
                    landscapeStartInset = if (landscape) {
                        if (libraryPlaylistFullScreen) {
                            10.dp
                        } else {
                            (if (dockMenuLayoutExpanded) MainDockRailWidth else MainDockCompactCircleDp) + 6.dp
                        }
                    } else {
                        0.dp
                    },
                    onOpenSourcePlaylist = if (st.sourcePlaylistId != null) {
                        {
                            val title = st.sourcePlaylistTitle ?: "歌单"
                            pendingLibraryOpen = st.sourcePlaylistId!! to title
                            destination = MainDestination.Library
                            showFullPlayer = false
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private val TipCyan = Color(0xFF00FFD1)
private val TipDim = Color(0xFF8FA8B8)

@Composable
private fun MainDockQuickSwitchTip(
    destination: MainDestination,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(
                Color(0xFF050910).copy(alpha = 0.9f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = "${destination.glyph}  ${destination.titleZh}",
            style = TextStyle(
                color = TipCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = destination.blurb,
            style = TextStyle(
                color = TipDim.copy(alpha = 0.88f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
                lineHeight = 14.sp,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "松开手指 · 确认切换到该页",
            style = TextStyle(
                color = TipDim.copy(alpha = 0.55f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp,
                textAlign = TextAlign.Start,
            ),
        )
    }
}
