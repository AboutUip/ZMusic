package com.kite.zmusic.ui.player

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.R
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerBackgroundPreset
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DynamicInk = Color(0xFF1C1C1E)
private val DynamicMeta = Color(0xFF8A8A8A)
private val CoverFrame = Color.White
private val CoverShape = RoundedCornerShape(8.dp)
private val CoverInnerShape = RoundedCornerShape(6.dp)

private const val LyricFanRadius = 4

private val CoverMotion = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
private const val CoverNextExitMs = 920
private const val CoverNextGrowMs = 520
private const val CoverPrevEnterMs = 820
private const val CoverUnderScale = 0.85f
private const val CoverFlingVelocity = 1400f
/** 扇形张角：邻句开头相对当前句的水平收拢，与文字朝向无关。 */
private const val LyricFanOpenDeg = 9.0f
private const val LyricFanXPull = 0.32f
private const val LyricColWidthFrac = 0.58f
/** 文字相对水平线的旋转（约为拆分前的一半）。 */
private const val LyricTextRotXDeg = 3.2f
private const val LyricTextRotZDeg = 1.95f
/** 整块歌词相对「末句开头对齐屏幕中线」再往右的固定步长。 */
private val LyricGroupNudgeRight = 36.dp
/** 歌名列比封面略宽，少把短句挤成两行。 */
private const val DynamicTitleWidthMul = 1.24f
/** 加宽后的歌名列和歌词扇形之间留出的空隙。 */
private val DynamicTitleLyricGap = 20.dp
/** 测最长句宽度用加粗主词，保证命中条盖住播放中那行。 */
private val DynamicLyricHitStyle = TextStyle(
    fontSize = 21.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 28.sp,
    letterSpacing = 0.12.sp,
)

/**
 * 横屏「动态」播放页：方封 + 封面虚化铺底 + 景深歌词。
 * 构图对齐 `dev/res/ywo.jpg`（封面必须 1:1）。
 */
@Composable
internal fun LandscapeDynamicStage(
    track: TrackRow,
    lines: List<LrcLine>,
    lyricCompanions: List<LrcLine?>,
    originalOnTop: Boolean,
    showCompanionOnOthers: Boolean,
    positionMs: Long,
    durationMs: Long,
    peekNext: TrackRow?,
    peekPrev: TrackRow?,
    /** 播放条等外部切歌方向；封面手势自己会先落定，避免再播一遍。 */
    skipDirection: VinylSkipDirection,
    gesturesEnabled: Boolean = true,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onArtistClick: (() -> Unit)?,
    transportRevealT: Float = 0f,
    transportReserve: Dp = 0.dp,
    /** 方封与歌名整列水平偏移（dp），负左正右。 */
    coverOffsetXDp: Float = 0f,
    /** 封面外层白框磨砂。1 为实心白框，0 为透出虚化封面。 */
    coverGlass: Float = 1f,
    /** 右侧歌词水平偏移（dp），负左正右。 */
    lyricOffsetXDp: Float = 0f,
    lyricPlayingStyle: LyricRoleStyle = LyricRoleStyle.DynamicPlayingDefault,
    lyricPlayedStyle: LyricRoleStyle = LyricRoleStyle.DynamicPlayedDefault,
    lyricUnplayedStyle: LyricRoleStyle = LyricRoleStyle.DynamicUnplayedDefault,
    customBg: PlayerBackgroundPreset? = null,
    customBgProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val cover = minOf(maxWidth * 0.202f, maxHeight * 0.362f) * 1.22f
        val leftPad = (maxWidth * 0.055f - 8.dp).coerceAtLeast(10.dp)
        val coverShiftX = coverOffsetXDp.coerceIn(
            PlayerDisplayPrefs.DYNAMIC_COVER_OFFSET_X_MIN,
            PlayerDisplayPrefs.DYNAMIC_COVER_OFFSET_X_MAX,
        ).dp
        val lyricsEndPad = maxWidth * 0.028f
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val lyricInset = 52.dp + navBottom
        val stageWidthPx = constraints.maxWidth
        val lyricsLeftPx = stageWidthPx * (1f - LyricColWidthFrac)
        val revealT = transportRevealT.coerceIn(0f, 1f)
        val liftPx = with(density) { (transportReserve.toPx() * revealT) / 2f }
        val coverExitX = with(density) {
            (leftPad + coverShiftX.coerceAtLeast(0.dp) + cover + 36.dp).toPx()
        }
        val coverExitY = with(density) {
            (maxHeight * 0.62f).toPx().coerceAtLeast((cover * 1.45f).toPx())
        }
        val coverEnterX = with(density) { (cover + leftPad + 12.dp).toPx() }
        val lyricLeft = maxWidth * (1f - LyricColWidthFrac) + lyricOffsetXDp.dp
        val titleRoom = lyricLeft - leftPad - coverShiftX - DynamicTitleLyricGap
        val titleWidth = if (titleRoom <= cover) {
            cover
        } else {
            (cover * DynamicTitleWidthMul).coerceAtMost(titleRoom)
        }
        val titleClearStagePx = with(density) {
            (leftPad + coverShiftX + titleWidth + DynamicTitleLyricGap).toPx()
        }
        val lyricOffsetPx = with(density) { lyricOffsetXDp.dp.toPx() }

        LandscapeDynamicStageBack(
            customBg = customBg,
            customBgProgress = customBgProgress,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                // 布局位移才会进锚点。画在 graphicsLayer 上时，飞层停在没抬起的位置，交接再跳。
                .offset { IntOffset(0, -liftPx.roundToInt()) },
        ) {
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = leftPad)
                .offset(x = coverShiftX)
                .width(titleWidth),
        ) {
            LandscapeDynamicSquareCover(
                track = track,
                peekNext = peekNext,
                peekPrev = peekPrev,
                glass = coverGlass,
                size = cover,
                exitXPx = coverExitX,
                exitYPx = coverExitY,
                enterXPx = coverEnterX,
                skipDirection = skipDirection,
                gesturesEnabled = gesturesEnabled,
                onSkipNext = onSkipNext,
                onSkipPrev = onSkipPrev,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = track.name.ifBlank { t("未知歌曲") },
                style = TextStyle(
                    color = DynamicInk,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 32.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .playerExpandAnchor(PlayerExpandSlot.FullTitle)
                    .playerExpandHideFull(),
            )
            val album = track.album?.trim().orEmpty()
            val artists = track.artists.trim()
            if (artists.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LandscapeDynamicMetaRow(
                    icon = ZIcons.MusicNote,
                    text = artists,
                    onClick = onArtistClick,
                    modifier = Modifier
                        .playerExpandAnchor(PlayerExpandSlot.FullArtist)
                        .playerExpandHideFull(),
                )
            }
            if (album.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                LandscapeDynamicMetaRow(
                    icon = ZIcons.Album,
                    text = album,
                    modifier = Modifier.playerExpandHideExtra(),
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = lyricOffsetXDp.dp)
                .fillMaxHeight()
                .fillMaxWidth(LyricColWidthFrac)
                .padding(
                    end = lyricsEndPad,
                    top = lyricInset,
                    bottom = lyricInset,
                )
                .graphicsLayer { clip = false },
        ) {
            LandscapeDynamicLyricsFan(
                lines = lines,
                companions = lyricCompanions,
                positionMs = positionMs,
                durationMs = durationMs,
                originalOnTop = originalOnTop,
                showCompanionOnOthers = showCompanionOnOthers,
                onSeek = onSeek,
                stageWidthPx = stageWidthPx.toFloat(),
                lyricsLeftPx = lyricsLeftPx,
                lyricOffsetPx = lyricOffsetPx,
                titleClearStagePx = titleClearStagePx,
                playingStyle = lyricPlayingStyle,
                playedStyle = lyricPlayedStyle,
                unplayedStyle = lyricUnplayedStyle,
                modifier = Modifier.fillMaxSize(),
            )
        }
        }
    }
}

