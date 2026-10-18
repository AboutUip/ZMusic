package com.kite.zmusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.HomeBanner
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.platform.HomeBlock
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.RecommendPlaylistCard
import com.kite.zmusic.data.TrackRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.kite.zmusic.i18n.t

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
    val unavailable: Set<HomeBlock> = emptySet(),
)

class HomeViewModel(
    private val homeFeed: HomeFeedRepository,
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
            unavailable = feed.unavailable,
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
            unavailable = homeFeed.peek().unavailable,
        ),
    )

    init {
        homeFeed.ensureLoaded()
    }

    fun loadPersonalFm(onReady: (List<TrackRow>) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val (tracks, err) = homeFeed.loadPersonalFm()
            if (tracks.isEmpty()) {
                onError(err ?: t("暂时没有漫游歌曲"))
            } else {
                onReady(tracks)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshAwait() }
    }

    /** 下拉刷新要等这次请求结束再收回指示器，不能只看中间有没有闪过 refreshing。 */
    suspend fun refreshAwait() {
        if (homeFeed.peek().refreshing) {
            homeFeed.feed.first { !it.refreshing }
            return
        }
        val job = viewModelScope.launch { homeFeed.refresh(force = true) }
        job.join()
    }
}
