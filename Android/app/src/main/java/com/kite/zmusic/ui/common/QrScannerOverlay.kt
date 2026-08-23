package com.kite.zmusic.ui.common

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.player.PlayerDisplayQr
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

private val ScanCurve = CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f)

@Composable
fun QrScannerOverlay(
    title: String,
    subtitle: String,
    onDetected: (String) -> Boolean,
    onOpenGallery: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = (activity as? LifecycleOwner)
    val detected = remember { AtomicBoolean(false) }
    val lastReject = remember { AtomicReference<String?>(null) }
    val lastRejectAt = remember { AtomicLong(0L) }
    val onDetectedState = rememberUpdatedState(onDetected)
    val scanAlpha = remember { Animatable(0f) }
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scanAlpha.animateTo(1f, tween(380, easing = ScanCurve))
    }
    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = scanAlpha.value }
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val analysisExecutor = Executors.newSingleThreadExecutor()
                val reader = MultiFormatReader().apply { setHints(PlayerDisplayQr.DecodeHints) }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val mainExecutor = ContextCompat.getMainExecutor(ctx)
                val owner = lifecycleOwner
                fun bindCamera() {
                    if (owner == null) {
                        previewView.post { feedback = "无法打开相机，请从相册选取" }
                        return
                    }
                    val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return
                    val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                    val preview = Preview.Builder()
                        .setTargetRotation(rotation)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(rotation)
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1280, 720),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                    ),
                                )
                                .build(),
                        )
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        if (detected.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        try {
                            val text = decodeProxy(imageProxy, reader)
                            if (text.isNullOrBlank()) return@setAnalyzer
                            val now = android.os.SystemClock.elapsedRealtime()
                            val sameReject = text == lastReject.get() &&
                                now - lastRejectAt.get() < 1_600L
                            if (sameReject || !detected.compareAndSet(false, true)) return@setAnalyzer
                            previewView.post {
                                val consumed = runCatching { onDetectedState.value(text) }.getOrDefault(false)
                                if (consumed) {
                                    lastReject.set(null)
                                } else {
                                    lastReject.set(text)
                                    lastRejectAt.set(android.os.SystemClock.elapsedRealtime())
                                    detected.set(false)
                                    feedback = "不是社区登录二维码"
                                }
                            }
                        } catch (_: Throwable) {
                            detected.set(false)
                        } finally {
                            imageProxy.close()
                        }
                    }
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            owner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }.onFailure {
                        previewView.post { feedback = "无法打开相机，请从相册选取" }
                    }
                }
                previewView.addOnAttachStateChangeListener(
                    object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {
                            cameraProviderFuture.addListener({ bindCamera() }, mainExecutor)
                        }

                        override fun onViewDetachedFromWindow(v: android.view.View) {
                            runCatching {
                                cameraProviderFuture.get().unbindAll()
                            }
                        }
                    },
                )
                previewView.tag = analysisExecutor
                previewView
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view ->
                (view.tag as? java.util.concurrent.ExecutorService)?.shutdown()
                runCatching {
                    ProcessCameraProvider.getInstance(context).get().unbindAll()
                }
            },
        )

        Canvas(Modifier.fillMaxSize()) {
            val side = min(size.width, size.height) * 0.42f
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            val len = side * 0.18f
            val c = MainPalette.Accent.copy(alpha = 0.92f)
            val sw = 4f
            drawLine(c, Offset(left, top), Offset(left + len, top), sw, StrokeCap.Round)
            drawLine(c, Offset(left, top), Offset(left, top + len), sw, StrokeCap.Round)
            drawLine(c, Offset(left + side, top), Offset(left + side - len, top), sw, StrokeCap.Round)
            drawLine(c, Offset(left + side, top), Offset(left + side, top + len), sw, StrokeCap.Round)
            drawLine(c, Offset(left, top + side), Offset(left + len, top + side), sw, StrokeCap.Round)
            drawLine(c, Offset(left, top + side), Offset(left, top + side - len), sw, StrokeCap.Round)
            drawLine(
                c,
                Offset(left + side, top + side),
                Offset(left + side - len, top + side),
                sw,
                StrokeCap.Round,
            )
            drawLine(
                c,
                Offset(left + side, top + side),
                Offset(left + side, top + side - len),
                sw,
                StrokeCap.Round,
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            feedback?.let { msg ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = msg,
                    color = MainPalette.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ScannerActionChip(label = "相册", onClick = onOpenGallery)
            ScannerActionChip(label = "关闭", onClick = onClose)
        }
    }
}

@Composable
private fun ScannerActionChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun decodeProxy(imageProxy: ImageProxy, reader: MultiFormatReader): String? {
    yuvText(imageProxy, reader)?.let { return it }
    return runCatching {
        val raw = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val upright = rotateBitmap(raw, rotation)
        try {
            PlayerDisplayQr.decodeBitmap(upright)
        } finally {
            if (upright !== raw && !upright.isRecycled) upright.recycle()
            if (!raw.isRecycled) raw.recycle()
        }
    }.getOrNull()
}

private fun yuvText(imageProxy: ImageProxy, reader: MultiFormatReader): String? = runCatching {
    val crop = imageProxy.cropRect
    val yPlane = imageProxy.planes[0]
    val data = extractY(yPlane, imageProxy.width, imageProxy.height) ?: return@runCatching null
    val source = PlanarYUVLuminanceSource(
        data,
        yPlane.rowStride.takeIf { yPlane.pixelStride == 1 } ?: imageProxy.width,
        imageProxy.height,
        crop.left,
        crop.top,
        crop.width(),
        crop.height(),
        false,
    )
    reader.reset()
    runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }
        .recoverCatching {
            reader.reset()
            reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source))).text
        }
        .getOrNull()
}.getOrNull()

private fun extractY(
    yPlane: ImageProxy.PlaneProxy,
    width: Int,
    height: Int,
): ByteArray? {
    val buffer = yPlane.buffer
    buffer.rewind()
    if (yPlane.pixelStride == 1) {
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        return data
    }
    val rowStride = yPlane.rowStride
    val pixelStride = yPlane.pixelStride
    val packed = ByteArray(width * height)
    val row = ByteArray(rowStride)
    var out = 0
    for (rowIndex in 0 until height) {
        buffer.position(rowIndex * rowStride)
        val read = min(rowStride, buffer.remaining())
        if (read <= 0) break
        buffer.get(row, 0, read)
        var col = 0
        while (col < width && col * pixelStride < read) {
            packed[out++] = row[col * pixelStride]
            col++
        }
    }
    return packed
}

private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    val deg = rotationDegrees % 360
    if (deg == 0) return bitmap
    val matrix = Matrix().apply { postRotate(deg.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
