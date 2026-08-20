package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.PlayingEqualizer
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.pageSheetHazeStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

private val QueueLabel get() = MainPalette.Ink
private val QueueAccent get() = MainPalette.Accent
private val QueueHint get() = MainPalette.Secondary
private val QueueIconTint get() = MainPalette.Ink
private val QueueCoverBg get() = MainPalette.Placeholder
private val QueuePanelShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

private const val QueuePrefetchBehind = 8
private const val QueuePrefetchAhead = 24

private data class QueueVisibleRow(
    val originalIndex: Int,
    val track: TrackRow,
)

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
 * 竖屏曲谱 / 播放列表面板：浅色磨砂，与评论 / 竖屏显示同一套语言。
 * 行样式对齐歌单内部（无描边、播放中底色 + 均衡器），不含更多按钮。
 */
@Composable
fun PortraitQueueSheet(
    tracks: List<TrackRow>,
    currentIndex: Int,
    onPlayIndex: (Int) -> Unit,
    isPlaying: Boolean = false,
    /** 每次打开递增：滚到当前播放行 */
    revealToken: Int = 0,
    onDragHandleVertical: ((dragAmountPx: Float) -> Unit)? = null,
    onDragHandleEnd: (() -> Unit)? = null,
    onApproachEnd: (lastVisibleIndex: Int) -> Unit = {},
    panelShape: RoundedCornerShape = QueuePanelShape,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    val safeIndex = if (tracks.isEmpty()) {
        0
    } else {
        currentIndex.coerceIn(0, tracks.lastIndex)
    }
    val visibleRows = remember(tracks, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            tracks.mapIndexed { index, track -> QueueVisibleRow(index, track) }
        } else {
            tracks.mapIndexedNotNull { index, track ->
                if (track.matchesQueueQuery(needle)) {
                    QueueVisibleRow(index, track)
                } else {
                    null
                }
            }
        }
    }
    val searching = query.trim().isNotEmpty()

    val onApproachUpdated = rememberUpdatedState(onApproachEnd)
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = tracks.size
            total > 1 && last >= total - 8
        }
    }
    LaunchedEffect(nearEnd, tracks.size, searching) {
        if (tracks.isEmpty()) return@LaunchedEffect
        if (searching) {
            onApproachUpdated.value(tracks.lastIndex)
            return@LaunchedEffect
        }
        if (nearEnd) {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: tracks.lastIndex
            onApproachUpdated.value(last)
        }
    }

    LaunchedEffect(revealToken) {
        query = ""
        keyboard?.hide()
        focus.clearFocus(force = true)
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
        if (hazeState != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState, style = pageSheetHazeStyle()),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MainPalette.Page.copy(alpha = 0.96f)),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(MainPalette.SheetWash),
        )

        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
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
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MainPalette.Hint),
                )
            }
            Text(
                text = "曲谱",
                style = TextStyle(
                    color = QueueLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.2).sp,
                ),
            )
            Spacer(Modifier.height(10.dp))
            PortraitQueueSearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = {
                    keyboard?.hide()
                    focus.clearFocus()
                },
                onClear = {
                    query = ""
                    focus.clearFocus()
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    tracks.isEmpty() -> "暂无播放列表"
                    searching && visibleRows.isEmpty() -> "没有找到相关歌曲"
                    searching -> "找到 ${visibleRows.size} 首"
                    else -> "共 ${tracks.size} 首"
                },
                style = TextStyle(
                    color = QueueHint,
                    fontSize = 13.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))

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
                            color = QueueHint,
                            fontSize = 14.sp,
                        ),
                    )
                }
            } else if (searching && visibleRows.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "换个关键词试试",
                        style = TextStyle(
                            color = QueueHint,
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
                ) {
                    itemsIndexed(
                        items = visibleRows,
                        key = { _, row -> "${row.track.id}_${row.originalIndex}" },
                    ) { _, row ->
                        PortraitQueueTrackRow(
                            index = row.originalIndex + 1,
                            track = row.track,
                            current = row.originalIndex == safeIndex,
                            playing = isPlaying,
                            onClick = { onPlayIndex(row.originalIndex) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitQueueSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(MainPalette.Placeholder)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Search,
            contentDescription = null,
            tint = MainPalette.Secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(MainPalette.Accent),
            textStyle = TextStyle(color = MainPalette.Ink, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "搜索歌名、歌手或专辑",
                        style = TextStyle(color = MainPalette.Hint, fontSize = 15.sp),
                    )
                }
                inner()
            },
        )
        if (value.isNotEmpty()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClear,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Close,
                    contentDescription = "清空",
                    tint = MainPalette.Secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PortraitQueueTrackRow(
    index: Int,
    track: TrackRow,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val rowClick = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (current) MainPalette.Accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(
                interactionSource = rowClick,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (current) {
                PlayingEqualizer(
                    playing = playing,
                    color = MainPalette.Accent,
                    modifier = Modifier.size(16.dp, 14.dp),
                )
            } else {
                Text(
                    text = index.toString(),
                    color = MainPalette.Hint,
                    fontSize = 13.sp,
                )
            }
        }
        Box(
            Modifier
                .size(48.dp)
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = if (current) QueueAccent else QueueLabel,
                    fontSize = 15.sp,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
            Text(
                text = track.artists.ifBlank { "—" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = if (current) QueueAccent.copy(alpha = 0.72f) else QueueHint,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

private fun TrackRow.matchesQueueQuery(needle: String): Boolean {
    if (name.contains(needle, ignoreCase = true)) return true
    if (artists.contains(needle, ignoreCase = true)) return true
    val albumName = album
    return !albumName.isNullOrBlank() && albumName.contains(needle, ignoreCase = true)
}
