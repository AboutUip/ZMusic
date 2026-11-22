package com.kite.zmusic.ui.orientation

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.OrientationLock

/**
 * 把音乐模式里记住的方向锁套到 Activity 上。
 * 自动且会话钉还在时不动方向；锁定竖屏 / 横屏时清掉会话钉再写入固定方向。
 */
object OrientationLockApplier {
    fun applyTo(activity: Activity) {
        val app = activity.application as? ZMusicApplication ?: return
        apply(activity, app.landscapeModeStore.orientationLock.value)
    }

    fun apply(activity: Activity, lock: OrientationLock) {
        when (lock) {
            OrientationLock.Auto -> releaseToAuto(activity)
            OrientationLock.Portrait -> applyFixed(activity, landscape = false)
            OrientationLock.Landscape -> applyFixed(activity, landscape = true)
        }
    }

    private fun releaseToAuto(activity: Activity) {
        if (SessionRotationLockStore.locked) return
        if (!isFixedOrientation(activity.requestedOrientation)) return
        val landscapeNow = activity.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        if (landscapeNow) OrientationMaskGate.pin()
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun applyFixed(activity: Activity, landscape: Boolean) {
        SessionRotationLockStore.relinquish()
        val target = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val landscapeNow = activity.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        if (landscapeNow != landscape) OrientationMaskGate.pin()
        if (activity.requestedOrientation != target) {
            activity.requestedOrientation = target
        }
    }

    private fun isFixedOrientation(orientation: Int): Boolean = when (orientation) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
        -> true
        else -> false
    }
}
