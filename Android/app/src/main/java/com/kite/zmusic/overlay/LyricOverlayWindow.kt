package com.kite.zmusic.overlay

import android.app.Application
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kite.zmusic.R
import com.kite.zmusic.data.LyricOverlayPrefs
import com.kite.zmusic.data.LyricOverlayStore
import com.kite.zmusic.playback.PlaybackBridge
import com.kite.zmusic.ui.lyricoverlay.LyricOverlayContent
import kotlin.math.roundToInt

/**
 * WindowManager 歌词悬浮窗。仅由 [LyricOverlayController] 在应用外且通知栏已开启时挂上。
 */
internal class LyricOverlayWindow(
    private val app: Application,
    private val store: LyricOverlayStore,
    private val playback: PlaybackBridge,
) {
    private val windowManager = app.getSystemService(WindowManager::class.java)
    private var composeView: ComposeView? = null
    private var host: OverlayComposeHost? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var configRegistered = false

    val attached: Boolean get() = composeView != null

    fun show() {
        if (composeView != null) {
            applyAppearance(store.current())
            return
        }
        if (!Settings.canDrawOverlays(app)) return
        val host = OverlayComposeHost().also { this.host = it }
        host.onCreate()
        val lp = createLayoutParams(store.current())
        layoutParams = lp
        val view = ComposeView(ContextThemeWrapper(app, R.style.Theme_ZMusic)).apply {
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setContent {
                val prefs by store.prefsFlow.collectAsState()
                val ui by playback.ui.collectAsState()
                LyricOverlayContent(
                    playback = ui,
                    prefs = prefs,
                    maxWidthPx = maxContentWidth(prefs),
                    onPrefs = { next -> store.update { next } },
                    onLock = { store.setLocked(true) },
                    onTogglePlay = { playback.togglePlayPause() },
                    onSkipPrevious = { playback.skipPrevious() },
                    onSkipNext = { playback.skipNext() },
                    onCenterHorizontally = { centerHorizontally() },
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onDragEnd = { persistPosition() },
                )
            }
        }
        composeView = view
        val added = runCatching { windowManager.addView(view, lp) }.isSuccess
        if (!added) {
            composeView = null
            layoutParams = null
            host.onDestroy()
            this.host = null
            return
        }
        if (!configRegistered) {
            app.registerComponentCallbacks(configCallback)
            configRegistered = true
        }
    }

    fun hide() {
        if (configRegistered) {
            runCatching { app.unregisterComponentCallbacks(configCallback) }
            configRegistered = false
        }
        val view = composeView ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        composeView = null
        layoutParams = null
        host?.onDestroy()
        host = null
    }

    private val configCallback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val lp = layoutParams ?: return
            val view = composeView ?: return
            val prefs = store.current()
            val screen = screenSize()
            lp.x = remapX(prefs, screen.first)
            lp.y = remapY(prefs, screen.second)
            applyAppearance(prefs)
            runCatching { windowManager.updateViewLayout(view, lp) }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }

    private fun createLayoutParams(prefs: LyricOverlayPrefs): WindowManager.LayoutParams {
        val screen = screenSize()
        val x = if (prefs.posX == LyricOverlayPrefs.UNSET) {
            (screen.first * 0.12f).roundToInt()
        } else {
            remapX(prefs, screen.first)
        }
        val y = if (prefs.posY == LyricOverlayPrefs.UNSET) {
            (screen.second * 0.18f).roundToInt()
        } else {
            remapY(prefs, screen.second)
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayFlags(prefs),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            applyCutoutMode(this, prefs)
            clearScreenBlur(this)
        }
    }

    internal fun applyAppearance(prefs: LyricOverlayPrefs) {
        val lp = layoutParams ?: return
        val view = composeView ?: return
        lp.flags = overlayFlags(prefs)
        applyCutoutMode(lp, prefs)
        clearScreenBlur(lp)
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    private fun overlayFlags(prefs: LyricOverlayPrefs): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (prefs.locked) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (prefs.ignoreCutout) flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return flags
    }

    private fun applyCutoutMode(lp: WindowManager.LayoutParams, prefs: LyricOverlayPrefs) {
        lp.layoutInDisplayCutoutMode = if (prefs.ignoreCutout) {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
    }

    /** FLAG_BLUR_BEHIND 会糊掉整块屏幕，不能用在悬浮窗上。 */
    private fun clearScreenBlur(lp: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        lp.setBlurBehindRadius(0)
    }

    private fun moveBy(dx: Float, dy: Float) {
        val lp = layoutParams ?: return
        val view = composeView ?: return
        val screen = screenSize()
        val w = view.width.coerceAtLeast(48)
        lp.x = (lp.x + dx.roundToInt()).coerceIn(-w / 2, screen.first - w / 2)
        lp.y = (lp.y + dy.roundToInt()).coerceIn(0, screen.second - 48)
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    private fun persistPosition() {
        val lp = layoutParams ?: return
        val screen = screenSize()
        store.update {
            it.copy(posX = lp.x, posY = lp.y, posRefW = screen.first, posRefH = screen.second)
        }
    }

    private fun centerHorizontally() {
        val lp = layoutParams ?: return
        val view = composeView ?: return
        val prefs = store.current()
        val box = contentBox(prefs)
        val w = if (view.width > 0) view.width else (box.third * 0.6f).roundToInt()
        lp.x = box.first + (box.third - w) / 2
        runCatching { windowManager.updateViewLayout(view, lp) }
        persistPosition()
    }

    private fun maxContentWidth(prefs: LyricOverlayPrefs): Int {
        val box = contentBox(prefs)
        return (box.third - 16).coerceAtLeast(120)
    }

    private fun contentBox(prefs: LyricOverlayPrefs): Triple<Int, Int, Int> {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        if (prefs.ignoreCutout) {
            return Triple(0, 0, bounds.width())
        }
        val inset = metrics.windowInsets.getInsets(
            android.view.WindowInsets.Type.statusBars() or
                android.view.WindowInsets.Type.displayCutout(),
        )
        return Triple(inset.left, inset.top, bounds.width() - inset.left - inset.right)
    }

    private fun screenSize(): Pair<Int, Int> {
        val b = windowManager.currentWindowMetrics.bounds
        return b.width() to b.height()
    }

    private fun remapX(prefs: LyricOverlayPrefs, newW: Int): Int {
        if (prefs.posX == LyricOverlayPrefs.UNSET) return (newW * 0.12f).roundToInt()
        if (prefs.posRefW <= 0) return prefs.posX
        return (prefs.posX.toLong() * newW / prefs.posRefW).toInt()
    }

    private fun remapY(prefs: LyricOverlayPrefs, newH: Int): Int {
        if (prefs.posY == LyricOverlayPrefs.UNSET) return (newH * 0.18f).roundToInt()
        if (prefs.posRefH <= 0) return prefs.posY
        return (prefs.posY.toLong() * newH / prefs.posRefH).toInt()
    }
}

private class OverlayComposeHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) return
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        store.clear()
    }
}
