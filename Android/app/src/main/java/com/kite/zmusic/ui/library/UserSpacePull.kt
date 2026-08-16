package com.kite.zmusic.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val SpaceMorphEasing = CubicBezierEasing(0.45f, 0.02f, 0.15f, 1f)

/** 飞入头像与源头像交接：叠加层出现的同时藏起源头像，避免红圈晚一拍再跟上。 */
internal const val SpaceAvatarHandoffProgress = 0.001f

/** 系统栏改浅色图标：等照片已经铺开、幕布开始压暗，避免中段闪一下。 */
internal const val SpaceDarkBarsProgress = 0.24f

/**
 * 歌单层淡出。原先 0.28 处 alpha 归零，进场像列表被擦掉、出场像灰底突然砸回来。
 * 跟照片增高同步下滑，大部分行程里列表仍是实底，只在后段收干净。
 */
internal fun spaceSheetAlpha(t: Float): Float {
    val u = (t.coerceIn(0f, 1f) / 0.72f).coerceIn(0f, 1f)
    return 1f - FastOutSlowInEasing.transform(u)
}

/**
 * 星空 / 星座 / 压暗幕布。前段只做照片展开 + 列表退下，后段再从同一张图里长出星座。
 */
internal fun spaceSceneProgress(t: Float): Float {
    return ((t - 0.18f) / 0.58f).coerceIn(0f, 1f)
}

/**
 * 昵称 / 签名 / 听歌数据分槽离场，回退原路入场。
 * [slot] 0 昵称+等级，1 签名，2 听歌进度。
 */
internal fun spaceIdentitySlot(t: Float, slot: Int): Float {
    val start = 0.03f + slot * 0.08f
    val span = 0.26f
    return FastOutSlowInEasing.transform(
        ((t.coerceIn(0f, 1f) - start) / span).coerceIn(0f, 1f),
    )
}

internal fun Modifier.spaceIdentityLeave(progress: Float, slot: Int): Modifier {
    return graphicsLayer {
        val u = spaceIdentitySlot(progress, slot)
        alpha = 1f - u
        translationY = u * (14f + slot * 12f)
        val s = 1f - u * 0.05f
        scaleX = s
        scaleY = s
    }
}

/** Dock / 横屏栏：跟列表后段一起收，避免一拉就先掉一层壳。 */
internal fun spaceChromeLeave(t: Float): Float {
    return FastOutSlowInEasing.transform(
        (t.coerceIn(0f, 1f) / 0.78f).coerceIn(0f, 1f),
    )
}

private val SpaceEnterSpring = spring<Float>(
    dampingRatio = 0.9f,
    stiffness = 780f,
)
private val SpacePageSpring = spring<Float>(
    dampingRatio = 0.88f,
    stiffness = 680f,
)

/** 顶栏 / 箭头相对空间进度延后出现，进度回退时原路收回。 */
internal fun spaceChromeProgress(t: Float): Float {
    return ((t - 0.22f) / 0.78f).coerceIn(0f, 1f)
}

/**
 * 个人页 ↔ 用户空间：只认手指相对按下点的绝对位移，不累加 nested-scroll leftover。
 *
 * [activationPx] 是死区，这段位移进度保持 0。[rangePx] 是死区之后拉满进度需要的距离。
 */
