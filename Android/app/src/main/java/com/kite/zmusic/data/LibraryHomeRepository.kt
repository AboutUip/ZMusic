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
import org.json.JSONObject

data class LibraryHomeSnapshot(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val isGuest: Boolean = false,
    val profile: UserProfileBrief? = null,
    val subcount: SubcountBrief? = null,
    val likedTrackCount: Int = 0,
) {
    val isWarm: Boolean get() = profile != null
}

/**
 * 个人页资料 / 歌单列表预加载：进主壳即拉，切到「个人」时直接有数据。
 */
class LibraryHomeRepository(
    private val sessionRepository: SessionRepository,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val playlistCollection: PlaylistCollectionRepository,
    private val albumCollection: AlbumCollectionRepository,
    private val authClient: NcmAuthClient,
    private val userClient: NcmUserClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var loadJob: Job? = null

    private val _snapshot = MutableStateFlow(LibraryHomeSnapshot())
    val snapshot: StateFlow<LibraryHomeSnapshot> = _snapshot.asStateFlow()
    val albums: StateFlow<AlbumCollectionSnapshot> get() = albumCollection.snapshot

    fun peek(): LibraryHomeSnapshot = _snapshot.value

    fun prefetchOnAppReady() {
        if (_snapshot.value.isWarm) return
        val job = loadJob
        if (job?.isActive == true) return
        loadJob = scope.launch {
            runCatching { refresh(force = false) }
        }
    }

    fun clear() {
        loadJob?.cancel()
        loadJob = null
        _snapshot.value = LibraryHomeSnapshot()
        playlistCollection.clear()
        albumCollection.clear()
    }

    suspend fun refresh(force: Boolean = false) {
        mutex.withLock {
            val session = sessionRepository.session.value
            if (session == null) {
                playlistCollection.clear()
                albumCollection.clear()
                _snapshot.value = LibraryHomeSnapshot(error = "未登录")
                return
            }
            if (!force && _snapshot.value.isWarm && playlistCollection.playlists.value.isNotEmpty()) {
                return
            }
            _snapshot.update {
                it.copy(
                    loading = it.profile == null,
                    refreshing = it.profile != null,
                    error = null,
                    isGuest = session.isGuest,
                )
            }
            try {
                val status = authClient.loginStatus(session.cookie)
                val uid = NcmJson.userIdFromLoginStatus(status)
                if (uid == null) {
                    _snapshot.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = "无法获取用户信息：登录状态里缺少用户 ID，请重新登录或检查 API 返回格式",
                            isGuest = session.isGuest,
                        )
                    }
                    return
                }
                playlistCollection.setSelfUserId(uid)
                val fetched = coroutineScope {
                    val detailDef = async {
                        runCatching { userClient.userDetail(uid, session.cookie) }.getOrNull()
                    }
                    val levelDef = async {
                        runCatching { userClient.userLevel(session.cookie) }.getOrNull()
                    }
                    val vipDef = async {
                        runCatching { userClient.vipInfo(session.cookie, uid) }.getOrNull()
                            ?: runCatching { userClient.vipInfoLegacy(session.cookie, uid) }.getOrNull()
                    }
                    val plDef = async {
                        userClient.userPlaylist(uid, session.cookie, limit = 80, offset = 0)
                    }
                    val subDef = async {
                        runCatching { userClient.userSubcount(session.cookie) }.getOrNull()
                    }
                    val albumsDef = async {
                        if (session.isGuest) {
                            null
                        } else {
                            runCatching {
                                userClient.albumSublist(session.cookie, limit = AlbumPage, offset = 0)
                            }.getOrNull()
                        }
                    }
                    HomeFetch(
                        detail = detailDef.await(),
                        level = levelDef.await(),
                        vip = vipDef.await(),
                        playlists = plDef.await(),
                        subcount = subDef.await(),
                        albums = albumsDef.await(),
                    )
                }
                var profile = fetched.detail?.let { NcmLibraryParse.userProfileFromDetail(it) }
                    ?: UserProfileBrief(
                        userId = uid,
                        nickname = session.displayLabel?.trim().orEmpty().ifBlank { "用户" },
                        avatarUrl = null,
                        signature = null,
                        level = null,
                        listenSongs = null,
                    )
                fetched.level?.let { profile = NcmLibraryParse.mergeLevelIntoProfile(profile, it) }
                fetched.vip?.let { vipJson ->
                    if (NcmJson.apiCode(vipJson) == 200) {
                        val vip = NcmLibraryParse.vipBriefFromInfo(vipJson)
                        profile = profile.copy(vipKind = vip.kind, vipIconUrl = vip.iconUrl)
                    }
                }
                val playlists = NcmLibraryParse.playlistsFromUserPlaylist(fetched.playlists, uid)
                val likedSnap = likedPlaylistRepository.peek()
                val playlistsMerged = mergeHeartTrackCount(playlists, likedSnap)
                playlistCollection.replaceAll(playlistsMerged)
                val subcount = fetched.subcount?.let { NcmLibraryParse.subcountFromJson(it) }
                applyAlbumPage(fetched.albums)
                subcount?.let { sc ->
                    profile = profile.copy(artistFollows = sc.subArtistCount.toLong().coerceAtLeast(0L))
                }
                _snapshot.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = null,
                        isGuest = session.isGuest,
                        profile = profile,
                        subcount = subcount,
                        likedTrackCount = likedSnap?.trackCount ?: _snapshot.value.likedTrackCount,
                    )
                }
                if (!session.isGuest) {
                    likedPlaylistRepository.prefetchOnAppReady()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snapshot.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = NcmJson.userFacingThrowable(e, "加载失败"),
                    )
                }
            }
        }
    }

    fun applyLikedTrackCount(count: Int) {
        if (count < 0) return
        _snapshot.update { it.copy(likedTrackCount = count) }
    }

    suspend fun unsubscribeAlbum(album: CollectedAlbum): String {
        val session = sessionRepository.session.value ?: return "请先登录"
        albumCollection.setSubscribed(album.id, false)
        return try {
            val json = userClient.albumSub(album.id, false, session.cookie)
            if (NcmJson.apiCode(json) != 200) {
                albumCollection.setSubscribed(album.id, true, album)
                NcmJson.userFacingMessage(json, "取消收藏失败")
            } else {
                "已取消收藏"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            albumCollection.setSubscribed(album.id, true, album)
            NcmJson.userFacingThrowable(e, "取消收藏失败")
        }
    }

    suspend fun loadMoreAlbums() {
        val session = sessionRepository.session.value ?: return
        val cur = albumCollection.peek()
        if (!cur.hasMore || cur.loadingMore || cur.loading) return
        albumCollection.markLoading(more = true)
        try {
            val json = userClient.albumSublist(
                session.cookie,
                limit = AlbumPage,
                offset = cur.albums.size,
            )
            val (page, total, more) = NcmHomeParse.collectedAlbumPage(json, AlbumPage)
            if (NcmJson.apiCode(json) != 200 && page.isEmpty()) {
                albumCollection.fail(
                    NcmJson.userFacingMessage(json, "专辑加载失败"),
                    more = true,
                )
                return
            }
            albumCollection.appendPage(page, total, more && page.isNotEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            albumCollection.fail(NcmJson.userFacingThrowable(e, "专辑加载失败"), more = true)
        }
    }

    private fun applyAlbumPage(json: JSONObject?) {
        if (json == null) {
            if (albumCollection.peek().albums.isEmpty()) {
                albumCollection.replacePage(emptyList(), 0, false)
            }
            return
        }
        if (NcmJson.apiCode(json) != 200) {
            albumCollection.fail(
                NcmJson.userFacingMessage(json, "专辑加载失败"),
                more = false,
            )
            return
        }
        val (albums, total, more) = NcmHomeParse.collectedAlbumPage(json, AlbumPage)
        albumCollection.replacePage(albums, total, more)
    }

    private data class HomeFetch(
        val detail: JSONObject?,
        val level: JSONObject?,
        val vip: JSONObject?,
        val playlists: JSONObject,
        val subcount: JSONObject?,
        val albums: JSONObject?,
    )

    companion object {
        private const val AlbumPage = 20

        fun mergeHeartTrackCount(
            playlists: List<PlaylistSummary>,
            snap: LikedPlaylistRepository.Snapshot?,
        ): List<PlaylistSummary> {
            if (snap == null) return playlists
            val heartId = snap.playlistId.takeIf { it > 0L }
                ?: playlists.firstOrNull { it.isHeartPlaylist && it.isOwned }?.id
                ?: return playlists
            return playlists.map { pl ->
                if (pl.id == heartId) pl.copy(trackCount = snap.trackCount) else pl
            }
        }
    }
}
