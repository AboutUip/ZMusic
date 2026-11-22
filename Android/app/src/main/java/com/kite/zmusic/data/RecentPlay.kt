package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject

/** 最近播放只留在本机。歌曲 500 首，MV 100 条，新的在前。 */
internal const val RecentSongCap = 500
internal const val RecentMvCap = 100

/** 列表先露出一页，滑到底再加。搜索不走这一页，直接查全部。 */
internal const val RecentPageSize = 40

data class RecentSong(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String?,
    val durationMs: Long,
    val coverUrl: String?,
    val sourceId: String?,
    val sourceHash: String?,
    val albumMid: String?,
    val sourceSongId: Long,
    val fee: Int,
    val localAudioUri: String?,
    val localFolder: String?,
    val localLyricUri: String?,
    val localTransLyricUri: String?,
    val artistRefs: List<TrackArtist>,
    val playedAt: Long,
)

data class RecentMv(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val artist: String?,
    val playedAt: Long,
)

internal data class RecentPlaySnapshot(
    val songs: List<RecentSong> = emptyList(),
    val mvs: List<RecentMv> = emptyList(),
)

internal data class RecentSlice<T>(
    val shown: List<T>,
    val playable: List<T>,
    val hasMore: Boolean,
)

internal fun TrackRow.toRecentSong(playedAt: Long) = RecentSong(
    id = id,
    name = name,
    artists = artists,
    album = album,
    durationMs = durationMs,
    coverUrl = coverUrl,
    sourceId = sourceId,
    sourceHash = sourceHash,
    albumMid = albumMid,
    sourceSongId = sourceSongId,
    fee = fee,
    localAudioUri = localAudioUri,
    localFolder = localFolder,
    localLyricUri = localLyricUri,
    localTransLyricUri = localTransLyricUri,
    artistRefs = artistRefs,
    playedAt = playedAt,
)

internal fun RecentSong.toTrackRow() = TrackRow(
    id = id,
    name = name,
    artists = artists,
    album = album,
    durationMs = durationMs,
    coverUrl = coverUrl,
    sourceId = sourceId,
    sourceHash = sourceHash,
    albumMid = albumMid,
    sourceSongId = sourceSongId,
    artistRefs = artistRefs,
    localAudioUri = localAudioUri,
    localFolder = localFolder,
    localLyricUri = localLyricUri,
    localTransLyricUri = localTransLyricUri,
    fee = fee,
)

internal fun RecommendMvCard.toRecentMv(playedAt: Long) = RecentMv(
    id = id,
    name = name,
    coverUrl = coverUrl,
    artist = artist,
    playedAt = playedAt,
)

internal fun <T> recentPush(
    existing: List<T>,
    item: T,
    cap: Int,
    same: (T, T) -> Boolean,
): List<T> {
    if (cap <= 0) return emptyList()
    val next = ArrayList<T>(minOf(existing.size + 1, cap))
    next.add(item)
    for (old in existing) {
        if (next.size >= cap) break
        if (!same(old, item)) next.add(old)
    }
    return next
}

/** 磁盘读回来之前若已经记下新的，新的留在前面，再接上还没出现过的旧记录。 */
internal fun <T> recentMergeFront(
    fresh: List<T>,
    stored: List<T>,
    cap: Int,
    id: (T) -> Long,
): List<T> {
    if (cap <= 0) return emptyList()
    val seen = HashSet<Long>()
    val out = ArrayList<T>(cap)
    for (item in fresh) {
        if (out.size >= cap) return out
        if (seen.add(id(item))) out.add(item)
    }
    for (item in stored) {
        if (out.size >= cap) break
        if (seen.add(id(item))) out.add(item)
    }
    return out
}

internal fun recentSongMatches(song: RecentSong, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return song.name.contains(q, ignoreCase = true) ||
        song.artists.contains(q, ignoreCase = true) ||
        song.album.orEmpty().contains(q, ignoreCase = true)
}

internal fun recentMvMatches(mv: RecentMv, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return mv.name.contains(q, ignoreCase = true) ||
        mv.artist.orEmpty().contains(q, ignoreCase = true)
}

/**
 * 空白查询只露出已加载的一截。
 * 有查询时先在全部记录里检索，再按已加载数量露出命中项。
 * [playable] 始终是这次要播的完整结果，不限于已经画出来的行。
 */
internal fun <T> recentSlice(
    all: List<T>,
    query: String,
    loaded: Int,
    matches: (T, String) -> Boolean,
): RecentSlice<T> {
    val q = query.trim()
    val corpus = if (q.isEmpty()) all else all.filter { matches(it, q) }
    val count = if (corpus.isEmpty()) 0 else loaded.coerceIn(0, corpus.size)
    return RecentSlice(
        shown = if (count == 0) emptyList() else corpus.take(count),
        playable = corpus,
        hasMore = count < corpus.size,
    )
}

