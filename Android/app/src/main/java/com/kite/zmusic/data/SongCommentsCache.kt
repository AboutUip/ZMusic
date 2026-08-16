package com.kite.zmusic.data

/**
 * 歌曲评论内存缓存：同一首歌再次打开评论直接复用，不强制重拉。
 */
internal data class SongCommentsSnapshot(
    val songId: Long,
    val sortType: Int,
    val comments: List<SongComment>,
    val pageNo: Int,
    val cursor: String?,
    val hasMore: Boolean,
    val total: Long,
    val useLegacy: Boolean,
    val expandedTextIds: Set<Long> = emptySet(),
    val openFloorIds: Set<Long> = emptySet(),
)

internal object SongCommentsCache {
    private const val MaxEntries = 12
    private val lock = Any()
    private val map = LinkedHashMap<String, SongCommentsSnapshot>(MaxEntries, 0.75f, true)

    fun get(songId: Long, sortType: Int): SongCommentsSnapshot? = synchronized(lock) {
        map[key(songId, sortType)]
    }

    fun put(snapshot: SongCommentsSnapshot) {
        if (snapshot.songId <= 0L || snapshot.comments.isEmpty()) return
        synchronized(lock) {
            map[key(snapshot.songId, snapshot.sortType)] = snapshot
            while (map.size > MaxEntries) {
                val oldest = map.keys.firstOrNull() ?: break
                map.remove(oldest)
            }
        }
    }

    private fun key(songId: Long, sortType: Int) = "$songId:$sortType"
}
