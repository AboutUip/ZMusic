package com.kite.zmusic.ui.splash

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.scifi.SciFiBackdrop
import kotlinx.coroutines.delay
import kotlin.random.Random

private val CyanBright = Color(0xFF00FFD1)
private val TermGreen = Color(0xFF39FF9C)

private const val Brand = "ZMUSIC"

/**
 * 启动页：背景与扫描线全屏沉浸；终端行 `> ZMUSIC` 打字机 + 光标闪烁；正文避让状态栏/导航栏。
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var typedLen by remember { mutableIntStateOf(0) }

    val cursorBlink = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by cursorBlink.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )

    LaunchedEffect(Unit) {
        delay(120)
        repeat(Brand.length) { i ->
            delay(55L + Random.nextLong(45))
            typedLen = i + 1
        }
        delay(750)
        onFinished()
    }

    val scan = rememberInfiniteTransition(label = "scan")
    val scanY by scan.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanY",
    )

    Box(modifier = modifier.fillMaxSize()) {
        SciFiBackdrop(Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            val y = scanY * size.height
            drawLine(
                color = CyanBright.copy(alpha = 0.18f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        CyanBright.copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                    startY = y - 24f,
                    endY = y + 24f,
                ),
            )
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 40.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(
                    Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val sideStyle = TextStyle(
                        color = CyanBright.copy(alpha = 0.55f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                    )
                    Text(text = "BOOT // SEQUENCE", style = sideStyle)
                    Spacer(Modifier.height(8.dp))
                    Text(text = "ZMUSIC TERMINAL", style = sideStyle.copy(fontSize = 9.sp, letterSpacing = 1.sp))
                }
                Row(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val baseStyle = TextStyle(
                        color = TermGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        letterSpacing = 4.sp,
                    )
                    Text(text = "> ${Brand.take(typedLen)}", style = baseStyle)
                    Text(
                        text = if (typedLen < Brand.length) "█" else "_",
                        style = baseStyle,
                        modifier = Modifier.graphicsLayer { alpha = cursorAlpha },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val baseStyle = TextStyle(
                    color = TermGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                    letterSpacing = 4.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "> ${Brand.take(typedLen)}",
                        style = baseStyle,
                    )
                    Text(
                        text = if (typedLen < Brand.length) "█" else "_",
                        style = baseStyle,
                        modifier = Modifier.graphicsLayer { alpha = cursorAlpha },
                    )
                }
            }
        }
    }
}