@Composable
private fun LandscapeDynamicStageBack(
    customBg: PlayerBackgroundPreset?,
    customBgProgress: Float,
    modifier: Modifier = Modifier,
) {
    val expand = LocalPlayerExpand.current
    val backAlpha = when {
        expand == null || !expand.mounted -> 1f
        expand.pastHandoff -> 1f
        else -> expand.visualProgress.coerceIn(0f, 1f)
    }
    val customT = customBgProgress.coerceIn(0f, 1f)
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.img_dynamic_stage_back),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backAlpha * (1f - customT) },
        )
        if (customBg != null && customT > 0.001f) {
            PlayerCustomBackgroundLayer(
                preset = customBg,
                progress = backAlpha * customT,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private class ExitingDynamicCover(
    val key: Long,
    val track: TrackRow,
    val progress: Animatable<Float, AnimationVector1D>,
)

/**
 * 方形封面切歌：手势门槛与黑胶一致。
 * 下一首离场沿四分之一圆弧向左下退出；入场仍是底下放大。
 * 上一首入场仍从左侧滑入盖住当前封面。
 */
@Composable
private fun LandscapeDynamicSquareCover(
    track: TrackRow,
    peekNext: TrackRow?,
    peekPrev: TrackRow?,
    glass: Float,
    size: Dp,
    exitXPx: Float,
    exitYPx: Float,
    enterXPx: Float,
    skipDirection: VinylSkipDirection,
    gesturesEnabled: Boolean,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val exitX = exitXPx.coerceAtLeast(1f)
    val exitY = exitYPx.coerceAtLeast(1f)
    val slidePx = enterXPx.coerceAtLeast(1f)
    var topTrack by remember { mutableStateOf(track) }
    var underTrack by remember { mutableStateOf(track) }
    var scaleHold by remember { mutableFloatStateOf(Float.NaN) }
    var enterHold by remember { mutableFloatStateOf(Float.NaN) }
    var settledId by remember { mutableStateOf(track.id) }
    var showUnder by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragMode by remember { mutableStateOf<VinylSkipDirection?>(null) }
    var followX by remember { mutableFloatStateOf(0f) }
    var prevRevealBase by remember { mutableFloatStateOf(0f) }
    var booted by remember { mutableStateOf(false) }
    val pose = remember { Animatable(0f) }
    val enterX = remember { Animatable(0f) }
    val topScale = remember { Animatable(1f) }
    val underScale = remember { Animatable(CoverUnderScale) }
    val exiting = remember { mutableStateListOf<ExitingDynamicCover>() }
    var exitSeq by remember { mutableStateOf(0L) }
    val directionRef = rememberUpdatedState(skipDirection)
    val peekNextRef = rememberUpdatedState(peekNext)
    val peekPrevRef = rememberUpdatedState(peekPrev)
    val onNextRef = rememberUpdatedState(onSkipNext)
    val onPrevRef = rememberUpdatedState(onSkipPrev)
    val gesturesRef = rememberUpdatedState(gesturesEnabled)

    fun arcTOf(x: Float): Float {
        // 角度参数若跟手指线性走，水平位移开头约是手指的 1.57 倍。
        // 反解成水平位移等于手指位移，弧线形状保留，拖动时封面跟手。
        val travel = ((-x) / exitX).coerceIn(0f, 1f)
        if (travel <= 0f) return 0f
        val angle = asin(travel)
        return (angle / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)
    }

    fun spawnExit(outgoing: TrackRow, fromT: Float) {
        exitSeq += 1L
        val layer = ExitingDynamicCover(
            key = exitSeq,
            track = outgoing,
            progress = Animatable(fromT.coerceIn(0f, 1f)),
        )
        exiting.add(layer)
        while (exiting.size > 4) exiting.removeAt(0)
        scope.launch {
            try {
                val remain = (1f - layer.progress.value).coerceIn(0.05f, 1f)
                layer.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = (CoverNextExitMs * remain).toInt().coerceIn(280, CoverNextExitMs),
                        easing = CoverMotion,
                    ),
                )
            } finally {
                exiting.removeAll { it.key == layer.key }
            }
        }
    }

    fun promoteNext(incoming: TrackRow, startScale: Float) {
        val start = startScale.coerceIn(CoverUnderScale, 1f)
        topTrack = incoming
        underTrack = incoming
        settledId = incoming.id
        showUnder = false
        dragMode = null
        followX = 0f
        prevRevealBase = 0f
        scaleHold = start
        enterHold = Float.NaN
        scope.launch {
            pose.snapTo(0f)
            enterX.snapTo(0f)
            topScale.snapTo(start)
            scaleHold = Float.NaN
            topScale.animateTo(1f, tween(CoverNextGrowMs, easing = CoverMotion))
        }
    }

    fun promotePrev(incoming: TrackRow, fromX: Float, under: TrackRow) {
        val from = fromX.coerceIn(-slidePx, 0f)
        underTrack = under
        showUnder = under.id != incoming.id
        topTrack = incoming
        settledId = incoming.id
        dragMode = null
        followX = 0f
        prevRevealBase = 0f
        enterHold = from
        scaleHold = 1f
        scope.launch {
            pose.snapTo(0f)
            topScale.snapTo(1f)
            scaleHold = Float.NaN
            enterX.snapTo(from)
            enterHold = Float.NaN
            enterX.animateTo(0f, tween(CoverPrevEnterMs, easing = CoverMotion))
            showUnder = false
            underTrack = incoming
            enterX.snapTo(0f)
        }
    }

    LaunchedEffect(track.id) {
        if (!booted) {
            topTrack = track
            underTrack = track
            settledId = track.id
            showUnder = false
            booted = true
            return@LaunchedEffect
        }
        if (track.id == settledId || dragging) return@LaunchedEffect
        when (directionRef.value) {
            VinylSkipDirection.Next -> {
                spawnExit(topTrack, pose.value)
                promoteNext(track, CoverUnderScale)
            }
            VinylSkipDirection.Previous -> {
                val under = topTrack
                promotePrev(track, -slidePx, under)
            }
        }
    }

    val commitPx = with(LocalDensity.current) { 96.dp.toPx() }
        .coerceAtMost(exitX * 0.72f)
        .coerceAtLeast(1f)
    val prevCommitPx = (slidePx * 0.42f).coerceIn(commitPx.coerceAtMost(slidePx * 0.42f), slidePx)
    val flingPx = CoverFlingVelocity
    val arcT = if (dragging && dragMode == VinylSkipDirection.Next) {
        arcTOf(followX)
    } else {
        pose.value
    }
    val prevShown = if (dragging && dragMode == VinylSkipDirection.Previous) {
        (followX - prevRevealBase).coerceIn(0f, slidePx)
    } else {
        0f
    }
    val enterShown = if (!enterHold.isNaN()) enterHold else enterX.value
    val topOffset = when {
        dragging && dragMode == VinylSkipDirection.Next -> coverExitArc(arcT, exitX, exitY)
        dragging && dragMode == VinylSkipDirection.Previous ->
            Offset(-slidePx + prevShown, 0f)
        enterShown < -0.5f -> Offset(enterShown, 0f)
        else -> coverExitArc(pose.value, exitX, exitY)
    }
    val underGrow = if (dragging && dragMode == VinylSkipDirection.Next && showUnder) {
        val p = (abs(followX) / commitPx).coerceIn(0f, 1f)
        CoverUnderScale + (1f - CoverUnderScale) * 0.40f * p
    } else {
        underScale.value
    }
    val topScaleNow = when {
        dragging -> 1f
        !scaleHold.isNaN() -> scaleHold
        else -> topScale.value
    }

    fun rubber(raw: Float): Float {
        val lo = if (peekNextRef.value != null) -exitX else -commitPx * 0.45f
        val hi = if (peekPrevRef.value != null) slidePx else commitPx * 0.45f
        val x = raw.coerceIn(lo, hi)
        return when {
            x < 0f && peekNextRef.value == null -> x * 0.35f
            x > 0f && peekPrevRef.value == null -> x * 0.35f
            else -> x
        }
    }

    fun applyDelta(delta: Float) {
        if (!dragging) return
        followX = rubber(followX + delta)
        val x = followX
        when {
            x < -1.5f && peekNextRef.value != null -> {
                dragMode = VinylSkipDirection.Next
                prevRevealBase = 0f
                if (topTrack.id != settledId) topTrack = track
                underTrack = peekNextRef.value ?: track
                showUnder = true
            }
            x > 1.5f && peekPrevRef.value != null -> {
                if (dragMode != VinylSkipDirection.Previous) {
                    dragMode = VinylSkipDirection.Previous
                    prevRevealBase = x
                    underTrack = if (topTrack.id == settledId) topTrack else track
                    topTrack = peekPrevRef.value ?: track
                    showUnder = true
                }
            }
            dragMode == VinylSkipDirection.Previous && x > prevRevealBase -> Unit
            dragMode == VinylSkipDirection.Next && x < 0f -> Unit
            else -> {
                if (dragMode == VinylSkipDirection.Previous && topTrack.id != settledId) {
                    topTrack = track
                }
                dragMode = null
                prevRevealBase = 0f
                showUnder = false
            }
        }
    }

    fun beginDrag() {
        if (topTrack.id != settledId) {
            topTrack = track
            underTrack = track
            showUnder = false
            settledId = track.id
        }
        followX = 0f
        prevRevealBase = 0f
        dragging = true
        dragMode = null
        scope.launch {
            pose.snapTo(0f)
            enterX.snapTo(0f)
            topScale.snapTo(1f)
        }
    }

    fun endDrag(velocity: Float) {
        if (!dragging) return
        val x = followX
        val mode = dragMode
        val reveal = if (mode == VinylSkipDirection.Previous) {
            (x - prevRevealBase).coerceAtLeast(0f)
        } else {
            0f
        }
        val scaleAtRelease = CoverUnderScale + (1f - CoverUnderScale) * 0.40f *
            (abs(x) / commitPx).coerceIn(0f, 1f)
        val goNext = mode == VinylSkipDirection.Next &&
            (x <= -commitPx || velocity <= -flingPx) &&
            peekNextRef.value != null
        val goPrev = mode == VinylSkipDirection.Previous &&
            (reveal >= prevCommitPx || velocity >= flingPx) &&
            peekPrevRef.value != null
        when {
            goNext -> {
                val incoming = checkNotNull(peekNextRef.value)
                val outgoing = topTrack
                dragging = false
                dragMode = null
                showUnder = false
                spawnExit(outgoing, arcTOf(x))
                promoteNext(incoming, scaleAtRelease)
                onNextRef.value.invoke()
            }
            goPrev -> {
                val incoming = checkNotNull(peekPrevRef.value)
                val under = when {
                    showUnder && underTrack.id != incoming.id -> underTrack
                    else -> track
                }
                dragging = false
                dragMode = null
                promotePrev(incoming, -slidePx + reveal.coerceAtMost(slidePx), under)
                onPrevRef.value.invoke()
            }
            else -> {
                val fromT = if (mode == VinylSkipDirection.Next) arcTOf(x) else 0f
                val fromEnter = if (mode == VinylSkipDirection.Previous) {
                    -slidePx + reveal.coerceAtMost(slidePx)
                } else {
                    0f
                }
                dragging = false
                scope.launch {
                    if (mode == VinylSkipDirection.Previous && topTrack.id != track.id) {
                        enterX.snapTo(fromEnter)
                        enterX.animateTo(
                            -slidePx,
                            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                        )
                        topTrack = track
                        enterX.snapTo(0f)
                    } else if (mode == VinylSkipDirection.Next) {
                        pose.snapTo(fromT)
                        pose.animateTo(
                            0f,
                            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    showUnder = false
                    dragMode = null
                    followX = 0f
                    prevRevealBase = 0f
                    if (settledId == track.id) topTrack = track
                }
            }
        }
    }

    Box(
        Modifier
            .width(size)
            .aspectRatio(1f),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .playerExpandAnchor(PlayerExpandSlot.FullCover),
        )
        Box(Modifier.matchParentSize()) {
            if (showUnder) {
                DynamicCoverFace(
                    track = underTrack,
                    glass = glass,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                        .graphicsLayer {
                            val s = if (dragMode == VinylSkipDirection.Previous || enterShown < -0.5f) {
                                1f
                            } else {
                                underGrow
                            }
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin.Center
                        },
                )
            }
            exiting.forEach { layer ->
                val t = layer.progress.value
                val at = coverExitArc(t, exitX, exitY)
                DynamicCoverFace(
                    track = layer.track,
                    glass = glass,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .graphicsLayer {
                            translationX = at.x
                            translationY = at.y
                        },
                )
            }
            DynamicCoverFace(
                track = topTrack,
                glass = glass,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer {
                        translationX = topOffset.x
                        translationY = topOffset.y
                        scaleX = topScaleNow
                        scaleY = topScaleNow
                        transformOrigin = TransformOrigin.Center
                    },
            )
        }
        val beginRef = rememberUpdatedState { beginDrag() }
        val deltaRef = rememberUpdatedState { delta: Float -> applyDelta(delta) }
        val endRef = rememberUpdatedState { velocity: Float -> endDrag(velocity) }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!gesturesRef.value) {
                            val pointerId = down.id
                            while (true) {
                                val rest = awaitPointerEvent(PointerEventPass.Main)
                                val c = rest.changes.find { it.id == pointerId }
                                    ?: return@awaitEachGesture
                                if (!c.pressed) return@awaitEachGesture
                            }
                        }
                        val pointerId = down.id
                        val start = down.position
                        var last = down.position
                        var locked = false
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.find { it.id == pointerId } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!locked) {
                                val dx = change.position.x - start.x
                                val dy = change.position.y - start.y
                                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                                    if (abs(dx) > abs(dy)) {
                                        locked = true
                                        beginRef.value.invoke()
                                        deltaRef.value.invoke(dx - sign(dx) * touchSlop)
                                        last = change.position
                                        change.consume()
                                    } else {
                                        return@awaitEachGesture
                                    }
                                } else if (!change.pressed) {
                                    return@awaitEachGesture
                                }
                            } else {
                                val delta = change.position.x - last.x
                                last = change.position
                                change.consume()
                                deltaRef.value.invoke(delta)
                                if (!change.pressed) {
                                    endRef.value.invoke(tracker.calculateVelocity().x)
                                    return@awaitEachGesture
                                }
                            }
                        }
                    }
                },
        )
    }
}

