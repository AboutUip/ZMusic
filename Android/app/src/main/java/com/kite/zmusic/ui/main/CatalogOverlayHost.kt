package com.kite.zmusic.ui.main

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.artist.ArtistAlbumsScreen
import com.kite.zmusic.ui.artist.ArtistMvsScreen
import com.kite.zmusic.ui.artist.ArtistScreen
import com.kite.zmusic.ui.catalog.CatalogCollectionPage
import com.kite.zmusic.ui.catalog.PlaylistManageBridge
import com.kite.zmusic.ui.catalog.PlaylistSearchScreen
import com.kite.zmusic.ui.chrome.ChromeWallpaperBackdrop
import com.kite.zmusic.ui.common.predictiveBackLayer
import com.kite.zmusic.ui.common.rememberPredictiveBackUi
import com.kite.zmusic.ui.library.LikedArtistsScreen
import com.kite.zmusic.ui.library.LikedArtistsSearchScreen
import com.kite.zmusic.ui.mv.MvPlayerScreen
import com.kite.zmusic.ui.search.SearchScreen
import com.kite.zmusic.ui.search.SearchViewModel
import com.kite.zmusic.ui.search.SearchViewModelFactory
import com.kite.zmusic.ui.settings.SettingsScreen
import kotlinx.coroutines.delay

private val OverlaySlideSpec = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)
private val OverlayFadeSpec = tween<Float>(durationMillis = 220)

