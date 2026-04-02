package com.kite.zmusic.ui.main

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlinx.coroutines.withTimeoutOrNull

private val Entries = MainDestination.entries

private enum class PreLongPress {
    Tap,
    Cancelled,
    Lost,
}

/**
 * 收纳键手势：短按打开/关闭 HUD；长按后竖屏水平、横屏垂直拖动，松手切换到预览页。
 */
fun Modifier.dockMenuScrubGestures(
    isLandscape: Boolean,
    scrubEnabled: Boolean,
    currentDestination: MainDestination,
    stepPerPage: Dp,
    onTogglePanel: () -> Unit,
    onPreviewDestination: (MainDestination?) -> Unit,
    onCommitDestination: (MainDestination) -> Unit,
    onUserInteraction: () -> Unit = {},
): Modifier = composed {
    val dest by rememberUpdatedState(currentDestination)
    val onToggle by rememberUpdatedState(onTogglePanel)
    val onPreview by rememberUpdatedState(onPreviewDestination)
    val onCommit by rememberUpdatedState(onCommitDestination)
    val onInteract by rememberUpdatedState(onUserInteraction)
    val viewConfig = LocalViewConfiguration.current
    val density = LocalDensity.current
    val stepPx = with(density) { stepPerPage.toPx() }

    then(
        if (!scrubEnabled) {
            // Keys must stay stable; callbacks use rememberUpdatedState above.
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onToggle()
                        onInteract()
                    },
                )
            }
        } else {
            Modifier.pointerInput(
                isLandscape,
                stepPx,
                viewConfig.longPressTimeoutMillis,
                viewConfig.touchSlop,
            ) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val slop = viewConfig.touchSlop.toFloat()
                    val longMs = viewConfig.longPressTimeoutMillis.toLong()

                    val phase1: PreLongPress? = withTimeoutOrNull<PreLongPress>(longMs) {
                        var dist = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.find { it.id == pointerId }
                                ?: return@withTimeoutOrNull PreLongPress.Lost
                            dist += hypot(change.positionChange().x, change.positionChange().y)
                            if (!change.pressed) {
                                return@withTimeoutOrNull if (dist < slop) {
                                    PreLongPress.Tap
                                } else {
                                    PreLongPress.Cancelled
                                }
                            }
                            if (dist >= slop) return@withTimeoutOrNull PreLongPress.Cancelled
                        }
                        error("unreachable")
                    }

                    when (phase1) {
                        PreLongPress.Tap -> {
                            onToggle()
                            onInteract()
                        }
                        PreLongPress.Cancelled, PreLongPress.Lost -> {
                            awaitPointerUpOrGone(pointerId)
                        }
                        null -> {
                            val startIndex = Entries.indexOf(dest).coerceIn(0, Entries.lastIndex)
                            var accum = 0f
                            var previewIndex = startIndex

                            fun previewFromAccum() {
                                val deltaSteps = if (!isLandscape) {
                                    (-accum / stepPx).toInt()
                                } else {
                                    (accum / stepPx).toInt()
                                }
                                val idx = (startIndex + deltaSteps).coerceIn(0, Entries.lastIndex)
                                if (idx != previewIndex) {
                                    previewIndex = idx
                                    onPreview(Entries[previewIndex])
                                }
                            }

                            onPreview(Entries[previewIndex])
                            val completed = drag(pointerId) { change ->
                                val d = if (!isLandscape) {
                                    change.positionChange().x
                                } else {
                                    change.positionChange().y
                                }
                                change.consume()
                                accum += d
                                previewFromAccum()
                            }
                            if (completed) {
                                onCommit(Entries[previewIndex])
                            }
                            onPreview(null)
                        }
                    }
                }
            }
        },
    )
}

private suspend fun AwaitPointerEventScope.awaitPointerUpOrGone(pointerId: PointerId) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.find { it.id == pointerId } ?: return
        if (!change.pressed) return
    }
}
