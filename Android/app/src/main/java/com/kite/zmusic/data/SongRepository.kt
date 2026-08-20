package com.kite.zmusic.data

/**
 * 单曲查询：详情、歌手解析、红心核对。Compose 不要直接打这些接口。
 */
class SongRepository(
    private val userClient: NcmUserClient,
) {
    suspend fun tracksByIds(ids: List<Long>, cookie: String): List<TrackRow> {
        if (ids.isEmpty()) return emptyList()
        val json = userClient.songDetail(ids, cookie)
        return NcmLibraryParse.tracksFromSongDetail(json)
    }

    suspend fun trackById(id: Long, cookie: String): TrackRow? =
        tracksByIds(listOf(id), cookie).firstOrNull()

    suspend fun resolveTrackArtists(track: TrackRow, cookie: String): List<TrackArtist> {
        val local = track.artistRefs.filter { it.name.isNotBlank() && it.name != "—" && it.id > 0L }
        if (local.isNotEmpty()) return local
        val json = runCatching { userClient.songDetail(listOf(track.id), cookie) }.getOrNull()
            ?: return emptyList()
        val songs = json.optJSONArray("songs") ?: return emptyList()
        val first = songs.optJSONObject(0) ?: return emptyList()
        val ar = first.optJSONArray("ar") ?: first.optJSONArray("artists") ?: return emptyList()
        return NcmLibraryParse.artistRefsFromArray(ar).filter { it.id > 0L }
    }

    suspend fun isTrackLiked(id: Long, cookie: String): Boolean? = runCatching {
        val json = userClient.songLikeCheck(listOf(id), cookie)
        NcmLibraryParse.isTrackLiked(json, id)
    }.getOrNull()

    suspend fun likeSong(id: Long, like: Boolean, cookie: String): CatalogApiAck {
        val json = userClient.likeSong(id, like, cookie)
        val ok = NcmJson.apiCode(json) == 200
        return CatalogApiAck(ok, if (ok) "" else NcmJson.userFacingMessage(json, "操作失败"))
    }
}
