package com.kite.zmusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.main.LandscapeLyricsWeight
import com.kite.zmusic.ui.main.LandscapeVinylWeight
import com.kite.zmusic.ui.main.landscapeVinylDiscDp
import kotlin.math.roundToInt
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
    activeHalo: Boolean = true,
    onHaloChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack
    var showTranslated by remember { mutableStateOf(false) }
    val lines = when {
        showTranslated && state.translatedLyricLines.isNotEmpty() -> state.translatedLyricLines
        wordByWord && state.wordLyricLines.isNotEmpty() -> state.wordLyricLines
        else -> state.lyricLines
    }
    val focus = lines.indexOfLast { it.timeMs <= state.positionMs }.coerceAtLeast(0)
    var controlsVisible by remember { mutableStateOf(true) }
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var swipeAcc by remember { mutableFloatStateOf(0f) }
    var queueOpen by remember { mutableStateOf(false) }
    var projection by remember { mutableStateOf(false) }
    val chrome = remember { Animatable(1f) }
    LaunchedEffect(controlsVisible) {
        chrome.animateTo(
            if (controlsVisible) 1f else 0f,
            tween(360, easing = FastOutSlowInEasing),
        )
    }
    LaunchedEffect(controlsVisible, sliderDragging, track?.id) {
        if (!controlsVisible || sliderDragging) return@LaunchedEffect
        if (System.getProperty("zmusic.test") == "true") return@LaunchedEffect
        delay(3_500)
        controlsVisible = false
    }
    val chromeT = chrome.value
    val density = LocalDensity.current
    val barSlidePx = with(density) { 52.dp.toPx() }

    Box(modifier.fillMaxSize()) {
        GeminiOrbsBackdrop(
            modifier = Modifier.fillMaxSize(),
            activeHalo = activeHalo,
            playWhenReady = state.playWhenReady,
            positionMs = state.positionMs,
            scrubbing = sliderDragging,
            trackId = track?.id ?: 0L,
            loadPending = state.loadPending,
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                }
                .pointerInput(projection) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (swipeAcc > 96f) {
                                if (projection) projection = false else onBack()
                            }
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
                if (!projection) {
                    BoxWithConstraints(
                        Modifier
                            .weight(LandscapeVinylWeight)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val discExpanded = landscapeVinylDiscDp(maxWidth)
                        val discCompact = (discExpanded * 0.86f).coerceAtLeast(118.dp)
                        val disc = lerpDp(discExpanded, discCompact, chromeT)
                        if (track != null) {
                            VinylWithCoverArt(
                                track = track,
                                spinning = state.isPlaying && !state.buffering && !state.loadPending,
                                onSkipNext = onNext,
                                onSkipPrev = onPrev,
                                skipDir = state.skipDir,
                                skipSeq = state.skipSeq,
                                modifier = Modifier.size(disc),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
                LandscapePlayerLyrics(
                    lines = lines,
                    focus = focus,
                    positionMs = state.positionMs,
                    wordByWord = wordByWord && !showTranslated,
                    projection = projection,
                    onSeekLine = { onSeek(it) },
                    modifier = Modifier
                        .weight(if (projection) 1f else LandscapeLyricsWeight)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                )
            }
        }
        if (track != null && !projection) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(LandscapeVinylWeight)
                    .padding(start = ChromePad, top = 18.dp, end = 8.dp)
                    .zIndex(30f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    track.name,
                    style = TextStyle(
                        color = LyricCurrent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    track.artists,
                    style = TextStyle(color = LyricDim, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                val source = state.sourcePlaylistTitle
                if (!source.isNullOrBlank()) {
                    Text(
                        source,
                        style = TextStyle(color = LyricDim.copy(alpha = 0.8f), fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = ChromePad, top = 18.dp)
                .zIndex(50f)
                .alpha(chromeT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.translatedLyricLines.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Subtitles,
                    contentDescription = if (showTranslated) "原文歌词" else "翻译歌词",
                    tint = if (showTranslated) AccentRose else LyricCurrent,
                    modifier = Modifier.size(26.dp).clickable(
                        enabled = chromeT > 0.2f,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            controlsVisible = true
                            showTranslated = !showTranslated
                        },
                    ),
                )
            }
            Icon(
                Icons.Outlined.OpenInFull,
                contentDescription = if (projection) "退出投影歌词" else "投影歌词",
                tint = if (projection) AccentRose else LyricCurrent,
                modifier = Modifier.size(26.dp).clickable(
                    enabled = chromeT > 0.2f,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        controlsVisible = true
                        projection = !projection
                    },
                ),
            )
            Icon(
                Icons.Outlined.BlurOn,
                contentDescription = if (activeHalo) "关闭动态光晕" else "开启动态光晕",
                tint = if (activeHalo) AccentRose else LyricDim,
                modifier = Modifier.size(26.dp).clickable(
                    enabled = chromeT > 0.2f,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        controlsVisible = true
                        onHaloChange(!activeHalo)
                    },
                ),
            )
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = LyricCurrent,
                modifier = Modifier.size(28.dp).clickable(
                    enabled = chromeT > 0.2f,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (projection) projection = false else onBack()
                    },
                ),
            )
        }
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
            onOpenQueue = {
                controlsVisible = true
                queueOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(40f)
                .graphicsLayer {
                    translationY = (1f - chromeT) * barSlidePx
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .alpha(chromeT)
                .padding(start = ChromePad, end = ChromePad, bottom = 16.dp),
        )
        AnimatedVisibility(
            visible = queueOpen,
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 3 },
            exit = fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { it / 4 },
        ) {
            QueuePickOverlay(
                state = state,
                onPlayAt = {
                    onPlayAt(it)
                    queueOpen = false
                },
                onDismiss = { queueOpen = false },
            )
        }
    }
}

