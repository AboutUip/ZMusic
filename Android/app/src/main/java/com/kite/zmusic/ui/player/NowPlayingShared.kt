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
import kotlinx.coroutines.flow.collect
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


/**
 * 空白区域手势：
 * - 纯点击（相对按下点位移未超 touchSlop）→ [onTap]
 * - 单次按住并明确下拖超过阈值 → [onSwipeDown]；为 null 时不认领下滑（留给歌词列表滚动）
 * - 回调经 [rememberUpdatedState] 更新，避免动画重组重启 pointerInput
 * - 位移相对「按下坐标」计算
 * - 按下即可跟踪（不要求 unconsumed）：子级 clickable 常在 down 时 consume
 * - **不在 Initial 消费**：歌词 / 设置列表必须先认领垂直滚动，否则下滑浏览会被当成退出
 * - 仅当子级全程未消费、且下拖过阈值时，才在 Main 触发 [onSwipeDown]
 */
internal fun Modifier.nowPlayingBlankGestures(
    dismissThresholdPx: Float,
    onTap: (() -> Unit)?,
    onSwipeDown: (() -> Unit)?,
): Modifier = composed {
    val tapRef = rememberUpdatedState(onTap)
    val swipeRef = rememberUpdatedState(onSwipeDown)
    val swipeEnabled = onSwipeDown != null
    Modifier.pointerInput(dismissThresholdPx, swipeEnabled) {
        val touchSlop = viewConfiguration.touchSlop
        val longPressMs = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val start = down.position
            val downUptime = System.currentTimeMillis()
            var dismissed = false
            var yieldedToChild = false
            var childConsumed = false
            val dismissSlop = touchSlop * 3.5f

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.find { it.id == pointerId } ?: break
                if (change.isConsumed) childConsumed = true
                val dx = change.position.x - start.x
                val dy = change.position.y - start.y
                val movedEnough = abs(dx) > touchSlop || abs(dy) > touchSlop
                val verticalIntent = abs(dy) > touchSlop && abs(dy) >= abs(dx) * 0.65f

                if (!dismissed && !yieldedToChild) {
                    if (!swipeEnabled && verticalIntent) {
                        yieldedToChild = true
                    } else if (childConsumed && verticalIntent) {
                        // 歌词 LazyColumn / 设置 verticalScroll 已认领
                        yieldedToChild = true
                    } else if (childConsumed && movedEnough) {
                        val maybeDismiss =
                            swipeEnabled && dy > touchSlop && dy >= abs(dx) * 0.85f
                        if (!maybeDismiss) yieldedToChild = true
                    }

                    if (
                        !yieldedToChild &&
                        swipeEnabled &&
                        !childConsumed &&
                        dy > dismissSlop &&
                        dy > abs(dx) * 1.6f &&
                        dy >= dismissThresholdPx
                    ) {
                        change.consume()
                        dismissed = true
                        swipeRef.value?.invoke()
                        while (true) {
                            val rest = awaitPointerEvent(PointerEventPass.Main)
                            val c = rest.changes.find { it.id == pointerId }
                                ?: return@awaitEachGesture
                            c.consume()
                            if (!c.pressed) return@awaitEachGesture
                        }
                    }
                }

                if (yieldedToChild) {
                    if (!change.pressed) return@awaitEachGesture
                    while (true) {
                        val rest = awaitPointerEvent(PointerEventPass.Main)
                        val c = rest.changes.find { it.id == pointerId }
                            ?: return@awaitEachGesture
                        if (!c.pressed) return@awaitEachGesture
                    }
                }

                if (!change.pressed) {
                    val heldLong =
                        System.currentTimeMillis() - downUptime >= longPressMs
                    val tapSlop = touchSlop * 3.25f
                    if (
                        !childConsumed &&
                        !dismissed &&
                        !yieldedToChild &&
                        !heldLong &&
                        abs(dx) < tapSlop &&
                        abs(dy) < tapSlop
                    ) {
                        tapRef.value?.invoke()
                    }
                    break
                }
            }
        }
    }
}

/**
 * 黑胶轻触 / 长按：长按超时前不 consume，避免挡住外层下滑退出与横向切歌。
 * 长按用 [withTimeoutOrNull] 竞速——手指静止无 move 事件时也能在超时后立刻触发（不必松手）。
 * 横屏单击由 [nowPlayingBlankGestures] 处理；此处仅在需要时挂长按（或竖屏进歌词）。
 */
