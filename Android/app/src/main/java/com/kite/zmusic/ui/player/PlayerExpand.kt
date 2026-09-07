package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TitleLineStyle
import com.kite.zmusic.ui.theme.MainPalette
import com.kite.zmusic.ui.theme.TextTheme
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class PlayerExpandSlot {
    MiniBar,
    MiniCover,
    MiniTitle,
    MiniArtist,
    MiniPlay,
    MiniProgress,
    FullCover,
    FullVinyl,
    FullTitle,
    FullArtist,
    FullPlay,
    FullProgress,
    FullElapsedTime,
    FullDurationTime,
}

/** 展开飞层跟播放页同一套实时偏好，避免飞默认黑胶再在终点跳到用户配置。 */
@Stable
internal data class PlayerExpandLook(
    val plateColors: VinylPlateColors,
    val vinylOuterScale: Float,
    val vinylFullCover: Boolean,
    val vinylCenterRadiusFrac: Float,
    val titleFontMul: Float,
    val titleDestColor: Color,
    val landscape: Boolean,
) {
    companion object {
        fun from(prefs: PlayerDisplayPrefs, landscape: Boolean): PlayerExpandLook {
            val ui = prefs.uiScale.coerceIn(PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX)
            val titleMul = if (landscape) {
                val nameSp = TitleLineStyle.BASE_NAME_SP *
                    prefs.titleNameStyle.sanitizedFontScale()
                nameSp / 13f * ui
            } else {
                PlayerExpandTitleFontMul * ui
            }
            return PlayerExpandLook(
                plateColors = prefs.vinylPlateColors(),
                vinylOuterScale = prefs.vinylOuterScale.coerceIn(
                    PlayerDisplayPrefs.VINYL_OUTER_SCALE_MIN,
                    PlayerDisplayPrefs.VINYL_OUTER_SCALE_MAX,
                ),
                vinylFullCover = prefs.vinylFullCover,
                vinylCenterRadiusFrac = prefs.vinylCenterRadiusFrac.coerceIn(
                    PlayerDisplayPrefs.VINYL_CENTER_RADIUS_MIN,
                    PlayerDisplayPrefs.VINYL_CENTER_RADIUS_MAX,
                ),
                titleFontMul = titleMul,
                titleDestColor = if (landscape) prefs.titleNameColor() else LyricCurrent,
                landscape = landscape,
            )
        }
    }
}

internal val LocalPlayerExpand = staticCompositionLocalOf<PlayerExpandState?> { null }

/** 快收尾的减速，避免 680ms 发黏。 */
private val PlayerExpandMotionEase = CubicBezierEasing(0.2f, 0.0f, 0.15f, 1f)
private val PlayerExpandCloseEase = CubicBezierEasing(0.3f, 0.0f, 0.15f, 1f)

private val PlayerExpandOpenSpec = tween<Float>(
    durationMillis = 340,
    easing = PlayerExpandMotionEase,
)
private val PlayerExpandCloseSpec = tween<Float>(
    durationMillis = 300,
    easing = PlayerExpandCloseEase,
)

internal const val PlayerExpandMiniHide = 0.001f
internal const val PlayerExpandFlightStart = 0.001f
/** 飞层走到终点再交接，避免 97% 处瞬移、双按钮。 */
internal const val PlayerExpandHandoff = 0.995f
/** 模式 / 切歌 / 喜欢：离场在此进度已经 invisible，避免缩到迷你条时残留。 */
private const val PlayerExpandExtraCloseGone = 0.88f
/** 进场过半后再显现，跟裁切长大对齐。 */
private const val PlayerExpandExtraOpenAppear = 0.48f
private const val PlayerExpandExtraOpenFull = 0.88f
/** 横屏播放键从迷你条右侧飞到左侧，上一首/下一首要等它过完再亮。 */
private const val PlayerExpandLandscapeExtraOpenAppear = 0.80f
private const val PlayerExpandLandscapeExtraOpenFull = 0.98f
/** 横屏离场先收掉切歌键，底栏容器仍跟进度淡出，避免播放键穿模。 */
private const val PlayerExpandLandscapeExtraCloseGone = 0.90f

