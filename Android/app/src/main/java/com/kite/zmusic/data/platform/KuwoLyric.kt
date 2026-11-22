package com.kite.zmusic.data.platform

import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LrcParser
import com.kite.zmusic.data.LyricWord
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * 酷我歌词：`mlyric.kuwo.cn` 返回 zlib，逐字层再用固定字节异或。
 */
internal object KuwoLyric {
    fun load(http: OkHttpClient, rid: String): OpenLyricBundle? {
        if (rid.isBlank()) return null
        val request = Request.Builder()
            .url("http://mlyric.kuwo.cn/mobi.s?f=web&type=lyric&lrcx=1&rid=$rid&encode=utf8")
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()
        val bytes = runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.bytes()
            }
        }.getOrNull() ?: return null
        val text = decode(bytes) ?: return null
        val bundle = parse(text)
        return bundle.takeIf { it.original.isNotEmpty() }
    }

    fun decode(raw: ByteArray): String? {
        if (raw.size < 10) return null
        val head = raw.copyOfRange(0, 10).toString(Charsets.UTF_8).lowercase()
        if (!head.startsWith("tp=content")) return null
        val split = indexOf(raw, SEPARATOR)
        if (split < 0) return null
        val inflated = inflate(raw.copyOfRange(split + SEPARATOR.size, raw.size))
        if (inflated.isEmpty()) return null
        val cipher = runCatching { Base64.getMimeDecoder().decode(inflated.trim()) }.getOrNull() ?: return null
        val key = KEY
        for (i in cipher.indices) {
            cipher[i] = ((cipher[i].toInt() and 0xFF) xor (key[i % key.size].toInt() and 0xFF)).toByte()
        }
        return cipher.toString(Charsets.UTF_8)
    }

    fun parse(raw: String): OpenLyricBundle {
        val div = kuwoDiv(raw)
        val items = ArrayList<Row>()
        for (line in raw.lineSequence()) {
            val match = timeRegex.find(line.trim()) ?: continue
            val timeMs = toTimeMs(match.groupValues[1], match.groupValues[2], match.groupValues[3])
            items.add(Row(time = match.groupValues[1] + ":" + match.groupValues[2] + "." + match.groupValues[3], timeMs = timeMs, body = match.groupValues[4]))
        }
        val (originalRows, translatedRows) = splitTranslation(items)
        return OpenLyricBundle(
            original = originalRows.mapNotNull { it.toLine(div) },
            translated = translatedRows.mapNotNull { it.toLine(div) },
        )
    }

    private fun kuwoDiv(raw: String): Pair<Int, Int> {
        val match = Regex("""\[kuwo:(\d+)]""").find(raw) ?: return 1 to 1
        val value = match.groupValues[1].toIntOrNull(8) ?: return 1 to 1
        val first = value / 10
        val second = value % 10
        if (first == 0 || second == 0) return 1 to 1
        return first to second
    }

    private fun splitTranslation(items: List<Row>): Pair<List<Row>, List<Row>> {
        val seen = HashSet<String>()
        val original = ArrayList<Row>()
        val translated = ArrayList<Row>()
        var wordTimed = false
        for (item in items) {
            if (!wordTimed && item.body.trimStart().startsWith("<")) wordTimed = true
            if (item.time in seen) {
                if (original.size < 2) continue
                val popped = original.removeAt(original.lastIndex)
                translated.add(popped.copy(time = original.last().time, timeMs = original.last().timeMs))
                original.add(item)
            } else {
                original.add(item)
                seen.add(item.time)
            }
        }
        if (!wordTimed && translated.size > original.size * 0.3 && original.size - translated.size > 6) {
            return items to emptyList()
        }
        return original to translated
    }

    private fun Row.toLine(div: Pair<Int, Int>): LrcLine? {
        val words = words(body, timeMs, div.first, div.second)
        val plain = if (words.isNotEmpty()) {
            words.joinToString("") { it.text }
        } else {
            body.replace(Regex("""<[^>]*>"""), "")
        }
        val text = LrcParser.sanitizeLyricText(plain) ?: return null
        return LrcLine(timeMs = timeMs, text = text, words = words)
    }

    private fun words(body: String, lineStart: Long, div: Int, div2: Int): List<LyricWord> {
        val spans = ArrayList<Span>()
        var prev: Span? = null
        for (match in wordRegex.findAll(body)) {
            val left = match.groupValues[1].toLongOrNull() ?: continue
            val right = match.groupValues[2].toLongOrNull() ?: continue
            val start = abs(left + right) / (div * 2)
            val end = abs(left - right) / (div2 * 2) + start
            val earlier = prev
            if (earlier != null && start < earlier.end) {
                earlier.end = start
                if (earlier.start > earlier.end) earlier.start = earlier.end
            }
            val span = Span(start, end, match.groupValues[3])
            spans.add(span)
            prev = span
        }
        return spans.mapNotNull { span ->
            if (span.text.isEmpty()) return@mapNotNull null
            LyricWord(
                timeMs = lineStart + span.start,
                durationMs = (span.end - span.start).coerceAtLeast(0L),
                text = span.text,
            )
        }
    }

    private fun toTimeMs(mm: String, ss: String, frac: String): Long {
        val minutes = mm.toLongOrNull() ?: return 0L
        val seconds = ss.toLongOrNull() ?: return 0L
        val sub = when (frac.length) {
            0 -> 0L
            1 -> (frac.toLongOrNull() ?: 0L) * 100L
            2 -> (frac.toLongOrNull() ?: 0L) * 10L
            else -> frac.take(3).toLongOrNull() ?: 0L
        }
        return (minutes * 60L + seconds) * 1000L + sub
    }

    private fun inflate(data: ByteArray): String {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                out.write(buf, 0, n)
            }
            out.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        } finally {
            inflater.end()
        }
    }

    private fun indexOf(data: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || data.size < needle.size) return -1
        for (i in 0..data.size - needle.size) {
            var ok = true
            for (j in needle.indices) {
                if (data[i + j] != needle[j]) {
                    ok = false
                    break
                }
            }
            if (ok) return i
        }
        return -1
    }

    private data class Row(val time: String, val timeMs: Long, val body: String)

    private class Span(var start: Long, var end: Long, val text: String)

    private val timeRegex = Regex("""^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)$""")
    private val wordRegex = Regex("""<(-?\d+),(-?\d+)(?:,-?\d+)?>([^<]*)""")
    private val KEY = "yeelion".toByteArray(Charsets.UTF_8)
    private val SEPARATOR = "\r\n\r\n".toByteArray(Charsets.UTF_8)
}
