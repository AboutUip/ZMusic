package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用内预测性返回（侧滑跟手预览）。默认关闭：系统手势仍可返回，只是没有跟手动画。
 */
class PredictiveBackStore(context: Context) {

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
        const val PREFS = "zmusic_predictive_back"
        const val KEY_ENABLED = "enabled"
        const val DEFAULT_ENABLED = false
    }
}
