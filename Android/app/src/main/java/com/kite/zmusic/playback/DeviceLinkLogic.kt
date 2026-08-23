package com.kite.zmusic.playback

/**
 * 蓝牙音频路由变化、充电状态变化的纯逻辑。
 * 不读 AudioDeviceInfo / BatteryManager，便于单测。
 */
internal data class BluetoothAudioDiff(
    val connected: Map<String, String>,
    val disconnected: Map<String, String>,
)

internal data class PowerSnapshot(
    val plugged: Boolean,
    val percent: Int?,
    val wireless: Boolean,
)

internal fun isBluetoothAudioOutputType(type: Int): Boolean = when (type) {
    AudioOutputTypes.BLUETOOTH_SCO,
    AudioOutputTypes.BLUETOOTH_A2DP,
    AudioOutputTypes.BLE_HEADSET,
    AudioOutputTypes.BLE_SPEAKER,
    AudioOutputTypes.BLE_BROADCAST,
    -> true
    else -> false
}

internal fun bluetoothDisplayName(raw: String): String =
    raw.trim().ifBlank { "蓝牙设备" }

internal fun bluetoothAudioKey(device: AudioOutputDevice): String {
    val address = device.address.trim()
    if (address.isNotEmpty()) return address.lowercase()
    return "name:${bluetoothDisplayName(device.name).lowercase()}"
}

/**
 * 同一副耳机常同时冒出 A2DP + SCO。按地址合并，优先留下非通话口的名字。
 */
internal fun coalesceBluetoothAudio(devices: List<AudioOutputDevice>): Map<String, String> {
    val ranked = devices
        .filter { isBluetoothAudioOutputType(it.type) }
        .sortedBy { bluetoothAudioTypeRank(it.type) }
    val out = LinkedHashMap<String, String>()
    for (device in ranked) {
        val key = bluetoothAudioKey(device)
        if (key in out) continue
        out[key] = bluetoothDisplayName(device.name)
    }
    return out
}

internal fun diffBluetoothAudio(
    previous: Map<String, String>,
    current: Map<String, String>,
): BluetoothAudioDiff = BluetoothAudioDiff(
    connected = current.filterKeys { it !in previous },
    disconnected = previous.filterKeys { it !in current },
)

internal fun bluetoothConnectNotice(name: String): String = "${bluetoothDisplayName(name)} 已连接"

internal fun bluetoothDisconnectNotice(name: String): String = "${bluetoothDisplayName(name)} 已断开"

internal fun powerNotice(snapshot: PowerSnapshot): String {
    val pct = snapshot.percent?.takeIf { it in 0..100 }?.let { " · $it%" }.orEmpty()
    return when {
        !snapshot.plugged -> "已断开电源$pct"
        snapshot.wireless -> "已开始无线充电$pct"
        else -> "已开始充电$pct"
    }
}

internal fun powerSnapshotFromExtras(pluggedType: Int, level: Int, scale: Int): PowerSnapshot {
    val percent = if (level >= 0 && scale > 0) {
        ((level * 100f) / scale).toInt().coerceIn(0, 100)
    } else {
        null
    }
    return PowerSnapshot(
        plugged = pluggedType != 0,
        percent = percent,
        wireless = pluggedType == BATTERY_PLUGGED_WIRELESS,
    )
}

/**
 * 短时闪断：断开后马上又连上，两边都不报。
 */
internal class BluetoothLinkDebouncer {
    private val pendingDisconnect = mutableMapOf<String, String>()

    /** @return true 表示应立刻报「已连接」；false 表示只取消待报的断开。 */
    fun connected(key: String): Boolean = pendingDisconnect.remove(key) == null

    fun disconnected(key: String, name: String) {
        pendingDisconnect[key] = name
    }

    /** @return 待报的设备名；null 表示已在延迟期内重连，不再报断开。 */
    fun confirmDisconnect(key: String): String? = pendingDisconnect.remove(key)
}

private fun bluetoothAudioTypeRank(type: Int): Int = when (type) {
    AudioOutputTypes.BLE_HEADSET,
    AudioOutputTypes.BLE_SPEAKER,
    AudioOutputTypes.BLE_BROADCAST,
    -> 0
    AudioOutputTypes.BLUETOOTH_A2DP -> 1
    AudioOutputTypes.BLUETOOTH_SCO -> 2
    else -> 9
}

/** 与 [android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS] 对齐。 */
internal const val BATTERY_PLUGGED_WIRELESS = 4
