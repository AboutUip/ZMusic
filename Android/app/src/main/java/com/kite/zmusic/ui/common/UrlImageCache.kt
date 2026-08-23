package com.kite.zmusic.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 封面内存/磁盘缓存：与 [UrlImage] / [com.kite.zmusic.playback.ArtworkLoader] 共用目录。
 * 键为封面 URL（trim 后），保证同一 URL 对应同一图，避免串图。
 */
object UrlImageCache {
    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 曲谱网格 / 歌单翻页后仍要留住已出图的封面；按像素预算而不是张数 */
    private const val MEMORY_MAX_KB = 32 * 1024
    private const val DISK_MAX_BYTES = 96L * 1024 * 1024
    private const val DISK_MAX_FILES = 400
    internal const val DECODE_MAX_PX = 720
    internal const val THUMB_MAX_PX = 256

    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_MAX_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val bytes = value.width.toLong() * value.height.toLong() * 4L
            return (bytes / 1024L).toInt().coerceAtLeast(1)
        }
    }

    fun memoryGet(url: String, maxPx: Int = DECODE_MAX_PX): ImageBitmap? {
        val urlKey = normalizeKey(url) ?: return null
        return memory.get(memoryKey(urlKey, maxPx))
    }

    fun memoryPut(url: String, bitmap: ImageBitmap, maxPx: Int = DECODE_MAX_PX) {
        val urlKey = normalizeKey(url) ?: return
        memory.put(memoryKey(urlKey, maxPx), bitmap)
    }

    fun diskFile(context: Context, url: String): File {
        val key = normalizeKey(url) ?: url.trim()
        val dir = File(context.applicationContext.cacheDir, "zmusic_image_cache")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${sha256Hex(key)}.img")
    }

    /** 预热磁盘 + 内存，供黑胶手势瞬间露脸。 */
    suspend fun prefetch(context: Context, url: String?) {
        val key = normalizeKey(url) ?: return
        if (memory.get(memoryKey(key, DECODE_MAX_PX)) != null) return
        withContext(Dispatchers.IO) {
            runCatching {
                val file = diskFile(context, key)
                val bytes = when {
                    isLocalMediaUri(key) -> readLocalBytes(context, key)
                    file.exists() -> file.readBytes()
                    else -> {
                        val req = imageRequest(key)
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@runCatching
                            resp.body?.bytes()
                        }
                    }
                } ?: return@runCatching
                val bmp = decodeSampledBitmap(bytes) ?: run {
                    if (file.exists()) file.delete()
                    return@runCatching
                }
                if (!isLocalMediaUri(key)) {
                    runCatching { file.writeBytes(bytes) }
                    trimDiskLocked(context)
                }
                memory.put(memoryKey(key, DECODE_MAX_PX), bmp)
            }
        }
    }

    /**
     * 批量预取（曲谱队列等）：按 URL 去重，限并发，优先吃磁盘再走网络。
     * 不要求实时性，命中后 [UrlImage] 直接出图。
     */
    suspend fun prefetchAll(
        context: Context,
        urls: Collection<String?>,
        parallelism: Int = 3,
    ) {
        val distinct = urls.mapNotNull { normalizeKey(it) }.distinct()
        if (distinct.isEmpty()) return
        val appCtx = context.applicationContext
        val sem = Semaphore(parallelism.coerceIn(1, 12))
        coroutineScope {
            distinct.map { url ->
                async {
                    sem.withPermit { prefetch(appCtx, url) }
                }
            }.awaitAll()
        }
    }

    fun trimDisk(context: Context) {
        trimDiskLocked(context.applicationContext)
    }

    private fun trimDiskLocked(context: Context) {
        val dir = File(context.cacheDir, "zmusic_image_cache")
        if (!dir.isDirectory) return
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        var count = files.size
        var i = 0
        while (i < files.size && (total > DISK_MAX_BYTES || count > DISK_MAX_FILES)) {
            val f = files[i++]
            val len = f.length()
            if (f.delete()) {
                total -= len
                count--
            }
        }
    }

    /**
     * 网易图床常校验 Referer。赞助商等第三方图不要带 `music.163.com`，
     * 否则会被对方按盗链拒绝。
     */
    fun imageRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36",
            )
            .get()
        if (needsNeteaseReferer(url)) {
            builder.header("Referer", "https://music.163.com/")
        }
        return builder.build()
    }

    internal fun needsNeteaseReferer(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "music.163.com" ||
            host.endsWith(".music.163.com") ||
            host.endsWith(".music.126.net") ||
            host.endsWith("music.126.net")
    }

    /** 缓存键：trim 后的完整 URL，严格一对一，禁止用曲目 id 顶替以免换封面后串图。 */
    fun normalizeKey(url: String?): String? =
        url?.trim()?.takeIf { it.isNotEmpty() }

    fun isLocalMediaUri(url: String): Boolean {
        val t = url.trim()
        return t.startsWith("content:", ignoreCase = true) ||
            t.startsWith("file:", ignoreCase = true)
    }

    fun readLocalBytes(context: Context, url: String): ByteArray? {
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(url.trim()))?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun decodeSampledBitmap(bytes: ByteArray, maxPx: Int = DECODE_MAX_PX): ImageBitmap? {
        if (bytes.isEmpty()) return null
        decodeRaster(bytes, maxPx)?.let { return it }
        return decodeSvg(bytes, maxPx)
    }

    fun decodeSampledFile(path: String, maxPx: Int = DECODE_MAX_PX): ImageBitmap? {
        val bytes = runCatching { File(path).readBytes() }.getOrNull() ?: return null
        return decodeSampledBitmap(bytes, maxPx)
    }

    private fun decodeRaster(bytes: ByteArray, maxPx: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (w / sample > maxPx || h / sample > maxPx) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }

    /** 社区 logo 可能是 SVG；按内容识别，不看扩展名。 */
    private fun decodeSvg(bytes: ByteArray, maxPx: Int): ImageBitmap? {
        if (!looksLikeSvg(bytes)) return null
        return runCatching {
            val svg = SVG.getFromInputStream(ByteArrayInputStream(bytes))
            svg.documentPreserveAspectRatio = PreserveAspectRatio.LETTERBOX
            val size = maxPx.coerceAtLeast(1)
            val picture = svg.renderToPicture(size, size)
            val bw = picture.width.coerceAtLeast(1)
            val bh = picture.height.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.TRANSPARENT)
            Canvas(bmp).drawPicture(picture)
            bmp.asImageBitmap()
        }.getOrNull()
    }

    private fun looksLikeSvg(bytes: ByteArray): Boolean {
        var offset = 0
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            offset = 3
        }
        val len = (bytes.size - offset).coerceAtMost(512).coerceAtLeast(0)
        if (len <= 0) return false
        val head = String(bytes, offset, len, Charsets.UTF_8).trimStart().lowercase()
        if (head.startsWith("<svg")) return true
        if (head.startsWith("<?xml") && head.contains("<svg")) return true
        return false
    }

    private fun memoryKey(url: String, maxPx: Int): String = "$url|$maxPx"

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
