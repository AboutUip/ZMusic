package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.TitleLineStyle
import com.kite.zmusic.data.TrackRow
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

enum class VinylSongPickPhase {
    Entering,
    Stacking,
    FanOut,
    Browsing,
    Confirming,
    Canceling,
}

private val FogAccent = Color(0xFF9AF0F0)
private const val MaxNextStack = 20
private const val MaxFanPrev = 12
private const val TiltDeg = 10f
private val BrowseGap = 18.dp

@Composable
internal fun VinylSongPickOverlay(
    queue: List<TrackRow>,
    queueIndex: Int,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    discSize: Dp,
    plate: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    titleNameStyle: TitleLineStyle,
    fogProgress: Float,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    onCoverMainChange: (Boolean) -> Unit = {},
    onPhaseChange: (VinylSongPickPhase) -> Unit = {},
) {
    var phase by remember { mutableStateOf(VinylSongPickPhase.Entering) }
    val onCover = rememberUpdatedState(onCoverMainChange)
    val onPhase = rememberUpdatedState(onPhaseChange)
    LaunchedEffect(phase) { onPhase.value(phase) }
    LaunchedEffect(Unit) {
        delay(maxOf(VinylCenterMs, 520).toLong() + 48L)
        if (phase == VinylSongPickPhase.Entering) phase = VinylSongPickPhase.Stacking
    }
    LaunchedEffect(phase) {
        when (phase) {
            VinylSongPickPhase.Stacking,
            VinylSongPickPhase.FanOut,
            VinylSongPickPhase.Browsing,
            VinylSongPickPhase.Confirming,
            -> onCover.value(true)
            VinylSongPickPhase.Entering -> onCover.value(false)
            VinylSongPickPhase.Canceling -> Unit
        }
    }
    VinylSongPickSession(
        phase = phase,
        fogProgress = fogProgress,
        queue = queue,
        queueIndex = queueIndex,
        focusedIndex = focusedIndex,
        onFocusedIndexChange = onFocusedIndexChange,
        discSize = discSize,
        plate = plate,
        fullCover = fullCover,
        centerRadiusFrac = centerRadiusFrac,
        outerScale = outerScale,
        titleNameStyle = titleNameStyle,
        onBack = {
            if (phase != VinylSongPickPhase.Confirming && phase != VinylSongPickPhase.Canceling) {
                phase = VinylSongPickPhase.Canceling
            }
        },
        onConfirmFocused = {
            if (phase == VinylSongPickPhase.Browsing) phase = VinylSongPickPhase.Confirming
        },
        onStackingFinished = {
            if (phase == VinylSongPickPhase.Stacking) phase = VinylSongPickPhase.FanOut
        },
        onFanOutFinished = {
            if (phase == VinylSongPickPhase.FanOut) phase = VinylSongPickPhase.Browsing
        },
        onCancelExitFinished = onDismiss,
        onConfirmExitFinished = {
            onConfirm(focusedIndex)
            onDismiss()
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VinylSongPickSession(
    phase: VinylSongPickPhase,
    fogProgress: Float,
    queue: List<TrackRow>,
    queueIndex: Int,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    discSize: Dp,
    plate: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    titleNameStyle: TitleLineStyle,
    onBack: () -> Unit,
    onConfirmFocused: () -> Unit,
    onStackingFinished: () -> Unit,
    onFanOutFinished: () -> Unit,
    onCancelExitFinished: () -> Unit,
    onConfirmExitFinished: () -> Unit,
) {
    val t = fogProgress.coerceIn(0f, 1f)
    var sessionQueue by remember { mutableStateOf(queue) }
    var sessionAnchor by remember {
        mutableIntStateOf(queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)))
    }
    LaunchedEffect(phase, queue, queueIndex) {
        if (phase == VinylSongPickPhase.Entering || phase == VinylSongPickPhase.Stacking) {
            sessionQueue = queue
            sessionAnchor = queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        }
    }
    val safeIndex = sessionAnchor.coerceIn(0, (sessionQueue.size - 1).coerceAtLeast(0))
    val nextTracks = remember(sessionQueue, safeIndex) {
        if (sessionQueue.isEmpty() || safeIndex >= sessionQueue.lastIndex) emptyList()
        else sessionQueue.subList(safeIndex + 1, sessionQueue.size).take(MaxNextStack)
    }
    val fanPrevTracks = remember(sessionQueue, safeIndex) {
        if (sessionQueue.isEmpty() || safeIndex <= 0) emptyList()
        else sessionQueue.subList(0, safeIndex).takeLast(MaxFanPrev)
    }
    val browseTracks = sessionQueue
    val browseFocusLocal = remember(browseTracks, focusedIndex, safeIndex) {
        val id = browseTracks.getOrNull(focusedIndex)?.id ?: browseTracks.getOrNull(safeIndex)?.id
        browseTracks.indexOfFirst { it.id == id }.coerceAtLeast(0)
    }
    val stackT = remember { Animatable(0f) }
    val fanT = remember { Animatable(0f) }
    val exitT = remember { Animatable(0f) }
    val stackReveal = remember { Animatable(0f) }
    val browseReveal = remember { Animatable(0f) }
    val ringReveal = remember { Animatable(0f) }
    val onStacking = rememberUpdatedState(onStackingFinished)
    val onFanOut = rememberUpdatedState(onFanOutFinished)
    val onCancelDone = rememberUpdatedState(onCancelExitFinished)
    val onConfirmDone = rememberUpdatedState(onConfirmExitFinished)
    LaunchedEffect(phase) {
        when (phase) {
            VinylSongPickPhase.Entering -> {
                exitT.snapTo(0f); stackT.snapTo(0f); fanT.snapTo(0f)
                stackReveal.snapTo(0f); browseReveal.snapTo(0f); ringReveal.snapTo(0f)
            }
            VinylSongPickPhase.Stacking -> {
                fanT.snapTo(0f); browseReveal.snapTo(0f); ringReveal.snapTo(0f)
                stackT.snapTo(0f); stackReveal.snapTo(1f)
                stackT.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                onStacking.value()
            }
            VinylSongPickPhase.FanOut -> {
                stackT.snapTo(1f); stackReveal.snapTo(1f); ringReveal.snapTo(0f)
                fanT.snapTo(0f); browseReveal.snapTo(0f)
                fanT.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
                withFrameNanos { }; withFrameNanos { }
                browseReveal.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                onFanOut.value()
            }
            VinylSongPickPhase.Browsing -> {
                stackT.snapTo(1f); fanT.snapTo(1f); stackReveal.snapTo(1f)
                browseReveal.snapTo(1f); exitT.snapTo(0f)
                ringReveal.snapTo(0f)
                ringReveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
            VinylSongPickPhase.Confirming -> {
                stackT.snapTo(1f); fanT.snapTo(1f); browseReveal.snapTo(1f)
                exitT.snapTo(0f)
                exitT.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                onConfirmDone.value()
            }
            VinylSongPickPhase.Canceling -> {
                when {
                    browseReveal.value > 0.2f -> {
                        ringReveal.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                        exitT.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
                    }
                    stackReveal.value > 0.2f -> {
                        fanT.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
                        stackT.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
                        exitT.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
                        stackReveal.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                    }
                    else -> delay(120)
                }
                onCancelDone.value()
            }
        }
    }
    val fogAlpha = t * (1f - exitT.value * 0.15f)
    val showStack = phase == VinylSongPickPhase.Entering ||
        phase == VinylSongPickPhase.Stacking ||
        phase == VinylSongPickPhase.FanOut ||
        (phase == VinylSongPickPhase.Canceling && browseReveal.value <= 0.2f && stackReveal.value > 0.01f)
    val showBrowse = phase == VinylSongPickPhase.FanOut ||
        phase == VinylSongPickPhase.Browsing ||
        phase == VinylSongPickPhase.Confirming ||
        (phase == VinylSongPickPhase.Canceling && browseReveal.value > 0.2f)
    val browseAlpha = browseReveal.value * (1f - exitT.value).coerceIn(0f, 1f)
    val stackAlpha = stackReveal.value * if (browseReveal.value >= 0.995f) 0f else 1f
    val chromeAlpha = fogAlpha * if (phase == VinylSongPickPhase.Confirming) 0f else 1f
    Box(Modifier.fillMaxSize().zIndex(80f)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fogAlpha }
                .background(Color(0x9905080E))
                .clickable(
                    enabled = phase == VinylSongPickPhase.Browsing,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val snapX = maxWidth / 2
            val snapY = maxHeight / 2
            if (showStack && sessionQueue.isNotEmpty() && stackAlpha > 0.01f) {
                VinylSongPickStackAndFan(
                    fanPrevTracks = fanPrevTracks,
                    current = sessionQueue[safeIndex],
                    nextTracks = nextTracks,
                    stackT = stackT.value,
                    fanT = fanT.value,
                    contentAlpha = stackAlpha,
                    snapCenterX = snapX,
                    snapCenterY = snapY,
                    discSize = discSize,
                    plate = plate,
                    fullCover = fullCover,
                    centerRadiusFrac = centerRadiusFrac,
                    outerScale = outerScale,
                )
            }
            if (showBrowse && browseTracks.isNotEmpty() && browseAlpha > 0.001f) {
                VinylSongPickBrowseRow(
                    tracks = browseTracks,
                    initialIndex = browseFocusLocal,
                    snapCenterX = snapX,
                    snapCenterY = snapY,
                    discSize = discSize,
                    plate = plate,
                    fullCover = fullCover,
                    centerRadiusFrac = centerRadiusFrac,
                    outerScale = outerScale,
                    contentAlpha = browseAlpha.coerceAtLeast(
                        if (phase == VinylSongPickPhase.FanOut) 0.001f else 0f,
                    ),
                    ringAlpha = if (phase == VinylSongPickPhase.Browsing) ringReveal.value else 0f,
                    hideFocused = (phase == VinylSongPickPhase.Confirming || phase == VinylSongPickPhase.Canceling) &&
                        exitT.value > 0.02f,
                    interactive = phase == VinylSongPickPhase.Browsing,
                    onFocusedChange = onFocusedIndexChange,
                    onConfirm = { onConfirmFocused() },
                )
            }
        }
        if (chromeAlpha > 0.04f) {
            val focused = browseTracks.getOrNull(browseFocusLocal) ?: sessionQueue.getOrNull(safeIndex)
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 18.dp, end = 20.dp)
                    .graphicsLayer { alpha = chromeAlpha },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", color = Color.White.copy(alpha = 0.92f), fontSize = 22.sp)
                }
                Text(
                    focused?.name.orEmpty(),
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    style = TextStyle(
                        color = Color(titleNameStyle.resolvedArgb(TitleLineStyle.DEFAULT_NAME_ARGB)),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (TitleLineStyle.BASE_NAME_SP * titleNameStyle.fontScale).sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VinylSongPickStackAndFan(
    fanPrevTracks: List<TrackRow>,
    current: TrackRow,
    nextTracks: List<TrackRow>,
    stackT: Float,
    fanT: Float,
    contentAlpha: Float,
    snapCenterX: Dp,
    snapCenterY: Dp,
    discSize: Dp,
    plate: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
) {
    val density = LocalDensity.current
    val st = stackT.coerceIn(0f, 1f)
    val ft = fanT.coerceIn(0f, 1f)
    val tilt = TiltDeg * (1f - ft) * st
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { clip = false }) {
        val halfScreen = maxWidth * 0.5f
        val budget = (halfScreen - discSize).coerceAtLeast(0.dp)
        val rightN = nextTracks.size
        val peek = if (rightN > 0) budget / rightN else 0.dp
        val stride = discSize + BrowseGap
        val originX = snapCenterX - discSize / 2
        val originY = snapCenterY - discSize / 2
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                    alpha = contentAlpha
                    if (abs(tilt) > 0.05f) {
                        transformOrigin = TransformOrigin(
                            (snapCenterX / maxWidth).coerceIn(0.05f, 0.95f),
                            (snapCenterY / maxHeight).coerceIn(0.05f, 0.95f),
                        )
                        rotationY = tilt
                        cameraDistance = 18f * density.density
                    }
                },
        ) {
            nextTracks.forEachIndexed { i, track ->
                val step = i + 1
                val stackX = originX + peek * step * st
                val rowX = originX + stride * step
                val x = lerpDp(stackX, rowX, ft)
                val a = 0.15f + 0.85f * st
                PickVinylDisc(
                    track, plate, fullCover, centerRadiusFrac, outerScale,
                    Modifier.offset(x = x, y = originY).size(discSize).zIndex((rightN - step).toFloat())
                        .graphicsLayer { alpha = a },
                )
            }
            PickVinylDisc(
                current, plate, fullCover, centerRadiusFrac, outerScale,
                Modifier.offset(x = originX, y = originY).size(discSize).zIndex(MaxNextStack + 1f),
            )
            fanPrevTracks.asReversed().forEachIndexed { visualOrder, track ->
                val step = visualOrder + 1
                val rowX = originX + stride * -step
                val x = lerpDp(originX, rowX, ft)
                if (ft > 0.01f) {
                    PickVinylDisc(
                        track, plate, fullCover, centerRadiusFrac, outerScale,
                        Modifier.offset(x = x, y = originY).size(discSize)
                            .zIndex(MaxNextStack + 2f + step)
                            .graphicsLayer { alpha = ft },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VinylSongPickBrowseRow(
    tracks: List<TrackRow>,
    initialIndex: Int,
    snapCenterX: Dp,
    snapCenterY: Dp,
    discSize: Dp,
    plate: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    contentAlpha: Float,
    ringAlpha: Float,
    hideFocused: Boolean,
    interactive: Boolean,
    onFocusedChange: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    val safe = initialIndex.coerceIn(0, tracks.lastIndex.coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safe)
    val snapFling = rememberSnapFlingBehavior(lazyListState = listState)
    val focusRef = rememberUpdatedState(onFocusedChange)
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) {
        val startPad = (snapCenterX - discSize / 2).coerceAtLeast(0.dp)
        val endPad = (maxWidth - snapCenterX - discSize / 2).coerceAtLeast(0.dp) + 8.dp
        val rowTop = snapCenterY - discSize / 2
        val startPadPx = with(LocalDensity.current) { startPad.roundToPx() }
        if (ringAlpha > 0.01f) {
            Box(
                Modifier
                    .offset(x = startPad, y = rowTop)
                    .size(discSize)
                    .graphicsLayer { alpha = ringAlpha * 0.95f }
                    .border(2.dp, FogAccent.copy(alpha = 0.65f), CircleShape),
            )
        }
        LaunchedEffect(listState, tracks.size, startPadPx) {
            snapshotFlow {
                val items = listState.layoutInfo.visibleItemsInfo
                if (items.isEmpty()) listState.firstVisibleItemIndex
                else items.minBy { abs(it.offset - startPadPx) }.index
            }.distinctUntilChanged().collect { focusRef.value(it) }
        }
        LazyRow(
            state = listState,
            flingBehavior = snapFling,
            userScrollEnabled = interactive,
            contentPadding = PaddingValues(start = startPad, end = endPad),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = rowTop),
        ) {
            itemsIndexed(tracks, key = { i, t -> "${t.id}_$i" }) { index, track ->
                val focused = index == listState.firstVisibleItemIndex
                PickVinylDisc(
                    track, plate, fullCover, centerRadiusFrac, outerScale,
                    Modifier
                        .padding(end = BrowseGap)
                        .size(discSize)
                        .graphicsLayer { alpha = if (hideFocused && focused) 0f else 1f }
                        .then(
                            if (interactive && focused) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onConfirm,
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun PickVinylDisc(
    track: TrackRow,
    plate: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    modifier: Modifier,
) {
    VinylWithCoverArt(
        track = track,
        spinning = false,
        onSkipNext = {},
        onSkipPrev = {},
        plate = plate,
        outerScale = outerScale,
        centerRadiusFrac = centerRadiusFrac,
        fullCover = fullCover,
        gesturesEnabled = false,
        modifier = modifier,
    )
}
