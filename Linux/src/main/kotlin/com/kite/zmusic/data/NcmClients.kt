package com.kite.zmusic.data

import com.kite.zmusic.config.NcmApiConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

class NcmEndpointMissingException : IOException("接口不可用")

class NcmAuthClient(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun loginStatus(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/login/status", mapOf("cookie" to cookie, "timestamp" to ts()))
    }

    suspend fun loginQrKey(): JSONObject = withContext(Dispatchers.IO) {
        get("/login/qr/key", mapOf("timestamp" to ts()))
    }

    suspend fun loginQrCreate(key: String): JSONObject = withContext(Dispatchers.IO) {
        get("/login/qr/create", mapOf("key" to key, "qrimg" to "true", "timestamp" to ts()))
    }

    suspend fun loginQrCheck(key: String, noCookie: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val q = mutableMapOf("key" to key, "timestamp" to ts())
        if (noCookie) q["noCookie"] = "true"
        get("/login/qr/check", q)
    }

    suspend fun captchaSent(phone: String, ctcode: String = "86"): JSONObject = withContext(Dispatchers.IO) {
        get("/captcha/sent", mapOf("phone" to phone, "ctcode" to ctcode, "timestamp" to ts()))
    }

    suspend fun loginCellphone(
        phone: String,
        password: String? = null,
        md5Password: String? = null,
        captcha: String? = null,
        countrycode: String = "86",
    ): JSONObject = withContext(Dispatchers.IO) {
        val form = mutableMapOf("phone" to phone, "countrycode" to countrycode)
        when {
            captcha != null -> form["captcha"] = captcha
            md5Password != null -> form["md5_password"] = md5Password
            password != null -> form["password"] = password
            else -> error("password or captcha required")
        }
        postForm("/login/cellphone", form)
    }

    suspend fun loginEmail(
        email: String,
        password: String? = null,
        md5Password: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val form = mutableMapOf("email" to email)
        when {
            md5Password != null -> form["md5_password"] = md5Password
            password != null -> form["password"] = password
            else -> error("password or md5_password required")
        }
        postForm("/login", form)
    }

    suspend fun registerCellphone(
        phone: String,
        captcha: String,
        password: String,
        nickname: String,
        countrycode: String = "86",
    ): JSONObject = withContext(Dispatchers.IO) {
        postForm(
            "/register/cellphone",
            mapOf(
                "phone" to phone,
                "captcha" to captcha,
                "password" to password,
                "nickname" to nickname,
                "countrycode" to countrycode,
            ),
        )
    }

    private fun get(path: String, query: Map<String, String>): JSONObject {
        val req = Request.Builder().url(buildUrl(path, query)).get().build()
        return executeJson(req)
    }

    private fun postForm(path: String, fields: Map<String, String>): JSONObject {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(buildUrl(path, mapOf("timestamp" to ts()))).post(body).build()
        return executeJson(req)
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = NcmApiConfig.baseUrl.trimEnd('/')
        val full = (base + if (path.startsWith("/")) path else "/$path").toHttpUrl().newBuilder()
        query.forEach { (k, v) -> full.addQueryParameter(k, v) }
        return full.build().toString()
    }

    private fun executeJson(req: Request): JSONObject {
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = try {
                if (text.isBlank()) null else JSONObject(text)
            } catch (_: JSONException) {
                null
            }
            if (json != null) return json
            if (resp.code == 404 || resp.code == 502) throw NcmEndpointMissingException()
            throw IOException("请求失败，请稍后重试")
        }
    }

    private fun ts() = System.currentTimeMillis().toString()

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

