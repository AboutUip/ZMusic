package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 逐字歌词怎么填色。
 * [Word] 一个字整颗亮起，[Width] 高亮按字的宽度从左往右扫过。
 * 两种都要当前曲自带逐字时间，否则仍按行显示。
 */
enum class LyricWordRender {
    Line,
    Word,
    Width,
    ;

    val usesWordTiming: Boolean get() = this != Line

    companion object {
        fun fromStored(raw: String?): LyricWordRender = when (raw) {
            "word" -> Word
            "width" -> Width
            else -> Line
        }

        fun stored(mode: LyricWordRender): String = when (mode) {
            Word -> "word"
            Width -> "width"
            Line -> "line"
        }
    }
}

/**
 * 旧版只存了布尔开关。没有新键时，开着视为按字，关着视为按行。
 */
internal fun resolveLyricWordRender(storedMode: String?, legacyWordByWord: Boolean): LyricWordRender {
    if (storedMode.isNullOrEmpty()) {
        return if (legacyWordByWord) LyricWordRender.Word else LyricWordRender.Line
    }
    return LyricWordRender.fromStored(storedMode)
}

/**
 * 全局歌词渲染。
 * 按字、按字宽仅当当前曲有逐字歌词时生效。
 * 长句默认换行，也可改成水平滚动。
 */
class LyricRenderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _wordRender = MutableStateFlow(
        resolveLyricWordRender(
            storedMode = prefs.getString(KEY_WORD_RENDER, null),
            legacyWordByWord = prefs.getBoolean(KEY_WORD_BY_WORD, false),
        ),
    )
    val wordRender: StateFlow<LyricWordRender> = _wordRender.asStateFlow()

    private val _longLine = MutableStateFlow(
        LongLyricRender.fromStored(prefs.getString(KEY_LONG_LINE, null)),
    )
    val longLine: StateFlow<LongLyricRender> = _longLine.asStateFlow()

    fun setWordRender(mode: LyricWordRender) {
        if (mode == _wordRender.value) return
        prefs.edit()
            .putString(KEY_WORD_RENDER, LyricWordRender.stored(mode))
            .putBoolean(KEY_WORD_BY_WORD, mode.usesWordTiming)
            .apply()
        _wordRender.value = mode
    }

    fun setLongLine(mode: LongLyricRender) {
        if (mode == _longLine.value) return
        prefs.edit().putString(KEY_LONG_LINE, LongLyricRender.stored(mode)).apply()
        _longLine.value = mode
    }

    companion object {
        private const val PREFS = "zmusic_lyric_render"
        private const val KEY_WORD_BY_WORD = "word_by_word"
        private const val KEY_WORD_RENDER = "word_render"
        private const val KEY_LONG_LINE = "long_line"
    }
}
