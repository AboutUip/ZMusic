package com.kite.zmusic.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    /** 曲谱网格可能一次露出多枚封面，内存池需盖住常见歌单体量 */
    private const val MEMORY_MAX_ENTRIES = 96
    private const val DISK_MAX_BYTES = 96L * 1024 * 1024
    private const val DISK_MAX_FILES = 400

    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_MAX_ENTRIES) {}

    fun memoryGet(url: String): ImageBitmap? {
        val key = normalizeKey(url) ?: return null
        return memory.get(key)
    }

    fun memoryPut(url: String, bitmap: ImageBitmap) {
        val key = normalizeKey(url) ?: return
        memory.put(key, bitmap)
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
        if (memory.get(key) != null) return
        withContext(Dispatchers.IO) {
            runCatching {
                val file = diskFile(context, key)
                val bytes = when {
                    file.exists() -> file.readBytes()
                    else -> {
                        val req = Request.Builder().url(key).get().build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@runCatching
                            val body = resp.body?.bytes() ?: return@runCatching
                            runCatching { file.writeBytes(body) }
                            trimDiskLocked(context)
                            body
                        }
                    }
                }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    ?: return@runCatching
                memory.put(key, bmp)
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
        parallelism: Int = 6,
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

    /** 缓存键：trim 后的完整 URL，严格一对一，禁止用曲目 id 顶替以免换封面后串图。 */
    fun normalizeKey(url: String?): String? =
        url?.trim()?.takeIf { it.isNotEmpty() }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