@Composable
private fun LandscapePlayerLyrics(
    lines: List<LrcLine>,
    focus: Int,
    positionMs: Long,
    wordByWord: Boolean,
    projection: Boolean = false,
    onSeekLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var browsing by remember { mutableStateOf(false) }
    val scrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    LaunchedEffect(scrolling) {
        if (scrolling) browsing = true
    }
    BoxWithConstraints(modifier) {
        val centerOffset = with(density) { -(maxHeight / 2).roundToPx() + 24 }
        LaunchedEffect(focus, lines.size, maxHeight, browsing) {
            if (lines.isEmpty() || browsing) return@LaunchedEffect
            runCatching { listState.animateScrollToItem(focus, centerOffset) }
        }
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无歌词", color = LyricDim, fontSize = if (projection) 22.sp else 15.sp)
            }
            return@BoxWithConstraints
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = maxHeight / 2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(lines, key = { i, l -> "${l.timeMs}-$i" }) { i, line ->
                val on = i == focus
                val played = i < focus
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                browsing = false
                                onSeekLine(line.timeMs)
                            },
                        )
                        .padding(vertical = if (projection) 14.dp else 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (wordByWord && on && line.words.isNotEmpty()) {
                        WordLine(line, positionMs)
                    } else {
                        Text(
                            line.text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                color = when {
                                    on -> LyricCurrent
                                    played -> LyricDim.copy(alpha = 0.72f)
                                    else -> LyricDim.copy(alpha = 0.48f)
                                },
                                fontSize = when {
                                    projection && on -> 32.sp
                                    projection -> 22.sp
                                    on -> 22.sp
                                    else -> 16.sp
                                },
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                lineHeight = if (projection) 40.sp else 28.sp,
                            ),
                        )
                    }
                }
            }
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
    onOpenQueue: () -> Unit,
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
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ModeIcon(state.playbackMode, onMode)
        TransportHit(
            if (state.trackLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (state.trackLiked) "取消喜欢" else "喜欢",
            onToggleLike,
            tint = if (state.trackLiked) Color(0xFFEC4141) else LyricCurrent,
        )
        TransportHit(Icons.Filled.SkipPrevious, "上一首", onPrev)
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
                if (state.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.playWhenReady) "暂停" else "播放",
                tint = PlayerPlayIcon,
                modifier = Modifier.size(16.dp),
            )
        }
        TransportHit(Icons.Filled.SkipNext, "下一首", onNext)
        TransportHit(Icons.AutoMirrored.Outlined.QueueMusic, "播放队列", onOpenQueue)
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
private fun QueuePickOverlay(
    state: PlaybackUiState,
    onPlayAt: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .zIndex(80f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.38f)
                .background(Color(0xE6111218))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text(
                "播放队列  ${state.queue.size}",
                color = LyricCurrent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f).heightIn(max = 520.dp)) {
                itemsIndexed(state.queue, key = { _, t -> t.id }) { i, t ->
                    val on = i == state.index
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onPlayAt(i) },
                            )
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Text(
                            t.name,
                            color = if (on) AccentRose else LyricCurrent,
                            fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            t.artists,
                            color = LyricDim,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeIcon(mode: PlaybackMode, onClick: () -> Unit) {
    val icon = when (mode) {
        PlaybackMode.ORDER -> Icons.Outlined.Repeat
        PlaybackMode.REPEAT_ONE -> Icons.Outlined.Repeat
        PlaybackMode.SHUFFLE -> Icons.Outlined.Shuffle
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = "播放模式", tint = LyricCurrent, modifier = Modifier.size(16.dp))
            if (mode == PlaybackMode.REPEAT_ONE) {
                Text("1", color = Color(0xFFEC4141), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WordLine(line: LrcLine, positionMs: Long) {
    val active = line.words.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    BasicText(
        text = buildAnnotatedString {
            line.words.forEachIndexed { i, w ->
                pushStyle(
                    SpanStyle(
                        color = if (i <= active) LyricCurrent else LyricDim,
                        fontSize = if (i == active) 22.sp else 18.sp,
                        fontWeight = if (i == active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
                append(w.text)
                pop()
            }
        },
    )
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
