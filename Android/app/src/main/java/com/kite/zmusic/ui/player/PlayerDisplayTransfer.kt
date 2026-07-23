package com.kite.zmusic.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PlayerDisplayPrefsCodec
import com.kite.zmusic.data.lerpPlayerDisplayPrefs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TransferLabel = Color(0xFFFFFFFF)
private val TransferHint = Color(0xFFE8F0F8)
private val TransferAccent = Color(0xFF9AF0F0)
private val TransferIconTint = Color(0xFFD5DEE8)
private val TransferPanelShape = RoundedCornerShape(18.dp)
private val TransferRowShape = RoundedCornerShape(12.dp)
private val TransferTextShadow = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 10f)
private val TransferGlassBg = Color(0xFF03060A)
private val TransferGlassStyle = HazeStyle(
    backgroundColor = TransferGlassBg,
    tints = listOf(
        HazeTint(Color(0xFF020408).copy(alpha = 0.78f)),
        HazeTint(Color(0xFF060A12).copy(alpha = 0.60f)),
        HazeTint(Color.Black.copy(alpha = 0.42f)),
    ),
    blurRadius = 84.dp,
    noiseFactor = 0.20f,
    fallbackTint = HazeTint(Color(0xCC05080E)),
)
private val TransferRowBg = Color(0xF0141A24)
private val TransferCurve = CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f)

/** 设置层外部点击 / 返回时，优先关闭导入导出顶层。 */
class PlayerDisplayTransferDismissGate {
    @Volatile
    var handler: (() -> Boolean)? = null

    fun requestDismissTop(): Boolean = handler?.invoke() == true
}

private sealed class TransferPhase {
    data object Hidden : TransferPhase()
    data object Export : TransferPhase()
    data object ImportChooser : TransferPhase()
    data class ImportConfirm(val prefs: PlayerDisplayPrefs) : TransferPhase()
    data class ImportApplying(val prefs: PlayerDisplayPrefs) : TransferPhase()
}

class PlayerDisplayTransferHost internal constructor(
    val onImportClick: () -> Unit,
    val onExportClick: () -> Unit,
    val Overlay: @Composable () -> Unit,
)

/**
 * 在设置面板内 remember：标题行用 [PlayerDisplayTransferHeaderIcons]，
 * 面板最上层调用 [PlayerDisplayTransferHost.Overlay]。
 */
