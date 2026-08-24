package com.kite.zmusic.workshop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.coroutineContext

data class WorkshopDownloadMeta(
    val contentLength: Long,
    val sha256: String,
    val kid: String,
    val signature: String,
)

/**
 * 下载工坊包：必须 Content-Length + 三头齐全，读完后严格验签，失败删文件。
 */
class WorkshopDownloader(
    private val http: OkHttpClient,
    private val client: WorkshopClient,
    private val auth: WorkshopAuthStore,
) {
    suspend fun download(
        pluginId: String,
        detail: WorkshopPluginDetail,
        dest: File,
        onProgress: (received: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            val reqBuilder = Request.Builder()
                .url(client.downloadUrl(pluginId))
                .get()
            client.authHeaders().forEach { (k, v) -> reqBuilder.header(k, v) }
            http.newCall(reqBuilder.build()).execute().use { resp ->
                coroutineContext.ensureActive()
                val body = resp.body ?: error("empty body")
                if (resp.code != 200) {
                    val text = runCatching { body.string() }.getOrDefault("")
                    if (text.contains("unauthorized") || resp.code == 401) {
                        auth.clear()
                        throw WorkshopApiError.Unauthorized
                    }
                    error("download http ${resp.code}")
                }
                val lengthHeader = resp.header("Content-Length")?.toLongOrNull()
                    ?: error("missing Content-Length")
                if (lengthHeader <= 0L || lengthHeader > WorkshopClient.maxZppBytes()) {
                    error("bad Content-Length")
                }
                if (lengthHeader != detail.sizeBytes) {
                    error("size mismatch detail")
                }
                val hSha = resp.header("X-Zpp-Sha256")?.trim()?.lowercase().orEmpty()
                val hKid = resp.header("X-Zpp-Kid")?.trim().orEmpty()
                val hSig = resp.header("X-Zpp-Signature")?.trim().orEmpty()
                if (hSha.isEmpty() || hKid.isEmpty() || hSig.isEmpty()) {
                    error("missing signature headers")
                }
                if (hSha != detail.sha256.lowercase()) error("sha256 header≠detail")
                if (hKid != detail.signature.kid) error("kid header≠detail")
                if (hSig != detail.signature.sig) error("sig header≠detail")
                if (detail.signature.alg != "Ed25519") error("bad alg")
                val pub = WorkshopKeys.publicKey(hKid) ?: error("unknown kid")

                onProgress(0L, lengthHeader)
                dest.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var received = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            received += n
                            if (received > lengthHeader) {
                                dest.delete()
                                error("overflow")
                            }
                            onProgress(received, lengthHeader)
                        }
                        if (received != lengthHeader) {
                            dest.delete()
                            error("length mismatch")
                        }
                    }
                }

                if (!WorkshopEd25519.verifyFileSha256(
                        file = dest,
                        expectedSha256Hex = hSha,
                        sigBase64 = hSig,
                        publicKey32 = pub,
                    )
                ) {
                    dest.delete()
                    error("signature verify failed")
                }
                dest
            }
        }.onFailure {
            dest.delete()
            if (it is WorkshopApiError.Unauthorized) {
                // token invalid — caller may toast; WorkshopClient clears on JSON paths
            }
        }
    }
}
