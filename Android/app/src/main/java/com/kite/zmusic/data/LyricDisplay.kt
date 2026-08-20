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
 * yrc 字时间一般是歌曲绝对毫秒；仅当明显是行内偏移时才加上行开始。
 */
internal fun absoluteWordTimes(lineStartMs: Long, words: List<LyricWord>): List<LyricWord> {
    if (words.isEmpty()) return words
    val first = words.first().timeMs
    val looksRelative = first < 1_000L && lineStartMs - first > 1_500L
    if (!looksRelative) return words
    return words.map { word -> word.copy(timeMs = word.timeMs + lineStartMs) }
}

/** 当前行已开始但所有字都还没到点：把字时间对齐到本行，避免整行停在未播放色。 */
internal fun LrcLine.karaokeWords(positionMs: Long): List<LyricWord> {
    val base = absoluteWordTimes(timeMs, words)
    if (base.isEmpty() || positionMs < timeMs) return base
    if (base.any { positionMs >= it.timeMs }) return base
    val origin = base.first().timeMs
    return base.map { word -> word.copy(timeMs = timeMs + (word.timeMs - origin)) }
}
