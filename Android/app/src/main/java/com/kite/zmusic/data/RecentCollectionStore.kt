package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 个人页「收藏」快捷位：最近打开过的歌单 / 专辑优先排到前 10。
 */
class RecentCollectionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _playlistIds = MutableStateFlow(load(KEY_PLAYLISTS))
    private val _albumIds = MutableStateFlow(load(KEY_ALBUMS))
    val playlistIds: StateFlow<List<Long>> = _playlistIds.asStateFlow()
    val albumIds: StateFlow<List<Long>> = _albumIds.asStateFlow()

    fun touchPlaylist(id: Long) {
        if (id <= 0L) return
        write(KEY_PLAYLISTS, promote(id, _playlistIds.value)) { _playlistIds.value = it }
    }

    fun touchAlbum(id: Long) {
        if (id <= 0L) return
        write(KEY_ALBUMS, promote(id, _albumIds.value)) { _albumIds.value = it }
    }

    private fun promote(id: Long, cur: List<Long>): List<Long> {
        return (listOf(id) + cur.filter { it != id }).take(MAX)
    }

    private fun load(key: String): List<Long> {
        val raw = prefs.getString(key, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull()?.takeIf { id -> id > 0L } }
            .distinct()
            .take(MAX)
    }

    private fun write(key: String, next: List<Long>, publish: (List<Long>) -> Unit) {
        prefs.edit().putString(key, next.joinToString(",")).apply()
        publish(next)
    }

    companion object {
        const val PREFS = "zmusic_recent_collection"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_ALBUMS = "albums"
        const val MAX = 40
        const val PREVIEW_LIMIT = 10
    }
}

/** 最近打开的排前，其余保持原序。 */
fun <T> List<T>.preferRecent(
    recentIds: List<Long>,
    idOf: (T) -> Long,
): List<T> {
    if (isEmpty() || recentIds.isEmpty()) return this
    val byId = associateBy(idOf)
    val seen = LinkedHashSet<Long>()
    val out = ArrayList<T>(size)
    for (id in recentIds) {
        val item = byId[id] ?: continue
        if (seen.add(id)) out += item
    }
    for (item in this) {
        val id = idOf(item)
        if (seen.add(id)) out += item
    }
    return out
}
