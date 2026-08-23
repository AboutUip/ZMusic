package com.kite.zmusic.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.FollowedUser
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.UserListenHit
import com.kite.zmusic.data.UserProfileBrief
import com.kite.zmusic.data.UserRepository
import com.kite.zmusic.data.VipKind
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PlaylistPage = 40
private const val RelationPage = 30

enum class UserPlaylistShelf {
    Created,
    Collected,
}

enum class UserListenShelf {
    Week,
    All,
}

data class UserUiState(
    val id: Long,
    val name: String,
    val avatarUrl: String? = null,
    val backgroundUrl: String? = null,
    val signature: String? = null,
    val gender: Int = 0,
    val vipKind: VipKind = VipKind.None,
    val vipIconUrl: String? = null,
    val level: Int? = null,
    val listenSongs: Long? = null,
    val follows: Long? = null,
    val followeds: Long? = null,
    val medalCount: Int? = null,
    val artistId: Long = 0L,
    val followed: Boolean = false,
    val followBusy: Boolean = false,
    val isSelf: Boolean = false,
    val created: List<PlaylistSummary> = emptyList(),
    val collected: List<PlaylistSummary> = emptyList(),
    val playlistsMore: Boolean = false,
    val playlistsLoading: Boolean = false,
    val shelf: UserPlaylistShelf = UserPlaylistShelf.Created,
    val weekListens: List<UserListenHit> = emptyList(),
    val allListens: List<UserListenHit> = emptyList(),
    val listenShelf: UserListenShelf = UserListenShelf.Week,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val listens: List<UserListenHit>
        get() = if (listenShelf == UserListenShelf.Week) {
            weekListens.ifEmpty { allListens }
        } else {
            allListens.ifEmpty { weekListens }
        }
}

