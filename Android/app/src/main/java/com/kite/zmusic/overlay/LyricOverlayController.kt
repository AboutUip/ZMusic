package com.kite.zmusic.overlay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.provider.Settings
import com.kite.zmusic.data.LyricOverlayStore
import com.kite.zmusic.playback.MvPlayback
import com.kite.zmusic.playback.PlaybackBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 歌词悬浮窗显隐：通知栏开启 + 应用在后台 + 已授权悬浮窗。
 * 软件内即使开关打开也不展示。
 */
class LyricOverlayController(
    private val app: Application,
    private val store: LyricOverlayStore,
    private val playback: PlaybackBridge,
    private val mvPlayback: MvPlayback,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val window = LyricOverlayWindow(app, store, playback)
    private var startedActivities = 0
    private var collectJob: Job? = null
    /**
     * 冷启动窗口：Application.onCreate 时 Activity 尚未 onStart，
     * startedActivities==0 会被误判为后台而闪一下悬浮窗。
     * 在首个 Activity 启动前（或短超时确认无界面进程）按前台处理。
     */
    private var coldStartGuard = true

    fun start() {
        if (collectJob != null) return
        app.registerActivityLifecycleCallbacks(activityCallbacks)
        collectJob = scope.launch {
            combine(
                store.prefsFlow,
                playback.ui.map { it.hasQueue }.distinctUntilChanged(),
                mvPlayback.ui.map { it.active }.distinctUntilChanged(),
            ) { prefs, hasQueue, mvActive ->
                Triple(prefs, hasQueue, mvActive)
            }.collect { (prefs, hasQueue, mvActive) ->
                sync(prefs.enabled, hasQueue, mvActive)
                if (window.attached) window.applyAppearance(prefs)
            }
        }
        // 不立刻按「无 Activity」去 show；等首帧 Activity 或超时后再裁决
        sync(
            enabled = store.current().enabled,
            hasQueue = playback.ui.value.hasQueue,
            mvActive = mvPlayback.ui.value.active,
        )
        scope.launch {
            delay(COLD_START_GUARD_MS)
            if (!coldStartGuard) return@launch
            coldStartGuard = false
            resync()
        }
    }

    private fun sync(enabled: Boolean, hasQueue: Boolean, mvActive: Boolean) {
        val foreground = startedActivities > 0 || coldStartGuard
        val show = enabled &&
            !foreground &&
            hasQueue &&
            !mvActive &&
            Settings.canDrawOverlays(app)
        if (show) window.show() else window.hide()
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
            if (coldStartGuard) coldStartGuard = false
            resync()
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            resync()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private fun resync() {
        sync(
            enabled = store.current().enabled,
            hasQueue = playback.ui.value.hasQueue,
            mvActive = mvPlayback.ui.value.active,
        )
    }

    companion object {
        /** 仅服务拉起进程、无 Activity 时，超时后允许按后台规则显示悬浮窗 */
        private const val COLD_START_GUARD_MS = 400L
    }
}
