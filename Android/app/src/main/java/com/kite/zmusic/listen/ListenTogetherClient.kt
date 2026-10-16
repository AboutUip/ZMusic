package com.kite.zmusic.listen

import com.kite.zmusic.data.CommunityServerStore
import com.kite.zmusic.workshop.WorkshopApiError
import com.kite.zmusic.workshop.WorkshopAuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
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
    private val heavy = http.newBuilder()
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
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

    suspend fun postChat(id: String, text: String): ListenRoomSnapshot =
        mutate("POST", "/rooms/${enc(id)}/chat", JSONObject().put("text", text))

    suspend fun markChatRead(id: String, upTo: Long): ListenRoomSnapshot =
        mutate("POST", "/rooms/${enc(id)}/chat/read", JSONObject().put("id", upTo))

    suspend fun chatBefore(id: String, before: Long): ListenChatPage =
        withContext(Dispatchers.IO) {
            val req = request("GET", "/rooms/${enc(id)}/chat?before=$before", null)
            immediate.newCall(req).execute().use { resp ->
                val json = parseObject(resp.body?.string().orEmpty())
                ListenChatPage(
                    messages = parseChat(json.optJSONArray("chat")),
                    hasMore = json.optBoolean("has_more"),
                )
            }
        }

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

    suspend fun presence() {
        withContext(Dispatchers.IO) {
            immediate.newCall(request("POST", "/presence", null)).execute().use {
                parseObject(it.body?.string().orEmpty())
            }
        }
    }

    suspend fun leavePresence() {
        withContext(Dispatchers.IO) {
            immediate.newCall(request("POST", "/presence/leave", null)).execute().use {
                parseObject(it.body?.string().orEmpty())
            }
        }
    }

    suspend fun match(wait: Boolean, skipUids: Collection<String> = emptyList()): ListenPeer =
        withContext(Dispatchers.IO) {
            val q = if (wait) "?wait=1" else ""
            val client = if (wait) waiting else immediate
            val body = if (skipUids.isEmpty()) {
                null
            } else {
                JSONObject().put(
                    "skip_uids",
                    JSONArray().also { arr -> skipUids.forEach { arr.put(it) } },
                )
            }
            client.newCall(request("POST", "/match$q", body)).execute().use { resp ->
                val json = parseObject(resp.body?.string().orEmpty())
                parsePeer(json.optJSONObject("peer"))
                    ?: throw WorkshopApiError.Message("nobody")
            }
        }

    suspend fun skipMatch(uid: String) {
        withContext(Dispatchers.IO) {
            immediate.newCall(
                request("POST", "/match/skip", JSONObject().put("uid", uid.trim())),
            ).execute().use { parseObject(it.body?.string().orEmpty()) }
        }
    }

    suspend fun invite(toUid: String): ListenInvite =
        withContext(Dispatchers.IO) {
            immediate.newCall(
                request("POST", "/invites", JSONObject().put("to_uid", toUid.trim())),
            ).execute().use { resp ->
                val json = parseObject(resp.body?.string().orEmpty())
                parseInvite(json.optJSONObject("invite"))
                    ?: throw WorkshopApiError.Message("unavailable")
            }
        }

    suspend fun getInvites(wait: Boolean): ListenInviteBox =
        withContext(Dispatchers.IO) {
            val q = if (wait) "?wait=1" else ""
            val client = if (wait) waiting else immediate
            client.newCall(request("GET", "/invites$q", null)).execute().use { resp ->
                parseInviteBox(resp.body?.string().orEmpty())
            }
        }

    suspend fun acceptInvite(id: String): ListenRoomSnapshot =
        mutate("POST", "/invites/${enc(id)}/accept", null)

    suspend fun declineInvite(id: String, today: Boolean): Unit =
        withContext(Dispatchers.IO) {
            immediate.newCall(
                request("POST", "/invites/${enc(id)}/decline", JSONObject().put("today", today)),
            ).execute().use { parseObject(it.body?.string().orEmpty()) }
            Unit
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

    private fun binaryRequest(method: String, path: String, body: RequestBody?): Request {
        val s = auth.current() ?: throw WorkshopApiError.Unauthorized
        val builder = Request.Builder()
            .url(communityUrl("$BASE$path"))
            .header("Authorization", "Bearer ${s.appToken}")
            .header("X-Zmusic-Uid", s.uid)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
            else -> error(method)
        }
        return builder.build()
    }

    suspend fun listStickers(): List<ListenSticker> = withContext(Dispatchers.IO) {
        heavy.newCall(request("GET", "/stickers", null)).execute().use { resp ->
            val json = parseObject(resp.body?.string().orEmpty())
            parseStickerList(json.optJSONArray("stickers"))
        }
    }

    suspend fun uploadSticker(bytes: ByteArray): ListenSticker = withContext(Dispatchers.IO) {
        val mime = listenChatStickerMime(bytes) ?: throw WorkshopApiError.Message("bad_image")
        if (bytes.size > ListenStickerMaxBytes) throw WorkshopApiError.Message("too_large")
        val ext = when (mime) {
            "image/gif" -> "gif"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "sticker.$ext", bytes.toRequestBody(mime.toMediaType()))
            .build()
        heavy.newCall(binaryRequest("POST", "/stickers", body)).execute().use { resp ->
            val json = parseObject(resp.body?.string().orEmpty())
            parseSticker(json.optJSONObject("sticker"))
                ?: throw WorkshopApiError.Message("unavailable")
        }
    }

    suspend fun downloadSticker(id: String): ByteArray = withContext(Dispatchers.IO) {
        val clean = id.trim()
        if (!listenChatStickerIdOk(clean)) throw WorkshopApiError.Message("bad_request")
        heavy.newCall(binaryRequest("GET", "/stickers/${enc(clean)}", null)).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: throw WorkshopApiError.Message("unavailable")
            if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
                parseObject(bytes.toString(Charsets.UTF_8))
            }
            if (listenChatStickerMime(bytes) == null) throw WorkshopApiError.Message("bad_image")
            bytes
        }
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

        fun parseObject(raw: String): JSONObject {
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
            return json
        }

        fun parseSnapshot(raw: String): ListenRoomSnapshot {
            val json = parseObject(raw)
            val clockObj = json.optJSONObject("clock") ?: JSONObject()
            return ListenRoomSnapshot(
                id = json.optString("id"),
                hostUid = jsonUid(json, "host_uid"),
                maxMembers = json.optInt("max_members", 2),
                members = parseMembers(json.optJSONArray("members")),
                clock = ListenPlaybackClock(
                    hlc = jsonLong(clockObj, "hlc"),
                    actor = clockObj.optString("actor"),
                    trackId = jsonLong(clockObj, "track_id"),
                    title = clockObj.optString("title"),
                    artists = clockObj.optString("artists"),
                    coverUrl = clockObj.optString("cover_url"),
                    durationMs = jsonLong(clockObj, "duration_ms"),
                    playing = clockObj.optBoolean("playing"),
                    originMs = jsonLong(clockObj, "origin_ms"),
                    originAt = jsonLong(clockObj, "origin_at"),
                ),
                rev = json.optLong("rev"),
                serverNow = json.optLong("server_now"),
                closed = json.optBoolean("closed"),
                qrText = json.optString("qr_text"),
                chat = parseChat(json.optJSONArray("chat")),
                chatIncluded = json.has("chat") && !json.isNull("chat"),
                chatReads = parseChatReads(json.optJSONObject("chat_reads")),
                chatReadsIncluded = json.has("chat_reads") && !json.isNull("chat_reads"),
            )
        }

        fun parseInviteBox(raw: String): ListenInviteBox {
            val json = parseObject(raw)
            return ListenInviteBox(
                incoming = parseInvite(json.optJSONObject("incoming")),
                outgoing = parseInvite(json.optJSONObject("outgoing")),
            )
        }

        fun parseInvite(obj: JSONObject?): ListenInvite? {
            if (obj == null) return null
            val id = obj.optString("id").trim()
            if (id.isEmpty()) return null
            val from = parsePeer(obj.optJSONObject("from")) ?: return null
            val to = parsePeer(obj.optJSONObject("to")) ?: ListenPeer(uid = "", nickname = "", avatarUrl = "")
            return ListenInvite(
                id = id,
                roomId = obj.optString("room_id"),
                from = from,
                to = to,
                status = obj.optString("status").ifBlank { "pending" },
                expiresAt = jsonLong(obj, "expires_at"),
                expiresIn = jsonLong(obj, "expires_in"),
            )
        }

        fun parsePeer(obj: JSONObject?): ListenPeer? {
            if (obj == null) return null
            val uid = jsonUid(obj)
            if (uid.isEmpty()) return null
            return ListenPeer(
                uid = uid,
                nickname = obj.optString("nickname").ifBlank { uid },
                avatarUrl = obj.optString("avatar_url"),
            )
        }

        internal fun jsonLong(obj: JSONObject, key: String): Long {
            val raw = obj.opt(key) ?: return 0L
            return when (raw) {
                is Number -> raw.toLong()
                is String -> raw.trim().toLongOrNull() ?: 0L
                else -> 0L
            }
        }

        internal fun jsonUid(obj: JSONObject, key: String = "uid"): String {
            val raw = obj.opt(key) ?: return ""
            if (raw === JSONObject.NULL) return ""
            return when (raw) {
                is Number -> raw.toLong().toString()
                is String -> raw.trim()
                else -> raw.toString().trim()
            }
        }

        private fun parseMembers(arr: JSONArray?): List<ListenMember> {
            if (arr == null) return emptyList()
            val out = ArrayList<ListenMember>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val uid = jsonUid(o)
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

        private fun parseChatReads(obj: JSONObject?): Map<String, Long> {
            if (obj == null) return emptyMap()
            val out = LinkedHashMap<String, Long>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next().trim()
                if (key.isEmpty()) continue
                val value = jsonLong(obj, key)
                if (value > 0L) out[key] = value
            }
            return out
        }

        private fun parseStickerList(arr: JSONArray?): List<ListenSticker> {
            if (arr == null) return emptyList()
            val out = ArrayList<ListenSticker>(arr.length())
            for (i in 0 until arr.length()) {
                parseSticker(arr.optJSONObject(i))?.let { out += it }
            }
            return out
        }

        private fun parseSticker(obj: JSONObject?): ListenSticker? {
            if (obj == null) return null
            val id = obj.optString("id").trim()
            val mime = obj.optString("mime").trim()
            if (!listenChatStickerIdOk(id) || mime.isEmpty()) return null
            return ListenSticker(id = id, mime = mime, animated = obj.optBoolean("animated"))
        }

        private fun parseChat(arr: JSONArray?): List<ListenChatMsg> {
            if (arr == null) return emptyList()
            val out = ArrayList<ListenChatMsg>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val text = o.optString("text").trim()
                if (text.isEmpty()) continue
                out += ListenChatMsg(
                    id = jsonLong(o, "id"),
                    uid = jsonUid(o),
                    nickname = o.optString("nickname"),
                    avatarUrl = o.optString("avatar_url"),
                    text = text,
                    at = jsonLong(o, "at"),
                )
            }
            return out
        }
    }
}
