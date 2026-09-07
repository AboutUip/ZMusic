package com.kite.zmusic.plugin

import android.os.Handler
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.playback.PlaybackBridge

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
