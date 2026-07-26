package com.kite.zmusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerDisplayPrefs
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val PortraitLyricFallbackDim = Color(0xFF7A8899)
private val PortraitBrowseSelect = Color(0xFFDCE6F0)
private val PortraitSelectSelectedTextFallback = Color(0xFFFFFFFF)

/**
 * 竖屏歌词：LazyColumn 全列表 + 与横屏同套「滚动进入浏览态」。
 * - 跟滚：播放行居中
 * - 首滑进入浏览；浏览中可自由滚、点选 seek
 * - 长按进入选句：全量列表 + 方块多选（无弹窗）
 * - 闲置回跟滚；侧句 Crossfade 刷新
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitCinemaLyrics(
    lines: List<LrcLine>,
    positionMs: Long,
    trackDurationMs: Long,
    playingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    playedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
    playedCount: Int = 2,
    upcomingCount: Int = 2,
    lineSpacingDp: Float = 10f,
    /** 歌词 band 整体垂直偏移（dp），负上正下；band 内播放行仍居中 */
    offsetYDp: Float = 0f,
    contentAlpha: Float = 1f,
    /** 选句进度 0..1：band 铺满、冻结跟滚、方块选中 */
    selectProgress: Float = 0f,
    selectOpen: Boolean = false,
    selectedIndices: Set<Int> = emptySet(),
    onToggleSelect: ((Int) -> Unit)? = null,
    onLongPressLine: ((Int) -> Unit)? = null,
    /** 退出选句后递增：滚回播放行 */
    resumeScrollToken: Int = 0,
    onSeekToMs: (Long) -> Unit,
    onCollapse: () -> Unit,
    onBandCoords: ((LayoutCoordinates) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val contentA = contentAlpha.coerceIn(0f, 1f)
    val offsetYBase = offsetYDp
        .coerceIn(PlayerDisplayPrefs.LYRIC_OFFSET_MIN, PlayerDisplayPrefs.LYRIC_OFFSET_MAX)
    if (lines.isEmpty()) {
        Box(
            modifier
                .fillMaxWidth()
                .offset(y = offsetYBase.dp)
                .graphicsLayer { this.alpha = contentA },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "暂无逐行歌词",
                style = TextStyle(
                    color = PortraitLyricFallbackDim.copy(alpha = 0.38f),
                    fontFamily = FontFamily.SansSerif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        return
    }

    val selectT = selectProgress.coerceIn(0f, 1f)
    val selectMorphing = selectT > 0.001f
    val selectInteractive = selectOpen && selectT > 0.85f
    // 打开意图或进度未归零：全程冻跟滚，避免出场末帧切回浏览态闪一下
    val selectFrozen = selectOpen || selectMorphing

    val timing = lyricAnimTiming(lines, positionMs, trackDurationMs)
    val animActive = lyricAnimActiveIndex(lines, positionMs, trackDurationMs)
    val playFocus = lyricFocusIndex(lines, animActive)
    val live = lyricIsLive(lines, animActive, playFocus)
    val animMs = timing.durationMs
    val emphasis by animateFloatAsState(
        targetValue = if (live && !selectFrozen) 1f else 0f,
        animationSpec = tween(
            durationMillis = (animMs * 1.25f).toInt().coerceIn(280, 520),
            easing = LyricSoftEasing,
        ),
        label = "portraitLyricEmphasis",
    )

    val playedN = playedCount.coerceIn(
        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
        PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX,
    )
    val upcomingN = upcomingCount.coerceIn(
        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
        PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX,
    )
    val visibleCount = (playedN + 1 + upcomingN).coerceAtLeast(1)
    val playFs = playingStyle.sanitizedFontScale()
    val playedFs = playedStyle.sanitizedFontScale()
    val unplayedFs = unplayedStyle.sanitizedFontScale()
    val linePad = lineSpacingDp
        .coerceIn(PlayerDisplayPrefs.LINE_SPACING_MIN, PlayerDisplayPrefs.LINE_SPACING_MAX)
        .dp
    val slotHeight = maxOf(38f * playFs, 26f * playedFs, 26f * unplayedFs).dp + linePad * 2

    val playingColor = playingStyle.resolvedColorFor(LyricStyleRole.Playing)
    val playedColor = playedStyle.resolvedColorFor(LyricStyleRole.Played)
    val unplayedColor = unplayedStyle.resolvedColorFor(LyricStyleRole.Unplayed)
    val selectSelectedText = playingColor.takeIf { it.alpha > 0.01f }
        ?: PortraitSelectSelectedTextFallback
    val selectSelectedBg = lerp(unplayedColor, Color.White, 0.22f).copy(alpha = 0.22f)

    val listState = rememberLazyListState()
    var browsing by remember { mutableStateOf(false) }
    var dragSession by remember { mutableStateOf(false) }
    var idleGen by remember { mutableIntStateOf(0) }
    var suppressBrowseDetect by remember { mutableStateOf(false) }
    var followGen by remember { mutableIntStateOf(0) }
    var selectFrozenFocus by remember { mutableIntStateOf(-1) }
    var selectToggleArmed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val onSeekUpdated by rememberUpdatedState(onSeekToMs)
    val onCollapseUpdated by rememberUpdatedState(onCollapse)
    val onLongPressUpdated by rememberUpdatedState(onLongPressLine)
    val onToggleSelectUpdated by rememberUpdatedState(onToggleSelect)
    val playFocusUpdated by rememberUpdatedState(playFocus)
    val followFling = ScrollableDefaults.flingBehavior()

    LaunchedEffect(selectOpen) {
        if (selectOpen) {
            selectFrozenFocus = playFocusUpdated
            followGen++
            browsing = true
            dragSession = false
            selectToggleArmed = false
        }
    }
    LaunchedEffect(selectT) {
        if (selectT < 0.001f) {
            selectFrozenFocus = -1
            selectToggleArmed = false
        }
    }
    // 打开瞬间吞掉抬手，再允许点选 toggle
    LaunchedEffect(selectOpen, selectInteractive) {
        if (!selectOpen || !selectInteractive) return@LaunchedEffect
        delay(160)
        selectToggleArmed = true
    }

    val visualPlayFocus =
        if (selectFrozen && selectFrozenFocus >= 0) selectFrozenFocus else playFocus

    val slotHeightPx = with(density) { slotHeight.roundToPx() }
    val browseCenterIndex by remember {
        derivedStateOf { listState.browseCenterLyricIndex(visualPlayFocus) }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = contentA }
            .onGloballyPositioned { onBandCoords?.invoke(it) },
    ) {
        val desiredBand = slotHeight * visibleCount
        val normalBand = minOf(desiredBand, maxHeight).coerceAtLeast(slotHeight)
        // 全程按 selectT 插值：无视 around / 垂直偏移（selectT→1），禁止结构突变
        val bandHeight = androidx.compose.ui.unit.lerp(normalBand, maxHeight, selectT)
            .coerceAtLeast(slotHeight)
        val offsetY = (offsetYBase * (1f - selectT)).dp
        // follow 端 pad 锚定 normalBand，避免 band 变高时插值起点跟着跑导致列表跳
        val normalBandPx = with(density) { normalBand.roundToPx() }.coerceAtLeast(1)
        val followCenterPadPx = ((normalBandPx - slotHeightPx) / 2).coerceAtLeast(0)
        val selectEdgePadPx = with(density) { 28.dp.roundToPx() }
        val centerPadPx = lerp(followCenterPadPx.toFloat(), selectEdgePadPx.toFloat(), selectT)
            .roundToInt()
            .coerceAtLeast(0)
        val centerPad = with(density) { centerPadPx.toDp() }
        val hPad = lerp(18f, 12f, selectT).dp

        suspend fun scrollToCenteredIndex(index: Int, animated: Boolean, softSnap: Boolean = false) {
            val gen = followGen
            listState.scrollLyricToCenteredIndex(
                index = index,
                lastIndex = lines.lastIndex,
                slotHeightPx = slotHeightPx,
                animated = animated,
                softSnap = softSnap,
                followGen = gen,
                currentFollowGen = { followGen },
                setSuppressBrowseDetect = { suppressBrowseDetect = it },
            )
        }

        suspend fun snapToFullLines() {
            val gen = followGen
            listState.snapLyricToFullLines(
                slotHeightPx = slotHeightPx,
                lastIndex = lines.lastIndex,
                followGen = gen,
                currentFollowGen = { followGen },
                setSuppressBrowseDetect = { suppressBrowseDetect = it },
            )
        }

        fun exitBrowseAndFollow(animated: Boolean = true) {
            if (selectFrozen) return
            dragSession = false
            browsing = false
            scope.launch { scrollToCenteredIndex(playFocusUpdated, animated) }
        }

        fun hitLyricIndex(localY: Float): Int {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return playFocusUpdated.coerceIn(0, lines.lastIndex)
            val y = localY + info.viewportStartOffset
            visible.firstOrNull { y >= it.offset && y < it.offset + it.size }?.let { return it.index }
            return visible.minByOrNull { abs((it.offset + it.size / 2f) - y) }?.index
                ?: playFocusUpdated.coerceIn(0, lines.lastIndex)
        }

        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { inProgress ->
                    if (suppressBrowseDetect || selectFrozen) return@collect
                    // 进入浏览态只由跟滚手势 / 选句置位；此处若跟 isScrollInProgress
                    // 会把跟滚 animateScroll 误判成浏览，导致永远点选调句
                    if (!inProgress && browsing) {
                        idleGen++
                        snapToFullLines()
                    }
                }
        }

        LaunchedEffect(playFocus, browsing, lines.size, centerPadPx, dragSession, selectFrozen) {
            if (selectFrozen) return@LaunchedEffect
            if (!browsing && !dragSession) {
                scrollToCenteredIndex(playFocus, animated = true)
            }
        }

        LaunchedEffect(lines) {
            browsing = false
            dragSession = false
            scrollToCenteredIndex(playFocus, animated = false)
        }

        LaunchedEffect(browsing, idleGen, selectFrozen) {
            if (selectFrozen) return@LaunchedEffect
            if (!browsing || dragSession) return@LaunchedEffect
            delay(5_500)
            while (listState.isScrollInProgress) {
                delay(160)
            }
            delay(320)
            if (browsing && !dragSession && !listState.isScrollInProgress && !selectFrozen) {
                exitBrowseAndFollow(animated = true)
            }
        }

        LaunchedEffect(resumeScrollToken) {
            if (resumeScrollToken <= 0) return@LaunchedEffect
            browsing = false
            dragSession = false
            scrollToCenteredIndex(playFocusUpdated, animated = true)
        }

        Box(
            Modifier
                .align(Alignment.Center)
                .offset(y = offsetY)
                .fillMaxWidth()
                .height(bandHeight)
                .padding(horizontal = hPad)
                .pointerInput(browsing, selectFrozen, lines.size, slotHeightPx) {
                    if (browsing || selectFrozen) return@pointerInput
                    val touchSlop = viewConfiguration.touchSlop
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        val start = down.position
                        var lastY = start.y
                        var dragging = false
                        var flingLaunched = false
                        var longPressed = false
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)

                        fun beginDrag(fromY: Float, change: PointerInputChange?) {
                            dragging = true
                            followGen++
                            suppressBrowseDetect = true
                            dragSession = true
                            change?.consume()
                            lastY = fromY
                        }

                        try {
                            // null = 超时（真长按）；非 null = 提前结束（抬手 / 被消费 / 转拖动）
                            val preDragResult = withTimeoutOrNull(longPressTimeout) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.find { it.id == pointerId }
                                        ?: return@withTimeoutOrNull "cancel"
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (!change.pressed) return@withTimeoutOrNull "up"
                                    if (change.isConsumed) return@withTimeoutOrNull "cancel"
                                    val dy = change.position.y - start.y
                                    val dx = change.position.x - start.x
                                    if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 0.75f) {
                                        beginDrag(change.position.y, change)
                                        return@withTimeoutOrNull "drag"
                                    }
                                }
                                @Suppress("UNREACHABLE_CODE")
                                "cancel"
                            }
                            if (!dragging &&
                                preDragResult == null &&
                                onLongPressUpdated != null
                            ) {
                                // 超时：长按选句
                                longPressed = true
                                browsing = true
                                onLongPressUpdated?.invoke(hitLyricIndex(start.y))
                                // 吞掉抬手
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.find { it.id == pointerId } ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                }
                                return@awaitEachGesture
                            }
                            if (!dragging && preDragResult == "up") {
                                // 超时等待期内已抬手：短按收起
                                onCollapseUpdated()
                                return@awaitEachGesture
                            }
                            if (!dragging) {
                                // 仍按住：等抬手收起，或转拖动
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.find { it.id == pointerId }
                                        ?: return@awaitEachGesture
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (!change.pressed) {
                                        if (!longPressed) onCollapseUpdated()
                                        return@awaitEachGesture
                                    }
                                    if (change.isConsumed) return@awaitEachGesture
                                    val dy = change.position.y - start.y
                                    val dx = change.position.x - start.x
                                    if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 0.75f) {
                                        beginDrag(change.position.y, change)
                                        break
                                    }
                                }
                            }

                            while (dragging) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.find { it.id == pointerId } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val step = change.position.y - lastY
                                lastY = change.position.y
                                listState.dispatchRawDelta(-step)
                                change.consume()
                                if (!change.pressed) {
                                    val velocityY = tracker.calculateVelocity().y
                                    flingLaunched = true
                                    browsing = true
                                    suppressBrowseDetect = false
                                    scope.launch {
                                        try {
                                            listState.scroll {
                                                with(followFling) {
                                                    performFling(-velocityY)
                                                }
                                            }
                                        } finally {
                                            dragSession = false
                                            idleGen++
                                            snapToFullLines()
                                        }
                                    }
                                    break
                                }
                            }
                        } finally {
                            if (dragSession && !flingLaunched) {
                                if (dragging) browsing = true
                                dragSession = false
                                suppressBrowseDetect = false
                                if (dragging) {
                                    idleGen++
                                    scope.launch { snapToFullLines() }
                                }
                            }
                        }
                    }
                }
                .consumeUnclaimedVerticalDrag(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = centerPad),
                horizontalAlignment = Alignment.CenterHorizontally,
                userScrollEnabled = (browsing || selectInteractive) && !dragSession,
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, line -> "${line.timeMs}_$index" },
                ) { index, line ->
                    val isPlayingLine = index == visualPlayFocus
                    val isBrowseCenter =
                        browsing &&
                            !selectMorphing &&
                            index == browseCenterIndex &&
                            !isPlayingLine
                    val distance = if (selectMorphing) {
                        1
                    } else {
                        abs(index - visualPlayFocus).coerceAtLeast(1)
                    }
                    val played = animActive >= 0 && index < animActive
                    val selected = selectMorphing && index in selectedIndices
                    val lineIx = remember(index) { MutableInteractionSource() }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (selectMorphing) {
                                    Modifier.height(slotHeight)
                                } else {
                                    Modifier
                                        .heightIn(min = slotHeight)
                                        .wrapContentHeight()
                                },
                            )
                            .then(
                                if (selected) {
                                    Modifier.background(
                                        selectSelectedBg.copy(alpha = selectSelectedBg.alpha * selectT),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                when {
                                    selectInteractive && selectToggleArmed -> {
                                        Modifier.clickable(
                                            interactionSource = lineIx,
                                            indication = null,
                                            onClick = { onToggleSelectUpdated?.invoke(index) },
                                        )
                                    }
                                    selectMorphing -> Modifier
                                    browsing && onLongPressUpdated != null -> {
                                        Modifier.combinedClickable(
                                            interactionSource = lineIx,
                                            indication = null,
                                            onClick = {
                                                val i = index
                                                browsing = false
                                                dragSession = false
                                                scope.launch {
                                                    scrollToCenteredIndex(i, animated = true)
                                                }
                                                onSeekUpdated(
                                                    line.timeMs.coerceIn(
                                                        0L,
                                                        trackDurationMs.coerceAtLeast(0L),
                                                    ),
                                                )
                                            },
                                            onLongClick = {
                                                followGen++
                                                dragSession = false
                                                onLongPressUpdated?.invoke(index)
                                            },
                                        )
                                    }
                                    browsing -> {
                                        Modifier.clickable(
                                            interactionSource = lineIx,
                                            indication = null,
                                            onClick = {
                                                val i = index
                                                browsing = false
                                                dragSession = false
                                                scope.launch {
                                                    scrollToCenteredIndex(i, animated = true)
                                                }
                                                onSeekUpdated(
                                                    line.timeMs.coerceIn(
                                                        0L,
                                                        trackDurationMs.coerceAtLeast(0L),
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                    else -> Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        @Composable
                        fun FollowBody() {
                            when {
                                isPlayingLine -> {
                                    StableCenterLyricText(
                                        focus = visualPlayFocus,
                                        text = line.text,
                                        animMs = animMs,
                                        lineSpanMs = lyricLineSpanMs(
                                            lines,
                                            visualPlayFocus,
                                            trackDurationMs,
                                        ),
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(
                                            color = playingColor.copy(
                                                alpha = 0.55f + 0.45f * emphasis,
                                            ),
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = playingStyle.resolvedFontWeight(
                                                LyricStyleRole.Playing,
                                            ),
                                            fontStyle = playingStyle.resolvedFontStyle(),
                                            fontSize = (22f * playFs).sp,
                                            lineHeight = (32f * playFs).sp,
                                            letterSpacing = 0.2.sp,
                                            textAlign = TextAlign.Center,
                                        ),
                                        modifier = Modifier.padding(
                                            vertical = linePad,
                                            horizontal = 8.dp,
                                        ),
                                    )
                                }
                                isBrowseCenter -> {
                                    Text(
                                        text = line.text,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = linePad, horizontal = 10.dp),
                                        maxLines = 3,
                                        softWrap = true,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(
                                            color = PortraitBrowseSelect.copy(alpha = 0.88f),
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = (16f * unplayedFs).sp,
                                            lineHeight = (24f * unplayedFs).sp,
                                            letterSpacing = 0.25.sp,
                                            textAlign = TextAlign.Center,
                                        ),
                                    )
                                }
                                else -> {
                                    PortraitScrollSideLine(
                                        lineKey = index,
                                        text = line.text,
                                        played = played,
                                        distance = distance.coerceAtMost(3),
                                        animMs = animMs,
                                        playedStyle = playedStyle,
                                        unplayedStyle = unplayedStyle,
                                        playedColor = playedColor,
                                        unplayedColor = unplayedColor,
                                        playedFs = playedFs,
                                        unplayedFs = unplayedFs,
                                        verticalPad = linePad,
                                    )
                                }
                            }
                        }

                        @Composable
                        fun SelectBody() {
                            Text(
                                text = line.text,
                                style = TextStyle(
                                    color = if (selected) {
                                        selectSelectedText.copy(alpha = 0.96f)
                                    } else {
                                        unplayedColor.copy(alpha = 0.50f)
                                    },
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = if (selected) {
                                        playingStyle.resolvedFontWeight(LyricStyleRole.Playing)
                                    } else {
                                        unplayedStyle.resolvedFontWeight(LyricStyleRole.Unplayed)
                                    },
                                    fontStyle = if (selected) {
                                        playingStyle.resolvedFontStyle()
                                    } else {
                                        unplayedStyle.resolvedFontStyle()
                                    },
                                    fontSize = (16.5f * if (selected) playFs else unplayedFs).sp,
                                    lineHeight = (24f * if (selected) playFs else unplayedFs).sp,
                                    letterSpacing = 0.25.sp,
                                    textAlign = TextAlign.Center,
                                ),
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp),
                            )
                        }

                        // 跟滚样式 ↔ 选句方块：按 selectT 交叉淡化，禁止阈值硬切
                        if (selectMorphing) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (selectT < 0.995f) {
                                    Box(Modifier.fillMaxWidth().alpha(1f - selectT)) {
                                        FollowBody()
                                    }
                                }
                                Box(Modifier.fillMaxWidth().alpha(selectT)) {
                                    SelectBody()
                                }
                            }
                        } else {
                            FollowBody()
                        }
                    }
                }
            }
        }
    }
}

