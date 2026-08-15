package com.kite.zmusic.ui.orientation

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kite.zmusic.R
import com.kite.zmusic.ui.main.MainLightSystemBars
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.notice.IslandNoticeRoot
import kotlinx.coroutines.delay

/** 方向落地后蒙版再停留，遮挡横屏重建跳变 */
private const val OrientationMaskHoldMs = 480L

/**
 * 根容器：横屏隐藏状态栏；方向切换用全屏蒙版。
 * - 立刻改方向；蒙版尽量与点击同帧出现（竖→横会 preempt）
 * - 入场无淡入延迟，仅出场淡出
 * - 样式与进入主界面的浅色黑胶一致
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

    LaunchedEffect(orientKey) {
        if (!orientationInitialized) {
            orientationInitialized = true
            if (OrientationMaskGate.pinned) {
                delay(OrientationMaskHoldMs)
                orientationSwitchOverlay = false
                OrientationMaskGate.unpin()
            }
            return@LaunchedEffect
        }
        orientationSwitchOverlay = true
        maskGeneration += 1
        delay(OrientationMaskHoldMs)
        orientationSwitchOverlay = false
        OrientationMaskGate.unpin()
    }

    LaunchedEffect(maskPinned) {
        if (!maskPinned) return@LaunchedEffect
        maskGeneration += 1
        delay(OrientationMaskHoldMs + 80L)
        if (OrientationMaskGate.pinned) {
            orientationSwitchOverlay = false
            OrientationMaskGate.unpin()
        }
    }

    val maskAlpha by animateFloatAsState(
        targetValue = if (showMask) 1f else 0f,
        animationSpec = tween(if (showMask) 0 else 220),
        label = "orient_mask_alpha",
    )

    CompositionLocalProvider(LocalSessionRotationLock provides rotationLock) {
        Box(modifier = modifier.fillMaxSize()) {
            IslandNoticeRoot(Modifier.fillMaxSize()) {
                content()
            }

            if (maskAlpha > 0.001f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(10_000f)
                        .alpha(maskAlpha),
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
    MainLightSystemBars()
    val spin = rememberInfiniteTransition(label = "orient_vinyl")
    val rot by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.snapTo(0f)
        appear.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MainPalette.Page),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = appear.value },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_vinyl_z),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .graphicsLayer { rotationZ = rot },
                contentScale = ContentScale.Crop,
            )
            Text(
                text = "正在适配方向",
                modifier = Modifier.padding(top = 18.dp),
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
