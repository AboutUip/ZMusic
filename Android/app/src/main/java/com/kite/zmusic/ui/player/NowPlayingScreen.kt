package com.kite.zmusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
private val MistTop = Color(0xFF120A18)
private val MistMid = Color(0xFF1C1428)
private val MistBottom = Color(0xFF0A1420)
private val LyricCurrent = Color(0xFFF2EDE6)
private val LyricDim = Color(0xFF7A8899)
private val AccentRose = Color(0xFFE8B4BC)
private val CyanSoft = Color(0xFF6FD4D4)
private val OrbInk = Color(0xFF090B12)
private val GlassStroke = Color.White.copy(alpha = 0.16f)
private val GlassHi = Color.White.copy(alpha = 0.14f)
private val GlassLo = Color.White.copy(alpha = 0.045f)

/**
 * Gemini 式透光光球：相位线性循环，位移一律用整周期 sin/cos，保证首尾相接无跳变。
 */
@Composable
private fun GeminiOrbsBackdrop(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "geminiOrbs")
    // 多频率时钟；仅 Linear + Restart。位置必须对 phase∈[0,1] 以 1 为周期连续。
    val phaseA by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phaseA",
    )
    val phaseB by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(31_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phaseB",
    )
    val phaseC by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(17_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phaseC",
    )

    Canvas(modifier = modifier.background(OrbInk)) {
        val w = size.width
        val h = size.height
        val twoPi = (Math.PI * 2).toFloat()
        fun orb(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.38f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        val a = phaseA * twoPi
        val b = phaseB * twoPi
        val c = phaseC * twoPi
        // 呼吸用 sin，整数倍频，Restart 时与起点重合（无 Reverse 端点顿挫）
        val pulse = 1f + 0.12f * sin(a)
        val pulseInv = 1f + 0.10f * sin(a + Math.PI.toFloat())

        // 左上蔷薇：椭圆轨道（周期闭合）
        orb(
            cx = w * (0.22f + 0.14f * cos(a)),
            cy = h * (0.32f + 0.16f * sin(a)),
            radius = minOf(w, h) * 0.5f * pulse,
            color = Color(0xFFE8A0C8),
            alpha = 0.5f,
        )
        // 右下青蓝
        orb(
            cx = w * (0.72f + 0.12f * cos(b + 1.2f)),
            cy = h * (0.68f + 0.14f * sin(b + 0.4f)),
            radius = minOf(w, h) * 0.58f * (0.96f + 0.04f * sin(b)),
            color = Color(0xFF6EB8FF),
            alpha = 0.44f,
        )
        // 中右淡紫：整周期椭圆（勿用非整数倍角，否则 Restart 会跳）
        orb(
            cx = w * (0.58f + 0.11f * sin(c)),
            cy = h * (0.40f + 0.17f * cos(c)),
            radius = minOf(w, h) * 0.44f * pulseInv,
            color = Color(0xFFB8A0FF),
            alpha = 0.38f,
        )
        // 底部暖雾：水平往复，sin 映射后仍周期闭合
        orb(
            cx = w * (0.38f + 0.26f * sin(b)),
            cy = h * (0.88f + 0.04f * cos(b * 2f)),
            radius = w * 0.46f * (0.94f + 0.06f * sin(c)),
            color = Color(0xFFFFC9A8),
            alpha = 0.24f,
        )
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(if (compact) 18.dp else 24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(GlassHi, GlassLo),
                ),
            )
            .border(1.dp, GlassStroke, RoundedCornerShape(if (compact) 18.dp else 24.dp))
            .padding(
                horizontal = if (compact) 12.dp else 18.dp,
                vertical = if (compact) 8.dp else 16.dp,
            ),
        content = content,
    )
}

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

    val openSourcePlaylist = onOpenSourcePlaylist
    // 全局“下滑退出全屏播放器”：不占布局空间，仅监听手势
    val dismissSwipeThresholdPx = with(LocalDensity.current) {
        (if (isLandscape) 92.dp else 112.dp).toPx()
    }
    Box(
        modifier.fillMaxSize(),
    ) {
        if (isLandscape) {
            GeminiOrbsBackdrop(Modifier.fillMaxSize())
            RainGlassAtmosphere(Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(bg))
        }
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    start = landscapeStartInset + if (isLandscape) 10.dp else 12.dp,
                    end = if (isLandscape) 14.dp else 12.dp,
                    top = if (isLandscape) 8.dp else 6.dp,
                    bottom = if (isLandscape) 6.dp else 8.dp,
                ),
        ) {
            val srcTitle = state.sourcePlaylistTitle

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
                    sourceTitle = srcTitle,
                    onSourceClick = openSourcePlaylist,
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
    /** 横屏摆件：更薄的圆角与阴影，去掉厚重凹陷框感 */
    modernFlat: Boolean = false,
) {
    val pulse = rememberInfiniteTransition(label = "coverPulse")
    val breath by pulse.animateFloat(
        initialValue = if (modernFlat) 0.985f else 0.97f,
        targetValue = if (modernFlat) 1.02f else 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (modernFlat) 4200 else 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val drift by pulse.animateFloat(
        initialValue = if (modernFlat) -0.25f else -0.8f,
        targetValue = if (modernFlat) 0.25f else 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (modernFlat) 10_000 else 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val halo by pulse.animateFloat(
        initialValue = if (modernFlat) 0.28f else 0.22f,
        targetValue = if (modernFlat) 0.48f else 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo",
    )
    val corner = if (modernFlat) 16.dp else 22.dp
    val fillFrac = if (modernFlat) 0.94f else 0.88f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // 横屏现代封面：不要背后光晕，留给黑胶构图
        if (!modernFlat) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
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
        }
        Box(
            modifier = Modifier
                .fillMaxSize(fillFrac)
                .shadow(
                    if (modernFlat) 14.dp else 28.dp,
                    RoundedCornerShape(corner),
                    ambientColor = if (modernFlat) Color.Black.copy(alpha = 0.45f) else AccentRose.copy(alpha = 0.25f),
                )
                .clip(RoundedCornerShape(corner))
                .background(Color(0xFF0A0E14))
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                    rotationZ = if (modernFlat) 0f else drift * 0.35f
                    translationY = if (modernFlat) drift * 2.5f else 0f
                }
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            Crossfade(
                targetState = track.id,
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "coverMain",
            ) { targetId ->
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

/** 纯黑胶盘面（无封面）；纹路 + 外缘，中心由上层标签盖住。 */
@Composable
private fun VinylDiscBase(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        drawCircle(color = Color(0xFF0E0E10), radius = r, center = c)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF2C2C30), Color(0xFF141416), Color(0xFF080809)),
                center = c,
                radius = r,
            ),
            radius = r * 0.995f,
            center = c,
        )
        // 外缘高光
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = r * 0.985f,
            center = c,
            style = Stroke(width = r * 0.018f),
        )
        // 纹路环
        for (i in 1..11) {
            val rr = r * (0.26f + i * 0.055f)
            drawCircle(
                color = Color.White.copy(alpha = 0.035f + (i % 2) * 0.018f),
                radius = rr,
                center = c,
                style = Stroke(width = 1.1f),
            )
        }
    }
}

