package com.kite.zmusic.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private val UrlImageClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
}

private const val MEMORY_MAX_ENTRIES = 20

private val memoryCache = object : LruCache<String, ImageBitmap>(MEMORY_MAX_ENTRIES) {}

private fun sha256Hex(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun diskFileFor(context: Context, url: String): File {
    val dir = File(context.cacheDir, "zmusic_image_cache")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${sha256Hex(url)}.img")
}

/** 不依赖 Coil，用 OkHttp 拉取图片（与工程现有网络栈一致）。 */
@Composable
fun UrlImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = null
        if (url.isNullOrBlank()) return@LaunchedEffect
        val key = url.trim()

        // 1) 内存缓存
        memoryCache.get(key)?.let {
            bitmap = it
            return@LaunchedEffect
        }

        // 2) 磁盘缓存
        val fromDisk: ImageBitmap? = withContext(Dispatchers.IO) {
            runCatching {
                val file = diskFileFor(context, key)
                if (!file.exists()) return@runCatching null
                val bytes = file.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        if (fromDisk != null) {
            memoryCache.put(key, fromDisk)
            bitmap = fromDisk
            return@LaunchedEffect
        }

        // 3) 网络获取 + 写入缓存
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(key).get().build()
                UrlImageClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    val file = diskFileFor(context, key)
                    runCatching { file.writeBytes(bytes) }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
        }?.also { bmp ->
            if (bmp != null) memoryCache.put(key, bmp)
        }
    }
    val b = bitmap
    if (b != null) {
        Image(
            bitmap = b,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
