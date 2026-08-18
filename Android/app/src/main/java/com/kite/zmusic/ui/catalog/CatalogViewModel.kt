package com.kite.zmusic.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.AlbumBrief
import com.kite.zmusic.data.AlbumCollectionRepository
import com.kite.zmusic.data.AlbumDynamic
import com.kite.zmusic.data.AlbumTracksCache
import com.kite.zmusic.data.ChartSummary
import com.kite.zmusic.data.CollectedAlbum
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.NcmArtistParse
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlaylistCollectionRepository
import com.kite.zmusic.data.PlaylistSubscribeMeta
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.PlaylistTrackLoader
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.isDefaultPlaylistCover
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CatalogListState(
    val title: String = "",
    val subtitle: String? = null,
    val coverUrl: String? = null,
    val tracks: List<TrackRow> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val playlistId: Long = 0L,
    val expectedCount: Int = 0,
    val complete: Boolean = true,
    val canSubscribe: Boolean = false,
    val subscribed: Boolean = false,
    val subscribeBusy: Boolean = false,
    val isOwnedPlaylist: Boolean = false,
    val isHeartPlaylist: Boolean = false,
    val creatorName: String? = null,
    val creatorAvatarUrl: String? = null,
    val creatorId: Long = 0L,
    val playCount: Long = 0L,
    val subscribedCount: Int = 0,
    val albumId: Long = 0L,
    val albumCompany: String? = null,
    val albumPublishTime: Long = 0L,
    val albumDescription: String? = null,
    val albumType: String? = null,
    val albumAlias: String? = null,
    val commentCount: Int = 0,
) {
    val isAlbum: Boolean get() = albumId > 0L
}

data class ChartsUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val charts: List<ChartSummary> = emptyList(),
)

private data class SubscribePin(val album: Boolean, val id: Long, val on: Boolean)