internal fun Modifier.vinylLightTapGestures(
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
): Modifier = composed {
    val tapRef = rememberUpdatedState(onTap)
    val longRef = rememberUpdatedState(onLongPress)
    Modifier.pointerInput(Unit) {
        if (tapRef.value == null && longRef.value == null) return@pointerInput
        val touchSlop = viewConfiguration.touchSlop
        val longPressMs = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val start = down.position

            // 无长按：仅短按（竖屏点黑胶进歌词）
            if (longRef.value == null) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.find { it.id == pointerId } ?: return@awaitEachGesture
                    val dx = change.position.x - start.x
                    val dy = change.position.y - start.y
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        while (true) {
                            val rest = awaitPointerEvent(PointerEventPass.Main)
                            val c = rest.changes.find { it.id == pointerId } ?: return@awaitEachGesture
                            if (!c.pressed) return@awaitEachGesture
                        }
                    }
                    if (!change.pressed) {
                        tapRef.value?.invoke()
                        return@awaitEachGesture
                    }
                }
            }

            // 长按竞速：超时仍按住且未出 slop → 立即回调（按住即开，无需松手）
            val race = withTimeoutOrNull(longPressMs) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.find { it.id == pointerId }
                        ?: return@withTimeoutOrNull "cancel"
                    if (!change.pressed) return@withTimeoutOrNull "up"
                    val dx = change.position.x - start.x
                    val dy = change.position.y - start.y
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        // 已滑动：交给横滑切歌 / 外层下滑退出，不 consume
                        return@withTimeoutOrNull "slop"
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                "cancel"
            }

            when (race) {
                null -> {
                    longRef.value?.invoke()
                    while (true) {
                        val rest = awaitPointerEvent(PointerEventPass.Main)
                        val c = rest.changes.find { it.id == pointerId } ?: return@awaitEachGesture
                        c.consume()
                        if (!c.pressed) return@awaitEachGesture
                    }
                }
                "up" -> {
                    tapRef.value?.invoke()
                }
                else -> {
                    // slop / cancel：不 consume，让黑胶横滑与下滑退出继续
                    while (true) {
                        val rest = awaitPointerEvent(PointerEventPass.Main)
                        val c = rest.changes.find { it.id == pointerId } ?: return@awaitEachGesture
                        if (!c.pressed) return@awaitEachGesture
                    }
                }
            }
        }
    }
}

/**
 * 歌词条带内：子列表未消费的垂直拖动也要吃掉，避免滚到顶/点到行间时
 * 被外层 [nowPlayingBlankGestures] 当成下滑退出。
 * 仅应挂在「歌词本身 + 周围」的条带上，勿挂满整列，否则右侧空白无法收回播放页。
 */
internal fun Modifier.consumeUnclaimedVerticalDrag(): Modifier = pointerInput(Unit) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        val start = down.position
        var claimed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.find { it.id == pointerId } ?: return@awaitEachGesture
            if (change.isConsumed && !claimed) {
                while (true) {
                    val rest = awaitPointerEvent(PointerEventPass.Main)
                    val c = rest.changes.find { it.id == pointerId } ?: return@awaitEachGesture
                    if (!c.pressed) return@awaitEachGesture
                }
            }
            val dx = change.position.x - start.x
            val dy = change.position.y - start.y
            if (!claimed && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                if (abs(dy) > abs(dx) * 0.65f) {
                    claimed = true
                    change.consume()
                } else {
                    return@awaitEachGesture
                }
            }
            if (claimed) change.consume()
            if (!change.pressed) return@awaitEachGesture
        }
    }
}

internal val MistTop = Color(0xFF120A18)
internal val MistMid = Color(0xFF1C1428)
internal val MistBottom = Color(0xFF0A1420)
internal val LyricCurrent = Color(0xFFF2EDE6)
internal val LyricDim = Color(0xFF7A8899)
internal val AccentRose = Color(0xFFE8B4BC)
internal val CyanSoft = Color(0xFF6FD4D4)
/** 浏览视觉中心：两色中值，偏亮灰白（横/竖屏共用） */
internal val LyricBrowseSelect = lerp(Color(0xFFF8FAFC), Color(0xFFDCE6F0), 0.5f)
internal val OrbInk = Color(0xFF090B12)
internal val GlassStroke = Color.White.copy(alpha = 0.16f)
internal val GlassHi = Color.White.copy(alpha = 0.14f)
internal val GlassLo = Color.White.copy(alpha = 0.045f)

