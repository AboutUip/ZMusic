package com.kite.zmusic.ui.notice

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.plugin.PluginLookPresent
import com.kite.zmusic.ui.common.GlassActionSheetHostState
import com.kite.zmusic.ui.common.GlassActionSheetOverlay
import com.kite.zmusic.ui.common.GlassAlertHostState
import com.kite.zmusic.ui.common.GlassAlertOverlay
import com.kite.zmusic.ui.plugin.PluginAlertHost
import com.kite.zmusic.ui.plugin.PluginContextMenuHost
import com.kite.zmusic.ui.plugin.PluginFaultHost
import com.kite.zmusic.ui.plugin.PluginSheetHost
import com.kite.zmusic.ui.easter.MjEasterEggHost
import com.kite.zmusic.ui.update.AppUpdateHost
import com.kite.zmusic.ui.player.ListenMatchHost
import com.kite.zmusic.ui.player.LocalShareSheetHost
import com.kite.zmusic.ui.player.ShareSheetHostState
import com.kite.zmusic.ui.player.ShareSheetOverlay
import com.kite.zmusic.ui.common.LocalGlassActionSheetHost
import com.kite.zmusic.ui.common.LocalGlassAlertHost
import com.kite.zmusic.ui.common.LocalPredictiveBackClaimsState
import com.kite.zmusic.ui.common.LocalPredictiveBackEnabled
import com.kite.zmusic.ui.common.PredictiveBackClaimsState
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.main.LocalChromeGlassStyle
import com.kite.zmusic.ui.main.LocalChromeHaze
import com.kite.zmusic.ui.theme.TextTheme
import com.kite.zmusic.ui.main.islandLiquidGlass
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 弹出：快起、过冲再回。 */
private val IslandPopEasing = CubicBezierEasing(0.18f, 1.22f, 0.28f, 1f)

/** 展开成岛：先慢后快再过冲。 */
private val IslandExpandEasing = CubicBezierEasing(0.14f, 1.16f, 0.24f, 1f)

/** 换条时收到一半：前段快收。 */
private val IslandHalfCollapseEasing = CubicBezierEasing(0.52f, 0.04f, 0.18f, 1f)

/** 收岛离场。 */
private val IslandDismissEasing = CubicBezierEasing(0.4f, 0.02f, 0.16f, 1f)

private val CoverMorphEasing = CubicBezierEasing(0.22f, 0.08f, 0.2f, 1f)

private val IslandCapsuleShape = RoundedCornerShape(50)

/**
 * 根层液体玻璃采样：记录整页（含全屏播放器），岛画在记录层之外。
 * 放在方向切换蒙版之下，避免挡住横竖屏过渡。
 */
