package com.kite.zmusic.ui.player

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.ui.main.MainPalette
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 用原文件解码。GIF 走 [AnimatedImageDrawable] 保留逐帧，PNG / WebP 的透明通道不铺底。
 */
@Composable
internal fun ListenChatStickerImage(
    stickerId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    var drawable by remember(stickerId) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(stickerId) {
        val file = app.listenTogether.cachedStickerFile(stickerId) ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) { decodeListenSticker(file) } ?: return@LaunchedEffect
        if (!isActive) {
            (decoded as? AnimatedImageDrawable)?.stop()
            return@LaunchedEffect
        }
        drawable = decoded
    }
    DisposableEffect(drawable) {
        onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
    }
    Box(modifier) {
        if (drawable == null) {
            Box(Modifier.fillMaxSize().background(MainPalette.Card))
        }
        val current = drawable
        if (current != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        isClickable = false
                        isFocusable = false
                    }
                },
                update = { view ->
                    view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    if (view.drawable !== current) {
                        (view.drawable as? AnimatedImageDrawable)?.stop()
                        view.setImageDrawable(current)
                        (current as? AnimatedImageDrawable)?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        (current as? Animatable)?.start()
                    }
                },
                onRelease = { view ->
                    (view.drawable as? AnimatedImageDrawable)?.stop()
                    view.setImageDrawable(null)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun decodeListenSticker(file: File): Drawable? = runCatching {
    val source = ImageDecoder.createSource(file)
    ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val w = info.size.width
        val h = info.size.height
        val longEdge = max(w, h)
        if (longEdge > 720 && w > 0 && h > 0) {
            val scale = 720f / longEdge.toFloat()
            decoder.setTargetSize(
                (w * scale).toInt().coerceAtLeast(1),
                (h * scale).toInt().coerceAtLeast(1),
            )
        }
    }
}.getOrNull()
