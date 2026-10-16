package com.kite.zmusic.plugin

import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.playback.PlaybackMode

internal fun PlaybackMode.pluginName(): String = when (this) {
    PlaybackMode.ORDER -> "order"
    PlaybackMode.REPEAT_ONE -> "repeatOne"
    PlaybackMode.SHUFFLE -> "shuffle"
}

internal fun playbackModeFromPlugin(name: String): PlaybackMode? = when (name) {
    "order" -> PlaybackMode.ORDER
    "repeatOne" -> PlaybackMode.REPEAT_ONE
    "shuffle" -> PlaybackMode.SHUFFLE
    else -> null
}

internal object PluginLyricMaps {
    const val LINE_CAP = 800
    private const val WORD_CAP = 60

    fun indexAt(lines: List<LrcLine>, positionMs: Long): Int {
        var idx = -1
        val pos = positionMs.coerceAtLeast(0L)
        for (i in lines.indices) {
            if (lines[i].timeMs <= pos) idx = i else break
        }
        return idx
    }

    fun current(
        original: List<LrcLine>,
        translated: List<LrcLine>,
        positionMs: Long,
    ): Map<String, Any?>? {
        val index = indexAt(original, positionMs)
        val line = original.getOrNull(index) ?: return null
        val trans = translated.getOrNull(indexAt(translated, positionMs))
        return mapOf(
            "index" to index,
            "timeMs" to line.timeMs,
            "text" to line.text,
            "translation" to trans?.text,
        )
    }

    fun pack(
        original: List<LrcLine>,
        translated: List<LrcLine>,
        positionMs: Long,
    ): Map<String, Any?> {
        val now = current(original, translated, positionMs)
        return mapOf(
            "index" to (now?.get("index") ?: -1),
            "text" to now?.get("text"),
            "translation" to now?.get("translation"),
            "truncated" to (original.size > LINE_CAP || translated.size > LINE_CAP),
            "lines" to original.take(LINE_CAP).map { lineMap(it) },
            "translations" to translated.take(LINE_CAP).map { lineMap(it) },
        )
    }

    private fun lineMap(line: LrcLine): Map<String, Any?> = mapOf(
        "timeMs" to line.timeMs,
        "text" to line.text,
        "words" to line.words.take(WORD_CAP).map { word ->
            mapOf(
                "timeMs" to word.timeMs,
                "durationMs" to word.durationMs,
                "text" to word.text,
            )
        },
    )
}
