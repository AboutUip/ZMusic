package com.kite.zmusic.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.kite.zmusic.AppContainer
import com.kite.zmusic.config.NcmApiConfig
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.catalog.AlbumOverlay
import com.kite.zmusic.ui.catalog.ArtistOverlay
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
import com.kite.zmusic.ui.theme.MainColors
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    LaunchedEffect(prefs.appearance) {
        MainPalette.bind(
            if (prefs.appearance == com.kite.zmusic.data.AppAppearance.Dark) MainColors.Dark else MainColors.Light,
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
                            uid = uid,
                            userClient = app.user,
                            onOpenOverlay = ::push,
                            onPlayTracks = { t, i, pid, title -> play(t, i, pid, title) },
                        )
                        MainDestination.Features -> FeaturesScreen(
                            onOpenOverlay = ::push,
                            onStartFm = {
                                CoroutineScope(Dispatchers.Default).launch {
                                    val json = runCatching { app.user.personalFm(cookie) }.getOrNull()
                                        ?: return@launch
                                    val data = json.optJSONArray("data") ?: return@launch
                                    val tracks = buildList {
                                        for (i in 0 until data.length()) {
                                            val o = data.optJSONObject(i) ?: continue
                                            NcmLibraryParse.trackFromSongObject(o)?.let { add(it) }
                                        }
                                    }
                                    if (tracks.isNotEmpty()) {
                                        app.bridge.playQueue(tracks, 0, fm = true)
                                    }
                                }
                            },
                            onStartIntelligence = {
                                CoroutineScope(Dispatchers.Default).launch {
                                    if (uid <= 0L) return@launch
                                    val lists = runCatching {
                                        NcmLibraryParse.playlistsFromUserPlaylist(
                                            app.user.userPlaylist(uid, cookie),
                                            uid,
                                        )
                                    }.getOrDefault(emptyList())
                                    val heart = lists.firstOrNull { it.isHeartPlaylist } ?: return@launch
                                    val seed = NcmLibraryParse.tracksFromPlaylistDetail(
                                        app.user.playlistDetail(heart.id, cookie),
                                    ).firstOrNull() ?: return@launch
                                    val json = runCatching {
                                        app.user.intelligenceList(seed.id, cookie)
                                    }.getOrNull() ?: return@launch
                                    val arr = json.optJSONArray("data") ?: return@launch
                                    val tracks = buildList {
                                        for (i in 0 until arr.length()) {
                                            val o = arr.optJSONObject(i) ?: continue
                                            val song = o.optJSONObject("songInfo") ?: o
                                            NcmLibraryParse.trackFromSongObject(song)?.let { add(it) }
                                        }
                                    }
                                    if (tracks.isNotEmpty()) {
                                        app.bridge.playQueue(tracks, 0, intelligence = true)
                                    }
                                }
                            },
                        )
                        MainDestination.Profile -> ProfileScreen(
                            session = session,
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
                                    onBack = ::pop,
                                    onPlay = { t, i -> play(t, i, o.id, o.title) },
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
                                )
                                is MainOverlay.Album -> AlbumOverlay(
                                    overlay = o,
                                    cookie = cookie,
                                    userClient = app.user,
                                    onBack = ::pop,
                                    onPlay = { t, i -> play(t, i) },
                                )
                                is MainOverlay.Artist -> ArtistOverlay(
                                    overlay = o,
                                    cookie = cookie,
                                    userClient = app.user,
                                    onBack = ::pop,
                                    onPlay = { t, i -> play(t, i) },
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
                                is MainOverlay.CachedSongs -> OverlayScaffold("缓存的歌曲", ::pop) {
                                    CachedSongsBody()
                                }
                                else -> OverlayScaffold(o?.javaClass?.simpleName ?: "返回", ::pop) {}
                            }
                        }
                    }
                }
                if (playback.hasQueue && !playerOpen) {
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
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160)),
        ) {
            LandscapePlayerBody(
                state = playback,
                wordByWord = prefs.lyricWordByWord,
                onBack = { playerOpen = false },
                onToggle = { app.bridge.togglePlay() },
                onSeek = { app.bridge.seek(it) },
                onMode = { app.bridge.cycleMode() },
                onNext = { app.bridge.skipNext() },
                onPrev = { app.bridge.skipPrev() },
            )
        }
        IslandNoticeHost(app.notices, prefs.glass, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun CachedSongsBody() {
    val files = remember {
        com.kite.zmusic.data.LocalLibrary.cacheDir().toFile().listFiles()?.filter { it.isFile }
            .orEmpty()
    }
    if (files.isEmpty()) {
        androidx.compose.material3.Text("还没有缓存曲目。", color = MainPalette.Secondary)
    } else {
        Column {
            files.forEach { f ->
                androidx.compose.material3.Text(
                    f.name,
                    color = MainPalette.Ink,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

private fun loadWallpaper(path: String): ImageBitmap? {
    if (path.isBlank()) return null
    return runCatching {
        val bytes = java.io.File(path).readBytes()
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}