@Composable
fun IslandNoticeRoot(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val islandHaze = remember { HazeState() }
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val glassStyle by app.chromeGlassStore.style.collectAsStateWithLifecycle()
    val effectiveGlass = PluginLookPresent.glass(glassStyle)
    val predictiveBackOn by app.predictiveBackStore.enabled.collectAsStateWithLifecycle()
    val predictiveBackClaims = remember { PredictiveBackClaimsState() }
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val alertHost = remember { GlassAlertHostState() }
    val actionSheetHost = remember { GlassActionSheetHostState() }
    val shareSheetHost = remember { ShareSheetHostState() }
    var islandLive by remember { mutableStateOf(false) }
    val captureLive =
        islandLive || alertHost.visible || actionSheetHost.visible || shareSheetHost.visible
    CompositionLocalProvider(
        LocalGlassAlertHost provides alertHost,
        LocalGlassActionSheetHost provides actionSheetHost,
        LocalShareSheetHost provides shareSheetHost,
        LocalChromeGlassStyle provides effectiveGlass,
        LocalChromeHaze provides islandHaze,
        LocalPredictiveBackEnabled provides predictiveBackOn,
        LocalPredictiveBackClaimsState provides predictiveBackClaims,
    ) {
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (captureLive) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (captureLive) {
                                Modifier.hazeSource(state = islandHaze, zIndex = 0f)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    content()
                }
            }
            IslandNoticeHost(
                center = app.islandNoticeCenter,
                backdrop = backdrop,
                landscape = landscape,
                onLiveChange = { islandLive = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(500f),
            )
            GlassActionSheetOverlay(
                state = actionSheetHost,
                backdrop = backdrop,
                landscape = landscape,
                modifier = Modifier.zIndex(790f),
            )
            ShareSheetOverlay(
                state = shareSheetHost,
                hazeState = islandHaze,
                modifier = Modifier.zIndex(795f),
            )
            GlassAlertOverlay(
                state = alertHost,
                backdrop = backdrop,
                landscape = landscape,
                modifier = Modifier.zIndex(800f),
            )
            PluginFaultHost()
            PluginAlertHost()
            PluginSheetHost()
            PluginContextMenuHost()
            AppUpdateHost()
            ListenMatchHost()
            MjEasterEggHost(
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun IslandNoticeHost(
    center: IslandNoticeCenter,
    backdrop: Backdrop,
    landscape: Boolean,
    onLiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val densityState = rememberUpdatedState(density)
    val landscapeState = rememberUpdatedState(landscape)
    val configuration = LocalConfiguration.current
    val configState = rememberUpdatedState(configuration)
    val measurer = rememberTextMeasurer()

    var notice by remember { mutableStateOf<IslandNotice?>(null) }
    var mounted by remember { mutableStateOf(false) }

    val expansion = remember { Animatable(0f) }
    val scale = remember { Animatable(0.42f) }
    val offsetY = remember { Animatable(0f) }
    val coverBlend = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val squashY = remember { Animatable(1f) }
    val capturing = mounted || alpha.value > 0.02f
    SideEffect { onLiveChange(capturing) }

    LaunchedEffect(center) {
        fun px(dp: Dp): Float = with(densityState.value) { dp.toPx() }

        suspend fun appear(next: IslandNotice) {
            notice = next
            mounted = true
            coverBlend.snapTo(0f)
            expansion.snapTo(0f)
            squashY.snapTo(1f)
            alpha.snapTo(0f)
            scale.snapTo(0.38f)
            offsetY.snapTo(-px(16.dp))
            coroutineScope {
                launch {
                    alpha.animateTo(1f, tween(90, easing = IslandPopEasing))
                }
                launch {
                    scale.animateTo(
                        1.11f,
                        spring(dampingRatio = 0.46f, stiffness = 540f),
                    )
                }
                launch {
                    offsetY.animateTo(
                        px(3.dp),
                        spring(dampingRatio = 0.52f, stiffness = 480f),
                    )
                }
            }
            coroutineScope {
                launch {
                    scale.animateTo(
                        1f,
                        spring(dampingRatio = 0.74f, stiffness = 380f),
                    )
                }
                launch {
                    offsetY.animateTo(
                        0f,
                        spring(dampingRatio = 0.8f, stiffness = 360f),
                    )
                }
            }
            delay(if (next.coverUrl != null) 130L else 90L)
            if (next.coverUrl != null) {
                coroutineScope {
                    launch {
                        coverBlend.animateTo(1f, tween(230, easing = CoverMorphEasing))
                    }
                    launch {
                        scale.animateTo(1.045f, tween(120, easing = CoverMorphEasing))
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = 0.58f, stiffness = 420f),
                        )
                    }
                }
            }
            delay(36L)
            coroutineScope {
                launch {
                    expansion.animateTo(1f, tween(540, easing = IslandExpandEasing))
                }
                launch {
                    squashY.snapTo(0.88f)
                    squashY.animateTo(
                        1.055f,
                        tween(260, easing = CubicBezierEasing(0.18f, 0.9f, 0.32f, 1f)),
                    )
                    squashY.animateTo(
                        1f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = 380f,
                        ),
                    )
                }
            }
        }

        suspend fun replace(next: IslandNotice) {
            expansion.animateTo(0.38f, tween(210, easing = IslandHalfCollapseEasing))
            notice = next
            coverBlend.snapTo(if (next.coverUrl != null) 1f else 0f)
            squashY.snapTo(0.92f)
            coroutineScope {
                launch {
                    expansion.animateTo(1f, tween(470, easing = IslandExpandEasing))
                }
                launch {
                    squashY.animateTo(
                        1.05f,
                        tween(200, easing = CubicBezierEasing(0.2f, 1.1f, 0.32f, 1f)),
                    )
                    squashY.animateTo(
                        1f,
                        spring(dampingRatio = 0.5f, stiffness = 400f),
                    )
                }
            }
        }

        suspend fun dismiss() {
            coroutineScope {
                launch {
                    expansion.animateTo(0f, tween(260, easing = IslandDismissEasing))
                }
                launch {
                    delay(90L)
                    scale.animateTo(0.72f, tween(200, easing = IslandDismissEasing))
                }
                launch {
                    delay(90L)
                    offsetY.animateTo(-px(10.dp), tween(200, easing = IslandDismissEasing))
                }
                launch {
                    delay(110L)
                    alpha.animateTo(0f, tween(160, easing = IslandDismissEasing))
                }
            }
            mounted = false
            notice = null
            scale.snapTo(0.42f)
            offsetY.snapTo(0f)
            squashY.snapTo(1f)
            coverBlend.snapTo(0f)
        }

        while (isActive) {
            when (val work = center.awaitWork()) {
                is IslandWork.Sticky -> {
                    var shown = work.notice
                    if (!mounted) {
                        appear(shown)
                    } else {
                        notice = shown
                    }
                    while (isActive) {
                        val next = center.awaitStickyChange(shown)
                        if (next == null) {
                            dismiss()
                            break
                        }
                        notice = next
                        shown = next
                    }
                }
                is IslandWork.Queued -> {
                    var shown = work.notice
                    appear(shown)
                    while (isActive) {
                        when (
                            val next = center.awaitQueuedOrStickyOrTimeout(
                                queuedDwellMs(
                                    notice = shown,
                                    landscape = landscapeState.value,
                                    screenW = configState.value.screenWidthDp.dp,
                                    measurer = measurer,
                                    density = densityState.value,
                                ),
                            )
                        ) {
                            null -> {
                                dismiss()
                                break
                            }
                            is IslandWork.Sticky -> break
                            is IslandWork.Queued -> {
                                replace(next.notice)
                                shown = next.notice
                            }
                        }
                    }
                }
            }
        }
    }

    val current = notice ?: run {
        Box(modifier.size(0.dp))
        return
    }
    if (!mounted && alpha.value <= 0.02f) {
        Box(modifier.size(0.dp))
        return
    }

    val screenW = configuration.screenWidthDp.dp
    val islandH = if (landscapeState.value) 36.dp else 40.dp
    val coverSize = islandH - 8.dp
    val maxCapsule = if (landscapeState.value) {
        minOf(screenW * 0.42f, 320.dp)
    } else {
        minOf(screenW * 0.78f, 300.dp)
    }
    val expandT = expansion.value.coerceIn(0f, 1f)
    val startPad = lerp(4.dp, 6.dp, expandT)
    val endPad = lerp(4.dp, 14.dp, expandT)
    val gap = lerp(0.dp, 8.dp, expandT)
    val textStyle = islandTextStyle(landscapeState.value)
    val textPx = measurer.measure(
        text = current.message,
        style = textStyle,
        maxLines = 1,
    ).size.width
    val textWant = with(density) { textPx.toDp() }
    val maxText = (maxCapsule - startPad - coverSize - gap - endPad).coerceAtLeast(24.dp)
    val usedText = minOf(textWant, maxText)
    val targetWidth = (startPad + coverSize + gap + usedText + endPad)
        .coerceIn(islandH, maxCapsule)
    val width = lerp(
        islandH,
        targetWidth,
        expansion.value.coerceIn(0f, 1.08f),
    )
    val textSlot = (width - startPad - coverSize - gap - endPad).coerceAtLeast(0.dp)
    val textLin = ((expansion.value - 0.28f) / 0.42f).coerceIn(0f, 1f)
    val textAlpha = textLin * textLin * (3f - 2f * textLin)
    val overflowing = textWant > maxText + 1.dp && expansion.value > 0.92f

    Box(
        modifier
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(top = if (landscapeState.value) 8.dp else 6.dp)
            .wrapContentSize(Alignment.TopCenter)
            .semantics { contentDescription = current.message },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value * squashY.value
                    translationY = offsetY.value
                    this.alpha = alpha.value
                }
                .width(width)
                .height(islandH)
                .islandLiquidGlass(backdrop, IslandCapsuleShape)
                .clip(IslandCapsuleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                Modifier
                    .height(islandH)
                    .padding(start = startPad, end = endPad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IslandGlyph(
                    coverUrl = current.coverUrl,
                    coverBlend = coverBlend.value,
                    size = coverSize,
                )
                if (textAlpha > 0.02f && textSlot > 4.dp) {
                    IslandMarqueeText(
                        text = current.message,
                        style = textStyle,
                        overflowing = overflowing,
                        modifier = Modifier
                            .padding(start = gap)
                            .width(textSlot)
                            .graphicsLayer { this.alpha = textAlpha },
                    )
                }
            }
        }
    }
}

