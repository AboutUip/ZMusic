package com.kite.zmusic.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.kite.zmusic.data.TrackRow
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

/** 轴心镂空相对整盘半径；封面挖孔更大，露出中间黑胶环。 */
private const val SpindleHoleFrac = 0.048f
private const val CoverFrac = 0.76f
private const val CoverHoleFrac = 0.20f

private const val NextExitMs = 920
/** 下一首底层起始缩放 */
private const val NextUnderScale = 0.85f
private const val PrevEnterMs = 820

/** 旧胶右移出场：剩余可见比例（右侧）达到此值时，新胶应刚好缩放到 100% */
private const val NextGrowSyncRemain = 0.20f

private const val FlingVelocityPx = 900f

/**
 * 切歌黑胶舞台：
 * - 手势跟手（无透明度）：右→左下一首；左→右上一首（新胶从左侧跟手滑入）
 * - 未达阈值松手：上一首新胶缩回左侧；下一首旧胶弹回中心
 * - peek 必须与 PlaylistCoordinator 的 skip 目标一致（随机模式尤其重要）
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
    fullCover: Boolean = false,
    /**
     * 上一首入场起点：相对舞台中心，整盘完全离开左栏左缘所需的位移（px）。
     * 过大则跟手时看不见；过小则一现身就露右侧一角。
     */
    prevEnterSlidePx: Float? = null,
    modifier: Modifier = Modifier,
) {
    var topTrack by remember { mutableStateOf(track) }
    var bottomTrack by remember { mutableStateOf(track) }
    var settledId by remember { mutableLongStateOf(track.id) }
    var booted by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragMode by remember { mutableStateOf<VinylSkipDirection?>(null) }
    var transitionRunning by remember { mutableStateOf(false) }
    var followX by remember { mutableFloatStateOf(0f) }
    // 松手瞬间 dragging=false，但 Animatable.snapTo 异步；用同步粘性 X 避免上一首胶瞬移到中心
    var stickyTopX by remember { mutableFloatStateOf(Float.NaN) }
    /** 进入「上一首」手势时的 followX，揭示量从 0 起算，避免一现身就露出右侧一角 */
    var prevRevealBase by remember { mutableFloatStateOf(0f) }

    val topX = remember { Animatable(0f) }
    val topScale = remember { Animatable(1f) }
    val bottomX = remember { Animatable(0f) }
    val bottomScale = remember { Animatable(1f) }
    var showBottom by remember { mutableStateOf(false) }
    var handoffX by remember { mutableFloatStateOf(Float.NaN) }
    /** 手势松手后本地收尾动画；不等待 track 切换，避免几百毫秒停住 */
    var dragFinishJob by remember { mutableStateOf<Job?>(null) }

    val spinAngles = remember { mutableMapOf<Long, Animatable<Float, AnimationVector1D>>() }
    fun angleOf(id: Long): Animatable<Float, AnimationVector1D> =
        spinAngles.getOrPut(id) { Animatable(0f) }

    suspend fun resetSpin(id: Long) {
        angleOf(id).snapTo(0f)
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    fun setBusy(busy: Boolean) {
        transitionRunning = busy
        onTransitionRunningChange(busy)
    }

    BoxWithConstraints(
        // 不圆形裁剪：旧胶完整离场、新胶完整入场，可画进左侧挖孔区
        modifier.graphicsLayer { clip = false },
        contentAlignment = Alignment.Center,
    ) {
        // 下一首：旧胶需完整滑出可视区
        val stageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val exitPx = stageW * 2.35f
        // 上一首：默认略大于一盘；父级传入时含水平偏移后的真实离场距离
        val slidePx = (prevEnterSlidePx?.takeIf { it > stageW * 0.5f } ?: (stageW * 1.06f))
            .coerceAtLeast(stageW * 0.92f)
        val commitPx = with(density) { 56.dp.toPx() }
            .coerceAtMost(stageW * 0.32f)
            .coerceAtLeast(1f)
        // 上一首提交：至少露出约 28% 盘面，否则跟手感消失
        val prevCommitPx = (stageW * 0.28f).coerceIn(commitPx, stageW * 0.42f)
        // 旧胶中心左移至此，舞台宽度内约剩右侧 20%
        val nextGrowSyncX = -stageW * (1f - NextGrowSyncRemain)

        fun prevReveal(rawFollow: Float): Float =
            (rawFollow - prevRevealBase).coerceAtLeast(0f)

        suspend fun finishNextExit(exitStartX: Float, scaleStart: Float, incoming: TrackRow) {
            val outgoing = topTrack
            resetSpin(incoming.id)
            bottomTrack = incoming
            topTrack = outgoing
            showBottom = true
            bottomX.snapTo(0f)
            topX.snapTo(exitStartX.coerceIn(-exitPx, 0f))
            stickyTopX = Float.NaN
            bottomScale.snapTo(scaleStart.coerceIn(NextUnderScale, 1f))
            topScale.snapTo(1f)
            val syncX = nextGrowSyncX.coerceAtMost(topX.value - 1f)
            val startX = topX.value
            val startScale = bottomScale.value

            coroutineScope {
                val exitJob = launch {
                    topX.animateTo(
                        targetValue = -exitPx,
                        animationSpec = tween(NextExitMs, easing = VinylMotion),
                    )
                }
                launch {
                    while (isActive) {
                        val x = topX.value
                        if (x <= syncX) {
                            bottomScale.snapTo(1f)
                            break
                        }
                        val span = (startX - syncX).coerceAtLeast(1f)
                        val raw = ((startX - x) / span).coerceIn(0f, 1f)
                        val eased = VinylMotion.transform(raw)
                        bottomScale.snapTo(startScale + (1f - startScale) * eased)
                        withFrameNanos { }
                    }
                }
                exitJob.join()
                bottomScale.snapTo(1f)
            }

            topTrack = incoming
            topX.snapTo(0f)
            topScale.snapTo(1f)
            bottomTrack = incoming
            bottomScale.snapTo(1f)
            bottomX.snapTo(0f)
            showBottom = true
            withFrameNanos { }
            withFrameNanos { }
            showBottom = false
            settledId = incoming.id
        }

        suspend fun finishPrevEnter(enterStartX: Float, incoming: TrackRow) {
            resetSpin(incoming.id)
            topTrack = incoming
            showBottom = true
            bottomX.snapTo(0f)
            bottomScale.snapTo(1f)
            topX.snapTo(enterStartX.coerceIn(-slidePx, 0f))
            stickyTopX = Float.NaN
            topScale.snapTo(1f)
            topX.animateTo(
                targetValue = 0f,
                animationSpec = tween(PrevEnterMs, easing = VinylMotion),
            )
            bottomTrack = incoming
            showBottom = false
            topX.snapTo(0f)
            topScale.snapTo(1f)
            stickyTopX = Float.NaN
            settledId = incoming.id
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

        // 上一首：X = -slidePx + reveal（reveal 从 0 起，裁剪区内从无到有）
        val displayX = when {
            dragging && dragMode == VinylSkipDirection.Previous ->
                -slidePx + prevReveal(followX)
            dragging -> followX
            !stickyTopX.isNaN() -> stickyTopX
            else -> topX.value
        }
        val displayBottomScale = when {
            dragging && dragMode == VinylSkipDirection.Next && showBottom ->
                NextUnderScale + (1f - NextUnderScale) *
                    (abs(followX) / commitPx).coerceIn(0f, 1f)
            else -> bottomScale.value
        }

        val dragState = rememberDraggableState { delta ->
            if (!dragging) return@rememberDraggableState
            followX = rubberDrag(followX + delta)
            val x = followX
            when {
                x < -1.5f && peekNext != null -> {
                    if (dragMode == VinylSkipDirection.Previous && topTrack.id != track.id) {
                        topTrack = track
                    }
                    dragMode = VinylSkipDirection.Next
                    prevRevealBase = 0f
                    bottomTrack = peekNext
                    showBottom = true
                }
                x > 1.5f && peekPrev != null -> {
                    if (dragMode != VinylSkipDirection.Previous) {
                        dragMode = VinylSkipDirection.Previous
                        // 揭示从 0 开始：此时盘面完全在左侧裁剪外
                        prevRevealBase = x
                        bottomTrack = track
                        topTrack = peekPrev
                        showBottom = true
                        scope.launch {
                            bottomScale.snapTo(1f)
                            bottomX.snapTo(0f)
                            resetSpin(peekPrev.id)
                        }
                    }
                }
                dragMode == VinylSkipDirection.Previous && x > prevRevealBase -> Unit
                dragMode == VinylSkipDirection.Next && x < 0f -> Unit
                else -> {
                    if (dragMode == VinylSkipDirection.Previous && topTrack.id != track.id) {
                        topTrack = track
                    }
                    dragMode = null
                    prevRevealBase = 0f
                    showBottom = false
                }
            }
        }

        LaunchedEffect(track.id) {
            val dir = direction
            val fromDrag = !handoffX.isNaN()
            val startX = if (fromDrag) handoffX else 0f
            handoffX = Float.NaN
            dragging = false
            dragMode = null
            prevRevealBase = 0f

            if (!booted) {
                topTrack = track
                bottomTrack = track
                settledId = track.id
                stickyTopX = Float.NaN
                topX.snapTo(0f)
                topScale.snapTo(1f)
                bottomX.snapTo(0f)
                bottomScale.snapTo(1f)
                showBottom = false
                booted = true
                setBusy(false)
                return@LaunchedEffect
            }

            // 手势已在本地收尾：等动画结束再对齐真实 track，避免松手后停顿等待切歌
            val finishing = dragFinishJob
            if (finishing != null && finishing.isActive) {
                finishing.join()
                if (topTrack.id != track.id) {
                    topTrack = track
                    bottomTrack = track
                    topX.snapTo(0f)
                    topScale.snapTo(1f)
                    bottomX.snapTo(0f)
                    bottomScale.snapTo(1f)
                    showBottom = false
                }
                settledId = track.id
                stickyTopX = Float.NaN
                setBusy(false)
                return@LaunchedEffect
            }

            if (track.id == settledId &&
                abs(topX.value) < 1f &&
                abs(topScale.value - 1f) < 0.01f &&
                !showBottom &&
                !fromDrag &&
                stickyTopX.isNaN()
            ) {
                return@LaunchedEffect
            }

            setBusy(true)
            try {
                when (dir) {
                    VinylSkipDirection.Next -> {
                        if (fromDrag) {
                            val p = (abs(startX) / commitPx).coerceIn(0f, 1f)
                            finishNextExit(
                                exitStartX = startX,
                                scaleStart = NextUnderScale + (1f - NextUnderScale) * p,
                                incoming = track,
                            )
                        } else {
                            finishNextExit(
                                exitStartX = if (abs(topX.value) < 4f) 0f else topX.value,
                                scaleStart = NextUnderScale,
                                incoming = track,
                            )
                        }
                    }

                    VinylSkipDirection.Previous -> {
                        if (fromDrag) {
                            finishPrevEnter(enterStartX = startX, incoming = track)
                        } else {
                            val under = topTrack
                            bottomTrack = under
                            showBottom = true
                            finishPrevEnter(enterStartX = -slidePx, incoming = track)
                        }
                    }
                }
            } finally {
                stickyTopX = Float.NaN
                setBusy(false)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = gesturesEnabled && (
                        dragging ||
                            (!transitionRunning && track.id == settledId)
                        ),
                    onDragStarted = {
                        if (transitionRunning) return@draggable
                        dragging = true
                        dragMode = null
                        followX = 0f
                        prevRevealBase = 0f
                        setBusy(true)
                    },
                    onDragStopped = { velocity ->
                        if (!dragging) return@draggable
                        val x = followX
                        val mode = dragMode
                        val reveal = if (mode == VinylSkipDirection.Previous) prevReveal(x) else 0f
                        val coverX = -slidePx + reveal
                        val scaleAtRelease = NextUnderScale + (1f - NextUnderScale) *
                            (abs(x) / commitPx).coerceIn(0f, 1f)
                        val goNext = mode == VinylSkipDirection.Next &&
                            (x <= -commitPx || velocity <= -FlingVelocityPx) &&
                            peekNext != null
                        val goPrev = mode == VinylSkipDirection.Previous &&
                            (reveal >= prevCommitPx || velocity >= FlingVelocityPx) &&
                            peekPrev != null
                        val nextTrack = peekNext
                        val prevTrack = peekPrev
                        when {
                            goNext && nextTrack != null -> {
                                stickyTopX = x
                                dragging = false
                                dragMode = null
                                prevRevealBase = 0f
                                handoffX = Float.NaN
                                dragFinishJob?.cancel()
                                dragFinishJob = scope.launch {
                                    setBusy(true)
                                    try {
                                        topX.snapTo(x)
                                        stickyTopX = Float.NaN
                                        if (showBottom) bottomScale.snapTo(scaleAtRelease)
                                        finishNextExit(
                                            exitStartX = x,
                                            scaleStart = scaleAtRelease,
                                            incoming = nextTrack,
                                        )
                                    } finally {
                                        stickyTopX = Float.NaN
                                        setBusy(false)
                                    }
                                }
                                onCommitSkip(VinylSkipDirection.Next)
                            }
                            goPrev && prevTrack != null -> {
                                stickyTopX = coverX
                                dragging = false
                                dragMode = null
                                prevRevealBase = 0f
                                handoffX = Float.NaN
                                dragFinishJob?.cancel()
                                dragFinishJob = scope.launch {
                                    setBusy(true)
                                    try {
                                        topX.snapTo(coverX)
                                        stickyTopX = Float.NaN
                                        bottomScale.snapTo(1f)
                                        finishPrevEnter(
                                            enterStartX = coverX,
                                            incoming = prevTrack,
                                        )
                                    } finally {
                                        stickyTopX = Float.NaN
                                        setBusy(false)
                                    }
                                }
                                onCommitSkip(VinylSkipDirection.Previous)
                            }
                            else -> {
                                if (mode == VinylSkipDirection.Previous) {
                                    stickyTopX = coverX
                                } else if (mode == VinylSkipDirection.Next) {
                                    stickyTopX = x
                                }
                                dragging = false
                                scope.launch {
                                    try {
                                        when (mode) {
                                            VinylSkipDirection.Previous -> {
                                                // 未达阈值：新胶缩回左侧（完全出裁剪区）
                                                topX.snapTo(coverX)
                                                stickyTopX = Float.NaN
                                                topX.animateTo(
                                                    -slidePx,
                                                    spring(
                                                        dampingRatio = 0.86f,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                    ),
                                                )
                                                topTrack = track
                                                topX.snapTo(0f)
                                                showBottom = false
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
                                            }
                                            null -> {
                                                topTrack = track
                                                stickyTopX = Float.NaN
                                                topX.snapTo(0f)
                                                showBottom = false
                                            }
                                        }
                                        followX = 0f
                                        prevRevealBase = 0f
                                        dragMode = null
                                    } finally {
                                        stickyTopX = Float.NaN
                                        setBusy(false)
                                    }
                                }
                            }
                        }
                    },
                ),
        ) {
            if (showBottom) {
                key(bottomTrack.id, "bottom") {
                    VinylDiscFace(
                        track = bottomTrack,
                        angle = angleOf(bottomTrack.id),
                        spinning = false,
                        fullCover = fullCover,
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
                    angle = angleOf(topTrack.id),
                    spinning = spinning &&
                        !showBottom &&
                        !dragging &&
                        abs(displayX) < 2f &&
                        abs(topScale.value - 1f) < 0.02f,
                    fullCover = fullCover,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .graphicsLayer {
                            translationX = displayX
                            scaleX = topScale.value
                            scaleY = topScale.value
                            alpha = 1f
                            transformOrigin = TransformOrigin.Center
                        },
                )
            }
        }
    }
}

@Composable
private fun VinylDiscFace(
    track: TrackRow,
    angle: Animatable<Float, AnimationVector1D>,
    spinning: Boolean,
    fullCover: Boolean,
    modifier: Modifier = Modifier,
) {
    val coverT by animateFloatAsState(
        targetValue = if (fullCover) 1f else 0f,
        animationSpec = tween(
            durationMillis = 520,
            easing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f),
        ),
        label = "vinylFullCover",
    )
    // 镂空半径：1→轴孔可见，0→封面铺满
    val coverHoleFrac = (CoverHoleFrac / CoverFrac) * (1f - coverT)
    val spindleFrac = SpindleHoleFrac * (1f - coverT)

    LaunchedEffect(spinning, track.id) {
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
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.40f),
                spotColor = Color.Black.copy(alpha = 0.28f),
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle.value },
            contentAlignment = Alignment.Center,
        ) {
            VinylDiscPlate(
                modifier = Modifier.fillMaxSize(),
                spindleHoleFrac = spindleFrac,
            )

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
private data class VinylAnnulusShape(
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

/** 黑胶盘面：黑色径向底 + 同心纹路；轴孔半径可动画收拢。 */
@Composable
private fun VinylDiscPlate(
    modifier: Modifier = Modifier,
    spindleHoleFrac: Float = SpindleHoleFrac,
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
                        0.0f to Color(0xFF101012),
                        0.18f to Color(0xFF161618),
                        0.55f to Color(0xFF121214),
                        1.0f to Color(0xFF080809),
                    ),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = r * 0.985f,
                center = c,
                style = Stroke(width = r * 0.018f),
            )
            val innerStart = if (holeR > 0.5f) (holeR / r) + 0.012f else 0.04f
            val ringCount = 22
            for (i in 0 until ringCount) {
                val t = i / (ringCount - 1).toFloat()
                val rr = r * (innerStart + t * (0.97f - innerStart))
                val alpha = when {
                    rr < r * CoverHoleFrac -> 0.028f + (i % 2) * 0.012f
                    else -> 0.035f + (i % 2) * 0.018f
                }
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = rr,
                    center = c,
                    style = Stroke(width = if (rr < r * CoverHoleFrac) 0.9f else 1.1f),
                )
            }
            if (holeR > 0.5f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f * (holeR / (r * SpindleHoleFrac)).coerceIn(0f, 1f)),
                    radius = holeR * 1.08f,
                    center = c,
                    style = Stroke(width = r * 0.006f),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.55f * (holeR / (r * SpindleHoleFrac)).coerceIn(0f, 1f)),
                    radius = holeR * 1.02f,
                    center = c,
                    style = Stroke(width = r * 0.004f),
                )
            }
        }
    }
}
