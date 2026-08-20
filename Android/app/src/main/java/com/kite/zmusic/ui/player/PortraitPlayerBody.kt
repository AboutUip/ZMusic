@file:Suppress("UnusedBoxWithConstraintsScope")

package com.kite.zmusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateSet
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LrcParser
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PlayerDisplayPrefsStore
import com.kite.zmusic.data.PlaylistTrackLoader
import com.kite.zmusic.data.TitleAlignMode
import com.kite.zmusic.data.TitleLineStyle
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.VinylColorStyle
import com.kite.zmusic.playback.AudioSpectrumBands
import com.kite.zmusic.playback.PlaybackNotice
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.playback.mergePlaylistQueue
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.notice.showIslandNotice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.unit.lerp as lerpDp


@Composable
internal fun PortraitPlayerBody(
    track: TrackRow,
    lines: List<LrcLine>,
    positionMs: Long,
    seekPositionMs: Long,
    lyricsExpanded: Boolean,
    onOpenLyrics: () -> Unit,
    onCollapseLyrics: () -> Unit,
    playWhenReady: Boolean,
    buffering: Boolean,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    playbackMode: PlaybackMode,
    onCyclePlaybackMode: () -> Unit,
    trackLiked: Boolean,
    onToggleLike: () -> Unit,
    durationMs: Long,
    sliderDragging: Boolean,
    sliderValue: Float,
    onSliderDragStart: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderDragEnd: (Float) -> Unit,
    onDismiss: () -> Unit,
    dismissSwipeThresholdPx: Float,
    onOpenMore: (() -> Unit)? = null,
    onOpenScore: (() -> Unit)? = null,
    onOpenQuality: (() -> Unit)? = null,
    onOpenComments: (() -> Unit)? = null,
    settingsOpen: Boolean = false,
    scoreOpen: Boolean = false,
    qualityOpen: Boolean = false,
    commentsOpen: Boolean = false,
    /** 有面板盖在歌词页上时暂停自动清屏并保持 chrome */
    panelHold: Boolean = false,
    onCloseSettings: (() -> Unit)? = null,
    onCloseScore: (() -> Unit)? = null,
    onCloseQuality: (() -> Unit)? = null,
    onCloseComments: (() -> Unit)? = null,
    displayPrefs: PlayerDisplayPrefs = PlayerDisplayPrefs(),
    peekNextTrack: TrackRow? = null,
    peekPrevTrack: TrackRow? = null,
    onSeek: (Long) -> Unit = {},
    lyricContentAlpha: Float = 1f,
    onLyricBandCoords: ((LayoutCoordinates) -> Unit)? = null,
    frozenLyricPositionMs: Long? = null,
    lyricSelectOpen: Boolean = false,
    lyricSelectProgress: Float = 0f,
    lyricSelectSelected: Set<Int> = emptySet(),
    lyricSelectResumeToken: Int = 0,
    onLyricSelectResumeConsumed: (() -> Unit)? = null,
    onLyricSelectLongPress: (() -> Unit)? = null,
    onLyricSelectToggle: ((Int) -> Unit)? = null,
    onLyricSelectCancel: (() -> Unit)? = null,
    onLyricSelectCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var vinylSkipDir by remember { mutableStateOf(VinylSkipDirection.Next) }
    var vinylBusy by remember { mutableStateOf(false) }
    val vinylSizeScale = displayPrefs.vinylSizeScale
        .coerceIn(PlayerDisplayPrefs.VINYL_SIZE_SCALE_MIN, PlayerDisplayPrefs.VINYL_SIZE_SCALE_MAX)
    val vinylOffsetY = displayPrefs.vinylOffsetYDp
        .coerceIn(PlayerDisplayPrefs.VINYL_OFFSET_Y_MIN, PlayerDisplayPrefs.VINYL_OFFSET_Y_MAX)
        .dp
    val vinylFullCover = displayPrefs.vinylFullCover
    val uiScale = displayPrefs.uiScale
        .coerceIn(PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX)
    val selectT = lyricSelectProgress.coerceIn(0f, 1f)
    val chromeT = (1f - selectT).coerceIn(0f, 1f)
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    // 与 PortraitLyricSelectBar 占位一致；仅选句时收放占位。
    // 进/出歌词页绝不切换 chrome 挂载方式，否则中间区高度突变导致黑胶上下跳。
    val selectBarReserve = 48.dp + 16.dp + navBottom.coerceAtLeast(12.dp)
    var transportReserve by remember { mutableStateOf(200.dp) }
    val topReserve = 56.dp * chromeT
    val bottomReserve = androidx.compose.ui.unit.lerp(selectBarReserve, transportReserve, chromeT)
    val selectUiActive = lyricSelectOpen || selectT > 0.001f
    val autoClearEnabled = displayPrefs.portraitLyricAutoClear
    val autoClearArmed = lyricsExpanded && autoClearEnabled && !selectUiActive
    val overlayHold = panelHold || sliderDragging
    var chromeRevealed by remember { mutableStateOf(true) }
    var revealGen by remember { mutableIntStateOf(0) }
    val clearSeconds = displayPrefs.portraitLyricAutoClearSeconds.coerceIn(
        PlayerDisplayPrefs.AUTO_CLEAR_SECONDS_MIN,
        PlayerDisplayPrefs.AUTO_CLEAR_SECONDS_MAX,
    )
    fun pokeClearChrome() {
        chromeRevealed = true
        revealGen++
    }
    LaunchedEffect(lyricsExpanded, autoClearEnabled) {
        chromeRevealed = true
        revealGen++
    }
    LaunchedEffect(overlayHold) {
        if (overlayHold) {
            chromeRevealed = true
            revealGen++
        }
    }
    LaunchedEffect(autoClearArmed, overlayHold, revealGen, clearSeconds, chromeRevealed) {
        if (!autoClearArmed || overlayHold || !chromeRevealed) return@LaunchedEffect
        delay(clearSeconds * 1000L)
        chromeRevealed = false
    }
    val hiding = autoClearArmed && !chromeRevealed && !overlayHold
    val topHide = hiding && displayPrefs.portraitLyricAutoClearTop
    val transportHide = hiding && displayPrefs.portraitLyricAutoClearTransport
    val toolbarHide = hiding && displayPrefs.portraitLyricAutoClearToolbar
    val clearAnim = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
    val topClearA by animateFloatAsState(
        targetValue = if (topHide) 0f else 1f,
        animationSpec = clearAnim,
        label = "portraitLyricClearTop",
    )
    val transportClearA by animateFloatAsState(
        targetValue = if (transportHide) 0f else 1f,
        animationSpec = clearAnim,
        label = "portraitLyricClearTransport",
    )
    val toolbarClearA by animateFloatAsState(
        targetValue = if (toolbarHide) 0f else 1f,
        animationSpec = clearAnim,
        label = "portraitLyricClearToolbar",
    )
    val lyricOffsetAnim by animateFloatAsState(
        targetValue = if (hiding) 0f else displayPrefs.lyricOffsetYDp,
        animationSpec = clearAnim,
        label = "portraitLyricClearOffset",
    )
    val chromeFullyAwake =
        chromeRevealed &&
            topClearA > 0.97f &&
            transportClearA > 0.97f &&
            toolbarClearA > 0.97f
    fun onLyricBlankTap() {
        if (autoClearArmed && !chromeFullyAwake) {
            pokeClearChrome()
            return
        }
        onCollapseLyrics()
    }
    val context = LocalContext.current
    LaunchedEffect(hiding) {
        if (hiding) {
            context.showIslandNotice("已进入清屏沉浸模式")
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = uiScale
                scaleY = uiScale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                clip = false
            }
            .nowPlayingBlankGestures(
                dismissThresholdPx = dismissSwipeThresholdPx,
                onTap = null,
                onSwipeDown = when {
                    // 歌词页 / 选句：下滑只滚列表，点按收起或点取消；勿抢手势
                    lyricSelectOpen || selectT > 0.001f || lyricsExpanded -> null
                    commentsOpen -> onCloseComments
                    settingsOpen -> onCloseSettings
                    scoreOpen -> onCloseScore
                    qualityOpen -> onCloseQuality
                    else -> onDismiss
                },
            ),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (selectUiActive) {
                // 选句：顶栏改为叠层，这里只保留连续插值占位
                Spacer(Modifier.height(topReserve))
            } else {
                CollapseFade(progress = topClearA, slideDown = false) {
                    Column(Modifier.padding(horizontal = 4.dp)) {
                        PortraitPlayerTopBar(
                            trackName = track.name,
                            onDismiss = onDismiss,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            AnimatedContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                targetState = lyricsExpanded,
                transitionSpec = {
                    (
                        fadeIn(tween(280)) togetherWith fadeOut(tween(200))
                        ) using SizeTransform(clip = false)
                },
                label = "portraitLyricMode",
            ) { expanded ->
                if (!expanded) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            // 整页可点进歌词（含黑胶左右空白）；勿用 clickable，以免 down 即 consume 挡住下滑退出
                            .vinylLightTapGestures(onTap = onOpenLyrics),
                        contentAlignment = Alignment.Center,
                    ) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val base = maxWidth.coerceAtMost(312.dp).coerceAtLeast(200.dp)
                            val side = base * vinylSizeScale
                            Box(
                                Modifier
                                    .size(side)
                                    .align(Alignment.Center)
                                    .offset(y = vinylOffsetY),
                            ) {
                                VinylTransitionStage(
                                    track = track,
                                    peekNext = peekNextTrack,
                                    peekPrev = peekPrevTrack,
                                    spinning = playWhenReady && !buffering && !vinylBusy,
                                    direction = vinylSkipDir,
                                    gesturesEnabled = !settingsOpen && !commentsOpen && !qualityOpen,
                                    onTransitionRunningChange = { vinylBusy = it },
                                    onCommitSkip = { dir ->
                                        vinylSkipDir = dir
                                        when (dir) {
                                            VinylSkipDirection.Next -> onSkipNext()
                                            VinylSkipDirection.Previous -> onSkipPrev()
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    fullCover = vinylFullCover,
                                    centerRadiusFrac = 0.20f,
                                    outerScale = 1f,
                                    plateColors = VinylPlateColors.Black,
                                    gestureDamping = displayPrefs.vinylGestureDamping,
                                )
                            }
                        }
                    }
                } else {
                    val outerIx = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    ) {
                        if (!selectUiActive || chromeT > 0.35f) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clickable(
                                        interactionSource = outerIx,
                                        indication = null,
                                        onClick = { onLyricBlankTap() },
                                    ),
                            )
                        }
                        PortraitCinemaLyrics(
                            lines = lines,
                            positionMs = frozenLyricPositionMs ?: positionMs,
                            trackDurationMs = durationMs,
                            playingStyle = displayPrefs.lyricPlayingStyle,
                            playedStyle = displayPrefs.lyricPlayedStyle,
                            unplayedStyle = displayPrefs.lyricUnplayedStyle,
                            playedCount = displayPrefs.lyricPlayedCount,
                            upcomingCount = displayPrefs.lyricUpcomingCount,
                            lineSpacingDp = displayPrefs.lyricLineSpacingDp,
                            offsetYDp = lyricOffsetAnim,
                            contentAlpha = lyricContentAlpha,
                            selectProgress = selectT,
                            selectOpen = lyricSelectOpen,
                            selectedIndices = lyricSelectSelected,
                            onToggleSelect = onLyricSelectToggle,
                            onLongPressLine = { onLyricSelectLongPress?.invoke() },
                            resumeScrollToken = lyricSelectResumeToken,
                            onResumeScrollConsumed = onLyricSelectResumeConsumed,
                            onSeekToMs = { ms ->
                                onSeek(ms.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                                if (displayPrefs.lyricTapAutoPlay && !playWhenReady) {
                                    onTogglePlay()
                                }
                            },
                            onCollapse = { onLyricBlankTap() },
                            onBandCoords = onLyricBandCoords,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .then(
                                    if (autoClearArmed) {
                                        Modifier.pointerInput(autoClearArmed) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    if (event.changes.any { it.pressed && !it.previousPressed }) {
                                                        pokeClearChrome()
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }

            if (selectUiActive) {
                Spacer(Modifier.height(bottomReserve))
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .onSizeChanged { sz ->
                            if (sz.height > 0) {
                                val h = with(density) { sz.height.toDp() }
                                if (h > transportReserve) transportReserve = h
                            }
                        },
                ) {
                    PlayerTransport(
                        isPlaying = playWhenReady,
                        buffering = buffering,
                        onTogglePlay = onTogglePlay,
                        onSkipNext = {
                            vinylSkipDir = VinylSkipDirection.Next
                            onSkipNext()
                        },
                        onSkipPrev = {
                            vinylSkipDir = VinylSkipDirection.Previous
                            onSkipPrev()
                        },
                        durationMs = durationMs,
                        positionMs = seekPositionMs,
                        sliderDragging = sliderDragging,
                        sliderValue = sliderValue,
                        onSliderDragStart = onSliderDragStart,
                        onSliderChange = onSliderChange,
                        onSliderDragEnd = onSliderDragEnd,
                        playbackMode = playbackMode,
                        onCyclePlaybackMode = onCyclePlaybackMode,
                        trackLiked = trackLiked,
                        onToggleLike = onToggleLike,
                        portraitSlim = true,
                        landscapeDense = false,
                        onOpenMore = onOpenMore,
                        onOpenScore = onOpenScore,
                        onOpenQuality = onOpenQuality,
                        onOpenComments = onOpenComments,
                        controlsOffsetYDp = displayPrefs.portraitTransportOffsetYDp,
                        controlsContainerInclude = displayPrefs.portraitTransportContainerInclude,
                        controlsChromeAlpha = transportClearA,
                        toolbarChromeAlpha = toolbarClearA,
                    )
                }
            }
        }

        if (selectUiActive) {
            // 顶栏叠层：只淡出位移，不改播放条测量约束
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        alpha = chromeT
                        translationY = -(1f - chromeT) * 24f
                    },
            ) {
                PortraitPlayerTopBar(
                    trackName = track.name,
                    onDismiss = onDismiss,
                )
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .onSizeChanged { sz ->
                        if (sz.height > 0) {
                            val h = with(density) { sz.height.toDp() }
                            // 只抬高、不回落，避免淡出过程测量抖动改写占位
                            if (h > transportReserve) transportReserve = h
                        }
                    }
                    .graphicsLayer {
                        alpha = chromeT
                        translationY = (1f - chromeT) * 28f
                    },
            ) {
                PlayerTransport(
                    isPlaying = playWhenReady,
                    buffering = buffering,
                    onTogglePlay = onTogglePlay,
                    onSkipNext = {
                        vinylSkipDir = VinylSkipDirection.Next
                        onSkipNext()
                    },
                    onSkipPrev = {
                        vinylSkipDir = VinylSkipDirection.Previous
                        onSkipPrev()
                    },
                    durationMs = durationMs,
                    positionMs = seekPositionMs,
                    sliderDragging = sliderDragging,
                    sliderValue = sliderValue,
                    onSliderDragStart = onSliderDragStart,
                    onSliderChange = onSliderChange,
                    onSliderDragEnd = onSliderDragEnd,
                    playbackMode = playbackMode,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    trackLiked = trackLiked,
                    onToggleLike = onToggleLike,
                    portraitSlim = true,
                    landscapeDense = false,
                    onOpenMore = onOpenMore,
                    onOpenScore = onOpenScore,
                    onOpenQuality = onOpenQuality,
                    onOpenComments = onOpenComments,
                    controlsOffsetYDp = displayPrefs.portraitTransportOffsetYDp,
                    controlsContainerInclude = displayPrefs.portraitTransportContainerInclude,
                )
            }

            PortraitLyricSelectBar(
                selectedCount = lyricSelectSelected.size,
                progress = selectT,
                onCancel = { onLyricSelectCancel?.invoke() },
                onCopy = { onLyricSelectCopy?.invoke() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PortraitPlayerTopBar(
    trackName: String,
    onDismiss: () -> Unit,
) {
    val activity = LocalActivity.current
    val rotationLock = com.kite.zmusic.ui.orientation.LocalSessionRotationLock.current
    val rotationLocked = com.kite.zmusic.ui.orientation.SessionRotationLockStore.locked
    val systemAutoRotate =
        com.kite.zmusic.ui.orientation.rememberSystemAutoRotateEnabled()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowPlayingDismissIconButton(
            onClick = onDismiss,
            chromeBackground = false,
        )
        Text(
            text = trackName,
            style = TextStyle(
                color = LyricCurrent,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Start,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        NowPlayingRotationLockButton(
            locked = rotationLocked,
            forceToLandscape = if (systemAutoRotate) null else true,
            chromeBackground = false,
            onClick = {
                if (systemAutoRotate) {
                    rotationLock.toggle(activity)
                } else {
                    rotationLock.forceOrientation(activity, landscape = true)
                }
            },
        )
    }
}

/** 横屏歌词：可手动滑动浏览；浏览时停自动跟滚；点选 seek；高度由已播/待播句数决定。 */
