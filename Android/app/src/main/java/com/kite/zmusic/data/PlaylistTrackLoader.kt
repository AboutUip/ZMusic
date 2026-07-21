package com.kite.zmusic.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 歌单曲目渐进加载：
 * - [trackIds] 为完整顺序（文档约定；[tracks] 常不完整）
 * - 先组装前 [FIRST_BATCH] 首，快速返回
 * - 其余按块拉取，经 [onProgress] 回传「按 id 顺序拼好的累积列表」
 */
internal object PlaylistTrackLoader {
    const val FIRST_BATCH = 500
    const val SONG_DETAIL_CHUNK = 200

    data class FirstBatch(
        /** 前 [FIRST_BATCH]（或更少）曲，顺序与 [allIds] 前缀一致 */
        val tracks: List<TrackRow>,
        /** 歌单完整 id 序列 */
        val allIds: List<Long>,
        val complete: Boolean,
    )

    suspend fun loadFirstBatch(
        userClient: NcmUserClient,
        playlistId: Long,
        cookie: String,
        firstBatch: Int = FIRST_BATCH,
    ): FirstBatch = withContext(Dispatchers.IO) {
        val detail = userClient.playlistDetail(playlistId, cookie, limit = firstBatch.coerceAtLeast(1))
        val allIds = NcmLibraryParse.trackIdsFromPlaylistDetail(detail)
        val fromDetail = NcmLibraryParse.tracksFromPlaylistDetail(detail)
        if (allIds.isEmpty()) {
            // 无 trackIds 时退回 tracks（可能不完整）
            return@withContext FirstBatch(
                tracks = fromDetail,
                allIds = fromDetail.map { it.id },
                complete = true,
            )
        }
        val byId = fromDetail.associateBy { it.id }.toMutableMap()
        val headIds = allIds.take(firstBatch.coerceAtLeast(1))
        val missing = headIds.filter { it !in byId }
        if (missing.isNotEmpty()) {
            fetchSongsInto(userClient, cookie, missing, byId)
        }
        val head = headIds.mapNotNull { byId[it] }
        FirstBatch(
            tracks = head,
            allIds = allIds,
            complete = head.size >= allIds.size,
        )
    }

    /**
     * 拉取 [allIds] 中尚未出现在 [already] 的歌曲，按完整顺序组装。
     * 每完成一块调用 [onProgress]（累积完整前缀，含已有 + 新拉）。
     */
    suspend fun loadRemaining(
        userClient: NcmUserClient,
        cookie: String,
        allIds: List<Long>,
        already: List<TrackRow>,
        onProgress: suspend (List<TrackRow>) -> Unit,
    ): List<TrackRow> = withContext(Dispatchers.IO) {
        if (allIds.isEmpty()) return@withContext already
        val byId = already.associateBy { it.id }.toMutableMap()
        val missing = allIds.filter { it !in byId }
        if (missing.isEmpty()) {
            val ordered = allIds.mapNotNull { byId[it] }
            onProgress(ordered)
            return@withContext ordered
        }
        var loaded = 0
        for (chunk in missing.chunked(SONG_DETAIL_CHUNK)) {
            fetchSongsInto(userClient, cookie, chunk, byId)
            loaded += chunk.size
            val ordered = allIds.mapNotNull { byId[it] }
            onProgress(ordered)
            Log.d(TAG, "playlist fill $loaded/${missing.size} (+have ${already.size})")
        }
        allIds.mapNotNull { byId[it] }
    }

    private suspend fun fetchSongsInto(
        userClient: NcmUserClient,
        cookie: String,
        ids: List<Long>,
        into: MutableMap<Long, TrackRow>,
    ) {
        for (chunk in ids.chunked(SONG_DETAIL_CHUNK)) {
            runCatching {
                NcmLibraryParse.tracksFromSongDetail(userClient.songDetail(chunk, cookie))
            }.onSuccess { rows ->
                for (t in rows) into[t.id] = t
            }.onFailure {
                Log.w(TAG, "song/detail chunk failed size=${chunk.size}", it)
            }
        }
    }

    private const val TAG = "PlaylistTrackLoader"
}
