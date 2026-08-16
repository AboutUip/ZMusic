package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局默认音源质量（设置与竖屏播放页共用，切歌仍沿用）。
 */
class AudioQualityStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _quality = MutableStateFlow(load())
    val quality: StateFlow<AudioQuality> = _quality.asStateFlow()

    fun current(): AudioQuality = _quality.value

    fun set(quality: AudioQuality) {
        if (quality == _quality.value) return
        prefs.edit().putString(KEY_LEVEL, quality.level).apply()
        _quality.value = quality
    }

    private fun load(): AudioQuality =
        AudioQuality.fromLevel(prefs.getString(KEY_LEVEL, null))

    companion object {
        private const val PREFS = "zmusic_audio_quality"
        private const val KEY_LEVEL = "level"
    }
}
