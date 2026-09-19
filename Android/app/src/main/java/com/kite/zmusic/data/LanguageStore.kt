package com.kite.zmusic.data

import android.content.Context
import com.kite.zmusic.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 界面语言（简体中文 / English / 日本語）。默认中文。
 */
class LanguageStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(load())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun current(): AppLanguage = _language.value

    fun set(next: AppLanguage) {
        if (next == _language.value) return
        prefs.edit().putString(KEY_LANGUAGE, next.name).commit()
        _language.value = next
    }

    private fun load(): AppLanguage =
        AppLanguage.fromStored(prefs.getString(KEY_LANGUAGE, null))

    companion object {
        private const val PREFS = "zmusic_language"
        private const val KEY_LANGUAGE = "language"

        fun peek(context: Context): AppLanguage {
            val store = context.applicationContext ?: context
            val prefs = store.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return AppLanguage.fromStored(prefs.getString(KEY_LANGUAGE, null))
        }
    }
}
