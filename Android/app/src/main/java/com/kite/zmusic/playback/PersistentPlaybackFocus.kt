package com.kite.zmusic.playback

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.kite.zmusic.data.PersistentPlaybackStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 持续播放：我们正在播时，不因焦点暂停；被压制时短暂压低音量再恢复，与对方同时出声。
 * 用户手动暂停后不再自动开播（短视频播完时系统/竞争回调会误续）。
 * MV 占用（[setForeignYield]）仍暂停歌曲。
 */
@UnstableApi
internal class PersistentPlaybackFocus(
    context: Context,
    private val store: PersistentPlaybackStore,
    private val scope: CoroutineScope,
    private val player: ExoPlayer,
    private val musicAudioAttrs: AudioAttributes,
) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    /** 游戏用途较少被短视频应用按「后台音乐」静音；音量键仍走媒体流。 */
    private val mixAudioAttrs = AudioAttributes.Builder()
        .setUsage(C.USAGE_GAME)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private var enabled = false
    private var foreignYield = false
    /** 用户（或我们主动）要保持播放；手动暂停后为 false。 */
    private var holdPlayback = false
    private var othersPlaying = false
    private var suppressAutoResumeUntil = 0L
    private var lastDipAt = 0L
    private var volumeJob: Job? = null
    private var collectJob: Job? = null
    private var callbackRegistered = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        onFocusChange(change)
    }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            PlatformAudioAttributes.Builder()
                .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
                .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setWillPauseWhenDucked(false)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(focusListener, handler)
        .build()

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            if (!enabled || foreignYield) return
            val others = otherAppsPlaying(configs)
            if (others && !othersPlaying) {
                abandonFocus()
                if (holdPlayback) {
                    ensurePlaying()
                    dipThenRestore()
                }
            } else if (!others && othersPlaying && !holdPlayback) {
                suppressAutoResumeUntil = SystemClock.elapsedRealtime() + SUPPRESS_AUTO_RESUME_MS
            }
            othersPlaying = others
        }
    }

    fun start() {
        holdPlayback = player.playWhenReady
        applyPolicy(store.current())
        collectJob = scope.launch {
            store.enabled.drop(1).collect { applyPolicy(it) }
        }
    }

    fun release() {
        collectJob?.cancel()
        collectJob = null
        volumeJob?.cancel()
        volumeJob = null
        unregisterPlaybackCallback()
        abandonFocus()
        player.volume = 1f
    }

    fun setForeignYield(active: Boolean) {
        if (foreignYield == active) return
        foreignYield = active
        volumeJob?.cancel()
        volumeJob = null
        if (active) {
            if (player.playWhenReady) player.pause()
            player.setAudioAttributes(musicAudioAttrs, false)
            abandonFocus()
            player.volume = 1f
        } else {
            applyPolicy(enabled)
            if (holdPlayback) ensurePlaying()
        }
    }

    /**
     * 焦点被抢而暂停时立刻续播，并做一次压低再恢复。
     * 手动暂停后不拦截、也不把后续系统续播放行成「还在播」。
     * @return true 表示已拦截，调用方不要把 UI 写成暂停。
     */
    fun consumePlayWhenReadyChange(playWhenReady: Boolean, reason: Int): Boolean {
        if (!enabled || foreignYield) {
            return false
        }
        if (playWhenReady) {
            if (!holdPlayback && SystemClock.elapsedRealtime() < suppressAutoResumeUntil) {
                handler.post {
                    if (!holdPlayback && player.playWhenReady) player.pause()
                }
                return true
            }
            holdPlayback = true
            return false
        }
        when (reason) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
            -> {
                holdPlayback = false
                return false
            }
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> {
                if (!holdPlayback) return false
                abandonFocus()
                player.play()
                dipThenRestore()
                return true
            }
            else -> return false
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!enabled || foreignYield || isPlaying || !holdPlayback) return
        if (!player.playWhenReady || player.playbackState != Player.STATE_READY) return
        if (player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) return
        player.play()
        dipThenRestore()
    }

    fun fadeInFromSilence(durationMs: Long) {
        fadeVolume(from = 0f, to = 1f, durationMs = durationMs)
    }

    fun snapVolume() {
        if (volumeJob?.isActive == true) return
        player.volume = 1f
    }

    private fun applyPolicy(nextEnabled: Boolean) {
        enabled = nextEnabled
        othersPlaying = false
        volumeJob?.cancel()
        volumeJob = null
        if (foreignYield) {
            player.setAudioAttributes(musicAudioAttrs, false)
            unregisterPlaybackCallback()
            abandonFocus()
            player.volume = 1f
            return
        }
        if (enabled) {
            // 不让 Exo 处理焦点，也不去抢 AUDIOFOCUS_GAIN，避免成为被系统压着的「前任持有者」。
            player.setAudioAttributes(mixAudioAttrs, false)
            abandonFocus()
            player.volume = 1f
            registerPlaybackCallback()
            othersPlaying = otherAppsPlaying(audioManager.activePlaybackConfigurations)
        } else {
            unregisterPlaybackCallback()
            abandonFocus()
            player.volume = 1f
            player.setAudioAttributes(musicAudioAttrs, true)
        }
    }

    private fun onFocusChange(change: Int) {
        if (!enabled || foreignYield) return
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                abandonFocus()
                if (holdPlayback) {
                    ensurePlaying()
                    dipThenRestore()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!holdPlayback) return
                ensurePlaying()
                if (volumeJob?.isActive != true) player.volume = 1f
            }
        }
    }

    private fun ensurePlaying() {
        if (foreignYield || !holdPlayback) return
        if (!player.playWhenReady) player.play()
    }

    private fun abandonFocus() {
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
    }

    private fun registerPlaybackCallback() {
        if (callbackRegistered) return
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        callbackRegistered = true
    }

    private fun unregisterPlaybackCallback() {
        if (!callbackRegistered) return
        runCatching { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
        callbackRegistered = false
    }

    /** 持续播放时自己走 USAGE_GAME，其它媒体流即外部竞争。 */
    private fun otherAppsPlaying(configs: List<AudioPlaybackConfiguration>): Boolean =
        configs.any { cfg ->
            val usage = cfg.audioAttributes.usage
            usage != PlatformAudioAttributes.USAGE_GAME && isCompetingUsage(usage)
        }

    private fun isCompetingUsage(usage: Int): Boolean = when (usage) {
        PlatformAudioAttributes.USAGE_MEDIA,
        PlatformAudioAttributes.USAGE_GAME,
        PlatformAudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        PlatformAudioAttributes.USAGE_VOICE_COMMUNICATION,
        PlatformAudioAttributes.USAGE_ASSISTANT,
        -> true
        else -> false
    }

    private fun dipThenRestore() {
        if (!enabled || foreignYield || !holdPlayback) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDipAt < DIP_COOLDOWN_MS) {
            ensurePlaying()
            if (volumeJob?.isActive != true) player.volume = 1f
            return
        }
        lastDipAt = now
        volumeJob?.cancel()
        volumeJob = scope.launch {
            ensurePlaying()
            animateVolume(player.volume, DIP_LEVEL, DIP_DOWN_MS)
            if (!isActive) return@launch
            delay(DIP_HOLD_MS)
            if (!isActive) return@launch
            ensurePlaying()
            animateVolume(player.volume, 1f, DIP_UP_MS)
            if (!isActive) return@launch
            player.volume = 1f
        }
    }

    private fun fadeVolume(from: Float, to: Float, durationMs: Long) {
        volumeJob?.cancel()
        volumeJob = scope.launch {
            animateVolume(from, to, durationMs)
        }
    }

    private suspend fun animateVolume(from: Float, to: Float, durationMs: Long) {
        val start = from.coerceIn(0f, 1f)
        val end = to.coerceIn(0f, 1f)
        if (abs(end - start) < 0.01f) {
            player.volume = end
            return
        }
        val steps = 20
        val stepDelay = (durationMs / steps).coerceAtLeast(1L)
        for (i in 1..steps) {
            if (!currentCoroutineContext().isActive) return
            delay(stepDelay)
            val t = i / steps.toFloat()
            val eased = 1f - (1f - t) * (1f - t)
            player.volume = start + (end - start) * eased
        }
        player.volume = end
    }

    companion object {
        private const val DIP_LEVEL = 0.45f
        private const val DIP_DOWN_MS = 180L
        private const val DIP_HOLD_MS = 240L
        private const val DIP_UP_MS = 480L
        private const val DIP_COOLDOWN_MS = 1_600L
        /** 其他应用刚停声后，拦截系统把我们当「前任音乐」自动续上。 */
        private const val SUPPRESS_AUTO_RESUME_MS = 2_500L
    }
}
