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
                val insertAt = list.indexOfFirst { it.isOwned && !it.isHeartPlaylist }
                    .takeIf { it >= 0 } ?: (list.indexOfLast { it.isHeartPlaylist } + 1)
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

    /** 详情页确认了歌单自身封面：写回列表并清掉首曲误覆盖。 */
    fun restorePlaylistCover(id: Long, coverUrl: String?) {
        if (id <= 0L) return
        val incoming = normalizedCover(coverUrl)
        if (incoming != null) {
            coverOverrides.remove(id)
        }
        _playlists.update { list ->
            var changed = false
            val next = list.map { pl ->
                if (pl.id != id || pl.trackCount <= 0) {
                    pl
                } else if (incoming == null || pl.coverUrl == incoming) {
                    pl
                } else {
                    changed = true
                    pl.copy(coverUrl = incoming)
                }
            }
            if (changed) next else list
        }
    }

    /** 仅在列表还没有真实封面时写入；不覆盖已有歌单封面。 */
    fun syncCover(id: Long, coverUrl: String?) {
        if (id <= 0L) return
        val incoming = normalizedCover(coverUrl) ?: return
        _playlists.update { list ->
            var changed = false
            val next = list.map { pl ->
                if (pl.id != id || pl.trackCount <= 0) {
                    pl
                } else {
                    val existing = pl.resolvedCoverUrl()
                    when {
                        existing != null -> pl
                        pl.coverUrl == incoming -> pl
                        else -> {
                            coverOverrides[id] = incoming
                            changed = true
                            pl.copy(coverUrl = incoming)
                        }
                    }
                }
            }
            if (changed) next else list
        }
    }

    /** 加歌成功：首数 +1；仅空封面歌单才跟到这首歌。 */
    fun applyAddedTrack(id: Long, coverUrl: String?) {
        if (id <= 0L) return
        val incoming = normalizedCover(coverUrl)
        _playlists.update { list ->
            list.map { pl ->
                if (pl.id != id) {
                    pl
                } else {
                    val keepCover = pl.resolvedCoverUrl() != null
                    if (!keepCover && incoming != null) {
                        coverOverrides[id] = incoming
                    }
                    pl.copy(
                        trackCount = pl.trackCount + 1,
                        coverUrl = if (keepCover) pl.coverUrl else (incoming ?: pl.coverUrl),
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
            /**
             * 说实话不用java视角我也有点看不懂
             * 这里的map不是传统的key-value键值对
             * 这里是对列表里的每一个元素(起名叫 pl),执行大括号里的逻辑，把返回值收集起来，得到一个新列表。
             * 类似于list.stram().map(x->f(x)).collect(toList())
             */
            if (existing != null) {
                return list.map { pl ->
                    //如果歌单列表中有我需要的那个歌单
                    if (pl.id == id) {
                        pl.copy(
                            //克隆 pl
                            isSubscribed = true,
                            //如果没有收藏时间就用系统时间
                            subscribedTime = if (pl.subscribedTime > 0L) pl.subscribedTime else System.currentTimeMillis(),
                        )
                    } else {
                        pl
                    }
                }
            }
            if (insert == null) return list
            val fresh = insert.copy(
                id = id,
                isSubscribed = true,
                isOwned = false,
                isHeartPlaylist = false,
                subscribedTime = System.currentTimeMillis(),
            )
            //找第一个“非自建”歌单的下标
            val insertAt = list.indexOfFirst { !it.isOwned }
                .takeIf { it >= 0 } ?: list.size

            return list.take(insertAt) + fresh + list.drop(insertAt)
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
