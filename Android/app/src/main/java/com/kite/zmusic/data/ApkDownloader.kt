package com.kite.zmusic.data

import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

interface AppUpdateDownloader {
    suspend fun download(
        url: String,
        dest: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): Result<File>
}

interface AppUpdateFiles {
    fun apkFile(version: String): File
    fun deleteApk(version: String)
    fun deleteAll()
}

class DiskAppUpdateFiles(private val dir: File) : AppUpdateFiles {
    override fun apkFile(version: String): File {
        dir.mkdirs()
        val safe = ChangelogRoster.normalizeVersion(version).replace(Regex("[^0-9A-Za-z._-]"), "_")
        return File(dir, "ZMusic-$safe.apk")
    }

    override fun deleteApk(version: String) {
        apkFile(version).delete()
    }

    override fun deleteAll() {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { it.delete() }
    }
}

/**
 * APK 字节流：GET + Range 续传，按目录 sha256 / 大小校验。不用 HEAD，不用 XAIOP。
 */
class ApkDownloader(
    private val http: OkHttpClient,
) : AppUpdateDownloader {

    override suspend fun download(
        url: String,
        dest: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): Result<File> = runCatching {
        dest.parentFile?.mkdirs()
        if (dest.isFile && dest.length() == expectedSize && sha256Hex(dest) == expectedSha256) {
            onProgress(expectedSize, expectedSize)
            return@runCatching dest
        }
        var existing = dest.takeIf { it.isFile }?.length() ?: 0L
        if (existing > expectedSize) {
            dest.delete()
            existing = 0L
        }
        if (existing in 1 until expectedSize) {
            onProgress(existing, expectedSize)
        }
        val request = Request.Builder()
            .url(url)
            .get()
            .apply {
                if (existing in 1 until expectedSize) {
                    header("Range", "bytes=$existing-")
                }
            }
            .build()
        http.newCall(request).execute().use { response ->
            coroutineContext.ensureActive()
            if (isJsonFailure(response)) {
                error(httpFailMessage(response.code, response.body?.string().orEmpty()))
            }
            when (response.code) {
                200 -> {
                    dest.delete()
                    writeBody(response, dest, startAt = 0L, expectedSize, onProgress)
                }
                206 -> {
                    if (existing <= 0L) {
                        dest.delete()
                        writeBody(response, dest, startAt = 0L, expectedSize, onProgress)
                    } else {
                        writeBody(response, dest, startAt = existing, expectedSize, onProgress)
                    }
                }
                else -> error("apk http ${response.code}")
            }
        }
        if (!dest.isFile || dest.length() != expectedSize) {
            dest.delete()
            error("apk size mismatch")
        }
        if (sha256Hex(dest) != expectedSha256) {
            dest.delete()
            error("apk sha256 mismatch")
        }
        dest
    }

    private fun writeBody(
        response: Response,
        dest: File,
        startAt: Long,
        expectedSize: Long,
        onProgress: (received: Long, total: Long) -> Unit,
    ) {
        val body = response.body ?: error("empty body")
        RandomAccessFile(dest, "rw").use { raf ->
            raf.seek(startAt)
            var received = startAt
            val buf = ByteArray(64 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    raf.write(buf, 0, n)
                    received += n
                    onProgress(received.coerceAtMost(expectedSize), expectedSize)
                    if (received > expectedSize) {
                        error("apk overflow")
                    }
                }
            }
        }
    }

    companion object {
        internal fun isJsonFailure(response: Response): Boolean {
            if (response.code in 200..299) {
                val type = response.header("Content-Type").orEmpty()
                return type.contains("json", ignoreCase = true)
            }
            return true
        }

        internal fun httpFailMessage(code: Int, body: String): String {
            val named = jsonStringField(body, "Code")
            if (!named.isNullOrBlank()) return named
            val msg = jsonStringField(body, "Message")
            if (!msg.isNullOrBlank()) return msg.take(120)
            return "apk http $code"
        }

        private fun jsonStringField(body: String, key: String): String? {
            val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .find(body)
            return match?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        }

        internal fun sha256Hex(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { b -> "%02x".format(b) }
        }
    }
}
