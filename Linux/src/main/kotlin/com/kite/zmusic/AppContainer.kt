package com.kite.zmusic

import com.kite.zmusic.config.NcmApiConfig
import com.kite.zmusic.data.CommunityCatalogClient
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.LibraryFeedRepository
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PrefsStore
import com.kite.zmusic.data.SessionStore
import com.kite.zmusic.playback.DbusMprisExporter
import com.kite.zmusic.playback.FakePlaybackEngine
import com.kite.zmusic.playback.MpvPlaybackEngine
import com.kite.zmusic.playback.NoopMprisExporter
import com.kite.zmusic.playback.PlaybackBridge
import com.kite.zmusic.playback.PlaybackEngine
import com.kite.zmusic.playback.PlaylistCoordinator
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import okhttp3.OkHttpClient

class AppContainer(
    engineOverride: PlaybackEngine? = null,
) {
    val http: OkHttpClient = NcmAuthClient.defaultClient()
    val auth = NcmAuthClient(http)
    val user = NcmUserClient(http)
    val sessions = SessionStore()
    val prefs = PrefsStore()
    val notices = IslandNoticeCenter()
    val catalog = CommunityCatalogClient(http) { prefs.current().communityServer }
    val homeFeed = HomeFeedRepository(sessions, user)
    val libraryFeed = LibraryFeedRepository(sessions, user, auth)
    val engine: PlaybackEngine = engineOverride
        ?: MpvPlaybackEngine.create()
        ?: FakePlaybackEngine().also {
            System.err.println("zmusic: libmpv failed to load; playback will be silent")
        }
    val mpris = DbusMprisExporter.create() ?: NoopMprisExporter()
    val coordinator = PlaylistCoordinator(
        userClient = user,
        engine = engine,
        mpris = mpris,
        cookie = { sessions.session.value?.cookie },
        quality = { prefs.current().audioQuality },
        persistentPlayback = { prefs.current().persistentPlayback },
        downloadAccel = { prefs.current().downloadAccel },
        cachePrefs = { prefs.current().realtimeCache },
    )
    val bridge = PlaybackBridge(coordinator)

    init {
        val server = prefs.current().musicServer
        if (server.isNotBlank()) NcmApiConfig.setRuntimeBaseUrl(server)
        coordinator.mvWillPlay = { coordinator.stopForMv() }
    }
}
