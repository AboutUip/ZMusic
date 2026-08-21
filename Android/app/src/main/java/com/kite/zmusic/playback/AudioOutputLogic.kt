package com.kite.zmusic.playback

/**
 * 音频输出口筛选与命名。类型常量与 [android.media.AudioDeviceInfo] 对齐，
 * 逻辑不依赖 AudioDeviceInfo，便于单测。
 */
internal object AudioOutputTypes {
    const val UNKNOWN = 0
    const val BUILTIN_EARPIECE = 1
    const val BUILTIN_SPEAKER = 2
    const val WIRED_HEADSET = 3
    const val WIRED_HEADPHONES = 4
    const val LINE_ANALOG = 5
    const val LINE_DIGITAL = 6
    const val BLUETOOTH_SCO = 7
    const val BLUETOOTH_A2DP = 8
    const val HDMI = 9
    const val HDMI_ARC = 10
    const val USB_DEVICE = 11
    const val USB_ACCESSORY = 12
    const val DOCK = 13
    const val FM = 14
    const val AUX_LINE = 19
    const val IP = 20
    const val BUS = 21
    const val USB_HEADSET = 22
    const val HEARING_AID = 23
    const val BUILTIN_SPEAKER_SAFE = 24
    const val REMOTE_SUBMIX = 25
    const val BLE_HEADSET = 26
    const val BLE_SPEAKER = 27
    const val ECHO_REFERENCE = 28
    const val HDMI_EARC = 29
    const val BLE_BROADCAST = 30
    const val TELEPHONY = 18
    const val FM_TUNER = 16
    const val TV_TUNER = 17
    const val BUILTIN_MIC = 15
}

data class AudioOutputDevice(
    val id: Int,
    val type: Int,
    val address: String,
    val name: String,
)

data class AudioOutputPreference(
    val smart: Boolean,
    val deviceId: Int,
    val deviceType: Int,
    val address: String,
) {
    companion object {
        val Smart = AudioOutputPreference(
            smart = true,
            deviceId = 0,
            deviceType = 0,
            address = "",
        )
    }
}

data class AudioOutputUiState(
    val devices: List<AudioOutputDevice>,
    val preference: AudioOutputPreference,
    val active: AudioOutputDevice?,
) {
    val usingSmart: Boolean get() = preference.smart || active == null

    val moreSubtitle: String
        get() {
            val device = active
            return if (usingSmart || device == null) {
                "智能模式 · 由系统决定"
            } else {
                device.name
            }
        }
}

internal fun isListedAudioOutputType(type: Int): Boolean = when (type) {
    AudioOutputTypes.TELEPHONY,
    AudioOutputTypes.REMOTE_SUBMIX,
    AudioOutputTypes.ECHO_REFERENCE,
    AudioOutputTypes.FM_TUNER,
    AudioOutputTypes.TV_TUNER,
    AudioOutputTypes.BUILTIN_MIC,
    -> false
    else -> true
}

internal fun audioOutputFallbackName(type: Int): String = when (type) {
    AudioOutputTypes.BUILTIN_SPEAKER,
    AudioOutputTypes.BUILTIN_SPEAKER_SAFE,
    -> "本机扬声器"
    AudioOutputTypes.BUILTIN_EARPIECE -> "听筒"
    AudioOutputTypes.WIRED_HEADSET,
    AudioOutputTypes.WIRED_HEADPHONES,
    -> "有线耳机"
    AudioOutputTypes.USB_HEADSET,
    AudioOutputTypes.USB_DEVICE,
    AudioOutputTypes.USB_ACCESSORY,
    -> "USB 音频"
    AudioOutputTypes.BLUETOOTH_A2DP,
    AudioOutputTypes.BLUETOOTH_SCO,
    AudioOutputTypes.BLE_HEADSET,
    AudioOutputTypes.BLE_SPEAKER,
    AudioOutputTypes.BLE_BROADCAST,
    -> "蓝牙设备"
    AudioOutputTypes.HDMI,
    AudioOutputTypes.HDMI_ARC,
    AudioOutputTypes.HDMI_EARC,
    -> "HDMI"
    AudioOutputTypes.DOCK -> "底座"
    AudioOutputTypes.HEARING_AID -> "助听器"
    AudioOutputTypes.LINE_ANALOG,
    AudioOutputTypes.LINE_DIGITAL,
    AudioOutputTypes.AUX_LINE,
    -> "线路输出"
    AudioOutputTypes.FM -> "FM"
    AudioOutputTypes.IP -> "网络音频"
    else -> "音频设备"
}

internal fun audioOutputTypeRank(type: Int): Int = when (type) {
    AudioOutputTypes.BUILTIN_SPEAKER,
    AudioOutputTypes.BUILTIN_SPEAKER_SAFE,
    -> 0
    AudioOutputTypes.WIRED_HEADSET,
    AudioOutputTypes.WIRED_HEADPHONES,
    -> 1
    AudioOutputTypes.USB_HEADSET,
    AudioOutputTypes.USB_DEVICE,
    AudioOutputTypes.USB_ACCESSORY,
    -> 2
    AudioOutputTypes.BLUETOOTH_A2DP,
    AudioOutputTypes.BLE_HEADSET,
    AudioOutputTypes.BLE_SPEAKER,
    AudioOutputTypes.BLE_BROADCAST,
    -> 3
    AudioOutputTypes.BLUETOOTH_SCO -> 4
    AudioOutputTypes.HDMI,
    AudioOutputTypes.HDMI_ARC,
    AudioOutputTypes.HDMI_EARC,
    -> 5
    AudioOutputTypes.DOCK -> 6
    AudioOutputTypes.HEARING_AID -> 7
    AudioOutputTypes.BUILTIN_EARPIECE -> 8
    else -> 9
}

internal fun resolveAudioOutputDevice(
    devices: List<AudioOutputDevice>,
    preference: AudioOutputPreference,
): AudioOutputDevice? {
    if (preference.smart) return null
    val address = preference.address.trim()
    if (address.isNotEmpty()) {
        devices.firstOrNull { it.address == address && it.type == preference.deviceType }
            ?.let { return it }
        devices.firstOrNull { it.address == address }?.let { return it }
    }
    devices.firstOrNull { it.id == preference.deviceId }?.let { return it }
    return devices.firstOrNull { it.type == preference.deviceType }
}

internal fun sortAudioOutputDevices(
    devices: List<AudioOutputDevice>,
): List<AudioOutputDevice> =
    devices.sortedWith(
        compareBy<AudioOutputDevice> { audioOutputTypeRank(it.type) }
            .thenBy { it.name }
            .thenBy { it.id },
    )
