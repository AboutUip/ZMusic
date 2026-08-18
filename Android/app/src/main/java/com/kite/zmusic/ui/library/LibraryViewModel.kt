package com.kite.zmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.CollectedAlbum
import com.kite.zmusic.data.LibraryHomeRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.isDefaultPlaylistCover
import com.kite.zmusic.data.PlaylistCollectionRepository
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.SubcountBrief
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.UserProfileBrief
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val isGuest: Boolean = false,
    val profile: UserProfileBrief? = null,
    val subcount: SubcountBrief? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val albums: List<CollectedAlbum> = emptyList(),
    val albumsTotal: Int = 0,
    val albumsHasMore: Boolean = false,
    val albumsLoading: Boolean = false,
    val albumsLoadingMore: Boolean = false,
    val albumsError: String? = null,
    val likedTrackCount: Int = 0,
    val sheet: LibrarySheet = LibrarySheet.Hidden,
    /** 当前详情是否为「我喜欢的音乐」（用于展示刷新按钮） */
    val sheetIsHeart: Boolean = false,
    val sheetRefreshing: Boolean = false,
)

sealed class LibrarySheet {
    data object Hidden : LibrarySheet()
    data class Loading(val id: Long, val title: String) : LibrarySheet()
    data class Ready(val id: Long, val title: String, val tracks: List<TrackRow>) : LibrarySheet()
    data class Failed(val id: Long, val title: String, val message: String) : LibrarySheet()
}

