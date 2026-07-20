package com.kite.zmusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
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
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LrcParser
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PlayerDisplayPrefsStore
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.ui.common.UrlImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 空白区域手势（子控件已消费的指针不会进入）：
 * - 纯点击（位移未超 touchSlop）→ [onTap]
 * - 明确向下滑超过阈值 → [onSwipeDown]
 * - 其它滑动不触发 [onTap]，避免滑动误唤出播放控件
 * - 用 Initial 通道累计位移，避免子控件（黑胶拖动）consume 后被误判为点击
 */
private fun Modifier.nowPlayingBlankGestures(
    dismissThresholdPx: Float,
    onTap: (() -> Unit)?,
    onSwipeDown: () -> Unit,
): Modifier = pointerInput(dismissThresholdPx, onTap, onSwipeDown) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val pointerId = down.id
        var totalDx = 0f
        var totalDy = 0f
        var decidedSwipeDown = false
        var movedBeyondSlop = false

        while (true) {
            // Initial：在子控件 consume 前记下真实位移，防止黑胶横滑松手被当成点击
            val raw = awaitPointerEvent(PointerEventPass.Initial)
            val rawChange = raw.changes.find { it.id == pointerId } ?: break
            val rawDelta = rawChange.positionChange()
            totalDx += rawDelta.x
            totalDy += rawDelta.y
            if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                movedBeyondSlop = true
            }

            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.find { it.id == pointerId } ?: break

            val downDominant =
                totalDy > touchSlop && totalDy > abs(totalDx) * 1.2f
            if (downDominant) {
                decidedSwipeDown = true
                change.consume()
                if (totalDy >= dismissThresholdPx) {
                    onSwipeDown()
                    while (true) {
                        val rest = awaitPointerEvent(PointerEventPass.Main)
                        val c = rest.changes.find { it.id == pointerId } ?: return@awaitEachGesture
                        c.consume()
                        if (!c.pressed) return@awaitEachGesture
                    }
                }
            }

            if (!change.pressed) {
                if (
                    !decidedSwipeDown &&
                    !movedBeyondSlop &&
                    abs(totalDx) < touchSlop &&
                    abs(totalDy) < touchSlop
                ) {
                    onTap?.invoke()
                }
                break
            }
        }
    }
}
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

/**
 * 歌词切换动画时序：短句自动压缩，避免固定 260ms 盖过行间隔。
 * - [leadMs]：虚拟进度提前量（过渡在真实时间戳附近收束）
 * - [durationMs]：进入/退出动画时长
 */
data class LyricAnimTiming(
    val leadMs: Long,
    val durationMs: Int,
)

/** 默认（长句）上限；短句会按行间隔下压。 */
const val LyricAnimLeadMs = 260L
const val LyricAnimDurationMs = 260

/** 当前行原始时长（到下一行 / 曲终），不做 800ms 抬底。 */
fun lyricLineSpanMs(
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
    return (end - start).coerceAtLeast(1L)
}

/**
 * 按「当前行 / 下一行」中较短的间隔，计算可完成的过渡时序。
 * 保证动画约占窗口的 ~40%，并留出可见的定格段。
 */
fun lyricAnimTiming(
    lines: List<LrcLine>,
    positionMs: Long,
    trackDurationMs: Long = 0L,
): LyricAnimTiming {
    if (lines.isEmpty()) {
        return LyricAnimTiming(LyricAnimLeadMs, LyricAnimDurationMs)
    }
    val real = lyricActiveIndex(lines, positionMs)
    val currSpan = when {
        real < 0 -> lines.first().timeMs.coerceAtLeast(1L)
        else -> lyricLineSpanMs(lines, real, trackDurationMs)
    }
    val nextSpan = when {
        real < 0 -> if (lines.size > 1) lyricLineSpanMs(lines, 0, trackDurationMs) else currSpan
        real + 1 in lines.indices -> lyricLineSpanMs(lines, real + 1, trackDurationMs)
        else -> currSpan
    }
    val window = minOf(currSpan, nextSpan).coerceAtLeast(1L)
    return lyricAnimTimingForWindow(window)
}

fun lyricAnimTimingForWindow(windowMs: Long): LyricAnimTiming {
    val w = windowMs.coerceAtLeast(1L)
    // 短句提高占比与下限，保证仍有可感知过渡；长句略放宽更丝滑
    val durationMs = when {
        w < 280L -> (w * 0.62f).toInt().coerceIn(140, 180)
        w < 480L -> (w * 0.54f).toInt().coerceIn(170, 230)
        w < 800L -> (w * 0.48f).toInt().coerceIn(210, 280)
        w < 1_500L -> (w * 0.42f).toInt().coerceIn(260, 340)
        else -> 380
    }
    // 提前量略小于时长，短句也能看到完整入场再切走
    val leadMs = (durationMs * 0.88f).toLong()
        .coerceAtMost((w * 0.50f).toLong())
        .coerceAtLeast(56L)
    return LyricAnimTiming(leadMs = leadMs, durationMs = durationMs)
}

