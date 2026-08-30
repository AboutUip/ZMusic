package com.kite.zmusic.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LibraryFeed(
    val loading: Boolean = false,
    val error: String? = null,
    val profile: UserProfileBrief? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val albums: List<CollectedAlbum> = emptyList(),
    val uid: Long = 0L,
) {
    val isWarm: Boolean get() = profile != null || playlists.isNotEmpty()
}

/**
 * 个人页资料 / 歌单：进主壳即拉，切到「个人」时直接有数据。
 */
class LibraryFeedRepository(
    private val sessions: SessionStore,
    private val userClient: NcmUserClient,
    private val auth: NcmAuthClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var loadJob: Job? = null

    private val _feed = MutableStateFlow(LibraryFeed())
    val feed: StateFlow<LibraryFeed> = _feed.asStateFlow()

    fun peek(): LibraryFeed = _feed.value

    fun ensureLoaded() {
        if (_feed.value.isWarm) return
        val job = loadJob
        if (job?.isActive == true) return
        loadJob = scope.launch {
            runCatching { refresh(force = false) }
        }
    }

    fun clear() {
        loadJob?.cancel()
        loadJob = null
        _feed.value = LibraryFeed()
    }

    suspend fun refresh(force: Boolean = false) {
        mutex.withLock {
            val session = sessions.session.value
            val cookie = session?.cookie.orEmpty()
            if (cookie.isBlank()) {
                _feed.value = LibraryFeed(error = "未登录")
                return
            }
            if (!force && _feed.value.isWarm) return
            _feed.update {
                it.copy(
                    loading = it.profile == null,
                    error = null,
                )
            }
            try {
                val status = auth.loginStatus(cookie)
                val uid = NcmJson.userIdFromLoginStatus(status) ?: 0L
                if (uid <= 0L) {
                    _feed.update {
                        it.copy(loading = false, error = "无法获取用户信息")
                    }
                    return
                }
                coroutineScope {
                    val detailDef = async {
                        runCatching { userClient.userDetail(uid, cookie) }.getOrNull()
                    }
                    val plDef = async {
                        runCatching { userClient.userPlaylist(uid, cookie, limit = 80, offset = 0) }.getOrNull()
                    }
                    val albumDef = async {
                        runCatching { userClient.albumSublist(cookie) }.getOrNull()
                    }
                    val profile = detailDef.await()?.let { NcmLibraryParse.profileFromUserDetail(it) }
                    val playlists = plDef.await()?.let {
                        NcmLibraryParse.playlistsFromUserPlaylist(it, uid)
                    }.orEmpty()
                    val albums = albumDef.await()?.let { NcmHomeParse.collectedAlbums(it) }.orEmpty()
                    _feed.value = LibraryFeed(
                        loading = false,
                        error = if (profile == null && playlists.isEmpty()) "暂时没有内容" else null,
                        profile = profile,
                        playlists = playlists,
                        albums = albums,
                        uid = uid,
                    )
                }
            } catch (e: CancellationException) {
                _feed.update { it.copy(loading = false) }
                throw e
            } catch (e: Exception) {
                _feed.update {
                    it.copy(
                        loading = false,
                        error = if (it.isWarm) it.error else NcmJson.userFacingThrowable(e, "加载失败"),
                    )
                }
            }
        }
    }

    suspend fun createPlaylist(name: String): Boolean {
        val cookie = sessions.session.value?.cookie.orEmpty()
        if (cookie.isBlank() || name.isBlank()) return false
        val json = runCatching { userClient.playlistCreate(name, cookie) }.getOrNull() ?: return false
        if (NcmJson.apiCode(json) != 200) return false
        refresh(force = true)
        return true
    }
}