/** 竖屏歌词阅读罩：全屏低透光磨砂（兼容任意自定义背景，避免局部卡片割裂） */
internal fun portraitLyricReadingGlassStyle(veilStrength: Float): HazeStyle {
    val s = veilStrength.coerceIn(0f, 1f)
    return HazeStyle(
        backgroundColor = Color(0xFF0A0E16),
        tints = listOf(
            HazeTint(Color(0xFF0B1220).copy(alpha = 0.58f * s)),
            HazeTint(Color(0xFF141C2C).copy(alpha = 0.36f * s)),
            HazeTint(Color(0xFF1E2838).copy(alpha = 0.16f * s)),
            HazeTint(Color.Black.copy(alpha = 0.28f * s)),
        ),
        blurRadius = 28.dp,
        noiseFactor = 0.06f * s,
        fallbackTint = HazeTint(Color(0xFF0A101C).copy(alpha = 0.82f * s)),
    )
}

internal fun Color.scaledAlpha(factor: Float): Color =
    copy(alpha = (alpha * factor).coerceIn(0f, 1f))

/**
 * 进入歌词页时铺在背景与前景之间：整屏连续磨砂，标题/歌词/播放条共用同一阅读环境。
 * [progress] 0..1 控制显隐；[transparency] 0..1 越高则磨砂越淡、背景越可见。
 */
@Composable
internal fun PortraitLyricReadingVeil(
    progress: Float,
    hazeState: HazeState?,
    transparency: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0.001f) return
    val veilStrength = (1f - transparency.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    if (veilStrength <= 0.001f) return
    val glassStyle = portraitLyricReadingGlassStyle(veilStrength)
    val glassFallbackTint = HazeTint(Color(0xFF0A101C).copy(alpha = 0.82f * veilStrength))
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t },
    ) {
        if (hazeState != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState, style = glassStyle) {
                        blurRadius = 28.dp
                        noiseFactor = 0.06f * veilStrength
                        fallbackTint = glassFallbackTint
                    },
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color(0xFF0A101C).copy(alpha = 0.82f * veilStrength)),
            )
        }
        // 墨色纵深：略偏冷钢蓝，避免纯黑平板
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xB2060A14).scaledAlpha(veilStrength),
                            0.22f to Color(0xC40C1422).scaledAlpha(veilStrength),
                            0.55f to Color(0xCC0E1624).scaledAlpha(veilStrength),
                            0.82f to Color(0xC40A121E).scaledAlpha(veilStrength),
                            1.00f to Color(0xB805080E).scaledAlpha(veilStrength),
                        ),
                    ),
                ),
        )
        // 中央略提亮冷雾、四角收墨，破坏「一块实色」观感
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0x281A2838).scaledAlpha(veilStrength),
                            0.42f to Color(0x140E1828).scaledAlpha(veilStrength),
                            0.78f to Color(0x33060A12).scaledAlpha(veilStrength),
                            1.00f to Color(0x6602080C).scaledAlpha(veilStrength),
                        ),
                    ),
                ),
        )
        // 极轻暖/冷双色横扫，让任意照片底上仍有层次
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0x22101828).scaledAlpha(veilStrength),
                            0.45f to Color(0x0A182028).scaledAlpha(veilStrength),
                            1.00f to Color(0x1A0C1018).scaledAlpha(veilStrength),
                        ),
                    ),
                ),
        )
    }
}

/**
 * Gemini 式透光光球：相位线性循环，位移一律用整周期 sin/cos，保证首尾相接无跳变。
 * [activeHalo] 开关经 [Animatable] 过渡（可打断）；
 * 蔷薇=低音/鼓点、淡紫=中音、青蓝=高音 —— **独立响应**（可同时亮）。
 * 频谱按时间常数跟瞄；切歌 / 点选歌词 / 拖动进度均有可打断的压暗→回升，避免硬切。
 */
