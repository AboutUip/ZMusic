package com.kite.zmusic.plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.i18n.I18n
import com.kite.zmusic.workshop.WorkshopAuthStore
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

data class PluginNavRequest(
    val name: String,
    val id: Long = 0L,
    val title: String = "",
)

class PluginNavBus {
    private val handler = AtomicReference<((PluginNavRequest) -> Boolean)?>(null)
    private val main = Handler(Looper.getMainLooper())

    fun set(next: ((PluginNavRequest) -> Boolean)?) {
        handler.set(next)
    }

    fun open(request: PluginNavRequest): Boolean {
        val current = handler.get() ?: return false
        if (Looper.myLooper() == Looper.getMainLooper()) return current(request)
        main.post { current(request) }
        return true
    }
}

internal object PluginForegroundActivity {
    private val ref = AtomicReference<WeakReference<Activity>?>(null)

    fun set(activity: Activity) {
        ref.set(WeakReference(activity))
    }

    fun clear(activity: Activity) {
        val current = ref.get()?.get()
        if (current === activity) ref.set(null)
    }

    fun get(): Activity? = ref.get()?.get()
}

interface PluginLocalHost {
    fun facts(): PluginHostFacts
    fun user(): Map<String, Any?>
    fun locale(): String
    fun platform(): String
    fun openUrl(url: String): Boolean
    fun haptic(ms: Int): Boolean
    fun screen(): Map<String, Any?>
    fun orientation(): String
    fun network(): String
    fun battery(): Map<String, Any?>
    fun keepAwake(on: Boolean): Boolean
    fun openNav(request: PluginNavRequest): Boolean

    companion object {
        val Noop: PluginLocalHost = object : PluginLocalHost {
            override fun facts(): PluginHostFacts = PluginHostFacts(false, false, false)
            override fun user(): Map<String, Any?> = mapOf(
                "loggedIn" to false,
                "guest" to false,
                "label" to null,
                "community" to mapOf("loggedIn" to false, "uid" to null),
            )
            override fun locale(): String = "zh"
            override fun platform(): String = "android"
            override fun openUrl(url: String): Boolean = false
            override fun haptic(ms: Int): Boolean = false
            override fun screen(): Map<String, Any?> = emptyMap()
            override fun orientation(): String = "portrait"
            override fun network(): String = "none"
            override fun battery(): Map<String, Any?> = mapOf("level" to -1, "charging" to false)
            override fun keepAwake(on: Boolean): Boolean = false
            override fun openNav(request: PluginNavRequest): Boolean = false
        }
    }
}

internal class PluginAndroidLocal(
    private val app: Context,
    private val session: SessionRepository,
    private val community: WorkshopAuthStore,
    private val facts: () -> PluginHostFacts,
    private val nav: PluginNavBus,
) : PluginLocalHost {
    private val main = Handler(Looper.getMainLooper())

    override fun facts(): PluginHostFacts = facts()

    override fun user(): Map<String, Any?> {
        val music = session.session.value
        val uid = community.current()?.uid?.takeIf { it.isNotBlank() }
        return mapOf(
            "loggedIn" to (music != null),
            "guest" to (music?.isGuest == true),
            "label" to music?.displayLabel?.takeIf { it.isNotBlank() },
            "community" to mapOf(
                "loggedIn" to (uid != null),
                "uid" to uid,
            ),
        )
    }

    override fun locale(): String = I18n.language.tag

    override fun platform(): String = "android"

    override fun openUrl(url: String): Boolean {
        val raw = url.trim()
        if (raw.length !in 8..2000) return false
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { app.startActivity(intent) }.isSuccess
    }

    override fun haptic(ms: Int): Boolean {
        if (ms !in 1..400) return false
        val vibrator = app.getSystemService(Vibrator::class.java) ?: return false
        if (!vibrator.hasVibrator()) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms.toLong())
            }
        }.isSuccess
    }

    override fun screen(): Map<String, Any?> {
        val metrics = app.resources.displayMetrics
        return mapOf(
            "widthPx" to metrics.widthPixels,
            "heightPx" to metrics.heightPixels,
            "density" to metrics.density.toDouble(),
        )
    }

    override fun orientation(): String {
        return if (app.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) {
            "landscape"
        } else {
            "portrait"
        }
    }

    override fun network(): String {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return "none"
        val network = manager.activeNetwork ?: return "none"
        val caps = manager.getNetworkCapabilities(network) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    override fun battery(): Map<String, Any?> {
        val manager = app.getSystemService(BatteryManager::class.java)
        val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = manager?.isCharging == true
        return mapOf("level" to level, "charging" to charging)
    }

    override fun keepAwake(on: Boolean): Boolean {
        val activity = PluginForegroundActivity.get() ?: return false
        main.post {
            val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            if (on) activity.window.addFlags(flag) else activity.window.clearFlags(flag)
        }
        return true
    }

    override fun openNav(request: PluginNavRequest): Boolean = nav.open(request)
}
