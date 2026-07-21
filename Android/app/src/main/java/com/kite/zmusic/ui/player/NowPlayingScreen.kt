@file:Suppress("UnusedBoxWithConstraintsScope")

package com.kite.zmusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LrcParser
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PlayerDisplayPrefsStore
import com.kite.zmusic.data.TitleAlignMode
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.VinylColorStyle
import com.kite.zmusic.playback.AudioSpectrumBands
import com.kite.zmusic.playback.PlaybackNotice
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.ui.common.UrlImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.unit.lerp as lerpDp

/**
 * 空白区域手势：
 * - 纯点击（相对按下点位移未超 touchSlop）→ [onTap]
 * - 单次按住并明确下拖超过阈值 → [onSwipeDown]
 * - 回调经 [rememberUpdatedState] 更新，避免动画重组重启 pointerInput
 * - 位移相对「按下坐标」计算
 * - Main 通道若子控件（歌词 LazyColumn / 黑胶）已 consume，则本手势放弃退出/点击，
 *   与歌词滚动、黑胶拖动严格隔离
 */
private fun Modifier.nowPlayingBlankGestures(
    dismissThresholdPx: Float,
    onTap: (() -> Unit)?,
    onSwipeDown: () -> Unit,
): Modifier = composed {
    val tapRef = rememberUpdatedState(onTap)
    val swipeRef = rememberUpdatedState(onSwipeDown)
    Modifier.pointerInput(dismissThresholdPx) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val pointerId = down.id
            val start = down.position
            var decidedSwipeDown = false
            var dismissed = false
            var yieldedToChild = false

            while (true) {
                // Initial：仅用于在子控件消费前读取位移（黑胶横滑松手防误触点击）
                val raw = awaitPointerEvent(PointerEventPass.Initial)
                val rawChange = raw.changes.find { it.id == pointerId } ?: break
                val dx = rawChange.position.x - start.x
                val dy = rawChange.position.y - start.y

                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.find { it.id == pointerId } ?: break

                // 歌词列表等已接手 → 不再争抢下滑退出 / 空白点击
                if (change.isConsumed && !decidedSwipeDown) {
                    yieldedToChild = true
                    while (true) {
                        val rest = awaitPointerEvent(PointerEventPass.Main)
                        val c = rest.changes.find { it.id == pointerId } ?: return@awaitEachGesture
                        if (!c.pressed) return@awaitEachGesture
                    }
                }

                if (yieldedToChild) break

                // 退出判定用更大 slop，把常规 touchSlop 留给歌词 LazyColumn 优先认领垂直滚动。
                // 注意：不可用「刚过 touchSlop」永久禁止退出，否则黑胶等非歌词区永远无法下滑退出。
                val dismissSlop = touchSlop * 3.5f
                val downDominant =
                    dy > dismissSlop &&
                        dy > abs(dx) * 1.6f
                if (downDominant) {
                    decidedSwipeDown = true
                    change.consume()
                    if (!dismissed && dy >= dismissThresholdPx) {
                        dismissed = true
                        swipeRef.value()
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
                        !yieldedToChild &&
                        abs(dx) < touchSlop &&
                        abs(dy) < touchSlop
                    ) {
                        tapRef.value?.invoke()
                    }
                    break
                }
            }
        }
    }
}

/**
 * 歌词条带内：子列表未消费的垂直拖动也要吃掉，避免滚到顶/点到行间时
 * 被外层 [nowPlayingBlankGestures] 当成下滑退出。
 * 仅应挂在「歌词本身 + 周围」的条带上，勿挂满整列，否则右侧空白无法收回播放页。
 */
private fun Modifier.consumeUnclaimedVerticalDrag(): Modifier = pointerInput(Unit) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        val start = down.position
        var claimed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.find { it.id == pointerId } ?: return@awaitEachGesture
            if (change.isConsumed && !claimed) {
                while (true) {
                    val rest = awaitPointerEvent(PointerEventPass.Main)
                    val c = rest.changes.find { it.id == pointerId } ?: return@awaitEachGesture
                    if (!c.pressed) return@awaitEachGesture
                }
            }
            val dx = change.position.x - start.x
            val dy = change.position.y - start.y
            if (!claimed && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                if (abs(dy) > abs(dx) * 0.65f) {
                    claimed = true
                    change.consume()
                } else {
                    return@awaitEachGesture
                }
            }
            if (claimed) change.consume()
            if (!change.pressed) return@awaitEachGesture
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
/** 横屏：播放中 / 未播侧句（与 LandscapeCenter / Side 一致） */
private val LyricPlayingLand = Color(0xFFF8FAFC)
private val LyricIdleLand = Color(0xFFDCE6F0)
/** 浏览视觉中心：两色中值，偏亮灰白 */
private val LyricBrowseSelect = lerp(LyricPlayingLand, LyricIdleLand, 0.5f)
private val OrbInk = Color(0xFF090B12)
private val GlassStroke = Color.White.copy(alpha = 0.16f)
private val GlassHi = Color.White.copy(alpha = 0.14f)
private val GlassLo = Color.White.copy(alpha = 0.045f)

/**
 * Gemini 式透光光球：相位线性循环，位移一律用整周期 sin/cos，保证首尾相接无跳变。
 * [activeHalo] 开关经 [Animatable] 过渡（可打断）；
 * 蔷薇=低音/鼓点、淡紫=中音、青蓝=高音 —— **独立响应**（可同时亮）。
 * 频谱按时间常数跟瞄；切歌 / 点选歌词 / 拖动进度均有可打断的压暗→回升，避免硬切。
 */
@Composable
private fun GeminiOrbsBackdrop(
    modifier: Modifier = Modifier,
    activeHalo: Boolean = false,
    spectrum: AudioSpectrumBands = AudioSpectrumBands.ZERO,
    playWhenReady: Boolean = false,
    positionMs: Long = 0L,
    scrubbing: Boolean = false,
    trackId: Long = 0L,
    loadPending: Boolean = false,
) {
    val haloEase = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f)
    val crossEase = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1f)

    val haloGate = remember { Animatable(if (activeHalo) 1f else 0f) }
    LaunchedEffect(activeHalo) {
        val target = if (activeHalo) 1f else 0f
        val distance = abs(target - haloGate.value).coerceIn(0f, 1f)
        val durationMs = (640f * distance).toInt().coerceIn(220, 640)
        haloGate.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = durationMs, easing = haloEase),
        )
    }

    // 播放意图 / 加载：能量门控柔和开闭，避免 seek 缓冲时 isPlaying 闪断造成光效跳变
    val energyGate = remember {
        Animatable(if (playWhenReady && !loadPending) 1f else 0f)
    }
    LaunchedEffect(playWhenReady, loadPending) {
        val target = if (playWhenReady && !loadPending) 1f else 0f
        val down = target < energyGate.value
        energyGate.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = if (down) 520 else 380,
                easing = haloEase,
            ),
        )
    }

    // 切歌：从当前亮度压暗再回升（可被连点打断）
    val trackCross = remember { Animatable(1f) }
    var lastTrackId by remember { mutableLongStateOf(trackId) }
    LaunchedEffect(trackId) {
        if (trackId == 0L) return@LaunchedEffect
        if (lastTrackId == 0L) {
            lastTrackId = trackId
            trackCross.snapTo(1f)
            return@LaunchedEffect
        }
        if (trackId == lastTrackId) return@LaunchedEffect
        lastTrackId = trackId
        trackCross.animateTo(0.05f, tween(340, easing = crossEase))
        trackCross.animateTo(1f, tween(860, easing = crossEase))
    }

    // 点选歌词 / 大幅跳转：浅压暗后回升
    val seekCross = remember { Animatable(1f) }
    var lastSeekPos by remember { mutableLongStateOf(positionMs) }
    var lastSeekTrack by remember { mutableLongStateOf(trackId) }
    LaunchedEffect(positionMs, trackId, scrubbing) {
        if (trackId != lastSeekTrack) {
            lastSeekTrack = trackId
            lastSeekPos = positionMs
            seekCross.snapTo(1f)
            return@LaunchedEffect
        }
        val jump = abs(positionMs - lastSeekPos)
        lastSeekPos = positionMs
        if (scrubbing || jump < 800L) return@LaunchedEffect
        val dip = (0.38f + (1f - (jump / 45_000f).coerceIn(0f, 1f)) * 0.28f)
            .coerceIn(0.38f, 0.66f)
            .coerceAtMost(seekCross.value)
        seekCross.animateTo(dip, tween(200, easing = crossEase))
        seekCross.animateTo(1f, tween(640, easing = crossEase))
    }

    // 拖动进度条：持续压低响应，松手后缓慢恢复
    val scrubGate = remember { Animatable(1f) }
    LaunchedEffect(scrubbing) {
        if (scrubbing) {
            scrubGate.animateTo(0.48f, tween(220, easing = haloEase))
        } else {
            scrubGate.animateTo(1f, tween(560, easing = haloEase))
        }
    }

    var phaseA by remember { mutableFloatStateOf(0f) }
    var phaseB by remember { mutableFloatStateOf(0f) }
    var phaseC by remember { mutableFloatStateOf(0f) }
    var lowT by remember { mutableFloatStateOf(0f) }
    var midT by remember { mutableFloatStateOf(0f) }
    var highT by remember { mutableFloatStateOf(0f) }
    // 频谱目标再一层慢跟，视觉比音频包络更柔
    var lowS by remember { mutableFloatStateOf(0f) }
    var midS by remember { mutableFloatStateOf(0f) }
    var highS by remember { mutableFloatStateOf(0f) }

    val gateRef = rememberUpdatedState(haloGate.value)
    val energyRef = rememberUpdatedState(energyGate.value)
    val spectrumRef = rememberUpdatedState(spectrum)
    val crossRef = rememberUpdatedState(trackCross.value)
    val seekRef = rememberUpdatedState(seekCross.value)
    val scrubRef = rememberUpdatedState(scrubGate.value)

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L) {
                    val dt = ((now - last).coerceIn(0L, 48L)) / 1000f
                    val gate = gateRef.value
                    val speed = lerp(1f, 1.28f, gate)
                    phaseA = (phaseA + dt / 22f * speed) % 1f
                    phaseB = (phaseB + dt / 31f * speed) % 1f
                    phaseC = (phaseC + dt / 17f * speed) % 1f

                    val presence = (
                        gate *
                            energyRef.value *
                            crossRef.value *
                            seekRef.value *
                            scrubRef.value
                        ).coerceIn(0f, 1f)
                    val sp = spectrumRef.value
                    // 频谱源慢跟：抑制切歌 / seek 后突发尖峰
                    fun lag(cur: Float, target: Float, tau: Float): Float {
                        val a = (1f - kotlin.math.exp(-dt / tau)).coerceIn(0f, 1f)
                        return cur + (target - cur) * a
                    }
                    lowS = lag(lowS, sp.low.coerceIn(0f, 1f), 0.11f)
                    midS = lag(midS, sp.mid.coerceIn(0f, 1f), 0.13f)
                    highS = lag(highS, sp.high.coerceIn(0f, 1f), 0.10f)

                    val tLow = (lowS * presence).coerceIn(0f, 1f)
                    val tMid = (midS * presence).coerceIn(0f, 1f)
                    val tHigh = (highS * presence).coerceIn(0f, 1f)
                    // 显示层：起音约 180ms，衰减约 480ms
                    fun follow(cur: Float, target: Float): Float {
                        val tau = if (target > cur) 0.18f else 0.48f
                        return lag(cur, target, tau)
                    }
                    lowT = follow(lowT, tLow)
                    midT = follow(midT, tMid)
                    highT = follow(highT, tHigh)
                }
                last = now
            }
        }
    }

    val gate = haloGate.value
    val presence = (
        gate *
            energyGate.value *
            trackCross.value *
            seekCross.value *
            scrubGate.value
        ).coerceIn(0f, 1f)
    // 活跃底光随综合 presence 柔和变化
    val baseScale = if (gate > 0.05f) {
        lerp(1f, 0.55f, presence)
    } else {
        1f
    }

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
        val pulse = 1f + 0.12f * sin(a)
        val pulseInv = 1f + 0.10f * sin(a + Math.PI.toFloat())

        orb(
            cx = w * (0.22f + 0.14f * cos(a)),
            cy = h * (0.32f + 0.16f * sin(a)),
            radius = minOf(w, h) * 0.5f * pulse * (1f + 0.18f * lowT),
            color = Color(0xFFE8A0C8),
            alpha = (0.22f * baseScale + 0.58f * lowT).coerceIn(0f, 0.92f),
        )
        orb(
            cx = w * (0.72f + 0.12f * cos(b + 1.2f)),
            cy = h * (0.68f + 0.14f * sin(b + 0.4f)),
            radius = minOf(w, h) * 0.58f * (0.96f + 0.04f * sin(b)) * (1f + 0.16f * highT),
            color = Color(0xFF6EB8FF),
            alpha = (0.20f * baseScale + 0.55f * highT).coerceIn(0f, 0.90f),
        )
        orb(
            cx = w * (0.58f + 0.11f * sin(c)),
            cy = h * (0.40f + 0.17f * cos(c)),
            radius = minOf(w, h) * 0.44f * pulseInv * (1f + 0.14f * midT),
            color = Color(0xFFB8A0FF),
            alpha = (0.18f * baseScale + 0.52f * midT).coerceIn(0f, 0.88f),
        )
        val ambience = maxOf(lowT, midT, highT)
        orb(
            cx = w * (0.38f + 0.26f * sin(b)),
            cy = h * (0.88f + 0.04f * cos(b * 2f)),
            radius = w * 0.46f * (0.94f + 0.06f * sin(c)) * (1f + 0.08f * ambience),
            color = Color(0xFFFFC9A8),
            alpha = 0.18f * baseScale + 0.22f * ambience,
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
/** 松手轻对齐：短促、低存在感，避免「磁吸抢走」 */
private val LyricSnapEasing = CubicBezierEasing(0.22f, 0.1f, 0.18f, 1f)
private val LyricSnapScrollSpec = tween<Float>(durationMillis = 220, easing = LyricSnapEasing)
private val LyricFollowScrollSpec = tween<Float>(durationMillis = 420, easing = LyricSoftEasing)
/** 选句退出回正：快速柔和（约跟跟滚同级），按距离略伸缩 */
private fun lyricResumeScrollSpec(distancePx: Float) = tween<Float>(
    durationMillis = ((abs(distancePx) / 2600f) * 1000f)
        .toInt()
        .coerceIn(300, 560),
    easing = LyricSoftEasing,
)

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
    maxLines: Int = 6,
    fillWidth: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    /** 为 true 时首次合成直接亮起，跳过入场（选句退出恢复时防闪烁） */
    instantAppear: Boolean = false,
    /** 选句态：焦点/文案可更新，但禁止翻页入场/出场 */
    freezeTransitions: Boolean = false,
) {
    var shownFocus by remember {
        mutableIntStateOf(if (instantAppear || freezeTransitions) focus else -1)
    }
    var shownText by remember {
        mutableStateOf(if (instantAppear || freezeTransitions) text else "")
    }
    var lastSpanMs by remember { mutableLongStateOf(lineSpanMs) }
    val enterAlpha = remember {
        Animatable(if (instantAppear || freezeTransitions) 1f else 0f)
    }
    val enterLift = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(focus, text, animMs, lineSpanMs, instantAppear, freezeTransitions) {
        if (freezeTransitions) {
            shownFocus = focus
            shownText = text
            lastSpanMs = lineSpanMs
            enterAlpha.snapTo(1f)
            enterLift.snapTo(0f)
            return@LaunchedEffect
        }
        val phaseMs = animMs.coerceIn(220, 420)
        val liftPx = with(density) { 10.dp.toPx() }
        if (focus == shownFocus && shownText == text) {
            enterAlpha.snapTo(1f)
            enterLift.snapTo(0f)
            lastSpanMs = lineSpanMs
            return@LaunchedEffect
        }
        // 选句退出后首次挂回：不要再播一遍翻页入场
        if (shownFocus < 0 && instantAppear) {
            shownFocus = focus
            shownText = text
            lastSpanMs = lineSpanMs
            enterAlpha.snapTo(1f)
            enterLift.snapTo(0f)
            return@LaunchedEffect
        }
        val skipExit = shownFocus < 0 || lastSpanMs < LyricSkipExitSpanMs

        if (!skipExit && enterAlpha.value > 0.04f) {
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
        softWrap = true,
        overflow = overflow,
        style = style,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
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
    onPlayQueueIndex: (Int) -> Unit = {},
    /** 横屏额外左侧 inset（本页已无 Dock，通常为 0） */
    landscapeStartInset: Dp = 0.dp,
    spectrum: AudioSpectrumBands = AudioSpectrumBands.ZERO,
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

    val app = context.applicationContext as ZMusicApplication
    val userClient = remember { NcmUserClient() }
    val likedRepo = app.likedPlaylistRepository
    val likeScope = rememberCoroutineScope()
    // 首帧即读缓存，避免切歌先闪「未喜欢」
    var trackLiked by remember(track.id) {
        mutableStateOf(likedRepo.isLiked(track.id) ?: false)
    }
    var likeBusy by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        likedRepo.isLiked(track.id)?.let { trackLiked = it }

        // 红心歌单缓存到达 / 本地点赞后同步 UI
        launch {
            likedRepo.snapshot.collect { snap ->
                if (snap != null) {
                    trackLiked = snap.likedIds.contains(track.id)
                }
            }
        }

        // 仍未知时再网络检查，并写入缓存供邻曲/回切复用
        if (likedRepo.isLiked(track.id) == null) {
            val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
            if (cookie.isNotEmpty()) {
                try {
                    val json = userClient.songLikeCheck(listOf(track.id), cookie)
                    val liked = NcmLibraryParse.isTrackLiked(json, track.id)
                    trackLiked = liked
                    likedRepo.recordLikeStatus(track, liked)
                } catch (_: Exception) {
                    // 检查失败保持未喜欢态，不打断播放页
                }
            }
        }
    }

    fun toggleTrackLike() {
        if (likeBusy || state.loadPending) return
        val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isEmpty()) return
        val next = !trackLiked
        trackLiked = next
        likedRepo.applyLocalLike(track, liked = next)
        likeBusy = true
        likeScope.launch {
            try {
                val json = userClient.likeSong(track.id, like = next, cookie = cookie)
                if (NcmJson.apiCode(json) != 200) {
                    trackLiked = !next
                    likedRepo.applyLocalLike(track, liked = !next, scheduleSync = false)
                }
            } catch (_: Exception) {
                trackLiked = !next
                likedRepo.applyLocalLike(track, liked = !next, scheduleSync = false)
            } finally {
                likeBusy = false
            }
        }
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
        (if (isLandscape) 120.dp else 112.dp).toPx()
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
                GeminiOrbsBackdrop(
                    modifier = Modifier.fillMaxSize(),
                    activeHalo = displayPrefs.activeHalo,
                    spectrum = spectrum,
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
            Box(Modifier.fillMaxSize().background(bg))
        }
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
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    },
                )
                .padding(
                    start = if (isLandscape) 0.dp else (landscapeStartInset + 12.dp),
                    end = if (isLandscape) 0.dp else 12.dp,
                    top = if (isLandscape) 0.dp else 6.dp,
                    bottom = if (isLandscape) 0.dp else 8.dp,
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
                    onToggleLike = ::toggleTrackLike,
                    durationMs = duration,
                    sourceTitle = srcTitle,
                    // 横屏：歌单名点击曾直接关全屏（像左上角隐形退出区）；改由右上角退出钮
                    onSourceClick = null,
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
                    notice = state.notice,
                    transportWakeToken = state.transportWakeToken,
                    onSeek = onSeek,
                    queue = state.queue,
                    queueIndex = state.index,
                    onPlayQueueIndex = onPlayQueueIndex,
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
                    playWhenReady = state.playWhenReady,
                    buffering = state.loadPending,
                    onTogglePlay = onTogglePlay,
                    onSkipNext = onSkipNext,
                    onSkipPrev = onSkipPrev,
                    playbackMode = state.playbackMode,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    trackLiked = trackLiked,
                    onToggleLike = ::toggleTrackLike,
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
                        maxLines = 4,
                        overflow = TextOverflow.Clip,
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
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Clip,
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
            isPlaying = playWhenReady,
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
            trackLiked = trackLiked,
            onToggleLike = onToggleLike,
            portraitSlim = true,
            landscapeDense = false,
        )
    }
}

