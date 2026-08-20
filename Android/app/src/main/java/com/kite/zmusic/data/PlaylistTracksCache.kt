package com.kite.zmusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 普通歌单曲目缓存（不含「我喜欢的音乐」专用路径）：
 * - 进页只拉前 [PlaylistTrackLoader.FIRST_BATCH] 首
 * - 下滑再按页补，经 [updates] 推送
 */
class PlaylistTracksCache(
    context: Context,
    private val userClient: NcmUserClient,
) {
    data class Entry(
        val playlistId: Long,
        val title: String,
        val tracks: List<TrackRow>,
        val updatedAtMs: Long,
        val expectedCount: Int = tracks.size,
        val complete: Boolean = true,
        /** 歌单完整 id 序；未齐时用于后台补全，避免再次请求 playlist/detail */
        val allIds: List<Long> = emptyList(),
        /** 仅内存：磁盘缓存不存收藏态，避免过期 */
        val subscribeMeta: PlaylistSubscribeMeta? = null,
    )

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "zmusic_playlist_cache").apply { mkdirs() }
    private val memory = ConcurrentHashMap<Long, Entry>()
    private val ioMutex = Mutex()
    private val pendingAllIds = ConcurrentHashMap<Long, List<Long>>()

    private val _updates = MutableSharedFlow<Entry>(
        extraBufferCapacity = 64,
        replay = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates: SharedFlow<Entry> = _updates.asSharedFlow()
    private val _revision = MutableStateFlow(0L)
    /** 缓存写入时钟：不会像 [updates] 那样丢掉事件，歌单页用来 peek 对齐。 */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun peek(playlistId: Long): Entry? {
        if (playlistId <= 0L) return null
        memory[playlistId]?.let { return it }
        return loadFromDisk(playlistId)?.also { entry ->
            memory[playlistId] = entry
            if (!entry.complete && entry.allIds.isNotEmpty()) {
                pendingAllIds[playlistId] = entry.allIds
            }
        }
    }

    fun attachSubscribeMeta(playlistId: Long, meta: PlaylistSubscribeMeta) {
        if (playlistId <= 0L) return
        val cur = memory[playlistId] ?: return
        memory[playlistId] = cur.copy(subscribeMeta = meta)
    }

    fun patchSubscribed(playlistId: Long, subscribed: Boolean, countDelta: Int = 0) {
        if (playlistId <= 0L) return
        val cur = memory[playlistId] ?: return
        val meta = cur.subscribeMeta ?: return
        memory[playlistId] = cur.copy(
            subscribeMeta = meta.copy(
                subscribed = subscribed,
                subscribedCount = (meta.subscribedCount + countDelta).coerceAtLeast(0),
            ),
        )
    }

    /** 有缓存则立刻返回；否则只拉首屏，不后台灌完整列表。 */
    suspend fun getOrFetch(
        playlistId: Long,
        title: String,
        cookie: String,
    ): Entry {
        val cached = peek(playlistId)?.takeIf { it.tracks.isNotEmpty() }
        if (cached != null) return cached
        return fetchAndStore(playlistId, title, cookie, force = false)
    }

    /** 再往后补到至少 [minCount] 首（或整张歌单结束）。 */
    suspend fun ensureLoadedThrough(
        playlistId: Long,
        title: String,
        cookie: String,
        minCount: Int,
    ): Entry {
        var entry = peek(playlistId)?.takeIf { it.tracks.isNotEmpty() }
            ?: getOrFetch(playlistId, title, cookie)
        if (entry.complete || entry.tracks.size >= minCount) return entry
        return ioMutex.withLock {
            entry = peek(playlistId) ?: entry
            if (entry.complete || entry.tracks.size >= minCount) return@withLock entry
            val allIds = entry.allIds.takeIf { it.isNotEmpty() }
                ?: pendingAllIds[playlistId]
                ?: withContext(Dispatchers.IO) {
                    val detail = userClient.playlistDetail(
                        playlistId,
                        cookie,
                        limit = PlaylistTrackLoader.FIRST_BATCH,
                    )
                    NcmLibraryParse.trackIdsFromPlaylistDetail(detail)
                }
            if (allIds.isEmpty()) return@withLock entry
            pendingAllIds[playlistId] = allIds
            val page = (minCount - entry.tracks.size)
                .coerceAtLeast(PlaylistTrackLoader.PAGE)
            val tracks = NcmLibraryParse.mergeTrackRows(
                entry.tracks,
                PlaylistTrackLoader.loadNextPage(
                    userClient = userClient,
                    cookie = cookie,
                    allIds = allIds,
                    already = entry.tracks,
                    pageSize = page,
                    playlistId = playlistId,
                ),
            )
            val next = Entry(
                playlistId = playlistId,
                title = entry.title.ifBlank { title },
                tracks = tracks,
                updatedAtMs = System.currentTimeMillis(),
                expectedCount = allIds.size.coerceAtLeast(tracks.size),
                complete = tracks.size >= allIds.size,
                allIds = allIds,
                subscribeMeta = entry.subscribeMeta,
            )
            publish(next)
            withContext(Dispatchers.IO) { persistToDisk(next) }
            next
        }
    }

    /**
     * 播放队列已经比磁盘/内存缓存更长时（曲谱 / 黑胶选歌补全），把多出来的曲目写回歌单缓存。
     */
    suspend fun absorbIfLonger(playlistId: Long, tracks: List<TrackRow>) {
        if (playlistId <= 0L || tracks.size <= 1) return
        ioMutex.withLock {
            val cur = peek(playlistId) ?: return@withLock
            if (tracks.size <= cur.tracks.size) return@withLock
            val allIds = cur.allIds
            val merged = if (allIds.isNotEmpty()) {
                NcmLibraryParse.mergeLoadedInOrder(allIds, tracks, cur.tracks)
            } else {
                val byId = LinkedHashMap<Long, TrackRow>(cur.tracks.size + tracks.size)
                for (t in cur.tracks) byId[t.id] = t
                for (t in tracks) byId[t.id] = NcmLibraryParse.preferExistingCover(byId[t.id], t)
                tracks.mapNotNull { byId[it.id] }.ifEmpty { cur.tracks }
            }
            if (merged.size <= cur.tracks.size) return@withLock
            val expected = cur.expectedCount
                .coerceAtLeast(allIds.size)
                .coerceAtLeast(merged.size)
            val next = cur.copy(
                tracks = merged,
                expectedCount = expected,
                complete = when {
                    allIds.isNotEmpty() -> merged.size >= allIds.size
                    expected > 0 -> merged.size >= expected
                    else -> cur.complete
                },
                allIds = allIds,
                updatedAtMs = System.currentTimeMillis(),
            )
            publish(next)
            withContext(Dispatchers.IO) { persistToDisk(next) }
        }
    }

    /** 强制网络刷新（仍只拉首屏）。 */
    suspend fun forceRefresh(
        playlistId: Long,
        title: String,
        cookie: String,
    ): Entry = fetchAndStore(playlistId, title, cookie, force = true)

    suspend fun removeTrack(playlistId: Long, trackId: Long): Entry? =
        removeTracks(playlistId, listOf(trackId))

    suspend fun removeTracks(playlistId: Long, trackIds: Collection<Long>): Entry? = ioMutex.withLock {
        if (playlistId <= 0L) return@withLock null
        val drop = trackIds.filter { it > 0L }.toHashSet()
        if (drop.isEmpty()) return@withLock null
        val cur = peek(playlistId) ?: return@withLock null
        val tracks = cur.tracks.filterNot { it.id in drop }
        if (tracks.size == cur.tracks.size) return@withLock cur
        val allIds = cur.allIds.filterNot { it in drop }
        val next = cur.copy(
            tracks = tracks,
            allIds = allIds,
            expectedCount = when {
                allIds.isNotEmpty() -> allIds.size
                else -> (cur.expectedCount - drop.size).coerceAtLeast(tracks.size)
            },
            complete = allIds.isEmpty() || tracks.size >= allIds.size,
            updatedAtMs = System.currentTimeMillis(),
        )
        pendingAllIds[playlistId] = allIds
        publish(next)
        withContext(Dispatchers.IO) { persistToDisk(next) }
        next
    }

    fun containsTrack(playlistId: Long, trackId: Long): Boolean? {
        if (playlistId <= 0L || trackId <= 0L) return false
        val cur = memory[playlistId] ?: return null
        if (cur.tracks.any { it.id == trackId }) return true
        if (cur.allIds.contains(trackId)) return true
        if (cur.complete) return false
        return null
    }

    fun renameTitle(playlistId: Long, title: String) {
        if (playlistId <= 0L) return
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val cur = memory[playlistId] ?: return
        if (cur.title == trimmed) return
        publish(cur.copy(title = trimmed))
    }

    suspend fun addTrackIfAbsent(playlistId: Long, track: TrackRow) {
        if (playlistId <= 0L || track.id <= 0L) return
        ioMutex.withLock {
            val cur = peek(playlistId) ?: return@withLock
            if (cur.tracks.any { it.id == track.id }) return@withLock
            val allIds = when {
                cur.allIds.isEmpty() -> listOf(track.id) + cur.tracks.map { it.id }
                track.id in cur.allIds -> cur.allIds
                else -> listOf(track.id) + cur.allIds
            }
            val tracks = listOf(track) + cur.tracks
            val expected = cur.expectedCount
                .coerceAtLeast(allIds.size)
                .coerceAtLeast(tracks.size)
            val next = cur.copy(
                tracks = tracks,
                allIds = allIds,
                expectedCount = expected,
                complete = allIds.isEmpty() || tracks.size >= allIds.size,
                updatedAtMs = System.currentTimeMillis(),
            )
            pendingAllIds[playlistId] = allIds
            publish(next)
            withContext(Dispatchers.IO) { persistToDisk(next) }
        }
    }

    fun clear() {
        pendingAllIds.clear()
        memory.clear()
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private suspend fun fetchAndStore(
        playlistId: Long,
        title: String,
        cookie: String,
        force: Boolean,
    ): Entry = ioMutex.withLock {
        if (!force) {
            memory[playlistId]?.takeIf { it.tracks.isNotEmpty() }?.let { return@withLock it }
        }
        val previous = memory[playlistId] ?: loadFromDisk(playlistId)
        val first = withContext(Dispatchers.IO) {
            PlaylistTrackLoader.loadFirstBatch(userClient, playlistId, cookie)
        }
        val retained = previous?.tracks.orEmpty()
        val tracks = withContext(Dispatchers.IO) {
            val want = retained.size
                .coerceAtLeast(first.tracks.size)
                .coerceAtMost(first.allIds.size.coerceAtLeast(first.tracks.size))
            val loaded = if (want > first.tracks.size && first.allIds.isNotEmpty()) {
                PlaylistTrackLoader.loadOrderedIds(
                    userClient = userClient,
                    cookie = cookie,
                    ids = first.allIds.take(want),
                    known = first.tracks + retained,
                )
            } else {
                first.tracks
            }
            NcmLibraryParse.mergeLoadedInOrder(first.allIds, loaded, retained)
        }
        val entry = Entry(
            playlistId = playlistId,
            title = first.subscribeMeta?.name?.takeIf { it.isNotBlank() } ?: title,
            tracks = tracks,
            updatedAtMs = System.currentTimeMillis(),
            expectedCount = first.allIds.size.coerceAtLeast(tracks.size),
            complete = first.allIds.isEmpty() || tracks.size >= first.allIds.size,
            allIds = first.allIds,
            subscribeMeta = first.subscribeMeta ?: previous?.subscribeMeta,
        )
        publish(entry)
        withContext(Dispatchers.IO) { persistToDisk(entry) }
        if (!first.complete && first.allIds.isNotEmpty() && !entry.complete) {
            pendingAllIds[playlistId] = first.allIds
        } else {
            pendingAllIds.remove(playlistId)
        }
        entry
    }

    private fun publish(entry: Entry) {
        memory[entry.playlistId] = entry
        _revision.value = _revision.value + 1
        _updates.tryEmit(entry)
    }

    private fun diskFile(playlistId: Long) = File(dir, "$playlistId.json")

    private fun loadFromDisk(playlistId: Long): Entry? {
        val file = diskFile(playlistId)
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val id = root.optLong("playlistId", playlistId)
            val title = root.optString("title", "")
            val updatedAtMs = root.optLong("updatedAtMs", 0L)
            val arr = root.optJSONArray("tracks") ?: JSONArray()
            val tracks = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    NcmLibraryParse.trackFromCacheJson(o)?.let { add(it) }
                }
            }
            val expectedCount = root.optInt("expectedCount", tracks.size).coerceAtLeast(tracks.size)
            val complete = if (root.has("complete")) root.optBoolean("complete", true) else true
            val idsArr = root.optJSONArray("allIds")
            val allIds = if (idsArr != null) {
                buildList {
                    for (i in 0 until idsArr.length()) {
                        val tid = idsArr.optLong(i, 0L)
                        if (tid > 0L) add(tid)
                    }
                }
            } else {
                emptyList()
            }
            Entry(id, title, tracks, updatedAtMs, expectedCount, complete, allIds)
        }.onFailure {
            Log.w(TAG, "load playlist cache failed id=$playlistId", it)
        }.getOrNull()
    }

    private fun persistToDisk(entry: Entry) {
        runCatching {
            val arr = JSONArray()
            entry.tracks.forEach { t ->
                arr.put(NcmLibraryParse.trackToCacheJson(t))
            }
            val root = JSONObject()
                .put("playlistId", entry.playlistId)
                .put("title", entry.title)
                .put("updatedAtMs", entry.updatedAtMs)
                .put("expectedCount", entry.expectedCount)
                .put("complete", entry.complete)
                .put("allIds", JSONArray(entry.allIds))
                .put("tracks", arr)
            diskFile(entry.playlistId).writeText(root.toString(), Charsets.UTF_8)
            trimDiskLocked()
        }.onFailure {
            Log.w(TAG, "persist playlist cache failed id=${entry.playlistId}", it)
        }
    }

    private fun trimDiskLocked() {
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
            ?.sortedBy { it.lastModified() }
            ?: return
        var excess = files.size - DISK_MAX
        var i = 0
        while (excess > 0 && i < files.size) {
            if (files[i++].delete()) excess--
        }
    }

    companion object {
        private const val TAG = "PlaylistTracksCache"
        private const val DISK_MAX = 32
    }
}
