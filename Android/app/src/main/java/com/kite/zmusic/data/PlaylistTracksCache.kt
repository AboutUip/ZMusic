package com.kite.zmusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 普通歌单曲目缓存（不含「我喜欢的音乐」预拉取逻辑）：
 * - 首次打开写入内存 + 磁盘；再次进入直接命中，避免反复请求
 * - 仅用户点刷新时强制网络更新
 * - 不预加载
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
    )

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "zmusic_playlist_cache").apply { mkdirs() }
    private val memory = ConcurrentHashMap<Long, Entry>()
    private val ioMutex = Mutex()

    fun peek(playlistId: Long): Entry? {
        if (playlistId <= 0L) return null
        memory[playlistId]?.let { return it }
        return loadFromDisk(playlistId)?.also { memory[playlistId] = it }
    }

    /** 有缓存则返回；否则请求网络并写入缓存。 */
    suspend fun getOrFetch(
        playlistId: Long,
        title: String,
        cookie: String,
    ): Entry {
        peek(playlistId)?.takeIf { it.tracks.isNotEmpty() }?.let { return it }
        return fetchAndStore(playlistId, title, cookie)
    }

    /** 强制网络刷新并覆盖缓存。 */
    suspend fun forceRefresh(
        playlistId: Long,
        title: String,
        cookie: String,
    ): Entry = fetchAndStore(playlistId, title, cookie)

    fun clear() {
        memory.clear()
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private suspend fun fetchAndStore(
        playlistId: Long,
        title: String,
        cookie: String,
    ): Entry = ioMutex.withLock {
        val tracks = withContext(Dispatchers.IO) { loadTracks(playlistId, cookie) }
        val entry = Entry(
            playlistId = playlistId,
            title = title,
            tracks = tracks,
            updatedAtMs = System.currentTimeMillis(),
        )
        memory[playlistId] = entry
        withContext(Dispatchers.IO) { persistToDisk(entry) }
        entry
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
            Entry(id, title, tracks, updatedAtMs)
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
