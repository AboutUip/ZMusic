package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerDisplayPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 一首歌的整套歌词。[lines] 为空时：[missing] 为 true 是确认没有歌词，
 * 否则还在加载（或界面上仍是上一首的词），这时什么都不画。
 */
@Immutable
internal class LyricSet(
    val trackId: Long,
    val lines: List<LrcLine>,
    val companions: List<LrcLine?>,
    val missing: Boolean,
    val replayEpoch: Int = 0,
) {
    val ready: Boolean get() = lines.isNotEmpty() || missing
}

internal val NoLyricRoll: () -> Float = { 0f }

private const val LyricRollWaitMs = 1_500L
private const val LyricRollMs = 620
private const val LyricFillMs = 280
private const val LyricStallMs = 6_000L

@Stable
private class LyricRollEntry(val id: Int, set: LyricSet) {
    var set by mutableStateOf(set)
    val fill = Animatable(1f)
}

@Stable
private class LyricRollState(initial: LyricSet) {
    private var seq = 0
    var front by mutableStateOf<LyricRollEntry?>(entry(initial))
    var back by mutableStateOf<LyricRollEntry?>(null)
    var rolling by mutableStateOf(false)
    val roll = Animatable(0f)
    val frontShift: () -> Float = { if (rolling) roll.value - 1f else 0f }
    val backShift: () -> Float = { if (rolling) roll.value else 0f }

    fun entry(set: LyricSet) = LyricRollEntry(seq++, set)
}

/**
 * 换歌时按歌词滚动的方式换整套词：新歌开头一屏接在旧词上方（视口外），
 * 整体往下滚一屏把新词滚进来，落定后旧词整套撤掉。
 *
 * - 新词没就绪时旧词原地定住；等太久先滚到空白，词到了再原地淡入。
 * - 确认没有歌词时滚进来的是占位（[LyricSet.missing]）。
 * - 同一首补上译文 / 逐字只原地更新。
 * - 同一首单曲循环回到开头（[replayEpoch] 加一）也整屏再滚一次：结尾留在旧面上，开头从上方进来。
 *
 * 歌词面要做的：把内容按 `shift`（以自身视口高为单位，换下的 0→1、换上的 -1→0）
 * 在视口里平移并裁在视口内，见 [lyricRollShift]；`leaving` 为 true 时停住时钟、不跟滚、不响应操作。
 */
