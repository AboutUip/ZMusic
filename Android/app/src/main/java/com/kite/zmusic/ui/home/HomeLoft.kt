package com.kite.zmusic.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.kite.zmusic.ui.main.MainPalette
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class HomePullPhase {
    Pull,
    Refresh,
    Loft,
}

/** 松手时只看位移，不靠速度跳过刷新直接进二楼。 */
internal enum class HomePullRelease {
    Cancel,
    Refresh,
    OpenLoft,
}

private val HomeLoftSpring = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = 480f,
)

private val HomeLoftReveal = tween<Float>(
    durationMillis = 460,
    easing = FastOutSlowInEasing,
)

/** 圆收回加载圆之后，加载圆再沿进楼时的路径向上退出。 */
private val HomeLoftIndicatorExit = tween<Float>(
    durationMillis = 320,
    easing = FastOutSlowInEasing,
)

/** 与 Material 下拉刷新相同：手指距离先折半，再拿去跟刷新线比。 */
internal const val HomeRefreshDragMultiplier = 0.5f

/** 刷新比二楼更早成立。二楼线保持不动，中间多留一段松手即刷新的距离。 */
private val HomeRefreshThreshold = 32.dp
private val HomeLoftThreshold = 80.dp

internal val LocalHomeRefreshBridge = staticCompositionLocalOf<HomeRefreshBridge?> { null }
internal val LocalMainFloorLocked = staticCompositionLocalOf { false }

/** 二楼里的表情面板要先吃掉返回键，再交给关楼。 */
@Stable
internal class LoftBackClaim {
    var held by mutableStateOf(false)
}

internal val LocalLoftBackClaim = staticCompositionLocalOf { LoftBackClaim() }

@Stable
internal class HomeRefreshBridge {
    var refreshing by mutableStateOf(false)
    var refresh: () -> Unit = {}
}

/** 二楼展开时锁住首页 / 功能 / 我的的横向切换。事件回调读这个，不读 CompositionLocal。 */
@Stable
internal class MainFloorLock {
    var locked by mutableStateOf(false)
}

internal fun homePullPhase(offsetPx: Float, refreshPx: Float, loftPx: Float): HomePullPhase {
    return when {
        offsetPx >= loftPx -> HomePullPhase.Loft
        offsetPx >= refreshPx -> HomePullPhase.Refresh
        else -> HomePullPhase.Pull
    }
}

internal fun homePullReturnRelease(
    offsetPx: Float,
    containerPx: Float,
    velocityY: Float,
): HomePullRelease {
    return when {
        velocityY < -900f -> HomePullRelease.Cancel
        containerPx <= 1f || offsetPx < containerPx * 0.82f -> HomePullRelease.Cancel
        else -> HomePullRelease.OpenLoft
    }
}

internal fun homePullRelease(offsetPx: Float, refreshPx: Float, loftPx: Float): HomePullRelease {
    return when {
        offsetPx >= loftPx -> HomePullRelease.OpenLoft
        offsetPx >= refreshPx -> HomePullRelease.Refresh
        else -> HomePullRelease.Cancel
    }
}

internal fun homeAdjustedPull(rawPx: Float): Float =
    (rawPx * HomeRefreshDragMultiplier).coerceAtLeast(0f)

/** Material 指示器的可见位移：过刷新线后橡皮筋，不再线性跟着手指跑。 */
internal fun homeRefreshVisualOffset(adjustedPx: Float, thresholdPx: Float): Float {
    if (thresholdPx <= 0f) return 0f
    val pulled = adjustedPx.coerceAtLeast(0f)
    if (pulled <= thresholdPx) return pulled
    val overshoot = abs(pulled / thresholdPx) - 1f
    val linear = overshoot.coerceIn(0f, 2f)
    val tension = linear - linear * linear / 4f
    return thresholdPx + thresholdPx * tension
}

internal fun homeIndicatorFraction(adjustedPx: Float, thresholdPx: Float, loftPx: Float): Float {
    if (thresholdPx <= 0f) return 0f
    val capped = adjustedPx.coerceIn(0f, loftPx)
    return homeRefreshVisualOffset(capped, thresholdPx) / thresholdPx
}

