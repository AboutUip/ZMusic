package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.VinylPlateColors
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

private val LabelColor = Color(0xFFFFFFFF)
private val Accent = Color(0xFF9AF0F0)
private val IconTint = Color(0xFFD5DEE8)
private val HintColor = Color(0xFFE8F0F8)
private val PanelShape = RoundedCornerShape(18.dp)
private val CoverShape = RoundedCornerShape(8.dp)
private val CardShape = RoundedCornerShape(10.dp)
private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 10f)
/** 1:1 音乐卡片底 */
private val CardBg = Color(0xFF0C121C)
/** 曲谱弹窗底：深色实底（展开/收起一致，不再使用磨砂） */
private val PanelBg = Color(0xF2080C14)
/** 切列遮挡专用：完全不透明，避免透出网格重组卡顿帧 */
private val BlockerBg = Color(0xFF080C14)

/** 打开/滚动时预热当前附近封面，避免大歌单整队解码 */
private const val ScorePrefetchBehind = 12
private const val ScorePrefetchAhead = 36

/**
 * @param coverExpanded 目标展开态（箭头朝向）
 * @param onCommitCoverWidth 遮挡后提交面板宽度（suspend，应带动画）；切列在其完成后再做
 * @param onPlayTrack 点选：带回卡片黑胶根坐标，供飞入动画
 *
 * 列数切换不播网格 morph：不透明唱机遮挡下离散切换 2↔4 列；宽度仍平滑动画。
 */
@Composable
fun ScoreSheetOverlay(
    coverExpanded: Boolean,
    onToggleCoverExpand: () -> Unit,
    onCommitCoverWidth: suspend (expanded: Boolean) -> Unit,
    tracks: List<TrackRow>,
    currentIndex: Int,
    plateColors: VinylPlateColors,
    onPlayTrack: (index: Int, vinylCenterRoot: Offset, vinylSizePx: Float) -> Unit,
    openGeneration: Int,
    modifier: Modifier = Modifier,
) {
    // 网格实际列数：仅在实底遮挡下跳变
    var gridExpanded by remember(openGeneration) { mutableStateOf(coverExpanded) }
    var transitionBusy by remember { mutableStateOf(false) }
    var showTurntable by remember { mutableStateOf(false) }
    val chevronT = remember { Animatable(if (coverExpanded) 1f else 0f) }

    LaunchedEffect(coverExpanded) {
        chevronT.animateTo(
            targetValue = if (coverExpanded) 1f else 0f,
            animationSpec = tween(280, easing = FastOutSlowInEasing),
        )
    }

    LaunchedEffect(coverExpanded, openGeneration) {
        if (coverExpanded == gridExpanded) {
            showTurntable = false
            transitionBusy = false
            return@LaunchedEffect
        }
        transitionBusy = true
        // ① 实底挡住 → ② 宽度动画 → ③ 切列 → ④ 布局就绪立刻揭开（不等 loading 演完）
        showTurntable = true
        withFrameNanos { }
        withFrameNanos { }
        onCommitCoverWidth(coverExpanded)
        gridExpanded = coverExpanded
        // 等网格完成至少两帧布局/绘制后再揭开
        withFrameNanos { }
        withFrameNanos { }
        withFrameNanos { }
        showTurntable = false
        transitionBusy = false
    }

    val layoutT = if (gridExpanded) 1f else 0f
    val gridGap = androidx.compose.ui.unit.lerp(10.dp, 8.dp, layoutT)
    val headerT = chevronT.value
    val initialIndex = remember(openGeneration, tracks.size) {
        if (tracks.isEmpty()) 0
        else currentIndex.coerceIn(0, tracks.lastIndex)
    }
    val context = LocalContext.current
    LaunchedEffect(openGeneration, tracks.size, currentIndex) {
        if (tracks.isEmpty()) return@LaunchedEffect
        val center = currentIndex.coerceIn(0, tracks.lastIndex)
        val from = (center - ScorePrefetchBehind).coerceAtLeast(0)
        val to = (center + ScorePrefetchAhead).coerceAtMost(tracks.lastIndex)
        UrlImageCache.prefetchAll(
            context = context,
            urls = tracks.subList(from, to + 1).map { it.coverUrl },
        )
    }

    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(PanelShape)
            .background(PanelBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.28f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f),
                            Color.White.copy(alpha = 0.06f),
                        ),
                    ),
                    shape = PanelShape,
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = "SCORE",
                style = TextStyle(
                    color = Accent.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    shadow = TextShadow,
                ),
            )
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp),
            ) {
                val chevronSize = 28.dp
                val titleGap = 8.dp
                val chevronX = androidx.compose.ui.unit.lerp(
                    maxWidth - chevronSize,
                    0.dp,
                    headerT,
                )
                val titleStartPad = androidx.compose.ui.unit.lerp(
                    0.dp,
                    chevronSize + titleGap,
                    headerT,
                )
                Text(
                    text = "曲谱",
                    style = TextStyle(
                        color = LabelColor,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        letterSpacing = 0.3.sp,
                        shadow = TextShadow,
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = titleStartPad),
                )
                ScoreExpandChevronButton(
                    expanded = coverExpanded,
                    onClick = {
                        if (!transitionBusy) onToggleCoverExpand()
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = chevronX),
                )
            }
            Spacer(Modifier.height(14.dp))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(CardShape),
            ) {
                key(openGeneration) {
                    ScoreMorphGrid(
                        expandT = layoutT,
                        gridGap = gridGap,
                        tracks = tracks,
                        currentIndex = currentIndex,
                        plateColors = plateColors,
                        initialIndex = initialIndex,
                        onPlayTrack = onPlayTrack,
                        interactionEnabled = !transitionBusy,
                    )
                }
            }
        }

        // 不透明整页遮挡 + 精致曲谱 loading；盖住切宽/切列重组
        if (showTurntable) {
            ScoreExpandLoadingBlocker(
                expanding = coverExpanded,
                plateColors = plateColors,
                modifier = Modifier
                    .matchParentSize()
                    .clip(PanelShape),
            )
        }
    }
}

