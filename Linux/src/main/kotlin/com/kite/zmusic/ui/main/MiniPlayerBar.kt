package com.kite.zmusic.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.GlassStyle
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.chrome.chromeGlassSurface
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.theme.MainPalette

@Composable
fun MiniPlayerBar(
    state: PlaybackUiState,
    glass: GlassStyle,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
                    .chromeGlassSurface(RoundedCornerShape(LandscapeMiniBarRadius), glass)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            track.coverUrl,
            Modifier
                .size(LandscapeMiniCover)
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExpand,
                ),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExpand,
                ),
        ) {
            AnimatedContent(
                targetState = Triple(track.id, track.name, track.artists),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "miniMeta",
            ) { (_, name, artists) ->
                Column {
                    Text(
                        name,
                        style = TextStyle(
                            color = MainPalette.MiniPlayerTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        artists,
                        style = TextStyle(color = MainPalette.MiniPlayerSubtitle, fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MainPalette.Accent,
                trackColor = MainPalette.TrackOff,
            )
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.playWhenReady) "暂停" else "播放",
                tint = MainPalette.MiniPlayerIcon,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
