package com.kite.zmusic.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.LongLyricAlign
import com.kite.zmusic.data.LongLyricMotion
import com.kite.zmusic.data.LongLyricRender
import com.kite.zmusic.data.LyricWord
import com.kite.zmusic.data.longLyricOffsetPx
import com.kite.zmusic.data.longLyricProgress
import com.kite.zmusic.data.longLyricSungFraction
import kotlin.math.roundToInt

@Composable
internal fun rememberLongLyricRender(): LongLyricRender {
    val context = LocalContext.current
    val store = remember(context) {
        (context.applicationContext as ZMusicApplication).lyricRenderStore
    }
    val mode by store.longLine.collectAsStateWithLifecycle()
    return mode
}

internal fun TextAlign?.toLongLyricAlign(): LongLyricAlign = when (this) {
    TextAlign.Start, TextAlign.Left, TextAlign.Justify -> LongLyricAlign.Start
    TextAlign.End, TextAlign.Right -> LongLyricAlign.End
    else -> LongLyricAlign.Center
}

/**
 * 换行时沿用原来的多行排版。水平滚动时只留一行：
 * 当前句跟着播放移到句尾，其余长句单行截断，避免整页一起跑。
 */
@Composable
internal fun LongLyricText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 6,
    overflow: TextOverflow = TextOverflow.Clip,
    words: List<LyricWord> = emptyList(),
    positionMs: Long = 0L,
    unplayedColor: Color? = null,
    tracking: Boolean = false,
    lineActive: Boolean = false,
    lineStartMs: Long = 0L,
    lineSpanMs: Long = 0L,
) {
    val horizontal = rememberLongLyricRender() == LongLyricRender.Horizontal
    val karaoke = words.isNotEmpty() && unplayedColor != null
    if (!horizontal) {
        if (karaoke) {
            KaraokeLyricText(
                words = words,
                positionMs = positionMs,
                playingColor = style.color,
                unplayedColor = unplayedColor,
                tracking = tracking,
                style = style,
                modifier = modifier,
                maxLines = maxLines,
                overflow = overflow,
            )
        } else {
            Text(
                text = text,
                maxLines = maxLines,
                softWrap = true,
                overflow = overflow,
                style = style,
                modifier = modifier,
            )
        }
        return
    }
    if (!lineActive) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = style,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    val pos = rememberSmoothedLyricPositionMs(positionMs, tracking)
    val karaokeColor = unplayedColor?.takeIf { words.isNotEmpty() }
    HorizontalLyricViewport(
        motion = if (karaokeColor != null) {
            LongLyricMotion.Karaoke(longLyricSungFraction(words, pos))
        } else {
            LongLyricMotion.Timed(longLyricProgress(pos, lineStartMs, lineSpanMs))
        },
        align = style.textAlign.toLongLyricAlign(),
        modifier = modifier,
    ) {
        val idleColor = karaokeColor
        if (idleColor != null) {
            Row {
                words.forEachIndexed { index, word ->
                    key(index, word.timeMs, word.text) {
                        val color = wordColor(word, pos, style.color, idleColor)
                        Text(
                            text = word.text,
                            color = color,
                            style = style.copy(color = color),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        } else {
            Text(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
internal fun HorizontalLyricViewport(
    motion: LongLyricMotion,
    align: LongLyricAlign,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier.clipToBounds().fillMaxWidth()) { constraints ->
        val placeable = subcompose("line", content).first().measure(
            Constraints(
                maxWidth = Constraints.Infinity,
                maxHeight = constraints.maxHeight,
            ),
        )
        val viewport = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        val offset = longLyricOffsetPx(
            textWidth = placeable.width.toFloat(),
            viewportWidth = viewport.toFloat(),
            align = align,
            motion = motion,
        ).roundToInt()
        layout(viewport, placeable.height) {
            placeable.place(offset, 0)
        }
    }
}