/**
 * 2 列 / 4 列网格（离散布局，不做连续 morph）。
 *
 * 只用视口大小的容器 + 绝对定位可见项，**禁止** `height(整表内容高)`。
 */
@Composable
private fun ScoreMorphGrid(
    expandT: Float,
    gridGap: androidx.compose.ui.unit.Dp,
    tracks: List<TrackRow>,
    currentIndex: Int,
    plateColors: VinylPlateColors,
    initialIndex: Int,
    onPlayTrack: (index: Int, vinylCenterRoot: Offset, vinylSizePx: Float) -> Unit,
    interactionEnabled: Boolean = true,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    var scrollY by remember { mutableFloatStateOf(0f) }
    /**
     * 上一帧网格几何。加宽过程中 cell 会变、切列时 columns 会变；
     * 必须用「变之前」的几何反推锚点，再用新几何写回 scrollY，避免锚点漂移。
     */
    val layoutMetrics = remember {
        object {
            var ready = false
            var columns = 2
            var cellPx = 1f
            var gapPx = 0f
        }
    }
    // 归一成端点，避免浮点中间态
    val layoutT = if (expandT >= 0.5f) 1f else 0f
    val columns = if (layoutT >= 0.5f) 4 else 2

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val gapPx = with(density) { gridGap.toPx() }
        val n = tracks.size
        val cell2 = scoreGridCellPx(columns = 2, widthPx = widthPx, gapPx = gapPx)
        val cell4 = scoreGridCellPx(columns = 4, widthPx = widthPx, gapPx = gapPx)
        val cellPx = if (columns == 4) cell4 else cell2
        val bottomPadPx = with(density) { 6.dp.toPx() }
        val contentH = scoreGridContentHeightPx(
            count = n,
            cell2 = cell2,
            cell4 = cell4,
            gapPx = gapPx,
            t = layoutT,
        ) + bottomPadPx
        val maxScroll = (contentH - viewportH).coerceAtLeast(0f)

        fun itemTop(index: Int, cols: Int, cell: Float, gap: Float): Float =
            scoreGridItemTop(index, columns = cols, cellPx = cell, gapPx = gap)

        fun itemTopNow(index: Int): Float = itemTop(index, columns, cellPx, gapPx)

        fun itemLeft(index: Int): Float =
            scoreGridItemLeft(index, columns = columns, cellPx = cellPx, gapPx = gapPx)

        // 宽度动画或 2↔4 切列：按上一帧几何锁锚点，再映射到当前几何
        if (!layoutMetrics.ready) {
            layoutMetrics.ready = true
            layoutMetrics.columns = columns
            layoutMetrics.cellPx = cellPx
            layoutMetrics.gapPx = gapPx
        } else {
            val geomChanged =
                layoutMetrics.columns != columns ||
                    abs(layoutMetrics.cellPx - cellPx) > 0.5f ||
                    abs(layoutMetrics.gapPx - gapPx) > 0.5f
            if (geomChanged && n > 0 && layoutMetrics.cellPx > 0.5f) {
                val oldCols = layoutMetrics.columns
                val oldCell = layoutMetrics.cellPx
                val oldGap = layoutMetrics.gapPx
                fun oldTop(i: Int) = itemTop(i, oldCols, oldCell, oldGap)

                val firstIdx = scoreGridFirstVisibleIndex(
                    scrollY = scrollY,
                    count = n,
                    itemTop = { i -> oldTop(i) },
                    cellPx = oldCell,
                )
                // 正在播的曲若仍在旧视口内，优先钉住它（观感更稳）
                val viewBottom = scrollY + viewportH
                val playingVisible = currentIndex in 0 until n &&
                    oldTop(currentIndex) < viewBottom - 0.5f &&
                    oldTop(currentIndex) + oldCell > scrollY + 0.5f
                val anchorIdx = if (playingVisible) currentIndex else firstIdx
                val viewportY = oldTop(anchorIdx) - scrollY
                scrollY = (itemTopNow(anchorIdx) - viewportY).coerceIn(0f, maxScroll)
            }
            layoutMetrics.columns = columns
            layoutMetrics.cellPx = cellPx
            layoutMetrics.gapPx = gapPx
        }

        val effectiveScrollY = scrollY.coerceIn(0f, maxScroll)

        LaunchedEffect(maxScroll) {
            if (scrollY > maxScroll) scrollY = maxScroll
        }

        LaunchedEffect(Unit) {
            if (n <= 0) return@LaunchedEffect
            yield()
            val idx = initialIndex.coerceIn(0, n - 1)
            scrollY = itemTopNow(idx).coerceIn(0f, maxScroll)
            layoutMetrics.ready = true
            layoutMetrics.columns = columns
            layoutMetrics.cellPx = cellPx
            layoutMetrics.gapPx = gapPx
        }

        val scrollableState = rememberScrollableState { delta ->
            if (!interactionEnabled) return@rememberScrollableState 0f
            val old = scrollY
            scrollY = (old - delta).coerceIn(0f, maxScroll)
            old - scrollY
        }

        val overscan = cellPx + gapPx
        val visibleIndices = remember(
            effectiveScrollY,
            viewportH,
            columns,
            n,
            cellPx,
            gapPx,
            overscan,
        ) {
            scoreGridVisibleInFixedColumns(
                columns = columns,
                cellPx = cellPx,
                gapPx = gapPx,
                scrollY = effectiveScrollY,
                viewportH = viewportH,
                overscan = overscan,
                count = n,
            )
        }

        val prefetchAnchor = remember(effectiveScrollY, n, cellPx, columns, gapPx) {
            if (n <= 0 || cellPx < 1f) -1
            else scoreGridFirstVisibleIndex(
                scrollY = effectiveScrollY,
                count = n,
                itemTop = { i -> itemTopNow(i) },
                cellPx = cellPx,
            ) / 12
        }
        LaunchedEffect(prefetchAnchor, n) {
            if (prefetchAnchor < 0 || n <= 0) return@LaunchedEffect
            val approxIndex = (prefetchAnchor * 12).coerceIn(0, n - 1)
            val from = (approxIndex - ScorePrefetchBehind).coerceAtLeast(0)
            val to = (approxIndex + ScorePrefetchAhead).coerceAtMost(n - 1)
            UrlImageCache.prefetchAll(
                context = context,
                urls = tracks.subList(from, to + 1).map { it.coverUrl },
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .scrollable(
                    state = scrollableState,
                    orientation = Orientation.Vertical,
                    enabled = interactionEnabled && maxScroll > 0f,
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                ),
        ) {
            val sideDp = with(density) { cellPx.toDp() }
            for (index in visibleIndices) {
                val track = tracks[index]
                val left = itemLeft(index)
                val topInViewport = itemTopNow(index) - effectiveScrollY
                key(track.id) {
                    ScoreTrackCard(
                        track = track,
                        playing = index == currentIndex,
                        plateColors = plateColors,
                        showCover = true,
                        onClick = { center, sizePx ->
                            onPlayTrack(index, center, sizePx)
                        },
                        modifier = Modifier
                            .offset {
                                IntOffset(left.roundToInt(), topInViewport.roundToInt())
                            }
                            .size(sideDp),
                    )
                }
            }
        }
    }
}