/** 竖屏顶栏 19.sp / 迷你条 13.sp，按字号放大，不跟目标框拉宽高比。 */
internal const val PlayerExpandTitleFontMul = 19f / 13f

@Stable
internal class PlayerExpandState(
    private val scope: CoroutineScope,
    initiallyOpen: Boolean = false,
) {
    private val anim = Animatable(if (initiallyOpen) 1f else 0f)
    private var override by mutableStateOf<Float?>(null)
    private var job: Job? = null
    private var gen = 0
    internal val clipPath = Path()

    var mounted by mutableStateOf(initiallyOpen)
        private set
    var targetOpen by mutableStateOf(initiallyOpen)
        private set
    var immersiveChrome by mutableStateOf(initiallyOpen)
        private set

    var shellRect by mutableStateOf(Rect.Zero)
        private set
    var shellOrigin by mutableStateOf(Offset.Zero)
        private set
    var fallbackMiniBar by mutableStateOf(Rect.Zero)

    var miniBar by mutableStateOf(Rect.Zero)
        private set
    var miniCover by mutableStateOf(Rect.Zero)
        private set
    var miniTitle by mutableStateOf(Rect.Zero)
        private set
    var miniArtist by mutableStateOf(Rect.Zero)
        private set
    var miniPlay by mutableStateOf(Rect.Zero)
        private set
    var fullCover by mutableStateOf(Rect.Zero)
        private set
    var fullVinyl by mutableStateOf(Rect.Zero)
        private set
    var fullTitle by mutableStateOf(Rect.Zero)
        private set
    var look by mutableStateOf<PlayerExpandLook?>(null)
        private set
    var fullArtist by mutableStateOf(Rect.Zero)
        private set
    var fullPlay by mutableStateOf(Rect.Zero)
        private set
    var miniProgress by mutableStateOf(Rect.Zero)
        private set
    var fullProgress by mutableStateOf(Rect.Zero)
        private set
    var fullElapsedTime by mutableStateOf(Rect.Zero)
        private set
    var fullDurationTime by mutableStateOf(Rect.Zero)
        private set

    val visualProgress: Float
        get() = override ?: anim.value

    val hideMiniShared: Boolean
        get() = visualProgress > PlayerExpandMiniHide

    val pastHandoff: Boolean
        get() = !mounted || visualProgress >= PlayerExpandHandoff

    val fullSharedAlpha: Float
        get() = if (pastHandoff) 1f else 0f

    val flightAlpha: Float
        get() {
            val p = visualProgress
            if (!mounted || p < PlayerExpandFlightStart || p >= PlayerExpandHandoff) return 0f
            return 1f
        }

    /**
     * 不进飞层的控件（模式 / 上一首 / 下一首 / 喜欢 / 竖屏底栏）。
     * 离场立刻收掉；进场等容器过半再淡入。横屏更晚，避免挡住飞层播放键。
     */
    val extraChromeAlpha: Float
        get() {
            if (!mounted) return 0f
            val p = visualProgress
            val landscape = look?.landscape == true
            return if (targetOpen) {
                val appear = if (landscape) {
                    PlayerExpandLandscapeExtraOpenAppear
                } else {
                    PlayerExpandExtraOpenAppear
                }
                val full = if (landscape) {
                    PlayerExpandLandscapeExtraOpenFull
                } else {
                    PlayerExpandExtraOpenFull
                }
                val span = (full - appear).coerceAtLeast(0.001f)
                ((p - appear) / span).coerceIn(0f, 1f)
            } else {
                val gone = if (landscape) {
                    PlayerExpandLandscapeExtraCloseGone
                } else {
                    PlayerExpandExtraCloseGone
                }
                val span = (PlayerExpandHandoff - gone).coerceAtLeast(0.001f)
                ((p - gone) / span).coerceIn(0f, 1f)
            }
        }

    fun setShell(rect: Rect, originInWindow: Offset) {
        if (!rectNear(shellRect, rect)) shellRect = rect
        if ((shellOrigin - originInWindow).getDistance() > 0.5f) {
            shellOrigin = originInWindow
        }
    }

    fun report(slot: PlayerExpandSlot, rect: Rect) {
        if (!rect.isAnchorValid() &&
            slot != PlayerExpandSlot.MiniBar &&
            slot != PlayerExpandSlot.MiniProgress &&
            slot != PlayerExpandSlot.FullElapsedTime &&
            slot != PlayerExpandSlot.FullDurationTime
        ) {
            return
        }
        when (slot) {
            PlayerExpandSlot.MiniBar -> if (!rectNear(miniBar, rect)) miniBar = rect
            PlayerExpandSlot.MiniCover -> if (!rectNear(miniCover, rect)) miniCover = rect
            PlayerExpandSlot.MiniTitle -> if (!rectNear(miniTitle, rect)) miniTitle = rect
            PlayerExpandSlot.MiniArtist -> if (!rectNear(miniArtist, rect)) miniArtist = rect
            PlayerExpandSlot.MiniPlay -> if (!rectNear(miniPlay, rect)) miniPlay = rect
            PlayerExpandSlot.MiniProgress ->
                if (rect.isProgressAnchorValid() && !rectNear(miniProgress, rect)) {
                    miniProgress = rect
                }
            PlayerExpandSlot.FullCover -> if (!rectNear(fullCover, rect)) fullCover = rect
            PlayerExpandSlot.FullVinyl -> if (!rectNear(fullVinyl, rect)) fullVinyl = rect
            PlayerExpandSlot.FullTitle -> if (!rectNear(fullTitle, rect)) fullTitle = rect
            PlayerExpandSlot.FullArtist -> if (!rectNear(fullArtist, rect)) fullArtist = rect
            PlayerExpandSlot.FullPlay -> if (!rectNear(fullPlay, rect)) fullPlay = rect
            PlayerExpandSlot.FullProgress -> if (!rectNear(fullProgress, rect)) fullProgress = rect
            PlayerExpandSlot.FullElapsedTime ->
                if (rect.isTimeAnchorValid() && !rectNear(fullElapsedTime, rect)) {
                    fullElapsedTime = rect
                }
            PlayerExpandSlot.FullDurationTime ->
                if (rect.isTimeAnchorValid() && !rectNear(fullDurationTime, rect)) {
                    fullDurationTime = rect
                }
        }
    }

    fun reportLook(next: PlayerExpandLook) {
        if (look != next) look = next
    }

    fun clearFullDestinations() {
        fullCover = Rect.Zero
        fullVinyl = Rect.Zero
        fullTitle = Rect.Zero
        fullArtist = Rect.Zero
        fullPlay = Rect.Zero
        fullProgress = Rect.Zero
        fullElapsedTime = Rect.Zero
        fullDurationTime = Rect.Zero
        look = null
    }

    fun toShell(rect: Rect): Rect {
        val o = shellOrigin
        return Rect(rect.left - o.x, rect.top - o.y, rect.right - o.x, rect.bottom - o.y)
    }

    fun miniBarInShell(): Rect {
        val live = toShell(miniBar)
        val fallback = fallbackMiniBar
        if (fallback.isAnchorValid()) {
            if (!targetOpen && live.isAnchorValid() && live.top < fallback.top - 4f) {
                // 离场当帧 window 坐标偶发偏上，裁切会先落在偏高处再跳回底栏。
                return fallback
            }
            if (!live.isAnchorValid()) return fallback
        }
        if (live.isAnchorValid()) return live
        return live
    }

    fun open() {
        targetOpen = true
        mounted = true
        clearFullDestinations()
        val my = ++gen
        job?.cancel()
        job = scope.launch {
            if (visualProgress < 0.02f) {
                withFrameNanos { }
                withFrameNanos { }
                withFrameNanos { }
                withFrameNanos { }
                // 横屏进场要等底栏量出播放键；竖屏一旦标好方向就不再多等。
                var extra = 0
                while (extra < 8) {
                    if (look?.landscape == false) break
                    val vinylReady = fullVinyl.isAnchorValid() || fullCover.isAnchorValid()
                    if (vinylReady && fullPlay.isAnchorValid()) break
                    withFrameNanos { }
                    extra++
                }
            }
            if (my != gen) return@launch
            val from = visualProgress
            override = null
            anim.stop()
            anim.snapTo(from)
            anim.animateTo(1f, PlayerExpandOpenSpec)
        }
    }

    fun close() {
        targetOpen = false
        val my = ++gen
        job?.cancel()
        job = scope.launch {
            val from = visualProgress
            override = null
            anim.stop()
            anim.snapTo(from)
            anim.animateTo(0f, PlayerExpandCloseSpec)
            if (my == gen && !targetOpen && anim.value < 0.001f) {
                mounted = false
            }
        }
    }

    fun snapClosed() {
        targetOpen = false
        val my = ++gen
        job?.cancel()
        override = null
        job = scope.launch {
            anim.stop()
            anim.snapTo(0f)
            if (my == gen) mounted = false
        }
    }

    fun beginScrub() {
        job?.cancel()
        override = visualProgress
        val freeze = override ?: 0f
        job = scope.launch {
            anim.stop()
            anim.snapTo(freeze)
        }
    }

    fun scrub(progress: Float) {
        override = progress.coerceIn(0f, 1f)
    }

    init {
        scope.launch {
            snapshotFlow { visualProgress }.collect { p ->
                val dark = p > 0.36f
                if (immersiveChrome != dark) immersiveChrome = dark
            }
        }
    }
}

