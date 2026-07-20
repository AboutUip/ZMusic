package com.kite.zmusic.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.playback.PlaybackViewModel
import com.kite.zmusic.playback.PlaybackViewModelFactory
import com.kite.zmusic.ui.scifi.SciFiBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MainGate {
    Checking,
    Ready,
    NeedLogin,
}

/**
 * 主界面：进入后校验会话；未登录则交由导航跳转登录页；就绪后进入带方向适配 Dock 的主壳。
 */
@Composable
fun MainPlaceholderScreen(
    sessionRepository: SessionRepository,
    onRequireLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by sessionRepository.session.collectAsStateWithLifecycle()
    var gate by remember { mutableStateOf(MainGate.Checking) }
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val playbackFactory = remember(app.playbackBridge) {
        PlaybackViewModelFactory(app.playbackBridge)
    }
    val playback: PlaybackViewModel = viewModel(factory = playbackFactory)

    LaunchedEffect(session) {
        val s = session
        if (s == null) {
            app.playbackBridge.stopForLogout()
            app.likedPlaylistRepository.clear()
            app.playlistTracksCache.clear()
            gate = MainGate.NeedLogin
            onRequireLogin()
            return@LaunchedEffect
        }
        gate = MainGate.Checking
        val stillValid = if (s.isGuest) {
            true
        } else {
            try {
                val json = withContext(Dispatchers.IO) {
                    NcmAuthClient().loginStatus(s.cookie)
                }
                when {
                    NcmJson.isLoggedInStatus(json) -> true
                    NcmJson.apiCode(json) == 200 -> false
                    else -> true
                }
            } catch (_: Exception) {
                true
            }
        }
        if (!stillValid) {
            app.playbackBridge.stopForLogout()
            app.likedPlaylistRepository.clear()
            app.playlistTracksCache.clear()
            sessionRepository.clear()
            gate = MainGate.NeedLogin
            onRequireLogin()
        } else {
            gate = MainGate.Ready
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (gate) {
            MainGate.Checking, MainGate.NeedLogin -> {
                SciFiBackdrop(Modifier.fillMaxSize())
                GateScanOverlay(
                    subtitle = if (gate == MainGate.Checking) {
                        "VERIFY SESSION // 校验会话"
                    } else {
                        "AUTH REQUIRED // 需要登录"
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MainGate.Ready -> {
                LaunchedEffect(Unit) {
                    app.playbackBridge.hydrateForUi()
                    app.likedPlaylistRepository.prefetchOnAppReady()
                }
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(520)),
                    exit = fadeOut(),
                ) {
                    MainShell(
                        displayLabel = session?.displayLabel,
                        sessionRepository = sessionRepository,
                        playback = playback,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GateScanOverlay(
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val scan = rememberInfiniteTransition(label = "gate")
    val scanY by scan.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanY",
    )
    Box(modifier.systemBarsPadding()) {
        Canvas(Modifier.fillMaxSize()) {
            val y = scanY * size.height
            drawLine(
                color = Color(0xFF00FFD1).copy(alpha = 0.14f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }
        Text(
            text = subtitle,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            style = TextStyle(
                color = Color(0xFF8FA8B8).copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
