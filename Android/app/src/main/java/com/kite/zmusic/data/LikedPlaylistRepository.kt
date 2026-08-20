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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 「我喜欢的音乐」缓存：
 * - 展示序用心形歌单 `/playlist/detail` 的 trackIds（与官方 App 一致）
 * - `/likelist` 只做红心集合（官方文档标明无序），不能当列表顺序
 * - 进页只拉前 [PlaylistTrackLoader.FIRST_BATCH] 首，下滑再按页补
 * - like / 取消 like 立即改本地，稍后用 `/likelist` + 歌单 trackIds 对齐
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

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    private val _checkedLikes = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

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
            val disk = loadFromDisk() ?: return@launch
            ioMutex.withLock {
                if (_snapshot.value != null) return@withLock
                restorePendingFromFile()
                _snapshot.value = disk
            }
        }
    }

    fun peek(): Snapshot? = _snapshot.value

    /**
     * 是否喜欢：
     * - 在 `/likelist` 或本地待同步喜欢里 → true
     * - 已有全量 id 且不在内 → false
     * - 尚未拉到全量 → 单曲检查缓存 / null
     */
    fun isLiked(trackId: Long): Boolean? {
        if (pendingAdds.containsKey(trackId)) return true
        if (pendingRemoves.contains(trackId)) return false
        val snap = _snapshot.value
        if (snap != null) {
            if (snap.likedIds.contains(trackId)) return true
            if (snap.allLikedIds.isNotEmpty() || snap.complete) return false
            return _checkedLikes.value[trackId]
        }
        return _checkedLikes.value[trackId]
    }

    fun recordLikeStatus(track: TrackRow, liked: Boolean) {
        if (liked && pendingRemoves.contains(track.id)) return
        if (!liked && pendingAdds.containsKey(track.id)) return
        _checkedLikes.value = _checkedLikes.value + (track.id to liked)
        val snap = _snapshot.value ?: run {
            if (liked) applyLocalLike(track, liked = true, scheduleSync = false)
            return
        }
        val inTracks = snap.tracks.any { it.id == track.id }
        if (liked && !inTracks) {
            applyLocalLike(track, liked = true, scheduleSync = false)
            return
        }
        if (snap.likedIds.contains(track.id) == liked && (liked == inTracks || !liked)) return
        applyLocalLike(track, liked = liked, scheduleSync = false)
    }

    fun recordLikeStatuses(tracks: List<TrackRow>, likedIds: Set<Long>) {
        if (tracks.isEmpty()) return
        val merge = tracks.associate { it.id to likedIds.contains(it.id) }
        _checkedLikes.value = _checkedLikes.value + merge
        if (_snapshot.value == null) return
        for (t in tracks) {
            val liked = likedIds.contains(t.id)
            val snap = _snapshot.value ?: return
            if (snap.likedIds.contains(t.id) != liked) {
                applyLocalLike(t, liked = liked, scheduleSync = false)
            }
        }
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
        _checkedLikes.value = _checkedLikes.value + (track.id to liked)
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
        scope.launch(Dispatchers.IO) {
            runCatching { if (cacheFile.exists()) cacheFile.delete() }
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
            _checkedLikes.value = _checkedLikes.value + mergedIds.associateWith { true }
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
        pendingAdds.clear()
        pendingRemoves.clear()
        val byId = tracks.associateBy { it.id }
        val addArr = root.optJSONArray("pendingAdds")
        if (addArr != null) {
            for (i in 0 until addArr.length()) {
                val o = addArr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                if (id <= 0L) continue
                pendingAdds[id] = NcmLibraryParse.trackFromCacheJson(o) ?: continue
            }
        }
        val addIds = root.optJSONArray("pendingAddIds")
        if (addIds != null) {
            for (i in 0 until addIds.length()) {
                val id = addIds.optLong(i, 0L)
                if (id <= 0L || pendingAdds.containsKey(id)) continue
                byId[id]?.let { pendingAdds[id] = it }
            }
        }
        val removeIds = root.optJSONArray("pendingRemoveIds")
        if (removeIds != null) {
            for (i in 0 until removeIds.length()) {
                val id = removeIds.optLong(i, 0L)
                if (id > 0L) pendingRemoves.add(id)
            }
        }
    }

    companion object {
        private const val TAG = "LikedPlaylistRepo"
        private const val DEBOUNCE_MS = 2_000L
    }
}
