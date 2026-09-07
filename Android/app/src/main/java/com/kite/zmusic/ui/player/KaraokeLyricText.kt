package com.kite.zmusic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.kite.zmusic.data.LyricWord

/**
 * 仅用于当前播放行：已唱完的字用播放中颜色，未唱的字用未播放颜色。
 * 每个字单独上色，避免整行 Text 把颜色盖掉。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KaraokeLyricText(
    words: List<LyricWord>,
    positionMs: Long,
    playingColor: Color,
    unplayedColor: Color,
    tracking: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 6,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val pos = rememberSmoothedLyricPositionMs(positionMs, tracking)
    val horizontal = when (style.textAlign) {
        TextAlign.Start, TextAlign.Left, TextAlign.Justify -> Arrangement.Start
        TextAlign.End, TextAlign.Right -> Arrangement.End
        else -> Arrangement.Center
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontal,
        verticalArrangement = Arrangement.Center,
        maxLines = maxLines.coerceAtLeast(1),
    ) {
        words.forEach { word ->
            val color = wordColor(word, pos, playingColor, unplayedColor)
            Text(
                text = word.text,
                color = color,
                style = style.copy(color = color),
                maxLines = 1,
                softWrap = false,
                overflow = overflow,
            )
        }
    }
}

internal fun wordColor(
    word: LyricWord,
    positionMs: Long,
    playingColor: Color,
    unplayedColor: Color,
): Color {
    val start = word.timeMs
    val dur = word.durationMs.coerceAtLeast(1L)
    val end = start + dur
    return when {
        positionMs <= start -> unplayedColor
        positionMs >= end -> playingColor
        else -> {
            val t = ((positionMs - start).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            lerp(unplayedColor, playingColor, t)
        }
    }
}

/**
 * 逐字着色用的平滑进度：只在播放源 **位置真正变大** 之后，于两次 UI tick（约 200ms）之间补帧。
 *
 * 暂停、缓冲、刚进播放页、同一毫秒反复上报时，源位置不会变大，必须钉在 [positionMs]，
 * 不能按墙钟把当前句唱完（否则会从中间自动填到句尾，或把首字填两遍）。
 */
@Composable
internal fun rememberSmoothedLyricPositionMs(
    positionMs: Long,
    tracking: Boolean,
): Long {
    var smooth by remember { mutableLongStateOf(positionMs) }
    val positionUpdated by rememberUpdatedState(positionMs)
    LaunchedEffect(tracking) {
        if (!tracking) {
            smooth = positionUpdated
            return@LaunchedEffect
        }
        var lastTick = positionUpdated
        var lastTickFrame = withFrameMillis { it }
        var sourceAdvancing = false
        smooth = lastTick
        while (true) {
            val now = withFrameMillis { it }
            val latest = positionUpdated
            when {
                latest > lastTick -> {
                    sourceAdvancing = true
                    lastTick = latest
                    lastTickFrame = now
                    // 源已追上或超过补帧：贴合；源仍落后则保住补帧，避免首字填完又被拉回
                    if (latest >= smooth) smooth = latest
                }
                latest < lastTick - LyricClockSeekBackMs -> {
                    sourceAdvancing = false
                    lastTick = latest
                    lastTickFrame = now
                    smooth = latest
                }
                else -> {
                    val elapsed = now - lastTickFrame
                    if (!sourceAdvancing || elapsed > LyricClockStallMs) {
                        sourceAdvancing = false
                        smooth = latest
                    } else {
                        val ahead = lastTick + elapsed.coerceAtMost(LyricClockMaxAheadMs)
                        if (ahead >= smooth) smooth = ahead
                    }
                }
            }
        }
    }
    if (!tracking) return positionMs
    return smooth
}

/** 播放页进度约 200ms 一跳；超过则视为时钟已停。 */
private const val LyricClockStallMs = 360L
/** 补帧最多超前一拍，避免唱过真实位置再被下一跳拉回。 */
private const val LyricClockMaxAheadMs = 220L
private const val LyricClockSeekBackMs = 48L