/**
 * 左侧主视觉：整盘黑胶；封面圆形贴在盘面（留外缘与中心）；
 * 播放时缓慢旋转，暂停保留角度（不重置）。
 */
@Composable
private fun VinylWithCoverArt(
    track: TrackRow,
    spinning: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // 用 Animatable 累加角度：暂停时取消动画但保留当前值，继续播放从断点续转
    val angle = remember { Animatable(0f) }
    LaunchedEffect(track.id) {
        angle.snapTo(0f)
    }
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        while (isActive) {
            val next = angle.value + 360f
            angle.animateTo(
                targetValue = next,
                animationSpec = tween(durationMillis = 28_000, easing = LinearEasing),
            )
        }
    }

    Box(
        modifier
            .shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.55f))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle.value },
            contentAlignment = Alignment.Center,
        ) {
            VinylDiscBase(Modifier.fillMaxSize())

            val coverFrac = 0.76f
            val centerFrac = 0.20f
            Box(
                Modifier
                    .fillMaxSize(coverFrac)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = track.id,
                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                    label = "vinylCover",
                ) { id ->
                    if (id == Long.MIN_VALUE) Unit
                    val u = track.coverUrl
                    if (!u.isNullOrBlank()) {
                        UrlImage(
                            url = u,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1A2230)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "♪",
                                style = TextStyle(
                                    color = LyricDim.copy(alpha = 0.45f),
                                    fontSize = 36.sp,
                                ),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize(centerFrac / coverFrac)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1E28)),
                )
            }

            Box(
                Modifier
                    .fillMaxSize(0.20f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2A3344), Color(0xFF12161E)),
                        ),
                    )
                    .border(1.dp, CyanSoft.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize(0.22f)
                        .clip(CircleShape)
                        .background(Color(0xFF050508)),
                )
            }
        }
    }
}

