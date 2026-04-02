package com.kite.zmusic.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.ui.scifi.SciFiPanelFrame

private val DockCyan = Color(0xFF00FFD1)
private val DockDim = Color(0xFF8FA8B8)

internal val MainDockRailWidth = 52.dp
internal val MainDockPortraitBarHeight = 54.dp
internal val MainDockCompactCircleDp = 40.dp

private val DockWidth = MainDockRailWidth
private val DockHeight = MainDockPortraitBarHeight
private val DockAnimSpec: AnimationSpec<Dp> = tween(durationMillis = 320)
private val DockAlphaSpec: AnimationSpec<Float> = tween(durationMillis = 280)

/**
 * 竖屏：底缘仅收纳键；点按展开 HUD，长按后水平滑动快速切换页。
 * [layoutExpanded] 为 false 时收缩为底部居中半透明圆，占位随之一并缩小。
 */
@Composable
fun MainDockPortrait(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    faceDestination: MainDestination,
    committedDestination: MainDestination,
    onQuickSwitchPreview: (MainDestination?) -> Unit,
    onQuickSwitchCommit: (MainDestination) -> Unit,
    layoutExpanded: Boolean,
    onUserActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fullW = maxWidth
            val targetW = if (layoutExpanded) fullW else MainDockCompactCircleDp
            val targetH = if (layoutExpanded) DockHeight else MainDockCompactCircleDp
            val w by animateDpAsState(targetW, DockAnimSpec, label = "dockPortraitW")
            val h by animateDpAsState(targetH, DockAnimSpec, label = "dockPortraitH")
            Box(
                Modifier
                    .width(w)
                    .height(h)
                    .align(Alignment.Center)
                    .then(
                        if (!layoutExpanded) Modifier.clip(CircleShape) else Modifier,
                    ),
            ) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    DockMenuTrigger(
                        expanded = expanded,
                        layoutExpanded = layoutExpanded,
                        isLandscape = false,
                        faceDestination = faceDestination,
                        committedDestination = committedDestination,
                        onToggle = { onExpandedChange(!expanded) },
                        onQuickSwitchPreview = onQuickSwitchPreview,
                        onQuickSwitchCommit = onQuickSwitchCommit,
                        onUserActivity = onUserActivity,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * 横屏：左侧仅收纳键；[layoutExpanded] 为 false 时轨宽收缩为圆直径，仅保留半透明圆钮。
 */
@Composable
fun MainDockLandscape(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    faceDestination: MainDestination,
    committedDestination: MainDestination,
    onQuickSwitchPreview: (MainDestination?) -> Unit,
    onQuickSwitchCommit: (MainDestination) -> Unit,
    layoutExpanded: Boolean,
    onUserActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val railW by animateDpAsState(
        targetValue = if (layoutExpanded) DockWidth else MainDockCompactCircleDp,
        animationSpec = DockAnimSpec,
        label = "dockRailW",
    )
    // 横屏：左侧整列高度，导航钮在垂直方向居中，避免挤在左上角难辨认
    Box(
        modifier
            .fillMaxHeight()
            .width(railW),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .width(railW),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.then(
                    if (!layoutExpanded) {
                        Modifier
                            .size(MainDockCompactCircleDp)
                            .clip(CircleShape)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(align = Alignment.CenterVertically)
                    },
                ),
            ) {
                DockMenuTriggerVertical(
                    expanded = expanded,
                    layoutExpanded = layoutExpanded,
                    faceDestination = faceDestination,
                    committedDestination = committedDestination,
                    onToggle = { onExpandedChange(!expanded) },
                    onQuickSwitchPreview = onQuickSwitchPreview,
                    onQuickSwitchCommit = onQuickSwitchCommit,
                    onUserActivity = onUserActivity,
                )
            }
        }
    }
}

