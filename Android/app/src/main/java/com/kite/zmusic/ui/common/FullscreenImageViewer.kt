package com.kite.zmusic.ui.common

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.icons.ZIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min

/** 任意页面调用即可打开全屏看图。由 [ImagePreviewHost] 提供。 */
val LocalImagePreview = staticCompositionLocalOf<(String) -> Unit> { {} }

private const val PreviewMaxPx = 1600
private const val PreviewMaxScale = 4f
private const val PreviewDoubleScale = 2.5f

@Composable
fun ImagePreviewHost(content: @Composable () -> Unit) {
    var url by remember { mutableStateOf<String?>(null) }
    CompositionLocalProvider(
        LocalImagePreview provides { next ->
            if (next.isNotBlank()) url = next
        },
    ) {
        content()
        val current = url
        if (current != null) {
            FullscreenImageViewer(
                url = current,
                onDismiss = { url = null },
            )
        }
    }
}

fun openImagePreview(open: (String) -> Unit, url: String?) {
    val clean = url?.trim().orEmpty()
    if (clean.isNotEmpty()) open(clean)
}

@Composable
fun FullscreenImageViewer(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolved = remember(url) { previewImageUrl(url) }
    val bitmap = rememberUrlImageBitmap(resolved, PreviewMaxPx)
    var scale by remember(resolved) { mutableFloatStateOf(1f) }
    var offset by remember(resolved) { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var hintGen by remember { mutableIntStateOf(0) }
    val appear = remember { Animatable(0f) }
    val dismiss = rememberUpdatedState(onDismiss)
    val scaleState = rememberUpdatedState(scale)
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) {
        onDispose { dismissJob?.cancel() }
    }
    LaunchedEffect(resolved) {
        appear.snapTo(0f)
        appear.animateTo(1f, tween(180))
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value }
                .background(Color.Black),
        ) {
            val view = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val image = bitmap?.let { Size(it.width.toFloat(), it.height.toFloat()) } ?: Size.Zero
            val viewState = rememberUpdatedState(view)
            val imageState = rememberUpdatedState(image)
            val offsetState = rememberUpdatedState(offset)
            val onQuietTap = rememberUpdatedState {
                val now = System.currentTimeMillis()
                if (now - lastTapAt < 260L) {
                    dismissJob?.cancel()
                    lastTapAt = 0L
                    if (scaleState.value > 1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = PreviewDoubleScale
                        offset = Offset.Zero
                    }
                } else {
                    lastTapAt = now
                    dismissJob?.cancel()
                    dismissJob = scope.launch {
                        delay(260)
                        dismiss.value.invoke()
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val origin = down.position
                            var pastSlop = false
                            var pinching = false
                            var lastCentroid = down.position
                            var lastSpan = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                when {
                                    pressed.size >= 2 -> {
                                        pinching = true
                                        pastSlop = true
                                        dismissJob?.cancel()
                                        val a = pressed[0].position
                                        val b = pressed[1].position
                                        val span = hypot(
                                            (a.x - b.x).toDouble(),
                                            (a.y - b.y).toDouble(),
                                        ).toFloat().coerceAtLeast(1f)
                                        val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                                        if (lastSpan > 0f) {
                                            val next = (scaleState.value * (span / lastSpan))
                                                .coerceIn(1f, PreviewMaxScale)
                                            val raw = if (next <= 1.01f) {
                                                Offset.Zero
                                            } else {
                                                offsetState.value + (centroid - lastCentroid)
                                            }
                                            scale = next
                                            offset = clampImageOffset(
                                                raw,
                                                next,
                                                viewState.value,
                                                imageState.value,
                                            )
                                        }
                                        lastSpan = span
                                        lastCentroid = centroid
                                        event.changes.forEach { if (it.pressed) it.consume() }
                                    }
                                    pressed.size == 1 -> {
                                        val change = pressed[0]
                                        val delta = change.position - lastCentroid
                                        if (!pinching && (change.position - origin).getDistance() > slop) {
                                            pastSlop = true
                                            dismissJob?.cancel()
                                        }
                                        if (pastSlop && scaleState.value > 1.01f && !pinching) {
                                            val next = scaleState.value
                                            offset = clampImageOffset(
                                                offsetState.value + delta,
                                                next,
                                                viewState.value,
                                                imageState.value,
                                            )
                                            change.consume()
                                        }
                                        lastCentroid = change.position
                                        lastSpan = 0f
                                    }
                                    else -> {
                                        if (!pastSlop && !pinching) onQuietTap.value.invoke()
                                        break
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val frame = bitmap
                if (frame != null) {
                    Image(
                        bitmap = frame,
                        contentDescription = t("图片预览"),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                                transformOrigin = TransformOrigin.Center
                            },
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            val message = hint
            if (message != null) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(color = Color.White, fontSize = 13.sp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 84.dp, start = 24.dp, end = 24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 24.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(enabled = !saving) {
                        if (saving) return@clickable
                        saving = true
                        hint = null
                        scope.launch {
                            val saved = savePreviewImage(context, resolved)
                            saving = false
                            val gen = hintGen + 1
                            hintGen = gen
                            hint = if (saved.isSuccess) t("已保存到相册") else t("保存失败")
                            delay(1600)
                            if (hintGen == gen) hint = null
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Icon(
                        imageVector = ZIcons.GetApp,
                        contentDescription = t("下载图片"),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

private fun previewImageUrl(url: String): String =
    NcmLibraryParse.ncmHttpsImage(url, "1080y1080") ?: url.trim()

private fun clampImageOffset(offset: Offset, scale: Float, view: Size, image: Size): Offset {
    if (scale <= 1.01f || view.width <= 1f || view.height <= 1f || image.width <= 1f || image.height <= 1f) {
        return Offset.Zero
    }
    val fit = min(view.width / image.width, view.height / image.height)
    val shownW = image.width * fit * scale
    val shownH = image.height * fit * scale
    val maxX = ((shownW - view.width) / 2f).coerceAtLeast(0f)
    val maxY = ((shownH - view.height) / 2f).coerceAtLeast(0f)
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
}

private suspend fun savePreviewImage(context: Context, url: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = UrlImageCache.loadBytes(context, url) ?: error("empty")
            val mime = sniffImageMime(bytes)
            val ext = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val name = "ZMusic_${System.currentTimeMillis()}.$ext"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZMusic")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: error("insert")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("write")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Unit
        }
    }

private fun sniffImageMime(bytes: ByteArray): String {
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte()
    ) {
        return "image/png"
    }
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
    if (bytes.size >= 6 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) {
        return "image/gif"
    }
    if (bytes.size >= 12 &&
        bytes[0] == 0x52.toByte() &&
        bytes[8] == 0x57.toByte() &&
        bytes[9] == 0x45.toByte() &&
        bytes[10] == 0x42.toByte() &&
        bytes[11] == 0x50.toByte()
    ) {
        return "image/webp"
    }
    return "image/jpeg"
}
