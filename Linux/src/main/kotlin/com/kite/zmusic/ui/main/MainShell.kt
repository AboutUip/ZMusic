package com.kite.zmusic.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kite.zmusic.AppContainer
import com.kite.zmusic.config.NcmApiConfig
import com.kite.zmusic.data.AppAppearance
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.catalog.AlbumOverlay
import com.kite.zmusic.ui.catalog.ArtistOverlay
import com.kite.zmusic.ui.catalog.CachedSongsOverlay
import com.kite.zmusic.ui.catalog.ChartsOverlay
import com.kite.zmusic.ui.catalog.DailyOverlay
import com.kite.zmusic.ui.catalog.LikedArtistsOverlay
import com.kite.zmusic.ui.catalog.OverlayScaffold
import com.kite.zmusic.ui.catalog.PlaylistOverlay
import com.kite.zmusic.ui.catalog.SearchOverlay
import com.kite.zmusic.ui.catalog.UserOverlay
import com.kite.zmusic.ui.features.FeaturesScreen
import com.kite.zmusic.ui.home.HomeScreen
import com.kite.zmusic.ui.library.ProfileScreen
import com.kite.zmusic.ui.notice.IslandNoticeHost
import com.kite.zmusic.ui.player.LandscapePlayerBody
import com.kite.zmusic.ui.settings.SettingsScreen
import com.kite.zmusic.ui.theme.LinuxSystemTheme
import com.kite.zmusic.ui.theme.MainColors
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainShell(app: AppContainer, onLogout: () -> Unit) {
    val prefs by app.prefs.prefs.collectAsState()
    val session by app.sessions.session.collectAsState()
    val playback by app.bridge.ui.collectAsState()
    var dest by remember { mutableStateOf(MainDestination.Home) }
    val overlayStack = remember { OverlayStack() }
    var overlayTick by remember { mutableStateOf(0) }
    var playerOpen by remember { mutableStateOf(false) }
    var uid by remember { mutableStateOf(0L) }
    val cookie = session?.cookie.orEmpty()
    val scope = rememberCoroutineScope()

    var systemDark by remember { mutableStateOf(LinuxSystemTheme.isDark()) }
    LaunchedEffect(prefs.appearance) {
        if (System.getProperty("zmusic.test") == "true") return@LaunchedEffect
        if (prefs.appearance != AppAppearance.System) return@LaunchedEffect
        while (true) {
            systemDark = LinuxSystemTheme.isDark()
            delay(2_500)
        }
    }
    LaunchedEffect(prefs.appearance, systemDark) {
        MainPalette.bind(
            if (prefs.appearance.resolveDark(systemDark)) MainColors.Dark else MainColors.Light,
        )
    }
    LaunchedEffect(cookie) {
        uid = if (cookie.isBlank()) {
            0L
        } else {
            runCatching { NcmJson.userIdFromLoginStatus(app.auth.loginStatus(cookie)) }.getOrNull() ?: 0L
        }
    }
    LaunchedEffect(playback.notice?.token) {
        playback.notice?.let { app.notices.show(it.message) }
    }

    fun push(o: MainOverlay) {
        overlayStack.push(o)
        overlayTick++
    }

    fun pop() {
        overlayStack.pop()
        overlayTick++
    }

    fun play(tracks: List<TrackRow>, index: Int, pid: Long? = null, title: String? = null) {
        app.bridge.playQueue(tracks, index, pid, title)
        playerOpen = true
    }

    val overlay = remember(overlayTick) { overlayStack.top() }
    val wallpaper = remember(prefs.wallpaperPath) { loadWallpaper(prefs.wallpaperPath) }
    LaunchedEffect(prefs.musicServer) {
        if (prefs.musicServer.isNotBlank()) NcmApiConfig.setRuntimeBaseUrl(prefs.musicServer)
    }
    Box(Modifier.fillMaxSize().background(MainPalette.Page)) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Row(Modifier.fillMaxSize()) {
            LandscapeNavRail(
                selected = dest,
                settingsSelected = overlay is MainOverlay.Settings,
                onDestination = {
                    dest = it
                    if (overlay is MainOverlay.Settings) pop()
                },
                onOpenSettings = {
                    if (overlay is MainOverlay.Settings) pop() else push(MainOverlay.Settings)
                },
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (dest) {
                        MainDestination.Home -> HomeScreen(
                            cookie = cookie,
                            userClient = app.user,
                            onOpenOverlay = ::push,
                            onPlayTracks = { t, i, pid, title -> play(t, i, pid, title) },
                        )
                        MainDestination.Features -> FeaturesScreen(
                            onOpenOverlay = ::push,
                            onStartFm = {
                                scope.launch {
                                    val json = runCatching { app.user.personalFm(cookie) }.getOrNull()
                                        ?: return@launch
                                    val tracks = NcmHomeParse.personalFmTracks(json)
                                    if (tracks.isNotEmpty()) {
                                        app.bridge.playQueue(tracks, 0, fm = true, playlistTitle = "私人漫游")
                                        playerOpen = true
                                    } else {
                                        app.notices.show("暂时无法开始私人漫游")
                                    }
                                }
                            },
                            onStartIntelligence = {
                                scope.launch {
                                    if (uid <= 0L) {
                                        app.notices.show("未登录")
                                        return@launch
                                    }
                                    val lists = runCatching {
                                        NcmLibraryParse.playlistsFromUserPlaylist(
                                            app.user.userPlaylist(uid, cookie),
                                            uid,
                                        )
                                    }.getOrDefault(emptyList())
                                    val heart = lists.firstOrNull { it.isHeartPlaylist }
                                    if (heart == null) {
                                        app.notices.show("先在我喜欢的音乐里收藏几首歌")
                                        return@launch
                                    }
                                    val seed = NcmLibraryParse.tracksFromPlaylistDetail(
                                        app.user.playlistDetail(heart.id, cookie),
                                    ).firstOrNull()
                                    if (seed == null) {
                                        app.notices.show("先在我喜欢的音乐里收藏几首歌")
                                        return@launch
                                    }
                                    val json = runCatching {
                                        app.user.intelligenceList(seed.id, cookie, playlistId = heart.id)
                                    }.getOrNull()
                                    val tracks = json?.let { NcmHomeParse.intelligenceTracks(it) }.orEmpty()
                                    if (tracks.isNotEmpty()) {
                                        app.bridge.playQueue(
                                            tracks,
                                            0,
                                            playlistId = heart.id,
                                            playlistTitle = "心动模式",
                                            intelligence = true,
                                        )
                                        playerOpen = true
                                    } else {
                                        app.notices.show("心动模式暂时不可用")
                                    }
                                }
                            },
                        )
                        MainDestination.Profile -> ProfileScreen(
                            cookie = cookie,
                            uid = uid,
                            userClient = app.user,
                            onOpenOverlay = ::push,
                            onOpenLikedArtists = { push(MainOverlay.LikedArtists) },
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = overlay != null,
                        enter = LandscapeCoverEnter,
                        exit = LandscapeCoverExit,
                    ) {
                        Box(Modifier.fillMaxSize().background(MainPalette.Page)) {
                            when (val o = overlay) {
                                is MainOverlay.Settings -> SettingsScreen(
                                    prefs = prefs,
                                    catalog = app.catalog,
                                    notices = app.notices,
                                    onUpdate = { app.prefs.update(it) },
                                    onLogout = {
                                        app.bridge.stopAndClear()
                                        app.sessions.clear()
                                        onLogout()
                                    },
                                )
                                is MainOverlay.Playlist -> PlaylistOverlay(
                                    overlay = o,
                                    cookie = cookie,
                                    userClient = app.user,
                                    uid = uid,
                                    onBack = ::pop,
                                    onPlay = { t, i -> play(t, i, o.id, o.title) },
                                    onInsertNext = { app.bridge.insertNext(it) },
                                    onNotice = { app.notices.show(it) },
                                )
                                is MainOverlay.Charts -> ChartsOverlay(
                                    cookie = cookie,
                                    userClient = app.user,
                                    onBack = ::pop,
                                    onOpenPlaylist = { id, name ->
                                        overlayStack.replaceTop(MainOverlay.Playlist(id, name))
                                        overlayTick++
                                    },
                                )
                                is MainOverlay.Daily -> DailyOverlay(
                                    cookie = cookie,
                                    userClient = app.user,
                                    onBack = ::pop,
                                    onPlay = { t, i -> play(t, i, title = "每日推荐") },
                                    onInsertNext = { app.bridge.insertNext(it) },
                                )
                                is MainOverlay.Search -> SearchOverlay(
                                    cookie = cookie,
                                    userClient = app.user,
                                    onBack = ::pop,
                                    onPlay = { t, i -> play(t, i) },
                                    onOpenArtist = { id, name ->
                                        overlayStack.push(MainOverlay.Artist(id, name))
                                        overlayTick++
                                    },
                                    onOpenAlbum = { id, name ->
                                        overlayStack.push(MainOverlay.Album(id, name))
                                        overlayTick++
                                    },
                                    onOpenPlaylist = { id, name ->
                                        overlayStack.push(MainOverlay.Playlist(id, name))
                                        overlayTick++
                                    },
                                    onOpenUser = { id, name ->
                                        overlayStack.push(MainOverlay.User(id, name))
                                        overlayTick++
                                    },
                                    onOpenMv = { id, name ->
                                        overlayStack.push(MainOverlay.Mv(id, name))
                                        overlayTick++
                                    },
                                )
                                is MainOverlay.Album -> AlbumOverlay(
                                    overlay = o,
                                    cookie = cookie,
                                    userClient = app.user,
                                    onBack = ::pop,
                                    onPlay = { t, i -> play(t, i) },
                                    onInsertNext = { app.bridge.insertNext(it) },
                                    onNotice = { app.notices.show(it) },
                                )
                                is MainOverlay.Artist -> ArtistOverlay(
                                    overlay = o,
                                    cookie = cookie,
                                    userClient = app.user,
                                    onBack = ::pop,
                                    onPlay = { t, i -> play(t, i) },
                                    onInsertNext = { app.bridge.insertNext(it) },
                                    onOpenAlbum = { id, name ->
                                        overlayStack.push(MainOverlay.Album(id, name))
                                        overlayTick++
                                    },
                                    onOpenMv = { id, name ->
                                        overlayStack.push(MainOverlay.Mv(id, name))
                                        overlayTick++
                                    },
                                    onNotice = { app.notices.show(it) },
                                )
                                is MainOverlay.User -> UserOverlay(
                                    overlay = o,
                                    cookie = cookie,
                                    userClient = app.user,
                                    onBack = ::pop,
                                    onOpenPlaylist = { id, name ->
                                        overlayStack.push(MainOverlay.Playlist(id, name))
                                        overlayTick++
                                    },
                                    onNotice = { app.notices.show(it) },
                                )
                                is MainOverlay.LikedArtists -> LikedArtistsOverlay(
                                    cookie = cookie,
                                    userClient = app.user,
                                    onBack = ::pop,
                                    onOpenArtist = { id, name ->
                                        overlayStack.push(MainOverlay.Artist(id, name))
                                        overlayTick++
                                    },
                                )
                                is MainOverlay.Mv -> {
                                    LaunchedEffect(o.id) { app.coordinator.mvWillPlay?.invoke() }
                                    OverlayScaffold(o.title, ::pop) {
                                        androidx.compose.material3.Text(
                                            "MV 将在 0.1.x 用 libmpv 视频窗播放；歌曲已暂停。",
                                            color = MainPalette.Secondary,
                                        )
                                    }
                                }
                                is MainOverlay.CachedSongs -> CachedSongsOverlay(
                                    cookie = cookie,
                                    userClient = app.user,
                                    onBack = ::pop,
                                    onPlay = { t, i -> play(t, i, title = "缓存的歌曲") },
                                )
                                else -> OverlayScaffold(o?.javaClass?.simpleName ?: "返回", ::pop) {}
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = playback.hasQueue && !playerOpen,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 3 },
                    exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 4 },
                ) {
                    MiniPlayerBar(
                        state = playback,
                        glass = prefs.glass,
                        onToggle = { app.bridge.togglePlay() },
                        onExpand = { playerOpen = true },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = playerOpen && playback.hasQueue,
            enter = LandscapeCoverEnter,
            exit = LandscapeCoverExit,
        ) {
            LandscapePlayerBody(
                state = playback,
                wordByWord = prefs.lyricWordByWord,
                activeHalo = prefs.playerHalo,
                onHaloChange = { on -> app.prefs.update { it.copy(playerHalo = on) } },
                onBack = { playerOpen = false },
                onToggle = { app.bridge.togglePlay() },
                onSeek = { app.bridge.seek(it) },
                onMode = { app.bridge.cycleMode() },
                onNext = { app.bridge.skipNext() },
                onPrev = { app.bridge.skipPrev() },
                onToggleLike = { app.bridge.toggleLike() },
                onPlayAt = { app.bridge.playAt(it) },
            )
        }
        IslandNoticeHost(app.notices, prefs.glass, Modifier.align(Alignment.TopCenter))
    }
}

private fun loadWallpaper(path: String): ImageBitmap? {
    if (path.isBlank()) return null
    return runCatching {
        val bytes = java.io.File(path).readBytes()
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}
