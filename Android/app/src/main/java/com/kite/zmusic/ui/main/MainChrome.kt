package com.kite.zmusic.ui.main

import android.app.Activity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kite.zmusic.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow

internal object MainPalette {
    val Page = Color(0xFFF6F7F9)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF2C2C2E)
    val Secondary = Color(0xFF8E8E93)
    val Hint = Color(0xFFC7C7CC)
    val Accent = Color(0xFFEC4141)
    val Hairline = Color(0x14000000)
    val DockGlass = Color(0xE6FFFFFF)
    val DockStroke = Color(0x33FFFFFF)
}

internal val FloatingDockHeight = 64.dp
internal val FloatingDockCompactHeight = 52.dp
internal val MiniPlayerStackHeight = 64.dp
internal val FloatingChromeGap = 10.dp
internal val FloatingChromeSide = 18.dp
internal val FloatingChromeBottom = 10.dp

/** 三页共用顶栏高度，避免 HorizontalPager 滑动时标题行高低不一产生视差。 */
internal val MainPageHeaderHeight = 62.dp
internal val MainContentPadTop = 8.dp

internal fun mainContentPadH(landscape: Boolean) = if (landscape) 28.dp else 20.dp

/** 横屏主栏 / 设置：新页覆盖淡入，旧页只淡出，避免双页叠在液体玻璃采样里重影。 */
internal val LandscapeCoverEnter: EnterTransition =
    fadeIn(tween(260, easing = FastOutSlowInEasing)) +
        scaleIn(
            initialScale = 0.985f,
            animationSpec = tween(260, easing = FastOutSlowInEasing),
        )

internal val LandscapeCoverExit: ExitTransition =
    fadeOut(tween(200, easing = FastOutSlowInEasing))

@Composable
internal fun MainPageHeader(
    title: String,
    landscape: Boolean,
    modifier: Modifier = Modifier,
    showLogo: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(MainPageHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLogo) {
            Image(
                painter = painterResource(R.drawable.ic_logo_vinyl_z),
                contentDescription = null,
                modifier = Modifier
                    .size(if (landscape) 30.dp else 32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = if (landscape) 26.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Kyant Backdrop 液体玻璃：采样下层后 vibrancy → blur → lens。
 * 折射高度不超过圆角（Dock 胶囊约 32dp，迷你条 20dp），过小会只剩一层雾。
 */
internal fun Modifier.mainLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    surface: Color = Color.White.copy(alpha = 0.28f),
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(2.5f.dp.toPx())
        lens(10.7f.dp.toPx(), 21.3f.dp.toPx(), depthEffect = true)
    },
    highlight = { Highlight(width = 0.55.dp, alpha = 0.38f) },
    shadow = { null },
    onDrawSurface = { drawRect(surface) },
)

/**
 * 个人页资料卡：28dp 圆角，lens 高度低于圆角；白膜略高于 Dock，保证字可读。
 */
internal fun Modifier.profileLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(2.4f.dp.toPx())
        lens(8.dp.toPx(), 16.dp.toPx(), depthEffect = true)
    },
    highlight = { Highlight(width = 0.55.dp, alpha = 0.40f) },
    shadow = { null },
    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.32f)) },
)

/**
 * 确认弹窗卡片：32dp 圆角，lens 高度低于圆角；略提高白膜保证字可读。
 */
internal fun Modifier.dialogLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(2.2f.dp.toPx())
        lens(10.7f.dp.toPx(), 21.3f.dp.toPx(), depthEffect = true)
    },
    highlight = { Highlight(width = 0.55.dp, alpha = 0.40f) },
    shadow = { null },
    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.34f)) },
)

/**
 * 灵动岛胶囊：圆角约半高（36–40dp → 18–20dp），lens 高度必须低于圆角。
 * 与 Dock 同族：vibrancy → blur → lens，避免大 blur + 高不透明白导致发灰。
 */
internal fun Modifier.islandLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(2.2f.dp.toPx())
        lens(5.3f.dp.toPx(), 10.7f.dp.toPx(), depthEffect = true)
    },
    highlight = { Highlight(width = 0.5.dp, alpha = 0.42f) },
    shadow = { null },
    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.26f)) },
)

/** Dock 当前项：正向折射 + 内阴影，避免负 lens（文档要求高度 ≥ 0）。 */
internal fun Modifier.dockSunkenGlass(
    backdrop: Backdrop,
    shape: Shape,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(1.5f.dp.toPx())
        lens(6.7f.dp.toPx(), 14.7f.dp.toPx())
    },
    highlight = { Highlight(width = 0.45.dp, alpha = 0.16f) },
    shadow = { null },
    innerShadow = {
        InnerShadow(
            radius = 18.dp,
            offset = DpOffset(0.dp, 8.dp),
            color = Color.Black.copy(alpha = 0.42f),
            alpha = 1f,
        )
    },
    onDrawSurface = { drawRect(Color(0xFF141416).copy(alpha = 0.16f)) },
)

@Composable
internal fun MainLightSystemBars() {
    MainSystemBarIcons(lightIconsOnDarkScrim = false)
}

@Composable
internal fun MainDarkSystemBars() {
    MainSystemBarIcons(lightIconsOnDarkScrim = true)
}

/** 竖屏 MV：状态栏压在视频上用浅色图标，导航栏压在相关列表上用深色图标。 */
@Composable
internal fun MainMvPortraitSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = controller?.isAppearanceLightStatusBars
        val prevNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (prevStatus != null) controller.isAppearanceLightStatusBars = prevStatus
            if (prevNav != null) controller.isAppearanceLightNavigationBars = prevNav
        }
    }
}

@Composable
private fun MainSystemBarIcons(lightIconsOnDarkScrim: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, lightIconsOnDarkScrim) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = controller?.isAppearanceLightStatusBars
        val prevNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = !lightIconsOnDarkScrim
        controller?.isAppearanceLightNavigationBars = !lightIconsOnDarkScrim
        onDispose {
            if (prevStatus != null) controller.isAppearanceLightStatusBars = prevStatus
            if (prevNav != null) controller.isAppearanceLightNavigationBars = prevNav
        }
    }
}
