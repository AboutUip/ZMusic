package com.kite.zmusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.kite.zmusic.ui.notice.showIslandNotice

/**
 * 「我喜欢的音乐」缓存：
 * - 展示序用心形歌单 `/playlist/detail` 的 trackIds（与官方 App 一致）
 * - `/likelist` 只做红心集合（官方文档标明无序），不能当列表顺序
 * - 进页只拉前 [PlaylistTrackLoader.FIRST_BATCH] 首，下滑再按页补
 * - like / 取消 like 立即改本地，`/like` 后台确认（失败重试），单曲红心另有磁盘缓存
 */
class LikedPlaylistRepository(
    context: Context,
    private val sessionRepository: SessionRepository,
    private val authClient: NcmAuthClient,
    private val userClient: NcmUserClient,
) {
    data class Snapshot(
        val playlistId: Long,
        val title: String,
        val coverUrl: String?,
        val tracks: List<TrackRow>,
        val updatedAtMs: Long,
        /** 歌单完整曲目数（来自 trackIds）；未齐前可能大于 tracks.size */
        val expectedCount: Int = tracks.size,
        val complete: Boolean = true,
        /** `/likelist` 全量 id，只做红心判断，顺序无意义。 */
        val allLikedIds: List<Long> = emptyList(),
        /** 心形歌单 trackIds 展示序（新喜欢一般在前）；分页必须用它。 */
        val displayIds: List<Long> = emptyList(),
    ) {
        val likedIds: Set<Long>
            get() = allLikedIds.ifEmpty { displayIds.ifEmpty { tracks.map { it.id } } }.toSet()
        val trackCount: Int
            get() {
                val expected = displayIds.size.takeIf { it > 0 } ?: expectedCount
                return if (complete) tracks.size else expected.coerceAtLeast(tracks.size)
            }
        fun orderKey(): List<Long> = displayIds.ifEmpty { allLikedIds }
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioMutex = Mutex()
    private val cacheFile = File(appContext.filesDir, "zmusic_liked_playlist.json")
    private val statusFile = File(appContext.filesDir, "zmusic_liked_status.json")

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    private val _checkedLikes = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val likeStatus: StateFlow<Map<Long, Boolean>> = _checkedLikes.asStateFlow()
    private val likeEpoch = ConcurrentHashMap<Long, Int>()
    private val likeLocks = ConcurrentHashMap<Long, Mutex>()
    private val generation = AtomicInteger(0)
    private var statusPersistJob: Job? = null

    private val syncScheduled = AtomicBoolean(false)
    private val networkWarmed = AtomicBoolean(false)
    @Volatile private var lastLikeListOk = false
    private var syncJob: Job? = null
    private var prefetchJob: Job? = null
    private val fillJobs = ConcurrentHashMap<Long, Job>()
    /** 补全用的完整 id 序（内存） */
    private val pendingAllIds = ConcurrentHashMap<Long, List<Long>>()
    /** 本地刚喜欢、网易云 `/likelist` 还没跟上的曲目（进程 + 磁盘都保留）。 */
    private val pendingAdds = ConcurrentHashMap<Long, TrackRow>()
    /** 本地刚取消喜欢、`/likelist` 还没跟上的 id。 */
    private val pendingRemoves = ConcurrentHashMap.newKeySet<Long>()

    init {
        scope.launch(Dispatchers.IO) {
            val status = loadLikeStatusFromDisk()
            val disk = loadFromDisk()
            ioMutex.withLock {
                restorePendingFromFile()
                if (_snapshot.value == null && disk != null) {
                    _snapshot.value = disk
                }
                val positives = _snapshot.value?.likedIds?.associateWith { true }.orEmpty()
                val merged = LinkedHashMap<Long, Boolean>(positives.size + status.size + 8)
                positives.forEach { (id, liked) -> merged[id] = liked }
                status.forEach { (id, liked) -> merged[id] = liked }
                _checkedLikes.value.forEach { (id, liked) -> merged[id] = liked }
                while (merged.size > STATUS_MAX) {
                    val oldest = merged.keys.firstOrNull() ?: break
                    merged.remove(oldest)
                }
                _checkedLikes.value = merged
                persistLikeStatusSoon()
            }
        }
    }

    fun peek(): Snapshot? = _snapshot.value

    /**
     * 是否喜欢：待同步与单曲缓存优先；红心歌单只提供「在列表里 → true」。
     * 不在 `/likelist` 里不能当成 false，否则搜索/别人歌单会先闪未喜欢。
     */
    fun isLiked(trackId: Long): Boolean? {
        if (pendingAdds.containsKey(trackId)) return true
        if (pendingRemoves.contains(trackId)) return false
        val snap = _snapshot.value
        if (snap != null && snap.likedIds.contains(trackId)) return true
        return _checkedLikes.value[trackId]
    }

    fun recordLikeStatus(track: TrackRow, liked: Boolean) {
        if (track.id <= 0L) return
        if (liked && pendingRemoves.contains(track.id)) return
        if (!liked && pendingAdds.containsKey(track.id)) return
        mergeCheckedLikes(mapOf(track.id to liked))
    }

    fun recordLikeStatuses(tracks: List<TrackRow>, likedIds: Set<Long>) {
        if (tracks.isEmpty()) return
        val eligible = tracks.filter { t ->
            t.id > 0L &&
                !pendingAdds.containsKey(t.id) &&
                !pendingRemoves.contains(t.id)
        }
        if (eligible.isEmpty()) return
        mergeCheckedLikes(eligible.associate { it.id to likedIds.contains(it.id) })
    }

    /**
     * 对尚未缓存的曲目批量 `/song/like/check`，true / false 都写入磁盘。
     * 已有结论或本地待同步的跳过。
     */
    fun prefetchLikeStatuses(tracks: List<TrackRow>) {
        val session = sessionRepository.session.value ?: return
        if (session.isGuest) return
        val cookie = session.cookie
        if (cookie.isBlank()) return
        val need = tracks.filter { it.id > 0L && isLiked(it.id) == null }.distinctBy { it.id }
        if (need.isEmpty()) return
        val gen = generation.get()
        scope.launch(Dispatchers.IO) {
            need.chunked(LIKE_CHECK_BATCH).forEach { batch ->
                if (!isActive || generation.get() != gen) return@launch
                if (sessionRepository.session.value?.cookie != cookie) return@launch
                val still = batch.filter { isLiked(it.id) == null }
                if (still.isEmpty()) return@forEach
                runCatching {
                    val json = userClient.songLikeCheck(still.map { it.id }, cookie)
                    val likedIds = NcmLibraryParse.tryLikedIdsFromLikeCheck(json) ?: return@runCatching
                    if (generation.get() != gen) return@runCatching
                    if (sessionRepository.session.value?.cookie != cookie) return@runCatching
                    recordLikeStatuses(still, likedIds)
                }.onFailure { Log.w(TAG, "prefetch like status failed", it) }
            }
        }
    }

    /**
     * 点击后立刻 [applyLocalLike]，再交给仓库在独立协程里确认。
     * 离开播放页也不会取消重试；失败才回滚并灵动岛。
     */
    fun submitLike(track: TrackRow, liked: Boolean, cookie: String) {
        if (cookie.isBlank() || track.id <= 0L) return
        val expectedEpoch = likeEpoch[track.id] ?: 0
        val gen = generation.get()
        scope.launch(Dispatchers.IO) {
            val ok = pushLike(track, liked, cookie, expectedEpoch)
            if (ok || generation.get() != gen) return@launch
            if ((likeEpoch[track.id] ?: 0) != expectedEpoch) return@launch
            applyLocalLike(track, liked = !liked, scheduleSync = false)
            withContext(Dispatchers.Main.immediate) {
                appContext.showIslandNotice(
                    if (liked) "喜欢失败" else "取消喜欢失败",
                    track.coverUrl,
                )
            }
        }
    }

    /**
     * 后台确认 `/like`。调用方应先 [applyLocalLike]。
     * 最多尝试 [LIKE_ATTEMPTS] 次；中途用户再次点击则视为本轮成功（由新一轮负责）。
     */
    suspend fun pushLike(track: TrackRow, liked: Boolean, cookie: String): Boolean {
        val expectedEpoch = likeEpoch[track.id] ?: 0
        return pushLike(track, liked, cookie, expectedEpoch)
    }

    private suspend fun pushLike(
        track: TrackRow,
        liked: Boolean,
        cookie: String,
        expectedEpoch: Int,
    ): Boolean {
        if (cookie.isBlank() || track.id <= 0L) return false
        val lock = lockFor(track.id)
        return lock.withLock {
            if ((likeEpoch[track.id] ?: 0) != expectedEpoch) return@withLock true
            repeat(LIKE_ATTEMPTS) { attempt ->
                if ((likeEpoch[track.id] ?: 0) != expectedEpoch) return@withLock true
                if (sessionRepository.session.value?.cookie != cookie) return@withLock true
                val ok = runCatching {
                    NcmJson.apiCode(userClient.likeSong(track.id, liked, cookie)) == 200
                }.getOrDefault(false)
                if (ok) {
                    if ((likeEpoch[track.id] ?: 0) == expectedEpoch) {
                        mergeCheckedLikes(mapOf(track.id to liked))
                    }
                    return@withLock true
                }
                if (attempt < LIKE_ATTEMPTS - 1) delay(400L * (attempt + 1))
            }
            (likeEpoch[track.id] ?: 0) != expectedEpoch
        }
    }

    private fun lockFor(trackId: Long): Mutex {
        likeLocks[trackId]?.let { return it }
        val created = Mutex()
        return likeLocks.putIfAbsent(trackId, created) ?: created
    }

    fun prefetchOnAppReady() {
        val session = sessionRepository.session.value ?: return
        if (session.isGuest) return
        if (prefetchJob?.isActive == true) return
        prefetchJob = scope.launch {
            runCatching { refreshFromNetwork(force = true) }
                .onSuccess { snap ->
                    if (lastLikeListOk && snap != null && snap.playlistId > 0L) {
                        networkWarmed.set(true)
                    }
                }
                .onFailure { Log.w(TAG, "prefetch liked playlist failed", it) }
        }
    }

    suspend fun forceRefresh(): Snapshot? {
        val snap = refreshFromNetwork(force = true)
        if (lastLikeListOk && snap != null && snap.playlistId > 0L) networkWarmed.set(true)
        return snap
    }

    fun applyLocalLike(
        track: TrackRow,
        liked: Boolean,
        scheduleSync: Boolean = true,
    ): Snapshot? {
        if (scheduleSync) {
            likeEpoch.compute(track.id) { _, v -> (v ?: 0) + 1 }
        }
        mergeCheckedLikes(mapOf(track.id to liked), persist = false)
        statusPersistJob?.cancel()
        scope.launch(Dispatchers.IO) { persistLikeStatusFile() }
        val current = _snapshot.value
        if (current == null && !liked) {
            if (scheduleSync) scheduleDeferredSync()
            return null
        }
        val nextTracks = if (current == null) {
            listOf(track)
        } else {
            val without = current.tracks.filterNot { it.id == track.id }
            if (liked) listOf(track) + without else without
        }
        val baseIds = current?.allLikedIds?.ifEmpty { current.tracks.map { it.id } }.orEmpty()
        val nextIds = if (liked) {
            listOf(track.id) + baseIds.filterNot { it == track.id }
        } else {
            baseIds.filterNot { it == track.id }
        }.ifEmpty {
            if (liked) listOf(track.id) else emptyList()
        }
        val baseDisplay = current?.displayIds?.ifEmpty { current.tracks.map { it.id } }.orEmpty()
        val nextDisplay = if (liked) {
            listOf(track.id) + baseDisplay.filterNot { it == track.id }
        } else {
            baseDisplay.filterNot { it == track.id }
        }.ifEmpty {
            nextIds
        }
        val expected = nextDisplay.size.coerceAtLeast(nextIds.size).coerceAtLeast(nextTracks.size)
        rememberPendingLike(track, liked)
        val next = Snapshot(
            playlistId = current?.playlistId ?: 0L,
            title = current?.title?.takeIf { it.isNotBlank() } ?: "我喜欢的音乐",
            coverUrl = current?.coverUrl ?: track.coverUrl,
            tracks = nextTracks,
            updatedAtMs = System.currentTimeMillis(),
            expectedCount = expected,
            complete = false,
            allLikedIds = nextIds,
            displayIds = nextDisplay,
        )
        _snapshot.value = next
        if (next.playlistId > 0L && nextDisplay.isNotEmpty()) {
            pendingAllIds[next.playlistId] = nextDisplay
        }
        scope.launch(Dispatchers.IO) { persistToDisk(next) }
        if (scheduleSync) scheduleDeferredSync()
        return next
    }

    fun clear() {
        generation.incrementAndGet()
        syncJob?.cancel()
        syncJob = null
        syncScheduled.set(false)
        prefetchJob?.cancel()
        prefetchJob = null
        fillJobs.values.forEach { it.cancel() }
        fillJobs.clear()
        pendingAllIds.clear()
        pendingAdds.clear()
        pendingRemoves.clear()
        networkWarmed.set(false)
        lastLikeListOk = false
        _snapshot.value = null
        _checkedLikes.value = emptyMap()
        likeEpoch.clear()
        likeLocks.clear()
        statusPersistJob?.cancel()
        statusPersistJob = null
        scope.launch(Dispatchers.IO) {
            runCatching { if (cacheFile.exists()) cacheFile.delete() }
            runCatching { if (statusFile.exists()) statusFile.delete() }
        }
    }

    private fun scheduleDeferredSync() {
        if (!syncScheduled.compareAndSet(false, true)) return
        syncJob = scope.launch {
            try {
                delay(DEBOUNCE_MS)
                runCatching { refreshFromNetwork(force = true) }
                    .onSuccess { snap ->
                        if (lastLikeListOk && snap != null && snap.playlistId > 0L) {
                            networkWarmed.set(true)
                        }
                    }
                    .onFailure { Log.w(TAG, "deferred liked sync failed", it) }
            } finally {
                syncScheduled.set(false)
            }
        }
    }

    private suspend fun refreshFromNetwork(force: Boolean): Snapshot? {
        val session = sessionRepository.session.value ?: return _snapshot.value
        if (session.isGuest) return _snapshot.value
        if (!force) {
            val cached = _snapshot.value
            if (cached != null && cached.tracks.isNotEmpty() && cached.playlistId > 0L) {
                return cached
            }
        }
        return ioMutex.withLock {
            if (!force) {
                val cached = _snapshot.value
                if (cached != null && cached.tracks.isNotEmpty() && cached.playlistId > 0L) {
                    return@withLock cached
                }
            }
            val cookie = session.cookie
            val previous = _snapshot.value
            val status = withContext(Dispatchers.IO) { authClient.loginStatus(cookie) }
            val uid = NcmJson.userIdFromLoginStatus(status) ?: return@withLock previous
            val fetched = withContext(Dispatchers.IO) {
                coroutineScope {
                    val plDef = async {
                        userClient.userPlaylist(uid, cookie, limit = 80, offset = 0)
                    }
                    val likeDef = async { fetchLikeIds(uid, cookie) }
                    val playlists = NcmLibraryParse.playlistsFromUserPlaylist(plDef.await(), uid)
                    val heart = playlists.firstOrNull { it.isHeartPlaylist && it.isOwned }
                        ?: playlists.firstOrNull { it.isHeartPlaylist }
                    Pair(heart, likeDef.await())
                }
            }
            val heart = fetched.first
            val playlistId = heart?.id?.takeIf { it > 0L }
                ?: previous?.playlistId?.takeIf { it > 0L }
                ?: 0L
            val trackIds = if (playlistId > 0L) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        NcmLibraryParse.trackIdsFromPlaylistDetail(
                            userClient.playlistDetail(
                                playlistId,
                                cookie,
                                limit = PlaylistTrackLoader.FIRST_BATCH,
                            ),
                        )
                    }.getOrDefault(emptyList())
                }
            } else {
                emptyList()
            }
            val likeOrder = fetched.second
            if (likeOrder == null && trackIds.isEmpty()) {
                lastLikeListOk = false
                Log.w(TAG, "likelist unavailable, keep local liked snapshot")
                return@withLock previous
            }
            if (likeOrder == null) {
                lastLikeListOk = false
                Log.w(TAG, "likelist unordered/unavailable, use heart playlist trackIds")
            } else if (likeOrder.isEmpty()) {
                val localCount = previous?.allLikedIds?.size?.takeIf { it > 0 }
                    ?: previous?.tracks?.size
                    ?: 0
                if (localCount > 5) {
                    lastLikeListOk = false
                    Log.w(TAG, "likelist empty while local has songs, keep local liked snapshot")
                    return@withLock previous
                }
                lastLikeListOk = true
            } else {
                lastLikeListOk = true
            }
            val membership = when {
                likeOrder != null -> likeOrder
                trackIds.isNotEmpty() -> trackIds
                else -> previous?.allLikedIds.orEmpty()
            }
            if (playlistId > 0L) {
                fillJobs[playlistId]?.cancel()
                fillJobs.remove(playlistId)
            }
            reconcilePendingWithRemote(membership)
            val mergedIds = mergeLikedIds(membership)
            val displayIds = mergeDisplayIds(trackIds, membership)
            if (mergedIds.isEmpty() && displayIds.isEmpty()) {
                val empty = Snapshot(
                    playlistId = playlistId,
                    title = heart?.name ?: previous?.title ?: "我喜欢的音乐",
                    coverUrl = heart?.coverUrl ?: previous?.coverUrl,
                    tracks = emptyList(),
                    updatedAtMs = System.currentTimeMillis(),
                    expectedCount = 0,
                    complete = pendingAdds.isEmpty(),
                    allLikedIds = emptyList(),
                    displayIds = emptyList(),
                )
                _snapshot.value = empty
                withContext(Dispatchers.IO) { persistToDisk(empty) }
                if (playlistId > 0L) pendingAllIds.remove(playlistId)
                return@withLock empty
            }
            val orderedIds = displayIds.ifEmpty { mergedIds }
            val known = buildList {
                addAll(pendingAdds.values)
                previous?.tracks.orEmpty().forEach { add(it) }
            }.distinctBy { it.id }
            val want = known.size
                .coerceAtLeast(PlaylistTrackLoader.FIRST_BATCH)
                .coerceAtMost(orderedIds.size.coerceAtLeast(1))
            val loaded = withContext(Dispatchers.IO) {
                PlaylistTrackLoader.loadOrderedIds(
                    userClient = userClient,
                    cookie = cookie,
                    ids = orderedIds.take(want),
                    known = known,
                )
            }
            val tracks = NcmLibraryParse.mergeLoadedInOrder(
                orderedIds,
                loaded,
                previous?.tracks.orEmpty(),
            )
            val snap = Snapshot(
                playlistId = playlistId,
                title = heart?.name ?: previous?.title ?: "我喜欢的音乐",
                coverUrl = heart?.coverUrl ?: previous?.coverUrl,
                tracks = tracks,
                updatedAtMs = System.currentTimeMillis(),
                expectedCount = orderedIds.size.coerceAtLeast(tracks.size),
                complete = orderedIds.isEmpty() || tracks.size >= orderedIds.size,
                allLikedIds = mergedIds.ifEmpty { orderedIds },
                displayIds = orderedIds,
            )
            _snapshot.value = snap
            mergeCheckedLikes(mergedIds.associateWith { true })
            withContext(Dispatchers.IO) { persistToDisk(snap) }
            if (playlistId > 0L) {
                if (!snap.complete) {
                    pendingAllIds[playlistId] = snap.displayIds.ifEmpty { snap.allLikedIds }
                } else {
                    pendingAllIds.remove(playlistId)
                }
            }
            snap
        }
    }

    private suspend fun fetchLikeIds(uid: Long, cookie: String): List<Long>? {
        repeat(2) { attempt ->
            val json = runCatching { userClient.likeList(uid, cookie) }.getOrNull()
            val ids = json?.let { NcmLibraryParse.tryLikeIdsInOrder(it) }
            if (ids != null) return ids
            if (attempt == 0) delay(400)
        }
        return null
    }

    private fun rememberPendingLike(track: TrackRow, liked: Boolean) {
        if (liked) {
            pendingRemoves.remove(track.id)
            pendingAdds[track.id] = track
        } else {
            pendingAdds.remove(track.id)
            pendingRemoves.add(track.id)
        }
    }

    private fun reconcilePendingWithRemote(likeOrder: List<Long>) {
        val remote = likeOrder.toSet()
        pendingAdds.keys.toList().forEach { id ->
            if (id in remote) pendingAdds.remove(id)
        }
        pendingRemoves.toList().forEach { id ->
            if (id !in remote) pendingRemoves.remove(id)
        }
    }

    private fun mergeLikedIds(likeOrder: List<Long>): List<Long> {
        val removes = pendingRemoves.toSet()
        val ids = ArrayList<Long>(likeOrder.size + pendingAdds.size)
        pendingAdds.keys.forEach { id ->
            if (id !in removes) ids.add(id)
        }
        likeOrder.forEach { id ->
            if (id > 0L && id !in removes && id !in ids) ids.add(id)
        }
        return ids
    }

    /**
     * 展示序：本地刚喜欢 → 尚未写入心形歌单的 likelist 差额 → 歌单 trackIds。
     * `/likelist` 本身无序，不能整表拿来当列表。
     */
    private fun mergeDisplayIds(playlistTrackIds: List<Long>, likeIds: List<Long>): List<Long> {
        val removes = pendingRemoves.toSet()
        val pendingFront = ArrayList<Long>(pendingAdds.size)
        val seen = HashSet<Long>()
        pendingAdds.keys.forEach { id ->
            if (id !in removes && seen.add(id)) pendingFront.add(id)
        }
        if (playlistTrackIds.isEmpty()) {
            likeIds.forEach { id ->
                if (id > 0L && id !in removes && seen.add(id)) pendingFront.add(id)
            }
            return pendingFront
        }
        val fromPlaylist = playlistTrackIds.filter { id ->
            id > 0L && id !in removes && seen.add(id)
        }
        val extras = likeIds.filter { id ->
            id > 0L && id !in removes && seen.add(id)
        }
        // `/likelist` 无序。差额过大说明 trackIds 不完整，不能把无序 id 堆到最前。
        if (fromPlaylist.isNotEmpty() && extras.size > PlaylistTrackLoader.FIRST_BATCH) {
            return pendingFront + fromPlaylist
        }
        return pendingFront + extras + fromPlaylist
    }

    fun ensureLoadedThrough(minCount: Int) {
        val snap = _snapshot.value ?: return
        if (snap.playlistId <= 0L) return
        if (snap.complete || snap.tracks.size >= minCount) return
        if (fillJobs[snap.playlistId]?.isActive == true) return
        val session = sessionRepository.session.value ?: return
        if (session.isGuest) return
        val playlistId = snap.playlistId
        val title = snap.title
        val coverUrl = snap.coverUrl
        val already = snap.tracks
        fillJobs[playlistId] = scope.launch {
            try {
                ioMutex.withLock {
                    val live = _snapshot.value
                    if (live == null || live.playlistId != playlistId) return@withLock
                    if (live.complete || live.tracks.size >= minCount) return@withLock
                    val cookie = session.cookie
                    val displayIds = pendingAllIds[playlistId]
                        ?: live.displayIds.takeIf { it.isNotEmpty() }
                        ?: live.allLikedIds.takeIf { it.isNotEmpty() }
                    if (displayIds.isNullOrEmpty()) return@withLock
                    pendingAllIds[playlistId] = displayIds
                    val page = (minCount - live.tracks.size)
                        .coerceAtLeast(PlaylistTrackLoader.PAGE)
                    val want = (live.tracks.size + page).coerceAtMost(displayIds.size)
                    val ordered = PlaylistTrackLoader.loadOrderedIds(
                        userClient = userClient,
                        cookie = cookie,
                        ids = displayIds.take(want),
                        known = live.tracks.ifEmpty { already },
                    )
                    val next = Snapshot(
                        playlistId = playlistId,
                        title = title,
                        coverUrl = coverUrl ?: live.coverUrl,
                        tracks = NcmLibraryParse.tracksUntilIdGap(displayIds, ordered),
                        updatedAtMs = System.currentTimeMillis(),
                        expectedCount = displayIds.size.coerceAtLeast(ordered.size),
                        complete = ordered.size >= displayIds.size,
                        allLikedIds = live.allLikedIds.ifEmpty { displayIds },
                        displayIds = displayIds,
                    )
                    _snapshot.value = next
                    withContext(Dispatchers.IO) { persistToDisk(next) }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "liked playlist page failed id=$playlistId", t)
            } finally {
                fillJobs.remove(playlistId)
            }
        }
    }

    /** 播放队列已比喜欢列表更长时，把多出来的曲目写回快照。 */
    suspend fun absorbIfLonger(tracks: List<TrackRow>) {
        if (tracks.size <= 1) return
        ioMutex.withLock {
            val live = _snapshot.value ?: return@withLock
            if (tracks.size <= live.tracks.size) return@withLock
            val ids = live.displayIds.ifEmpty { live.allLikedIds }
            val merged = if (ids.isNotEmpty()) {
                NcmLibraryParse.mergeLoadedInOrder(ids, tracks, live.tracks)
            } else {
                NcmLibraryParse.mergeTrackRows(live.tracks, tracks)
            }
            if (merged.size <= live.tracks.size) return@withLock
            val expected = live.expectedCount.coerceAtLeast(ids.size).coerceAtLeast(merged.size)
            val next = live.copy(
                tracks = merged,
                expectedCount = expected,
                complete = when {
                    ids.isNotEmpty() -> merged.size >= ids.size
                    expected > 0 -> merged.size >= expected
                    else -> live.complete
                },
                updatedAtMs = System.currentTimeMillis(),
            )
            _snapshot.value = next
            withContext(Dispatchers.IO) { persistToDisk(next) }
        }
    }

    private fun loadFromDisk(): Snapshot? {
        if (!cacheFile.exists()) return null
        return runCatching {
            val root = JSONObject(cacheFile.readText(Charsets.UTF_8))
            val playlistId = root.optLong("playlistId", 0L)
            val title = root.optString("title", "我喜欢的音乐")
            val coverUrl = root.optString("coverUrl", "").takeIf { it.isNotBlank() }
            val updatedAtMs = root.optLong("updatedAtMs", 0L)
            val arr = root.optJSONArray("tracks") ?: JSONArray()
            val tracks = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    NcmLibraryParse.trackFromCacheJson(o)?.let { add(it) }
                }
            }
            val expectedCount = root.optInt("expectedCount", tracks.size).coerceAtLeast(tracks.size)
            val idArr = root.optJSONArray("allLikedIds")
            val allLikedIds = if (idArr != null && idArr.length() > 0) {
                buildList {
                    for (i in 0 until idArr.length()) {
                        val id = idArr.optLong(i, 0L)
                        if (id > 0L) add(id)
                    }
                }
            } else {
                tracks.map { it.id }
            }
            val displayArr = root.optJSONArray("displayIds")
            val displayIds = if (displayArr != null && displayArr.length() > 0) {
                buildList {
                    for (i in 0 until displayArr.length()) {
                        val id = displayArr.optLong(i, 0L)
                        if (id > 0L) add(id)
                    }
                }
            } else {
                emptyList()
            }
            val order = displayIds.ifEmpty { allLikedIds }
            val orderedTracks = NcmLibraryParse.tracksUntilIdGap(order, tracks)
            val complete = orderedTracks.size >= order.size && order.isNotEmpty()
            if (playlistId <= 0L && tracks.isEmpty()) null
            else Snapshot(
                playlistId,
                title,
                coverUrl,
                orderedTracks,
                updatedAtMs,
                expectedCount.coerceAtLeast(order.size),
                complete,
                allLikedIds,
                displayIds,
            )
        }.getOrNull()
    }

    private fun persistToDisk(snap: Snapshot) {
        val gen = generation.get()
        runCatching {
            val arr = JSONArray()
            snap.tracks.forEach { t ->
                arr.put(NcmLibraryParse.trackToCacheJson(t))
            }
            val root = JSONObject()
                .put("playlistId", snap.playlistId)
                .put("title", snap.title)
                .put("coverUrl", snap.coverUrl ?: "")
                .put("updatedAtMs", snap.updatedAtMs)
                .put("expectedCount", snap.expectedCount)
                .put("complete", snap.complete)
                .put("allLikedIds", JSONArray().also { arrIds ->
                    snap.allLikedIds.forEach { arrIds.put(it) }
                })
                .put("displayIds", JSONArray().also { arrIds ->
                    snap.displayIds.forEach { arrIds.put(it) }
                })
                .put("pendingAddIds", JSONArray().also { arrIds ->
                    pendingAdds.keys.forEach { arrIds.put(it) }
                })
                .put("pendingAdds", JSONArray().also { arrAdds ->
                    pendingAdds.values.forEach { t ->
                        arrAdds.put(NcmLibraryParse.trackToCacheJson(t))
                    }
                })
                .put("pendingRemoveIds", JSONArray().also { arrIds ->
                    pendingRemoves.forEach { arrIds.put(it) }
                })
                .put("tracks", arr)
            if (generation.get() != gen) return@runCatching
            cacheFile.writeText(root.toString(), Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "persist liked playlist failed", it) }
    }

    private fun restorePendingFromFile() {
        if (!cacheFile.exists()) return
        runCatching {
            val root = JSONObject(cacheFile.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("tracks") ?: JSONArray()
            val tracks = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    NcmLibraryParse.trackFromCacheJson(o)?.let { add(it) }
                }
            }
            restorePendingFromDisk(root, tracks)
        }
    }

    private fun restorePendingFromDisk(root: JSONObject, tracks: List<TrackRow>) {
        val byId = tracks.associateBy { it.id }
        val addArr = root.optJSONArray("pendingAdds")
        if (addArr != null) {
            for (i in 0 until addArr.length()) {
                val o = addArr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                if (id <= 0L) continue
                if (pendingAdds.containsKey(id) || pendingRemoves.contains(id)) continue
                pendingAdds[id] = NcmLibraryParse.trackFromCacheJson(o) ?: continue
            }
        }
        val addIds = root.optJSONArray("pendingAddIds")
        if (addIds != null) {
            for (i in 0 until addIds.length()) {
                val id = addIds.optLong(i, 0L)
                if (id <= 0L || pendingAdds.containsKey(id) || pendingRemoves.contains(id)) continue
                byId[id]?.let { pendingAdds[id] = it }
            }
        }
        val removeIds = root.optJSONArray("pendingRemoveIds")
        if (removeIds != null) {
            for (i in 0 until removeIds.length()) {
                val id = removeIds.optLong(i, 0L)
                if (id > 0L && !pendingAdds.containsKey(id)) pendingRemoves.add(id)
            }
        }
    }

    private fun mergeCheckedLikes(patch: Map<Long, Boolean>, persist: Boolean = true) {
        if (patch.isEmpty()) return
        val merged = LinkedHashMap<Long, Boolean>(_checkedLikes.value.size + patch.size)
        _checkedLikes.value.forEach { (id, liked) -> merged[id] = liked }
        patch.forEach { (id, liked) ->
            merged.remove(id)
            merged[id] = liked
        }
        while (merged.size > STATUS_MAX) {
            val oldest = merged.keys.firstOrNull() ?: break
            merged.remove(oldest)
        }
        _checkedLikes.value = merged
        if (persist) persistLikeStatusSoon()
    }

    private fun persistLikeStatusSoon() {
        statusPersistJob?.cancel()
        statusPersistJob = scope.launch(Dispatchers.IO) {
            delay(80)
            persistLikeStatusFile()
        }
    }

    private fun loadLikeStatusFromDisk(): Map<Long, Boolean> {
        if (!statusFile.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(statusFile.readText(Charsets.UTF_8))
            val items = root.optJSONObject("items") ?: return@runCatching emptyMap()
            buildMap {
                val keys = items.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = key.toLongOrNull() ?: continue
                    if (id > 0L) put(id, items.optBoolean(key))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun persistLikeStatusFile() {
        val gen = generation.get()
        runCatching {
            val items = JSONObject()
            _checkedLikes.value.forEach { (id, liked) ->
                items.put(id.toString(), liked)
            }
            if (generation.get() != gen) return@runCatching
            statusFile.writeText(
                JSONObject().put("v", 1).put("items", items).toString(),
                Charsets.UTF_8,
            )
        }.onFailure { Log.w(TAG, "persist like status failed", it) }
    }

    companion object {
        private const val TAG = "LikedPlaylistRepo"
        private const val DEBOUNCE_MS = 2_000L
        private const val LIKE_ATTEMPTS = 3
        private const val LIKE_CHECK_BATCH = 40
        private const val STATUS_MAX = 5_000
    }
}
