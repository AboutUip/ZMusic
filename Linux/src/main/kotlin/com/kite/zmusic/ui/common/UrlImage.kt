package com.kite.zmusic.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kite.zmusic.data.LocalLibrary
import com.kite.zmusic.ui.theme.MainPalette
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image

private val client = OkHttpClient()
private val memory = ConcurrentHashMap<String, ImageBitmap>()

@Composable
fun UrlImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(url) { mutableStateOf(url?.let { memory[it] }) }
    LaunchedEffect(url) {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) {
            bitmap = null
            return@LaunchedEffect
        }
        memory[u]?.let {
            bitmap = it
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                LocalLibrary.readImageCache(u)?.let { bytes ->
                    Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
                } ?: run {
                    val req = Request.Builder().url(u).header("Referer", "https://music.163.com").get().build()
                    client.newCall(req).execute().use { resp ->
                        val bytes = resp.body?.bytes() ?: return@use null
                        LocalLibrary.writeImageCache(u, bytes)
                        Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
                    }
                }
            }.getOrNull()?.also { memory[u] = it }
        }
    }
    Box(modifier.background(MainPalette.Placeholder)) {
        Crossfade(targetState = bitmap, animationSpec = tween(220), label = "cover") { bmp ->
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }
        }
    }
}
