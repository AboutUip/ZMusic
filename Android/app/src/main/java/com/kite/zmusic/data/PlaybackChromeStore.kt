package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 底部播放条，或常驻灵动岛。默认底部播放条。 */
enum class PlaybackChromeMode(val stored: String) {
    Default("default"),
    Island("island"),
    ;

    companion object {
        fun fromStored(raw: String?): PlaybackChromeMode =
            entries.firstOrNull { it.stored == raw } ?: Default
    }
}

class PlaybackChromeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(PlaybackChromeMode.fromStored(prefs.getString(KEY_MODE, null)))
    val mode: StateFlow<PlaybackChromeMode> = _mode.asStateFlow()

    fun current(): PlaybackChromeMode = _mode.value

    fun setMode(mode: PlaybackChromeMode) {
        if (mode == _mode.value) return
        prefs.edit().putString(KEY_MODE, mode.stored).apply()
        _mode.value = mode
    }

    companion object {
        const val PREFS = "zmusic_playback_chrome"
        const val KEY_MODE = "mode"
    }
}
