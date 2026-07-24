package com.kite.zmusic.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs

/** 跟滚 / 回播放行 */
internal val LyricScrollFollowSpec =
    tween<Float>(durationMillis = 420, easing = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1f))

/** 松手轻对齐：短促、低存在感 */
internal val LyricScrollSnapSpec =
    tween<Float>(durationMillis = 220, easing = CubicBezierEasing(0.22f, 0.1f, 0.18f, 1f))

/** 浏览态：视口垂直中心最近的一行（滚动预览目标） */
internal fun LazyListState.browseCenterLyricIndex(fallback: Int): Int {
    val info = layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return fallback
    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { item ->
        abs((item.offset + item.size / 2) - mid)
    }?.index ?: fallback
}

/**
 * 将 [index] 滚到视口绝对垂直中心（与横屏跟滚同一套几何）。
 * [followGen] / [currentFollowGen] 用于手势打断进行中的跟滚。
 */
internal suspend fun LazyListState.scrollLyricToCenteredIndex(
    index: Int,
    lastIndex: Int,
    slotHeightPx: Int,
    animated: Boolean,
    softSnap: Boolean = false,
    followGen: Int,
    currentFollowGen: () -> Int,
    setSuppressBrowseDetect: (Boolean) -> Unit,
) {
    if (lastIndex < 0) return
    setSuppressBrowseDetect(true)
    try {
        val target = index.coerceIn(0, lastIndex)

        fun viewportSize(): Int {
            val info = layoutInfo
            return (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
        }

        suspend fun jumpToCentered(itemSizeHint: Int = slotHeightPx) {
            val vs = viewportSize()
            val visibleSize = layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == target }
                ?.size
            val size = (visibleSize ?: itemSizeHint).coerceIn(1, vs)
            val offset = ((vs - size) / 2).coerceAtLeast(0)
            scrollToItem(target, scrollOffset = offset)
        }

        fun deltaToTarget(): Float? {
            val info = layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return null
            val viewportCenter =
                (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val item = visible.firstOrNull { it.index == target }
            return if (item != null) {
                (item.offset + item.size / 2f) - viewportCenter
            } else {
                val step = slotHeightPx.toFloat().coerceAtLeast(1f)
                val anchor = visible.minByOrNull { abs(it.index - target) } ?: visible.first()
                val approxCenter =
                    anchor.offset + anchor.size / 2f + (target - anchor.index) * step
                approxCenter - viewportCenter
            }
        }

        val scrollSpec = if (softSnap) LyricScrollSnapSpec else LyricScrollFollowSpec

        if (!animated) {
            jumpToCentered()
            val fine = deltaToTarget()
            if (fine != null && abs(fine) > 1f) {
                scrollBy(fine)
            }
            return
        }

        if (layoutInfo.visibleItemsInfo.none { it.index == target }) {
            val approxDelta = deltaToTarget()
            if (approxDelta != null && abs(approxDelta) > 1f) {
                animateScrollBy(approxDelta, animationSpec = scrollSpec)
            }
            if (currentFollowGen() != followGen) return
            if (layoutInfo.visibleItemsInfo.none { it.index == target }) {
                jumpToCentered()
            }
        }
        if (currentFollowGen() != followGen) return
        val delta = deltaToTarget() ?: return
        if (abs(delta) > 1f) {
            animateScrollBy(delta, animationSpec = scrollSpec)
        }
        if (currentFollowGen() != followGen) return
        // 真实 item 高度可能与 slot 提示不一致，补一次精修
        val fine = deltaToTarget()
        if (fine != null && abs(fine) > 1.5f) {
            scrollBy(fine)
        }
    } finally {
        setSuppressBrowseDetect(false)
    }
}

/** 松手后：仅当中心偏离明显时轻对齐到最近整行 */
internal suspend fun LazyListState.snapLyricToFullLines(
    slotHeightPx: Int,
    lastIndex: Int,
    followGen: Int,
    currentFollowGen: () -> Int,
    setSuppressBrowseDetect: (Boolean) -> Unit,
) {
    val info = layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return
    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val closest = info.visibleItemsInfo.minByOrNull { item ->
        abs((item.offset + item.size / 2) - mid)
    } ?: return
    val itemMid = closest.offset + closest.size / 2
    val threshold = (slotHeightPx * 0.42f).coerceAtLeast(18f)
    if (abs(itemMid - mid) <= threshold) return
    scrollLyricToCenteredIndex(
        index = closest.index,
        lastIndex = lastIndex,
        slotHeightPx = slotHeightPx,
        animated = true,
        softSnap = true,
        followGen = followGen,
        currentFollowGen = currentFollowGen,
        setSuppressBrowseDetect = setSuppressBrowseDetect,
    )
}