data class UserRelationsUi(
    val userId: Long,
    val title: String,
    val fans: Boolean,
    val people: List<FollowedUser> = emptyList(),
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class UserViewModel(
    private val userId: Long,
    seedName: String,
    seedAvatar: String?,
    private val sessionRepository: SessionRepository,
    private val islandNotices: IslandNoticeCenter,
    private val users: UserRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        UserUiState(
            id = userId,
            name = seedName.ifBlank { "用户" },
            avatarUrl = seedAvatar,
        ),
    )
    val ui: StateFlow<UserUiState> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var playlistJob: Job? = null
    private var allPlaylists: List<PlaylistSummary> = emptyList()

    fun load(force: Boolean = false) {
        if (!force && !_ui.value.loading && _ui.value.error == null) {
            val hasBody = _ui.value.created.isNotEmpty() ||
                _ui.value.collected.isNotEmpty() ||
                _ui.value.signature != null
            if (hasBody) return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetch(refresh = force && !_ui.value.loading) }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetch(refresh = true) }
    }

    fun setShelf(shelf: UserPlaylistShelf) {
        _ui.update { it.copy(shelf = shelf) }
    }

    fun setListenShelf(shelf: UserListenShelf) {
        _ui.update { it.copy(listenShelf = shelf) }
    }

    fun loadMorePlaylists() {
        val state = _ui.value
        if (!state.playlistsMore || state.playlistsLoading || playlistJob?.isActive == true) {
            return
        }
        playlistJob = viewModelScope.launch {
            _ui.update { it.copy(playlistsLoading = true) }
            try {
                val json = users.playlists(
                    userId,
                    cookie(),
                    limit = PlaylistPage,
                    offset = allPlaylists.size,
                )
                if (NcmJson.apiCode(json) != 200) {
                    _ui.update { it.copy(playlistsLoading = false, playlistsMore = false) }
                    return@launch
                }
                val page = NcmLibraryParse.playlistsFromUserPlaylist(json, userId)
                allPlaylists = allPlaylists.mergeById(page) { it.id }
                _ui.update { cur ->
                    val shelves = splitPlaylists(allPlaylists)
                    cur.copy(
                        created = shelves.first,
                        collected = shelves.second,
                        playlistsMore = json.optBoolean("more", page.size >= PlaylistPage) &&
                            page.isNotEmpty(),
                        playlistsLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(playlistsLoading = false) }
            }
        }
    }

    fun toggleFollow() {
        val state = _ui.value
        if (state.followBusy || state.id <= 0L || state.isSelf) return
        val session = sessionRepository.session.value
        val cookie = session?.cookie.orEmpty()
        if (cookie.isBlank() || session?.isGuest == true) {
            islandNotices.show("请先登录")
            return
        }
        val next = !state.followed
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    followBusy = true,
                    followed = next,
                    followeds = adjustCount(it.followeds, next),
                )
            }
            try {
                val json = users.follow(state.id, next, cookie)
                val code = NcmJson.apiCode(json)
                if (code == 301 || code == 302) {
                    revertFollow(next)
                    islandNotices.show("请先登录")
                    return@launch
                }
                if (code != 200) {
                    revertFollow(next)
                    islandNotices.show(
                        NcmJson.userFacingMessage(
                            json,
                            if (next) "关注失败" else "取消关注失败",
                        ),
                        state.avatarUrl,
                    )
                    return@launch
                }
                _ui.update { it.copy(followBusy = false, followed = next) }
                islandNotices.show(
                    if (next) "已关注" else "已取消关注",
                    state.avatarUrl,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                revertFollow(next)
                islandNotices.show(
                    NcmJson.userFacingThrowable(e, "操作失败"),
                    state.avatarUrl,
                )
            }
        }
    }

    private fun revertFollow(attempted: Boolean) {
        _ui.update {
            it.copy(
                followBusy = false,
                followed = !attempted,
                followeds = adjustCount(it.followeds, !attempted),
            )
        }
    }

    private suspend fun fetch(refresh: Boolean) {
        _ui.update {
            it.copy(
                loading = !refresh,
                refreshing = refresh,
                error = if (refresh) it.error else null,
            )
        }
        val cookie = cookie()
        try {
            coroutineScope {
                val detailDef = async { runCatching { users.detail(userId, cookie) } }
                val listsDef = async {
                    runCatching {
                        users.playlists(userId, cookie, limit = PlaylistPage, offset = 0)
                    }
                }
                val weekDef = async {
                    runCatching { users.record(userId, cookie, type = 1) }
                }
                val allDef = async {
                    runCatching { users.record(userId, cookie, type = 0) }
                }
                val medalDef = async { runCatching { users.medal(userId, cookie) } }
                val selfDef = async { runCatching { users.loginUserId(cookie) } }

                val detailJson = detailDef.await().getOrNull()
                val profile = detailJson?.let { NcmLibraryParse.userProfileFromDetail(it) }
                val selfId = selfDef.await().getOrDefault(0L)
                val listsJson = listsDef.await().getOrNull()
                val weekJson = weekDef.await().getOrNull()
                val allJson = allDef.await().getOrNull()
                val medalJson = medalDef.await().getOrNull()

                if (profile == null &&
                    (detailJson == null || NcmJson.apiCode(detailJson) != 200)
                ) {
                    val msg = detailJson?.let {
                        NcmJson.userFacingMessage(it, "暂时无法打开这位用户")
                    } ?: "暂时无法打开这位用户"
                    _ui.update {
                        it.copy(loading = false, refreshing = false, error = msg)
                    }
                    return@coroutineScope
                }

                allPlaylists = listsJson?.let {
                    NcmLibraryParse.playlistsFromUserPlaylist(it, userId)
                }.orEmpty()
                val shelves = splitPlaylists(allPlaylists)
                val more = listsJson?.optBoolean("more", allPlaylists.size >= PlaylistPage)
                    ?: false
                val medals = medalJson?.let { NcmLibraryParse.medalCountFromJson(it) }
                val applied = profile ?: UserProfileBrief(
                    userId = userId,
                    nickname = _ui.value.name,
                    avatarUrl = _ui.value.avatarUrl,
                    signature = null,
                    level = null,
                    listenSongs = null,
                )

                _ui.update {
                    it.copy(
                        name = applied.nickname.ifBlank { it.name },
                        avatarUrl = applied.avatarUrl ?: it.avatarUrl,
                        backgroundUrl = applied.backgroundUrl,
                        signature = applied.signature,
                        gender = applied.gender,
                        vipKind = applied.vipKind,
                        vipIconUrl = applied.vipIconUrl,
                        level = applied.level,
                        listenSongs = applied.listenSongs,
                        follows = applied.follows,
                        followeds = applied.followeds,
                        medalCount = medals ?: applied.medalCount,
                        artistId = applied.artistId,
                        followed = applied.followed,
                        isSelf = selfId > 0L && selfId == userId,
                        created = shelves.first,
                        collected = shelves.second,
                        playlistsMore = more && allPlaylists.isNotEmpty(),
                        playlistsLoading = false,
                        weekListens = weekJson?.let { NcmLibraryParse.listenRecord(it) }.orEmpty(),
                        allListens = allJson?.let { NcmLibraryParse.listenRecord(it) }.orEmpty(),
                        loading = false,
                        refreshing = false,
                        error = null,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = if (it.created.isEmpty() && it.collected.isEmpty()) {
                        NcmJson.userFacingThrowable(e, "暂时无法打开这位用户")
                    } else {
                        it.error
                    },
                )
            }
        }
    }

    private fun cookie(): String = sessionRepository.session.value?.cookie.orEmpty()
}

