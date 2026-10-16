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
 * 歌曲百科缓存，对齐专辑曲目缓存：再次打开先读内存，没有再读磁盘，命中就不打百科接口。
 * 未登录时写入的条目，登录后再打开会重拉一次，把登录才有的内容补上。
 */
internal class SongWikiCache(context: Context) {
    private data class Entry(
        val songId: Long,
        val page: SongWikiPage,
        val authed: Boolean,
        val updatedAtMs: Long,
    )

    private val dir = File(context.applicationContext.filesDir, "zmusic_wiki_cache").apply { mkdirs() }
    private val memory = ConcurrentHashMap<Long, Entry>()
    private val ioMutex = Mutex()

    fun peekMemory(songId: Long, authed: Boolean): SongWikiPage? {
        val entry = memory[songId] ?: return null
        return entry.page.takeIf { it.usable(entry.authed, authed) }
    }

    suspend fun load(songId: Long, authed: Boolean): SongWikiPage? {
        if (songId <= 0L) return null
        peekMemory(songId, authed)?.let { return it }
        val entry = withContext(Dispatchers.IO) { readDisk(songId) } ?: return null
        if (!entry.page.usable(entry.authed, authed)) return null
        memory[songId] = entry
        return entry.page
    }

    suspend fun save(songId: Long, page: SongWikiPage, authed: Boolean) {
        if (songId <= 0L || page.isEmpty) return
        val entry = Entry(
            songId = songId,
            page = page,
            authed = authed,
            updatedAtMs = System.currentTimeMillis(),
        )
        memory[songId] = entry
        withContext(Dispatchers.IO) {
            ioMutex.withLock { writeDisk(entry) }
        }
    }

    fun clear() {
        memory.clear()
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun SongWikiPage.usable(storedAuthed: Boolean, requestAuthed: Boolean): Boolean {
        if (isEmpty) return false
        if (!storedAuthed && requestAuthed) return false
        return true
    }

    private fun diskFile(songId: Long) = File(dir, "$songId.json")

    private fun readDisk(songId: Long): Entry? {
        val file = diskFile(songId)
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val page = pageFromJson(root)
            if (page.isEmpty) return@runCatching null
            Entry(
                songId = root.optLong("songId", songId),
                page = page,
                authed = root.optBoolean("authed", false),
                updatedAtMs = root.optLong("updatedAtMs", 0L),
            )
        }.onFailure {
            Log.w(TAG, "load wiki cache failed id=$songId", it)
        }.getOrNull()
    }

    private fun writeDisk(entry: Entry) {
        runCatching {
            val root = JSONObject()
                .put("songId", entry.songId)
                .put("authed", entry.authed)
                .put("updatedAtMs", entry.updatedAtMs)
                .put("facts", factsToJson(entry.page.facts))
                .put("chips", JSONArray(entry.page.chips))
                .put("notes", factsToJson(entry.page.notes))
                .put("paragraphs", JSONArray(entry.page.paragraphs))
                .put("similar", coversToJson(entry.page.similar))
                .put("playlists", coversToJson(entry.page.playlists))
            entry.page.album?.let { root.put("album", coverToJson(it)) }
            diskFile(entry.songId).writeText(root.toString(), Charsets.UTF_8)
            trimDiskLocked()
        }.onFailure {
            Log.w(TAG, "persist wiki cache failed id=${entry.songId}", it)
        }
    }

    private fun trimDiskLocked() {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.sortedBy { it.lastModified() }
            ?: return
        var excess = files.size - DISK_MAX
        var i = 0
        while (excess > 0 && i < files.size) {
            if (files[i++].delete()) excess--
        }
    }

    private fun pageFromJson(root: JSONObject): SongWikiPage {
        return SongWikiPage(
            facts = factsFromJson(root.optJSONArray("facts")),
            chips = stringsFromJson(root.optJSONArray("chips")),
            notes = factsFromJson(root.optJSONArray("notes")),
            paragraphs = stringsFromJson(root.optJSONArray("paragraphs")),
            similar = coversFromJson(root.optJSONArray("similar")),
            playlists = coversFromJson(root.optJSONArray("playlists")),
            album = root.optJSONObject("album")?.let { coverFromJson(it) },
        )
    }

    private fun factsToJson(facts: List<SongWikiFact>): JSONArray {
        val arr = JSONArray()
        facts.forEach { fact ->
            arr.put(JSONObject().put("label", fact.label).put("value", fact.value))
        }
        return arr
    }

    private fun factsFromJson(arr: JSONArray?): List<SongWikiFact> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val label = o.optString("label")
                val value = o.optString("value")
                if (label.isNotBlank() || value.isNotBlank()) add(SongWikiFact(label, value))
            }
        }
    }

    private fun stringsFromJson(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val text = arr.optString(i)
                if (text.isNotBlank()) add(text)
            }
        }
    }

    private fun coversToJson(items: List<SongWikiCoverItem>): JSONArray {
        val arr = JSONArray()
        items.forEach { arr.put(coverToJson(it)) }
        return arr
    }

    private fun coverToJson(item: SongWikiCoverItem): JSONObject {
        return JSONObject()
            .put("id", item.id)
            .put("title", item.title)
            .put("subtitle", item.subtitle)
            .put("coverUrl", item.coverUrl ?: "")
            .put("playCount", item.playCount)
    }

    private fun coversFromJson(arr: JSONArray?): List<SongWikiCoverItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                coverFromJson(o)?.let { add(it) }
            }
        }
    }

    private fun coverFromJson(o: JSONObject): SongWikiCoverItem? {
        val id = o.optLong("id", 0L)
        val title = o.optString("title")
        if (id <= 0L && title.isBlank()) return null
        return SongWikiCoverItem(
            id = id,
            title = title,
            subtitle = o.optString("subtitle"),
            coverUrl = o.optString("coverUrl").takeIf { it.isNotBlank() },
            playCount = o.optLong("playCount", 0L),
        )
    }

    companion object {
        private const val TAG = "SongWikiCache"
        private const val DISK_MAX = 48
    }
}
