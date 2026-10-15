package com.kite.zmusic.data.uapipro

import com.kite.zmusic.config.UApiProConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * UApiPro HTTP 客户端。供应商级封装；当前仅暴露翻译，后续可在此扩展其它端点。
 */
class UApiProClient(
    private val http: OkHttpClient = defaultClient(),
) {
    data class TranslateResult(
        val translatedText: String,
    )

    suspend fun translate(
        text: String,
        targetLang: String,
        sourceLang: String? = null,
        style: String = "professional",
        context: String = "entertainment",
        preserveFormat: Boolean = true,
    ): Result<TranslateResult> = withContext(Dispatchers.IO) {
        runCatching {
            val base = UApiProConfig.baseUrl.trimEnd('/')
            val key = UApiProConfig.apiKey.trim()
            require(base.isNotEmpty()) { "UApiPro baseUrl empty" }
            require(key.isNotEmpty()) { "UApiPro apiKey empty" }
            require(text.isNotBlank()) { "text empty" }
            require(targetLang.isNotBlank()) { "target_lang empty" }

            val bodyJson = JSONObject().apply {
                put("text", text)
                if (!sourceLang.isNullOrBlank()) put("source_lang", sourceLang)
                put("style", style)
                put("context", context)
                put("preserve_format", preserveFormat)
            }
            val url = "$base/ai/translate?target_lang=${
                java.net.URLEncoder.encode(targetLang.trim(), Charsets.UTF_8.name())
            }"
            val req = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $key")
                .post(bodyJson.toString().toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(raw).optString("error")
                            .ifBlank { JSONObject(raw).optString("message") }
                    }.getOrNull().orEmpty().ifBlank { "HTTP ${resp.code}" }
                    error(msg)
                }
                val root = JSONObject(raw)
                val data = root.optJSONObject("data")
                    ?: error(root.optString("message").ifBlank { "empty data" })
                val translated = data.optString("translated_text").trim()
                require(translated.isNotEmpty()) { "empty translation" }
                TranslateResult(translated)
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
