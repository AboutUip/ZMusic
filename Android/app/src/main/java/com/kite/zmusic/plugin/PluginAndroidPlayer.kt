package com.kite.zmusic.plugin

import android.os.Handler
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.playback.PlaybackBridge
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.playback.SleepTimer

/**
 * 主线程转发播放控制；喜欢先改本地再后台确认，失败重试后回滚。
 * 不在插件线程等待主线程，避免 `delay` 之外再卡住 JS。
 */
internal class PluginAndroidPlayer(
    private val mainHandler: Handler,
    private val playback: PlaybackBridge,
    private val likedRepo: LikedPlaylistRepository,
    private val session: SessionRepository,
) : PluginPlayerController {
    override fun play(): Boolean {
        val ui = playback.ui.value
        if (!ui.hasQueue || ui.index < 0) return false
        mainHandler.post {
            playback.ensureService()
            if (!playback.ui.value.playWhenReady) playback.togglePlayPause()
        }
        return true
    }

    override fun pause(): Boolean {
        if (!playback.ui.value.hasQueue) return false
        mainHandler.post {
            if (playback.ui.value.playWhenReady) playback.togglePlayPause()
        }
        return true
    }

    override fun next(): Boolean {
        if (!playback.ui.value.hasQueue) return false
        mainHandler.post { playback.skipNext() }
        return true
    }

    override fun prev(): Boolean {
        if (!playback.ui.value.hasQueue) return false
        mainHandler.post { playback.skipPrevious() }
        return true
    }

    override fun seek(ms: Long): Boolean {
        if (!playback.ui.value.hasQueue) return false
        mainHandler.post { playback.seekTo(ms.coerceAtLeast(0L)) }
        return true
    }

    override fun setVolume(level: Float): Boolean {
        playback.setUserVolume(level)
        return true
    }

    override fun volume(): Float = playback.userVolume()

    override fun setPlaybackRate(rate: Float): Boolean {
        playback.setPluginPlaybackRate(rate)
        return true
    }

    override fun playbackRate(): Float = playback.pluginPlaybackRate()

    override fun mode(): String = playback.ui.value.playbackMode.pluginName()

    override fun setMode(name: String): Boolean {
        val mode = playbackModeFromPlugin(name) ?: return false
        return playback.setPlaybackMode(mode)
    }

    override fun cycleMode(): Boolean {
        mainHandler.post { playback.cyclePlaybackMode() }
        return true
    }

    override fun playAt(index: Int): Boolean {
        val ui = playback.ui.value
        if (!ui.hasQueue || index !in ui.queue.indices) return false
        mainHandler.post { playback.playIndex(index) }
        return true
    }

    override fun clearQueue(): Boolean {
        mainHandler.post { playback.clearQueue() }
        return true
    }

    override fun openPlayer(): Boolean {
        mainHandler.post { playback.requestOpenPlayer() }
        return true
    }

    override fun sleep(): Map<String, Any?> {
        val ui = playback.sleepTimer.value
        return mapOf(
            "active" to ui.active,
            "remainingMs" to ui.remainingMs.coerceAtLeast(0L),
            "waitForTrackEnd" to ui.waitForTrackEnd,
            "pendingStopAfterTrack" to ui.pendingStopAfterTrack,
        )
    }

    override fun sleepStart(minutes: Int, waitForTrackEnd: Boolean): Boolean {
        if (minutes !in SleepTimer.MIN_MINUTES..SleepTimer.MAX_MINUTES) return false
        mainHandler.post { playback.startSleepTimer(minutes, waitForTrackEnd) }
        return true
    }

    override fun sleepCancel(): Boolean {
        mainHandler.post { playback.cancelSleepTimer() }
        return true
    }

    override fun lyric(): Map<String, Any?> {
        val ui = playback.ui.value
        return PluginLyricMaps.pack(
            original = ui.lyricLines,
            translated = ui.translatedLyricLines,
            positionMs = ui.lyricClockMs(),
        )
    }

    override fun lyricLine(): Map<String, Any?>? {
        val ui = playback.ui.value
        return PluginLyricMaps.current(
            original = ui.lyricLines,
            translated = ui.translatedLyricLines,
            positionMs = ui.lyricClockMs(),
        )
    }

    override fun setLiked(liked: Boolean): Boolean {
        val sess = session.session.value ?: return false
        if (sess.isGuest) return false
        val cookie = sess.cookie
        if (cookie.isBlank()) return false
        val track = playback.ui.value.currentTrack ?: return false
        likedRepo.applyLocalLike(track, liked = liked)
        likedRepo.submitLike(track, liked, cookie)
        return true
    }
}
