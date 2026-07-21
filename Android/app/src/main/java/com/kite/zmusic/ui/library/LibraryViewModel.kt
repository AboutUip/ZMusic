package com.kite.zmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
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
    private val authClient: NcmAuthClient = NcmAuthClient(),
    private val userClient: NcmUserClient = NcmUserClient(),
) : ViewModel() {

    private val _ui = MutableStateFlow(LibraryUiState())
    val ui: StateFlow<LibraryUiState> = _ui.asStateFlow()

    private var sheetLoadJob: Job? = null
    private var openHeartPlaylistId: Long? = null

    init {
        viewModelScope.launch {
            likedPlaylistRepository.snapshot.collectLatest { snap ->
                applyLikedSnapshot(snap)
            }
        }
        viewModelScope.launch {
            playlistTracksCache.updates.collect { entry ->
                _ui.update { state ->
                    val sheet = state.sheet
                    if (sheet is LibrarySheet.Ready && sheet.id == entry.playlistId) {
                        state.copy(
                            sheet = sheet.copy(
                                title = entry.title.ifBlank { sheet.title },
                                tracks = entry.tracks,
                            ),
                        )
                    } else {
                        state
                    }
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val session = sessionRepository.session.value
            if (session == null) {
                likedPlaylistRepository.clear()
                playlistTracksCache.clear()
                _ui.update {
                    it.copy(
                        loading = false,
                        error = "未登录",
                        profile = null,
                        playlists = emptyList(),
                        likedTrackCount = 0,
                    )
                }
                return@launch
            }
            _ui.update {
                it.copy(
                    loading = it.playlists.isEmpty(),
                    refreshing = it.playlists.isNotEmpty(),
                    error = null,
                )
            }
            try {
                val status = authClient.loginStatus(session.cookie)
                val uid = NcmJson.userIdFromLoginStatus(status)
                if (uid == null) {
                    _ui.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = "无法获取用户信息：登录状态里缺少用户 ID，请重新登录或检查 API 返回格式",
                            isGuest = session.isGuest,
                        )
                    }
                    return@launch
                }
                val detailJson = userClient.userDetail(uid, session.cookie)
                var profile = NcmLibraryParse.userProfileFromDetail(detailJson)
                if (profile == null) {
                    profile = UserProfileBrief(
                        userId = uid,
                        nickname = session.displayLabel?.trim().orEmpty().ifBlank { "用户" },
                        avatarUrl = null,
                        signature = null,
                        level = null,
                        listenSongs = null,
                    )
                }
                val subJson = try {
                    userClient.userSubcount(session.cookie)
                } catch (_: Exception) {
                    null
                }
                val sub = subJson?.let { NcmLibraryParse.subcountFromJson(it) }
                val plJson = userClient.userPlaylist(uid, session.cookie, limit = 80, offset = 0)
                val playlists = NcmLibraryParse.playlistsFromUserPlaylist(plJson, uid)
                val likedSnap = likedPlaylistRepository.peek()
                val likeN = likedSnap?.trackCount
                    ?: run {
                        val likeJson = try {
                            userClient.likeList(uid, session.cookie)
                        } catch (_: Exception) {
                            null
                        }
                        likeJson?.let { NcmLibraryParse.likeIdsCount(it) } ?: 0
                    }
                val playlistsMerged = mergeHeartTrackCount(playlists, likedSnap)

                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = null,
                        isGuest = session.isGuest,
                        profile = profile,
                        subcount = sub,
                        playlists = playlistsMerged,
                        likedTrackCount = likeN,
                    )
                }

                // 首页刷新顺带确保喜欢歌单缓存（无缓存时后台补）
                if (!session.isGuest) {
                    likedPlaylistRepository.prefetchOnAppReady()
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "加载失败",
                    )
                }
            }
        }
    }

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
                            playlists = mergeHeartTrackCount(it.playlists, snap),
                        )
                    }
                } catch (e: CancellationException) {
                    return@launch
                } catch (e: Exception) {
                    if (!isActive) return@launch
                    _ui.update {
                        it.copy(
                            sheetRefreshing = false,
                            sheet = LibrarySheet.Failed(id, title, e.message ?: "刷新失败"),
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
                            tracks = cached.tracks,
                        ),
                        likedTrackCount = cached.trackCount,
                        playlists = mergeHeartTrackCount(it.playlists, cached),
                    )
                }
                // 未齐则依赖 snapshot 流 / prefetch 后台补全
                if (!cached.complete) {
                    likedPlaylistRepository.prefetchOnAppReady()
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
                            sheet = LibrarySheet.Failed(playlistId, title, "加载曲目失败"),
                        )
                    }
                    return@launch
                }
                openHeartPlaylistId = snap.playlistId
                _ui.update {
                    it.copy(
                        sheetIsHeart = true,
                        sheet = LibrarySheet.Ready(snap.playlistId, snap.title, snap.tracks),
                        likedTrackCount = snap.trackCount,
                        playlists = mergeHeartTrackCount(it.playlists, snap),
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
                            e.message ?: "加载曲目失败",
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
            if (!forceNetwork) {
                val cached = playlistTracksCache.peek(playlistId)
                if (cached != null && cached.tracks.isNotEmpty()) {
                    _ui.update {
                        it.copy(
                            sheetIsHeart = isHeart,
                            sheetRefreshing = false,
                            sheet = LibrarySheet.Ready(
                                playlistId,
                                cached.title.ifBlank { title },
                                cached.tracks,
                            ),
                        )
                    }
                    // 触发未齐补全（getOrFetch 内部也会 schedule）
                    if (!cached.complete) {
                        runCatching {
                            playlistTracksCache.getOrFetch(playlistId, title, cookie)
                        }
                    }
                    return@launch
                }
            }

            _ui.update {
                it.copy(
                    sheetIsHeart = isHeart,
                    sheetRefreshing = forceNetwork && it.sheet is LibrarySheet.Ready,
                    sheet = if (forceNetwork && it.sheet is LibrarySheet.Ready) {
                        it.sheet
                    } else {
                        LibrarySheet.Loading(playlistId, title)
                    },
                )
            }
            try {
                val entry = if (forceNetwork) {
                    playlistTracksCache.forceRefresh(playlistId, title, cookie)
                } else {
                    playlistTracksCache.getOrFetch(playlistId, title, cookie)
                }
                if (!isActive) return@launch
                _ui.update {
                    it.copy(
                        sheetRefreshing = false,
                        sheet = LibrarySheet.Ready(
                            entry.playlistId,
                            entry.title.ifBlank { title },
                            entry.tracks,
                        ),
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
                            playlistId,
                            title,
                            e.message ?: if (forceNetwork) "刷新失败" else "加载曲目失败",
                        ),
                    )
                }
            }
        }
    }

    private fun applyLikedSnapshot(snap: LikedPlaylistRepository.Snapshot?) {
        if (snap == null) return
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
                    tracks = snap.tracks,
                )
            } else {
                sheet
            }
            state.copy(
                likedTrackCount = snap.trackCount,
                playlists = mergeHeartTrackCount(state.playlists, snap),
                sheet = nextSheet,
                sheetIsHeart = if (viewingHeart) true else state.sheetIsHeart,
            )
        }
    }

    private fun mergeHeartTrackCount(
        playlists: List<PlaylistSummary>,
        snap: LikedPlaylistRepository.Snapshot?,
    ): List<PlaylistSummary> {
        if (snap == null) return playlists
        return playlists.map { pl ->
            if (pl.isHeartPlaylist || (snap.playlistId > 0L && pl.id == snap.playlistId)) {
                pl.copy(trackCount = snap.trackCount)
            } else {
                pl
            }
        }
    }
}
