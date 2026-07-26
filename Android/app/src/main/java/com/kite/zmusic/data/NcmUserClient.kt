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

    suspend fun playlistDetail(playlistId: Long, cookie: String, limit: Int = 500): JSONObject =
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

    /**
     * 新版评论：`type=0` 歌曲；[sortType] 1 推荐 / 2 热度 / 3 时间。
     * 时间序翻页需传上一页 [cursor]。
     */
    suspend fun commentNew(
        songId: Long,
        cookie: String,
        pageNo: Int = 1,
        pageSize: Int = 20,
        sortType: Int = 2,
        cursor: String? = null,
        type: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        val q = linkedMapOf(
            "id" to songId.toString(),
            "type" to type.toString(),
            "pageNo" to pageNo.toString(),
            "pageSize" to pageSize.toString(),
            "sortType" to sortType.toString(),
            "timestamp" to ts(),
        )
        if (cookie.isNotBlank()) q["cookie"] = cookie
        if (!cursor.isNullOrBlank()) q["cursor"] = cursor
        get("/comment/new", q)
    }

    /** 旧版歌曲评论（新版失败时回退）；[before] 为上一页最后一项 time。 */
    suspend fun commentMusic(
        songId: Long,
        cookie: String,
        limit: Int = 20,
        offset: Int = 0,
        before: Long? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val q = linkedMapOf(
            "id" to songId.toString(),
            "limit" to limit.toString(),
            "offset" to offset.toString(),
            "timestamp" to ts(),
        )
        if (cookie.isNotBlank()) q["cookie"] = cookie
        if (before != null && before > 0L) q["before"] = before.toString()
        get("/comment/music", q)
    }

    /** 楼层回复：[parentCommentId] 为一级评论 id。 */
    suspend fun commentFloor(
        songId: Long,
        parentCommentId: Long,
        cookie: String,
        limit: Int = 20,
        time: Long? = null,
        type: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        val q = linkedMapOf(
            "id" to songId.toString(),
            "parentCommentId" to parentCommentId.toString(),
            "type" to type.toString(),
            "limit" to limit.toString(),
            "timestamp" to ts(),
        )
        if (cookie.isNotBlank()) q["cookie"] = cookie
        if (time != null && time > 0L) q["time"] = time.toString()
        get("/comment/floor", q)
    }

    /**
     * 发送 / 回复评论。对齐 `/comment`：
     * [t]=1 发评论，[t]=2 回复（须传 [replyCommentId]）；歌曲 [type]=0。
     */
    suspend fun commentPost(
        songId: Long,
        content: String,
        cookie: String,
        replyCommentId: Long? = null,
        type: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        val isReply = replyCommentId != null && replyCommentId > 0L
        val q = linkedMapOf(
            "t" to if (isReply) "2" else "1",
            "type" to type.toString(),
            "id" to songId.toString(),
            "content" to content,
            "cookie" to cookie,
            "timestamp" to ts(),
        )
        if (isReply) q["commentId"] = replyCommentId.toString()
        get("/comment", q)
    }

    /**
     * 给评论点赞 / 取消。[t]=1 点赞，0 取消；歌曲 [type]=0。
     * 严格对齐网易云 `/comment/like`。
     */
    suspend fun commentLike(
        songId: Long,
        commentId: Long,
        like: Boolean,
        cookie: String,
        type: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/comment/like",
            mapOf(
                "id" to songId.toString(),
                "cid" to commentId.toString(),
                "t" to if (like) "1" else "0",
                "type" to type.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    /**
     * 抱一抱评论。参数严格对齐文档：
     * [uid]=被抱用户（评论作者），[cid]=评论 id，[sid]=资源 id（歌曲 id）。
     */
    suspend fun hugComment(
        targetUid: Long,
        commentId: Long,
        songId: Long,
        cookie: String,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/hug/comment",
            mapOf(
                "uid" to targetUid.toString(),
                "cid" to commentId.toString(),
                "sid" to songId.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    /**
     * 评论抱一抱列表。参数严格对齐文档 uid/cid/sid；分页 cursor、idCursor。
     */
    suspend fun commentHugList(
        targetUid: Long,
        commentId: Long,
        songId: Long,
        cookie: String,
        page: Int = 1,
        pageSize: Int = 50,
        cursor: String? = null,
        idCursor: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val q = linkedMapOf(
            "uid" to targetUid.toString(),
            "cid" to commentId.toString(),
            "sid" to songId.toString(),
            "page" to page.toString(),
            "pageSize" to pageSize.toString(),
            "cookie" to cookie,
            "timestamp" to ts(),
        )
        if (!cursor.isNullOrBlank()) q["cursor"] = cursor
        if (!idCursor.isNullOrBlank()) q["idCursor"] = idCursor
        get("/comment/hug/list", q)
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
