@file:Suppress("UnusedBoxWithConstraintsScope")

package com.kite.zmusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.kite.zmusic.ui.common.rememberNetworkOnline
import com.kite.zmusic.ui.icons.ZIcons
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
@Suppress("KotlinConstantConditions")
internal fun PlayerTransport(
    isPlaying: Boolean,
    buffering: Boolean,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    durationMs: Long,
    positionMs: Long,
    sliderDragging: Boolean,
    sliderValue: Float,
    onSliderDragStart: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderDragEnd: (Float) -> Unit,
    playbackMode: PlaybackMode,
    onCyclePlaybackMode: () -> Unit,
    trackLiked: Boolean,
    onToggleLike: () -> Unit,
    portraitSlim: Boolean = false,
    landscapeDense: Boolean = false,
    controlsLocked: Boolean = false,
    onOpenScore: (() -> Unit)? = null,
    onOpenQuality: (() -> Unit)? = null,
    onOpenMore: (() -> Unit)? = null,
    /** 竖屏：底栏评论入口；时长上方不再单独放钮 */
    onOpenComments: (() -> Unit)? = null,
    /**
     * 竖屏：进度条与播放按钮行的垂直偏移。
     * 关闭「容器包含」时底部设置条不参与；开启后整块玻璃容器一并偏移。
     */
    controlsOffsetYDp: Float = 0f,
    /** 竖屏：半透明底是否包含进度 / 时长 / 播放控件（否则仅设置条） */
    controlsContainerInclude: Boolean = false,
    /** 竖屏歌词清屏：进度+播放按钮透明度；低于约 0.02 时不占位、不拦截点击 */
    controlsChromeAlpha: Float = 1f,
    /** 竖屏歌词清屏：底部工具栏透明度 */
    toolbarChromeAlpha: Float = 1f,
) {
    val context = LocalContext.current
    val online = rememberNetworkOnline()
    fun requireOnline(action: () -> Unit) {
        if (online) action() else context.showIslandNotice("当前无网络")
    }
    val maxF = durationMs.toFloat().coerceAtLeast(1f)
    val sliderPos = if (sliderDragging) sliderValue else positionMs.toFloat()
    val displayPosMs = if (sliderDragging) sliderPos.toLong() else positionMs
    var lastScrub by remember { mutableFloatStateOf(sliderPos) }
    fun onScrub(v: Float) {
        lastScrub = v
        if (!sliderDragging) onSliderDragStart()
        onSliderChange(v)
    }
    fun onScrubEnd() {
        onSliderChange(lastScrub)
        onSliderDragEnd(lastScrub)
    }
    val playPulse by animateFloatAsState(
        targetValue = if (isPlaying) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f),
        label = "playPulse",
    )
    val iconTint = if (controlsLocked) {
        Color(0xFF7A8796)
    } else {
        Color(0xFFB8C5D4)
    }
    val playSize = when {
        landscapeDense -> 36.dp
        portraitSlim -> 50.dp
        else -> 52.dp
    }
    val skipHit = when {
        landscapeDense -> 34.dp
        portraitSlim -> 50.dp
        else -> 48.dp
    }
    val sliderH = when {
        landscapeDense -> 28.dp
        portraitSlim -> 16.dp
        else -> 20.dp
    }
    val transportIconSize = when {
        landscapeDense -> 16.dp
        portraitSlim -> 22.dp
        else -> 18.dp
    }
    // 厚轨道左右半圆半径 ≈ 高度一半；缩短进度条后半圆外缘与端点图标外缘对齐
    val trackCapRadius = sliderH / 2
    val portraitAlignPad = trackCapRadius
    val portraitBottomBandHeight = 36.dp
    val timeStyle = TextStyle(
        color = if (portraitSlim) {
            // 竖屏进度/总时长：提高对比，避免压在背景上发灰看不清
            Color(0xFFE8EEF5).copy(alpha = 0.92f)
        } else {
            LyricDim.copy(alpha = 0.7f)
        },
        fontFamily = FontFamily.Monospace,
        fontSize = if (landscapeDense) 11.sp else if (portraitSlim) 11.sp else 10.sp,
        letterSpacing = 0.3.sp,
        fontWeight = if (portraitSlim) FontWeight.Medium else FontWeight.Normal,
    )
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color(0xFFE8EEF5),
        activeTrackColor = Color(0xFFD5DEE8).copy(alpha = 0.9f),
        inactiveTrackColor = Color.White.copy(alpha = 0.14f),
    )

    // 横屏：全宽简约条 — 左：模式+传输+喜欢；右：当前时间 | 进度 | 总时长（同一水平线）
    if (landscapeDense) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlaybackModeControl(
                mode = playbackMode,
                onClick = onCyclePlaybackMode,
                circleSize = skipHit,
                tint = iconTint,
            )
            Box(
                modifier = Modifier
                    .size(skipHit)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkipPrev,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TransportSkipIcon(forward = false, size = 16.dp, tint = iconTint)
            }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = playPulse
                        scaleY = playPulse
                    }
                    .size(playSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlay,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TransportPlayPauseIcon(
                    playing = isPlaying,
                    buffering = buffering,
                    size = 16.dp,
                    tint = Color(0xFFF5F7FA),
                )
            }
            Box(
                modifier = Modifier
                    .size(skipHit)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkipNext,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TransportSkipIcon(forward = true, size = 16.dp, tint = iconTint)
            }
            Box(
                modifier = Modifier
                    .size(skipHit)
                    .clip(CircleShape)
                    .alpha(if (online) 1f else 0.38f)
                    .clickable(
                        enabled = !controlsLocked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { requireOnline(onToggleLike) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TransportLikeIcon(
                    liked = trackLiked,
                    size = skipHit * 0.70f,
                    outlineTint = iconTint,
                )
            }

            Text(
                text = formatTimeMs(displayPosMs),
                style = timeStyle,
                modifier = Modifier.widthIn(min = 36.dp),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(sliderH),
                contentAlignment = Alignment.Center,
            ) {
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    value = sliderPos.coerceIn(0f, maxF),
                    onValueChange = { v ->
                        if (controlsLocked) return@Slider
                        onScrub(v)
                    },
                    onValueChangeFinished = { onScrubEnd() },
                    valueRange = 0f..maxF,
                    enabled = !controlsLocked,
                    colors = sliderColors,
                )
            }
            Text(
                text = formatTimeMs(durationMs),
                style = timeStyle,
                modifier = Modifier.widthIn(min = 36.dp),
                textAlign = TextAlign.Start,
                maxLines = 1,
            )
            if (onOpenScore != null) {
                Box(
                    modifier = Modifier
                        .size(skipHit)
                        .clip(CircleShape)
                        .clickable(
                            enabled = !controlsLocked,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenScore,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    TransportScoreIcon(
                        size = 16.dp,
                        tint = iconTint,
                    )
                }
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                top = if (portraitSlim) 1.dp else 3.dp,
            ),
    ) {
        // 半圆纳入进度条视觉长度：时长 / 缩短后的进度条 / 按钮 / 底栏共用同一水平边距
        val alignPad =
            if (portraitSlim) Modifier.padding(horizontal = portraitAlignPad) else Modifier
        val controlsOffsetMod = if (portraitSlim) {
            Modifier.offset(
                y = controlsOffsetYDp
                    .coerceIn(
                        PlayerDisplayPrefs.PORTRAIT_TRANSPORT_OFFSET_Y_MIN,
                        PlayerDisplayPrefs.PORTRAIT_TRANSPORT_OFFSET_Y_MAX,
                    )
                    .dp,
            )
        } else {
            Modifier
        }
        val glassBg = Color.Black.copy(alpha = 0.22f)
        val glassShape = RoundedCornerShape(14.dp)
        val includeInContainer = portraitSlim && controlsContainerInclude
        /** 容器包含时略加深，增强包裹感 */
        val containerGlassBg = Color.Black.copy(alpha = 0.34f)
        val likeGlyphSize = if (portraitSlim) playSize * 0.70f else playSize
        val modeGlyphSize = if (portraitSlim) playSize * 0.61f else playSize

        @Composable
        fun PortraitTimeAndSlider(rowPad: Modifier) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(rowPad)
                    .padding(bottom = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = formatTimeMs(displayPosMs), style = timeStyle)
                    Text(text = formatTimeMs(durationMs), style = timeStyle)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(rowPad)
                    .height(sliderH),
                contentAlignment = Alignment.Center,
            ) {
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    value = sliderPos.coerceIn(0f, maxF),
                    onValueChange = { v -> onScrub(v) },
                    onValueChangeFinished = { onScrubEnd() },
                    valueRange = 0f..maxF,
                    colors = sliderColors,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        @Composable
        fun TransportButtonsRow(rowPad: Modifier) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(rowPad)
                    .padding(vertical = if (portraitSlim) 4.dp else 2.dp),
                // 左右两端贴齐进度条半圆外缘；中间 3 个控件之间形成 4 段等分间隙
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaybackModeControl(
                    mode = playbackMode,
                    onClick = onCyclePlaybackMode,
                    circleSize = modeGlyphSize,
                    tint = iconTint,
                    glyphFraction = if (portraitSlim) 1f else 0.625f,
                )
                Box(
                    modifier = Modifier
                        .size(playSize)
                        .clip(CircleShape)
                        .clickable(onClick = onSkipPrev),
                    contentAlignment = Alignment.Center,
                ) {
                    TransportSkipIcon(forward = false, size = transportIconSize, tint = iconTint)
                }
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = playPulse
                            scaleY = playPulse
                        }
                        .size(playSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    TransportPlayPauseIcon(
                        playing = isPlaying,
                        buffering = buffering,
                        size = transportIconSize,
                        tint = Color(0xFFF5F7FA),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(playSize)
                        .clip(CircleShape)
                        .clickable(onClick = onSkipNext),
                    contentAlignment = Alignment.Center,
                ) {
                    TransportSkipIcon(forward = true, size = transportIconSize, tint = iconTint)
                }
                Box(
                    modifier = Modifier
                        .size(likeGlyphSize)
                        .clip(CircleShape)
                        .alpha(if (online) 1f else 0.38f)
                        .clickable(onClick = { requireOnline(onToggleLike) }),
                    contentAlignment = Alignment.Center,
                ) {
                    TransportLikeIcon(
                        liked = trackLiked,
                        size = likeGlyphSize,
                        outlineTint = iconTint,
                    )
                }
            }
        }

        @Composable
        fun PortraitAccessoryBar(rowPad: Modifier, painted: Boolean) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(rowPad)
                    .height(portraitBottomBandHeight)
                    .then(
                        if (painted) {
                            Modifier.clip(glassShape).background(glassBg)
                        } else {
                            Modifier
                        },
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onOpenQuality != null) {
                    PortraitAccessoryIcon(
                        icon = ZIcons.GraphicEq,
                        contentDescription = "音质",
                        tint = iconTint,
                        enabled = online,
                        onClick = { requireOnline(onOpenQuality) },
                    )
                }
                if (onOpenComments != null) {
                    PortraitAccessoryIcon(
                        icon = ZIcons.Comments,
                        contentDescription = "评论",
                        tint = iconTint,
                        enabled = online,
                        onClick = { requireOnline(onOpenComments) },
                    )
                }
                if (onOpenScore != null) {
                    PortraitAccessoryIcon(
                        icon = ZIcons.Playlist,
                        contentDescription = "曲谱",
                        tint = iconTint,
                        onClick = onOpenScore,
                    )
                }
                if (onOpenMore != null) {
                    PortraitAccessoryIcon(
                        icon = ZIcons.MoreHoriz,
                        contentDescription = "更多",
                        tint = iconTint,
                        onClick = onOpenMore,
                    )
                }
            }
        }

        if (includeInContainer) {
            // 开启「容器包含」：与未开启同高占位（防黑胶上移）；
            // 一块加深玻璃画在背后真正包住进度 / 控件 / 底栏图标（左右留边、底避开导航条）。
            val navBottom = WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()
            val bottomZoneHeight = navBottom + 56.dp
            val containerMarginH = 6.dp
            val containerMarginBottom = navBottom.coerceAtLeast(8.dp)
            val containerPadH = 18.dp
            val containerExpandTop = 22.dp
            val containerRadius = 20.dp
            val contentPad = Modifier.padding(horizontal = containerPadH)
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(controlsOffsetMod)
                    .drawBehind {
                        val mh = containerMarginH.toPx()
                        val mb = containerMarginBottom.toPx()
                        val et = containerExpandTop.toPx()
                        val r = containerRadius.toPx()
                        drawRoundRect(
                            color = containerGlassBg,
                            topLeft = Offset(mh, -et),
                            size = Size(
                                (this.size.width - mh * 2f).coerceAtLeast(0f),
                                (this.size.height - mb + et).coerceAtLeast(0f),
                            ),
                            cornerRadius = CornerRadius(r, r),
                        )
                    },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    CollapseFade(progress = controlsChromeAlpha, slideDown = true) {
                        Column {
                            PortraitTimeAndSlider(rowPad = contentPad)
                            TransportButtonsRow(rowPad = contentPad)
                        }
                    }
                    CollapseFade(progress = toolbarChromeAlpha, slideDown = true) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bottomZoneHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            PortraitAccessoryBar(rowPad = contentPad, painted = false)
                        }
                    }
                }
            }
        } else {
            // 默认：进度 + 传输按钮可整体垂直偏移；底部设置条位置固定
            CollapseFade(
                progress = controlsChromeAlpha,
                slideDown = true,
                modifier = controlsOffsetMod,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    if (portraitSlim) {
                        PortraitTimeAndSlider(rowPad = alignPad)
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(sliderH),
                            contentAlignment = Alignment.Center,
                        ) {
                            Slider(
                                modifier = Modifier.fillMaxWidth(),
                                value = sliderPos.coerceIn(0f, maxF),
                                onValueChange = { v -> onScrub(v) },
                                onValueChangeFinished = { onScrubEnd() },
                                valueRange = 0f..maxF,
                                colors = sliderColors,
                            )
                        }
                    }
                    TransportButtonsRow(rowPad = alignPad)
                }
            }

            // 竖屏底部：区域延伸进系统导航条；玻璃条在剩余空白内垂直居中
            // 背景与横屏底部播放条一致（半透明黑底 + 14dp 圆角）；位置固定，不受播放控件偏移影响
            if (portraitSlim) {
                val navBottom = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
                val bottomZoneHeight = navBottom + 56.dp
                CollapseFade(progress = toolbarChromeAlpha, slideDown = true) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bottomZoneHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        PortraitAccessoryBar(rowPad = alignPad, painted = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitAccessoryIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun CollapseFade(
    progress: Float,
    slideDown: Boolean = true,
    slidePx: Float = 22f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    var fullPx by remember { mutableIntStateOf(0) }
    val collapsing = p < 0.999f
    Box(
        modifier
            .fillMaxWidth()
            .then(
                if (collapsing) {
                    Modifier.height(with(density) { (fullPx * p).toDp() })
                } else {
                    Modifier
                },
            )
            .onSizeChanged { sz ->
                if (!collapsing && sz.height > 0) fullPx = sz.height
            }
            .clipToBounds()
            .graphicsLayer {
                alpha = p
                translationY = (1f - p) * slidePx * if (slideDown) 1f else -1f
            },
        content = content,
    )
}
