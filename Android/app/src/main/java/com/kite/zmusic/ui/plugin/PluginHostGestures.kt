package com.kite.zmusic.ui.plugin

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.plugin.PluginUiTarget
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 不推迟单击：短按只投 `ui.press` 且不 consume，宿主原有 clickable 照常。
 * 长按 consume 并弹出已登记的操作槽（无插件项则走 [onHostLongPress]）。
 */
fun Modifier.pluginSurface(
    surface: String,
    target: PluginUiTarget,
    hostLongPressLabel: String? = null,
    onHostLongPress: (() -> Unit)? = null,
): Modifier = composed {
    val context = LocalContext.current
    val engine = remember(context) {
        (context.applicationContext as ZMusicApplication).pluginEngine
    }
    val targetRef = rememberUpdatedState(target)
    val labelRef = rememberUpdatedState(hostLongPressLabel)
    val hostRef = rememberUpdatedState(onHostLongPress)
    Modifier.pointerInput(surface) {
        val slop = viewConfiguration.touchSlop
        val timeout = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val start = down.position
            val raced = withTimeoutOrNull(timeout) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.find { it.id == pointerId }
                        ?: return@withTimeoutOrNull "cancel"
                    if (!change.pressed) return@withTimeoutOrNull "up"
                    val dx = change.position.x - start.x
                    val dy = change.position.y - start.y
                    if (dx * dx + dy * dy > slop * slop) return@withTimeoutOrNull "slop"
                }
                @Suppress("UNREACHABLE_CODE")
                "cancel"
            }
            when (raced) {
                null -> {
                    engine.handleSurfaceLongPress(
                        surface,
                        targetRef.value,
                        labelRef.value,
                        hostRef.value,
                    )
                    while (true) {
                        val rest = awaitPointerEvent(PointerEventPass.Main)
                        val c = rest.changes.find { it.id == pointerId } ?: return@awaitEachGesture
                        c.consume()
                        if (!c.pressed) return@awaitEachGesture
                    }
                }
                "up" -> engine.emitUiGesture("press", surface, targetRef.value)
                else -> {
                    while (true) {
                        val rest = awaitPointerEvent(PointerEventPass.Main)
                        val c = rest.changes.find { it.id == pointerId } ?: return@awaitEachGesture
                        if (!c.pressed) return@awaitEachGesture
                    }
                }
            }
        }
    }
}
