package com.kite.zmusic.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.VinylPlateColors
import com.kite.zmusic.ui.common.UrlImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

enum class VinylSkipDirection {
    Next,
    Previous,
}

private val VinylDim = Color(0xFF8FA8B8)
/** 缓慢优雅：柔和 ease-in-out */
private val VinylMotion = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)

/** 轴心镂空相对整盘半径（固定，不受中心黑胶半径设置影响）。 */
private const val SpindleHoleFrac = 0.048f
private const val CoverFrac = 0.76f
/** 默认中心黑胶挖孔（相对整盘）；与 prefs 默认一致 */
private const val DefaultCoverHoleFrac = 0.20f

private const val NextExitMs = 920
/** 下一首底层起始缩放 */
private const val NextUnderScale = 0.85f
private const val PrevEnterMs = 820
private const val NextGrowMs = 520

/** 甩动切歌速度门槛（提高以减少轻扫误触） */
private const val FlingVelocityPx = 1400f

/** 离场层上限，超出丢弃最旧层 */
private const val MaxExitingLayers = 4

/**
 * 离场中的黑胶层：不打断收尾，与当前可交互层叠放。
 */
private class ExitingVinylLayer(
    val key: Long,
    val track: TrackRow,
    val x: Animatable<Float, AnimationVector1D>,
    val scale: Animatable<Float, AnimationVector1D>,
    /** 离场瞬间冻结的封面角，避免与新顶层抢同一旋转状态 */
    val frozenSpinDeg: Float,
)

