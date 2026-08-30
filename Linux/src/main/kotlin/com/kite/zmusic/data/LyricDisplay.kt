package com.kite.zmusic.data

fun LrcLine.karaokeWords(positionMs: Long): List<LyricWord> {
    val base = absoluteWordTimes(timeMs, words)
    if (base.isEmpty() || positionMs < timeMs) return base
    if (base.any { positionMs >= it.timeMs }) return base
    val origin = base.first().timeMs
    return base.map { word -> word.copy(timeMs = timeMs + (word.timeMs - origin)) }
}

private fun absoluteWordTimes(lineStartMs: Long, words: List<LyricWord>): List<LyricWord> {
    if (words.isEmpty()) return words
    val first = words.first().timeMs
    val looksRelative = first < 1_000L && lineStartMs - first > 1_500L
    if (!looksRelative) return words
    return words.map { word -> word.copy(timeMs = word.timeMs + lineStartMs) }
}