/** 侧句：槽内文案 Crossfade + 透明度过渡（对齐横屏） */
@Composable
private fun PortraitScrollSideLine(
    lineKey: Int,
    text: String,
    played: Boolean,
    distance: Int,
    animMs: Int,
    playedStyle: LyricRoleStyle,
    unplayedStyle: LyricRoleStyle,
    playedColor: Color,
    unplayedColor: Color,
    playedFs: Float,
    unplayedFs: Float,
    verticalPad: Dp,
) {
    val role = if (played) LyricStyleRole.Played else LyricStyleRole.Unplayed
    val style = if (played) playedStyle else unplayedStyle
    val baseColor = if (played) playedColor else unplayedColor
    val fontScale = if (played) playedFs else unplayedFs
    val targetAlpha = (0.46f - distance.coerceAtMost(2) * 0.06f).coerceIn(0.28f, 0.5f)
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = (animMs * 1.1f).toInt().coerceIn(240, 480),
            easing = LyricSoftEasing,
        ),
        label = "portraitSideA",
    )
    Crossfade(
        targetState = lineKey to text,
        animationSpec = tween(
            durationMillis = animMs.coerceIn(200, 420),
            easing = LyricSoftEasing,
        ),
        label = "portraitSideCrossfade",
        modifier = Modifier.fillMaxWidth(),
    ) { (_, shown) ->
        Text(
            text = shown,
            style = TextStyle(
                color = baseColor.copy(alpha = alpha),
                fontFamily = FontFamily.SansSerif,
                fontWeight = style.resolvedFontWeight(role),
                fontStyle = style.resolvedFontStyle(),
                fontSize = (15f * fontScale).sp,
                lineHeight = (24f * fontScale).sp,
                letterSpacing = 0.25.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 3,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = verticalPad, horizontal = 10.dp),
        )
    }
}
