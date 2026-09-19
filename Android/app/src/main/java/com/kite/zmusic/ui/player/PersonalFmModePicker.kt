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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.kite.zmusic.data.PersonalFmModeChoice
import com.kite.zmusic.data.personalFmModeEntries
import com.kite.zmusic.i18n.I18n
import com.kite.zmusic.i18n.t
import kotlinx.coroutines.flow.first

private val DropEasing = CubicBezierEasing(0.16f, 1.18f, 0.24f, 1f)
private val PanelFill = Color(0xE6161A20)
private val CellIdle = Color(0x14FFFFFF)
private val CellCurrent = Color(0x52F2EDE6)
private val Ink = Color(0xFFF2EDE6)
private val PanelH = 252.dp
private val StartSize = 36.dp
private val CellH = 56.dp

@Composable
internal fun PersonalFmModePickerOverlay(
    visible: Boolean,
    originInWindow: Offset,
    current: PersonalFmModeChoice,
    applying: Boolean,
    onDismiss: () -> Unit,
    onSelect: (PersonalFmModeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(420, easing = DropEasing))
        } else if (mounted) {
            reveal.animateTo(0f, tween(220, easing = CubicBezierEasing(0.4f, 0.02f, 0.2f, 1f)))
            mounted = false
        }
    }
    if (!mounted && reveal.value <= 0.01f) return
    val t = reveal.value
    BackHandler(enabled = visible && !applying) { onDismiss() }
    val density = LocalDensity.current
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .zIndex(80f)
            .onGloballyPositioned { coords ->
                val b = coords.boundsInWindow()
                overlayOrigin = Offset(b.left, b.top)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!applying) onDismiss() },
            ),
    ) {
        val panelW = maxWidth * 0.94f
        val panelLeft = (maxWidth - panelW) / 2f
        val panelTop = 56.dp
        val localOrigin = originInWindow - overlayOrigin
        val startLeft = with(density) { localOrigin.x.toDp() } - StartSize / 2
        val startTop = with(density) { localOrigin.y.toDp() } - StartSize / 2
        val left = lerp(startLeft, panelLeft, t)
        val top = lerp(startTop, panelTop, t)
        val w = lerp(StartSize, panelW, t)
        val h = lerp(StartSize, PanelH, t)
        val radius = lerp(StartSize / 2, 22.dp, t)
        val contentA = ((t - 0.42f) / 0.38f).coerceIn(0f, 1f)
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
                .clip(RoundedCornerShape(radius))
                .background(PanelFill)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            if (contentA > 0.02f) {
                val entries = remember(I18n.language) { personalFmModeEntries() }
                val gridState = rememberLazyGridState()
                LaunchedEffect(visible, current, entries) {
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
                        .padding(10.dp)
                        .graphicsLayer { alpha = contentA },
                    contentPadding = PaddingValues(bottom = 6.dp),
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
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) {
                                        Brush.verticalGradient(
                                            listOf(CellCurrent, Color(0x28F2EDE6)),
                                        )
                                    } else {
                                        Brush.verticalGradient(listOf(CellIdle, CellIdle))
                                    },
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 1.5.dp,
                                            color = Ink.copy(alpha = 0.92f),
                                            shape = RoundedCornerShape(14.dp),
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
                                        color = Ink.copy(alpha = if (selected) 1f else 0.72f),
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
                                            color = Ink.copy(alpha = 0.78f),
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
        }
    }
}
