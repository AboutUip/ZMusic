package com.kite.zmusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImage
import kotlin.math.roundToInt

private val Cyan = Color(0xFF00FFD1)
private val Dim = Color(0xFF8FA8B8)

@Composable
fun MiniPlayerBar(
    track: TrackRow,
    isPlaying: Boolean,
    buffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    onOpenFull: () -> Unit,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    loadPending: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    val dismissReveal = (-dragX / 120f).coerceIn(0f, 1f)
    val haptic = LocalHapticFeedback.current
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

    Row(
        modifier
            .graphicsLayer { translationX = dragX }
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dx ->
                        dragX = (dragX + dx).coerceIn(-200f, 0f)
                    },
                    onDragEnd = {
                        if (dragX < -96f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClose()
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                )
            }
            .clip(RoundedCornerShape(14.dp))
            // 不要透明：避免透视底层页面内容造成视觉重叠
            .background(Color(0xFF0A0E16))
            .border(1.dp, Cyan.copy(alpha = 0.1f + 0.08f * dismissReveal), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF121A28))
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
                val url = track.coverUrl
                if (!url.isNullOrBlank()) {
                    UrlImage(
                        url = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
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
                            color = Color(0xFFE8E4DF).copy(alpha = 0.95f),
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            letterSpacing = 0.2.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artists,
                        style = TextStyle(
                            color = Dim.copy(alpha = 0.72f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 0.3.sp,
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
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Cyan.copy(alpha = 0.75f),
                trackColor = Color.White.copy(alpha = 0.08f),
            )

            if (dismissReveal > 0.05f) {
                Text(
                    text = "继续左滑清空",
                    style = TextStyle(
                        color = Cyan.copy(alpha = 0.35f + 0.4f * dismissReveal),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Text(
            text = if (isPlaying) "❚❚" else "▶",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlay,
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            style = TextStyle(
                color = Cyan.copy(alpha = 0.9f),
                fontSize = 14.sp,
            ),
        )
    }
}
