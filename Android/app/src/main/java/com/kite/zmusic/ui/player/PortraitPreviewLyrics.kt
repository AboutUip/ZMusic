package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricWord
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PreviewLyricAlign
import com.kite.zmusic.data.karaokeWords
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 竖屏进度条上方的预览歌词（叠在黑胶区底部）。
 * [fancy] 开启：切句整列上移（下一句滑进播放位，其余行顺势更新），并尊重逐字渲染。
 * 关闭：无切句动画，强制整句。首次出现 / 切歌重建时直接亮起，不滚句。
 */
@Composable
internal fun PortraitPreviewLyrics(
    lines: List<LrcLine>,
    companions: List<LrcLine?> = emptyList(),
    originalOnTop: Boolean = true,
    showCompanionOnOthers: Boolean = true,
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
    clockRunning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) return
    val timing = lyricAnimTiming(lines, positionMs, durationMs)
    val animActive = if (clockRunning) {
        lyricAnimActiveIndex(lines, positionMs, durationMs)
    } else {
        lyricActiveIndex(lines, positionMs)
    }
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
    val upcomingColor = upcomingBase.copy(
        alpha = (upcomingBase.alpha * 0.40f).coerceIn(0.28f, 0.48f),
    )
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
    val gap = lineSpacingDp.coerceIn(
        PlayerDisplayPrefs.PREVIEW_LYRIC_LINE_SPACING_MIN,
        PlayerDisplayPrefs.PREVIEW_LYRIC_LINE_SPACING_MAX,
    )
    val rootMod = modifier
        .fillMaxWidth()
        .offset(y = offsetYDp.dp)
        .padding(top = 4.dp, bottom = 6.dp)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpenLyrics,
        )

    if (!fancy) {
        PreviewLyricStaticColumn(
            lines = lines,
            companions = companions,
            originalOnTop = originalOnTop,
            showCompanionOnOthers = showCompanionOnOthers,
            focus = focus,
            showCount = showCount,
            textAlign = textAlign,
            columnAlign = columnAlign,
            playingColor = playingColor,
            upcomingColor = upcomingColor,
            playFs = playFs,
            upcomingFs = upcomingFs,
            gap = gap,
            modifier = rootMod,
        )
        return
    }

    val density = LocalDensity.current
    val pairGapPx = with(density) { 2.dp.toPx() }
    val playH = with(density) { (playFs * 1.28f).sp.toPx() }
    val upH = with(density) { (upcomingFs * 1.28f).sp.toPx() }
    val gapPx = with(density) { gap.dp.toPx() }
    fun blockH(playing: Boolean, dual: Boolean): Float {
        val h = if (playing) playH else upH
        return if (dual) h * 2f + pairGapPx else h
    }
    fun dualAt(index: Int): Boolean {
        val c = companions.getOrNull(index)
        if (c == null) return false
        return index == focus || showCompanionOnOthers
    }
    fun yAt(slot: Int): Float {
        val playBlock = blockH(playing = true, dual = dualAt(focus))
        if (slot <= 0) return slot * (playBlock + gapPx)
        var y = playBlock + gapPx
        var i = 1
        while (i < slot) {
            y += blockH(playing = false, dual = dualAt(focus + i)) + gapPx
            i++
        }
        return y
    }
    fun yOf(slot: Float): Float {
        val i = floor(slot.toDouble()).toInt()
        val t = (slot - i).coerceIn(0f, 1f)
        return lerp(yAt(i), yAt(i + 1), t)
    }
    val boxH = (yAt(showCount) - gapPx).coerceAtLeast(blockH(true, dualAt(focus)))
    val focusAnim = remember { Animatable(focus.toFloat()) }
    LaunchedEffect(focus) {
        val cur = focusAnim.value
        if (abs(focus - cur) < 0.001f) return@LaunchedEffect
        if (abs(focus - cur) > 2.51f) {
            focusAnim.snapTo(focus.toFloat())
            return@LaunchedEffect
        }
        focusAnim.animateTo(
            targetValue = focus.toFloat(),
            animationSpec = tween(
                durationMillis = animMs.coerceIn(220, 420),
                easing = LyricSoftEasing,
            ),
        )
    }
    val visualFocus = focusAnim.value
    val start = floor(visualFocus.toDouble()).toInt() - 1
    val end = ceil(visualFocus.toDouble()).toInt() + showCount
    val live = lyricIsLive(lines, animActive, focus)
    val playLine = lines[focus]
    val playPair = previewOrderedPair(
        original = playLine,
        translation = companions.getOrNull(focus),
        originalOnTop = originalOnTop,
    )
    val playStyle = TextStyle(
        color = playingColor,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = playFs.sp,
        lineHeight = (playFs * 1.28f).sp,
        textAlign = textAlign,
    )
    val playTransStyle = playStyle.copy(
        fontSize = (playFs * 0.88f).sp,
        lineHeight = (playFs * 1.28f * 0.88f).sp,
        fontWeight = FontWeight.Medium,
        color = playingColor.copy(alpha = playingColor.alpha * 0.88f),
    )
    val playSlot = focus - visualFocus
    val playFadeTop = if (playSlot < 0f) (playSlot + 1f).coerceIn(0f, 1f) else 1f
    val playFadeBot = if (playSlot > showCount - 1f) {
        (showCount - playSlot).coerceIn(0f, 1f)
    } else {
        1f
    }
    val playAlpha = playFadeTop * playFadeBot

    Box(
        rootMod
            .height(with(density) { boxH.toDp() })
            .clipToBounds(),
        contentAlignment = when (align) {
            PreviewLyricAlign.LEFT -> Alignment.TopStart
            PreviewLyricAlign.CENTER -> Alignment.TopCenter
            PreviewLyricAlign.RIGHT -> Alignment.TopEnd
        },
    ) {
        for (index in start..end) {
            if (index == focus) continue
            val line = lines.getOrNull(index) ?: continue
            val text = line.text.trim()
            if (text.isEmpty()) continue
            val slot = index - visualFocus
            if (slot < -1.02f || slot > showCount + 0.02f) continue
            val fadeTop = if (slot < 0f) (slot + 1f).coerceIn(0f, 1f) else 1f
            val fadeBot = if (slot > showCount - 1f) {
                (showCount - slot).coerceIn(0f, 1f)
            } else {
                1f
            }
            val alpha = fadeTop * fadeBot
            if (alpha < 0.02f) continue
            val companion = if (showCompanionOnOthers) companions.getOrNull(index) else null
            val pair = previewOrderedPair(line, companion, originalOnTop)
            val style = TextStyle(
                color = upcomingColor,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = upcomingFs.sp,
                lineHeight = (upcomingFs * 1.28f).sp,
                textAlign = textAlign,
            )
            val transStyle = style.copy(
                fontSize = (upcomingFs * 0.88f).sp,
                lineHeight = (upcomingFs * 1.28f * 0.88f).sp,
                fontWeight = FontWeight.Medium,
                color = upcomingColor.copy(alpha = upcomingColor.alpha * 0.88f),
            )
            val y = yOf(slot)
            key(index) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = y
                            this.alpha = alpha
                        },
                ) {
                    PreviewLyricPairColumn(
                        upper = pair.first,
                        lower = pair.second,
                        upperStyle = style,
                        lowerStyle = transStyle,
                        isPlaying = false,
                        words = emptyList(),
                        secondaryWords = emptyList(),
                        positionMs = 0L,
                        playingColor = playingColor,
                        karaokeUnplayed = karaokeUnplayed,
                        clockRunning = false,
                    )
                }
            }
        }
        // 播放行单独挂载：切句只换文案，不销毁逐字时钟。
        // 精美切句已经在整列 translationY 上完成，不要再走歌词页的入场抬起。
        if (playLine.text.trim().isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = yOf(playSlot)
                        this.alpha = playAlpha
                    },
            ) {
                PreviewPlayingKaraokeHost(
                    upper = playPair.first,
                    lower = playPair.second,
                    upperStyle = playStyle,
                    lowerStyle = playTransStyle,
                    positionMs = positionMs,
                    playingColor = playingColor,
                    karaokeUnplayed = karaokeUnplayed,
                    clockRunning = live && clockRunning,
                )
            }
        }
    }
}

