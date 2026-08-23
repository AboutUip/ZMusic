package com.kite.zmusic.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 两页横向跟手切换（0=左，1=右），嵌在纵向滚动里时把多余的横滑交给父级。
 */
@Stable
class TwoPanePagerState(
    private val scope: CoroutineScope,
    initial: Float = 0f,
) {
    var offset by mutableFloatStateOf(initial)
        private set

    private val anim = Animatable(initial)
    private var job: Job? = null
    private var gen: Int = 0
    private var dragging: Boolean = false
    private var target: Float = initial

    fun dragDelta(deltaPx: Float, widthPx: Float): Float {
        if (!dragging) {
            cancelAnim()
            dragging = true
        }
        val w = widthPx.coerceAtLeast(1f)
        val old = offset
        offset = (old - deltaPx / w).coerceIn(0f, 1f)
        return (old - offset) * w
    }

    fun settle(velocityPx: Float): Float {
        dragging = false
        val dest = when {
            velocityPx < -680f -> 1f
            velocityPx > 680f -> 0f
            else -> if (offset >= 0.5f) 1f else 0f
        }
        animateTo(dest)
        return dest
    }

    fun goTo(dest: Float) {
        dragging = false
        val d = dest.coerceIn(0f, 1f)
        if (abs(offset - d) < 0.002f && job?.isActive != true) {
            offset = d
            target = d
            return
        }
        if (abs(target - d) < 0.002f && job?.isActive == true) return
        animateTo(d)
    }

    private fun animateTo(dest: Float) {
        val my = ++gen
        dragging = false
        target = dest
        job?.cancel()
        job = scope.launch {
            anim.snapTo(offset)
            anim.animateTo(
                dest,
                spring(dampingRatio = 0.82f, stiffness = 420f),
            ) {
                if (my == gen) offset = value
            }
            if (my != gen) return@launch
            offset = dest
            anim.snapTo(dest)
        }
    }

    private fun cancelAnim() {
        gen += 1
        job?.cancel()
        job = null
    }
}

@Composable
fun TwoPaneSwipe(
    progress: Float,
    pager: TwoPanePagerState,
    onSettled: (page: Int) -> Unit,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragFrom by remember { mutableIntStateOf(0) }
    val nestedDispatcher = remember { NestedScrollDispatcher() }
    val nestedConnection = remember { object : NestedScrollConnection {} }
    val drag = rememberDraggableState { delta ->
        val parentPre = nestedDispatcher.dispatchPreScroll(
            Offset(delta, 0f),
            NestedScrollSource.UserInput,
        )
        val remaining = delta - parentPre.x
        val self = pager.dragDelta(remaining, widthPx)
        nestedDispatcher.dispatchPostScroll(
            consumed = Offset(self, 0f),
            available = Offset(remaining - self, 0f),
            source = NestedScrollSource.UserInput,
        )
    }
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .nestedScroll(nestedConnection, nestedDispatcher)
            .draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    dragFrom = if (pager.offset >= 0.5f) 1 else 0
                },
                onDragStopped = { velocity ->
                    val atStart = pager.offset <= 0.001f
                    val atEnd = pager.offset >= 0.999f
                    val passParent = (atStart && velocity > 0f) || (atEnd && velocity < 0f)
                    if (passParent) {
                        val available = Velocity(velocity, 0f)
                        val consumed = nestedDispatcher.dispatchPreFling(available)
                        nestedDispatcher.dispatchPostFling(consumed, available - consumed)
                        pager.settle(0f)
                    } else {
                        val dest = pager.settle(velocity)
                        val destPage = if (dest >= 0.5f) 1 else 0
                        if (destPage != dragFrom) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSettled(destPage)
                    }
                },
            ),
        content = {
            Box(Modifier.fillMaxWidth()) { left() }
            Box(Modifier.fillMaxWidth()) { right() }
        },
    ) { measurables, constraints ->
        val pageW = constraints.maxWidth.coerceAtLeast(0)
        val pageConstraints = constraints.copy(
            minWidth = pageW,
            maxWidth = pageW,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        )
        val leftPlaceable = measurables[0].measure(pageConstraints)
        val rightPlaceable = measurables[1].measure(pageConstraints)
        val t = progress.coerceIn(0f, 1f)
        val height = (
            leftPlaceable.height +
                (rightPlaceable.height - leftPlaceable.height) * t
            ).roundToInt().coerceAtLeast(0)
        val x = (-t * pageW).roundToInt()
        layout(pageW, height) {
            leftPlaceable.placeRelative(x, 0)
            rightPlaceable.placeRelative(x + pageW, 0)
        }
    }
}
