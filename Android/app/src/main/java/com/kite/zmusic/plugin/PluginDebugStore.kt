package com.kite.zmusic.plugin

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 插件引擎调试开关。产品默认关闭；**当前测试期默认开启**，发行前把 [DEFAULT_ENABLED] 改回 `false`。
 * 崩溃不得改此开关。关闭后引擎不打日志，并拒绝内部调试 API。不决定插件是否运行。
 */
class PluginDebugStore(
    context: Context,
    prefsName: String = PREFS,
) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun current(): Boolean = _enabled.value

    fun setEnabled(enabled: Boolean) {
        if (enabled == _enabled.value) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    companion object {
        const val PREFS = "zmusic_plugin_engine_debug"
        const val KEY_ENABLED = "enabled"

        /** 发行前改为 false。 */
        const val DEFAULT_ENABLED = true
    }
}
