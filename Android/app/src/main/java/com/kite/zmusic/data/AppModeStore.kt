package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 主界面模式。默认音乐；播客会换掉整套主壳。 */
enum class AppMode {
    Music,
    Podcast,
    ;

    companion object {
        fun fromStored(raw: String?): AppMode =
            entries.firstOrNull { it.name == raw } ?: Music
    }
}

class AppModeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(AppMode.fromStored(prefs.getString(KEY, null)))
    val mode: StateFlow<AppMode> = _mode.asStateFlow()

    fun current(): AppMode = _mode.value

    fun set(mode: AppMode) {
        if (mode == _mode.value) return
        prefs.edit().putString(KEY, mode.name).apply()
        _mode.value = mode
    }

    companion object {
        const val PREFS = "zmusic_app_mode"
        const val KEY = "mode"
    }
}
