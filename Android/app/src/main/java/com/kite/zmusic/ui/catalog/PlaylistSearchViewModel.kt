package com.kite.zmusic.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.CatalogRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.PlaylistTrackLoader
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PlaylistSearchUiState(
    val title: String,
    val query: String = "",
    val hits: List<TrackRow> = emptyList(),
    val loaded: List<TrackRow> = emptyList(),
    val expectedCount: Int = 0,
    val complete: Boolean = false,
    val scanning: Boolean = false,
    val error: String? = null,
)

class PlaylistSearchViewModel(
    private val playlistId: Long,
    private val title: String,
    heart: Boolean,
    private val sessionRepository: SessionRepository,
    private val playlistTracksCache: PlaylistTracksCache,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val islandNotices: IslandNoticeCenter,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private var heartSource = heart
    private val _ui = MutableStateFlow(PlaylistSearchUiState(title = title))
    val ui: StateFlow<PlaylistSearchUiState> = _ui.asStateFlow()

    private var debounceJob: Job? = null
    private var fillJob: Job? = null

    init {
        seed()
        viewModelScope.launch {
            playlistTracksCache.revision.collect {
                if (heartSource) return@collect
                val entry = playlistTracksCache.peek(playlistId) ?: return@collect
                if (entry.tracks.isEmpty()) return@collect
                applyTracks(entry.tracks, entry.expectedCount, entry.complete, entry.title)
            }
        }
        viewModelScope.launch {
            likedPlaylistRepository.snapshot.collect { snap ->
                if (snap == null || snap.tracks.isEmpty()) return@collect
                val belongs = heartSource ||
                    (playlistId > 0L && snap.playlistId == playlistId)
                if (!belongs) return@collect
                heartSource = true
                applyTracks(
                    snap.tracks,
                    snap.trackCount,
                    snap.complete,
                    snap.title.ifBlank { title },
                )
            }
        }
        viewModelScope.launch { ensureSeedLoaded() }
    }

    fun onQueryChange(value: String) {
        _ui.update { it.copy(query = value, error = null) }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(120)
            refilter()
            startFillIfNeeded()
        }
    }

    fun clearQuery() {
        debounceJob?.cancel()
        fillJob?.cancel()
        _ui.update {
            it.copy(query = "", hits = emptyList(), scanning = false, error = null)
        }
    }

    fun resetQuery() = clearQuery()

    fun indexInPlaylist(trackId: Long): Int =
        _ui.value.loaded.indexOfFirst { it.id == trackId }

    fun removeTrack(track: TrackRow, owned: Boolean) {
        viewModelScope.launch {
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            if (cookie.isBlank()) {
                islandNotices.show("请先登录", track.coverUrl)
                return@launch
            }
            try {
                if (heartSource) {
                    likedPlaylistRepository.applyLocalLike(track, liked = false)
                    val ack = catalog.unlikeSong(track.id, cookie)
                    if (!ack.ok) {
                        likedPlaylistRepository.applyLocalLike(track, liked = true, scheduleSync = false)
                        islandNotices.show("移除失败", track.coverUrl)
                        return@launch
                    }
                    islandNotices.show("已从喜欢的音乐移除", track.coverUrl)
                    return@launch
                }
                if (!owned) {
                    islandNotices.show("只能从自己创建的歌单移除歌曲", track.coverUrl)
                    return@launch
                }
                val ack = catalog.deletePlaylistTracks(playlistId, listOf(track.id), cookie)
                if (!ack.ok) {
                    islandNotices.show("无法从歌单移除", track.coverUrl)
                    return@launch
                }
                playlistTracksCache.removeTrack(playlistId, track.id)
                islandNotices.show("已从歌单移除", track.coverUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                islandNotices.show(NcmJson.userFacingThrowable(e, "移除失败"), track.coverUrl)
            }
        }
    }

    private fun seed() {
        val liked = likedPlaylistRepository.peek()
        if (liked != null && liked.tracks.isNotEmpty() &&
            (heartSource || (playlistId > 0L && liked.playlistId == playlistId) ||
                (heartSource && liked.playlistId == 0L))
        ) {
            heartSource = true
            applyTracks(
                liked.tracks,
                liked.trackCount,
                liked.complete,
                liked.title.ifBlank { title },
            )
            return
        }
        val cached = playlistTracksCache.peek(playlistId)?.takeIf { it.tracks.isNotEmpty() }
        if (cached != null) {
            applyTracks(cached.tracks, cached.expectedCount, cached.complete, cached.title)
        }
    }

    private suspend fun ensureSeedLoaded() {
        if (_ui.value.loaded.isNotEmpty()) return
        if (heartSource) {
            val liked = likedPlaylistRepository.peek()
            if (liked != null && liked.tracks.isNotEmpty()) {
                applyTracks(liked.tracks, liked.trackCount, liked.complete, liked.title)
            }
            return
        }
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isBlank()) {
            _ui.update { it.copy(error = "请先登录") }
            return
        }
        try {
            val entry = playlistTracksCache.getOrFetch(playlistId, title, cookie)
            applyTracks(entry.tracks, entry.expectedCount, entry.complete, entry.title)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(error = NcmJson.userFacingThrowable(e, "歌单加载失败"))
            }
        }
    }

    private fun startFillIfNeeded() {
        val state = _ui.value
        if (state.query.isBlank() || state.complete) {
            fillJob?.cancel()
            _ui.update { it.copy(scanning = false) }
            return
        }
        if (fillJob?.isActive == true) return
        fillJob = viewModelScope.launch {
            _ui.update { it.copy(scanning = true) }
            try {
                while (_ui.value.query.isNotBlank() && !_ui.value.complete) {
                    val grew = fillNextPage()
                    if (!grew) break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(error = NcmJson.userFacingThrowable(e, "搜索失败"))
                }
            } finally {
                _ui.update { it.copy(scanning = false) }
            }
        }
    }

    private suspend fun fillNextPage(): Boolean {
        if (heartSource) return fillLikedNext()
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isBlank()) return false
        val before = _ui.value.loaded.size
        val entry = playlistTracksCache.ensureLoadedThrough(
            playlistId,
            _ui.value.title.ifBlank { title },
            cookie,
            before + SEARCH_PAGE,
        )
        applyTracks(entry.tracks, entry.expectedCount, entry.complete, entry.title)
        return entry.complete || entry.tracks.size > before
    }

    private suspend fun fillLikedNext(): Boolean {
        val snap = likedPlaylistRepository.peek() ?: return false
        if (snap.complete) {
            applyTracks(snap.tracks, snap.trackCount, true, snap.title)
            return false
        }
        val before = snap.tracks.size
        likedPlaylistRepository.ensureLoadedThrough(before + SEARCH_PAGE)
        val next = withTimeoutOrNull(8_000) {
            likedPlaylistRepository.snapshot.first { latest ->
                latest != null && (latest.complete || latest.tracks.size > before)
            }
        } ?: return false
        val grown = next ?: return false
        if (grown.tracks.isNotEmpty()) {
            applyTracks(grown.tracks, grown.trackCount, grown.complete, grown.title)
        }
        return grown.complete || grown.tracks.size > before
    }

    private fun applyTracks(
        tracks: List<TrackRow>,
        expectedCount: Int,
        complete: Boolean,
        sourceTitle: String,
    ) {
        _ui.update { state ->
            val nextLoaded = if (!complete && tracks.size < state.loaded.size) {
                state.loaded
            } else {
                tracks
            }
            val titleNow = sourceTitle.ifBlank { state.title }
            state.copy(
                title = titleNow,
                loaded = nextLoaded,
                expectedCount = expectedCount.coerceAtLeast(nextLoaded.size),
                complete = complete || (expectedCount > 0 && nextLoaded.size >= expectedCount),
                hits = filterTracks(nextLoaded, state.query),
                error = if (nextLoaded.isNotEmpty()) null else state.error,
            )
        }
    }

    private fun refilter() {
        _ui.update { state ->
            state.copy(hits = filterTracks(state.loaded, state.query))
        }
    }

    companion object {
        private const val SEARCH_PAGE = 100
    }
}

private fun filterTracks(tracks: List<TrackRow>, query: String): List<TrackRow> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return tracks.filter { it.matchesPlaylistQuery(needle) }
}

private fun TrackRow.matchesPlaylistQuery(needle: String): Boolean {
    if (name.contains(needle, ignoreCase = true)) return true
    if (artists.contains(needle, ignoreCase = true)) return true
    val albumName = album
    return !albumName.isNullOrBlank() && albumName.contains(needle, ignoreCase = true)
}

class PlaylistSearchViewModelFactory(
    private val playlistId: Long,
    private val title: String,
    private val heart: Boolean,
    private val sessionRepository: SessionRepository,
    private val playlistTracksCache: PlaylistTracksCache,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val islandNotices: IslandNoticeCenter,
    private val catalog: CatalogRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaylistSearchViewModel::class.java)) {
            return PlaylistSearchViewModel(
                playlistId,
                title,
                heart,
                sessionRepository,
                playlistTracksCache,
                likedPlaylistRepository,
                islandNotices,
                catalog,
            ) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
