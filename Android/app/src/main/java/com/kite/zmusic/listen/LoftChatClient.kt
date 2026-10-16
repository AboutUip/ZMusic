package com.kite.zmusic.listen

import com.kite.zmusic.data.CommunityServerStore
import com.kite.zmusic.workshop.WorkshopApiError
import com.kite.zmusic.workshop.WorkshopAuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LoftChatClient(
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

    suspend fun latest(): ListenChatPage = fetch(immediate, "/chat")

    suspend fun before(id: Long): ListenChatPage = fetch(immediate, "/chat?before=$id")

    suspend fun after(id: Long, wait: Boolean): ListenChatPage {
        val path = if (wait) "/chat?after=$id&wait=1" else "/chat?after=$id"
        return fetch(if (wait) waiting else immediate, path)
    }

    suspend fun post(text: String): ListenChatMsg = withContext(Dispatchers.IO) {
        val body = JSONObject().put("text", text).toString().toRequestBody(JSON)
        val call = immediate.newCall(request("POST", "/chat", body))
        coroutineContext.job.invokeOnCompletion { call.cancel() }
        call.execute().use { resp ->
            val json = ListenTogetherClient.parseObject(resp.body?.string().orEmpty())
            parseLoftMessage(json.optJSONObject("message"))
                ?: throw WorkshopApiError.Message("unavailable")
        }
    }

    private suspend fun fetch(client: OkHttpClient, path: String): ListenChatPage =
        withContext(Dispatchers.IO) {
            val call = client.newCall(request("GET", path, null))
            coroutineContext.job.invokeOnCompletion { call.cancel() }
            call.execute().use { resp ->
                val json = ListenTogetherClient.parseObject(resp.body?.string().orEmpty())
                ListenChatPage(
                    messages = parseLoftMessages(json.optJSONArray("chat")),
                    hasMore = json.optBoolean("has_more"),
                )
            }
        }

    private fun request(method: String, path: String, body: RequestBody?): Request {
        val session = auth.current() ?: throw WorkshopApiError.Unauthorized
        val builder = Request.Builder()
            .url(communityUrl("$BASE$path"))
            .header("Authorization", "Bearer ${session.appToken}")
            .header("X-Zmusic-Uid", session.uid)
            .header("Accept", "application/json")
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: "".toRequestBody(JSON))
            else -> error(method)
        }
        return builder.build()
    }

    private fun communityUrl(path: String): String {
        val endpoint = community.current()
        val host = endpoint.host.trim()
        val authority = if (endpoint.port == 80) host else "$host:${endpoint.port}"
        return "http://$authority$path"
    }

    companion object {
        const val BASE = "/api/v1/communities/zmusic/loft"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun parseLoftMessages(arr: JSONArray?): List<ListenChatMsg> {
            if (arr == null) return emptyList()
            val out = ArrayList<ListenChatMsg>(arr.length())
            for (i in 0 until arr.length()) {
                parseLoftMessage(arr.optJSONObject(i))?.let { out += it }
            }
            return out
        }

        private fun parseLoftMessage(obj: JSONObject?): ListenChatMsg? {
            if (obj == null) return null
            val text = obj.optString("text")
            if (text.isBlank()) return null
            val uid = obj.optString("uid").trim().ifBlank {
                obj.opt("uid")?.toString()?.trim().orEmpty()
            }
            if (uid.isEmpty()) return null
            return ListenChatMsg(
                id = obj.optLong("id"),
                uid = uid,
                nickname = obj.optString("nickname"),
                avatarUrl = obj.optString("avatar_url"),
                text = text,
                at = obj.optLong("at"),
            )
        }
    }
}
