package com.kite.zmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.FollowedUser
import com.kite.zmusic.data.LikedArtist
import com.kite.zmusic.data.NcmArtistParse
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PageSize = 30
private const val SearchPage = 50

data class LikedArtistsUi(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val scanning: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val artists: List<LikedArtist> = emptyList(),
    val hits: List<LikedArtist> = emptyList(),
    val hasMore: Boolean = false,
    val usersLoading: Boolean = false,
    val usersRefreshing: Boolean = false,
    val usersLoadingMore: Boolean = false,
    val usersError: String? = null,
    val users: List<FollowedUser> = emptyList(),
    val userHits: List<FollowedUser> = emptyList(),
    val usersHasMore: Boolean = false,
)

class LikedArtistsViewModel(
    private val sessionRepository: SessionRepository,
    private val islandNotices: IslandNoticeCenter,
    private val userClient: NcmUserClient = NcmUserClient(),
    private val authClient: NcmAuthClient = NcmAuthClient(),
) : ViewModel() {
    private val _ui = MutableStateFlow(LikedArtistsUi())
    val ui: StateFlow<LikedArtistsUi> = _ui.asStateFlow()
    private var loadJob: Job? = null
    private var moreJob: Job? = null
    private var scanJob: Job? = null
    private var debounceJob: Job? = null
    private var usersJob: Job? = null
    private var usersMoreJob: Job? = null
    private var selfUid: Long = 0L
    private var searchingUsers: Boolean = false
    private val unfollowBusy = hashSetOf<Long>()

    fun load(force: Boolean = false) {
        if (!force && (loadJob?.isActive == true || _ui.value.artists.isNotEmpty())) return
        loadJob?.cancel()
        moreJob?.cancel()
        scanJob?.cancel()
        loadJob = viewModelScope.launch { fetchFirst() }
    }

    fun refresh() = load(force = true)

    fun loadUsers(force: Boolean = false) {
        if (!force && (usersJob?.isActive == true || _ui.value.users.isNotEmpty())) return
        usersJob?.cancel()
        usersMoreJob?.cancel()
        usersJob = viewModelScope.launch { fetchUsersFirst() }
    }

    fun refreshUsers() = loadUsers(force = true)

    fun loadMoreUsers() {
        val cur = _ui.value
        if (!cur.usersHasMore || cur.usersLoading || cur.usersRefreshing ||
            cur.usersLoadingMore || cur.users.isEmpty()
        ) {
            return
        }
        if (usersMoreJob?.isActive == true) return
        usersMoreJob = viewModelScope.launch { fetchUsersMore() }
    }

    fun onQueryChange(value: String, searchUsers: Boolean = false) {
        searchingUsers = searchUsers
        _ui.update { it.copy(query = value, error = null, usersError = null) }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(120)
            refilter()
            startScanIfNeeded()
        }
    }

    fun clearQuery() {
        debounceJob?.cancel()
        scanJob?.cancel()
        _ui.update {
            it.copy(
                query = "",
                hits = emptyList(),
                userHits = emptyList(),
                scanning = false,
                error = null,
            )
        }
    }

    fun resetQuery() = clearQuery()

    fun loadMore() {
        val cur = _ui.value
        if (!cur.hasMore || cur.loading || cur.refreshing || cur.loadingMore || cur.artists.isEmpty()) {
            return
        }
        if (cur.query.isNotBlank()) return
        if (moreJob?.isActive == true) return
        moreJob = viewModelScope.launch { fetchMore() }
    }

    fun unfollow(artist: LikedArtist) {
        if (artist.id <= 0L || !unfollowBusy.add(artist.id)) return
        val session = sessionRepository.session.value
        val cookie = session?.cookie.orEmpty()
        if (cookie.isBlank() || session?.isGuest == true) {
            unfollowBusy.remove(artist.id)
            islandNotices.show("请先登录")
            return
        }
        val snapshot = _ui.value.artists
        _ui.update { state ->
            val artists = state.artists.filterNot { it.id == artist.id }
            state.copy(
                artists = artists,
                hits = filterArtists(artists, state.query),
            )
        }
        viewModelScope.launch {
            try {
                val json = userClient.artistSub(artist.id, follow = false, cookie)
                val code = NcmJson.apiCode(json)
                if (code == 301 || code == 302) {
                    restore(snapshot)
                    islandNotices.show("请先登录")
                    return@launch
                }
                if (code != 200) {
                    restore(snapshot)
                    islandNotices.show(
                        NcmJson.userFacingMessage(json, "取消收藏失败"),
                        artist.coverUrl,
                    )
                    return@launch
                }
                islandNotices.show("已取消收藏", artist.coverUrl)
            } catch (e: CancellationException) {
                restore(snapshot)
                throw e
            } catch (e: Exception) {
                restore(snapshot)
                islandNotices.show(NcmJson.userFacingThrowable(e, "取消收藏失败"), artist.coverUrl)
            } finally {
                unfollowBusy.remove(artist.id)
            }
        }
    }

    private suspend fun fetchFirst() {
        val session = sessionRepository.session.value
        if (session == null) {
            _ui.update { it.copy(loading = false, refreshing = false, error = "未登录") }
            return
        }
        if (session.isGuest) {
            _ui.update {
                it.copy(loading = false, refreshing = false, error = "登录后查看喜欢的歌手")
            }
            return
        }
        _ui.update {
            it.copy(
                loading = it.artists.isEmpty(),
                refreshing = it.artists.isNotEmpty(),
                error = null,
            )
        }
        try {
            val json = userClient.artistSublist(session.cookie, limit = PageSize, offset = 0)
            val (list, more) = NcmArtistParse.likedArtists(json, PageSize)
            _ui.update { state ->
                state.copy(
                    loading = false,
                    refreshing = false,
                    artists = list,
                    hits = filterArtists(list, state.query),
                    hasMore = more,
                    error = null,
                )
            }
            startScanIfNeeded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = if (it.artists.isEmpty()) {
                        NcmJson.userFacingThrowable(e, "加载失败")
                    } else {
                        it.error
                    },
                )
            }
        }
    }

    private suspend fun fetchUsersFirst() {
        val session = sessionRepository.session.value
        if (session == null) {
            _ui.update { it.copy(usersLoading = false, usersRefreshing = false, usersError = "未登录") }
            return
        }
        if (session.isGuest) {
            _ui.update {
                it.copy(
                    usersLoading = false,
                    usersRefreshing = false,
                    usersError = "登录后查看关注的用户",
                )
            }
            return
        }
        _ui.update {
            it.copy(
                usersLoading = it.users.isEmpty(),
                usersRefreshing = it.users.isNotEmpty(),
                usersError = null,
            )
        }
        try {
            val uid = resolveUid(session.cookie)
            if (uid <= 0L) {
                _ui.update {
                    it.copy(
                        usersLoading = false,
                        usersRefreshing = false,
                        usersError = "无法获取用户信息",
                    )
                }
                return
            }
            val json = userClient.userFollows(uid, session.cookie, limit = PageSize, offset = 0)
            val (list, more) = NcmLibraryParse.followedUsers(json, PageSize)
            _ui.update { state ->
                state.copy(
                    usersLoading = false,
                    usersRefreshing = false,
                    users = list,
                    userHits = filterUsers(list, state.query),
                    usersHasMore = more,
                    usersError = null,
                )
            }
            startScanIfNeeded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(
                    usersLoading = false,
                    usersRefreshing = false,
                    usersError = if (it.users.isEmpty()) {
                        NcmJson.userFacingThrowable(e, "加载失败")
                    } else {
                        it.usersError
                    },
                )
            }
        }
    }

    private suspend fun fetchUsersMore(limit: Int = PageSize, showLoading: Boolean = true) {
        val session = sessionRepository.session.value ?: return
        val uid = selfUid.takeIf { it > 0L } ?: resolveUid(session.cookie)
        if (uid <= 0L) return
        if (showLoading) _ui.update { it.copy(usersLoadingMore = true) }
        try {
            val json = userClient.userFollows(
                uid,
                session.cookie,
                limit = limit,
                offset = _ui.value.users.size,
            )
            val (page, more) = NcmLibraryParse.followedUsers(json, limit)
            _ui.update { state ->
                val merged = (state.users + page).distinctBy { it.id }
                state.copy(
                    usersLoadingMore = false,
                    users = merged,
                    userHits = filterUsers(merged, state.query),
                    usersHasMore = more && page.isNotEmpty(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _ui.update { it.copy(usersLoadingMore = false, usersHasMore = false) }
        }
    }

    private suspend fun resolveUid(cookie: String): Long {
        if (selfUid > 0L) return selfUid
        val uid = NcmJson.userIdFromLoginStatus(authClient.loginStatus(cookie)) ?: 0L
        if (uid > 0L) selfUid = uid
        return uid
    }

    private suspend fun fetchMore(limit: Int = PageSize, showLoading: Boolean = true) {
        val session = sessionRepository.session.value ?: return
        if (showLoading) _ui.update { it.copy(loadingMore = true) }
        try {
            val json = userClient.artistSublist(
                session.cookie,
                limit = limit,
                offset = _ui.value.artists.size,
            )
            val (page, more) = NcmArtistParse.likedArtists(json, limit)
            _ui.update { state ->
                val merged = (state.artists + page).distinctBy { it.id }
                state.copy(
                    loadingMore = false,
                    artists = merged,
                    hits = filterArtists(merged, state.query),
                    hasMore = more && page.isNotEmpty(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _ui.update { it.copy(loadingMore = false, hasMore = false) }
        }
    }

    private fun refilter() {
        _ui.update { state ->
            state.copy(
                hits = filterArtists(state.artists, state.query),
                userHits = filterUsers(state.users, state.query),
            )
        }
    }

    private fun startScanIfNeeded() {
        val state = _ui.value
        val users = searchingUsers
        val more = if (users) state.usersHasMore else state.hasMore
        if (state.query.isBlank() || !more) {
            scanJob?.cancel()
            _ui.update { it.copy(scanning = false) }
            return
        }
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _ui.update { it.copy(scanning = true) }
            try {
                while (_ui.value.query.trim().isNotEmpty()) {
                    val cur = _ui.value
                    val hasMore = if (users) cur.usersHasMore else cur.hasMore
                    if (!hasMore) break
                    if (users) {
                        if (usersMoreJob?.isActive == true) usersMoreJob?.join()
                        else fetchUsersMore(limit = SearchPage, showLoading = false)
                    } else {
                        if (moreJob?.isActive == true) moreJob?.join()
                        else fetchMore(limit = SearchPage, showLoading = false)
                    }
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

    private fun restore(snapshot: List<LikedArtist>) {
        _ui.update { state ->
            state.copy(
                artists = snapshot,
                hits = filterArtists(snapshot, state.query),
            )
        }
    }
}

private fun filterArtists(artists: List<LikedArtist>, query: String): List<LikedArtist> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return artists.filter { it.name.contains(needle, ignoreCase = true) }
}

private fun filterUsers(users: List<FollowedUser>, query: String): List<FollowedUser> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return users.filter { user ->
        user.name.contains(needle, ignoreCase = true) ||
            (!user.signature.isNullOrBlank() && user.signature.contains(needle, ignoreCase = true))
    }
}

class LikedArtistsViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val islandNotices: IslandNoticeCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LikedArtistsViewModel::class.java)) {
            return LikedArtistsViewModel(sessionRepository, islandNotices) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