/** 供 UI 动画用的激活下标（已含自适应提前量）。 */
fun lyricAnimActiveIndex(
    lines: List<LrcLine>,
    positionMs: Long,
    trackDurationMs: Long = 0L,
): Int {
    val lead = lyricAnimTiming(lines, positionMs, trackDurationMs).leadMs
    return lyricActiveIndex(lines, positionMs + lead)
}

/** 播放位下标：有歌词时永不返回 -1。空行应在解析阶段已剔除。 */
fun lyricFocusIndex(lines: List<LrcLine>, activeIndex: Int): Int {
    if (lines.isEmpty()) return -1
    if (activeIndex < 0) return 0
    return activeIndex.coerceIn(0, lines.lastIndex)
}

/** 当前是否已进入「演唱中」样式（含动画提前量）。 */
fun lyricIsLive(lines: List<LrcLine>, activeIndex: Int, focusIndex: Int): Boolean =
    activeIndex >= 0 &&
        focusIndex == activeIndex &&
        lines.getOrNull(activeIndex)?.text?.isNotBlank() == true

/** 短句用更陡的进出；长句保持原包络。目标值需再经动画插值，避免硬切。 */
private fun lyricHaloStrength(progress: Float, lineSpanMs: Long): Float {
    val p = progress.coerceIn(0f, 1f)
    val short = lineSpanMs < 1_000L
    return if (short) {
        when {
            p < 0.22f -> (p / 0.22f) * (p / 0.22f) // ease-in 起光
            p < 0.55f -> 1f
            else -> {
                val t = ((1f - p) / 0.45f).coerceIn(0f, 1f)
                t * t
            }
        }
    } else {
        when {
            p < 0.16f -> {
                val t = p / 0.16f
                t * t * (3f - 2f * t) // smoothstep
            }
            p < 0.68f -> 1f
            else -> {
                val t = ((1f - p) / 0.32f).coerceIn(0f, 1f)
                t * t * (3f - 2f * t)
            }
        }
    }
}

/** 歌词过渡通用缓动：慢起慢收，偏温柔。 */
private val LyricSoftEasing = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1f)
private val LyricSofterEasing = CubicBezierEasing(0.4f, 0.0f, 0.15f, 1f)

/** 短于此间隔的歌词跳过出场，直接入场下一句。 */
private const val LyricSkipExitSpanMs = 480L

/**
 * 中心歌词稳定槽：翻页感过渡。
 * - 出场：溶解淡出 + 向上收；极短句跳过出场
 * - 入场：自下而上 + 淡入
 * 禁止 scaleX/Y，避免水平错位。
 */
