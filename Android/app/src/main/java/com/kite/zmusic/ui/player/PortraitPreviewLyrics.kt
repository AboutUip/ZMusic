package com.kite.zmusic.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PreviewLyricAlign
import com.kite.zmusic.data.karaokeWords

/**
 * 竖屏进度条上方的预览歌词（叠在黑胶区底部）。
 * [fancy] 开启：切句动画与歌词页一致；若当前行带逐字词则按设置着色推进。
 * 关闭：无切句动画，强制整句。
 */
@Composable
internal fun PortraitPreviewLyrics(
    lines: List<LrcLine>,
    positionMs: Long,
    durationMs: Long,
    count: Int,
    playingArgb: Int,
    upcomingArgb: Int,
    playingFontSp: Float,
    upcomingFontSp: Float,
    fancy: Boolean,
    align: PreviewLyricAlign,
    offsetYDp: Float,
    lineSpacingDp: Float,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) return
    val timing = lyricAnimTiming(lines, positionMs, durationMs)
    val animActive = lyricAnimActiveIndex(lines, positionMs, durationMs)
    val focus = lyricFocusIndex(lines, animActive)
    if (focus < 0) return
    val showCount = count.coerceIn(
        PlayerDisplayPrefs.PREVIEW_LYRIC_COUNT_MIN,
        PlayerDisplayPrefs.PREVIEW_LYRIC_COUNT_MAX,
    )
    val textAlign = when (align) {
        PreviewLyricAlign.LEFT -> TextAlign.Start
        PreviewLyricAlign.CENTER -> TextAlign.Center
        PreviewLyricAlign.RIGHT -> TextAlign.End
    }
    val columnAlign = when (align) {
        PreviewLyricAlign.LEFT -> Alignment.Start
        PreviewLyricAlign.CENTER -> Alignment.CenterHorizontally
        PreviewLyricAlign.RIGHT -> Alignment.End
    }
    val playingColor = Color(playingArgb)
    val upcomingBase = Color(upcomingArgb)
    // 与歌词页侧句一致：待播默认压到约 0.4 透明度，避免和播放中抢视线
    val upcomingColor = upcomingBase.copy(
        alpha = (upcomingBase.alpha * 0.40f).coerceIn(0.28f, 0.48f),
    )
    // 播放行未唱字：同样低调，才能看出逐字推进
    val karaokeUnplayed = upcomingBase.copy(alpha = 0.42f)
    val playFs = playingFontSp.coerceIn(
        PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MIN,
        PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MAX,
    )
    val upcomingFs = upcomingFontSp.coerceIn(
        PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MIN,
        PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MAX,
    )
    val animMs = timing.durationMs
    val span = lyricLineSpanMs(lines, focus, durationMs)
    val gap = lineSpacingDp.coerceIn(
        PlayerDisplayPrefs.PREVIEW_LYRIC_LINE_SPACING_MIN,
        PlayerDisplayPrefs.PREVIEW_LYRIC_LINE_SPACING_MAX,
    )
    val visibleLines = buildList {
        for (i in 0 until showCount) {
            val line = lines.getOrNull(focus + i) ?: break
            val text = line.text.trim()
            if (text.isNotEmpty()) add((focus + i) to line)
        }
    }
    if (visibleLines.isEmpty()) return

    Column(
        modifier
            .fillMaxWidth()
            .offset(y = offsetYDp.dp)
            .padding(top = 4.dp, bottom = 6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenLyrics,
            ),
        horizontalAlignment = columnAlign,
        verticalArrangement = Arrangement.spacedBy(gap.dp),
    ) {
        visibleLines.forEachIndexed { slot, (index, line) ->
            val text = line.text.trim()
            val isPlaying = slot == 0
            val color = if (isPlaying) playingColor else upcomingColor
            val fontSp = if (isPlaying) playFs else upcomingFs
            val style = TextStyle(
                color = color,
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = fontSp.sp,
                lineHeight = (fontSp * 1.28f).sp,
                textAlign = textAlign,
            )
            if (isPlaying) {
                val words = if (fancy) line.karaokeWords(positionMs) else emptyList()
                StableCenterLyricText(
                    focus = index,
                    text = text,
                    animMs = animMs,
                    lineSpanMs = span,
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    freezeTransitions = !fancy,
                    instantAppear = !fancy,
                    words = words,
                    positionMs = positionMs,
                    unplayedColor = karaokeUnplayed.takeIf { words.isNotEmpty() },
                    tracking = fancy && words.isNotEmpty(),
                )
            } else {
                Text(
                    text = text,
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
