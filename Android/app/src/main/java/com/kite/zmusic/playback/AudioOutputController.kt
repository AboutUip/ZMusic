package com.kite.zmusic.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.kite.zmusic.data.AudioOutputStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 列出当前所有音频输出口，并把用户选择应用到 ExoPlayer。
 * 智能模式：[ExoPlayer.setPreferredAudioDevice] 传 null，交给系统。
 */
@OptIn(UnstableApi::class)
class AudioOutputController(
    context: Context,
    private val store: AudioOutputStore,
) {
    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var player: ExoPlayer? = null

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<AudioOutputUiState> = _state.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(callback, mainHandler)
        refresh()
    }

    fun attachPlayer(exo: ExoPlayer) {
        player = exo
        applyToPlayer()
    }

    fun detachPlayer(exo: ExoPlayer) {
        if (player === exo) {
            player = null
        }
    }

    fun selectSmart() {
        store.setSmart()
        applyToPlayer()
        refresh()
    }

    fun selectDevice(device: AudioOutputDevice) {
        store.setDevice(device.id, device.type, device.address)
        applyToPlayer()
        refresh()
    }

    fun refresh() {
        _state.value = snapshot()
        applyToPlayer()
    }

    private fun snapshot(): AudioOutputUiState {
        val devices = sortAudioOutputDevices(listOutputs())
        val preference = store.current()
        val active = resolveAudioOutputDevice(devices, preference)
        return AudioOutputUiState(
            devices = devices,
            preference = preference,
            active = active,
        )
    }

    private fun listOutputs(): List<AudioOutputDevice> {
        val infos = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())
        return infos.mapNotNull { info ->
            if (!isListedAudioOutputType(info.type)) return@mapNotNull null
            val product = info.productName?.toString()?.trim().orEmpty()
            AudioOutputDevice(
                id = info.id,
                type = info.type,
                address = info.address.orEmpty(),
                name = product.ifBlank { audioOutputFallbackName(info.type) },
            )
        }
    }

    private fun applyToPlayer() {
        val exo = player ?: return
        val current = _state.value
        val target = if (current.usingSmart) {
            null
        } else {
            findLiveInfo(current.active)
        }
        runCatching { exo.setPreferredAudioDevice(target) }
    }

    private fun findLiveInfo(device: AudioOutputDevice?): AudioDeviceInfo? {
        if (device == null) return null
        val infos = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())
        return infos.firstOrNull { it.id == device.id }
            ?: infos.firstOrNull {
                it.address == device.address &&
                    device.address.isNotBlank() &&
                    it.type == device.type
            }
    }
}
