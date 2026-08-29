package com.kite.zmusic.data

import com.kite.zmusic.data.xaiop.OkHttpXaiop
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 社区公开目录。优先走 XAIOP SDK（与 Android 同一套树）；
 * 正文若是 JSON 则转成同样的 Map，单测不依赖网络。
 */
class CommunityCatalogClient(
    private val http: OkHttpClient = defaultClient(),
    private val authority: () -> String,
) {
    private val xaiop = OkHttpXaiop(http)

    fun httpUrl(path: String, query: String = ""): String {
        val host = authority().trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val q = if (query.isEmpty()) "" else "?$query"
        return "http://$host$path$q"
    }

    suspend fun get(path: String, query: String = ""): Any? = withContext(Dispatchers.IO) {
        val url = httpUrl(path, query)
        sdkSnapshot(url)?.let { return@withContext it }
        val req = Request.Builder()
            .url(url)
            .header("Accept", "text/xaiop, application/json;q=0.9, */*;q=0.1")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return@use null
            val trimmed = text.trimStart()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return@use jsonToCatalogTree(
                    if (trimmed.startsWith("[")) org.json.JSONArray(trimmed) else JSONObject(trimmed),
                )
            }
            null
        }
    }

    private fun sdkSnapshot(url: String): Any? = runCatching {
        val stream = xaiop.stream(url)
        xaiop.sendHttp(
            stream = stream,
            url = url,
            headers = mapOf("Accept" to "text/xaiop"),
            timeoutMs = 30_000L,
        ).get(30, TimeUnit.SECONDS)
    }.getOrNull()

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