internal fun recentPlayToJson(songs: List<RecentSong>, mvs: List<RecentMv>): String {
    val root = JSONObject()
    val songArr = JSONArray()
    songs.take(RecentSongCap).forEach { songArr.put(it.toJson()) }
    val mvArr = JSONArray()
    mvs.take(RecentMvCap).forEach { mvArr.put(it.toJson()) }
    root.put("songs", songArr)
    root.put("mvs", mvArr)
    return root.toString()
}

internal fun recentPlayFromJson(text: String): RecentPlaySnapshot {
    val root = runCatching { JSONObject(text) }.getOrNull() ?: return RecentPlaySnapshot()
    return RecentPlaySnapshot(
        songs = root.optJSONArray("songs").toRecentSongs(),
        mvs = root.optJSONArray("mvs").toRecentMvs(),
    )
}

private fun RecentSong.toJson(): JSONObject {
    val refs = JSONArray()
    artistRefs.forEach { artist ->
        val one = JSONObject()
        one.put("id", artist.id as Any)
        one.put("name", artist.name)
        refs.put(one)
    }
    val obj = JSONObject()
    obj.put("id", id as Any)
    obj.put("name", name)
    obj.put("artists", artists)
    obj.put("album", album ?: JSONObject.NULL)
    obj.put("durationMs", durationMs as Any)
    obj.put("coverUrl", coverUrl ?: JSONObject.NULL)
    obj.put("sourceId", sourceId ?: JSONObject.NULL)
    obj.put("sourceHash", sourceHash ?: JSONObject.NULL)
    obj.put("albumMid", albumMid ?: JSONObject.NULL)
    obj.put("sourceSongId", sourceSongId as Any)
    obj.put("fee", fee as Any)
    obj.put("localAudioUri", localAudioUri ?: JSONObject.NULL)
    obj.put("localFolder", localFolder ?: JSONObject.NULL)
    obj.put("localLyricUri", localLyricUri ?: JSONObject.NULL)
    obj.put("localTransLyricUri", localTransLyricUri ?: JSONObject.NULL)
    obj.put("artistRefs", refs)
    obj.put("playedAt", playedAt as Any)
    return obj
}

private fun RecentMv.toJson(): JSONObject {
    val obj = JSONObject()
    obj.put("id", id as Any)
    obj.put("name", name)
    obj.put("coverUrl", coverUrl ?: JSONObject.NULL)
    obj.put("artist", artist ?: JSONObject.NULL)
    obj.put("playedAt", playedAt as Any)
    return obj
}

private fun JSONArray?.toRecentSongs(): List<RecentSong> {
    if (this == null) return emptyList()
    val out = ArrayList<RecentSong>(minOf(length(), RecentSongCap))
    val seen = HashSet<Long>()
    for (i in 0 until length()) {
        if (out.size >= RecentSongCap) break
        val obj = optJSONObject(i) ?: continue
        val id = obj.optLong("id")
        if (id <= 0L || !seen.add(id)) continue
        val refs = obj.optJSONArray("artistRefs")
        val artists = ArrayList<TrackArtist>()
        if (refs != null) {
            for (r in 0 until refs.length()) {
                val one = refs.optJSONObject(r) ?: continue
                val name = one.optString("name")
                if (name.isBlank()) continue
                artists.add(TrackArtist(one.optLong("id"), name))
            }
        }
        out.add(
            RecentSong(
                id = id,
                name = obj.optString("name"),
                artists = obj.optString("artists"),
                album = obj.optNullableString("album"),
                durationMs = obj.optLong("durationMs"),
                coverUrl = obj.optNullableString("coverUrl"),
                sourceId = obj.optNullableString("sourceId"),
                sourceHash = obj.optNullableString("sourceHash"),
                albumMid = obj.optNullableString("albumMid"),
                sourceSongId = obj.optLong("sourceSongId"),
                fee = obj.optInt("fee"),
                localAudioUri = obj.optNullableString("localAudioUri"),
                localFolder = obj.optNullableString("localFolder"),
                localLyricUri = obj.optNullableString("localLyricUri"),
                localTransLyricUri = obj.optNullableString("localTransLyricUri"),
                artistRefs = artists,
                playedAt = obj.optLong("playedAt"),
            ),
        )
    }
    return out
}

private fun JSONArray?.toRecentMvs(): List<RecentMv> {
    if (this == null) return emptyList()
    val out = ArrayList<RecentMv>(minOf(length(), RecentMvCap))
    val seen = HashSet<Long>()
    for (i in 0 until length()) {
        if (out.size >= RecentMvCap) break
        val obj = optJSONObject(i) ?: continue
        val id = obj.optLong("id")
        if (id <= 0L || !seen.add(id)) continue
        out.add(
            RecentMv(
                id = id,
                name = obj.optString("name"),
                coverUrl = obj.optNullableString("coverUrl"),
                artist = obj.optNullableString("artist"),
                playedAt = obj.optLong("playedAt"),
            ),
        )
    }
    return out
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key)
    return value.takeIf { it.isNotEmpty() }
}
