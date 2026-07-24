package com.kite.zmusic.ui.orientation

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 主动竖→横时同步钉住蒙版（不延后改方向）。
 * 横屏布局/Insets 重建更重，仅靠 orientation 回调起 loading 会滞后一截。
 */
object OrientationMaskGate {
    /** Host 订阅：为 true 时立刻盖蒙版（无淡入）。 */
    var pinned by mutableStateOf(false)
        private set

    fun pin() {
        pinned = true
    }

    fun unpin() {
        pinned = false
    }
}

/**
 * 进程内旋转锁（不落盘）。
 *
 * 注意：不能用 [ActivityInfo.SCREEN_ORIENTATION_LOCKED] 做唯一策略——
 * Activity 被销毁后从通知回到前台时，系统可能已是竖屏，再设 LOCKED 会锁成竖屏。
 * 因此锁定时记下明确的 portrait/landscape（含 reverse），并在 onCreate/onStart/onResume 重套。
 */
object SessionRotationLockStore {
    var locked by mutableStateOf(false)
        private set

    /** [ActivityInfo] 方向常量；未锁定时为 UNSPECIFIED。 */
    var lockedOrientation by mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        private set

    fun setLocked(activity: Activity?, value: Boolean) {
        if (value) {
            val act = activity ?: return
            val orient = resolveCurrentOrientation(act)
            lockedOrientation = orient
            locked = true
            act.requestedOrientation = orient
        } else {
            locked = false
            lockedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun toggle(activity: Activity?) = setLocked(activity, !locked)

    /**
     * 系统旋转锁定开启时：「确定旋转」——立刻改方向。
     * 竖→横时 [LocalConfiguration] 回调往往更晚，需同步预盖蒙版，避免 loading 滞后露底。
     */
    fun forceOrientation(activity: Activity?, landscape: Boolean) {
        val act = activity ?: return
        if (landscape) {
            OrientationMaskGate.pin()
        }
        applyForceOrientation(act, landscape)
    }

    fun applyForceOrientation(activity: Activity, landscape: Boolean) {
        // 勿用 SENSOR_*：系统锁旋转时传感器方向会显著滞后（尤其竖→横）
        val orient = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        lockedOrientation = orient
        locked = true
        activity.requestedOrientation = orient
    }

    /** Activity 重建 / 从通知回前台时重套已锁定的具体方向。 */
    fun applyTo(activity: Activity) {
        if (!locked) return
        if (lockedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return
        if (activity.requestedOrientation != lockedOrientation) {
            activity.requestedOrientation = lockedOrientation
        }
    }

    fun resolveCurrentOrientation(activity: Activity): Int {
        val rotation = currentDisplayRotation(activity)
        return when (rotation) {
            Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> when (activity.resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE ->
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    private fun currentDisplayRotation(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.rotation
        }
    }
}

/**
 * Compose 侧门面：读写同一进程级 [SessionRotationLockStore]。
 */
@Stable
class SessionRotationLock {
    val locked: Boolean
        get() = SessionRotationLockStore.locked

    fun setLocked(activity: Activity?, value: Boolean) =
        SessionRotationLockStore.setLocked(activity, value)

    fun toggle(activity: Activity?) = SessionRotationLockStore.toggle(activity)

    fun forceOrientation(activity: Activity?, landscape: Boolean) =
        SessionRotationLockStore.forceOrientation(activity, landscape)
}

val LocalSessionRotationLock = staticCompositionLocalOf<SessionRotationLock> {
    error("SessionRotationLock not provided")
}

@Composable
fun rememberSessionRotationLock(): SessionRotationLock {
    val activity = LocalContext.current as? Activity
    val facade = remember { SessionRotationLock() }
    // Activity 附着 / 锁状态变化时重套（进程状态，不依赖 Compose remember 存活）
    DisposableEffect(activity, SessionRotationLockStore.locked, SessionRotationLockStore.lockedOrientation) {
        activity?.let { SessionRotationLockStore.applyTo(it) }
        onDispose { }
    }
    return facade
}

/**
 * 系统「自动旋转」是否开启（[Settings.System.ACCELEROMETER_ROTATION] == 1）。
 * 关闭时即系统旋转锁定，此时应用内应变更为「确定旋转」而非会话锁。
 */
@Composable
fun rememberSystemAutoRotateEnabled(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var enabled by remember {
        mutableStateOf(
            Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1,
        )
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = Settings.System.getInt(
                    resolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    1,
                ) == 1
            }
        }
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}
