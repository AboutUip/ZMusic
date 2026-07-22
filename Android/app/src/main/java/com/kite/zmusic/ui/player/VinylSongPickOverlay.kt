package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TitleLineStyle
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.VinylPlateColors
import com.kite.zmusic.ui.common.UrlImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class VinylSongPickPhase {
    Entering,
    Stacking,
    FanOut,
    Browsing,
    Confirming,
    Canceling,
}

private val FogAccent = Color(0xFF9AF0F0)
private val PickFogStyle = HazeStyle(
    backgroundColor = Color(0xFF03060A),
    tints = listOf(
        HazeTint(Color(0xFF070B12).copy(alpha = 0.28f)),
        HazeTint(Color.Black.copy(alpha = 0.18f)),
    ),
    blurRadius = 72.dp,
    noiseFactor = 0.10f,
    fallbackTint = HazeTint(Color(0x9905080E)),
)

private const val MaxNextStack = 20
/** 散开时左侧滑入的上一首数量（仅动画覆盖，浏览列表用完整 queue） */
private const val MaxFanPrev = 12
private const val TiltDeg = 10f
private val BrowseGap = 18.dp

@Composable
fun VinylSongPickOverlay(
    phase: VinylSongPickPhase,
    progress: Float,
    queue: List<TrackRow>,
    queueIndex: Int,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    snapCenterX: Dp,
    snapCenterY: Dp,
    discSize: Dp,
    plateColors: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    hazeState: HazeState,
    /** 顶栏歌名：与横屏标题信息同一套标题样式 */
    titleNameStyle: TitleLineStyle = TitleLineStyle.NameDefault,
    uiScale: Float = 1f,
    /** 每次打开选歌递增；用于重建会话，避免沿用切歌前的旧队列/锚点 */
    sessionKey: Int = 0,
    onBack: () -> Unit,
    onConfirmFocused: () -> Unit,
    onCancelExitFinished: () -> Unit,
    onConfirmExitFinished: () -> Unit,
    onStackingFinished: () -> Unit,
    onFanOutFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0.001f) return

    key(sessionKey) {
        VinylSongPickOverlaySession(
            phase = phase,
            progress = t,
            queue = queue,
            queueIndex = queueIndex,
            focusedIndex = focusedIndex,
            onFocusedIndexChange = onFocusedIndexChange,
            snapCenterX = snapCenterX,
            snapCenterY = snapCenterY,
            discSize = discSize,
            plateColors = plateColors,
            fullCover = fullCover,
            centerRadiusFrac = centerRadiusFrac,
            outerScale = outerScale,
            hazeState = hazeState,
            titleNameStyle = titleNameStyle,
            uiScale = uiScale,
            onBack = onBack,
            onConfirmFocused = onConfirmFocused,
            onCancelExitFinished = onCancelExitFinished,
            onConfirmExitFinished = onConfirmExitFinished,
            onStackingFinished = onStackingFinished,
            onFanOutFinished = onFanOutFinished,
            modifier = modifier,
        )
    }
}

