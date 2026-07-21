package com.kite.zmusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 普通歌单曲目缓存（不含「我喜欢的音乐」专用路径）：
 * - 首次打开：先返回最多 [PlaylistTrackLoader.FIRST_BATCH] 首，后台补全
 * - 补全过程经 [updates] 推送，供详情页 / 播放队列同步
 */
class PlaylistTracksCache(
    context: Context,
    private val userClient: NcmUserClient = NcmUserClient(),
) {
    data class Entry(
        val playlistId: Long,
        val title: String,
        val tracks: List<TrackRow>,
        val updatedAtMs: Long,
        val expectedCount: Int = tracks.size,
        val complete: Boolean = true,
    )

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "zmusic_playlist_cache").apply { mkdirs() }
    private val memory = ConcurrentHashMap<Long, Entry>()
    private val ioMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fillJobs = ConcurrentHashMap<Long, Job>()
    private val pendingAllIds = ConcurrentHashMap<Long, List<Long>>()

    private val _updates = MutableSharedFlow<Entry>(extraBufferCapacity = 16, replay = 0)
    val updates: SharedFlow<Entry> = _updates.asSharedFlow()

    fun peek(playlistId: Long): Entry? {
        if (playlistId <= 0L) return null
        memory[playlistId]?.let { return it }
        return loadFromDisk(playlistId)?.also { memory[playlistId] = it }
    }

    /** 有缓存则返回（未齐则后台继续补）；否则首批网络 + 后台补全。 */
    suspend fun getOrFetch(
        playlistId: Long,
        title: String,
        cookie: String,
    ): Entry {
        val cached = peek(playlistId)?.takeIf { it.tracks.isNotEmpty() }
        if (cached != null) {
            if (!cached.complete) {
                scheduleFill(playlistId, cached.title.ifBlank { title }, cookie, cached.tracks)
            }
            return cached
        }
        return fetchAndStore(playlistId, title, cookie, force = false)
    }

    /** 强制网络刷新（仍先 500，再后台补）。 */
    suspend fun forceRefresh(
        playlistId: Long,
        title: String,
        cookie: String,
    ): Entry = fetchAndStore(playlistId, title, cookie, force = true)

    fun clear() {
        fillJobs.values.forEach { it.cancel() }
        fillJobs.clear()
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
        fillJobs[playlistId]?.cancel()
        val first = withContext(Dispatchers.IO) {
            PlaylistTrackLoader.loadFirstBatch(userClient, playlistId, cookie)
        }
        val entry = Entry(
            playlistId = playlistId,
            title = title,
            tracks = first.tracks,
            updatedAtMs = System.currentTimeMillis(),
            expectedCount = first.allIds.size.coerceAtLeast(first.tracks.size),
            complete = first.complete,
        )
        memory[playlistId] = entry
        withContext(Dispatchers.IO) { persistToDisk(entry) }
        _updates.tryEmit(entry)
        if (!first.complete) {
            pendingAllIds[playlistId] = first.allIds
            scheduleFill(playlistId, title, cookie, first.tracks, first.allIds)
        } else {
            pendingAllIds.remove(playlistId)
        }
        entry
    }

    private fun scheduleFill(
        playlistId: Long,
        title: String,
        cookie: String,
        already: List<TrackRow>,
        knownIds: List<Long>? = null,
    ) {
        if (playlistId <= 0L) return
        if (fillJobs[playlistId]?.isActive == true) return
        fillJobs[playlistId] = scope.launch {
            try {
                runCatching {
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
                        val entry = Entry(
                            playlistId = playlistId,
                            title = title,
                            tracks = ordered,
                            updatedAtMs = System.currentTimeMillis(),
                            expectedCount = allIds.size,
                            complete = ordered.size >= allIds.size,
                        )
                        memory[playlistId] = entry
                        withContext(Dispatchers.IO) { persistToDisk(entry) }
                        _updates.emit(entry)
                    }
                }.onFailure { Log.w(TAG, "playlist fill failed id=$playlistId", it) }
            } finally {
                fillJobs.remove(playlistId)
            }
        }
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
                    val tid = o.optLong("id", 0L)
                    if (tid <= 0L) continue
                    add(
                        TrackRow(
                            id = tid,
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
            val complete = if (root.has("complete")) root.optBoolean("complete", true) else true
            Entry(id, title, tracks, updatedAtMs, expectedCount, complete)
        }.onFailure {
            Log.w(TAG, "load playlist cache failed id=$playlistId", it)
        }.getOrNull()
    }

    private fun persistToDisk(entry: Entry) {
        runCatching {
            val arr = JSONArray()
            entry.tracks.forEach { t ->
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
                .put("playlistId", entry.playlistId)
                .put("title", entry.title)
                .put("updatedAtMs", entry.updatedAtMs)
                .put("expectedCount", entry.expectedCount)
                .put("complete", entry.complete)
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
