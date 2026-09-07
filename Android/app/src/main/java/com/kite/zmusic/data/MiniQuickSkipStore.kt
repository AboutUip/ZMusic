package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MiniQuickSkipAxis {
    Horizontal,
    Vertical,
}

data class MiniQuickSkipPrefs(
    val enabled: Boolean = false,
    val axis: MiniQuickSkipAxis = MiniQuickSkipAxis.Horizontal,
)

/**
 * 迷你条快速切歌。默认关闭：只点进播放页。
 * 开启后在封面和歌名区域滑动切歌，不跟手。
 */
class MiniQuickSkipStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<MiniQuickSkipPrefs> = _state.asStateFlow()

    fun current(): MiniQuickSkipPrefs = _state.value

    fun setEnabled(enabled: Boolean) {
        val cur = _state.value
        if (enabled == cur.enabled) return
        write(cur.copy(enabled = enabled))
    }

    fun setAxis(axis: MiniQuickSkipAxis) {
        val cur = _state.value
        if (axis == cur.axis) return
        write(cur.copy(axis = axis))
    }

    private fun load(): MiniQuickSkipPrefs {
        val axisName = prefs.getString(KEY_AXIS, MiniQuickSkipAxis.Horizontal.name)
        val axis = MiniQuickSkipAxis.entries.firstOrNull { it.name == axisName }
            ?: MiniQuickSkipAxis.Horizontal
        return MiniQuickSkipPrefs(
            enabled = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED),
            axis = axis,
        )
    }

    private fun write(next: MiniQuickSkipPrefs) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_AXIS, next.axis.name)
            .apply()
        _state.value = next
    }

    companion object {
        const val PREFS = "zmusic_mini_quick_skip"
        const val KEY_ENABLED = "enabled"
        const val KEY_AXIS = "axis"
        const val DEFAULT_ENABLED = false
    }
}