@Composable
private fun DynamicCoverFace(
    track: TrackRow,
    glass: Float,
    modifier: Modifier = Modifier,
) {
    val frost = glass.coerceIn(0f, 1f)
    val sheen = 0.32f * frost
    Box(
        modifier
            .playerExpandHideFull()
            .shadow(
                elevation = 8.dp,
                shape = CoverShape,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.12f),
            )
            .clip(CoverShape)
            .background(CoverFrame.copy(alpha = frost)),
    ) {
        if (frost < 0.98f && Build.VERSION.SDK_INT >= 31) {
            UrlImage(
                url = track.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                showPlaceholder = false,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - frost }
                    .blur((18f * (1f - frost)).dp, BlurredEdgeTreatment.Unbounded),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(5.dp)
                .clip(CoverInnerShape),
        ) {
            UrlImage(
                url = track.coverUrl,
                contentDescription = t("封面"),
                contentScale = ContentScale.Crop,
                showPlaceholder = true,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.White.copy(alpha = sheen),
                            0.11f to Color.Transparent,
                            0.89f to Color.Transparent,
                            1.00f to Color.White.copy(alpha = sheen),
                        ),
                    ),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0.00f to Color.White.copy(alpha = sheen),
                            0.11f to Color.Transparent,
                            0.89f to Color.Transparent,
                            1.00f to Color.White.copy(alpha = sheen),
                        ),
                    ),
            )
        }
    }
}