/**
 * 曲谱展开/收起 loading：舞台式黑胶 + 谱线氛围（非玩具唱机壳）。
 * 盘在实底之上保持清晰；遮挡仅用于切列重组。
 */
@Composable
private fun ScoreExpandLoadingBlocker(
    expanding: Boolean,
    plateColors: VinylPlateColors,
    modifier: Modifier = Modifier,
) {
    val spin = rememberInfiniteTransition(label = "scoreExpandLoad")
    val vinylRot by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scoreLoadVinylRot",
    )
    val sweep by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scoreLoadSweep",
    )
    val staveBreath by spin.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scoreLoadStave",
    )
    val glowPulse by spin.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scoreLoadGlow",
    )
    val enterT = remember { Animatable(0f) }
    val armT = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enterT.snapTo(0f)
        armT.snapTo(0f)
        enterT.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        armT.animateTo(1f, tween(640, easing = FastOutSlowInEasing))
    }
    val enter = enterT.value
    val arm = armT.value
    val caption = if (expanding) "展开曲谱" else "收起曲谱"

    Box(
        modifier
            .background(BlockerBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 舞台光晕
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 0.55f + 0.45f * enter }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Accent.copy(alpha = 0.07f * glowPulse),
                            Color(0xFF122030).copy(alpha = 0.35f),
                            BlockerBg,
                        ),
                    ),
                ),
        )

        // 谱线氛围（曲谱语义）
        Canvas(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = enter * staveBreath },
        ) {
            val midY = size.height * 0.42f
            val lineGap = size.minDimension * 0.028f
            val inset = size.width * 0.12f
            for (i in -2..2) {
                val y = midY + i * lineGap
                drawLine(
                    color = HintColor.copy(alpha = 0.07f + abs(i) * 0.012f),
                    start = Offset(inset, y),
                    end = Offset(size.width - inset, y),
                    strokeWidth = 1.1f,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    alpha = 0.35f + 0.65f * enter
                    scaleX = 0.92f + 0.08f * enter
                    scaleY = 0.92f + 0.08f * enter
                    translationY = (1f - enter) * 14f
                },
        ) {
            Text(
                text = "SCORE",
                style = TextStyle(
                    color = Accent.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 3.2.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(Modifier.height(18.dp))

            Box(
                Modifier.size(168.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension * 0.48f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = r,
                        center = c,
                        style = Stroke(width = 1.6f),
                    )
                    drawArc(
                        color = Accent.copy(alpha = 0.55f + 0.25f * glowPulse),
                        startAngle = sweep - 90f,
                        sweepAngle = 72f,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                        style = Stroke(width = 2.4f, cap = StrokeCap.Round),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = r * 0.88f,
                        center = c,
                        style = Stroke(width = 1.1f),
                    )
                }

                Box(
                    Modifier
                        .size(142.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF2A3448),
                                    Color(0xFF121820),
                                    Color(0xFF070A10),
                                ),
                            ),
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.04f),
                                ),
                            ),
                            shape = CircleShape,
                        ),
                )

                VinylDiscPlate(
                    modifier = Modifier
                        .size(122.dp)
                        .graphicsLayer {
                            rotationZ = vinylRot
                            transformOrigin = TransformOrigin.Center
                        },
                    colors = plateColors,
                )

                Canvas(
                    Modifier
                        .size(122.dp)
                        .graphicsLayer {
                            rotationZ = vinylRot
                            transformOrigin = TransformOrigin.Center
                        },
                ) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                            start = Offset(c.x - r * 0.2f, c.y - r),
                            end = Offset(c.x + r, c.y + r * 0.3f),
                        ),
                        radius = r * 0.98f,
                        center = c,
                    )
                }

                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFF4F7FA), Color(0xFF8A96A6)),
                            ),
                        )
                        .border(1.dp, Color(0xFF4A5666).copy(alpha = 0.55f), CircleShape),
                )

                Canvas(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-2).dp)
                        .size(78.dp),
                ) {
                    val pivot = Offset(size.width * 0.82f, size.height * 0.18f)
                    val rest = -48f
                    val play = 28f
                    val deg = rest + (play - rest) * arm
                    val rad = Math.toRadians(deg.toDouble()).toFloat()
                    val len = size.minDimension * 0.78f
                    val tip = Offset(pivot.x + cos(rad) * len, pivot.y + sin(rad) * len)
                    val back = Math.toRadians((deg + 180f).toDouble()).toFloat()
                    val counter = Offset(
                        pivot.x + cos(back) * len * 0.22f,
                        pivot.y + sin(back) * len * 0.22f,
                    )
                    drawLine(
                        color = Color.Black.copy(alpha = 0.35f),
                        start = Offset(counter.x + 1.5f, counter.y + 2f),
                        end = Offset(tip.x + 1.5f, tip.y + 2f),
                        strokeWidth = size.minDimension * 0.038f,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = Color(0xFF1A222E),
                        radius = size.minDimension * 0.09f,
                        center = pivot,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFE8EEF4), Color(0xFF7A8694)),
                            center = pivot,
                            radius = size.minDimension * 0.055f,
                        ),
                        radius = size.minDimension * 0.055f,
                        center = pivot,
                    )
                    drawCircle(
                        color = Color(0xFFC5CED8),
                        radius = size.minDimension * 0.048f,
                        center = counter,
                    )
                    drawLine(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFEEF3F8), Color(0xFF9AA6B4)),
                            start = counter,
                            end = tip,
                        ),
                        start = counter,
                        end = tip,
                        strokeWidth = size.minDimension * 0.032f,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = Color(0xFF151C26),
                        radius = size.minDimension * 0.048f,
                        center = tip,
                    )
                    drawCircle(
                        color = Accent.copy(alpha = 0.35f + 0.55f * arm),
                        radius = size.minDimension * 0.02f,
                        center = tip,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = caption,
                style = TextStyle(
                    color = HintColor.copy(alpha = 0.78f),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    letterSpacing = 1.2.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (expanding) "加宽编排中" else "收束编排中",
                style = TextStyle(
                    color = HintColor.copy(alpha = 0.38f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                ),
            )
        }
    }
}

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun scoreGridCellPx(columns: Int, widthPx: Float, gapPx: Float): Float {
    val gaps = (columns - 1).coerceAtLeast(0) * gapPx
    return ((widthPx - gaps) / columns.toFloat()).coerceAtLeast(1f)
}

