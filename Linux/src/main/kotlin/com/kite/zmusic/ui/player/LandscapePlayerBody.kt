package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TitleLineStyle
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.LandscapeLyricsWeight
import com.kite.zmusic.ui.main.LandscapeVinylWeight
import com.kite.zmusic.ui.main.landscapeVinylDiscDp
import kotlinx.coroutines.delay

private val ChromePad = 28.dp

@Composable
fun LandscapePlayerBody(
    state: PlaybackUiState,
    wordByWord: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onMode: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleLike: () -> Unit = {},
    onPlayAt: (Int) -> Unit = {},
    displayPrefs: PlayerDisplayPrefs = PlayerDisplayPrefs(),
    onDisplayPrefsChange: (PlayerDisplayPrefs) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack
    val lines = when {
        wordByWord && state.wordLyricLines.isNotEmpty() -> state.wordLyricLines
        else -> state.lyricLines
    }
    var controlsVisible by remember {
        mutableStateOf(
            displayPrefs.transportAlwaysVisible || System.getProperty("zmusic.test") == "true",
        )
    }
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var swipeAcc by remember { mutableFloatStateOf(0f) }
    var vinylSongPickOpen by remember { mutableStateOf(false) }
    var pickFocus by remember { mutableIntStateOf(state.index) }
    var settingsOpen by remember { mutableStateOf(false) }
    var lyricStyleOpen by remember { mutableStateOf(false) }
    var titleStyleOpen by remember { mutableStateOf(false) }
    var vinylColorOpen by remember { mutableStateOf(false) }
    var lyricSelectOpen by remember { mutableStateOf(false) }
    val lyricSelectSelected = remember { androidx.compose.runtime.mutableStateSetOf<Int>() }
    val chrome = remember {
        Animatable(
            if (displayPrefs.transportAlwaysVisible || System.getProperty("zmusic.test") == "true") 1f else 0f,
        )
    }
    LaunchedEffect(displayPrefs.transportAlwaysVisible, settingsOpen, vinylSongPickOpen, lyricStyleOpen, titleStyleOpen, vinylColorOpen, lyricSelectOpen) {
        if (displayPrefs.transportAlwaysVisible && !settingsOpen && !vinylSongPickOpen &&
            !lyricStyleOpen && !titleStyleOpen && !vinylColorOpen && !lyricSelectOpen
        ) {
            controlsVisible = true
        }
    }
    LaunchedEffect(controlsVisible, sliderDragging, track?.id, displayPrefs.transportAlwaysVisible, settingsOpen, vinylSongPickOpen, lyricStyleOpen, titleStyleOpen, vinylColorOpen, lyricSelectOpen) {
        if (!controlsVisible || sliderDragging || displayPrefs.transportAlwaysVisible) return@LaunchedEffect
        if (settingsOpen || vinylSongPickOpen || lyricStyleOpen || titleStyleOpen || vinylColorOpen || lyricSelectOpen) return@LaunchedEffect
        if (System.getProperty("zmusic.test") == "true") return@LaunchedEffect
        delay(3_500)
        controlsVisible = false
    }
    val overlayOpen = settingsOpen || vinylSongPickOpen || lyricStyleOpen || titleStyleOpen ||
        vinylColorOpen || lyricSelectOpen
    val showBar = (controlsVisible || sliderDragging || displayPrefs.transportAlwaysVisible) && !overlayOpen
    LaunchedEffect(showBar) {
        chrome.animateTo(
            if (showBar) 1f else 0f,
            tween(360, easing = FastOutSlowInEasing),
        )
    }
    val chromeT = chrome.value
    val density = LocalDensity.current
    val barSlidePx = with(density) { 52.dp.toPx() }
    val uiScale = displayPrefs.uiScale.coerceIn(PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX)
    val plate = displayPrefs.vinylPlateColors()
    val rain = remember { Animatable(if (displayPrefs.rainNightEnabled) 1f else 0f) }
    LaunchedEffect(displayPrefs.rainNightEnabled) {
        rain.animateTo(
            if (displayPrefs.rainNightEnabled) 1f else 0f,
            tween(640, easing = FastOutSlowInEasing),
        )
    }
    LaunchedEffect(state.index) {
        if (!vinylSongPickOpen) pickFocus = state.index
    }
    val settingsT = rememberSheetProgress(settingsOpen)
    val lyricStyleT = rememberSheetProgress(lyricStyleOpen)
    val titleStyleT = rememberSheetProgress(titleStyleOpen)
    val lyricSelectT = rememberSheetProgress(lyricSelectOpen, VinylCenterMs, VinylCenterEasing)
    val editorPanel = remember { Animatable(0f) }
    LaunchedEffect(vinylColorOpen) {
        if (vinylColorOpen) {
            delay(VinylCenterMs.toLong())
            if (vinylColorOpen) {
                editorPanel.animateTo(1f, tween(VinylCenterMs, easing = VinylCenterEasing))
            }
        } else {
            editorPanel.animateTo(0f, tween(VinylCenterMs, easing = VinylCenterEasing))
        }
    }
    val editorT = editorPanel.value
    val pickFog = remember { Animatable(0f) }
    var pickPhase by remember { mutableStateOf(VinylSongPickPhase.Entering) }
    var pickCoversMain by remember { mutableStateOf(false) }
    LaunchedEffect(vinylSongPickOpen, pickPhase) {
        if (!vinylSongPickOpen) {
            pickCoversMain = false
            pickFog.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
            pickPhase = VinylSongPickPhase.Entering
            return@LaunchedEffect
        }
        if (pickPhase == VinylSongPickPhase.Entering) return@LaunchedEffect
        pickFog.animateTo(1f, tween(380, easing = VinylCenterEasing))
    }
    val forceVinylYCentered = vinylColorOpen || vinylSongPickOpen || editorT > 0.001f
    val vinylAbsT by animateFloatAsState(
        targetValue = if (displayPrefs.vinylAbsoluteCenter || forceVinylYCentered) 1f else 0f,
        animationSpec = tween(VinylCenterMs, easing = VinylCenterEasing),
        label = "vinylAbsCenter",
    )
    val pickUiFade = (1f - pickFog.value).coerceIn(0f, 1f)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val chromeSidePad = ChromePad
        val innerW = (maxWidth - chromeSidePad * 2 - 12.dp).coerceAtLeast(1.dp)
        val vinylCol = innerW * LandscapeVinylWeight
        val lyricsCol = innerW * LandscapeLyricsWeight
        val vinylCenterX = chromeSidePad + vinylCol / 2 + displayPrefs.vinylOffsetXDp.dp
        val lyricsCenterX = chromeSidePad + vinylCol + 12.dp + lyricsCol / 2 + displayPrefs.lyricOffsetXDp.dp
        val screenCenterX = maxWidth / 2
        val discForTitle = landscapeVinylDiscDp(maxWidth) * displayPrefs.vinylSizeScale
        val titleMaxWidth = (discForTitle * 1.08f).coerceAtMost(maxWidth * 0.52f)
        val vinylVisualScale = displayPrefs.vinylSizeScale * maxOf(displayPrefs.vinylOuterScale, 1f)
        val vinylRightEdge = vinylCenterX + discForTitle * vinylVisualScale / 2f
        val lyricsColStart = chromeSidePad + vinylCol + 12.dp
        val vinylLeftInset = (vinylRightEdge + 10.dp - lyricsColStart).coerceAtLeast(0.dp)

        GeminiOrbsBackdrop(
            modifier = Modifier.fillMaxSize(),
            activeHalo = displayPrefs.activeHalo,
            playWhenReady = state.playWhenReady,
            positionMs = state.positionMs,
            scrubbing = sliderDragging,
            trackId = track?.id ?: 0L,
            loadPending = state.loadPending,
        )
        RainGlassAtmosphere(Modifier.fillMaxSize(), intensity = rain.value)
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp)
                .graphicsLayer {
                    scaleX = uiScale
                    scaleY = uiScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    clip = false
                },
        ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (!displayPrefs.transportAlwaysVisible) {
                            controlsVisible = !controlsVisible
                        }
                    })
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (swipeAcc > 96f) onBack()
                            swipeAcc = 0f
                        },
                        onDragCancel = { swipeAcc = 0f },
                        onVerticalDrag = { _, dy -> swipeAcc += dy },
                    )
                },
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = ChromePad, end = ChromePad, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoxWithConstraints(
                    Modifier
                        .weight(LandscapeVinylWeight)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    val discExpanded = landscapeVinylDiscDp(maxWidth) * displayPrefs.vinylSizeScale
                    val discCompact = (discExpanded * 0.86f).coerceAtLeast(118.dp)
                    val absT = vinylAbsT
                    val disc = lerpDp(lerpDp(discExpanded, discCompact, chromeT), discExpanded, absT)
                    val compactRatio = if (discExpanded.value > 0.1f) discCompact / discExpanded else 1f
                    val vinylScale = lerp(1f, lerp(1f, compactRatio, chromeT), absT)
                    val yOff = displayPrefs.vinylOffsetYDp.dp * (1f - absT)
                    if (track != null) {
                        VinylWithCoverArt(
                            track = track,
                            spinning = state.isPlaying && !state.buffering && !state.loadPending &&
                                !vinylSongPickOpen,
                            onSkipNext = onNext,
                            onSkipPrev = onPrev,
                            skipDir = state.skipDir,
                            skipSeq = state.skipSeq,
                            plate = plate,
                            outerScale = displayPrefs.vinylOuterScale,
                            centerRadiusFrac = displayPrefs.vinylCenterRadiusFrac,
                            fullCover = displayPrefs.vinylFullCover,
                            gestureDamping = displayPrefs.vinylGestureDamping,
                            onLongPress = if (displayPrefs.vinylSongPickEnabled) {
                                {
                                    pickFocus = state.index
                                    vinylSongPickOpen = true
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .size(disc)
                                .graphicsLayer {
                                    translationX = displayPrefs.vinylOffsetXDp.dp.toPx()
                                    translationY = yOff.toPx()
                                    scaleX = vinylScale
                                    scaleY = vinylScale
                                    transformOrigin = TransformOrigin.Center
                                    alpha = if (pickCoversMain) 0f else 1f
                                },
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                LandscapeProjectionLyrics(
                    lines = lines,
                    positionMs = state.positionMs,
                    trackDurationMs = state.durationMs,
                    wordByWord = wordByWord,
                    lineSpacingDp = displayPrefs.lyricLineSpacingDp,
                    playedCount = displayPrefs.lyricPlayedCount,
                    upcomingCount = displayPrefs.lyricUpcomingCount,
                    playingStyle = displayPrefs.lyricPlayingStyle,
                    playedStyle = displayPrefs.lyricPlayedStyle,
                    unplayedStyle = displayPrefs.lyricUnplayedStyle,
                    dynamicLyrics = displayPrefs.dynamicLyrics,
                    vinylLeftInset = vinylLeftInset,
                    offsetXDp = displayPrefs.lyricOffsetXDp,
                    onSeekToMs = { ms ->
                        onSeek(ms)
                        if (displayPrefs.lyricTapAutoPlay && !state.playWhenReady) onToggle()
                    },
                    onLongPressLine = { index ->
                        lyricSelectSelected.clear()
                        lyricSelectSelected.add(index)
                        lyricSelectOpen = true
                    },
                    modifier = Modifier
                        .weight(LandscapeLyricsWeight)
                        .fillMaxHeight()
                        .padding(end = 4.dp)
                        .graphicsLayer { alpha = pickUiFade },
                )
            }
        }
        if (track != null) {
            LandscapeAlignedSongMeta(
                track = track,
                sourceTitle = state.sourcePlaylistTitle,
                titleAlign = displayPrefs.titleAlign,
                songMetaTopPad = 18.dp,
                titleOffsetYDp = displayPrefs.titleOffsetYDp,
                titleNameColor = Color(displayPrefs.titleNameStyle.resolvedArgb(TitleLineStyle.DEFAULT_NAME_ARGB)),
                titleArtistColor = Color(displayPrefs.titleArtistStyle.resolvedArgb(TitleLineStyle.DEFAULT_ARTIST_ARGB)),
                titleSourceColor = Color(displayPrefs.titleSourceStyle.resolvedArgb(TitleLineStyle.DEFAULT_SOURCE_ARGB)),
                titleNameFontScale = displayPrefs.titleNameStyle.fontScale,
                titleArtistFontScale = displayPrefs.titleArtistStyle.fontScale,
                titleSourceFontScale = displayPrefs.titleSourceStyle.fontScale,
                chromeSidePad = chromeSidePad,
                vinylCenterX = vinylCenterX,
                lyricsCenterX = lyricsCenterX,
                screenCenterX = screenCenterX,
                titleMaxWidth = titleMaxWidth,
                contentAlpha = pickUiFade,
                onRevealControls = { controlsVisible = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .zIndex(30f),
            )
        }
        }
        if (showBar || chromeT > 0.001f) {
        val transportShape = if (displayPrefs.transportDocked) {
            RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        } else {
            RoundedCornerShape(14.dp)
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(40f)
                .graphicsLayer {
                    translationY = (1f - chromeT) * barSlidePx
                    scaleX = uiScale
                    scaleY = uiScale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .alpha(chromeT)
                .padding(
                    start = chromeSidePad,
                    end = chromeSidePad,
                    bottom = if (displayPrefs.transportDocked) 0.dp else displayPrefs.transportBottomInsetDp.dp,
                )
                .clip(transportShape)
                .background(Color.Black.copy(alpha = 0.22f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            LandscapeTransportBar(
                state = state,
                sliderDragging = sliderDragging,
                sliderValue = sliderValue,
                onSliderDragStart = {
                    sliderDragging = true
                    sliderValue = state.positionMs.toFloat()
                    controlsVisible = true
                },
                onSliderChange = { sliderValue = it },
                onSliderDragEnd = {
                    sliderDragging = false
                    onSeek(sliderValue.toLong())
                    controlsVisible = true
                },
                onToggle = {
                    controlsVisible = true
                    onToggle()
                },
                onMode = {
                    controlsVisible = true
                    onMode()
                },
                onNext = {
                    controlsVisible = true
                    onNext()
                },
                onPrev = {
                    controlsVisible = true
                    onPrev()
                },
                onToggleLike = {
                    controlsVisible = true
                    onToggleLike()
                },
            )
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = chromeSidePad, top = 18.dp)
                .zIndex(50f)
                .graphicsLayer {
                    scaleX = uiScale
                    scaleY = uiScale
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .alpha(chromeT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NowPlayingChromeIconGap),
        ) {
            NowPlayingDismissIconButton(
                onClick = {
                    if (chromeT <= 0.2f) return@NowPlayingDismissIconButton
                    onBack()
                },
            )
            NowPlayingSettingsIconButton(
                onClick = {
                    if (chromeT <= 0.2f) return@NowPlayingSettingsIconButton
                    controlsVisible = false
                    settingsOpen = true
                },
            )
        }
        }
        if (settingsT > 0.001f || settingsOpen) {
            LandscapePlayerSettingsOverlay(
                prefs = displayPrefs,
                onPrefsChange = onDisplayPrefsChange,
                onDismiss = { settingsOpen = false },
                onOpenLyricStyleEditor = {
                    settingsOpen = false
                    lyricStyleOpen = true
                },
                onOpenTitleStyleEditor = {
                    settingsOpen = false
                    titleStyleOpen = true
                },
                onOpenVinylColorEditor = {
                    settingsOpen = false
                    vinylColorOpen = true
                },
                progress = settingsT,
            )
        }
        if (lyricStyleT > 0.001f || lyricStyleOpen) {
            LyricStyleEditorOverlay(
                prefs = displayPrefs,
                onPrefsChange = onDisplayPrefsChange,
                onDismiss = { lyricStyleOpen = false },
                onBackToSettings = {
                    lyricStyleOpen = false
                    settingsOpen = true
                },
                progress = lyricStyleT,
            )
        }
        if (titleStyleT > 0.001f || titleStyleOpen) {
            TitleStyleEditorOverlay(
                prefs = displayPrefs,
                onPrefsChange = onDisplayPrefsChange,
                onDismiss = { titleStyleOpen = false },
                onBackToSettings = {
                    titleStyleOpen = false
                    settingsOpen = true
                },
                progress = titleStyleT,
            )
        }
        if (editorT > 0.001f || vinylColorOpen) {
            VinylColorEditorOverlay(
                prefs = displayPrefs,
                onPrefsChange = onDisplayPrefsChange,
                onDismiss = { vinylColorOpen = false },
                onBackToSettings = {
                    vinylColorOpen = false
                    settingsOpen = true
                },
                progress = editorT,
            )
        }
        if (vinylSongPickOpen) {
            VinylSongPickOverlay(
                queue = state.queue,
                queueIndex = state.index,
                focusedIndex = pickFocus,
                onFocusedIndexChange = { pickFocus = it },
                discSize = landscapeVinylDiscDp(maxWidth) * displayPrefs.vinylSizeScale,
                plate = plate,
                fullCover = displayPrefs.vinylFullCover,
                centerRadiusFrac = displayPrefs.vinylCenterRadiusFrac,
                outerScale = displayPrefs.vinylOuterScale,
                titleNameStyle = displayPrefs.titleNameStyle,
                fogProgress = pickFog.value,
                onConfirm = { idx -> onPlayAt(idx) },
                onDismiss = { vinylSongPickOpen = false },
                onCoverMainChange = { pickCoversMain = it },
                onPhaseChange = { pickPhase = it },
            )
        }
        if (lyricSelectT > 0.001f || lyricSelectOpen) {
            LyricSelectOverlay(
                lines = lines,
                selected = lyricSelectSelected,
                onDismiss = {
                    lyricSelectOpen = false
                    lyricSelectSelected.clear()
                },
                progress = lyricSelectT,
            )
        }
    }
}

@Composable
private fun LandscapeTransportBar(
    state: PlaybackUiState,
    sliderDragging: Boolean,
    sliderValue: Float,
    onSliderDragStart: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderDragEnd: () -> Unit,
    onToggle: () -> Unit,
    onMode: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dur = state.durationMs.toFloat().coerceAtLeast(1f)
    val pos = if (sliderDragging) sliderValue else state.positionMs.toFloat()
    val displayPos = if (sliderDragging) sliderValue.toLong() else state.positionMs
    val playPulse by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (state.playWhenReady) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f),
        label = "playPulse",
    )
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ModeIcon(state.playbackMode, onMode)
        TransportHit(ZIcons.SkipPrevious, "上一首", onPrev)
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = playPulse
                    scaleY = playPulse
                }
                .size(36.dp)
                .clip(CircleShape)
                .background(PlayerPlayFill)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.playWhenReady) ZIcons.Pause else ZIcons.Play,
                contentDescription = if (state.playWhenReady) "暂停" else "播放",
                tint = PlayerPlayIcon,
                modifier = Modifier.size(16.dp),
            )
        }
        TransportHit(ZIcons.SkipNext, "下一首", onNext)
        TransportHit(
            if (state.trackLiked) ZIcons.Favorite else ZIcons.FavoriteBorder,
            if (state.trackLiked) "取消喜欢" else "喜欢",
            onToggleLike,
            tint = if (state.trackLiked) Color(0xFFEC4141) else LyricCurrent,
        )
        Text(
            formatMs(displayPos),
            style = TextStyle(
                color = LyricCurrent.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 0.3.sp,
            ),
            modifier = Modifier.widthIn(min = 36.dp),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Slider(
            value = pos.coerceIn(0f, dur),
            onValueChange = {
                if (!sliderDragging) onSliderDragStart()
                onSliderChange(it)
            },
            onValueChangeFinished = onSliderDragEnd,
            valueRange = 0f..dur,
            modifier = Modifier.weight(1f).height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f),
            ),
        )
        Text(
            formatMs(state.durationMs),
            style = TextStyle(
                color = LyricCurrent.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 0.3.sp,
            ),
            modifier = Modifier.widthIn(min = 36.dp),
            textAlign = TextAlign.Start,
            maxLines = 1,
        )
    }
}

@Composable
private fun TransportHit(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = LyricCurrent,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ModeIcon(mode: PlaybackMode, onClick: () -> Unit) {
    val icon = when (mode) {
        PlaybackMode.ORDER -> ZIcons.Repeat
        PlaybackMode.REPEAT_ONE -> ZIcons.RepeatOne
        PlaybackMode.SHUFFLE -> ZIcons.Shuffle
    }
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = "播放模式", tint = LyricCurrent, modifier = Modifier.size(16.dp))
    }
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
