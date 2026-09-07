package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 加速启动动画。默认关闭：播完整 Xuan → ZMusic 与标语。
 * 开启后只保留 Xuan 聚齐，随后进入软件；插件未就绪时仍走完整动画。
 */
class SplashAccelStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun current(): Boolean = _enabled.value

    fun setEnabled(enabled: Boolean) {
        if (enabled == _enabled.value) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    companion object {
        const val PREFS = "zmusic_splash_accel"
        const val KEY_ENABLED = "enabled"
        const val DEFAULT_ENABLED = false
    }
}