private fun scoreGridItemLeft(index: Int, columns: Int, cellPx: Float, gapPx: Float): Float {
    val col = index % columns
    return col * (cellPx + gapPx)
}

private fun scoreGridItemTop(index: Int, columns: Int, cellPx: Float, gapPx: Float): Float {
    val row = index / columns
    return row * (cellPx + gapPx)
}

private fun scoreGridContentHeightPx(
    count: Int,
    cell2: Float,
    cell4: Float,
    gapPx: Float,
    t: Float,
): Float {
    if (count <= 0) return 0f
    val rows2 = (count + 1) / 2
    val rows4 = (count + 3) / 4
    val h2 = rows2 * cell2 + (rows2 - 1).coerceAtLeast(0) * gapPx
    val h4 = rows4 * cell4 + (rows4 - 1).coerceAtLeast(0) * gapPx
    return lerpFloat(h2, h4, t)
}

private fun scoreGridFirstVisibleIndex(
    scrollY: Float,
    count: Int,
    itemTop: (Int) -> Float,
    cellPx: Float,
): Int {
    if (count <= 0) return 0
    for (i in 0 until count) {
        val top = itemTop(i)
        if (top + cellPx > scrollY + 0.5f) return i
    }
    return count - 1
}

/**
 * 静止态按行推算可见区间；morph 中取 2 列与 4 列可见并集（加大 overscan），
 * 避免 O(n) 全表扫描。
 */
