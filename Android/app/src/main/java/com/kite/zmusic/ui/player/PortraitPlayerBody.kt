@file:Suppress("UnusedBoxWithConstraintsScope")

package com.kite.zmusic.ui.player

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.plugin.PluginSurfaces
import com.kite.zmusic.plugin.PluginUiTarget
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.PlaybackChromeMode
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PortraitArtistPlacement
import com.kite.zmusic.data.TrackArtist
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.ui.artist.portraitArtistTokens
import com.kite.zmusic.ui.notice.showIslandNotice
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import com.kite.zmusic.i18n.t
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

internal data class PortraitPlayerSheetChrome(
    val settingsOpen: Boolean = false,
    val scoreOpen: Boolean = false,
    val qualityOpen: Boolean = false,
    val commentsOpen: Boolean = false,
    val shareOpen: Boolean = false,
    val panelHold: Boolean = false,
    val onOpenMore: (() -> Unit)? = null,
    val onOpenScore: (() -> Unit)? = null,
    val onOpenQuality: (() -> Unit)? = null,
    val onOpenComments: (() -> Unit)? = null,
    val onOpenShare: (() -> Unit)? = null,
    val onOpenWiki: (() -> Unit)? = null,
    val onCloseSettings: (() -> Unit)? = null,
    val onCloseScore: (() -> Unit)? = null,
    val onCloseQuality: (() -> Unit)? = null,
    val onCloseComments: (() -> Unit)? = null,
    val onCloseShare: (() -> Unit)? = null,
    val onOpenUser: (Long, String, String?) -> Unit = { _, _, _ -> },
    val onOpenListenTogether: () -> Unit = {},
)

internal data class PortraitLyricOverlay(
    val contentAlpha: Float = 1f,
    val onBandCoords: ((LayoutCoordinates) -> Unit)? = null,
    val frozenPositionMs: Long? = null,
    val selectOpen: Boolean = false,
    val selectProgress: Float = 0f,
    val selectSelected: Set<Int> = emptySet(),
    val selectResumeToken: Int = 0,
    val onSelectResumeConsumed: (() -> Unit)? = null,
    val onSelectLongPress: (() -> Unit)? = null,
    val onSelectToggle: ((Int) -> Unit)? = null,
    val onSelectCancel: (() -> Unit)? = null,
    val onSelectCopy: (() -> Unit)? = null,
)