class NcmUserClient(
    private val client: OkHttpClient = NcmAuthClient.defaultClient(),
) {
    suspend fun userPlaylist(uid: Long, cookie: String, limit: Int = 60, offset: Int = 0): JSONObject =
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

    suspend fun playlistDetail(id: Long, cookie: String, limit: Int = 1000): JSONObject =
        get(
            "/playlist/detail",
            mapOf("id" to id.toString(), "limit" to limit.toString(), "cookie" to cookie, "timestamp" to ts()),
        )

    suspend fun playlistTrackAll(playlistId: Long, cookie: String, limit: Int, offset: Int): JSONObject =
        get(
            "/playlist/track/all",
            mapOf(
                "id" to playlistId.toString(),
                "limit" to limit.coerceAtLeast(1).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun songDetail(ids: List<Long>, cookie: String): JSONObject {
        val idStr = ids.joinToString(",")
        return get("/song/detail", mapOf("ids" to idStr, "cookie" to cookie, "timestamp" to ts()))
    }

    suspend fun songUrl(ids: List<Long>, cookie: String, br: Int = 320_000): JSONObject {
        val idStr = ids.joinToString(",")
        return get(
            "/song/url",
            mapOf("id" to idStr, "br" to br.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun songUrlV1(
        ids: List<Long>,
        cookie: String,
        level: String = "exhigh",
        encodeType: String = "mp3",
    ): JSONObject {
        val idStr = ids.joinToString(",")
        return get(
            "/song/url/v1",
            mapOf(
                "id" to idStr,
                "level" to level,
                "encodeType" to encodeType,
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun banner(cookie: String, type: Int = 1): JSONObject =
        get("/banner", mapOf("type" to type.toString(), "cookie" to cookie, "timestamp" to ts()))

    suspend fun personalizedPlaylists(cookie: String, limit: Int = 30): JSONObject =
        get(
            "/personalized",
            mapOf("limit" to limit.toString(), "cookie" to cookie, "timestamp" to ts()),
        )

    suspend fun personalizedNewsong(cookie: String, limit: Int = 20): JSONObject =
        get(
            "/personalized/newsong",
            mapOf(
                "limit" to limit.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun personalizedMv(cookie: String): JSONObject =
        get("/personalized/mv", mapOf("cookie" to cookie, "timestamp" to ts()))

    suspend fun mvFirst(cookie: String, limit: Int = 24): JSONObject =
        get(
            "/mv/first",
            mapOf(
                "limit" to limit.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun topPlaylists(
        cookie: String,
        limit: Int = 15,
        offset: Int = 0,
        order: String = "hot",
    ): JSONObject =
        get(
            "/top/playlist",
            mapOf(
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "order" to order,
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun recommendResource(cookie: String): JSONObject =
        get("/recommend/resource", mapOf("cookie" to cookie, "timestamp" to ts()))

    suspend fun toplistDetail(cookie: String): JSONObject =
        get("/toplist/detail", mapOf("cookie" to cookie, "timestamp" to ts()))

    suspend fun lyric(songId: Long, cookie: String): JSONObject =
        get("/lyric/new", mapOf("id" to songId.toString(), "cookie" to cookie, "timestamp" to ts()))

    suspend fun search(keywords: String, cookie: String, type: Int = 1, limit: Int = 30): JSONObject =
        get(
            "/cloudsearch",
            mapOf(
                "keywords" to keywords,
                "type" to type.toString(),
                "limit" to limit.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun album(id: Long, cookie: String): JSONObject =
        get("/album", mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()))

    suspend fun artist(id: Long, cookie: String): JSONObject =
        get("/artists", mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()))

    suspend fun personalFm(cookie: String): JSONObject =
        get("/personal_fm", mapOf("cookie" to cookie, "timestamp" to ts()))

    suspend fun recommendSongs(cookie: String): JSONObject =
        get("/recommend/songs", mapOf("cookie" to cookie, "timestamp" to ts()))

    suspend fun mvUrl(id: Long, cookie: String): JSONObject =
        get("/mv/url", mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()))

    suspend fun intelligenceList(
        songId: Long,
        cookie: String,
        playlistId: Long = 0L,
        startSongId: Long = songId,
    ): JSONObject {
        val q = mutableMapOf(
            "id" to songId.toString(),
            "sid" to startSongId.toString(),
            "count" to "50",
            "cookie" to cookie,
            "timestamp" to ts(),
        )
        if (playlistId > 0L) q["pid"] = playlistId.toString()
        return get("/playmode/intelligence/list", q)
    }

    suspend fun artistSublist(cookie: String): JSONObject =
        get("/artist/sublist", mapOf("cookie" to cookie, "timestamp" to ts()))

    suspend fun albumSublist(cookie: String, limit: Int = 50, offset: Int = 0): JSONObject =
        get(
            "/album/sublist",
            mapOf(
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun userDetail(uid: Long, cookie: String): JSONObject =
        get("/user/detail", mapOf("uid" to uid.toString(), "cookie" to cookie, "timestamp" to ts()))

    suspend fun likeList(uid: Long, cookie: String): JSONObject =
        get("/likelist", mapOf("uid" to uid.toString(), "cookie" to cookie, "timestamp" to ts()))

    suspend fun songLikeCheck(ids: List<Long>, cookie: String): JSONObject {
        val idsParam = ids.joinToString(separator = ",", prefix = "[", postfix = "]")
        return get(
            "/song/like/check",
            mapOf("ids" to idsParam, "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun likeSong(id: Long, like: Boolean, cookie: String): JSONObject =
        get(
            "/like",
            mapOf("id" to id.toString(), "like" to like.toString(), "cookie" to cookie, "timestamp" to ts()),
        )

    suspend fun playlistSubscribe(id: Long, subscribe: Boolean, cookie: String): JSONObject =
        get(
            "/playlist/subscribe",
            mapOf(
                "t" to if (subscribe) "1" else "2",
                "id" to id.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun playlistDetailDynamic(id: Long, cookie: String): JSONObject =
        get("/playlist/detail/dynamic", mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()))

    suspend fun playlistCreate(name: String, cookie: String): JSONObject =
        get("/playlist/create", mapOf("name" to name, "cookie" to cookie, "timestamp" to ts()))

    suspend fun albumDetailDynamic(id: Long, cookie: String): JSONObject =
        get("/album/detail/dynamic", mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()))

    suspend fun albumSub(id: Long, subscribe: Boolean, cookie: String): JSONObject =
        get(
            "/album/sub",
            mapOf(
                "id" to id.toString(),
                "t" to if (subscribe) "1" else "0",
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun artistSub(id: Long, follow: Boolean, cookie: String): JSONObject =
        get(
            "/artist/sub",
            mapOf(
                "id" to id.toString(),
                "t" to if (follow) "1" else "0",
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun artistAlbums(id: Long, cookie: String, limit: Int = 30, offset: Int = 0): JSONObject =
        get(
            "/artist/album",
            mapOf(
                "id" to id.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun artistMv(id: Long, cookie: String, limit: Int = 30, offset: Int = 0): JSONObject =
        get(
            "/artist/mv",
            mapOf(
                "id" to id.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun userFollow(id: Long, follow: Boolean, cookie: String): JSONObject =
        get(
            "/follow",
            mapOf(
                "id" to id.toString(),
                "t" to if (follow) "1" else "0",
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )

    suspend fun searchHotDetail(cookie: String): JSONObject =
        get("/search/hot/detail", mapOf("cookie" to cookie, "timestamp" to ts()))

    suspend fun searchSuggest(keywords: String, cookie: String): JSONObject =
        get(
            "/search/suggest",
            mapOf("keywords" to keywords, "type" to "mobile", "cookie" to cookie, "timestamp" to ts()),
        )

    private suspend fun get(path: String, query: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val base = NcmApiConfig.baseUrl.trimEnd('/')
        val full = (base + if (path.startsWith("/")) path else "/$path").toHttpUrl().newBuilder()
        query.forEach { (k, v) -> full.addQueryParameter(k, v) }
        val req = Request.Builder().url(full.build()).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = try {
                if (text.isBlank()) null else JSONObject(text)
            } catch (_: JSONException) {
                null
            }
            if (json != null) return@use json
            if (resp.code == 404 || resp.code == 502) throw NcmEndpointMissingException()
            throw IOException("请求失败，请稍后重试")
        }
    }

    private fun ts() = System.currentTimeMillis().toString()
}
