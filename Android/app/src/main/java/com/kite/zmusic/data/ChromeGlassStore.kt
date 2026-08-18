package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局玻璃主题（设置预览与 Dock / 迷你条 / 弹窗 / 灵动岛共用）。
 */
class ChromeGlassStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _style = MutableStateFlow(load())
    val style: StateFlow<ChromeGlassStyle> = _style.asStateFlow()

    fun current(): ChromeGlassStyle = _style.value

    fun setMode(mode: ChromeGlassMode) {
        val next = _style.value.copy(mode = mode)
        if (next == _style.value) return
        persist(next)
    }

    fun setRefraction(value: Float) {
        val next = _style.value.copy(
            refraction = value.coerceIn(ChromeGlassStyle.REFRACTION_MIN, ChromeGlassStyle.REFRACTION_MAX),
        )
        if (next == _style.value) return
        persist(next)
    }

    fun setBlur(value: Float) {
        val next = _style.value.copy(blur = value.coerceIn(0f, 1f))
        if (next == _style.value) return
        persist(next)
    }

    fun apply(style: ChromeGlassStyle) {
        val next = style.copy(
            refraction = style.refraction.coerceIn(
                ChromeGlassStyle.REFRACTION_MIN,
                ChromeGlassStyle.REFRACTION_MAX,
            ),
            blur = style.blur.coerceIn(0f, 1f),
        )
        persist(next)
    }

    fun reset() {
        persist(ChromeGlassStyle.Default)
    }

    private fun persist(next: ChromeGlassStyle) {
        prefs.edit()
            .putString(KEY_MODE, next.mode.name)
            .putFloat(KEY_REFRACTION, next.refraction)
            .putFloat(KEY_BLUR, next.blur)
            .apply()
        _style.value = next
    }

    private fun load(): ChromeGlassStyle {
        val refraction = prefs.safeFloat(KEY_REFRACTION, ChromeGlassStyle.REFRACTION_DEFAULT)
            .coerceIn(ChromeGlassStyle.REFRACTION_MIN, ChromeGlassStyle.REFRACTION_MAX)
        val blur = prefs.safeFloat(KEY_BLUR, ChromeGlassStyle.BLUR_DEFAULT).coerceIn(0f, 1f)
        return ChromeGlassStyle(
            mode = ChromeGlassMode.fromStored(prefs.getString(KEY_MODE, null)),
            refraction = refraction,
            blur = blur,
        )
    }

    companion object {
        private const val PREFS = "zmusic_chrome_glass"
        private const val KEY_MODE = "mode"
        private const val KEY_REFRACTION = "refraction"
        private const val KEY_BLUR = "blur"
    }
}

private fun android.content.SharedPreferences.safeFloat(key: String, default: Float): Float =
    runCatching { getFloat(key, default) }.getOrDefault(default)
