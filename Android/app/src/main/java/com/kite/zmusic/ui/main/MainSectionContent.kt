package com.kite.zmusic.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.library.LibraryScreen

private val Cyan = Color(0xFF00FFD1)
private val Dim = Color(0xFF8FA8B8)
private val Green = Color(0xFF39FF9C)

/**
 * 主导航各模块内容；「我的」为完整资料与歌单页，其余仍为占位。
 */
@Composable
fun MainSectionContent(
    destination: MainDestination,
    displayLabel: String?,
    isLandscape: Boolean,
    sessionRepository: SessionRepository,
    playbackState: PlaybackUiState,
    pendingLibraryOpen: Pair<Long, String>?,
    onConsumePendingLibraryOpen: () -> Unit,
    onLibraryPlaylistFullScreenChange: (Boolean) -> Unit = {},
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    when (destination) {
        MainDestination.Library -> {
            LibraryScreen(
                sessionRepository = sessionRepository,
                isLandscape = isLandscape,
                onPlaylistFullScreenChange = onLibraryPlaylistFullScreenChange,
                playbackState = playbackState,
                pendingLibraryOpen = pendingLibraryOpen,
                onConsumePendingLibraryOpen = onConsumePendingLibraryOpen,
                onPlayTracks = onPlayTracks,
                modifier = modifier,
            )
        }
        else -> MainSectionPlaceholder(
            destination = destination,
            displayLabel = displayLabel,
            isLandscape = isLandscape,
            modifier = modifier,
        )
    }
}

@Composable
private fun MainSectionPlaceholder(
    destination: MainDestination,
    displayLabel: String?,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isLandscape) 28.dp else 32.dp,
                vertical = if (isLandscape) 20.dp else 24.dp,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${destination.shortLabel} // ${destination.titleZh}",
            style = TextStyle(
                color = Cyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (isLandscape) 14.sp else 15.sp,
                letterSpacing = 3.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = destination.blurb,
            style = TextStyle(
                color = Dim.copy(alpha = 0.88f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                lineHeight = 17.sp,
            ),
            textAlign = TextAlign.Center,
        )
        if (!displayLabel.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "SESSION · $displayLabel",
                style = TextStyle(
                    color = Green.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                ),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "模块占位 · 接入数据与播放器后替换",
            style = TextStyle(
                color = Dim.copy(alpha = 0.55f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            ),
            textAlign = TextAlign.Center,
        )
    }
}
