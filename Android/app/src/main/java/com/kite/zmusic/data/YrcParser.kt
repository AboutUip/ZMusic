package com.kite.zmusic.data

/**
 * 网易云 `yrc` / `ytlrc`：`[行开始,行时长](字开始,字时长,未知)字...`
 */
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
        return LrcLine(
            timeMs = start,
            text = cleaned,
            words = absoluteWordTimes(start, words),
        )
    }
}
