package com.kite.zmusic.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.kite.zmusic.ui.common.UrlImageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 封面 Bitmap 加载（通知 / 锁屏），与 [UrlImageCache] 共用磁盘缓存目录。
 */
object ArtworkLoader {
    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val memoryCache = object : LruCache<String, Bitmap>(8) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun loadBitmap(context: Context, url: String?, maxEdge: Int = 512): Bitmap? {
        if (url.isNullOrBlank()) return null
        val key = url.trim()
        memoryCache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val file = UrlImageCache.diskFile(context, key)
                val bytes = when {
                    file.exists() -> file.readBytes()
                    else -> {
                        val req = Request.Builder().url(key).get().build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@runCatching null
                            val body = resp.body?.bytes() ?: return@runCatching null
                            runCatching { file.writeBytes(body) }
                            UrlImageCache.trimDisk(context)
                            body
                        }
                    }
                } ?: return@runCatching null

                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                val sample = calculateInSampleSize(opts.outWidth, opts.outHeight, maxEdge)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)?.also {
                    memoryCache.put(key, it)
                }
            }.getOrNull()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / sample > maxEdge || h / sample > maxEdge) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}
