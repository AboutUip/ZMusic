package com.kite.zmusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.VinylPlateColors
import com.kite.zmusic.ui.common.UrlImage
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
 * @param coverExpanded 目标列数跟布尔走（展开动画一开始就切 4 列），避免中途跳列失焦
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
    // 跟目标布尔：展开瞬间用 4 列随宽度变宽，收回瞬间用 2 列随宽度变窄 —— 无中途跳列
    val columnCount = if (coverExpanded) 4 else 2
    val gridGap = androidx.compose.ui.unit.lerp(
        10.dp,
        8.dp,
        coverExpandProgress.coerceIn(0f, 1f),
    )
    val initialIndex = remember(openGeneration, tracks.size) {
        if (tracks.isEmpty()) 0
        else currentIndex.coerceIn(0, tracks.lastIndex)
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
                    val gridState = rememberLazyGridState(
                        initialFirstVisibleItemIndex = initialIndex,
                    )
                    // 列数切换时瞬时锚定当前曲，避免失焦跳动
                    LaunchedEffect(columnCount) {
                        if (currentIndex in tracks.indices) {
                            runCatching { gridState.scrollToItem(currentIndex) }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(gridGap),
                        verticalArrangement = Arrangement.spacedBy(gridGap),
                    ) {
                        itemsIndexed(
                            items = tracks,
                            key = { _, t -> t.id },
                        ) { index, track ->
                            ScoreTrackCard(
                                track = track,
                                playing = index == currentIndex,
                                plateColors = plateColors,
                                onClick = { center, sizePx ->
                                    onPlayTrack(index, center, sizePx)
                                },
                                modifier = Modifier.aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    }
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
