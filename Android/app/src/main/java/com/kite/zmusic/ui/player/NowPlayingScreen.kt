package com.kite.zmusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.ui.common.UrlImage
import kotlin.math.abs

private val MistTop = Color(0xFF120A18)
private val MistMid = Color(0xFF1C1428)
private val MistBottom = Color(0xFF0A1420)
private val LyricCurrent = Color(0xFFF2EDE6)
private val LyricDim = Color(0xFF7A8899)
private val AccentRose = Color(0xFFE8B4BC)
private val CyanSoft = Color(0xFF6FD4D4)

fun lyricActiveIndex(lines: List<LrcLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var l = 0
    var r = lines.lastIndex
    var ans = -1
    while (l <= r) {
        val m = (l + r) ushr 1
        if (lines[m].timeMs <= positionMs) {
            ans = m
            l = m + 1
        } else {
            r = m - 1
        }
    }
    return ans
}

private fun formatTimeMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}

@Composable
fun NowPlayingScreen(
    state: PlaybackUiState,
    isLandscape: Boolean,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onOpenSourcePlaylist: (() -> Unit)? = null,
    /** 横屏时左侧 Dock 占位，避免封面与控件被遮挡 */
    landscapeStartInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    var portraitLyricsOpen by rememberSaveable { mutableStateOf(false) }
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    // 竖屏：优先关闭“歌词预览”，再次返回才退出全屏播放器
    BackHandler(enabled = !isLandscape && portraitLyricsOpen) {
        portraitLyricsOpen = false
    }

    LaunchedEffect(track.id) {
        portraitLyricsOpen = false
        sliderDragging = false
    }

    val duration = state.durationMs.coerceAtLeast(1L)
    val displayPos = if (sliderDragging) sliderValue.toLong() else state.positionMs

    val bg = Brush.verticalGradient(
        colors = listOf(MistTop, MistMid, MistBottom),
    )

    val srcIx = remember { MutableInteractionSource() }
    val openSourcePlaylist = onOpenSourcePlaylist
    // 全局“下滑退出全屏播放器”：不占布局空间，仅监听手势
    val dismissSwipeThresholdPx = with(LocalDensity.current) {
        (if (isLandscape) 92.dp else 112.dp).toPx()
    }
    Box(
        modifier
            .fillMaxSize()
            .background(bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    start = landscapeStartInset + if (isLandscape) 8.dp else 12.dp,
                    end = if (isLandscape) 10.dp else 12.dp,
                    top = if (isLandscape) 4.dp else 6.dp,
                    bottom = if (isLandscape) 4.dp else 8.dp,
                ),
        ) {
            val srcTitle = state.sourcePlaylistTitle
            if (isLandscape && !srcTitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "SOURCE · $srcTitle",
                    style = TextStyle(
                        color = if (openSourcePlaylist != null) {
                            AccentRose.copy(alpha = 0.5f)
                        } else {
                            LyricDim.copy(alpha = 0.48f)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        letterSpacing = 0.65.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (openSourcePlaylist != null) {
                        Modifier.clickable(
                            interactionSource = srcIx,
                            indication = null,
                            onClick = openSourcePlaylist,
                        )
                    } else {
                        Modifier
                    },
                )
                Spacer(Modifier.height(4.dp))
            } else if (isLandscape) {
                Spacer(Modifier.height(4.dp))
            }

            state.error?.let { err ->
                Text(
                    text = err,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    style = TextStyle(
                        color = Color(0xFFFFB86C),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp,
                    ),
                )
            }

            if (isLandscape) {
                LandscapePlayerBody(
                    track = track,
                    lines = state.lyricLines,
                    positionMs = displayPos,
                    isPlaying = state.isPlaying,
                    buffering = state.buffering,
                    onTogglePlay = onTogglePlay,
                    onSkipNext = onSkipNext,
                    onSkipPrev = onSkipPrev,
                    playbackMode = state.playbackMode,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    durationMs = duration,
                    queueIndex = state.index,
                    queueSize = state.queue.size,
                    sliderDragging = sliderDragging,
                    sliderValue = sliderValue,
                    onSliderDragStart = {
                        sliderDragging = true
                        sliderValue = state.positionMs.toFloat()
                    },
                    onSliderChange = { sliderValue = it },
                    onSliderDragEnd = {
                        sliderDragging = false
                        onSeek(sliderValue.toLong().coerceIn(0L, state.durationMs))
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                PortraitPlayerBody(
                    track = track,
                    lines = state.lyricLines,
                    positionMs = displayPos,
                    lyricsExpanded = portraitLyricsOpen,
                    onOpenLyrics = { portraitLyricsOpen = true },
                    onCollapseLyrics = { portraitLyricsOpen = false },
                    sourceTitle = srcTitle,
                    onSourceClick = openSourcePlaylist,
                    isPlaying = state.isPlaying,
                    buffering = state.buffering,
                    onTogglePlay = onTogglePlay,
                    onSkipNext = onSkipNext,
                    onSkipPrev = onSkipPrev,
                    playbackMode = state.playbackMode,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    durationMs = duration,
                    sliderDragging = sliderDragging,
                    sliderValue = sliderValue,
                    onSliderDragStart = {
                        sliderDragging = true
                        sliderValue = state.positionMs.toFloat()
                    },
                    onSliderChange = { sliderValue = it },
                    onSliderDragEnd = {
                        sliderDragging = false
                        onSeek(sliderValue.toLong().coerceIn(0L, state.durationMs))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 顶部 HUD 叠层（不占布局空间）
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = if (isLandscape) 6.dp else 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (state.buffering) "···" else "NOW PLAYING",
                style = TextStyle(
                    color = LyricDim.copy(alpha = 0.3f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.sp,
                    letterSpacing = if (isLandscape) 1.6.sp else 2.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }

        // 全局“下滑退出”：覆盖整个全屏区域，不占布局空间
        Box(
            Modifier
                .matchParentSize()
                // 让全局手势层落在交互组件后面，避免拦截点击/滑动。
                .zIndex(-1f)
                .pointerInput(onDismiss, dismissSwipeThresholdPx) {
                    awaitEachGesture {
                        // 只在没有被其它手势消费时参与，避免阻断点击/滑动/进度条。
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val pointerId = down.id
                        var accDy = 0f

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.find { it.id == pointerId } ?: break
                            val dy = change.positionChange().y
                            if (dy > 0) accDy += dy

                            if (accDy > dismissSwipeThresholdPx) {
                                change.consume()
                                onDismiss()
                                break
                            }

                            if (!change.pressed) break
                        }
                    }
                }
        )
    }
}

@Composable
private fun AnimatedCoverArt(
    track: TrackRow,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val pulse = rememberInfiniteTransition(label = "coverPulse")
    val breath by pulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val drift by pulse.animateFloat(
        initialValue = -0.8f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val halo by pulse.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.52f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentRose.copy(alpha = halo * 0.35f),
                        CyanSoft.copy(alpha = halo * 0.12f),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = r * 1.35f,
                ),
                radius = r * 1.2f,
                center = c,
            )
        }
        val cover = track.coverUrl
        if (!cover.isNullOrBlank()) {
            UrlImage(
                url = cover,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(0.92f)
                    .graphicsLayer {
                        scaleX = breath * 1.08f
                        scaleY = breath * 1.08f
                        alpha = 0.28f
                        rotationZ = drift
                    }
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .shadow(28.dp, RoundedCornerShape(22.dp), ambientColor = AccentRose.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0A0E14))
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                    rotationZ = drift * 0.35f
                }
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            Crossfade(
                targetState = track.id,
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "coverMain",
            ) { targetId ->
                // 显式使用 targetId，避免 IDE 报 “target state parameter is not used”
                if (targetId == Long.MIN_VALUE) Unit
                val u = track.coverUrl
                if (!u.isNullOrBlank()) {
                    UrlImage(
                        url = u,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "♪",
                            style = TextStyle(
                                color = LyricDim.copy(alpha = 0.4f),
                                fontSize = 48.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 竖屏展开：根据可用高度尽可能多展示歌词，并保持垂直居中 */
@Composable
private fun PortraitCinemaLyrics(
    lines: List<LrcLine>,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val active = lyricActiveIndex(lines, positionMs)
    if (lines.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无逐行歌词",
                style = TextStyle(
                    color = LyricDim.copy(alpha = 0.38f),
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        return
    }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        // 近似每条歌词的“稳定高度”，用于计算能放下多少行（避免只显示少量行）
        val activeHeightPx =
            with(density) { 26.sp.toPx() } + with(density) { (10.dp * 2).toPx() }
        val sideHeightPx =
            with(density) { 19.sp.toPx() } + with(density) { (6.dp * 2).toPx() }

        // 使用 density 将可用高度换算成 px，供计算歌词行数使用
        val availableHeightPx = with(density) { maxHeight.toPx() }
        val rawSideCount =
            if (availableHeightPx <= activeHeightPx + 1f) 0
            else ((availableHeightPx - activeHeightPx) / (sideHeightPx * 2f)).toInt()

        val sideCount = rawSideCount.coerceIn(0, 10)

        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (offset in -sideCount..sideCount) {
                val i = active + offset
                if (offset == 0) {
                    AnimatedContent(
                        targetState = active,
                        transitionSpec = {
                            (
                                slideInVertically { h -> h / 8 } +
                                    fadeIn(tween(360, easing = FastOutSlowInEasing))
                                ).togetherWith(
                                slideOutVertically { h -> -h / 12 } +
                                    fadeOut(tween(280, easing = FastOutSlowInEasing)),
                            )
                        },
                        label = "portraitCinemaCur",
                    ) {
                        val t = lines.getOrNull(it)?.text ?: "· · ·"
                        Text(
                            text = t,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = LyricCurrent,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                lineHeight = 26.sp,
                                letterSpacing = 0.3.sp,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                } else {
                    val t = lines.getOrNull(i)?.text
                    if (t != null) {
                        Text(
                            text = t,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = LyricDim.copy(
                                    alpha = (0.4f - abs(offset) * 0.1f).coerceIn(0.18f, 0.4f),
                                ),
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                        )
                    } else {
                        Spacer(
                            Modifier.height(
                                with(density) { (sideHeightPx).toDp() },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitPlayerBody(
    track: TrackRow,
    lines: List<LrcLine>,
    positionMs: Long,
    lyricsExpanded: Boolean,
    onOpenLyrics: () -> Unit,
    onCollapseLyrics: () -> Unit,
    sourceTitle: String?,
    onSourceClick: (() -> Unit)?,
    isPlaying: Boolean,
    buffering: Boolean,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    playbackMode: PlaybackMode,
    onCyclePlaybackMode: () -> Unit,
    durationMs: Long,
    sliderDragging: Boolean,
    sliderValue: Float,
    onSliderDragStart: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idx = lyricActiveIndex(lines, positionMs)
    val srcIx = remember { MutableInteractionSource() }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            targetState = lyricsExpanded,
            transitionSpec = {
                fadeIn(tween(280)) togetherWith fadeOut(tween(200))
            },
            label = "portraitLyricMode",
        ) { expanded ->
            if (!expanded) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = track.name,
                        style = TextStyle(
                            color = LyricCurrent,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 19.sp,
                            letterSpacing = 0.2.sp,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = track.artists,
                        style = TextStyle(
                            color = AccentRose.copy(alpha = 0.84f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!sourceTitle.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "SOURCE · $sourceTitle",
                            style = TextStyle(
                                color = if (onSourceClick != null) {
                                    AccentRose.copy(alpha = 0.52f)
                                } else {
                                    LyricDim.copy(alpha = 0.48f)
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                letterSpacing = 0.65.sp,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (onSourceClick != null) {
                                Modifier.clickable(
                                    interactionSource = srcIx,
                                    indication = null,
                                    onClick = onSourceClick,
                                )
                            } else {
                                Modifier
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .weight(0.54f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val w = maxWidth
                    val side = w.coerceAtMost(312.dp).coerceAtLeast(200.dp)
                            Box(
                                Modifier
                                    .size(side)
                                    .align(Alignment.Center),
                            ) {
                                AnimatedCoverArt(track = track, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    LyricPreviewBlock(
                        lines = lines,
                        activeIndex = idx,
                        onOpenFull = onOpenLyrics,
                        // 折叠态预览高度加倍：显示更多歌词行
                        lineCount = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 内部底部留白别太大；真正的“远离进度条”由外部 Spacer 控制
                            .padding(top = 4.dp, bottom = 12.dp),
                    )
                    // 竖屏折叠态：LyricPreviewBlock 是最后一个内容，容易贴底；这里显式留出外部间距
                    // 竖屏折叠态：把歌词预览再向上推一点，拉开与底部进度条的距离
                    Spacer(Modifier.height(92.dp))
                }
            } else {
                val outerIx = remember { MutableInteractionSource() }

                // 竖屏：点击“歌词外部”退出歌词预览；点击歌词区域不退出
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                ) {
                    // 背景拦截：只要没被歌词内容覆盖，就能关闭
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = outerIx,
                                indication = null,
                                onClick = onCollapseLyrics,
                            ),
                    )

                    Column(
                        Modifier
                            .fillMaxWidth(),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 8.dp)
                                .clickable(
                                    interactionSource = outerIx,
                                    indication = null,
                                    onClick = onCollapseLyrics,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .width(44.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(LyricDim.copy(alpha = 0.28f)),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = track.name,
                                style = TextStyle(
                                    color = LyricDim.copy(alpha = 0.45f),
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "点击返回封面",
                                style = TextStyle(
                                    color = LyricDim.copy(alpha = 0.28f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.sp,
                                    letterSpacing = 0.5.sp,
                                ),
                            )
                        }

                        // 注意：PortraitCinemaLyrics 已不再 fillMaxSize，因此“歌词外部”区域会露出来命中上面的 scrim
                        PortraitCinemaLyrics(
                            lines = lines,
                            positionMs = positionMs,
                            modifier = Modifier
                                .fillMaxWidth()
                                // 保留一条底部触控空隙，避免完全覆盖“点击外部退出”区域
                                .fillMaxHeight()
                                .padding(bottom = 20.dp),
                        )
                    }
                }
            }
        }

        PlayerTransport(
            isPlaying = isPlaying,
            buffering = buffering,
            onTogglePlay = onTogglePlay,
            onSkipNext = onSkipNext,
            onSkipPrev = onSkipPrev,
            durationMs = durationMs,
            positionMs = positionMs,
            sliderDragging = sliderDragging,
            sliderValue = sliderValue,
            onSliderDragStart = onSliderDragStart,
            onSliderChange = onSliderChange,
            onSliderDragEnd = onSliderDragEnd,
            playbackMode = playbackMode,
            onCyclePlaybackMode = onCyclePlaybackMode,
            portraitSlim = true,
            landscapeDense = false,
        )
    }
}

/** 横屏：当前 ±3 共 7 行，中心句漂浮切换 */
@Composable
private fun LandscapeProjectionLyrics(
    lines: List<LrcLine>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                style = TextStyle(
                    color = LyricDim.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        return
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(end = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (offset in -3..3) {
            val i = activeIndex + offset
            when {
                offset == 0 -> {
                    AnimatedContent(
                        targetState = activeIndex,
                        transitionSpec = {
                            (
                                slideInVertically { h -> h / 6 } +
                                    fadeIn(tween(380, easing = FastOutSlowInEasing))
                            ).togetherWith(
                                slideOutVertically { h -> -h / 8 } +
                                    fadeOut(tween(300, easing = FastOutSlowInEasing)),
                            )
                        },
                        label = "landProjCur",
                    ) { act ->
                        val t = lines.getOrNull(act)?.text ?: "· · ·"
                        Text(
                            text = t,
                            style = TextStyle(
                                color = LyricCurrent,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                // 横屏歌词行间距：加大更“舒展”，避免挤成一条
                                lineHeight = 26.sp,
                                letterSpacing = 0.25.sp,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        )
                    }
                }
                i in lines.indices -> {
                    Text(
                        text = lines[i].text,
                        style = TextStyle(
                            color = LyricDim.copy(
                                alpha = (0.52f - abs(offset) * 0.065f).coerceIn(0.22f, 0.52f),
                            ),
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 11.5.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                else -> Spacer(Modifier.height(22.dp))
            }
        }
    }
}

@Composable
private fun LandscapePlayerBody(
    track: TrackRow,
    lines: List<LrcLine>,
    positionMs: Long,
    isPlaying: Boolean,
    buffering: Boolean,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    durationMs: Long,
    queueIndex: Int,
    queueSize: Int,
    sliderDragging: Boolean,
    sliderValue: Float,
    onSliderDragStart: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderDragEnd: () -> Unit,
    playbackMode: PlaybackMode,
    onCyclePlaybackMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeLyric = lyricActiveIndex(lines, positionMs)
    val density = LocalDensity.current.density
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(0.44f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        // 向中线凹陷：翻转旋转方向，让“右边靠近中线”更突出
                        rotationY = 12f
                        transformOrigin = TransformOrigin(1f, 0.5f)
                        cameraDistance = 10.5f * density
                        scaleX = 0.97f
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                BoxWithConstraints {
                    val h = maxHeight
                    val w = maxWidth
                    val side = (h - 16.dp)
                        .coerceAtMost(w - 4.dp)
                        .coerceAtMost(252.dp)
                        .coerceAtLeast(128.dp)
                    // 封面向右靠近一些（更激进），避免“太靠左”
                    Box(
                        Modifier
                            .size(side)
                            .align(Alignment.CenterEnd)
                            .offset(x = 6.dp),
                    ) {
                        AnimatedCoverArt(
                            track = track,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Spacer(Modifier.width(0.dp))
            Column(
                Modifier
                    .weight(0.56f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        // 与封面层相对反向旋转，形成凹陷而不是鼓出
                        rotationY = -12f
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = 10.5f * density
                        scaleX = 0.97f
                    }
                    .padding(start = 0.dp),
            ) {
                Text(
                    text = track.name,
                    style = TextStyle(
                        color = LyricCurrent,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.18.sp,
                        lineHeight = 17.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artists,
                    style = TextStyle(
                        color = AccentRose.copy(alpha = 0.82f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (queueSize > 0 && queueIndex >= 0) {
                    Text(
                        text = "QUEUE · %02d / %02d".format(queueIndex + 1, queueSize),
                        style = TextStyle(
                            color = LyricDim.copy(alpha = 0.4f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 6.5.sp,
                            letterSpacing = 0.7.sp,
                        ),
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                LandscapeProjectionLyrics(
                    lines = lines,
                    activeIndex = activeLyric,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // 给底部传输控件留出触控安全距离，避免点击歌词时误触按钮/进度条
                        .padding(bottom = 10.dp),
                )
            }
        }
        PlayerTransport(
            isPlaying = isPlaying,
            buffering = buffering,
            onTogglePlay = onTogglePlay,
            onSkipNext = onSkipNext,
            onSkipPrev = onSkipPrev,
            durationMs = durationMs,
            positionMs = positionMs,
            sliderDragging = sliderDragging,
            sliderValue = sliderValue,
            onSliderDragStart = onSliderDragStart,
            onSliderChange = onSliderChange,
            onSliderDragEnd = onSliderDragEnd,
            playbackMode = playbackMode,
            onCyclePlaybackMode = onCyclePlaybackMode,
            portraitSlim = false,
            landscapeDense = true,
        )
    }
}

@Composable
private fun LyricPreviewBlock(
    lines: List<LrcLine>,
    activeIndex: Int,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier,
    lineCount: Int = 2,
) {
    val n = lineCount.coerceIn(1, 4)
    Column(
        modifier
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF060A12).copy(alpha = 0.38f))
            .clickable(onClick = onOpenFull)
            .padding(vertical = 7.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "歌词预览 · 点按展开",
            style = TextStyle(
                color = LyricDim.copy(alpha = 0.32f),
                fontFamily = FontFamily.Monospace,
                fontSize = 7.sp,
                letterSpacing = 0.6.sp,
            ),
        )
        if (lines.isEmpty()) {
            repeat(n) { i ->
                Text(
                    text = when (i) {
                        0 -> "暂无歌词"
                        1 -> "让旋律代替语言"
                        else -> "· · ·"
                    },
                    style = TextStyle(
                        color = LyricDim.copy(alpha = 0.35f - i * 0.06f),
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = if (i == 0) 15.sp else 13.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            return
        }
        for (offset in 0 until n) {
            val i = (activeIndex + offset).coerceIn(0, lines.lastIndex)
            val line = lines[i]
            val isCurrent = offset == 0
            val alpha by animateFloatAsState(
                targetValue = if (isCurrent) 1f else 0.42f,
                animationSpec = tween(380, easing = FastOutSlowInEasing),
                label = "prevA$offset",
            )
            Text(
                text = line.text,
                style = TextStyle(
                    color = if (isCurrent) LyricCurrent.copy(alpha = alpha) else LyricDim.copy(alpha = alpha),
                    fontFamily = FontFamily.Serif,
                    fontStyle = if (isCurrent) FontStyle.Normal else FontStyle.Italic,
                    fontSize = if (isCurrent) 15.sp else 12.5.sp,
                    letterSpacing = 0.4.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                ),
            )
        }
    }
}

@Composable
@Suppress("KotlinConstantConditions")
private fun PlayerTransport(
    isPlaying: Boolean,
    buffering: Boolean,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    durationMs: Long,
    positionMs: Long,
    sliderDragging: Boolean,
    sliderValue: Float,
    onSliderDragStart: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderDragEnd: () -> Unit,
    playbackMode: PlaybackMode,
    onCyclePlaybackMode: () -> Unit,
    portraitSlim: Boolean = false,
    landscapeDense: Boolean = false,
) {
    val maxF = durationMs.toFloat().coerceAtLeast(1f)
    val sliderPos = if (sliderDragging) sliderValue else positionMs.toFloat()
    val displayPosMs = if (sliderDragging) sliderPos.toLong() else positionMs
    val playPulse by animateFloatAsState(
        targetValue = if (isPlaying) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f),
        label = "playPulse",
    )
    val skipSp = when {
        landscapeDense -> 13.sp
        portraitSlim -> 16.sp
        else -> 20.sp
    }
    val playSize = when {
        landscapeDense -> 30.dp
        portraitSlim -> 42.dp
        else -> 52.dp
    }
    val playGlyphSp = when {
        landscapeDense -> 12.sp
        portraitSlim -> 15.sp
        else -> 18.sp
    }
    val sliderH = when {
        // 两种全屏下都把进度条高度再压矮一点
        landscapeDense -> 12.dp
        portraitSlim -> 14.dp
        else -> 20.dp
    }

    // 横屏：左侧播放按钮 + 右侧进度条/时间，左右同一水平线
    if (landscapeDense) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(0.44f),
                contentAlignment = Alignment.Center,
            ) {
                // 用 5 槽等分：加入透明占位，避免“非播放键”被推到两侧
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // slot1: 播放模式
                    PlaybackModeControl(
                        mode = playbackMode,
                        onClick = onCyclePlaybackMode,
                        circleSize = playSize,
                        glyphSize = skipSp,
                    )

                    // slot2: 上一首
                    Box(
                        modifier = Modifier
                            .size(playSize)
                            .clip(CircleShape)
                            .clickable(onClick = onSkipPrev),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⏮",
                            style = TextStyle(color = CyanSoft.copy(alpha = 0.82f), fontSize = skipSp),
                            textAlign = TextAlign.Center,
                        )
                    }

                    // slot3: 播放/暂停（居中）
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = playPulse
                                scaleY = playPulse
                            }
                            .size(playSize)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(AccentRose.copy(alpha = 0.32f), Color(0xFF1A2230)),
                                ),
                            )
                            .clickable(onClick = onTogglePlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                buffering -> "···"
                                isPlaying -> "❚❚"
                                else -> "▶"
                            },
                            style = TextStyle(
                                color = LyricCurrent,
                                fontSize = playGlyphSp,
                            ),
                            textAlign = TextAlign.Center,
                        )
                    }

                    // slot4: 下一首
                    Box(
                        modifier = Modifier
                            .size(playSize)
                            .clip(CircleShape)
                            .clickable(onClick = onSkipNext),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⏭",
                            style = TextStyle(color = CyanSoft.copy(alpha = 0.82f), fontSize = skipSp),
                            textAlign = TextAlign.Center,
                        )
                    }

                    // slot5: 透明占位
                    Box(
                        modifier = Modifier
                            .size(playSize)
                            .clip(CircleShape)
                            // 占位但不可见：避免依赖 Modifier.alpha 扩展在当前 Compose 版本失效
                            .graphicsLayer { alpha = 0f },
                    )
                }
            }

            Column(
                Modifier
                    .weight(0.56f)
                    .padding(end = 10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(sliderH),
                    contentAlignment = Alignment.Center,
                ) {
                    Slider(
                        modifier = Modifier.fillMaxWidth(),
                        value = sliderPos.coerceIn(0f, maxF),
                        onValueChange = { v ->
                            if (!sliderDragging) onSliderDragStart()
                            onSliderChange(v)
                        },
                        onValueChangeFinished = onSliderDragEnd,
                        valueRange = 0f..maxF,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentRose,
                            activeTrackColor = AccentRose.copy(alpha = 0.82f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                        ),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTimeMs(displayPosMs),
                        style = TextStyle(
                            color = LyricDim.copy(alpha = 0.52f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            letterSpacing = 0.4.sp,
                        ),
                    )
                    Text(
                        text = formatTimeMs(durationMs),
                        style = TextStyle(
                            color = LyricDim.copy(alpha = 0.46f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            letterSpacing = 0.4.sp,
                        ),
                    )
                }
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                top = if (portraitSlim) 1.dp else 3.dp,
                bottom = if (portraitSlim) 10.dp else 0.dp,
            ),
    ) {
        if (landscapeDense || portraitSlim) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimeMs(displayPosMs),
                    style = TextStyle(
                        color = LyricDim.copy(alpha = 0.52f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (portraitSlim) 9.sp else 7.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
                )
                Text(
                    text = formatTimeMs(durationMs),
                    style = TextStyle(
                        color = LyricDim.copy(alpha = 0.46f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (portraitSlim) 9.sp else 7.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
                )
            }
        }

        // portraitSlim 的模式按钮会统一放在底部控件行（与上一首同一行）
        Box(
            Modifier
                .fillMaxWidth()
                .height(sliderH),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (portraitSlim) 3.dp else 0.dp),
                value = sliderPos.coerceIn(0f, maxF),
                onValueChange = { v ->
                    if (!sliderDragging) onSliderDragStart()
                    onSliderChange(v)
                },
                onValueChangeFinished = onSliderDragEnd,
                valueRange = 0f..maxF,
                colors = SliderDefaults.colors(
                    thumbColor = AccentRose,
                    activeTrackColor = AccentRose.copy(alpha = 0.82f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                ),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = if (landscapeDense) 0.dp else 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 5 槽均分：模式 / 上一首 / 播放(中间) / 下一首 / 透明占位
            PlaybackModeControl(
                mode = playbackMode,
                onClick = onCyclePlaybackMode,
                circleSize = playSize,
                glyphSize = skipSp,
            )

            Box(
                modifier = Modifier
                    .size(playSize)
                    .clip(CircleShape)
                    .clickable(onClick = onSkipPrev),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⏮",
                    style = TextStyle(color = CyanSoft.copy(alpha = 0.82f), fontSize = skipSp),
                    textAlign = TextAlign.Center,
                )
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = playPulse
                        scaleY = playPulse
                    }
                    .size(playSize)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentRose.copy(alpha = 0.32f), Color(0xFF1A2230)),
                        ),
                    )
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        buffering -> "···"
                        isPlaying -> "❚❚"
                        else -> "▶"
                    },
                    style = TextStyle(
                        color = LyricCurrent,
                        fontSize = playGlyphSp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }

            Box(
                modifier = Modifier
                    .size(playSize)
                    .clip(CircleShape)
                    .clickable(onClick = onSkipNext),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⏭",
                    style = TextStyle(color = CyanSoft.copy(alpha = 0.82f), fontSize = skipSp),
                    textAlign = TextAlign.Center,
                )
            }

            // 透明占位（不可点击），保证“播放键”在正中
            Box(
                modifier = Modifier
                    .size(playSize)
                    .clip(CircleShape)
                    .graphicsLayer { alpha = 0f },
            )
        }
    }
}