/** 四分之一圆：先向左、再弯到左下，终点完全离开封面槽。 */
private fun coverExitArc(t: Float, exitX: Float, exitY: Float): Offset {
    val a = t.coerceIn(0f, 1f) * (Math.PI.toFloat() / 2f)
    return Offset(
        x = -exitX * sin(a),
        y = exitY * (1f - cos(a)),
    )
}

@Composable
private fun LandscapeDynamicMetaRow(
    icon: ImageVector,
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(
                if (Build.VERSION.SDK_INT >= 31) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .blur(0.7.dp, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DynamicMeta,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = TextStyle(
                color = DynamicMeta,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 16.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LandscapeDynamicLyricsFan(
    lines: List<LrcLine>,
    companions: List<LrcLine?>,
    positionMs: Long,
    durationMs: Long,
    originalOnTop: Boolean,
    showCompanionOnOthers: Boolean,
    onSeek: (Long) -> Unit,
    stageWidthPx: Float,
    lyricsLeftPx: Float,
    lyricOffsetPx: Float,
    titleClearStagePx: Float,
    playingStyle: LyricRoleStyle,
    playedStyle: LyricRoleStyle,
    unplayedStyle: LyricRoleStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val lyricScale = maxOf(
        playingStyle.sanitizedFontScale(),
        playedStyle.sanitizedFontScale(),
        unplayedStyle.sanitizedFontScale(),
    )
    val maxLyricWidthPx = remember(lines, textMeasurer, lyricScale) {
        var maxW = 0
        for (line in lines) {
            val text = line.text
            if (text.isEmpty()) continue
            val w = textMeasurer.measure(
                text = text,
                style = DynamicLyricHitStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            ).size.width
            if (w > maxW) maxW = w
        }
        maxW * lyricScale
    }
    val animActive = lyricAnimActiveIndex(lines, positionMs, durationMs)
    val targetIndex = lyricFocusIndex(lines, animActive).let { if (it < 0) 0 else it }
    val focus = remember { Animatable(targetIndex.toFloat()) }
    var browsing by remember { mutableStateOf(false) }
    var dragSession by remember { mutableStateOf(false) }
    var idleGen by remember { mutableIntStateOf(0) }
    var dragVisual by remember { mutableFloatStateOf(Float.NaN) }
    val browsingNow = browsing || dragSession
    val onSeekRef = rememberUpdatedState(onSeek)
    val browsingRef = rememberUpdatedState(browsingNow)

    LaunchedEffect(lines.size) {
        val last = lines.lastIndex.coerceAtLeast(0).toFloat()
        focus.updateBounds(0f, last)
    }

    LaunchedEffect(lines.size) {
        browsing = false
        dragSession = false
        if (lines.isNotEmpty()) {
            focus.snapTo(targetIndex.toFloat().coerceIn(0f, lines.lastIndex.toFloat()))
        }
    }

    LaunchedEffect(targetIndex, lines.size, browsing, dragSession) {
        if (browsing || dragSession || lines.isEmpty()) return@LaunchedEffect
        val dest = targetIndex.toFloat().coerceIn(0f, lines.lastIndex.toFloat())
        val jump = abs(dest - focus.value)
        if (jump < 0.02f) return@LaunchedEffect
        val followMs = (400f + jump * 52f).toInt().coerceIn(380, 820)
        focus.animateTo(
            targetValue = dest,
            animationSpec = tween(
                durationMillis = followMs,
                easing = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1f),
            ),
        )
    }

    LaunchedEffect(browsing, idleGen) {
        if (!browsing) return@LaunchedEffect
        delay(5_500)
        while (dragSession) delay(160)
        delay(320)
        if (browsing && !dragSession) {
            browsing = false
        }
    }

    if (lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            LandscapeDynamicFanLine(
                text = t("暂无歌词"),
                companion = null,
                originalOnTop = true,
                offset = 0f,
                density = density.density,
            )
        }
        return
    }

    BoxWithConstraints(
        modifier.graphicsLayer { clip = false },
    ) {
        val halfPx = constraints.maxHeight / 2f
        val edgePx = with(density) { 12.dp.toPx() }
        val spacingPx = ((halfPx - edgePx) / LyricFanRadius.toFloat() * 1.16f).coerceIn(
            with(density) { 34.dp.toPx() },
            with(density) { 48.dp.toPx() },
        )
        val visual = if (!dragVisual.isNaN()) dragVisual else focus.value
        val start = floor(visual - LyricFanRadius).toInt().coerceAtLeast(0)
        val end = ceil(visual + LyricFanRadius).toInt().coerceAtMost(lines.lastIndex)
        val stepRad = Math.toRadians(LyricFanOpenDeg.toDouble()).toFloat()
        val radius = if (abs(sin(stepRad)) < 1e-4f) {
            spacingPx * 12f
        } else {
            spacingPx / sin(stepRad)
        }
        val lastUnplayedRelX = -(1f - cos(LyricFanRadius * stepRad)) * radius * LyricFanXPull
        val nudgePx = with(density) { LyricGroupNudgeRight.toPx() }
        val baseShiftX = stageWidthPx * 0.5f - lyricsLeftPx - lastUnplayedRelX + nudgePx
        // 末句开头在屏幕上的位置。歌名列加宽后，整块扇形再往右让，避免飞入时压到标题。
        val outerStageX = stageWidthPx * 0.5f + nudgePx + lyricOffsetPx
        val fanPush = (titleClearStagePx - outerStageX).coerceAtLeast(0f)
        val groupShiftX = baseShiftX + fanPush
        val lastIndex = lines.lastIndex.toFloat()
        val lastIndexInt = lines.lastIndex
        val spacingKey = spacingPx.roundToInt()
        val visualRef = rememberUpdatedState(visual)
        val preselect = visual.roundToInt().coerceIn(0, lastIndexInt)
        val centerLocalX = stageWidthPx * 0.5f - lyricsLeftPx + fanPush
        val boxW = constraints.maxWidth.toFloat()
        val hitLeftPx = centerLocalX.coerceIn(0f, boxW)
        val hitWpx = (centerLocalX + maxLyricWidthPx - hitLeftPx).coerceIn(0f, boxW - hitLeftPx)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(lines.size, spacingKey, hitLeftPx.roundToInt(), hitWpx.roundToInt()) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x < hitLeftPx ||
                            down.position.x > hitLeftPx + hitWpx
                        ) {
                            return@awaitEachGesture
                        }
                        val pointerId = down.id
                        val startPos = down.position
                        var lastY = startPos.y
                        var dragging = false
                        var flingLaunched = false
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)

                        fun beginDrag(fromY: Float) {
                            dragging = true
                            dragSession = true
                            lastY = fromY
                            dragVisual = focus.value
                        }

                        try {
                            while (!dragging) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.find { it.id == pointerId }
                                    ?: return@awaitEachGesture
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    if (browsingRef.value) {
                                        val mid = size.height / 2f
                                        val rel = (startPos.y - mid) / spacingPx
                                        val tapped = (visualRef.value + rel).roundToInt()
                                            .coerceIn(0, lastIndexInt)
                                        val pre = visualRef.value.roundToInt()
                                            .coerceIn(0, lastIndexInt)
                                        if (isBrowseSeekHit(tapped, pre)) {
                                            val ms = lines.getOrNull(pre)?.timeMs
                                            if (ms != null) onSeekRef.value(ms)
                                            scope.launch { focus.snapTo(pre.toFloat()) }
                                        }
                                        browsing = false
                                        dragSession = false
                                        dragVisual = Float.NaN
                                        change.consume()
                                    }
                                    return@awaitEachGesture
                                }
                                if (change.isConsumed) return@awaitEachGesture
                                val dy = change.position.y - startPos.y
                                val dx = change.position.x - startPos.x
                                if (abs(dx) > touchSlop && abs(dx) >= abs(dy) * 0.75f) {
                                    return@awaitEachGesture
                                }
                                if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 0.75f) {
                                    beginDrag(change.position.y)
                                    change.consume()
                                    break
                                }
                            }

                            while (dragging) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.find { it.id == pointerId } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val step = change.position.y - lastY
                                lastY = change.position.y
                                val from = if (dragVisual.isNaN()) focus.value else dragVisual
                                dragVisual = (from - step / spacingPx).coerceIn(0f, lastIndex)
                                change.consume()
                                if (!change.pressed) {
                                    val velocityY = tracker.calculateVelocity().y
                                    flingLaunched = true
                                    browsing = true
                                    val releaseAt = dragVisual
                                    scope.launch {
                                        try {
                                            focus.snapTo(releaseAt)
                                            dragVisual = Float.NaN
                                            focus.animateDecay(
                                                initialVelocity = -velocityY / spacingPx,
                                                animationSpec = exponentialDecay(
                                                    frictionMultiplier = 1.35f,
                                                ),
                                            )
                                            val nearest = focus.value.roundToInt()
                                                .toFloat()
                                                .coerceIn(0f, lastIndex)
                                            if (abs(focus.value - nearest) > 0.06f) {
                                                focus.animateTo(
                                                    nearest,
                                                    spring(dampingRatio = 0.9f, stiffness = 220f),
                                                )
                                            }
                                        } finally {
                                            dragVisual = Float.NaN
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
                                    val releaseAt = if (dragVisual.isNaN()) focus.value else dragVisual
                                    scope.launch {
                                        focus.snapTo(releaseAt)
                                        dragVisual = Float.NaN
                                    }
                                }
                                dragSession = false
                                if (dragging) idleGen++
                            }
                        }
                    }
                },
        ) {
            for (i in start..end) {
                val offset = i - visual
                if (abs(offset) > LyricFanRadius + 0.12f) continue
                val companion = companions.getOrNull(i)?.text?.trim()?.takeIf { it.isNotEmpty() }
                val absD = abs(offset)
                val showCompanion = companion != null &&
                    (absD < 0.55f || showCompanionOnOthers)
                val theta = offset * stepRad
                val yPx = sin(theta) * radius
                val xPx = -(1f - cos(theta)) * radius * LyricFanXPull + groupShiftX
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                        .zIndex(LyricFanRadius + 1f - absD),
                ) {
                    LandscapeDynamicFanLine(
                        text = lines[i].text,
                        companion = companion.takeIf { showCompanion },
                        originalOnTop = originalOnTop,
                        offset = offset,
                        density = density.density,
                        isPlayingLine = i == targetIndex,
                        isBrowseCenter = browsingNow && i == preselect && i != targetIndex,
                        style = when {
                            i == targetIndex || (browsingNow && i == preselect) -> playingStyle
                            i < targetIndex -> playedStyle
                            else -> unplayedStyle
                        },
                        builtinArgb = when {
                            i == targetIndex || (browsingNow && i == preselect) ->
                                LyricRoleStyle.DynamicPlayingDefault.preset0Argb
                            i < targetIndex -> LyricRoleStyle.DynamicPlayedDefault.preset0Argb
                            else -> LyricRoleStyle.DynamicUnplayedDefault.preset0Argb
                        },
                    )
                }
            }
            if (hitWpx > 1f) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(hitLeftPx.roundToInt(), 0) }
                        .width(with(density) { hitWpx.toDp() })
                        .fillMaxHeight()
                        .zIndex(LyricFanRadius + 2f)
                        .consumeUnclaimedVerticalDrag(),
                )
            }
        }
    }
}

