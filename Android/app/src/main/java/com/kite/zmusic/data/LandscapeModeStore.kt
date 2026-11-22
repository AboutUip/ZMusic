package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 音乐模式「横屏模式」页里的方向锁。默认跟随系统。 */
enum class OrientationLock {
    Auto,
    Portrait,
    Landscape,
    ;

    fun persistKey(): String = when (this) {
        Auto -> "auto"
        Portrait -> "portrait"
        Landscape -> "landscape"
    }

    companion object {
        fun fromPersist(raw: String?): OrientationLock = when (raw) {
            "portrait" -> Portrait
            "landscape" -> Landscape
            else -> Auto
        }
    }
}

/**
 * 应用内横屏入口：旋转按钮 / 会话锁。默认开启，与上线时行为一致。
 * 关闭后不锁死竖屏，系统自动旋转仍可进入横屏。
 *
 * [orientationLock] 不是这个开关。非自动时开关被强制关掉并拒绝再打开，
 * 方向由 [OrientationLock] 单独钉住。回到自动只放开开关，不把它重新打开。
 */
class LandscapeModeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _orientationLock = MutableStateFlow(
        OrientationLock.fromPersist(prefs.getString(KEY_ORIENTATION_LOCK, null)),
    )
    val orientationLock: StateFlow<OrientationLock> = _orientationLock.asStateFlow()

    init {
        if (_orientationLock.value != OrientationLock.Auto && _enabled.value) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            _enabled.value = false
        }
    }

    fun current(): Boolean = _enabled.value

    fun setEnabled(enabled: Boolean) {
        if (enabled && _orientationLock.value != OrientationLock.Auto) return
        if (enabled == _enabled.value) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    fun setOrientationLock(lock: OrientationLock) {
        val changed = lock != _orientationLock.value
        if (!changed && (lock == OrientationLock.Auto || !_enabled.value)) return
        if (changed) {
            prefs.edit().putString(KEY_ORIENTATION_LOCK, lock.persistKey()).apply()
            _orientationLock.value = lock
        }
        if (lock != OrientationLock.Auto) {
            setEnabled(false)
        }
    }

    companion object {
        const val PREFS = "zmusic_landscape_mode"
        const val KEY_ENABLED = "enabled"
        const val KEY_ORIENTATION_LOCK = "orientation_lock"
        const val DEFAULT_ENABLED = true
    }
}
