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
 * 专辑曲目缓存，对齐 [PlaylistTracksCache]：进页 peek 命中则不打完整接口。
 * 收藏态 / 评论数只留内存，磁盘不存，避免过期。
 */
class AlbumTracksCache(
    context: Context,
) {
    data class Entry(
        val albumId: Long,
        val title: String,
        val tracks: List<TrackRow>,
        val coverUrl: String?,
        val artist: String?,
        val artistId: Long = 0L,
        val artistCoverUrl: String? = null,
        val publishTime: Long = 0L,
        val company: String? = null,
        val description: String? = null,
        val type: String? = null,
        val alias: String? = null,
        val size: Int = 0,
        val updatedAtMs: Long = 0L,
        val subscribed: Boolean? = null,
        val subscribedCount: Int = 0,
        val commentCount: Int = 0,
    )

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "zmusic_album_cache").apply { mkdirs() }
    private val memory = ConcurrentHashMap<Long, Entry>()
    private val ioMutex = Mutex()

    fun peek(albumId: Long): Entry? {
        if (albumId <= 0L) return null
        memory[albumId]?.let { return it }
        return loadFromDisk(albumId)?.also { memory[albumId] = it }
    }

    suspend fun save(entry: Entry) {
        if (entry.albumId <= 0L || entry.tracks.isEmpty()) return
        memory[entry.albumId] = entry
        withContext(Dispatchers.IO) {
            ioMutex.withLock { persistToDisk(entry) }
        }
    }

    fun attachDynamic(
        albumId: Long,
        subscribed: Boolean,
        subscribedCount: Int,
        commentCount: Int,
    ) {
        if (albumId <= 0L) return
        val cur = memory[albumId] ?: return
        memory[albumId] = cur.copy(
            subscribed = subscribed,
            subscribedCount = subscribedCount.coerceAtLeast(0),
            commentCount = commentCount.coerceAtLeast(0),
        )
    }

    fun patchSubscribed(albumId: Long, subscribed: Boolean, countDelta: Int = 0) {
        if (albumId <= 0L) return
        val cur = memory[albumId] ?: return
        memory[albumId] = cur.copy(
            subscribed = subscribed,
            subscribedCount = (cur.subscribedCount + countDelta).coerceAtLeast(0),
        )
    }

    fun clear() {
        memory.clear()
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun diskFile(albumId: Long) = File(dir, "$albumId.json")

    private fun loadFromDisk(albumId: Long): Entry? {
        val file = diskFile(albumId)
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("tracks") ?: JSONArray()
            val tracks = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    NcmLibraryParse.trackFromCacheJson(o)?.let { add(it) }
                }
            }
            if (tracks.isEmpty()) return@runCatching null
            Entry(
                albumId = root.optLong("albumId", albumId),
                title = root.optString("title", ""),
                tracks = tracks,
                coverUrl = root.optString("coverUrl", "").takeIf { it.isNotBlank() },
                artist = root.optString("artist", "").takeIf { it.isNotBlank() },
                artistId = root.optLong("artistId", 0L),
                artistCoverUrl = root.optString("artistCoverUrl", "").takeIf { it.isNotBlank() },
                publishTime = root.optLong("publishTime", 0L),
                company = root.optString("company", "").takeIf { it.isNotBlank() && it != "null" },
                description = root.optString("description", "").takeIf { it.isNotBlank() },
                type = root.optString("type", "").takeIf { it.isNotBlank() && it != "null" },
                alias = root.optString("alias", "").takeIf { it.isNotBlank() },
                size = root.optInt("size", tracks.size).coerceAtLeast(tracks.size),
                updatedAtMs = root.optLong("updatedAtMs", 0L),
            )
        }.onFailure {
            Log.w(TAG, "load album cache failed id=$albumId", it)
        }.getOrNull()
    }

    private fun persistToDisk(entry: Entry) {
        runCatching {
            val arr = JSONArray()
            entry.tracks.forEach { t ->
                arr.put(NcmLibraryParse.trackToCacheJson(t))
            }
            val root = JSONObject()
                .put("albumId", entry.albumId)
                .put("title", entry.title)
                .put("updatedAtMs", entry.updatedAtMs)
                .put("coverUrl", entry.coverUrl ?: "")
                .put("artist", entry.artist ?: "")
                .put("artistId", entry.artistId)
                .put("artistCoverUrl", entry.artistCoverUrl ?: "")
                .put("publishTime", entry.publishTime)
                .put("company", entry.company ?: "")
                .put("description", entry.description ?: "")
                .put("type", entry.type ?: "")
                .put("alias", entry.alias ?: "")
                .put("size", entry.size.coerceAtLeast(entry.tracks.size))
                .put("tracks", arr)
            diskFile(entry.albumId).writeText(root.toString(), Charsets.UTF_8)
            trimDiskLocked()
        }.onFailure {
            Log.w(TAG, "persist album cache failed id=${entry.albumId}", it)
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
        private const val TAG = "AlbumTracksCache"
        private const val DISK_MAX = 32
    }
}
