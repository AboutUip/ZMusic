package com.kite.zmusic

import android.app.Application
import android.content.res.Configuration
import com.kite.zmusic.data.AlbumCollectionRepository
import com.kite.zmusic.data.AlbumTracksCache
import com.kite.zmusic.data.AudioQualityStore
import com.kite.zmusic.data.LyricOverlayStore
import com.kite.zmusic.data.LyricRenderStore
import com.kite.zmusic.data.PersistentPlaybackStore
import com.kite.zmusic.data.ChromeGlassStore
import com.kite.zmusic.data.ThemeStore
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.LibraryHomeRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlaylistCollectionRepository
import com.kite.zmusic.data.PlaylistEditor
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SearchHistoryRepository
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.SessionWarmup
import com.kite.zmusic.data.TrackExportRepository
import com.kite.zmusic.data.UserSpaceBackgroundStore
import com.kite.zmusic.data.NetworkPhase
import com.kite.zmusic.overlay.LyricOverlayController
import com.kite.zmusic.playback.MvPlayback
import com.kite.zmusic.playback.PlaybackBridge
import com.kite.zmusic.playback.PlaybackQueueSync
import com.kite.zmusic.plugin.PluginHostFacts
import com.kite.zmusic.plugin.PluginPlaybackSnapshot
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import com.kite.zmusic.ui.theme.MainColors
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ZMusicApplication : Application() {
    lateinit var container: AppContainer
        private set

    val ncmUserClient: NcmUserClient get() = container.ncmUserClient
    val ncmAuthClient get() = container.ncmAuthClient
    val sessionRepository: SessionRepository get() = container.sessionRepository
    val communityServerStore get() = container.communityServerStore
    val communityLoginRepository get() = container.communityLoginRepository
    val audioQualityStore: AudioQualityStore get() = container.audioQualityStore
    val audioOutputController get() = container.audioOutputController
    val persistentPlaybackStore: PersistentPlaybackStore get() = container.persistentPlaybackStore
    val predictiveBackStore get() = container.predictiveBackStore
    val landscapeModeStore get() = container.landscapeModeStore
    val lyricRenderStore: LyricRenderStore get() = container.lyricRenderStore
    val lyricOverlayStore: LyricOverlayStore get() = container.lyricOverlayStore
    val chromeGlassStore: ChromeGlassStore get() = container.chromeGlassStore
    val themeStore: ThemeStore get() = container.themeStore
    val chromeWallpaperStore get() = container.chromeWallpaperStore
    val downloadAccelStore get() = container.downloadAccelStore
    val downloadAccelIndex get() = container.downloadAccelIndex
    val realtimeCacheStore get() = container.realtimeCacheStore
    val realtimeCache get() = container.realtimeCache
    val playbackBridge: PlaybackBridge get() = container.playbackBridge
    val likedPlaylistRepository: LikedPlaylistRepository get() = container.likedPlaylistRepository
    val homeFeedRepository: HomeFeedRepository get() = container.homeFeedRepository
    val playlistTracksCache: PlaylistTracksCache get() = container.playlistTracksCache
    val albumTracksCache: AlbumTracksCache get() = container.albumTracksCache
    val searchHistoryRepository: SearchHistoryRepository get() = container.searchHistoryRepository
    val playlistCollectionRepository: PlaylistCollectionRepository
        get() = container.playlistCollectionRepository
    val albumCollectionRepository: AlbumCollectionRepository get() = container.albumCollectionRepository
    val libraryHomeRepository: LibraryHomeRepository get() = container.libraryHomeRepository
    val userSpaceBackgroundStore: UserSpaceBackgroundStore get() = container.userSpaceBackgroundStore
    val sessionWarmup: SessionWarmup get() = container.sessionWarmup
    val islandNoticeCenter: IslandNoticeCenter get() = container.islandNoticeCenter
    val trackExportRepository: TrackExportRepository get() = container.trackExportRepository
    val playlistEditor: PlaylistEditor get() = container.playlistEditor
    val mvPlayback: MvPlayback get() = container.mvPlayback
    val songRepository get() = container.songRepository
    val catalogRepository get() = container.catalogRepository
    val commentsRepository get() = container.commentsRepository
    val searchRepository get() = container.searchRepository
    val artistRepository get() = container.artistRepository
    val userRepository get() = container.userRepository
    val networkMode get() = container.networkMode
    val appUpdateStore get() = container.appUpdateStore
    val appUpdateCoordinator get() = container.appUpdateCoordinator
    val pluginEngine get() = container.pluginEngine
    val pluginDebugStore get() = container.pluginDebugStore
    val workshopAuthStore get() = container.workshopAuthStore
    val workshopRepository get() = container.workshopRepository

    private lateinit var queueSync: PlaybackQueueSync
    private lateinit var lyricOverlayController: LyricOverlayController
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.networkMode.start()
        container.deviceLinkMonitor.start()
        container.trackExportRepository.onLibraryChanged = {
            container.downloadAccelIndex.notifyLibraryChanged()
        }
        container.downloadAccelIndex.start()
        container.realtimeCache.start()
        // Application 阶段先按系统绑定，Activity 里再叠用户已存外观
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        MainPalette.bind(if (systemDark) MainColors.Dark else MainColors.Light)
        val resolvedDark = themeStore.current().resolveDark(systemDark)
        MainPalette.bind(if (resolvedDark) MainColors.Dark else MainColors.Light)
        playbackBridge.musicWillPlay = { mvPlayback.stop() }
        queueSync = PlaybackQueueSync(
            likedPlaylistRepository = likedPlaylistRepository,
            playlistTracksCache = playlistTracksCache,
            sessionRepository = sessionRepository,
            playbackBridge = playbackBridge,
        )
        queueSync.start()
        lyricOverlayController = LyricOverlayController(
            app = this,
            store = lyricOverlayStore,
            playback = playbackBridge,
            mvPlayback = mvPlayback,
        )
        lyricOverlayController.start()
        // 有通知权限 + 有上次队列：冷启动即挂歌曲通知（暂停态），无需先点播放
        playbackBridge.maybeWarmMediaNotificationOnColdStart()
        container.appUpdateCoordinator.start()
        val offline = container.networkMode.state.value.phase == NetworkPhase.Offline
        container.pluginEngine.setHostFacts(currentPluginHostFacts())
        container.pluginEngine.start(offline = offline)
        registerActivityLifecycleCallbacks(PluginForegroundCallbacks())
        appScope.launch {
            container.networkMode.state
                .map { it.phase == NetworkPhase.Offline }
                .distinctUntilChanged()
                .collect { isOffline ->
                    if (isOffline) {
                        container.pluginEngine.setHostFacts(currentPluginHostFacts().copy(online = false))
                        container.pluginEngine.setOffline(true)
                    } else {
                        container.pluginEngine.setHostFacts(currentPluginHostFacts().copy(online = true))
                        container.pluginEngine.setOffline(false)
                    }
                }
        }
        appScope.launch {
            combine(
                container.themeStore.appearance,
                container.sessionRepository.session,
            ) { _, _ -> currentPluginHostFacts() }
                .distinctUntilChanged()
                .collect { facts ->
                    container.pluginEngine.setHostFacts(facts)
                }
        }
        appScope.launch {
            combine(
                playbackBridge.ui,
                likedPlaylistRepository.snapshot,
            ) { ui, _ ->
                val liked = ui.currentTrack?.let { likedPlaylistRepository.isLiked(it.id) }
                PluginPlaybackSnapshot.from(ui, liked)
            }.collect { snap ->
                container.pluginEngine.setPlaybackSnapshot(snap)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        container.pluginEngine.setHostFacts(currentPluginHostFacts())
    }

    private fun currentPluginHostFacts(): PluginHostFacts {
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return PluginHostFacts(
            online = container.networkMode.state.value.phase != NetworkPhase.Offline,
            dark = themeStore.current().resolveDark(systemDark),
            loggedIn = sessionRepository.session.value != null,
            foreground = pluginEngine.hostFacts().foreground,
        )
    }

    fun isSourcePlaylistComplete(playlistId: Long): Boolean =
        queueSync.isSourcePlaylistComplete(playlistId)

    fun hydrateSourcePlaylist(playlistId: Long, minCount: Int) {
        queueSync.hydrateSourcePlaylist(playlistId, minCount)
    }

    private inner class PluginForegroundCallbacks : ActivityLifecycleCallbacks {
        private var started = 0

        override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit

        override fun onActivityStarted(activity: android.app.Activity) {
            if (started++ == 0) {
                pluginEngine.setHostFacts(currentPluginHostFacts().copy(foreground = true))
            }
        }

        override fun onActivityResumed(activity: android.app.Activity) = Unit

        override fun onActivityPaused(activity: android.app.Activity) = Unit

        override fun onActivityStopped(activity: android.app.Activity) {
            if (--started <= 0) {
                started = 0
                pluginEngine.setHostFacts(currentPluginHostFacts().copy(foreground = false))
            }
        }

        override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit

        override fun onActivityDestroyed(activity: android.app.Activity) = Unit
    }
}
