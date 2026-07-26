package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache

private val QueueLabel = Color(0xFFFFFFFF)
private val QueueAccent = Color(0xFF9AF0F0)
private val QueueHint = Color(0xFFB8C0CC)
private val QueueIconTint = Color(0xFFD5DEE8)
private val QueueRowBg = Color(0xF0141A24)
private val QueueRowSelected = Color(0xFF1A2430)
private val QueueCoverBg = Color(0xFF1C2028)
private val QueuePanelShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

private const val QueuePrefetchBehind = 8
private const val QueuePrefetchAhead = 24

/**
 * 竖屏底栏「曲谱」图标：与设置 / 海报钮同尺寸的细线谱面。
 */
@Composable
fun NowPlayingScoreIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(width = 32.dp, height = 28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TransportScoreIcon(
            size = 15.dp,
            tint = QueueIconTint,
        )
    }
}

/**
 * 竖屏曲谱 / 播放列表面板：玻璃壳、把手、顶圆角与设置面板一致；
 * 内容为一行一首（方封面 + 歌名 / 制作人）。
 */
@Composable
fun PortraitQueueSheet(
    tracks: List<TrackRow>,
    currentIndex: Int,
    onPlayIndex: (Int) -> Unit,
    /** 每次打开递增：滚到当前播放行 */
    revealToken: Int = 0,
    onDragHandleVertical: ((dragAmountPx: Float) -> Unit)? = null,
    onDragHandleEnd: (() -> Unit)? = null,
    panelShape: RoundedCornerShape = QueuePanelShape,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val safeIndex = if (tracks.isEmpty()) {
        0
    } else {
        currentIndex.coerceIn(0, tracks.lastIndex)
    }

    LaunchedEffect(revealToken, tracks.size) {
        if (tracks.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(safeIndex)
    }

    LaunchedEffect(tracks.size, safeIndex) {
        if (tracks.isEmpty()) return@LaunchedEffect
        val from = (safeIndex - QueuePrefetchBehind).coerceAtLeast(0)
        val to = (safeIndex + QueuePrefetchAhead).coerceAtMost(tracks.lastIndex)
        UrlImageCache.prefetchAll(
            context = context,
            urls = tracks.subList(from, to + 1).map { it.coverUrl },
        )
    }

    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(panelShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        // 静态玻璃：与竖屏设置面板同一套，避免弹出卡顿
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xE6080C14),
                            Color(0xF005080E),
                            Color(0xF0020408),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Color(0x3305080E)),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f),
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
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    ),
                    shape = panelShape,
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                onDragHandleVertical?.invoke(dragAmount)
                            },
                            onDragEnd = { onDragHandleEnd?.invoke() },
                            onDragCancel = { onDragHandleEnd?.invoke() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.38f)),
                )
            }
            Text(
                text = "曲谱",
                style = TextStyle(
                    color = QueueLabel,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    letterSpacing = 0.3.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (tracks.isEmpty()) "暂无播放列表" else "共 ${tracks.size} 首",
                style = TextStyle(
                    color = QueueHint.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                ),
            )
            Spacer(Modifier.height(10.dp))

            if (tracks.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "队列为空",
                        style = TextStyle(
                            color = QueueHint.copy(alpha = 0.45f),
                            fontSize = 14.sp,
                        ),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        items = tracks,
                        key = { index, track -> "${track.id}_$index" },
                    ) { index, track ->
                        PortraitQueueTrackRow(
                            track = track,
                            selected = index == safeIndex,
                            onClick = { onPlayIndex(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitQueueTrackRow(
    track: TrackRow,
    selected: Boolean,
    onClick: () -> Unit,
    coverSize: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) QueueRowSelected else QueueRowBg)
            .border(
                width = 1.dp,
                color = if (selected) QueueAccent.copy(alpha = 0.55f)
                else Color.White.copy(alpha = 0.06f),
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(8.dp))
                .background(QueueCoverBg),
        ) {
            UrlImage(
                url = track.coverUrl,
                contentDescription = track.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = TextStyle(
                    color = if (selected) QueueAccent else QueueLabel,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.15.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.artists.ifBlank { "—" },
                style = TextStyle(
                    color = QueueHint.copy(alpha = if (selected) 0.88f else 0.72f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
