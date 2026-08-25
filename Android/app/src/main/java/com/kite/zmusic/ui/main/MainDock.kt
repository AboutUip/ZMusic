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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.plugin.PluginDebugProbe
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.theme.TextTheme
import com.kyant.backdrop.Backdrop
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Apple 式悬浮 Dock。
 * 按住横滑 1:1 跟手切页；滑过约四分之一格即切换。调试开启时可滑到「调优」。
 */
@Composable
fun FloatingTabDock(
    pagerState: PagerState,
    onDestination: (MainDestination) -> Unit,
    onDragByTabs: (Float) -> Unit,
    onDragSettled: (velocityTabsPerSec: Float, startPage: Int) -> Unit,
    compactProgress: () -> Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
    showProbeTab: Boolean = false,
    onOpenProbe: () -> Unit = {},
) {
    val density = LocalDensity.current
    val expandedHpx = with(density) { FloatingDockHeight.roundToPx() }
    val compactHpx = with(density) { FloatingDockCompactHeight.roundToPx() }
    val shape = RoundedCornerShape(percent = 50)
    val wellShape = RoundedCornerShape(percent = 50)
    val destCount = MainDestination.entries.size
    val tabCount = destCount + if (showProbeTab) 1 else 0
    val lastIndex = tabCount - 1

    Row(
        modifier
            .widthIn(
                min = if (landscape) 280.dp else 248.dp,
                max = when {
                    landscape && showProbeTab -> 400.dp
                    landscape -> 360.dp
                    showProbeTab -> 360.dp
                    else -> 320.dp
                },
            )
            .mainLiquidGlass(backdrop, shape)
            .layout { measurable, constraints ->
                val p = compactProgress().coerceIn(0f, 1f)
                val h = (expandedHpx + (compactHpx - expandedHpx) * p).roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(
                    constraints.copy(minHeight = h, maxHeight = h),
                )
                layout(placeable.width, h) {
                    placeable.placeRelative(0, 0)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemW = maxWidth / tabCount
            val itemWpx = with(LocalDensity.current) { itemW.toPx() }
            DockSelectionLayer(
                pagerState = pagerState,
                tabCount = tabCount,
                lastIndex = lastIndex,
                destCount = destCount,
                showProbeTab = showProbeTab,
                itemW = itemW,
                itemWpx = itemWpx,
                compactProgress = compactProgress,
                backdrop = backdrop,
                wellShape = wellShape,
                onDestination = onDestination,
                onOpenProbe = onOpenProbe,
                onDragByTabs = onDragByTabs,
                onDragSettled = onDragSettled,
            )
        }
    }
}

@Composable
private fun DockSelectionLayer(
    pagerState: PagerState,
    tabCount: Int,
    lastIndex: Int,
    destCount: Int,
    showProbeTab: Boolean,
    itemW: Dp,
    itemWpx: Float,
    compactProgress: () -> Float,
    backdrop: Backdrop,
    wellShape: RoundedCornerShape,
    onDestination: (MainDestination) -> Unit,
    onOpenProbe: () -> Unit,
    onDragByTabs: (Float) -> Unit,
    onDragSettled: (Float, Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val onOpenProbeState = rememberUpdatedState(onOpenProbe)
    val onDestinationState = rememberUpdatedState(onDestination)
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
                label = dest.titleZh,
                icon = ZIcons.dock(dest),
                selection = selection,
                index = index,
                compactProgress = compactProgress,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        if (showProbeTab) {
            DockItem(
                label = PluginDebugProbe.DOCK_LABEL,
                icon = ZIcons.BugReport,
                selection = selection,
                index = destCount,
                compactProgress = compactProgress,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(tabCount, showProbeTab) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPage = pagerState.currentPage
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                    }
                    val tabW = size.width.toFloat() / tabCount
                    if (tabW <= 0f) return@awaitEachGesture
                    if (slop == null) {
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up != null && up.changedToUpIgnoreConsumed()) {
                            val travel = (up.position - down.position).getDistance()
                            if (travel < viewConfiguration.touchSlop * 2) {
                                val index = (down.position.x / tabW)
                                    .toInt()
                                    .coerceIn(0, lastIndex)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (index >= destCount) {
                                    onOpenProbeState.value()
                                } else {
                                    onDestinationState.value(MainDestination.entries[index])
                                }
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
                    onDragSettled(tracker.calculateVelocity().x / tabW, startPage)
                }
            },
    )
}

@Composable
private fun DockItem(
    label: String,
    icon: ImageVector,
    selection: Float,
    index: Int,
    compactProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val progress = compactProgress().coerceIn(0f, 1f)
    val proximity = (1f - abs(selection - index)).coerceIn(0f, 1f)
    val selected = proximity > 0.5f
    val tint = lerp(TextTheme.DockInactive, TextTheme.DockActive, proximity)
    val labelH = lerp(14.dp, 0.dp, progress)
    Column(
        modifier.semantics {
            role = Role.Tab
            contentDescription = label
            this.selected = selected
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        if (progress < 0.92f) {
            Text(
                text = label,
                style = TextStyle(
                    color = tint,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.2.sp,
                ),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .height(labelH)
                    .alpha((1f - progress).coerceIn(0f, 1f)),
            )
        }
    }
}
