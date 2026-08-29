package com.kite.zmusic.ui.main

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