@Composable
private fun IslandGlyph(
    coverUrl: String?,
    coverBlend: Float,
    size: Dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_vinyl_z),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - coverBlend },
            contentScale = ContentScale.Crop,
        )
        if (!coverUrl.isNullOrBlank()) {
            UrlImage(
                url = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = coverBlend
                        val s = 0.82f + 0.18f * coverBlend
                        scaleX = s
                        scaleY = s
                    },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun IslandMarqueeText(
    text: String,
    style: TextStyle,
    overflowing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!overflowing) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = modifier,
        )
        return
    }
    val offset = remember(text) { Animatable(0f) }
    var slotPx by remember(text) { mutableFloatStateOf(0f) }
    var contentPx by remember(text) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    LaunchedEffect(text) {
        offset.snapTo(0f)
        val distance = snapshotFlow { contentPx - slotPx }.first { it > 1f }
        delay(IslandMarqueeHoldMs)
        val pxPerSec = with(density) { IslandMarqueeVelocity.toPx() }.coerceAtLeast(1f)
        val ms = ((distance / pxPerSec) * 1_000f).toInt().coerceAtLeast(1)
        offset.animateTo(distance, tween(ms, easing = LinearEasing))
    }
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { slotPx = it.width.toFloat() },
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .onSizeChanged { contentPx = it.width.toFloat() }
                .graphicsLayer { translationX = -offset.value },
        )
    }
}