@Composable
private fun StableCenterLyricText(
    focus: Int,
    text: String,
    animMs: Int,
    lineSpanMs: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    var shownFocus by remember { mutableIntStateOf(focus) }
    var shownText by remember { mutableStateOf(text) }
    var lastSpanMs by remember { mutableLongStateOf(lineSpanMs) }
    val enterAlpha = remember { Animatable(1f) }
    val enterLift = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(focus, text, animMs, lineSpanMs) {
        if (focus == shownFocus && shownText == text) {
            enterAlpha.snapTo(1f)
            enterLift.snapTo(0f)
            lastSpanMs = lineSpanMs
            return@LaunchedEffect
        }
        // 出场 / 入场同长，保证反转连续性
        val phaseMs = animMs.coerceIn(200, 480)
        val liftPx = with(density) { 12.dp.toPx() }
        val skipExit = lastSpanMs < LyricSkipExitSpanMs

        if (!skipExit && enterAlpha.value > 0.04f) {
            // 出场：淡出并向上离开（修正此前误向下）
            coroutineScope {
                launch {
                    enterAlpha.animateTo(0f, tween(phaseMs, easing = LyricSofterEasing))
                }
                launch {
                    enterLift.animateTo(
                        -liftPx,
                        tween(phaseMs, easing = LyricSofterEasing),
                    )
                }
            }
        }

        shownFocus = focus
        shownText = text
        lastSpanMs = lineSpanMs
        enterAlpha.snapTo(0f)
        // 入场：自下而上
        enterLift.snapTo(liftPx)
        coroutineScope {
            launch {
                enterAlpha.animateTo(1f, tween(phaseMs, easing = LyricSoftEasing))
            }
            launch {
                enterLift.animateTo(0f, tween(phaseMs, easing = LyricSoftEasing))
            }
        }
    }

    Text(
        text = shownText,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enterAlpha.value
                translationY = enterLift.value
            },
    )
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
    /** 横屏额外左侧 inset（本页已无 Dock，通常为 0） */
    landscapeStartInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    var portraitLyricsOpen by rememberSaveable { mutableStateOf(false) }
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val displayPrefsStore = remember { PlayerDisplayPrefsStore(context) }
    var displayPrefs by remember { mutableStateOf(displayPrefsStore.load()) }
    fun updateDisplayPrefs(next: PlayerDisplayPrefs) {
        val sanitized = next.sanitized()
        displayPrefs = sanitized
        displayPrefsStore.save(sanitized)
    }

    // 竖屏：优先关闭“歌词预览”，再次返回才退出全屏播放器
    BackHandler(enabled = !isLandscape && portraitLyricsOpen) {
        portraitLyricsOpen = false
    }

    LaunchedEffect(track.id) {
        portraitLyricsOpen = false
        sliderDragging = false
    }

    val duration = state.durationMs.coerceAtLeast(1L)
    // 进度条/时间：切歌时快速动画归零；歌词仍用真实 position，避免回 scrub
    val seekDisplayPos = rememberSeekDisplayPositionMs(
        trackId = track.id,
        positionMs = state.positionMs,
        loadPending = state.loadPending,
        seeking = sliderDragging,
    )
    val displayPos = if (sliderDragging) sliderValue.toLong() else seekDisplayPos
    val lyricPos = if (sliderDragging) sliderValue.toLong() else state.positionMs
    // 获取后即净化空行（兼容旧缓存里仍含空白的 LRC）
    val lyricLines = remember(state.lyricLines) {
        state.lyricLines.mapNotNull { line ->
            LrcParser.sanitizeLyricText(line.text)?.let { line.copy(text = it) }
        }
    }

    val bg = Brush.verticalGradient(
        colors = listOf(MistTop, MistMid, MistBottom),
    )

    val openSourcePlaylist = onOpenSourcePlaylist
    // 下滑退出阈值：交给竖/横屏 body 的空白手势识别
    val dismissSwipeThresholdPx = with(LocalDensity.current) {
        (if (isLandscape) 92.dp else 112.dp).toPx()
    }
    // Animatable：连点开关会取消上一跳，从当前强度反向；时长按剩余路程缩放，打断可预测
    val rainProgress = remember {
        Animatable(if (displayPrefs.rainNightEnabled) 1f else 0f)
    }
    LaunchedEffect(displayPrefs.rainNightEnabled) {
        val target = if (displayPrefs.rainNightEnabled) 1f else 0f
        val distance = kotlin.math.abs(target - rainProgress.value).coerceIn(0f, 1f)
        val durationMs = (1_100f * distance).toInt().coerceIn(280, 1_100)
        rainProgress.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f),
            ),
        )
    }
    val rainIntensity = rainProgress.value
    // 横屏设置面板磨砂源：氛围层 + 播放内容共用同一 HazeState
    val settingsHazeState = if (isLandscape) rememberHazeState() else null
    Box(
        modifier.fillMaxSize(),
    ) {
        if (isLandscape) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        settingsHazeState?.let { Modifier.hazeSource(state = it, zIndex = 0f) }
                            ?: Modifier,
                    ),
            ) {
                GeminiOrbsBackdrop(Modifier.fillMaxSize())
                if (rainIntensity > 0.01f) {
                    RainGlassAtmosphere(
                        modifier = Modifier.fillMaxSize(),
                        intensity = rainIntensity,
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize().background(bg))
        }
        Column(
            Modifier
                .fillMaxSize()
                // 横屏：内容可延伸进左侧挖孔/原状态栏区；仅避开导航条
                .then(
                    if (isLandscape) {
                        Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.End,
                            ),
                        )
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    },
                )
                .padding(
                    start = if (isLandscape) 0.dp else (landscapeStartInset + 12.dp),
                    end = if (isLandscape) 14.dp else 12.dp,
                    top = if (isLandscape) 0.dp else 6.dp,
                    bottom = if (isLandscape) 0.dp else 8.dp,
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
                    lines = lyricLines,
                    positionMs = lyricPos,
                    seekPositionMs = displayPos,
                    isPlaying = state.isPlaying,
                    buffering = state.buffering,
                    loadPending = state.loadPending,
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
                        if (!state.loadPending) {
                            sliderDragging = true
                            sliderValue = seekDisplayPos.toFloat()
                        }
                    },
                    onSliderChange = { sliderValue = it },
                    onSliderDragEnd = {
                        sliderDragging = false
                        onSeek(sliderValue.toLong().coerceIn(0L, state.durationMs))
                    },
                    onDismiss = onDismiss,
                    dismissSwipeThresholdPx = dismissSwipeThresholdPx,
                    displayPrefs = displayPrefs,
                    onDisplayPrefsChange = ::updateDisplayPrefs,
                    settingsHazeState = settingsHazeState!!,
                    peekNextTrack = state.peekNextTrack,
                    peekPrevTrack = state.peekPrevTrack,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PortraitPlayerBody(
                    track = track,
                    lines = lyricLines,
                    positionMs = lyricPos,
                    seekPositionMs = displayPos,
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
                        sliderValue = seekDisplayPos.toFloat()
                    },
                    onSliderChange = { sliderValue = it },
                    onSliderDragEnd = {
                        sliderDragging = false
                        onSeek(sliderValue.toLong().coerceIn(0L, state.durationMs))
                    },
                    onDismiss = onDismiss,
                    dismissSwipeThresholdPx = dismissSwipeThresholdPx,
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

