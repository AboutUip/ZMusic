@file:Suppress("UnusedBoxWithConstraintsScope")

package com.kite.zmusic.ui.player

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricWord
import com.kite.zmusic.data.lyricWordProgress
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

/**
 * 歌词切换动画时序：短句自动压缩，避免固定 260ms 盖过行间隔。
 * - [leadMs]：提前量。切句从这里开始
 * - [durationMs]：入场时长，与 [leadMs] 相同，过渡在下一句时间戳上收住
 */
data class LyricAnimTiming(
    val leadMs: Long,
    val durationMs: Int,
)

/** 默认（长句）上限；短句会按行间隔下压。 */
const val LyricAnimLeadMs = 260L
const val LyricAnimDurationMs = 260

/** 当前行原始时长（到下一行 / 曲终），不做 800ms 抬底。 */
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

/**
 * 按「当前行 / 下一行」中较短的间隔，计算可完成的过渡时序。
 * 保证动画约占窗口的 ~40%，并留出可见的定格段。
 */
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
    // 短句提高占比与下限，保证仍有可感知过渡；长句略放宽更丝滑
    val wanted = when {
        w < 280L -> (w * 0.62f).toInt().coerceIn(140, 180)
        w < 480L -> (w * 0.54f).toInt().coerceIn(170, 230)
        w < 800L -> (w * 0.48f).toInt().coerceIn(210, 280)
        w < 1_500L -> (w * 0.42f).toInt().coerceIn(260, 340)
        else -> 380
    }
    // 入场时长就是提前量。跟滚若比提前量更长，新句会在歌声出来之后才落到播放位。
    val leadMs = wanted.toLong()
        .coerceAtMost((w * 0.50f).toLong())
        .coerceAtLeast(56L)
    return LyricAnimTiming(leadMs = leadMs, durationMs = leadMs.toInt())
}

/** 供 UI 动画用的激活下标（已含自适应提前量）。 */
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
 * 与 [lyricAnimTiming] 在本句仍是真实播放句时算出的 [LyricAnimTiming.leadMs] 相同。
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

/**
 * 最后一个字的渲染时长。其余的字用原来的时长。
 * 只把切句动画提前量从这一字的总长度里减去，它会在句子开始切走时填完。
 */
fun lyricLastWordRenderDurationMs(durationMs: Long, switchLeadMs: Long): Long {
    val natural = durationMs.coerceAtLeast(1L)
    if (switchLeadMs <= 0L) return natural
    return (natural - switchLeadMs).coerceAtLeast(1L)
}

/** 逐字、逐字宽按字面时间填色。[renderShortenMs] 只给当前句最后一个字。 */
fun lyricSweepProgress(
    word: LyricWord,
    positionMs: Long,
    renderShortenMs: Long = 0L,
): Float {
    val dur = lyricLastWordRenderDurationMs(word.durationMs, renderShortenMs)
    val timed = if (dur == word.durationMs) word else word.copy(durationMs = dur)
    return lyricWordProgress(timed, positionMs)
}

/** 播放位下标：有歌词时永不返回 -1。空行应在解析阶段已剔除。 */
fun lyricFocusIndex(lines: List<LrcLine>, activeIndex: Int): Int {
    if (lines.isEmpty()) return -1
    if (activeIndex < 0) return 0
    return activeIndex.coerceIn(0, lines.lastIndex)
}

/** 当前是否已进入「演唱中」样式（含动画提前量）。 */
fun lyricIsLive(lines: List<LrcLine>, activeIndex: Int, focusIndex: Int): Boolean =
    activeIndex >= 0 &&
        focusIndex == activeIndex &&
        lines.getOrNull(activeIndex)?.text?.isNotBlank() == true

/** 短句用更陡的进出；长句保持原包络。目标值需再经动画插值，避免硬切。 */
internal fun lyricHaloStrength(progress: Float, lineSpanMs: Long): Float {
    val p = progress.coerceIn(0f, 1f)
    val short = lineSpanMs < 1_000L
    return if (short) {
        when {
            p < 0.22f -> (p / 0.22f) * (p / 0.22f) // ease-in 起光
            p < 0.55f -> 1f
            else -> {
                val t = ((1f - p) / 0.45f).coerceIn(0f, 1f)
                t * t
            }
        }
    } else {
        when {
            p < 0.16f -> {
                val t = p / 0.16f
                t * t * (3f - 2f * t) // smoothstep
            }
            p < 0.68f -> 1f
            else -> {
                val t = ((1f - p) / 0.32f).coerceIn(0f, 1f)
                t * t * (3f - 2f * t)
            }
        }
    }
}

/** 歌词过渡通用缓动：慢起慢收，偏温柔。 */
internal val LyricSoftEasing = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1f)
internal val LyricSofterEasing = CubicBezierEasing(0.4f, 0.0f, 0.15f, 1f)
/** 选句退出回正：快速柔和（约跟跟滚同级），按距离略伸缩 */
internal fun lyricResumeScrollSpec(distancePx: Float) = tween<Float>(
    durationMillis = ((abs(distancePx) / 2600f) * 1000f)
        .toInt()
        .coerceIn(300, 560),
    easing = LyricSoftEasing,
)