@Composable
fun rememberPlayerDisplayTransferHost(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    hazeState: HazeState,
    dismissGate: PlayerDisplayTransferDismissGate,
): PlayerDisplayTransferHost {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<TransferPhase>(TransferPhase.Hidden) }
    var visible by remember { mutableStateOf(false) }
    var scannerOpen by remember { mutableStateOf(false) }
    val panelT = remember { Animatable(0f) }
    val latestPhase = rememberUpdatedState(phase)
    val prefsSnapshot = rememberUpdatedState(prefs)
    val onPrefsChangeState = rememberUpdatedState(onPrefsChange)

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun openPhase(next: TransferPhase) {
        phase = next
        visible = true
    }

    fun requestCloseTransfer(): Boolean {
        val current = latestPhase.value
        if (current is TransferPhase.ImportApplying) return true
        if (current is TransferPhase.Hidden) return false
        visible = false
        return true
    }

    LaunchedEffect(visible) {
        if (visible) {
            panelT.snapTo(0f)
            panelT.animateTo(1f, tween(420, easing = TransferCurve))
        } else if (phase !is TransferPhase.Hidden) {
            panelT.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
            if (phase !is TransferPhase.ImportApplying) {
                phase = TransferPhase.Hidden
            }
        }
    }

    LaunchedEffect(Unit) {
        dismissGate.handler = {
            when (latestPhase.value) {
                is TransferPhase.Hidden -> false
                is TransferPhase.ImportApplying -> true
                else -> requestCloseTransfer()
            }
        }
    }
    DisposableEffect(dismissGate) {
        onDispose { dismissGate.handler = null }
    }

    BackHandler(enabled = phase !is TransferPhase.Hidden && !scannerOpen) {
        requestCloseTransfer()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                PlayerDisplayQr.decodeUri(context, uri)
            }
            if (text.isNullOrBlank()) {
                toast("未识别到二维码，请换一张更清晰的图片")
                return@launch
            }
            PlayerDisplayPrefsCodec.decode(text).fold(
                onSuccess = { imported ->
                    scannerOpen = false
                    openPhase(TransferPhase.ImportConfirm(imported))
                },
                onFailure = { e ->
                    toast(e.message?.takeIf { it.isNotBlank() } ?: "配置解析失败")
                },
            )
        }
    }

    fun openGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scannerOpen = true
        } else {
            toast("需要相机权限才能扫码")
        }
    }

    fun openCameraScanner() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            scannerOpen = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (scannerOpen) {
        Dialog(
            onDismissRequest = { scannerOpen = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            QrScannerFullscreen(
                onDetected = { raw ->
                    PlayerDisplayPrefsCodec.decode(raw).fold(
                        onSuccess = { imported ->
                            scannerOpen = false
                            openPhase(TransferPhase.ImportConfirm(imported))
                        },
                        onFailure = { e ->
                            toast(e.message?.takeIf { it.isNotBlank() } ?: "配置解析失败")
                        },
                    )
                },
                onOpenGallery = { openGallery() },
                onClose = { scannerOpen = false },
            )
        }
    }

    val overlay: @Composable () -> Unit = {
        if (phase !is TransferPhase.Hidden || panelT.value > 0.001f) {
            val t = panelT.value
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = t
                        scaleX = 0.92f + 0.08f * t
                        scaleY = 0.92f + 0.08f * t
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
            ) {
                when (val p = phase) {
                    TransferPhase.Hidden -> Unit
                    is TransferPhase.ImportApplying -> {
                        Dialog(
                            onDismissRequest = {},
                            properties = DialogProperties(
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false,
                                usePlatformDefaultWidth = false,
                                decorFitsSystemWindows = false,
                            ),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.28f))
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.45f)
                                        .fillMaxSize()
                                        .padding(top = 12.dp, bottom = 12.dp, end = 12.dp),
                                ) {
                                    TransferPanelChrome(hazeState = hazeState)
                                    ImportApplyingContent(
                                        from = prefsSnapshot.value,
                                        to = p.prefs,
                                        onPrefsChange = onPrefsChangeState.value,
                                        onDone = {
                                            phase = TransferPhase.Hidden
                                            visible = false
                                            scope.launch { panelT.snapTo(0f) }
                                            toast("导入完成")
                                        },
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        TransferPanelChrome(hazeState = hazeState)
                        when (p) {
                            TransferPhase.Export -> ExportPanelContent(
                                prefs = prefsSnapshot.value,
                                onCancel = { requestCloseTransfer() },
                                onSaved = { toast("已保存到相册") },
                                onSaveFailed = { toast(it) },
                            )
                            TransferPhase.ImportChooser -> ImportChooserContent(
                                onCamera = {
                                    visible = false
                                    phase = TransferPhase.Hidden
                                    scope.launch { panelT.snapTo(0f) }
                                    openCameraScanner()
                                },
                                onGallery = { openGallery() },
                                onCancel = { requestCloseTransfer() },
                            )
                            is TransferPhase.ImportConfirm -> ImportConfirmContent(
                                onYes = { phase = TransferPhase.ImportApplying(p.prefs) },
                                onNo = { requestCloseTransfer() },
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    return PlayerDisplayTransferHost(
        onImportClick = { openPhase(TransferPhase.ImportChooser) },
        onExportClick = { openPhase(TransferPhase.Export) },
        Overlay = overlay,
    )
}

@Composable
fun PlayerDisplayTransferHeaderIcons(
    host: PlayerDisplayTransferHost,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TransferIconButton(onClick = host.onImportClick) { TransferImportIcon() }
        TransferIconButton(onClick = host.onExportClick) { TransferExportIcon() }
    }
}

@Composable
private fun TransferPanelChrome(hazeState: HazeState) {
    Box(Modifier.fillMaxSize().clip(TransferPanelShape)) {
        Box(
            Modifier
                .matchParentSize()
                .hazeEffect(state = hazeState, style = TransferGlassStyle) {
                    blurRadius = 84.dp
                    noiseFactor = 0.20f
                    fallbackTint = HazeTint(Color(0xCC05080E))
                },
        )
        Box(Modifier.matchParentSize().background(Color(0x9905080E)))
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    ),
                    shape = TransferPanelShape,
                ),
        )
    }
}

@Composable
private fun ExportPanelContent(
    prefs: PlayerDisplayPrefs,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    onSaveFailed: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    val qrSizePx = with(density) { 200.dp.roundToPx() }

    LaunchedEffect(prefs) {
        qrBitmap = withContext(Dispatchers.Default) {
            runCatching {
                PlayerDisplayQr.encodeBitmap(PlayerDisplayPrefsCodec.encode(prefs), qrSizePx)
            }.getOrNull()
        }
        if (qrBitmap == null) onSaveFailed("生成二维码失败")
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TransferEyebrow("EXPORT")
        Spacer(Modifier.height(4.dp))
        TransferTitle("导出配置")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "扫描此码可导入全部横屏播放配置",
            style = TextStyle(
                color = TransferHint.copy(alpha = 0.62f),
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        val bmp = qrBitmap
        Box(
            Modifier
                .size(212.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "配置二维码",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(text = "生成中…", color = Color.Black.copy(alpha = 0.45f), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransferSecondaryButton(
                label = "取消",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            TransferPrimaryButton(
                label = if (saving) "保存中…" else "保存到相册",
                enabled = bmp != null && !saving,
                modifier = Modifier.weight(1f),
                onClick = {
                    val image = bmp ?: return@TransferPrimaryButton
                    saving = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            PlayerDisplayQr.saveToGallery(
                                context,
                                image,
                                "ZMusic_landscape_${System.currentTimeMillis()}.png",
                            )
                        }
                        saving = false
                        result.fold(
                            onSuccess = { onSaved() },
                            onFailure = { onSaveFailed(it.message ?: "保存失败") },
                        )
                    }
                },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ImportChooserContent(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TransferEyebrow("IMPORT")
        Spacer(Modifier.height(4.dp))
        TransferTitle("导入配置")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "选择相机扫码或从相册选取二维码图片",
            style = TextStyle(
                color = TransferHint.copy(alpha = 0.62f),
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        TransferPrimaryButton(label = "使用相机扫描", onClick = onCamera)
        Spacer(Modifier.height(8.dp))
        TransferPrimaryButton(label = "从相册选择", onClick = onGallery)
        Spacer(Modifier.height(8.dp))
        TransferSecondaryButton(label = "取消", onClick = onCancel)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ImportConfirmContent(onYes: () -> Unit, onNo: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TransferEyebrow("IMPORT")
        Spacer(Modifier.height(4.dp))
        TransferTitle("已获取配置")
        Spacer(Modifier.height(12.dp))
        Text(
            text = "是否采用并覆盖当前横屏播放页全部配置？\n此操作不可撤销。",
            style = TextStyle(
                color = TransferHint.copy(alpha = 0.72f),
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        TransferPrimaryButton(label = "是，立即采用", onClick = onYes)
        Spacer(Modifier.height(8.dp))
        TransferSecondaryButton(label = "否", onClick = onNo)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ImportApplyingContent(
    from: PlayerDisplayPrefs,
    to: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onDone: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val onPrefsChangeState = rememberUpdatedState(onPrefsChange)
    val onDoneState = rememberUpdatedState(onDone)
    val fromFrozen = remember { from }
    val toFrozen = remember { to.sanitized() }

    BackHandler(enabled = true) { /* 导入中禁止返回 */ }

    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 780, easing = TransferCurve),
        ) {
            onPrefsChangeState.value(lerpPlayerDisplayPrefs(fromFrozen, toFrozen, value))
        }
        onPrefsChangeState.value(toFrozen)
        delay(180)
        onDoneState.value()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TransferTitle("正在导入")
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth(0.72f)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(TransferAccent.copy(alpha = 0.85f)),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "配置动画覆盖中，请稍候…",
            style = TextStyle(
                color = TransferHint.copy(alpha = 0.62f),
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun QrScannerFullscreen(
    onDetected: (String) -> Unit,
    onOpenGallery: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val detected = remember { AtomicBoolean(false) }
    val onDetectedState = rememberUpdatedState(onDetected)
    val scanAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scanAlpha.animateTo(1f, tween(380, easing = TransferCurve))
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
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val mainExecutor = ContextCompat.getMainExecutor(ctx)
                val analysisExecutor = Executors.newSingleThreadExecutor()
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            if (detected.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            try {
                                val width = imageProxy.width
                                val height = imageProxy.height
                                val yPlane = imageProxy.planes[0]
                                val rowStride = yPlane.rowStride
                                val yBuffer = yPlane.buffer
                                val data = if (rowStride == width) {
                                    ByteArray(yBuffer.remaining()).also { yBuffer.get(it) }
                                } else {
                                    ByteArray(width * height).also { out ->
                                        var offset = 0
                                        for (row in 0 until height) {
                                            yBuffer.position(row * rowStride)
                                            yBuffer.get(out, offset, width)
                                            offset += width
                                        }
                                    }
                                }
                                val source = PlanarYUVLuminanceSource(
                                    data,
                                    width,
                                    height,
                                    0,
                                    0,
                                    width,
                                    height,
                                    false,
                                )
                                val binary = BinaryBitmap(HybridBinarizer(source))
                                val result = runCatching {
                                    MultiFormatReader().decode(
                                        binary,
                                        mapOf(
                                            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                            DecodeHintType.TRY_HARDER to true,
                                        ),
                                    )
                                }.getOrNull()
                                if (result != null && detected.compareAndSet(false, true)) {
                                    onDetectedState.value(result.text)
                                }
                            } catch (_: Throwable) {
                            } finally {
                                imageProxy.close()
                            }
                        }
                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    },
                    mainExecutor,
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
            val c = TransferAccent.copy(alpha = 0.92f)
            val sw = 4f
            drawLine(c, Offset(left, top), Offset(left + len, top), sw, StrokeCap.Round)
            drawLine(c, Offset(left, top), Offset(left, top + len), sw, StrokeCap.Round)
            drawLine(c, Offset(left + side, top), Offset(left + side - len, top), sw, StrokeCap.Round)
            drawLine(c, Offset(left + side, top), Offset(left + side, top + len), sw, StrokeCap.Round)
            drawLine(c, Offset(left, top + side), Offset(left + len, top + side), sw, StrokeCap.Round)
            drawLine(c, Offset(left, top + side), Offset(left, top + side - len), sw, StrokeCap.Round)
            drawLine(c, Offset(left + side, top + side), Offset(left + side - len, top + side), sw, StrokeCap.Round)
            drawLine(c, Offset(left + side, top + side), Offset(left + side, top + side - len), sw, StrokeCap.Round)
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "扫描配置二维码",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "将二维码置于框内即可自动识别",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
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

@Composable
private fun TransferEyebrow(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = TransferAccent.copy(alpha = 0.75f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            shadow = TransferTextShadow,
        ),
    )
}

@Composable
private fun TransferTitle(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = TransferLabel,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            letterSpacing = 0.3.sp,
            shadow = TransferTextShadow,
        ),
    )
}

@Composable
private fun TransferPrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(TransferRowShape)
            .background(
                if (enabled) TransferAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
            )
            .border(
                width = 1.dp,
                color = if (enabled) {
                    TransferAccent.copy(alpha = 0.35f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                shape = TransferRowShape,
            )
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) TransferLabel else TransferHint.copy(alpha = 0.35f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun TransferSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(TransferRowShape)
            .background(TransferRowBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = TransferHint.copy(alpha = 0.78f), fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun TransferIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center, content = { content() })
    }
}

@Composable
private fun TransferExportIcon() {
    Canvas(Modifier.fillMaxSize()) {
        val sw = size.minDimension * 0.11f
        val pad = size.minDimension * 0.12f
        val boxTop = size.height * 0.42f
        drawLine(TransferIconTint, Offset(pad, boxTop), Offset(pad, size.height - pad), sw, StrokeCap.Round)
        drawLine(
            TransferIconTint,
            Offset(pad, size.height - pad),
            Offset(size.width - pad, size.height - pad),
            sw,
            StrokeCap.Round,
        )
        drawLine(
            TransferIconTint,
            Offset(size.width - pad, boxTop),
            Offset(size.width - pad, size.height - pad),
            sw,
            StrokeCap.Round,
        )
        val cx = size.width / 2f
        val arrowTop = pad * 0.6f
        val arrowBottom = size.height * 0.58f
        drawLine(TransferIconTint, Offset(cx, arrowBottom), Offset(cx, arrowTop), sw, StrokeCap.Round)
        drawLine(
            TransferIconTint,
            Offset(cx, arrowTop),
            Offset(cx - size.width * 0.18f, arrowTop + size.height * 0.18f),
            sw,
            StrokeCap.Round,
        )
        drawLine(
            TransferIconTint,
            Offset(cx, arrowTop),
            Offset(cx + size.width * 0.18f, arrowTop + size.height * 0.18f),
            sw,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun TransferImportIcon() {
    Canvas(Modifier.fillMaxSize()) {
        val sw = size.minDimension * 0.11f
        val pad = size.minDimension * 0.12f
        val boxTop = size.height * 0.42f
        drawLine(TransferIconTint, Offset(pad, boxTop), Offset(pad, size.height - pad), sw, StrokeCap.Round)
        drawLine(
            TransferIconTint,
            Offset(pad, size.height - pad),
            Offset(size.width - pad, size.height - pad),
            sw,
            StrokeCap.Round,
        )
        drawLine(
            TransferIconTint,
            Offset(size.width - pad, boxTop),
            Offset(size.width - pad, size.height - pad),
            sw,
            StrokeCap.Round,
        )
        val cx = size.width / 2f
        val arrowTop = pad * 0.6f
        val arrowBottom = size.height * 0.58f
        drawLine(TransferIconTint, Offset(cx, arrowTop), Offset(cx, arrowBottom), sw, StrokeCap.Round)
        drawLine(
            TransferIconTint,
            Offset(cx, arrowBottom),
            Offset(cx - size.width * 0.18f, arrowBottom - size.height * 0.18f),
            sw,
            StrokeCap.Round,
        )
        drawLine(
            TransferIconTint,
            Offset(cx, arrowBottom),
            Offset(cx + size.width * 0.18f, arrowBottom - size.height * 0.18f),
            sw,
            StrokeCap.Round,
        )
    }
}
