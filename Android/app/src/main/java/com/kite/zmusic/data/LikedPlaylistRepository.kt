package com.kite.zmusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
 * - 首屏最多 [PlaylistTrackLoader.FIRST_BATCH] 首，后台补全并写盘
 * - like / 取消 like 立即改本地；完整列表补全后红心判定才对未收录曲返回 false
 */
class LikedPlaylistRepository(
    context: Context,
    private val sessionRepository: SessionRepository,
    private val authClient: NcmAuthClient = NcmAuthClient(),
    private val userClient: NcmUserClient = NcmUserClient(),
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
    ) {
        val likedIds: Set<Long> get() = tracks.map { it.id }.toSet()
        val trackCount: Int get() = if (complete) tracks.size else expectedCount.coerceAtLeast(tracks.size)
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioMutex = Mutex()
    private val cacheFile = File(appContext.filesDir, "zmusic_liked_playlist.json")

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    private val _checkedLikes = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

    private val syncScheduled = AtomicBoolean(false)
    private var syncJob: Job? = null
    private var prefetchJob: Job? = null
    private val fillJobs = ConcurrentHashMap<Long, Job>()
    /** 补全用的完整 id 序（内存） */
    private val pendingAllIds = ConcurrentHashMap<Long, List<Long>>()

    init {
        scope.launch(Dispatchers.IO) {
            loadFromDisk()?.let { snap ->
                _snapshot.value = snap
                if (!snap.complete && snap.playlistId > 0L) {
                    scheduleFill(snap.playlistId, snap.title, snap.coverUrl, snap.tracks)
                }
            }
        }
    }

    fun peek(): Snapshot? = _snapshot.value

    /**
     * 是否喜欢：
     * - 在缓存列表内 → true
     * - 列表已完整且不在内 → false
     * - 列表未完整且不在内 → 单曲检查缓存 / null
     */
    fun isLiked(trackId: Long): Boolean? {
        val snap = _snapshot.value
        if (snap != null) {
            if (snap.likedIds.contains(trackId)) return true
            if (snap.complete) return false
            return _checkedLikes.value[trackId]
        }
        return _checkedLikes.value[trackId]
    }

    fun recordLikeStatus(track: TrackRow, liked: Boolean) {
        _checkedLikes.value = _checkedLikes.value + (track.id to liked)
        val snap = _snapshot.value ?: return
        if (snap.likedIds.contains(track.id) == liked) return
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
        val existing = _snapshot.value
        if (existing != null && existing.playlistId > 0L && existing.tracks.isNotEmpty()) {
            if (!existing.complete) {
                scheduleFill(existing.playlistId, existing.title, existing.coverUrl, existing.tracks)
            }
            return
        }
        if (prefetchJob?.isActive == true) return
        prefetchJob = scope.launch {
            runCatching { refreshFromNetwork(force = false) }
                .onFailure { Log.w(TAG, "prefetch liked playlist failed", it) }
        }
    }

    suspend fun forceRefresh(): Snapshot? = refreshFromNetwork(force = true)

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
        val expected = when {
            current == null -> 1
            liked && current.likedIds.contains(track.id).not() ->
                (current.expectedCount + 1).coerceAtLeast(nextTracks.size)
            !liked && current.likedIds.contains(track.id) ->
                (current.expectedCount - 1).coerceAtLeast(nextTracks.size)
            else -> current.expectedCount.coerceAtLeast(nextTracks.size)
        }
        val next = Snapshot(
            playlistId = current?.playlistId ?: 0L,
            title = current?.title?.takeIf { it.isNotBlank() } ?: "我喜欢的音乐",
            coverUrl = current?.coverUrl ?: track.coverUrl,
            tracks = nextTracks,
            updatedAtMs = System.currentTimeMillis(),
            expectedCount = expected,
            // 本地改动后仍保持原完整标记；未齐时继续后台补
            complete = current?.complete == true,
        )
        _snapshot.value = next
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
                if (!cached.complete) {
                    scheduleFill(cached.playlistId, cached.title, cached.coverUrl, cached.tracks)
                }
                return cached
            }
        }
        return ioMutex.withLock {
            if (!force) {
                val cached = _snapshot.value
                if (cached != null && cached.tracks.isNotEmpty() && cached.playlistId > 0L) {
                    if (!cached.complete) {
                        scheduleFill(cached.playlistId, cached.title, cached.coverUrl, cached.tracks)
                    }
                    return@withLock cached
                }
            }
            val cookie = session.cookie
            val status = withContext(Dispatchers.IO) { authClient.loginStatus(cookie) }
            val uid = NcmJson.userIdFromLoginStatus(status) ?: return@withLock _snapshot.value
            val plJson = withContext(Dispatchers.IO) {
                userClient.userPlaylist(uid, cookie, limit = 80, offset = 0)
            }
            val playlists = NcmLibraryParse.playlistsFromUserPlaylist(plJson, uid)
            val heart = playlists.firstOrNull { it.isHeartPlaylist }
                ?: return@withLock _snapshot.value
            fillJobs[heart.id]?.cancel()
            fillJobs.remove(heart.id)
            val first = withContext(Dispatchers.IO) {
                PlaylistTrackLoader.loadFirstBatch(userClient, heart.id, cookie)
            }
            val snap = Snapshot(
                playlistId = heart.id,
                title = heart.name,
                coverUrl = heart.coverUrl,
                tracks = first.tracks,
                updatedAtMs = System.currentTimeMillis(),
                expectedCount = first.allIds.size.coerceAtLeast(first.tracks.size),
                complete = first.complete,
            )
            _snapshot.value = snap
            withContext(Dispatchers.IO) { persistToDisk(snap) }
            if (!first.complete) {
                pendingAllIds[heart.id] = first.allIds
                scheduleFill(heart.id, heart.name, heart.coverUrl, first.tracks, first.allIds)
            } else {
                pendingAllIds.remove(heart.id)
            }
            snap
        }
    }

    private fun scheduleFill(
        playlistId: Long,
        title: String,
        coverUrl: String?,
        already: List<TrackRow>,
        knownIds: List<Long>? = null,
    ) {
        if (playlistId <= 0L) return
        if (fillJobs[playlistId]?.isActive == true) return
        val session = sessionRepository.session.value ?: return
        if (session.isGuest) return
        fillJobs[playlistId] = scope.launch {
            try {
                runCatching {
                    val cookie = session.cookie
                    val allIds = knownIds
                        ?: pendingAllIds[playlistId]
                        ?: withContext(Dispatchers.IO) {
                            val detail = userClient.playlistDetail(
                                playlistId,
                                cookie,
                                limit = PlaylistTrackLoader.FIRST_BATCH,
                            )
                            NcmLibraryParse.trackIdsFromPlaylistDetail(detail)
                        }
                    if (allIds.isEmpty()) return@runCatching
                    pendingAllIds[playlistId] = allIds
                    PlaylistTrackLoader.loadRemaining(
                        userClient = userClient,
                        cookie = cookie,
                        allIds = allIds,
                        already = already,
                    ) { ordered ->
                        val snap = Snapshot(
                            playlistId = playlistId,
                            title = title,
                            coverUrl = coverUrl ?: _snapshot.value?.coverUrl,
                            tracks = ordered,
                            updatedAtMs = System.currentTimeMillis(),
                            expectedCount = allIds.size,
                            complete = ordered.size >= allIds.size,
                        )
                        _snapshot.value = snap
                        withContext(Dispatchers.IO) { persistToDisk(snap) }
                    }
                }.onFailure { Log.w(TAG, "liked playlist fill failed id=$playlistId", it) }
            } finally {
                fillJobs.remove(playlistId)
            }
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
                    val id = o.optLong("id", 0L)
                    if (id <= 0L) continue
                    add(
                        TrackRow(
                            id = id,
                            name = o.optString("name", "—"),
                            artists = o.optString("artists", "—"),
                            album = o.optString("album", "").takeIf { it.isNotBlank() },
                            durationMs = o.optLong("durationMs", 0L),
                            coverUrl = o.optString("coverUrl", "").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
            val expectedCount = root.optInt("expectedCount", tracks.size).coerceAtLeast(tracks.size)
            val complete = if (root.has("complete")) {
                root.optBoolean("complete", true)
            } else {
                // 旧缓存无字段：视为完整，避免误伤；下次 force 会重拉
                true
            }
            if (playlistId <= 0L && tracks.isEmpty()) null
            else Snapshot(playlistId, title, coverUrl, tracks, updatedAtMs, expectedCount, complete)
        }.getOrNull()
    }

    private fun persistToDisk(snap: Snapshot) {
        runCatching {
            val arr = JSONArray()
            snap.tracks.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("name", t.name)
                        .put("artists", t.artists)
                        .put("album", t.album ?: "")
                        .put("durationMs", t.durationMs)
                        .put("coverUrl", t.coverUrl ?: ""),
                )
            }
            val root = JSONObject()
                .put("playlistId", snap.playlistId)
                .put("title", snap.title)
                .put("coverUrl", snap.coverUrl ?: "")
                .put("updatedAtMs", snap.updatedAtMs)
                .put("expectedCount", snap.expectedCount)
                .put("complete", snap.complete)
                .put("tracks", arr)
            cacheFile.writeText(root.toString(), Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "persist liked playlist failed", it) }
    }

    companion object {
        private const val TAG = "LikedPlaylistRepo"
        private const val DEBOUNCE_MS = 3 * 60 * 1000L
    }
}