@Composable
private fun VinylSongPickOverlaySession(
    phase: VinylSongPickPhase,
    progress: Float,
    queue: List<TrackRow>,
    queueIndex: Int,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    snapCenterX: Dp,
    snapCenterY: Dp,
    discSize: Dp,
    plateColors: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    hazeState: HazeState,
    titleNameStyle: TitleLineStyle,
    uiScale: Float,
    onBack: () -> Unit,
    onConfirmFocused: () -> Unit,
    onCancelExitFinished: () -> Unit,
    onConfirmExitFinished: () -> Unit,
    onStackingFinished: () -> Unit,
    onFanOutFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = progress.coerceIn(0f, 1f)
    val fogAlpha = t
    val isCanceling = phase == VinylSongPickPhase.Canceling

    // 父级已按打开瞬间快照传入；此处再冻结，避免确认切歌后父级 props 闪变
    var sessionQueue by remember {
        mutableStateOf(queue)
    }
    var sessionAnchor by remember {
        mutableIntStateOf(queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)))
    }
    val queueUpdated by rememberUpdatedState(queue)
    val queueIndexUpdated by rememberUpdatedState(queueIndex)
    LaunchedEffect(phase) {
        if (phase == VinylSongPickPhase.Entering || phase == VinylSongPickPhase.Stacking) {
            val q = queueUpdated
            sessionQueue = q
            sessionAnchor = queueIndexUpdated.coerceIn(0, (q.size - 1).coerceAtLeast(0))
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
    // 浏览：完整歌单（不是堆叠预览的固定张数）
    val browseTracks = sessionQueue
    val browseFocusLocal = remember(browseTracks, focusedIndex, safeIndex) {
        val id = browseTracks.getOrNull(focusedIndex)?.id
            ?: browseTracks.getOrNull(safeIndex)?.id
        browseTracks.indexOfFirst { it.id == id }.coerceAtLeast(0)
    }

    val stackT = remember { Animatable(0f) }
    val fanT = remember { Animatable(0f) }
    val exitT = remember { Animatable(0f) }
    /** 主黑胶 → 叠层交叉淡入 */
    val stackReveal = remember { Animatable(0f) }
    /** 散开行 → 浏览 LazyRow 交叉淡入（避免瞬移换层） */
    val browseReveal = remember { Animatable(0f) }
    /** 吸附描边：仅进入可滑动浏览后动画描入 */
    val ringReveal = remember { Animatable(0f) }
    val onStackingFinishedUpdated by rememberUpdatedState(onStackingFinished)
    val onFanOutFinishedUpdated by rememberUpdatedState(onFanOutFinished)
    val onCancelExitFinishedUpdated by rememberUpdatedState(onCancelExitFinished)
    val onConfirmExitFinishedUpdated by rememberUpdatedState(onConfirmExitFinished)

    LaunchedEffect(phase) {
        when (phase) {
            VinylSongPickPhase.Entering -> {
                exitT.snapTo(0f)
                stackT.snapTo(0f)
                fanT.snapTo(0f)
                // 入场不画叠层盘：主黑胶继续清晰居中/归正，避免坐标未稳交接闪跳
                stackReveal.snapTo(0f)
                browseReveal.snapTo(0f)
                ringReveal.snapTo(0f)
            }
            VinylSongPickPhase.Stacking -> {
                exitT.snapTo(0f)
                fanT.snapTo(0f)
                browseReveal.snapTo(0f)
                ringReveal.snapTo(0f)
                stackT.snapTo(0f)
                // 先实色盖住主盘，再堆叠；haze 延后到 FanOut，避免居中刚结束就虚化闪一下
                stackReveal.snapTo(0f)
                stackReveal.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
                stackT.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                onStackingFinishedUpdated()
            }
            VinylSongPickPhase.FanOut -> {
                stackT.snapTo(1f)
                stackReveal.snapTo(1f)
                ringReveal.snapTo(0f)
                fanT.snapTo(0f)
                browseReveal.snapTo(0f)
                fanT.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
                // 预挂载 LazyRow 两帧对齐布局，再淡入盖住实色叠层（勿双边半透明叠画）
                withFrameNanos { }
                withFrameNanos { }
                browseReveal.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                onFanOutFinishedUpdated()
            }
            VinylSongPickPhase.Browsing -> {
                stackT.snapTo(1f)
                fanT.snapTo(1f)
                stackReveal.snapTo(1f)
                browseReveal.snapTo(1f)
                exitT.snapTo(0f)
                // 可滑动选歌时再动画描边，避免堆叠/散开期描边切割黑胶
                ringReveal.snapTo(0f)
                ringReveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
            VinylSongPickPhase.Confirming -> {
                stackT.snapTo(1f)
                fanT.snapTo(1f)
                stackReveal.snapTo(1f)
                browseReveal.snapTo(1f)
                exitT.snapTo(0f)
                exitT.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                onConfirmExitFinishedUpdated()
            }
            VinylSongPickPhase.Canceling -> {
                // 按当前进度反向退场：浏览收束 / 叠层收回 / 仅雾气时短停
                when {
                    browseReveal.value > 0.2f -> {
                        exitT.snapTo(0f)
                        ringReveal.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                        exitT.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                    }
                    stackReveal.value > 0.2f -> {
                        exitT.snapTo(0f)
                        ringReveal.snapTo(0f)
                        fanT.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
                        stackT.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
                        // 收到中心后交接盘接住，再卸叠层
                        exitT.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
                        stackReveal.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                    }
                    else -> {
                        exitT.snapTo(0f)
                        ringReveal.snapTo(0f)
                        delay(120)
                    }
                }
                onCancelExitFinishedUpdated()
            }
        }
    }

    val showStackOrFan = phase == VinylSongPickPhase.Entering ||
        phase == VinylSongPickPhase.Stacking ||
        phase == VinylSongPickPhase.FanOut ||
        (isCanceling && browseReveal.value <= 0.2f && stackReveal.value > 0.01f)
    val showBrowse = phase == VinylSongPickPhase.FanOut ||
        phase == VinylSongPickPhase.Browsing ||
        phase == VinylSongPickPhase.Confirming ||
        (isCanceling && browseReveal.value > 0.2f)
    val chromeVisible = phase != VinylSongPickPhase.Confirming
    val exitProgress = exitT.value
    val isConfirming = phase == VinylSongPickPhase.Confirming
    // 叠层托底保持不透明，浏览层淡入盖住后再卸叠层，避免 50%+50% 透底闪一下
    val browseCovered = browseReveal.value >= 0.995f
    val stackContentAlpha = fogAlpha * stackReveal.value * if (browseCovered) 0f else 1f
    val browseContentAlpha = fogAlpha * browseReveal.value * (1f - exitProgress * 0.55f)
    val browseRingAlpha = when {
        phase == VinylSongPickPhase.Browsing -> ringReveal.value
        phase == VinylSongPickPhase.Confirming || phase == VinylSongPickPhase.Canceling ->
            ringReveal.value * (1f - exitProgress).coerceIn(0f, 1f)
        else -> 0f
    }
    val chromeAlpha = fogAlpha * if (isCanceling) {
        (1f - exitProgress).coerceIn(0f, 1f)
    } else {
        1f
    }
    // Entering/Stacking 仅实色压暗；叠层盖住主盘后再启 haze，消除居中刚结束的虚化闪烁
    val usePickHaze = phase == VinylSongPickPhase.FanOut ||
        phase == VinylSongPickPhase.Browsing ||
        phase == VinylSongPickPhase.Confirming ||
        (phase == VinylSongPickPhase.Canceling && stackReveal.value > 0.5f)
    val showHandoffDisc = (isConfirming || isCanceling) && exitProgress > 0.45f

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(0f)
                .graphicsLayer { alpha = fogAlpha * (1f - exitProgress * 0.15f) }
                .clip(RoundedCornerShape(0.dp))
                .then(
                    if (usePickHaze) {
                        Modifier
                            .hazeEffect(state = hazeState, style = PickFogStyle) {
                                blurRadius = 72.dp
                                noiseFactor = 0.10f
                            }
                            // 与实色期同色同浓度，避免启 haze 时亮度跳变
                            .background(Color(0x9905080E))
                    } else {
                        Modifier.background(Color(0x9905080E))
                    },
                ),
        )

        Box(
            Modifier
                .fillMaxSize()
                .zIndex(2f)
                .graphicsLayer { clip = false },
        ) {
            if (showStackOrFan && sessionQueue.isNotEmpty() && stackContentAlpha > 0.01f) {
                VinylSongPickStackAndFan(
                    fanPrevTracks = fanPrevTracks,
                    current = sessionQueue[safeIndex],
                    nextTracks = nextTracks,
                    stackT = stackT.value,
                    fanT = fanT.value,
                    contentAlpha = stackContentAlpha,
                    snapCenterX = snapCenterX,
                    snapCenterY = snapCenterY,
                    discSize = discSize,
                    plateColors = plateColors,
                    fullCover = fullCover,
                    centerRadiusFrac = centerRadiusFrac,
                    outerScale = outerScale,
                )
            }

            // FanOut 起即挂载（可 alpha=0）以便布局对齐，避免可滑瞬间才首次测量闪一下
            if (showBrowse && browseTracks.isNotEmpty()) {
                key(sessionAnchor) {
                    VinylSongPickBrowseRow(
                        tracks = browseTracks,
                        initialLocalIndex = browseFocusLocal,
                        snapCenterX = snapCenterX,
                        snapCenterY = snapCenterY,
                        discSize = discSize,
                        plateColors = plateColors,
                        fullCover = fullCover,
                        centerRadiusFrac = centerRadiusFrac,
                        outerScale = outerScale,
                        contentAlpha = browseContentAlpha.coerceAtLeast(
                            if (phase == VinylSongPickPhase.FanOut) 0.001f else 0f,
                        ),
                        exitProgress = exitProgress,
                        keepFocusedOpaque = false,
                        hideFocusedDisc = (isConfirming || isCanceling) && exitProgress > 0.5f,
                        ringAlpha = browseRingAlpha,
                        focusedLocal = browseFocusLocal,
                        interactive = phase == VinylSongPickPhase.Browsing,
                        onFocusedLocalChange = { local ->
                            val track = browseTracks.getOrNull(local) ?: return@VinylSongPickBrowseRow
                            val qi = browseTracks.indexOfFirst { it.id == track.id }
                            if (qi >= 0) onFocusedIndexChange(qi)
                        },
                        onConfirmLocal = { local ->
                            val track = browseTracks.getOrNull(local) ?: return@VinylSongPickBrowseRow
                            val qi = browseTracks.indexOfFirst { it.id == track.id }
                            if (qi >= 0) {
                                onFocusedIndexChange(qi)
                                onConfirmFocused()
                            }
                        },
                    )
                }
            }

            // 确认/取消交接盘：盖住主黑胶直到雾气末段再淡出
            if (showHandoffDisc) {
                val handoff = if (isConfirming) {
                    browseTracks.getOrNull(browseFocusLocal)
                        ?: sessionQueue.getOrNull(safeIndex)
                } else {
                    // 取消：交回打开时的当前曲（主黑胶未换）
                    sessionQueue.getOrNull(safeIndex)
                }
                if (handoff != null) {
                    val handoffAlpha = when {
                        fogAlpha >= 0.22f -> 1f
                        else -> (fogAlpha / 0.22f).coerceIn(0f, 1f)
                    }
                    PickVinylDisc(
                        track = handoff,
                        plateColors = plateColors,
                        showCover = true,
                        lite = false,
                        fullCover = fullCover,
                        centerRadiusFrac = centerRadiusFrac,
                        outerScale = outerScale,
                        modifier = Modifier
                            .offset(
                                x = snapCenterX - discSize / 2,
                                y = snapCenterY - discSize / 2,
                            )
                            .size(discSize)
                            .zIndex(20f)
                            .graphicsLayer { alpha = handoffAlpha },
                    )
                }
            }
        }

        if (chromeVisible && chromeAlpha > 0.04f) {
            val focusedTrack = browseTracks.getOrNull(browseFocusLocal)
                ?: sessionQueue.getOrNull(safeIndex)
            Row(
                Modifier
                    .zIndex(3f)
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
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(18.dp)) {
                        val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round)
                        val cx = size.width * 0.58f
                        val cy = size.height / 2f
                        val arm = size.minDimension * 0.28f
                        drawLine(
                            color = Color.White.copy(alpha = 0.92f),
                            start = Offset(cx, cy - arm),
                            end = Offset(cx - arm * 1.15f, cy),
                            strokeWidth = stroke.width,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.92f),
                            start = Offset(cx - arm * 1.15f, cy),
                            end = Offset(cx, cy + arm),
                            strokeWidth = stroke.width,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                val title = focusedTrack?.name.orEmpty()
                if (title.isNotBlank()) {
                    val scale = uiScale.coerceIn(
                        PlayerDisplayPrefs.UI_MIN,
                        PlayerDisplayPrefs.UI_MAX,
                    )
                    val nameSp = titleNameStyle.resolvedFontSizeSp(TitleStyleLine.Name) * scale
                    Text(
                        text = title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        style = TextStyle(
                            color = titleNameStyle.resolvedColorFor(TitleStyleLine.Name),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = nameSp.sp,
                            letterSpacing = 0.35.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
    plateColors: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
) {
    val density = LocalDensity.current
    val st = stackT.coerceIn(0f, 1f)
    val ft = fanT.coerceIn(0f, 1f)
    val tilt = TiltDeg * (1f - ft) * st

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false },
    ) {
        val halfScreen = maxWidth * 0.5f
        val budget = (halfScreen - discSize).coerceAtLeast(0.dp)
        val rightN = nextTracks.size
        val peek = if (rightN > 0) budget / rightN else 0.dp
        val stride = discSize + BrowseGap
        val originX = snapCenterX - discSize / 2
        val originY = snapCenterY - discSize / 2

        data class Slot(
            val track: TrackRow,
            val stackX: Dp,
            val rowLocal: Int,
            val z: Float,
            val coverInStack: Boolean,
            val isCurrent: Boolean,
            /** 仅散开阶段从中心滑出到左侧（堆叠期不显示） */
            val fanFromCenter: Boolean,
        )

        // 堆叠：当前 + 下层 next；散开：再把上一首从中心滑到左侧，避免浏览交接时左侧突兀弹出
        val slotsList = buildList {
            nextTracks.forEachIndexed { i, track ->
                val step = i + 1
                add(
                    Slot(
                        track = track,
                        stackX = originX + peek * step * st,
                        rowLocal = step,
                        z = (rightN - step).toFloat(),
                        coverInStack = false,
                        isCurrent = false,
                        fanFromCenter = false,
                    ),
                )
            }
            add(
                Slot(
                    track = current,
                    stackX = originX,
                    rowLocal = 0,
                    z = MaxNextStack + 1f,
                    coverInStack = true,
                    isCurrent = true,
                    fanFromCenter = false,
                ),
            )
            fanPrevTracks.asReversed().forEachIndexed { visualOrder, track ->
                val step = visualOrder + 1
                add(
                    Slot(
                        track = track,
                        stackX = originX,
                        rowLocal = -step,
                        z = MaxNextStack + 2f + step,
                        coverInStack = false,
                        isCurrent = false,
                        fanFromCenter = true,
                    ),
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                    alpha = contentAlpha
                    if (abs(tilt) > 0.05f) {
                        transformOrigin = TransformOrigin(
                            pivotFractionX = (snapCenterX / maxWidth).coerceIn(0.05f, 0.95f),
                            pivotFractionY = (snapCenterY / maxHeight).coerceIn(0.05f, 0.95f),
                        )
                        rotationY = tilt
                        cameraDistance = 18f * density.density
                    }
                },
        ) {
            slotsList.forEach { slot ->
                val rowX = originX + stride * slot.rowLocal
                val x = if (slot.fanFromCenter) {
                    lerpDp(originX, rowX, ft)
                } else {
                    lerpDp(slot.stackX, rowX, ft)
                }
                val showCover = slot.coverInStack || ft > 0.35f || slot.fanFromCenter
                val lite = !showCover
                val enterA = when {
                    slot.isCurrent -> 1f
                    slot.fanFromCenter -> ft
                    else -> 0.15f + 0.85f * st
                }
                if (enterA <= 0.01f) return@forEach
                PickVinylDisc(
                    track = slot.track,
                    plateColors = plateColors,
                    showCover = showCover,
                    lite = lite,
                    fullCover = fullCover,
                    centerRadiusFrac = centerRadiusFrac,
                    outerScale = outerScale,
                    modifier = Modifier
                        .offset(x = x, y = originY)
                        .size(discSize)
                        .zIndex(slot.z)
                        .graphicsLayer { alpha = enterA },
                )
            }
        }
    }
}

private fun lerpDp(a: Dp, b: Dp, t: Float): Dp =
    Dp(lerp(a.value, b.value, t.coerceIn(0f, 1f)))

/**
 * 浏览行：LazyRow + SnapPosition.Start + contentPadding。
 * 禁止松手后再 animateScrollBy / scrollTo 二次校正（那会连锁往后滚）。
 * 焦点只跟列表汇报，绝不反向驱动滚动。
 */
@Composable
private fun VinylSongPickBrowseRow(
    tracks: List<TrackRow>,
    initialLocalIndex: Int,
    snapCenterX: Dp,
    snapCenterY: Dp,
    discSize: Dp,
    plateColors: VinylPlateColors,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    contentAlpha: Float,
    exitProgress: Float,
    keepFocusedOpaque: Boolean,
    hideFocusedDisc: Boolean = false,
    ringAlpha: Float = 1f,
    focusedLocal: Int,
    interactive: Boolean,
    onFocusedLocalChange: (Int) -> Unit,
    onConfirmLocal: (Int) -> Unit,
) {
    val safeInitial = initialLocalIndex.coerceIn(0, tracks.lastIndex.coerceAtLeast(0))
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = safeInitial,
        initialFirstVisibleItemScrollOffset = 0,
    )
    // 已验证可用：Start + contentPadding；勿再用 beforeContentPadding / scrollBy 二次校正（会右偏连锁）
    val snapFling = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Start,
    )
    val scope = rememberCoroutineScope()
    val onFocusedUpdated by rememberUpdatedState(onFocusedLocalChange)
    val confirmUpdated by rememberUpdatedState(onConfirmLocal)
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha },
    ) {
        val density = LocalDensity.current
        val startPad = (snapCenterX - discSize / 2).coerceAtLeast(0.dp)
        val endPad = (maxWidth - snapCenterX - discSize / 2).coerceAtLeast(0.dp)
        val rowTop = snapCenterY - discSize / 2
        val startPadPx = with(density) { startPad.roundToPx() }

        // 描边仅在可滑动浏览后由 ringAlpha 动画描入
        if (ringAlpha > 0.01f) {
            Box(
                Modifier
                    .offset(x = startPad, y = rowTop)
                    .size(discSize)
                    .graphicsLayer {
                        alpha = ringAlpha * (1f - exitProgress).coerceIn(0f, 1f) * 0.95f
                    }
                    .border(
                        width = 2.dp,
                        color = FogAccent.copy(alpha = 0.65f),
                        shape = CircleShape,
                    ),
            )
        }

        // 滑动过程中也更新焦点（歌名跟滑）；绝不因此反向驱动滚动
        LaunchedEffect(listState, tracks.size, startPadPx) {
            snapshotFlow {
                val items = listState.layoutInfo.visibleItemsInfo
                if (items.isEmpty()) {
                    listState.firstVisibleItemIndex
                } else {
                    items.minBy { abs(it.offset - startPadPx) }.index
                }
            }
                .distinctUntilChanged()
                .collect { idx -> onFocusedUpdated(idx) }
        }

        fun scrollToIndex(index: Int) {
            val safe = index.coerceIn(0, tracks.lastIndex.coerceAtLeast(0))
            scrollJob?.cancel()
            scrollJob = scope.launch {
                listState.animateScrollToItem(safe)
                onFocusedUpdated(safe)
            }
        }

        val onDiscTap by rememberUpdatedState { index: Int ->
            val settled = !listState.isScrollInProgress &&
                scrollJob?.isActive != true &&
                listState.firstVisibleItemIndex == index &&
                abs(listState.firstVisibleItemScrollOffset) <= 2
            if (settled) {
                confirmUpdated(index)
            } else {
                scrollToIndex(index)
            }
        }

        LazyRow(
            state = listState,
            flingBehavior = snapFling,
            userScrollEnabled = interactive,
            contentPadding = PaddingValues(start = startPad, end = endPad),
            horizontalArrangement = Arrangement.spacedBy(BrowseGap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .offset(y = rowTop)
                .height(discSize),
        ) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                val isFocused = index == focusedLocal
                Box(
                    Modifier
                        .size(discSize)
                        .graphicsLayer {
                            val ep = exitProgress
                            when {
                                isFocused && hideFocusedDisc -> alpha = 0f
                                ep > 0f && isFocused && keepFocusedOpaque -> alpha = 1f
                                ep > 0f && isFocused -> {
                                    scaleX = 1f - 0.06f * ep
                                    scaleY = 1f - 0.06f * ep
                                    alpha = 1f - 0.2f * ep
                                }
                                ep > 0f -> {
                                    alpha = (1f - ep).coerceIn(0f, 1f)
                                    scaleX = 1f - 0.12f * ep
                                    scaleY = 1f - 0.12f * ep
                                }
                            }
                        }
                        .then(
                            if (interactive) {
                                Modifier.pointerInput(index) {
                                    detectTapGestures(onTap = { onDiscTap(index) })
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    PickVinylDisc(
                        track = track,
                        plateColors = plateColors,
                        showCover = true,
                        lite = false,
                        fullCover = fullCover,
                        centerRadiusFrac = centerRadiusFrac,
                        outerScale = outerScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickVinylDisc(
    track: TrackRow,
    plateColors: VinylPlateColors,
    showCover: Boolean,
    lite: Boolean,
    fullCover: Boolean,
    centerRadiusFrac: Float,
    outerScale: Float,
    modifier: Modifier = Modifier,
) {
    if (lite || !showCover) {
        Box(
            modifier.clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            VinylDiscPlate(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val o = outerScale.coerceIn(0.5f, 1.6f)
                        scaleX = o
                        scaleY = o
                        transformOrigin = TransformOrigin.Center
                        clip = false
                    },
                colors = plateColors,
            )
        }
    } else {
        // 与主播放黑胶同一套盘面（完整封面 / 中心孔 / 外圈倍率）
        VinylDiscFace(
            track = track,
            spinDeg = 0f,
            spinning = false,
            fullCover = fullCover,
            centerRadiusFrac = centerRadiusFrac,
            outerScale = outerScale,
            plateColors = plateColors,
            animateStyleChanges = false,
            modifier = modifier,
        )
    }
}
