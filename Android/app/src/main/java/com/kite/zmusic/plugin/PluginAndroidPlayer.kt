package com.kite.zmusic.plugin

import android.os.Handler
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.SongRepository
import com.kite.zmusic.playback.PlaybackBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 主线程转发播放控制；喜欢走与播放页相同的本地优先再同步远端。
 * 不在插件线程等待主线程，避免 `delay` 之外再卡住 JS。
 */
internal class PluginAndroidPlayer(
    private val mainHandler: Handler,
    private val playback: PlaybackBridge,
    private val likedRepo: LikedPlaylistRepository,
    private val session: SessionRepository,
    private val songs: SongRepository,
    private val online: () -> Boolean,
    private val ioScope: CoroutineScope,
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
        if (!online()) return false
        val sess = session.session.value ?: return false
        if (sess.isGuest) return false
        val cookie = sess.cookie
        if (cookie.isBlank()) return false
        val track = playback.ui.value.currentTrack ?: return false
        likedRepo.applyLocalLike(track, liked = liked)
        ioScope.launch {
            try {
                val ack = songs.likeSong(track.id, like = liked, cookie = cookie)
                if (!ack.ok) {
                    likedRepo.applyLocalLike(track, liked = !liked, scheduleSync = false)
                }
            } catch (_: Exception) {
                likedRepo.applyLocalLike(track, liked = !liked, scheduleSync = false)
            }
        }
        return true
    }
}
