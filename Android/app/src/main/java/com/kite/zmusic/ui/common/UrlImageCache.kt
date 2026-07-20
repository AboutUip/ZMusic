package com.kite.zmusic.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 封面内存/磁盘缓存：与 [UrlImage] / [com.kite.zmusic.playback.ArtworkLoader] 共用目录。
 * 预取下一首/上一首封面时走这里，切胶时内存命中即无感。
 */
object UrlImageCache {
    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private const val MEMORY_MAX_ENTRIES = 24
    private const val DISK_MAX_BYTES = 48L * 1024 * 1024
    private const val DISK_MAX_FILES = 120

    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_MAX_ENTRIES) {}

    fun memoryGet(url: String): ImageBitmap? = memory.get(url)

    fun memoryPut(url: String, bitmap: ImageBitmap) {
        memory.put(url, bitmap)
    }

    fun diskFile(context: Context, url: String): File {
        val dir = File(context.applicationContext.cacheDir, "zmusic_image_cache")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${sha256Hex(url)}.img")
    }

    /** 预热磁盘 + 内存，供黑胶手势瞬间露脸。 */
    suspend fun prefetch(context: Context, url: String?) {
        val key = url?.trim().orEmpty()
        if (key.isEmpty()) return
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

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
