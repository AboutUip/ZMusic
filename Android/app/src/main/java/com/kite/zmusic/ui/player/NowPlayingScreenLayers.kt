@file:Suppress("UnusedBoxWithConstraintsScope")

package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerBackgroundPreset
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.theme.TextTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NowPlayingScreenLayers(
    modifier: Modifier,
    isLandscape: Boolean,
    state: PlaybackUiState,
    track: TrackRow,
    lyricLines: List<LrcLine>,
    lyricPos: Long,
    displayPos: Long,
    seekDisplayPos: Long,
    duration: Long,
    sliderDragging: Boolean,
    sliderValue: Float,
    onSliderDraggingChange: (Boolean) -> Unit,
    onSliderValueChange: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    onOpenArtist: (() -> Unit)?,
    onOpenUser: (Long, String, String?) -> Unit = { _, _, _ -> },
    onPlayQueueIndex: (Int) -> Unit,
    onNeedQueueThrough: (Int) -> Unit,
    trackLiked: Boolean,
    onToggleLike: () -> Unit,
    displayPrefs: PlayerDisplayPrefs,
    portraitDisplayPrefs: PlayerDisplayPrefs,
    onDisplayPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onPortraitDisplayPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onPortraitDisplayPrefsFlush: () -> Unit,
    onDisplayPrefsFlush: () -> Unit,
    settingsHazeState: HazeState,
    rainIntensity: Float,
    dismissSwipeThresholdPx: Float,
    landscapeStartInset: Dp,
    audioQuality: AudioQuality,
    app: ZMusicApplication,
    portraitLyricsOpen: Boolean,
    onPortraitLyricsOpenChange: (Boolean) -> Unit,
    portraitSettingsOpen: Boolean,
    portraitScoreOpen: Boolean,
    portraitQualityOpen: Boolean,
    portraitCommentsOpen: Boolean,
    portraitMoreOpen: Boolean,
    portraitPosterOpen: Boolean,
    portraitPosterFrozenPositionMs: Long,
    portraitBackgroundEditorOpen: Boolean,
    onPortraitBackgroundEditorOpenChange: (Boolean) -> Unit,
    portraitLyricStyleEditorOpen: Boolean,
    portraitLyricSelectOpen: Boolean,
    portraitSettingsT: Float,
    portraitScoreT: Float,
    portraitQualityT: Float,
    portraitCommentsT: Float,
    portraitMoreT: Float,
    portraitLyricSelectT: Float,
    portraitLyricStyleT: Float,
    portraitLiveLyricAlpha: Float,
    portraitStyleCloneAlpha: Float,
    portraitCustomBg: PlayerBackgroundPreset?,
    portraitCustomBgProgress: Float,
    portraitSheetFrac: Animatable<Float, AnimationVector1D>,
    portraitMoreSheetFrac: Animatable<Float, AnimationVector1D>,
    portraitScoreSheetFrac: Animatable<Float, AnimationVector1D>,
    portraitCommentsSheetFrac: Animatable<Float, AnimationVector1D>,
    portraitSheetDragVel: Float,
    onPortraitSheetDragVelChange: (Float) -> Unit,
    portraitMoreSheetDragVel: Float,
    onPortraitMoreSheetDragVelChange: (Float) -> Unit,
    portraitScoreSheetDragVel: Float,
    onPortraitScoreSheetDragVelChange: (Float) -> Unit,
    portraitMoreNested: Boolean,
    onPortraitMoreNestedChange: (Boolean) -> Unit,
    portraitMoreSavedFrac: Float,
    onPortraitMoreSavedFracChange: (Float) -> Unit,
    portraitSheetScope: CoroutineScope,
    portraitScoreRevealToken: Int,
    onPortraitPlayerRootCoords: (LayoutCoordinates) -> Unit,
    onPortraitLyricsBandCoords: (LayoutCoordinates) -> Unit,
    portraitLyricSelectSelected: SnapshotStateSet<Int>,
    portraitLyricSelectResumeToken: Int,
    onPortraitLyricSelectResumeTokenChange: (Int) -> Unit,
    portraitLyricStyleSnapshot: LyricStyleSnapshot?,
    portraitLyricStyleFrozenPositionMs: Long,
    draftPortraitLyricPlaying: LyricRoleStyle,
    draftPortraitLyricPlayed: LyricRoleStyle,
    draftPortraitLyricUnplayed: LyricRoleStyle,
    draftPortraitPlayedCount: Int,
    draftPortraitUpcomingCount: Int,
    draftPortraitLineSpacing: Float,
    onDraftPortraitLyricPlayingChange: (LyricRoleStyle) -> Unit,
    onDraftPortraitLyricPlayedChange: (LyricRoleStyle) -> Unit,
    onDraftPortraitLyricUnplayedChange: (LyricRoleStyle) -> Unit,
    onDraftPortraitPlayedCountChange: (Int) -> Unit,
    onDraftPortraitUpcomingCountChange: (Int) -> Unit,
    onDraftPortraitLineSpacingChange: (Float) -> Unit,
    closePortraitSettings: () -> Unit,
    closePortraitScore: () -> Unit,
    closePortraitQuality: () -> Unit,
    closePortraitComments: () -> Unit,
    closePortraitMore: () -> Unit,
    closePortraitLyricSelect: () -> Unit,
    closePortraitPoster: () -> Unit,
    closePortraitLyricStyleEditor: (Boolean) -> Unit,
    openPortraitMore: () -> Unit,
    openPortraitScore: () -> Unit,
    openPortraitQuality: () -> Unit,
    openPortraitComments: () -> Unit,
    openPortraitSettings: () -> Unit,
    openPortraitPoster: () -> Unit,
    openPortraitLyricSelect: () -> Unit,
    requestPortraitLyricStyleEditor: () -> Unit,
    snapPortraitSheet: () -> Unit,
    snapPortraitMoreSheet: () -> Unit,
    snapPortraitScoreSheet: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier
            .fillMaxSize()
            .then(
                if (!isLandscape) {
                    Modifier.onGloballyPositioned { onPortraitPlayerRootCoords(it) }
                } else {
                    Modifier
                },
            ),
    ) {
        val stageBackdrop = @Composable {
        if (isLandscape) {
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(state = settingsHazeState, zIndex = 0f),
            ) {
                GeminiOrbsBackdrop(
                    modifier = Modifier.fillMaxSize(),
                    activeHalo = displayPrefs.activeHalo,
                    playWhenReady = state.playWhenReady,
                    positionMs = state.positionMs,
                    scrubbing = sliderDragging,
                    trackId = track.id,
                    loadPending = state.loadPending,
                )
                if (rainIntensity > 0.01f) {
                    RainGlassAtmosphere(
                        modifier = Modifier.fillMaxSize(),
                        intensity = rainIntensity,
                    )
                }
            }
        } else {
            // 竖屏：自定义背景与光球交叉淡入；自定义图铺满含系统栏区域
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(state = settingsHazeState, zIndex = 0f),
            ) {
                // 不透明底：Fit 留白 / 交叉淡入时不透出主界面迷你条
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(TextTheme.PlayerStage),
                )
                GeminiOrbsBackdrop(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (1f - portraitCustomBgProgress).coerceIn(0f, 1f)
                        },
                    activeHalo = portraitDisplayPrefs.activeHalo &&
                        !portraitDisplayPrefs.customBackgroundEnabled,
                    playWhenReady = state.playWhenReady,
                    positionMs = state.positionMs,
                    scrubbing = sliderDragging,
                    trackId = track.id,
                    loadPending = state.loadPending,
                    motionEnabled = !portraitLyricsOpen &&
                        !portraitCommentsOpen &&
                        !portraitSettingsOpen &&
                        !portraitPosterOpen,
                )
                PlayerCustomBackgroundLayer(
                    preset = portraitCustomBg,
                    progress = portraitCustomBgProgress,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 竖屏歌词页：全屏低透光磨砂铺在背景之上、控件之下（含播放条区域，无局部卡片）
        val lyricVeilT by animateFloatAsState(
            targetValue = if (!isLandscape && portraitLyricsOpen) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (portraitLyricsOpen) 380 else 260,
                easing = FastOutSlowInEasing,
            ),
            label = "portraitLyricReadingVeil",
        )
        PortraitLyricReadingVeil(
            progress = lyricVeilT,
            hazeState = settingsHazeState,
            transparency = portraitDisplayPrefs.lyricBackgroundTransparency,
            modifier = Modifier.fillMaxSize(),
        )

        }
        val playerColumn = @Composable {
        Column(
            Modifier
                .fillMaxSize()
                // 横屏：内容可延伸进挖孔区；仅避开底部导航条。
                // 左右边距对称交给底部播放条自行处理，避免 End-only inset 导致不居中。
                .then(
                    if (isLandscape) {
                        Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
                        )
                    } else {
                        // 竖屏底部留给播放组件延伸到系统导航条区域做垂直居中
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                            ),
                        )
                    },
                )
                .padding(
                    start = if (isLandscape) 0.dp else (landscapeStartInset + 12.dp),
                    end = if (isLandscape) 0.dp else 12.dp,
                    top = if (isLandscape) 0.dp else 6.dp,
                    bottom = 0.dp,
                ),
        ) {
            val srcTitle = state.sourcePlaylistTitle

            if (isLandscape) {
                LandscapePlayerBody(
                    track = track,
                    lines = lyricLines,
                    positionMs = lyricPos,
                    seekPositionMs = displayPos,
                    isPlaying = state.isPlaying,
                    playWhenReady = state.playWhenReady,
                    buffering = state.buffering,
                    loadPending = state.loadPending,
                    onTogglePlay = onTogglePlay,
                    onSkipNext = onSkipNext,
                    onSkipPrev = onSkipPrev,
                    playbackMode = state.playbackMode,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    trackLiked = trackLiked,
                    onToggleLike = onToggleLike,
                    durationMs = duration,
                    sourceTitle = srcTitle,
                    // 横屏：歌单名点击曾直接关全屏（像左上角隐形退出区）；改由右上角退出钮
                    onSourceClick = null,
                    onArtistClick = onOpenArtist,
                    sliderDragging = sliderDragging,
                    sliderValue = sliderValue,
                    onSliderDragStart = {
                        if (!state.loadPending) {
                            onSliderDraggingChange(true)
                            onSliderValueChange(seekDisplayPos.toFloat())
                        }
                    },
                    onSliderChange = { onSliderValueChange(it) },
                    onSliderDragEnd = { v ->
                        onSliderDraggingChange(false)
                        onSeek(v.toLong().coerceIn(0L, state.durationMs))
                    },
                    onDismiss = onDismiss,
                    dismissSwipeThresholdPx = dismissSwipeThresholdPx,
                    displayPrefs = displayPrefs,
                    onDisplayPrefsChange = onDisplayPrefsChange,
                    onDisplayPrefsFlush = onDisplayPrefsFlush,
                    settingsHazeState = settingsHazeState,
                    peekNextTrack = state.peekNextTrack,
                    peekPrevTrack = state.peekPrevTrack,
                    notice = state.notice,
                    transportWakeToken = state.transportWakeToken,
                    onSeek = onSeek,
                    queue = state.queue,
                    queueIndex = state.index,
                    onPlayQueueIndex = onPlayQueueIndex,
                    onNeedQueueThrough = onNeedQueueThrough,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PortraitPlayerBody(
                    track = track,
                    lines = lyricLines,
                    positionMs = lyricPos,
                    seekPositionMs = displayPos,
                    lyricsExpanded = portraitLyricsOpen,
                    onOpenLyrics = { onPortraitLyricsOpenChange(true) },
                    onCollapseLyrics = {
                        if (portraitLyricSelectOpen || portraitLyricSelectT > 0.001f) {
                            closePortraitLyricSelect()
                        } else {
                            onPortraitLyricsOpenChange(false)
                        }
                    },
                    playWhenReady = state.playWhenReady,
                    buffering = state.loadPending,
                    onTogglePlay = onTogglePlay,
                    onSkipNext = onSkipNext,
                    onSkipPrev = onSkipPrev,
                    playbackMode = state.playbackMode,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    trackLiked = trackLiked,
                    onToggleLike = onToggleLike,
                    durationMs = duration,
                    sliderDragging = sliderDragging,
                    sliderValue = sliderValue,
                    onSliderDragStart = {
                        onSliderDraggingChange(true)
                        onSliderValueChange(seekDisplayPos.toFloat())
                    },
                    onSliderChange = { onSliderValueChange(it) },
                    onSliderDragEnd = { v ->
                        onSliderDraggingChange(false)
                        onSeek(v.toLong().coerceIn(0L, state.durationMs))
                    },
                    onDismiss = onDismiss,
                    dismissSwipeThresholdPx = dismissSwipeThresholdPx,
                    onOpenMore = { openPortraitMore() },
                    onOpenScore = { openPortraitScore() },
                    onOpenQuality = { openPortraitQuality() },
                    onOpenComments = { openPortraitComments() },
                    settingsOpen = portraitSettingsOpen,
                    scoreOpen = portraitScoreOpen,
                    qualityOpen = portraitQualityOpen,
                    commentsOpen = portraitCommentsOpen,
                    panelHold = portraitSettingsOpen ||
                        portraitScoreOpen ||
                        portraitQualityOpen ||
                        portraitCommentsOpen ||
                        portraitMoreOpen ||
                        portraitPosterOpen ||
                        portraitBackgroundEditorOpen ||
                        portraitLyricStyleEditorOpen,
                    onCloseSettings = { closePortraitSettings() },
                    onCloseScore = { closePortraitScore() },
                    onCloseQuality = { closePortraitQuality() },
                    onCloseComments = { closePortraitComments() },
                    displayPrefs = portraitDisplayPrefs,
                    peekNextTrack = state.peekNextTrack,
                    peekPrevTrack = state.peekPrevTrack,
                    onSeek = onSeek,
                    lyricContentAlpha = portraitLiveLyricAlpha,
                    onLyricBandCoords = onPortraitLyricsBandCoords,
                    frozenLyricPositionMs = if (portraitLyricStyleSnapshot != null) {
                        portraitLyricStyleFrozenPositionMs
                    } else {
                        null
                    },
                    lyricSelectOpen = portraitLyricSelectOpen,
                    lyricSelectProgress = portraitLyricSelectT,
                    lyricSelectSelected = portraitLyricSelectSelected,
                    lyricSelectResumeToken = portraitLyricSelectResumeToken,
                    onLyricSelectResumeConsumed = { onPortraitLyricSelectResumeTokenChange(0) },
                    onLyricSelectLongPress = { openPortraitLyricSelect() },
                    onLyricSelectToggle = { index ->
                        if (index in portraitLyricSelectSelected) {
                            portraitLyricSelectSelected.remove(index)
                        } else {
                            portraitLyricSelectSelected.add(index)
                        }
                    },
                    onLyricSelectCancel = { closePortraitLyricSelect() },
                    onLyricSelectCopy = {
                        copyLyricSelection(
                            context,
                            lyricLines,
                            portraitLyricSelectSelected.toSet(),
                        )
                        closePortraitLyricSelect()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        }
        val portraitChrome: @Composable BoxScope.() -> Unit = {
        // 竖屏设置：独立配置 + 可拉伸底部面板（吸附 1/3、2/3、全屏，不强制）
        if (!isLandscape && (portraitSettingsT > 0.001f || portraitSettingsOpen)) {
            val density = LocalDensity.current
            NowPlayingSettingsOutsideDismiss(
                onDismiss = { closePortraitSettings() },
                enabled = portraitSettingsOpen || portraitSettingsT > 0.05f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = portraitSettingsT },
            )
            BoxWithConstraints(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                val screenH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                // 全屏吸附不超过状态栏下沿，避免把手顶进状态栏后无法再下拉
                val statusTopPx = with(density) {
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
                }
                val maxSheetH = (screenH - statusTopPx).coerceAtLeast(screenH * 0.5f)
                val sheetHPx = (portraitSheetFrac.value * maxSheetH)
                    .coerceIn(maxSheetH * 0.12f, maxSheetH)
                val sheetHDp = with(density) { sheetHPx.toDp() }
                NowPlayingSettingsSheet(
                    prefs = portraitDisplayPrefs,
                    onPrefsChange = onPortraitDisplayPrefsChange,
                    hazeState = settingsHazeState,
                    showTransferActions = false,
                    titleOnlyHeader = true,
                    headerTitle = "竖屏显示",
                    portraitContent = true,
                    enableRealtimeHaze = true,
                    panelShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    glassBlurRadius = 56.dp,
                    showDragHandle = true,
                    onOpenCustomBackgroundEditor = {
                        onPortraitBackgroundEditorOpenChange(true)
                    },
                    onOpenLyricStyleEditor = {
                        requestPortraitLyricStyleEditor()
                    },
                    onDragHandleVertical = { dragAmount ->
                        // 跟手：向上拖为负 → 增高；记录高度变化速度供松手吸附偏向
                        val deltaFrac = -dragAmount / maxSheetH
                        onPortraitSheetDragVelChange(
                            portraitSheetDragVel * 0.62f + deltaFrac * 0.38f,
                        )
                        val next = (portraitSheetFrac.value + deltaFrac).coerceIn(0.12f, 1f)
                        portraitSheetScope.launch { portraitSheetFrac.snapTo(next) }
                    },
                    onDragHandleEnd = { snapPortraitSheet() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(sheetHDp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = (1f - portraitSettingsT) * sheetHPx
                            alpha = portraitSettingsT
                        },
                )
            }
        }

        // 竖屏曲谱：与设置同壳层动画；打开固定 2/3，可吸附 1/3·2/3·全屏
        if (!isLandscape && (portraitScoreT > 0.001f || portraitScoreOpen)) {
            val density = LocalDensity.current
            NowPlayingSettingsOutsideDismiss(
                onDismiss = { closePortraitScore() },
                enabled = portraitScoreOpen || portraitScoreT > 0.05f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = portraitScoreT },
            )
            BoxWithConstraints(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                val screenH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val statusTopPx = with(density) {
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
                }
                val maxSheetH = (screenH - statusTopPx).coerceAtLeast(screenH * 0.5f)
                val sheetHPx = (portraitScoreSheetFrac.value * maxSheetH)
                    .coerceIn(maxSheetH * 0.12f, maxSheetH)
                val sheetHDp = with(density) { sheetHPx.toDp() }
                PortraitQueueSheet(
                    tracks = state.queue,
                    currentIndex = state.index,
                    isPlaying = state.playWhenReady,
                    revealToken = portraitScoreRevealToken,
                    onPlayIndex = onPlayQueueIndex,
                    hazeState = settingsHazeState,
                    onDragHandleVertical = { dragAmount ->
                        val deltaFrac = -dragAmount / maxSheetH
                        onPortraitScoreSheetDragVelChange(
                            portraitScoreSheetDragVel * 0.62f + deltaFrac * 0.38f,
                        )
                        val next = (portraitScoreSheetFrac.value + deltaFrac)
                            .coerceIn(0.12f, 1f)
                        portraitSheetScope.launch { portraitScoreSheetFrac.snapTo(next) }
                    },
                    onDragHandleEnd = { snapPortraitScoreSheet() },
                    onApproachEnd = onNeedQueueThrough,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(sheetHDp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = (1f - portraitScoreT) * sheetHPx
                            alpha = portraitScoreT
                        },
                )
            }
        }

        // 竖屏音源：与曲谱同壳层进出场；固定打开 1/3
        if (!isLandscape && (portraitQualityT > 0.001f || portraitQualityOpen)) {
            val density = LocalDensity.current
            NowPlayingSettingsOutsideDismiss(
                onDismiss = { closePortraitQuality() },
                enabled = portraitQualityOpen || portraitQualityT > 0.05f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = portraitQualityT },
            )
            BoxWithConstraints(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                val screenH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val statusTopPx = with(density) {
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
                }
                val maxSheetH = (screenH - statusTopPx).coerceAtLeast(screenH * 0.5f)
                val sheetHPx = maxSheetH / 3f
                val sheetHDp = with(density) { sheetHPx.toDp() }
                PortraitQualitySheet(
                    selected = audioQuality,
                    onSelect = { next ->
                        if (next != audioQuality) {
                            app.audioQualityStore.set(next)
                            context.showIslandNotice("已切换到${next.title}")
                        }
                        closePortraitQuality()
                    },
                    hazeState = settingsHazeState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(sheetHDp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = (1f - portraitQualityT) * sheetHPx
                            alpha = portraitQualityT
                        },
                )
            }
        }

        // 竖屏评论：与曲谱同壳层进出场；固定打开 2/3，上箭头扩全屏（不可拖拽改高）
        if (!isLandscape && (portraitCommentsT > 0.001f || portraitCommentsOpen)) {
            val density = LocalDensity.current
            val commentCookie = app.sessionRepository.session.value?.cookie.orEmpty()
            NowPlayingSettingsOutsideDismiss(
                onDismiss = { closePortraitComments() },
                enabled = portraitCommentsOpen || portraitCommentsT > 0.05f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = portraitCommentsT },
            )
            BoxWithConstraints(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                val screenH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                // 全屏时弹窗/背景铺满到屏幕顶（含状态栏区域）；内容区仍自留安全边距
                val maxSheetH = screenH
                val sheetHPx = (portraitCommentsSheetFrac.value * maxSheetH)
                    .coerceIn(maxSheetH * (2f / 3f), maxSheetH)
                val sheetHDp = with(density) { sheetHPx.toDp() }
                PortraitCommentsSheet(
                    songId = track.id,
                    cookie = commentCookie,
                    openProgress = portraitCommentsT,
                    sheetFrac = portraitCommentsSheetFrac.value,
                    onExpandFullscreen = {
                        portraitSheetScope.launch {
                            portraitCommentsSheetFrac.animateCommentSheetFrac(1f)
                        }
                    },
                    onCollapseToTwoThirds = {
                        portraitSheetScope.launch {
                            portraitCommentsSheetFrac.animateCommentSheetFrac(2f / 3f)
                        }
                    },
                    coverUrl = track.coverUrl,
                    hazeState = settingsHazeState,
                    onOpenUser = onOpenUser,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(sheetHDp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = (1f - portraitCommentsT) * sheetHPx
                            alpha = portraitCommentsT
                        },
                )
            }
        }

        // 竖屏：自定义背景全屏编辑器（沉浸铺满，样式对齐设置面板）
        if (!isLandscape) {
            CustomBackgroundEditorOverlay(
                open = portraitBackgroundEditorOpen,
                prefs = portraitDisplayPrefs,
                sampleTrack = track,
                onPrefsChange = onPortraitDisplayPrefsChange,
                onDismiss = {
                    onPortraitDisplayPrefsFlush()
                    onPortraitBackgroundEditorOpenChange(false)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 竖屏：制作海报全屏向导
        if (!isLandscape) {
            PosterMakeOverlay(
                open = portraitPosterOpen,
                track = track,
                lines = lyricLines,
                frozenPositionMs = portraitPosterFrozenPositionMs,
                onDismiss = { closePortraitPoster() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!isLandscape && (portraitMoreT > 0.001f || portraitMoreOpen)) {
            val density = LocalDensity.current
            NowPlayingSettingsOutsideDismiss(
                onDismiss = { closePortraitMore() },
                enabled = portraitMoreOpen || portraitMoreT > 0.05f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = portraitMoreT },
            )
            BoxWithConstraints(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                val screenH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val statusTopPx = with(density) {
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
                }
                val maxSheetH = (screenH - statusTopPx).coerceAtLeast(screenH * 0.5f)
                val sheetHPx = (portraitMoreSheetFrac.value * maxSheetH)
                    .coerceIn(maxSheetH * 0.12f, maxSheetH)
                val sheetHDp = with(density) { sheetHPx.toDp() }
                val maxSheetHDp = with(density) { maxSheetH.toDp() }
                PortraitMoreSheet(
                    track = track,
                    excludePlaylistId = state.sourcePlaylistId ?: 0L,
                    visible = portraitMoreOpen,
                    maxHeight = maxSheetHDp,
                    lyricPreferTranslation = portraitDisplayPrefs.portraitLyricPreferTranslation,
                    onLyricPreferTranslationChange = { on ->
                        onPortraitDisplayPrefsChange(
                            portraitDisplayPrefs.copy(portraitLyricPreferTranslation = on),
                        )
                    },
                    onOpenPoster = {
                        closePortraitMore()
                        openPortraitPoster()
                    },
                    onOpenSettings = {
                        closePortraitMore()
                        openPortraitSettings()
                    },
                    onClose = { closePortraitMore() },
                    hazeState = settingsHazeState,
                    onDragHandleVertical = { dragAmount ->
                        val deltaFrac = -dragAmount / maxSheetH
                        onPortraitMoreSheetDragVelChange(
                            portraitMoreSheetDragVel * 0.62f + deltaFrac * 0.38f,
                        )
                        val next = (portraitMoreSheetFrac.value + deltaFrac).coerceIn(0.12f, 1f)
                        portraitSheetScope.launch { portraitMoreSheetFrac.snapTo(next) }
                    },
                    onDragHandleEnd = { snapPortraitMoreSheet() },
                    onCoverMinFrac = { minFrac ->
                        portraitSheetScope.launch {
                            if (minFrac != null) {
                                if (!portraitMoreNested) {
                                    onPortraitMoreSavedFracChange(portraitMoreSheetFrac.value)
                                    onPortraitMoreNestedChange(true)
                                }
                                if (minFrac > portraitMoreSheetFrac.value + 0.02f) {
                                    portraitMoreSheetFrac.animateTo(
                                        minFrac.coerceIn(1f / 3f, 1f),
                                        animationSpec = spring(
                                            dampingRatio = 0.82f,
                                            stiffness = 380f,
                                        ),
                                    )
                                }
                            } else if (portraitMoreNested) {
                                onPortraitMoreNestedChange(false)
                                portraitMoreSheetFrac.animateTo(
                                    portraitMoreSavedFrac.coerceIn(1f / 3f, 1f),
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = 380f,
                                    ),
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(sheetHDp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = (1f - portraitMoreT) * sheetHPx
                            alpha = portraitMoreT
                        },
                )
            }
        }

        // 竖屏：歌词样式全屏编辑（克隆穿透 + 优雅进出场）
        if (!isLandscape) {
            val styleSnap = portraitLyricStyleSnapshot
            val styleT = portraitLyricStyleT
            if (styleT > 0.001f || portraitLyricStyleEditorOpen) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val statusTop = WindowInsets.statusBars
                        .asPaddingValues()
                        .calculateTopPadding()
                    val navBottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                    val restSlot = portraitLyricStyleRestPreviewSlot(
                        screenWidth = maxWidth,
                        screenHeight = maxHeight,
                        statusTop = statusTop,
                        navBottom = navBottom,
                    )
                    PortraitLyricStyleEditorOverlay(
                        progress = styleT,
                        draftPlaying = draftPortraitLyricPlaying,
                        draftPlayed = draftPortraitLyricPlayed,
                        draftUnplayed = draftPortraitLyricUnplayed,
                        draftPlayedCount = draftPortraitPlayedCount,
                        draftUpcomingCount = draftPortraitUpcomingCount,
                        draftLineSpacingDp = draftPortraitLineSpacing,
                        onDraftPlayingChange = onDraftPortraitLyricPlayingChange,
                        onDraftPlayedChange = onDraftPortraitLyricPlayedChange,
                        onDraftUnplayedChange = onDraftPortraitLyricUnplayedChange,
                        onDraftPlayedCountChange = onDraftPortraitPlayedCountChange,
                        onDraftUpcomingCountChange = onDraftPortraitUpcomingCountChange,
                        onDraftLineSpacingChange = onDraftPortraitLineSpacingChange,
                        hazeState = settingsHazeState,
                        onDismiss = { closePortraitLyricStyleEditor(false) },
                        onBackToSettings = { closePortraitLyricStyleEditor(true) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (styleSnap != null && portraitStyleCloneAlpha > 0.001f) {
                        LyricStyleCloneLayer(
                            snapshot = styleSnap.copy(
                                playedCount = draftPortraitPlayedCount,
                                upcomingCount = draftPortraitUpcomingCount,
                                lineSpacingDp = draftPortraitLineSpacing,
                            ),
                            draftPlaying = draftPortraitLyricPlaying,
                            draftPlayed = draftPortraitLyricPlayed,
                            draftUnplayed = draftPortraitLyricUnplayed,
                            progress = styleT,
                            targetSlot = restSlot,
                            uiScale = portraitDisplayPrefs.uiScale,
                            contentAlpha = portraitStyleCloneAlpha,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // 竖屏：右上短通知（贴外层 Box，避免挡在 Column 流式布局里）
        if (!isLandscape) {
            PlaybackCornerNotice(
                notice = state.notice,
                chromeProgress = 0f,
                topBase = 10.dp,
                endPad = 14.dp,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        // 顶部 HUD：竖屏保留；横屏极淡，像器物铭牌
        if (!isLandscape || state.buffering) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = if (isLandscape) 10.dp else 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.buffering) "···" else "NOW PLAYING",
                    style = TextStyle(
                        color = LyricDim.copy(alpha = if (isLandscape) 0.18f else 0.3f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        letterSpacing = if (isLandscape) 1.6.sp else 2.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        }
        stageBackdrop()
        playerColumn()
        portraitChrome()
    }
}
