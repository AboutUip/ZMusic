package com.kite.zmusic.playback

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.asAndroidBitmap
import com.kite.zmusic.ui.common.UrlImageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
                    UrlImageCache.isLocalMediaUri(key) -> UrlImageCache.readLocalBytes(context, key)
                    file.exists() -> file.readBytes()
                    else -> {
                        val req = UrlImageCache.imageRequest(key)
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@runCatching null
                            resp.body?.bytes()
                        }
                    }
                } ?: return@runCatching null
                val bmp = UrlImageCache.decodeSampledBitmap(bytes, maxEdge)?.asAndroidBitmap()
                if (bmp == null) {
                    if (file.exists()) file.delete()
                    return@runCatching null
                }
                if (!UrlImageCache.isLocalMediaUri(key)) {
                    runCatching { file.writeBytes(bytes) }
                    UrlImageCache.trimDisk(context)
                }
                memoryCache.put(key, bmp)
                bmp
            }.getOrNull()
        }
    }
}