@Composable
private fun DockMenuTrigger(
    expanded: Boolean,
    layoutExpanded: Boolean,
    isLandscape: Boolean,
    faceDestination: MainDestination,
    committedDestination: MainDestination,
    onToggle: () -> Unit,
    onQuickSwitchPreview: (MainDestination?) -> Unit,
    onQuickSwitchCommit: (MainDestination) -> Unit,
    onUserActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glyphAlpha by animateFloatAsState(
        targetValue = if (layoutExpanded) 0.78f else 0.48f,
        animationSpec = DockAlphaSpec,
        label = "dockGlyphA",
    )
    val glyphSp by animateFloatAsState(
        targetValue = if (layoutExpanded) 14f else 11.5f,
        animationSpec = DockAlphaSpec,
        label = "dockGlyphSp",
    )
    Box(
        modifier
            .dockMenuScrubGestures(
                isLandscape = isLandscape,
                scrubEnabled = !expanded,
                currentDestination = committedDestination,
                stepPerPage = 40.dp,
                onTogglePanel = onToggle,
                onPreviewDestination = onQuickSwitchPreview,
                onCommitDestination = onQuickSwitchCommit,
                onUserInteraction = onUserActivity,
            ),
        contentAlignment = Alignment.Center,
    ) {
        DockTriggerFacePortrait(
            expanded = expanded,
            layoutExpanded = layoutExpanded,
            destination = faceDestination,
            glyphAlpha = glyphAlpha,
            glyphSp = glyphSp,
        )
    }
}

