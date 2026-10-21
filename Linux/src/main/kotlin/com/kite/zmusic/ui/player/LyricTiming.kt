package com.kite.zmusic.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricWord
import kotlin.math.abs

fun lyricActiveIndex(lines: List<LrcLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var l = 0
    var r = lines.lastIndex
    var ans = -1
    while (l <= r) {
        val m = (l + r) ushr 1
        if (lines[m].timeMs <= positionMs) {
            ans = m
            l = m + 1
        } else {
            r = m - 1
        }
    }
    return ans
}

data class LyricAnimTiming(
    val leadMs: Long,
    val durationMs: Int,
)

const val LyricAnimLeadMs = 260L
const val LyricAnimDurationMs = 260

fun lyricLineSpanMs(
    lines: List<LrcLine>,
    index: Int,
    trackDurationMs: Long,
): Long {
    if (index !in lines.indices) return 4_000L
    val start = lines[index].timeMs
    val end = when {
        index + 1 < lines.size -> lines[index + 1].timeMs
        trackDurationMs > start -> trackDurationMs
        else -> start + 4_000L
    }
    return (end - start).coerceAtLeast(1L)
}

fun lyricAnimTiming(
    lines: List<LrcLine>,
    positionMs: Long,
    trackDurationMs: Long = 0L,
): LyricAnimTiming {
    if (lines.isEmpty()) {
        return LyricAnimTiming(LyricAnimLeadMs, LyricAnimDurationMs)
    }
    val real = lyricActiveIndex(lines, positionMs)
    val currSpan = when {
        real < 0 -> lines.first().timeMs.coerceAtLeast(1L)
        else -> lyricLineSpanMs(lines, real, trackDurationMs)
    }
    val nextSpan = when {
        real < 0 -> if (lines.size > 1) lyricLineSpanMs(lines, 0, trackDurationMs) else currSpan
        real + 1 in lines.indices -> lyricLineSpanMs(lines, real + 1, trackDurationMs)
        else -> currSpan
    }
    val window = minOf(currSpan, nextSpan).coerceAtLeast(1L)
    return lyricAnimTimingForWindow(window)
}

fun lyricAnimTimingForWindow(windowMs: Long): LyricAnimTiming {
    val w = windowMs.coerceAtLeast(1L)
    val durationMs = when {
        w < 280L -> (w * 0.62f).toInt().coerceIn(140, 180)
        w < 480L -> (w * 0.54f).toInt().coerceIn(170, 230)
        w < 800L -> (w * 0.48f).toInt().coerceIn(210, 280)
        w < 1_500L -> (w * 0.42f).toInt().coerceIn(260, 340)
        else -> 380
    }
    val leadMs = (durationMs * 0.88f).toLong()
        .coerceAtMost((w * 0.50f).toLong())
        .coerceAtLeast(56L)
    return LyricAnimTiming(leadMs = leadMs, durationMs = durationMs)
}

fun lyricAnimActiveIndex(
    lines: List<LrcLine>,
    positionMs: Long,
    trackDurationMs: Long = 0L,
): Int {
    val lead = lyricAnimTiming(lines, positionMs, trackDurationMs).leadMs
    return lyricActiveIndex(lines, positionMs + lead)
}

/** 这一句的结束时刻：下一句开始，或曲终。 */
fun lyricLineEndMs(
    lines: List<LrcLine>,
    index: Int,
    trackDurationMs: Long,
): Long {
    if (index !in lines.indices) return Long.MAX_VALUE
    return lines[index].timeMs + lyricLineSpanMs(lines, index, trackDurationMs)
}

/**
 * 正在显示的这一句会提前多久被切走。
 * 与 [lyricAnimTiming] 在本句仍是真实播放句时算出的提前量相同。
 */
fun lyricSweepLeadMs(
    lines: List<LrcLine>,
    displayedIndex: Int,
    trackDurationMs: Long,
): Long {
    if (displayedIndex !in lines.indices) return 0L
    val curr = lyricLineSpanMs(lines, displayedIndex, trackDurationMs)
    val next = if (displayedIndex + 1 in lines.indices) {
        lyricLineSpanMs(lines, displayedIndex + 1, trackDurationMs)
    } else {
        curr
    }
    return lyricAnimTimingForWindow(minOf(curr, next)).leadMs
}

/** 逐字填色时钟：叠上切句提前量。 */
fun lyricSweepClockMs(positionMs: Long, leadMs: Long): Long =
    positionMs + leadMs.coerceAtLeast(0L)

/**
 * 逐字进度。切句带提前量，填色按字面时间会在切走时停在半亮。
 * 时钟加上同一提前量，并把字的结束收到本句结束。
 */
fun lyricSweepProgress(
    word: LyricWord,
    positionMs: Long,
    leadMs: Long,
    lineEndMs: Long,
): Float {
    val start = word.timeMs
    val natural = word.durationMs.coerceAtLeast(1L)
    val dur = if (lineEndMs > start) {
        minOf(natural, (lineEndMs - start).coerceAtLeast(1L))
    } else {
        natural
    }
    val clock = lyricSweepClockMs(positionMs, leadMs)
    val end = start + dur
    return when {
        clock <= start -> 0f
        clock >= end -> 1f
        else -> ((clock - start).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    }
}

fun lyricFocusIndex(lines: List<LrcLine>, activeIndex: Int): Int {
    if (lines.isEmpty()) return -1
    if (activeIndex < 0) return 0
    return activeIndex.coerceIn(0, lines.lastIndex)
}

fun lyricIsLive(lines: List<LrcLine>, activeIndex: Int, focusIndex: Int): Boolean =
    activeIndex >= 0 &&
        focusIndex == activeIndex &&
        lines.getOrNull(activeIndex)?.text?.isNotBlank() == true

internal val LyricSoftEasing = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1f)
internal val LyricSofterEasing = CubicBezierEasing(0.4f, 0.0f, 0.15f, 1f)

internal fun lyricResumeScrollSpec(distancePx: Float) = tween<Float>(
    durationMillis = ((abs(distancePx) / 2600f) * 1000f)
        .toInt()
        .coerceIn(300, 560),
    easing = LyricSoftEasing,
)

internal fun argbColor(argb: Int): Color = Color(
    red = ((argb shr 16) and 0xFF) / 255f,
    green = ((argb shr 8) and 0xFF) / 255f,
    blue = (argb and 0xFF) / 255f,
    alpha = ((argb ushr 24) and 0xFF) / 255f,
)

internal val LyricPlayingColor = argbColor(0xFFF8FAFC.toInt())
internal val LyricPlayedColor = argbColor(0xFFB8C0CC.toInt())
internal val LyricUnplayedColor = argbColor(0xFFDCE6F0.toInt())
internal val LyricBrowseSelect = Color(0xFFE8C4A0)

internal val LyricPlayingWeight = FontWeight.SemiBold
internal val LyricPlayedWeight = FontWeight.Light
internal val LyricUnplayedWeight = FontWeight.Normal
internal val LyricPlayedStyle = FontStyle.Italic
internal val LyricNormalStyle = FontStyle.Normal