@Composable
private fun PreviewPlayingKaraokeHost(
    upper: LrcLine,
    lower: LrcLine?,
    upperStyle: TextStyle,
    lowerStyle: TextStyle,
    positionMs: Long,
    playingColor: Color,
    karaokeUnplayed: Color,
    clockRunning: Boolean,
) {
    val smoothPos = rememberSmoothedLyricPositionMs(positionMs, clockRunning)
    val words = remember(upper.timeMs, upper.text, upper.words) {
        upper.karaokeWords()
    }
    val secondaryWords = remember(lower?.timeMs, lower?.text, lower?.words) {
        lower?.karaokeWords().orEmpty()
    }
    PreviewLyricPairColumn(
        upper = upper,
        lower = lower,
        upperStyle = upperStyle,
        lowerStyle = lowerStyle,
        isPlaying = true,
        words = words,
        secondaryWords = secondaryWords,
        positionMs = smoothPos,
        playingColor = playingColor,
        karaokeUnplayed = karaokeUnplayed,
        clockRunning = false,
    )
}

@Composable
private fun PreviewLyricStaticColumn(
    lines: List<LrcLine>,
    companions: List<LrcLine?>,
    originalOnTop: Boolean,
    showCompanionOnOthers: Boolean,
    focus: Int,
    showCount: Int,
    textAlign: TextAlign,
    columnAlign: Alignment.Horizontal,
    playingColor: Color,
    upcomingColor: Color,
    playFs: Float,
    upcomingFs: Float,
    gap: Float,
    modifier: Modifier,
) {
    val visibleLines = buildList {
        for (i in 0 until showCount) {
            val line = lines.getOrNull(focus + i) ?: break
            val text = line.text.trim()
            if (text.isNotEmpty()) add((focus + i) to line)
        }
    }
    if (visibleLines.isEmpty()) return
    Column(
        modifier,
        horizontalAlignment = columnAlign,
        verticalArrangement = Arrangement.spacedBy(gap.dp),
    ) {
        visibleLines.forEachIndexed { slot, (index, line) ->
            val isPlaying = slot == 0
            val companion = when {
                isPlaying -> companions.getOrNull(index)
                showCompanionOnOthers -> companions.getOrNull(index)
                else -> null
            }
            val pair = previewOrderedPair(line, companion, originalOnTop)
            val fontSp = if (isPlaying) playFs else upcomingFs
            val color = if (isPlaying) playingColor else upcomingColor
            val style = TextStyle(
                color = color,
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = fontSp.sp,
                lineHeight = (fontSp * 1.28f).sp,
                textAlign = textAlign,
            )
            val transStyle = style.copy(
                fontSize = (fontSp * 0.88f).sp,
                lineHeight = (fontSp * 1.28f * 0.88f).sp,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = color.alpha * 0.88f),
            )
            PreviewLyricPairColumn(
                upper = pair.first,
                lower = pair.second,
                upperStyle = style,
                lowerStyle = transStyle,
                isPlaying = isPlaying,
                words = emptyList(),
                secondaryWords = emptyList(),
                positionMs = 0L,
                playingColor = playingColor,
                karaokeUnplayed = upcomingColor,
                clockRunning = false,
            )
        }
    }
}