/**
 * 切歌黑胶舞台：
 * - 手势跟手：右→左下一首；左→右上一首
 * - 提交后旧胶继续离场收尾，新胶立刻可再滑（多层叠放）
 * - 未达阈值松手回弹；peek 与 skip 目标一致
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun VinylTransitionStage(
    track: TrackRow,
    peekNext: TrackRow?,
    peekPrev: TrackRow?,
    spinning: Boolean,
    direction: VinylSkipDirection,
    gesturesEnabled: Boolean,
    onTransitionRunningChange: (Boolean) -> Unit,
    onCommitSkip: (VinylSkipDirection) -> Unit,
    /** 当前落定展示的黑胶曲目（含手势已提交、播放状态尚未重组时） */
    onSettledTrackChange: (TrackRow) -> Unit = {},
    modifier: Modifier = Modifier,
    fullCover: Boolean = false,
    /**
     * 中心黑胶半径（相对整体大小 = 100% 的基准盘）：封面中心挖孔外缘；
     * 与 [outerScale] 解耦。完整封面时由内部忽略挖孔。
     */
    centerRadiusFrac: Float = DefaultCoverHoleFrac,
    /**
     * 外圈黑胶倍率：仅缩放黑胶盘面（绕中心）；封面锁定在整体容器的 CoverFrac。
     */
    outerScale: Float = 1f,
    plateColors: VinylPlateColors = VinylPlateColors.Black,
    /**
     * 上一首入场起点：相对舞台中心，整盘完全离开左栏左缘所需的位移（px）。
     */
    prevEnterSlidePx: Float? = null,
    /** 为 true 时外部切歌直接落定，不做离场/入场位移动画（曲谱飞入专用）。 */
    suppressEnterTransition: Boolean = false,
    /**
     * 切歌手势阻尼（灵敏度）：默认 0.5 与历史 96dp / 甩速阈值一致；
     * 值越高阈值越低、越灵敏。
     */
    gestureDamping: Float = 0.5f,
    /**
     * 选歌入场：停止连转后把当前角沿最短路径动画归正到 0°（避免叠层 0° 交接瞬移）。
     */
    settleSpinUpright: Boolean = false,
) {
    var topTrack by remember { mutableStateOf(track) }
    var bottomTrack by remember { mutableStateOf(track) }
    var settledId by remember { mutableLongStateOf(track.id) }
    var booted by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragMode by remember { mutableStateOf<VinylSkipDirection?>(null) }
    var followX by remember { mutableFloatStateOf(0f) }
    var stickyTopX by remember { mutableFloatStateOf(Float.NaN) }
    var prevRevealBase by remember { mutableFloatStateOf(0f) }
    /** 回弹中不可开新手势；离场收尾不锁手势 */
    var bounceRunning by remember { mutableStateOf(false) }

    val topX = remember { Animatable(0f) }
    val topScale = remember { Animatable(1f) }
    val bottomX = remember { Animatable(0f) }
    val bottomScale = remember { Animatable(1f) }
    var showBottom by remember { mutableStateOf(false) }

    val exiting = remember { mutableStateListOf<ExitingVinylLayer>() }
    var exitSeq by remember { mutableLongStateOf(0L) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var bounceJob by remember { mutableStateOf<Job?>(null) }

    /**
     * 顶层旋转角：用可变 Float 同步清零（非挂起），保证入场首帧必为 0°。
     * 旧实现按 trackId 缓存 Animatable，切歌首帧常仍读到上一圈角度。
     *
     * [topSpinEpoch] 与 [topSpinGen] 同步递增，但可读不依赖重组：
     * 旧旋转协程在 reset 后、下一帧重组前仍可能跑一帧，必须用 epoch 同步丢弃写回。
     */
    var topSpinDeg by remember { mutableFloatStateOf(0f) }
    var topSpinGen by remember { mutableIntStateOf(0) }
    val topSpinEpoch = remember { intArrayOf(0) }
    /** 上一首预览前保存的当前盘角度；取消/回撤时还原，NaN 表示无待还原值 */
    var savedPrevSpinDeg by remember { mutableFloatStateOf(Float.NaN) }
    fun resetTopSpin() {
        topSpinEpoch[0]++
        topSpinGen = topSpinEpoch[0]
        topSpinDeg = 0f
    }
    fun restoreSavedPrevSpin() {
        if (savedPrevSpinDeg.isNaN()) return
        topSpinEpoch[0]++
        topSpinGen = topSpinEpoch[0]
        topSpinDeg = savedPrevSpinDeg
        savedPrevSpinDeg = Float.NaN
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val settleSpin by rememberUpdatedState(settleSpinUpright)
    val onSettledTrackChangeUpdated by rememberUpdatedState(onSettledTrackChange)
    fun publishSettledTrack(t: TrackRow) {
        onSettledTrackChangeUpdated(t)
    }

    // 选歌入场：连转已停时，把冻结角平滑归正，禁止瞬间跳到 0°
    LaunchedEffect(settleSpinUpright, track.id) {
        if (!settleSpinUpright) return@LaunchedEffect
        val start = topSpinDeg
        val norm = ((start % 360f) + 360f) % 360f
        val shortest = if (norm <= 180f) norm else norm - 360f
        topSpinEpoch[0]++
        topSpinGen = topSpinEpoch[0]
        if (abs(shortest) < 0.4f) {
            topSpinDeg = 0f
            return@LaunchedEffect
        }
        val anim = Animatable(start)
        anim.animateTo(
            targetValue = start - shortest,
            animationSpec = tween(
                durationMillis = 520,
                easing = FastOutSlowInEasing,
            ),
        ) {
            if (settleSpin) topSpinDeg = value
        }
        if (settleSpin) topSpinDeg = 0f
    }

    LaunchedEffect(bounceRunning, exiting.size, dragging) {
        onTransitionRunningChange(bounceRunning || exiting.isNotEmpty() || dragging)
    }

    BoxWithConstraints(
        modifier.graphicsLayer { clip = false },
        contentAlignment = Alignment.Center,
    ) {
        val stageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val exitPx = stageW * 2.35f
        val slidePx = (prevEnterSlidePx?.takeIf { it > stageW * 0.5f } ?: (stageW * 1.06f))
            .coerceAtLeast(stageW * 0.92f)
        // 0.5 → 倍率 1（历史默认）；越高越灵敏 → 阈值越低
        val damp = gestureDamping.coerceIn(
            PlayerDisplayPrefs.VINYL_GESTURE_DAMPING_MIN,
            PlayerDisplayPrefs.VINYL_GESTURE_DAMPING_MAX,
        )
        val threshScale = (0.5f / damp).coerceIn(0.45f, 2.2f)
        val commitBase = with(density) { 96.dp.toPx() }
            .coerceAtMost(stageW * 0.42f)
            .coerceAtLeast(1f)
        // 必须保证 commitPx ≤ prevUpper，否则 coerceIn(commitPx, prevUpper) 会因 min>max 崩
        val prevUpper = stageW * 0.72f
        val commitPx = (commitBase * threshScale).coerceIn(1f, prevUpper)
        val prevCommitPx = (stageW * 0.40f * threshScale).coerceIn(commitPx, prevUpper)
        val flingVelocityPx = FlingVelocityPx * threshScale

        fun prevReveal(rawFollow: Float): Float =
            (rawFollow - prevRevealBase).coerceAtLeast(0f)

        fun trimExiting() {
            while (exiting.size > MaxExitingLayers) {
                exiting.removeAt(0)
            }
        }

        /** 旧胶离场：独立层继续飞出，不打断 */
        fun spawnExitNext(outgoing: TrackRow, fromX: Float, fromScale: Float = 1f) {
            exitSeq += 1L
            val layer = ExitingVinylLayer(
                key = exitSeq,
                track = outgoing,
                x = Animatable(fromX.coerceIn(-exitPx, 0f)),
                scale = Animatable(fromScale.coerceIn(NextUnderScale, 1f)),
                frozenSpinDeg = topSpinDeg,
            )
            exiting.add(layer)
            trimExiting()
            scope.launch {
                try {
                    layer.x.animateTo(
                        targetValue = -exitPx,
                        animationSpec = tween(NextExitMs, easing = VinylMotion),
                    )
                } finally {
                    exiting.removeAll { it.key == layer.key }
                }
            }
        }

        fun promoteIncoming(
            incoming: TrackRow,
            startScale: Float,
            startX: Float = 0f,
            animateEnter: Boolean = false,
            underTrack: TrackRow? = null,
        ) {
            settleJob?.cancel()
            val keepUnderSpin = animateEnter &&
                underTrack != null &&
                underTrack.id != incoming.id
            if (keepUnderSpin) {
                // 入场覆盖期间底层旧盘保持冻结角；手势预览已存则沿用，按钮切歌则现取
                if (savedPrevSpinDeg.isNaN()) {
                    savedPrevSpinDeg = topSpinDeg
                }
            } else {
                savedPrevSpinDeg = Float.NaN
            }
            // 必须先同步清零再换顶层，否则入场首帧仍带旧旋转
            resetTopSpin()
            topTrack = incoming
            stickyTopX = Float.NaN
            settledId = incoming.id
            publishSettledTrack(incoming)
            followX = 0f
            prevRevealBase = 0f
            dragMode = null
            if (underTrack != null && underTrack.id != incoming.id) {
                bottomTrack = underTrack
                showBottom = true
            } else {
                bottomTrack = incoming
                showBottom = false
            }
            settleJob = scope.launch {
                if (animateEnter) {
                    topX.snapTo(startX.coerceIn(-slidePx, 0f))
                    topScale.snapTo(1f)
                    topX.animateTo(0f, tween(PrevEnterMs, easing = VinylMotion))
                    bottomTrack = incoming
                    showBottom = false
                    savedPrevSpinDeg = Float.NaN
                    topX.snapTo(0f)
                } else {
                    topX.snapTo(0f)
                    topScale.snapTo(startScale.coerceIn(NextUnderScale, 1f))
                    topScale.animateTo(1f, tween(NextGrowMs, easing = VinylMotion))
                }
            }
        }

        fun rubberDrag(raw: Float): Float {
            val lo = if (peekNext != null) -exitPx else -commitPx * 0.45f
            val hi = when {
                peekPrev == null -> commitPx * 0.45f
                dragMode == VinylSkipDirection.Previous -> prevRevealBase + slidePx
                else -> slidePx
            }
            val x = raw.coerceIn(lo, hi)
            return when {
                x < 0f && peekNext == null -> x * 0.35f
                x > 0f && peekPrev == null -> x * 0.35f
                else -> x
            }
        }

        val displayX = when {
            dragging && dragMode == VinylSkipDirection.Previous ->
                -slidePx + prevReveal(followX)
            dragging -> followX
            !stickyTopX.isNaN() -> stickyTopX
            else -> topX.value
        }
        val displayBottomScale = when {
            dragging && dragMode == VinylSkipDirection.Next && showBottom -> {
                val p = (abs(followX) / commitPx).coerceIn(0f, 1f)
                NextUnderScale + (1f - NextUnderScale) * 0.40f * p
            }
            else -> bottomScale.value
        }

        val dragState = rememberDraggableState { delta ->
            if (!dragging) return@rememberDraggableState
            followX = rubberDrag(followX + delta)
            val x = followX
            when {
                x < -1.5f && peekNext != null -> {
                    dragMode = VinylSkipDirection.Next
                    prevRevealBase = 0f
                    if (topTrack.id != settledId) {
                        topTrack = track
                    }
                    bottomTrack = peekNext
                    showBottom = true
                    scope.launch {
                        bottomScale.snapTo(NextUnderScale)
                        bottomX.snapTo(0f)
                    }
                }
                x > 1.5f && peekPrev != null -> {
                    if (dragMode != VinylSkipDirection.Previous) {
                        dragMode = VinylSkipDirection.Previous
                        prevRevealBase = x
                        bottomTrack = if (topTrack.id == settledId) topTrack else track
                        // 当前盘压到底层前先存角；顶层换上一首封面须从 0° 起
                        savedPrevSpinDeg = topSpinDeg
                        resetTopSpin()
                        topTrack = peekPrev
                        showBottom = true
                        scope.launch {
                            bottomScale.snapTo(1f)
                            bottomX.snapTo(0f)
                        }
                    }
                }
                dragMode == VinylSkipDirection.Previous && x > prevRevealBase -> Unit
                dragMode == VinylSkipDirection.Next && x < 0f -> Unit
                else -> {
                    if (dragMode == VinylSkipDirection.Previous && topTrack.id != settledId) {
                        topTrack = track
                        restoreSavedPrevSpin()
                    }
                    dragMode = null
                    prevRevealBase = 0f
                    showBottom = false
                }
            }
        }

        // 外部切歌（播放条按钮等）：同样叠层收尾，尽快恢复可滑
        val suppressEnter by rememberUpdatedState(suppressEnterTransition)
        LaunchedEffect(track.id) {
            if (!booted) {
                resetTopSpin()
                topTrack = track
                bottomTrack = track
                settledId = track.id
                publishSettledTrack(track)
                stickyTopX = Float.NaN
                topX.snapTo(0f)
                topScale.snapTo(1f)
                bottomX.snapTo(0f)
                bottomScale.snapTo(1f)
                showBottom = false
                booted = true
                return@LaunchedEffect
            }
            if (track.id == settledId) return@LaunchedEffect
            if (topTrack.id == track.id) {
                // 手势已换顶层：再断言 0°（防旧旋转协程在 reset↔重组间隙写回污染）
                resetTopSpin()
                settledId = track.id
                publishSettledTrack(track)
                return@LaunchedEffect
            }
            // 曲谱飞入接管：禁止常规切歌位移动画，直接落定
            if (suppressEnter) {
                settleJob?.cancel()
                bounceJob?.cancel()
                exiting.clear()
                dragging = false
                dragMode = null
                prevRevealBase = 0f
                stickyTopX = Float.NaN
                savedPrevSpinDeg = Float.NaN
                resetTopSpin()
                topTrack = track
                bottomTrack = track
                settledId = track.id
                publishSettledTrack(track)
                showBottom = false
                topX.snapTo(0f)
                topScale.snapTo(1f)
                bottomX.snapTo(0f)
                bottomScale.snapTo(1f)
                return@LaunchedEffect
            }
            dragging = false
            dragMode = null
            prevRevealBase = 0f
            stickyTopX = Float.NaN
            when (direction) {
                VinylSkipDirection.Next -> {
                    spawnExitNext(topTrack, topX.value, topScale.value)
                    promoteIncoming(track, NextUnderScale, animateEnter = false)
                }
                VinylSkipDirection.Previous -> {
                    val under = topTrack
                    promoteIncoming(
                        track,
                        1f,
                        startX = -slidePx,
                        animateEnter = true,
                        underTrack = under,
                    )
                }
            }
        }

        val canStartDrag = gesturesEnabled && !bounceRunning &&
            (dragging || topTrack.id == settledId)

        Box(
            Modifier
                .fillMaxSize()
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = canStartDrag,
                    onDragStarted = {
                        if (bounceRunning) return@draggable
                        // 仅接管当前层 settle，不取消离场层
                        settleJob?.cancel()
                        settleJob = null
                        if (topTrack.id != settledId) {
                            topTrack = track
                        }
                        followX = topX.value
                        dragging = true
                        dragMode = null
                        prevRevealBase = 0f
                        stickyTopX = Float.NaN
                    },
                    onDragStopped = { velocity ->
                        if (!dragging) return@draggable
                        val x = followX
                        val mode = dragMode
                        val reveal = if (mode == VinylSkipDirection.Previous) prevReveal(x) else 0f
                        val coverX = -slidePx + reveal
                        val scaleAtRelease = NextUnderScale + (1f - NextUnderScale) * 0.40f *
                            (abs(x) / commitPx).coerceIn(0f, 1f)
                        val goNext = mode == VinylSkipDirection.Next &&
                            (x <= -commitPx || velocity <= -flingVelocityPx) &&
                            peekNext != null
                        val goPrev = mode == VinylSkipDirection.Previous &&
                            (reveal >= prevCommitPx || velocity >= flingVelocityPx) &&
                            peekPrev != null
                        when {
                            goNext -> {
                                val incoming = checkNotNull(peekNext)
                                val outgoing = topTrack
                                dragging = false
                                dragMode = null
                                prevRevealBase = 0f
                                stickyTopX = Float.NaN
                                showBottom = false
                                // 先提交播放索引（主线程同步），再换盘；避免长按选歌读到旧 index
                                onCommitSkip(VinylSkipDirection.Next)
                                spawnExitNext(outgoing, x, topScale.value)
                                promoteIncoming(incoming, scaleAtRelease, animateEnter = false)
                            }
                            goPrev -> {
                                val incoming = checkNotNull(peekPrev)
                                // 跟手时 bottom 已是旧中心；否则用当前 settled
                                val under = when {
                                    showBottom && bottomTrack.id != incoming.id -> bottomTrack
                                    topTrack.id == settledId -> topTrack
                                    else -> track
                                }
                                dragging = false
                                dragMode = null
                                prevRevealBase = 0f
                                stickyTopX = Float.NaN
                                onCommitSkip(VinylSkipDirection.Previous)
                                promoteIncoming(
                                    incoming,
                                    1f,
                                    startX = coverX,
                                    animateEnter = true,
                                    underTrack = under,
                                )
                            }
                            else -> {
                                if (mode == VinylSkipDirection.Previous) {
                                    stickyTopX = coverX
                                } else if (mode == VinylSkipDirection.Next) {
                                    stickyTopX = x
                                }
                                dragging = false
                                bounceRunning = true
                                bounceJob?.cancel()
                                bounceJob = scope.launch {
                                    try {
                                        when (mode) {
                                            VinylSkipDirection.Previous -> {
                                                topX.snapTo(coverX)
                                                stickyTopX = Float.NaN
                                                topX.animateTo(
                                                    -slidePx,
                                                    spring(
                                                        dampingRatio = 0.86f,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                    ),
                                                )
                                                showBottom = false
                                                topTrack = track
                                                restoreSavedPrevSpin()
                                                topX.snapTo(0f)
                                                topScale.snapTo(1f)
                                            }
                                            VinylSkipDirection.Next -> {
                                                topX.snapTo(x)
                                                stickyTopX = Float.NaN
                                                if (showBottom) bottomScale.snapTo(scaleAtRelease)
                                                coroutineScope {
                                                    launch {
                                                        topX.animateTo(
                                                            0f,
                                                            spring(
                                                                dampingRatio = 0.86f,
                                                                stiffness = Spring.StiffnessMediumLow,
                                                            ),
                                                        )
                                                    }
                                                    if (showBottom) {
                                                        launch {
                                                            bottomScale.animateTo(
                                                                NextUnderScale,
                                                                tween(220, easing = VinylMotion),
                                                            )
                                                        }
                                                    }
                                                }
                                                showBottom = false
                                                bottomScale.snapTo(1f)
                                                topScale.snapTo(1f)
                                            }
                                            null -> {
                                                showBottom = false
                                                topTrack = track
                                                restoreSavedPrevSpin()
                                                stickyTopX = Float.NaN
                                                topX.snapTo(0f)
                                                topScale.snapTo(1f)
                                            }
                                        }
                                        followX = 0f
                                        prevRevealBase = 0f
                                        dragMode = null
                                        settledId = track.id
                                        publishSettledTrack(track)
                                    } finally {
                                        stickyTopX = Float.NaN
                                        bounceRunning = false
                                    }
                                }
                            }
                        }
                    },
                ),
        ) {
            if (showBottom) {
                key(bottomTrack.id, "bottom") {
                    // 上一首预览/入场覆盖：底层旧盘用冻结角，直到被完全盖住
                    val underSpin =
                        if (!savedPrevSpinDeg.isNaN()) savedPrevSpinDeg else 0f
                    VinylDiscFace(
                        track = bottomTrack,
                        spinDeg = underSpin,
                        spinning = false,
                        fullCover = fullCover,
                        centerRadiusFrac = centerRadiusFrac,
                        outerScale = outerScale,
                        plateColors = plateColors,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                            .graphicsLayer {
                                translationX = bottomX.value
                                scaleX = displayBottomScale
                                scaleY = displayBottomScale
                                transformOrigin = TransformOrigin.Center
                            },
                    )
                }
            }

            key(topTrack.id, "top") {
                VinylDiscFace(
                    track = topTrack,
                    spinDeg = topSpinDeg,
                    spinGen = topSpinGen,
                    spinEpoch = topSpinEpoch,
                    onSpinDegChange = { topSpinDeg = it },
                    spinning = spinning &&
                        !showBottom &&
                        !dragging &&
                        exiting.isEmpty() &&
                        abs(displayX) < 2f &&
                        abs(topScale.value - 1f) < 0.02f,
                    fullCover = fullCover,
                    centerRadiusFrac = centerRadiusFrac,
                    outerScale = outerScale,
                    plateColors = plateColors,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .graphicsLayer {
                            translationX = displayX
                            scaleX = topScale.value
                            scaleY = topScale.value
                            transformOrigin = TransformOrigin.Center
                        },
                )
            }

            // 离场层叠在上方继续飞出；手势挂在父 Box，不挡连续滑动
            exiting.forEachIndexed { index, layer ->
                key(layer.key, "exit") {
                    VinylDiscFace(
                        track = layer.track,
                        spinDeg = layer.frozenSpinDeg,
                        spinning = false,
                        fullCover = fullCover,
                        centerRadiusFrac = centerRadiusFrac,
                        outerScale = outerScale,
                        plateColors = plateColors,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(2f + index)
                            .graphicsLayer {
                                translationX = layer.x.value
                                scaleX = layer.scale.value
                                scaleY = layer.scale.value
                                transformOrigin = TransformOrigin.Center
                            },
                    )
                }
            }
        }
    }
}

@Composable
internal fun VinylDiscFace(
    track: TrackRow,
    spinDeg: Float,
    spinning: Boolean,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    plateColors: VinylPlateColors,
    modifier: Modifier = Modifier,
    spinGen: Int = 0,
    /** 与 spinGen 同源；动画帧内同步读取，不依赖重组后的 rememberUpdatedState */
    spinEpoch: IntArray? = null,
    onSpinDegChange: ((Float) -> Unit)? = null,
    /** 选歌叠层等交接场景关闭样式过渡，避免从 0 动画到用户设置造成闪一下 */
    animateStyleChanges: Boolean = true,
) {
    val coverTTarget = if (fullCover) 1f else 0f
    val coverTAnimated by animateFloatAsState(
        targetValue = coverTTarget,
        animationSpec = tween(
            durationMillis = 520,
            easing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f),
        ),
        label = "vinylFullCover",
    )
    val coverT = if (animateStyleChanges) coverTAnimated else coverTTarget
    // 外圈：只缩放黑胶盘面（绕中心）；封面尺寸锁定在整体容器的 CoverFrac
    val outerTarget = outerScale.coerceIn(0.5f, 1.6f)
    val outerAnimated by animateFloatAsState(
        targetValue = outerTarget,
        animationSpec = tween(
            durationMillis = 360,
            easing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f),
        ),
        label = "vinylOuterScale",
    )
    val outer = if (animateStyleChanges) outerAnimated else outerTarget
    // 中心挖孔相对整体容器（封面不随 outer 变），轴心在盘面本地坐标补偿 outer 缩放以保持绝对大小
    val coverHoleFrac = (centerRadiusFrac / CoverFrac).coerceIn(0.08f, 0.95f) * (1f - coverT)
    val spindleFrac = (SpindleHoleFrac / outer.coerceAtLeast(0.01f))
        .coerceIn(0.02f, 0.35f) * (1f - coverT)

    LaunchedEffect(spinning, track.id, spinGen) {
        if (!spinning || onSpinDegChange == null) return@LaunchedEffect
        val gen = spinGen
        // 切歌/gen 变更时外部已把 spinDeg 同步置 0；同曲暂停后再转则续当前角
        val anim = Animatable(spinDeg)
        while (isActive) {
            anim.animateTo(
                targetValue = anim.value + 360f,
                animationSpec = tween(durationMillis = 28_000, easing = LinearEasing),
            ) {
                // 同步读 epoch，丢弃已被 resetTopSpin 作废的旧协程写回
                val live = spinEpoch?.get(0) ?: gen
                if (live == gen) onSpinDegChange(value)
            }
        }
    }

    Box(
        modifier
            .graphicsLayer { clip = false }
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.40f),
                spotColor = Color.Black.copy(alpha = 0.28f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = spinDeg
                    clip = false
                },
            contentAlignment = Alignment.Center,
        ) {
            // 黑胶盘：按 outer 绕中心缩放；外圈 <100% 只收黑圈，不收封面
            VinylDiscPlate(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = outer
                        scaleY = outer
                        transformOrigin = TransformOrigin.Center
                        clip = false
                    },
                spindleHoleFrac = spindleFrac,
                colors = plateColors,
            )

            // 封面：只跟整体容器走，不受 outer 影响
            Box(
                Modifier
                    .fillMaxSize(CoverFrac)
                    .clip(
                        if (coverHoleFrac < 0.012f) {
                            CircleShape
                        } else {
                            VinylAnnulusShape(holeFrac = coverHoleFrac)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
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
                            .background(Color(0xFF121214)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "♪",
                            style = TextStyle(
                                color = VinylDim.copy(alpha = 0.35f),
                                fontSize = 36.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 圆环裁剪：外圆保留、中心镂空，露出下层黑胶纹理。 */
internal data class VinylAnnulusShape(
    private val holeFrac: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = min(size.width, size.height) * 0.5f
        val holeR = r * holeFrac.coerceIn(0f, 0.9f)
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
            if (holeR > 0.5f) {
                addOval(Rect(cx - holeR, cy - holeR, cx + holeR, cy + holeR))
            }
        }
        return Outline.Generic(path)
    }
}

/** 黑胶盘面：径向底 + 同心纹路；轴孔半径可动画收拢。 */
@Composable
internal fun VinylDiscPlate(
    modifier: Modifier = Modifier,
    spindleHoleFrac: Float = SpindleHoleFrac,
    colors: VinylPlateColors = VinylPlateColors.Black,
) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        val holeR = r * spindleHoleFrac.coerceAtLeast(0f)
        val discPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r))
            if (holeR > 0.5f) {
                addOval(Rect(c.x - holeR, c.y - holeR, c.x + holeR, c.y + holeR))
            }
        }
        clipPath(discPath) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to colors.baseInner,
                        0.18f to colors.baseMid,
                        0.55f to colors.baseOuter,
                        1.0f to colors.baseEdge,
                    ),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
            drawCircle(
                color = colors.rim.copy(alpha = 0.12f),
                radius = r * 0.985f,
                center = c,
                style = Stroke(width = r * 0.018f),
            )
            val innerStart = if (holeR > 0.5f) (holeR / r) + 0.012f else 0.04f
            val ringCount = 22
            // 深色纹路在浅底上需提高不透明度，否则几乎看不见
            val grooveBoost =
                if (colors.groove.red + colors.groove.green + colors.groove.blue < 1.35f) 3.2f else 1f
            for (i in 0 until ringCount) {
                val t = i / (ringCount - 1).toFloat()
                val rr = r * (innerStart + t * (0.97f - innerStart))
                val alpha = when {
                    rr < r * DefaultCoverHoleFrac -> 0.028f + (i % 2) * 0.012f
                    else -> 0.035f + (i % 2) * 0.018f
                }
                drawCircle(
                    color = colors.groove.copy(
                        alpha = (alpha * grooveBoost).coerceIn(0f, 0.42f),
                    ),
                    radius = rr,
                    center = c,
                    style = Stroke(width = if (rr < r * DefaultCoverHoleFrac) 0.9f else 1.1f),
                )
            }
            if (holeR > 0.5f) {
                drawCircle(
                    color = colors.holeLight.copy(
                        alpha = 0.10f * (holeR / (r * SpindleHoleFrac)).coerceIn(0f, 1f),
                    ),
                    radius = holeR * 1.08f,
                    center = c,
                    style = Stroke(width = r * 0.006f),
                )
                drawCircle(
                    color = colors.holeDark.copy(
                        alpha = 0.55f * (holeR / (r * SpindleHoleFrac)).coerceIn(0f, 1f),
                    ),
                    radius = holeR * 1.02f,
                    center = c,
                    style = Stroke(width = r * 0.004f),
                )
            }
        }
    }
}
