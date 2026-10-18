package com.kite.zmusic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.kite.zmusic.data.LyricWord
import com.kite.zmusic.data.lyricWordProgress

/**
 * 仅用于当前播放行。
 * [sweep] 为 false 时一个字整颗变色；为 true 时高亮按这个字的宽度从左往右扫过。
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
    sweep: Boolean = false,
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
        words.forEachIndexed { index, word ->
            key(index, word.timeMs, word.text) {
                KaraokeWordText(
                    word = word,
                    positionMs = pos,
                    playingColor = playingColor,
                    unplayedColor = unplayedColor,
                    style = style,
                    sweep = sweep,
                    overflow = overflow,
                )
            }
        }
    }
}

@Composable
internal fun KaraokeWordText(
    word: LyricWord,
    positionMs: Long,
    playingColor: Color,
    unplayedColor: Color,
    style: TextStyle,
    sweep: Boolean,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val progress = lyricWordProgress(word, positionMs)
    if (!sweep) {
        val color = wordColor(word, positionMs, playingColor, unplayedColor)
        KaraokeWordLabel(word.text, color, style, overflow)
        return
    }
    when {
        progress <= 0f -> KaraokeWordLabel(word.text, unplayedColor, style, overflow)
        progress >= 1f -> KaraokeWordLabel(word.text, playingColor, style, overflow)
        else -> {
            Box {
                KaraokeWordLabel(word.text, unplayedColor, style, overflow)
                KaraokeWordLabel(
                    text = word.text,
                    color = playingColor,
                    style = style,
                    overflow = overflow,
                    modifier = Modifier.drawWithContent {
                        clipRect(right = size.width * progress) {
                            this@drawWithContent.drawContent()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun KaraokeWordLabel(
    text: String,
    color: Color,
    style: TextStyle,
    overflow: TextOverflow,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        style = style.copy(color = color),
        maxLines = 1,
        softWrap = false,
        overflow = overflow,
        modifier = modifier,
    )
}

internal fun wordColor(
    word: LyricWord,
    positionMs: Long,
    playingColor: Color,
    unplayedColor: Color,
): Color = lerp(unplayedColor, playingColor, lyricWordProgress(word, positionMs))

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