@Composable
private fun PreviewLyricPairColumn(
    upper: LrcLine,
    lower: LrcLine?,
    upperStyle: TextStyle,
    lowerStyle: TextStyle,
    isPlaying: Boolean,
    words: List<LyricWord>,
    secondaryWords: List<LyricWord>,
    positionMs: Long,
    playingColor: Color,
    karaokeUnplayed: Color,
    clockRunning: Boolean,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PreviewLyricOneLine(
            line = upper,
            style = upperStyle,
            isPlaying = isPlaying,
            words = words,
            positionMs = positionMs,
            playingColor = playingColor,
            karaokeUnplayed = karaokeUnplayed,
            clockRunning = clockRunning,
        )
        if (lower != null && lower.text.trim().isNotEmpty()) {
            PreviewLyricOneLine(
                line = lower,
                style = lowerStyle,
                isPlaying = isPlaying,
                words = secondaryWords,
                positionMs = positionMs,
                playingColor = playingColor,
                karaokeUnplayed = karaokeUnplayed,
                clockRunning = clockRunning,
            )
        }
    }
}

@Composable
private fun PreviewLyricOneLine(
    line: LrcLine,
    style: TextStyle,
    isPlaying: Boolean,
    words: List<LyricWord>,
    positionMs: Long,
    playingColor: Color,
    karaokeUnplayed: Color,
    clockRunning: Boolean,
) {
    val text = line.text.trim()
    if (words.isNotEmpty()) {
        KaraokeLyricText(
            words = words,
            positionMs = positionMs,
            playingColor = playingColor,
            unplayedColor = karaokeUnplayed,
            tracking = clockRunning,
            style = style,
            modifier = Modifier.fillMaxWidth(),
            maxLines = if (isPlaying) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = text,
            style = style,
            modifier = Modifier.fillMaxWidth(),
            maxLines = if (isPlaying) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun previewOrderedPair(
    original: LrcLine,
    translation: LrcLine?,
    originalOnTop: Boolean,
): Pair<LrcLine, LrcLine?> {
    val trans = translation ?: return original to null
    return if (originalOnTop) original to trans else trans to original
}