private fun islandTextStyle(landscape: Boolean) = TextStyle(
    color = TextTheme.Island,
    fontSize = if (landscape) 12.5.sp else 13.5.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.15).sp,
    lineHeight = if (landscape) 16.sp else 18.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val IslandMarqueeVelocity = 28.dp
private const val IslandMarqueeHoldMs = 420L
private const val IslandMarqueeEndRestMs = 500L
private const val IslandFitDwellMs = 2_400L

private fun islandMaxTextSlot(landscape: Boolean, screenW: Dp): Dp {
    val islandH = if (landscape) 36.dp else 40.dp
    val coverSize = islandH - 8.dp
    val maxCapsule = if (landscape) {
        minOf(screenW * 0.42f, 320.dp)
    } else {
        minOf(screenW * 0.78f, 300.dp)
    }
    return (maxCapsule - 6.dp - coverSize - 8.dp - 14.dp).coerceAtLeast(24.dp)
}

private fun queuedDwellMs(
    notice: IslandNotice,
    landscape: Boolean,
    screenW: Dp,
    measurer: TextMeasurer,
    density: Density,
): Long {
    val textPx = measurer.measure(
        text = notice.message,
        style = islandTextStyle(landscape),
        maxLines = 1,
    ).size.width
    val textWant = with(density) { textPx.toDp() }
    val overflow = textWant - islandMaxTextSlot(landscape, screenW)
    if (overflow <= 1.dp) return IslandFitDwellMs
    val scrollMs = ((overflow / IslandMarqueeVelocity) * 1_000f).toLong()
    return maxOf(IslandFitDwellMs, IslandMarqueeHoldMs + scrollMs + IslandMarqueeEndRestMs)
}