/** 过了二楼线不再跟手，圆从指示器半径扩到盖住整屏。 */
internal fun homeLoftCoverRadius(progress: Float, startRadius: Float, fullRadius: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return startRadius + (fullRadius - startRadius) * t
}

internal fun homeLoftFullRadius(center: Offset, width: Float, height: Float): Float {
    if (width <= 0f || height <= 0f) return startRadiusFallback(center)
    val dx = maxOf(center.x, width - center.x)
    val dy = maxOf(center.y, height - center.y)
    return hypot(dx, dy).coerceAtLeast(1f)
}

private fun startRadiusFallback(center: Offset): Float = hypot(center.x, center.y).coerceAtLeast(1f)

/** 圆还小的时候只看见加载圆本身，铺开后再让二楼内容出来。 */
internal fun homeLoftContentAlpha(progress: Float): Float =
    ((progress - 0.22f) / 0.38f).coerceIn(0f, 1f)

/** 刷新指示器完全收起时，圆心在顶栏内边距上方一个半径。 */
internal fun homeLoftIndicatorHiddenCenterY(indicatorTopPx: Float, indicatorRadiusPx: Float): Float =
    indicatorTopPx - indicatorRadiusPx

/** 退出二楼后，加载圆从展开时的位置回到隐藏位置。 */
internal fun homeLoftIndicatorExitCenterY(
    shownCenterY: Float,
    hiddenCenterY: Float,
    leave: Float,
): Float {
    val t = leave.coerceIn(0f, 1f)
    return shownCenterY + (hiddenCenterY - shownCenterY) * t
}

/** 前半段保持可见，后半段再淡出，避免还在屏内就消失。 */
internal fun homeLoftIndicatorExitAlpha(leave: Float): Float {
    val t = leave.coerceIn(0f, 1f)
    return ((1f - t) / 0.45f).coerceIn(0f, 1f)
}

internal fun homeLoftIndicatorExitScale(leave: Float): Float =
    1f - 0.16f * leave.coerceIn(0f, 1f)

/** 没过二楼线时一楼不动。展开由圆覆盖动画接管，不跟手指。 */
internal fun homeSheetOffset(adjustedPx: Float, loftPx: Float): Float = 0f

internal fun homePullOffsetAfterDrag(
    offsetPx: Float,
    deltaY: Float,
    refreshPx: Float,
    loftPx: Float,
    maxPx: Float,
): Float {
    if (deltaY == 0f) return offsetPx.coerceAtLeast(0f)
    if (deltaY < 0f) return (offsetPx + deltaY).coerceAtLeast(0f)
    val resistance = when {
        offsetPx >= loftPx -> 0.38f
        offsetPx >= refreshPx -> 0.72f
        else -> 1f
    }
    val cap = maxOf(maxPx, offsetPx)
    return (offsetPx + deltaY * resistance).coerceIn(0f, cap)
}

internal fun homePullConsumed(before: Float, after: Float, deltaY: Float, maxPx: Float): Float {
    if (deltaY < 0f) return after - before
    if (deltaY > 0f && (after > before || before >= maxPx - 0.5f)) return deltaY
    return 0f
}

