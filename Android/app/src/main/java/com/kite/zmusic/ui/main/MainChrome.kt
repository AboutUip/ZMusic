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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kite.zmusic.R
import com.kite.zmusic.data.ChromeGlassMode
import com.kite.zmusic.data.ChromeGlassStyle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

internal typealias MainPalette = com.kite.zmusic.ui.theme.MainPalette
internal typealias MainControls = com.kite.zmusic.ui.theme.MainControls

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

internal val LocalChromeGlassStyle = staticCompositionLocalOf { ChromeGlassStyle.Default }

internal val LocalChromeHaze = staticCompositionLocalOf<HazeState?> { null }

internal val LocalChromeBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * 自定义背景铺上时，设置条目 / 个人页歌单等组件边界。
 * null 表示本页没铺图，继续用纯色底。
 */
internal val LocalWallpaperItemChrome = staticCompositionLocalOf<ChromeGlassMode?> { null }

private val FrostedBlurMin = 4.dp
private val FrostedBlurMax = 72.dp

private fun ChromeGlassStyle.scaledBlur(base: Dp): Dp {
    val t = (0.25f + 1.75f * blur.coerceIn(0f, 1f)) /
        (0.25f + 1.75f * ChromeGlassStyle.BLUR_DEFAULT)
    return base * t
}

private fun ChromeGlassStyle.scaledLens(base: Dp): Dp =
    base * refraction.coerceIn(ChromeGlassStyle.REFRACTION_MIN, ChromeGlassStyle.REFRACTION_MAX)

/**
 * Kyant lens 高度必须小于圆角，否则 RenderThread SIGSEGV。
 * 侧栏整块矩形圆角为 0，不能开 lens。
 */
private fun Density.lensCapPx(shape: Shape): Float {
    if (shape === RectangleShape) return 0f
    val probe = Size(512f, 512f)
    val minR = when (shape) {
        is RoundedCornerShape -> minOf(
            shape.topStart.toPx(probe, this),
            shape.topEnd.toPx(probe, this),
            shape.bottomStart.toPx(probe, this),
            shape.bottomEnd.toPx(probe, this),
        )
        else -> 8.dp.toPx()
    }
    if (minR < 1f) return 0f
    return minR * 0.82f
}

private fun ChromeGlassStyle.frostedBlur(): Dp =
    lerp(FrostedBlurMin, FrostedBlurMax, blur.coerceIn(0f, 1f))

private fun ChromeGlassStyle.frostedTintAlpha(): Float =
    0.16f + 0.14f * blur.coerceIn(0f, 1f)

private fun ChromeGlassStyle.frostedHazeStyle(): HazeStyle {
    val tint = MainPalette.glassFill(frostedTintAlpha())
    return HazeStyle(
        backgroundColor = MainPalette.glassFill(0.22f),
        tints = listOf(HazeTint(tint)),
        blurRadius = frostedBlur(),
        noiseFactor = 0.04f + 0.08f * blur.coerceIn(0f, 1f),
        fallbackTint = HazeTint(MainPalette.glassFill(0.62f)),
    )
}

internal fun pageSheetHazeStyle(): HazeStyle {
    val page = MainPalette.Page
    return HazeStyle(
        backgroundColor = page,
        tints = listOf(
            HazeTint(MainPalette.SheetTint),
            HazeTint(page.copy(alpha = 0.52f)),
        ),
        blurRadius = 56.dp,
        noiseFactor = 0.08f,
        fallbackTint = HazeTint(page.copy(alpha = 0.94f)),
    )
}

/**
 * 按当前主题画玻璃：液态用 Kyant Backdrop；磨砂用 Haze；纯色不透明。
 */
internal fun Modifier.chromeGlassSurface(
    backdrop: Backdrop,
    shape: Shape,
    style: ChromeGlassStyle,
    haze: HazeState?,
    liquidBlur: Dp,
    liquidLensHeight: Dp,
    liquidLensAmount: Dp,
    highlightWidth: Dp,
    highlightAlpha: Float,
    surface: Color,
    depthEffect: Boolean = true,
): Modifier = when (style.mode) {
    ChromeGlassMode.Liquid -> composed {
        val cap = with(LocalDensity.current) { lensCapPx(shape) }
        val lensH = with(LocalDensity.current) {
            style.scaledLens(liquidLensHeight).toPx()
        }.coerceAtMost(cap)
        val lensA = with(LocalDensity.current) {
            style.scaledLens(liquidLensAmount).toPx()
        }.coerceAtMost(cap * 2f)
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(style.scaledBlur(liquidBlur).toPx())
                if (lensH > 0.5f) {
                    lens(lensH, lensA, depthEffect = depthEffect)
                }
            },
            highlight = { Highlight(width = highlightWidth, alpha = highlightAlpha) },
            shadow = { null },
            onDrawSurface = { drawRect(surface) },
        )
    }
    ChromeGlassMode.Frosted -> {
        val h = haze
        if (h != null) {
            clip(shape).hazeEffect(state = h, style = style.frostedHazeStyle())
        } else {
            clip(shape).background(MainPalette.glassFill(0.62f))
        }
    }
    ChromeGlassMode.Solid -> clip(shape).background(MainPalette.Surface)
}

/**
 * 铺了自定义背景时，把纯色条目改成磨砂 / 液态；数值跟 [LocalChromeGlassStyle]。
 * 没铺图或选纯色时仍是 [MainPalette.Surface]。
 */
