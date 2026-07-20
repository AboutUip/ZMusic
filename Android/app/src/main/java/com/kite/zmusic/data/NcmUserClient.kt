package com.kite.zmusic.data

import com.kite.zmusic.config.NcmApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 用户与歌单相关 GET（携带 cookie），与 [NcmAuthClient] 共用同一套 API 基址。
 */
class NcmUserClient(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun userDetail(uid: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/user/detail",
            mapOf("uid" to uid.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun userPlaylist(uid: Long, cookie: String, limit: Int = 60, offset: Int = 0): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/user/playlist",
                mapOf(
                    "uid" to uid.toString(),
                    "limit" to limit.toString(),
                    "offset" to offset.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun userSubcount(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/user/subcount", mapOf("cookie" to cookie, "timestamp" to ts()))
    }

    suspend fun likeList(uid: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/likelist", mapOf("uid" to uid.toString(), "cookie" to cookie, "timestamp" to ts()))
    }

    /**
     * 批量检查歌曲是否已喜爱。
     * `ids` 按文档传方括号列表，如 `[2058263032,1497529942]`。
     */
    suspend fun songLikeCheck(ids: List<Long>, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            val idsParam = ids.joinToString(separator = ",", prefix = "[", postfix = "]")
            get(
                "/song/like/check",
                mapOf(
                    "ids" to idsParam,
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    /** 喜欢 / 取消喜欢；[like]=false 为取消。 */
    suspend fun likeSong(id: Long, like: Boolean, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/like",
                mapOf(
                    "id" to id.toString(),
                    "like" to like.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun playlistDetail(playlistId: Long, cookie: String, limit: Int = 1000): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/playlist/detail",
                mapOf(
                    "id" to playlistId.toString(),
                    "limit" to limit.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun songDetail(ids: List<Long>, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        val idStr = ids.joinToString(",")
        get(
            "/song/detail",
            mapOf("ids" to idStr, "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun songUrl(ids: List<Long>, cookie: String, br: Int = 320_000): JSONObject =
        withContext(Dispatchers.IO) {
            val idStr = ids.joinToString(",")
            get(
                "/song/url",
                mapOf(
                    "id" to idStr,
                    "br" to br.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    /** 新版音质接口；旧版 `/song/url` 上游偶发 502 时作回退。 */
    suspend fun songUrlV1(
        ids: List<Long>,
        cookie: String,
        level: String = "exhigh",
    ): JSONObject = withContext(Dispatchers.IO) {
        val idStr = ids.joinToString(",")
        get(
            "/song/url/v1",
            mapOf(
                "id" to idStr,
                "level" to level,
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun lyric(songId: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/lyric",
            mapOf(
                "id" to songId.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    private fun get(path: String, query: Map<String, String>): JSONObject {
        val url = buildUrl(path, query)
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            return JSONObject(text)
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = NcmApiConfig.baseUrl.trimEnd('/')
        val full = (base + if (path.startsWith("/")) path else "/$path").toHttpUrl()
            .newBuilder()
        query.forEach { (k, v) -> full.addQueryParameter(k, v) }
        return full.build().toString()
    }

    private fun ts() = System.currentTimeMillis().toString()

    companion object {
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
