package com.kite.zmusic.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val UrlImageClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
}

/** 与 [UrlImage] 同一套缓存；只取位图，由调用方决定怎么铺进格子。 */
@Composable
fun rememberUrlImageBitmap(
    url: String?,
    maxPx: Int = UrlImageCache.DECODE_MAX_PX,
): ImageBitmap? {
    val context = LocalContext.current
    val urlKey = UrlImageCache.normalizeKey(url).orEmpty()
    // 不按 urlKey 重置：个人页刷新换参、Lazy 复用时先留旧图，避免闪占位。
    var heldKey by remember { mutableStateOf("") }
    var heldBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val memoryHit = if (urlKey.isNotEmpty()) {
        UrlImageCache.memoryGet(urlKey, maxPx)
    } else {
        null
    }

    LaunchedEffect(urlKey, maxPx) {
        if (urlKey.isEmpty()) {
            heldKey = ""
            heldBitmap = null
            return@LaunchedEffect
        }
        UrlImageCache.memoryGet(urlKey, maxPx)?.let { hit ->
            heldKey = urlKey
            heldBitmap = hit
            return@LaunchedEffect
        }
        if (UrlImageCache.isLocalMediaUri(urlKey)) {
            val local = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = UrlImageCache.readLocalBytes(context, urlKey) ?: return@runCatching null
                    UrlImageCache.decodeSampledBitmap(bytes, maxPx)
                }.getOrNull()
            }
            if (local != null) {
                UrlImageCache.memoryPut(urlKey, local, maxPx)
                heldKey = urlKey
                heldBitmap = local
            }
            return@LaunchedEffect
        }
        val fromDisk: ImageBitmap? = withContext(Dispatchers.IO) {
            runCatching {
                val file = UrlImageCache.diskFile(context, urlKey)
                if (!file.exists()) return@runCatching null
                val bytes = file.readBytes()
                val bmp = UrlImageCache.decodeSampledBitmap(bytes, maxPx)
                if (bmp == null) file.delete()
                bmp
            }.getOrNull()
        }
        if (fromDisk != null) {
            UrlImageCache.memoryPut(urlKey, fromDisk, maxPx)
            heldKey = urlKey
            heldBitmap = fromDisk
            return@LaunchedEffect
        }
        val fromNet = withContext(Dispatchers.IO) {
            runCatching {
                val req = UrlImageCache.imageRequest(urlKey)
                UrlImageClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    val bmp = UrlImageCache.decodeSampledBitmap(bytes, maxPx) ?: return@use null
                    val file = UrlImageCache.diskFile(context, urlKey)
                    runCatching { file.writeBytes(bytes) }
                    UrlImageCache.trimDisk(context)
                    bmp
                }
            }.getOrNull()
        }
        if (fromNet != null) {
            UrlImageCache.memoryPut(urlKey, fromNet, maxPx)
            heldKey = urlKey
            heldBitmap = fromNet
        }
    }

    return when {
        urlKey.isEmpty() -> null
        memoryHit != null -> memoryHit
        heldKey == urlKey -> heldBitmap
        heldBitmap != null -> heldBitmap
        else -> null
    }
}

/** 不依赖 Coil，用 OkHttp 拉取图片（与工程现有网络栈一致）。键为 URL，与 [UrlImageCache] 一致。 */
@Composable
fun UrlImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    showPlaceholder: Boolean = true,
    maxPx: Int = UrlImageCache.DECODE_MAX_PX,
) {
    val b = rememberUrlImageBitmap(url, maxPx)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else if (showPlaceholder) {
            if (maxPx <= UrlImageCache.THUMB_MAX_PX) {
                Box(Modifier.fillMaxSize().background(MainPalette.Placeholder))
            } else {
                CoverPlaceholderVinyl(Modifier.fillMaxSize())
            }
        }
    }
}
