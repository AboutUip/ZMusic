package com.kite.zmusic.playback

import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 歌单分页缓存 ↔ 播放队列：后台补全能扩当前队列；播放页补全后写回缓存。
 */
class PlaybackQueueSync(
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val playlistTracksCache: PlaylistTracksCache,
    private val sessionRepository: SessionRepository,
    private val playbackBridge: PlaybackBridge,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start() {
        wirePlaylistQueueSync()
        wirePlaybackQueueAbsorb()
    }

    fun isSourcePlaylistComplete(playlistId: Long): Boolean {
        if (playlistId <= 0L) return true
        likedPlaylistRepository.peek()?.takeIf { it.playlistId == playlistId }?.let {
            return it.complete
        }
        return playlistTracksCache.peek(playlistId)?.complete ?: true
    }

    /** 按分页缓存把同源歌单补到至少 [minCount] 首；同步扩展播放队列。 */
    fun hydrateSourcePlaylist(playlistId: Long, minCount: Int) {
        if (playlistId <= 0L || minCount <= 0) return
        scope.launch {
            val liked = likedPlaylistRepository.peek()
            if (liked != null && liked.playlistId == playlistId) {
                likedPlaylistRepository.ensureLoadedThrough(minCount)
                return@launch
            }
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            if (cookie.isBlank()) return@launch
            val title = playlistTracksCache.peek(playlistId)?.title.orEmpty()
            runCatching {
                playlistTracksCache.ensureLoadedThrough(playlistId, title, cookie, minCount)
            }
        }
    }

    private fun wirePlaylistQueueSync() {
        scope.launch {
            likedPlaylistRepository.snapshot.collectLatest { snap ->
                if (snap == null || snap.playlistId <= 0L || snap.tracks.isEmpty()) return@collectLatest
                if (playbackBridge.ui.value.intelligenceActive) return@collectLatest
                playbackBridge.expandQueueFromSourcePlaylist(snap.playlistId, snap.tracks)
            }
        }
        scope.launch {
            playlistTracksCache.updates.collect { entry ->
                if (entry.tracks.isEmpty()) return@collect
                if (playbackBridge.ui.value.intelligenceActive) return@collect
                playbackBridge.expandQueueFromSourcePlaylist(entry.playlistId, entry.tracks)
            }
        }
    }

    private fun wirePlaybackQueueAbsorb() {
        scope.launch {
            playbackBridge.ui
                .map { ui -> ui.sourcePlaylistId to ui.queue.size }
                .distinctUntilChanged()
                .collect { (pid, size) ->
                    if (pid == null || pid <= 0L || size <= 1) return@collect
                    if (playbackBridge.ui.value.intelligenceActive) return@collect
                    val queue = playbackBridge.ui.value.queue
                    if (queue.size < size) return@collect
                    val liked = likedPlaylistRepository.peek()
                    if (liked != null && liked.playlistId == pid) {
                        runCatching { likedPlaylistRepository.absorbIfLonger(queue) }
                    } else {
                        runCatching { playlistTracksCache.absorbIfLonger(pid, queue) }
                    }
                }
        }
    }
}
