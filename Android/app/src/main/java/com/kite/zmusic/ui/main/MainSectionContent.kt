package com.kite.zmusic.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.NetworkPhase
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.features.FeaturesScreen
import com.kite.zmusic.ui.home.HomeScreen
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.library.LibraryScreen
import com.kite.zmusic.ui.offline.OfflineEmptyPage

@Composable
fun MainSectionContent(
    destination: MainDestination,
    isLandscape: Boolean,
    sessionRepository: SessionRepository,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit = { _, _, _, _ -> },
    onOpenOverlay: (MainOverlay) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onStartFm: () -> Unit = {},
    onStartIntelligence: () -> Unit = {},
    onPlaySong: (Long) -> Unit = {},
    onHint: (String) -> Unit = {},
    contentBottomInset: Dp = 0.dp,
    onUserSpaceProgress: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val net by app.networkMode.state.collectAsStateWithLifecycle()
    val pageMod = modifier
        .fillMaxSize()
        .statusBarsPadding()
    val offline = net.phase == NetworkPhase.Offline
    if (offline && destination != MainDestination.Features) {
        OfflineSectionPage(
            destination = destination,
            isLandscape = isLandscape,
            contentBottomInset = contentBottomInset,
            onOpenOverlay = onOpenOverlay,
            modifier = pageMod,
        )
        return
    }
    when (destination) {
        MainDestination.Home -> {
            HomeScreen(
                sessionRepository = sessionRepository,
                contentBottomInset = contentBottomInset,
                onOpenOverlay = onOpenOverlay,
                onPlayTracks = onPlayTracks,
                onPlaySong = onPlaySong,
                onHint = onHint,
                modifier = pageMod,
            )
        }
        MainDestination.Features -> {
            FeaturesScreen(
                contentBottomInset = contentBottomInset,
                onOpenOverlay = onOpenOverlay,
                onStartFm = onStartFm,
                onStartIntelligence = onStartIntelligence,
                offline = offline,
                modifier = pageMod,
            )
        }
        MainDestination.Profile -> {
            LibraryScreen(
                sessionRepository = sessionRepository,
                isLandscape = isLandscape,
                onOpenOverlay = onOpenOverlay,
                contentBottomInset = contentBottomInset,
                onUserSpaceProgress = onUserSpaceProgress,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OfflineSectionPage(
    destination: MainDestination,
    isLandscape: Boolean,
    contentBottomInset: Dp,
    onOpenOverlay: (MainOverlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val padH = mainContentPadH(isLandscape)
    Column(modifier.fillMaxSize()) {
        if (!isLandscape) {
            val headerMod = Modifier
                .padding(horizontal = padH)
                .padding(top = MainContentPadTop)
            when (destination) {
                MainDestination.Home -> MainPageHeader(
                    title = "ZMusic",
                    landscape = false,
                    showLogo = true,
                    modifier = headerMod,
                    trailing = {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onOpenOverlay(MainOverlay.Settings) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = ZIcons.Settings,
                                contentDescription = "设置",
                                tint = MainPalette.Ink,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                )
                MainDestination.Features -> MainPageHeader(
                    title = "功能",
                    landscape = false,
                    modifier = headerMod,
                )
                MainDestination.Profile -> MainPageHeader(
                    title = "个人",
                    landscape = false,
                    modifier = headerMod,
                )
            }
        }
        OfflineEmptyPage(
            modifier = Modifier.weight(1f),
            contentBottomInset = contentBottomInset,
            onAction = { onOpenOverlay(MainOverlay.CachedSongs) },
        )
    }
}
