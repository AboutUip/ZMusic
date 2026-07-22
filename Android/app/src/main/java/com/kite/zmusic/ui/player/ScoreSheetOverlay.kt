package com.kite.zmusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
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

/**
 * @param coverExpanded 仅驱动标题箭头朝向；列数由 [coverExpandProgress] 在 2↔4 间连续插值
 * @param coverExpandProgress 0=两列收起，1=四列展开；与面板宽度同曲线，卡片位姿同步 morph
 * @param onPlayTrack 点选：带回卡片黑胶根坐标，供飞入动画
 */
@Composable
fun ScoreSheetOverlay(
    coverExpanded: Boolean,
    coverExpandProgress: Float,
    onToggleCoverExpand: () -> Unit,
    tracks: List<TrackRow>,
    currentIndex: Int,
    plateColors: VinylPlateColors,
    onPlayTrack: (index: Int, vinylCenterRoot: Offset, vinylSizePx: Float) -> Unit,
    openGeneration: Int,
    modifier: Modifier = Modifier,
) {
    val expandT = coverExpandProgress.coerceIn(0f, 1f)
    val gridGap = androidx.compose.ui.unit.lerp(10.dp, 8.dp, expandT)
    val initialIndex = remember(openGeneration, tracks.size) {
        if (tracks.isEmpty()) 0
        else currentIndex.coerceIn(0, tracks.lastIndex)
    }
    val context = LocalContext.current
    // 打开曲谱即预热整队封面：键为 coverUrl，磁盘优先，保证匹配且少打网
    LaunchedEffect(openGeneration, tracks) {
        UrlImageCache.prefetchAll(
            context = context,
            urls = tracks.map { it.coverUrl },
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
        // 轻微纵向层次，非磨砂
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreExpandChevronButton(
                    expanded = coverExpanded,
                    onClick = onToggleCoverExpand,
                )
                Spacer(Modifier.width(8.dp))
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
                )
            }
            Spacer(Modifier.height(14.dp))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // 与内部音乐卡片圆角一致，避免与组件视觉割裂
                    .clip(CardShape),
            ) {
                key(openGeneration) {
                    ScoreMorphGrid(
                        expandT = expandT,
                        gridGap = gridGap,
                        tracks = tracks,
                        currentIndex = currentIndex,
                        plateColors = plateColors,
                        initialIndex = initialIndex,
                        onPlayTrack = onPlayTrack,
                    )
                }
            }
        }
    }
}

/** 展开 morph 时锁定的滚动锚点：保持某曲在视口中的相对位置不跳 */
private data class ScoreMorphScrollAnchor(
    val index: Int,
    val viewportY: Float,
)

/**
 * 2 列 ↔ 4 列：每张卡片在两端布局位姿间插值，随面板加宽同步 morph，避免瞬间换列重排。
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
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var morphAnchor by remember { mutableStateOf<ScoreMorphScrollAnchor?>(null) }
    val morphing = expandT > 0.001f && expandT < 0.999f

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val gapPx = with(density) { gridGap.toPx() }
        val n = tracks.size
        val cell2 = scoreGridCellPx(columns = 2, widthPx = widthPx, gapPx = gapPx)
        val cell4 = scoreGridCellPx(columns = 4, widthPx = widthPx, gapPx = gapPx)
        val cellPx = lerpFloat(cell2, cell4, expandT)
        val bottomPadPx = with(density) { 6.dp.toPx() }
        val contentH = scoreGridContentHeightPx(
            count = n,
            cell2 = cell2,
            cell4 = cell4,
            gapPx = gapPx,
            t = expandT,
        ) + bottomPadPx
        val maxScroll = (contentH - viewportH).coerceAtLeast(0f)

        fun itemTop(index: Int, t: Float): Float =
            lerpFloat(
                scoreGridItemTop(index, columns = 2, cellPx = cell2, gapPx = gapPx),
                scoreGridItemTop(index, columns = 4, cellPx = cell4, gapPx = gapPx),
                t,
            )

        fun itemLeft(index: Int, t: Float): Float =
            lerpFloat(
                scoreGridItemLeft(index, columns = 2, cellPx = cell2, gapPx = gapPx),
                scoreGridItemLeft(index, columns = 4, cellPx = cell4, gapPx = gapPx),
                t,
            )

        // 首进：滚到当前播放曲（仅 openGeneration 重建时）
        LaunchedEffect(Unit) {
            if (n <= 0) return@LaunchedEffect
            yield()
            val idx = initialIndex.coerceIn(0, n - 1)
            val top = itemTop(idx, expandT)
            scrollState.scrollTo(top.roundToInt().coerceIn(0, maxScroll.roundToInt()))
        }

        // 开始 morph：锁定当前视口锚点曲目
        LaunchedEffect(morphing) {
            if (morphing && n > 0) {
                val scrollY = scrollState.value.toFloat()
                val idx = scoreGridFirstVisibleIndex(
                    scrollY = scrollY,
                    count = n,
                    itemTop = { i -> itemTop(i, expandT) },
                    cellPx = cellPx,
                )
                val top = itemTop(idx, expandT)
                morphAnchor = ScoreMorphScrollAnchor(
                    index = idx,
                    viewportY = top - scrollY,
                )
            } else {
                morphAnchor = null
            }
        }

        // morph 全程按锚点回写 scroll，卡片只平滑挪位不跳页
        LaunchedEffect(expandT, morphing, morphAnchor, maxScroll, cell2, cell4, gapPx) {
            val anchor = morphAnchor
            if (!morphing || anchor == null || n <= 0) return@LaunchedEffect
            val idx = anchor.index.coerceIn(0, n - 1)
            val target = (itemTop(idx, expandT) - anchor.viewportY)
                .coerceIn(0f, maxScroll)
                .roundToInt()
            if (abs(scrollState.value - target) > 0) {
                scrollState.scrollTo(target)
            }
        }

        val scrollY = scrollState.value.toFloat()
        val overscan = cellPx + gapPx
        val visibleIndices = remember(scrollY, viewportH, expandT, n, cellPx, cell2, cell4, gapPx) {
            if (n <= 0) emptyList()
            else buildList {
                for (i in 0 until n) {
                    val top = itemTop(i, expandT)
                    if (top + cellPx >= scrollY - overscan && top <= scrollY + viewportH + overscan) {
                        add(i)
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = !morphing),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { contentH.toDp() }),
            ) {
                for (index in visibleIndices) {
                    val track = tracks[index]
                    val left = itemLeft(index, expandT)
                    val top = itemTop(index, expandT)
                    val sideDp = with(density) { cellPx.toDp() }
                    key(track.id) {
                        ScoreTrackCard(
                            track = track,
                            playing = index == currentIndex,
                            plateColors = plateColors,
                            onClick = { center, sizePx ->
                                onPlayTrack(index, center, sizePx)
                            },
                            modifier = Modifier
                                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                                .size(sideDp),
                        )
                    }
                }
            }
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

@Composable
private fun ScoreTrackCard(
    track: TrackRow,
    playing: Boolean,
    plateColors: VinylPlateColors,
    onClick: (vinylCenterRoot: Offset, vinylSizePx: Float) -> Unit,
    modifier: Modifier = Modifier,
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
                    if (!url.isNullOrBlank()) {
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
) {
    val rot by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "scoreChevronRot",
    )
    Box(
        modifier = Modifier
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
