package com.kite.zmusic.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val UrlImageClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
}

/** 不依赖 Coil，用 OkHttp 拉取图片（与工程现有网络栈一致）。键为 URL，与 [UrlImageCache] 一致。 */
@Composable
fun UrlImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    showPlaceholder: Boolean = true,
) {
    val context = LocalContext.current
    val urlKey = UrlImageCache.normalizeKey(url).orEmpty()
    // 同步吃内存缓存，避免重组首帧空白闪一下；remember 绑 urlKey，防 Lazy 复用串图
    var bitmap by remember(urlKey) {
        mutableStateOf(
            urlKey.takeIf { it.isNotEmpty() }?.let { UrlImageCache.memoryGet(it) },
        )
    }
    LaunchedEffect(urlKey) {
        if (urlKey.isEmpty()) {
            bitmap = null
            return@LaunchedEffect
        }

        // 1) 内存缓存：直接命中，切勿先清空
        UrlImageCache.memoryGet(urlKey)?.let {
            bitmap = it
            return@LaunchedEffect
        }

        // 2) 磁盘缓存（无实时性要求，优先本地）
        val fromDisk: ImageBitmap? = withContext(Dispatchers.IO) {
            runCatching {
                val file = UrlImageCache.diskFile(context, urlKey)
                if (!file.exists()) return@runCatching null
                val bytes = file.readBytes()
                decodeSampledBitmap(bytes)
            }.getOrNull()
        }
        // 仍对应本 urlKey 才上屏，避免快速滑格时旧请求回写
        if (fromDisk != null) {
            UrlImageCache.memoryPut(urlKey, fromDisk)
            bitmap = fromDisk
            return@LaunchedEffect
        }

        // 3) 网络：已有图就先留着，避免翻页重组把已加载封面清掉
        val fromNet = withContext(Dispatchers.IO) {
            runCatching {
                val req = UrlImageCache.imageRequest(urlKey)
                UrlImageClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    val file = UrlImageCache.diskFile(context, urlKey)
                    runCatching { file.writeBytes(bytes) }
                    UrlImageCache.trimDisk(context)
                    decodeSampledBitmap(bytes)
                }
            }.getOrNull()
        }
        if (fromNet != null) {
            UrlImageCache.memoryPut(urlKey, fromNet)
            bitmap = fromNet
        }
    }
    val b = bitmap
    Box(modifier, contentAlignment = Alignment.Center) {
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else if (showPlaceholder) {
            CoverPlaceholderVinyl(Modifier.fillMaxSize())
        }
    }
}

private fun decodeSampledBitmap(bytes: ByteArray, maxPx: Int = 2048): ImageBitmap? {
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
