package com.kite.zmusic

import android.app.Application
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.playback.PlaybackBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ZMusicApplication : Application() {
    lateinit var sessionRepository: SessionRepository
        private set
    lateinit var playbackBridge: PlaybackBridge
        private set
    lateinit var likedPlaylistRepository: LikedPlaylistRepository
        private set
    lateinit var playlistTracksCache: PlaylistTracksCache
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        sessionRepository = SessionRepository(this)
        playbackBridge = PlaybackBridge(this, sessionRepository)
        likedPlaylistRepository = LikedPlaylistRepository(this, sessionRepository)
        playlistTracksCache = PlaylistTracksCache(this)
        wirePlaylistQueueSync()
    }

    /** 歌单后台补全 → 同步扩展当前播放队列（横屏曲谱等依赖 queue）。 */
    private fun wirePlaylistQueueSync() {
        appScope.launch {
            likedPlaylistRepository.snapshot.collectLatest { snap ->
                if (snap == null || snap.playlistId <= 0L || snap.tracks.isEmpty()) return@collectLatest
                playbackBridge.expandQueueFromSourcePlaylist(snap.playlistId, snap.tracks)
            }
        }
        appScope.launch {
            playlistTracksCache.updates.collect { entry ->
                if (entry.tracks.isEmpty()) return@collect
                playbackBridge.expandQueueFromSourcePlaylist(entry.playlistId, entry.tracks)
            }
        }
    }
}
