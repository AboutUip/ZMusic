package com.kite.zmusic.ui.main

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.icons.ZIcons
import com.kyant.backdrop.Backdrop
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Apple 式悬浮 Dock。
 * 按住横滑用 [PagerState.dispatchRawDelta] 跟手切页；当前项为下沉玻璃而非红色胶囊。
 */
@Composable
fun FloatingTabDock(
    pagerState: PagerState,
    onDestination: (MainDestination) -> Unit,
    onDragByTabs: (Float) -> Unit,
    onDragSettled: (velocityTabsPerSec: Float) -> Unit,
    compactProgress: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val progress = compactProgress.coerceIn(0f, 1f)
    val height = lerp(FloatingDockHeight, FloatingDockCompactHeight, progress)
    val shape = RoundedCornerShape(percent = 50)
    val wellShape = RoundedCornerShape(percent = 50)
    val haptic = LocalHapticFeedback.current
    val tabCount = MainDestination.entries.size
    val lastIndex = tabCount - 1
    val selection = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, lastIndex.toFloat())

    var hapticTick by remember { mutableIntStateOf(selection.roundToInt()) }
    LaunchedEffect(selection.roundToInt()) {
        val tick = selection.roundToInt().coerceIn(0, lastIndex)
        if (tick != hapticTick) {
            hapticTick = tick
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Row(
        modifier
            .widthIn(min = if (landscape) 280.dp else 248.dp, max = if (landscape) 360.dp else 320.dp)
            .mainLiquidGlass(backdrop, shape)
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemW = maxWidth / tabCount
            val itemWpx = with(LocalDensity.current) { itemW.toPx() }
            Box(
                Modifier
                    .graphicsLayer { translationX = itemWpx * selection }
                    .width(itemW)
                    .fillMaxHeight()
                    .padding(6.dp)
                    .dockSunkenGlass(backdrop, wellShape),
            )
            Row(Modifier.fillMaxSize()) {
                MainDestination.entries.forEachIndexed { index, dest ->
                    DockItem(
                        destination = dest,
                        selection = selection,
                        index = index,
                        compactProgress = progress,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(tabCount) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                            }
                            val tabW = size.width.toFloat() / tabCount
                            if (tabW <= 0f) return@awaitEachGesture
                            if (slop == null) {
                                // 横滑未成立时抬手已经发生，不能再 waitForUp，否则第一次点击被吞掉。
                                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                if (up != null && up.changedToUpIgnoreConsumed()) {
                                    val travel = (up.position - down.position).getDistance()
                                    if (travel < viewConfiguration.touchSlop * 2) {
                                        val index = (down.position.x / tabW)
                                            .toInt()
                                            .coerceIn(0, lastIndex)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDestination(MainDestination.entries[index])
                                    }
                                }
                                return@awaitEachGesture
                            }
                            val slopDx = slop.positionChange().x
                            if (slopDx != 0f) onDragByTabs(slopDx / tabW)
                            horizontalDrag(slop.id) { change ->
                                val dx = change.positionChange().x
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (dx != 0f) onDragByTabs(dx / tabW)
                            }
                            onDragSettled(tracker.calculateVelocity().x / tabW)
                        }
                    },
            )
        }
    }
}

@Composable
private fun DockItem(
    destination: MainDestination,
    selection: Float,
    index: Int,
    compactProgress: Float,
    modifier: Modifier = Modifier,
) {
    val proximity = (1f - abs(selection - index)).coerceIn(0f, 1f)
    val selected = proximity > 0.5f
    val tint = lerp(MainPalette.Secondary, MainPalette.Ink, proximity)
    val labelH = lerp(14.dp, 0.dp, compactProgress)
    Column(
        modifier.semantics {
            role = Role.Tab
            contentDescription = destination.titleZh
            this.selected = selected
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = ZIcons.dock(destination),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        if (compactProgress < 0.92f) {
            Text(
                text = destination.titleZh,
                style = TextStyle(
                    color = tint,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.2.sp,
                ),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .height(labelH)
                    .alpha((1f - compactProgress).coerceIn(0f, 1f)),
            )
        }
    }
}
