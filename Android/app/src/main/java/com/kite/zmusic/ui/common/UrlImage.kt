package com.kite.zmusic.ui.common

import android.graphics.BitmapFactory
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
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        // 仍对应本 urlKey 才上屏，避免快速滑格时旧请求回写
        if (fromDisk != null) {
            UrlImageCache.memoryPut(urlKey, fromDisk)
            bitmap = fromDisk
            return@LaunchedEffect
        }

        // 3) 网络：仅此时允许短暂空白
        bitmap = null
        val fromNet = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(urlKey).get().build()
                UrlImageClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    val file = UrlImageCache.diskFile(context, urlKey)
                    runCatching { file.writeBytes(bytes) }
                    UrlImageCache.trimDisk(context)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (fromNet != null) {
            UrlImageCache.memoryPut(urlKey, fromNet)
            bitmap = fromNet
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
