package com.kite.zmusic.data.xaiop

import io.xaiop.stream.Transport
import io.xaiop.stream.TransportKind
import io.xaiop.stream.XaiopStream
import io.xaiop.ws.XaiopWs
import io.xaiop.ws.XaiopWsConnection
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response as OkHttpResponse
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Android 上替代 SDK 自带的 JDK HttpClient 传输。
 *
 * 解析 / checkpoint / 控制面仍用 Maven 包；不要调用 `Xaiop.stream(url)` 或
 * `XaiopWs.connect(url)`，那两条会在运行时找不到 `java.net.http`。
 */
class OkHttpXaiop(
    http: OkHttpClient,
) {
    private val streamHttp: OkHttpClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val wsHttp: OkHttpClient = streamHttp.newBuilder()
        .pingInterval(20, TimeUnit.SECONDS)
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
    ): CompletableFuture<Any?> = sendRawBody(
        stream = stream,
        url = url,
        input = CallBodyInputStream(newCall(url, method, headers, body, timeoutMs, extraHeaders = null)),
    )

    fun sendSse(
        stream: XaiopStream,
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: String? = null,
        timeoutMs: Long? = null,
        sseEvents: Set<String>? = null,
    ): CompletableFuture<Any?> {
        val extra = if (headers?.keys.orEmpty().any { it.equals("Accept", ignoreCase = true) }) {
            null
        } else {
            mapOf("Accept" to "text/event-stream")
        }
        val call = newCall(url, method, headers, body, timeoutMs, extra)
        return sendRawBody(
            stream = stream,
            url = url,
            input = SseToWireInputStream(CallBodyInputStream(call), sseEvents),
        )
    }

    /**
     * 替代 [XaiopWs.connect]。[XaiopWs.ConnectOptions.httpClient] 会被忽略。
     * 相位回调必须在握手前放进 [options]（与官方 connect 相同，握手成功后会 lockHandlers）。
     */
    fun connect(
        url: String,
        options: XaiopWs.ConnectOptions? = null,
    ): CompletableFuture<XaiopWsConnection> {
        require(url.isNotEmpty()) { "connect requires a non-empty url" }
        val opts = options ?: XaiopWs.ConnectOptions()
        val waitMs = opts.handshakeTimeoutMs ?: 15_000L
        val socket = OkHttpXaiopWsSocket()
        val conn = XaiopWsConnection(socket, opts)
        val handshake = CompletableFuture<XaiopWsConnection>()
        val request = wsRequest(url, opts)
        val client = if (waitMs > 0) {
            wsHttp.newBuilder().connectTimeout(waitMs, TimeUnit.MILLISECONDS).build()
        } else {
            wsHttp
        }
        client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: OkHttpResponse) {
                    socket.attach(webSocket, response.header("Sec-WebSocket-Protocol"))
                    conn.lockHandlers()
                    handshake.complete(conn)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    socket.emitText(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    socket.emitText(bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    socket.markClosing()
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket.markClosed()
                    socket.fireClose()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkHttpResponse?) {
                    socket.fail(t)
                    socket.markClosed()
                    socket.fireClose()
                    handshake.completeExceptionally(t)
                }
            },
        )
        val timed = if (waitMs > 0) handshake.orTimeout(waitMs, TimeUnit.MILLISECONDS) else handshake
        return timed.whenComplete { _, err ->
            if (err == null) return@whenComplete
            socket.terminate()
        }.exceptionally { err ->
            var cause: Throwable = err
            if (cause is java.util.concurrent.CompletionException && cause.cause != null) {
                cause = cause.cause!!
            }
            if (cause is TimeoutException) {
                throw TimeoutException("WebSocket handshake timeout after ${waitMs}ms")
            }
            throw cause
        }
    }

    private fun sendRawBody(
        stream: XaiopStream,
        url: String,
        input: java.io.InputStream,
    ): CompletableFuture<Any?> {
        val done = CompletableFuture<Any?>()
        stream.onDone { snapshot -> done.complete(snapshot) }
        stream.onError { err -> done.completeExceptionally(err) }
        val options = XaiopStream.SendOptions()
            .url(url)
            .transport(TransportKind.RAW)
        options.inputStream = input
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
        extraHeaders: Map<String, String>?,
    ): okhttp3.Call {
        val client = if (timeoutMs != null && timeoutMs > 0) {
            streamHttp.newBuilder().connectTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        } else {
            streamHttp
        }
        return client.newCall(httpRequest(url, method, headers, body, extraHeaders))
    }

    private fun httpRequest(
        url: String,
        method: String,
        headers: Map<String, String>?,
        body: String?,
        extraHeaders: Map<String, String>?,
    ): Request {
        val b = Request.Builder().url(url)
        headers?.forEach { (k, v) -> b.header(k, v) }
        extraHeaders?.forEach { (k, v) -> b.header(k, v) }
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
        return b.method(verb, requestBody).build()
    }

    private fun wsRequest(url: String, opts: XaiopWs.ConnectOptions): Request {
        val b = Request.Builder().url(url)
        opts.headers?.forEach { (k, v) -> b.header(k, v) }
        val protocols = opts.protocols
        if (!protocols.isNullOrEmpty()) {
            b.header("Sec-WebSocket-Protocol", protocols.joinToString(", "))
        }
        return b.build()
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

/**
 * 把 SSE 帧收成 XAIOP 文本。分块规则与 SDK [Transport.parseSseBlock] 一致。
 */
internal class SseToWireInputStream(
    private val upstream: java.io.InputStream,
    private val allow: Set<String>?,
) : java.io.InputStream() {
    private val reader = upstream.bufferedReader(Charsets.UTF_8)
    private val carry = StringBuilder()
    private var pending = ByteArray(0)
    private var pendingAt = 0
    private var eof = false

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len <= 0) return 0
        while (pendingAt >= pending.size && !eof) {
            pull()
        }
        if (pendingAt >= pending.size) return -1
        val n = minOf(len, pending.size - pendingAt)
        System.arraycopy(pending, pendingAt, b, off, n)
        pendingAt += n
        return n
    }

    private fun pull() {
        while (true) {
            val line = reader.readLine()
            if (line == null) {
                eof = true
                queue(Transport.parseSseBlock(carry.toString(), allow))
                carry.setLength(0)
                return
            }
            if (line.isEmpty()) {
                val data = Transport.parseSseBlock(carry.toString(), allow)
                carry.setLength(0)
                if (data.isNotEmpty()) {
                    queue(data)
                    return
                }
            } else {
                if (carry.isNotEmpty()) carry.append('\n')
                carry.append(line)
            }
        }
    }

    private fun queue(data: String) {
        if (data.isEmpty()) {
            pending = ByteArray(0)
            pendingAt = 0
            return
        }
        val wire = if (data.endsWith("\n")) data else "$data\n"
        pending = wire.toByteArray(Charsets.UTF_8)
        pendingAt = 0
    }

    override fun close() {
        runCatching { reader.close() }
        runCatching { upstream.close() }
    }
}
