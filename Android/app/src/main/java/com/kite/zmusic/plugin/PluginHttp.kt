package com.kite.zmusic.plugin

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal data class PluginHttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?,
    val timeoutMs: Long,
)

internal object PluginHttpParams {
    const val MAX_BODY_BYTES = 1_048_576
    const val DEFAULT_TIMEOUT_MS = 15_000L
    const val MIN_TIMEOUT_MS = 1L
    const val MAX_TIMEOUT_MS = 60_000L
    const val MAX_HEADERS = 32
    const val MAX_HEADER_NAME = 128
    const val MAX_HEADER_VALUE = 4096

    private val METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD")

    fun parse(raw: Any?): PluginHttpRequest? {
        val map = raw as? Map<*, *> ?: return null
        val url = (map["url"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        val methodRaw = map["method"] as? String ?: "GET"
        val method = methodRaw.trim().uppercase()
        if (method !in METHODS) return null
        val timeout = when (val t = map["timeout"]) {
            null -> DEFAULT_TIMEOUT_MS
            else -> PluginInts.long(t, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS) ?: return null
        }
        val headers = parseHeaders(map["headers"]) ?: return null
        val body = when (val b = map["body"]) {
            null -> null
            is String -> b
            else -> return null
        }
        if (body != null && (method == "GET" || method == "HEAD")) return null
        return PluginHttpRequest(
            url = parsed.toString(),
            method = method,
            headers = headers,
            body = body,
            timeoutMs = timeout,
        )
    }

    private fun parseHeaders(raw: Any?): Map<String, String>? {
        if (raw == null) return emptyMap()
        val map = raw as? Map<*, *> ?: return null
        if (map.size > MAX_HEADERS) return null
        val out = LinkedHashMap<String, String>()
        for ((k, v) in map) {
            val name = k as? String ?: return null
            val key = name.trim()
            if (key.isEmpty() || key.length > MAX_HEADER_NAME) return null
            val value = v as? String ?: return null
            if (value.length > MAX_HEADER_VALUE) return null
            out[key] = value
        }
        return out
    }

    fun result(
        ok: Boolean,
        status: Int?,
        error: String?,
        text: String?,
        headers: Map<String, String> = emptyMap(),
    ): Map<String, Any?> = mapOf(
        "ok" to ok,
        "status" to status,
        "error" to error,
        "text" to text,
        "headers" to headers,
    )
}

internal class PluginHttpClient(private val base: OkHttpClient) {
    private val inflight = ConcurrentHashMap<String, CopyOnWriteArrayList<Pending>>()

    fun enqueue(
        pluginId: String,
        req: PluginHttpRequest,
        onDone: (Map<String, Any?>) -> Unit,
    ): Boolean {
        val httpUrl = req.url.toHttpUrlOrNull() ?: return false
        val client = base.newBuilder()
            .callTimeout(req.timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(req.timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(req.timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(req.timeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val builder = Request.Builder().url(httpUrl)
        req.headers.forEach { (k, v) ->
            if (k.equals("Host", ignoreCase = true) || k.equals("Content-Length", ignoreCase = true)) {
                return@forEach
            }
            runCatching { builder.header(k, v) }
        }
        val body = if (req.body != null) {
            val type = req.headers.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value
                ?.toMediaTypeOrNull()
            req.body.toRequestBody(type)
        } else if (req.method != "GET" && req.method != "HEAD") {
            ByteArray(0).toRequestBody(null)
        } else {
            null
        }
        val request = try {
            builder.method(req.method, body).build()
        } catch (_: IllegalArgumentException) {
            return false
        }
        val call = client.newCall(request)
        val pending = Pending(call, onDone)
        inflight.getOrPut(pluginId) { CopyOnWriteArrayList() }.add(pending)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                finish(pluginId, pending, failure(e, call.isCanceled()))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { finish(pluginId, pending, success(it)) }
            }
        })
        return true
    }

    fun cancel(pluginId: String) {
        val list = inflight.remove(pluginId) ?: return
        list.forEach { pending ->
            pending.call.cancel()
            pending.complete(PluginHttpParams.result(false, null, "cancelled", null))
        }
    }

    private fun finish(pluginId: String, pending: Pending, result: Map<String, Any?>) {
        inflight[pluginId]?.remove(pending)
        pending.complete(result)
    }

    private fun failure(e: IOException, cancelled: Boolean): Map<String, Any?> {
        val err = when {
            cancelled -> "cancelled"
            e is java.net.SocketTimeoutException -> "timeout"
            else -> "network"
        }
        return PluginHttpParams.result(false, null, err, null)
    }

    private fun success(response: Response): Map<String, Any?> {
        val finalUrl = response.request.url
        if (finalUrl.scheme != "http" && finalUrl.scheme != "https") {
            return PluginHttpParams.result(false, null, "network", null)
        }
        val headers = LinkedHashMap<String, String>()
        for (name in response.headers.names()) {
            headers[name] = response.headers.values(name).joinToString(",")
        }
        val source = response.body?.source()
        if (source == null) {
            return PluginHttpParams.result(
                ok = response.code in 200..299,
                status = response.code,
                error = null,
                text = null,
                headers = headers,
            )
        }
        val cap = PluginHttpParams.MAX_BODY_BYTES.toLong()
        val tooLarge = try {
            source.request(cap + 1L)
        } catch (_: IOException) {
            return PluginHttpParams.result(false, response.code, "network", null, headers)
        }
        if (tooLarge) {
            return PluginHttpParams.result(
                ok = false,
                status = response.code,
                error = "too_large",
                text = null,
                headers = headers,
            )
        }
        val text = try {
            source.readUtf8()
        } catch (_: IOException) {
            return PluginHttpParams.result(false, response.code, "network", null, headers)
        }
        return PluginHttpParams.result(
            ok = response.code in 200..299,
            status = response.code,
            error = null,
            text = text,
            headers = headers,
        )
    }

    private class Pending(
        val call: Call,
        val onDone: (Map<String, Any?>) -> Unit,
    ) {
        private val done = AtomicBoolean(false)

        fun complete(result: Map<String, Any?>) {
            if (done.compareAndSet(false, true)) {
                onDone(result)
            }
        }
    }
}