@Composable
fun CatalogOverlayHost(
    overlayStack: List<MainOverlay>,
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenPlaylist: (Long, String, String?) -> Unit,
    onPushOverlay: (MainOverlay) -> Unit = {},
    onHint: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    searchInStack: Boolean = overlayStack.any { it is MainOverlay.Search },
    playingTrackId: Long = 0L,
    playingSourceId: Long = 0L,
    isPlaying: Boolean = false,
    manageBridge: PlaylistManageBridge? = null,
    includeMv: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val searchVm: SearchViewModel = viewModel(
        factory = SearchViewModelFactory(sessionRepository, app.searchHistoryRepository, app.searchRepository),
    )
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(searchInStack) {
        if (!searchInStack) {
            keyboard?.hide()
            focus.clearFocus(force = true)
            delay(340)
            searchVm.resetToIdle()
        }
    }
    var held by remember { mutableStateOf<List<MainOverlay>>(emptyList()) }
    val stackKeys = overlayStack.map { it.stackKey() }
    val pushing = overlayStack.size >= held.size
    var snapPop by remember { mutableStateOf(false) }
    val backUi = rememberPredictiveBackUi(
        enabled = overlayStack.isNotEmpty(),
        onPeekCommit = { peeked -> snapPop = peeked },
        onBack = onBack,
    )
    val render = if (pushing || snapPop) overlayStack else held
    LaunchedEffect(stackKeys) {
        if (overlayStack.size >= held.size || snapPop) {
            held = overlayStack
            snapPop = false
        } else {
            delay(340)
            held = overlayStack
        }
    }
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val enter = slideInHorizontally(OverlaySlideSpec) { it } + fadeIn(OverlayFadeSpec)
    val exit = slideOutHorizontally(OverlaySlideSpec) { it } + fadeOut(OverlayFadeSpec)
    if (render.isEmpty()) return
    Box(modifier.fillMaxSize()) {
        render.forEachIndexed { index, item ->
            if (!includeMv && item is MainOverlay.Mv) return@forEachIndexed
            key(item.stackKey()) {
                val visibleState = remember { MutableTransitionState(false) }
                visibleState.targetState = overlayStack.any { it.stackKey() == item.stackKey() }
                val isTop = overlayStack.lastOrNull()?.stackKey() == item.stackKey()
                val cover = landscape && item is MainOverlay.Settings
                AnimatedVisibility(
                    visibleState = visibleState,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(index.toFloat())
                        .then(
                            if (isTop) Modifier.predictiveBackLayer(backUi)
                            else Modifier,
                        ),
                    enter = if (cover) LandscapeCoverEnter else enter,
                    exit = if (cover) LandscapeCoverExit else exit,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        ChromeWallpaperBackdrop()
                        CatalogOverlayPage(
                            overlay = item,
                            isTop = isTop,
                            sessionRepository = sessionRepository,
                            contentBottomInset = contentBottomInset,
                            onBack = onBack,
                            onPlayTracks = onPlayTracks,
                            onOpenPlaylist = onOpenPlaylist,
                            onPushOverlay = onPushOverlay,
                            onHint = onHint,
                            onLogout = onLogout,
                            playingTrackId = playingTrackId,
                            playingSourceId = playingSourceId,
                            isPlaying = isPlaying,
                            manageBridge = manageBridge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogOverlayPage(
    overlay: MainOverlay,
    isTop: Boolean,
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenPlaylist: (Long, String, String?) -> Unit,
    onPushOverlay: (MainOverlay) -> Unit,
    onHint: (String) -> Unit,
    onLogout: () -> Unit,
    playingTrackId: Long,
    playingSourceId: Long,
    isPlaying: Boolean,
    manageBridge: PlaylistManageBridge? = null,
) {
    when (overlay) {
        MainOverlay.Search -> {
            SearchScreen(
                sessionRepository = sessionRepository,
                contentBottomInset = contentBottomInset,
                isTop = isTop,
                onBack = onBack,
                onPlayTracks = onPlayTracks,
                onOpenPlaylist = onOpenPlaylist,
                onOpenAlbum = { id, title ->
                    onPushOverlay(MainOverlay.Album(id, title))
                },
                onOpenMv = { id, title, cover, artist ->
                    onPushOverlay(MainOverlay.Mv(id, title, cover, artist))
                },
                onOpenArtist = { hit ->
                    if (hit.id > 0L) {
                        onPushOverlay(MainOverlay.Artist(hit.id, hit.name, hit.coverUrl))
                    } else {
                        onHint("暂时无法打开这位歌手")
                    }
                },
                onHint = onHint,
            )
        }
        MainOverlay.Settings -> {
            SettingsScreen(
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onLogout = onLogout,
            )
        }
        is MainOverlay.PlaylistSearch -> {
            PlaylistSearchScreen(
                overlay = overlay,
                sessionRepository = sessionRepository,
                contentBottomInset = contentBottomInset,
                isTop = isTop,
                onBack = onBack,
                onPlayTracks = onPlayTracks,
                playingTrackId = playingTrackId,
                playingSourceId = playingSourceId,
                isPlaying = isPlaying,
                onOpenArtist = { id, name, cover ->
                    onPushOverlay(MainOverlay.Artist(id, name, cover))
                },
            )
        }
        is MainOverlay.Mv -> {
            val app = LocalContext.current.applicationContext as ZMusicApplication
            val mvUi by app.mvPlayback.ui.collectAsStateWithLifecycle()
            MvPlayerScreen(
                overlay = overlay,
                playback = app.mvPlayback,
                ui = mvUi,
                onBack = onBack,
                onOpenMv = { onPushOverlay(it) },
                onOpenArtist = { artist ->
                    if (artist.id > 0L) {
                        onPushOverlay(MainOverlay.Artist(artist.id, artist.name, artist.avatarUrl))
                    } else {
                        onHint("暂时无法打开这位歌手")
                    }
                },
            )
        }
        is MainOverlay.Artist -> {
            ArtistScreen(
                overlay = overlay,
                sessionRepository = sessionRepository,
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onPlayTracks = onPlayTracks,
                onOpenAlbum = { id, title ->
                    onPushOverlay(MainOverlay.Album(id, title))
                },
                onOpenMv = { id, title, cover, artist ->
                    onPushOverlay(MainOverlay.Mv(id, title, cover, artist))
                },
                onOpenArtist = { id, name, cover ->
                    onPushOverlay(MainOverlay.Artist(id, name, cover))
                },
                onOpenSongs = { name, cover ->
                    onPushOverlay(MainOverlay.ArtistSongs(overlay.id, name, cover))
                },
                onOpenAlbums = { name, cover ->
                    onPushOverlay(MainOverlay.ArtistAlbums(overlay.id, name, cover))
                },
                onOpenMvs = { name, cover ->
                    onPushOverlay(MainOverlay.ArtistMvs(overlay.id, name, cover))
                },
                playingTrackId = playingTrackId,
                playingSourceId = playingSourceId,
                isPlaying = isPlaying,
            )
        }
        is MainOverlay.ArtistAlbums -> {
            ArtistAlbumsScreen(
                overlay = overlay,
                sessionRepository = sessionRepository,
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onOpenAlbum = { id, title ->
                    onPushOverlay(MainOverlay.Album(id, title))
                },
            )
        }
        is MainOverlay.ArtistMvs -> {
            ArtistMvsScreen(
                overlay = overlay,
                sessionRepository = sessionRepository,
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onOpenMv = { id, title, cover, artist ->
                    onPushOverlay(MainOverlay.Mv(id, title, cover, artist))
                },
            )
        }
        MainOverlay.LikedArtists -> {
            LikedArtistsScreen(
                sessionRepository = sessionRepository,
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onSearch = { users ->
                    onPushOverlay(MainOverlay.LikedArtistsSearch(users = users))
                },
                onOpenArtist = { id, name, cover ->
                    onPushOverlay(MainOverlay.Artist(id, name, cover))
                },
            )
        }
        is MainOverlay.LikedArtistsSearch -> {
            LikedArtistsSearchScreen(
                sessionRepository = sessionRepository,
                contentBottomInset = contentBottomInset,
                isTop = isTop,
                searchUsers = overlay.users,
                onBack = onBack,
                onOpenArtist = { id, name, cover ->
                    onPushOverlay(MainOverlay.Artist(id, name, cover))
                },
            )
        }
        else -> CatalogCollectionPage(
            overlay = overlay,
            sessionRepository = sessionRepository,
            contentBottomInset = contentBottomInset,
            onBack = onBack,
            onPlayTracks = onPlayTracks,
            onOpenPlaylist = onOpenPlaylist,
            onPushOverlay = onPushOverlay,
            playingTrackId = playingTrackId,
            playingSourceId = playingSourceId,
            isPlaying = isPlaying,
            manageBridge = manageBridge,
        )
    }
}

