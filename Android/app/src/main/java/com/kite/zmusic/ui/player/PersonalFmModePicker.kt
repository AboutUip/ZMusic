package com.kite.zmusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.ChromeGlassMode
import com.kite.zmusic.data.PersonalFmModeChoice
import com.kite.zmusic.data.personalFmModeEntries
import com.kite.zmusic.i18n.I18n
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.main.LocalChromeGlassStyle
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.playerOverlayGlass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private val DropEasing = CubicBezierEasing(0.16f, 1.18f, 0.24f, 1f)
private val CollapseEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
private val PanelH = 252.dp
private val CellH = 56.dp
private val CellShape = RoundedCornerShape(14.dp)
private val ChromeStartRadius = 8.dp
private val GlyphStartRadius = 4.dp
private val ChromeBarBg = Color.Black.copy(alpha = 0.22f)

private fun ramp(t: Float, start: Float, end: Float): Float {
    if (end <= start) return if (t >= end) 1f else 0f
    return ((t - start) / (end - start)).coerceIn(0f, 1f)
}

@Composable
internal fun PersonalFmModePickerOverlay(
    visible: Boolean,
    originBounds: Rect,
    current: PersonalFmModeChoice,
    applying: Boolean,
    onDismiss: () -> Unit,
    onSelect: (PersonalFmModeChoice) -> Unit,
    modifier: Modifier = Modifier,
    haze: HazeState? = null,
    chromeBackground: Boolean = true,
    onCoveredChange: (Boolean) -> Unit = {},
) {
    val reveal = remember { Animatable(0f) }
    var running by remember { mutableStateOf(false) }
    var hostOrigin by remember { mutableStateOf<Offset?>(null) }
    val hostReady = hostOrigin != null
    val coveredCb = rememberUpdatedState(onCoveredChange)
    LaunchedEffect(visible, hostReady) {
        if (visible) {
            if (!hostReady) return@LaunchedEffect
            running = true
            reveal.animateTo(1f, tween(420, easing = DropEasing))
        } else if (running || reveal.value > 0.001f) {
            reveal.animateTo(0f, tween(300, easing = CollapseEasing))
            coveredCb.value(false)
            running = false
        }
    }
    val present = visible || running
    val drawing = present && hostReady && originBounds.width > 1f
    SideEffect {
        if (drawing) coveredCb.value(true)
    }
    if (!present) {
        SideEffect { hostOrigin = null }
        return
    }
    val t = reveal.value
    BackHandler(enabled = visible && !applying) { onDismiss() }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .zIndex(80f)
            .onGloballyPositioned { coords ->
                val b = coords.boundsInWindow()
                hostOrigin = Offset(b.left, b.top)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!applying) onDismiss() },
            ),
    ) {
        val host = hostOrigin ?: return@BoxWithConstraints
        if (originBounds.width <= 1f) return@BoxWithConstraints
        val panelW = maxWidth * 0.94f
        val panelLeft = (maxWidth - panelW) / 2f
        val panelTop = 56.dp
        val startLeft = with(density) { (originBounds.left - host.x).toDp() }
        val startTop = with(density) { (originBounds.top - host.y).toDp() }
        val startW = with(density) { originBounds.width.toDp() }.coerceAtLeast(1.dp)
        val startH = with(density) { originBounds.height.toDp() }.coerceAtLeast(1.dp)
        val startRadius = if (chromeBackground) ChromeStartRadius else GlyphStartRadius
        val left = lerp(startLeft, panelLeft, t)
        val top = lerp(startTop, panelTop, t)
        val w = lerp(startW, panelW, t)
        val h = lerp(startH, PanelH, t)
        val radius = lerp(startRadius, 22.dp, t)
        val panelShape = RoundedCornerShape(radius)
        val glassMix = ramp(t, 0.04f, 0.34f)
        val chromeBgA = if (chromeBackground) 1f - glassMix else 0f
        val iconA = 1f - ramp(t, 0.10f, 0.46f)
        val iconScale = 1f + ramp(t, 0f, 0.42f) * 0.22f
        val contentA = ramp(t, 0.36f, 0.78f)
        val glassMode = LocalChromeGlassStyle.current.mode
        // 液态 / 磨砂采的是偏暗的播放页，浅色主题的深色正文会消失。字跟播放页一样用浅色，玻璃样式保留。
        val ink = if (glassMode == ChromeGlassMode.Solid && !MainPalette.isDark) {
            MainPalette.Ink
        } else {
            LyricCurrent
        }
        val glyphSize = if (chromeBackground) 18.dp else 15.dp
        val startCx = originBounds.left + originBounds.width / 2f
        val startCy = originBounds.top + originBounds.height / 2f
        Box(
            Modifier
                .offset {
                    IntOffset(
                        left.roundToPx(),
                        top.roundToPx(),
                    )
                }
                .width(w)
                .height(h)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            if (chromeBgA > 0.02f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = chromeBgA }
                        .clip(panelShape)
                        .background(ChromeBarBg),
                )
            }
            if (glassMix > 0.02f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = glassMix }
                        .playerOverlayGlass(
                            shape = panelShape,
                            haze = haze,
                            liquidLensHeight = 10.7.dp,
                            liquidLensAmount = 21.3.dp,
                        ),
                )
            }
            if (contentA > 0.02f) {
                val entries = remember(I18n.language) { personalFmModeEntries() }
                val gridState = rememberLazyGridState()
                // 仅打开时把当前模式滚进视野；切换选中不要 scrollToItem，否则会强制置顶。
                LaunchedEffect(visible) {
                    if (!visible) return@LaunchedEffect
                    val idx = entries.indexOfFirst { it.choice == current }
                    if (idx < 0) return@LaunchedEffect
                    snapshotFlow { gridState.layoutInfo.totalItemsCount }
                        .first { it > idx }
                    gridState.scrollToItem(idx)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(panelShape)
                        .graphicsLayer {
                            alpha = contentA
                            clip = true
                            shape = panelShape
                        },
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = !applying,
                ) {
                    items(entries, key = { "${it.choice.mode}:${it.choice.submode.orEmpty()}" }) { entry ->
                        val selected = entry.choice == current
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(CellH)
                                .clip(CellShape)
                                .background(
                                    if (selected) {
                                        Brush.verticalGradient(
                                            listOf(
                                                ink.copy(alpha = 0.18f),
                                                ink.copy(alpha = 0.08f),
                                            ),
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(
                                                ink.copy(alpha = 0.08f),
                                                ink.copy(alpha = 0.08f),
                                            ),
                                        )
                                    },
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 1.5.dp,
                                            color = ink.copy(alpha = 0.55f),
                                            shape = CellShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(
                                    enabled = !applying,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = { onSelect(entry.choice) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = entry.title,
                                    style = TextStyle(
                                        color = ink.copy(alpha = if (selected) 1f else 0.72f),
                                        fontSize = if (selected) 13.5.sp else 12.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                                if (selected) {
                                    Text(
                                        text = t("当前"),
                                        style = TextStyle(
                                            color = ink.copy(alpha = 0.62f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            letterSpacing = 0.4.sp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (iconA > 0.02f) {
                val glyphLeftPx = startCx - host.x - with(density) { glyphSize.toPx() } / 2f
                val glyphTopPx = startCy - host.y - with(density) { glyphSize.toPx() } / 2f
                val panelLeftPx = with(density) { left.toPx() }
                val panelTopPx = with(density) { top.toPx() }
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (glyphLeftPx - panelLeftPx).roundToInt(),
                                (glyphTopPx - panelTopPx).roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            alpha = iconA
                            scaleX = iconScale
                            scaleY = iconScale
                            transformOrigin = TransformOrigin.Center
                        },
                ) {
                    FmModeGlyph(filled = chromeBackground)
                }
            }
        }
    }
}
