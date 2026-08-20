package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricWord
import com.kite.zmusic.data.karaokeWords
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

private val PreviewShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(12.dp)

@Composable
internal fun WordLyricSettingsPage(
    wordByWord: Boolean,
    onWordByWordChange: (Boolean) -> Unit,
    contentBottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "仅当正在播放的歌曲提供逐字歌词时才会按字渲染，否则仍按行显示。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RenderModeChip(
                title = "按行渲染",
                selected = !wordByWord,
                onClick = { onWordByWordChange(false) },
                modifier = Modifier.weight(1f),
            )
            RenderModeChip(
                title = "按字渲染",
                selected = wordByWord,
                onClick = { onWordByWordChange(true) },
                modifier = Modifier.weight(1f),
            )
        }
        WordLyricPreview(
            wordByWord = wordByWord,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RenderModeChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .then(
                if (selected) {
                    Modifier.clip(ChipShape).background(MainPalette.Accent.copy(alpha = 0.18f))
                } else {
                    Modifier.wallpaperItemChrome(ChipShape, MainPalette.Card)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = if (selected) MainPalette.Accent else MainPalette.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
        )
    }
}

@Composable
private fun WordLyricPreview(
    wordByWord: Boolean,
    modifier: Modifier = Modifier,
) {
    val lines = remember { demoWordLyricLines() }
    val loopMs = remember(lines) {
        val last = lines.last()
        val lastWord = last.words.lastOrNull()
        ((lastWord?.timeMs ?: last.timeMs) + (lastWord?.durationMs ?: 800L) + 400L)
            .coerceAtLeast(1L)
    }
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(loopMs) {
        val origin = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            positionMs = (now - origin) % loopMs
        }
    }
    val active = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    Column(
        modifier
            .wallpaperItemChrome(PreviewShape, MainPalette.Card)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "预览",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(Modifier.height(2.dp))
        lines.forEachIndexed { index, line ->
            val isPlaying = index == active
            val playingColor = MainPalette.Ink
            val unplayedColor = MainPalette.Secondary.copy(alpha = 0.42f)
            val style = TextStyle(
                color = if (isPlaying) playingColor else unplayedColor,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = if (isPlaying) 20.sp else 14.sp,
                lineHeight = if (isPlaying) 28.sp else 20.sp,
                textAlign = TextAlign.Center,
            )
            if (wordByWord && isPlaying && line.words.isNotEmpty()) {
                KaraokeLyricText(
                    words = line.karaokeWords(positionMs),
                    positionMs = positionMs,
                    playingColor = playingColor,
                    unplayedColor = unplayedColor,
                    tracking = true,
                    style = style,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = line.text,
                    style = style,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun demoWordLyricLines(): List<LrcLine> {
    fun line(start: Long, parts: List<Pair<String, Long>>): LrcLine {
        var t = start
        val words = parts.map { (text, dur) ->
            val word = LyricWord(timeMs = t, durationMs = dur, text = text)
            t += dur
            word
        }
        return LrcLine(start, words.joinToString("") { it.text }, words)
    }
    return listOf(
        line(0L, listOf("在" to 280L, "这" to 240L, "个" to 220L, "夜" to 320L, "晚" to 480L)),
        line(
            1600L,
            listOf(
                "我" to 220L,
                "听" to 240L,
                "见" to 260L,
                "你" to 280L,
                "的" to 200L,
                "声" to 280L,
                "音" to 520L,
            ),
        ),
        line(
            3600L,
            listOf("像" to 240L, "风" to 260L, "穿" to 240L, "过" to 220L, "走" to 280L, "廊" to 520L),
        ),
        line(
            5600L,
            listOf("只" to 220L, "把" to 240L, "心" to 260L, "事" to 280L, "留" to 260L, "下" to 560L),
        ),
    )
}
