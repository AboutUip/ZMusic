package com.kite.zmusic.data

/** 长歌词一行放不下时怎么画。默认换行。 */
enum class LongLyricRender {
    Wrap,
    Horizontal,
    ;

    companion object {
        fun fromStored(raw: String?): LongLyricRender = when (raw) {
            "horizontal" -> Horizontal
            else -> Wrap
        }

        fun stored(mode: LongLyricRender): String = when (mode) {
            Horizontal -> "horizontal"
            Wrap -> "wrap"
        }
    }
}

enum class LongLyricAlign {
    Start,
    Center,
    End,
}

/** 当前句的滚动依据：整句时间，或逐字已经唱到的位置。 */
sealed class LongLyricMotion {
    data class Timed(val progress: Float) : LongLyricMotion()
    data class Karaoke(val sungFraction: Float) : LongLyricMotion()
}

/** 句内进度。span 至少按 1ms 算，避免除零。 */
fun longLyricProgress(positionMs: Long, lineStartMs: Long, lineSpanMs: Long): Float {
    val span = lineSpanMs.coerceAtLeast(1L)
    return ((positionMs - lineStartMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
}

/**
 * 句首、句尾各留一段停住，中间再移过去，方便把开头和结尾读完。
 */
fun longLyricTimedFraction(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val hold = 0.12f
    return when {
        p <= hold -> 0f
        p >= 1f - hold -> 1f
        else -> (p - hold) / (1f - 2f * hold)
    }
}

/**
 * 已唱字符占整句的比例，用来让滚动跟着正在唱的字走。
 * [lineEndMs] 把超出本句的字时长收进来，和逐字宽填色用同一句尾。
 */
fun longLyricSungFraction(
    words: List<LyricWord>,
    positionMs: Long,
    lineEndMs: Long = Long.MAX_VALUE,
): Float {
    val total = words.sumOf { it.text.length }
    if (total <= 0) return 0f
    var sung = 0f
    for (word in words) {
        val len = word.text.length.toFloat()
        if (len <= 0f) continue
        val start = word.timeMs
        val natural = word.durationMs.coerceAtLeast(1L)
        val dur = if (lineEndMs > start) {
            minOf(natural, (lineEndMs - start).coerceAtLeast(1L))
        } else {
            natural
        }
        val end = start + dur
        when {
            positionMs >= end -> sung += len
            positionMs > start -> {
                sung += len * ((positionMs - start).toFloat() / dur.toFloat())
                return (sung / total).coerceIn(0f, 1f)
            }
            else -> return (sung / total).coerceIn(0f, 1f)
        }
    }
    return 1f
}

fun longLyricOffsetPx(
    textWidth: Float,
    viewportWidth: Float,
    align: LongLyricAlign,
    motion: LongLyricMotion,
): Float {
    if (textWidth <= 0f || viewportWidth <= 0f) return 0f
    val overflow = textWidth - viewportWidth
    if (overflow <= 1f) return longLyricRestOffset(textWidth, viewportWidth, align)
    return when (motion) {
        is LongLyricMotion.Timed -> -overflow * longLyricTimedFraction(motion.progress)
        is LongLyricMotion.Karaoke -> {
            val sungX = motion.sungFraction.coerceIn(0f, 1f) * textWidth
            val desired = sungX - viewportWidth * 0.32f
            -desired.coerceIn(0f, overflow)
        }
    }
}

private fun longLyricRestOffset(
    textWidth: Float,
    viewportWidth: Float,
    align: LongLyricAlign,
): Float = when (align) {
    LongLyricAlign.Start -> 0f
    LongLyricAlign.End -> viewportWidth - textWidth
    LongLyricAlign.Center -> (viewportWidth - textWidth) / 2f
}