/** 当前歌词行时长（到下一行 / 曲终）。 */
private fun lyricLineDurationMs(
    lines: List<LrcLine>,
    index: Int,
    trackDurationMs: Long,
): Long {
    if (index !in lines.indices) return 4_000L
    val start = lines[index].timeMs
    val end = when {
        index + 1 < lines.size -> lines[index + 1].timeMs
        trackDurationMs > start -> trackDurationMs
        else -> start + 4_000L
    }
    return (end - start).coerceAtLeast(800L)
}

/**
 * 当前行光晕强度：按时长进度升起，句尾前渐隐。
 * @return 0..1
 */
private fun lyricHaloStrength(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p < 0.12f -> p / 0.12f
        p < 0.62f -> 1f
        else -> ((1f - p) / 0.38f).coerceIn(0f, 1f)
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

/** 横屏歌词：更大字号；已播/未播区分；当前行按时长光晕并在句尾渐隐。 */
@Composable
private fun LandscapeProjectionLyrics(
    lines: List<LrcLine>,
    activeIndex: Int,
    positionMs: Long,
    trackDurationMs: Long,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                style = TextStyle(
                    color = LyricDim.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        return
    }
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (offset in -2..2) {
            val i = activeIndex + offset
            when {
                offset == 0 && activeIndex >= 0 -> {
                    val dur = lyricLineDurationMs(lines, activeIndex, trackDurationMs)
                    val enableHalo = dur >= 1_000L
                    val lineStart = lines[activeIndex].timeMs
                    val progress = if (enableHalo) {
                        ((positionMs - lineStart).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    // 仅当前行：微弱光晕，句尾略收
                    val halo = if (enableHalo) {
                        0.35f + 0.65f * lyricHaloStrength(progress)
                    } else {
                        0f
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .drawBehind {
                                if (!enableHalo || halo <= 0.01f) return@drawBehind
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val radius = size.maxDimension * 0.38f
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            CyanSoft.copy(alpha = 0.07f * halo),
                                            Color.White.copy(alpha = 0.025f * halo),
                                            Color.Transparent,
                                        ),
                                        center = Offset(cx, cy),
                                        radius = radius,
                                    ),
                                    radius = radius,
                                    center = Offset(cx, cy),
                                )
                            }
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = activeIndex,
                            transitionSpec = {
                                (
                                    fadeIn(tween(360, easing = FastOutSlowInEasing)) +
                                        slideInVertically { h -> h / 8 }
                                    ).togetherWith(
                                    fadeOut(tween(240, easing = FastOutSlowInEasing)) +
                                        slideOutVertically { h -> -h / 10 },
                                )
                            },
                            label = "landLyricCur",
                            modifier = Modifier.fillMaxWidth(),
                        ) { act ->
                            val t = lines.getOrNull(act)?.text ?: "· · ·"
                            Text(
                                text = t,
                                style = TextStyle(
                                    color = Color(0xFFF8FAFC),
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 28.sp,
                                    lineHeight = 40.sp,
                                    letterSpacing = 0.5.sp,
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                i in lines.indices -> {
                    val played = i < activeIndex
                    Text(
                        text = lines[i].text,
                        style = TextStyle(
                            color = if (played) {
                                // 已播放：略暖、更淡
                                Color(0xFFB8C0CC).copy(alpha = 0.32f)
                            } else {
                                // 未播放：更冷、稍清晰
                                Color(0xFFDCE6F0).copy(alpha = 0.46f - abs(offset).coerceAtMost(2) * 0.06f)
                            },
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = if (played) FontWeight.Light else FontWeight.Normal,
                            fontStyle = if (played) FontStyle.Italic else FontStyle.Normal,
                            fontSize = if (played) 15.sp else 16.5.sp,
                            lineHeight = 26.sp,
                            letterSpacing = 0.35.sp,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp, horizontal = 10.dp),
                    )
                }
                else -> Spacer(Modifier.height(38.dp))
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
    sourceTitle: String?,
    onSourceClick: (() -> Unit)?,
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
    val srcIx = remember { MutableInteractionSource() }
    var controlsVisible by remember { mutableStateOf(true) }
    var idleBump by remember { mutableIntStateOf(0) }
    val showBar = controlsVisible || sliderDragging
    val density = LocalDensity.current

    // 0 = 沉浸（黑胶放大）→ 1 = 控件可见（黑胶缩小让位）；Animatable 可中途改目标打断
    val chrome = remember { Animatable(1f) }
    LaunchedEffect(showBar) {
        chrome.animateTo(
            targetValue = if (showBar) 1f else 0f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
    }
    val chromeT = chrome.value

    fun revealControls() {
        controlsVisible = true
        idleBump++
    }

    fun toggleControls() {
        if (controlsVisible) {
            controlsVisible = false
        } else {
            revealControls()
        }
    }

    LaunchedEffect(idleBump, sliderDragging, track.id) {
        if (sliderDragging) {
            controlsVisible = true
            return@LaunchedEffect
        }
        if (!controlsVisible) return@LaunchedEffect
        delay(2_800)
        controlsVisible = false
    }

    val barSlidePx = with(density) { 52.dp.toPx() }

    Box(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { toggleControls() },
            ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 左侧靠右贴歌词：信息与黑胶同宽左对齐；尺寸随 chrome 联动
            BoxWithConstraints(
                Modifier
                    .weight(0.36f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val discBase = (maxWidth * 0.92f).coerceIn(132.dp, 252.dp)
                val discExpanded = (discBase * 1.14f)
                    .coerceAtMost(maxWidth * 0.99f)
                    .coerceAtMost(286.dp)
                val discCompact = (discBase * 0.86f).coerceAtLeast(118.dp)
                val disc = androidx.compose.ui.unit.lerp(discExpanded, discCompact, chromeT)
                // 控件显示时下沉留白；隐藏时收回，让黑胶吃到下方空间
                val vinylBottomPad = androidx.compose.ui.unit.lerp(2.dp, 66.dp, chromeT)

                Column(
                    Modifier
                        .width(disc)
                        .padding(bottom = vinylBottomPad),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = track.name,
                        style = TextStyle(
                            color = Color(0xFFF5F7FA),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            letterSpacing = 0.35.sp,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = track.artists.uppercase(),
                        style = TextStyle(
                            color = CyanSoft.copy(alpha = 0.72f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            letterSpacing = 1.8.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!sourceTitle.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = sourceTitle,
                            style = TextStyle(
                                color = LyricDim.copy(alpha = 0.4f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                letterSpacing = 0.55.sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (onSourceClick != null) {
                                        Modifier.clickable(
                                            interactionSource = srcIx,
                                            indication = null,
                                            onClick = {
                                                revealControls()
                                                onSourceClick()
                                            },
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    VinylWithCoverArt(
                        track = track,
                        spinning = isPlaying && !buffering,
                        onClick = { toggleControls() },
                        modifier = Modifier.size(disc),
                    )
                }
            }

            LandscapeProjectionLyrics(
                lines = lines,
                activeIndex = activeLyric,
                positionMs = positionMs,
                trackDurationMs = durationMs,
                modifier = Modifier
                    .weight(0.64f)
                    .fillMaxHeight()
                    .padding(start = 0.dp, end = 4.dp),
            )
        }

        // 与 chrome 同驱动：淡出 + 下滑；完全收起后不占命中，避免挡点击
        if (showBar || chromeT > 0.001f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = chromeT
                        translationY = (1f - chromeT) * barSlidePx
                    }
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .clickable(
                        enabled = chromeT > 0.2f,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { revealControls() },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                PlayerTransport(
                    isPlaying = isPlaying,
                    buffering = buffering,
                    onTogglePlay = {
                        revealControls()
                        onTogglePlay()
                    },
                    onSkipNext = {
                        revealControls()
                        onSkipNext()
                    },
                    onSkipPrev = {
                        revealControls()
                        onSkipPrev()
                    },
                    durationMs = durationMs,
                    positionMs = positionMs,
                    sliderDragging = sliderDragging,
                    sliderValue = sliderValue,
                    onSliderDragStart = {
                        revealControls()
                        onSliderDragStart()
                    },
                    onSliderChange = onSliderChange,
                    onSliderDragEnd = {
                        revealControls()
                        onSliderDragEnd()
                    },
                    playbackMode = playbackMode,
                    onCyclePlaybackMode = {
                        revealControls()
                        onCyclePlaybackMode()
                    },
                    portraitSlim = false,
                    landscapeDense = true,
                )
            }
        }
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
    val iconTint = Color(0xFFB8C5D4)
    val playSize = when {
        landscapeDense -> 36.dp
        portraitSlim -> 42.dp
        else -> 52.dp
    }
    val skipHit = when {
        landscapeDense -> 34.dp
        portraitSlim -> 42.dp
        else -> 48.dp
    }
    val sliderH = when {
        landscapeDense -> 28.dp
        portraitSlim -> 14.dp
        else -> 20.dp
    }
    val timeStyle = TextStyle(
        color = LyricDim.copy(alpha = 0.7f),
        fontFamily = FontFamily.Monospace,
        fontSize = if (landscapeDense) 11.sp else if (portraitSlim) 9.sp else 10.sp,
        letterSpacing = 0.3.sp,
    )
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color(0xFFE8EEF5),
        activeTrackColor = Color(0xFFD5DEE8).copy(alpha = 0.9f),
        inactiveTrackColor = Color.White.copy(alpha = 0.14f),
    )

    // 横屏：全宽简约条 — 左：模式+传输；右：当前时间 | 进度 | 总时长（同一水平线）
    if (landscapeDense) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlaybackModeControl(
                mode = playbackMode,
                onClick = onCyclePlaybackMode,
                circleSize = skipHit,
                tint = iconTint,
            )
            Box(
                modifier = Modifier
                    .size(skipHit)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkipPrev,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TransportSkipIcon(forward = false, size = 16.dp, tint = iconTint)
            }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = playPulse
                        scaleY = playPulse
                    }
                    .size(playSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlay,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TransportPlayPauseIcon(
                    playing = isPlaying,
                    buffering = buffering,
                    size = 16.dp,
                    tint = Color(0xFFF5F7FA),
                )
            }
            Box(
                modifier = Modifier
                    .size(skipHit)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkipNext,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TransportSkipIcon(forward = true, size = 16.dp, tint = iconTint)
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = formatTimeMs(displayPosMs),
                style = timeStyle,
                modifier = Modifier.widthIn(min = 36.dp),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            Box(
                Modifier
                    .weight(1f)
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
                    colors = sliderColors,
                )
            }
            Text(
                text = formatTimeMs(durationMs),
                style = timeStyle,
                modifier = Modifier.widthIn(min = 36.dp),
                textAlign = TextAlign.Start,
                maxLines = 1,
            )
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
        if (portraitSlim) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimeMs(displayPosMs),
                    style = timeStyle,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
                )
                Text(
                    text = formatTimeMs(durationMs),
                    style = timeStyle,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
                )
            }
        }

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
                colors = sliderColors,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackModeControl(
                mode = playbackMode,
                onClick = onCyclePlaybackMode,
                circleSize = playSize,
                tint = iconTint,
            )

            Box(
                modifier = Modifier
                    .size(playSize)
                    .clip(CircleShape)
                    .clickable(onClick = onSkipPrev),
                contentAlignment = Alignment.Center,
            ) {
                TransportSkipIcon(forward = false, size = 18.dp, tint = iconTint)
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = playPulse
                        scaleY = playPulse
                    }
                    .size(playSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                TransportPlayPauseIcon(
                    playing = isPlaying,
                    buffering = buffering,
                    size = 18.dp,
                    tint = Color(0xFFF5F7FA),
                )
            }

            Box(
                modifier = Modifier
                    .size(playSize)
                    .clip(CircleShape)
                    .clickable(onClick = onSkipNext),
                contentAlignment = Alignment.Center,
            ) {
                TransportSkipIcon(forward = true, size = 18.dp, tint = iconTint)
            }

            // 透明占位，保证播放键视觉居中
            Box(Modifier.size(playSize))
        }
    }
}
