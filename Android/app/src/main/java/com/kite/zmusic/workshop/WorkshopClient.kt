package com.kite.zmusic.workshop

import com.kite.zmusic.data.CommunityServerStore
import com.kite.zmusic.data.catalogLong
import com.kite.zmusic.data.catalogString
import com.kite.zmusic.data.parseCatalogArray
import com.kite.zmusic.data.xaiop.OkHttpXaiop
import com.kite.zmusic.plugin.PluginEngineVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.CompletionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WorkshopClient(
    private val http: OkHttpClient,
    private val xaiop: OkHttpXaiop,
    private val community: CommunityServerStore,
    private val auth: WorkshopAuthStore,
) {
    fun authority(): String {
        val e = community.current()
        val host = e.host.trim()
        return if (e.port == 80) host else "$host:${e.port}"
    }

    fun httpUrl(path: String, query: String = ""): String {
        val q = if (query.isEmpty()) "" else "?$query"
        return "http://${authority()}$path$q"
    }

    /** 与供应商 `logo` 相同：相对路径拼到当前社区主机。 */
    fun resolveUrl(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        if (t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true)
        ) {
            return t
        }
        if (t.startsWith("//")) return "http:$t"
        if (t.startsWith("/")) return "http://${authority()}$t"
        return t
    }

    suspend fun listPlugins(
        page: Int,
        perPage: Int = 20,
        sort: String = "updated",
        q: String = "",
    ): WorkshopPage<WorkshopPluginCard> {
        val parts = buildList {
            add("page=$page")
            add("per_page=$perPage")
            add("sort=${enc(sort)}")
            add("engine=${PluginEngineVersion.number}")
            if (q.isNotBlank()) add("q=${enc(q.trim())}")
        }
        val url = httpUrl("$BASE/plugins", parts.joinToString("&"))
        val snap = getXaiop(url)
        val pageData = parseCatalogArray(snap, "plugins") { parseCard(it)?.withResolvedCover() }
        throwIfBusinessError(pageData.ok, pageData.error)
        return WorkshopPage(pageData.ok, pageData.error, pageData.more, pageData.entries)
    }

    suspend fun pluginDetail(id: String): WorkshopPluginDetail {
        val url = httpUrl("$BASE/plugins/${enc(id)}")
        val snap = getXaiop(url) as? Map<*, *>
            ?: throw WorkshopApiError.Message("unavailable")
        if (snap["ok"] == false) {
            throwIfBusinessError(false, catalogString(snap["error"]))
        }
        val plugin = snap["plugin"] ?: throw WorkshopApiError.Missing
        return parseDetail(plugin)?.withResolvedCover()
            ?: throw WorkshopApiError.Message("bad plugin")
    }

    suspend fun listComments(id: String, page: Int, perPage: Int = 20): WorkshopPage<WorkshopComment> {
        val url = httpUrl(
            "$BASE/plugins/${enc(id)}/comments",
            "page=$page&per_page=$perPage",
        )
        val snap = getXaiop(url)
        val pageData = parseCatalogArray(snap, "comments") { parseComment(it)?.withResolvedAvatar() }
        throwIfBusinessError(pageData.ok, pageData.error)
        return WorkshopPage(pageData.ok, pageData.error, pageData.more, pageData.entries)
    }

    private fun WorkshopPluginCard.withResolvedCover(): WorkshopPluginCard =
        copy(coverUrl = resolveUrl(coverUrl))

    private fun WorkshopPluginDetail.withResolvedCover(): WorkshopPluginDetail =
        copy(card = card.withResolvedCover())

    private fun WorkshopComment.withResolvedAvatar(): WorkshopComment =
        copy(avatarUrl = resolveUrl(avatarUrl))

    suspend fun postComment(id: String, body: String): WorkshopComment = withContext(Dispatchers.IO) {
        val url = httpUrl("$BASE/plugins/${enc(id)}/comments")
        val json = jsonMutate(url, "POST", JSONObject().put("body", body))
        parseComment(jsonToMap(json.optJSONObject("comment") ?: json))
            ?.withResolvedAvatar()
            ?: error("bad comment")
    }

    suspend fun deleteComment(pluginId: String, commentId: String) = withContext(Dispatchers.IO) {
        val url = httpUrl("$BASE/plugins/${enc(pluginId)}/comments/${enc(commentId)}")
        jsonMutate(url, "DELETE", null)
        Unit
    }

    suspend fun putRating(id: String, stars: Int): WorkshopRatingResult = withContext(Dispatchers.IO) {
        val url = httpUrl("$BASE/plugins/${enc(id)}/rating")
        val json = jsonMutate(url, "PUT", JSONObject().put("stars", stars))
        parseRating(json)
    }

    suspend fun deleteRating(id: String): WorkshopRatingResult = withContext(Dispatchers.IO) {
        val url = httpUrl("$BASE/plugins/${enc(id)}/rating")
        val json = jsonMutate(url, "DELETE", null)
        parseRating(json)
    }

    private fun parseRating(json: JSONObject): WorkshopRatingResult {
        throwIfBusinessError(jsonOk(json), json.optString("error"))
        val my = json.opt("my_rating")
        return WorkshopRatingResult(
            ratingAvg = (json.optDouble("rating_avg", 0.0)),
            ratingCount = json.optInt("rating_count", 0),
            myRating = when (my) {
                null, JSONObject.NULL -> null
                is Number -> my.toInt().takeIf { it in 1..5 }
                else -> null
            },
        )
    }

    private suspend fun getXaiop(url: String): Any? = withContext(Dispatchers.IO) {
        withTimeout(RequestTimeoutMs) {
            val stream = xaiop.stream(url)
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { stream.abort() }
                xaiop.sendHttp(
                    stream,
                    url,
                    headers = authHeaders() + mapOf("Accept" to "text/xaiop"),
                    timeoutMs = RequestTimeoutMs,
                ).whenComplete { value, err ->
                    if (!cont.isActive) return@whenComplete
                    if (err != null) {
                        cont.resumeWithException(unwrap(err))
                    } else {
                        cont.resume(value)
                    }
                }
            }
        }
    }

    private fun jsonMutate(url: String, method: String, body: JSONObject?): JSONObject {
        val builder = Request.Builder().url(url)
        authHeaders().forEach { (k, v) -> builder.header(k, v) }
        builder.header("Accept", "application/json")
        builder.header("Content-Type", "application/json; charset=utf-8")
        val reqBody = body?.toString()?.toRequestBody(JSON_TYPE)
        when (method) {
            "POST" -> builder.post(reqBody ?: "".toRequestBody(JSON_TYPE))
            "PUT" -> builder.put(reqBody ?: "".toRequestBody(JSON_TYPE))
            "DELETE" -> {
                if (reqBody != null) builder.delete(reqBody) else builder.delete()
            }
            else -> error("bad method")
        }
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) error("empty")
            val json = runCatching { JSONObject(text) }.getOrElse { error("bad json") }
            throwIfBusinessError(jsonOk(json), json.optString("error"))
            return json
        }
    }

    fun authHeaders(): Map<String, String> {
        val s = auth.current() ?: throw WorkshopApiError.Unauthorized
        return buildMap {
            put("Authorization", "Bearer ${s.appToken}")
            put("X-Zmusic-Uid", s.uid)
        }
    }

    fun downloadUrl(id: String): String = httpUrl("$BASE/plugins/${enc(id)}/download")

    fun ackDownload(id: String) {
        jsonMutate(httpUrl("$BASE/plugins/${enc(id)}/download"), "POST", null)
    }

    private fun throwIfBusinessError(ok: Boolean, error: String) {
        if (ok) return
        when (error.trim()) {
            "unauthorized" -> {
                auth.clear()
                throw WorkshopApiError.Unauthorized
            }
            "rate_limited" -> throw WorkshopApiError.RateLimited
            "missing" -> throw WorkshopApiError.Missing
            else -> throw WorkshopApiError.Message(error.ifBlank { "unavailable" })
        }
    }

    companion object {
        const val BASE = "/api/v1/communities/zmusic/workshop"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MaxZppBytes = 64L * 1024L * 1024L
        /** 与公开目录一致量级：不可达时尽快失败，别空转半分钟。 */
        private const val RequestTimeoutMs = 8_000L

        fun maxZppBytes(): Long = MaxZppBytes

        private fun enc(s: String): String =
            URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20")

        private fun unwrap(err: Throwable): Throwable {
            var t = err
            while (t is CompletionException && t.cause != null) t = t.cause!!
            return t
        }

        private fun jsonOk(obj: JSONObject): Boolean = when (val value = obj.opt("ok")) {
            null -> true
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }

        private fun jsonToMap(obj: JSONObject?): Map<String, Any?>? {
            if (obj == null) return null
            val out = LinkedHashMap<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = obj.opt(k)
            }
            return out
        }

        internal fun parseCard(raw: Any?): WorkshopPluginCard? {
            val m = raw as? Map<*, *> ?: return null
            val id = catalogString(m["id"])
            if (id.isEmpty()) return null
            val version = catalogLong(m["version"])?.toInt() ?: return null
            val engineMin = catalogLong(m["engine_min"])?.toInt() ?: return null
            val engineMax = catalogLong(m["engine_max"])?.toInt()
            return WorkshopPluginCard(
                id = id,
                name = catalogString(m["name"]).ifBlank { id },
                version = version,
                description = catalogString(m["description"]),
                coverUrl = catalogString(m["cover_url"]),
                author = catalogString(m["author"]),
                publisherUid = catalogString(m["publisher_uid"]),
                ratingAvg = (m["rating_avg"] as? Number)?.toDouble() ?: 0.0,
                ratingCount = catalogLong(m["rating_count"])?.toInt() ?: 0,
                downloads = catalogLong(m["downloads"])?.toInt() ?: 0,
                updatedAt = catalogLong(m["updated_at"]) ?: 0L,
                engineMin = engineMin,
                engineMax = engineMax,
            )
        }

        internal fun parseDetail(raw: Any?): WorkshopPluginDetail? {
            val m = raw as? Map<*, *> ?: return null
            val card = parseCard(m) ?: return null
            val sha = catalogString(m["sha256"]).lowercase()
            if (sha.length != 64) return null
            val sigMap = m["signature"] as? Map<*, *> ?: return null
            val kid = catalogString(sigMap["kid"])
            val alg = catalogString(sigMap["alg"])
            val sig = catalogString(sigMap["sig"])
            if (kid.isEmpty() || alg != "Ed25519" || sig.isEmpty()) return null
            val size = catalogLong(m["size_bytes"]) ?: return null
            if (size <= 0L || size > MaxZppBytes) return null
            val my = m["my_rating"]
            val myRating = when (my) {
                null -> null
                is Number -> my.toInt().takeIf { it in 1..5 }
                else -> null
            }
            return WorkshopPluginDetail(
                card = card,
                readme = normalizeReadme(catalogString(m["readme"])),
                readmeTruncated = m["readme_truncated"] == true,
                sizeBytes = size,
                sha256 = sha,
                signature = WorkshopSignature(kid, alg, sig),
                myRating = myRating,
                packageUrl = catalogString(m["package_url"]),
            )
        }

        /** 还原字面 \\n，避免 README 整段挤在一行。 */
        private fun normalizeReadme(raw: String): String {
            var s = raw.trim()
            if (s.isEmpty()) return s
            if (s.contains("\\n")) {
                s = s.replace("\\r\\n", "\n")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\r", "\n")
            }
            return s.replace("\r\n", "\n").replace('\r', '\n')
        }

        internal fun parseComment(raw: Any?): WorkshopComment? {
            val m = raw as? Map<*, *> ?: return null
            val id = catalogString(m["id"])
            val body = catalogString(m["body"])
            if (id.isEmpty() || body.isEmpty()) return null
            return WorkshopComment(
                id = id,
                uid = catalogString(m["uid"]),
                nickname = catalogString(m["nickname"]),
                avatarUrl = catalogString(m["avatar_url"]),
                body = body,
                createdAt = catalogLong(m["created_at"]) ?: 0L,
            )
        }
    }
}