@Composable
private fun LandscapeDynamicFanLine(
    text: String,
    companion: String?,
    originalOnTop: Boolean,
    offset: Float,
    density: Float,
    isPlayingLine: Boolean = false,
    isBrowseCenter: Boolean = false,
    style: LyricRoleStyle = LyricRoleStyle.DynamicUnplayedDefault,
    builtinArgb: Int = LyricRoleStyle.DynamicUnplayedDefault.preset0Argb,
    modifier: Modifier = Modifier,
) {
    val absD = abs(offset)
    val blurDp = when {
        isPlayingLine && absD >= 0.42f -> dynamicFanBlur(absD) * 0.55f
        else -> dynamicFanBlur(absD)
    }
    val alpha = if (isPlayingLine) {
        dynamicFanAlpha(absD).coerceAtLeast(0.84f)
    } else {
        dynamicFanAlpha(absD)
    }
    val focused = absD < 0.42f || isBrowseCenter
    val ink = style.resolvedColor(builtinArgb)
    val weight = if (style.bold) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal
    val fontSize = 21.sp * style.sanitizedFontScale()
    val stageBlur = blurDp > 0.dp && Build.VERSION.SDK_INT >= 31
    // 模糊半径约 3 倍才落到全透明；先垫出透明边，晕开才能渗进歌词周围，而不是切在字形上。
    val bleed = if (stageBlur) blurDp * 3f else 0.dp

    Column(
        modifier
            .wrapContentWidth(Alignment.Start)
            .then(
                if (stageBlur) {
                    Modifier.lyricBlurBleed(bleed)
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                clip = false
                cameraDistance = 16f * density
                val bleedPx = bleed.toPx()
                transformOrigin = if (stageBlur && size.width > 0f) {
                    TransformOrigin(
                        (bleedPx / size.width).coerceIn(0f, 1f),
                        0.5f,
                    )
                } else {
                    TransformOrigin(0f, 0.5f)
                }
                rotationX = offset * LyricTextRotXDeg
                rotationZ = offset * LyricTextRotZDeg
                this.alpha = alpha
            }
            .then(
                if (stageBlur) {
                    Modifier
                        .graphicsLayer {
                            clip = false
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .blur(blurDp, BlurredEdgeTreatment.Unbounded)
                        .padding(bleed)
                } else {
                    Modifier
                },
            )
            .padding(vertical = if (focused) 3.dp else 2.dp),
    ) {
        val main = @Composable {
            Text(
                text = text,
                style = TextStyle(
                    color = ink,
                    fontSize = fontSize,
                    fontWeight = weight,
                    fontStyle = fontStyle,
                    lineHeight = 28.sp * style.sanitizedFontScale(),
                    letterSpacing = 0.12.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val sub = @Composable {
            if (!companion.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = companion,
                    style = TextStyle(
                        color = ink.copy(alpha = 0.62f),
                        fontSize = 16.sp * style.sanitizedFontScale(),
                        fontWeight = FontWeight.Normal,
                        fontStyle = fontStyle,
                        lineHeight = 22.sp * style.sanitizedFontScale(),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (originalOnTop) {
            main()
            sub()
        } else {
            sub()
            main()
        }
    }
}

/**
 * 布局仍按歌词本体占位，绘制时向四周伸出 [bleed]，给模糊留出衰减空间。
 */
private fun Modifier.lyricBlurBleed(bleed: Dp): Modifier = layout { measurable, constraints ->
    val bleedPx = bleed.roundToPx()
    val expanded = constraints.copy(
        maxWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth + bleedPx * 2
        } else {
            constraints.maxWidth
        },
        maxHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight + bleedPx * 2
        } else {
            constraints.maxHeight
        },
    )
    val placeable = measurable.measure(expanded)
    layout(
        (placeable.width - bleedPx * 2).coerceAtLeast(0),
        (placeable.height - bleedPx * 2).coerceAtLeast(0),
    ) {
        placeable.place(-bleedPx, -bleedPx)
    }
}

/** 浅磨砂：邻句保持可读，越远略糊，避免糊成色块。 */
private fun dynamicFanBlur(absD: Float): Dp {
    if (absD < 0.06f) return 0.dp
    return (0.75f + absD * 1.25f).coerceIn(1.0f, 6.8f).dp
}

private fun dynamicFanAlpha(absD: Float): Float {
    if (absD < 0.06f) return 1f
    val blurOk = Build.VERSION.SDK_INT >= 31
    return if (blurOk) {
        (0.95f - absD * 0.08f).coerceIn(0.60f, 0.93f)
    } else {
        (0.88f - absD * 0.11f).coerceIn(0.44f, 0.86f)
    }
}
