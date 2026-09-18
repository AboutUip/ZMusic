package com.kite.zmusic.ui.player

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kite.zmusic.R
import com.kite.zmusic.ui.chrome.wallpaperCanvasPlacement
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 自定义背景媒体类型：静图 / GIF 动图 / 视频（强制静音）。 */
internal enum class PlayerBackgroundMediaKind {
    Still,
    Gif,
    Video,
}

internal fun playerBackgroundMediaKind(path: String?): PlayerBackgroundMediaKind {
    if (path.isNullOrBlank()) return PlayerBackgroundMediaKind.Still
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "gif" -> PlayerBackgroundMediaKind.Gif
        "mp4", "webm", "mkv", "3gp", "mov", "m4v", "avi" -> PlayerBackgroundMediaKind.Video
        else -> PlayerBackgroundMediaKind.Still
    }
}

internal fun guessBackgroundMediaExtension(context: Context, uri: Uri): String {
    val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    when {
        mime == "image/gif" -> return "gif"
        mime == "image/png" -> return "png"
        mime == "image/webp" -> return "webp"
        mime == "image/jpeg" || mime == "image/jpg" -> return "jpg"
        mime == "video/mp4" -> return "mp4"
        mime == "video/webm" -> return "webm"
        mime == "video/3gpp" -> return "3gp"
        mime == "video/quicktime" -> return "mov"
        mime.startsWith("video/") -> return "mp4"
        mime.startsWith("image/") -> return "jpg"
    }
    val name = uri.lastPathSegment.orEmpty().lowercase()
    val fromName = name.substringAfterLast('.', "").substringBefore('?')
    return when (fromName) {
        "gif", "png", "webp", "jpg", "jpeg",
        "mp4", "webm", "mkv", "3gp", "mov", "m4v",
        -> if (fromName == "jpeg") "jpg" else fromName
        else -> "jpg"
    }
}

/** 缩略图：静图/GIF 首帧，视频取 0 时刻帧。 */
internal fun decodeBackgroundThumb(path: String, maxSide: Int = 320): ImageBitmap? {
    if (path.isBlank() || !File(path).isFile) return null
    return when (playerBackgroundMediaKind(path)) {
        PlayerBackgroundMediaKind.Video -> decodeVideoFrame(path, maxSide)
        PlayerBackgroundMediaKind.Still,
        PlayerBackgroundMediaKind.Gif,
        -> {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            val w = opts.outWidth.coerceAtLeast(1)
            val h = opts.outHeight.coerceAtLeast(1)
            val sample = max(1, max(w, h) / maxSide)
            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, decode)?.asImageBitmap()
        }
    }
}

private fun decodeVideoFrame(path: String, maxSide: Int): ImageBitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: return null
        val w = frame.width.coerceAtLeast(1)
        val h = frame.height.coerceAtLeast(1)
        val scale = maxSide.toFloat() / max(w, h).toFloat()
        val bmp = if (scale < 0.999f) {
            android.graphics.Bitmap.createScaledBitmap(
                frame,
                (w * scale).toInt().coerceAtLeast(1),
                (h * scale).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== frame) frame.recycle() }
        } else {
            frame
        }
        bmp.asImageBitmap()
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * 播放页自定义背景媒体层：静图 / 动图 GIF / 静音循环视频。
 * [coverFill]=true 时按壁纸裁切锚点铺满；用户预设一般为 Fit + 位移缩放。
 */
@Composable
internal fun PlayerBackgroundMedia(
    path: String,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    coverFill: Boolean,
    modifier: Modifier = Modifier,
) {
    when (playerBackgroundMediaKind(path)) {
        PlayerBackgroundMediaKind.Gif -> PlayerBackgroundGif(
            path = path,
            offsetX = offsetX,
            offsetY = offsetY,
            scale = scale,
            coverFill = coverFill,
            modifier = modifier,
        )
        PlayerBackgroundMediaKind.Video -> PlayerBackgroundVideo(
            path = path,
            offsetX = offsetX,
            offsetY = offsetY,
            scale = scale,
            coverFill = coverFill,
            modifier = modifier,
        )
        PlayerBackgroundMediaKind.Still -> {
            if (coverFill) {
                PlayerCoverFillStill(
                    path = path,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    scale = scale,
                    modifier = modifier,
                )
            } else {
                LocalPathImage(
                    path = path,
                    contentDescription = null,
                    modifier = modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = (offsetX - 0.5f) * size.width * 0.55f
                        translationY = (offsetY - 0.5f) * size.height * 0.55f
                    },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun PlayerCoverFillStill(
    path: String,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp == null) {
        Box(modifier.background(Color(0xFF12141A)))
        return
    }
    Canvas(modifier.clipToBounds()) {
        val place = wallpaperCanvasPlacement(
            viewW = size.width,
            viewH = size.height,
            imgW = bmp.width,
            imgH = bmp.height,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            coverFill = true,
        ) ?: return@Canvas
        drawImage(
            image = bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset(place.x, place.y),
            dstSize = IntSize(place.w, place.h),
            filterQuality = FilterQuality.Medium,
        )
    }
}

@Composable
private fun PlayerBackgroundGif(
    path: String,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    coverFill: Boolean,
    modifier: Modifier = Modifier,
) {
    var drawable by remember(path) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(path) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(File(path))
                ImageDecoder.decodeDrawable(source)
            }.getOrNull()
        }
        drawable = decoded
        (decoded as? Animatable)?.start()
    }
    DisposableEffect(path) {
        onDispose {
            (drawable as? Animatable)?.stop()
            drawable = null
        }
    }
    val d = drawable
    if (d == null) {
        Box(modifier.background(Color(0xFF12141A)))
        return
    }
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                adjustViewBounds = false
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { view ->
            if (view.drawable !== d) {
                view.setImageDrawable(d)
                (d as? Animatable)?.start()
            }
            view.scaleType = if (coverFill) {
                ImageView.ScaleType.CENTER_CROP
            } else {
                ImageView.ScaleType.FIT_CENTER
            }
        },
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = (offsetX - 0.5f) * size.width * 0.55f
                translationY = (offsetY - 0.5f) * size.height * 0.55f
            },
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerBackgroundVideo(
    path: String,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    coverFill: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(path) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus= */ false,
            )
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
            volume = 0f
        }
    }
    DisposableEffect(player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME,
                -> {
                    player.volume = 0f
                    player.playWhenReady = true
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> player.playWhenReady = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            // 必须 TextureView：SurfaceView 不进 Compose display list，graphicsLayer
            // 位移/缩放时画面偶发错位乱跳（media3#1237）。
            (LayoutInflater.from(ctx).inflate(
                R.layout.player_background_video,
                /* root= */ null,
                /* attachToRoot= */ false,
            ) as PlayerView).apply {
                this.player = player
                resizeMode = if (coverFill) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setEnableComposeSurfaceSyncWorkaround(true)
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.resizeMode = if (coverFill) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            player.volume = 0f
        },
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = (offsetX - 0.5f) * size.width * 0.55f
                translationY = (offsetY - 0.5f) * size.height * 0.55f
            },
    )
}

@Composable
internal fun LocalPathThumb(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) { decodeBackgroundThumb(path) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
        )
    } else {
        Box(modifier.background(Color(0xFF12141A)))
    }
}
