package com.kite.zmusic.data

data class LyricWord(
    val timeMs: Long,
    val durationMs: Long,
    val text: String,
)

data class LrcLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
)

object LrcParser {

    private val lineRegex = Regex("""^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)$""")

    fun parse(raw: String): List<LrcLine> {
        val out = ArrayList<LrcLine>()
        for (line in raw.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val m = lineRegex.find(t) ?: continue
            val mm = m.groupValues[1].toLongOrNull() ?: continue
            val ss = m.groupValues[2].toLongOrNull() ?: continue
            val frac = m.groupValues[3]
            val subMs = parseFractionMs(frac)
            val text = sanitizeLyricText(m.groupValues[4]) ?: continue
            val base = (mm * 60L + ss) * 1000L + subMs
            out.add(LrcLine(base, text))
        }
        out.sortBy { it.timeMs }
        return out
    }

    fun sanitizeLyricText(raw: String): String? {
        val t = raw.trim()
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (t.isEmpty()) return null
        if (t.all { it.isWhitespace() || it in "·.•…-_—~/|" }) return null
        return t
    }

    private fun parseFractionMs(frac: String): Long {
        if (frac.isEmpty()) return 0L
        return when (frac.length) {
            1 -> frac.toLongOrNull()?.times(100L) ?: 0L
            2 -> frac.toLongOrNull()?.times(10L) ?: 0L
            else -> frac.take(3).toLongOrNull()?.coerceAtMost(999L) ?: 0L
        }
    }
}

object YrcParser {

    private val headerRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val wordRegex = Regex("""[\(<](\d+),(\d+)(?:,-?\d+)*[\)>]([^<(]*)""")

    fun parse(raw: String): List<LrcLine> {
        val out = ArrayList<LrcLine>()
        for (line in raw.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("{")) continue
            val parsed = parseLine(t) ?: continue
            out.add(parsed)
        }
        out.sortBy { it.timeMs }
        return out
    }

    private fun parseLine(raw: String): LrcLine? {
        val m = headerRegex.find(raw) ?: return null
        val start = m.groupValues[1].toLongOrNull() ?: return null
        val body = m.groupValues[3]
        val words = ArrayList<LyricWord>()
        for (wm in wordRegex.findAll(body)) {
            val timeMs = wm.groupValues[1].toLongOrNull() ?: continue
            val durationMs = wm.groupValues[2].toLongOrNull()?.coerceAtLeast(0L) ?: continue
            val text = wm.groupValues[3]
            if (text.isEmpty()) continue
            words.add(LyricWord(timeMs = timeMs, durationMs = durationMs, text = text))
        }
        if (words.isEmpty()) return null
        val text = words.joinToString("") { it.text }
        val cleaned = LrcParser.sanitizeLyricText(text) ?: return null
        return LrcLine(timeMs = start, text = cleaned, words = words)
    }
}

data class LyricPack(
    val original: List<LrcLine>,
    val translated: List<LrcLine> = emptyList(),
    val wordOriginal: List<LrcLine> = emptyList(),
    val translatedWordLyricLines: List<LrcLine> = emptyList(),
    val translationResolved: Boolean = false,
) {
    companion object {
        val Empty = LyricPack(
            original = emptyList(),
            translated = emptyList(),
            wordOriginal = emptyList(),
            translatedWordLyricLines = emptyList(),
            translationResolved = true,
        )
    }
}
