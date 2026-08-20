package com.kite.zmusic.data

import org.json.JSONObject

class ArtistRepository(
    private val userClient: NcmUserClient,
    private val authClient: NcmAuthClient,
) {
    suspend fun detail(id: Long, cookie: String): JSONObject = userClient.artistDetail(id, cookie)

    suspend fun detailDynamic(id: Long, cookie: String): JSONObject =
        userClient.artistDetailDynamic(id, cookie)

    suspend fun topSongs(id: Long, cookie: String): JSONObject = userClient.artistTopSongs(id, cookie)

    suspend fun desc(id: Long, cookie: String): JSONObject = userClient.artistDesc(id, cookie)

    suspend fun similar(id: Long, cookie: String): JSONObject = userClient.simiArtist(id, cookie)

    suspend fun albums(id: Long, cookie: String, limit: Int, offset: Int): JSONObject =
        userClient.artistAlbums(id, cookie, limit = limit, offset = offset)

    suspend fun mvs(id: Long, cookie: String, limit: Int, offset: Int): JSONObject =
        userClient.artistMv(id, cookie, limit = limit, offset = offset)

    suspend fun subscribe(id: Long, follow: Boolean, cookie: String): JSONObject =
        userClient.artistSub(id, follow, cookie)

    suspend fun sublist(cookie: String, limit: Int, offset: Int): JSONObject =
        userClient.artistSublist(cookie, limit = limit, offset = offset)

    suspend fun userFollows(uid: Long, cookie: String, limit: Int, offset: Int): JSONObject =
        userClient.userFollows(uid, cookie, limit = limit, offset = offset)

    suspend fun loginUserId(cookie: String): Long =
        NcmJson.userIdFromLoginStatus(authClient.loginStatus(cookie)) ?: 0L
}
