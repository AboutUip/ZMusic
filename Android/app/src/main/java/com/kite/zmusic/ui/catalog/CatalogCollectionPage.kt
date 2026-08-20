package com.kite.zmusic.ui.catalog

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.ChartSummary
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.UserProfileBrief
import com.kite.zmusic.data.formatAlbumType
import com.kite.zmusic.data.formatAlbumYear
import com.kite.zmusic.ui.artist.ArtistAlbumsScreen
import com.kite.zmusic.ui.artist.ArtistMvsScreen
import com.kite.zmusic.ui.artist.ArtistScreen
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.PlayingEqualizer
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.library.LikedArtistsScreen
import com.kite.zmusic.ui.library.LikedArtistsSearchScreen
import com.kite.zmusic.ui.main.LandscapeCoverEnter
import com.kite.zmusic.ui.main.LandscapeCoverExit
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.mv.MvPlayerScreen
import com.kite.zmusic.ui.search.SearchScreen
import com.kite.zmusic.ui.search.SearchViewModel
import com.kite.zmusic.ui.search.SearchViewModelFactory
import com.kite.zmusic.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.kite.zmusic.ui.main.MainOverlay
@Composable
internal fun CatalogCollectionPage(
    overlay: MainOverlay,
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenPlaylist: (Long, String, String?) -> Unit,
    onPushOverlay: (MainOverlay) -> Unit,
    playingTrackId: Long,
    playingSourceId: Long,
    isPlaying: Boolean,
    manageBridge: PlaylistManageBridge? = null,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val vm: CatalogViewModel = viewModel(
        key = overlay.stackKey(),
        factory = CatalogViewModelFactory(
            overlay,
            sessionRepository,
            app.playlistTracksCache,
            app.albumTracksCache,
            app.homeFeedRepository,
            app.likedPlaylistRepository,
            app.playlistCollectionRepository,
            app.albumCollectionRepository,
            app.islandNoticeCenter,
            app.catalogRepository,
        ),
    )
    when (overlay) {
        MainOverlay.Search, MainOverlay.Settings, is MainOverlay.PlaylistSearch, is MainOverlay.Mv,
        is MainOverlay.Artist, is MainOverlay.ArtistAlbums, is MainOverlay.ArtistMvs,
        MainOverlay.LikedArtists, is MainOverlay.LikedArtistsSearch,
        -> Unit
        MainOverlay.Daily -> {
            LaunchedEffect(Unit) { vm.loadDaily() }
            val ui by vm.list.collectAsStateWithLifecycle()
            TrackCollectionScreen(
                state = ui,
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onPlayAt = { i ->
                    if (ui.tracks.isNotEmpty()) {
                        onPlayTracks(ui.tracks, i, null, "每日推荐")
                    }
                },
                onRetry = { vm.loadDaily(force = true) },
                playingTrackId = playingTrackId,
                playingSourceId = playingSourceId,
                isPlaying = isPlaying,
                onOpenArtist = { id, name, cover ->
                    onPushOverlay(MainOverlay.Artist(id, name, cover))
                },
            )
        }
        MainOverlay.Fm -> {
            LaunchedEffect(Unit) { vm.loadFm() }
            val ui by vm.list.collectAsStateWithLifecycle()
            TrackCollectionScreen(
                state = ui,
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onPlayAt = { i ->
                    if (ui.tracks.isNotEmpty()) {
                        onPlayTracks(ui.tracks, i, null, "私人漫游")
                    }
                },
                onRetry = vm::loadFm,
                extraActionLabel = "换一批",
                extraActionIcon = ZIcons.SkipNext,
                onExtraAction = vm::loadMoreFm,
                playingTrackId = playingTrackId,
                playingSourceId = playingSourceId,
                isPlaying = isPlaying,
                onOpenArtist = { id, name, cover ->
                    onPushOverlay(MainOverlay.Artist(id, name, cover))
                },
            )
        }
        MainOverlay.Charts -> {
            LaunchedEffect(Unit) { vm.loadCharts() }
            val ui by vm.charts.collectAsStateWithLifecycle()
            ChartsScreen(
                state = ui,
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onOpen = { onOpenPlaylist(it.id, it.name, it.coverUrl) },
                onRetry = vm::loadCharts,
            )
        }
        is MainOverlay.Playlist -> {
            LaunchedEffect(overlay.id) {
                vm.loadPlaylist(
                    overlay.id,
                    overlay.title,
                    overlay.coverUrl,
                    seedOwned = overlay.owned,
                    seedHeart = overlay.heart,
                    seedSubscribed = overlay.collected,
                )
            }
            val ui by vm.list.collectAsStateWithLifecycle()
            TrackCollectionScreen(
                state = ui,
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onPlayAt = { i ->
                    if (ui.tracks.isNotEmpty()) {
                        onPlayTracks(ui.tracks, i, overlay.id, ui.title)
                    }
                },
                onRetry = {
                    vm.loadPlaylist(
                        overlay.id,
                        overlay.title,
                        overlay.coverUrl,
                        force = true,
                        seedOwned = overlay.owned,
                        seedHeart = overlay.heart,
                        seedSubscribed = overlay.collected,
                    )
                },
                onLoadMore = vm::loadMorePlaylist,
                playingTrackId = playingTrackId,
                playingSourceId = playingSourceId,
                isPlaying = isPlaying,
                onSubscribe = if (ui.canSubscribe) vm::subscribe else null,
                onUnsubscribe = vm::unsubscribe,
                onRemoveTrack = vm::removeTrack,
                onRemoveTracks = vm::removeTracks,
                onSearch = {
                    onPushOverlay(
                        MainOverlay.PlaylistSearch(
                            playlistId = overlay.id,
                            title = ui.title.ifBlank { overlay.title },
                            heart = overlay.heart || ui.isHeartPlaylist,
                            owned = overlay.owned || ui.isOwnedPlaylist,
                        ),
                    )
                },
                manageBridge = manageBridge,
                onOpenArtist = { id, name, cover ->
                    onPushOverlay(MainOverlay.Artist(id, name, cover))
                },
            )
        }
        is MainOverlay.ArtistSongs -> {
            LaunchedEffect(overlay.artistId) {
                vm.loadArtistSongs(overlay.artistId, overlay.name, overlay.coverUrl)
            }
            val ui by vm.list.collectAsStateWithLifecycle()
            TrackCollectionScreen(
                state = ui,
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onPlayAt = { i ->
                    if (ui.tracks.isNotEmpty()) {
                        onPlayTracks(ui.tracks, i, null, overlay.name)
                    }
                },
                onRetry = {
                    vm.loadArtistSongs(overlay.artistId, overlay.name, overlay.coverUrl, force = true)
                },
                onLoadMore = vm::loadMorePlaylist,
                playingTrackId = playingTrackId,
                playingSourceId = playingSourceId,
                isPlaying = isPlaying,
                onOpenCreator = if (overlay.artistId > 0L) {
                    { onPushOverlay(MainOverlay.Artist(overlay.artistId, overlay.name, overlay.coverUrl)) }
                } else {
                    null
                },
                onOpenArtist = { id, name, cover ->
                    onPushOverlay(MainOverlay.Artist(id, name, cover))
                },
            )
        }
        is MainOverlay.Album -> {
            LaunchedEffect(overlay.id) { vm.loadAlbum(overlay.id, overlay.title) }
            val ui by vm.list.collectAsStateWithLifecycle()
            TrackCollectionScreen(
                state = ui,
                contentBottomInset = contentBottomInset,
                onBack = onBack,
                onPlayAt = { i ->
                    if (ui.tracks.isNotEmpty()) {
                        // 专辑 id 不是歌单 id：传过去会触发歌单灌列，把队列换成无关歌曲。
                        onPlayTracks(ui.tracks, i, null, ui.title)
                    }
                },
                onRetry = { vm.loadAlbum(overlay.id, overlay.title, force = true) },
                playingTrackId = playingTrackId,
                playingSourceId = playingSourceId,
                isPlaying = isPlaying,
                onSubscribe = if (ui.canSubscribe) vm::subscribe else null,
                onUnsubscribe = vm::unsubscribe,
                onOpenCreator = ui.creatorId.takeIf { it > 0L }?.let { aid ->
                    {
                        onPushOverlay(
                            MainOverlay.Artist(
                                aid,
                                ui.creatorName ?: overlay.title,
                                ui.creatorAvatarUrl ?: ui.coverUrl,
                            ),
                        )
                    }
                },
                onOpenArtist = { id, name, cover ->
                    onPushOverlay(MainOverlay.Artist(id, name, cover))
                },
            )
        }
    }
}
