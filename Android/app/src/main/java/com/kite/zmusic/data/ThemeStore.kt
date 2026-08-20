package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppAppearance {
    Light,
    Dark,
    System,
    ;

    val title: String
        get() = when (this) {
            Light -> "浅色"
            Dark -> "深色"
            System -> "跟随系统"
        }

    val subtitle: String
        get() = when (this) {
            Light -> "始终使用浅色界面"
            Dark -> "始终使用深色界面"
            System -> "与系统外观保持一致"
        }

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        Light -> false
        Dark -> true
        System -> systemDark
    }

    companion object {
        fun fromStored(raw: String?): AppAppearance =
            entries.find { it.name == raw } ?: Light
    }
}

/**
 * 全局外观（浅色 / 深色 / 跟随系统）。默认浅色。
 */
class ThemeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(load())
    val appearance: StateFlow<AppAppearance> = _appearance.asStateFlow()

    fun current(): AppAppearance = _appearance.value

    fun set(next: AppAppearance) {
        if (next == _appearance.value) return
        prefs.edit().putString(KEY_APPEARANCE, next.name).apply()
        _appearance.value = next
    }

    private fun load(): AppAppearance =
        AppAppearance.fromStored(prefs.getString(KEY_APPEARANCE, null))

    companion object {
        private const val PREFS = "zmusic_appearance"
        private const val KEY_APPEARANCE = "appearance"
    }
}