@Composable
private fun DockTriggerFacePortrait(
    expanded: Boolean,
    layoutExpanded: Boolean,
    destination: MainDestination,
    glyphAlpha: Float,
    glyphSp: Float,
) {
    val cyan = DockCyan.copy(alpha = glyphAlpha)
    val dim = DockDim.copy(alpha = glyphAlpha * 0.58f)
    if (expanded) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "▼",
                style = TextStyle(
                    color = cyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (glyphSp + 1f).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "NAV // 导航",
                style = TextStyle(
                    color = dim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    } else {
        Crossfade(
            targetState = destination,
            animationSpec = tween(durationMillis = 200),
            label = "dockDestPortrait",
        ) { dest ->
            if (layoutExpanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = dest.glyph,
                        style = TextStyle(
                            color = cyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (glyphSp + 3f).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "当前 · ${dest.titleZh}",
                            style = TextStyle(
                                color = cyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${dest.shortLabel} · ${dest.blurb}",
                            style = TextStyle(
                                color = dim,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                letterSpacing = 0.4.sp,
                                lineHeight = 11.sp,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    text = dest.glyph,
                    style = TextStyle(
                        color = cyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = glyphSp.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DockMenuTriggerVertical(
    expanded: Boolean,
    layoutExpanded: Boolean,
    faceDestination: MainDestination,
    committedDestination: MainDestination,
    onToggle: () -> Unit,
    onQuickSwitchPreview: (MainDestination?) -> Unit,
    onQuickSwitchCommit: (MainDestination) -> Unit,
    onUserActivity: () -> Unit,
) {
    val glyphAlpha by animateFloatAsState(
        targetValue = if (layoutExpanded) 0.78f else 0.48f,
        animationSpec = DockAlphaSpec,
        label = "dockGlyphAL",
    )
    val glyphSp by animateFloatAsState(
        targetValue = if (layoutExpanded) 13f else 11f,
        animationSpec = DockAlphaSpec,
        label = "dockGlyphSpL",
    )
    Box(
        Modifier
            .then(
                if (layoutExpanded) {
                    Modifier
                        .width(DockWidth)
                        .heightIn(min = 44.dp)
                } else {
                    Modifier.size(MainDockCompactCircleDp)
                },
            )
            .dockMenuScrubGestures(
                isLandscape = true,
                scrubEnabled = !expanded,
                currentDestination = committedDestination,
                stepPerPage = 36.dp,
                onTogglePanel = onToggle,
                onPreviewDestination = onQuickSwitchPreview,
                onCommitDestination = onQuickSwitchCommit,
                onUserInteraction = onUserActivity,
            ),
        contentAlignment = Alignment.Center,
    ) {
        DockTriggerFaceLandscape(
            expanded = expanded,
            layoutExpanded = layoutExpanded,
            destination = faceDestination,
            glyphAlpha = glyphAlpha,
            glyphSp = glyphSp,
        )
    }
}

@Composable
private fun DockTriggerFaceLandscape(
    expanded: Boolean,
    layoutExpanded: Boolean,
    destination: MainDestination,
    glyphAlpha: Float,
    glyphSp: Float,
) {
    val cyan = DockCyan.copy(alpha = glyphAlpha)
    val dim = DockDim.copy(alpha = glyphAlpha * 0.58f)
    if (expanded) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Text(
                text = "◀",
                style = TextStyle(
                    color = cyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (glyphSp + 1f).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "NAV\n导航",
                style = TextStyle(
                    color = dim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 0.8.sp,
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    } else {
        Crossfade(
            targetState = destination,
            animationSpec = tween(durationMillis = 200),
            label = "dockDestLand",
        ) { dest ->
            if (layoutExpanded) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = dest.glyph,
                        style = TextStyle(
                            color = cyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (glyphSp + 2f).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = dest.titleZh,
                        style = TextStyle(
                            color = cyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Clip,
                    )
                    Text(
                        text = dest.shortLabel,
                        style = TextStyle(
                            color = dim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            } else {
                Text(
                    text = dest.glyph,
                    style = TextStyle(
                        color = cyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = glyphSp.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
fun MainDockExpandedOverlay(
    visible: Boolean,
    isLandscape: Boolean,
    destination: MainDestination,
    onDestination: (MainDestination) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible) { onDismiss() }
    AnimatedVisibility(
        visible = visible,
        // 占满全屏；横屏勿叠加 slideInHorizontally，否则与测量/占位组合后面板会卡在左上角
        modifier = modifier
            .fillMaxSize()
            .zIndex(80f),
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(180)),
    ) {
        val scrim = Color(0xFF02060E)
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .background(scrim.copy(alpha = if (isLandscape) 0.88f else 0.90f))
                .drawBehind {
                    val edge = Color(0xFF00FFD1).copy(alpha = 0.06f)
                    if (isLandscape) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to edge,
                                    0.22f to Color.Transparent,
                                    1f to Color.Transparent,
                                ),
                                startX = 0f,
                                endX = size.width,
                            ),
                        )
                    } else {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.5f to Color.Transparent,
                                    1f to scrim.copy(alpha = 0.35f),
                                ),
                                startY = 0f,
                                endY = size.height,
                            ),
                        )
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            val panelModifier = if (isLandscape) {
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = DockWidth + 16.dp, top = 20.dp, bottom = 20.dp, end = 32.dp)
                    .widthIn(max = 300.dp)
                    .fillMaxHeight(0.9f)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = DockHeight + 24.dp)
            }
            SciFiPanelFrame(
                modifier = panelModifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { },
                ),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = "NAV // MODULES",
                        style = TextStyle(
                            color = DockDim.copy(alpha = 0.55f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 2.sp,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    MainDestination.entries.forEach { dest ->
                        val sel = dest == destination
                        val rowInteraction = remember(dest) { MutableInteractionSource() }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = rowInteraction,
                                    indication = null,
                                    onClick = {
                                        onDestination(dest)
                                        onDismiss()
                                    },
                                )
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                text = (if (sel) "› " else "  ") + "${dest.glyph}  ${dest.titleZh}",
                                style = TextStyle(
                                    color = if (sel) DockCyan else DockDim.copy(alpha = 0.82f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    letterSpacing = 0.8.sp,
                                ),
                            )
                            Text(
                                text = dest.blurb,
                                style = TextStyle(
                                    color = DockDim.copy(alpha = 0.45f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.3.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
