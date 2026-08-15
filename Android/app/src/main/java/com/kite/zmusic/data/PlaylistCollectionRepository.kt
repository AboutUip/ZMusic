package com.kite.zmusic.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 当前账号的歌单列表（我喜欢 / 自建 / 收藏），供资料页与歌单详情共享。
 * 收藏状态以这里为准，避免打开歌单后资料页仍是旧列表。
 */
class PlaylistCollectionRepository {
    private val _selfUserId = MutableStateFlow(0L)
    val selfUserId: StateFlow<Long> = _selfUserId.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = _playlists.asStateFlow()

    /** 用户刚改过的收藏，避免随后到达的 /user/playlist 旧快照把列表打回去。 */
    private val subscribeOverrides = mutableMapOf<Long, Boolean>()

    /** 刚加歌写上的封面，避免 /user/playlist 仍返回空歌单占位图。 */
    private val coverOverrides = mutableMapOf<Long, String>()

    fun setSelfUserId(uid: Long) {
        if (uid > 0L) _selfUserId.value = uid
    }

    fun replaceAll(list: List<PlaylistSummary>) {
        var next = list
        val overrides = subscribeOverrides.toMap()
        for ((id, subscribed) in overrides) {
            next = applySubscribe(next, id, subscribed, insert = _playlists.value.find { it.id == id })
            val server = list.find { it.id == id }
            val agreed = if (subscribed) {
                server?.isSubscribed == true
            } else {
                server == null || server.isOwned
            }
            if (agreed) subscribeOverrides.remove(id)
        }
        next = next.map { pl ->
            val forced = coverOverrides[pl.id]
            when {
                pl.trackCount <= 0 -> {
                    coverOverrides.remove(pl.id)
                    pl.copy(coverUrl = null)
                }
                forced != null -> {
                    val serverCover = pl.resolvedCoverUrl()
                    if (coverFingerprint(serverCover) == coverFingerprint(forced)) {
                        coverOverrides.remove(pl.id)
                        pl.copy(coverUrl = serverCover ?: forced)
                    } else {
                        pl.copy(coverUrl = forced)
                    }
                }
                isDefaultPlaylistCover(pl.coverUrl) -> pl.copy(coverUrl = null)
                else -> pl
            }
        }
        _playlists.value = next
    }

    fun find(id: Long): PlaylistSummary? {
        if (id <= 0L) return null
        return _playlists.value.find { it.id == id }
    }

    fun rename(id: Long, name: String) {
        if (id <= 0L) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _playlists.update { list ->
            list.map { pl -> if (pl.id == id) pl.copy(name = trimmed) else pl }
        }
    }

    fun remove(id: Long) {
        if (id <= 0L) return
        _playlists.update { list -> list.filter { it.id != id } }
        subscribeOverrides.remove(id)
    }

    fun upsertCreated(playlist: PlaylistSummary) {
        if (playlist.id <= 0L) return
        _playlists.update { list ->
            if (list.any { it.id == playlist.id }) {
                list.map { pl -> if (pl.id == playlist.id) playlist else pl }
            } else {
                val insertAt = list.indexOfLast { it.isHeartPlaylist || it.isOwned } + 1
                list.take(insertAt) + playlist + list.drop(insertAt)
            }
        }
    }

    fun bumpTrackCount(id: Long, delta: Int) {
        if (id <= 0L || delta == 0) return
        _playlists.update { list ->
            list.map { pl ->
                if (pl.id == id) {
                    pl.copy(trackCount = (pl.trackCount + delta).coerceAtLeast(0))
                } else {
                    pl
                }
            }
        }
    }

    fun forcedCover(id: Long): String? = coverOverrides[id]

    /** 歌单内第一首已变：列表封面跟过去，避免只改详情页。 */
    fun syncCover(id: Long, coverUrl: String?) {
        if (id <= 0L) return
        val incoming = normalizedCover(coverUrl) ?: return
        coverOverrides[id] = incoming
        _playlists.update { list ->
            var changed = false
            val next = list.map { pl ->
                if (pl.id != id || pl.trackCount <= 0) {
                    pl
                } else if (pl.coverUrl == incoming) {
                    pl
                } else {
                    changed = true
                    pl.copy(coverUrl = incoming)
                }
            }
            if (changed) next else list
        }
    }

    /** 加歌成功：首数 +1，封面跟到这首歌（与歌单内第一首一致）。 */
    fun applyAddedTrack(id: Long, coverUrl: String?) {
        if (id <= 0L) return
        val incoming = normalizedCover(coverUrl)
        if (incoming != null) {
            coverOverrides[id] = incoming
        }
        _playlists.update { list ->
            list.map { pl ->
                if (pl.id != id) {
                    pl
                } else {
                    pl.copy(
                        trackCount = pl.trackCount + 1,
                        coverUrl = incoming ?: pl.coverUrl,
                    )
                }
            }
        }
    }

    fun setSubscribed(id: Long, subscribed: Boolean, insert: PlaylistSummary? = null) {
        if (id <= 0L) return
        subscribeOverrides[id] = subscribed
        _playlists.update { list -> applySubscribe(list, id, subscribed, insert) }
    }

    fun clear() {
        _selfUserId.value = 0L
        _playlists.value = emptyList()
        subscribeOverrides.clear()
        coverOverrides.clear()
    }

    private fun applySubscribe(
        list: List<PlaylistSummary>,
        id: Long,
        subscribed: Boolean,
        insert: PlaylistSummary?,
    ): List<PlaylistSummary> {
        if (subscribed) {
            val existing = list.find { it.id == id }
            return when {
                existing != null -> list.map { pl ->
                    if (pl.id == id) {
                        pl.copy(isSubscribed = true)
                    } else {
                        pl
                    }
                }
                insert != null -> list + insert.copy(
                    id = id,
                    isSubscribed = true,
                    isOwned = false,
                    isHeartPlaylist = false,
                )
                else -> list
            }
        }
        return list.filter { it.id != id || it.isOwned }
    }

    private fun normalizedCover(url: String?): String? =
        url?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            ?.takeUnless { isDefaultPlaylistCover(it) }

    private fun coverFingerprint(url: String?): String? {
        val raw = normalizedCover(url) ?: return null
        return raw.substringBefore('?').substringAfterLast('/').lowercase()
    }
}
