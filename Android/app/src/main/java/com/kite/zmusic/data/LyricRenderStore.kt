package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局歌词渲染。
 * 按字仅当当前曲有逐字歌词时生效。
 * 长句默认换行，也可改成水平滚动。
 */
class LyricRenderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _wordByWord = MutableStateFlow(prefs.getBoolean(KEY_WORD_BY_WORD, false))
    val wordByWord: StateFlow<Boolean> = _wordByWord.asStateFlow()

    private val _longLine = MutableStateFlow(
        LongLyricRender.fromStored(prefs.getString(KEY_LONG_LINE, null)),
    )
    val longLine: StateFlow<LongLyricRender> = _longLine.asStateFlow()

    fun current(): Boolean = _wordByWord.value

    fun setWordByWord(enabled: Boolean) {
        if (enabled == _wordByWord.value) return
        prefs.edit().putBoolean(KEY_WORD_BY_WORD, enabled).apply()
        _wordByWord.value = enabled
    }

    fun setLongLine(mode: LongLyricRender) {
        if (mode == _longLine.value) return
        prefs.edit().putString(KEY_LONG_LINE, LongLyricRender.stored(mode)).apply()
        _longLine.value = mode
    }

    companion object {
        private const val PREFS = "zmusic_lyric_render"
        private const val KEY_WORD_BY_WORD = "word_by_word"
        private const val KEY_LONG_LINE = "long_line"
    }
}