@Stable
internal class UserSpaceRevealState(
    private val scope: CoroutineScope,
) {
    var progress by mutableFloatStateOf(0f)
        private set
    var dragging by mutableStateOf(false)
        private set
    var isOpen by mutableStateOf(false)
        private set
    var atTop: Boolean = true
    var enabled: Boolean = true
    var rangePx: Float = 1600f
    var activationPx: Float = 48f

    private val anim = Animatable(0f)
    private var job: Job? = null
    private var gen: Int = 0

    fun dragToPullPx(pullPx: Float) {
        if (!enabled) return
        cancelAnim()
        dragging = true
        progress = pullPxToProgress(pullPx)
    }

    fun dragToProgress(value: Float) {
        if (!enabled) return
        cancelAnim()
        dragging = true
        progress = value.coerceIn(0f, 1f)
    }

    fun pullPxFromProgress(): Float {
        return activationPx + progress.coerceIn(0f, 1f) * rangePx.coerceAtLeast(1f)
    }

    fun settle(velocityY: Float) {
        dragging = false
        val dest = if (isOpen || progress >= 0.999f) {
            when {
                velocityY < -850f -> 0f
                velocityY > 850f -> 1f
                progress < 0.72f -> 0f
                else -> 1f
            }
        } else {
            when {
                velocityY > 1100f && progress > 0.08f -> 1f
                velocityY < -900f -> 0f
                progress >= 0.42f -> 1f
                else -> 0f
            }
        }
        animateTo(dest)
    }

    fun open() = animateTo(1f)

    fun close() = animateTo(0f)

    private fun pullPxToProgress(pullPx: Float): Float {
        val extra = (pullPx - activationPx).coerceAtLeast(0f)
        return (extra / rangePx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    private fun cancelAnim() {
        gen += 1
        job?.cancel()
        job = null
    }

    private fun animateTo(target: Float) {
        val dest = target.coerceIn(0f, 1f)
        val my = ++gen
        dragging = false
        job?.cancel()
        job = scope.launch {
            anim.snapTo(progress.coerceIn(0f, 1f))
            anim.animateTo(dest, SpaceEnterSpring) {
                if (my == gen) progress = value
            }
            if (my != gen) return@launch
            progress = dest
            isOpen = dest >= 0.999f
        }
    }
}

/**
 * 星座分页：offset 可越出 [0, last] 以便环绕（-1 ↔ last，last+1 ↔ 0）。
 * 静止时落在整数页，from==to 且 morphT=1。
 */
@Stable
internal class ConstellationPagerState(
    private val scope: CoroutineScope,
    val pageCount: Int,
) {
    var page by mutableIntStateOf(0)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    var dragging by mutableStateOf(false)
        private set

    private val anim = Animatable(0f)
    private var job: Job? = null
    private var gen: Int = 0

    val lastPage: Int get() = (pageCount - 1).coerceAtLeast(0)

    val displayPage: Int get() = wrapPage(nearestPage(offset))

    val fromIndex: Int get() = wrapPage(floor(offset).toInt())

    val toIndex: Int get() = wrapPage(ceil(offset).toInt())

    val morphT: Float
        get() {
            val lo = floor(offset)
            val hi = ceil(offset)
            if (abs(hi - lo) < 0.001f) return 1f
            return (offset - lo).coerceIn(0f, 1f)
        }

    fun wrapPage(index: Int): Int {
        if (pageCount <= 0) return 0
        return ((index % pageCount) + pageCount) % pageCount
    }

    fun dragTo(pointerX: Float, startX: Float, startOffset: Float, width: Float) {
        val span = (width * 0.62f).coerceAtLeast(1f)
        val pages = -(pointerX - startX) / span
        cancelAnim()
        dragging = true
        offset = (startOffset + pages).coerceIn(startOffset - 1.2f, startOffset + 1.2f)
    }

    fun settle(velocityX: Float) {
        dragging = false
        val dest = when {
            velocityX < -700f -> ceil(offset - 0.02f).toInt()
            velocityX > 700f -> floor(offset + 0.02f).toInt()
            else -> nearestPage(offset)
        }
        animateToOffset(dest)
    }

    fun next() = animateToOffset(nearestPage(offset) + 1)

    fun prev() = animateToOffset(nearestPage(offset) - 1)

    fun goTo(target: Int) {
        val destPage = wrapPage(target)
        val current = wrapPage(nearestPage(offset))
        if (destPage == current && abs(offset - current) < 0.002f) {
            page = destPage
            offset = destPage.toFloat()
            return
        }
        val forward = (destPage - current + pageCount) % pageCount
        val backward = (current - destPage + pageCount) % pageCount
        val dest = if (forward <= backward) current + forward else current - backward
        animateToOffset(dest)
    }

    private fun animateToOffset(destUnwrapped: Int) {
        val my = ++gen
        dragging = false
        job?.cancel()
        job = scope.launch {
            anim.snapTo(offset)
            anim.animateTo(destUnwrapped.toFloat(), SpacePageSpring) {
                if (my == gen) offset = value
            }
            if (my != gen) return@launch
            val wrapped = wrapPage(destUnwrapped)
            offset = wrapped.toFloat()
            page = wrapped
            anim.snapTo(offset)
        }
    }

    private fun cancelAnim() {
        gen += 1
        job?.cancel()
        job = null
    }

    private fun nearestPage(value: Float): Int = floor(value + 0.5f).toInt()
}

/**
 * 列表 / 头图：按下后用当前 Y − 起始 Y 当拉力。
 *
 * 打开后必须结束 pointerInput，不能在 [awaitEachGesture] 里空 return：
 * 没有指针时会立刻再进下一轮，主线程空转直到卡死。
 */
internal fun Modifier.userSpaceRevealGesture(
    state: UserSpaceRevealState,
    requireAtTop: Boolean = true,
): Modifier {
    return pointerInput(state, requireAtTop, state.isOpen) {
        if (state.isOpen) return@pointerInput
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!state.enabled || state.isOpen) return@awaitEachGesture
            if (requireAtTop && !state.atTop && state.progress <= 0.001f) {
                return@awaitEachGesture
            }
            var tracking = state.progress > 0.01f
            val startY = down.position.y
            val startPull = if (tracking) state.pullPxFromProgress() else 0f
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp() || !change.pressed) {
                    if (tracking) state.settle(tracker.calculateVelocity().y)
                    break
                }
                tracker.addPosition(change.uptimeMillis, change.position)
                val totalDy = change.position.y - startY
                val ax = abs(change.position.x - down.position.x)
                val ay = abs(change.position.y - down.position.y)
                if (!tracking) {
                    if (ax > slop || ay > slop) {
                        val pullDown = totalDy > 0f && ay >= ax &&
                            (!requireAtTop || state.atTop || state.progress > 0.01f)
                        if (pullDown) {
                            tracking = true
                        } else {
                            break
                        }
                    }
                }
                if (tracking) {
                    change.consume()
                    state.dragToPullPx((startPull + totalDy).coerceAtLeast(0f))
                }
            }
        }
    }
}

