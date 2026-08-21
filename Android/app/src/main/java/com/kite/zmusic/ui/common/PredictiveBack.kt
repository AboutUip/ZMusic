package com.kite.zmusic.ui.common

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException

internal const val ZMUSIC_BACK_LOG = "ZMusicBack"

internal val LocalPredictiveBackEnabled = compositionLocalOf { false }

@Stable
internal class PredictiveBackUi {
    var progress by mutableFloatStateOf(0f)
        internal set
    var swipeEdge by mutableIntStateOf(BACK_EDGE_LEFT)
        internal set
}

@Stable
internal class PredictiveBackClaimsState {
    val stack = PredictiveBackClaimStack()
    var revision by mutableIntStateOf(0)
        private set

    fun claim(id: Any) {
        val before = stack.size
        stack.claim(id)
        if (stack.size != before) revision++
    }

    fun release(id: Any) {
        val before = stack.size
        stack.release(id)
        if (stack.size != before) revision++
    }

    fun isTop(id: Any): Boolean = stack.isTop(id)
}

internal val LocalPredictiveBackClaimsState = staticCompositionLocalOf {
    PredictiveBackClaimsState()
}

@Composable
internal fun rememberPredictiveBackUi(
    enabled: Boolean,
    onPeekCommit: (Boolean) -> Unit = {},
    onBack: () -> Unit,
): PredictiveBackUi {
    val ui = remember { PredictiveBackUi() }
    val settingOn = LocalPredictiveBackEnabled.current
    val claims = LocalPredictiveBackClaimsState.current
    val id = remember { Any() }
    val onBackUpdated = rememberUpdatedState(onBack)
    val onPeekUpdated = rememberUpdatedState(onPeekCommit)
    DisposableEffect(enabled) {
        if (enabled) claims.claim(id) else claims.release(id)
        onDispose { claims.release(id) }
    }
    val isTop = claims.revision.let { claims.isTop(id) }
    val predictiveOn = predictiveHandlerEnabled(settingOn, enabled, isTop)
    val instantOn = instantHandlerEnabled(settingOn, enabled, isTop)
    LaunchedEffect(predictiveOn) {
        if (!predictiveOn && ui.progress > 0f) {
            ui.progress = 0f
            ui.swipeEdge = BACK_EDGE_LEFT
        }
    }
    PredictiveBackHandler(enabled = predictiveOn) { events ->
        var maxP = 0f
        var lockedEdge: Int? = null
        Log.i(
            ZMUSIC_BACK_LOG,
            "start id=${id.hashCode()} stack=${claims.stack.debug()} edge-lock",
        )
        try {
            events.collect { event ->
                val edge = lockSwipeEdge(lockedEdge, event.swipeEdge)
                lockedEdge = edge
                ui.swipeEdge = edge
                val p = event.progress.coerceIn(0f, 1f)
                ui.progress = p
                if (p > maxP) maxP = p
            }
            val peeked = maxP >= 0.08f
            Log.i(
                ZMUSIC_BACK_LOG,
                "commit peeked=$peeked maxP=$maxP edge=${lockedEdge ?: ui.swipeEdge}",
            )
            onPeekUpdated.value(peeked)
            onBackUpdated.value()
            ui.progress = 0f
        } catch (e: CancellationException) {
            Log.i(ZMUSIC_BACK_LOG, "cancel progress=${ui.progress}")
            ui.progress = 0f
            ui.swipeEdge = BACK_EDGE_LEFT
            throw e
        }
    }
    BackHandler(enabled = instantOn) {
        Log.i(ZMUSIC_BACK_LOG, "instant-back id=${id.hashCode()}")
        onBackUpdated.value()
    }
    return ui
}

internal fun Modifier.predictiveBackLayer(
    ui: PredictiveBackUi,
    axis: PredictiveBackAxis = PredictiveBackAxis.Horizontal,
): Modifier = composed {
    val p = ui.progress.coerceIn(0f, 1f)
    val swipeEdge = ui.swipeEdge
    graphicsLayer {
        val params = predictiveBackLayerParams(p, swipeEdge, axis, size.width, size.height)
        if (p <= 0.001f) return@graphicsLayer
        alpha = params.alpha
        translationX = params.translationX
        translationY = params.translationY
        scaleX = params.scaleX
        scaleY = params.scaleY
        transformOrigin = TransformOrigin(params.originX, params.originY)
    }
}
