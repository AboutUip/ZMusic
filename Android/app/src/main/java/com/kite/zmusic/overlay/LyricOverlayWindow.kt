package com.kite.zmusic.overlay

import android.app.Application
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.MutableStateFlow

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
    private val layoutEpoch = MutableStateFlow(0)
    private val chromeIdle = MutableStateFlow(false)
    private val touchSlopPx = ViewConfiguration.get(app).scaledTouchSlop
    private var allowWindowDrag = true
    private var pointerOnOverlay = false
    private var windowDragging = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragX = 0f
    private var dragY = 0f

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
            setOnTouchListener { _, event ->
                onOverlayTouch(event)
                false
            }
            setContent {
                val prefs by store.prefsFlow.collectAsState()
                val epoch by layoutEpoch.collectAsState()
                val idleChrome by chromeIdle.collectAsState()
                val maxWidthPx = remember(epoch) { displayWidthPx() }
                LyricOverlayContent(
                    playbackUi = playback.ui,
                    prefs = prefs,
                    maxWidthPx = maxWidthPx,
                    onPrefs = { next -> store.update { next } },
                    onLock = { store.setLocked(true) },
                    onTogglePlay = { playback.togglePlayPause() },
                    onSkipPrevious = { playback.skipPrevious() },
                    onSkipNext = { playback.skipNext() },
                    onCenterHorizontally = { centerHorizontally() },
                    onClose = { store.setEnabled(false) },
                    idleChrome = idleChrome,
                    onWake = { chromeIdle.value = false },
                    onAllowWindowDrag = { allow -> allowWindowDrag = allow },
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
        windowDragging = false
        pointerOnOverlay = false
        chromeIdle.value = false
        host?.onDestroy()
        host = null
    }

    private val configCallback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val lp = layoutParams ?: return
            val view = composeView ?: return
            val prefs = store.current()
            val screen = screenSize()
            lp.y = clampedY(prefs, remapY(prefs, screen.second))
            lp.x = clampedX(prefs, remapX(prefs, screen.first))
            lp.width = windowWidthSpec(prefs)
            lp.flags = overlayFlags(prefs)
            applyCutoutMode(lp, prefs)
            clearScreenBlur(lp)
            layoutEpoch.value += 1
            runCatching { windowManager.updateViewLayout(view, lp) }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }

    private fun createLayoutParams(prefs: LyricOverlayPrefs): WindowManager.LayoutParams {
        val screen = screenSize()
        val w = overlayWidthPx(prefs)
        val x = if (prefs.posX == LyricOverlayPrefs.UNSET) {
            ((screen.first - w).coerceAtLeast(0) * 0.12f).roundToInt()
        } else {
            clampedX(prefs, remapX(prefs, screen.first), w)
        }
        val y = clampedY(
            prefs,
            if (prefs.posY == LyricOverlayPrefs.UNSET) {
                (screen.second * 0.18f).roundToInt()
            } else {
                remapY(prefs, screen.second)
            },
        )
        return WindowManager.LayoutParams(
            windowWidthSpec(prefs),
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
        val nextFlags = overlayFlags(prefs)
        val nextCutout = cutoutMode(prefs)
        val nextWidth = windowWidthSpec(prefs)
        val nextX = clampedX(
            prefs,
            lp.x,
            if (nextWidth > 0) nextWidth else overlayWidthPx(prefs),
        )
        val nextY = clampedY(prefs, lp.y)
        if (lp.flags == nextFlags &&
            lp.layoutInDisplayCutoutMode == nextCutout &&
            lp.x == nextX &&
            lp.y == nextY &&
            lp.width == nextWidth
        ) return
        lp.flags = nextFlags
        lp.layoutInDisplayCutoutMode = nextCutout
        lp.width = nextWidth
        lp.x = nextX
        lp.y = nextY
        clearScreenBlur(lp)
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    private fun overlayFlags(prefs: LyricOverlayPrefs): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (prefs.ignoreCutout) {
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        return flags
    }

    private fun applyCutoutMode(lp: WindowManager.LayoutParams, prefs: LyricOverlayPrefs) {
        lp.layoutInDisplayCutoutMode = cutoutMode(prefs)
    }

    private fun cutoutMode(prefs: LyricOverlayPrefs): Int = if (prefs.ignoreCutout) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    }

    /** FLAG_BLUR_BEHIND 会糊掉整块屏幕，不能用在悬浮窗上。 */
    private fun clearScreenBlur(lp: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        lp.setBlurBehindRadius(0)
    }

    private fun onOverlayTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                windowDragging = false
                pointerOnOverlay = false
                if (!store.current().locked && allowWindowDrag) {
                    chromeIdle.value = true
                }
            }
            MotionEvent.ACTION_DOWN -> {
                windowDragging = false
                pointerOnOverlay = true
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                val lp = layoutParams
                if (lp != null) {
                    dragX = lp.x.toFloat()
                    dragY = lp.y.toFloat()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                if (store.current().locked || !allowWindowDrag) return
                if (!windowDragging) {
                    val spanX = event.rawX - downRawX
                    val spanY = event.rawY - downRawY
                    if (spanX * spanX + spanY * spanY < touchSlopPx * touchSlopPx) return
                    windowDragging = true
                }
                moveTo(dragX + dx, dragY + dy)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (windowDragging) persistPosition()
                windowDragging = false
                if (pointerOnOverlay) chromeIdle.value = false
                pointerOnOverlay = false
            }
        }
    }

    private fun moveTo(x: Float, y: Float) {
        val lp = layoutParams ?: return
        val view = composeView ?: return
        val prefs = store.current()
        val w = when {
            lp.width > 0 -> lp.width
            view.width > 0 -> view.width
            else -> overlayWidthPx(prefs)
        }
        dragX = x
        dragY = y
        val nextX = clampedX(prefs, x.roundToInt(), w)
        val nextY = clampedY(prefs, y.roundToInt())
        if (lp.x == nextX && lp.y == nextY) return
        lp.x = nextX
        lp.y = nextY
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
        val displayW = displayWidthPx()
        val w = if (lp.width > 0) lp.width else if (view.width > 0) view.width else overlayWidthPx(prefs)
        lp.x = ((displayW - w).coerceAtLeast(0)) / 2
        runCatching { windowManager.updateViewLayout(view, lp) }
        persistPosition()
    }

    private fun windowWidthSpec(prefs: LyricOverlayPrefs): Int {
        if (prefs.dynamicWidth) return WindowManager.LayoutParams.WRAP_CONTENT
        return overlayWidthPx(prefs)
    }

    private fun overlayWidthPx(prefs: LyricOverlayPrefs): Int {
        val avail = displayWidthPx()
        if (prefs.dynamicWidth) {
            val v = composeView?.width ?: 0
            if (v > 0) return v.coerceIn(1, avail)
            return (avail * 0.6f).roundToInt().coerceIn(1, avail)
        }
        val pct = prefs.widthPercent.coerceIn(
            LyricOverlayPrefs.WIDTH_PERCENT_MIN,
            LyricOverlayPrefs.WIDTH_PERCENT_MAX,
        )
        return ((avail.toLong() * pct) / 100L).toInt().coerceIn(1, avail)
    }

    private fun clampedX(prefs: LyricOverlayPrefs, x: Int, widthPx: Int = overlayWidthPx(prefs)): Int {
        val displayW = displayWidthPx()
        val w = widthPx.coerceIn(1, displayW)
        val maxX = (displayW - w).coerceAtLeast(0)
        return x.coerceIn(0, maxX)
    }

    private fun clampedY(prefs: LyricOverlayPrefs, y: Int): Int {
        val min = minOverlayY(prefs)
        val max = (screenSize().second - 48).coerceAtLeast(min)
        return y.coerceIn(min, max)
    }

    private fun minOverlayY(prefs: LyricOverlayPrefs): Int {
        if (prefs.ignoreCutout) return 0
        return windowManager.maximumWindowMetrics.windowInsets.getInsets(
            android.view.WindowInsets.Type.statusBars() or
                android.view.WindowInsets.Type.displayCutout(),
        ).top
    }

    private fun displayWidthPx(): Int {
        return displayBounds().width().coerceAtLeast(1)
    }

    private fun displayBounds(): android.graphics.Rect {
        return windowManager.maximumWindowMetrics.bounds
    }

    private fun screenSize(): Pair<Int, Int> {
        val b = displayBounds()
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