/**
 * 仅在空间已打开时使用。放在按钮/顶栏后面的垫层上：
 * 未消费的按下才接手，所以箭头和顶栏点击不会进这里。
 * 横滑翻页、上滑退出，位移都相对按下点。
 */
internal fun Modifier.userSpaceOpenPad(
    reveal: UserSpaceRevealState,
    pager: ConstellationPagerState,
): Modifier {
    return pointerInput(reveal.isOpen, reveal, pager) {
        if (!reveal.isOpen) return@pointerInput
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var lockedH = false
            var lockedV = false
            val start = down.position
            val startProgress = reveal.progress
            val startOffset = pager.offset
            val range = reveal.rangePx.coerceAtLeast(1f)
            var finished = false
            fun finish() {
                if (finished) return
                finished = true
                val v = tracker.calculateVelocity()
                when {
                    lockedV -> reveal.settle(v.y)
                    lockedH -> pager.settle(v.x)
                }
            }
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp() || !change.pressed) {
                        finish()
                        break
                    }
                    val pos = change.position
                    tracker.addPosition(change.uptimeMillis, pos)
                    if (!lockedH && !lockedV) {
                        val ax = abs(pos.x - start.x)
                        val ay = abs(pos.y - start.y)
                        if (ax > slop || ay > slop) {
                            if (ay >= ax * 0.82f) lockedV = true else lockedH = true
                        }
                    }
                    if (lockedV) {
                        val p = (startProgress + (pos.y - start.y) / range).coerceIn(0f, 1f)
                        reveal.dragToProgress(p)
                        change.consume()
                    } else if (lockedH) {
                        pager.dragTo(pos.x, start.x, startOffset, size.width.toFloat())
                        change.consume()
                    }
                }
            } finally {
                finish()
            }
        }
    }
}

@Composable
internal fun rememberUserSpaceRevealState(
    scope: CoroutineScope,
): UserSpaceRevealState {
    return remember(scope) { UserSpaceRevealState(scope) }
}

@Composable
internal fun rememberUserSpacePullState(
    scope: CoroutineScope,
): UserSpaceRevealState = rememberUserSpaceRevealState(scope)

@Composable
internal fun rememberConstellationPagerState(
    scope: CoroutineScope,
    pageCount: Int,
): ConstellationPagerState {
    return remember(scope, pageCount) {
        ConstellationPagerState(scope, pageCount)
    }
}