internal fun Rect.isAnchorValid(): Boolean =
    width > 8f && height > 8f && left.isFinite() && top.isFinite()

internal fun Rect.isProgressAnchorValid(): Boolean =
    width > 8f && height > 1.5f && left.isFinite() && top.isFinite()

internal fun Rect.isTimeAnchorValid(): Boolean =
    width > 8f && height > 4f && left.isFinite() && top.isFinite()

internal fun rectNear(a: Rect, b: Rect, eps: Float = 0.6f): Boolean =
    abs(a.left - b.left) < eps &&
        abs(a.top - b.top) < eps &&
        abs(a.right - b.right) < eps &&
        abs(a.bottom - b.bottom) < eps

internal fun lerpRect(a: Rect, b: Rect, t: Float): Rect {
    val u = t.coerceIn(0f, 1f)
    return Rect(
        a.left + (b.left - a.left) * u,
        a.top + (b.top - a.top) * u,
        a.right + (b.right - a.right) * u,
        a.bottom + (b.bottom - a.bottom) * u,
    )
}

internal fun flightCenterTranslation(src: Rect, dest: Rect, t: Float): Offset {
    val end = if (dest.isAnchorValid()) dest else src
    val cx = lerp(src.center.x, end.center.x, t)
    val cy = lerp(src.center.y, end.center.y, t)
    return Offset(cx - src.width / 2f, cy - src.height / 2f)
}

