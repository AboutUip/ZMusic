package com.kite.zmusic

import android.app.Application
import com.kite.zmusic.data.AlbumCollectionRepository
import com.kite.zmusic.data.AlbumTracksCache
import com.kite.zmusic.data.AudioQualityStore
import com.kite.zmusic.data.AudioOutputStore
import com.kite.zmusic.data.LyricOverlayStore
import com.kite.zmusic.data.LyricRenderStore
import com.kite.zmusic.data.PersistentPlaybackStore
import com.kite.zmusic.data.LandscapeModeStore
import com.kite.zmusic.data.PredictiveBackStore
import com.kite.zmusic.data.ChromeGlassStore
import com.kite.zmusic.data.ThemeStore
import com.kite.zmusic.data.ChromeWallpaperStore
import com.kite.zmusic.data.DownloadAccelIndex
import com.kite.zmusic.data.DownloadAccelStore
import com.kite.zmusic.data.RealtimeCacheController
import com.kite.zmusic.data.RealtimeCacheStore
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.LibraryHomeRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.NetworkModeController
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlaylistCollectionRepository
import com.kite.zmusic.data.PlaylistEditor
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SearchHistoryRepository
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.SessionWarmup
import com.kite.zmusic.data.ArtistRepository
import com.kite.zmusic.data.CatalogRepository
import com.kite.zmusic.data.CommentsRepository
import com.kite.zmusic.data.CommunityLoginRepository
import com.kite.zmusic.data.CommunityServerStore
import com.kite.zmusic.data.ChangelogRepository
import com.kite.zmusic.data.CommunityXaiopClient
import com.kite.zmusic.data.AppUpdateCatalog
import com.kite.zmusic.data.AppUpdateCoordinator
import com.kite.zmusic.data.AppUpdateStore
import com.kite.zmusic.data.ApkDownloader
import com.kite.zmusic.data.DiskAppUpdateFiles
import com.kite.zmusic.data.PartnerRepository
import com.kite.zmusic.data.SponsorRepository
import com.kite.zmusic.data.SearchRepository
import com.kite.zmusic.data.SongRepository
import com.kite.zmusic.data.TrackExportRepository
import com.kite.zmusic.data.UserRepository
import com.kite.zmusic.data.UserSpaceBackgroundStore
import com.kite.zmusic.data.xaiop.OkHttpXaiop
import com.kite.zmusic.playback.DeviceLinkMonitor
import com.kite.zmusic.playback.MvPlayback
import com.kite.zmusic.playback.PlaybackBridge
import com.kite.zmusic.playback.AudioOutputController
import com.kite.zmusic.plugin.PluginDebugDrop
import com.kite.zmusic.plugin.PluginDebugProbe
import com.kite.zmusic.plugin.PluginDebugStore
import com.kite.zmusic.plugin.PluginEngine
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 进程内依赖装配。Compose / ViewModel 从 [ZMusicApplication.container] 取，
 * 不要再 `NcmUserClient()`。
 */
