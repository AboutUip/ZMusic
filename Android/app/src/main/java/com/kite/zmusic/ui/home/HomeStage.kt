package com.kite.zmusic.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kite.zmusic.ui.player.GeminiOrbsBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 主页舞台拉开时，挡住底栏横向翻页。 */
internal class HomeStagePagerBlock {
    var blocked by mutableStateOf(false)
}

/** 主页景片的位移。迷你播放条和底栏读同一份，跟着退进舞台。 */
internal class HomeStagePose {
    var progress by mutableFloatStateOf(0f)
    var translationX by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
    var rotationY by mutableFloatStateOf(0f)
}

internal val LocalHomeStagePose = staticCompositionLocalOf<HomeStagePose?> { null }

internal fun Modifier.homeStageFollow(pose: HomeStagePose?, densityScale: Float): Modifier {
    if (pose == null || pose.progress <= 0.001f) return this
    val tx = pose.translationX
    val scale = pose.scale
    val rotation = pose.rotationY
    return this.graphicsLayer {
        cameraDistance = 22f * densityScale
        transformOrigin = TransformOrigin(0f, 0.5f)
        translationX = tx
        scaleX = scale
        scaleY = scale
        rotationY = rotation
        clip = false
    }
}

internal val LocalHomeStagePagerBlock = staticCompositionLocalOf<HomeStagePagerBlock?> { null }

/** 手指和主页景片同一段行程，侧栏大约占屏宽的这段比例。 */
internal const val HomeStageTravelFraction = 0.74f

private const val HomeStageOpenScale = 0.86f
private const val HomeStageOpenRotation = -18f
/** 拉开后两块面板之间的缝，占屏宽的比例。 */
internal const val HomeStageGapFraction = 0.12f
/** 左缘伸出屏幕，盖住背景平移留下的缝，屏幕上看不见这条边。 */
private val HomeStageSidebarBleed = 48.dp
private val HomeStageSidebarShape = RoundedCornerShape(
    topEnd = 36.dp,
    bottomEnd = 36.dp,
)
private const val HomeStageSettleThreshold = 0.38f
private const val HomeStageFling = 1200f
private val HomeStageEdge = 32.dp
private val HomeStageCorner = 28.dp
private val HomeStageShadow = 22.dp
private val HomeStageOrbDrift = 22.dp
private val HomeStageWingLag = 56.dp

private val HomeStageSpring = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = 420f,
)

internal data class HomeStageFrame(
    val pageTranslationX: Float,
    val pageScale: Float,
    val pageRotationY: Float,
    val cornerPx: Float,
    val shadowPx: Float,
    val cavityTranslationX: Float,
    val wingTranslationX: Float,
)

internal fun homeStageFrame(
    progress: Float,
    widthPx: Float,
    cornerPx: Float,
    shadowPx: Float,
    cavityDriftPx: Float,
    wingLagPx: Float,
): HomeStageFrame {
    val p = progress.coerceIn(0f, 1f)
    return HomeStageFrame(
        pageTranslationX = widthPx * HomeStageTravelFraction * p,
        pageScale = 1f - (1f - HomeStageOpenScale) * p,
        pageRotationY = HomeStageOpenRotation * p,
        cornerPx = cornerPx * p,
        shadowPx = shadowPx * p,
        cavityTranslationX = cavityDriftPx * p,
        wingTranslationX = -wingLagPx * (1f - p),
    )
}

/**
 * 侧栏右缘停在景片左边，上下贴齐屏幕。左缘按 [bleedPx] 伸出屏幕，不绕 Y 转，
 * 所以左缘和上下缘都不会在屏幕里收成斜边或竖边。
 */
internal fun homeStageSidebarRect(
    progress: Float,
    widthPx: Float,
    heightPx: Float,
    bleedPx: Float,
): Rect {
    val p = progress.coerceIn(0f, 1f)
    val pageLeft = widthPx * HomeStageTravelFraction * p
    val gap = widthPx * HomeStageGapFraction * p
    val right = (pageLeft - gap).coerceAtLeast(0f)
    return Rect(-bleedPx * p, 0f, right, heightPx)
}

/** 景片绕左缘中点缩放。矩形是未旋转的占位，用来把液态背景让开。 */
internal fun homeStageCardRect(frame: HomeStageFrame, widthPx: Float, heightPx: Float): Rect {
    val scale = frame.pageScale
    val top = heightPx * (1f - scale) / 2f
    val left = frame.pageTranslationX
    val width = widthPx * scale
    val height = heightPx * scale
    return Rect(left, top, left + width, top + height)
}

internal fun homeStageProgressForDelta(current: Float, deltaPx: Float, widthPx: Float): Float {
    val travel = widthPx * HomeStageTravelFraction
    if (travel <= 1f) return current.coerceIn(0f, 1f)
    return (current + deltaPx / travel).coerceIn(0f, 1f)
}

