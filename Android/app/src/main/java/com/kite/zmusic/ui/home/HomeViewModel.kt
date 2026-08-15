package com.kite.zmusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.HomeBanner
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.RecommendPlaylistCard
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val banners: List<HomeBanner> = emptyList(),
    val dailySongs: List<TrackRow> = emptyList(),
    val playlists: List<RecommendPlaylistCard> = emptyList(),
    val dailyPlaylists: List<RecommendPlaylistCard> = emptyList(),
    val newSongs: List<TrackRow> = emptyList(),
    val mvs: List<RecommendMvCard> = emptyList(),
)

class HomeViewModel(
    private val sessionRepository: SessionRepository,
    private val homeFeed: HomeFeedRepository,
    private val userClient: NcmUserClient = NcmUserClient(),
) : ViewModel() {

    val ui: StateFlow<HomeUiState> = homeFeed.feed.map { feed ->
        HomeUiState(
            loading = feed.loading && !feed.isWarm,
            refreshing = feed.refreshing,
            error = feed.error,
            banners = feed.banners,
            dailySongs = feed.dailySongs,
            playlists = feed.playlists,
            dailyPlaylists = feed.dailyPlaylists,
            newSongs = feed.newSongs,
            mvs = feed.mvs,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        HomeUiState(
            loading = !homeFeed.peek().isWarm,
            refreshing = homeFeed.peek().refreshing,
            error = homeFeed.peek().error,
            banners = homeFeed.peek().banners,
            dailySongs = homeFeed.peek().dailySongs,
            playlists = homeFeed.peek().playlists,
            dailyPlaylists = homeFeed.peek().dailyPlaylists,
            newSongs = homeFeed.peek().newSongs,
            mvs = homeFeed.peek().mvs,
        ),
    )

    init {
        homeFeed.ensureLoaded()
    }

    fun loadPersonalFm(onReady: (List<TrackRow>) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            if (cookie.isBlank()) {
                onError("请先登录")
                return@launch
            }
            try {
                val json = userClient.personalFm(cookie)
                val tracks = NcmHomeParse.personalFmTracks(json)
                if (tracks.isEmpty()) {
                    onError(NcmJson.userFacingMessage(json, "暂时没有漫游歌曲"))
                    return@launch
                }
                onReady(tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(NcmJson.userFacingThrowable(e, "漫游加载失败"))
            }
        }
    }

    fun refresh() {
        if (homeFeed.peek().refreshing) return
        viewModelScope.launch { homeFeed.refresh(force = true) }
    }
}
