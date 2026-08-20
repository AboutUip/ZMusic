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
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
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
        var originPos = positionUpdated
        var originFrame = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val latest = positionUpdated
            if (latest != originPos) {
                originPos = latest
                originFrame = now
            }
            smooth = originPos + (now - originFrame)
        }
    }
    return if (tracking) smooth else positionMs
}
