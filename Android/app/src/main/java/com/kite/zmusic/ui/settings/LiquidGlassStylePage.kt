package com.kite.zmusic.ui.settings

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.R
import com.kite.zmusic.data.ChromeGlassMode
import com.kite.zmusic.data.ChromeGlassStyle
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.chromeGlassSurface
import com.kite.zmusic.ui.main.wallpaperItemChrome
import com.kite.zmusic.ui.theme.MainSlider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val PreviewShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(percent = 50)
private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun LiquidGlassStylePage(
    style: ChromeGlassStyle,
    applied: ChromeGlassStyle,
    onMode: (ChromeGlassMode) -> Unit,
    onRefraction: (Float) -> Unit,
    onBlur: (Float) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    val t = reveal.value
    val dirty = style != applied
    val applyAlpha by animateFloatAsState(
        targetValue = if (dirty) 1f else 0.40f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "applyEn",
    )
    val refractionEnabled = style.mode == ChromeGlassMode.Liquid
    val blurEnabled = style.mode != ChromeGlassMode.Solid
    val refractionAlpha by animateFloatAsState(
        targetValue = if (refractionEnabled) 1f else 0.38f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "refractionEn",
    )
    val blurAlpha by animateFloatAsState(
        targetValue = if (blurEnabled) 1f else 0.38f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "blurEn",
    )
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (landscape) {
        Row(
            modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, bottom = contentBottomInset + 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FittedGlassPreview(
                style = style,
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .graphicsLayer {
                        alpha = t
                    },
            )
            Spacer(Modifier.width(18.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(4.dp))
                GlassHintCopy(t, compact = true)
                Spacer(Modifier.height(10.dp))
                GlassControls(
                    style = style,
                    reveal = t,
                    refractionEnabled = refractionEnabled,
                    blurEnabled = blurEnabled,
                    refractionAlpha = refractionAlpha,
                    blurAlpha = blurAlpha,
                    onMode = onMode,
                    onRefraction = onRefraction,
                    onBlur = onBlur,
                    compact = true,
                )
                Spacer(Modifier.height(8.dp))
                GlassResetLink(
                    reveal = t,
                    onReset = onReset,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    } else {
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = contentBottomInset + 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            GlassHintCopy(t)
            Spacer(Modifier.height(16.dp))
            FittedGlassPreview(
                style = style,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = t
                        translationY = 14.dp.toPx() * (1f - t)
                    },
            )
            Spacer(Modifier.height(22.dp))
            GlassControls(
                style = style,
                reveal = t,
                refractionEnabled = refractionEnabled,
                blurEnabled = blurEnabled,
                refractionAlpha = refractionAlpha,
                blurAlpha = blurAlpha,
                onMode = onMode,
                onRefraction = onRefraction,
                onBlur = onBlur,
            )
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = t * applyAlpha }
                    .then(
                        if (dirty) {
                            Modifier.clip(CardShape).background(MainPalette.Accent)
                        } else {
                            Modifier.wallpaperItemChrome(CardShape)
                        },
                    )
                    .clickable(
                        enabled = dirty,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onApply,
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (dirty) "应用" else "已是当前样式",
                    style = TextStyle(
                        color = if (dirty) Color.White else MainPalette.Secondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            GlassResetLink(
                reveal = t,
                onReset = onReset,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun GlassHintCopy(reveal: Float, compact: Boolean = false) {
    Text(
        text = if (compact) {
            "拖预览条看折射；磨砂只模糊；纯色不透底。"
        } else {
            "拖一拖预览条，看它怎么盖在画面上。液态会折射；磨砂只做普通模糊；纯色不再透出背景。"
        },
        style = TextStyle(
            color = MainPalette.Secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        maxLines = if (compact) 2 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = reveal
                translationY = 10.dp.toPx() * (1f - reveal)
            }
            .padding(horizontal = 4.dp),
    )
}

@Composable
private fun GlassControls(
    style: ChromeGlassStyle,
    reveal: Float,
    refractionEnabled: Boolean,
    blurEnabled: Boolean,
    refractionAlpha: Float,
    blurAlpha: Float,
    onMode: (ChromeGlassMode) -> Unit,
    onRefraction: (Float) -> Unit,
    onBlur: (Float) -> Unit,
    compact: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
    SettingsHintLabel("模式", reveal, 0.06f)
    GlassModePicker(
        selected = style.mode,
        onSelect = onMode,
        compact = compact,
        modifier = Modifier.graphicsLayer {
            val local = ((reveal - 0.06f) / 0.94f).coerceIn(0f, 1f)
            alpha = local
            translationY = 12.dp.toPx() * (1f - local)
        },
    )
    Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
    SettingsHintLabel("参数", reveal, 0.12f)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val local = ((reveal - 0.12f) / 0.88f).coerceIn(0f, 1f)
                alpha = local
                translationY = 12.dp.toPx() * (1f - local)
            }
            .wallpaperItemChrome(CardShape),
    ) {
        GlassSliderRow(
            title = "折射率",
            valueLabel = ChromeGlassStyle.formatRefraction(style.refraction),
            value = style.refraction,
            valueRange = ChromeGlassStyle.REFRACTION_MIN..ChromeGlassStyle.REFRACTION_MAX,
            enabled = refractionEnabled,
            rowAlpha = refractionAlpha,
            compact = compact,
            onValueChange = onRefraction,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .height(0.5.dp)
                .background(MainPalette.Hairline),
        )
        GlassSliderRow(
            title = "模糊程度",
            valueLabel = ChromeGlassStyle.formatBlurPercent(style.blur),
            value = style.blur,
            valueRange = 0f..1f,
            enabled = blurEnabled,
            rowAlpha = blurAlpha,
            compact = compact,
            onValueChange = onBlur,
        )
    }
    }
}

@Composable
private fun GlassResetLink(
    reveal: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "恢复默认",
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onReset,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer { alpha = reveal },
        style = TextStyle(
            color = MainPalette.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun FittedGlassPreview(
    style: ChromeGlassStyle,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val maxW = maxWidth
        val maxH = maxHeight
        val fitH = maxW * 9f / 16f
        val width = if (maxH == Dp.Infinity || fitH <= maxH) maxW else maxH * 16f / 9f
        val height = width * 9f / 16f
        GlassPreviewCard(
            style = style,
            modifier = Modifier
                .width(width.coerceAtLeast(1.dp))
                .height(height.coerceAtLeast(1.dp)),
        )
    }
}

@Composable
private fun SettingsHintLabel(
    title: String,
    reveal: Float,
    delay: Float,
) {
    val t = ((reveal - delay) / (1f - delay).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
    Text(
        text = title,
        style = TextStyle(
            color = MainPalette.Secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        ),
        modifier = Modifier
            .graphicsLayer {
                alpha = t
                translationY = 8.dp.toPx() * (1f - t)
            }
            .padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun GlassPreviewCard(
    style: ChromeGlassStyle,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberLayerBackdrop()
    val haze = remember { HazeState() }
    var drag by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .clip(PreviewShape)
            .background(Color(0xFFE8E4DC)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(state = haze, zIndex = 0f),
        ) {
            Image(
                painter = painterResource(R.drawable.img_glass_preview),
                contentDescription = "液态玻璃预览画布",
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                contentScale = ContentScale.Crop,
            )
        }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            val next = drag + amount
                            val maxXpx = size.width / 2f - 12.dp.toPx() - (size.width * 0.72f) / 2f
                            val maxYpx = size.height / 2f - 12.dp.toPx() - 28.dp.toPx()
                            val clampX = maxXpx.coerceAtLeast(0f)
                            val clampY = maxYpx.coerceAtLeast(0f)
                            drag = Offset(
                                next.x.coerceIn(-clampX, clampX),
                                next.y.coerceIn(-clampY, clampY),
                            )
                        },
                    )
                },
        ) {
            val chipW = maxWidth * 0.72f
            val chipH = 56.dp
            AnimatedContent(
                targetState = style.mode,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) },
                transitionSpec = {
                    (
                        fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                            )
                        ) togetherWith (
                        fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                            scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                            )
                        )
                },
                label = "glassModeChip",
            ) { mode ->
                val chipStyle = style.copy(mode = mode)
                val lift = mode == ChromeGlassMode.Solid
                key(chipStyle.blur, chipStyle.refraction) {
                Box(
                    Modifier
                        .width(chipW)
                        .height(chipH)
                        .then(
                            if (lift) {
                                Modifier.shadow(
                                    elevation = 10.dp,
                                    shape = ChipShape,
                                    ambientColor = Color.Black.copy(alpha = 0.12f),
                                    spotColor = Color.Black.copy(alpha = 0.10f),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .chromeGlassSurface(
                            backdrop = backdrop,
                            shape = ChipShape,
                            style = chipStyle,
                            haze = haze,
                            liquidBlur = 2.5.dp,
                            liquidLensHeight = 10.7.dp,
                            liquidLensAmount = 21.3.dp,
                            highlightWidth = 0.55.dp,
                            highlightAlpha = 0.38f,
                            surface = MainPalette.glassFill(0.28f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = mode.title,
                            style = TextStyle(
                                color = MainPalette.Ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.2.sp,
                            ),
                        )
                        Text(
                            text = mode.caption,
                            style = TextStyle(
                                color = MainPalette.Secondary,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
                }
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun GlassModePicker(
    selected: ChromeGlassMode,
    onSelect: (ChromeGlassMode) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val modes = ChromeGlassMode.entries
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val indicator = remember { Animatable(selected.ordinal.toFloat()) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(selected) {
        indicator.animateTo(
            targetValue = selected.ordinal.toFloat(),
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        )
    }

    Column(
        modifier
            .fillMaxWidth()
            .wallpaperItemChrome(CardShape)
            .padding(
                horizontal = 14.dp,
                vertical = if (compact) 10.dp else 14.dp,
            ),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MainPalette.TrackOff)
                .pointerInput(modes.size) {
                    val segW = size.width / modes.size.toFloat()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragOffsetPx = 0f
                            scope.launch { indicator.stop() }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffsetPx += dragAmount
                            val live = (selected.ordinal + dragOffsetPx / segW)
                                .coerceIn(0f, modes.lastIndex.toFloat())
                            scope.launch { indicator.snapTo(live) }
                        },
                        onDragEnd = {
                            val idx = (selected.ordinal + dragOffsetPx / segW)
                                .roundToInt()
                                .coerceIn(0, modes.lastIndex)
                            dragOffsetPx = 0f
                            val next = modes[idx]
                            if (next != selected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(next)
                            } else {
                                scope.launch {
                                    indicator.animateTo(
                                        targetValue = selected.ordinal.toFloat(),
                                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            dragOffsetPx = 0f
                            scope.launch {
                                indicator.animateTo(
                                    targetValue = selected.ordinal.toFloat(),
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                                )
                            }
                        },
                    )
                },
        ) {
            val segW = maxWidth / modes.size
            val thumbPad = 3.dp
            Box(
                Modifier
                    .offset {
                        val x = with(density) {
                            (segW * indicator.value + thumbPad).roundToPx()
                        }
                        IntOffset(x, 0)
                    }
                    .padding(vertical = thumbPad)
                    .width(segW - thumbPad * 2)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MainPalette.Surface)
                    .border(
                        width = 1.dp,
                        color = MainPalette.Accent.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(8.dp),
                    ),
            )
            Row(Modifier.fillMaxSize()) {
                modes.forEachIndexed { index, mode ->
                    val active = selected == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (mode != selected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelect(mode)
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.title,
                            style = TextStyle(
                                color = if (active) MainPalette.Accent else MainPalette.Secondary,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 13.sp,
                                letterSpacing = 0.2.sp,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = selected.caption,
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        )
    }
}

@Composable
private fun GlassSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    rowAlpha: Float,
    onValueChange: (Float) -> Unit,
    compact: Boolean = false,
) {
    val sliderIx = remember { MutableInteractionSource() }
    val safeValue = value
        .takeIf { it.isFinite() }
        ?.coerceIn(valueRange.start, valueRange.endInclusive)
        ?: valueRange.start
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = rowAlpha }
            .padding(
                horizontal = 14.dp,
                vertical = if (compact) 2.dp else 8.dp,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = TextStyle(
                    color = if (enabled) MainPalette.Accent else MainPalette.Hint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        MainSlider(
            value = safeValue,
            onValueChange = { next ->
                val clamped = next
                    .takeIf { it.isFinite() }
                    ?.coerceIn(valueRange.start, valueRange.endInclusive)
                    ?: return@MainSlider
                onValueChange(clamped)
            },
            valueRange = valueRange,
            enabled = enabled,
            interactionSource = sliderIx,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
