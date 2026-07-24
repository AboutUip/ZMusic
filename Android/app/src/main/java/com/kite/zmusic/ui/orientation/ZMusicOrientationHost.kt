package com.kite.zmusic.ui.orientation

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kite.zmusic.ui.scifi.SciFiBackdrop
import kotlinx.coroutines.delay

private val MaskCyan = Color(0xFF00FFD1)
private val MaskDim = Color(0xFF8FA8B8)

/** 方向落地后蒙版再停留，遮挡横屏重建跳变 */
private const val OrientationMaskHoldMs = 480L

/**
 * 根容器：横屏隐藏状态栏；方向切换用全屏蒙版。
 * - 立刻改方向；蒙版尽量与点击同帧出现（竖→横会 preempt）
 * - 入场无淡入延迟，仅出场淡出
 */
@Composable
fun ZMusicOrientationHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rotationLock = rememberSessionRotationLock()
    val systemAutoRotate = rememberSystemAutoRotateEnabled()
    // 用户取消系统旋转锁定（false→true）后：应用内回到「自动」，清掉确定旋转残留的锁定
    var prevSystemAutoRotate by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(systemAutoRotate) {
        val was = prevSystemAutoRotate
        prevSystemAutoRotate = systemAutoRotate
        if (was == false && systemAutoRotate) {
            rotationLock.setLocked(activity, false)
        }
    }

    DisposableEffect(activity, isLandscape) {
        val act = activity
        if (act != null) {
            val window = act.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isLandscape) {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                val c = WindowCompat.getInsetsController(window, window.decorView)
                c.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    var orientationSwitchOverlay by remember { mutableStateOf(false) }
    var orientationInitialized by remember { mutableStateOf(false) }
    var maskGeneration by remember { mutableIntStateOf(0) }
    val orientKey = configuration.orientation
    val maskPinned = OrientationMaskGate.pinned
    val showMask = orientationSwitchOverlay || maskPinned

    // 方向落地后起算停留，再收蒙版（并松开竖→横预钉）
    LaunchedEffect(orientKey) {
        if (!orientationInitialized) {
            orientationInitialized = true
            return@LaunchedEffect
        }
        orientationSwitchOverlay = true
        maskGeneration += 1
        delay(OrientationMaskHoldMs)
        orientationSwitchOverlay = false
        OrientationMaskGate.unpin()
    }

    // 预钉时也推进 generation，保证立方体从点击帧开始播
    LaunchedEffect(maskPinned) {
        if (maskPinned) maskGeneration += 1
    }

    val maskAlpha by animateFloatAsState(
        targetValue = if (showMask) 1f else 0f,
        animationSpec = tween(if (showMask) 0 else 200),
        label = "orient_mask_alpha",
    )

    CompositionLocalProvider(LocalSessionRotationLock provides rotationLock) {
        Box(modifier = modifier.fillMaxSize()) {
            content()

            if (maskAlpha > 0.001f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(10_000f)
                        .graphicsLayer { alpha = maskAlpha },
                ) {
                    key(maskGeneration) {
                        OrientationSwitchMask()
                    }
                }
            }
        }
    }
}

@Composable
private fun OrientationSwitchMask() {
    val pulse = rememberInfiniteTransition(label = "orient_mask")
    val a by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
    ) {
        SciFiBackdrop(modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f)),
        )
        ColumnContent(a)
    }
}

@Composable
private fun ColumnContent(pulseAlpha: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CubeAssemblyIndicator()
            Spacer(Modifier.size(22.dp))
            Text(
                text = "LAYOUT // RECONFIG",
                style = TextStyle(
                    color = MaskCyan.copy(alpha = pulseAlpha),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "正在适配屏幕方向…",
                style = TextStyle(
                    color = MaskDim.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
