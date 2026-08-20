package com.kite.zmusic.data

import org.json.JSONObject

class SearchRepository(
    private val userClient: NcmUserClient,
) {
    suspend fun searchHotDetail(cookie: String): JSONObject = userClient.searchHotDetail(cookie)

    suspend fun searchSuggest(keywords: String, cookie: String, mobile: Boolean): JSONObject =
        userClient.searchSuggest(keywords, cookie, mobile = mobile)

    suspend fun search(
        keywords: String,
        cookie: String,
        type: Int,
        limit: Int,
        offset: Int,
    ): JSONObject = userClient.search(keywords, cookie, type = type, limit = limit, offset = offset)

    suspend fun cloudSearch(
        keywords: String,
        cookie: String,
        type: Int,
        limit: Int,
        offset: Int,
    ): JSONObject = userClient.cloudSearch(
        keywords, cookie, type = type, limit = limit, offset = offset,
    )
}
