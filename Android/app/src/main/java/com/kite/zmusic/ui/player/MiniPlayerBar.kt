package com.kite.zmusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.plugin.PluginSurfaces
import com.kite.zmusic.plugin.PluginUiTarget
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.theme.TextTheme
import com.kite.zmusic.ui.main.mainLiquidGlass
import com.kite.zmusic.ui.plugin.pluginSurface
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.Flow

@Composable
fun MiniPlayerBar(
    track: TrackRow,
    isPlaying: Boolean,
    buffering: Boolean,
    durationMs: Long,
    positions: Flow<Long>,
    initialPositionMs: Long,
    onOpenFull: () -> Unit,
    onTogglePlay: () -> Unit,
    backdrop: Backdrop,
    loadPending: Boolean = false,
    modifier: Modifier = Modifier,
) {
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
                .background(MainPalette.Placeholder)
                .pluginSurface(PluginSurfaces.MINIPLAYER_COVER, PluginUiTarget.track(track))
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
                    maxPx = UrlImageCache.THUMB_MAX_PX,
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
                            color = TextTheme.MiniPlayerTitle,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artists,
                        style = TextStyle(
                            color = TextTheme.MiniPlayerSubtitle,
                            fontSize = 11.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MiniPlayerProgress(
                trackId = track.id,
                durationMs = if (durationMs > 0L) durationMs else track.durationMs,
                buffering = buffering,
                loadPending = loadPending,
                positions = positions,
                initialPositionMs = initialPositionMs,
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
                tint = TextTheme.MiniPlayerIcon,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun MiniPlayerProgress(
    trackId: Long,
    durationMs: Long,
    buffering: Boolean,
    loadPending: Boolean,
    positions: Flow<Long>,
    initialPositionMs: Long,
) {
    val anim = remember {
        Animatable(initialPositionMs.toFloat().coerceAtLeast(0f))
    }
    var boundTrackId by remember { mutableLongStateOf(trackId) }
    val loadPendingRef = rememberUpdatedState(loadPending)
    val bufferingRef = rememberUpdatedState(buffering)
    val durationRef = rememberUpdatedState(durationMs)
    val initialRef = rememberUpdatedState(initialPositionMs)
    LaunchedEffect(trackId, positions) {
        val seed = initialRef.value.toFloat().coerceAtLeast(0f)
        if (seed > 48f && anim.value <= 48f) {
            anim.snapTo(seed)
        }
        positions.collect { positionMs ->
            val target = positionMs.toFloat().coerceAtLeast(0f)
            val from = anim.value
            val trackChanged = trackId != boundTrackId
            if (trackChanged) boundTrackId = trackId
            val holding = loadPendingRef.value || bufferingRef.value
            if (miniProgressWrapToStart(from, target, durationRef.value, trackChanged)) {
                anim.animateTo(
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = 360,
                        easing = FastOutSlowInEasing,
                    ),
                )
                return@collect
            }
            // 续播 / 缓冲时的假 0：保住当前进度，不要从开头再跟一遍
            if (target <= 48f && from > 200f && holding) {
                return@collect
            }
            anim.snapTo(target)
        }
    }
    LinearProgressIndicator(
        progress = {
            val dur = durationMs.toFloat()
            if (dur <= 0f) 0f
            else (anim.value / dur).coerceIn(0f, 1f)
        },
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp)),
        color = MainPalette.Accent,
        trackColor = MainPalette.Hairline,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** 曲末回到开头（单曲循环 / 自动下一首）才做回退动画；续播假 0 不动画。 */
private fun miniProgressWrapToStart(
    from: Float,
    target: Float,
    durationMs: Long,
    trackChanged: Boolean,
): Boolean {
    if (target > 48f || from <= 64f || from - target <= 64f) return false
    val dur = durationMs.toFloat()
    val nearEnd = dur > 0f && (from >= dur * 0.75f || dur - from <= 5_000f)
    return nearEnd || trackChanged
}