@Composable
internal fun PortraitPlayerBody(
    track: TrackRow,
    lines: List<LrcLine>,
    modifier: Modifier = Modifier,
    lyricCompanions: List<LrcLine?> = emptyList(),
    /** 为 false 表示 [lines] 还是上一首的（新歌歌词未绑定）。 */
    lyricsOnTrack: Boolean = true,
    /** 这首已确认没有歌词（[lines] 为空不一定是没有，也可能还在加载）。 */
    lyricsMissing: Boolean = false,
    /** 同一首播完回到开头时加一，预览词和歌词页再滚一屏。 */
    lyricReplayEpoch: Int = 0,
    positionMs: Long,
    seekPositionMs: Long,
    lyricsExpanded: Boolean,
    onOpenLyrics: () -> Unit,
    onCollapseLyrics: () -> Unit,
    playWhenReady: Boolean,
    isPlaying: Boolean = playWhenReady,
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
    onOpenArtist: (TrackArtist) -> Unit = {},
    dismissSwipeThresholdPx: Float,
    sheets: PortraitPlayerSheetChrome = PortraitPlayerSheetChrome(),
    displayPrefs: PlayerDisplayPrefs = PlayerDisplayPrefs(),
    peekNextTrack: TrackRow? = null,
    peekPrevTrack: TrackRow? = null,
    onSeek: (Long) -> Unit = {},
    lyric: PortraitLyricOverlay = PortraitLyricOverlay(),
    hazeState: HazeState? = null,
    playerLiquidBackdrop: LayerBackdrop? = null,
) {
    val onOpenMore = sheets.onOpenMore
    val onOpenScore = sheets.onOpenScore
    val onOpenQuality = sheets.onOpenQuality
    val onOpenComments = sheets.onOpenComments
    val onOpenShare = sheets.onOpenShare
    val settingsOpen = sheets.settingsOpen
    val scoreOpen = sheets.scoreOpen
    val qualityOpen = sheets.qualityOpen
    val commentsOpen = sheets.commentsOpen
    val shareOpen = sheets.shareOpen
    val panelHold = sheets.panelHold
    val onCloseSettings = sheets.onCloseSettings
    val onCloseScore = sheets.onCloseScore
    val onCloseQuality = sheets.onCloseQuality
    val onCloseComments = sheets.onCloseComments
    val onCloseShare = sheets.onCloseShare
    val onOpenWiki = sheets.onOpenWiki
    val onOpenUser = sheets.onOpenUser
    val onOpenListenTogether = sheets.onOpenListenTogether
    val lyricContentAlpha = lyric.contentAlpha
    val onLyricBandCoords = lyric.onBandCoords
    val frozenLyricPositionMs = lyric.frozenPositionMs
    val lyricSelectOpen = lyric.selectOpen
    val lyricSelectProgress = lyric.selectProgress
    val lyricSelectSelected = lyric.selectSelected
    val lyricSelectResumeToken = lyric.selectResumeToken
    val onLyricSelectResumeConsumed = lyric.onSelectResumeConsumed
    val onLyricSelectLongPress = lyric.onSelectLongPress
    val onLyricSelectToggle = lyric.onSelectToggle
    val onLyricSelectCancel = lyric.onSelectCancel
    val onLyricSelectCopy = lyric.onSelectCopy
    var vinylSkipDir by remember { mutableStateOf(VinylSkipDirection.Next) }
    var vinylBusy by remember { mutableStateOf(false) }
    val vinylSizeScale = displayPrefs.vinylSizeScale
        .coerceIn(PlayerDisplayPrefs.VINYL_SIZE_SCALE_MIN, PlayerDisplayPrefs.VINYL_SIZE_SCALE_MAX)
    val vinylOffsetX = displayPrefs.vinylOffsetXDp
        .coerceIn(PlayerDisplayPrefs.VINYL_OFFSET_MIN, PlayerDisplayPrefs.VINYL_OFFSET_MAX)
        .dp
    val vinylOffsetY = displayPrefs.vinylOffsetYDp
        .coerceIn(PlayerDisplayPrefs.VINYL_OFFSET_Y_MIN, PlayerDisplayPrefs.VINYL_OFFSET_Y_MAX)
        .dp
    val vinylFullCover = displayPrefs.vinylFullCover
    val vinylOuterScale = displayPrefs.vinylOuterScale
        .coerceIn(PlayerDisplayPrefs.VINYL_OUTER_SCALE_MIN, PlayerDisplayPrefs.VINYL_OUTER_SCALE_MAX)
    val vinylCenterRadiusFrac = displayPrefs.vinylCenterRadiusFrac
        .coerceIn(
            PlayerDisplayPrefs.VINYL_CENTER_RADIUS_MIN,
            PlayerDisplayPrefs.VINYL_CENTER_RADIUS_MAX,
        )
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
    var topChromeH by remember { mutableStateOf(64.dp) }
    val portraitArtists = if (displayPrefs.portraitArtistVisible) {
        track.portraitArtistTokens()
    } else {
        emptyList()
    }
    val topReserve = (if (portraitArtists.isNotEmpty()) topChromeH else 56.dp) * chromeT
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
    val app = context.applicationContext as ZMusicApplication
    val pluginEngine = app.pluginEngine
    val playbackChrome by app.playbackChromeStore.mode.collectAsStateWithLifecycle()
    val islandGestures = playbackChrome == PlaybackChromeMode.Island
    val listenUi by app.listenTogether.ui.collectAsStateWithLifecycle()
    val playbackFmActive by remember {
        app.playbackBridge.ui.map { it.fmActive }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = app.playbackBridge.ui.value.fmActive)
    val fmChoice by app.personalFmModeStore.choice.collectAsStateWithLifecycle()
    var fmPickerOpen by remember { mutableStateOf(false) }
    var fmPickerOrigin by remember { mutableStateOf(Rect.Zero) }
    var fmIconCovered by remember { mutableStateOf(false) }
    var fmApplying by remember { mutableStateOf(false) }
    LaunchedEffect(playbackFmActive) {
        if (!playbackFmActive) {
            fmPickerOpen = false
            fmApplying = false
        }
    }
    LaunchedEffect(hiding) {
        if (hiding) {
            context.showIslandNotice(t("已进入清屏沉浸模式"))
        }
    }

    Box(modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (playerLiquidBackdrop != null) {
                    Modifier.layerBackdrop(playerLiquidBackdrop)
                } else {
                    Modifier
                },
            )
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
                    shareOpen -> onCloseShare
                    // 灵动岛：下滑改开百科，上滑才退出。
                    islandGestures -> onOpenWiki
                    else -> onDismiss
                },
                // 仅黑胶模式；有面板时不抢上滑。不跟手，阈值与下滑退出对称。
                onSwipeUp = when {
                    lyricSelectOpen || selectT > 0.001f || lyricsExpanded || panelHold -> null
                    islandGestures -> onDismiss
                    else -> onOpenWiki
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
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .onSizeChanged { sz ->
                                if (sz.height > 0) {
                                    topChromeH = with(density) { sz.height.toDp() }
                                }
                            },
                    ) {
                        PortraitPlayerTopBar(
                            track = track,
                            artists = portraitArtists,
                            artistPlacement = displayPrefs.portraitArtistPlacement,
                            onOpenArtist = onOpenArtist,
                            onDismiss = onDismiss,
                            showFmMode = playbackFmActive,
                            fmModeCovered = fmIconCovered,
                            onFmModeClick = { origin ->
                                fmPickerOrigin = origin
                                fmPickerOpen = true
                            },
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
                            .clipToBounds()
                            // 整页可点进歌词（含黑胶左右空白）；勿用 clickable，以免 down 即 consume 挡住下滑退出
                            .vinylLightTapGestures(
                                onTap = onOpenLyrics,
                                onLongPress = {
                                    pluginEngine.handleSurfaceLongPress(
                                        PluginSurfaces.PLAYER_COVER,
                                        PluginUiTarget.track(track),
                                    )
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val budget = minOf(maxWidth, maxHeight)
                            val base = minOf(budget, 312.dp).let { cap ->
                                if (budget >= 200.dp) cap.coerceAtLeast(minOf(200.dp, budget)) else cap
                            }
                            val side = (base * vinylSizeScale).coerceAtMost(budget)
                            Box(
                                Modifier
                                    .size(side)
                                    .align(Alignment.Center)
                                    .offset(x = vinylOffsetX, y = vinylOffsetY)
                                    .playerExpandAnchor(PlayerExpandSlot.FullVinyl),
                            ) {
                                VinylTransitionStage(
                                    track = track,
                                    peekNext = peekNextTrack,
                                    peekPrev = peekPrevTrack,
                                    spinning = playWhenReady && !buffering && !vinylBusy,
                                    direction = vinylSkipDir,
                                    gesturesEnabled = !settingsOpen && !commentsOpen && !qualityOpen && !shareOpen,
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
                                    centerRadiusFrac = vinylCenterRadiusFrac,
                                    outerScale = vinylOuterScale,
                                    showOuterPlate = displayPrefs.vinylOuterEnabled,
                                    plateColors = displayPrefs.vinylPlateColors(),
                                    gestureDamping = displayPrefs.vinylGestureDamping,
                                    spinPeriodMs = displayPrefs.vinylSpinPeriodMs(),
                                )
                            }
                        }
                        // 叠在黑胶区底部、进度条上方：不占播放条高度，进出随封面 AnimatedContent
                        if (displayPrefs.portraitPreviewLyricEnabled) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .padding(bottom = 2.dp),
                            ) {
                                LyricRollSwap(
                                    trackId = track.id,
                                    lines = lines,
                                    companions = lyricCompanions,
                                    onTrack = lyricsOnTrack,
                                    missing = lyricsMissing,
                                    replayEpoch = lyricReplayEpoch,
                                    contentAlignment = Alignment.BottomCenter,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { set, leaving, shift ->
                                    PortraitPreviewLyrics(
                                        lines = set.lines,
                                        companions = set.companions,
                                        originalOnTop = displayPrefs.portraitLyricOriginalOnTop,
                                        showCompanionOnOthers =
                                            displayPrefs.portraitLyricOthersShowTranslation,
                                        positionMs = rememberHeldWhile(positionMs, leaving),
                                        durationMs = rememberHeldWhile(durationMs, leaving),
                                        count = displayPrefs.portraitPreviewLyricCount,
                                        playingArgb = displayPrefs.portraitPreviewLyricPlayingArgb,
                                        upcomingArgb = displayPrefs.portraitPreviewLyricUpcomingArgb,
                                        playingFontSp = displayPrefs.portraitPreviewLyricPlayingFontSp,
                                        upcomingFontSp = displayPrefs.portraitPreviewLyricUpcomingFontSp,
                                        fancy = displayPrefs.portraitPreviewLyricFancy,
                                        align = displayPrefs.portraitPreviewLyricAlign,
                                        offsetYDp = displayPrefs.portraitPreviewLyricOffsetYDp,
                                        lineSpacingDp = displayPrefs.portraitPreviewLyricLineSpacingDp,
                                        onOpenLyrics = onOpenLyrics,
                                        clockRunning = !leaving && isPlaying && playWhenReady &&
                                            !buffering && !sliderDragging,
                                        rollShift = shift,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
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
                        LyricRollSwap(
                            trackId = track.id,
                            lines = lines,
                            companions = lyricCompanions,
                            onTrack = lyricsOnTrack,
                            missing = lyricsMissing,
                            replayEpoch = lyricReplayEpoch,
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
                        ) { set, leaving, shift ->
                            if (!set.ready) return@LyricRollSwap
                            PortraitCinemaLyrics(
                                lines = set.lines,
                                companions = set.companions,
                                originalOnTop = displayPrefs.portraitLyricOriginalOnTop,
                                showCompanionOnOthers =
                                    displayPrefs.portraitLyricOthersShowTranslation,
                                positionMs = rememberHeldWhile(frozenLyricPositionMs ?: positionMs, leaving),
                                trackDurationMs = rememberHeldWhile(durationMs, leaving),
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
                                onToggleSelect = if (leaving) null else onLyricSelectToggle,
                                onLongPressLine = if (leaving) null else { _ -> onLyricSelectLongPress?.invoke() },
                                resumeScrollToken = rememberHeldWhile(lyricSelectResumeToken, leaving),
                                onResumeScrollConsumed = if (leaving) null else onLyricSelectResumeConsumed,
                                onSeekToMs = { ms ->
                                    if (!leaving) {
                                        onSeek(ms.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                                        if (displayPrefs.lyricTapAutoPlay && !playWhenReady) {
                                            onTogglePlay()
                                        }
                                    }
                                },
                                onCollapse = { onLyricBlankTap() },
                                onBandCoords = if (leaving) null else onLyricBandCoords,
                                clockRunning = !leaving && isPlaying && playWhenReady && !buffering &&
                                    !sliderDragging && frozenLyricPositionMs == null,
                                sliderDragging = !leaving && sliderDragging,
                                rollShift = shift,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
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
                        onOpenShare = onOpenShare,
                        commentsAsChat = listenUi.inRoom,
                        commentsUnread = listenUi.unreadChat,
                        controlsOffsetYDp = displayPrefs.portraitTransportOffsetYDp,
                        controlsContainerInclude = displayPrefs.portraitTransportContainerInclude,
                        controlsChromeAlpha = transportClearA,
                        toolbarChromeAlpha = toolbarClearA,
                    )
                }
            }
        }

        if (!selectUiActive && topClearA > 0.02f) {
            PortraitListenTogetherAvatars(
                compact = lyricsExpanded,
                onOpenUser = onOpenUser,
                onOpenListenTogether = onOpenListenTogether,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = topChromeH + 10.dp)
                    .graphicsLayer {
                        alpha = topClearA
                        clip = false
                    }
                    .zIndex(8f),
            )
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
                    track = track,
                    artists = portraitArtists,
                    artistPlacement = displayPrefs.portraitArtistPlacement,
                    onOpenArtist = onOpenArtist,
                    onDismiss = onDismiss,
                    showFmMode = playbackFmActive,
                    fmModeCovered = fmIconCovered,
                    onFmModeClick = { origin ->
                        fmPickerOrigin = origin
                        fmPickerOpen = true
                    },
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
                    onOpenShare = onOpenShare,
                    commentsAsChat = listenUi.inRoom,
                    commentsUnread = listenUi.unreadChat,
                    controlsOffsetYDp = displayPrefs.portraitTransportOffsetYDp,
                    controlsContainerInclude = displayPrefs.portraitTransportContainerInclude,
                    reportExpandPlay = false,
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

        PersonalFmModePickerOverlay(
            visible = fmPickerOpen,
            originBounds = fmPickerOrigin,
            current = fmChoice,
            applying = fmApplying,
            haze = hazeState,
            chromeBackground = false,
            onCoveredChange = { fmIconCovered = it },
            onDismiss = { if (!fmApplying) fmPickerOpen = false },
            onSelect = { choice ->
                if (choice == fmChoice) {
                    fmPickerOpen = false
                    return@PersonalFmModePickerOverlay
                }
                fmApplying = true
                app.playbackBridge.applyPersonalFmMode(choice) {
                    fmApplying = false
                    fmPickerOpen = false
                }
            },
        )
    }
}

@Composable
private fun PortraitPlayerTopBar(
    track: TrackRow,
    artists: List<TrackArtist>,
    artistPlacement: PortraitArtistPlacement,
    onOpenArtist: (TrackArtist) -> Unit,
    onDismiss: () -> Unit,
    showFmMode: Boolean = false,
    onFmModeClick: (Rect) -> Unit = {},
    fmModeCovered: Boolean = false,
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
        PortraitPlayerTitle(
            name = track.name,
            artists = artists,
            placement = artistPlacement,
            onOpenArtist = onOpenArtist,
            modifier = Modifier.weight(1f),
        )
        if (showFmMode) {
            NowPlayingFmModeButton(
                chromeBackground = false,
                covered = fmModeCovered,
                onClick = onFmModeClick,
            )
        }
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

@Composable
private fun PortraitPlayerTitle(
    name: String,
    artists: List<TrackArtist>,
    placement: PortraitArtistPlacement,
    onOpenArtist: (TrackArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleStyle = TextStyle(
        color = LyricCurrent,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        letterSpacing = 0.2.sp,
        textAlign = TextAlign.Start,
    )
    val shown = artists.isNotEmpty()
    AnimatedContent(
        targetState = placement to shown,
        modifier = modifier.playerExpandHideFull(),
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            val move = tween<IntOffset>(360, easing = FastOutSlowInEasing)
            val dropping = targetState.second &&
                targetState.first == PortraitArtistPlacement.Below &&
                initialState.second &&
                initialState.first == PortraitArtistPlacement.Append
            val rising = targetState.second &&
                targetState.first == PortraitArtistPlacement.Append &&
                initialState.second &&
                initialState.first == PortraitArtistPlacement.Below
            val enterFrom: (Int) -> Int = { height ->
                when {
                    dropping -> -height / 4
                    rising -> height / 3
                    targetState.second && !initialState.second -> height / 4
                    else -> -height / 5
                }
            }
            val exitTo: (Int) -> Int = { height ->
                when {
                    dropping -> height / 3
                    rising -> -height / 4
                    initialState.second && !targetState.second -> -height / 4
                    else -> height / 5
                }
            }
            val transform = (
                fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                    slideInVertically(move, enterFrom)
                ) togetherWith (
                fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                    slideOutVertically(move, exitTo)
                ) using SizeTransform(clip = false) { _, _ ->
                tween(360, easing = FastOutSlowInEasing)
            }
            transform.targetContentZIndex = 1f
            transform
        },
        label = "portraitArtistPlace",
    ) { (mode, visible) ->
        PortraitPlayerTitleFrame(
            name = name,
            artists = if (visible) artists else emptyList(),
            placement = mode,
            onOpenArtist = onOpenArtist,
            titleStyle = titleStyle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PortraitPlayerTitleFrame(
    name: String,
    artists: List<TrackArtist>,
    placement: PortraitArtistPlacement,
    onOpenArtist: (TrackArtist) -> Unit,
    titleStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) {
        Text(
            text = name,
            style = titleStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.playerExpandAnchor(PlayerExpandSlot.FullTitle),
        )
        return
    }
    val artistColor = LyricCurrent.copy(alpha = 0.78f)
    val dotColor = LyricCurrent.copy(alpha = 0.45f)
    when (placement) {
        PortraitArtistPlacement.Append -> {
            Text(
                text = buildAnnotatedString {
                    append(name)
                    withStyle(SpanStyle(color = dotColor)) { append(" · ") }
                    appendPortraitArtists(
                        artists = artists,
                        style = SpanStyle(
                            color = artistColor,
                            fontWeight = FontWeight.Medium,
                        ),
                        onOpen = onOpenArtist,
                    )
                },
                style = titleStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier.playerExpandAnchor(PlayerExpandSlot.FullTitle),
            )
        }
        PortraitArtistPlacement.Below -> {
            Column(modifier) {
                Text(
                    text = name,
                    style = titleStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .playerExpandAnchor(PlayerExpandSlot.FullTitle),
                )
                Text(
                    text = buildAnnotatedString {
                        appendPortraitArtists(
                            artists = artists,
                            style = SpanStyle(
                                color = artistColor,
                                fontWeight = FontWeight.Medium,
                            ),
                            onOpen = onOpenArtist,
                        )
                    },
                    style = TextStyle(
                        color = artistColor,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        letterSpacing = 0.15.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp),
                )
            }
        }
    }
}

private fun AnnotatedString.Builder.appendPortraitArtists(
    artists: List<TrackArtist>,
    style: SpanStyle,
    onOpen: (TrackArtist) -> Unit,
) {
    val separator = style.copy(color = style.color.copy(alpha = 0.45f))
    artists.forEachIndexed { index, artist ->
        if (index > 0) {
            withStyle(separator) { append(" / ") }
        }
        withLink(
            LinkAnnotation.Clickable(
                tag = "portrait-artist-$index-${artist.id}",
                styles = TextLinkStyles(
                    style = style,
                    pressedStyle = style.copy(color = style.color.copy(alpha = 0.4f)),
                ),
                linkInteractionListener = { onOpen(artist) },
            ),
        ) {
            append(artist.name)
        }
    }
}

/** 横屏歌词：可手动滑动浏览；浏览时停自动跟滚；点选 seek；高度由已播/待播句数决定。 */