class CatalogViewModel(
    private val sessionRepository: SessionRepository,
    private val playlistTracksCache: PlaylistTracksCache,
    private val albumTracksCache: AlbumTracksCache,
    private val homeFeed: HomeFeedRepository,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val playlistCollection: PlaylistCollectionRepository,
    private val albumCollection: AlbumCollectionRepository,
    private val islandNotices: IslandNoticeCenter,
    private val userClient: NcmUserClient = NcmUserClient(),
) : ViewModel() {

    private val _list = MutableStateFlow(CatalogListState())
    val list: StateFlow<CatalogListState> = _list.asStateFlow()

    private val _charts = MutableStateFlow(ChartsUiState())
    val charts: StateFlow<ChartsUiState> = _charts.asStateFlow()

    private var loadJob: Job? = null
    private var moreJob: Job? = null
    private var subscribeMetaJob: Job? = null
    private var openPlaylistId: Long = 0L
    private var artistSongsId: Long = 0L
    /** 用户刚点过收藏/取消，在远端对账前不要被旧的 detail / 列表覆盖。 */
    private var pinnedSubscribe: SubscribePin? = null

    init {
        viewModelScope.launch {
            playlistTracksCache.revision.collect {
                absorbOpenPlaylistFromCache()
            }
        }
        viewModelScope.launch {
            likedPlaylistRepository.snapshot.collect { snap ->
                if (snap == null || snap.tracks.isEmpty()) return@collect
                val viewingHeart = _list.value.isHeartPlaylist ||
                    (openPlaylistId > 0L && snap.playlistId == openPlaylistId)
                if (!viewingHeart) return@collect
                if (snap.playlistId > 0L && openPlaylistId != snap.playlistId) {
                    openPlaylistId = snap.playlistId
                }
                applyLikedSnapshot(snap, _list.value.title)
            }
        }
        viewModelScope.launch {
            playlistCollection.playlists.collect { list ->
                val id = openPlaylistId
                if (id <= 0L) return@collect
                val known = list.find { it.id == id } ?: return@collect
                applyKnownPlaylistFlags(known)
            }
        }
    }

    fun loadDaily(force: Boolean = false) {
        closePlaylist()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val warm = homeFeed.peek()
            if (!force && warm.dailySongs.isNotEmpty()) {
                applyDaily(warm.dailySongs, warm.error)
                return@launch
            }
            if (warm.dailySongs.isEmpty()) {
                _list.update { CatalogListState(title = "每日推荐", loading = true) }
            } else {
                applyDaily(warm.dailySongs, null)
                if (force) _list.update { it.copy(refreshing = true) }
            }
            try {
                homeFeed.refresh(force = force || warm.dailySongs.isEmpty())
                val feed = homeFeed.peek()
                applyDaily(
                    feed.dailySongs,
                    if (feed.dailySongs.isEmpty()) {
                        feed.error ?: "今日还没有日推"
                    } else {
                        null
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_list.value.tracks.isNotEmpty()) {
                    _list.update { it.copy(refreshing = false, loading = false) }
                    return@launch
                }
                _list.update {
                    CatalogListState(
                        title = "每日推荐",
                        error = NcmJson.userFacingThrowable(e, "加载失败"),
                    )
                }
            }
        }
    }

    fun loadFm() {
        closePlaylist()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val keep = _list.value.takeIf { it.tracks.isNotEmpty() }
            if (keep != null) {
                _list.update { it.copy(refreshing = true, error = null) }
            } else {
                _list.update { CatalogListState(title = "私人漫游", loading = true) }
            }
            fetchFm(replace = true)
        }
    }

    fun loadMoreFm() {
        if (_list.value.loading) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _list.update { it.copy(loading = true, error = null) }
            fetchFm(replace = true)
        }
    }

    fun loadPlaylist(
        id: Long,
        title: String,
        coverUrl: String? = null,
        force: Boolean = false,
        seedOwned: Boolean = false,
        seedHeart: Boolean = false,
        seedSubscribed: Boolean? = null,
    ) {
        val previousId = openPlaylistId
        if (previousId != id) pinnedSubscribe = null
        openPlaylistId = id
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val switching = previousId != id || _list.value.playlistId != id
            val seededCover = coverUrl?.takeIf { it.isNotBlank() }
                ?: _list.value.takeIf { it.playlistId == id }?.coverUrl
            if (switching) {
                _list.value = openingPlaylistState(
                    id = id,
                    title = title,
                    coverUrl = seededCover,
                    seedOwned = seedOwned,
                    seedHeart = seedHeart,
                    seedSubscribed = seedSubscribed,
                )
            } else {
                applySeedSubscribe(id, seedOwned, seedHeart, seedSubscribed)
            }
            val likedPeek = likedPlaylistRepository.peek()
            val isHeart = seedHeart ||
                _list.value.isHeartPlaylist ||
                (likedPeek != null && likedPeek.playlistId > 0L && likedPeek.playlistId == id) ||
                playlistCollection.find(id)?.isHeartPlaylist == true
            if (isHeart) {
                val liked = likedPlaylistRepository.peek()
                if (liked != null && liked.tracks.isNotEmpty() &&
                    (liked.playlistId == id || liked.playlistId == 0L)
                ) {
                    applyLikedSnapshot(liked, title, seededCover)
                }
                if (!force && _list.value.tracks.isNotEmpty()) {
                    _list.update { it.copy(refreshing = false, loading = false) }
                    val needsOrderFix = liked == null || liked.displayIds.isEmpty()
                    if (!needsOrderFix) return@launch
                    cookieOrNull() ?: return@launch
                    try {
                        val snap = likedPlaylistRepository.forceRefresh()
                        if (openPlaylistId != id && !_list.value.isHeartPlaylist) return@launch
                        if (snap != null) {
                            if (snap.playlistId > 0L) openPlaylistId = snap.playlistId
                            applyLikedSnapshot(snap, title, seededCover)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                    _list.update { it.copy(refreshing = false, loading = false) }
                    return@launch
                }
                if (force && _list.value.tracks.isNotEmpty()) {
                    _list.update { it.copy(refreshing = true, error = null, loading = false) }
                }
                cookieOrNull() ?: return@launch
                try {
                    val snap = likedPlaylistRepository.forceRefresh()
                    if (openPlaylistId != id && !_list.value.isHeartPlaylist) return@launch
                    if (snap != null) {
                        if (snap.playlistId > 0L) openPlaylistId = snap.playlistId
                        applyLikedSnapshot(snap, title, seededCover)
                    }
                    _list.update { it.copy(refreshing = false, loading = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (openPlaylistId != id) return@launch
                    _list.update {
                        if (it.playlistId == id && it.tracks.isNotEmpty()) {
                            it.copy(refreshing = false, loading = false)
                        } else {
                            it
                        }
                    }
                }
                return@launch
            }
            if (!force) {
                val cached = withContext(Dispatchers.IO) { playlistTracksCache.peek(id) }
                    ?.takeIf { it.playlistId == id && it.tracks.isNotEmpty() }
                if (cached != null) {
                    applyPlaylistEntry(cached, title, seededCover)
                    refreshSubscribeMeta(id)
                    _list.update { it.copy(refreshing = false, loading = false, error = null) }
                    return@launch
                }
            } else if (_list.value.playlistId == id && _list.value.tracks.isNotEmpty()) {
                _list.update { it.copy(refreshing = true, error = null) }
            }
            if (_list.value.playlistId != id || _list.value.tracks.isEmpty()) {
                _list.update {
                    if (it.playlistId != id) {
                        openingPlaylistState(
                            id = id,
                            title = title,
                            coverUrl = seededCover,
                            seedOwned = seedOwned,
                            seedHeart = seedHeart,
                            seedSubscribed = seedSubscribed,
                        )
                    } else {
                        it.copy(
                            title = title,
                            coverUrl = seededCover ?: it.coverUrl,
                            loading = true,
                            complete = false,
                            error = null,
                        )
                    }
                }
            }
            val cookie = cookieOrNull() ?: return@launch
            try {
                val keepCount = _list.value.tracks.size
                var entry = if (force) {
                    playlistTracksCache.forceRefresh(id, title, cookie)
                } else {
                    playlistTracksCache.getOrFetch(id, title, cookie)
                }
                if (!entry.complete && keepCount > entry.tracks.size) {
                    entry = playlistTracksCache.ensureLoadedThrough(id, title, cookie, keepCount)
                }
                if (openPlaylistId != id) return@launch
                applyPlaylistEntry(entry, title, seededCover)
                refreshSubscribeMeta(id)
                _list.update {
                    if (it.playlistId == id) it.copy(refreshing = false) else it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (openPlaylistId != id) return@launch
                if (_list.value.playlistId == id && _list.value.tracks.isNotEmpty()) {
                    _list.update { it.copy(refreshing = false, loading = false) }
                    return@launch
                }
                val flags = _list.value.takeIf { it.playlistId == id }
                _list.update {
                    CatalogListState(
                        title = title,
                        coverUrl = seededCover,
                        playlistId = id,
                        error = NcmJson.userFacingThrowable(e, "歌单加载失败"),
                        canSubscribe = flags?.canSubscribe ?: true,
                        subscribed = flags?.subscribed ?: false,
                        isOwnedPlaylist = flags?.isOwnedPlaylist ?: false,
                        isHeartPlaylist = flags?.isHeartPlaylist ?: false,
                    )
                }
            }
        }
    }

    fun loadMorePlaylist() {
        if (artistSongsId > 0L) {
            loadMoreArtistSongs()
            return
        }
        val id = openPlaylistId
        if (id <= 0L) return
        val state = _list.value
        if (state.complete || state.loading || state.refreshing) return
        if (moreJob?.isActive == true) return
        moreJob = viewModelScope.launch {
            val liked = likedPlaylistRepository.peek()
            if (liked != null && liked.playlistId == id) {
                if (liked.tracks.size > state.tracks.size) {
                    applyLikedSnapshot(liked, state.title)
                    if (liked.complete) return@launch
                }
                likedPlaylistRepository.ensureLoadedThrough(
                    liked.tracks.size + PlaylistTrackLoader.PAGE,
                )
                return@launch
            }
            val cached = playlistTracksCache.peek(id)
            if (cached != null && cached.tracks.size > state.tracks.size) {
                applyPlaylistEntry(cached, state.title, state.coverUrl)
                if (cached.complete) return@launch
            }
            val cookie = cookieOrNull() ?: return@launch
            val title = state.title
            runCatching {
                playlistTracksCache.ensureLoadedThrough(
                    id,
                    title,
                    cookie,
                    _list.value.tracks.size + PlaylistTrackLoader.PAGE,
                )
            }.onSuccess { applyPlaylistEntry(it, title, state.coverUrl) }
        }
    }

    fun removeTrack(track: TrackRow) {
        val state = _list.value
        val id = state.playlistId
        if (id <= 0L) return
        viewModelScope.launch {
            val cookie = cookieOrNull() ?: return@launch
            try {
                if (state.isHeartPlaylist) {
                    likedPlaylistRepository.applyLocalLike(track, liked = false)
                    val json = userClient.likeSong(track.id, like = false, cookie)
                    if (NcmJson.apiCode(json) != 200) {
                        likedPlaylistRepository.applyLocalLike(track, liked = true, scheduleSync = false)
                        islandNotices.show("移除失败", track.coverUrl)
                        return@launch
                    }
                    islandNotices.show("已从喜欢的音乐移除", track.coverUrl)
                    return@launch
                }
                if (!state.isOwnedPlaylist) {
                    islandNotices.show("只能从自己创建的歌单移除歌曲", track.coverUrl)
                    return@launch
                }
                val json = userClient.playlistTracks("del", id, listOf(track.id), cookie)
                if (NcmJson.apiCode(json) != 200) {
                    islandNotices.show("无法从歌单移除", track.coverUrl)
                    return@launch
                }
                playlistTracksCache.removeTrack(id, track.id)
                _list.update {
                    if (it.playlistId != id) it else it.copy(
                        tracks = it.tracks.filterNot { t -> t.id == track.id },
                        expectedCount = (it.expectedCount - 1).coerceAtLeast(0),
                    )
                }
                islandNotices.show("已从歌单移除", track.coverUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                islandNotices.show(NcmJson.userFacingThrowable(e, "移除失败"), track.coverUrl)
            }
        }
    }

    fun removeTracks(tracks: List<TrackRow>, onFinished: (Boolean) -> Unit = {}) {
        val state = _list.value
        val id = state.playlistId
        val unique = tracks.distinctBy { it.id }.filter { it.id > 0L }
        if (id <= 0L || unique.isEmpty()) {
            onFinished(false)
            return
        }
        viewModelScope.launch {
            val cookie = cookieOrNull()
            if (cookie == null) {
                onFinished(false)
                return@launch
            }
            val cover = unique.first().coverUrl
            try {
                if (state.isHeartPlaylist) {
                    unique.forEach { likedPlaylistRepository.applyLocalLike(it, liked = false) }
                    var failed = 0
                    for (track in unique) {
                        val json = userClient.likeSong(track.id, like = false, cookie)
                        if (NcmJson.apiCode(json) != 200) {
                            likedPlaylistRepository.applyLocalLike(track, liked = true, scheduleSync = false)
                            failed++
                        }
                    }
                    val ok = failed < unique.size
                    islandNotices.show(
                        when {
                            failed == 0 -> "已从喜欢的音乐移除 ${unique.size} 首"
                            ok -> "已移除 ${unique.size - failed} 首，${failed} 首失败"
                            else -> "移除失败"
                        },
                        cover,
                    )
                    onFinished(ok)
                    return@launch
                }
                if (!state.isOwnedPlaylist) {
                    islandNotices.show("只能从自己创建的歌单移除歌曲", cover)
                    onFinished(false)
                    return@launch
                }
                val ids = unique.map { it.id }
                for (chunk in ids.chunked(50)) {
                    val json = userClient.playlistTracks("del", id, chunk, cookie)
                    if (NcmJson.apiCode(json) != 200) {
                        islandNotices.show("无法从歌单移除", cover)
                        onFinished(false)
                        return@launch
                    }
                }
                playlistTracksCache.removeTracks(id, ids)
                _list.update {
                    if (it.playlistId != id) it else it.copy(
                        tracks = it.tracks.filterNot { t -> t.id in ids.toHashSet() },
                        expectedCount = (it.expectedCount - ids.size).coerceAtLeast(0),
                    )
                }
                islandNotices.show("已从歌单移除 ${unique.size} 首", cover)
                onFinished(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                islandNotices.show(NcmJson.userFacingThrowable(e, "移除失败"), cover)
                onFinished(false)
            }
        }
    }

    fun toggleSubscribe() {
        val state = _list.value
        if (state.isAlbum) {
            if (!state.canSubscribe || state.albumId <= 0L || state.subscribeBusy) return
            applyAlbumSubscribe(state, next = !state.subscribed)
            return
        }
        if (state.isHeartPlaylist) {
            islandNotices.show("我喜欢的音乐不能收藏", state.coverUrl)
            return
        }
        if (state.isOwnedPlaylist) {
            islandNotices.show("自己创建的歌单无需收藏", state.coverUrl)
            return
        }
        if (!state.canSubscribe || state.playlistId <= 0L || state.subscribeBusy) return
        applySubscribeChange(state, next = !state.subscribed)
    }

    fun subscribe() {
        val state = _list.value
        if (state.subscribed) return
        toggleSubscribe()
    }

    fun unsubscribe() {
        val state = _list.value
        if (!state.subscribed) return
        toggleSubscribe()
    }

    private fun applySubscribeChange(state: CatalogListState, next: Boolean) {
        val id = state.playlistId
        viewModelScope.launch {
            val cookie = cookieOrNull() ?: return@launch
            subscribeMetaJob?.cancel()
            pinnedSubscribe = SubscribePin(album = false, id = id, on = next)
            val countDelta = if (next) 1 else -1
            _list.update {
                it.copy(
                    subscribeBusy = true,
                    subscribed = next,
                    subscribedCount = (it.subscribedCount + countDelta).coerceAtLeast(0),
                )
            }
            try {
                val json = userClient.playlistSubscribe(id, next, cookie)
                if (NcmJson.apiCode(json) != 200) {
                    revertSubscribe(id, next)
                    islandNotices.show(
                        NcmJson.userFacingMessage(
                            json,
                            if (next) "收藏失败" else "取消收藏失败",
                        ),
                        state.coverUrl,
                    )
                    return@launch
                }
                _list.update {
                    if (it.playlistId == id) {
                        it.copy(subscribeBusy = false, subscribed = next)
                    } else {
                        it
                    }
                }
                playlistTracksCache.patchSubscribed(id, next, countDelta)
                playlistCollection.setSubscribed(
                    id,
                    next,
                    insert = if (next) summaryForSubscribe(_list.value.takeIf { it.playlistId == id } ?: state) else null,
                )
                islandNotices.show(if (next) "已收藏歌单" else "已取消收藏", state.coverUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                revertSubscribe(id, next)
                islandNotices.show(NcmJson.userFacingThrowable(e, "操作失败"), state.coverUrl)
            }
        }
    }

    private fun revertSubscribe(id: Long, attempted: Boolean) {
        if (pinMatches(id)) pinnedSubscribe = null
        _list.update {
            if (it.playlistId != id) return@update it
            val delta = if (attempted) -1 else 1
            it.copy(
                subscribeBusy = false,
                subscribed = !attempted,
                subscribedCount = (it.subscribedCount + delta).coerceAtLeast(0),
            )
        }
    }

    private fun applyAlbumSubscribe(state: CatalogListState, next: Boolean) {
        val id = state.albumId
        viewModelScope.launch {
            val cookie = cookieOrNull() ?: return@launch
            pinnedSubscribe = SubscribePin(album = true, id = id, on = next)
            val countDelta = if (next) 1 else -1
            _list.update {
                if (it.albumId != id) return@update it
                it.copy(
                    subscribeBusy = true,
                    subscribed = next,
                    subscribedCount = (it.subscribedCount + countDelta).coerceAtLeast(0),
                )
            }
            try {
                val json = userClient.albumSub(id, next, cookie)
                if (NcmJson.apiCode(json) != 200) {
                    revertAlbumSubscribe(id, next)
                    islandNotices.show(
                        NcmJson.userFacingMessage(
                            json,
                            if (next) "收藏失败" else "取消收藏失败",
                        ),
                        state.coverUrl,
                    )
                    return@launch
                }
                _list.update {
                    if (it.albumId == id) it.copy(subscribeBusy = false, subscribed = next) else it
                }
                albumCollection.setSubscribed(
                    id,
                    next,
                    insert = if (next) collectedFromState(_list.value.takeIf { it.albumId == id } ?: state) else null,
                )
                albumTracksCache.patchSubscribed(id, next, countDelta)
                islandNotices.show(if (next) "已收藏专辑" else "已取消收藏", state.coverUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                revertAlbumSubscribe(id, next)
                islandNotices.show(NcmJson.userFacingThrowable(e, "操作失败"), state.coverUrl)
            }
        }
    }

    private fun revertAlbumSubscribe(id: Long, attempted: Boolean) {
        if (pinMatchesAlbum(id)) pinnedSubscribe = null
        _list.update {
            if (it.albumId != id) return@update it
            val delta = if (attempted) -1 else 1
            it.copy(
                subscribeBusy = false,
                subscribed = !attempted,
                subscribedCount = (it.subscribedCount + delta).coerceAtLeast(0),
            )
        }
    }

    private fun collectedFromState(state: CatalogListState) = CollectedAlbum(
        id = state.albumId,
        name = state.title,
        coverUrl = state.coverUrl,
        artist = state.creatorName,
        artistId = state.creatorId,
        artistCoverUrl = state.creatorAvatarUrl,
        size = state.tracks.size.coerceAtLeast(1),
        publishTime = state.albumPublishTime,
        company = state.albumCompany,
        type = state.albumType,
        alias = state.albumAlias,
    )

    fun loadAlbum(id: Long, title: String, force: Boolean = false) {
        closePlaylist()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!force) {
                val live = _list.value.takeIf { it.albumId == id && it.tracks.isNotEmpty() }
                if (live != null) {
                    _list.update { it.copy(refreshing = false, loading = false, error = null) }
                    refreshAlbumDynamic(id)
                    return@launch
                }
                val cached = withContext(Dispatchers.IO) { albumTracksCache.peek(id) }
                    ?.takeIf { it.albumId == id && it.tracks.isNotEmpty() }
                if (cached != null) {
                    applyAlbumEntry(cached, title)
                    refreshAlbumDynamic(id)
                    return@launch
                }
            } else if (_list.value.albumId == id && _list.value.tracks.isNotEmpty()) {
                _list.update { it.copy(refreshing = true, error = null, loading = false) }
            }
            if (_list.value.albumId != id || _list.value.tracks.isEmpty()) {
                _list.update {
                    CatalogListState(
                        title = title,
                        albumId = id,
                        loading = true,
                        error = null,
                    )
                }
            }
            val cookie = cookieOrNull() ?: return@launch
            try {
                val (json, dynamicJson) = coroutineScope {
                    val albumDef = async { userClient.album(id, cookie) }
                    val dynamicDef = async {
                        runCatching { userClient.albumDetailDynamic(id, cookie) }.getOrNull()
                    }
                    albumDef.await() to dynamicDef.await()
                }
                val album = NcmHomeParse.albumBrief(json, id)
                if (album == null || album.songs.isEmpty()) {
                    _list.update {
                        if (it.tracks.isNotEmpty() && it.albumId == id) {
                            it.copy(refreshing = false, loading = false)
                        } else {
                            CatalogListState(
                                title = title,
                                albumId = id,
                                error = NcmJson.userFacingMessage(json, "专辑加载失败"),
                            )
                        }
                    }
                    return@launch
                }
                val dynamic = dynamicJson?.let { NcmHomeParse.albumDynamic(it) }
                val entry = albumCacheEntry(album, dynamic)
                albumTracksCache.save(entry)
                applyAlbumEntry(entry, title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _list.update {
                    if (it.tracks.isNotEmpty() && it.albumId == id) {
                        it.copy(refreshing = false, loading = false)
                    } else {
                        CatalogListState(
                            title = title,
                            albumId = id,
                            error = NcmJson.userFacingThrowable(e, "专辑加载失败"),
                        )
                    }
                }
            }
        }
    }

    private fun albumCacheEntry(
        album: AlbumBrief,
        dynamic: AlbumDynamic?,
    ) = AlbumTracksCache.Entry(
        albumId = album.id,
        title = album.name,
        tracks = album.songs,
        coverUrl = album.coverUrl,
        artist = album.artist,
        artistId = album.artistId,
        artistCoverUrl = album.artistCoverUrl,
        publishTime = album.publishTime,
        company = album.company,
        description = album.description,
        type = album.type,
        alias = album.alias,
        size = album.size.takeIf { it > 0 } ?: album.songs.size,
        updatedAtMs = System.currentTimeMillis(),
        subscribed = dynamic?.isSub,
        subscribedCount = dynamic?.subCount ?: 0,
        commentCount = dynamic?.commentCount ?: 0,
    )

    private fun applyAlbumEntry(entry: AlbumTracksCache.Entry, fallbackTitle: String) {
        val id = entry.albumId
        val size = entry.size.takeIf { it > 0 } ?: entry.tracks.size
        _list.value = CatalogListState(
            title = entry.title.ifBlank { fallbackTitle },
            subtitle = "${size}首",
            coverUrl = entry.coverUrl,
            tracks = entry.tracks,
            playlistId = 0L,
            complete = true,
            canSubscribe = true,
            subscribed = resolveAlbumSubscribed(id, entry.subscribed ?: false),
            creatorName = entry.artist,
            creatorId = entry.artistId,
            creatorAvatarUrl = entry.artistCoverUrl,
            subscribedCount = entry.subscribedCount,
            albumId = id,
            albumCompany = entry.company,
            albumPublishTime = entry.publishTime,
            albumDescription = entry.description,
            albumType = entry.type,
            albumAlias = entry.alias,
            commentCount = entry.commentCount,
        )
    }

    private fun refreshAlbumDynamic(id: Long) {
        if (id <= 0L) return
        subscribeMetaJob?.cancel()
        subscribeMetaJob = viewModelScope.launch {
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            if (cookie.isBlank()) return@launch
            val dynamic = runCatching {
                NcmHomeParse.albumDynamic(userClient.albumDetailDynamic(id, cookie))
            }.getOrNull() ?: return@launch
            if (_list.value.albumId != id) return@launch
            val subscribed = resolveAlbumSubscribed(id, dynamic.isSub)
            _list.update {
                if (it.albumId != id) {
                    it
                } else {
                    it.copy(
                        subscribed = subscribed,
                        subscribedCount = dynamic.subCount,
                        commentCount = dynamic.commentCount,
                    )
                }
            }
            albumTracksCache.attachDynamic(id, subscribed, dynamic.subCount, dynamic.commentCount)
        }
    }

    private fun resolveAlbumSubscribed(albumId: Long, remote: Boolean): Boolean {
        val pin = pinnedSubscribe
        if (pin != null && pin.album && pin.id == albumId) {
            if (remote == pin.on) pinnedSubscribe = null
            return pin.on
        }
        albumCollection.isSubscribed(albumId)?.let { return it }
        return remote
    }

    fun loadArtistSongs(artistId: Long, name: String, coverUrl: String?, force: Boolean = false) {
        closePlaylist()
        artistSongsId = artistId
        loadJob?.cancel()
        moreJob?.cancel()
        loadJob = viewModelScope.launch {
            val keep = _list.value.takeIf { !force && it.tracks.isNotEmpty() && it.playlistId == 0L }
            if (keep != null) {
                _list.update { it.copy(refreshing = true, error = null) }
            } else {
                _list.update {
                    CatalogListState(
                        title = "热门歌曲",
                        coverUrl = coverUrl,
                        creatorName = name,
                        creatorAvatarUrl = coverUrl,
                        creatorId = artistId,
                        loading = true,
                    )
                }
            }
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            try {
                val json = userClient.artistSongs(artistId, cookie, order = "hot", limit = 50, offset = 0)
                val (songs, more, total) = if (NcmJson.apiCode(json) == 200) {
                    NcmArtistParse.songsPage(json)
                } else {
                    Triple(emptyList(), false, 0)
                }
                val tracks = songs.ifEmpty {
                    val hot = userClient.artistTopSongs(artistId, cookie)
                    if (NcmJson.apiCode(hot) == 200) NcmArtistParse.topSongs(hot) else emptyList()
                }
                if (tracks.isEmpty()) {
                    _list.update {
                        CatalogListState(
                            title = "热门歌曲",
                            coverUrl = coverUrl,
                            creatorName = name,
                            creatorAvatarUrl = coverUrl,
                            creatorId = artistId,
                            error = NcmJson.userFacingMessage(json, "暂时没有歌曲"),
                        )
                    }
                    return@launch
                }
                _list.update {
                    CatalogListState(
                        title = "热门歌曲",
                        subtitle = if (total > tracks.size) "${tracks.size} / $total 首" else "${tracks.size} 首",
                        coverUrl = coverUrl ?: tracks.firstOrNull()?.coverUrl,
                        tracks = tracks,
                        creatorName = name,
                        creatorAvatarUrl = coverUrl,
                        creatorId = artistId,
                        complete = songs.isEmpty() || !more,
                        expectedCount = total.coerceAtLeast(tracks.size),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _list.update {
                    if (it.tracks.isNotEmpty()) {
                        it.copy(refreshing = false, loading = false)
                    } else {
                        CatalogListState(
                            title = "热门歌曲",
                            error = NcmJson.userFacingThrowable(e, "加载失败"),
                        )
                    }
                }
            }
        }
    }

    private fun loadMoreArtistSongs() {
        val id = artistSongsId
        if (id <= 0L) return
        val state = _list.value
        if (state.complete || state.loading || state.refreshing) return
        if (moreJob?.isActive == true) return
        moreJob = viewModelScope.launch {
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            try {
                val json = userClient.artistSongs(
                    id,
                    cookie,
                    order = "hot",
                    limit = 50,
                    offset = state.tracks.size,
                )
                if (NcmJson.apiCode(json) != 200) {
                    _list.update { it.copy(complete = true) }
                    return@launch
                }
                val (page, more, total) = NcmArtistParse.songsPage(json)
                _list.update { cur ->
                    if (artistSongsId != id) return@update cur
                    val merged = cur.tracks.mergeById(page) { it.id }
                    cur.copy(
                        tracks = merged,
                        complete = !more || page.isEmpty(),
                        expectedCount = total.coerceAtLeast(merged.size),
                        subtitle = if (total > merged.size) "${merged.size} / $total 首" else "${merged.size} 首",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _list.update { it.copy(complete = true) }
            }
        }
    }

    fun loadCharts() {
        closePlaylist()
        viewModelScope.launch {
            _charts.update { it.copy(loading = true, error = null) }
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            try {
                val json = userClient.toplistDetail(cookie)
                val charts = NcmHomeParse.charts(json)
                _charts.update {
                    ChartsUiState(
                        charts = charts,
                        error = if (charts.isEmpty()) {
                            NcmJson.userFacingMessage(json, "暂时没有榜单")
                        } else {
                            null
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _charts.update {
                    ChartsUiState(error = NcmJson.userFacingThrowable(e, "榜单加载失败"))
                }
            }
        }
    }

    suspend fun loadSong(id: Long): TrackRow? {
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isBlank() || id <= 0L) return null
        return runCatching {
            val json = userClient.songDetail(listOf(id), cookie)
            NcmLibraryParse.tracksFromSongDetail(json).firstOrNull()
        }.getOrNull()
    }

    private suspend fun fetchFm(replace: Boolean) {
        val cookie = cookieOrNull() ?: return
        try {
            val json = userClient.personalFm(cookie)
            val tracks = NcmHomeParse.personalFmTracks(json)
            if (tracks.isEmpty()) {
                _list.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = NcmJson.userFacingMessage(json, "暂时没有漫游歌曲"),
                    )
                }
                return
            }
            _list.update {
                CatalogListState(
                    title = "私人漫游",
                    subtitle = "根据口味继续听",
                    coverUrl = tracks.first().coverUrl,
                    tracks = if (replace) tracks else (it.tracks + tracks),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _list.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = NcmJson.userFacingThrowable(e, "漫游加载失败"),
                )
            }
        }
    }

    private fun absorbOpenPlaylistFromCache() {
        val id = openPlaylistId
        if (id <= 0L) return
        val cur = _list.value
        if (cur.isHeartPlaylist) {
            val snap = likedPlaylistRepository.peek() ?: return
            if (snap.tracks.isEmpty()) return
            if (snap.playlistId != id && snap.playlistId != 0L) return
            if (cur.tracks.size >= snap.tracks.size && cur.complete == snap.complete) return
            applyLikedSnapshot(snap, cur.title)
            return
        }
        val entry = playlistTracksCache.peek(id)?.takeIf { it.tracks.isNotEmpty() } ?: return
        if (cur.playlistId == id &&
            cur.tracks.size >= entry.tracks.size &&
            cur.complete == entry.complete
        ) {
            return
        }
        applyPlaylistEntry(entry, cur.title)
    }

    private fun applyPlaylistEntry(
        entry: PlaylistTracksCache.Entry,
        fallbackTitle: String,
        seededCover: String? = null,
    ) {
        if (entry.playlistId != openPlaylistId) return
        val expected = entry.expectedCount.coerceAtLeast(entry.tracks.size)
        val meta = entry.subscribeMeta?.takeIf { it.id == entry.playlistId }
        var syncedCover: String? = null
        _list.update {
            val same = it.playlistId == entry.playlistId
            val merged = NcmLibraryParse.mergeLoadedInOrder(
                entry.allIds,
                entry.tracks,
                if (same) it.tracks else emptyList(),
            )
            val tracks = if (same && merged.size < it.tracks.size && !entry.complete) {
                it.tracks
            } else {
                merged
            }
            val expectedNow = expected.coerceAtLeast(tracks.size)
            val firstCover = tracks.firstOrNull()?.coverUrl
                ?.takeUnless { isDefaultPlaylistCover(it) }
            syncedCover = firstCover
            CatalogListState(
                title = entry.title.ifBlank { fallbackTitle.ifBlank { it.title } },
                subtitle = playlistSubtitle(tracks.size, expectedNow, entry.complete),
                coverUrl = firstCover
                    ?: meta?.coverUrl?.takeUnless { isDefaultPlaylistCover(it) }
                    ?: seededCover
                    ?: it.coverUrl.takeIf { same },
                tracks = tracks,
                playlistId = entry.playlistId,
                expectedCount = expectedNow,
                complete = entry.complete || (expectedNow > 0 && tracks.size >= expectedNow),
                loading = false,
                refreshing = if (same) it.refreshing else false,
                canSubscribe = if (same) it.canSubscribe else false,
                subscribed = if (same) it.subscribed else false,
                subscribeBusy = if (same) it.subscribeBusy else false,
                isOwnedPlaylist = if (same) it.isOwnedPlaylist else false,
                isHeartPlaylist = if (same) it.isHeartPlaylist else false,
                creatorName = meta?.creatorName ?: it.creatorName.takeIf { same },
                creatorAvatarUrl = meta?.creatorAvatarUrl ?: it.creatorAvatarUrl.takeIf { same },
                playCount = meta?.playCount?.takeIf { c -> c > 0L }
                    ?: if (same) it.playCount else 0L,
                subscribedCount = if (pinMatches(entry.playlistId) && same) {
                    it.subscribedCount
                } else {
                    meta?.subscribedCount?.takeIf { c -> c > 0 }
                        ?: if (same) it.subscribedCount else 0
                },
            )
        }
        syncedCover?.let { playlistCollection.syncCover(entry.playlistId, it) }
        meta?.let { applySubscribeMeta(it) }
    }

    private fun applyLikedSnapshot(
        snap: LikedPlaylistRepository.Snapshot,
        fallbackTitle: String,
        seededCover: String? = null,
    ) {
        if (snap.playlistId > 0L &&
            openPlaylistId > 0L &&
            snap.playlistId != openPlaylistId &&
            !_list.value.isHeartPlaylist
        ) {
            return
        }
        val expected = snap.expectedCount.coerceAtLeast(snap.tracks.size)
        val order = snap.orderKey()
        _list.update {
            val same = it.playlistId == snap.playlistId || it.isHeartPlaylist
            val incoming = NcmLibraryParse.mergeLoadedInOrder(
                order,
                snap.tracks,
                if (same) it.tracks else emptyList(),
            )
            val tracks = if (same && incoming.size < it.tracks.size && !snap.complete) {
                it.tracks
            } else {
                incoming
            }
            val expectedNow = expected.coerceAtLeast(order.size).coerceAtLeast(tracks.size)
            CatalogListState(
                title = snap.title.ifBlank { fallbackTitle.ifBlank { it.title } },
                subtitle = playlistSubtitle(tracks.size, expectedNow, snap.complete),
                coverUrl = snap.coverUrl
                    ?: snap.tracks.firstOrNull()?.coverUrl
                    ?: seededCover
                    ?: it.coverUrl.takeIf { same },
                tracks = tracks,
                playlistId = snap.playlistId.takeIf { it > 0L } ?: openPlaylistId,
                expectedCount = expectedNow,
                complete = snap.complete || (expectedNow > 0 && tracks.size >= expectedNow),
                loading = false,
                refreshing = if (same) it.refreshing else false,
                canSubscribe = false,
                isHeartPlaylist = true,
                isOwnedPlaylist = true,
                creatorName = it.creatorName.takeIf { same },
                creatorAvatarUrl = it.creatorAvatarUrl.takeIf { same },
            )
        }
    }

    private fun openingPlaylistState(
        id: Long,
        title: String,
        coverUrl: String?,
        seedOwned: Boolean,
        seedHeart: Boolean,
        seedSubscribed: Boolean?,
    ): CatalogListState {
        val liked = likedPlaylistRepository.peek()
        if (seedHeart || (liked != null && liked.playlistId == id)) {
            return CatalogListState(
                title = title,
                coverUrl = coverUrl ?: liked?.coverUrl,
                loading = true,
                playlistId = id,
                complete = false,
                canSubscribe = false,
                isHeartPlaylist = true,
                isOwnedPlaylist = true,
            )
        }
        if (seedOwned) {
            return CatalogListState(
                title = title,
                coverUrl = coverUrl,
                loading = true,
                playlistId = id,
                complete = false,
                canSubscribe = false,
                isOwnedPlaylist = true,
            )
        }
        val known = playlistCollection.find(id)
        if (known != null) {
            val owned = known.isHeartPlaylist || known.isOwned
            return CatalogListState(
                title = title.ifBlank { known.name },
                coverUrl = coverUrl ?: known.coverUrl,
                loading = true,
                playlistId = id,
                complete = false,
                canSubscribe = !owned,
                subscribed = if (owned) false else known.isSubscribed,
                isHeartPlaylist = known.isHeartPlaylist,
                isOwnedPlaylist = known.isOwned,
            )
        }
        return CatalogListState(
            title = title,
            coverUrl = coverUrl,
            loading = true,
            playlistId = id,
            complete = false,
            canSubscribe = true,
            subscribed = seedSubscribed == true,
        )
    }

    private fun applySeedSubscribe(
        id: Long,
        seedOwned: Boolean,
        seedHeart: Boolean,
        seedSubscribed: Boolean?,
    ) {
        val liked = likedPlaylistRepository.peek()
        if (seedHeart || (liked != null && liked.playlistId == id)) {
            _list.update {
                it.copy(
                    playlistId = id,
                    canSubscribe = false,
                    subscribed = false,
                    isHeartPlaylist = true,
                    isOwnedPlaylist = true,
                )
            }
            return
        }
        if (seedOwned) {
            _list.update {
                it.copy(
                    playlistId = id,
                    canSubscribe = false,
                    subscribed = false,
                    isHeartPlaylist = false,
                    isOwnedPlaylist = true,
                )
            }
            return
        }
        val known = playlistCollection.find(id)
        if (known != null) {
            applyKnownPlaylistFlags(known)
            return
        }
        _list.update {
            it.copy(
                playlistId = id,
                canSubscribe = true,
                subscribed = seedSubscribed == true,
                isHeartPlaylist = false,
                isOwnedPlaylist = false,
            )
        }
    }

    private fun applyKnownPlaylistFlags(known: PlaylistSummary) {
        if (_list.value.playlistId != known.id) return
        if (known.isHeartPlaylist || known.isOwned) {
            if (pinMatches(known.id)) pinnedSubscribe = null
            _list.update {
                it.copy(
                    canSubscribe = false,
                    subscribed = false,
                    isHeartPlaylist = known.isHeartPlaylist,
                    isOwnedPlaylist = known.isOwned,
                )
            }
        } else {
            _list.update {
                it.copy(
                    canSubscribe = true,
                    subscribed = resolveSubscribed(known.id, known.isSubscribed),
                    isHeartPlaylist = false,
                    isOwnedPlaylist = false,
                )
            }
        }
    }

    private fun applySubscribeMeta(meta: PlaylistSubscribeMeta) {
        if (meta.id != openPlaylistId && meta.id != _list.value.playlistId) return
        applyPlaylistDisplayMeta(meta)
        val uid = selfUid()
        if (meta.isHeart(uid) || meta.isOwned(uid) || _list.value.isHeartPlaylist || _list.value.isOwnedPlaylist) {
            if (pinMatches(meta.id)) pinnedSubscribe = null
            _list.update {
                it.copy(
                    canSubscribe = false,
                    subscribed = false,
                    isHeartPlaylist = it.isHeartPlaylist || meta.isHeart(uid),
                    isOwnedPlaylist = it.isOwnedPlaylist || meta.isOwned(uid),
                )
            }
            return
        }
        _list.update {
            it.copy(
                canSubscribe = true,
                subscribed = resolveSubscribed(meta.id, meta.subscribed),
                isHeartPlaylist = false,
                isOwnedPlaylist = false,
            )
        }
    }

    private fun applyPlaylistDisplayMeta(meta: PlaylistSubscribeMeta) {
        _list.update {
            if (it.playlistId != meta.id) return@update it
            it.copy(
                title = meta.name.takeIf { n -> n.isNotBlank() } ?: it.title,
                coverUrl = it.tracks.firstOrNull()?.coverUrl?.takeUnless { c -> isDefaultPlaylistCover(c) }
                    ?: it.coverUrl.takeUnless { c -> isDefaultPlaylistCover(c) }
                    ?: meta.coverUrl
                    ?: it.coverUrl,
                creatorName = meta.creatorName ?: it.creatorName,
                creatorAvatarUrl = meta.creatorAvatarUrl ?: it.creatorAvatarUrl,
                playCount = meta.playCount.takeIf { c -> c > 0L } ?: it.playCount,
                subscribedCount = if (pinMatches(meta.id)) {
                    it.subscribedCount
                } else {
                    meta.subscribedCount.takeIf { c -> c > 0 } ?: it.subscribedCount
                },
                expectedCount = meta.trackCount.takeIf { c -> c > 0 } ?: it.expectedCount,
            )
        }
    }

    private fun refreshSubscribeMeta(playlistId: Long) {
        if (playlistId <= 0L) return
        if (_list.value.isHeartPlaylist) return
        subscribeMetaJob?.cancel()
        subscribeMetaJob = viewModelScope.launch {
            val liked = likedPlaylistRepository.peek()
            if (liked != null && liked.playlistId == playlistId) {
                _list.update {
                    it.copy(
                        canSubscribe = false,
                        isHeartPlaylist = true,
                        isOwnedPlaylist = true,
                    )
                }
                return@launch
            }
            playlistCollection.find(playlistId)?.let { applyKnownPlaylistFlags(it) }
            val cachedMeta = playlistTracksCache.peek(playlistId)
                ?.subscribeMeta
                ?.takeIf { it.id == playlistId }
            if (cachedMeta != null) {
                applySubscribeMeta(cachedMeta)
            }
            if (_list.value.isHeartPlaylist) return@launch
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            var meta = cachedMeta
            if (cookie.isNotBlank()) {
                val fetched = runCatching {
                    NcmLibraryParse.playlistMetaFromDetail(
                        userClient.playlistDetail(playlistId, cookie, limit = 1),
                    )
                }.getOrNull()?.takeIf { it.id == playlistId }
                if (openPlaylistId != playlistId) return@launch
                if (fetched != null) {
                    playlistTracksCache.attachSubscribeMeta(playlistId, fetched)
                    applySubscribeMeta(fetched)
                    meta = fetched
                }
            }
            if (_list.value.isHeartPlaylist) return@launch
            if (_list.value.isOwnedPlaylist) return@launch
            if (cookie.isBlank()) return@launch
            if (meta != null && playlistCollection.find(playlistId) != null) return@launch
            val subscribed = runCatching {
                NcmLibraryParse.subscribedFromDynamic(
                    userClient.playlistDetailDynamic(playlistId, cookie),
                )
            }.getOrNull()
            if (openPlaylistId != playlistId || subscribed == null) return@launch
            if (_list.value.isHeartPlaylist || _list.value.isOwnedPlaylist) return@launch
            _list.update {
                it.copy(canSubscribe = true, subscribed = resolveSubscribed(playlistId, subscribed))
            }
        }
    }

    private fun summaryForSubscribe(state: CatalogListState): PlaylistSummary = PlaylistSummary(
        id = state.playlistId,
        name = state.title,
        coverUrl = state.coverUrl,
        trackCount = state.expectedCount.coerceAtLeast(state.tracks.size),
        isHeartPlaylist = false,
        isOwned = false,
        isSubscribed = true,
        playCount = state.playCount,
    )

    private fun closePlaylist() {
        openPlaylistId = 0L
        artistSongsId = 0L
        pinnedSubscribe = null
    }

    private fun resolveSubscribed(playlistId: Long, remote: Boolean): Boolean {
        val pin = pinnedSubscribe
        if (pin == null || pin.album || pin.id != playlistId) return remote
        if (remote == pin.on) pinnedSubscribe = null
        return pin.on
    }

    private fun pinMatches(playlistId: Long): Boolean {
        val pin = pinnedSubscribe ?: return false
        return !pin.album && pin.id == playlistId
    }

    private fun pinMatchesAlbum(albumId: Long): Boolean {
        val pin = pinnedSubscribe ?: return false
        return pin.album && pin.id == albumId
    }

    private fun selfUid(): Long = playlistCollection.selfUserId.value

    private fun playlistSubtitle(loaded: Int, expected: Int, complete: Boolean): String {
        return if (!complete && expected > loaded) {
            "$loaded / $expected 首"
        } else {
            "$loaded 首"
        }
    }

    private fun applyDaily(tracks: List<TrackRow>, error: String?) {
        _list.update {
            CatalogListState(
                title = "每日推荐",
                subtitle = if (tracks.isEmpty()) null else "今日 ${tracks.size} 首",
                coverUrl = tracks.firstOrNull()?.coverUrl,
                tracks = tracks,
                error = error,
            )
        }
    }

    private fun cookieOrNull(): String? {
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isBlank()) {
            _list.update { it.copy(loading = false, refreshing = false, error = "请先登录") }
            return null
        }
        return cookie
    }

    private fun <T> List<T>.mergeById(extra: List<T>, idOf: (T) -> Long): List<T> {
        if (extra.isEmpty()) return this
        val seen = mapTo(mutableSetOf()) { idOf(it) }
        return this + extra.filter { idOf(it) !in seen }
    }
}