/** 还没过触摸阈值时返回 null。true 表示这是水平拖。 */
internal fun homeStageDragAxis(totalX: Float, totalY: Float, slopPx: Float): Boolean? {
    if (abs(totalX) < slopPx && abs(totalY) < slopPx) return null
    return abs(totalX) >= abs(totalY) * 1.2f
}

/**
 * 未决定时返回 null。关上时向右拖拉出侧栏，向左拖留给底栏翻页。
 * 拉开之后两个方向都跟主页走。
 * 水平滚动的子组件要先滚，见 [homeStageYieldsToHorizontalChild]。
 */
internal fun homeStageClaimsDrag(
    progress: Float,
    totalX: Float,
    totalY: Float,
    slopPx: Float,
): Boolean? {
    val horizontal = homeStageDragAxis(totalX, totalY, slopPx) ?: return null
    if (!horizontal) return false
    if (progress > 0.02f) return true
    return totalX > 0f
}

/** 侧栏还关着，且手指按在轮播或横滑列表上。拉开之后主页自己跟手。 */
internal fun homeStageYieldsToHorizontalChild(progress: Float, onHorizontalChild: Boolean): Boolean =
    progress <= 0.02f && onHorizontalChild

private class HomeStageHorizontalTouch {
    var holding: Boolean = false
}

private val LocalHomeStageDrawer = staticCompositionLocalOf { false }

private val LocalHomeStageHorizontalTouch = staticCompositionLocalOf<HomeStageHorizontalTouch?> { null }

/**
 * 标在本身能横向滚的组件上。按下时告诉侧栏：这次手势归这里。
 * 底栏翻页也会消费水平拖，不能靠「有没有被消费」来判断，否则侧栏永远打不开。
 */
internal fun Modifier.homeStageHorizontalContent(): Modifier = composed {
    val touch = LocalHomeStageHorizontalTouch.current ?: return@composed this
    pointerInput(touch) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            touch.holding = true
            try {
                val slop = viewConfiguration.touchSlop
                var totalX = 0f
                var totalY = 0f
                var horizontal: Boolean? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp()) break
                    val delta = change.positionChangeIgnoreConsumed()
                    totalX += delta.x
                    totalY += delta.y
                    if (horizontal == null) {
                        when (homeStageDragAxis(totalX, totalY, slop)) {
                            null -> continue
                            false -> break
                            true -> horizontal = true
                        }
                    }
                    change.consume()
                }
            } finally {
                touch.holding = false
            }
        }
    }
}

internal fun homeStageSettlesOpen(progress: Float, velocityX: Float): Boolean {
    if (velocityX > HomeStageFling) return true
    if (velocityX < -HomeStageFling) return false
    return progress >= HomeStageSettleThreshold
}

private class HomeStageCavityShape(
    private val card: Rect,
    private val cornerPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            if (card.width > 1f && card.height > 1f) {
                addRoundRect(
                    RoundRect(
                        card,
                        CornerRadius(cornerPx, cornerPx),
                    ),
                )
            }
        }
        return Outline.Generic(path)
    }
}

/**
 * 竖屏主页：手指向右拖，主页退成舞台景片，左侧空出液态光球。
 * 横屏不包这一层。
 */
