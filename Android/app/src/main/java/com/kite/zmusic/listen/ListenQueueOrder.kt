package com.kite.zmusic.listen

import com.kite.zmusic.data.TrackRow
import org.json.JSONArray
import org.json.JSONObject

/** 一起听共享曲谱上限。双人和多人用同一份，不按人数分叉。 */
const val ListenSharedQueueCap = 300

data class ListenQueueTrack(
    val id: Long,
    val title: String,
    val artists: String,
    val coverUrl: String,
    val durationMs: Long,
)

fun ListenQueueTrack.toTrackRow(): TrackRow = TrackRow(
    id = id,
    name = title,
    artists = artists,
    album = null,
    durationMs = durationMs.coerceAtLeast(0L),
    coverUrl = coverUrl.ifBlank { null },
)

fun listenQueueTracksForPost(
    tracks: List<TrackRow>,
    cap: Int = ListenSharedQueueCap,
): List<ListenQueueTrack> {
    if (cap <= 0) return emptyList()
    val out = ArrayList<ListenQueueTrack>(minOf(tracks.size, cap))
    val seen = HashSet<Long>()
    for (row in tracks) {
        if (row.id <= 0L || !seen.add(row.id)) continue
        out += ListenQueueTrack(
            id = row.id,
            title = row.name,
            artists = row.artists,
            coverUrl = row.coverUrl.orEmpty(),
            durationMs = row.durationMs.coerceAtLeast(0L),
        )
        if (out.size >= cap) break
    }
    return out
}

fun listenQueueOpJson(tracks: List<TrackRow>): JSONObject? {
    val rows = listenQueueTracksForPost(tracks)
    if (rows.isEmpty()) return null
    val arr = JSONArray()
    for (row in rows) {
        arr.put(
            JSONObject()
                .put("id", row.id)
                .put("title", row.title)
                .put("artists", row.artists)
                .put("cover_url", row.coverUrl)
                .put("duration_ms", row.durationMs),
        )
    }
    return JSONObject().put("kind", "queue").put("tracks", arr)
}

/**
 * 用房间曲谱重排本机列表。同一 id 留本机那条（封面等更全）。
 * 正在播的 id 不在房间曲谱里时插回原位置，避免把当前这首丢掉。
 */
fun mergeSharedListenQueue(
    local: List<TrackRow>,
    shared: List<TrackRow>,
    currentId: Long,
): List<TrackRow> {
    val localById = LinkedHashMap<Long, TrackRow>(local.size)
    for (row in local) {
        if (row.id > 0L) localById.putIfAbsent(row.id, row)
    }
    val out = ArrayList<TrackRow>(shared.size)
    val seen = HashSet<Long>()
    for (row in shared) {
        if (row.id <= 0L || !seen.add(row.id)) continue
        out += localById[row.id] ?: row
    }
    if (currentId > 0L && currentId !in seen) {
        val current = localById[currentId] ?: return out
        val at = local.indexOfFirst { it.id == currentId }.coerceIn(0, out.size)
        out.add(at, current)
    }
    return out
}

/**
 * 房间切到一首已经在曲谱里的歌时，跳到它所在的下标。
 * 不把它抽出来插到当前曲后面，否则共享顺序会被下一次切歌打乱。
 * 返回 -1 表示曲谱里没有，调用方再插到当前曲后面。
 */
fun listenSharedTrackIndex(queueIds: List<Long>, incomingId: Long): Int {
    if (incomingId <= 0L) return -1
    return queueIds.indexOf(incomingId)
}
