package com.kite.zmusic.ui.main

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** 与 Android `MainChrome` 横屏常量对齐。 */
internal val MainPageHeaderHeight = 62.dp
internal val MainContentPadTop = 8.dp

internal fun mainContentPadH() = 28.dp

internal val LandscapeCoverEnter: EnterTransition =
    fadeIn(tween(260, easing = FastOutSlowInEasing)) +
        scaleIn(initialScale = 0.985f, animationSpec = tween(260, easing = FastOutSlowInEasing))

internal val LandscapeCoverExit: ExitTransition =
    fadeOut(tween(200))

internal const val LandscapeVinylWeight = 0.36f
internal const val LandscapeLyricsWeight = 0.64f
internal val LandscapeMiniBarRadius = 24.dp
internal val LandscapeMiniCover = 44.dp
internal val LandscapeCloudPillHeight = 48.dp
internal val LandscapeSettingsIconWell = 36.dp
internal val LandscapeFeatureIconWell = 36.dp
internal val LandscapeHomeSearchHeight = 40.dp

/** Android `LandscapePlayerBody` 唱片边长：`(maxWidth*0.92).coerceIn(132,252)*1.14`，上限 286。 */
internal fun landscapeVinylDiscDp(columnMaxWidth: Dp): Dp {
    val raw = (columnMaxWidth * 0.92f).coerceIn(132.dp, 252.dp) * 1.14f
    return raw.coerceAtMost(286.dp)
}

/** 空白区域也吃掉点击，避免盖在主页上的 overlay 点穿到歌单。 */
internal fun Modifier.consumeClicks(): Modifier = clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = {},
)

/**
 * 横屏三栏常驻组合：切 Tab 不卸页面，进页缓存与滚动位置都还在。
 * 动画语义对齐 Android `LandscapeCoverPages`（淡入 + 微缩放）。
 */
@Composable
internal fun LandscapeCoverPages(
    currentIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
    page: @Composable (Int) -> Unit,
) {
    Box(modifier) {
        repeat(pageCount) { index ->
            val selected = index == currentIndex
            val alpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                label = "coverAlpha$index",
            )
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.985f,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                label = "coverScale$index",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(if (selected) 1f else 0f)
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                        clip = true
                    }
                    .then(
                        if (selected) {
                            Modifier
                        } else {
                            Modifier.pointerInput(Unit) {}
                        },
                    ),
            ) {
                page(index)
            }
        }
    }
}
