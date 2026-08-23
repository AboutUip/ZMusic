package com.kite.zmusic.data

import com.kite.zmusic.data.xaiop.OkHttpXaiop
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CompletionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 社区公开目录的 HTTP：无 cookie，Accept text/xaiop，失败静默重试后灵动岛提示。
 */
class CommunityXaiopClient(
    private val xaiop: OkHttpXaiop,
    private val community: CommunityServerStore,
    private val notices: IslandNoticeCenter,
) {
    fun authority(): String {
        val e = community.current()
        val host = e.host.trim()
        return if (e.port == 80) host else "$host:${e.port}"
    }

    fun httpUrl(path: String, query: String = ""): String {
        val q = if (query.isEmpty()) "" else "?$query"
        return "http://${authority()}$path$q"
    }

    fun resolveUrl(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        if (t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true)
        ) {
            return t
        }
        if (t.startsWith("//")) return "http:$t"
        if (t.startsWith("/")) return "http://${authority()}$t"
        return t
    }

    /** 启动检查用：失败不弹「网络波动」。最多两试。 */
    suspend fun getQuiet(url: String): Any? {
        runAttempt { get(url) }.getOrNull()?.let { return it }
        return runAttempt { get(url) }.getOrNull()
    }

    suspend fun get(url: String): Any? = withContext(Dispatchers.IO) {
        withTimeout(RequestTimeoutMs) {
            val stream = xaiop.stream(url)
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { stream.abort() }
                xaiop.sendHttp(
                    stream,
                    url,
                    headers = mapOf("Accept" to "text/xaiop"),
                    timeoutMs = RequestTimeoutMs,
                ).whenComplete { value, err ->
                    if (!cont.isActive) return@whenComplete
                    if (err != null) {
                        cont.resumeWithException(unwrap(err))
                    } else {
                        cont.resume(value)
                    }
                }
            }
        }
    }

    suspend fun <T> withRemoteRetry(block: suspend () -> T): T? {
        runAttempt(block).getOrNull()?.let { return it }
        runAttempt(block).getOrNull()?.let { return it }
        notices.show("网络波动")
        return runAttempt(block).getOrNull()
    }

    private suspend fun <T> runAttempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (_: TimeoutCancellationException) {
            Result.failure(IllegalStateException("timeout"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

    private fun unwrap(err: Throwable): Throwable {
        var t = err
        while (t is CompletionException && t.cause != null) {
            t = t.cause!!
        }
        return t
    }

    companion object {
        private const val RequestTimeoutMs = 30_000L
    }
}
