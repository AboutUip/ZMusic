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
        sync(
            enabled = store.current().enabled,
            hasQueue = playback.ui.value.hasQueue,
            mvActive = mvPlayback.ui.value.active,
        )
    }

    private fun sync(enabled: Boolean, hasQueue: Boolean, mvActive: Boolean) {
        val foreground = startedActivities > 0
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
}
