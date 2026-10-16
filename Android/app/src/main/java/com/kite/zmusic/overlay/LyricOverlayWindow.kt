package com.kite.zmusic.overlay

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Display
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
import com.kite.zmusic.plugin.PluginStylePresent
import com.kite.zmusic.data.LyricOverlayStore
import com.kite.zmusic.data.overlayClaimsWindowTouches
import com.kite.zmusic.data.overlayClampX
import com.kite.zmusic.data.overlayDefaultX
import com.kite.zmusic.data.overlayDefaultY
import com.kite.zmusic.data.overlayDisplaySize
import com.kite.zmusic.data.overlayFixedWidthPx
import com.kite.zmusic.data.overlayRemapCoord
import com.kite.zmusic.data.overlaySystemOrientation
import com.kite.zmusic.data.overlayWakesFromIdle
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
    private val overlayContext = createOverlayWindowContext(app)
    private val windowManager = overlayContext.getSystemService(WindowManager::class.java)
    private var composeView: ComposeView? = null
    private var host: OverlayComposeHost? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var configRegistered = false
    private var lastLaidOutScreen: Pair<Int, Int>? = null
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
        val view = ComposeView(ContextThemeWrapper(overlayContext, R.style.Theme_ZMusic)).apply {
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setOnTouchListener { _, event ->
                onOverlayTouch(event)
            }
            setContent {
                val prefs by store.prefsFlow.collectAsState()
                val epoch by layoutEpoch.collectAsState()
                val idleChrome by chromeIdle.collectAsState()
                val screen = remember(epoch) { screenSize() }
                LyricOverlayContent(
                    playbackUi = playback.ui,
                    prefs = prefs,
                    maxWidthPx = screen.first,
                    screenHeightPx = screen.second,
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
                    onPaintedLayout = { applyAppearance(store.current()) },
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
        applyAppearance(store.current())
        startDisplayWatch()
    }

    fun hide() {
        stopDisplayWatch()
        val view = composeView ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        composeView = null
        layoutParams = null
        lastLaidOutScreen = null
        windowDragging = false
        pointerOnOverlay = false
        chromeIdle.value = false
        host?.onDestroy()
        host = null
    }

    private val configCallback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            relayoutForSystemDisplay()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            val watched = overlayDisplay(app, windowManager)?.displayId ?: Display.DEFAULT_DISPLAY
            if (displayId != watched) return
            relayoutForSystemDisplay()
        }
    }

    private fun startDisplayWatch() {
        if (configRegistered) return
        app.registerComponentCallbacks(configCallback)
        app.getSystemService(DisplayManager::class.java)
            ?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        configRegistered = true
    }

    private fun stopDisplayWatch() {
        if (!configRegistered) return
        runCatching { app.unregisterComponentCallbacks(configCallback) }
        runCatching {
            app.getSystemService(DisplayManager::class.java)
                ?.unregisterDisplayListener(displayListener)
        }
        configRegistered = false
    }

    /** 按系统 Display 朝向重铺；忽略 Application 因小窗/竖屏锁而来的 orientation。 */
    private fun relayoutForSystemDisplay() {
        val lp = layoutParams ?: return
        val view = composeView ?: return
        val prefs = PluginStylePresent.paintOverlay(store.current())
        val screen = screenSize()
        val nextWidth = windowWidthSpec(prefs, screen.first)
        val widthPx = overlayWidthPx(prefs, screen.first)
        val nextX = clampedX(remapX(prefs, screen.first), widthPx, screen.first)
        val nextY = clampedY(prefs, remapY(prefs, screen.second), screen.second)
        val nextFlags = overlayFlags(prefs)
        val nextCutout = cutoutMode(prefs)
        if (lastLaidOutScreen == screen &&
            lp.width == nextWidth &&
            lp.x == nextX &&
            lp.y == nextY &&
            lp.flags == nextFlags &&
            lp.layoutInDisplayCutoutMode == nextCutout
        ) {
            return
        }
        lastLaidOutScreen = screen
        lp.width = nextWidth
        lp.y = nextY
        lp.x = nextX
        lp.flags = nextFlags
        applyCutoutMode(lp, prefs)
        clearScreenBlur(lp)
        layoutEpoch.value += 1
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    private fun createLayoutParams(raw: LyricOverlayPrefs): WindowManager.LayoutParams {
        val prefs = PluginStylePresent.paintOverlay(raw)
        val screen = screenSize()
        val w = overlayWidthPx(prefs, screen.first)
        val x = clampedX(remapX(prefs, screen.first), w, screen.first)
        val y = clampedY(prefs, remapY(prefs, screen.second), screen.second)
        lastLaidOutScreen = screen
        return WindowManager.LayoutParams(
            windowWidthSpec(prefs, screen.first),
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

    internal fun applyAppearance(raw: LyricOverlayPrefs) {
        val prefs = PluginStylePresent.paintOverlay(raw)
        val lp = layoutParams ?: return
        val view = composeView ?: return
        view.isClickable = !prefs.locked
        view.isLongClickable = false
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        val screen = screenSize()
        val nextFlags = overlayFlags(prefs)
        val nextCutout = cutoutMode(prefs)
        val nextWidth = windowWidthSpec(prefs, screen.first)
        val widthPx = if (nextWidth > 0) nextWidth else overlayWidthPx(prefs, screen.first)
        val nextX = clampedX(
            if (prefs.posX == LyricOverlayPrefs.UNSET) lp.x else remapX(prefs, screen.first),
            widthPx,
            screen.first,
        )
        val nextY = clampedY(
            prefs,
            if (prefs.posY == LyricOverlayPrefs.UNSET) lp.y else remapY(prefs, screen.second),
            screen.second,
        )
        if (lp.flags == nextFlags &&
            lp.layoutInDisplayCutoutMode == nextCutout &&
            lp.x == nextX &&
            lp.y == nextY &&
            lp.width == nextWidth
        ) {
            lastLaidOutScreen = screen
            return
        }
        lastLaidOutScreen = screen
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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (prefs.locked) {
            // 锁定：整窗不接触摸，交互渗透到下层。
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
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

    private fun onOverlayTouch(event: MotionEvent): Boolean {
        val prefs = store.current()
        if (prefs.locked) return false
        val idleUnlocked = chromeIdle.value
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                windowDragging = false
                pointerOnOverlay = false
                if (allowWindowDrag) {
                    chromeIdle.value = true
                }
                return false
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
                if (!allowWindowDrag) {
                    return overlayClaimsWindowTouches(prefs.locked, idleUnlocked, windowDragging)
                }
                if (!windowDragging) {
                    val spanX = event.rawX - downRawX
                    val spanY = event.rawY - downRawY
                    if (spanX * spanX + spanY * spanY < touchSlopPx * touchSlopPx) {
                        return overlayClaimsWindowTouches(prefs.locked, idleUnlocked, false)
                    }
                    windowDragging = true
                }
                moveTo(dragX + dx, dragY + dy)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dragged = windowDragging
                if (dragged) persistPosition()
                windowDragging = false
                if (overlayWakesFromIdle(
                        idleChrome = idleUnlocked,
                        locked = prefs.locked,
                        dragged = dragged,
                        pointerOnOverlay = pointerOnOverlay,
                    )
                ) {
                    chromeIdle.value = false
                }
                pointerOnOverlay = false
                return overlayClaimsWindowTouches(prefs.locked, idleUnlocked, dragged)
            }
        }
        return overlayClaimsWindowTouches(prefs.locked, idleUnlocked, windowDragging)
    }

    private fun moveTo(x: Float, y: Float) {
        val lp = layoutParams ?: return
        val view = composeView ?: return
        val prefs = store.current()
        val screen = screenSize()
        val w = when {
            lp.width > 0 -> lp.width
            view.width > 0 -> view.width
            else -> overlayWidthPx(prefs, screen.first)
        }
        val nextX = clampedX(x.roundToInt(), w, screen.first)
        val nextY = clampedY(prefs, y.roundToInt(), screen.second)
        dragX = nextX.toFloat()
        dragY = nextY.toFloat()
        if (lp.x == nextX && lp.y == nextY) return
        lp.x = nextX
        lp.y = nextY
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    private fun persistPosition(screen: Pair<Int, Int> = screenSize()) {
        val lp = layoutParams ?: return
        store.update {
            it.copy(posX = lp.x, posY = lp.y, posRefW = screen.first, posRefH = screen.second)
        }
    }

    private fun centerHorizontally() {
        val lp = layoutParams ?: return
        val view = composeView ?: return
        val prefs = store.current()
        val screen = screenSize()
        val w = if (lp.width > 0) {
            lp.width
        } else if (view.width > 0) {
            view.width
        } else {
            overlayWidthPx(prefs, screen.first)
        }
        lp.x = overlayClampX(((screen.first - w).coerceAtLeast(0)) / 2, w, screen.first)
        runCatching { windowManager.updateViewLayout(view, lp) }
        persistPosition(screen)
    }

    private fun windowWidthSpec(prefs: LyricOverlayPrefs, displayW: Int = displayWidthPx()): Int {
        if (prefs.dynamicWidth) return WindowManager.LayoutParams.WRAP_CONTENT
        return overlayWidthPx(prefs, displayW)
    }

    private fun overlayWidthPx(prefs: LyricOverlayPrefs, displayW: Int = displayWidthPx()): Int {
        val avail = displayW.coerceAtLeast(1)
        if (prefs.dynamicWidth) {
            val v = composeView?.width ?: 0
            if (v > 0) return v.coerceIn(1, avail)
            return (avail * 0.6f).roundToInt().coerceIn(1, avail)
        }
        return overlayFixedWidthPx(avail, prefs.widthPercent)
    }

    private fun clampedX(
        x: Int,
        widthPx: Int,
        displayW: Int,
    ): Int = overlayClampX(x, widthPx, displayW)

    private fun clampedY(
        prefs: LyricOverlayPrefs,
        y: Int,
        displayH: Int = screenSize().second,
    ): Int {
        val min = minOverlayY(prefs)
        val max = (displayH - 48).coerceAtLeast(min)
        return y.coerceIn(min, max)
    }

    private fun minOverlayY(prefs: LyricOverlayPrefs): Int {
        if (prefs.ignoreCutout) return 0
        val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets
        } else {
            windowManager.maximumWindowMetrics.windowInsets
        }
        return insets.getInsets(
            android.view.WindowInsets.Type.statusBars() or
                android.view.WindowInsets.Type.displayCutout(),
        ).top
    }

    private fun displayWidthPx(): Int = screenSize().first

    private fun screenSize(): Pair<Int, Int> {
        val raw = rawDisplaySize()
        return overlayDisplaySize(raw.first, raw.second, systemOrientation())
    }

    /** 默认屏旋转，不读 Application/Activity 配置（小窗会把后者拧成竖屏）。 */
    private fun systemOrientation(): Int {
        val display = overlayDisplay(app, windowManager)
            ?: return overlayContext.resources.configuration.orientation
        val mode = runCatching { display.mode }.getOrNull()
        return overlaySystemOrientation(
            rotation = display.rotation,
            physicalWidth = mode?.physicalWidth ?: 0,
            physicalHeight = mode?.physicalHeight ?: 0,
        )
    }

    @Suppress("DEPRECATION")
    private fun rawDisplaySize(): Pair<Int, Int> {
        val display = overlayDisplay(app, windowManager)
        if (display != null) {
            val point = Point()
            runCatching { display.getRealSize(point) }
            if (point.x > 0 && point.y > 0) return point.x to point.y
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val maxBounds = runCatching { windowManager.maximumWindowMetrics.bounds }.getOrNull()
            if (maxBounds != null && maxBounds.width() > 0 && maxBounds.height() > 0) {
                return maxBounds.width() to maxBounds.height()
            }
            val curBounds = runCatching { windowManager.currentWindowMetrics.bounds }.getOrNull()
            if (curBounds != null && curBounds.width() > 0 && curBounds.height() > 0) {
                return curBounds.width() to curBounds.height()
            }
        }
        val dm = overlayContext.resources.displayMetrics
        return dm.widthPixels.coerceAtLeast(1) to dm.heightPixels.coerceAtLeast(1)
    }

    private fun remapX(prefs: LyricOverlayPrefs, newW: Int): Int = overlayRemapCoord(
        pos = prefs.posX,
        ref = prefs.posRefW,
        newSize = newW,
        fallback = overlayDefaultX(newW),
    )

    private fun remapY(prefs: LyricOverlayPrefs, newH: Int): Int = overlayRemapCoord(
        pos = prefs.posY,
        ref = prefs.posRefH,
        newSize = newH,
        fallback = overlayDefaultY(newH),
    )
}

private fun createOverlayWindowContext(app: Application): Context {
    val wm = app.getSystemService(WindowManager::class.java)
    val display = overlayDisplay(app, wm) ?: return app
    val displayContext = app.createDisplayContext(display)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return displayContext
    return runCatching {
        displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }.getOrDefault(displayContext)
}

/** Application 没有关联 Display，禁止 Context.getDisplay()。 */
@Suppress("DEPRECATION")
private fun overlayDisplay(app: Application, windowManager: WindowManager): Display? {
    val fromManager = app.getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
    if (fromManager != null) return fromManager
    return runCatching { windowManager.defaultDisplay }.getOrNull()
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
