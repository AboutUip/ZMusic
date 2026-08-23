package com.kite.zmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CommunitySubmitAck(
    val ok: Boolean,
    val status: String,
)

class CommunityLoginClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    suspend fun submitAllow(url: String, assertion: String): CommunitySubmitAck =
        post(url, JSONObject().put("assertion", assertion))

    suspend fun submitDeny(url: String, sid: String): CommunitySubmitAck =
        post(
            url,
            JSONObject()
                .put("sid", sid)
                .put("decision", "denied"),
        )

    private suspend fun post(url: String, body: JSONObject): CommunitySubmitAck = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json; charset=utf-8")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) {
                error("empty")
            }
            val json = runCatching { JSONObject(text) }.getOrElse {
                error("提交失败")
            }
            val body = json.optJSONObject("data") ?: json
            CommunitySubmitAck(
                ok = jsonOk(body) || jsonOk(json),
                status = firstStatus(body, json),
            )
        }
    }

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun jsonOk(obj: JSONObject): Boolean = when (val value = obj.opt("ok")) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }

        private fun firstStatus(vararg objs: JSONObject): String {
            for (obj in objs) {
                val status = obj.optString("status", "").trim()
                if (status.isNotEmpty() && status != "null") return status
            }
            return ""
        }
    }
}