class AppContainer(app: Application) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val ncmUserClient = NcmUserClient(httpClient)
    val ncmAuthClient = NcmAuthClient(httpClient)
    val xaiop = OkHttpXaiop(httpClient)

    val sessionRepository = SessionRepository(app)
    val communityServerStore = CommunityServerStore(app)
    val workshopAuthStore = com.kite.zmusic.workshop.WorkshopAuthStore(app)
    val communityLoginRepository = CommunityLoginRepository(
        sessionRepository,
        ncmAuthClient,
        ncmUserClient,
        communityServerStore,
        workshopAuthStore,
    )
    val audioQualityStore = AudioQualityStore(app)
    val audioOutputStore = AudioOutputStore(app)
    val audioOutputController = AudioOutputController(app, audioOutputStore)
    val persistentPlaybackStore = PersistentPlaybackStore(app)
    val predictiveBackStore = PredictiveBackStore(app)
    val landscapeModeStore = LandscapeModeStore(app)
    val lyricRenderStore = LyricRenderStore(app)
    val lyricOverlayStore = LyricOverlayStore(app)
    val chromeGlassStore = ChromeGlassStore(app)
    val themeStore = ThemeStore(app)
    val chromeWallpaperStore = ChromeWallpaperStore(app)
    val downloadAccelStore = DownloadAccelStore(app)
    val realtimeCacheStore = RealtimeCacheStore(app)
    val playbackBridge = PlaybackBridge(app, sessionRepository, ncmUserClient)
    val likedPlaylistRepository = LikedPlaylistRepository(
        app,
        sessionRepository,
        ncmAuthClient,
        ncmUserClient,
    )
    val homeFeedRepository = HomeFeedRepository(sessionRepository, ncmUserClient)
    val playlistTracksCache = PlaylistTracksCache(app, ncmUserClient)
    val albumTracksCache = AlbumTracksCache(app)
    val searchHistoryRepository = SearchHistoryRepository(app)
    val playlistCollectionRepository = PlaylistCollectionRepository()
    val albumCollectionRepository = AlbumCollectionRepository()
    val libraryHomeRepository = LibraryHomeRepository(
        sessionRepository,
        likedPlaylistRepository,
        playlistCollectionRepository,
        albumCollectionRepository,
        ncmAuthClient,
        ncmUserClient,
    )
    val userSpaceBackgroundStore = UserSpaceBackgroundStore(app)
    val sessionWarmup = SessionWarmup(
        sessionRepository,
        ncmAuthClient,
        onUserId = { uid -> playlistCollectionRepository.setSelfUserId(uid) },
    )
    val islandNoticeCenter = IslandNoticeCenter()
    private val communityXaiop = CommunityXaiopClient(
        xaiop,
        communityServerStore,
        islandNoticeCenter,
    )
    val changelogRepository = ChangelogRepository(communityXaiop)
    val sponsorRepository = SponsorRepository(communityXaiop)
    val partnerRepository = PartnerRepository(communityXaiop)
    val appUpdateStore = AppUpdateStore(app)
    val appUpdateCoordinator = AppUpdateCoordinator(
        context = app,
        catalog = AppUpdateCatalog(communityXaiop),
        prefs = appUpdateStore,
        downloader = ApkDownloader(
            httpClient.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build(),
        ),
        files = DiskAppUpdateFiles(java.io.File(app.filesDir, "updates")),
        notices = islandNoticeCenter,
        playbackBridge = playbackBridge,
        localVersion = BuildConfig.VERSION_NAME,
    )
    val deviceLinkMonitor = DeviceLinkMonitor(app, audioOutputController, islandNoticeCenter)
    val trackExportRepository = TrackExportRepository(app, audioQualityStore, ncmUserClient)
    val downloadAccelIndex = DownloadAccelIndex(app, trackExportRepository, downloadAccelStore)
    val realtimeCache = RealtimeCacheController(
        app,
        realtimeCacheStore,
        sessionRepository,
        ncmUserClient,
    )
    val playlistEditor = PlaylistEditor(
        sessionRepository,
        ncmUserClient,
        playlistCollectionRepository,
        playlistTracksCache,
        likedPlaylistRepository,
        libraryHomeRepository,
    )
    val mvPlayback = MvPlayback(app, sessionRepository, playbackBridge, ncmUserClient)
    val songRepository = SongRepository(ncmUserClient)
    val catalogRepository = CatalogRepository(ncmUserClient)
    val commentsRepository = CommentsRepository(ncmUserClient, ncmAuthClient)
    val searchRepository = SearchRepository(ncmUserClient)
    val artistRepository = ArtistRepository(ncmUserClient, ncmAuthClient)
    val userRepository = UserRepository(ncmUserClient, ncmAuthClient)
    val networkMode = NetworkModeController(
        app,
        homeFeedRepository,
        libraryHomeRepository,
        likedPlaylistRepository,
    )
    val pluginDebugStore = PluginDebugStore(app)
    val pluginEngine = PluginEngine(
        filesDir = app.filesDir,
        debugStore = pluginDebugStore,
        appVersionName = BuildConfig.VERSION_NAME,
        debugDropDir = app.getExternalFilesDir(null)?.let(PluginDebugDrop::dir),
        showNotice = { message, coverUrl -> islandNoticeCenter.show(message, coverUrl) },
        bundledDebugProbe = {
            PluginDebugProbe.copyFromAssets(
                app.assets,
                java.io.File(app.cacheDir, "plugin-engine/probe.zpp"),
            )
        },
    )
    private val workshopHttp = httpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    val workshopClient = com.kite.zmusic.workshop.WorkshopClient(
        http = workshopHttp,
        xaiop = xaiop,
        community = communityServerStore,
        auth = workshopAuthStore,
    )
    val workshopDownloader = com.kite.zmusic.workshop.WorkshopDownloader(
        http = workshopHttp,
        client = workshopClient,
        auth = workshopAuthStore,
    )
    val workshopRepository = com.kite.zmusic.workshop.WorkshopRepository(
        client = workshopClient,
        downloader = workshopDownloader,
        pluginEngine = pluginEngine,
        auth = workshopAuthStore,
        notices = islandNoticeCenter,
        cacheDir = java.io.File(app.cacheDir, "workshop"),
    )
}
