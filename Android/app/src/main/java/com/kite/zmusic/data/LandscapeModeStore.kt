package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用内横屏入口：旋转按钮 / 会话锁。默认开启，与上线时行为一致。
 * 关闭后不锁死竖屏，系统自动旋转仍可进入横屏。
 */
class LandscapeModeStore(context: Context) {

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
        const val PREFS = "zmusic_landscape_mode"
        const val KEY_ENABLED = "enabled"
        const val DEFAULT_ENABLED = true
    }
}
