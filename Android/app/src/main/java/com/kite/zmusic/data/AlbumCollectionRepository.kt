package com.kite.zmusic.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AlbumCollectionSnapshot(
    val albums: List<CollectedAlbum> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

/**
 * 当前账号收藏的专辑。收藏状态以这里为准，专辑页和资料页共用。
 */
class AlbumCollectionRepository {
    private val _snapshot = MutableStateFlow(AlbumCollectionSnapshot())
    val snapshot: StateFlow<AlbumCollectionSnapshot> = _snapshot.asStateFlow()

    /** 用户刚改过的收藏，避免随后到达的 /album/sublist 旧快照把列表打回去。 */
    private val subscribeOverrides = mutableMapOf<Long, Boolean>()

    fun peek(): AlbumCollectionSnapshot = _snapshot.value

    fun find(id: Long): CollectedAlbum? {
        if (id <= 0L) return null
        return _snapshot.value.albums.find { it.id == id }
    }

    fun isSubscribed(id: Long): Boolean? {
        if (id <= 0L) return null
        subscribeOverrides[id]?.let { return it }
        if (_snapshot.value.albums.any { it.id == id }) return true
        return null
    }

    fun markLoading(more: Boolean) {
        _snapshot.update {
            if (more) {
                it.copy(loadingMore = true, error = null)
            } else {
                it.copy(
                    loading = it.albums.isEmpty(),
                    loadingMore = false,
                    error = null,
                )
            }
        }
    }

    fun replacePage(
        albums: List<CollectedAlbum>,
        total: Int,
        hasMore: Boolean,
        error: String? = null,
    ) {
        var next = albums
        val overrides = subscribeOverrides.toMap()
        for ((id, subscribed) in overrides) {
            val onServer = albums.any { it.id == id }
            next = applySubscribe(
                next,
                id,
                subscribed,
                insert = _snapshot.value.albums.find { it.id == id },
            )
            val agreed = if (subscribed) onServer else !onServer
            if (agreed) subscribeOverrides.remove(id)
        }
        _snapshot.value = AlbumCollectionSnapshot(
            albums = next,
            total = total.coerceAtLeast(next.size),
            hasMore = hasMore,
            loading = false,
            loadingMore = false,
            error = error,
        )
    }

    fun appendPage(albums: List<CollectedAlbum>, total: Int, hasMore: Boolean) {
        if (albums.isEmpty()) {
            _snapshot.update {
                it.copy(hasMore = false, loadingMore = false, loading = false)
            }
            return
        }
        _snapshot.update { cur ->
            val seen = cur.albums.mapTo(HashSet()) { it.id }
            val extra = albums.filter { seen.add(it.id) }
            val merged = cur.albums + extra
            cur.copy(
                albums = merged,
                total = total.coerceAtLeast(merged.size),
                hasMore = hasMore,
                loading = false,
                loadingMore = false,
            )
        }
    }

    fun fail(message: String, more: Boolean) {
        _snapshot.update {
            it.copy(
                loading = false,
                loadingMore = false,
                error = if (more && it.albums.isNotEmpty()) null else message,
            )
        }
    }

    fun setSubscribed(id: Long, subscribed: Boolean, insert: CollectedAlbum? = null) {
        if (id <= 0L) return
        subscribeOverrides[id] = subscribed
        _snapshot.update { cur ->
            val next = applySubscribe(cur.albums, id, subscribed, insert)
            val delta = next.size - cur.albums.size
            cur.copy(
                albums = next,
                total = (cur.total + delta).coerceAtLeast(next.size),
            )
        }
    }

    fun clear() {
        subscribeOverrides.clear()
        _snapshot.value = AlbumCollectionSnapshot()
    }

    private fun applySubscribe(
        list: List<CollectedAlbum>,
        id: Long,
        subscribed: Boolean,
        insert: CollectedAlbum?,
    ): List<CollectedAlbum> {
        val exists = list.any { it.id == id }
        return when {
            subscribed && exists -> list
            subscribed && insert != null -> listOf(insert.copy(id = id)) + list.filter { it.id != id }
            subscribed -> list
            else -> list.filter { it.id != id }
        }
    }
}
