package com.kite.zmusic.ui.orientation

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * 根容器：横屏隐藏状态栏；屏幕方向变化时全屏蒙版过渡，避免布局跳变刺眼。
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

    LaunchedEffect(configuration.orientation) {
        if (!orientationInitialized) {
            orientationInitialized = true
            return@LaunchedEffect
        }
        orientationSwitchOverlay = true
        delay(420)
        orientationSwitchOverlay = false
    }

    CompositionLocalProvider(LocalSessionRotationLock provides rotationLock) {
        Box(modifier = modifier.fillMaxSize()) {
            content()

            AnimatedVisibility(
                visible = orientationSwitchOverlay,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10_000f),
                enter = fadeIn(animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(220)),
            ) {
                OrientationSwitchMask()
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
        SciFiBackdrop(Modifier.fillMaxSize())
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
