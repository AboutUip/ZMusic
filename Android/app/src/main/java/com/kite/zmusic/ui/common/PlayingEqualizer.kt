package com.kite.zmusic.ui.common

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 列表「正在播放」指示：播放时三柱跳动，暂停时停在低位，与迷你条播放态对齐。
 */
@Composable
fun PlayingEqualizer(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val animate = playing && !reduceMotion
    val infinite = rememberInfiniteTransition(label = "eq")
    val aAnim by infinite.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eqA",
    )
    val bAnim by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eqB",
    )
    val cAnim by infinite.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eqC",
    )
    val a = if (animate) aAnim else if (playing) 0.72f else 0.34f
    val b = if (animate) bAnim else if (playing) 0.95f else 0.52f
    val c = if (animate) cAnim else if (playing) 0.58f else 0.28f
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val barW = ((size.width - gap * 2f) / 3f).coerceAtLeast(1.5.dp.toPx())
        val radius = CornerRadius(barW / 2f, barW / 2f)
        val levels = floatArrayOf(a, b, c)
        repeat(3) { i ->
            val h = (size.height * levels[i]).coerceIn(barW, size.height)
            val x = i * (barW + gap)
            val y = size.height - h
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barW, h),
                cornerRadius = radius,
            )
        }
    }
}
