@file:Suppress("UnusedBoxWithConstraintsScope")

package com.kite.zmusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateSet
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LrcParser
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.karaokeWords
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PlayerDisplayPrefsStore
import com.kite.zmusic.data.PlaylistTrackLoader
import com.kite.zmusic.data.TitleAlignMode
import com.kite.zmusic.data.TitleLineStyle
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.VinylColorStyle
import com.kite.zmusic.playback.AudioSpectrumBands
import com.kite.zmusic.playback.PlaybackNotice
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.playback.mergePlaylistQueue
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.notice.showIslandNotice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.unit.lerp as lerpDp


@Composable
internal fun LandscapeProjectionLyrics(
    lines: List<LrcLine>,
    positionMs: Long,
    trackDurationMs: Long,
    lineSpacingDp: Float = 10f,
    playedCount: Int = 2,
    upcomingCount: Int = 2,
    offsetXDp: Float = 0f,
    dynamicLyrics: Boolean = false,
    vinylLeftInset: Dp = 0.dp,
    onSeekToMs: (Long) -> Unit,
    /** 外部点击等：递增后立即退出浏览并滚回播放行 */
    resumeScrollToken: Int = 0,
    /** 选句进度 0..1：原列表水平滑入挖孔 + 样式消解 */
    selectProgress: Float = 0f,
    selectGeom: LyricSelectGeom? = null,
    /** 歌词列左缘相对播放页根（与 geom 同一坐标系） */
    lyricsColStartDp: Dp = 0.dp,
    selectedIndices: Set<Int> = emptySet(),
    onToggleSelect: ((Int) -> Unit)? = null,
    onLongPressLine: ((index: Int) -> Unit)? = null,
    onBandCenterPx: ((cx: Float, cy: Float) -> Unit)? = null,
    /** 选句时歌词区附着与操作区相同的磨砂；取消后移除 */
    selectHazeState: HazeState? = null,
    /** 选句是否处于打开意图（区别于 progress，用于玻璃入/出时机） */
    selectOpen: Boolean = false,
    playingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    playedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
    /** 可交互歌词 band 的布局坐标（相对播放页根），供样式克隆开场对齐 */
    onLyricBandCoords: ((LayoutCoordinates) -> Unit)? = null,
    /** 为 true 时禁止跟滚/回中，避免歌词样式开场瞬间「绝对居中」跳一下 */
    scrollFrozen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                style = TextStyle(
                    color = LyricDim.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        return
    }
    val timing = lyricAnimTiming(lines, positionMs, trackDurationMs)
    val animActive = lyricAnimActiveIndex(lines, positionMs, trackDurationMs)
    val playFocus = lyricFocusIndex(lines, animActive)
    val live = lyricIsLive(lines, animActive, playFocus)
    val playFs = playingStyle.sanitizedFontScale()
    val playedFs = playedStyle.sanitizedFontScale()
    val unplayedFs = unplayedStyle.sanitizedFontScale()
    val linePad = lineSpacingDp
        .coerceIn(PlayerDisplayPrefs.LINE_SPACING_MIN, PlayerDisplayPrefs.LINE_SPACING_MAX)
        .dp
    val played = playedCount.coerceIn(
        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
        PlayerDisplayPrefs.LYRIC_AROUND_MAX,
    )
    val upcoming = upcomingCount.coerceIn(
        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
        PlayerDisplayPrefs.LYRIC_AROUND_MAX,
    )
    val visibleCount = played + 1 + upcoming
    val animMs = timing.durationMs

    val dynamicT = remember { Animatable(if (dynamicLyrics) 1f else 0f) }
    LaunchedEffect(dynamicLyrics) {
        val target = if (dynamicLyrics) 1f else 0f
        val distance = abs(target - dynamicT.value).coerceIn(0f, 1f)
        val durationMs = (480f * distance).toInt().coerceIn(160, 480)
        dynamicT.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f),
            ),
        )
    }

    val listState = rememberLazyListState()
    var browsing by remember { mutableStateOf(false) }
    /** 首滑手势进行中：保持手势 overlay，避免 browsing=true 后中途卸掉导致首次滚动失败 */
    var dragSession by remember { mutableStateOf(false) }
    var idleGen by remember { mutableIntStateOf(0) }
    var suppressBrowseDetect by remember { mutableStateOf(false) }
    /** 选句退出回滚中：抑制浏览高亮与跟滚抢位 */
    var resumeSettling by remember { mutableStateOf(false) }
    /** 选句打开时冻结的播放行，避免播放推进触发入场动画 */
    var selectFrozenFocus by remember { mutableIntStateOf(-1) }
    /** 递增以作废进行中的跟滚，避免与用户拖动手势抢滚动 */
    var followGen by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val onLongPressLineUpdated by rememberUpdatedState(onLongPressLine)
    val onBandCenterPxUpdated by rememberUpdatedState(onBandCenterPx)
    val onLyricBandCoordsUpdated by rememberUpdatedState(onLyricBandCoords)
    val selectProgressUpdated by rememberUpdatedState(selectProgress)
    val playFocusUpdated by rememberUpdatedState(playFocus)
    val selectT = selectProgress.coerceIn(0f, 1f)
    val selectMode = selectT > 0.001f
    val selectInteractive = selectT > 0.85f
    /**
     * 长按打开选句后，同一手指抬起可能被重组后的 combinedClickable 当成 onClick，
     * 从而误选一行；等全部触点抬起后再允许点选。
     */
    var selectToggleArmed by remember { mutableStateOf(false) }
    // 歌词玻璃：进入晚起（等滑移一段），退出尽快消掉（避免直角雾块滞留）
    val lyricGlassAlpha = remember { Animatable(0f) }
    LaunchedEffect(selectOpen) {
        if (selectOpen) {
            selectToggleArmed = false
            selectFrozenFocus = playFocus
            delay(210)
            lyricGlassAlpha.animateTo(
                1f,
                tween(durationMillis = 280, easing = LyricSoftEasing),
            )
        } else {
            selectToggleArmed = false
            // 退出不 snap：显示 alpha 改跟 selectT，与操作区同步收
            if (selectT <= 0.001f) {
                lyricGlassAlpha.snapTo(0f)
            }
        }
    }
    LaunchedEffect(selectOpen, selectT) {
        if (!selectOpen && selectT <= 0.001f) {
            lyricGlassAlpha.snapTo(0f)
        }
    }
    LaunchedEffect(selectMode) {
        if (selectMode) {
            followGen++
            browsing = true
            dragSession = false
            if (selectFrozenFocus < 0) selectFrozenFocus = playFocus
        } else {
            selectFrozenFocus = -1
        }
    }
    // 选句期间视觉锁定打开时的播放行；回滚目标仍用实时 playFocus
    val visualPlayFocus =
        if (selectMode && selectFrozenFocus >= 0) selectFrozenFocus else playFocus

    // 槽高取各角色字号下的最大行高，保证滚动居中稳定
    val slotHeight = maxOf(38f * playFs, 26f * playedFs, 26f * unplayedFs).dp + linePad * 2
    val bandHeight = slotHeight * visibleCount
    val haloBleed = 96.dp
    // 上下垫白，使任意一行（含仅 1～2 行）都能滚到视口绝对垂直中心
    val slotHeightPx = with(density) { slotHeight.roundToPx() }
    val bandHeightPx = with(density) { bandHeight.roundToPx() }
    val centerPadPx = ((bandHeightPx - slotHeightPx) / 2).coerceAtLeast(0)
    val centerPad = with(density) { centerPadPx.toDp() }

    val browseCenterIndex by remember {
        derivedStateOf { listState.browseCenterLyricIndex(playFocus) }
    }

    val span = lyricLineSpanMs(lines, playFocus, trackDurationMs)
    val enableHalo = live && span >= 320L
    val lineStart = lines.getOrNull(playFocus)?.timeMs ?: 0L
    val rawProgress = if (enableHalo) {
        ((positionMs - lineStart).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val targetHalo = if (enableHalo) {
        lyricHaloStrength(rawProgress, span)
    } else {
        0f
    }
    val halo by animateFloatAsState(
        targetValue = targetHalo,
        animationSpec = tween(
            durationMillis = if (targetHalo > 0.02f) 520 else 420,
            easing = LyricSoftEasing,
        ),
        label = "landLyricHalo",
    )
    val breath = rememberInfiniteTransition(label = "lyricHaloBreath")
    val breathAmp by breath.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LyricSoftEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lyricHaloBreathAmp",
    )
    val drawnHalo = if (halo > 0.01f) halo * (0.94f + 0.06f * breathAmp) else 0f

    suspend fun scrollToCenteredIndex(
        index: Int,
        animated: Boolean,
        /** 松手轻对齐用稍短缓动；跟滚/回播放行用稍柔和的过渡 */
        softSnap: Boolean = false,
        /** 选句退出：快速柔和回正，禁止先顶对齐再下移 */
        resumeSoft: Boolean = false,
    ) {
        val gen = followGen
        if (resumeSoft && animated) {
            suppressBrowseDetect = true
            try {
                val target = index.coerceIn(0, lines.lastIndex)
                fun deltaToTarget(): Float? {
                    val info = listState.layoutInfo
                    val visible = info.visibleItemsInfo
                    if (visible.isEmpty()) return null
                    val viewportCenter =
                        (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val item = visible.firstOrNull { it.index == target }
                    return if (item != null) {
                        (item.offset + item.size / 2f) - viewportCenter
                    } else {
                        val step = slotHeightPx.toFloat().coerceAtLeast(1f)
                        val anchor = visible.minByOrNull { abs(it.index - target) }
                            ?: visible.first()
                        val approxCenter =
                            anchor.offset + anchor.size / 2f + (target - anchor.index) * step
                        approxCenter - viewportCenter
                    }
                }
                suspend fun jumpToCentered() {
                    val info = listState.layoutInfo
                    val vs = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
                    val visibleSize = info.visibleItemsInfo.firstOrNull { it.index == target }?.size
                    val size = (visibleSize ?: slotHeightPx).coerceIn(1, vs)
                    val offset = ((vs - size) / 2).coerceAtLeast(0)
                    listState.scrollToItem(target, scrollOffset = offset)
                }
                // 等选句 morph 基本结束，行高稳定后再一次滚到位，杜绝「冲到上方再折返」
                var waitFrames = 0
                while (selectProgressUpdated > 0.04f && waitFrames++ < 90) {
                    if (followGen != gen) return
                    withFrameMillis { }
                }
                if (followGen != gen) return

                var delta = deltaToTarget()
                if (delta == null ||
                    listState.layoutInfo.visibleItemsInfo.none { it.index == target }
                ) {
                    jumpToCentered()
                    withFrameMillis { }
                    if (followGen != gen) return
                    delta = deltaToTarget()
                }
                if (delta != null && abs(delta) > 1.5f) {
                    listState.animateScrollBy(
                        delta,
                        animationSpec = lyricResumeScrollSpec(delta),
                    )
                    if (followGen != gen) return
                    withFrameMillis { }
                    val fine = deltaToTarget()
                    if (fine != null && abs(fine) > 1.5f) {
                        listState.scrollBy(fine)
                    }
                }
            } finally {
                suppressBrowseDetect = false
            }
            return
        }

        listState.scrollLyricToCenteredIndex(
            index = index,
            lastIndex = lines.lastIndex,
            slotHeightPx = slotHeightPx,
            animated = animated,
            softSnap = softSnap,
            followGen = gen,
            currentFollowGen = { followGen },
            setSuppressBrowseDetect = { suppressBrowseDetect = it },
        )
    }

    suspend fun scrollToPlayFocus(animated: Boolean, resumeSoft: Boolean = false) {
        scrollToCenteredIndex(playFocus, animated, resumeSoft = resumeSoft)
    }

    /** 仅当偏离中心较多时做一次轻对齐；接近则不动，去掉强磁吸感 */
    suspend fun snapToFullLines() {
        val gen = followGen
        listState.snapLyricToFullLines(
            slotHeightPx = slotHeightPx,
            lastIndex = lines.lastIndex,
            followGen = gen,
            currentFollowGen = { followGen },
            setSuppressBrowseDetect = { suppressBrowseDetect = it },
        )
    }

    fun exitBrowseAndFollow(animated: Boolean = true) {
        dragSession = false
        browsing = false
        scope.launch { scrollToPlayFocus(animated) }
    }

    /** 选句退出：等 morph 稳定后一趟回正，再退浏览态 */
    suspend fun resumeFromSelectToPlayFocus() {
        dragSession = false
        followGen++
        val gen = followGen
        resumeSettling = true
        try {
            scrollToPlayFocus(animated = true, resumeSoft = true)
        } finally {
            if (followGen == gen) {
                browsing = false
                resumeSettling = false
            }
        }
    }

    LaunchedEffect(listState, scrollFrozen) {
        if (scrollFrozen) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { inProgress ->
                if (suppressBrowseDetect) return@collect
                if (inProgress) {
                    if (!dragSession) browsing = true
                } else if (browsing) {
                    idleGen++
                    // 不再每次松手强行磁吸；仅轻微对齐明显错位
                    snapToFullLines()
                }
            }
    }

    LaunchedEffect(playFocus, browsing, lines.size, centerPadPx, selectMode, scrollFrozen) {
        if (scrollFrozen) return@LaunchedEffect
        if (!browsing && !selectMode && !resumeSettling) {
            scrollToPlayFocus(animated = true)
        }
    }

    LaunchedEffect(lines, scrollFrozen) {
        if (scrollFrozen) return@LaunchedEffect
        if (selectMode) return@LaunchedEffect
        browsing = false
        scrollToPlayFocus(animated = false)
    }

    LaunchedEffect(browsing, idleGen, selectMode, scrollFrozen) {
        if (scrollFrozen) return@LaunchedEffect
        if (!browsing || selectMode || resumeSettling) return@LaunchedEffect
        delay(5_500)
        // 仍在滚则继续等，避免惯性滚动中途被拽回
        while (listState.isScrollInProgress) {
            delay(160)
        }
        delay(320)
        if (browsing && !selectMode && !resumeSettling && !listState.isScrollInProgress) {
            exitBrowseAndFollow(animated = true)
        }
    }

    LaunchedEffect(resumeScrollToken) {
        if (resumeScrollToken > 0) {
            resumeFromSelectToPlayFocus()
        }
    }

    // 选句进入：不滚动列表，仅水平滑入挖孔，避免丢焦点

    BoxWithConstraints(
        modifier.fillMaxSize(),
    ) {
        val colW = maxWidth
        val t = dynamicT.value
        val centerX = colW / 2 + offsetXDp.dp
        val leftLimit = lerpDp(0.dp, vinylLeftInset.coerceIn(0.dp, colW), t)
        val halfDyn = minOf(
            (centerX - leftLimit).coerceAtLeast(0.dp),
            (colW - centerX).coerceAtLeast(0.dp),
        )
        val dynW = (halfDyn * 2).coerceAtLeast(48.dp)
        val dynStart = centerX - dynW / 2
        val boxW = lerpDp(colW, dynW, t)
        val startX = lerpDp(offsetXDp.dp, dynStart, t)
        val geom = selectGeom
        val targetBandH = geom?.listHeight ?: bandHeight
        val targetListW = geom?.listWidth ?: maxOf(boxW, 48.dp)
        val morphBandH = lerpDp(bandHeight, targetBandH, selectT)
        // 槽高 / 中心垫保持不变，避免 LazyColumn 重排丢焦点
        val morphListW = lerpDp(boxW.coerceAtLeast(48.dp), targetListW, selectT)
        // 列内坐标：源中心（个性化 X）→ 挖孔水平中心
        val sourceCenterLocal = startX + boxW.coerceAtLeast(48.dp) / 2
        val targetCenterLocal = if (geom != null) {
            geom.listCenterX - lyricsColStartDp
        } else {
            sourceCenterLocal
        }
        val animCenterLocal = lerpDp(sourceCenterLocal, targetCenterLocal, selectT)
        val animLeft = animCenterLocal - morphListW / 2
        // Y：整块平移到挖孔垂直中心（列内居中 → 挖孔中心），不滚动列表
        val shiftYpx = if (geom != null) {
            with(density) { ((geom.listCenterY - maxHeight / 2) * selectT).toPx() }
        } else {
            0f
        }

        val playItem = listState.layoutInfo.visibleItemsInfo.find { it.index == playFocus }
        val haloCenterYRatio = if (playItem != null) {
            val mid = playItem.offset + playItem.size / 2f
            val vh = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)
                .coerceAtLeast(1)
            (mid / vh.toFloat()).coerceIn(0f, 1f)
        } else {
            0.5f
        }

        // 选句推进时收掉光晕外扩，最终高度 = 挖孔高，避免右侧玻璃贯穿全屏
        val bandHostH = morphBandH + haloBleed * 2 * (1f - selectT)
        Box(
            Modifier
                .height(bandHostH)
                .width(morphListW)
                .align(Alignment.CenterStart)
                .offset(x = animLeft)
                .graphicsLayer {
                    // 选句态裁在挖孔高度内，避免玻璃/触控垫上下溢出
                    clip = selectT > 0.35f
                    translationY = shiftYpx
                }
                .onGloballyPositioned { coords ->
                    if (selectT < 0.01f) {
                        val c = coords.positionInRoot()
                        onBandCenterPxUpdated?.invoke(
                            c.x + coords.size.width / 2f,
                            c.y + coords.size.height / 2f,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 选句：歌词孔内直角玻璃，与外壳挖孔贴合（同款样式，无圆角以免漏风）
            val glassA = if (selectOpen) lyricGlassAlpha.value else selectT
            if (glassA > 0.01f && selectHazeState != null) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .height(morphBandH)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = glassA }
                        .clip(RectangleShape)
                        .hazeEffect(state = selectHazeState, style = LyricSelectGlassStyle) {
                            blurRadius = 72.dp
                            noiseFactor = 0.10f
                        },
                )
            }
            if (drawnHalo > 0.01f && selectT < 0.98f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            clip = false
                            alpha = 1f - selectT
                        }
                        .drawBehind {
                            val bleedPx = haloBleed.toPx() * (1f - selectT)
                            val bandPx = morphBandH.toPx()
                            val cx = size.width / 2f
                            val cy = bleedPx + bandPx * haloCenterYRatio
                            val radius = size.maxDimension * (0.42f + 0.08f * drawnHalo)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        CyanSoft.copy(alpha = 0.11f * drawnHalo),
                                        Color.White.copy(alpha = 0.04f * drawnHalo),
                                        Color.Transparent,
                                    ),
                                    center = Offset(cx, cy),
                                    radius = radius,
                                ),
                                radius = radius,
                                center = Offset(cx, cy),
                            )
                        },
                )
            }

            // 歌词列尽量用满可用宽度，长句才能在列内折行；短句仍居中显示
            val maxLyricW = morphListW.coerceAtLeast(48.dp)
            // 正常态触控/列表宽收为 0.85，减少无歌词空白误触滚动；选句挖孔仍用满宽
            val interactiveLyricW = if (selectMode) maxLyricW else maxLyricW * 0.85f
            val followFling = ScrollableDefaults.flingBehavior()
            // 选句收掉上下触控垫，高度与挖孔一致
            val lyricTouchPad = 28.dp * (1f - selectT)
            val lyricTouchPadPx = with(density) { lyricTouchPad.roundToPx() }

            Box(
                Modifier
                    .height(morphBandH + lyricTouchPad * 2)
                    .width(interactiveLyricW)
                    .align(Alignment.Center)
                    // 打开选句后：等打开那次长按的手指抬起，再允许点选
                    .pointerInput(selectOpen) {
                        if (!selectOpen) return@pointerInput
                        awaitPointerEventScope {
                            if (currentEvent.changes.any { it.pressed }) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                        selectToggleArmed = true
                    }
                    // 跟滚态：拖动中只置 dragSession，松手再置 browsing。
                    // browsing 进 key，但不会在首滑中途重启 pointerInput。
                    .pointerInput(browsing, selectMode, lines.size, slotHeightPx, lyricTouchPadPx) {
                        if (browsing || selectMode) return@pointerInput
                        val touchSlop = viewConfiguration.touchSlop
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId = down.id
                            val start = down.position
                            var lastY = start.y
                            var dragging = false
                            var flingLaunched = false
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)

                            fun hitLyricIndex(localY: Float): Int? {
                                if (lines.isEmpty()) return null
                                val yInList = localY - lyricTouchPadPx
                                val visible = listState.layoutInfo.visibleItemsInfo
                                if (visible.isEmpty()) {
                                    // 跟滚动画空窗：回落到当前播放行，避免曲末长按失效
                                    return playFocusUpdated.coerceIn(0, lines.lastIndex)
                                }
                                visible.firstOrNull { info ->
                                    yInList >= info.offset && yInList < info.offset + info.size
                                }?.index?.let { return it }
                                // 曲首/曲末视口上下有 contentPadding 空白：命中最近一行，
                                // 避免「未播放不足 N 行」时长按落在空白区无法开选句
                                return visible.minByOrNull { info ->
                                    val cy = info.offset + info.size / 2f
                                    abs(cy - yInList)
                                }?.index
                            }

                            fun beginDrag(fromY: Float, change: PointerInputChange?) {
                                dragging = true
                                followGen++
                                // 拖动期间抑制 isScrollInProgress→browsing，避免中途重启本 pointerInput
                                suppressBrowseDetect = true
                                dragSession = true
                                change?.consume()
                                lastY = fromY
                            }

                            try {
                                var slopChange: PointerInputChange? = null
                                val race = withTimeoutOrNull(longPressTimeout) {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.find { it.id == pointerId }
                                            ?: return@withTimeoutOrNull "cancel"
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        if (!change.pressed) return@withTimeoutOrNull "up"
                                        if (change.isConsumed) return@withTimeoutOrNull "cancel"
                                        val dy = change.position.y - start.y
                                        val dx = change.position.x - start.x
                                        if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 0.75f) {
                                            slopChange = change
                                            return@withTimeoutOrNull "drag"
                                        }
                                    }
                                    @Suppress("UNREACHABLE_CODE")
                                    "cancel"
                                }

                                when (race) {
                                    null -> {
                                        val index = hitLyricIndex(start.y)
                                        if (index != null && onLongPressLineUpdated != null) {
                                            followGen++
                                            browsing = true
                                            dragSession = false
                                            onLongPressLineUpdated?.invoke(index)
                                            waitForUpOrCancellation()
                                            return@awaitEachGesture
                                        }
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.find { it.id == pointerId }
                                                ?: return@awaitEachGesture
                                            if (!change.pressed) return@awaitEachGesture
                                            val dy = change.position.y - start.y
                                            val dx = change.position.x - start.x
                                            if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 0.75f) {
                                                beginDrag(change.position.y, change)
                                                break
                                            }
                                        }
                                    }
                                    "drag" -> {
                                        beginDrag(
                                            slopChange?.position?.y ?: start.y,
                                            slopChange,
                                        )
                                    }
                                    else -> return@awaitEachGesture
                                }

                                while (dragging) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.find { it.id == pointerId }
                                        ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    val step = change.position.y - lastY
                                    lastY = change.position.y
                                    listState.dispatchRawDelta(-step)
                                    change.consume()
                                    if (!change.pressed) {
                                        val velocityY = tracker.calculateVelocity().y
                                        flingLaunched = true
                                        browsing = true
                                        suppressBrowseDetect = false
                                        scope.launch {
                                            try {
                                                listState.scroll {
                                                    with(followFling) {
                                                        performFling(-velocityY)
                                                    }
                                                }
                                            } finally {
                                                dragSession = false
                                                idleGen++
                                            }
                                        }
                                        break
                                    }
                                }
                            } finally {
                                if (dragSession && !flingLaunched) {
                                    if (dragging) {
                                        browsing = true
                                    }
                                    dragSession = false
                                    suppressBrowseDetect = false
                                    if (dragging) {
                                        idleGen++
                                        scope.launch { snapToFullLines() }
                                    }
                                }
                            }
                        }
                    }
                    // 垫区未滚列表时吞垂直滑，避免误触下滑退出；列表自己能消费时不会抢走
                    .consumeUnclaimedVerticalDrag(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .height(morphBandH)
                        .width(interactiveLyricW)
                        .onGloballyPositioned { coords ->
                            // 仅跟滚态上报可见歌词块（不含触控垫），样式克隆开场对齐此处
                            if (selectT < 0.01f) {
                                onLyricBandCoordsUpdated?.invoke(coords)
                            }
                        },
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RectangleShape),
                        contentPadding = PaddingValues(vertical = centerPad),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // 浏览 / 选句可滚；首滑由父级 pointerInput 抢跟滚
                        userScrollEnabled = (browsing || selectInteractive) && !dragSession,
                    ) {
                        itemsIndexed(
                            items = lines,
                            key = { index, line -> "${line.timeMs}_$index" },
                        ) { index, line ->
                            // 选句锁定打开时的播放行，避免播放推进换行入场；回滚仍跟实时 playFocus
                            val inSelect = selectT > 0.001f
                            val isPlayingLine = index == visualPlayFocus
                            val isBrowseCenter =
                                !inSelect &&
                                    browsing &&
                                    !resumeSettling &&
                                    index == browseCenterIndex &&
                                    !isPlayingLine
                            val selected = inSelect && index in selectedIndices
                            val rowHeight = if (geom != null) {
                                lerpDp(slotHeight, geom.cellHeight, selectT)
                            } else {
                                slotHeight
                            }
                            LandscapeScrollLyricLine(
                                text = line.text,
                                lineKey = index,
                                isPlayingLine = isPlayingLine,
                                isBrowseCenter = isBrowseCenter,
                                live = live && isPlayingLine && selectT < 0.98f,
                                // 选句中仍保留已播放判定，斜体随 selectT 过渡，禁止进/出瞬间掐掉
                                played = animActive >= 0 && index < animActive,
                                distanceFromPlay = if (inSelect) {
                                    1
                                } else {
                                    abs(index - visualPlayFocus)
                                },
                                lines = lines,
                                focus = visualPlayFocus,
                                trackDurationMs = trackDurationMs,
                                lineSpacing = 0.dp,
                                slotHeight = rowHeight,
                                animMs = animMs,
                                browsing = browsing || selectMode,
                                selected = selected,
                                selectStyleT = selectT,
                                fixedSelectRow = selectT > 0.15f,
                                freezeLineTransitions = inSelect || resumeSettling,
                                instantAppear = resumeSettling && index == playFocus,
                                positionMs = positionMs,
                                playingStyle = playingStyle,
                                playedStyle = playedStyle,
                                unplayedStyle = unplayedStyle,
                                onSeekClick = when {
                                    selectInteractive && selectToggleArmed -> {
                                        { onToggleSelect?.invoke(index) }
                                    }
                                    selectInteractive -> null
                                    !browsing -> null
                                    else -> {
                                        {
                                            val i = index
                                            browsing = false
                                            dragSession = false
                                            scope.launch {
                                                scrollToCenteredIndex(i, animated = true)
                                            }
                                            onSeekToMs(line.timeMs)
                                        }
                                    }
                                },
                                // 跟滚态长按由父级 pointerInput 统一命中；浏览态仍走行内长按
                                onLongPress = if (selectMode || !browsing || onLongPressLine == null) {
                                    null
                                } else {
                                    {
                                        followGen++
                                        dragSession = false
                                        onLongPressLineUpdated?.invoke(index)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal val LyricSelectSelectedTextFallback = Color(0xFFFFFFFF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LandscapeScrollLyricLine(
    text: String,
    lineKey: Int,
    isPlayingLine: Boolean,
    isBrowseCenter: Boolean,
    live: Boolean,
    played: Boolean,
    distanceFromPlay: Int,
    lines: List<LrcLine>,
    focus: Int,
    trackDurationMs: Long,
    lineSpacing: Dp,
    slotHeight: Dp,
    animMs: Int,
    browsing: Boolean,
    selected: Boolean = false,
    selectStyleT: Float = 0f,
    /** 选句行：固定行高，选中底铺满整格，相邻选中拼成连续矩形 */
    fixedSelectRow: Boolean = false,
    freezeLineTransitions: Boolean = false,
    instantAppear: Boolean = false,
    positionMs: Long = 0L,
    playingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    playedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
    onSeekClick: (() -> Unit)?,
    onLongPress: (() -> Unit)? = null,
) {
    val ix = remember { MutableInteractionSource() }
    val st = selectStyleT.coerceIn(0f, 1f)
    val playFs = playingStyle.sanitizedFontScale()
    val playedFs = playedStyle.sanitizedFontScale()
    val unplayedFs = unplayedStyle.sanitizedFontScale()
    val sideFs = if (played) playedFs else unplayedFs
    val selectUnplayed = unplayedStyle.resolvedColorFor(LyricStyleRole.Unplayed)
    val selectSelectedText = playingStyle.resolvedColorFor(LyricStyleRole.Playing)
        .takeIf { it.alpha > 0.01f }
        ?: LyricSelectSelectedTextFallback
    val selectSelectedBg = lerp(selectUnplayed, Color.White, 0.22f).copy(alpha = 0.22f)
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (fixedSelectRow) {
                    Modifier.height(slotHeight)
                } else {
                    Modifier
                        .heightIn(min = slotHeight)
                        .wrapContentHeight()
                },
            )
            .then(
                if (selected && st > 0.5f) {
                    Modifier.background(selectSelectedBg)
                } else {
                    Modifier
                },
            )
            .then(
                when {
                    onLongPress != null && onSeekClick != null -> {
                        Modifier.combinedClickable(
                            interactionSource = ix,
                            indication = null,
                            onClick = onSeekClick,
                            onLongClick = onLongPress,
                        )
                    }
                    onLongPress != null -> {
                        Modifier.combinedClickable(
                            interactionSource = ix,
                            indication = null,
                            onClick = {},
                            onLongClick = onLongPress,
                        )
                    }
                    onSeekClick != null -> {
                        Modifier.clickable(
                            interactionSource = ix,
                            indication = null,
                            onClick = onSeekClick,
                        )
                    }
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        @Composable
        fun SelectStaticText(alpha: Float) {
            val baseAlpha = lerp(0.46f, 0.50f, st)
            val selectFs = if (selected) playFs else unplayedFs
            Text(
                text = text,
                style = TextStyle(
                    color = if (selected) {
                        selectSelectedText.copy(alpha = 0.96f * alpha)
                    } else {
                        selectUnplayed.copy(alpha = baseAlpha * alpha)
                    },
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (selected) {
                        playingStyle.resolvedFontWeight(LyricStyleRole.Playing)
                    } else {
                        unplayedStyle.resolvedFontWeight(LyricStyleRole.Unplayed)
                    },
                    fontStyle = if (selected) {
                        playingStyle.resolvedFontStyle()
                    } else {
                        unplayedStyle.resolvedFontStyle()
                    },
                    fontSize = (16.5f * selectFs).sp,
                    lineHeight = (26f * selectFs).sp,
                    letterSpacing = 0.35.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
            )
        }

        // 播放行：保持 Center 挂载，字号/透明度随 selectT 过渡
        if (isPlayingLine) {
            LandscapeCenterLyricLine(
                lines = lines,
                focus = focus,
                live = live,
                trackDurationMs = trackDurationMs,
                animMs = animMs.coerceAtLeast(280),
                compact = true,
                lineSpacing = lineSpacing,
                fillWidth = true,
                instantAppear = instantAppear,
                freezeTransitions = freezeLineTransitions,
                selectMorphT = st,
                selectSelected = selected,
                playingStyle = playingStyle,
                unplayedStyle = unplayedStyle,
                positionMs = positionMs,
            )
            return@Box
        }

        val visualMode = when {
            isBrowseCenter -> 1
            else -> 2
        }
        @Composable
        fun LyricBody(mode: Int) {
            when (mode) {
                1 -> {
                    Text(
                        text = text,
                        style = TextStyle(
                            color = LyricBrowseSelect.copy(alpha = 0.88f),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (16.5f * sideFs).sp,
                            lineHeight = (26f * sideFs).sp,
                            letterSpacing = 0.35.sp,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 4,
                        softWrap = true,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = lineSpacing, horizontal = 10.dp),
                    )
                }
                else -> {
                    LandscapeSideLyricLine(
                        text = text,
                        lineKey = lineKey,
                        played = played,
                        distance = distanceFromPlay.coerceAtMost(3).coerceAtLeast(1),
                        animMs = animMs.coerceAtLeast(240),
                        verticalPadding = lineSpacing,
                        fillWidth = true,
                        selectMorphT = st,
                        playedStyle = playedStyle,
                        unplayedStyle = unplayedStyle,
                    )
                }
            }
        }

        // 侧句：选句 progress 上做交叉淡化，避免硬切未播放样式
        if (st > 0.001f) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (st < 0.995f) {
                    Box(Modifier.graphicsLayer { alpha = 1f - st }) {
                        if (browsing) {
                            LyricBody(visualMode)
                        } else {
                            AnimatedContent(
                                targetState = visualMode,
                                transitionSpec = {
                                    (
                                        fadeIn(tween(260, easing = LyricSoftEasing)) +
                                            slideInVertically(tween(260, easing = LyricSoftEasing)) { it / 5 }
                                        ) togetherWith (
                                        fadeOut(tween(180, easing = LyricSofterEasing)) +
                                            slideOutVertically(tween(180, easing = LyricSofterEasing)) { -it / 6 }
                                        ) using SizeTransform(clip = false)
                                },
                                label = "landLyricVisual",
                            ) { mode -> LyricBody(mode) }
                        }
                    }
                }
                Box(Modifier.graphicsLayer { alpha = st }) {
                    SelectStaticText(alpha = 1f)
                }
            }
            return@Box
        }

        if (browsing) {
            LyricBody(visualMode)
        } else {
            AnimatedContent(
                targetState = visualMode,
                transitionSpec = {
                    (
                        fadeIn(tween(260, easing = LyricSoftEasing)) +
                            slideInVertically(tween(260, easing = LyricSoftEasing)) { it / 5 }
                        ) togetherWith (
                        fadeOut(tween(180, easing = LyricSofterEasing)) +
                            slideOutVertically(tween(180, easing = LyricSofterEasing)) { -it / 6 }
                        ) using SizeTransform(clip = false)
                },
                label = "landLyricVisual",
            ) { mode -> LyricBody(mode) }
        }
    }
}

@Composable
internal fun LandscapeCenterLyricLine(
    lines: List<LrcLine>,
    focus: Int,
    live: Boolean,
    trackDurationMs: Long,
    animMs: Int,
    compact: Boolean = false,
    lineSpacing: Dp = 10.dp,
    fillWidth: Boolean = true,
    instantAppear: Boolean = false,
    freezeTransitions: Boolean = false,
    /** 选句形态进度：字号/字重/透明度向未播放样式过渡 */
    selectMorphT: Float = 0f,
    selectSelected: Boolean = false,
    playingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
    positionMs: Long = 0L,
) {
    val emphasis by animateFloatAsState(
        targetValue = if (live) 1f else 0f,
        animationSpec = tween(
            durationMillis = (animMs * 1.25f).toInt().coerceIn(280, 520),
            easing = LyricSoftEasing,
        ),
        label = "landLyricEmphasis",
    )
    val st = selectMorphT.coerceIn(0f, 1f)
    val playFs = playingStyle.sanitizedFontScale()
    val unplayedFs = unplayedStyle.sanitizedFontScale()
    // 布局用固定字号；不做缩放强调，避免入场结束水平微跳
    val playFont = 26f * playFs
    val playLine = 38f * playFs
    val selectFont = 16.5f * unplayedFs
    val selectLine = 26f * unplayedFs
    val baseFont = lerp(playFont, selectFont, st)
    val baseLine = lerp(playLine, selectLine, st)
    val playAlpha = 0.58f + 0.42f * emphasis * (1f - st)
    val selectAlpha = if (selectSelected) 0.96f else 0.50f
    val textAlpha = lerp(playAlpha, selectAlpha, st)
    val playingColor = playingStyle.resolvedColorFor(LyricStyleRole.Playing)
    val selectUnplayed = unplayedStyle.resolvedColorFor(LyricStyleRole.Unplayed)
    val selectSelectedText = playingColor
    val playColor = playingColor.copy(alpha = textAlpha)
    val selectColor = if (selectSelected) {
        selectSelectedText.copy(alpha = textAlpha)
    } else {
        selectUnplayed.copy(alpha = textAlpha)
    }
    val textColor = lerp(playColor, selectColor, st)
    val playWeight = playingStyle.resolvedFontWeight(LyricStyleRole.Playing)
    val selectWeight = if (selectSelected) {
        playingStyle.resolvedFontWeight(LyricStyleRole.Playing)
    } else {
        unplayedStyle.resolvedFontWeight(LyricStyleRole.Unplayed)
    }
    val weight = when {
        selectSelected && st > 0.45f -> selectWeight
        st > 0.55f -> selectWeight
        else -> playWeight
    }
    val playStyle = playingStyle.resolvedFontStyle()
    val selectStyle = if (selectSelected) {
        playingStyle.resolvedFontStyle()
    } else {
        unplayedStyle.resolvedFontStyle()
    }
    val fontStyle = if (st > 0.5f) selectStyle else playStyle
    val span = lyricLineSpanMs(lines, focus, trackDurationMs)
    val vPad = if (compact) lineSpacing else 10.dp

    Box(
        Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .padding(vertical = vPad, horizontal = 4.dp)
            .padding(vertical = if (compact) 0.dp else 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        StableCenterLyricText(
            focus = focus,
            text = lines.getOrNull(focus)?.text.orEmpty(),
            animMs = animMs,
            lineSpanMs = span,
            fillWidth = fillWidth,
            maxLines = if (st > 0.5f) 2 else 6,
            overflow = if (st > 0.5f) TextOverflow.Ellipsis else TextOverflow.Clip,
            instantAppear = instantAppear,
            freezeTransitions = freezeTransitions,
            words = lines.getOrNull(focus)?.karaokeWords(positionMs).orEmpty(),
            positionMs = positionMs,
            unplayedColor = selectUnplayed.copy(alpha = 0.46f),
            tracking = live && st < 0.5f,
            style = TextStyle(
                color = textColor,
                fontFamily = FontFamily.SansSerif,
                fontWeight = weight,
                fontStyle = fontStyle,
                fontSize = baseFont.sp,
                lineHeight = baseLine.sp,
                letterSpacing = lerp(0.5f, 0.35f, st).sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
internal fun LandscapeSideLyricLine(
    text: String,
    lineKey: Int,
    played: Boolean,
    distance: Int,
    animMs: Int,
    verticalPadding: Dp = 11.dp,
    fillWidth: Boolean = true,
    /** 选句进度：已播放斜体/字重随此值过渡到正体未播放样式 */
    selectMorphT: Float = 0f,
    playedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
) {
    val st = selectMorphT.coerceIn(0f, 1f)
    // 已播放视觉强度：1=完整已播放样式，0=选句未播放样式；随 selectT 连续变化
    val playedStrength = if (played) (1f - st) else 0f
    val unplayedAlpha = (0.46f - distance.coerceAtMost(2) * 0.06f)
    val targetAlpha = lerp(unplayedAlpha, 0.32f, playedStrength)
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = (animMs * 1.1f).toInt().coerceIn(240, 480),
            easing = LyricSoftEasing,
        ),
        label = "landSideA",
    )
    val playedScale = playedStyle.sanitizedFontScale()
    val unplayedScale = unplayedStyle.sanitizedFontScale()
    val unplayedSizeSp = lerp(16.5f, 15f, 0f) * unplayedScale
    val playedSizeSp = lerp(16.5f, 15f, 1f) * playedScale
    val padMod = Modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
        .padding(vertical = verticalPadding, horizontal = 10.dp)

    val unplayedColor = unplayedStyle.resolvedColorFor(LyricStyleRole.Unplayed)
    val playedColor = playedStyle.resolvedColorFor(LyricStyleRole.Played)
    val unplayedWeight = unplayedStyle.resolvedFontWeight(LyricStyleRole.Unplayed)
    val playedWeight = playedStyle.resolvedFontWeight(LyricStyleRole.Played)
    val unplayedFontStyle = unplayedStyle.resolvedFontStyle()
    val playedFontStyle = playedStyle.resolvedFontStyle()

    // 槽位固定后侧句文本会原地替换：用淡入淡出避免硬切
    Crossfade(
        targetState = lineKey to text,
        animationSpec = tween(
            durationMillis = animMs.coerceIn(200, 420),
            easing = LyricSoftEasing,
        ),
        label = "landSideCrossfade",
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
    ) { (_, shown) ->
        // 斜体无法插值：已播放层与未播放层按 playedStrength 交叉淡化
        Box(
            modifier = padMod,
            contentAlignment = Alignment.Center,
        ) {
            if (playedStrength < 0.995f) {
                Text(
                    text = shown,
                    style = TextStyle(
                        color = unplayedColor.copy(alpha = alpha * (1f - playedStrength)),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = unplayedWeight,
                        fontStyle = unplayedFontStyle,
                        fontSize = unplayedSizeSp.sp,
                        lineHeight = (26f * unplayedScale).sp,
                        letterSpacing = 0.35.sp,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 4,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
                )
            }
            if (playedStrength > 0.01f) {
                Text(
                    text = shown,
                    style = TextStyle(
                        color = playedColor.copy(alpha = alpha * playedStrength),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = playedWeight,
                        fontStyle = playedFontStyle,
                        fontSize = playedSizeSp.sp,
                        lineHeight = (26f * playedScale).sp,
                        letterSpacing = 0.35.sp,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 4,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
                )
            }
        }
    }
}

/**
 * 横屏标题信息块：歌名 / 制作人 / 歌单。
 * 每一行按自身宽度独立算目标 X，[Animatable] 同步滑动，切换对齐不再只有标题在动。
 */
@Composable
internal fun LandscapeAlignedSongMeta(
    track: TrackRow,
    sourceTitle: String?,
    onSourceClick: (() -> Unit)?,
    onArtistClick: (() -> Unit)? = null,
    onRevealControls: () -> Unit,
    titleAlign: TitleAlignMode,
    songMetaTopPad: Dp,
    titleOffsetYDp: Float,
    titleNameColor: Color,
    titleArtistColor: Color,
    titleSourceColor: Color,
    titleNameFontScale: Float,
    titleArtistFontScale: Float,
    titleSourceFontScale: Float,
    chromeSidePad: Dp,
    vinylCenterX: Dp,
    lyricsCenterX: Dp,
    screenCenterX: Dp,
    titleMaxWidth: Dp,
    contentAlpha: Float = 1f,
    onMetaVisualBoundsInRoot: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val srcIx = remember { MutableInteractionSource() }
    val artistIx = remember { MutableInteractionSource() }
    val onMetaBoundsUpdated by rememberUpdatedState(onMetaVisualBoundsInRoot)
    val lineBounds = remember { mutableStateMapOf<Int, Rect>() }
    val centerModes = titleAlign == TitleAlignMode.VINYL ||
        titleAlign == TitleAlignMode.CENTER ||
        titleAlign == TitleAlignMode.LYRICS
    val textAlign = if (centerModes) TextAlign.Center else TextAlign.Start
    val alpha = contentAlpha.coerceIn(0f, 1f)

    fun publishVisualUnion() {
        val rects = lineBounds.values
        if (rects.isEmpty()) return
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        rects.forEach { r ->
            left = minOf(left, r.left)
            top = minOf(top, r.top)
            right = maxOf(right, r.right)
            bottom = maxOf(bottom, r.bottom)
        }
        if (left.isFinite() && top.isFinite() && right > left && bottom > top) {
            onMetaBoundsUpdated?.invoke(Rect(left, top, right, bottom))
        }
    }

    fun reportLineBounds(index: Int, rect: Rect) {
        lineBounds[index] = rect
        publishVisualUnion()
    }

    fun targetXForWidth(widthPx: Float): Float {
        if (widthPx <= 0.5f) return 0f
        return with(density) {
            when (titleAlign) {
                TitleAlignMode.LEFT -> chromeSidePad.toPx()
                TitleAlignMode.VINYL -> vinylCenterX.toPx() - widthPx / 2f
                TitleAlignMode.CENTER -> screenCenterX.toPx() - widthPx / 2f
                TitleAlignMode.LYRICS -> lyricsCenterX.toPx() - widthPx / 2f
            }
        }
    }

    Box(
        modifier.graphicsLayer { this.alpha = alpha },
    ) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = songMetaTopPad)
                .offset(y = titleOffsetYDp.dp)
                .widthIn(max = titleMaxWidth)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            AlignedMetaLine(
                text = track.name,
                textAlign = textAlign,
                titleAlign = titleAlign,
                targetXForWidth = ::targetXForWidth,
                style = TextStyle(
                    color = titleNameColor,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (TitleLineStyle.BASE_NAME_SP * titleNameFontScale).sp,
                    letterSpacing = 0.35.sp,
                    textAlign = textAlign,
                ),
                maxLines = 2,
                onVisualBoundsInRoot = { reportLineBounds(0, it) },
            )
            Spacer(Modifier.height(5.dp))
            AlignedMetaLine(
                text = track.artists.uppercase(),
                textAlign = textAlign,
                titleAlign = titleAlign,
                targetXForWidth = ::targetXForWidth,
                style = TextStyle(
                    color = titleArtistColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (TitleLineStyle.BASE_ARTIST_SP * titleArtistFontScale).sp,
                    letterSpacing = 1.8.sp,
                    textAlign = textAlign,
                ),
                maxLines = 1,
                onVisualBoundsInRoot = { reportLineBounds(1, it) },
                modifier = Modifier.then(
                    if (onArtistClick != null) {
                        Modifier.clickable(
                            interactionSource = artistIx,
                            indication = null,
                            onClick = {
                                onRevealControls()
                                onArtistClick()
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
            )
            if (!sourceTitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                AlignedMetaLine(
                    text = sourceTitle,
                    textAlign = textAlign,
                    titleAlign = titleAlign,
                    targetXForWidth = ::targetXForWidth,
                    style = TextStyle(
                        color = titleSourceColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (TitleLineStyle.BASE_SOURCE_SP * titleSourceFontScale).sp,
                        letterSpacing = 0.55.sp,
                        textAlign = textAlign,
                    ),
                    maxLines = 1,
                    onVisualBoundsInRoot = { reportLineBounds(2, it) },
                    modifier = Modifier.then(
                        if (onSourceClick != null) {
                            Modifier.clickable(
                                interactionSource = srcIx,
                                indication = null,
                                onClick = {
                                    onRevealControls()
                                    onSourceClick()
                                },
                            )
                        } else {
                            Modifier
                        },
                    ),
                )
            }
        }
    }

    LaunchedEffect(sourceTitle.isNullOrBlank()) {
        if (sourceTitle.isNullOrBlank() && lineBounds.containsKey(2)) {
            lineBounds.remove(2)
            publishVisualUnion()
        }
    }
}

@Composable
internal fun AlignedMetaLine(
    text: String,
    textAlign: TextAlign,
    titleAlign: TitleAlignMode,
    targetXForWidth: (Float) -> Float,
    style: TextStyle,
    maxLines: Int,
    onVisualBoundsInRoot: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    val x = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = targetXForWidth(widthPx)
    val onBoundsUpdated by rememberUpdatedState(onVisualBoundsInRoot)

    LaunchedEffect(target, widthPx, titleAlign, text) {
        if (widthPx <= 0.5f) return@LaunchedEffect
        if (!placed) {
            x.snapTo(target)
            placed = true
        } else {
            x.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.86f,
                    stiffness = 280f,
                ),
            )
        }
    }

    Text(
        text = text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier
            .graphicsLayer { alpha = if (placed) 1f else 0f }
            .offset { IntOffset(x.value.roundToInt(), 0) }
            .wrapContentWidth(align = Alignment.Start)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .onGloballyPositioned { coords ->
                onBoundsUpdated?.invoke(coords.boundsInRoot())
            },
    )
}

/**
 * 右上角非阻塞通知：不可点击；入场自右淡入、出场淡出右移；
 * [chromeProgress] 升高时 Y 下移避让旋转锁/设置图标，可打断。
 */
@Composable
internal fun PlaybackCornerNotice(
    notice: PlaybackNotice?,
    chromeProgress: Float,
    topBase: Dp,
    endPad: Dp,
    modifier: Modifier = Modifier,
) {
    var displayed by remember { mutableStateOf<PlaybackNotice?>(null) }
    val density = LocalDensity.current
    val panelAlpha = remember { Animatable(0f) }
    val panelSlideX = remember { Animatable(0f) }
    val panelTopY = remember { Animatable(0f) }
    val noticeCurve = remember { CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f) }

    val iconAvoid = NowPlayingChromeIconHeight + 10.dp
    val targetTopDp = topBase + iconAvoid * chromeProgress.coerceIn(0f, 1f)

    LaunchedEffect(targetTopDp) {
        val y = with(density) { targetTopDp.toPx() }
        if (panelAlpha.value < 0.04f) {
            panelTopY.snapTo(y)
        } else {
            panelTopY.animateTo(
                y,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 320f),
            )
        }
    }

    LaunchedEffect(notice?.token) {
        val incoming = notice
        if (incoming != null) {
            displayed = incoming
            val fromX = with(density) { 28.dp.toPx() }
            panelSlideX.snapTo(fromX)
            panelAlpha.snapTo(0f)
            coroutineScope {
                launch {
                    panelAlpha.animateTo(
                        1f,
                        tween(300, easing = noticeCurve),
                    )
                }
                launch {
                    panelSlideX.animateTo(
                        0f,
                        tween(340, easing = noticeCurve),
                    )
                }
            }
        } else if (displayed != null) {
            val toX = with(density) { 18.dp.toPx() }
            coroutineScope {
                launch {
                    panelAlpha.animateTo(
                        0f,
                        tween(240, easing = LyricSofterEasing),
                    )
                }
                launch {
                    panelSlideX.animateTo(
                        toX,
                        tween(260, easing = LyricSofterEasing),
                    )
                }
            }
            displayed = null
        }
    }

    val shown = displayed
    if (shown == null && panelAlpha.value < 0.02f) return

    val msg = shown?.message ?: return
    Box(
        modifier
            .offset {
                IntOffset(
                    x = panelSlideX.value.roundToInt(),
                    y = panelTopY.value.roundToInt(),
                )
            }
            .padding(end = endPad)
            .graphicsLayer { alpha = panelAlpha.value },
    ) {
        Text(
            text = msg,
            style = TextStyle(
                color = Color(0xFFE8F0F8).copy(alpha = 0.92f),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.2.sp,
                shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 8f),
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE0121822))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 曲谱加宽进度只在此子树读取，避免 [LandscapePlayerBody] 每帧重组合。
 */
@Composable
internal fun BoxScope.ScoreCoverWidthHost(
    coverAnim: Animatable<Float, AnimationVector1D>,
    collapsedWidth: Dp,
    expandedWidth: Dp,
    endPad: Dp,
    content: @Composable (panelW: Float) -> Unit,
) {
    val coverT = coverAnim.value
    val outerWidth = lerpDp(collapsedWidth, expandedWidth, coverT)
    val sheetWidth = (outerWidth - endPad).coerceAtLeast(80.dp)
    BoxWithConstraints(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(sheetWidth),
    ) {
        content(constraints.maxWidth.toFloat().coerceAtLeast(1f))
    }
}

