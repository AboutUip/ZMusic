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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.ChromeGlassMode
import com.kite.zmusic.data.MiniQuickSkipAxis
import com.kite.zmusic.data.MiniQuickSkipPrefs
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.plugin.PluginSurfaces
import com.kite.zmusic.plugin.PluginUiTarget
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.LocalChromeGlassStyle
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.theme.TextTheme
import com.kite.zmusic.ui.main.mainLiquidGlass
import com.kite.zmusic.ui.plugin.pluginSurface
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.kite.zmusic.i18n.t

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
    quickSkip: MiniQuickSkipPrefs = MiniQuickSkipPrefs(),
    onSkipNext: () -> Unit = {},
    onSkipPrev: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val noopDrag = rememberDraggableState { }
    val skipAnim = remember { Animatable(1f) }
    val skipScope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf<TrackRow?>(null) }
    var skipToNext by remember { mutableStateOf(true) }
    var skipVertical by remember { mutableStateOf(false) }
    val skipBusy = skipAnim.isRunning || leaving != null
    val skipEnabled = quickSkip.enabled
    val horizontalSkip = skipEnabled && quickSkip.axis == MiniQuickSkipAxis.Horizontal
    fun beginSkip(toNext: Boolean) {
        if (skipBusy) return
        leaving = track
        skipToNext = toNext
        skipVertical = quickSkip.axis == MiniQuickSkipAxis.Vertical
        skipScope.launch { skipAnim.snapTo(0f) }
        if (toNext) onSkipNext() else onSkipPrev()
    }
    LaunchedEffect(track.id) {
        val from = leaving ?: return@LaunchedEffect
        if (from.id == track.id) return@LaunchedEffect
        skipAnim.snapTo(0f)
        skipAnim.animateTo(
            1f,
            tween(400, easing = FastOutSlowInEasing),
        )
        leaving = null
    }
    LaunchedEffect(leaving) {
        val from = leaving ?: return@LaunchedEffect
        kotlinx.coroutines.delay(520)
        if (leaving?.id == from.id && track.id == from.id) {
            leaving = null
            skipAnim.snapTo(1f)
        }
    }

    val expand = LocalPlayerExpand.current
    val chromeReveal = expand?.miniChromeReveal ?: 1f
    val glassMode = LocalChromeGlassStyle.current.mode
    val landing = expandCardFromColor()
    val chrome = if (glassMode == ChromeGlassMode.Solid) {
        Modifier
            .clip(shape)
            .background(lerp(landing, MainPalette.Surface, chromeReveal))
    } else {
        // 液态玻璃必须画在有尺寸的节点上，不能再外包一层 clip：
        // 切 Dock 会换壁纸并重采样 Backdrop，外层 clip 会让 RenderThread 崩掉。
        Modifier.mainLiquidGlass(backdrop, shape)
    }
    Box(
        modifier
            .fillMaxSize()
            .playerExpandAnchor(PlayerExpandSlot.MiniBar)
            .then(
                if (horizontalSkip) {
                    Modifier
                } else {
                    Modifier.draggable(
                        state = noopDrag,
                        orientation = Orientation.Horizontal,
                    )
                },
            )
            .then(chrome),
    ) {
        if (glassMode != ChromeGlassMode.Solid && chromeReveal < 0.999f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(landing.copy(alpha = 1f - chromeReveal)),
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Row(
            Modifier
                .weight(1f)
                .miniQuickSkipDetect(
                    enabled = skipEnabled,
                    axis = quickSkip.axis,
                    busy = skipBusy,
                    onNext = { beginSkip(true) },
                    onPrev = { beginSkip(false) },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenFull,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            Modifier
                .playerExpandAnchor(PlayerExpandSlot.MiniCover)
                .playerExpandHideMini()
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MainPalette.Placeholder)
                .pluginSurface(PluginSurfaces.MINIPLAYER_COVER, PluginUiTarget.track(track)),
        ) {
            if (leaving == null) {
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
            } else {
                MiniSkipVisual(
                    current = track,
                    outgoing = leaving,
                    progress = skipAnim.value,
                    vertical = skipVertical,
                    toNext = skipToNext,
                    modifier = Modifier.fillMaxSize(),
                ) { item ->
                    UrlImage(
                        url = item.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        maxPx = UrlImageCache.THUMB_MAX_PX,
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            if (leaving == null) {
                Crossfade(
                    targetState = track.id,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "miniMeta",
                    modifier = Modifier.playerExpandHideMini(),
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
                            modifier = Modifier.playerExpandAnchor(PlayerExpandSlot.MiniTitle),
                        )
                        Text(
                            text = track.artists,
                            style = TextStyle(
                                color = TextTheme.MiniPlayerSubtitle,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.playerExpandAnchor(PlayerExpandSlot.MiniArtist),
                        )
                    }
                }
            } else {
                MiniSkipVisual(
                    current = track,
                    outgoing = leaving,
                    progress = skipAnim.value,
                    vertical = skipVertical,
                    toNext = skipToNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .playerExpandHideMini(),
                ) { item ->
                    Column {
                        Text(
                            text = item.name,
                            style = TextStyle(
                                color = TextTheme.MiniPlayerTitle,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.playerExpandAnchor(PlayerExpandSlot.MiniTitle),
                        )
                        Text(
                            text = item.artists,
                            style = TextStyle(
                                color = TextTheme.MiniPlayerSubtitle,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.playerExpandAnchor(PlayerExpandSlot.MiniArtist),
                        )
                    }
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
        }
        Box(
            Modifier
                .playerExpandAnchor(PlayerExpandSlot.MiniPlay)
                .playerExpandHideMini()
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
                contentDescription = if (isPlaying) t("暂停") else t("播放"),
                tint = TextTheme.MiniPlayerIcon,
                modifier = Modifier.size(22.dp),
            )
        }
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
    var livePos by remember { mutableLongStateOf(initialPositionMs.coerceAtLeast(0L)) }
    val initialRef = rememberUpdatedState(initialPositionMs)
    LaunchedEffect(positions) {
        val seed = initialRef.value.coerceAtLeast(0L)
        if (seed > 48L && livePos <= 48L) livePos = seed
        positions.collect { livePos = it.coerceAtLeast(0L) }
    }
    val clock = rememberSeekDisplayClock(
        trackId = trackId,
        positionMs = livePos,
        durationMs = durationMs,
        loadPending = loadPending || buffering,
    )
    LinearProgressIndicator(
        progress = {
            seekProgressFraction(clock.positionMs, clock.durationMs)
        },
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(2.dp)
            .playerExpandAnchor(PlayerExpandSlot.MiniProgress)
            .playerExpandHideMini()
            .clip(RoundedCornerShape(1.dp)),
        color = MainPalette.Accent,
        trackColor = MainPalette.Hairline,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