internal fun coverRectInVinyl(vinyl: Rect): Rect {
    val side = minOf(vinyl.width, vinyl.height) * VinylCoverFrac
    val cx = vinyl.center.x
    val cy = vinyl.center.y
    val h = side / 2f
    return Rect(cx - h, cy - h, cx + h, cy + h)
}

internal fun vinylRectFromCover(cover: Rect): Rect {
    val side = minOf(cover.width, cover.height) / VinylCoverFrac.coerceAtLeast(0.01f)
    val cx = cover.center.x
    val cy = cover.center.y
    val h = side / 2f
    return Rect(cx - h, cy - h, cx + h, cy + h)
}

/** 飞层容器落到播放页黑胶盘（layout 尺寸），不要用旋转封面 AABB。 */
internal fun flightVinylDest(expand: PlayerExpandState, miniCover: Rect): Rect {
    val vinyl = expand.toShell(expand.fullVinyl)
    if (vinyl.isAnchorValid()) return vinyl
    val cover = expand.toShell(expand.fullCover)
    if (cover.isAnchorValid()) return vinylRectFromCover(cover)
    return miniCover
}

/** 四角映射，保证父级 graphicsLayer（竖屏 uiScale）计入展开锚点。 */
internal fun LayoutCoordinates.windowAabb(): Rect {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val p0 = localToWindow(Offset.Zero)
    val p1 = localToWindow(Offset(w, 0f))
    val p2 = localToWindow(Offset(w, h))
    val p3 = localToWindow(Offset(0f, h))
    return Rect(
        minOf(p0.x, p1.x, p2.x, p3.x),
        minOf(p0.y, p1.y, p2.y, p3.y),
        maxOf(p0.x, p1.x, p2.x, p3.x),
        maxOf(p0.y, p1.y, p2.y, p3.y),
    )
}