/** 竖屏展开：根据可用高度尽可能多展示歌词，并保持垂直居中 */
@Composable
private fun PortraitCinemaLyrics(
    lines: List<LrcLine>,
    positionMs: Long,
    trackDurationMs: Long,
    modifier: Modifier = Modifier,
) {
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
    val timing = lyricAnimTiming(lines, positionMs, trackDurationMs)
    val animActive = lyricAnimActiveIndex(lines, positionMs, trackDurationMs)
    val focus = lyricFocusIndex(lines, animActive)
    val live = lyricIsLive(lines, animActive, focus)
    val animMs = timing.durationMs
    val emphasis by animateFloatAsState(
        targetValue = if (live) 1f else 0f,
        animationSpec = tween(
            durationMillis = (animMs * 1.25f).toInt().coerceIn(280, 520),
            easing = LyricSoftEasing,
        ),
        label = "portraitLyricEmphasis",
    )
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
        val slots = remember(focus, lines.size, sideCount) {
            (-sideCount..sideCount).mapNotNull { offset ->
                val i = focus + offset
                if (i in lines.indices) offset to i else null
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            slots.forEach { (offset, i) ->
                if (offset == 0) {
                    StableCenterLyricText(
                        focus = focus,
                        text = lines.getOrNull(focus)?.text.orEmpty(),
                        animMs = animMs,
                        lineSpanMs = lyricLineSpanMs(lines, focus, trackDurationMs),
                        maxLines = 1,
                        style = TextStyle(
                            color = LyricCurrent.copy(alpha = 0.55f + 0.45f * emphasis),
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            lineHeight = 26.sp,
                            letterSpacing = 0.3.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                } else {
                    Text(
                        text = lines[i].text,
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
    seekPositionMs: Long,
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
    onDismiss: () -> Unit,
    dismissSwipeThresholdPx: Float,
    modifier: Modifier = Modifier,
) {
    val idx = lyricAnimActiveIndex(lines, positionMs, durationMs)
    val srcIx = remember { MutableInteractionSource() }

    Column(
        modifier
            .fillMaxSize()
            .nowPlayingBlankGestures(
                dismissThresholdPx = dismissSwipeThresholdPx,
                onTap = null,
                onSwipeDown = {
                    if (lyricsExpanded) onCollapseLyrics() else onDismiss()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                        positionMs = positionMs,
                        trackDurationMs = durationMs,
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
                            trackDurationMs = durationMs,
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
            positionMs = seekPositionMs,
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

/** 横屏歌词：无空行占位；居中句连续缩放；用提前量抵消动画滞后。 */
@Composable
private fun LandscapeProjectionLyrics(
    lines: List<LrcLine>,
    positionMs: Long,
    trackDurationMs: Long,
    fontScale: Float = 1f,
    offsetXDp: Float = 0f,
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
    val timing = lyricAnimTiming(lines, positionMs, trackDurationMs)
    val animActive = lyricAnimActiveIndex(lines, positionMs, trackDurationMs)
    val focus = lyricFocusIndex(lines, animActive)
    val live = lyricIsLive(lines, animActive, focus)
    val fs = fontScale.coerceIn(PlayerDisplayPrefs.FONT_MIN, PlayerDisplayPrefs.FONT_MAX)
    val animMs = timing.durationMs

    // 只渲染真实存在的句子，避免 Spacer 造成「空行」
    val slots = remember(focus, lines.size) {
        (-2..2).mapNotNull { offset ->
            val i = focus + offset
            if (i in lines.indices) offset to i else null
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .offset(x = offsetXDp.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        slots.forEach { (offset, i) ->
            if (offset == 0) {
                LandscapeCenterLyricLine(
                    lines = lines,
                    focus = focus,
                    live = live,
                    positionMs = positionMs,
                    trackDurationMs = trackDurationMs,
                    fontScale = fs,
                    animMs = animMs,
                )
            } else {
                LandscapeSideLyricLine(
                    text = lines[i].text,
                    played = animActive >= 0 && i < animActive,
                    distance = abs(offset),
                    fontScale = fs,
                    animMs = animMs,
                )
            }
        }
    }
}

@Composable
private fun LandscapeCenterLyricLine(
    lines: List<LrcLine>,
    focus: Int,
    live: Boolean,
    positionMs: Long,
    trackDurationMs: Long,
    fontScale: Float,
    animMs: Int,
) {
    val emphasis by animateFloatAsState(
        targetValue = if (live) 1f else 0f,
        animationSpec = tween(
            durationMillis = (animMs * 1.25f).toInt().coerceIn(280, 520),
            easing = LyricSoftEasing,
        ),
        label = "landLyricEmphasis",
    )
    // 布局用固定字号；不做缩放强调，避免入场结束水平微跳
    val baseFont = 26f * fontScale
    val baseLine = 38f * fontScale
    val textAlpha = 0.58f + 0.42f * emphasis

    val span = lyricLineSpanMs(lines, focus, trackDurationMs)
    val enableHalo = live && span >= 320L
    val lineStart = lines.getOrNull(focus)?.timeMs ?: 0L
    val rawProgress = if (enableHalo) {
        ((positionMs - lineStart).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val targetHalo = if (enableHalo) {
        lyricHaloStrength(rawProgress, span) * (0.55f + 0.45f * emphasis)
    } else {
        0f
    }
    // 光晕独立平滑，切句时柔进柔出，不再跟进度硬切
    val halo by animateFloatAsState(
        targetValue = targetHalo,
        animationSpec = tween(
            durationMillis = if (targetHalo > 0.02f) 520 else 420,
            easing = LyricSoftEasing,
        ),
        label = "landLyricHalo",
    )
    // 演唱中轻微呼吸，让光晕「活」起来
    val breath = rememberInfiniteTransition(label = "lyricHaloBreath")
    val breathAmp by breath.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LyricSoftEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lyricHaloBreathAmp",
    )
    val drawnHalo = if (halo > 0.01f) halo * (0.94f + 0.06f * breathAmp) else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .drawBehind {
                if (drawnHalo <= 0.01f) return@drawBehind
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = size.maxDimension * (0.34f + 0.06f * drawnHalo)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CyanSoft.copy(alpha = 0.09f * drawnHalo),
                            Color.White.copy(alpha = 0.03f * drawnHalo),
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
        StableCenterLyricText(
            focus = focus,
            text = lines.getOrNull(focus)?.text.orEmpty(),
            animMs = animMs,
            lineSpanMs = span,
            style = TextStyle(
                color = Color(0xFFF8FAFC).copy(alpha = textAlpha),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = baseFont.sp,
                lineHeight = baseLine.sp,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun LandscapeSideLyricLine(
    text: String,
    played: Boolean,
    distance: Int,
    fontScale: Float,
    animMs: Int,
) {
    val targetAlpha = if (played) {
        0.32f
    } else {
        (0.46f - distance.coerceAtMost(2) * 0.06f)
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = (animMs * 1.1f).toInt().coerceIn(240, 480),
            easing = LyricSoftEasing,
        ),
        label = "landSideA",
    )
    val sizeSp = ((if (played) 15f else 16.5f) * fontScale)
    Text(
        text = text,
        style = TextStyle(
            color = if (played) {
                Color(0xFFB8C0CC).copy(alpha = alpha)
            } else {
                Color(0xFFDCE6F0).copy(alpha = alpha)
            },
            fontFamily = FontFamily.SansSerif,
            fontWeight = if (played) FontWeight.Light else FontWeight.Normal,
            fontStyle = if (played) FontStyle.Italic else FontStyle.Normal,
            fontSize = sizeSp.sp,
            lineHeight = (26f * fontScale).sp,
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

@Composable
private fun LandscapePlayerBody(
    track: TrackRow,
    lines: List<LrcLine>,
    positionMs: Long,
    seekPositionMs: Long,
    isPlaying: Boolean,
    buffering: Boolean,
    loadPending: Boolean,
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
    onDismiss: () -> Unit,
    dismissSwipeThresholdPx: Float,
    displayPrefs: PlayerDisplayPrefs,
    onDisplayPrefsChange: (PlayerDisplayPrefs) -> Unit,
    settingsHazeState: HazeState,
    peekNextTrack: TrackRow?,
    peekPrevTrack: TrackRow?,
    modifier: Modifier = Modifier,
) {
    val srcIx = remember { MutableInteractionSource() }
    val activity = LocalContext.current as? android.app.Activity
    val rotationLock = com.kite.zmusic.ui.orientation.LocalSessionRotationLock.current
    // 直接读 Store，保证锁状态变化触发重组（不依赖 @Stable 门面推断）
    val rotationLocked = com.kite.zmusic.ui.orientation.SessionRotationLockStore.locked
    // 沉浸默认隐藏；仅纯点击唤出，滑动不唤出（常显时恒定展开）
    var controlsVisible by remember { mutableStateOf(displayPrefs.transportAlwaysVisible) }
    var settingsOpen by remember { mutableStateOf(false) }
    var idleBump by remember { mutableIntStateOf(0) }
    var vinylSkipDir by remember { mutableStateOf(VinylSkipDirection.Next) }
    var vinylBusy by remember { mutableStateOf(false) }
    // 未预加载 / URL 解析中：控件锁定，显示加载
    val controlsLocked = loadPending
    val transportBuffering = buffering || loadPending
    val transportPinned = displayPrefs.transportAlwaysVisible
    // 设置与底部播放条互斥；常显时除设置打开外保持展开
    val showBar = (controlsVisible || sliderDragging || transportPinned) && !settingsOpen
    val density = LocalDensity.current
    val uiScale = displayPrefs.uiScale.coerceIn(PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX)
    val settingsCurve = remember { CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f) }

    LaunchedEffect(transportPinned) {
        if (transportPinned) controlsVisible = true
    }

    // 0 = 沉浸（黑胶放大）→ 1 = 控件可见（黑胶缩小让位）；Animatable 可中途改目标打断
    val chrome = remember { Animatable(if (transportPinned) 1f else 0f) }
    LaunchedEffect(showBar) {
        chrome.animateTo(
            targetValue = if (showBar) 1f else 0f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
    }
    val chromeT = chrome.value

    val settingsPanel = remember { Animatable(0f) }
    LaunchedEffect(settingsOpen) {
        settingsPanel.animateTo(
            targetValue = if (settingsOpen) 1f else 0f,
            animationSpec = tween(durationMillis = 460, easing = settingsCurve),
        )
    }
    val settingsT = settingsPanel.value

    fun closeSettings() {
        settingsOpen = false
        if (transportPinned) controlsVisible = true
    }

    fun openSettings() {
        // 互斥：收回下方播放组件
        controlsVisible = false
        settingsOpen = true
    }

    fun revealControls() {
        if (settingsOpen) return
        controlsVisible = true
        idleBump++
    }

    fun toggleControls() {
        if (settingsOpen) {
            closeSettings()
            return
        }
        if (transportPinned) {
            // 常显：点击不收回，仅刷新计时无意义，保持展开
            return
        }
        if (controlsVisible) {
            controlsVisible = false
        } else {
            revealControls()
        }
    }

    BackHandler(enabled = settingsOpen) {
        closeSettings()
    }

    LaunchedEffect(idleBump, sliderDragging, track.id, settingsOpen, transportPinned) {
        if (settingsOpen || transportPinned) return@LaunchedEffect
        if (sliderDragging) {
            controlsVisible = true
            return@LaunchedEffect
        }
        if (!controlsVisible) return@LaunchedEffect
        delay(3_500)
        controlsVisible = false
    }

    val barSlidePx = with(density) { 52.dp.toPx() }
    // 与底部播放条外缘对齐
    val chromeSidePad = 2.dp

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .nowPlayingBlankGestures(
                dismissThresholdPx = dismissSwipeThresholdPx,
                onTap = { toggleControls() },
                onSwipeDown = {
                    if (settingsOpen) closeSettings() else onDismiss()
                },
            ),
    ) {
        // 与左侧歌曲信息同一上边距（按左栏 discExpanded / edgeInset 推算）
        val rowGap = 4.dp
        val leftColW = (maxWidth - rowGap) * 0.36f
        val discBaseForPad = (leftColW * 0.92f).coerceIn(132.dp, 252.dp)
        val discExpandedForPad = (discBaseForPad * 1.14f)
            .coerceAtMost(leftColW * 0.99f)
            .coerceAtMost(286.dp)
        val songMetaTopPad = ((leftColW - discExpandedForPad) / 2).coerceAtLeast(6.dp)

        // 播放内容作磨砂源（不含设置面板本身）
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(state = settingsHazeState, zIndex = 1f)
                .graphicsLayer { clip = false },
        ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp)
                .graphicsLayer {
                    scaleX = uiScale
                    scaleY = uiScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    clip = false
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            // 左侧：歌名贴顶；黑胶可偏移 / 绝对垂直居中（动画过渡）
            // 左栏铺满至挖孔侧，黑胶离场/入场可画进摄像头区域
            val layoutDir = LocalLayoutDirection.current
            val cutoutStart = WindowInsets.displayCutout
                .asPaddingValues()
                .calculateStartPadding(layoutDir)
            BoxWithConstraints(
                Modifier
                    .weight(0.36f)
                    .fillMaxHeight()
                    .graphicsLayer { clip = false },
                contentAlignment = Alignment.TopEnd,
            ) {
                val discBase = (maxWidth * 0.92f).coerceIn(132.dp, 252.dp)
                val discExpanded = (discBase * 1.14f)
                    .coerceAtMost(maxWidth * 0.99f)
                    .coerceAtMost(286.dp)
                val discCompact = (discBase * 0.86f).coerceAtLeast(118.dp)
                val absT by animateFloatAsState(
                    targetValue = if (displayPrefs.vinylAbsoluteCenter) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 480,
                        easing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f),
                    ),
                    label = "vinylAbsCenter",
                )
                // 非绝对居中：尺寸随 chrome 缩；绝对居中：尺寸固定，chrome 只缩放
                val disc = androidx.compose.ui.unit.lerp(
                    androidx.compose.ui.unit.lerp(discExpanded, discCompact, chromeT),
                    discExpanded,
                    absT,
                )
                val chromePad = androidx.compose.ui.unit.lerp(2.dp, 66.dp, chromeT)
                val vinylBottomPad = androidx.compose.ui.unit.lerp(0.dp, chromePad, 1f - absT)
                val compactRatio = if (discExpanded.value > 0.1f) {
                    discCompact / discExpanded
                } else {
                    1f
                }
                val vinylScale = androidx.compose.ui.util.lerp(
                    1f,
                    androidx.compose.ui.util.lerp(1f, compactRatio, chromeT),
                    absT,
                )
                val metaWidth = discExpanded
                val edgeInset = (maxWidth - metaWidth).coerceAtLeast(0.dp)
                val ox = displayPrefs.vinylOffsetXDp.dp
                val oy = (displayPrefs.vinylOffsetYDp * (1f - absT)).dp
                // 从「剩余区居中」过渡到「整栏垂直居中」：约半个信息块高度
                val layoutShiftY = with(density) {
                    (((edgeInset / 2) + 72.dp).toPx() * 0.5f +
                        chromePad.toPx() * 0.35f) * (1f - absT)
                }
                // 上一首入场：相对「实际静止中心」（含水平偏移 / 缩放）整盘离开左栏左缘
                val prevEnterSlidePx = with(density) {
                    val centerFromLeft =
                        (maxWidth - metaWidth / 2).toPx() + ox.toPx()
                    val radius = disc.toPx() * 0.5f * vinylScale
                    centerFromLeft + radius + 6.dp.toPx()
                }

                @Composable
                fun VinylDisc(mod: Modifier) {
                    VinylTransitionStage(
                        track = track,
                        peekNext = peekNextTrack,
                        peekPrev = peekPrevTrack,
                        spinning = isPlaying && !transportBuffering && !vinylBusy,
                        direction = vinylSkipDir,
                        gesturesEnabled = !controlsLocked && !settingsOpen,
                        onTransitionRunningChange = { vinylBusy = it },
                        onCommitSkip = { dir ->
                            vinylSkipDir = dir
                            when (dir) {
                                VinylSkipDirection.Next -> onSkipNext()
                                VinylSkipDirection.Previous -> onSkipPrev()
                            }
                        },
                        fullCover = displayPrefs.vinylFullCover,
                        prevEnterSlidePx = prevEnterSlidePx,
                        modifier = mod,
                    )
                }

                Column(
                    Modifier
                        .fillMaxHeight()
                        .width(metaWidth)
                        // 文案避开挖孔；黑胶舞台仍可画进挖孔
                        .padding(start = cutoutStart, top = songMetaTopPad),
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
                    Spacer(Modifier.weight(1f))
                }

                // 单一黑胶：占满左栏，离场/入场可画进左侧挖孔（舞台不圆形裁剪）
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = false },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(metaWidth)
                            .graphicsLayer { clip = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        VinylDisc(
                            Modifier
                                .offset(x = ox, y = oy)
                                .graphicsLayer {
                                    translationY = layoutShiftY
                                    scaleX = vinylScale
                                    scaleY = vinylScale
                                    transformOrigin = TransformOrigin.Center
                                    clip = false
                                }
                                .padding(bottom = vinylBottomPad)
                                .size(disc),
                        )
                    }
                }
            }

            LandscapeProjectionLyrics(
                lines = lines,
                positionMs = positionMs,
                trackDurationMs = durationMs,
                fontScale = displayPrefs.fontScale,
                offsetXDp = displayPrefs.lyricOffsetXDp,
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
                        scaleX = uiScale
                        scaleY = uiScale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .padding(horizontal = chromeSidePad)
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
                    buffering = transportBuffering,
                    controlsLocked = controlsLocked,
                    onTogglePlay = {
                        if (!controlsLocked) {
                            revealControls()
                            onTogglePlay()
                        }
                    },
                    onSkipNext = {
                        if (!controlsLocked) {
                            revealControls()
                            vinylSkipDir = VinylSkipDirection.Next
                            onSkipNext()
                        }
                    },
                    onSkipPrev = {
                        if (!controlsLocked) {
                            revealControls()
                            vinylSkipDir = VinylSkipDirection.Previous
                            onSkipPrev()
                        }
                    },
                    durationMs = durationMs,
                    positionMs = seekPositionMs,
                    sliderDragging = sliderDragging,
                    sliderValue = sliderValue,
                    onSliderDragStart = {
                        if (!controlsLocked) {
                            revealControls()
                            onSliderDragStart()
                        }
                    },
                    onSliderChange = onSliderChange,
                    onSliderDragEnd = {
                        revealControls()
                        onSliderDragEnd()
                    },
                    playbackMode = playbackMode,
                    onCyclePlaybackMode = {
                        if (!controlsLocked) {
                            revealControls()
                            onCyclePlaybackMode()
                        }
                    },
                    portraitSlim = false,
                    landscapeDense = true,
                )
            }

            // 右上：旋转锁定 | 设置（与底部播放条同显隐 / 同底 / 右缘对齐）
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = songMetaTopPad, end = chromeSidePad)
                    .graphicsLayer {
                        alpha = chromeT
                        scaleX = uiScale
                        scaleY = uiScale
                        transformOrigin = TransformOrigin(1f, 0f)
                    },
                horizontalArrangement = Arrangement.spacedBy(NowPlayingChromeIconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NowPlayingRotationLockButton(
                    locked = rotationLocked,
                    onClick = { rotationLock.toggle(activity) },
                )
                NowPlayingSettingsIconButton(
                    onClick = { openSettings() },
                )
            }
        }
        } // hazeSource：播放内容

        // 设置层：从右向左曲线展开；点外部 / 返回收回；无蒙版变暗
        if (settingsT > 0.001f || settingsOpen) {
            NowPlayingSettingsOutsideDismiss(
                onDismiss = { closeSettings() },
                modifier = Modifier.fillMaxSize(),
            )
            BoxWithConstraints(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f),
            ) {
                val panelW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                NowPlayingSettingsSheet(
                    prefs = displayPrefs,
                    onPrefsChange = onDisplayPrefsChange,
                    hazeState = settingsHazeState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            translationX = (1f - settingsT) * panelW
                            scaleX = 0.88f + 0.12f * settingsT
                            alpha = settingsT
                        },
                )
            }
        }
    }
}

@Composable
private fun LyricPreviewBlock(
    lines: List<LrcLine>,
    activeIndex: Int,
    positionMs: Long,
    trackDurationMs: Long,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier,
    lineCount: Int = 2,
) {
    val n = lineCount.coerceIn(1, 4)
    val previewAnimMs = lyricAnimTiming(lines, positionMs, trackDurationMs).durationMs
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
        val focus = lyricFocusIndex(lines, activeIndex)
        val live = lyricIsLive(lines, activeIndex, focus)
        for (offset in 0 until n) {
            val i = (focus + offset).coerceIn(0, lines.lastIndex)
            val line = lines[i]
            val isCurrent = offset == 0
            val alpha by animateFloatAsState(
                targetValue = when {
                    isCurrent && live -> 1f
                    isCurrent -> 0.62f
                    else -> 0.42f
                },
                animationSpec = tween(previewAnimMs, easing = FastOutSlowInEasing),
                label = "prevA$offset",
            )
            Text(
                text = line.text,
                style = TextStyle(
                    color = if (isCurrent) LyricCurrent.copy(alpha = alpha) else LyricDim.copy(alpha = alpha),
                    fontFamily = FontFamily.Serif,
                    fontStyle = if (isCurrent && live) FontStyle.Normal else FontStyle.Italic,
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
    controlsLocked: Boolean = false,
) {
    val maxF = durationMs.toFloat().coerceAtLeast(1f)
    val sliderPos = if (sliderDragging) sliderValue else positionMs.toFloat()
    val displayPosMs = if (sliderDragging) sliderPos.toLong() else positionMs
    val playPulse by animateFloatAsState(
        targetValue = if (isPlaying) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f),
        label = "playPulse",
    )
    val iconTint = if (controlsLocked) {
        Color(0xFF7A8796)
    } else {
        Color(0xFFB8C5D4)
    }
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
                        if (controlsLocked) return@Slider
                        if (!sliderDragging) onSliderDragStart()
                        onSliderChange(v)
                    },
                    onValueChangeFinished = onSliderDragEnd,
                    valueRange = 0f..maxF,
                    enabled = !controlsLocked,
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