/** 横屏歌词：可手动滑动浏览；浏览时停自动跟滚；点选 seek；高度由已播/待播句数决定。 */
@Composable
private fun LandscapeProjectionLyrics(
    lines: List<LrcLine>,
    positionMs: Long,
    trackDurationMs: Long,
    fontScale: Float = 1f,
    lineSpacingDp: Float = 10f,
    playedCount: Int = 2,
    upcomingCount: Int = 2,
    offsetXDp: Float = 0f,
    dynamicLyrics: Boolean = false,
    vinylLeftInset: Dp = 0.dp,
    onSeekToMs: (Long) -> Unit,
    /** 外部点击等：递增后立即退出浏览并滚回播放行 */
    resumeScrollToken: Int = 0,
    /** 选句进度 0..1：原列表水平滑入挖孔 + 样式消解 */
    selectProgress: Float = 0f,
    selectGeom: LyricSelectGeom? = null,
    /** 歌词列左缘相对播放页根（与 geom 同一坐标系） */
    lyricsColStartDp: Dp = 0.dp,
    selectedIndices: Set<Int> = emptySet(),
    onToggleSelect: ((Int) -> Unit)? = null,
    onLongPressLine: ((index: Int) -> Unit)? = null,
    onBandCenterPx: ((cx: Float, cy: Float) -> Unit)? = null,
    /** 选句时歌词区附着与操作区相同的磨砂；取消后移除 */
    selectHazeState: HazeState? = null,
    /** 选句是否处于打开意图（区别于 progress，用于玻璃入/出时机） */
    selectOpen: Boolean = false,
    playingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    playedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
    /** 可交互歌词 band 的布局坐标（相对播放页根），供样式克隆开场对齐 */
    onLyricBandCoords: ((LayoutCoordinates) -> Unit)? = null,
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
    val playFocus = lyricFocusIndex(lines, animActive)
    val live = lyricIsLive(lines, animActive, playFocus)
    val fs = fontScale.coerceIn(PlayerDisplayPrefs.FONT_MIN, PlayerDisplayPrefs.FONT_MAX)
    val linePad = lineSpacingDp
        .coerceIn(PlayerDisplayPrefs.LINE_SPACING_MIN, PlayerDisplayPrefs.LINE_SPACING_MAX)
        .dp
    val played = playedCount.coerceIn(
        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
        PlayerDisplayPrefs.LYRIC_AROUND_MAX,
    )
    val upcoming = upcomingCount.coerceIn(
        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
        PlayerDisplayPrefs.LYRIC_AROUND_MAX,
    )
    val visibleCount = played + 1 + upcoming
    val animMs = timing.durationMs

    val dynamicT = remember { Animatable(if (dynamicLyrics) 1f else 0f) }
    LaunchedEffect(dynamicLyrics) {
        val target = if (dynamicLyrics) 1f else 0f
        val distance = abs(target - dynamicT.value).coerceIn(0f, 1f)
        val durationMs = (480f * distance).toInt().coerceIn(160, 480)
        dynamicT.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f),
            ),
        )
    }

    val listState = rememberLazyListState()
    var browsing by remember { mutableStateOf(false) }
    /** 首滑手势进行中：保持手势 overlay，避免 browsing=true 后中途卸掉导致首次滚动失败 */
    var dragSession by remember { mutableStateOf(false) }
    var idleGen by remember { mutableIntStateOf(0) }
    var suppressBrowseDetect by remember { mutableStateOf(false) }
    /** 选句退出回滚中：抑制浏览高亮与跟滚抢位 */
    var resumeSettling by remember { mutableStateOf(false) }
    /** 选句打开时冻结的播放行，避免播放推进触发入场动画 */
    var selectFrozenFocus by remember { mutableIntStateOf(-1) }
    /** 递增以作废进行中的跟滚，避免与用户拖动手势抢滚动 */
    var followGen by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val onLongPressLineUpdated by rememberUpdatedState(onLongPressLine)
    val onBandCenterPxUpdated by rememberUpdatedState(onBandCenterPx)
    val onLyricBandCoordsUpdated by rememberUpdatedState(onLyricBandCoords)
    val selectProgressUpdated by rememberUpdatedState(selectProgress)
    val selectT = selectProgress.coerceIn(0f, 1f)
    val selectMode = selectT > 0.001f
    val selectInteractive = selectT > 0.85f
    /**
     * 长按打开选句后，同一手指抬起可能被重组后的 combinedClickable 当成 onClick，
     * 从而误选一行；等全部触点抬起后再允许点选。
     */
    var selectToggleArmed by remember { mutableStateOf(false) }
    // 歌词玻璃：进入晚起（等滑移一段），退出尽快消掉（避免直角雾块滞留）
    val lyricGlassAlpha = remember { Animatable(0f) }
    LaunchedEffect(selectOpen) {
        if (selectOpen) {
            selectToggleArmed = false
            selectFrozenFocus = playFocus
            delay(210)
            lyricGlassAlpha.animateTo(
                1f,
                tween(durationMillis = 280, easing = LyricSoftEasing),
            )
        } else {
            selectToggleArmed = false
            // 退出不 snap：显示 alpha 改跟 selectT，与操作区同步收
            if (selectT <= 0.001f) {
                lyricGlassAlpha.snapTo(0f)
            }
        }
    }
    LaunchedEffect(selectOpen, selectT) {
        if (!selectOpen && selectT <= 0.001f) {
            lyricGlassAlpha.snapTo(0f)
        }
    }
    LaunchedEffect(selectMode) {
        if (selectMode) {
            followGen++
            browsing = true
            dragSession = false
            if (selectFrozenFocus < 0) selectFrozenFocus = playFocus
        } else {
            selectFrozenFocus = -1
        }
    }
    // 选句期间视觉锁定打开时的播放行；回滚目标仍用实时 playFocus
    val visualPlayFocus =
        if (selectMode && selectFrozenFocus >= 0) selectFrozenFocus else playFocus

    // 槽高 = 字高 + 行间距×2；间距=0 时贴紧，调大则行距与区域高度同步变大（仍恰好 N 行）
    val slotHeight = (38f * fs).dp + linePad * 2
    val bandHeight = slotHeight * visibleCount
    val haloBleed = 96.dp
    // 上下垫白，使任意一行（含仅 1～2 行）都能滚到视口绝对垂直中心
    val slotHeightPx = with(density) { slotHeight.roundToPx() }
    val bandHeightPx = with(density) { bandHeight.roundToPx() }
    val centerPadPx = ((bandHeightPx - slotHeightPx) / 2).coerceAtLeast(0)
    val centerPad = with(density) { centerPadPx.toDp() }

    val browseCenterIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf playFocus
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - mid)
            }?.index ?: playFocus
        }
    }

    val span = lyricLineSpanMs(lines, playFocus, trackDurationMs)
    val enableHalo = live && span >= 320L
    val lineStart = lines.getOrNull(playFocus)?.timeMs ?: 0L
    val rawProgress = if (enableHalo) {
        ((positionMs - lineStart).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val targetHalo = if (enableHalo) {
        lyricHaloStrength(rawProgress, span)
    } else {
        0f
    }
    val halo by animateFloatAsState(
        targetValue = targetHalo,
        animationSpec = tween(
            durationMillis = if (targetHalo > 0.02f) 520 else 420,
            easing = LyricSoftEasing,
        ),
        label = "landLyricHalo",
    )
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

    suspend fun scrollToCenteredIndex(
        index: Int,
        animated: Boolean,
        /** 松手轻对齐用稍短缓动；跟滚/回播放行用稍柔和的过渡 */
        softSnap: Boolean = false,
        /** 选句退出：快速柔和回正，禁止先顶对齐再下移 */
        resumeSoft: Boolean = false,
    ) {
        val gen = followGen
        suppressBrowseDetect = true
        try {
            val target = index.coerceIn(0, lines.lastIndex)

            fun viewportSize(): Int {
                val info = listState.layoutInfo
                return (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
            }

            /** 将 [target] 落到视口垂直中心；仅用于瞬时落位，不做顶对齐 */
            suspend fun jumpToCentered(itemSizeHint: Int = slotHeightPx) {
                val vs = viewportSize()
                val size = itemSizeHint.coerceIn(1, vs)
                // 正数 offset：item 顶边距视口顶 (vs-size)/2，中心对齐；避免 scrollToItem 默认 0 顶贴齐
                val offset = ((vs - size) / 2).coerceAtLeast(0)
                listState.scrollToItem(target, scrollOffset = offset)
            }

            suspend fun deltaToTarget(): Float? {
                val info = listState.layoutInfo
                val visible = info.visibleItemsInfo
                if (visible.isEmpty()) return null
                val viewportCenter =
                    (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val item = visible.firstOrNull { it.index == target }
                return if (item != null) {
                    (item.offset + item.size / 2f) - viewportCenter
                } else {
                    // 估距：固定用 slot 高，避免选句 cell 高估导致冲过目标再折返
                    val step = slotHeightPx.toFloat().coerceAtLeast(1f)
                    val anchor = visible.minByOrNull { abs(it.index - target) } ?: visible.first()
                    val approxCenter =
                        anchor.offset + anchor.size / 2f + (target - anchor.index) * step
                    approxCenter - viewportCenter
                }
            }

            if (resumeSoft && animated) {
                // 等选句 morph 基本结束，行高稳定后再一次滚到位，杜绝「冲到上方再折返」
                var waitFrames = 0
                while (selectProgressUpdated > 0.04f && waitFrames++ < 90) {
                    if (followGen != gen) return
                    withFrameMillis { }
                }
                if (followGen != gen) return

                var delta = deltaToTarget()
                if (delta == null ||
                    listState.layoutInfo.visibleItemsInfo.none { it.index == target }
                ) {
                    jumpToCentered()
                    withFrameMillis { }
                    if (followGen != gen) return
                    delta = deltaToTarget()
                }
                if (delta != null && abs(delta) > 1.5f) {
                    // 只允许一趟动画；残余误差瞬时补齐，避免第二趟反向动画
                    listState.animateScrollBy(
                        delta,
                        animationSpec = lyricResumeScrollSpec(delta),
                    )
                    if (followGen != gen) return
                    withFrameMillis { }
                    val fine = deltaToTarget()
                    if (fine != null && abs(fine) > 1.5f) {
                        listState.scrollBy(fine)
                    }
                }
                return
            }

            val scrollSpec = when {
                softSnap -> LyricSnapScrollSpec
                else -> LyricFollowScrollSpec
            }
            // 先保证目标行可见，再按像素差滚到视口绝对垂直中心。
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
                if (animated) {
                    val approxDelta = deltaToTarget()
                    if (approxDelta != null && abs(approxDelta) > 1f) {
                        listState.animateScrollBy(approxDelta, animationSpec = scrollSpec)
                    }
                    if (followGen != gen) return
                    if (listState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
                        jumpToCentered()
                    }
                } else {
                    jumpToCentered()
                }
            }
            if (followGen != gen) return
            val delta = deltaToTarget() ?: return
            if (abs(delta) > 1f) {
                if (animated) {
                    listState.animateScrollBy(delta, animationSpec = scrollSpec)
                } else {
                    listState.scrollBy(delta)
                }
            }
        } finally {
            // 无论是否被 followGen 打断，都必须清掉抑制，否则浏览检测永久失效、跟滚会乱抢
            suppressBrowseDetect = false
        }
    }

    suspend fun scrollToPlayFocus(animated: Boolean, resumeSoft: Boolean = false) {
        scrollToCenteredIndex(playFocus, animated, resumeSoft = resumeSoft)
    }

    /** 仅当偏离中心较多时做一次轻对齐；接近则不动，去掉强磁吸感 */
    suspend fun snapToFullLines() {
        val info = listState.layoutInfo
        if (info.visibleItemsInfo.isEmpty()) return
        val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val closest = info.visibleItemsInfo.minByOrNull { item ->
            abs((item.offset + item.size / 2) - mid)
        } ?: return
        val itemMid = closest.offset + closest.size / 2
        val threshold = (slotHeightPx * 0.42f).coerceAtLeast(18f)
        if (abs(itemMid - mid) <= threshold) return
        scrollToCenteredIndex(closest.index, animated = true, softSnap = true)
    }

    fun exitBrowseAndFollow(animated: Boolean = true) {
        dragSession = false
        browsing = false
        scope.launch { scrollToPlayFocus(animated) }
    }

    /** 选句退出：等 morph 稳定后一趟回正，再退浏览态 */
    suspend fun resumeFromSelectToPlayFocus() {
        dragSession = false
        followGen++
        val gen = followGen
        resumeSettling = true
        try {
            scrollToPlayFocus(animated = true, resumeSoft = true)
        } finally {
            if (followGen == gen) {
                browsing = false
                resumeSettling = false
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { inProgress ->
                if (suppressBrowseDetect) return@collect
                if (inProgress) {
                    if (!dragSession) browsing = true
                } else if (browsing) {
                    idleGen++
                    // 不再每次松手强行磁吸；仅轻微对齐明显错位
                    snapToFullLines()
                }
            }
    }

    LaunchedEffect(playFocus, browsing, lines.size, centerPadPx, selectMode) {
        if (!browsing && !selectMode && !resumeSettling) {
            scrollToPlayFocus(animated = true)
        }
    }

    LaunchedEffect(lines) {
        if (selectMode) return@LaunchedEffect
        browsing = false
        scrollToPlayFocus(animated = false)
    }

    LaunchedEffect(browsing, idleGen, selectMode) {
        if (!browsing || selectMode || resumeSettling) return@LaunchedEffect
        delay(5_500)
        // 仍在滚则继续等，避免惯性滚动中途被拽回
        while (listState.isScrollInProgress) {
            delay(160)
        }
        delay(320)
        if (browsing && !selectMode && !resumeSettling && !listState.isScrollInProgress) {
            exitBrowseAndFollow(animated = true)
        }
    }

    LaunchedEffect(resumeScrollToken) {
        if (resumeScrollToken > 0) {
            resumeFromSelectToPlayFocus()
        }
    }

    // 选句进入：不滚动列表，仅水平滑入挖孔，避免丢焦点

    BoxWithConstraints(
        modifier.fillMaxSize(),
    ) {
        val colW = maxWidth
        val t = dynamicT.value
        val centerX = colW / 2 + offsetXDp.dp
        val leftLimit = lerpDp(0.dp, vinylLeftInset.coerceIn(0.dp, colW), t)
        val halfDyn = minOf(
            (centerX - leftLimit).coerceAtLeast(0.dp),
            (colW - centerX).coerceAtLeast(0.dp),
        )
        val dynW = (halfDyn * 2).coerceAtLeast(48.dp)
        val dynStart = centerX - dynW / 2
        val boxW = lerpDp(colW, dynW, t)
        val startX = lerpDp(offsetXDp.dp, dynStart, t)
        val geom = selectGeom
        val targetBandH = geom?.listHeight ?: bandHeight
        val targetListW = geom?.listWidth ?: maxOf(boxW, 48.dp)
        val morphBandH = lerpDp(bandHeight, targetBandH, selectT)
        // 槽高 / 中心垫保持不变，避免 LazyColumn 重排丢焦点
        val morphListW = lerpDp(boxW.coerceAtLeast(48.dp), targetListW, selectT)
        // 列内坐标：源中心（个性化 X）→ 挖孔水平中心
        val sourceCenterLocal = startX + boxW.coerceAtLeast(48.dp) / 2
        val targetCenterLocal = if (geom != null) {
            geom.listCenterX - lyricsColStartDp
        } else {
            sourceCenterLocal
        }
        val animCenterLocal = lerpDp(sourceCenterLocal, targetCenterLocal, selectT)
        val animLeft = animCenterLocal - morphListW / 2
        // Y：整块平移到挖孔垂直中心（列内居中 → 挖孔中心），不滚动列表
        val shiftYpx = if (geom != null) {
            with(density) { ((geom.listCenterY - maxHeight / 2) * selectT).toPx() }
        } else {
            0f
        }

        val playItem = listState.layoutInfo.visibleItemsInfo.find { it.index == playFocus }
        val haloCenterYRatio = if (playItem != null) {
            val mid = playItem.offset + playItem.size / 2f
            val vh = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)
                .coerceAtLeast(1)
            (mid / vh.toFloat()).coerceIn(0f, 1f)
        } else {
            0.5f
        }

        // 选句推进时收掉光晕外扩，最终高度 = 挖孔高，避免右侧玻璃贯穿全屏
        val bandHostH = morphBandH + haloBleed * 2 * (1f - selectT)
        Box(
            Modifier
                .height(bandHostH)
                .width(morphListW)
                .align(Alignment.CenterStart)
                .offset(x = animLeft)
                .graphicsLayer {
                    // 选句态裁在挖孔高度内，避免玻璃/触控垫上下溢出
                    clip = selectT > 0.35f
                    translationY = shiftYpx
                }
                .onGloballyPositioned { coords ->
                    if (selectT < 0.01f) {
                        val c = coords.positionInRoot()
                        onBandCenterPxUpdated?.invoke(
                            c.x + coords.size.width / 2f,
                            c.y + coords.size.height / 2f,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 选句：歌词孔内直角玻璃，与外壳挖孔贴合（同款样式，无圆角以免漏风）
            val glassA = if (selectOpen) lyricGlassAlpha.value else selectT
            if (glassA > 0.01f && selectHazeState != null) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .height(morphBandH)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = glassA }
                        .clip(RectangleShape)
                        .hazeEffect(state = selectHazeState, style = LyricSelectGlassStyle) {
                            blurRadius = 72.dp
                            noiseFactor = 0.10f
                        },
                )
            }
            if (drawnHalo > 0.01f && selectT < 0.98f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            clip = false
                            alpha = 1f - selectT
                        }
                        .drawBehind {
                            val bleedPx = haloBleed.toPx() * (1f - selectT)
                            val bandPx = morphBandH.toPx()
                            val cx = size.width / 2f
                            val cy = bleedPx + bandPx * haloCenterYRatio
                            val radius = size.maxDimension * (0.42f + 0.08f * drawnHalo)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        CyanSoft.copy(alpha = 0.11f * drawnHalo),
                                        Color.White.copy(alpha = 0.04f * drawnHalo),
                                        Color.Transparent,
                                    ),
                                    center = Offset(cx, cy),
                                    radius = radius,
                                ),
                                radius = radius,
                                center = Offset(cx, cy),
                            )
                        },
                )
            }

            // 歌词列尽量用满可用宽度，长句才能在列内折行；短句仍居中显示
            val maxLyricW = morphListW.coerceAtLeast(48.dp)
            // 正常态触控/列表宽收为 0.85，减少无歌词空白误触滚动；选句挖孔仍用满宽
            val interactiveLyricW = if (selectMode) maxLyricW else maxLyricW * 0.85f
            val followFling = ScrollableDefaults.flingBehavior()
            // 选句收掉上下触控垫，高度与挖孔一致
            val lyricTouchPad = 28.dp * (1f - selectT)
            val lyricTouchPadPx = with(density) { lyricTouchPad.roundToPx() }

            Box(
                Modifier
                    .height(morphBandH + lyricTouchPad * 2)
                    .width(interactiveLyricW)
                    .align(Alignment.Center)
                    .onGloballyPositioned { coords ->
                        // 仅跟滚态上报：这是真歌词可见块，样式克隆开场必须对齐此处
                        if (selectT < 0.01f) {
                            onLyricBandCoordsUpdated?.invoke(coords)
                        }
                    }
                    // 打开选句后：等打开那次长按的手指抬起，再允许点选
                    .pointerInput(selectOpen) {
                        if (!selectOpen) return@pointerInput
                        awaitPointerEventScope {
                            if (currentEvent.changes.any { it.pressed }) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                        selectToggleArmed = true
                    }
                    // 跟滚态：拖动中只置 dragSession，松手再置 browsing。
                    // browsing 进 key，但不会在首滑中途重启 pointerInput。
                    .pointerInput(browsing, selectMode, lines.size, slotHeightPx, lyricTouchPadPx) {
                        if (browsing || selectMode) return@pointerInput
                        val touchSlop = viewConfiguration.touchSlop
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId = down.id
                            val start = down.position
                            var lastY = start.y
                            var dragging = false
                            var flingLaunched = false
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)

                            fun hitLyricIndex(localY: Float): Int? {
                                val yInList = localY - lyricTouchPadPx
                                return listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { info ->
                                        yInList >= info.offset && yInList < info.offset + info.size
                                    }
                                    ?.index
                            }

                            fun beginDrag(fromY: Float, change: PointerInputChange?) {
                                dragging = true
                                followGen++
                                // 拖动期间抑制 isScrollInProgress→browsing，避免中途重启本 pointerInput
                                suppressBrowseDetect = true
                                dragSession = true
                                change?.consume()
                                lastY = fromY
                            }

                            try {
                                var slopChange: PointerInputChange? = null
                                val race = withTimeoutOrNull(longPressTimeout) {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.find { it.id == pointerId }
                                            ?: return@withTimeoutOrNull "cancel"
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        if (!change.pressed) return@withTimeoutOrNull "up"
                                        if (change.isConsumed) return@withTimeoutOrNull "cancel"
                                        val dy = change.position.y - start.y
                                        val dx = change.position.x - start.x
                                        if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 0.75f) {
                                            slopChange = change
                                            return@withTimeoutOrNull "drag"
                                        }
                                    }
                                    @Suppress("UNREACHABLE_CODE")
                                    "cancel"
                                }

                                when (race) {
                                    null -> {
                                        val index = hitLyricIndex(start.y)
                                        if (index != null && onLongPressLineUpdated != null) {
                                            followGen++
                                            browsing = true
                                            dragSession = false
                                            onLongPressLineUpdated?.invoke(index)
                                            waitForUpOrCancellation()
                                            return@awaitEachGesture
                                        }
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.find { it.id == pointerId }
                                                ?: return@awaitEachGesture
                                            if (!change.pressed) return@awaitEachGesture
                                            val dy = change.position.y - start.y
                                            val dx = change.position.x - start.x
                                            if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 0.75f) {
                                                beginDrag(change.position.y, change)
                                                break
                                            }
                                        }
                                    }
                                    "drag" -> {
                                        beginDrag(
                                            slopChange?.position?.y ?: start.y,
                                            slopChange,
                                        )
                                    }
                                    else -> return@awaitEachGesture
                                }

                                while (dragging) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.find { it.id == pointerId }
                                        ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    val step = change.position.y - lastY
                                    lastY = change.position.y
                                    listState.dispatchRawDelta(-step)
                                    change.consume()
                                    if (!change.pressed) {
                                        val velocityY = tracker.calculateVelocity().y
                                        flingLaunched = true
                                        browsing = true
                                        suppressBrowseDetect = false
                                        scope.launch {
                                            try {
                                                listState.scroll {
                                                    with(followFling) {
                                                        performFling(-velocityY)
                                                    }
                                                }
                                            } finally {
                                                dragSession = false
                                                idleGen++
                                            }
                                        }
                                        break
                                    }
                                }
                            } finally {
                                if (dragSession && !flingLaunched) {
                                    if (dragging) {
                                        browsing = true
                                    }
                                    dragSession = false
                                    suppressBrowseDetect = false
                                    if (dragging) {
                                        idleGen++
                                        scope.launch { snapToFullLines() }
                                    }
                                }
                            }
                        }
                    }
                    // 垫区未滚列表时吞垂直滑，避免误触下滑退出；列表自己能消费时不会抢走
                    .consumeUnclaimedVerticalDrag(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .height(morphBandH)
                        .width(interactiveLyricW),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RectangleShape),
                        contentPadding = PaddingValues(vertical = centerPad),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // 浏览 / 选句可滚；首滑由父级 pointerInput 抢跟滚
                        userScrollEnabled = (browsing || selectInteractive) && !dragSession,
                    ) {
                        itemsIndexed(
                            items = lines,
                            key = { index, line -> "${line.timeMs}_$index" },
                        ) { index, line ->
                            // 选句锁定打开时的播放行，避免播放推进换行入场；回滚仍跟实时 playFocus
                            val inSelect = selectT > 0.001f
                            val isPlayingLine = index == visualPlayFocus
                            val isBrowseCenter =
                                !inSelect &&
                                    browsing &&
                                    !resumeSettling &&
                                    index == browseCenterIndex &&
                                    !isPlayingLine
                            val selected = inSelect && index in selectedIndices
                            val rowHeight = if (geom != null) {
                                lerpDp(slotHeight, geom.cellHeight, selectT)
                            } else {
                                slotHeight
                            }
                            LandscapeScrollLyricLine(
                                text = line.text,
                                lineKey = index,
                                isPlayingLine = isPlayingLine,
                                isBrowseCenter = isBrowseCenter,
                                live = live && isPlayingLine && selectT < 0.98f,
                                // 选句中仍保留已播放判定，斜体随 selectT 过渡，禁止进/出瞬间掐掉
                                played = animActive >= 0 && index < animActive,
                                distanceFromPlay = if (inSelect) {
                                    1
                                } else {
                                    abs(index - visualPlayFocus)
                                },
                                lines = lines,
                                focus = visualPlayFocus,
                                trackDurationMs = trackDurationMs,
                                fontScale = fs,
                                lineSpacing = 0.dp,
                                slotHeight = rowHeight,
                                animMs = animMs,
                                browsing = browsing || selectMode,
                                selected = selected,
                                selectStyleT = selectT,
                                fixedSelectRow = selectT > 0.15f,
                                freezeLineTransitions = inSelect || resumeSettling,
                                instantAppear = resumeSettling && index == playFocus,
                                playingStyle = playingStyle,
                                playedStyle = playedStyle,
                                unplayedStyle = unplayedStyle,
                                onSeekClick = when {
                                    selectInteractive && selectToggleArmed -> {
                                        { onToggleSelect?.invoke(index) }
                                    }
                                    selectInteractive -> null
                                    !browsing -> null
                                    else -> {
                                        {
                                            val i = index
                                            browsing = false
                                            dragSession = false
                                            scope.launch {
                                                scrollToCenteredIndex(i, animated = true)
                                            }
                                            onSeekToMs(line.timeMs)
                                        }
                                    }
                                },
                                // 跟滚态长按由父级 pointerInput 统一命中；浏览态仍走行内长按
                                onLongPress = if (selectMode || !browsing || onLongPressLine == null) {
                                    null
                                } else {
                                    {
                                        followGen++
                                        dragSession = false
                                        onLongPressLineUpdated?.invoke(index)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val LyricSelectSelectedTextFallback = Color(0xFFFFFFFF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LandscapeScrollLyricLine(
    text: String,
    lineKey: Int,
    isPlayingLine: Boolean,
    isBrowseCenter: Boolean,
    live: Boolean,
    played: Boolean,
    distanceFromPlay: Int,
    lines: List<LrcLine>,
    focus: Int,
    trackDurationMs: Long,
    fontScale: Float,
    lineSpacing: Dp,
    slotHeight: Dp,
    animMs: Int,
    browsing: Boolean,
    selected: Boolean = false,
    selectStyleT: Float = 0f,
    /** 选句行：固定行高，选中底铺满整格，相邻选中拼成连续矩形 */
    fixedSelectRow: Boolean = false,
    freezeLineTransitions: Boolean = false,
    instantAppear: Boolean = false,
    playingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    playedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
    onSeekClick: (() -> Unit)?,
    onLongPress: (() -> Unit)? = null,
) {
    val ix = remember { MutableInteractionSource() }
    val st = selectStyleT.coerceIn(0f, 1f)
    val selectUnplayed = unplayedStyle.resolvedColorFor(LyricStyleRole.Unplayed)
    val selectSelectedText = playingStyle.resolvedColorFor(LyricStyleRole.Playing)
        .takeIf { it.alpha > 0.01f }
        ?: LyricSelectSelectedTextFallback
    val selectSelectedBg = lerp(selectUnplayed, Color.White, 0.22f).copy(alpha = 0.22f)
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (fixedSelectRow) {
                    Modifier.height(slotHeight)
                } else {
                    Modifier
                        .heightIn(min = slotHeight)
                        .wrapContentHeight()
                },
            )
            .then(
                if (selected && st > 0.5f) {
                    Modifier.background(selectSelectedBg)
                } else {
                    Modifier
                },
            )
            .then(
                when {
                    onLongPress != null && onSeekClick != null -> {
                        Modifier.combinedClickable(
                            interactionSource = ix,
                            indication = null,
                            onClick = onSeekClick,
                            onLongClick = onLongPress,
                        )
                    }
                    onLongPress != null -> {
                        Modifier.combinedClickable(
                            interactionSource = ix,
                            indication = null,
                            onClick = {},
                            onLongClick = onLongPress,
                        )
                    }
                    onSeekClick != null -> {
                        Modifier.clickable(
                            interactionSource = ix,
                            indication = null,
                            onClick = onSeekClick,
                        )
                    }
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        @Composable
        fun SelectStaticText(alpha: Float) {
            val baseAlpha = lerp(0.46f, 0.50f, st)
            Text(
                text = text,
                style = TextStyle(
                    color = if (selected) {
                        selectSelectedText.copy(alpha = 0.96f * alpha)
                    } else {
                        selectUnplayed.copy(alpha = baseAlpha * alpha)
                    },
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (selected) {
                        playingStyle.resolvedFontWeight(LyricStyleRole.Playing)
                    } else {
                        unplayedStyle.resolvedFontWeight(LyricStyleRole.Unplayed)
                    },
                    fontStyle = if (selected) {
                        playingStyle.resolvedFontStyle()
                    } else {
                        unplayedStyle.resolvedFontStyle()
                    },
                    fontSize = (16.5f * fontScale).sp,
                    lineHeight = (26f * fontScale).sp,
                    letterSpacing = 0.35.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
            )
        }

        // 播放行：保持 Center 挂载，字号/透明度随 selectT 过渡
        if (isPlayingLine) {
            LandscapeCenterLyricLine(
                lines = lines,
                focus = focus,
                live = live,
                trackDurationMs = trackDurationMs,
                fontScale = fontScale,
                animMs = animMs.coerceAtLeast(280),
                compact = true,
                lineSpacing = lineSpacing,
                fillWidth = true,
                instantAppear = instantAppear,
                freezeTransitions = freezeLineTransitions,
                selectMorphT = st,
                selectSelected = selected,
                playingStyle = playingStyle,
                unplayedStyle = unplayedStyle,
            )
            return@Box
        }

        val visualMode = when {
            isBrowseCenter -> 1
            else -> 2
        }
        @Composable
        fun LyricBody(mode: Int) {
            when (mode) {
                1 -> {
                    Text(
                        text = text,
                        style = TextStyle(
                            color = LyricBrowseSelect.copy(alpha = 0.88f),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (16.5f * fontScale).sp,
                            lineHeight = (26f * fontScale).sp,
                            letterSpacing = 0.35.sp,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 4,
                        softWrap = true,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = lineSpacing, horizontal = 10.dp),
                    )
                }
                else -> {
                    LandscapeSideLyricLine(
                        text = text,
                        lineKey = lineKey,
                        played = played,
                        distance = distanceFromPlay.coerceAtMost(3).coerceAtLeast(1),
                        fontScale = fontScale,
                        animMs = animMs.coerceAtLeast(240),
                        verticalPadding = lineSpacing,
                        fillWidth = true,
                        selectMorphT = st,
                        playedStyle = playedStyle,
                        unplayedStyle = unplayedStyle,
                    )
                }
            }
        }

        // 侧句：选句 progress 上做交叉淡化，避免硬切未播放样式
        if (st > 0.001f) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (st < 0.995f) {
                    Box(Modifier.graphicsLayer { alpha = 1f - st }) {
                        if (browsing) {
                            LyricBody(visualMode)
                        } else {
                            AnimatedContent(
                                targetState = visualMode,
                                transitionSpec = {
                                    (
                                        fadeIn(tween(260, easing = LyricSoftEasing)) +
                                            slideInVertically(tween(260, easing = LyricSoftEasing)) { it / 5 }
                                        ) togetherWith (
                                        fadeOut(tween(180, easing = LyricSofterEasing)) +
                                            slideOutVertically(tween(180, easing = LyricSofterEasing)) { -it / 6 }
                                        ) using SizeTransform(clip = false)
                                },
                                label = "landLyricVisual",
                            ) { mode -> LyricBody(mode) }
                        }
                    }
                }
                Box(Modifier.graphicsLayer { alpha = st }) {
                    SelectStaticText(alpha = 1f)
                }
            }
            return@Box
        }

        if (browsing) {
            LyricBody(visualMode)
        } else {
            AnimatedContent(
                targetState = visualMode,
                transitionSpec = {
                    (
                        fadeIn(tween(260, easing = LyricSoftEasing)) +
                            slideInVertically(tween(260, easing = LyricSoftEasing)) { it / 5 }
                        ) togetherWith (
                        fadeOut(tween(180, easing = LyricSofterEasing)) +
                            slideOutVertically(tween(180, easing = LyricSofterEasing)) { -it / 6 }
                        ) using SizeTransform(clip = false)
                },
                label = "landLyricVisual",
            ) { mode -> LyricBody(mode) }
        }
    }
}

@Composable
private fun LandscapeCenterLyricLine(
    lines: List<LrcLine>,
    focus: Int,
    live: Boolean,
    trackDurationMs: Long,
    fontScale: Float,
    animMs: Int,
    compact: Boolean = false,
    lineSpacing: Dp = 10.dp,
    fillWidth: Boolean = true,
    instantAppear: Boolean = false,
    freezeTransitions: Boolean = false,
    /** 选句形态进度：字号/字重/透明度向未播放样式过渡 */
    selectMorphT: Float = 0f,
    selectSelected: Boolean = false,
    playingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
) {
    val emphasis by animateFloatAsState(
        targetValue = if (live) 1f else 0f,
        animationSpec = tween(
            durationMillis = (animMs * 1.25f).toInt().coerceIn(280, 520),
            easing = LyricSoftEasing,
        ),
        label = "landLyricEmphasis",
    )
    val st = selectMorphT.coerceIn(0f, 1f)
    // 布局用固定字号；不做缩放强调，避免入场结束水平微跳
    val playFont = 26f * fontScale
    val playLine = 38f * fontScale
    val selectFont = 16.5f * fontScale
    val selectLine = 26f * fontScale
    val baseFont = lerp(playFont, selectFont, st)
    val baseLine = lerp(playLine, selectLine, st)
    val playAlpha = 0.58f + 0.42f * emphasis * (1f - st)
    val selectAlpha = if (selectSelected) 0.96f else 0.50f
    val textAlpha = lerp(playAlpha, selectAlpha, st)
    val playingColor = playingStyle.resolvedColorFor(LyricStyleRole.Playing)
    val selectUnplayed = unplayedStyle.resolvedColorFor(LyricStyleRole.Unplayed)
    val selectSelectedText = playingColor
    val playColor = playingColor.copy(alpha = textAlpha)
    val selectColor = if (selectSelected) {
        selectSelectedText.copy(alpha = textAlpha)
    } else {
        selectUnplayed.copy(alpha = textAlpha)
    }
    val textColor = lerp(playColor, selectColor, st)
    val playWeight = playingStyle.resolvedFontWeight(LyricStyleRole.Playing)
    val selectWeight = if (selectSelected) {
        playingStyle.resolvedFontWeight(LyricStyleRole.Playing)
    } else {
        unplayedStyle.resolvedFontWeight(LyricStyleRole.Unplayed)
    }
    val weight = when {
        selectSelected && st > 0.45f -> selectWeight
        st > 0.55f -> selectWeight
        else -> playWeight
    }
    val playStyle = playingStyle.resolvedFontStyle()
    val selectStyle = if (selectSelected) {
        playingStyle.resolvedFontStyle()
    } else {
        unplayedStyle.resolvedFontStyle()
    }
    val fontStyle = if (st > 0.5f) selectStyle else playStyle
    val span = lyricLineSpanMs(lines, focus, trackDurationMs)
    val vPad = if (compact) lineSpacing else 10.dp

    Box(
        Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .padding(vertical = vPad, horizontal = 4.dp)
            .padding(vertical = if (compact) 0.dp else 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        StableCenterLyricText(
            focus = focus,
            text = lines.getOrNull(focus)?.text.orEmpty(),
            animMs = animMs,
            lineSpanMs = span,
            fillWidth = fillWidth,
            maxLines = if (st > 0.5f) 2 else 6,
            overflow = if (st > 0.5f) TextOverflow.Ellipsis else TextOverflow.Clip,
            instantAppear = instantAppear,
            freezeTransitions = freezeTransitions,
            style = TextStyle(
                color = textColor,
                fontFamily = FontFamily.SansSerif,
                fontWeight = weight,
                fontStyle = fontStyle,
                fontSize = baseFont.sp,
                lineHeight = baseLine.sp,
                letterSpacing = lerp(0.5f, 0.35f, st).sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun LandscapeSideLyricLine(
    text: String,
    lineKey: Int,
    played: Boolean,
    distance: Int,
    fontScale: Float,
    animMs: Int,
    verticalPadding: Dp = 11.dp,
    fillWidth: Boolean = true,
    /** 选句进度：已播放斜体/字重随此值过渡到正体未播放样式 */
    selectMorphT: Float = 0f,
    playedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
) {
    val st = selectMorphT.coerceIn(0f, 1f)
    // 已播放视觉强度：1=完整已播放样式，0=选句未播放样式；随 selectT 连续变化
    val playedStrength = if (played) (1f - st) else 0f
    val unplayedAlpha = (0.46f - distance.coerceAtMost(2) * 0.06f)
    val targetAlpha = lerp(unplayedAlpha, 0.32f, playedStrength)
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = (animMs * 1.1f).toInt().coerceIn(240, 480),
            easing = LyricSoftEasing,
        ),
        label = "landSideA",
    )
    val sizeSp = lerp(16.5f, 15f, playedStrength) * fontScale
    val padMod = Modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
        .padding(vertical = verticalPadding, horizontal = 10.dp)

    val unplayedColor = unplayedStyle.resolvedColorFor(LyricStyleRole.Unplayed)
    val playedColor = playedStyle.resolvedColorFor(LyricStyleRole.Played)
    val unplayedWeight = unplayedStyle.resolvedFontWeight(LyricStyleRole.Unplayed)
    val playedWeight = playedStyle.resolvedFontWeight(LyricStyleRole.Played)
    val unplayedFs = unplayedStyle.resolvedFontStyle()
    val playedFs = playedStyle.resolvedFontStyle()

    // 槽位固定后侧句文本会原地替换：用淡入淡出避免硬切
    Crossfade(
        targetState = lineKey to text,
        animationSpec = tween(
            durationMillis = animMs.coerceIn(200, 420),
            easing = LyricSoftEasing,
        ),
        label = "landSideCrossfade",
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
    ) { (_, shown) ->
        // 斜体无法插值：已播放层与未播放层按 playedStrength 交叉淡化
        Box(
            modifier = padMod,
            contentAlignment = Alignment.Center,
        ) {
            if (playedStrength < 0.995f) {
                Text(
                    text = shown,
                    style = TextStyle(
                        color = unplayedColor.copy(alpha = alpha * (1f - playedStrength)),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = unplayedWeight,
                        fontStyle = unplayedFs,
                        fontSize = sizeSp.sp,
                        lineHeight = (26f * fontScale).sp,
                        letterSpacing = 0.35.sp,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 4,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
                )
            }
            if (playedStrength > 0.01f) {
                Text(
                    text = shown,
                    style = TextStyle(
                        color = playedColor.copy(alpha = alpha * playedStrength),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = playedWeight,
                        fontStyle = playedFs,
                        fontSize = sizeSp.sp,
                        lineHeight = (26f * fontScale).sp,
                        letterSpacing = 0.35.sp,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 4,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
                )
            }
        }
    }
}

/**
 * 横屏标题信息块：歌名 / 制作人 / 歌单。
 * 每一行按自身宽度独立算目标 X，[Animatable] 同步滑动，切换对齐不再只有标题在动。
 */
@Composable
private fun LandscapeAlignedSongMeta(
    track: TrackRow,
    sourceTitle: String?,
    onSourceClick: (() -> Unit)?,
    onRevealControls: () -> Unit,
    titleAlign: TitleAlignMode,
    songMetaTopPad: Dp,
    chromeSidePad: Dp,
    vinylCenterX: Dp,
    lyricsCenterX: Dp,
    screenCenterX: Dp,
    titleMaxWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val srcIx = remember { MutableInteractionSource() }
    val centerModes = titleAlign == TitleAlignMode.VINYL ||
        titleAlign == TitleAlignMode.CENTER ||
        titleAlign == TitleAlignMode.LYRICS
    val textAlign = if (centerModes) TextAlign.Center else TextAlign.Start

    fun targetXForWidth(widthPx: Float): Float {
        if (widthPx <= 0.5f) return 0f
        return with(density) {
            when (titleAlign) {
                TitleAlignMode.LEFT -> chromeSidePad.toPx()
                TitleAlignMode.VINYL -> vinylCenterX.toPx() - widthPx / 2f
                TitleAlignMode.CENTER -> screenCenterX.toPx() - widthPx / 2f
                TitleAlignMode.LYRICS -> lyricsCenterX.toPx() - widthPx / 2f
            }
        }
    }

    Box(modifier) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = songMetaTopPad)
                .widthIn(max = titleMaxWidth)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            AlignedMetaLine(
                text = track.name,
                textAlign = textAlign,
                titleAlign = titleAlign,
                targetXForWidth = ::targetXForWidth,
                style = TextStyle(
                    color = Color(0xFFF5F7FA),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    letterSpacing = 0.35.sp,
                    textAlign = textAlign,
                ),
                maxLines = 2,
            )
            Spacer(Modifier.height(5.dp))
            AlignedMetaLine(
                text = track.artists.uppercase(),
                textAlign = textAlign,
                titleAlign = titleAlign,
                targetXForWidth = ::targetXForWidth,
                style = TextStyle(
                    color = CyanSoft.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.8.sp,
                    textAlign = textAlign,
                ),
                maxLines = 1,
            )
            if (!sourceTitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                AlignedMetaLine(
                    text = sourceTitle,
                    textAlign = textAlign,
                    titleAlign = titleAlign,
                    targetXForWidth = ::targetXForWidth,
                    style = TextStyle(
                        color = LyricDim.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 0.55.sp,
                        textAlign = textAlign,
                    ),
                    maxLines = 1,
                    modifier = Modifier.then(
                        if (onSourceClick != null) {
                            Modifier.clickable(
                                interactionSource = srcIx,
                                indication = null,
                                onClick = {
                                    onRevealControls()
                                    onSourceClick()
                                },
                            )
                        } else {
                            Modifier
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun AlignedMetaLine(
    text: String,
    textAlign: TextAlign,
    titleAlign: TitleAlignMode,
    targetXForWidth: (Float) -> Float,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    val x = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = targetXForWidth(widthPx)

    LaunchedEffect(target, widthPx, titleAlign, text) {
        if (widthPx <= 0.5f) return@LaunchedEffect
        if (!placed) {
            x.snapTo(target)
            placed = true
        } else {
            x.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.86f,
                    stiffness = 280f,
                ),
            )
        }
    }

    Text(
        text = text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier
            .graphicsLayer { alpha = if (placed) 1f else 0f }
            .offset { IntOffset(x.value.roundToInt(), 0) }
            .wrapContentWidth(align = Alignment.Start)
            .onSizeChanged { widthPx = it.width.toFloat() },
    )
}

/**
 * 右上角非阻塞通知：不可点击；入场自右淡入、出场淡出右移；
 * [chromeProgress] 升高时 Y 下移避让旋转锁/设置图标，可打断。
 */
@Composable
private fun PlaybackCornerNotice(
    notice: PlaybackNotice?,
    chromeProgress: Float,
    topBase: Dp,
    endPad: Dp,
    modifier: Modifier = Modifier,
) {
    var displayed by remember { mutableStateOf<PlaybackNotice?>(null) }
    val density = LocalDensity.current
    val panelAlpha = remember { Animatable(0f) }
    val panelSlideX = remember { Animatable(0f) }
    val panelTopY = remember { Animatable(0f) }
    val noticeCurve = remember { CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f) }

    val iconAvoid = NowPlayingChromeIconHeight + 10.dp
    val targetTopDp = topBase + iconAvoid * chromeProgress.coerceIn(0f, 1f)

    LaunchedEffect(targetTopDp) {
        val y = with(density) { targetTopDp.toPx() }
        if (panelAlpha.value < 0.04f) {
            panelTopY.snapTo(y)
        } else {
            panelTopY.animateTo(
                y,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 320f),
            )
        }
    }

    LaunchedEffect(notice?.token) {
        val incoming = notice
        if (incoming != null) {
            displayed = incoming
            val fromX = with(density) { 28.dp.toPx() }
            panelSlideX.snapTo(fromX)
            panelAlpha.snapTo(0f)
            coroutineScope {
                launch {
                    panelAlpha.animateTo(
                        1f,
                        tween(300, easing = noticeCurve),
                    )
                }
                launch {
                    panelSlideX.animateTo(
                        0f,
                        tween(340, easing = noticeCurve),
                    )
                }
            }
        } else if (displayed != null) {
            val toX = with(density) { 18.dp.toPx() }
            coroutineScope {
                launch {
                    panelAlpha.animateTo(
                        0f,
                        tween(240, easing = LyricSofterEasing),
                    )
                }
                launch {
                    panelSlideX.animateTo(
                        toX,
                        tween(260, easing = LyricSofterEasing),
                    )
                }
            }
            displayed = null
        }
    }

    val shown = displayed
    if (shown == null && panelAlpha.value < 0.02f) return

    val msg = shown?.message ?: return
    Box(
        modifier
            .offset {
                IntOffset(
                    x = panelSlideX.value.roundToInt(),
                    y = panelTopY.value.roundToInt(),
                )
            }
            .padding(end = endPad)
            .graphicsLayer { alpha = panelAlpha.value }
            // 明确不可点击，不拦截下层
            .clickable(
                enabled = false,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Text(
            text = msg,
            style = TextStyle(
                color = Color(0xFFE8F0F8).copy(alpha = 0.92f),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.2.sp,
                shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 8f),
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE0121822))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LandscapePlayerBody(
    track: TrackRow,
    lines: List<LrcLine>,
    positionMs: Long,
    seekPositionMs: Long,
    isPlaying: Boolean,
    playWhenReady: Boolean,
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
    trackLiked: Boolean,
    onToggleLike: () -> Unit,
    onDismiss: () -> Unit,
    dismissSwipeThresholdPx: Float,
    displayPrefs: PlayerDisplayPrefs,
    onDisplayPrefsChange: (PlayerDisplayPrefs) -> Unit,
    settingsHazeState: HazeState,
    peekNextTrack: TrackRow?,
    peekPrevTrack: TrackRow?,
    onSeek: (Long) -> Unit,
    queue: List<TrackRow>,
    queueIndex: Int,
    onPlayQueueIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    notice: PlaybackNotice? = null,
    transportWakeToken: Int = 0,
) {
    val activity = LocalActivity.current
    val rotationLock = com.kite.zmusic.ui.orientation.LocalSessionRotationLock.current
    // 直接读 Store，保证锁状态变化触发重组（不依赖 @Stable 门面推断）
    val rotationLocked = com.kite.zmusic.ui.orientation.SessionRotationLockStore.locked
    // 沉浸默认隐藏；仅纯点击唤出，滑动不唤出（常显时恒定展开）
    var controlsVisible by remember { mutableStateOf(displayPrefs.transportAlwaysVisible) }
    var settingsOpen by remember { mutableStateOf(false) }
    var scoreOpen by remember { mutableStateOf(false) }
    /** 曲谱展开：强制黑胶垂直居中（忽略个性化 Y，保留 X）；收窗动画结束后再松开 */
    var scoreVinylCentered by remember { mutableStateOf(false) }
    /** 曲谱加宽覆盖黑胶：左/右边距对称 = chromeSidePad */
    var scoreCoverExpanded by remember { mutableStateOf(false) }
    /** 每次打开曲谱递增，网格首帧直接落在当前曲 */
    var scoreOpenGeneration by remember { mutableIntStateOf(0) }
    var scoreFlight by remember { mutableStateOf<ScoreVinylFlight?>(null) }
    var suppressVinylEnter by remember { mutableStateOf(false) }
    var mainVinylCenterRoot by remember { mutableStateOf(Offset.Zero) }
    var mainVinylSizePx by remember { mutableFloatStateOf(0f) }
    /** 切歌时递增，重挂 hazeEffect 恢复模糊采样（设置/选句用；曲谱不再 remount） */
    var hazeNonce by remember { mutableIntStateOf(0) }
    var vinylColorEditorOpen by remember { mutableStateOf(false) }
    /** 编辑态黑胶居中锁：进入时立刻开启；退出时等弹窗收完再关，与弹窗错开 */
    var editorVinylCentered by remember { mutableStateOf(false) }
    var reopenSettingsAfterEditor by remember { mutableStateOf(false) }
    var lyricStyleEditorOpen by remember { mutableStateOf(false) }
    var reopenSettingsAfterLyricStyle by remember { mutableStateOf(false) }
    var lyricStyleSnapshot by remember { mutableStateOf<LyricStyleSnapshot?>(null) }
    var draftLyricPlaying by remember { mutableStateOf(LyricRoleStyle.PlayingDefault) }
    var draftLyricPlayed by remember { mutableStateOf(LyricRoleStyle.PlayedDefault) }
    var draftLyricUnplayed by remember { mutableStateOf(LyricRoleStyle.UnplayedDefault) }
    /** 编辑期间冻结真歌词进度，关闭交接时与克隆同位 */
    var lyricStyleFrozenPositionMs by remember { mutableLongStateOf(0L) }
    var playerRootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lyricsBandCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var idleBump by remember { mutableIntStateOf(0) }
    var vinylSkipDir by remember { mutableStateOf(VinylSkipDirection.Next) }
    var vinylBusy by remember { mutableStateOf(false) }
    var lyricResumeToken by remember { mutableIntStateOf(0) }
    var lyricSelectOpen by remember { mutableStateOf(false) }
    var lyricSelectOutsideArmed by remember { mutableStateOf(false) }
    val lyricSelectSelected: SnapshotStateSet<Int> = remember { mutableStateSetOf() }
    // 未预加载 / URL 解析中：控件锁定，显示加载
    val controlsLocked = loadPending
    val transportBuffering = buffering || loadPending
    val transportPinned = displayPrefs.transportAlwaysVisible
    val forceVinylYCentered = editorVinylCentered || scoreVinylCentered || scoreFlight != null
    // 设置 / 曲谱 / 自选编辑 / 选句与底部播放条互斥；编辑时强制隐藏（忽略常显）
    val showBar = (controlsVisible || sliderDragging || transportPinned) &&
        !settingsOpen &&
        !scoreOpen &&
        !forceVinylYCentered &&
        !lyricSelectOpen &&
        !lyricStyleEditorOpen
    val density = LocalDensity.current
    val uiScale = displayPrefs.uiScale.coerceIn(PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX)
    val settingsCurve = remember { CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f) }
    val vinylCenterEasing = remember { CubicBezierEasing(0.33f, 0f, 0.2f, 1f) }
    val vinylCenterMs = 480

    LaunchedEffect(transportPinned, forceVinylYCentered) {
        if (transportPinned && !forceVinylYCentered) controlsVisible = true
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

    // 曲谱：与设置同曲线展开；黑胶 Y 居中与弹窗同开同收，全程有动画
    val scorePanel = remember { Animatable(0f) }
    val scoreCoverAnim = remember { Animatable(0f) }
    LaunchedEffect(scoreOpen) {
        if (scoreOpen) {
            scorePanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 460, easing = settingsCurve),
            )
        } else {
            scoreCoverExpanded = false
            scorePanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 460, easing = settingsCurve),
            )
            scoreCoverAnim.snapTo(0f)
            // 飞入进行中保持黑胶居中，飞完再松
            if (scoreFlight == null) {
                scoreVinylCentered = false
                if (transportPinned &&
                    !settingsOpen &&
                    !editorVinylCentered &&
                    !lyricSelectOpen &&
                    !lyricStyleEditorOpen
                ) {
                    controlsVisible = true
                }
            }
        }
    }
    LaunchedEffect(scoreCoverExpanded) {
        scoreCoverAnim.animateTo(
            targetValue = if (scoreCoverExpanded) 1f else 0f,
            animationSpec = tween(durationMillis = 520, easing = settingsCurve),
        )
    }
    val scoreT = scorePanel.value
    val scoreCoverT = scoreCoverAnim.value

    // 切歌：设置/选句刷新磨砂；曲谱已改为实底，不参与 remount
    LaunchedEffect(track.id) {
        if (lyricSelectOpen) {
            lyricSelectSelected.clear()
        }
        if (settingsOpen || lyricSelectOpen || vinylColorEditorOpen || editorVinylCentered ||
            lyricStyleEditorOpen
        ) {
            delay(64)
            hazeNonce++
            delay(280)
            hazeNonce++
        }
    }

    val editorPanel = remember { Animatable(0f) }
    LaunchedEffect(vinylColorEditorOpen) {
        if (vinylColorEditorOpen) {
            // 先等黑胶居中就位，再渐显弹窗
            delay(vinylCenterMs.toLong())
            if (!vinylColorEditorOpen) return@LaunchedEffect
            editorPanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = vinylCenterMs, easing = vinylCenterEasing),
            )
        } else {
            // 先收弹窗，再在完成后松开黑胶居中（见下方）
            editorPanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = vinylCenterMs, easing = vinylCenterEasing),
            )
            editorVinylCentered = false
            if (reopenSettingsAfterEditor) {
                reopenSettingsAfterEditor = false
                controlsVisible = false
                settingsOpen = true
            } else if (transportPinned) {
                controlsVisible = true
            }
        }
    }
    val editorT = editorPanel.value

    val lyricStylePanel = remember { Animatable(0f) }
    LaunchedEffect(lyricStyleEditorOpen) {
        if (lyricStyleEditorOpen) {
            // 先钉在源位一帧，避免开场就插值造成跳位
            lyricStylePanel.snapTo(0f)
            delay(32)
            if (!lyricStyleEditorOpen) return@LaunchedEffect
            lyricStylePanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 460, easing = settingsCurve),
            )
        } else {
            lyricStylePanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 460, easing = settingsCurve),
            )
            // 回位后短暂停在重叠态，完成交叉淡出再卸克隆
            if (lyricStyleSnapshot != null) {
                delay(200)
            }
            if (reopenSettingsAfterLyricStyle) {
                reopenSettingsAfterLyricStyle = false
                controlsVisible = false
                settingsOpen = true
            } else if (transportPinned &&
                !settingsOpen &&
                !editorVinylCentered &&
                !lyricSelectOpen &&
                !scoreOpen
            ) {
                controlsVisible = true
            }
            if (!lyricStyleEditorOpen) {
                lyricStyleSnapshot = null
            }
        }
    }
    val lyricStyleT = lyricStylePanel.value
    // 关闭回位末段：真歌词淡入、克隆淡出
    val lyricStyleHandoffT = if (
        !lyricStyleEditorOpen &&
        lyricStyleSnapshot != null
    ) {
        ((0.35f - lyricStyleT) / 0.35f).coerceIn(0f, 1f)
    } else {
        0f
    }
    val liveLyricAlpha = when {
        lyricStyleSnapshot == null -> 1f
        lyricStyleEditorOpen -> 0f
        else -> lyricStyleHandoffT
    }
    val styleCloneAlpha = when {
        lyricStyleSnapshot == null -> 0f
        lyricStyleEditorOpen -> 1f
        else -> (1f - lyricStyleHandoffT).coerceIn(0f, 1f)
    }

    val lyricSelectPanel = remember { Animatable(0f) }
    var lyricSelectEverOpen by remember { mutableStateOf(false) }
    LaunchedEffect(lyricSelectOpen) {
        if (lyricSelectOpen) {
            lyricSelectEverOpen = true
            lyricSelectPanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = vinylCenterMs, easing = vinylCenterEasing),
            )
        } else {
            // 一开始就触发回滚，与收窗/样式 morph 并行，避免收完再突然跳位
            if (lyricSelectEverOpen) {
                lyricResumeToken++
            }
            lyricSelectPanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = vinylCenterMs, easing = vinylCenterEasing),
            )
                if (lyricSelectEverOpen) {
                    lyricSelectEverOpen = false
                    if (transportPinned && !settingsOpen && !editorVinylCentered && !scoreOpen &&
                        !lyricStyleEditorOpen
                    ) {
                        controlsVisible = true
                    }
                }
        }
    }
    val lyricSelectT = lyricSelectPanel.value

    fun closeSettings() {
        settingsOpen = false
        if (transportPinned && !forceVinylYCentered && !lyricSelectOpen && !scoreOpen &&
            !lyricStyleEditorOpen
        ) {
            controlsVisible = true
        }
    }

    fun commitLyricStyleDraft() {
        val next = displayPrefs.copy(
            lyricPlayingStyle = draftLyricPlaying,
            lyricPlayedStyle = draftLyricPlayed,
            lyricUnplayedStyle = draftLyricUnplayed,
        )
        if (next != displayPrefs) {
            onDisplayPrefsChange(next)
        }
    }

    fun closeLyricStyleEditor() {
        reopenSettingsAfterLyricStyle = false
        commitLyricStyleDraft()
        lyricStyleEditorOpen = false
    }

    fun closeLyricStyleEditorToSettings() {
        reopenSettingsAfterLyricStyle = true
        commitLyricStyleDraft()
        lyricStyleEditorOpen = false
    }

    fun openLyricStyleEditor() {
        if (vinylColorEditorOpen || editorVinylCentered || lyricSelectOpen ||
            scoreOpen || scoreVinylCentered || scoreFlight != null
        ) {
            return
        }
        val root = playerRootCoords
        val lyric = lyricsBandCoords
        if (root == null || lyric == null || !root.isAttached || !lyric.isAttached) return
        // 与弹窗同坐标系：用视觉 bounds（含 uiScale），开场必能盖住真歌词
        val bandBounds = lyric.boundsInRoot()
        val rootBounds = root.boundsInRoot()
        val srcLeft = with(density) { (bandBounds.left - rootBounds.left).toDp() }
        val srcTop = with(density) { (bandBounds.top - rootBounds.top).toDp() }
        val srcW = with(density) { bandBounds.width.toDp() }
        val srcH = with(density) { bandBounds.height.toDp() }
        settingsOpen = false
        controlsVisible = false
        reopenSettingsAfterLyricStyle = false
        val animActive = lyricAnimActiveIndex(lines, positionMs, durationMs)
        val focus = lyricFocusIndex(lines, animActive)
        draftLyricPlaying = displayPrefs.lyricPlayingStyle
        draftLyricPlayed = displayPrefs.lyricPlayedStyle
        draftLyricUnplayed = displayPrefs.lyricUnplayedStyle
        lyricStyleFrozenPositionMs = positionMs
        lyricStyleSnapshot = LyricStyleSnapshot(
            lines = lines,
            focusIndex = focus,
            playedCount = displayPrefs.lyricPlayedCount,
            upcomingCount = displayPrefs.lyricUpcomingCount,
            fontScale = displayPrefs.fontScale,
            lineSpacingDp = displayPrefs.lyricLineSpacingDp,
            sourceLeftDp = srcLeft,
            sourceTopDp = srcTop,
            sourceWidthDp = srcW.coerceAtLeast(48.dp),
            sourceHeightDp = srcH.coerceAtLeast(48.dp),
        )
        lyricStyleEditorOpen = true
    }

    fun closeScore() {
        scoreOpen = false
        scoreCoverExpanded = false
    }

    fun openScore() {
        if (editorVinylCentered || vinylColorEditorOpen || lyricSelectOpen || settingsOpen ||
            scoreFlight != null || lyricStyleEditorOpen
        ) {
            return
        }
        controlsVisible = false
        scoreCoverExpanded = false
        scoreOpenGeneration++
        scoreVinylCentered = true
        scoreOpen = true
    }

    fun startScoreFlight(index: Int, center: Offset, sizePx: Float) {
        val t = queue.getOrNull(index) ?: return
        if (index == queueIndex) {
            closeScore()
            return
        }
        // 拒绝无效起点，避免从左上角 (0,0) 飞入
        if (center.x < 1f || center.y < 1f || sizePx < 8f) return
        suppressVinylEnter = true
        scoreFlight = ScoreVinylFlight(
            track = t,
            queueIndex = index,
            startCenter = center,
            startSizePx = sizePx,
        )
        closeScore()
    }

    fun finishScoreFlight() {
        scoreFlight = null
        scoreVinylCentered = false
        suppressVinylEnter = false
        if (transportPinned && !settingsOpen && !editorVinylCentered && !lyricSelectOpen &&
            !lyricStyleEditorOpen
        ) {
            controlsVisible = true
        }
    }

    fun closeVinylColorEditor() {
        reopenSettingsAfterEditor = false
        vinylColorEditorOpen = false
    }

    /** 左上角返回：弹窗收完后再打开设置页 */
    fun closeVinylColorEditorToSettings() {
        reopenSettingsAfterEditor = true
        vinylColorEditorOpen = false
    }

    fun openVinylColorEditor() {
        // 收回设置与播放条（忽略常显）；保留黑胶 X，Y 由编辑态强制垂直居中
        if (lyricSelectOpen || scoreOpen || scoreVinylCentered || lyricStyleEditorOpen) return
        settingsOpen = false
        controlsVisible = false
        reopenSettingsAfterEditor = false
        if (displayPrefs.vinylColorStyle != VinylColorStyle.CUSTOM) {
            onDisplayPrefsChange(displayPrefs.copy(vinylColorStyle = VinylColorStyle.CUSTOM))
        }
        editorVinylCentered = true
        vinylColorEditorOpen = true
    }

    fun closeLyricSelect() {
        lyricSelectSelected.clear()
        lyricSelectOpen = false
        lyricSelectOutsideArmed = false
    }

    @Suppress("UNUSED_PARAMETER")
    fun openLyricSelect(index: Int) {
        if (vinylColorEditorOpen || editorVinylCentered || scoreOpen || scoreVinylCentered ||
            lyricStyleEditorOpen
        ) {
            return
        }
        settingsOpen = false
        controlsVisible = false
        // 进入时不预选；由用户再点选
        lyricSelectSelected.clear()
        lyricSelectOutsideArmed = false
        lyricSelectOpen = true
    }

    fun openSettings() {
        // 互斥：收回下方播放组件
        if (editorVinylCentered || vinylColorEditorOpen || lyricSelectOpen ||
            scoreOpen || scoreVinylCentered || scoreFlight != null || lyricStyleEditorOpen
        ) {
            return
        }
        controlsVisible = false
        settingsOpen = true
    }

    fun revealControls() {
        if (settingsOpen || forceVinylYCentered || vinylColorEditorOpen ||
            lyricSelectOpen || scoreOpen || lyricStyleEditorOpen
        ) {
            return
        }
        controlsVisible = true
        idleBump++
    }

    fun toggleControls() {
        if (lyricSelectOpen || lyricSelectT > 0.001f) {
            // 打开手势的松手一律忽略；解锁后才允许空白单击关闭
            if (lyricSelectOutsideArmed) closeLyricSelect()
            return
        }
        if (lyricStyleEditorOpen || lyricStyleT > 0.001f) {
            closeLyricStyleEditor()
            return
        }
        if (vinylColorEditorOpen || editorVinylCentered || editorT > 0.001f) {
            closeVinylColorEditor()
            return
        }
        if (scoreOpen || scoreT > 0.001f) {
            closeScore()
            return
        }
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

    // 播放失败重试：唤醒底部播放组件
    LaunchedEffect(transportWakeToken) {
        if (transportWakeToken > 0) {
            if (lyricSelectOpen) {
                lyricSelectSelected.clear()
                lyricSelectOpen = false
                lyricSelectOutsideArmed = false
                lyricSelectPanel.snapTo(0f)
            }
            if (lyricStyleEditorOpen || lyricStyleT > 0.001f) {
                reopenSettingsAfterLyricStyle = false
                lyricStyleEditorOpen = false
                lyricStylePanel.snapTo(0f)
                lyricStyleSnapshot = null
            }
            if (vinylColorEditorOpen || editorVinylCentered) {
                reopenSettingsAfterEditor = false
                vinylColorEditorOpen = false
                editorVinylCentered = false
                editorPanel.snapTo(0f)
            }
            if (scoreOpen || scoreVinylCentered || scoreFlight != null) {
                scoreOpen = false
                scoreVinylCentered = false
                scoreFlight = null
                suppressVinylEnter = false
                scorePanel.snapTo(0f)
            }
            if (settingsOpen) {
                settingsOpen = false
                if (transportPinned) controlsVisible = true
            }
            controlsVisible = true
            idleBump++
        }
    }

    BackHandler(enabled = lyricSelectOpen || lyricSelectT > 0.001f) {
        closeLyricSelect()
    }
    BackHandler(enabled = lyricStyleEditorOpen || lyricStyleT > 0.001f) {
        closeLyricStyleEditor()
    }
    BackHandler(enabled = vinylColorEditorOpen || editorVinylCentered || editorT > 0.001f) {
        closeVinylColorEditor()
    }
    BackHandler(enabled = scoreOpen || scoreT > 0.001f) {
        closeScore()
    }
    BackHandler(
        enabled = settingsOpen &&
            !vinylColorEditorOpen &&
            !editorVinylCentered &&
            !lyricSelectOpen &&
            !lyricStyleEditorOpen &&
            !scoreOpen &&
            !scoreVinylCentered,
    ) {
        closeSettings()
    }

    LaunchedEffect(
        idleBump,
        sliderDragging,
        track.id,
        settingsOpen,
        scoreOpen,
        transportPinned,
        forceVinylYCentered,
        lyricSelectOpen,
        lyricStyleEditorOpen,
    ) {
        if (settingsOpen || scoreOpen || transportPinned || forceVinylYCentered ||
            lyricSelectOpen || lyricStyleEditorOpen
        ) {
            return@LaunchedEffect
        }
        if (sliderDragging) {
            controlsVisible = true
            return@LaunchedEffect
        }
        if (!controlsVisible) return@LaunchedEffect
        delay(3_500)
        controlsVisible = false
    }

    val barSlidePx = with(density) { 52.dp.toPx() }
    // 左右对称外边距：取导航条左右 inset 较大者，避免仅右侧避让导致播放条偏左
    val chromeLayoutDir = LocalLayoutDirection.current
    val navPads = WindowInsets.navigationBars.asPaddingValues()
    val navSideBalance = maxOf(
        navPads.calculateStartPadding(chromeLayoutDir),
        navPads.calculateEndPadding(chromeLayoutDir),
    )
    val chromeSidePad = navSideBalance + 28.dp

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { playerRootCoords = it }
            .nowPlayingBlankGestures(
                dismissThresholdPx = dismissSwipeThresholdPx,
                onTap = {
                    lyricResumeToken++
                    toggleControls()
                },
                onSwipeDown = {
                    when {
                        lyricSelectOpen || lyricSelectT > 0.001f -> {
                            if (lyricSelectOutsideArmed) closeLyricSelect()
                        }
                        lyricStyleEditorOpen || lyricStyleT > 0.001f ->
                            closeLyricStyleEditor()
                        vinylColorEditorOpen || editorVinylCentered || editorT > 0.001f ->
                            closeVinylColorEditor()
                        scoreOpen || scoreT > 0.001f -> closeScore()
                        settingsOpen -> closeSettings()
                        else -> onDismiss()
                    }
                },
            ),
    ) {
        // 与左侧歌曲信息同一上边距（按左栏 discExpanded / edgeInset 推算）
        val rootMaxW = maxWidth
        val rootMaxH = maxHeight
        val rowGap = 4.dp
        val leftColW = (rootMaxW - rowGap) * 0.36f
        val discBaseForPad = (leftColW * 0.92f).coerceIn(132.dp, 252.dp)
        val discExpandedForPad = (discBaseForPad * 1.14f)
            .coerceAtMost(leftColW * 0.99f)
            .coerceAtMost(286.dp)
        val songMetaTopPad = ((leftColW - discExpandedForPad) / 2).coerceAtLeast(6.dp)

        // 与左栏同源的黑胶几何：供动态歌词计算右缘侵入
        // 自选编辑 / 曲谱态强制垂直居中（忽略个性化 Y，保留 X）
        val vinylAbsT by animateFloatAsState(
            targetValue = if (displayPrefs.vinylAbsoluteCenter || forceVinylYCentered) 1f else 0f,
            animationSpec = tween(
                durationMillis = vinylCenterMs,
                easing = vinylCenterEasing,
            ),
            label = "vinylAbsCenterOuter",
        )
        val discCompactForPad = (discBaseForPad * 0.86f).coerceAtLeast(118.dp)
        val discForLyric = lerpDp(
            lerpDp(discExpandedForPad, discCompactForPad, chromeT),
            discExpandedForPad,
            vinylAbsT,
        )
        val compactRatioForLyric = if (discExpandedForPad.value > 0.1f) {
            discCompactForPad / discExpandedForPad
        } else {
            1f
        }
        val vinylScaleForLyric = androidx.compose.ui.util.lerp(
            1f,
            androidx.compose.ui.util.lerp(1f, compactRatioForLyric, chromeT),
            vinylAbsT,
        )
        val vinylOx = displayPrefs.vinylOffsetXDp.dp
        val vinylSizeScale = displayPrefs.vinylSizeScale
            .coerceIn(PlayerDisplayPrefs.VINYL_SIZE_SCALE_MIN, PlayerDisplayPrefs.VINYL_SIZE_SCALE_MAX)
        val vinylOuterScale = displayPrefs.vinylOuterScale
            .coerceIn(PlayerDisplayPrefs.VINYL_OUTER_SCALE_MIN, PlayerDisplayPrefs.VINYL_OUTER_SCALE_MAX)
        // 右缘按「整体 × 外圈」可视外缘（外圈可大于 100% 溢出）
        val vinylVisualScale = vinylSizeScale * maxOf(vinylOuterScale, 1f)
        // 左栏黑胶中心（相对 Row）≈ 左栏右缘 - metaWidth/2 + ox
        val vinylCenterX = leftColW - discExpandedForPad / 2 + vinylOx
        val vinylRightEdge = vinylCenterX + discForLyric * vinylScaleForLyric * vinylVisualScale / 2f
        val lyricsColStart = leftColW + rowGap
        val lyricsColWidth = (maxWidth - leftColW - rowGap - 4.dp).coerceAtLeast(0.dp)
        val lyricsCenterX = lyricsColStart + lyricsColWidth / 2 + displayPrefs.lyricOffsetXDp.dp
        val screenCenterX = maxWidth / 2
        val titleMaxWidth = (discExpandedForPad * 1.08f).coerceAtMost(maxWidth * 0.52f)
        // 黑胶右缘越过歌词栏左缘的部分 + 间隙
        val vinylLyricClearance = 10.dp
        val vinylLeftInset = (vinylRightEdge + vinylLyricClearance - lyricsColStart)
            .coerceAtLeast(0.dp)
        val lyricSelectGeomTarget = rememberLyricSelectGeom(
            lines = lines,
            fontScale = displayPrefs.fontScale,
            screenWidth = maxWidth,
            screenHeight = maxHeight,
        )
        val lyricSelectGeom = rememberAnimatedLyricSelectGeom(
            target = lyricSelectGeomTarget,
            animateChanges = lyricSelectOpen && lyricSelectT > 0.98f,
        )

        // 播放内容作磨砂源（不含设置面板本身）
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(state = settingsHazeState, zIndex = 1f)
                .graphicsLayer { clip = false },
        ) {
        // 黑胶 / 歌词 / 标题同一缩放层，保证对齐坐标一致
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
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            // 左侧：黑胶可偏移 / 绝对垂直居中（动画过渡）
            // 左栏铺满至挖孔侧，黑胶离场/入场可画进摄像头区域
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
                    targetValue = if (displayPrefs.vinylAbsoluteCenter || forceVinylYCentered) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = vinylCenterMs,
                        easing = vinylCenterEasing,
                    ),
                    label = "vinylAbsCenter",
                )
                // 非绝对居中：尺寸随 chrome 缩；绝对居中：尺寸固定，chrome 只缩放
                val disc = lerpDp(
                    lerpDp(discExpanded, discCompact, chromeT),
                    discExpanded,
                    absT,
                )
                val chromePad = lerpDp(2.dp, 66.dp, chromeT)
                val vinylBottomPad = lerpDp(0.dp, chromePad, 1f - absT)
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
                val bottomPadPx = with(density) { vinylBottomPad.toPx() }
                // 上一首入场：相对「实际静止中心」（含水平偏移 / 缩放）整盘离开左栏左缘
                val prevEnterSlidePx = with(density) {
                    val centerFromLeft =
                        (maxWidth - metaWidth / 2).toPx() + ox.toPx()
                    val radius = disc.toPx() * 0.5f * vinylScale * vinylSizeScale *
                        maxOf(vinylOuterScale, 1f)
                    centerFromLeft + radius + 6.dp.toPx()
                }

                @Composable
                fun VinylDisc(mod: Modifier) {
                    VinylTransitionStage(
                        track = track,
                        peekNext = peekNextTrack,
                        peekPrev = peekPrevTrack,
                        spinning = isPlaying && !transportBuffering && !vinylBusy &&
                            scoreFlight == null,
                        direction = vinylSkipDir,
                        gesturesEnabled = !settingsOpen && !forceVinylYCentered &&
                            !scoreOpen && scoreFlight == null && !lyricStyleEditorOpen,
                        onTransitionRunningChange = { vinylBusy = it },
                        onCommitSkip = { dir ->
                            vinylSkipDir = dir
                            when (dir) {
                                VinylSkipDirection.Next -> onSkipNext()
                                VinylSkipDirection.Previous -> onSkipPrev()
                            }
                        },
                        modifier = mod.onGloballyPositioned { coords ->
                            val b = coords.boundsInRoot()
                            mainVinylCenterRoot = Offset(
                                b.left + b.width / 2f,
                                b.top + b.height / 2f,
                            )
                            mainVinylSizePx = minOf(b.width, b.height)
                        },
                        fullCover = displayPrefs.vinylFullCover,
                        centerRadiusFrac = displayPrefs.vinylCenterRadiusFrac,
                        outerScale = vinylOuterScale,
                        plateColors = rememberAnimatedVinylPlateColors(
                            displayPrefs.vinylPlateColors(),
                        ),
                        prevEnterSlidePx = prevEnterSlidePx,
                        suppressEnterTransition = suppressVinylEnter,
                        gestureDamping = displayPrefs.vinylGestureDamping,
                    )
                }

                // 单一黑胶：宿主尺寸固定，整体/外圈均绕圆心缩放，避免改 size 导致圆心漂移
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
                                    // 原 bottom padding 会把缩放原点抬离圆心；改为平移，原点保持盘心
                                    translationY = layoutShiftY - bottomPadPx * 0.5f
                                    scaleX = vinylScale * vinylSizeScale
                                    scaleY = vinylScale * vinylSizeScale
                                    transformOrigin = TransformOrigin.Center
                                    clip = false
                                }
                                .size(disc),
                        )
                    }
                }
            }

            LandscapeProjectionLyrics(
                lines = lines,
                positionMs = if (lyricStyleSnapshot != null) {
                    lyricStyleFrozenPositionMs
                } else {
                    positionMs
                },
                trackDurationMs = durationMs,
                fontScale = displayPrefs.fontScale,
                lineSpacingDp = displayPrefs.lyricLineSpacingDp,
                playedCount = displayPrefs.lyricPlayedCount,
                upcomingCount = displayPrefs.lyricUpcomingCount,
                offsetXDp = displayPrefs.lyricOffsetXDp,
                dynamicLyrics = displayPrefs.dynamicLyrics,
                vinylLeftInset = vinylLeftInset,
                onSeekToMs = { ms ->
                    onSeek(ms.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                    if (displayPrefs.lyricTapAutoPlay && !playWhenReady) {
                        onTogglePlay()
                    }
                },
                resumeScrollToken = lyricResumeToken,
                selectProgress = lyricSelectT,
                selectGeom = lyricSelectGeom,
                lyricsColStartDp = lyricsColStart,
                selectedIndices = lyricSelectSelected,
                onToggleSelect = { index ->
                    if (index in lyricSelectSelected) {
                        lyricSelectSelected.remove(index)
                    } else {
                        lyricSelectSelected.add(index)
                    }
                },
                onLongPressLine = { index -> openLyricSelect(index) },
                onBandCenterPx = null,
                selectHazeState = settingsHazeState,
                selectOpen = lyricSelectOpen,
                playingStyle = displayPrefs.lyricPlayingStyle,
                playedStyle = displayPrefs.lyricPlayedStyle,
                unplayedStyle = displayPrefs.lyricUnplayedStyle,
                onLyricBandCoords = { lyricsBandCoords = it },
                modifier = Modifier
                    .weight(0.64f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        clip = false
                        alpha = liveLyricAlpha
                    }
                    .padding(start = 0.dp, end = 4.dp),
            )
        }

        // 标题信息层：歌名 / 制作人 / 歌单；水平对齐可切换且可打断
        LandscapeAlignedSongMeta(
            track = track,
            sourceTitle = sourceTitle,
            onSourceClick = onSourceClick,
            onRevealControls = { revealControls() },
            titleAlign = displayPrefs.titleAlign,
            songMetaTopPad = songMetaTopPad,
            chromeSidePad = chromeSidePad,
            vinylCenterX = vinylCenterX,
            lyricsCenterX = lyricsCenterX,
            screenCenterX = screenCenterX,
            titleMaxWidth = titleMaxWidth,
            modifier = Modifier.fillMaxSize(),
        )
        } // uiScale 内容层

        // 贴底、左右留边：仅上方圆角，底边无圆角
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
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                        ),
                    )
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
                    isPlaying = playWhenReady,
                    buffering = loadPending,
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
                    trackLiked = trackLiked,
                    onToggleLike = {
                        if (!controlsLocked) {
                            revealControls()
                            onToggleLike()
                        }
                    },
                    portraitSlim = false,
                    landscapeDense = true,
                    onOpenScore = {
                        if (!controlsLocked) openScore()
                    },
                )
            }

            // 右上：退出 | 旋转锁定 | 设置（与底部播放条同显隐 / 同底 / 右缘对齐）
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
                NowPlayingDismissIconButton(
                    onClick = onDismiss,
                )
                NowPlayingRotationLockButton(
                    locked = rotationLocked,
                    onClick = { rotationLock.toggle(activity) },
                )
                NowPlayingSettingsIconButton(
                    onClick = { openSettings() },
                )
            }
        }

        // 右上短通知：避让 chrome 图标；入场/出场/Y 位移可打断
        PlaybackCornerNotice(
            notice = notice,
            chromeProgress = chromeT,
            topBase = songMetaTopPad,
            endPad = chromeSidePad,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    scaleX = uiScale
                    scaleY = uiScale
                    transformOrigin = TransformOrigin(1f, 0f)
                },
        )
        } // hazeSource：播放内容

        // 设置层：从右向左曲线展开；点外部 / 返回收回；无蒙版变暗
        // 卡片顶/底边距 = 右侧边距（chromeSidePad）
        if (settingsT > 0.001f || settingsOpen) {
            NowPlayingSettingsOutsideDismiss(
                onDismiss = { closeSettings() },
                modifier = Modifier.fillMaxSize(),
            )
            BoxWithConstraints(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f)
                    .padding(
                        top = chromeSidePad,
                        bottom = chromeSidePad,
                        end = chromeSidePad,
                    ),
            ) {
                val panelW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                NowPlayingSettingsSheet(
                    prefs = displayPrefs,
                    onPrefsChange = onDisplayPrefsChange,
                    hazeState = settingsHazeState,
                    onOpenVinylColorEditor = { openVinylColorEditor() },
                    onOpenLyricStyleEditor = { openLyricStyleEditor() },
                    hazeNonce = hazeNonce,
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

        // 曲谱层：玻璃/间距同设置；左边界使黑胶两侧留白相等（1:1 构图）
        // 「<」加宽后左/右边距对称 = chromeSidePad，动画覆盖黑胶
        if (scoreT > 0.001f || scoreOpen) {
            val scaleOriginX = maxWidth / 2
            val visualVinylCx = scaleOriginX + (vinylCenterX - scaleOriginX) * uiScale
            val visualVinylR = discExpandedForPad / 2 * uiScale * vinylSizeScale *
                maxOf(vinylOuterScale, 1f)
            val vinylLeftVisual = visualVinylCx - visualVinylR
            val equalGap = vinylLeftVisual.coerceAtLeast(0.dp)
            val scoreCollapsedStart = visualVinylCx + visualVinylR + equalGap
            val scoreCollapsedWidth = (maxWidth - scoreCollapsedStart).coerceAtLeast(96.dp)
            // 加宽：左缘 = chromeSidePad，与右缘对称
            val scoreExpandedWidth = (maxWidth - chromeSidePad).coerceAtLeast(96.dp)
            val scoreOuterWidth = lerpDp(scoreCollapsedWidth, scoreExpandedWidth, scoreCoverT)
            // 内宽：外边距在外侧，避免宽含 endPad 导致左缘与磨砂错位出黑边
            val scoreSheetWidth = (scoreOuterWidth - chromeSidePad).coerceAtLeast(80.dp)
            NowPlayingSettingsOutsideDismiss(
                onDismiss = { closeScore() },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(
                        top = chromeSidePad,
                        bottom = chromeSidePad,
                        end = chromeSidePad,
                    ),
            ) {
                BoxWithConstraints(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(scoreSheetWidth),
                ) {
                    val panelW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    ScoreSheetOverlay(
                        coverExpanded = scoreCoverExpanded,
                        coverExpandProgress = scoreCoverT,
                        onToggleCoverExpand = { scoreCoverExpanded = !scoreCoverExpanded },
                        tracks = queue,
                        currentIndex = queueIndex,
                        plateColors = displayPrefs.vinylPlateColors(),
                        onPlayTrack = { idx, center, sizePx ->
                            startScoreFlight(idx, center, sizePx)
                        },
                        openGeneration = scoreOpenGeneration,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                translationX = (1f - scoreT) * panelW
                                alpha = scoreT
                            },
                    )
                }
            }
        }

        // 曲谱飞入：曲线落到主黑胶；覆盖后开播并抑制常规切歌位移动画
        val flight = scoreFlight
        if (flight != null) {
            ScoreVinylFlightLayer(
                flight = flight,
                targetCenter = mainVinylCenterRoot,
                targetSizePx = mainVinylSizePx,
                plateColors = displayPrefs.vinylPlateColors(),
                fullCover = displayPrefs.vinylFullCover,
                centerRadiusFrac = displayPrefs.vinylCenterRadiusFrac,
                outerScale = vinylOuterScale,
                // 目标尺寸来自主黑胶 bounds（已含 sizeScale / uiScale / 外圈视觉）
                onCoverTarget = {
                    onPlayQueueIndex(flight.queueIndex)
                },
                onFinished = { finishScoreFlight() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 自选黑胶颜色编辑：黑胶先居中就位，再渐显弹窗
        if (editorT > 0.001f) {
            // 与内容层同源：bottom pad + uiScale（绕内容中心）后的视觉几何
            val contentH = (maxHeight - 6.dp).coerceAtLeast(1.dp)
            val layoutVinylCy = contentH / 2
            val scaleOriginX = maxWidth / 2
            val editorVinylRadius = discExpandedForPad / 2 * uiScale * vinylSizeScale *
                maxOf(vinylOuterScale, 1f)
            val editorVinylCx = scaleOriginX + (vinylCenterX - scaleOriginX) * uiScale
            val editorVinylCy = layoutVinylCy
            VinylColorEditorOverlay(
                prefs = displayPrefs,
                onPrefsChange = onDisplayPrefsChange,
                hazeState = settingsHazeState,
                progress = editorT,
                vinylCenterX = editorVinylCx,
                vinylCenterY = editorVinylCy,
                vinylRadius = editorVinylRadius,
                screenWidth = maxWidth,
                screenHeight = maxHeight,
                onDismiss = { closeVinylColorEditor() },
                onBackToSettings = { closeVinylColorEditorToSettings() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 歌词样式：面板与克隆分离；克隆 morph 终点用静止槽几何（不含入场 layer）
        val styleSnap = lyricStyleSnapshot
        if (styleSnap != null &&
            (lyricStyleEditorOpen || lyricStyleT > 0.001f || styleCloneAlpha > 0.001f)
        ) {
            val restSlot = lyricStyleRestPreviewSlot(
                screenWidth = rootMaxW,
                screenHeight = rootMaxH,
                chromeSidePad = chromeSidePad,
                previewWidthDp = styleSnap.sourceWidthDp,
            )
            LyricStyleEditorOverlay(
                draftPlaying = draftLyricPlaying,
                draftPlayed = draftLyricPlayed,
                draftUnplayed = draftLyricUnplayed,
                onDraftPlayingChange = { draftLyricPlaying = it },
                onDraftPlayedChange = { draftLyricPlayed = it },
                onDraftUnplayedChange = { draftLyricUnplayed = it },
                hazeState = settingsHazeState,
                progress = lyricStyleT,
                chromeSidePad = chromeSidePad,
                previewWidthDp = styleSnap.sourceWidthDp,
                onDismiss = { closeLyricStyleEditor() },
                onBackToSettings = { closeLyricStyleEditorToSettings() },
                modifier = Modifier.fillMaxSize(),
            )
            LyricStyleCloneLayer(
                snapshot = styleSnap,
                draftPlaying = draftLyricPlaying,
                draftPlayed = draftLyricPlayed,
                draftUnplayed = draftLyricUnplayed,
                progress = lyricStyleT,
                targetSlot = restSlot,
                contentAlpha = styleCloneAlpha,
                uiScale = uiScale,
            )
        }

        // 长按歌词选句：磨砂壳 + 挖孔；原歌词层位移动画填入，非另起列表
        if (lyricSelectT > 0.001f) {
            val ctx = LocalContext.current
            LyricSelectOverlay(
                selectedCount = lyricSelectSelected.size,
                hazeState = settingsHazeState,
                progress = lyricSelectT,
                selectOpen = lyricSelectOpen,
                geom = lyricSelectGeom,
                hazeNonce = hazeNonce,
                onDismiss = { closeLyricSelect() },
                onClearSelection = { lyricSelectSelected.clear() },
                onCopy = {
                    copyLyricSelection(ctx, lines, lyricSelectSelected.toSet())
                    closeLyricSelect()
                },
                onOutsideDismissArmed = { lyricSelectOutsideArmed = true },
                modifier = Modifier.fillMaxSize(),
            )
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
    trackLiked: Boolean,
    onToggleLike: () -> Unit,
    portraitSlim: Boolean = false,
    landscapeDense: Boolean = false,
    controlsLocked: Boolean = false,
    onOpenScore: (() -> Unit)? = null,
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

    // 横屏：全宽简约条 — 左：模式+传输+喜欢；右：当前时间 | 进度 | 总时长（同一水平线）
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
            Box(
                modifier = Modifier
                    .size(skipHit)
                    .clip(CircleShape)
                    .clickable(
                        enabled = !controlsLocked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleLike,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TransportLikeIcon(
                    liked = trackLiked,
                    size = skipHit * 0.70f,
                    outlineTint = iconTint,
                )
            }

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
            if (onOpenScore != null) {
                Box(
                    modifier = Modifier
                        .size(skipHit)
                        .clip(CircleShape)
                        .clickable(
                            enabled = !controlsLocked,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenScore,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    TransportScoreIcon(
                        size = 16.dp,
                        tint = iconTint,
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

            Box(
                modifier = Modifier
                    .size(playSize)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleLike),
                contentAlignment = Alignment.Center,
            ) {
                TransportLikeIcon(
                    liked = trackLiked,
                    size = playSize * 0.70f,
                    outlineTint = iconTint,
                )
            }
        }
    }
}
