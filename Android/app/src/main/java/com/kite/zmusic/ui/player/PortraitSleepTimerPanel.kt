package com.kite.zmusic.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.playback.SleepTimer
import com.kite.zmusic.playback.SleepTimerUi
import com.kite.zmusic.ui.main.MainControls
import com.kite.zmusic.ui.main.MainPalette
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

private val PanelRowShape = RoundedCornerShape(14.dp)
private val WheelItemHeight = 36.dp
private const val WheelVisibleCount = 5

internal fun sleepTimerRowSubtitle(timer: SleepTimerUi): String = when {
    timer.pendingStopAfterTrack -> "本首结束后停止"
    timer.active -> "${formatSleepClock(timer.remainingMs)} 后停止"
    else -> "到点自动停止播放"
}

internal fun formatSleepClock(ms: Long): String {
    val sec = ((ms + 999L) / 1000L).coerceAtLeast(0L)
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}

@Composable
internal fun PortraitSleepTimerPanel(
    timer: SleepTimerUi,
    onStart: (minutes: Int, waitForTrackEnd: Boolean) -> Unit,
    onCancel: () -> Unit,
    onWaitChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var minutes by remember { mutableIntStateOf(30) }
    var wait by remember { mutableStateOf(timer.waitForTrackEnd) }
    var snapTo by remember { mutableStateOf<Int?>(null) }
    val switchEnabled = !timer.pendingStopAfterTrack
    val switchColors = MainControls.switchColors()

    Column(modifier.fillMaxWidth().fillMaxHeight()) {
        if (timer.running) {
            Text(
                text = if (timer.pendingStopAfterTrack) {
                    "本首结束后停止"
                } else {
                    formatSleepClock(timer.remainingMs)
                },
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (timer.pendingStopAfterTrack) 18.sp else 32.sp,
                    letterSpacing = (-0.4).sp,
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (!timer.pendingStopAfterTrack) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "后停止播放",
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(PanelRowShape)
                .background(MainPalette.Card)
                .clickable(
                    enabled = switchEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        val next = !wait
                        wait = next
                        if (timer.running) onWaitChange(next)
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "时间到后等当前歌曲播完",
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "到点不切歌，本首结束后再停",
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                )
            }
            Switch(
                checked = wait,
                onCheckedChange = { next ->
                    wait = next
                    if (timer.running) onWaitChange(next)
                },
                enabled = switchEnabled,
                colors = switchColors,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "预设",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SleepTimer.PRESETS.forEach { preset ->
                val selected = minutes == preset
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                MainPalette.Accent.copy(alpha = 0.16f)
                            } else {
                                MainPalette.TrackOff
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                minutes = preset
                                snapTo = preset
                                onStart(preset, wait)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${preset}分",
                        style = TextStyle(
                            color = if (selected) MainPalette.Accent else MainPalette.Ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "自定义",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            ),
        )
        Spacer(Modifier.height(8.dp))
        SleepMinuteWheel(
            minutes = minutes,
            snapTo = snapTo,
            onSnapConsumed = { snapTo = null },
            onMinutesChange = { minutes = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        if (timer.running) {
            SleepTimerActionButton(
                text = "取消定时",
                filled = false,
                onClick = onCancel,
            )
            Spacer(Modifier.height(8.dp))
        }
        SleepTimerActionButton(
            text = if (timer.running) "重新开始" else "开始",
            filled = true,
            onClick = { onStart(minutes, wait) },
        )
    }
}

@Composable
private fun SleepTimerActionButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (filled) MainPalette.Accent else MainPalette.TrackOff,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = if (filled) Color.White else MainPalette.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SleepMinuteWheel(
    minutes: Int,
    snapTo: Int?,
    onSnapConsumed: () -> Unit,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (minutes - 1).coerceIn(0, SleepTimer.MAX_MINUTES - 1),
    )
    val snapFling = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Start,
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return@snapshotFlow null
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            items.minBy { abs((it.offset + it.size / 2) - center) }.index + 1
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { onMinutesChange(it.coerceIn(SleepTimer.MIN_MINUTES, SleepTimer.MAX_MINUTES)) }
    }
    LaunchedEffect(snapTo) {
        val target = snapTo ?: return@LaunchedEffect
        listState.animateScrollToItem((target - 1).coerceIn(0, SleepTimer.MAX_MINUTES - 1))
        onSnapConsumed()
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(PanelRowShape)
            .background(MainPalette.Card),
        contentAlignment = Alignment.Center,
    ) {
        val rows = run {
            val raw = (maxHeight / WheelItemHeight).toInt().coerceIn(3, WheelVisibleCount)
            if (raw % 2 == 0) raw - 1 else raw
        }
        val padCount = (rows - 1) / 2
        val wheelHeight = WheelItemHeight * rows
        Box(
            Modifier
                .fillMaxWidth()
                .height(wheelHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MainPalette.Accent.copy(alpha = 0.10f)),
            )
            LazyColumn(
                state = listState,
                flingBehavior = snapFling,
                modifier = Modifier.fillMaxWidth().height(wheelHeight),
                contentPadding = PaddingValues(vertical = WheelItemHeight * padCount),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            items(SleepTimer.MAX_MINUTES) { index ->
                val value = index + 1
                val dist = abs(value - minutes)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onMinutesChange(value)
                                scope.launch { listState.animateScrollToItem(index) }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${value} 分钟",
                        modifier = Modifier.graphicsLayer {
                            alpha = when {
                                dist == 0 -> 1f
                                dist == 1 -> 0.55f
                                else -> 0.28f
                            }
                        },
                        style = TextStyle(
                            color = if (dist == 0) MainPalette.Ink else MainPalette.Secondary,
                            fontWeight = if (dist == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = if (dist == 0) 18.sp else 15.sp,
                        ),
                    )
                }
            }
            }
        }
    }
}
