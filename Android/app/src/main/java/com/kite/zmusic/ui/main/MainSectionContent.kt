package com.kite.zmusic.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.catalog.MainOverlay
import com.kite.zmusic.ui.features.FeaturesScreen
import com.kite.zmusic.ui.home.HomeScreen
import com.kite.zmusic.ui.library.LibraryScreen

@Composable
fun MainSectionContent(
    destination: MainDestination,
    isLandscape: Boolean,
    sessionRepository: SessionRepository,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit = { _, _, _, _ -> },
    onOpenOverlay: (MainOverlay) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onStartFm: () -> Unit = {},
    onPlaySong: (Long) -> Unit = {},
    onHint: (String) -> Unit = {},
    contentBottomInset: Dp = 0.dp,
    onUserSpaceProgress: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pageMod = modifier
        .fillMaxSize()
        .statusBarsPadding()
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
