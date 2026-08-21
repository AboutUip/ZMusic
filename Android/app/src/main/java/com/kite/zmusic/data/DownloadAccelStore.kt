package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 下载加速：命中 `Download/ZMusic` 已导出曲目时直接播本地文件，
 * 不再拉在线音源，也不再套用音质档位。
 */
class DownloadAccelStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun current(): Boolean = _enabled.value

    fun setEnabled(enabled: Boolean) {
        if (enabled == _enabled.value) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    companion object {
        private const val PREFS = "zmusic_download_accel"
        private const val KEY_ENABLED = "enabled"
    }
}
