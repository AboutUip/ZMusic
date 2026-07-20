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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 「我喜欢的音乐」缓存：
 * - 启动后后台预拉取；进入歌单优先读缓存
 * - like / 取消 like 立即改本地缓存
 * - 首次本地变更后启动 3 分钟一次性后台同步；期间不再重置 / 新建计时器
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
    ) {
        val likedIds: Set<Long> get() = tracks.map { it.id }.toSet()
        val trackCount: Int get() = tracks.size
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioMutex = Mutex()
    private val cacheFile = File(appContext.filesDir, "zmusic_liked_playlist.json")

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    /**
     * 单曲 like 检查结果（无完整红心歌单缓存时使用）。
     * 有 [Snapshot] 时以歌单为准；点赞/取消会同步写入这里。
     */
    private val _checkedLikes = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

    private val syncScheduled = AtomicBoolean(false)
    private var syncJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            loadFromDisk()?.let { _snapshot.value = it }
        }
    }

    fun peek(): Snapshot? = _snapshot.value

    /**
     * 是否喜欢：
     * - 有红心歌单缓存 → 是否在歌单内（永不为 null）
     * - 否则看单曲检查缓存；仍未知则 null
     */
    fun isLiked(trackId: Long): Boolean? {
        val snap = _snapshot.value
        if (snap != null) return snap.likedIds.contains(trackId)
        return _checkedLikes.value[trackId]
    }

    /** 写入单曲检查结果；若已有歌单缓存则校正成员关系（不触发延迟同步）。 */
    fun recordLikeStatus(track: TrackRow, liked: Boolean) {
        _checkedLikes.value = _checkedLikes.value + (track.id to liked)
        val snap = _snapshot.value ?: return
        if (snap.likedIds.contains(track.id) == liked) return
        applyLocalLike(track, liked = liked, scheduleSync = false)
    }

    /** 批量写入邻曲预检查结果（带 TrackRow，便于校正歌单成员）。 */
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

    /** 进入主界面后调用：有会话则后台预拉取（已有完整缓存则跳过）。 */
    fun prefetchOnAppReady() {
        val session = sessionRepository.session.value ?: return
        if (session.isGuest) return
        val existing = _snapshot.value
        if (existing != null && existing.tracks.isNotEmpty() && existing.playlistId > 0L) {
            return
        }
        if (prefetchJob?.isActive == true) return
        prefetchJob = scope.launch {
            runCatching { refreshFromNetwork(force = false) }
                .onFailure { Log.w(TAG, "prefetch liked playlist failed", it) }
        }
    }

    /** 用户点刷新：强制网络拉取并写缓存。 */
    suspend fun forceRefresh(): Snapshot? = refreshFromNetwork(force = true)

    /**
     * 本地 like / 取消 like。
     * @return 更新后的快照；无缓存时仅维护临时喜欢集合并仍触发延迟同步。
     */
    fun applyLocalLike(
        track: TrackRow,
        liked: Boolean,
        scheduleSync: Boolean = true,
    ): Snapshot? {
        _checkedLikes.value = _checkedLikes.value + (track.id to liked)
        val current = _snapshot.value
        if (current == null && !liked) {
            // 无歌单缓存时取消喜欢：只记单曲状态，避免写入「空红心歌单」误判其它曲
            if (scheduleSync) scheduleDeferredSync()
            return null
        }
        val nextTracks = if (current == null) {
            listOf(track)
        } else {
            val without = current.tracks.filterNot { it.id == track.id }
            if (liked) listOf(track) + without else without
        }
        val next = Snapshot(
            playlistId = current?.playlistId ?: 0L,
            title = current?.title?.takeIf { it.isNotBlank() } ?: "我喜欢的音乐",
            coverUrl = current?.coverUrl ?: track.coverUrl,
            tracks = nextTracks,
            updatedAtMs = System.currentTimeMillis(),
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
        _snapshot.value = null
        _checkedLikes.value = emptyMap()
        scope.launch(Dispatchers.IO) {
            runCatching { if (cacheFile.exists()) cacheFile.delete() }
        }
    }

    private fun scheduleDeferredSync() {
        // 已有计时：不重置、不新建
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
            val status = withContext(Dispatchers.IO) { authClient.loginStatus(cookie) }
            val uid = NcmJson.userIdFromLoginStatus(status) ?: return@withLock _snapshot.value
            val plJson = withContext(Dispatchers.IO) {
                userClient.userPlaylist(uid, cookie, limit = 80, offset = 0)
            }
            val playlists = NcmLibraryParse.playlistsFromUserPlaylist(plJson, uid)
            val heart = playlists.firstOrNull { it.isHeartPlaylist }
                ?: return@withLock _snapshot.value
            val tracks = withContext(Dispatchers.IO) {
                loadTracks(heart.id, cookie)
            }
            val snap = Snapshot(
                playlistId = heart.id,
                title = heart.name,
                coverUrl = heart.coverUrl,
                tracks = tracks,
                updatedAtMs = System.currentTimeMillis(),
            )
            _snapshot.value = snap
            withContext(Dispatchers.IO) { persistToDisk(snap) }
            snap
        }
    }

    private suspend fun loadTracks(playlistId: Long, cookie: String): List<TrackRow> {
        val detail = userClient.playlistDetail(playlistId, cookie)
        val fromPl = NcmLibraryParse.tracksFromPlaylistDetail(detail)
        if (fromPl.isNotEmpty()) return fromPl
        val ids = NcmLibraryParse.trackIdsFromPlaylistDetail(detail)
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(400).flatMap { chunk ->
            NcmLibraryParse.tracksFromSongDetail(userClient.songDetail(chunk, cookie))
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
            if (playlistId <= 0L && tracks.isEmpty()) null
            else Snapshot(playlistId, title, coverUrl, tracks, updatedAtMs)
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
                .put("tracks", arr)
            cacheFile.writeText(root.toString(), Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "persist liked playlist failed", it) }
    }

    companion object {
        private const val TAG = "LikedPlaylistRepo"
        private const val DEBOUNCE_MS = 3 * 60 * 1000L
    }
}
