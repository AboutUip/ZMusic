package com.kite.zmusic.ui.common

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException

internal enum class PredictiveBackAxis {
    Horizontal,
    Vertical,
}

@Stable
internal class PredictiveBackUi {
    var progress by mutableFloatStateOf(0f)
        internal set
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
        internal set
}

@Composable
internal fun rememberPredictiveBackUi(
    enabled: Boolean,
    onPeekCommit: (Boolean) -> Unit = {},
    onBack: () -> Unit,
): PredictiveBackUi {
    val ui = remember { PredictiveBackUi() }
    val onBackUpdated = rememberUpdatedState(onBack)
    val onPeekUpdated = rememberUpdatedState(onPeekCommit)
    LaunchedEffect(enabled) {
        if (!enabled && ui.progress > 0f) ui.progress = 0f
    }
    PredictiveBackHandler(enabled = enabled) { events ->
        var maxP = 0f
        try {
            events.collect { event ->
                ui.swipeEdge = event.swipeEdge
                val p = event.progress.coerceIn(0f, 1f)
                ui.progress = p
                if (p > maxP) maxP = p
            }
            val peeked = maxP >= 0.08f
            if (peeked) ui.progress = 1f
            onPeekUpdated.value(peeked)
            onBackUpdated.value()
            if (!peeked) ui.progress = 0f
        } catch (e: CancellationException) {
            val start = ui.progress
            val anim = Animatable(start)
            anim.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) {
                ui.progress = value
            }
            throw e
        }
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
        if (p <= 0.001f) return@graphicsLayer
        alpha = 1f - 0.08f * p
        when (axis) {
            PredictiveBackAxis.Horizontal -> {
                val dir = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                translationX = dir * p * size.width
                val s = 1f - 0.04f * p
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(if (dir > 0f) 0f else 1f, 0.5f)
            }
            PredictiveBackAxis.Vertical -> {
                translationY = p * size.height
                val s = 1f - 0.03f * p
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
    }
}