private fun scoreGridVisibleIndices(
    count: Int,
    expandT: Float,
    cell2: Float,
    cell4: Float,
    gapPx: Float,
    scrollY: Float,
    viewportH: Float,
    overscan: Float,
): List<Int> {
    if (count <= 0) return emptyList()
    if (expandT <= 0.001f) {
        return scoreGridVisibleInFixedColumns(
            columns = 2,
            cellPx = cell2,
            gapPx = gapPx,
            scrollY = scrollY,
            viewportH = viewportH,
            overscan = overscan,
            count = count,
        )
    }
    if (expandT >= 0.999f) {
        return scoreGridVisibleInFixedColumns(
            columns = 4,
            cellPx = cell4,
            gapPx = gapPx,
            scrollY = scrollY,
            viewportH = viewportH,
            overscan = overscan,
            count = count,
        )
    }
    val a = scoreGridVisibleInFixedColumns(
        columns = 2,
        cellPx = cell2,
        gapPx = gapPx,
        scrollY = scrollY,
        viewportH = viewportH,
        overscan = overscan,
        count = count,
    )
    val b = scoreGridVisibleInFixedColumns(
        columns = 4,
        cellPx = cell4,
        gapPx = gapPx,
        scrollY = scrollY,
        viewportH = viewportH,
        overscan = overscan,
        count = count,
    )
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    // 有序归并去重
    return buildList(a.size + b.size) {
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i >= a.size -> add(b[j++])
                j >= b.size -> add(a[i++])
                a[i] < b[j] -> add(a[i++])
                b[j] < a[i] -> add(b[j++])
                else -> {
                    add(a[i])
                    i++
                    j++
                }
            }
        }
    }
}

