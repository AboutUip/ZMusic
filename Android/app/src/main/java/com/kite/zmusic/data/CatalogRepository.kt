package com.kite.zmusic.data

data class CatalogApiAck(
    val ok: Boolean,
    val message: String,
)

data class AlbumLoadPayload(
    val album: AlbumBrief?,
    val dynamic: AlbumDynamic?,
    val error: String?,
)

data class ArtistSongsPage(
    val songs: List<TrackRow>,
    val hasMore: Boolean,
    val total: Int,
)

/**
 * 目录页（日推 / 漫游 / 歌单 / 专辑 / 榜单）的 HTTP + 解析。
 */
class CatalogRepository(
    private val userClient: NcmUserClient,
) {
    suspend fun personalFm(cookie: String): Pair<List<TrackRow>, String?> {
        val json = userClient.personalFm(cookie)
        val tracks = NcmHomeParse.personalFmTracks(json)
        val err = if (tracks.isEmpty()) {
            NcmJson.userFacingMessage(json, "暂时没有漫游歌曲")
        } else {
            null
        }
        return tracks to err
    }

    suspend fun intelligenceList(
        cookie: String,
        songId: Long,
        playlistId: Long,
        startSongId: Long = songId,
    ): Pair<List<TrackRow>, String?> {
        val json = userClient.playmodeIntelligenceList(
            songId = songId,
            playlistId = playlistId,
            cookie = cookie,
            startSongId = startSongId,
        )
        val tracks = NcmHomeParse.intelligenceTracks(json)
        val err = if (tracks.isEmpty()) {
            NcmJson.userFacingMessage(json, "暂时没有心动歌曲")
        } else {
            null
        }
        return tracks to err
    }

    suspend fun charts(cookie: String): Pair<List<ChartSummary>, String?> {
        val json = userClient.toplistDetail(cookie)
        val list = NcmHomeParse.charts(json)
        val err = if (list.isEmpty()) NcmJson.userFacingMessage(json, "暂时没有榜单") else null
        return list to err
    }

    suspend fun trackById(id: Long, cookie: String): TrackRow? {
        val json = userClient.songDetail(listOf(id), cookie)
        return NcmLibraryParse.tracksFromSongDetail(json).firstOrNull()
    }

    suspend fun playlistSubscribe(id: Long, subscribe: Boolean, cookie: String): CatalogApiAck {
        val json = userClient.playlistSubscribe(id, subscribe, cookie)
        val ok = NcmJson.apiCode(json) == 200
        val fallback = if (subscribe) "收藏失败" else "取消收藏失败"
        return CatalogApiAck(ok, if (ok) "" else NcmJson.userFacingMessage(json, fallback))
    }

    suspend fun albumSubscribe(id: Long, subscribe: Boolean, cookie: String): CatalogApiAck {
        val json = userClient.albumSub(id, subscribe, cookie)
        val ok = NcmJson.apiCode(json) == 200
        val fallback = if (subscribe) "收藏失败" else "取消收藏失败"
        return CatalogApiAck(ok, if (ok) "" else NcmJson.userFacingMessage(json, fallback))
    }

    suspend fun unlikeSong(id: Long, cookie: String): CatalogApiAck {
        val json = userClient.likeSong(id, like = false, cookie)
        val ok = NcmJson.apiCode(json) == 200
        return CatalogApiAck(ok, if (ok) "" else NcmJson.userFacingMessage(json, "移除失败"))
    }

    suspend fun deletePlaylistTracks(playlistId: Long, ids: List<Long>, cookie: String): CatalogApiAck {
        val json = userClient.playlistTracks("del", playlistId, ids, cookie)
        val ok = NcmJson.apiCode(json) == 200
        return CatalogApiAck(ok, if (ok) "" else NcmJson.userFacingMessage(json, "移除失败"))
    }

    suspend fun playlistMeta(playlistId: Long, cookie: String): PlaylistSubscribeMeta? =
        NcmLibraryParse.playlistMetaFromDetail(
            userClient.playlistDetail(playlistId, cookie, limit = 1),
        )

    suspend fun playlistSubscribed(playlistId: Long, cookie: String): Boolean? =
        NcmLibraryParse.subscribedFromDynamic(
            userClient.playlistDetailDynamic(playlistId, cookie),
        )

    suspend fun loadAlbum(id: Long, cookie: String): AlbumLoadPayload {
        val json = userClient.album(id, cookie)
        val album = NcmHomeParse.albumBrief(json, id)
        val dynamic = runCatching {
            NcmHomeParse.albumDynamic(userClient.albumDetailDynamic(id, cookie))
        }.getOrNull()
        val error = if (album == null || album.songs.isEmpty()) {
            NcmJson.userFacingMessage(json, "专辑加载失败")
        } else {
            null
        }
        return AlbumLoadPayload(album, dynamic, error)
    }

    suspend fun albumDynamic(id: Long, cookie: String): AlbumDynamic? =
        NcmHomeParse.albumDynamic(userClient.albumDetailDynamic(id, cookie))

    suspend fun artistSongs(
        artistId: Long,
        cookie: String,
        limit: Int,
        offset: Int,
    ): Pair<ArtistSongsPage, String?> {
        val json = userClient.artistSongs(artistId, cookie, order = "hot", limit = limit, offset = offset)
        if (NcmJson.apiCode(json) != 200) {
            return ArtistSongsPage(emptyList(), false, 0) to NcmJson.userFacingMessage(json, "暂时没有歌曲")
        }
        val (songs, more, total) = NcmArtistParse.songsPage(json)
        return ArtistSongsPage(songs, more, total) to null
    }

    suspend fun artistTopSongs(artistId: Long, cookie: String): List<TrackRow> {
        val json = userClient.artistTopSongs(artistId, cookie)
        return if (NcmJson.apiCode(json) == 200) NcmArtistParse.topSongs(json) else emptyList()
    }

    fun mergeLoadedInOrder(
        ids: List<Long>,
        fresh: List<TrackRow>,
        previous: List<TrackRow>,
    ): List<TrackRow> = NcmLibraryParse.mergeLoadedInOrder(ids, fresh, previous)
}
