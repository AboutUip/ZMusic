package com.kite.zmusic.data.xaiop

import io.xaiop.stream.TransportKind
import io.xaiop.stream.XaiopStream
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response as OkHttpResponse

/**
 * 与 Android 相同：解析走 XAIOP Maven 包，HTTP 走 OkHttp，不用 JDK HttpClient。
 */
class OkHttpXaiop(
    http: OkHttpClient,
) {
    private val streamHttp: OkHttpClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun stream(
        url: String,
        options: XaiopStream.Options = XaiopStream.Options.defaults(),
    ): XaiopStream = XaiopStream(url, options)

    fun sendHttp(
        stream: XaiopStream,
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: String? = null,
        timeoutMs: Long? = null,
    ): CompletableFuture<Any?> {
        val done = CompletableFuture<Any?>()
        stream.onDone { snapshot -> done.complete(snapshot) }
        stream.onError { err -> done.completeExceptionally(err) }
        val options = XaiopStream.SendOptions()
            .url(url)
            .transport(TransportKind.RAW)
        options.inputStream = CallBodyInputStream(newCall(url, method, headers, body, timeoutMs))
        return try {
            stream.send(options) ?: done
        } catch (err: RuntimeException) {
            done.completeExceptionally(err)
            done
        }
    }

    private fun newCall(
        url: String,
        method: String,
        headers: Map<String, String>?,
        body: String?,
        timeoutMs: Long?,
    ): okhttp3.Call {
        val client = if (timeoutMs != null && timeoutMs > 0) {
            streamHttp.newBuilder().connectTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        } else {
            streamHttp
        }
        val b = Request.Builder().url(url)
        headers?.forEach { (k, v) -> b.header(k, v) }
        val verb = method.ifBlank { "GET" }
        val media = headers
            ?.entries
            ?.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.toMediaTypeOrNull()
        val requestBody = when {
            verb.equals("GET", ignoreCase = true) || verb.equals("HEAD", ignoreCase = true) -> null
            body != null -> body.toRequestBody(media)
            else -> ByteArray(0).toRequestBody(media)
        }
        return client.newCall(b.method(verb, requestBody).build())
    }
}

internal class CallBodyInputStream(
    private val call: okhttp3.Call,
) : java.io.InputStream() {
    private val lock = Any()
    private var response: OkHttpResponse? = null
    private var delegate: java.io.InputStream? = null
    @Volatile private var closed = false

    private fun ensure(): java.io.InputStream {
        if (closed) throw IOException("closed")
        synchronized(lock) {
            delegate?.let { return it }
            if (closed) throw IOException("closed")
            val resp = call.execute()
            response = resp
            if (!resp.isSuccessful) {
                val code = resp.code
                resp.close()
                throw IOException("HTTP $code")
            }
            val stream = resp.body?.byteStream() ?: throw IOException("empty body")
            delegate = stream
            return stream
        }
    }

    override fun read(): Int = ensure().read()

    override fun read(b: ByteArray, off: Int, len: Int): Int = ensure().read(b, off, len)

    override fun close() {
        closed = true
        call.cancel()
        synchronized(lock) {
            runCatching { delegate?.close() }
            runCatching { response?.close() }
            delegate = null
            response = null
        }
    }
}
