package com.kite.zmusic.listen

import com.kite.zmusic.data.CommunityServerStore
import com.kite.zmusic.workshop.WorkshopApiError
import com.kite.zmusic.workshop.WorkshopAuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ListenTogetherClient(
    http: OkHttpClient,
    private val community: CommunityServerStore,
    private val auth: WorkshopAuthStore,
) {
    private val immediate = http.newBuilder()
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val waiting = http.newBuilder()
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(28, TimeUnit.SECONDS)
        .build()

    suspend fun create(maxMembers: Int): ListenRoomSnapshot = mutate(
        "POST",
        "/rooms",
        JSONObject().put("max_members", maxMembers.coerceIn(2, 8)),
    )

    suspend fun join(id: String): ListenRoomSnapshot =
        mutate("POST", "/rooms/${enc(id)}/join", null)

    suspend fun leave(id: String): ListenRoomSnapshot =
        mutate("POST", "/rooms/${enc(id)}/leave", null)

    suspend fun close(id: String): ListenRoomSnapshot =
        mutate("POST", "/rooms/${enc(id)}/close", null)

    suspend fun postOp(id: String, body: JSONObject): ListenRoomSnapshot =
        mutate("POST", "/rooms/${enc(id)}/ops", body)

    suspend fun get(id: String, after: Long, wait: Boolean): ListenRoomSnapshot =
        withContext(Dispatchers.IO) {
            val q = buildString {
                append("after=").append(after)
                if (wait) append("&wait=1")
            }
            val req = request("GET", "/rooms/${enc(id)}?$q", null)
            val client = if (wait) waiting else immediate
            client.newCall(req).execute().use { parseSnapshot(it.body?.string().orEmpty()) }
        }

    private suspend fun mutate(method: String, path: String, body: JSONObject?): ListenRoomSnapshot =
        withContext(Dispatchers.IO) {
            immediate.newCall(request(method, path, body)).execute().use { resp ->
                parseSnapshot(resp.body?.string().orEmpty())
            }
        }

    private fun request(method: String, path: String, body: JSONObject?): Request {
        val s = auth.current() ?: throw WorkshopApiError.Unauthorized
        val url = communityUrl("$BASE$path")
        val builder = Request.Builder().url(url)
            .header("Authorization", "Bearer ${s.appToken}")
            .header("X-Zmusic-Uid", s.uid)
            .header("Accept", "application/json")
        val payload = body?.toString()?.toRequestBody(JSON)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(payload ?: "".toRequestBody(JSON))
            else -> error(method)
        }
        return builder.build()
    }

    private fun communityUrl(path: String): String {
        val e = community.current()
        val host = e.host.trim()
        val authority = if (e.port == 80) host else "$host:${e.port}"
        return "http://$authority$path"
    }

    companion object {
        const val BASE = "/api/v1/communities/zmusic/listen"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun enc(s: String): String =
            URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20")

        fun parseSnapshot(raw: String): ListenRoomSnapshot {
            if (raw.isBlank()) throw WorkshopApiError.Message("unavailable")
            val json = runCatching { JSONObject(raw) }.getOrElse {
                throw WorkshopApiError.Message("unavailable")
            }
            val ok = when (val value = json.opt("ok")) {
                null -> true
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.equals("true", ignoreCase = true) || value == "1"
                else -> false
            }
            if (!ok) {
                when (val error = json.optString("error").trim()) {
                    "unauthorized" -> throw WorkshopApiError.Unauthorized
                    "rate_limited" -> throw WorkshopApiError.RateLimited
                    "missing" -> throw WorkshopApiError.Missing
                    else -> throw WorkshopApiError.Message(error.ifBlank { "unavailable" })
                }
            }
            val clockObj = json.optJSONObject("clock") ?: JSONObject()
            return ListenRoomSnapshot(
                id = json.optString("id"),
                hostUid = json.optString("host_uid"),
                maxMembers = json.optInt("max_members", 2),
                members = parseMembers(json.optJSONArray("members")),
                clock = ListenPlaybackClock(
                    hlc = clockObj.optLong("hlc"),
                    actor = clockObj.optString("actor"),
                    trackId = clockObj.optLong("track_id"),
                    title = clockObj.optString("title"),
                    artists = clockObj.optString("artists"),
                    coverUrl = clockObj.optString("cover_url"),
                    durationMs = clockObj.optLong("duration_ms"),
                    playing = clockObj.optBoolean("playing"),
                    originMs = clockObj.optLong("origin_ms"),
                    originAt = clockObj.optLong("origin_at"),
                ),
                rev = json.optLong("rev"),
                serverNow = json.optLong("server_now"),
                closed = json.optBoolean("closed"),
                qrText = json.optString("qr_text"),
            )
        }

        private fun parseMembers(arr: JSONArray?): List<ListenMember> {
            if (arr == null) return emptyList()
            val out = ArrayList<ListenMember>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val uid = o.optString("uid").trim()
                if (uid.isEmpty()) continue
                out += ListenMember(
                    uid = uid,
                    nickname = o.optString("nickname").ifBlank { uid },
                    avatarUrl = o.optString("avatar_url"),
                    host = o.optBoolean("host"),
                )
            }
            return out
        }
    }
}
