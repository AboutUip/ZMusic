package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.islandLiquidGlass
import com.kite.zmusic.ui.notice.IslandMarqueeText
import com.kite.zmusic.ui.notice.IslandGlyph
import com.kite.zmusic.ui.notice.islandTextStyle
import com.kite.zmusic.ui.theme.TextTheme
import com.kyant.backdrop.Backdrop
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val PlaybackIslandShape = RoundedCornerShape(50)

/**
 * 常驻播放岛。收起时只有圆形封面；点封面展开到最大宽度，再点进入播放页。
 * 展开后几秒收回封面；横滑则宽度和位移一起收到封面，并偏向滑动方向。
 * 通知借用同一座岛变形，不再另起一座胶囊。
 */
@Composable
internal fun PlaybackIsland(
    track: TrackRow,
    isPlaying: Boolean,
    landscape: Boolean,
    handoffNotices: Boolean,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val expandState = LocalPlayerExpand.current
    val playerHidden = expandState?.hideMiniShared == true
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val center = app.islandNoticeCenter
    val frame by center.frame.collectAsStateWithLifecycle()
    val claim = handoffNotices && !playerHidden
    SideEffect {
        center.setPlaybackClaim(claim)
        val state = expandState ?: return@SideEffect
        if (state.mounted) return@SideEffect
        val shell = state.toShell(state.miniBar)
        if (shell.isAnchorValid()) state.fallbackMiniBar = shell
    }
    DisposableEffect(center) {
        onDispose { center.setPlaybackClaim(false) }
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val islandH = if (landscape) 36.dp else 40.dp
    val coverSize = islandH - 8.dp
    var expanded by remember { mutableStateOf(false) }
    var openWhenReady by remember { mutableStateOf(false) }
    var gestureOwned by remember { mutableStateOf(false) }
    var gestureGen by remember { mutableIntStateOf(0) }
    var followFinger by remember { mutableStateOf(false) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    var collapseDir by remember { mutableFloatStateOf(0f) }
    var dwellToken by remember { mutableIntStateOf(0) }
    val expand = remember { Animatable(0f) }
    val dragX = remember { Animatable(0f) }
    val openRequest = rememberUpdatedState(onOpen)
    val playSource = remember { MutableInteractionSource() }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val noticeBusy = claim && frame.mounted && frame.expansion > 0.05f

    LaunchedEffect(expanded, gestureOwned) {
        if (gestureOwned) return@LaunchedEffect
        expand.animateTo(
            if (expanded) 1f else 0f,
            tween(PlaybackIslandCollapseMs, easing = FastOutSlowInEasing),
        )
    }
    LaunchedEffect(
        expanded,
        gestureOwned,
        followFinger,
        playerHidden,
        noticeBusy,
        dwellToken,
        track.id,
    ) {
        if (!expanded || gestureOwned || followFinger || playerHidden || noticeBusy) return@LaunchedEffect
        delay(PlaybackIslandCoverDwellMs)
        if (gestureOwned || followFinger || playerHidden) return@LaunchedEffect
        expanded = false
    }
    LaunchedEffect(openWhenReady, expanded) {
        if (!openWhenReady || !expanded) return@LaunchedEffect
        snapshotFlow { expand.value }.first { it >= 0.995f }
        delay(32)
        if (!openWhenReady || !expanded) return@LaunchedEffect
        openRequest.value()
        openWhenReady = false
    }

    BoxWithConstraints(modifier) {
        val incoming = if (maxWidth.value.isFinite()) maxWidth - 24.dp else 300.dp
        val maxCapsule = minOf(incoming, if (landscape) 320.dp else 300.dp).coerceAtLeast(islandH)
        val blend = if (claim && frame.mounted) frame.expansion.coerceIn(0f, 1f) else 0f
        val playbackW = lerp(islandH, maxCapsule, expand.value.coerceIn(0f, 1f))
        val noticeTarget = frame.restWidthDp.dp.coerceIn(islandH, maxCapsule)
        val visualW = lerp(playbackW, noticeTarget, blend)
        val musicA = 1f - islandFade(blend)
        val noticeA = islandFade(blend)
        val textIn = expand.value.coerceIn(0f, 1f)
        val dragShown = if (followFinger) fingerX else dragX.value
        val pinPx = if (!followFinger && collapseDir != 0f) {
            val maxPx = with(density) { maxCapsule.toPx() }
            val nowPx = with(density) { playbackW.toPx() }
            collapseDir * (maxPx - nowPx).coerceAtLeast(0f) / 2f * textIn
        } else {
            0f
        }
        val startPad = lerp(4.dp, 6.dp, blend)
        val musicEnd = lerp(4.dp, 6.dp, expand.value.coerceIn(0f, 1f))
        val endPad = lerp(musicEnd, 14.dp, blend)
        val squash = if (claim && frame.mounted) frame.squashY.coerceIn(0.82f, 1.12f) else 1f
        val playEnabled = textIn > 0.9f && blend < 0.08f && !playerHidden
        val canSwipeNow = expand.value > 0.92f && blend < 0.04f && !playerHidden
        val swipeEnabled = rememberUpdatedState(canSwipeNow)
        val expandedNow = rememberUpdatedState(expanded)
        val expandNow = rememberUpdatedState(expand.value)
        val blendNow = rememberUpdatedState(blend)
        val hiddenNow = rememberUpdatedState(playerHidden)

        fun settleSwipe(dx: Float) {
            val gen = ++gestureGen
            settleJob?.cancel()
            settleJob = scope.launch {
                gestureOwned = true
                openWhenReady = false
                dragX.snapTo(dx)
                if (gestureGen != gen) return@launch
                followFinger = false
                try {
                    val threshold = with(density) { 48.dp.toPx() }
                    if (abs(dx) < threshold) {
                        collapseDir = 0f
                        dragX.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 480f))
                        return@launch
                    }
                    collapseDir = if (dx >= 0f) 1f else -1f
                    expanded = false
                    coroutineScope {
                        launch {
                            dragX.animateTo(0f, tween(PlaybackIslandCollapseMs, easing = FastOutSlowInEasing))
                        }
                        launch {
                            expand.animateTo(0f, tween(PlaybackIslandCollapseMs, easing = FastOutSlowInEasing))
                        }
                    }
                } finally {
                    if (gestureGen == gen) {
                        collapseDir = 0f
                        gestureOwned = false
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .playerExpandAnchor(PlayerExpandSlot.MiniBar)
                .graphicsLayer {
                    alpha = if (playerHidden) 0f else 1f
                    translationX = dragShown + pinPx
                    scaleY = squash
                }
                .width(visualW)
                .height(islandH)
                .islandLiquidGlass(backdrop, PlaybackIslandShape)
                .clip(PlaybackIslandShape)
                .then(
                    if (playerHidden) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            val slop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                if (hiddenNow.value) return@awaitEachGesture
                                val down = awaitFirstDown()
                                var totalDx = 0f
                                var totalDy = 0f
                                var horizontal = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val delta = change.positionChange()
                                    totalDx += delta.x
                                    totalDy += delta.y
                                    if (!horizontal &&
                                        abs(totalDx) > slop &&
                                        abs(totalDx) > abs(totalDy) * 1.15f &&
                                        swipeEnabled.value
                                    ) {
                                        horizontal = true
                                        gestureGen += 1
                                        settleJob?.cancel()
                                        gestureOwned = true
                                        openWhenReady = false
                                        followFinger = true
                                        fingerX = totalDx
                                    }
                                    if (horizontal) {
                                        change.consume()
                                        fingerX = totalDx
                                        followFinger = true
                                    }
                                    if (!change.pressed) {
                                        if (horizontal) {
                                            settleSwipe(totalDx)
                                        } else if (abs(totalDx) < slop && abs(totalDy) < slop) {
                                            when {
                                                blendNow.value >= 0.04f -> Unit
                                                expandedNow.value && expandNow.value >= 0.995f ->
                                                    openRequest.value()
                                                expandedNow.value -> openWhenReady = true
                                                else -> expanded = true
                                            }
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    },
                ),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = startPad, end = endPad)
                    .graphicsLayer { alpha = musicA },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlImage(
                    url = track.coverUrl,
                    contentDescription = track.name,
                    modifier = Modifier
                        .playerExpandAnchor(PlayerExpandSlot.MiniCover)
                        .playerExpandHideMini()
                        .size(coverSize)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text(
                        text = track.name,
                        style = TextStyle(
                            color = TextTheme.Island,
                            fontSize = if (landscape) 12.5.sp else 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.15).sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .playerExpandAnchor(PlayerExpandSlot.MiniTitle)
                            .playerExpandHideMini()
                            .padding(horizontal = 8.dp)
                            .graphicsLayer { alpha = islandFade(textIn) },
                    )
                    Box(
                        Modifier
                            .playerExpandAnchor(PlayerExpandSlot.MiniPlay)
                            .playerExpandHideMini()
                            .size(32.dp)
                            .clip(CircleShape)
                            .graphicsLayer { alpha = islandFade(textIn) }
                            .then(
                                if (!playEnabled) {
                                    Modifier
                                } else {
                                    Modifier.clickable(
                                        interactionSource = playSource,
                                        indication = null,
                                        onClick = {
                                            dwellToken++
                                            onTogglePlay()
                                        },
                                    )
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) ZIcons.Pause else ZIcons.Play,
                            contentDescription = if (isPlaying) t("暂停") else t("播放"),
                            tint = TextTheme.MiniPlayerIcon,
                            modifier = Modifier.size(18.dp),
                        )
                    }
            }
            if (noticeA > 0.02f && frame.message.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(start = startPad, end = endPad)
                        .graphicsLayer { alpha = noticeA },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IslandGlyph(
                        coverUrl = frame.coverUrl,
                        coverBlend = frame.coverBlend,
                        size = coverSize,
                    )
                    IslandMarqueeText(
                        text = frame.message,
                        style = islandTextStyle(landscape),
                        overflowing = frame.overflowing && blend > 0.92f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private const val PlaybackIslandCollapseMs = 340
private const val PlaybackIslandCoverDwellMs = 2_800L

private fun islandFade(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