class LibraryViewModel(
    private val sessionRepository: SessionRepository,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val playlistTracksCache: PlaylistTracksCache,
    private val playlistCollection: PlaylistCollectionRepository,
    private val libraryHome: LibraryHomeRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LibraryUiState())
    val ui: StateFlow<LibraryUiState> = _ui.asStateFlow()

    private var sheetLoadJob: Job? = null
    private var openHeartPlaylistId: Long? = null

    init {
        viewModelScope.launch {
            libraryHome.snapshot.collect { snap ->
                _ui.update { state ->
                    state.copy(
                        loading = snap.loading && state.playlists.isEmpty(),
                        refreshing = snap.refreshing,
                        error = snap.error,
                        isGuest = snap.isGuest,
                        profile = snap.profile,
                        subcount = snap.subcount,
                        likedTrackCount = snap.likedTrackCount,
                    )
                }
            }
        }
        viewModelScope.launch {
            likedPlaylistRepository.snapshot.collectLatest { snap ->
                applyLikedSnapshot(snap)
            }
        }
        viewModelScope.launch {
            playlistCollection.playlists.collect { list ->
                val merged = LibraryHomeRepository.mergeHeartTrackCount(
                    list,
                    likedPlaylistRepository.peek(),
                ).map { pl -> pl.withLiveCover() }
                _ui.update { state ->
                    state.copy(playlists = merged)
                }
            }
        }
        viewModelScope.launch {
            libraryHome.albums.collect { snap ->
                _ui.update { state ->
                    state.copy(
                        albums = snap.albums,
                        albumsTotal = snap.total,
                        albumsHasMore = snap.hasMore,
                        albumsLoading = snap.loading,
                        albumsLoadingMore = snap.loadingMore,
                        albumsError = snap.error,
                    )
                }
            }
        }
        viewModelScope.launch {
            playlistTracksCache.revision.collect {
                _ui.update { state ->
                    var next = state
                    val cur = state.sheet
                    if (cur is LibrarySheet.Ready) {
                        val entry = playlistTracksCache.peek(cur.id)
                        if (entry != null &&
                            entry.tracks.isNotEmpty() &&
                            (entry.tracks.size >= cur.tracks.size || entry.complete)
                        ) {
                            next = next.copy(
                                sheet = cur.copy(
                                    title = entry.title.ifBlank { cur.title },
                                    tracks = if (entry.tracks.size >= cur.tracks.size) {
                                        entry.tracks
                                    } else {
                                        cur.tracks
                                    },
                                ),
                            )
                        }
                    }
                    val playlists = next.playlists.map { it.withLiveCover() }
                    next.copy(playlists = playlists)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            libraryHome.refresh(force = true)
        }
    }

    fun loadMoreAlbums() {
        viewModelScope.launch {
            libraryHome.loadMoreAlbums()
        }
    }

    suspend fun unsubscribeAlbum(album: CollectedAlbum): String =
        libraryHome.unsubscribeAlbum(album)

    fun openPlaylist(p: PlaylistSummary) {
        if (p.isHeartPlaylist) {
            openHeartPlaylist(p.id, p.name)
        } else {
            openNetworkPlaylist(p.id, p.name, isHeart = false)
        }
    }

    fun dismissSheet() {
        sheetLoadJob?.cancel()
        sheetLoadJob = null
        openHeartPlaylistId = null
        _ui.update { it.copy(sheet = LibrarySheet.Hidden, sheetIsHeart = false, sheetRefreshing = false) }
    }

    /** 从播放器「回到歌单」等场景：仅依赖 id + 标题即可拉取曲目 */
    fun openPlaylistFromId(playlistId: Long, title: String) {
        val heartId = likedPlaylistRepository.peek()?.playlistId
        val isHeart = heartId != null && heartId == playlistId ||
            _ui.value.playlists.any { it.id == playlistId && it.isHeartPlaylist }
        if (isHeart) {
            openHeartPlaylist(playlistId, title)
        } else {
            openNetworkPlaylist(playlistId, title, isHeart = false)
        }
    }

    /** 详情页刷新：强制更新当前歌单缓存。 */
    fun refreshOpenPlaylist() {
        val sheet = _ui.value.sheet
        val id: Long
        val title: String
        when (sheet) {
            is LibrarySheet.Ready -> {
                id = sheet.id
                title = sheet.title
            }
            is LibrarySheet.Failed -> {
                id = sheet.id
                title = sheet.title
            }
            is LibrarySheet.Loading -> {
                id = sheet.id
                title = sheet.title
            }
            LibrarySheet.Hidden -> return
        }
        if (_ui.value.sheetIsHeart || openHeartPlaylistId == id) {
            sheetLoadJob?.cancel()
            sheetLoadJob = viewModelScope.launch {
                _ui.update {
                    it.copy(
                        sheetRefreshing = true,
                        sheet = if (it.sheet is LibrarySheet.Ready) {
                            it.sheet
                        } else {
                            LibrarySheet.Loading(id, title)
                        },
                    )
                }
                try {
                    val snap = likedPlaylistRepository.forceRefresh()
                    if (!isActive) return@launch
                    if (snap == null) {
                        _ui.update {
                            it.copy(
                                sheetRefreshing = false,
                                sheet = LibrarySheet.Failed(id, title, "刷新失败"),
                            )
                        }
                        return@launch
                    }
                    openHeartPlaylistId = snap.playlistId
                    _ui.update {
                        it.copy(
                            sheetRefreshing = false,
                            sheetIsHeart = true,
                            sheet = LibrarySheet.Ready(snap.playlistId, snap.title, snap.tracks),
                            likedTrackCount = snap.trackCount,
                            playlists = LibraryHomeRepository.mergeHeartTrackCount(it.playlists, snap),
                        )
                    }
                } catch (e: CancellationException) {
                    return@launch
                } catch (e: Exception) {
                    if (!isActive) return@launch
                    _ui.update {
                        it.copy(
                            sheetRefreshing = false,
                            sheet = LibrarySheet.Failed(
                                id,
                                title,
                                NcmJson.userFacingThrowable(e, "刷新失败"),
                            ),
                        )
                    }
                }
            }
        } else {
            openCachedPlaylist(id, title, forceNetwork = true)
        }
    }

    private fun openHeartPlaylist(playlistId: Long, title: String) {
        sheetLoadJob?.cancel()
        openHeartPlaylistId = playlistId
        sheetLoadJob = viewModelScope.launch {
            val cached = likedPlaylistRepository.peek()
            if (cached != null && cached.tracks.isNotEmpty() &&
                (cached.playlistId == playlistId ||
                    (cached.playlistId == 0L && playlistId > 0L) ||
                    (playlistId == 0L && cached.playlistId > 0L))
            ) {
                openHeartPlaylistId = cached.playlistId.takeIf { it > 0L } ?: playlistId
                _ui.update {
                    it.copy(
                        sheetIsHeart = true,
                        sheetRefreshing = false,
                        sheet = LibrarySheet.Ready(
                            id = openHeartPlaylistId ?: playlistId,
                            title = cached.title.ifBlank { title },
                            tracks = NcmLibraryParse.tracksUntilIdGap(
                                cached.orderKey(),
                                cached.tracks,
                            ),
                        ),
                        likedTrackCount = cached.trackCount,
                        playlists = LibraryHomeRepository.mergeHeartTrackCount(it.playlists, cached),
                    )
                }
                return@launch
            }
            _ui.update {
                it.copy(
                    sheetIsHeart = true,
                    sheetRefreshing = false,
                    sheet = LibrarySheet.Loading(playlistId, title),
                )
            }
            try {
                val snap = likedPlaylistRepository.forceRefresh()
                if (!isActive) return@launch
                if (snap == null) {
                    _ui.update {
                        it.copy(
                            sheetRefreshing = false,
                            sheet = if (it.sheet is LibrarySheet.Ready) {
                                it.sheet
                            } else {
                                LibrarySheet.Failed(playlistId, title, "加载曲目失败")
                            },
                        )
                    }
                    return@launch
                }
                openHeartPlaylistId = snap.playlistId
                _ui.update {
                    it.copy(
                        sheetIsHeart = true,
                        sheetRefreshing = false,
                        sheet = LibrarySheet.Ready(
                            snap.playlistId,
                            snap.title,
                            NcmLibraryParse.tracksUntilIdGap(snap.orderKey(), snap.tracks),
                        ),
                        likedTrackCount = snap.trackCount,
                        playlists = LibraryHomeRepository.mergeHeartTrackCount(it.playlists, snap),
                    )
                }
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Exception) {
                if (!isActive) return@launch
                _ui.update {
                    it.copy(
                        sheet = LibrarySheet.Failed(
                            playlistId,
                            title,
                            NcmJson.userFacingThrowable(e, "加载曲目失败"),
                        ),
                    )
                }
            }
        }
    }

    private fun openNetworkPlaylist(playlistId: Long, title: String, isHeart: Boolean) {
        openCachedPlaylist(playlistId, title, forceNetwork = false, isHeart = isHeart)
    }

    /** 普通歌单：优先缓存；[forceNetwork] 为 true 时强制刷新。 */
    private fun openCachedPlaylist(
        playlistId: Long,
        title: String,
        forceNetwork: Boolean,
        isHeart: Boolean = false,
    ) {
        sheetLoadJob?.cancel()
        openHeartPlaylistId = null
        sheetLoadJob = viewModelScope.launch {
            val cookie = sessionRepository.session.value?.cookie ?: return@launch
            val cached = playlistTracksCache.peek(playlistId)
            if (cached != null && cached.tracks.isNotEmpty()) {
                _ui.update {
                    it.copy(
                        sheetIsHeart = isHeart,
                        sheetRefreshing = forceNetwork,
                        sheet = LibrarySheet.Ready(
                            playlistId,
                            cached.title.ifBlank { title },
                            cached.tracks,
                        ),
                    )
                }
                if (!forceNetwork) return@launch
            } else {
                _ui.update {
                    it.copy(
                        sheetIsHeart = isHeart,
                        sheetRefreshing = false,
                        sheet = LibrarySheet.Loading(playlistId, title),
                    )
                }
            }
            try {
                val entry = if (forceNetwork) {
                    playlistTracksCache.forceRefresh(playlistId, title, cookie)
                } else {
                    playlistTracksCache.getOrFetch(playlistId, title, cookie)
                }
                val keepCount = cached?.tracks?.size ?: 0
                val next = if (!entry.complete && keepCount > entry.tracks.size) {
                    playlistTracksCache.ensureLoadedThrough(playlistId, title, cookie, keepCount)
                } else {
                    entry
                }
                if (!isActive) return@launch
                _ui.update {
                    it.copy(
                        sheetRefreshing = false,
                        sheet = LibrarySheet.Ready(
                            next.playlistId,
                            next.title.ifBlank { title },
                            next.tracks,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Exception) {
                if (!isActive) return@launch
                _ui.update { state ->
                    val sheet = state.sheet
                    state.copy(
                        sheetRefreshing = false,
                        sheet = if (sheet is LibrarySheet.Ready) {
                            sheet
                        } else {
                            LibrarySheet.Failed(
                                playlistId,
                                title,
                                NcmJson.userFacingThrowable(e, "加载曲目失败"),
                            )
                        },
                    )
                }
            }
        }
    }

    private fun applyLikedSnapshot(snap: LikedPlaylistRepository.Snapshot?) {
        if (snap == null) return
        libraryHome.applyLikedTrackCount(snap.trackCount)
        _ui.update { state ->
            val sheet = state.sheet
            val viewingHeart = state.sheetIsHeart ||
                openHeartPlaylistId == snap.playlistId ||
                (sheet is LibrarySheet.Ready && sheet.id == snap.playlistId) ||
                (sheet is LibrarySheet.Loading && sheet.id == snap.playlistId)
            val nextSheet = if (viewingHeart && sheet !is LibrarySheet.Hidden) {
                LibrarySheet.Ready(
                    id = snap.playlistId.takeIf { it > 0L }
                        ?: (sheet as? LibrarySheet.Ready)?.id
                        ?: (sheet as? LibrarySheet.Loading)?.id
                        ?: 0L,
                    title = snap.title.ifBlank {
                        (sheet as? LibrarySheet.Ready)?.title
                            ?: (sheet as? LibrarySheet.Loading)?.title
                            ?: "我喜欢的音乐"
                    },
                    tracks = NcmLibraryParse.mergeLoadedInOrder(
                        snap.orderKey(),
                        snap.tracks,
                        (sheet as? LibrarySheet.Ready)?.tracks.orEmpty(),
                    ),
                )
            } else {
                sheet
            }
            state.copy(
                likedTrackCount = snap.trackCount,
                playlists = LibraryHomeRepository.mergeHeartTrackCount(state.playlists, snap)
                    .map { it.withLiveCover() },
                sheet = nextSheet,
                sheetIsHeart = if (viewingHeart) true else state.sheetIsHeart,
            )
        }
    }

    private fun PlaylistSummary.withLiveCover(): PlaylistSummary {
        val forced = playlistCollection.forcedCover(id)
        if (forced != null) return copy(coverUrl = forced)
        val first = playlistTracksCache.peek(id)?.tracks?.firstOrNull()?.coverUrl
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { isDefaultPlaylistCover(it) }
        if (first != null) return copy(coverUrl = first)
        return copy(coverUrl = resolvedCoverUrl())
    }
}
