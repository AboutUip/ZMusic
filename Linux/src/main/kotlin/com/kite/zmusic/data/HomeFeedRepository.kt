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

data class HomeFeed(
    val banners: List<HomeBanner> = emptyList(),
    val dailySongs: List<TrackRow> = emptyList(),
    val playlists: List<RecommendPlaylistCard> = emptyList(),
    val dailyPlaylists: List<RecommendPlaylistCard> = emptyList(),
    val newSongs: List<TrackRow> = emptyList(),
    val mvs: List<RecommendMvCard> = emptyList(),
    val error: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
) {
    val isWarm: Boolean
        get() = banners.isNotEmpty() ||
            dailySongs.isNotEmpty() ||
            playlists.isNotEmpty() ||
            dailyPlaylists.isNotEmpty() ||
            newSongs.isNotEmpty() ||
            mvs.isNotEmpty()
}

/**
 * 首页推荐：进页缓存，切 Tab 不重复拉；下拉/点错误再 refresh。
 */
class HomeFeedRepository(
    private val sessions: SessionStore,
    private val userClient: NcmUserClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var loadJob: Job? = null

    private val _feed = MutableStateFlow(HomeFeed())
    val feed: StateFlow<HomeFeed> = _feed.asStateFlow()

    private var playlistPool = emptyList<RecommendPlaylistCard>()
    private var dailyPlPool = emptyList<RecommendPlaylistCard>()
    private var newSongPool = emptyList<TrackRow>()
    private var mvPool = emptyList<RecommendMvCard>()
    private var playlistCursor = 0
    private var dailyPlCursor = 0
    private var newSongCursor = 0
    private var mvCursor = 0
    private var mixPage = 0

    fun peek(): HomeFeed = _feed.value

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
        playlistPool = emptyList()
        dailyPlPool = emptyList()
        newSongPool = emptyList()
        mvPool = emptyList()
        playlistCursor = 0
        dailyPlCursor = 0
        newSongCursor = 0
        mvCursor = 0
        mixPage = 0
        _feed.value = HomeFeed()
    }

    suspend fun refresh(force: Boolean = true) {
        if (force && _feed.value.isWarm) {
            _feed.update { it.copy(refreshing = true, error = null) }
        }
        mutex.withLock {
            if (!force && _feed.value.isWarm) {
                _feed.update { it.copy(refreshing = false) }
                return
            }
            val cookie = sessions.session.value?.cookie.orEmpty()
            if (cookie.isBlank()) {
                _feed.update { it.copy(loading = false, refreshing = false, error = "请先登录") }
                return
            }
            val keep = _feed.value
            if (!keep.isWarm) {
                _feed.update { it.copy(loading = true, refreshing = false, error = null) }
            } else if (force) {
                _feed.update { it.copy(refreshing = true, error = null) }
            }
            try {
                coroutineScope {
                    val bannersDef = async {
                        runCatching { userClient.banner(cookie, type = 1) }.getOrNull()
                    }
                    val dailyDef = async {
                        runCatching { userClient.recommendSongs(cookie) }.getOrNull()
                    }
                    val plDef = async {
                        runCatching { userClient.personalizedPlaylists(cookie, limit = 30) }.getOrNull()
                    }
                    val dailyPlDef = async {
                        runCatching { userClient.recommendResource(cookie) }.getOrNull()
                    }
                    val newSongDef = async {
                        runCatching { userClient.personalizedNewsong(cookie, limit = 20) }.getOrNull()
                    }
                    val mvDef = async {
                        runCatching { userClient.personalizedMv(cookie) }.getOrNull()
                    }
                    val mvFirstDef = async {
                        runCatching { userClient.mvFirst(cookie, limit = 24) }.getOrNull()
                    }
                    val topPlDef = async {
                        runCatching {
                            userClient.topPlaylists(
                                cookie = cookie,
                                limit = 15,
                                offset = (mixPage * 15) % 90,
                                order = if (mixPage % 2 == 0) "hot" else "new",
                            )
                        }.getOrNull()
                    }
                    val banners = bannersDef.await()?.let { NcmHomeParse.banners(it) }.orEmpty()
                    val daily = dailyDef.await()?.let { NcmHomeParse.dailySongs(it) }.orEmpty()
                    val personalized = dropLiked(
                        plDef.await()?.let { NcmHomeParse.personalizedPlaylists(it) }.orEmpty(),
                    )
                    val mixed = dropLiked(
                        topPlDef.await()?.let { NcmHomeParse.topPlaylists(it) }.orEmpty(),
                    )
                    val dailyPlaylistsRaw = dropLiked(
                        dailyPlDef.await()?.let { NcmHomeParse.recommendResourcePlaylists(it) }.orEmpty(),
                    )
                    val newSongsRaw = newSongDef.await()?.let { NcmHomeParse.personalizedNewSongs(it) }.orEmpty()
                    val recommendedMvs = mvDef.await()?.let { NcmHomeParse.personalizedMvs(it) }.orEmpty()
                    val latestMvs = mvFirstDef.await()?.let { NcmHomeParse.latestMvs(it) }.orEmpty()

                    playlistPool = mergePool(playlistPool, personalized + mixed, POOL_PLAYLISTS) { it.id }
                    dailyPlPool = mergePool(dailyPlPool, dailyPlaylistsRaw, POOL_DAILY_PL) { it.id }
                    newSongPool = mergePool(newSongPool, newSongsRaw, POOL_SONGS) { it.id }
                    mvPool = mergePool(mvPool, recommendedMvs + latestMvs, POOL_MVS) { it.id }

                    val playlists = if (!force && keep.playlists.isEmpty()) {
                        playlistCursor = PLAYLIST_SHOW
                        personalized.take(PLAYLIST_SHOW).ifEmpty { playlistPool.take(PLAYLIST_SHOW) }
                    } else {
                        val next = nextBatch(
                            playlistPool,
                            PLAYLIST_SHOW,
                            playlistCursor,
                            keep.playlists.map { it.id }.toSet(),
                        ) { it.id }
                        playlistCursor = next.second
                        next.first
                    }
                    val dailyPlaylists = if (!force && keep.dailyPlaylists.isEmpty()) {
                        dailyPlCursor = DAILY_PL_SHOW
                        dailyPlaylistsRaw.take(DAILY_PL_SHOW)
                    } else {
                        val next = nextBatch(
                            dailyPlPool,
                            DAILY_PL_SHOW,
                            dailyPlCursor,
                            keep.dailyPlaylists.map { it.id }.toSet(),
                        ) { it.id }
                        dailyPlCursor = next.second
                        next.first
                    }
                    val newSongs = if (!force && keep.newSongs.isEmpty()) {
                        newSongCursor = NEW_SONG_SHOW
                        newSongsRaw.take(NEW_SONG_SHOW)
                    } else {
                        val next = nextBatch(
                            newSongPool,
                            NEW_SONG_SHOW,
                            newSongCursor,
                            keep.newSongs.map { it.id }.toSet(),
                        ) { it.id }
                        newSongCursor = next.second
                        next.first
                    }
                    val mvs = if (!force && keep.mvs.isEmpty()) {
                        mvCursor = MV_SHOW
                        val seenMv = mutableSetOf<Long>()
                        (recommendedMvs + latestMvs).filter { seenMv.add(it.id) }.take(MV_SHOW)
                    } else {
                        val next = nextBatch(
                            mvPool,
                            MV_SHOW,
                            mvCursor,
                            keep.mvs.map { it.id }.toSet(),
                        ) { it.id }
                        mvCursor = next.second
                        next.first
                    }
                    if (force) mixPage += 1
                    val failedAll = banners.isEmpty() &&
                        daily.isEmpty() &&
                        playlists.isEmpty() &&
                        dailyPlaylists.isEmpty() &&
                        newSongs.isEmpty() &&
                        mvs.isEmpty()
                    val err = if (failedAll) {
                        "暂时没有内容，点这里重试"
                    } else {
                        null
                    }
                    if (failedAll && keep.isWarm) {
                        _feed.update { it.copy(loading = false, refreshing = false, error = err) }
                        return@coroutineScope
                    }
                    _feed.value = HomeFeed(
                        banners = banners,
                        dailySongs = daily,
                        playlists = playlists,
                        dailyPlaylists = dailyPlaylists,
                        newSongs = newSongs,
                        mvs = mvs,
                        error = err,
                        loading = false,
                        refreshing = false,
                    )
                }
            } catch (e: CancellationException) {
                _feed.update { it.copy(loading = false, refreshing = false) }
                throw e
            } catch (e: Exception) {
                _feed.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = if (it.isWarm) it.error else NcmJson.userFacingThrowable(e, "加载失败"),
                    )
                }
            }
        }
    }

    companion object {
        private const val PLAYLIST_SHOW = 10
        private const val DAILY_PL_SHOW = 10
        private const val NEW_SONG_SHOW = 12
        private const val MV_SHOW = 6
        private const val POOL_PLAYLISTS = 90
        private const val POOL_DAILY_PL = 24
        private const val POOL_SONGS = 40
        private const val POOL_MVS = 40

        private fun dropLiked(cards: List<RecommendPlaylistCard>): List<RecommendPlaylistCard> =
            cards.filter { !isLikedMusicPlaylistName(it.name) }

        internal fun <T> mergePool(
            old: List<T>,
            incoming: List<T>,
            cap: Int,
            idOf: (T) -> Long,
        ): List<T> {
            if (incoming.isEmpty()) return old
            val seen = LinkedHashSet<Long>()
            val out = ArrayList<T>(minOf(cap, old.size + incoming.size))
            for (item in incoming + old) {
                val id = idOf(item)
                if (id <= 0L || !seen.add(id)) continue
                out.add(item)
                if (out.size >= cap) break
            }
            return out
        }

        internal fun <T> nextBatch(
            pool: List<T>,
            take: Int,
            cursor: Int,
            avoid: Set<Long>,
            idOf: (T) -> Long,
        ): Pair<List<T>, Int> {
            val unique = pool.filter { idOf(it) > 0L }.distinctBy(idOf)
            if (unique.isEmpty()) return emptyList<T>() to cursor
            if (unique.size <= take) return unique to cursor
            val fresh = unique.filter { idOf(it) !in avoid }
            val ordered = if (fresh.size >= take) {
                fresh
            } else {
                fresh + unique.filter { idOf(it) in avoid }
            }
            val start = ((cursor % ordered.size) + ordered.size) % ordered.size
            val out = ArrayList<T>(take)
            var i = 0
            while (out.size < take && i < ordered.size) {
                out.add(ordered[(start + i) % ordered.size])
                i++
            }
            return out to start + take
        }
    }
}
