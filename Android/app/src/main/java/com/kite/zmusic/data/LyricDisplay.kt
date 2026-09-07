package com.kite.zmusic.data

internal fun LrcLine.sanitizedForDisplay(): LrcLine? {
    if (words.isNotEmpty()) {
        val kept = words.mapNotNull { word ->
            val t = word.text.replace('\u00A0', ' ')
            if (t.isEmpty()) null else word.copy(text = t)
        }
        if (kept.isEmpty()) return null
        val joined = kept.joinToString("") { it.text }
        val cleaned = LrcParser.sanitizeLyricText(joined) ?: return null
        return copy(text = cleaned, words = absoluteWordTimes(timeMs, kept))
    }
    return LrcParser.sanitizeLyricText(text)?.let { copy(text = it) }
}

internal data class DisplayLyricBundle(
    val lines: List<LrcLine>,
    val companions: List<LrcLine?>,
)

internal fun pickDisplayLyricLines(
    original: List<LrcLine>,
    translated: List<LrcLine>,
    wordOriginal: List<LrcLine>,
    wordTranslated: List<LrcLine>,
    preferTranslation: Boolean,
    wordByWord: Boolean,
): List<LrcLine> {
    val lineSource = if (preferTranslation && translated.isNotEmpty()) translated else original
    if (!wordByWord) {
        return lineSource.map { line ->
            if (line.words.isEmpty()) line else line.copy(words = emptyList())
        }
    }
    val wordSource = when {
        preferTranslation && wordTranslated.isNotEmpty() -> wordTranslated
        preferTranslation && translated.isNotEmpty() -> emptyList()
        else -> wordOriginal
    }
    if (wordSource.isEmpty()) return lineSource
    return wordSource.map { line ->
        line.copy(words = absoluteWordTimes(line.timeMs, line.words))
    }
}

/**
 * 竖屏翻译：覆盖仍走单列；并存时 [lines] 永远是原文时间轴，
 * [companions] 按时间对齐的译文（无译文则为 null）。
 */
internal fun pickDisplayLyricBundle(
    original: List<LrcLine>,
    translated: List<LrcLine>,
    wordOriginal: List<LrcLine>,
    wordTranslated: List<LrcLine>,
    preferTranslation: Boolean,
    coexist: Boolean,
    wordByWord: Boolean,
): DisplayLyricBundle {
    if (!preferTranslation || translated.isEmpty()) {
        return DisplayLyricBundle(
            lines = pickDisplayLyricLines(
                original = original,
                translated = translated,
                wordOriginal = wordOriginal,
                wordTranslated = wordTranslated,
                preferTranslation = false,
                wordByWord = wordByWord,
            ),
            companions = emptyList(),
        )
    }
    if (!coexist) {
        return DisplayLyricBundle(
            lines = pickDisplayLyricLines(
                original = original,
                translated = translated,
                wordOriginal = wordOriginal,
                wordTranslated = wordTranslated,
                preferTranslation = true,
                wordByWord = wordByWord,
            ),
            companions = emptyList(),
        )
    }
    val main = pickDisplayLyricLines(
        original = original,
        translated = emptyList(),
        wordOriginal = wordOriginal,
        wordTranslated = emptyList(),
        preferTranslation = false,
        wordByWord = wordByWord,
    )
    val trans = pickDisplayLyricLines(
        original = translated,
        translated = emptyList(),
        wordOriginal = wordTranslated,
        wordTranslated = emptyList(),
        preferTranslation = false,
        wordByWord = wordByWord,
    )
    return DisplayLyricBundle(
        lines = main,
        companions = alignLyricCompanions(main, trans),
    )
}

internal fun alignLyricCompanions(
    primary: List<LrcLine>,
    translated: List<LrcLine>,
): List<LrcLine?> {
    if (primary.isEmpty() || translated.isEmpty()) {
        return List(primary.size) { null }
    }
    if (primary.size == translated.size) {
        var maxSkew = 0L
        for (i in primary.indices) {
            val skew = kotlin.math.abs(primary[i].timeMs - translated[i].timeMs)
            if (skew > maxSkew) maxSkew = skew
        }
        if (maxSkew <= 1_200L) return translated
    }
    return primary.map { src ->
        translated.minByOrNull { kotlin.math.abs(it.timeMs - src.timeMs) }
            ?.takeIf { kotlin.math.abs(it.timeMs - src.timeMs) <= 1_800L }
    }
}

/**
 * yrc 字时间一般是歌曲绝对毫秒；仅当明显是行内偏移时才加上行开始。
 */
internal fun absoluteWordTimes(lineStartMs: Long, words: List<LyricWord>): List<LyricWord> {
    if (words.isEmpty()) return words
    val first = words.first().timeMs
    val looksRelative = first < 1_000L && lineStartMs - first > 1_500L
    if (!looksRelative) return words
    return words.map { word -> word.copy(timeMs = word.timeMs + lineStartMs) }
}

/**
 * 逐字渲染用的字时间。行开始早于首字时，把首字起点拉到行首并拉长时长，
 * 避免切到播放位后整行停在未唱色。时间轴固定，不会在首字真正开唱时跳回原时间
 * （否则第一个字会把填色动画播两遍）。
 */
internal fun LrcLine.karaokeWords(): List<LyricWord> {
    val base = absoluteWordTimes(timeMs, words)
    if (base.isEmpty()) return base
    val first = base.first()
    val lead = first.timeMs - timeMs
    if (lead <= 0L) return base
    return buildList(base.size) {
        add(first.copy(timeMs = timeMs, durationMs = first.durationMs + lead))
        addAll(base.subList(1, base.size))
    }
}
