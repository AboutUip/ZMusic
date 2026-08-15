package com.kite.zmusic

import android.app.Application
import com.kite.zmusic.data.LibraryHomeRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlaylistEditor
import com.kite.zmusic.data.PlaylistCollectionRepository
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SearchHistoryRepository
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.SessionWarmup
import com.kite.zmusic.data.TrackExportRepository
import com.kite.zmusic.data.UserSpaceBackgroundStore
import com.kite.zmusic.playback.MvPlayback
import com.kite.zmusic.playback.PlaybackBridge
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ZMusicApplication : Application() {
    lateinit var sessionRepository: SessionRepository
        private set
    lateinit var playbackBridge: PlaybackBridge
        private set
    lateinit var likedPlaylistRepository: LikedPlaylistRepository
        private set
    lateinit var homeFeedRepository: HomeFeedRepository
        private set
    lateinit var playlistTracksCache: PlaylistTracksCache
        private set
    lateinit var searchHistoryRepository: SearchHistoryRepository
        private set
    lateinit var playlistCollectionRepository: PlaylistCollectionRepository
        private set
    lateinit var libraryHomeRepository: LibraryHomeRepository
        private set
    lateinit var userSpaceBackgroundStore: UserSpaceBackgroundStore
        private set
    lateinit var sessionWarmup: SessionWarmup
        private set
    lateinit var islandNoticeCenter: IslandNoticeCenter
        private set
    lateinit var trackExportRepository: TrackExportRepository
        private set
    lateinit var playlistEditor: PlaylistEditor
        private set
    lateinit var mvPlayback: MvPlayback
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        sessionRepository = SessionRepository(this)
        playbackBridge = PlaybackBridge(this, sessionRepository)
        likedPlaylistRepository = LikedPlaylistRepository(this, sessionRepository)
        homeFeedRepository = HomeFeedRepository(sessionRepository)
        playlistTracksCache = PlaylistTracksCache(this)
        searchHistoryRepository = SearchHistoryRepository(this)
        playlistCollectionRepository = PlaylistCollectionRepository()
        libraryHomeRepository = LibraryHomeRepository(
            sessionRepository,
            likedPlaylistRepository,
            playlistCollectionRepository,
        )
        userSpaceBackgroundStore = UserSpaceBackgroundStore(this)
        sessionWarmup = SessionWarmup(
            sessionRepository,
            onUserId = { uid -> playlistCollectionRepository.setSelfUserId(uid) },
        )
        islandNoticeCenter = IslandNoticeCenter()
        trackExportRepository = TrackExportRepository(this)
        playlistEditor = PlaylistEditor(
            sessionRepository,
            NcmUserClient(),
            playlistCollectionRepository,
            playlistTracksCache,
            likedPlaylistRepository,
            libraryHomeRepository,
        )
        mvPlayback = MvPlayback(this, sessionRepository, playbackBridge)
        playbackBridge.musicWillPlay = { mvPlayback.stop() }
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

    /** 按分页缓存把同源歌单补到至少 [minCount] 首；[wirePlaylistQueueSync] 会扩播放队列。 */
    fun hydrateSourcePlaylist(playlistId: Long, minCount: Int) {
        if (playlistId <= 0L || minCount <= 0) return
        appScope.launch {
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

    /** 播放页（曲谱 / 黑胶）把队列补全后，写回歌单缓存，避免退出仍停在首屏 40 首。 */
    private fun wirePlaybackQueueAbsorb() {
        appScope.launch {
            playbackBridge.ui
                .map { ui -> ui.sourcePlaylistId to ui.queue.size }
                .distinctUntilChanged()
                .collect { (pid, size) ->
                    if (pid == null || pid <= 0L || size <= 1) return@collect
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
