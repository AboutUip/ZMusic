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

/** 仅绝对 http(s) 才直取 TOS。相对路径必须走社区流，避免拼到社区主机后被当成无鉴权直链。 */
internal fun workshopDirectPackageUrl(packageUrl: String): String? {
    val t = packageUrl.trim()
    if (t.startsWith("https://", ignoreCase = true) ||
        t.startsWith("http://", ignoreCase = true)
    ) {
        return t
    }
    return null
}

/** TOS 401 / 正文含 unauthorized 不得清工坊 token。 */
internal fun workshopDownloadClearsAuth(direct: Boolean, httpCode: Int, body: String): Boolean =
    !direct && (httpCode == 401 || body.contains("unauthorized"))

/**
 * 社区流必须有 Content-Length。TOS 直取若缺头则用详情 `size_bytes`（仍 ≤64MiB 且与详情一致）。
 */
internal fun workshopExpectedDownloadBytes(
    direct: Boolean,
    contentLengthHeader: String?,
    sizeBytes: Long,
    maxBytes: Long = WorkshopClient.maxZppBytes(),
): Long {
    val header = contentLengthHeader?.trim()?.toLongOrNull()
    if (header != null) {
        if (header <= 0L || header > maxBytes) error("bad Content-Length")
        if (header != sizeBytes) error("size mismatch detail")
        return header
    }
    if (!direct) error("missing Content-Length")
    if (sizeBytes <= 0L || sizeBytes > maxBytes) error("bad size")
    return sizeBytes
}

/**
 * 下载工坊包：优先直取 TOS；否则走社区流。读完后严格验签，失败删文件。
 */
class WorkshopDownloader(
    private val http: OkHttpClient,
    private val downloadUrl: (pluginId: String) -> String,
    private val authHeaders: () -> Map<String, String>,
    private val ackDownload: (pluginId: String) -> Unit,
    private val clearAuth: () -> Unit,
) {
    constructor(
        http: OkHttpClient,
        client: WorkshopClient,
        auth: WorkshopAuthStore,
    ) : this(
        http = http,
        downloadUrl = client::downloadUrl,
        authHeaders = client::authHeaders,
        ackDownload = client::ackDownload,
        clearAuth = auth::clear,
    )

    suspend fun download(
        pluginId: String,
        detail: WorkshopPluginDetail,
        dest: File,
        onProgress: (received: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            val directUrl = workshopDirectPackageUrl(detail.packageUrl)
            val direct = directUrl != null
            val req = if (directUrl != null) {
                Request.Builder().url(directUrl).get().build()
            } else {
                val reqBuilder = Request.Builder()
                    .url(downloadUrl(pluginId))
                    .get()
                authHeaders().forEach { (k, v) -> reqBuilder.header(k, v) }
                reqBuilder.build()
            }
            http.newCall(req).execute().use { resp ->
                coroutineContext.ensureActive()
                val body = resp.body ?: error("empty body")
                if (resp.code != 200) {
                    val text = runCatching { body.string() }.getOrDefault("")
                    if (workshopDownloadClearsAuth(direct, resp.code, text)) {
                        clearAuth()
                        throw WorkshopApiError.Unauthorized
                    }
                    error("download http ${resp.code}")
                }
                val expectedLen = workshopExpectedDownloadBytes(
                    direct = direct,
                    contentLengthHeader = resp.header("Content-Length"),
                    sizeBytes = detail.sizeBytes,
                )
                val hSha: String
                val hKid: String
                val hSig: String
                if (direct) {
                    hSha = detail.sha256.lowercase()
                    hKid = detail.signature.kid
                    hSig = detail.signature.sig
                    if (hSha.isEmpty() || hKid.isEmpty() || hSig.isEmpty()) {
                        error("missing signature")
                    }
                } else {
                    hSha = resp.header("X-Zpp-Sha256")?.trim()?.lowercase().orEmpty()
                    hKid = resp.header("X-Zpp-Kid")?.trim().orEmpty()
                    hSig = resp.header("X-Zpp-Signature")?.trim().orEmpty()
                    if (hSha.isEmpty() || hKid.isEmpty() || hSig.isEmpty()) {
                        error("missing signature headers")
                    }
                    if (hSha != detail.sha256.lowercase()) error("sha256 header≠detail")
                    if (hKid != detail.signature.kid) error("kid header≠detail")
                    if (hSig != detail.signature.sig) error("sig header≠detail")
                }
                if (detail.signature.alg != "Ed25519") error("bad alg")
                val pub = WorkshopKeys.publicKey(hKid) ?: error("unknown kid")

                onProgress(0L, expectedLen)
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
                            if (received > expectedLen) {
                                dest.delete()
                                error("overflow")
                            }
                            onProgress(received, expectedLen)
                        }
                        if (received != expectedLen) {
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
                if (direct) {
                    runCatching { ackDownload(pluginId) }
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