@Composable
internal fun LyricRollSwap(
    trackId: Long,
    lines: List<LrcLine>,
    companions: List<LrcLine?>,
    onTrack: Boolean,
    missing: Boolean,
    replayEpoch: Int = 0,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (set: LyricSet, leaving: Boolean, shift: () -> Float) -> Unit,
) {
    // 加载一直没有结果（没走到确认无词的路径）时，久等后按没有歌词处理
    val loading = onTrack && lines.isEmpty() && !missing
    var stalled by remember(trackId) { mutableStateOf(false) }
    LaunchedEffect(trackId, loading) {
        stalled = false
        if (loading) {
            delay(LyricStallMs)
            stalled = true
        }
    }
    val target = remember(trackId, onTrack, lines, companions, missing, stalled, replayEpoch) {
        if (onTrack) {
            LyricSet(
                trackId,
                lines,
                companions,
                missing = lines.isEmpty() && (missing || stalled),
                replayEpoch = replayEpoch,
            )
        } else {
            LyricSet(trackId, emptyList(), emptyList(), missing = false, replayEpoch = replayEpoch)
        }
    }
    val targetNow by rememberUpdatedState(target)
    val state = remember { LyricRollState(target) }

    LaunchedEffect(target) {
        val front = state.front ?: return@LaunchedEffect
        if (front.set.trackId != target.trackId) return@LaunchedEffect
        // 代际变了要整屏滚，不能把新开头写进还停在结尾的那一面
        if (front.set.replayEpoch != target.replayEpoch) return@LaunchedEffect
        if (front.set !== target) {
            val filling = !front.set.ready && target.ready
            front.set = target
            if (filling) front.fill.snapTo(0f)
        }
        if (front.fill.value < 1f) {
            front.fill.animateTo(1f, tween(LyricFillMs, easing = LyricSoftEasing))
        }
    }

    LaunchedEffect(target.trackId, target.replayEpoch) {
        val incomingId = target.trackId
        val incomingEpoch = target.replayEpoch
        val shown = state.front
        if (shown != null &&
            shown.set.trackId == incomingId &&
            shown.set.replayEpoch == incomingEpoch
        ) {
            return@LaunchedEffect
        }
        if (state.rolling) {
            // 上一次还没滚完又换歌：先落定
            state.back = null
            state.rolling = false
        }
        if (shown != null) {
            state.back = shown
            state.front = null
        }
        val previous = state.back
        if (previous != null &&
            previous.set.trackId == incomingId &&
            previous.set.replayEpoch == incomingEpoch
        ) {
            // 等新词期间又切回了这一首：原样留下
            state.front = previous
            state.back = null
            return@LaunchedEffect
        }
        withTimeoutOrNull(LyricRollWaitMs) {
            snapshotFlow { targetNow }.first {
                it.trackId == incomingId && it.replayEpoch == incomingEpoch && it.ready
            }
        }
        val incoming = state.entry(targetNow)
        state.roll.snapTo(0f)
        state.rolling = state.back != null
        state.front = incoming
        if (state.rolling) {
            state.roll.animateTo(1f, tween(LyricRollMs, easing = LyricSoftEasing))
        }
        state.back = null
        state.rolling = false
    }

    Box(modifier, contentAlignment = contentAlignment) {
        val back = state.back
        for (entry in listOfNotNull(back, state.front)) {
            key(entry.id) {
                // 换歌或单曲循环那一帧就定住，不等效应把它挪到 back：此时传进来的进度已经是开头
                val leaving = entry === back ||
                    entry.set.trackId != target.trackId ||
                    entry.set.replayEpoch != target.replayEpoch
                Box(
                    Modifier.graphicsLayer { alpha = entry.fill.value },
                    contentAlignment = contentAlignment,
                ) {
                    content(entry.set, leaving, if (entry === back) state.backShift else state.frontShift)
                }
            }
        }
    }
}

/**
 * 内容在自身视口里按 [shift]（视口高为单位）上下平移。平移时上下裁在视口内，左右不裁。
 * 放在会随内容一起走的绘制（如裁掉已播放句）之前。
 */
internal fun Modifier.lyricRollShift(shift: () -> Float): Modifier = this
    .drawWithContent {
        if (shift() == 0f) {
            drawContent()
        } else {
            clipRect(left = -size.width, top = 0f, right = size.width * 2f, bottom = size.height) {
                this@drawWithContent.drawContent()
            }
        }
    }
    .graphicsLayer { translationY = shift() * size.height }

/** [hold] 期间停在开始 hold 前最后一次的值（换下的那套词不跟新歌跑）。 */
@Composable
internal fun <T> rememberHeldWhile(value: T, hold: Boolean): T {
    val held = remember { mutableStateOf(value) }
    SideEffect {
        if (!hold) held.value = value
    }
    return if (hold) held.value else value
}

/** 跟滚歌词一行的槽高（不含译文）。 */
internal fun lyricSlotHeight(
    playingStyle: LyricRoleStyle,
    playedStyle: LyricRoleStyle,
    unplayedStyle: LyricRoleStyle,
    lineSpacingDp: Float,
): Dp {
    val linePad = lineSpacingDp
        .coerceIn(PlayerDisplayPrefs.LINE_SPACING_MIN, PlayerDisplayPrefs.LINE_SPACING_MAX)
        .dp
    return maxOf(
        38f * playingStyle.sanitizedFontScale(),
        26f * playedStyle.sanitizedFontScale(),
        26f * unplayedStyle.sanitizedFontScale(),
    ).dp + linePad * 2
}
