package com.kite.zmusic.data

import com.kite.zmusic.config.NcmApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** 播客听众接口。失败时返回空对象，由解析层跳过。 */
class NcmPodcastClient(
    private val client: OkHttpClient,
) {
    suspend fun djBanner(cookie: String): JSONObject = get("/dj/banner", cookie)

    suspend fun djPersonalize(cookie: String, limit: Int = 12): JSONObject =
        get("/dj/personalize/recommend", cookie, mapOf("limit" to limit.toString()))

    suspend fun djRecommend(cookie: String): JSONObject = get("/dj/recommend", cookie)

    suspend fun djToday(cookie: String): JSONObject = get("/dj/today/perfered", cookie)

    suspend fun djHot(cookie: String, limit: Int = 12): JSONObject =
        get("/dj/hot", cookie, mapOf("limit" to limit.toString()))

    suspend fun djCatelist(cookie: String): JSONObject = get("/dj/catelist", cookie)

    suspend fun djRecommendType(cookie: String, type: Long, limit: Int = 30, offset: Int = 0): JSONObject =
        get(
            "/dj/recommend/type",
            cookie,
            mapOf("type" to type.toString(), "limit" to limit.toString(), "offset" to offset.toString()),
        )

    suspend fun djToplist(cookie: String, type: String, limit: Int = 30): JSONObject =
        get("/dj/toplist", cookie, mapOf("type" to type, "limit" to limit.toString()))

    suspend fun djToplistPay(cookie: String, limit: Int = 30): JSONObject =
        get("/dj/toplist/pay", cookie, mapOf("limit" to limit.toString()))

    suspend fun djProgramToplist(cookie: String, limit: Int = 30): JSONObject =
        get("/dj/program/toplist", cookie, mapOf("limit" to limit.toString()))

    suspend fun djProgramToplistHours(cookie: String, limit: Int = 20): JSONObject =
        get("/dj/program/toplist/hours", cookie, mapOf("limit" to limit.toString()))

    suspend fun djToplistHours(cookie: String, limit: Int = 20): JSONObject =
        get("/dj/toplist/hours", cookie, mapOf("limit" to limit.toString()))

    suspend fun djToplistNewcomer(cookie: String, limit: Int = 20): JSONObject =
        get("/dj/toplist/newcomer", cookie, mapOf("limit" to limit.toString()))

    suspend fun djToplistPopular(cookie: String, limit: Int = 20): JSONObject =
        get("/dj/toplist/popular", cookie, mapOf("limit" to limit.toString()))

    suspend fun djPaygift(cookie: String, limit: Int = 12): JSONObject =
        get("/dj/paygift", cookie, mapOf("limit" to limit.toString()))

    suspend fun djSublist(cookie: String, limit: Int = 30, offset: Int = 0): JSONObject =
        get("/dj/sublist", cookie, mapOf("limit" to limit.toString(), "offset" to offset.toString()))

    suspend fun djDetail(cookie: String, rid: Long): JSONObject =
        get("/dj/detail", cookie, mapOf("rid" to rid.toString()))

    suspend fun djProgram(
        cookie: String,
        rid: Long,
        limit: Int = 30,
        offset: Int = 0,
        asc: Boolean = false,
    ): JSONObject = get(
        "/dj/program",
        cookie,
        mapOf(
            "rid" to rid.toString(),
            "limit" to limit.toString(),
            "offset" to offset.toString(),
            "asc" to asc.toString(),
        ),
    )

    suspend fun djProgramDetail(cookie: String, id: Long): JSONObject =
        get("/dj/program/detail", cookie, mapOf("id" to id.toString()))

    suspend fun djSub(cookie: String, rid: Long, subscribe: Boolean): JSONObject =
        get("/dj/sub", cookie, mapOf("rid" to rid.toString(), "t" to if (subscribe) "1" else "0"))

    suspend fun search(cookie: String, keywords: String, type: Int, limit: Int = 30, offset: Int = 0): JSONObject =
        get(
            "/cloudsearch",
            cookie,
            mapOf(
                "keywords" to keywords,
                "type" to type.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            ),
        )

    suspend fun commentPost(cookie: String, id: Long, content: String): JSONObject =
        get(
            "/comment",
            cookie,
            mapOf(
                "t" to "1",
                "type" to "4",
                "id" to id.toString(),
                "content" to content,
            ),
        )

    suspend fun commentDj(cookie: String, id: Long, limit: Int = 20, offset: Int = 0): JSONObject =
        get(
            "/comment/dj",
            cookie,
            mapOf("id" to id.toString(), "limit" to limit.toString(), "offset" to offset.toString()),
        )

    suspend fun resourceLike(cookie: String, id: Long, like: Boolean, type: Int): JSONObject =
        get(
            "/resource/like",
            cookie,
            mapOf("id" to id.toString(), "t" to if (like) "1" else "0", "type" to type.toString()),
        )

    suspend fun shareResource(cookie: String, id: Long, type: String): JSONObject =
        get("/share/resource", cookie, mapOf("id" to id.toString(), "type" to type))

    suspend fun recentDj(cookie: String, limit: Int = 30): JSONObject =
        get("/record/recent/dj", cookie, mapOf("limit" to limit.toString()))

    suspend fun recentVoice(cookie: String, limit: Int = 30): JSONObject =
        get("/record/recent/voice", cookie, mapOf("limit" to limit.toString()))

    suspend fun voiceLyric(cookie: String, id: Long): JSONObject =
        get("/voice/lyric", cookie, mapOf("id" to id.toString()))

    suspend fun userAudio(cookie: String, uid: Long): JSONObject =
        get("/user/audio", cookie, mapOf("uid" to uid.toString()))

    suspend fun userAccount(cookie: String): JSONObject = get("/user/account", cookie)

    private suspend fun get(
        path: String,
        cookie: String,
        extra: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        runCatching {
            val query = LinkedHashMap<String, String>(extra.size + 2)
            query.putAll(extra)
            if (cookie.isNotBlank()) query["cookie"] = cookie
            query["timestamp"] = System.currentTimeMillis().toString()
            val full = (NcmApiConfig.baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder()
            query.forEach { (key, value) -> full.addQueryParameter(key, value) }
            val req = Request.Builder().url(full.build()).get().build()
            client.newCall(req).execute().use { resp ->
                JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
            }
        }.getOrElse { JSONObject() }
    }
}