@Composable
internal fun HomeStageDrawer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val densityScale = density.density
    val pagerBlock = LocalHomeStagePagerBlock.current
    val pose = LocalHomeStagePose.current
    val floorLocked = LocalMainFloorLocked.current
    val anim = remember { Animatable(0f) }
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val widthHolder = remember { floatArrayOf(0f) }
    val progress = dragProgress ?: anim.value
    val frame = with(density) {
        homeStageFrame(
            progress = progress,
            widthPx = widthPx,
            cornerPx = HomeStageCorner.toPx(),
            shadowPx = HomeStageShadow.toPx(),
            cavityDriftPx = HomeStageOrbDrift.toPx(),
            wingLagPx = HomeStageWingLag.toPx(),
        )
    }
    val card = homeStageCardRect(frame, widthPx, heightPx)
    val edgePx = with(density) { HomeStageEdge.toPx() }
    val horizontalTouch = remember { HomeStageHorizontalTouch() }
    var horizontalChildGesture by remember { mutableStateOf(false) }
    SideEffect {
        pagerBlock?.blocked = progress > 0.001f || horizontalChildGesture
        pose?.progress = progress
        pose?.translationX = frame.pageTranslationX
        pose?.scale = frame.pageScale
        pose?.rotationY = frame.pageRotationY
    }
    DisposableEffect(pagerBlock, pose) {
        onDispose {
            pagerBlock?.blocked = false
            pose?.progress = 0f
            pose?.translationX = 0f
            pose?.scale = 1f
            pose?.rotationY = 0f
        }
    }
    BackHandler(enabled = progress > 0.02f && !floorLocked) {
        dragProgress = null
        scope.launch { anim.animateTo(0f, HomeStageSpring) }
    }
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                widthPx = it.width.toFloat()
                heightPx = it.height.toFloat()
                widthHolder[0] = widthPx
            }
            .systemGestureExclusion { coords ->
                if (progress > 0.02f) {
                    Rect.Zero
                } else {
                    Rect(0f, 0f, edgePx, coords.size.height.toFloat())
                }
            }
            .pointerInput(floorLocked, horizontalTouch) {
                if (floorLocked) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val origin = dragProgress ?: anim.value
                    val downFinal = awaitPointerEvent(PointerEventPass.Final)
                    val downChange = downFinal.changes.firstOrNull { it.id == down.id }
                    if (downChange == null || downChange.changedToUp()) return@awaitEachGesture
                    if (homeStageYieldsToHorizontalChild(origin, horizontalTouch.holding)) {
                        horizontalChildGesture = true
                        try {
                            while (true) {
                                val rest = awaitPointerEvent(PointerEventPass.Final)
                                val resting = rest.changes.firstOrNull { it.id == down.id } ?: break
                                if (resting.changedToUp()) break
                            }
                        } finally {
                            horizontalChildGesture = false
                        }
                        return@awaitEachGesture
                    }
                    var totalX = 0f
                    var totalY = 0f
                    var claimed: Boolean? = null
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) {
                            if (claimed == true) {
                                val velocityX = tracker.calculateVelocity().x
                                val settled = dragProgress ?: anim.value
                                val open = homeStageSettlesOpen(settled, velocityX)
                                scope.launch {
                                    anim.snapTo(settled)
                                    dragProgress = null
                                    anim.animateTo(if (open) 1f else 0f, HomeStageSpring)
                                }
                                change.consume()
                            }
                            break
                        }
                        if (change.isConsumed && claimed != true) break
                        val delta = change.positionChangeIgnoreConsumed()
                        totalX += delta.x
                        totalY += delta.y
                        if (claimed == null) {
                            claimed = homeStageClaimsDrag(
                                progress = origin,
                                totalX = totalX,
                                totalY = totalY,
                                slopPx = slop,
                            )
                            if (claimed == false) break
                            if (claimed == null) continue
                            pagerBlock?.blocked = true
                        }
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        dragProgress = homeStageProgressForDelta(origin, totalX, widthHolder[0])
                    }
                }
            },
    ) {
        if (progress > 0.001f && widthPx > 1f) {
            val sideBleedPx = with(density) { HomeStageSidebarBleed.toPx() }
            val side = homeStageSidebarRect(progress, widthPx, heightPx, sideBleedPx)
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(HomeStageCavityShape(card, frame.cornerPx))
                    .graphicsLayer { translationX = frame.cavityTranslationX },
            ) {
                GeminiOrbsBackdrop(
                    modifier = Modifier.fillMaxSize(),
                    motionEnabled = true,
                    idleGlow = 0.28f,
                )
            }
            if (side.width > 2f) {
                Box(
                    Modifier
                        .offset { IntOffset(side.left.roundToInt(), side.top.roundToInt()) }
                        .size(
                            width = with(density) { side.width.toDp() },
                            height = with(density) { side.height.toDp() },
                        )
                        .graphicsLayer {
                            translationX = frame.wingTranslationX
                        }
                        .clip(HomeStageSidebarShape),
                ) {
                    GeminiOrbsBackdrop(
                        modifier = Modifier.fillMaxSize(),
                        motionEnabled = true,
                        idleGlow = 0.92f,
                    )
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(28.dp)
                            .align(Alignment.CenterEnd)
                            .background(
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.White.copy(alpha = 0.16f),
                                ),
                            ),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    cameraDistance = 22f * densityScale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = frame.pageTranslationX
                    scaleX = frame.pageScale
                    scaleY = frame.pageScale
                    rotationY = frame.pageRotationY
                    shape = RoundedCornerShape(frame.cornerPx.toDp())
                    clip = progress > 0.02f
                    shadowElevation = frame.shadowPx
                },
        ) {
            CompositionLocalProvider(
                LocalHomeStageDrawer provides true,
                LocalHomeStageHorizontalTouch provides horizontalTouch,
            ) {
                content()
            }
            if (progress > 0.04f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(18.dp)
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.White.copy(alpha = 0.14f * progress),
                                1f to Color.Transparent,
                            ),
                        ),
                )
            }
        }
        if (progress > 0.92f && card.width > 1f) {
            Box(
                Modifier
                    .offset {
                        IntOffset(card.left.roundToInt(), card.top.roundToInt())
                    }
                    .size(
                        width = with(density) { card.width.toDp() },
                        height = with(density) { card.height.toDp() },
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointer = down.id
                            var moved = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointer } ?: break
                                moved += abs(change.positionChange().x) + abs(change.positionChange().y)
                                if (change.changedToUp()) {
                                    if (moved < viewConfiguration.touchSlop) {
                                        dragProgress = null
                                        scope.launch { anim.animateTo(0f, HomeStageSpring) }
                                    }
                                    break
                                }
                            }
                        }
                    },
            )
        }
    }
}
