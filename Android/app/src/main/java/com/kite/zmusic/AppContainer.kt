package com.kite.zmusic

import android.app.Application
import com.kite.zmusic.data.AlbumCollectionRepository
import com.kite.zmusic.data.AlbumTracksCache
import com.kite.zmusic.data.AudioQualityStore
import com.kite.zmusic.data.LyricOverlayStore
import com.kite.zmusic.data.LyricRenderStore
import com.kite.zmusic.data.PersistentPlaybackStore
import com.kite.zmusic.data.ChromeGlassStore
import com.kite.zmusic.data.ThemeStore
import com.kite.zmusic.data.ChromeWallpaperStore
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.LibraryHomeRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.NcmAuthClient
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
import com.kite.zmusic.data.SearchRepository
import com.kite.zmusic.data.SongRepository
import com.kite.zmusic.data.TrackExportRepository
import com.kite.zmusic.data.UserSpaceBackgroundStore
import com.kite.zmusic.playback.MvPlayback
import com.kite.zmusic.playback.PlaybackBridge
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

    val sessionRepository = SessionRepository(app)
    val audioQualityStore = AudioQualityStore(app)
    val persistentPlaybackStore = PersistentPlaybackStore(app)
    val lyricRenderStore = LyricRenderStore(app)
    val lyricOverlayStore = LyricOverlayStore(app)
    val chromeGlassStore = ChromeGlassStore(app)
    val themeStore = ThemeStore(app)
    val chromeWallpaperStore = ChromeWallpaperStore(app)
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
    val trackExportRepository = TrackExportRepository(app, audioQualityStore, ncmUserClient)
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
}
