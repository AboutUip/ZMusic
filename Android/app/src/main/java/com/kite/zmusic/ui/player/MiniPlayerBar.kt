package com.kite.zmusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.mainLiquidGlass
import com.kyant.backdrop.Backdrop

@Composable
fun MiniPlayerBar(
    track: TrackRow,
    isPlaying: Boolean,
    buffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    onOpenFull: () -> Unit,
    onTogglePlay: () -> Unit,
    backdrop: Backdrop,
    loadPending: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val displayPos = rememberSeekDisplayPositionMs(
        trackId = track.id,
        positionMs = positionMs,
        loadPending = loadPending,
    )
    val progress = if (durationMs > 0) {
        (displayPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shape = RoundedCornerShape(24.dp)
    val noopDrag = rememberDraggableState { }

    Row(
        modifier
            .draggable(
                state = noopDrag,
                orientation = Orientation.Horizontal,
            )
            .mainLiquidGlass(backdrop, shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF0F0F2))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenFull,
                ),
        ) {
            Crossfade(
                targetState = track.id,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                label = "miniCover",
            ) {
                UrlImage(
                    url = track.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenFull,
                ),
        ) {
            Crossfade(
                targetState = track.id,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "miniMeta",
            ) {
                Column {
                    Text(
                        text = track.name,
                        style = TextStyle(
                            color = MainPalette.Ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artists,
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 11.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (buffering) 0f else progress },
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MainPalette.Accent,
                trackColor = Color(0x14000000),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlay,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) ZIcons.Pause else ZIcons.Play,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
