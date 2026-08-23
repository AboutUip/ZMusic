package com.kite.zmusic.data

import org.json.JSONObject

class UserRepository(
    private val userClient: NcmUserClient,
    private val authClient: NcmAuthClient,
) {
    suspend fun detail(uid: Long, cookie: String): JSONObject =
        userClient.userDetail(uid, cookie)

    suspend fun playlists(
        uid: Long,
        cookie: String,
        limit: Int,
        offset: Int,
    ): JSONObject = userClient.userPlaylist(uid, cookie, limit = limit, offset = offset)

    suspend fun follow(id: Long, follow: Boolean, cookie: String): JSONObject =
        userClient.userFollow(id, follow, cookie)

    suspend fun follows(
        uid: Long,
        cookie: String,
        limit: Int,
        offset: Int,
    ): JSONObject = userClient.userFollows(uid, cookie, limit = limit, offset = offset)

    suspend fun followeds(
        uid: Long,
        cookie: String,
        limit: Int,
        offset: Int,
    ): JSONObject = userClient.userFolloweds(uid, cookie, limit = limit, offset = offset)

    suspend fun record(uid: Long, cookie: String, type: Int): JSONObject =
        userClient.userRecord(uid, cookie, type)

    suspend fun medal(uid: Long, cookie: String): JSONObject =
        userClient.userMedal(uid, cookie)

    suspend fun loginUserId(cookie: String): Long =
        NcmJson.userIdFromLoginStatus(authClient.loginStatus(cookie)) ?: 0L
}
