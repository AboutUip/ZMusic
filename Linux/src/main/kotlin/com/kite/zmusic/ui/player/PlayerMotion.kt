package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** 与 Android `LandscapePlayerBody` 设置侧栏同曲线。 */
internal val PlayerSettingsCurve = CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f)

/** 黑胶垂直居中 / 选句 / 自选配色开合。 */
internal val VinylCenterEasing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)
internal const val VinylCenterMs = 480
internal const val SettingsSheetMs = 460

/** 右侧悬浮板离场：滑过面板宽 + 右缝。 */
internal fun landscapeSideSheetSlideX(
    progress: Float,
    panelWidthPx: Float,
    endPadPx: Float,
): Float = (1f - progress.coerceIn(0f, 1f)) * (panelWidthPx + endPadPx)

@Composable
internal fun rememberSheetProgress(
    open: Boolean,
    durationMs: Int = SettingsSheetMs,
    easing: Easing = PlayerSettingsCurve,
): Float {
    val panel = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) {
        panel.animateTo(
            if (open) 1f else 0f,
            tween(durationMs, easing = easing),
        )
    }
    return panel.value
}

@Composable
internal fun LandscapeSideSheet(
    progress: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.45f,
    endPad: Dp = 28.dp,
    zIndex: Float = 70f,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0.001f) return
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .zIndex(zIndex)
            .clickable(
                enabled = t > 0.2f,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        val panelW = constraints.maxWidth * widthFraction
        val padPx = with(density) { endPad.toPx() }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(widthFraction)
                .padding(endPad)
                .graphicsLayer {
                    translationX = landscapeSideSheetSlideX(t, panelW, padPx)
                    alpha = (0.15f + 0.85f * t).coerceIn(0f, 1f)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            content = content,
        )
    }
}