class UserRelationsViewModel(
    private val userId: Long,
    seedName: String,
    private val fans: Boolean,
    private val sessionRepository: SessionRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        UserRelationsUi(
            userId = userId,
            title = if (fans) {
                "${seedName.ifBlank { "用户" }}的粉丝"
            } else {
                "${seedName.ifBlank { "用户" }}的关注"
            },
            fans = fans,
        ),
    )
    val ui: StateFlow<UserRelationsUi> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var moreJob: Job? = null

    fun load(force: Boolean = false) {
        if (!force && !_ui.value.loading && _ui.value.error == null && _ui.value.people.isNotEmpty()) {
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetch(refresh = force && !_ui.value.loading) }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetch(refresh = true) }
    }

    fun loadMore() {
        val state = _ui.value
        if (!state.hasMore || state.loadingMore || moreJob?.isActive == true) return
        moreJob = viewModelScope.launch {
            _ui.update { it.copy(loadingMore = true) }
            try {
                val json = page(state.people.size)
                if (NcmJson.apiCode(json) != 200) {
                    _ui.update { it.copy(loadingMore = false, hasMore = false) }
                    return@launch
                }
                val (page, more) = NcmLibraryParse.followedUsers(json, RelationPage)
                _ui.update { cur ->
                    cur.copy(
                        people = cur.people.mergeById(page) { it.id },
                        hasMore = more && page.isNotEmpty(),
                        loadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(loadingMore = false) }
            }
        }
    }

    private suspend fun fetch(refresh: Boolean) {
        _ui.update {
            it.copy(
                loading = it.people.isEmpty() && !refresh,
                refreshing = refresh,
                error = if (refresh) it.error else null,
            )
        }
        try {
            val json = page(0)
            if (NcmJson.apiCode(json) != 200) {
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = NcmJson.userFacingMessage(
                            json,
                            if (fans) "暂时无法打开粉丝列表" else "暂时无法打开关注列表",
                        ),
                    )
                }
                return
            }
            val (page, more) = NcmLibraryParse.followedUsers(json, RelationPage)
            _ui.update {
                it.copy(
                    people = page,
                    hasMore = more && page.isNotEmpty(),
                    loading = false,
                    refreshing = false,
                    error = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = if (it.people.isEmpty()) {
                        NcmJson.userFacingThrowable(
                            e,
                            if (fans) "暂时无法打开粉丝列表" else "暂时无法打开关注列表",
                        )
                    } else {
                        it.error
                    },
                )
            }
        }
    }

    private suspend fun page(offset: Int) = if (fans) {
        users.followeds(userId, cookie(), limit = RelationPage, offset = offset)
    } else {
        users.follows(userId, cookie(), limit = RelationPage, offset = offset)
    }

    private fun cookie(): String = sessionRepository.session.value?.cookie.orEmpty()
}

class UserViewModelFactory(
    private val userId: Long,
    private val seedName: String,
    private val seedAvatar: String?,
    private val sessionRepository: SessionRepository,
    private val islandNotices: IslandNoticeCenter,
    private val users: UserRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            return UserViewModel(
                userId,
                seedName,
                seedAvatar,
                sessionRepository,
                islandNotices,
                users,
            ) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}

class UserRelationsViewModelFactory(
    private val userId: Long,
    private val seedName: String,
    private val fans: Boolean,
    private val sessionRepository: SessionRepository,
    private val users: UserRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserRelationsViewModel::class.java)) {
            return UserRelationsViewModel(
                userId,
                seedName,
                fans,
                sessionRepository,
                users,
            ) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}

private fun splitPlaylists(
    all: List<PlaylistSummary>,
): Pair<List<PlaylistSummary>, List<PlaylistSummary>> {
    val created = all.filter { pl ->
        pl.isOwned && !(pl.isHeartPlaylist && pl.trackCount <= 0)
    }
    val collected = all.filter { !it.isOwned }
    return created to collected
}

private fun adjustCount(current: Long?, up: Boolean): Long? {
    if (current == null) return if (up) 1L else null
    return (current + if (up) 1L else -1L).coerceAtLeast(0L)
}

private fun <T> List<T>.mergeById(extra: List<T>, idOf: (T) -> Long): List<T> {
    if (extra.isEmpty()) return this
    val seen = mapTo(mutableSetOf()) { idOf(it) }
    return this + extra.filter { idOf(it) !in seen }
}