@Stable
internal class HomeLoftState(
    private val scope: CoroutineScope,
) {
    var rawPx by mutableFloatStateOf(0f)
        private set
    var reveal by mutableFloatStateOf(0f)
        private set
    var dragging by mutableStateOf(false)
        private set
    var open by mutableStateOf(false)
        private set
    var returning by mutableStateOf(false)
        private set
    var containerPx by mutableFloatStateOf(0f)
    var widthPx by mutableFloatStateOf(0f)
    var refreshPx by mutableFloatStateOf(0f)
    var loftPx by mutableFloatStateOf(0f)
    var interruptPx by mutableFloatStateOf(48f)
    var armed by mutableStateOf(false)
    var blockPull by mutableStateOf(false)
    var pendingCenter by mutableStateOf(Offset.Zero)
    var coverCenter by mutableStateOf(Offset.Zero)
        private set
    var startRadius by mutableFloatStateOf(0f)
    var coverRadius by mutableFloatStateOf(0f)
        private set
    /** 0 为加载圆停在进楼位置，1 为沿原路径收进顶栏。 */
    var indicatorLeave by mutableFloatStateOf(0f)
        private set
    var indicatorTopPx by mutableFloatStateOf(0f)

    val adjustedPx: Float get() = homeAdjustedPull(rawPx)
    val locksFloor: Boolean get() = open || reveal > 0.01f

    private var upward = 0f
    private var job: Job? = null
    private var gen = 0

    fun dragBy(deltaY: Float): Float {
        if (!armed && !open && !returning && reveal <= 0f) return 0f
        if (blockPull && !open && !returning && reveal <= 0f) return 0f
        if (open || reveal > 0.001f) {
            if (deltaY < 0f) {
                upward += -deltaY
                if (!returning && upward >= interruptPx) close()
            } else if (deltaY > 0f) {
                upward = 0f
            }
            return deltaY
        }
        if (returning) return 0f
        cancelAnim()
        dragging = true
        upward = 0f
        val before = rawPx
        rawPx = (rawPx + deltaY).coerceAtLeast(0f)
        if (adjustedPx >= loftPx) beginOpen()
        return homePullConsumed(before, rawPx, deltaY, Float.MAX_VALUE)
    }

    fun settle(velocityY: Float, onRefresh: () -> Unit) {
        if (!dragging && !open) return
        dragging = false
        upward = 0f
        if (open || returning || reveal > 0.01f) {
            if (open && velocityY < -900f) close()
            return
        }
        when (homePullRelease(adjustedPx, refreshPx, loftPx)) {
            HomePullRelease.OpenLoft -> beginOpen()
            HomePullRelease.Refresh -> {
                onRefresh()
                val parked = if (HomeRefreshDragMultiplier == 0f) 0f else refreshPx / HomeRefreshDragMultiplier
                animateRaw(parked)
            }
            HomePullRelease.Cancel -> animateRaw(0f)
        }
    }

    fun onRefreshingChanged(active: Boolean) {
        if (open || dragging || returning || reveal > 0.01f) return
        if (active) {
            val parked = if (HomeRefreshDragMultiplier == 0f) 0f else refreshPx / HomeRefreshDragMultiplier
            animateRaw(parked)
        } else if (rawPx > 0.5f) {
            // 松手时往往还在刷新线以下，不能等它先回到线上再收。
            animateRaw(0f)
        }
    }

    fun beginOpen() {
        if (open || returning) return
        if (widthPx < 1f || containerPx < 1f) return
        coverCenter = pendingCenter
        coverRadius = homeLoftFullRadius(coverCenter, widthPx, containerPx)
        open = true
        upward = 0f
        animateReveal(1f)
    }

    fun close() {
        if (returning) return
        open = false
        returning = true
        upward = 0f
        indicatorLeave = 0f
        rawPx = 0f
        val my = ++gen
        job?.cancel()
        job = scope.launch {
            if (reveal > 0.001f) {
                Animatable(reveal).animateTo(0f, HomeLoftReveal) {
                    if (my == gen) reveal = value
                }
            }
            if (my != gen) return@launch
            reveal = 0f
            Animatable(0f).animateTo(1f, HomeLoftIndicatorExit) {
                if (my == gen) indicatorLeave = value
            }
            if (my != gen) return@launch
            indicatorLeave = 0f
            returning = false
        }
    }

    fun dismissBecauseLeftHome() {
        if (open || reveal > 0.01f) return
        dragging = false
        returning = false
        animateRaw(0f)
    }

    private fun cancelAnim() {
        gen += 1
        job?.cancel()
        job = null
    }

    private fun animateRaw(target: Float) {
        val dest = target.coerceAtLeast(0f)
        val my = ++gen
        job?.cancel()
        job = scope.launch {
            Animatable(rawPx).animateTo(dest, HomeLoftSpring) {
                if (my == gen) rawPx = value
            }
            if (my != gen) return@launch
            rawPx = dest
        }
    }

    private fun animateReveal(target: Float, onEnd: () -> Unit = {}) {
        val dest = target.coerceIn(0f, 1f)
        val my = ++gen
        job?.cancel()
        job = scope.launch {
            Animatable(reveal).animateTo(dest, HomeLoftReveal) {
                if (my == gen) reveal = value
            }
            if (my != gen) return@launch
            reveal = dest
            onEnd()
        }
    }
}

