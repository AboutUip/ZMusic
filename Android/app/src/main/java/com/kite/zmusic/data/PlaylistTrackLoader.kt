package com.kite.zmusic.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 歌单曲目按需加载：
 * - 进页只拉前 [FIRST_BATCH] 首，50 首与 5000 首的进入耗时接近
 * - 继续下滑时按 [PAGE] 补下一批，已加载曲目（含封面）不得被下一页覆盖
 */
internal object PlaylistTrackLoader {
    const val FIRST_BATCH = 40
    const val PAGE = 50
    const val SONG_DETAIL_CHUNK = 50

    data class FirstBatch(
        /** 前 [FIRST_BATCH]（或更少）曲，顺序与 [allIds] 前缀一致 */
        val tracks: List<TrackRow>,
        /** 歌单完整 id 序列 */
        val allIds: List<Long>,
        val complete: Boolean,
        val subscribeMeta: PlaylistSubscribeMeta? = null,
    )

    suspend fun loadFirstBatch(
        userClient: NcmUserClient,
        playlistId: Long,
        cookie: String,
        firstBatch: Int = FIRST_BATCH,
    ): FirstBatch = withContext(Dispatchers.IO) {
        val detail = userClient.playlistDetail(playlistId, cookie, limit = firstBatch.coerceAtLeast(1))
        val meta = NcmLibraryParse.playlistMetaFromDetail(detail)
        val allIds = NcmLibraryParse.trackIdsFromPlaylistDetail(detail)
        val fromDetail = NcmLibraryParse.tracksFromPlaylistDetail(detail)
        if (allIds.isEmpty()) {
            return@withContext FirstBatch(
                tracks = fromDetail,
                allIds = fromDetail.map { it.id },
                complete = true,
                subscribeMeta = meta,
            )
        }
        val byId = LinkedHashMap<Long, TrackRow>(fromDetail.size)
        for (t in fromDetail) byId.putTrack(t)
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
            subscribeMeta = meta,
        )
    }

    /**
     * 只请求 [already] 之后的下一页，返回按 id 顺序的累积列表。
     * [already] 里已有封面的曲目会原样保留，不会用新接口结果覆盖。
     */
    suspend fun loadNextPage(
        userClient: NcmUserClient,
        cookie: String,
        allIds: List<Long>,
        already: List<TrackRow>,
        pageSize: Int = PAGE,
        playlistId: Long = 0L,
    ): List<TrackRow> = withContext(Dispatchers.IO) {
        if (allIds.isEmpty()) return@withContext already
        val byId = LinkedHashMap<Long, TrackRow>(already.size + pageSize)
        for (t in already) byId.putTrack(t)
        val offset = already.size
        if (offset >= allIds.size) {
            return@withContext allIds.mapNotNull { byId[it] }
        }
        val chunkIds = allIds.drop(offset).take(pageSize.coerceAtLeast(1))
        if (chunkIds.isEmpty()) {
            return@withContext allIds.mapNotNull { byId[it] }
        }
        if (playlistId > 0L) {
            val fromAll = runCatching {
                NcmLibraryParse.tracksFromSongDetail(
                    userClient.playlistTrackAll(playlistId, cookie, chunkIds.size, offset),
                )
            }.getOrDefault(emptyList())
            for (t in fromAll) byId.putTrack(t)
        }
        val missing = chunkIds.filter { it !in byId }
        if (missing.isNotEmpty()) {
            fetchSongsInto(userClient, cookie, missing, byId)
        }
        allIds.take(offset + chunkIds.size).mapNotNull { byId[it] }
    }

    /**
     * 按给定 id 序取曲目。 [known] 只做缓存，不能当成「已经是列表前缀」，
     * 否则刷新红心时旧歌会占掉 offset，最新喜欢被跳过。
     */
    suspend fun loadOrderedIds(
        userClient: NcmUserClient,
        cookie: String,
        ids: List<Long>,
        known: List<TrackRow> = emptyList(),
    ): List<TrackRow> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = LinkedHashMap<Long, TrackRow>(ids.size)
        for (t in known) byId.putTrack(t)
        val missing = ids.filter { it !in byId }
        if (missing.isNotEmpty()) {
            fetchSongsInto(userClient, cookie, missing, byId)
        }
        ids.mapNotNull { byId[it] }
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
                for (t in rows) into.putTrack(t)
            }.onFailure {
                Log.w(TAG, "song/detail chunk failed size=${chunk.size}", it)
            }
        }
    }

    private fun MutableMap<Long, TrackRow>.putTrack(incoming: TrackRow) {
        this[incoming.id] = NcmLibraryParse.preferExistingCover(this[incoming.id], incoming)
    }

    private const val TAG = "PlaylistTrackLoader"
}
