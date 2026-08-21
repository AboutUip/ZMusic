package com.kite.zmusic.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.playback.PlaybackViewModel
import com.kite.zmusic.playback.PlaybackViewModelFactory

private enum class MainGate {
    Checking,
    Ready,
    NeedLogin,
}

/**
 * 主界面：进入后校验会话；未登录则跳转登录；就绪后进入浅色主壳。
 */
@Composable
fun MainPlaceholderScreen(
    sessionRepository: SessionRepository,
    onRequireLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by sessionRepository.session.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as ZMusicApplication
    var gate by remember {
        mutableStateOf(
            if (app.sessionWarmup.isValidFor(sessionRepository.session.value?.cookie)) {
                MainGate.Ready
            } else {
                MainGate.Checking
            },
        )
    }
    val playbackFactory = remember(app.playbackBridge) {
        PlaybackViewModelFactory(app.playbackBridge)
    }
    val playback: PlaybackViewModel = viewModel(factory = playbackFactory)

    fun wipeLocalSession() {
        app.playbackBridge.stopForLogout()
        app.likedPlaylistRepository.clear()
        app.homeFeedRepository.clear()
        app.playlistTracksCache.clear()
        app.albumTracksCache.clear()
        app.searchHistoryRepository.clear()
        app.playlistCollectionRepository.clear()
        app.libraryHomeRepository.clear()
        app.sessionWarmup.invalidate()
    }

    LaunchedEffect(session?.cookie) {
        val s = session
        if (s == null) {
            wipeLocalSession()
            gate = MainGate.NeedLogin
            onRequireLogin()
            return@LaunchedEffect
        }
        if (gate != MainGate.Ready) {
            gate = MainGate.Checking
        }
        val stillValid = app.sessionWarmup.validate()
        if (!stillValid) {
            wipeLocalSession()
            sessionRepository.clear()
            gate = MainGate.NeedLogin
            onRequireLogin()
        } else {
            gate = MainGate.Ready
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MainPalette.Page)) {
        when (gate) {
            MainGate.Checking, MainGate.NeedLogin -> {
                MainLightSystemBars()
                Box(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    ColumnishGate()
                }
            }
            MainGate.Ready -> {
                LaunchedEffect(Unit) {
                    app.playbackBridge.hydrateForUi()
                    if (app.networkMode.state.value.online) {
                        app.likedPlaylistRepository.prefetchOnAppReady()
                        app.homeFeedRepository.prefetchOnAppReady()
                        app.libraryHomeRepository.prefetchOnAppReady()
                    }
                }
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(420)),
                    exit = fadeOut(),
                ) {
                    MainShell(
                        sessionRepository = sessionRepository,
                        playback = playback,
                        onLogout = {
                            wipeLocalSession()
                            sessionRepository.clear()
                            onRequireLogin()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnishGate() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_vinyl_z),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        CircularProgressIndicator(
            modifier = Modifier
                .padding(top = 22.dp)
                .size(22.dp),
            color = MainPalette.Accent,
            strokeWidth = 2.dp,
        )
        Text(
            text = "正在进入",
            modifier = Modifier.padding(top = 14.dp),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
