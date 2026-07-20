package com.kite.zmusic.data

/**
 * 网易云 [mm:ss.xx] / [mm:ss] 行歌词解析。
 */
data class LrcLine(
    val timeMs: Long,
    val text: String,
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

    /**
     * 去掉空白/无意义占位，获取后即净化，UI 不再出现空行。
     * @return null 表示应丢弃该行
     */
    fun sanitizeLyricText(raw: String): String? {
        val t = raw.trim()
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (t.isEmpty()) return null
        // 纯标点 / 占位符不算有效歌词
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
