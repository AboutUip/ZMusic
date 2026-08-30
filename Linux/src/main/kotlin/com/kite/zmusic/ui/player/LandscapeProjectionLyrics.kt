package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp as lerpDp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricRoleStyle
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun LandscapeProjectionLyrics(
    lines: List<LrcLine>,
    positionMs: Long,
    trackDurationMs: Long,
    wordByWord: Boolean,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lineSpacingDp: Float = 10f,
    playedCount: Int = 2,
    upcomingCount: Int = 2,
    playingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    playedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
    onLongPressLine: ((Int) -> Unit)? = null,
    dynamicLyrics: Boolean = false,
    vinylLeftInset: Dp = 0.dp,
    offsetXDp: Float = 0f,
) {
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                style = TextStyle(
                    color = LyricDim.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    letterSpacing = 1.2.sp,
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
    val dynamicT = remember { Animatable(if (dynamicLyrics) 1f else 0f) }
    LaunchedEffect(dynamicLyrics) {
        val target = if (dynamicLyrics) 1f else 0f
        val distance = abs(target - dynamicT.value).coerceIn(0f, 1f)
        val durationMs = (480f * distance).toInt().coerceIn(160, 480)
        dynamicT.animateTo(
            targetValue = target,
            tween(durationMs, easing = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f)),
        )
    }
    val listState = rememberLazyListState()
    var browsing by remember { mutableStateOf(false) }
    var idleGen by remember { mutableIntStateOf(0) }
    var suppressBrowseDetect by remember { mutableStateOf(false) }
    var followGen by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val slotHeight = 38.dp + lineSpacingDp.dp * 2
    val visible = (playedCount + 1 + upcomingCount).coerceIn(1, 7)
    val bandHeight = slotHeight * visible
    val slotHeightPx = with(density) { slotHeight.roundToPx() }
    val bandHeightPx = with(density) { bandHeight.roundToPx() }
    val centerPadPx = ((bandHeightPx - slotHeightPx) / 2).coerceAtLeast(0)
    val centerPad = with(density) { centerPadPx.toDp() }
    val browseCenterIndex by remember {
        derivedStateOf { listState.browseCenterLyricIndex(playFocus) }
    }

    suspend fun scrollToPlayFocus(animated: Boolean) {
        val gen = followGen
        listState.scrollLyricToCenteredIndex(
            index = playFocus,
            lastIndex = lines.lastIndex,
            slotHeightPx = slotHeightPx,
            animated = animated,
            followGen = gen,
            currentFollowGen = { followGen },
            setSuppressBrowseDetect = { suppressBrowseDetect = it },
        )
    }

    fun exitBrowseAndFollow() {
        browsing = false
        scope.launch { scrollToPlayFocus(animated = true) }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { inProgress ->
                if (suppressBrowseDetect) return@collect
                if (inProgress) {
                    browsing = true
                } else if (browsing) {
                    idleGen++
                    listState.snapLyricToFullLines(
                        slotHeightPx = slotHeightPx,
                        lastIndex = lines.lastIndex,
                        followGen = followGen,
                        currentFollowGen = { followGen },
                        setSuppressBrowseDetect = { suppressBrowseDetect = it },
                    )
                }
            }
    }

    LaunchedEffect(playFocus, browsing, lines.size, centerPadPx) {
        if (!browsing) {
            scrollToPlayFocus(animated = true)
        }
    }

    LaunchedEffect(lines) {
        browsing = false
        scrollToPlayFocus(animated = false)
    }

    LaunchedEffect(browsing, idleGen) {
        if (!browsing) return@LaunchedEffect
        delay(5_500)
        while (listState.isScrollInProgress) {
            delay(160)
        }
        delay(320)
        if (browsing && !listState.isScrollInProgress) {
            exitBrowseAndFollow()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val colW = maxWidth
        val dynT = dynamicT.value
        val centerX = colW / 2 + offsetXDp.dp
        val leftLimit = lerpDp(0.dp, vinylLeftInset.coerceIn(0.dp, colW), dynT)
        val halfDyn = minOf(
            (centerX - leftLimit).coerceAtLeast(0.dp),
            (colW - centerX).coerceAtLeast(0.dp),
        )
        val dynW = (halfDyn * 2).coerceAtLeast(48.dp)
        val dynStart = centerX - dynW / 2
        val boxW = lerpDp(colW, dynW, dynT)
        val startX = lerpDp(offsetXDp.dp, dynStart, dynT)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = startX)
                .height(bandHeight)
                .width(boxW.coerceAtLeast(48.dp))
                .clip(RectangleShape),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = centerPad),
                horizontalAlignment = Alignment.CenterHorizontally,
                userScrollEnabled = true,
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, line -> "${line.timeMs}_$index" },
                ) { index, line ->
                    val isPlayingLine = index == playFocus
                    val isBrowseCenter =
                        browsing && index == browseCenterIndex && !isPlayingLine
                    LandscapeScrollLyricLine(
                        text = line.text,
                        lineKey = index,
                        isPlayingLine = isPlayingLine,
                        isBrowseCenter = isBrowseCenter,
                        live = live && isPlayingLine,
                        played = animActive >= 0 && index < animActive,
                        distanceFromPlay = abs(index - playFocus),
                        lines = lines,
                        focus = playFocus,
                        trackDurationMs = trackDurationMs,
                        lineSpacing = lineSpacingDp.dp,
                        slotHeight = slotHeight,
                        playingStyle = playingStyle,
                        playedStyle = playedStyle,
                        unplayedStyle = unplayedStyle,
                        animMs = animMs,
                        browsing = browsing,
                        positionMs = positionMs,
                        wordByWord = wordByWord,
                        onSeekClick = if (!browsing) {
                            null
                        } else {
                            {
                                browsing = false
                                scope.launch { scrollToPlayFocus(animated = true) }
                                onSeekToMs(line.timeMs)
                            }
                        },
                        onLongPress = onLongPressLine?.let { cb -> { cb(index) } },
                    )
                }
            }
        }
    }
}
