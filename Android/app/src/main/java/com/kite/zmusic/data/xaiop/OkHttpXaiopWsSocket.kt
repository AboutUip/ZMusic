package com.kite.zmusic.data.xaiop

import io.xaiop.ws.WsSocket
import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

internal class OkHttpXaiopWsSocket : WsSocket {
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var protocol: String? = null
    private val readyState = AtomicInteger(WsSocket.CONNECTING)
    private val messageHandlers = CopyOnWriteArrayList<Consumer<String>>()
    private val closeHandlers = CopyOnWriteArrayList<Runnable>()
    private val errorHandlers = CopyOnWriteArrayList<Consumer<Throwable>>()
    private val openHandlers = CopyOnWriteArrayList<Runnable>()
    private val closeFired = AtomicInteger(0)

    fun attach(socket: WebSocket, negotiatedProtocol: String?) {
        webSocket = socket
        protocol = negotiatedProtocol?.takeIf { it.isNotEmpty() }
        readyState.set(WsSocket.OPEN)
        for (h in openHandlers) {
            runCatching { h.run() }
        }
    }

    fun emitText(text: String) {
        for (h in messageHandlers) {
            try {
                h.accept(text)
            } catch (ex: RuntimeException) {
                fail(ex)
            }
        }
    }

    fun fail(err: Throwable) {
        for (h in errorHandlers) {
            runCatching { h.accept(err) }
        }
    }

    fun markClosing() {
        readyState.compareAndSet(WsSocket.OPEN, WsSocket.CLOSING)
    }

    fun markClosed() {
        readyState.set(WsSocket.CLOSED)
    }

    fun fireClose() {
        if (!closeFired.compareAndSet(0, 1)) return
        for (h in closeHandlers) {
            runCatching { h.run() }
        }
    }

    override fun readyState(): Int = readyState.get()

    override fun bufferedAmount(): Long = 0

    override fun protocol(): String? = protocol

    override fun send(text: String) {
        val socket = webSocket
        if (socket == null || readyState.get() != WsSocket.OPEN) {
            throw IllegalStateException("WebSocket is not OPEN")
        }
        if (!socket.send(text)) {
            throw IllegalStateException("WebSocket send queue is full")
        }
    }

    override fun sendBinary(data: ByteArray) {
        val socket = webSocket
        if (socket == null || readyState.get() != WsSocket.OPEN) {
            throw IllegalStateException("WebSocket is not OPEN")
        }
        if (!socket.send(data.toByteString())) {
            throw IllegalStateException("WebSocket send queue is full")
        }
    }

    override fun close(code: Int, reason: String?) {
        val socket = webSocket
        if (socket == null) {
            readyState.set(WsSocket.CLOSED)
            fireClose()
            return
        }
        if (readyState.compareAndSet(WsSocket.OPEN, WsSocket.CLOSING) ||
            readyState.get() == WsSocket.CLOSING
        ) {
            val clipped = (reason ?: "").let { if (it.length > 123) it.substring(0, 123) else it }
            runCatching { socket.close(code, clipped) }
        }
    }

    override fun terminate() {
        readyState.set(WsSocket.CLOSED)
        runCatching { webSocket?.cancel() }
        fireClose()
    }

    override fun onMessage(handler: Consumer<String>) {
        messageHandlers.add(handler)
    }

    override fun onClose(handler: Runnable) {
        closeHandlers.add(handler)
    }

    override fun onError(handler: Consumer<Throwable>) {
        errorHandlers.add(handler)
    }

    override fun onOpen(handler: Runnable) {
        openHandlers.add(handler)
    }

    override fun removeListeners() {
        messageHandlers.clear()
        closeHandlers.clear()
        errorHandlers.clear()
        openHandlers.clear()
    }
}
