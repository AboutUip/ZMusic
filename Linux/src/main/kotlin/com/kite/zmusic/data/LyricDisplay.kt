package com.kite.zmusic.data

fun LrcLine.karaokeWords(): List<LyricWord> {
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

private fun absoluteWordTimes(lineStartMs: Long, words: List<LyricWord>): List<LyricWord> {
    if (words.isEmpty()) return words
    val first = words.first().timeMs
    val looksRelative = first < 1_000L && lineStartMs - first > 1_500L
    if (!looksRelative) return words
    return words.map { word -> word.copy(timeMs = word.timeMs + lineStartMs) }
}
