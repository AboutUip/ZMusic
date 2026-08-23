package com.kite.zmusic.playback

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 前台时把蓝牙音频连上/断开、充电/断电发到灵动岛。
 * 只认音频输出口（A2DP / LE Audio 等），不报手环一类非音频蓝牙。
 */
class DeviceLinkMonitor(
    private val app: Application,
    private val audioOutput: AudioOutputController,
    private val notices: IslandNoticeCenter,
) {
    private val appContext = app.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val debouncer = BluetoothLinkDebouncer()
    private val disconnectJobs = mutableMapOf<String, Job>()

    private var started = false
    private var seeded = false
    private var previousBluetooth = emptyMap<String, String>()
    private var startedActivities = 0
    private var lastAnnouncedPlugged: Boolean? = null
    private var powerJob: Job? = null
    private var collectJob: Job? = null

    fun start() {
        if (started) return
        started = true
        lastAnnouncedPlugged = readPowerSnapshot()?.plugged
        app.registerActivityLifecycleCallbacks(activityCallbacks)
        collectJob = scope.launch {
            audioOutput.state
                .map { coalesceBluetoothAudio(it.devices) }
                .distinctUntilChanged()
                .collect { current ->
                    if (!seeded) {
                        previousBluetooth = current
                        seeded = true
                        return@collect
                    }
                    val diff = diffBluetoothAudio(previousBluetooth, current)
                    previousBluetooth = current
                    applyBluetoothDiff(diff)
                }
        }
        ContextCompat.registerReceiver(
            appContext,
            powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun applyBluetoothDiff(diff: BluetoothAudioDiff) {
        for ((key, name) in diff.connected) {
            disconnectJobs.remove(key)?.cancel()
            if (debouncer.connected(key)) {
                showIfForeground(bluetoothConnectNotice(name))
            }
        }
        for ((key, name) in diff.disconnected) {
            disconnectJobs.remove(key)?.cancel()
            debouncer.disconnected(key, name)
            disconnectJobs[key] = scope.launch {
                delay(DISCONNECT_DEBOUNCE_MS)
                disconnectJobs.remove(key)
                val shown = debouncer.confirmDisconnect(key) ?: return@launch
                showIfForeground(bluetoothDisconnectNotice(shown))
            }
        }
    }

    private fun schedulePowerNotice() {
        powerJob?.cancel()
        powerJob = scope.launch {
            delay(POWER_DEBOUNCE_MS)
            val snap = readPowerSnapshot() ?: return@launch
            if (lastAnnouncedPlugged == snap.plugged) return@launch
            lastAnnouncedPlugged = snap.plugged
            showIfForeground(powerNotice(snap))
        }
    }

    private fun showIfForeground(message: String) {
        if (startedActivities <= 0) return
        notices.show(message)
    }

    private fun readPowerSnapshot(): PowerSnapshot? {
        val battery = runCatching {
            ContextCompat.registerReceiver(
                appContext,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.getOrNull() ?: return null
        val pluggedType = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return powerSnapshotFromExtras(pluggedType, level, scale)
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                -> schedulePowerNotice()
            }
        }
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    companion object {
        private const val DISCONNECT_DEBOUNCE_MS = 700L
        private const val POWER_DEBOUNCE_MS = 450L
    }
}
