package com.kite.zmusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerDisplayPrefs
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val PortraitLyricFallbackDim = Color(0xFF7A8899)
private val PortraitBrowseSelect = Color(0xFFDCE6F0)

/**
 * 竖屏歌词：LazyColumn 全列表 + 与横屏同套「滚动进入浏览态」。
 * - 跟滚：播放行居中
 * - 首滑进入浏览；浏览中可自由滚、点选 seek
 * - 闲置回跟滚；侧句 Crossfade 刷新
 */
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
    contentAlpha: Float = 1f,
    onSeekToMs: (Long) -> Unit,
    onCollapse: () -> Unit,
    onBandCoords: ((LayoutCoordinates) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val alpha = contentAlpha.coerceIn(0f, 1f)
    if (lines.isEmpty()) {
        Box(
            modifier
                .fillMaxWidth()
                .graphicsLayer { this.alpha = alpha },
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

    val timing = lyricAnimTiming(lines, positionMs, trackDurationMs)
    val animActive = lyricAnimActiveIndex(lines, positionMs, trackDurationMs)
    val playFocus = lyricFocusIndex(lines, animActive)
    val live = lyricIsLive(lines, animActive, playFocus)
    val animMs = timing.durationMs
    val emphasis by animateFloatAsState(
        targetValue = if (live) 1f else 0f,
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

    val listState = rememberLazyListState()
    var browsing by remember { mutableStateOf(false) }
    var dragSession by remember { mutableStateOf(false) }
    var idleGen by remember { mutableIntStateOf(0) }
    var suppressBrowseDetect by remember { mutableStateOf(false) }
    var followGen by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val onSeekUpdated by rememberUpdatedState(onSeekToMs)
    val onCollapseUpdated by rememberUpdatedState(onCollapse)
    val playFocusUpdated by rememberUpdatedState(playFocus)
    val followFling = ScrollableDefaults.flingBehavior()

    val slotHeightPx = with(density) { slotHeight.roundToPx() }
    val browseCenterIndex by remember {
        derivedStateOf { listState.browseCenterLyricIndex(playFocusUpdated) }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .onGloballyPositioned { onBandCoords?.invoke(it) },
    ) {
        val desiredBand = slotHeight * visibleCount
        val bandHeight = minOf(desiredBand, maxHeight).coerceAtLeast(slotHeight)
        val bandHeightPx = with(density) { bandHeight.roundToPx() }.coerceAtLeast(1)
        val centerPadPx = ((bandHeightPx - slotHeightPx) / 2).coerceAtLeast(0)
        val centerPad = with(density) { centerPadPx.toDp() }

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
            dragSession = false
            browsing = false
            scope.launch { scrollToCenteredIndex(playFocusUpdated, animated) }
        }

        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { inProgress ->
                    if (suppressBrowseDetect) return@collect
                    if (inProgress) {
                        if (!dragSession) browsing = true
                    } else if (browsing) {
                        idleGen++
                        snapToFullLines()
                    }
                }
        }

        LaunchedEffect(playFocus, browsing, lines.size, centerPadPx, dragSession) {
            if (!browsing && !dragSession) {
                scrollToCenteredIndex(playFocus, animated = true)
            }
        }

        LaunchedEffect(lines) {
            browsing = false
            dragSession = false
            scrollToCenteredIndex(playFocus, animated = false)
        }

        LaunchedEffect(browsing, idleGen) {
            if (!browsing || dragSession) return@LaunchedEffect
            delay(5_500)
            while (listState.isScrollInProgress) {
                delay(160)
            }
            delay(320)
            if (browsing && !dragSession && !listState.isScrollInProgress) {
                exitBrowseAndFollow(animated = true)
            }
        }

        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(bandHeight)
                .padding(horizontal = 18.dp)
                .pointerInput(browsing, lines.size, slotHeightPx) {
                    if (browsing) return@pointerInput
                    // 跟滚态：首滑抢手势进入浏览（与横屏一致）
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        val start = down.position
                        var lastY = start.y
                        var dragging = false
                        var flingLaunched = false
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
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.find { it.id == pointerId }
                                    ?: return@awaitEachGesture
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    if (!dragging) onCollapseUpdated()
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
                userScrollEnabled = browsing && !dragSession,
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, line -> "${line.timeMs}_$index" },
                ) { index, line ->
                    val isPlayingLine = index == playFocus
                    val isBrowseCenter =
                        browsing &&
                            index == browseCenterIndex &&
                            !isPlayingLine
                    val distance = abs(index - playFocus).coerceAtLeast(1)
                    val played = animActive >= 0 && index < animActive
                    val lineIx = remember(index) { MutableInteractionSource() }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = slotHeight)
                            .wrapContentHeight()
                            .then(
                                if (browsing) {
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
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isPlayingLine -> {
                                StableCenterLyricText(
                                    focus = playFocus,
                                    text = line.text,
                                    animMs = animMs,
                                    lineSpanMs = lyricLineSpanMs(
                                        lines,
                                        playFocus,
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