internal fun Modifier.wallpaperItemChrome(
    shape: Shape,
    solid: Color = MainPalette.Surface,
): Modifier = composed {
    val mode = LocalWallpaperItemChrome.current
    if (mode == null || mode == ChromeGlassMode.Solid) {
        return@composed clip(shape).background(solid)
    }
    val backdrop = LocalChromeBackdrop.current
    if (mode == ChromeGlassMode.Liquid && backdrop == null) {
        return@composed clip(shape).background(MainPalette.glassFill(0.62f))
    }
    chromeGlassSurface(
        backdrop = backdrop ?: return@composed clip(shape).background(MainPalette.glassFill(0.62f)),
        shape = shape,
        style = LocalChromeGlassStyle.current.copy(mode = mode),
        haze = LocalChromeHaze.current,
        liquidBlur = 2.2.dp,
        liquidLensHeight = 10.7.dp,
        liquidLensAmount = 21.3.dp,
        highlightWidth = 0.55.dp,
        highlightAlpha = 0.38f,
        surface = MainPalette.glassFill(0.30f),
        depthEffect = true,
    )
}

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
 * 跟 Dock / 迷你条同一套玻璃：液态 / 磨砂 / 纯色走 [LocalChromeGlassStyle]。
 */
internal fun Modifier.mainChromePlate(shape: Shape): Modifier = composed {
    val backdrop = LocalChromeBackdrop.current
    if (backdrop == null) {
        return@composed clip(shape).background(MainPalette.Surface)
    }
    mainLiquidGlass(backdrop, shape)
}

/**
 * 主栏玻璃（Dock / 迷你条）。液态走 Kyant Backdrop；磨砂走 Haze；纯色不透明。
 * 折射高度不超过圆角（Dock 胶囊约 32dp，迷你条 20dp），过小会只剩一层雾。
 */
internal fun Modifier.mainLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    surface: Color? = null,
): Modifier = composed {
    chromeGlassSurface(
        backdrop = backdrop,
        shape = shape,
        style = LocalChromeGlassStyle.current,
        haze = LocalChromeHaze.current,
        liquidBlur = 2.5.dp,
        liquidLensHeight = 10.7.dp,
        liquidLensAmount = 21.3.dp,
        highlightWidth = 0.55.dp,
        highlightAlpha = 0.38f,
        surface = surface ?: MainPalette.glassFill(0.28f),
        depthEffect = true,
    )
}

/**
 * 个人页资料卡：28dp 圆角，lens 高度低于圆角；白膜略高于 Dock，保证字可读。
 */
internal fun Modifier.profileLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
): Modifier = composed {
    chromeGlassSurface(
        backdrop = backdrop,
        shape = shape,
        style = LocalChromeGlassStyle.current,
        haze = LocalChromeHaze.current,
        liquidBlur = 2.4.dp,
        liquidLensHeight = 8.dp,
        liquidLensAmount = 16.dp,
        highlightWidth = 0.55.dp,
        highlightAlpha = 0.40f,
        surface = MainPalette.glassFill(0.32f),
        depthEffect = true,
    )
}

/**
 * 确认弹窗卡片：32dp 圆角，lens 高度低于圆角；略提高白膜保证字可读。
 */
internal fun Modifier.dialogLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
): Modifier = composed {
    chromeGlassSurface(
        backdrop = backdrop,
        shape = shape,
        style = LocalChromeGlassStyle.current,
        haze = LocalChromeHaze.current,
        liquidBlur = 2.2.dp,
        liquidLensHeight = 10.7.dp,
        liquidLensAmount = 21.3.dp,
        highlightWidth = 0.55.dp,
        highlightAlpha = 0.40f,
        surface = MainPalette.glassFill(0.34f),
        depthEffect = true,
    )
}

/**
 * 灵动岛胶囊：圆角约半高（36–40dp → 18–20dp），lens 高度必须低于圆角。
 * 与 Dock 同族：vibrancy → blur → lens，避免大 blur + 高不透明白导致发灰。
 */
internal fun Modifier.islandLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
): Modifier = composed {
    chromeGlassSurface(
        backdrop = backdrop,
        shape = shape,
        style = LocalChromeGlassStyle.current,
        haze = LocalChromeHaze.current,
        liquidBlur = 2.2.dp,
        liquidLensHeight = 5.3.dp,
        liquidLensAmount = 10.7.dp,
        highlightWidth = 0.5.dp,
        highlightAlpha = 0.42f,
        surface = MainPalette.glassFill(0.26f),
        depthEffect = true,
    )
}

/** Dock 当前项：正向折射 + 内阴影，避免负 lens（文档要求高度 ≥ 0）。 */
internal fun Modifier.dockSunkenGlass(
    backdrop: Backdrop,
    shape: Shape,
): Modifier = composed {
    val style = LocalChromeGlassStyle.current
    when (style.mode) {
        ChromeGlassMode.Liquid -> drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(style.scaledBlur(1.5.dp).toPx())
                val lensH = style.scaledLens(6.7.dp).toPx()
                val lensA = style.scaledLens(14.7.dp).toPx()
                if (lensH > 0.5f) {
                    lens(lensH, lensA)
                }
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
        ChromeGlassMode.Frosted ->
            clip(shape).background(Color(0xFF141416).copy(alpha = 0.16f))
        ChromeGlassMode.Solid ->
            clip(shape).background(Color(0xFF141416).copy(alpha = 0.22f))
    }
}

/** 首页/设置等跟色板走的页面：浅色深图标，深色浅图标。 */
@Composable
internal fun MainLightSystemBars() {
    MainSystemBarIcons(lightIconsOnDarkScrim = MainPalette.isDark)
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