private class HomeLoftConnection(
    private val state: HomeLoftState,
    private val onRefresh: () -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val eatX = if (state.locksFloor && available.x != 0f) available.x else 0f
        if (state.open || state.reveal > 0.001f) {
            // 二楼里的列表先滚动，吃不掉的上滑才用来回一楼。
            return Offset(eatX, 0f)
        }
        val eatY = if (available.y < 0f && state.rawPx > 0f) state.dragBy(available.y) else 0f
        return Offset(eatX, eatY)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (state.open || state.reveal > 0.001f) {
            if (available.y < 0f) state.dragBy(available.y)
            return if (available.y > 0f) Offset(0f, available.y) else Offset.Zero
        }
        if (available.y <= 0f) return Offset.Zero
        return Offset(0f, state.dragBy(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (state.open && state.reveal > 0.98f) return Velocity.Zero
        if (!state.dragging && !state.open) return Velocity.Zero
        val pulled = state.rawPx > 0f || state.reveal > 0f || state.open
        state.settle(available.y, onRefresh)
        return if (pulled) available else Velocity.Zero
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class LoftRefreshIndicatorState : PullToRefreshState {
    var fraction by mutableFloatStateOf(0f)
    override val distanceFraction: Float get() = fraction
    override suspend fun animateToThreshold() { fraction = 1f }
    override suspend fun animateToHidden() { fraction = 0f }
    override suspend fun snapTo(targetValue: Float) { fraction = targetValue }
}

private class CircleCoverShape(
    private val center: Offset,
    private val radius: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        return Outline.Rounded(
            RoundRect(
                Rect(
                    center.x - radius,
                    center.y - radius,
                    center.x + radius,
                    center.y + radius,
                ),
                CornerRadius(radius, radius),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainLoftHost(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    armed: Boolean,
    indicatorInset: Dp,
    floorLock: MainFloorLock,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = remember { HomeLoftState(scope) }
    val backClaim = remember { LoftBackClaim() }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val refreshPx = with(density) { HomeRefreshThreshold.toPx() }
    val loftPx = with(density) { HomeLoftThreshold.toPx() }
    val interruptPx = with(density) { 28.dp.toPx() }
    val indicatorSizePx = with(density) { 40.dp.toPx() }
    val indicatorMarkPx = with(density) { 16.dp.toPx() }
    val indicatorTopPx = with(density) { indicatorInset.toPx() }
    SideEffect {
        state.refreshPx = refreshPx
        state.loftPx = loftPx
        state.interruptPx = interruptPx
        state.startRadius = indicatorSizePx / 2f
        state.indicatorTopPx = indicatorTopPx
        state.armed = armed
        state.blockPull = refreshing
        floorLock.locked = state.locksFloor
        val fractionNow = homeIndicatorFraction(
            state.adjustedPx.coerceAtMost(loftPx),
            refreshPx,
            loftPx,
        )
        val translation = fractionNow * refreshPx - indicatorSizePx
        state.pendingCenter = Offset(
            state.widthPx / 2f,
            indicatorTopPx + translation + indicatorSizePx / 2f,
        )
    }
    LaunchedEffect(refreshing) {
        state.onRefreshingChanged(refreshing)
    }
    LaunchedEffect(armed) {
        if (!armed && !state.open) state.dismissBecauseLeftHome()
    }
    val refreshRef = remember { object { var action: () -> Unit = onRefresh } }
    SideEffect { refreshRef.action = onRefresh }
    val connection = remember(state) { HomeLoftConnection(state) { refreshRef.action() } }
    val phase = homePullPhase(state.adjustedPx, refreshPx, loftPx)
    var refreshArmed by remember { mutableStateOf(false) }
    var loftArmed by remember { mutableStateOf(false) }
    LaunchedEffect(phase, state.dragging) {
        if (!state.dragging || state.returning) return@LaunchedEffect
        if (phase == HomePullPhase.Refresh && !refreshArmed) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            refreshArmed = true
        }
        if (phase == HomePullPhase.Loft && !loftArmed) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            loftArmed = true
        }
        if (phase == HomePullPhase.Pull) {
            refreshArmed = false
            loftArmed = false
        }
    }
    LaunchedEffect(phase) {
        if (phase == HomePullPhase.Loft && !state.open && !state.returning) {
            state.beginOpen()
        }
    }
    val indicator = remember { LoftRefreshIndicatorState() }
    val fraction = homeIndicatorFraction(
        state.adjustedPx.coerceAtMost(loftPx),
        refreshPx,
        loftPx,
    )
    SideEffect { indicator.fraction = fraction }
    CompositionLocalProvider(LocalLoftBackClaim provides backClaim) {
    val showIndicator = !state.open &&
        !state.returning &&
        state.reveal <= 0.001f &&
        state.indicatorLeave <= 0.001f &&
        (state.rawPx > 0.5f || refreshing)
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                state.widthPx = it.width.toFloat().coerceAtLeast(0f)
                state.containerPx = it.height.toFloat().coerceAtLeast(0f)
            }
            .nestedScroll(connection)
            .pointerInput(state.open && state.reveal > 0.98f) {
                if (state.open && state.reveal > 0.98f) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    if (!state.locksFloor) return@awaitEachGesture
                    down.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.positionChange()
                        change.consume()
                        if (change.changedToUp()) {
                            val velocity = tracker.calculateVelocity()
                            state.settle(velocity.y) { refreshRef.action() }
                            break
                        }
                        if (delta.y != 0f) {
                            tracker.addPosition(change.uptimeMillis, change.position)
                            state.dragBy(delta.y)
                        }
                    }
                }
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalMainFloorLocked provides state.locksFloor) {
                content()
            }
        }
        if (state.reveal > 0.001f || state.open) {
            val contentAlpha = homeLoftContentAlpha(state.reveal)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val radius = homeLoftCoverRadius(
                            state.reveal,
                            state.startRadius,
                            state.coverRadius.coerceAtLeast(state.startRadius),
                        )
                        clip = true
                        shape = CircleCoverShape(state.coverCenter, radius)
                    }
                    .pointerInput(state.open, state.reveal > 0.98f) {
                        if (state.open && state.reveal > 0.98f) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    }
                    .background(MainPalette.Surface),
            ) {
                HomeSecondFloor(
                    onClose = { state.close() },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha },
                )
                if (contentAlpha < 0.98f) {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (state.coverCenter.x - indicatorMarkPx / 2f).roundToInt(),
                                    (state.coverCenter.y - indicatorMarkPx / 2f).roundToInt(),
                                )
                            }
                            .size(16.dp)
                            .graphicsLayer { alpha = 1f - contentAlpha },
                    )
                }
            }
        }
        if (state.returning && state.reveal <= 0.001f && !state.open) {
            val leave = state.indicatorLeave.coerceIn(0f, 1f)
            val hiddenY = homeLoftIndicatorHiddenCenterY(state.indicatorTopPx, state.startRadius)
            val centerY = homeLoftIndicatorExitCenterY(state.coverCenter.y, hiddenY, leave)
            val exitAlpha = homeLoftIndicatorExitAlpha(leave)
            val exitScale = homeLoftIndicatorExitScale(leave)
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (state.coverCenter.x - state.startRadius).roundToInt(),
                            (centerY - state.startRadius).roundToInt(),
                        )
                    }
                    .size(40.dp)
                    .graphicsLayer {
                        alpha = exitAlpha
                        scaleX = exitScale
                        scaleY = exitScale
                        transformOrigin = TransformOrigin.Center
                    }
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .background(MainPalette.Surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MainPalette.Accent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (showIndicator) {
            PullToRefreshDefaults.Indicator(
                state = indicator,
                isRefreshing = refreshing && phase != HomePullPhase.Loft,
                containerColor = MainPalette.Surface,
                color = MainPalette.Accent,
                threshold = HomeRefreshThreshold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = indicatorInset),
            )
        }
        if (state.locksFloor && !(state.open && state.reveal > 0.98f)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    },
            )
        }
    }
    BackHandler(enabled = state.open && !backClaim.held) { state.close() }
    }
}

@Composable
internal fun HomeSecondFloor(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LoftChatFloor(onClose = onClose, modifier = modifier.background(MainPalette.Surface))
}