private fun scoreGridVisibleInFixedColumns(
    columns: Int,
    cellPx: Float,
    gapPx: Float,
    scrollY: Float,
    viewportH: Float,
    overscan: Float,
    count: Int,
): List<Int> {
    val stride = cellPx + gapPx
    if (stride < 1f) return (0 until count).toList()
    val firstRow = ((scrollY - overscan) / stride).toInt().coerceAtLeast(0)
    val lastRow = ((scrollY + viewportH + overscan) / stride).toInt().coerceAtLeast(firstRow)
    val start = (firstRow * columns).coerceIn(0, count - 1)
    val endExclusive = ((lastRow + 1) * columns).coerceIn(0, count)
    if (start >= endExclusive) return emptyList()
    return (start until endExclusive).toList()
}

@Composable
private fun ScoreTrackCard(
    track: TrackRow,
    playing: Boolean,
    plateColors: VinylPlateColors,
    onClick: (vinylCenterRoot: Offset, vinylSizePx: Float) -> Unit,
    modifier: Modifier = Modifier,
    showCover: Boolean = true,
) {
    var vinylCoords by remember {
        mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null)
    }

    Column(
        modifier
            .clip(CardShape)
            .background(CardBg)
            .then(
                if (playing) {
                    Modifier.border(1.dp, Accent.copy(alpha = 0.55f), CardShape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    val c = vinylCoords
                    if (c == null || !c.isAttached) return@clickable
                    val w = c.size.width.toFloat()
                    val h = c.size.height.toFloat()
                    val sizePx = min(w, h)
                    if (sizePx < 8f) return@clickable
                    val center = c.localToRoot(Offset(w / 2f, h / 2f))
                    if (center.x < 1f || center.y < 1f) return@clickable
                    onClick(center, sizePx)
                },
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // 2/3 被封面挡住，仅 1/3 在左侧漏出；黑胶不裁进矩形底
            val vinylReveal = 1f / 3f
            val coverSide = minOf(maxHeight, maxWidth / (1f + 0.85f * vinylReveal))
            val vinylSide = coverSide * 0.85f
            val vinylPeek = vinylSide * vinylReveal
            val mediaW = coverSide + vinylPeek
            Box(
                Modifier.size(width = mediaW, height = coverSide),
                contentAlignment = Alignment.CenterStart,
            ) {
                VinylDiscPlate(
                    modifier = Modifier
                        .size(vinylSide)
                        .align(Alignment.CenterStart)
                        .onGloballyPositioned { vinylCoords = it },
                    colors = plateColors,
                )
                Box(
                    Modifier
                        .size(coverSide)
                        .offset(x = vinylPeek)
                        .align(Alignment.CenterStart)
                        .clip(CoverShape)
                        .background(Color(0xFF121A28)),
                ) {
                    val url = track.coverUrl
                    if (showCover && !url.isNullOrBlank()) {
                        // track.id + url 双键：复用格子时也不会短暂串图
                        key(track.id, url) {
                            UrlImage(
                                url = url,
                                contentDescription = track.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = track.name.ifBlank { "未知歌曲" },
            style = TextStyle(
                color = if (playing) Accent else LabelColor,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                shadow = TextShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = track.artists.ifBlank { "未知艺人" },
            style = TextStyle(
                color = HintColor.copy(alpha = 0.62f),
                fontFamily = FontFamily.SansSerif,
                fontSize = 9.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ScoreExpandChevronButton(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rot by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "scoreChevronRot",
    )
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rot },
        ) {
            val stroke = Stroke(width = size.minDimension * 0.14f, cap = StrokeCap.Round)
            val cx = size.width * 0.38f
            val cy = size.height * 0.5f
            val arm = size.minDimension * 0.42f
            val ang = Math.toRadians(55.0).toFloat()
            val dx = cos(ang) * arm
            val dy = sin(ang) * arm
            drawLine(
                color = IconTint,
                start = Offset(cx, cy),
                end = Offset(cx + dx, cy - dy),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = IconTint,
                start = Offset(cx, cy),
                end = Offset(cx + dx, cy + dy),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}