@Composable
internal fun GeminiOrbsBackdrop(
    modifier: Modifier = Modifier,
    activeHalo: Boolean = false,
    playWhenReady: Boolean = false,
    positionMs: Long = 0L,
    scrubbing: Boolean = false,
    trackId: Long = 0L,
    loadPending: Boolean = false,
    /** 评论 / 歌词等叠层打开时停相位，把 GPU 让给手势 */
    motionEnabled: Boolean = true,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val spectrumHolder = remember { arrayOf(AudioSpectrumBands.ZERO) }
    LaunchedEffect(Unit) {
        app.playbackBridge.spectrum.collect { spectrumHolder[0] = it }
    }
    val haloEase = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f)
    val crossEase = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1f)

    val haloGate = remember { Animatable(if (activeHalo) 1f else 0f) }
    LaunchedEffect(activeHalo) {
        val target = if (activeHalo) 1f else 0f
        val distance = abs(target - haloGate.value).coerceIn(0f, 1f)
        val durationMs = (640f * distance).toInt().coerceIn(220, 640)
        haloGate.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = durationMs, easing = haloEase),
        )
    }

    // 播放意图 / 加载：能量门控柔和开闭，避免 seek 缓冲时 isPlaying 闪断造成光效跳变
    val energyGate = remember {
        Animatable(if (playWhenReady && !loadPending) 1f else 0f)
    }
    LaunchedEffect(playWhenReady, loadPending) {
        val target = if (playWhenReady && !loadPending) 1f else 0f
        val down = target < energyGate.value
        energyGate.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = if (down) 520 else 380,
                easing = haloEase,
            ),
        )
    }

    // 切歌：从当前亮度压暗再回升（可被连点打断）
    val trackCross = remember { Animatable(1f) }
    var lastTrackId by remember { mutableLongStateOf(trackId) }
    LaunchedEffect(trackId) {
        if (trackId == 0L) return@LaunchedEffect
        if (lastTrackId == 0L) {
            lastTrackId = trackId
            trackCross.snapTo(1f)
            return@LaunchedEffect
        }
        if (trackId == lastTrackId) return@LaunchedEffect
        lastTrackId = trackId
        trackCross.animateTo(0.05f, tween(340, easing = crossEase))
        trackCross.animateTo(1f, tween(860, easing = crossEase))
    }

    // 点选歌词 / 大幅跳转：浅压暗后回升（不把 200ms 进度 tick 当 effect key）
    val seekCross = remember { Animatable(1f) }
    var lastSeekPos by remember { mutableLongStateOf(positionMs) }
    var lastSeekTrack by remember { mutableLongStateOf(trackId) }
    val scrubbingRef = rememberUpdatedState(scrubbing)
    val posRef = rememberUpdatedState(positionMs)
    LaunchedEffect(trackId) {
        if (trackId != lastSeekTrack) {
            lastSeekTrack = trackId
            lastSeekPos = posRef.value
            seekCross.snapTo(1f)
        }
        snapshotFlow { posRef.value }.collect { pos ->
            if (trackId != lastSeekTrack) {
                lastSeekTrack = trackId
                lastSeekPos = pos
                seekCross.snapTo(1f)
                return@collect
            }
            val jump = abs(pos - lastSeekPos)
            lastSeekPos = pos
            if (scrubbingRef.value || jump < 800L) return@collect
            val dip = (0.38f + (1f - (jump / 45_000f).coerceIn(0f, 1f)) * 0.28f)
                .coerceIn(0.38f, 0.66f)
                .coerceAtMost(seekCross.value)
            seekCross.animateTo(dip, tween(200, easing = crossEase))
            seekCross.animateTo(1f, tween(640, easing = crossEase))
        }
    }

    // 拖动进度条：持续压低响应，松手后缓慢恢复
    val scrubGate = remember { Animatable(1f) }
    LaunchedEffect(scrubbing) {
        if (scrubbing) {
            scrubGate.animateTo(0.48f, tween(220, easing = haloEase))
        } else {
            scrubGate.animateTo(1f, tween(560, easing = haloEase))
        }
    }

    val orbSim = remember { floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f) }
    val lagBands = remember { floatArrayOf(0f, 0f, 0f) }
    var drawGen by remember { mutableIntStateOf(0) }

    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L) {
                    val dt = ((now - last).coerceIn(0L, 48L)) / 1000f
                    val gate = haloGate.value
                    val speed = lerp(1f, 1.28f, gate)
                    orbSim[0] = (orbSim[0] + dt / 22f * speed) % 1f
                    orbSim[1] = (orbSim[1] + dt / 31f * speed) % 1f
                    orbSim[2] = (orbSim[2] + dt / 17f * speed) % 1f

                    val presence = (
                        gate *
                            energyGate.value *
                            trackCross.value *
                            seekCross.value *
                            scrubGate.value
                        ).coerceIn(0f, 1f)
                    val sp = spectrumHolder[0]
                    fun lag(cur: Float, target: Float, tau: Float): Float {
                        val a = (1f - kotlin.math.exp(-dt / tau)).coerceIn(0f, 1f)
                        return cur + (target - cur) * a
                    }
                    lagBands[0] = lag(lagBands[0], sp.low.coerceIn(0f, 1f), 0.11f)
                    lagBands[1] = lag(lagBands[1], sp.mid.coerceIn(0f, 1f), 0.13f)
                    lagBands[2] = lag(lagBands[2], sp.high.coerceIn(0f, 1f), 0.10f)

                    val tLow = (lagBands[0] * presence).coerceIn(0f, 1f)
                    val tMid = (lagBands[1] * presence).coerceIn(0f, 1f)
                    val tHigh = (lagBands[2] * presence).coerceIn(0f, 1f)
                    fun follow(cur: Float, target: Float): Float {
                        val tau = if (target > cur) 0.18f else 0.48f
                        return lag(cur, target, tau)
                    }
                    orbSim[3] = follow(orbSim[3], tLow)
                    orbSim[4] = follow(orbSim[4], tMid)
                    orbSim[5] = follow(orbSim[5], tHigh)
                    drawGen++
                }
                last = now
            }
        }
    }

    Canvas(modifier = modifier.background(OrbInk)) {
        drawGen
        val phaseA = orbSim[0]
        val phaseB = orbSim[1]
        val phaseC = orbSim[2]
        val lowT = orbSim[3]
        val midT = orbSim[4]
        val highT = orbSim[5]
        val gate = haloGate.value
        val presence = (
            gate *
                energyGate.value *
                trackCross.value *
                seekCross.value *
                scrubGate.value
            ).coerceIn(0f, 1f)
        val baseScale = if (gate > 0.05f) {
            lerp(1f, 0.55f, presence)
        } else {
            1f
        }
        val w = size.width
        val h = size.height
        val twoPi = (Math.PI * 2).toFloat()
        fun orb(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = alpha),
                    0.55f to color.copy(alpha = alpha * 0.38f),
                    1f to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        val a = phaseA * twoPi
        val b = phaseB * twoPi
        val c = phaseC * twoPi
        val pulse = 1f + 0.12f * sin(a)
        val pulseInv = 1f + 0.10f * sin(a + Math.PI.toFloat())

        orb(
            cx = w * (0.22f + 0.14f * cos(a)),
            cy = h * (0.32f + 0.16f * sin(a)),
            radius = minOf(w, h) * 0.5f * pulse * (1f + 0.18f * lowT),
            color = Color(0xFFE8A0C8),
            alpha = (0.22f * baseScale + 0.58f * lowT).coerceIn(0f, 0.92f),
        )
        orb(
            cx = w * (0.72f + 0.12f * cos(b + 1.2f)),
            cy = h * (0.68f + 0.14f * sin(b + 0.4f)),
            radius = minOf(w, h) * 0.58f * (0.96f + 0.04f * sin(b)) * (1f + 0.16f * highT),
            color = Color(0xFF6EB8FF),
            alpha = (0.20f * baseScale + 0.55f * highT).coerceIn(0f, 0.90f),
        )
        orb(
            cx = w * (0.58f + 0.11f * sin(c)),
            cy = h * (0.40f + 0.17f * cos(c)),
            radius = minOf(w, h) * 0.44f * pulseInv * (1f + 0.14f * midT),
            color = Color(0xFFB8A0FF),
            alpha = (0.18f * baseScale + 0.52f * midT).coerceIn(0f, 0.88f),
        )
        val ambience = maxOf(lowT, midT, highT)
        orb(
            cx = w * (0.38f + 0.26f * sin(b)),
            cy = h * (0.88f + 0.04f * cos(b * 2f)),
            radius = w * 0.46f * (0.94f + 0.06f * sin(c)) * (1f + 0.08f * ambience),
            color = Color(0xFFFFC9A8),
            alpha = 0.18f * baseScale + 0.22f * ambience,
        )
    }
}

@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(if (compact) 18.dp else 24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(GlassHi, GlassLo),
                ),
            )
            .border(1.dp, GlassStroke, RoundedCornerShape(if (compact) 18.dp else 24.dp))
            .padding(
                horizontal = if (compact) 12.dp else 18.dp,
                vertical = if (compact) 8.dp else 16.dp,
            ),
        content = content,
    )
}
