package com.kite.zmusic.data

import android.content.Context
import com.kite.zmusic.playback.AudioOutputPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放输出：默认智能模式（交由系统选择当前设备）。
 */
class AudioOutputStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _preference = MutableStateFlow(read())
    val preference: StateFlow<AudioOutputPreference> = _preference.asStateFlow()

    fun current(): AudioOutputPreference = _preference.value

    fun setSmart() {
        write(AudioOutputPreference.Smart)
    }

    fun setDevice(id: Int, type: Int, address: String) {
        write(
            AudioOutputPreference(
                smart = false,
                deviceId = id,
                deviceType = type,
                address = address,
            ),
        )
    }

    private fun read(): AudioOutputPreference {
        val smart = prefs.getBoolean(KEY_SMART, true)
        if (smart) return AudioOutputPreference.Smart
        return AudioOutputPreference(
            smart = false,
            deviceId = prefs.getInt(KEY_ID, 0),
            deviceType = prefs.getInt(KEY_TYPE, 0),
            address = prefs.getString(KEY_ADDRESS, "").orEmpty(),
        )
    }

    private fun write(next: AudioOutputPreference) {
        prefs.edit()
            .putBoolean(KEY_SMART, next.smart)
            .putInt(KEY_ID, next.deviceId)
            .putInt(KEY_TYPE, next.deviceType)
            .putString(KEY_ADDRESS, next.address)
            .apply()
        _preference.value = next
    }

    companion object {
        const val PREFS = "zmusic_audio_output"
        const val KEY_SMART = "smart"
        const val KEY_ID = "device_id"
        const val KEY_TYPE = "device_type"
        const val KEY_ADDRESS = "address"
    }
}