internal fun flightUniformScale(src: Rect, dest: Rect, t: Float): Float {
    val end = if (dest.isAnchorValid()) dest else src
    val a = minOf(src.width, src.height).coerceAtLeast(1f)
    val b = minOf(end.width, end.height).coerceAtLeast(1f)
    return lerp(a, b, t) / a
}

/** 歌名按字号等比放大，不跟目标框宽高比去拉扁。 */
internal fun flightTitleScale(t: Float, destMul: Float = PlayerExpandTitleFontMul): Float =
    lerp(1f, destMul, t.coerceIn(0f, 1f))

internal fun expandCardFromColor(): Color =
    if (MainPalette.isDark) {
        Color(0xFF3A3A3C)
    } else {
        Color(0xFFF4F4F6)
    }

/** 迷你条实色 → 播放页底，不借用光球色相。 */
internal fun expandCardColor(progress: Float, dest: Color): Color =
    lerp(expandCardFromColor(), dest, progress.coerceIn(0f, 1f))

internal fun Modifier.playerExpandAnchor(slot: PlayerExpandSlot): Modifier = composed {
    val expand = LocalPlayerExpand.current ?: return@composed this
    onGloballyPositioned { coords ->
        expand.report(slot, coords.windowAabb())
    }
}

internal fun Modifier.playerExpandHideMini(): Modifier = composed {
    val expand = LocalPlayerExpand.current ?: return@composed this
    graphicsLayer {
        alpha = if (expand.hideMiniShared) 0f else 1f
    }
}

internal fun Modifier.playerExpandHideFull(): Modifier = composed {
    val expand = LocalPlayerExpand.current ?: return@composed this
    graphicsLayer {
        alpha = expand.fullSharedAlpha
    }
}

/** 非飞层控件：离场早收，避免迷你条交接时图标还停着。 */
internal fun Modifier.playerExpandHideExtra(): Modifier = composed {
    val expand = LocalPlayerExpand.current ?: return@composed this
    graphicsLayer {
        alpha = expand.extraChromeAlpha
    }
}

/** 展开未交接时关掉播放键呼吸缩放，避免和飞层对不齐。 */
internal fun Modifier.playerExpandPlayPulse(pulse: Float): Modifier = composed {
    val expand = LocalPlayerExpand.current
    graphicsLayer {
        val s = if (expand != null && expand.mounted && !expand.pastHandoff) 1f else pulse
        scaleX = s
        scaleY = s
    }
}

/** 舞台实底跟展开进度走，不再整层藏到结束。 */
internal fun Modifier.playerExpandStageFill(): Modifier = composed {
    val expand = LocalPlayerExpand.current
    val color = if (expand != null && expand.mounted && !expand.pastHandoff) {
        expandCardColor(expand.visualProgress, TextTheme.PlayerStage)
    } else {
        TextTheme.PlayerStage
    }
    background(color)
}

/** 光球 / 自定义背景随裁切出现，透明度不再整层关掉。 */
internal fun Modifier.playerExpandAtmosphereReveal(): Modifier = this

internal fun Modifier.playerExpandContentClip(
    expand: PlayerExpandState,
    cardColor: Color,
): Modifier =
    drawWithContent {
        val p = expand.visualProgress
        if (p <= 0.001f) return@drawWithContent
        val rect = lerpRect(expand.miniBarInShell(), expand.shellRect, p)
        val radius = lerp(24.dp.toPx(), 0f, p.coerceIn(0f, 1f))
        val path = expand.clipPath
        path.reset()
        path.addRoundRect(
            RoundRect(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
                cornerRadius = CornerRadius(radius.coerceAtLeast(0f)),
            ),
        )
        clipPath(path) {
            drawRect(cardColor)
            this@drawWithContent.drawContent()
        }
    }

@Composable
internal fun PlayerExpandHost(
    expand: PlayerExpandState,
    stageColor: Color,
    content: @Composable () -> Unit,
) {
    val p = expand.visualProgress
    val cardColor = expandCardColor(p, stageColor)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (p <= 0.001f) 0f else 1f
                }
                .playerExpandContentClip(expand, cardColor),
        ) {
            content()
        }
    }
}
