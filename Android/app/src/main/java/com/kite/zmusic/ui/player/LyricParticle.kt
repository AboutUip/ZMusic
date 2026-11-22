package com.kite.zmusic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import com.kite.zmusic.data.LongLyricRender
import com.kite.zmusic.data.LrcLine
import kotlin.math.roundToInt
import kotlin.math.sin

/** 手指滑动歌词时的凝聚、消散比平时慢一些。 */
internal const val LyricBrowseGatherMs = 820L
internal const val LyricBrowseScatterMs = 1_100L

/** 完整落在可视区里的句子下标；没有就返回空区间。 */
internal fun LazyListState.fullyVisibleLyricSpan(): IntRange {
    val info = layoutInfo
    val top = info.viewportStartOffset
    val bottom = info.viewportEndOffset
    var first = -1
    var last = -1
    for (item in info.visibleItemsInfo) {
        if (item.offset >= top - 1 && item.offset + item.size <= bottom + 1) {
            if (first < 0) first = item.index
            last = item.index
        }
    }
    return if (first < 0) IntRange.EMPTY else first..last
}

internal fun lyricParticleSongKey(lines: List<LrcLine>): String {
    val first = lines.firstOrNull()
    val last = lines.lastOrNull()
    return "${lines.size}|${first?.timeMs}|${last?.timeMs}|${first?.text}"
}

internal enum class LyricSeekSurface {
    /** 正常跟滚。 */
    Live,
    /** 跳到播放句之后：整屏歌词先完整显示，还没有播放中样式。 */
    Present,
    /** 跳到播放句之前：整屏先处于消散。 */
    Scattered,
}

internal class LyricParticleCursor(
    val tokens: Map<Int, Int>,
    val settled: Map<Int, Int>,
    val gathered: Set<Int>,
    val scatterQueue: Set<Int>,
    val surface: LyricSeekSurface,
    val playingStyleReady: Boolean,
    /** 这一行以及更靠后的句子始终是完整歌词，不依赖是否正在可见。 */
    val textFloor: Int,
    /** 向后跳转还没开始消散：播放句上面的完整歌词也要留在画面里。 */
    val showingLeadIn: Boolean,
    /** 这次凝聚来自向前点进度条，动画更短。 */
    val seekGather: Boolean,
    /** 手指滑动歌词：只看句子在不在可视区里，进来凝聚、出去消散。 */
    val browseFx: Boolean,
    val finishGather: (Int) -> Unit,
)

/**
 * 正数是离场飘散，负数是滑动凝聚。0 表示这一行当前没有粒子。
 * 跟滚时，播放焦点往前走，刚结束的那句记一次正数。
 * 手动滑动时，新进入窗口的句子记一次负数。
 */
@Composable
internal fun rememberLyricParticleTokens(
    enabled: Boolean,
    playFocus: Int,
    browsing: Boolean,
    dragSession: Boolean,
    sliderDragging: Boolean = false,
    browseCenter: Int,
    selectFrozen: Boolean,
    lineCount: Int,
    songKey: String,
    window: Int,
    upcomingLines: Int = window,
    seekTarget: Int = -1,
    seekTick: Int = 0,
    visibleIndices: IntArray = IntArray(0),
    visibleKey: Int = 0,
    viewportSettled: Boolean = true,
    /** 完整落在可视区里的第一句和最后一句。 */
    coreFirst: Int = -1,
    coreLast: Int = -1,
): LyricParticleCursor {
    var tokens by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var settled by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    val gatheredState = remember { mutableStateOf(emptySet<Int>()) }
    var gathered by gatheredState
    var gatherQueue by remember { mutableStateOf(emptySet<Int>()) }
    var scatterQueue by remember { mutableStateOf(emptySet<Int>()) }
    var surface by remember { mutableStateOf(LyricSeekSurface.Live) }
    var textFloor by remember { mutableIntStateOf(playFocus) }
    var seekGather by remember { mutableStateOf(false) }
    var browseFx by remember { mutableStateOf(false) }
    val hold = remember { ParticleCueHold() }
    val visibleNow by rememberUpdatedState(visibleIndices)
    val finishGather: (Int) -> Unit = { index ->
        if (index !in gatheredState.value) gatheredState.value = gatheredState.value + index
        val current = tokens
        if ((current[index] ?: 0) < 0) tokens = current - index
    }
    LaunchedEffect(
        enabled,
        playFocus,
        browsing,
        dragSession,
        sliderDragging,
        browseCenter,
        selectFrozen,
        lineCount,
        songKey,
        window,
        seekTarget,
        seekTick,
        visibleKey,
        viewportSettled,
        coreFirst,
        coreLast,
    ) {
        if (!enabled) {
            browseFx = false
            hold.prevFocus = playFocus
            hold.gatherQueue.clear()
            hold.scatterQueue.clear()
            hold.seenVisible = emptySet()
            if (tokens.isNotEmpty()) tokens = emptyMap()
            if (settled.isNotEmpty()) settled = emptyMap()
            if (gathered.isNotEmpty()) gathered = emptySet()
            if (gatherQueue.isNotEmpty()) gatherQueue = emptySet()
            if (scatterQueue.isNotEmpty()) scatterQueue = emptySet()
            seekGather = false
            hold.scrubbing = false
            hold.scrubRevealReady = false
            hold.didRevealAll = false
            hold.revealArmed = false
            hold.pendingForwardUntil = -1
            hold.intro = LyricSeekIntro.None
            return@LaunchedEffect
        }
        if (hold.song != songKey) {
            hold.song = songKey
            hold.prevFocus = playFocus
            hold.gatherQueue.clear()
            hold.scatterQueue.clear()
            hold.seenVisible = emptySet()
            hold.wasManual = false
            hold.intro = LyricSeekIntro.None
            hold.scrubbing = false
            hold.scrubRevealReady = false
            hold.didRevealAll = false
            hold.revealArmed = false
            hold.pendingForwardUntil = -1
            browseFx = false
            surface = LyricSeekSurface.Live
            textFloor = playFocus
            tokens = emptyMap()
            settled = emptyMap()
            gathered = lyricRevealWindow(playFocus, upcomingLines.coerceAtLeast(0), lineCount)
            gatherQueue = emptySet()
            scatterQueue = emptySet()
            seekGather = false
            return@LaunchedEffect
        }
        if (selectFrozen || lineCount <= 0) {
            hold.prevFocus = playFocus
            return@LaunchedEffect
        }
        val ahead = upcomingLines.coerceAtLeast(0)
        val playedAround = (window - ahead).coerceAtLeast(0)
        val visible = visibleNow.filter { it in 0 until lineCount }.toSet()
        fun span(focus: Int): Set<Int> {
            if (focus !in 0 until lineCount) return emptySet()
            val from = (focus - playedAround).coerceAtLeast(0)
            val to = (focus + ahead).coerceAtMost(lineCount - 1)
            return (from..to).toSet()
        }
        fun begin(
            indices: Collection<Int>,
            gather: Boolean,
            includeFocus: Boolean = false,
            fast: Boolean = false,
        ) {
            if (indices.isEmpty()) return
            var next = tokens
            var nextSettled = settled
            var nextGathered = gathered
            var changed = false
            for (index in indices) {
                if (index !in 0 until lineCount) continue
                val token = next[index] ?: 0
                if (gather) {
                    if ((!includeFocus && index == playFocus) || index in nextGathered) continue
                    next = hold.issue(next, index, gather = true)
                    changed = true
                } else {
                    if (token > 0 && nextSettled[index] != token) continue
                    next = hold.issue(next, index, gather = false)
                    nextSettled = nextSettled - index
                    if (index in nextGathered) nextGathered = nextGathered - index
                    changed = true
                }
            }
            if (!changed) return
            if (gather) seekGather = fast
            tokens = next
            settled = nextSettled
            gathered = nextGathered
        }
        fun dropScatter() {
            val next = tokens.filterValues { it <= 0 }
            if (next.size != tokens.size) {
                tokens = next
                settled = settled.filterKeys { it in next }
            }
        }
        fun armForward(from: Int, until: Int) {
            val start = from.coerceIn(0, lineCount)
            val end = until.coerceIn(0, lineCount)
            if (end <= start) {
                hold.pendingForwardUntil = -1
                hold.intro = LyricSeekIntro.None
                hold.prevFocus = end
                return
            }
            hold.pendingForwardFrom = start
            hold.pendingForwardUntil = end
            hold.intro = LyricSeekIntro.ForwardShow
            hold.introFocus = end
            surface = LyricSeekSurface.Live
            if (textFloor > start) textFloor = start
            hold.prevFocus = end
        }
        fun commitPendingForward() {
            val from = hold.pendingForwardFrom.coerceAtLeast(0)
            val until = playFocus.coerceIn(0, lineCount)
            val nowVisible = visibleNow.filter { it in 0 until lineCount }.toSet()
            // 屏幕外还挂着凝聚的句子直接算完成，回到屏幕里时是完整歌词。
            val offGather = tokens.filter { (index, token) ->
                token < 0 && index >= until && index !in nowVisible
            }.keys
            if (offGather.isNotEmpty()) tokens = tokens - offGather
            if (until > from) {
                begin((from until until).filter { it in nowVisible }, gather = false)
            }
            textFloor = until
            gathered = lyricRevealWindow(until, ahead, lineCount) + offGather
            surface = LyricSeekSurface.Live
            hold.pendingForwardUntil = -1
            hold.intro = LyricSeekIntro.None
            hold.prevFocus = playFocus
        }
        fun commitBackward(dest: Int) {
            val focus = dest.coerceIn(0, lineCount - 1)
            val revive = (focus..(focus + ahead)).filter { it in 0 until lineCount }
            tokens = emptyMap()
            settled = emptyMap()
            gathered = emptySet()
            begin(revive, gather = true, includeFocus = true, fast = true)
            textFloor = focus
            surface = LyricSeekSurface.Live
            hold.intro = LyricSeekIntro.None
            hold.pendingForwardUntil = -1
            hold.prevFocus = focus
        }
        fun hiddenFloor(): Int =
            if (surface == LyricSeekSurface.Scattered || browseFx) lineCount else textFloor.coerceIn(0, lineCount)
        fun startBrowseFx() {
            val shown = visible.filter { index ->
                val token = tokens[index] ?: 0
                surface != LyricSeekSurface.Scattered &&
                    ((token == 0 && (index >= textFloor || index == playFocus)) ||
                        (token < 0 && index in gathered))
            }
            gathered = shown.toSet()
            surface = LyricSeekSurface.Live
            hold.pendingForwardUntil = -1
            hold.intro = LyricSeekIntro.None
            browseFx = true
        }
        /**
         * 滑动中：完整落在可视区里的句子凝聚出来，被上下边缘切到的句子消散。
         * 往哪边滑，哪边的句子就先散开，另一边进来的句子聚拢。
         */
        fun applyBrowseFx(focusFloor: Int = -1) {
            if (visible.isEmpty()) return
            val core = if (coreFirst in 0 until lineCount && coreLast >= coreFirst) {
                coreFirst..coreLast.coerceAtMost(lineCount - 1)
            } else {
                IntRange.EMPTY
            }
            var next = tokens
            var nextSettled = settled
            var nextGathered = gathered
            var changed = false
            val gone = next.keys.filter { it !in visible }
            if (gone.isNotEmpty()) {
                next = next - gone.toSet()
                nextSettled = nextSettled - gone.toSet()
                changed = true
            }
            val keptShown = nextGathered.filter { it in visible }.toSet()
            if (keptShown.size != nextGathered.size) {
                nextGathered = keptShown
                changed = true
            }
            for (index in visible) {
                val token = next[index] ?: 0
                val gatheringNow = token < 0 && index !in nextGathered
                val scatteringNow = token > 0 && nextSettled[index] != token
                val shown = index in nextGathered
                val wanted = if (focusFloor >= 0) index >= focusFloor else index in core
                if (wanted) {
                    if (!shown && !gatheringNow) {
                        next = hold.issue(next, index, gather = true)
                        changed = true
                    }
                } else if ((shown || gatheringNow) && !scatteringNow) {
                    next = hold.issue(next, index, gather = false)
                    nextSettled = nextSettled - index
                    nextGathered = nextGathered - index
                    changed = true
                }
            }
            if (!changed) return
            seekGather = false
            tokens = next
            settled = nextSettled
            gathered = nextGathered
        }
        fun settleBrowseFx() {
            val focus = playFocus.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
            applyBrowseFx(focusFloor = focus)
            textFloor = focus
            surface = LyricSeekSurface.Live
            browseFx = false
            hold.armedSeekTarget = -1
            hold.prevFocus = playFocus
        }
        fun revealHeldLyrics() {
            if (!hold.scrubRevealReady) {
                if (visible.isEmpty()) return
                dropScatter()
                val hiddenEnd = hold.hiddenBefore.coerceIn(0, lineCount)
                val hidden = (0 until hiddenEnd).filter { it !in gathered }
                begin(hidden.filter { it in visible }, gather = true, includeFocus = true, fast = false)
                val off = hidden.filter { it !in visible }
                if (off.isNotEmpty()) gathered = gathered + off
                hold.scrubRevealReady = true
            } else if (visible.isNotEmpty()) {
                // 凝聚到一半就滑出屏幕的句子算完成，滑回来时直接是完整歌词。
                val offGather = tokens.filter { (index, token) -> token < 0 && index !in visible }.keys
                if (offGather.isNotEmpty()) {
                    tokens = tokens - offGather
                    gathered = gathered + offGather
                }
            }
            surface = LyricSeekSurface.Live
            textFloor = 0
            hold.didRevealAll = true
            hold.revealArmed = true
        }
        suspend fun pumpArmed(): Boolean {
            val waiting = hold.pendingForwardUntil >= 0 || hold.intro == LyricSeekIntro.BackwardGather
            if (!waiting || sliderDragging || hold.scrubbing) return false
            if (!viewportSettled) return true
            if (hold.intro == LyricSeekIntro.BackwardGather) {
                commitBackward(playFocus)
                return true
            }
            delay(360)
            if (sliderDragging || hold.scrubbing || hold.pendingForwardUntil < 0) return true
            commitPendingForward()
            return true
        }
        fun openSeekIntro(origin: Int, dest: Int) {
            hold.seekOrigin = origin
            val remains = origin in span(dest)
            surface = LyricSeekSurface.Live
            hold.intro = LyricSeekIntro.None
            hold.pendingForwardUntil = -1
            if (remains) {
                if (dest > origin) {
                    begin((origin until dest).toList(), gather = false)
                    textFloor = dest
                    gathered = lyricRevealWindow(dest, ahead, lineCount)
                } else {
                    val revive = (dest..origin).filter { it in 0 until lineCount }
                    gathered = (gathered - revive.toSet()) +
                        lyricRevealWindow(dest, ahead, lineCount).filter { it > origin }
                    begin(revive, gather = true, includeFocus = true, fast = true)
                }
                hold.prevFocus = dest
                return
            }
            if (dest > origin) {
                val floor = if (textFloor in 0..origin) textFloor else origin.coerceAtLeast(0)
                if (textFloor > floor) textFloor = floor
                gathered = gathered + ((floor until lineCount).toSet())
                armForward(floor, dest)
            } else {
                tokens = emptyMap()
                settled = emptyMap()
                surface = LyricSeekSurface.Scattered
                gathered = emptySet()
                hold.intro = LyricSeekIntro.BackwardGather
                hold.introFocus = dest
                hold.prevFocus = dest
            }
        }
        if (sliderDragging) {
            if (!hold.scrubbing) {
                hold.scrubbing = true
                hold.scrubRevealReady = false
                hold.didRevealAll = false
                hold.revealArmed = false
                hold.pendingForwardUntil = -1
                hold.intro = LyricSeekIntro.None
                hold.scrubStartedAt = System.nanoTime()
                hold.focusAtScrubStart = when {
                    hold.prevFocus in 0 until lineCount -> hold.prevFocus
                    playFocus in 0 until lineCount -> playFocus
                    else -> textFloor
                }
                hold.hiddenBefore = hiddenFloor()
                browseFx = false
            }
            if (!hold.revealArmed) {
                val waited = (System.nanoTime() - hold.scrubStartedAt) / 1_000_000L
                val remain = 120L - waited
                if (remain > 0L) delay(remain)
                hold.revealArmed = true
            }
            revealHeldLyrics()
            return@LaunchedEffect
        }
        if (hold.scrubbing) {
            val origin = hold.focusAtScrubStart
            val dest = playFocus.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
            val revealed = hold.didRevealAll
            hold.scrubbing = false
            hold.scrubRevealReady = false
            hold.didRevealAll = false
            hold.revealArmed = false
            hold.wasManual = false
            hold.seenVisible = emptySet()
            hold.armedSeekTarget = -1
            when {
                lineCount <= 0 -> Unit
                // 按住期间整页都是完整歌词，松手后只把新播放句上面的消散掉。
                revealed -> armForward(0, dest)
                origin in 0 until lineCount && dest != origin -> openSeekIntro(origin, dest)
                else -> {
                    hold.intro = LyricSeekIntro.None
                    hold.pendingForwardUntil = -1
                    hold.prevFocus = dest
                }
            }
        }
        if (seekTick != hold.seekTick) {
            hold.seekTick = seekTick
            hold.armedSeekTarget = seekTarget.coerceIn(0, lineCount)
        }
        if (browsing || dragSession) {
            if (visible.isEmpty()) return@LaunchedEffect
            if (!browseFx) startBrowseFx()
            applyBrowseFx()
            return@LaunchedEffect
        }
        if (browseFx) {
            // 回到播放句的路上照样按可视区凝聚、消散；停稳后再按播放句收尾。
            if (!viewportSettled) {
                applyBrowseFx()
                return@LaunchedEffect
            }
            delay(360)
            if (browsing || dragSession || sliderDragging) return@LaunchedEffect
            settleBrowseFx()
            return@LaunchedEffect
        }
        if (pumpArmed()) return@LaunchedEffect
        if (hold.armedSeekTarget >= 0 && playFocus == hold.armedSeekTarget) {
            hold.armedSeekTarget = -1
        }
        if (hold.prevFocus >= 0 && playFocus != hold.prevFocus) {
            val step = playFocus - hold.prevFocus
            if (step in 1..2) {
                begin((hold.prevFocus until playFocus).filter { it in visible }, gather = false)
                textFloor = playFocus
                gathered = lyricRevealWindow(playFocus, ahead, lineCount)
            } else {
                openSeekIntro(hold.prevFocus, playFocus)
            }
        }
        if (hold.pendingForwardUntil < 0 && hold.intro != LyricSeekIntro.BackwardGather) {
            hold.prevFocus = playFocus
        }
        if (pumpArmed()) return@LaunchedEffect
    }
    val focusToken = tokens[playFocus] ?: 0
    val playingStyleReady = surface == LyricSeekSurface.Live && focusToken >= 0
    val showingLeadIn = browseFx ||
        (surface != LyricSeekSurface.Scattered && textFloor in 0 until playFocus)
    return LyricParticleCursor(
        tokens,
        settled,
        gathered,
        scatterQueue,
        surface,
        playingStyleReady,
        textFloor,
        showingLeadIn,
        seekGather,
        browseFx,
        finishGather,
    )
}

private enum class LyricSeekIntro { None, ForwardShow, BackwardGather }

private class ParticleCueHold {
    var prevFocus: Int = -1
    var song: String? = null
    var seenVisible: Set<Int> = emptySet()
    var wasManual: Boolean = false
    var scrubbing: Boolean = false
    var scrubRevealReady: Boolean = false
    var didRevealAll: Boolean = false
    var revealArmed: Boolean = false
    var hiddenBefore: Int = 0
    var scrubStartedAt: Long = 0L
    var pendingForwardFrom: Int = 0
    var pendingForwardUntil: Int = -1
    var focusAtScrubStart: Int = -1
    var intro: LyricSeekIntro = LyricSeekIntro.None
    var introFocus: Int = -1
    var seekOrigin: Int = -1
    var generation: Int = 0

    fun issue(map: Map<Int, Int>, index: Int, gather: Boolean): Map<Int, Int> {
        generation += 1
        val mag = generation
        return map + (index to if (gather) -mag else mag)
    }
    var seekTick: Int = 0
    var armedSeekTarget: Int = -1
    val gatherQueue = ArrayDeque<Int>()
    val scatterQueue = ArrayDeque<Int>()
}

private fun lyricRevealWindow(focus: Int, upcoming: Int, lineCount: Int): Set<Int> {
    if (lineCount <= 0 || focus !in 0 until lineCount) return emptySet()
    val end = (focus + upcoming).coerceAtMost(lineCount - 1)
    return (focus..end).toSet()
}

/**
 * 把一行歌词栅格化成软粒子。
 * [token] 为正：粒子停在字上再向上飘散，字本身不再回来。
 * [token] 为负：粒子从上方聚回字上，再把歌词留下来。
 */
@Composable
internal fun LyricParticleSlot(
    token: Int,
    primary: String,
    secondary: String?,
    color: Color,
    fontSizeSp: Float,
    lineHeightSp: Float,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
    letterSpacingSp: Float,
    secondaryScale: Float,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    onScatterStarted: () -> Unit = {},
    onScatterSettled: () -> Unit = {},
    onGatherSettled: () -> Unit = {},
    gatherDuration: Long = 600L,
    scatterDuration: Long = 920L,
    content: @Composable () -> Unit,
) {
    val particleFontWeight = fontWeight
    val gather = token < 0
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val singleLine = rememberLongLyricRender() == LongLyricRender.Horizontal
    var running by remember { mutableStateOf(false) }
    var glyphAlpha by remember { mutableFloatStateOf(1f) }
    var progress by remember { mutableFloatStateOf(0f) }
    var ink by remember { mutableStateOf<LyricInk?>(null) }
    var scatterDone by remember { mutableStateOf(false) }
    var gatherDone by remember { mutableStateOf(false) }
    val sprite = remember { lyricParticleSprite() }
    val tint = remember(color) { ColorFilter.tint(color.copy(alpha = 1f)) }
    val shownGlyph = when {
        token == 0 -> 1f
        token < 0 && (gatherDone || running) -> if (gatherDone) 1f else glyphAlpha
        token < 0 -> 0f
        scatterDone -> 0f
        running && progress >= 0.06f -> 0f
        else -> 1f
    }

    BoxWithConstraints(
        modifier.graphicsLayer { clip = false }.zIndex(if (token != 0) 1f else 0f),
    ) {
        val maxTextPx = with(density) {
            val inner = maxWidth - horizontalPadding * 2
            if (!maxWidth.value.isFinite() || inner <= 0.dp) 480.dp.roundToPx()
            else inner.roundToPx()
        }
        LaunchedEffect(token) {
        progress = 0f
        running = false
        scatterDone = false
        gatherDone = false
        ink = null
        glyphAlpha = if (token > 0) 1f else if (token < 0) 0f else 1f
        val textWidth = maxTextPx.coerceAtLeast(8)
        if (token == 0 || primary.isBlank()) {
            running = false
            glyphAlpha = 1f
            progress = 0f
            ink = null
            return@LaunchedEffect
        }
        val sampled = try {
            cachedLyricInk(
                primary = primary,
                secondary = secondary,
                color = color,
                fontSizeSp = fontSizeSp,
                lineHeightSp = lineHeightSp,
                fontWeight = particleFontWeight,
                letterSpacingSp = letterSpacingSp,
                secondaryScale = secondaryScale,
                maxWidthPx = textWidth,
                singleLine = singleLine,
            ) {
                sampleLyricInk(
                    measurer = measurer,
                    density = density,
                    primary = primary,
                    secondary = secondary?.takeIf { it.isNotBlank() },
                    color = color,
                    fontSizeSp = fontSizeSp,
                    lineHeightSp = lineHeightSp,
                    fontWeight = particleFontWeight,
                    fontStyle = fontStyle,
                    letterSpacingSp = letterSpacingSp,
                    secondaryScale = secondaryScale,
                    maxWidthPx = textWidth,
                    singleLine = singleLine,
                )
            }
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        if (sampled == null || sampled.seeds.isEmpty()) {
            running = false
            glyphAlpha = if (gather) 1f else 0f
            progress = 0f
            ink = null
            if (!gather) {
                scatterDone = true
                onScatterSettled()
            } else {
                gatherDone = true
                onGatherSettled()
            }
            return@LaunchedEffect
        }
        ink = sampled
        running = true
        progress = 0f
        glyphAlpha = if (gather) gatherGlyphAlpha(0f) else 1f
        if (!gather) onScatterStarted()
        val duration = if (gather) gatherDuration else scatterDuration
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - start) / 1_000_000f / duration.toFloat()).coerceIn(0f, 1f)
            progress = t
            glyphAlpha = if (gather) gatherGlyphAlpha(t) else 0f
            if (t >= 1f) break
        }
        running = false
        glyphAlpha = if (gather) 1f else 0f
        progress = 1f
        ink = null
        if (!gather) {
            scatterDone = true
            onScatterSettled()
        } else {
            gatherDone = true
            onGatherSettled()
        }
        }

        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = shownGlyph },
            ) {
                content()
            }
        }
        val drawing = ink
        if (running && drawing != null && drawing.seeds.isNotEmpty()) {
            val above = with(density) { 88.dp.roundToPx() }
            val below = with(density) { 40.dp.roundToPx() }
            Canvas(
                Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val slotH = constraints.maxHeight
                        val slotW = constraints.maxWidth
                        if (slotH == Constraints.Infinity || slotH <= 0 || slotW <= 0) {
                            val w = if (slotW == Constraints.Infinity) 0 else slotW.coerceAtLeast(0)
                            layout(w, 0) {}
                        } else {
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = slotW,
                                    minHeight = slotH + above + below,
                                    maxHeight = slotH + above + below,
                                ),
                            )
                            layout(slotW, slotH) { placeable.place(0, -above) }
                        }
                    },
            ) {
                val slotH = size.height - above - below
                val textLeft = (size.width - drawing.width) / 2f
                val textTop = above + (slotH - drawing.height) / 2f
                val riseBase = 28.dp.toPx()
                val riseSpan = 26.dp.toPx()
                val curlBase = 8.dp.toPx()
                val t = progress
                val move = if (gather) gatherMove(t) else scatterMove(t)
                val cloud = if (gather) gatherParticleAlpha(t) else scatterParticleAlpha(t)
                for (seed in drawing.seeds) {
                    val spread = if (gather) 1f - move else move
                    val rise = riseBase + riseSpan * seed.rise
                    val ox = sin(seed.phase + spread * 2.2f) * curlBase * spread
                    val oy = -rise * spread
                    val scale = if (gather) 0.5f + 0.5f * move else 1f - 0.5f * move
                    val diameter = (drawing.dot * 2.1f * scale).roundToInt().coerceAtLeast(2)
                    val cx = textLeft + seed.x + ox
                    val cy = textTop + seed.y + oy
                    val alpha = (cloud * (0.72f + 0.28f * seed.rise)).coerceIn(0f, 1f)
                    if (alpha < 0.02f) continue
                    drawImage(
                        image = sprite,
                        dstOffset = IntOffset(
                            (cx - diameter / 2f).roundToInt(),
                            (cy - diameter / 2f).roundToInt(),
                        ),
                        dstSize = IntSize(diameter, diameter),
                        alpha = alpha,
                        colorFilter = tint,
                    )
                }
            }
        }
    }
}

/**
 * 跟滚时把当前句以上裁掉。行高不变，已消散的句子不再露出来。
 */
internal fun Modifier.lyricConcealPast(enabled: Boolean, playSlotPx: Float): Modifier {
    if (!enabled) return this
    return this.drawWithContent {
        val top = ((size.height - playSlotPx) / 2f).coerceIn(0f, size.height)
        clipRect(top = top) {
            this@drawWithContent.drawContent()
        }
    }
}

private class LyricInk(
    val width: Float,
    val height: Float,
    val dot: Float,
    val seeds: List<InkSeed>,
)

private class InkSeed(
    val x: Float,
    val y: Float,
    val phase: Float,
    val rise: Float,
)

private fun scatterMove(t: Float): Float {
    val start = 0.14f
    if (t <= start) return 0f
    val u = ((t - start) / (1f - start)).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun scatterParticleAlpha(t: Float): Float {
    if (t < 0.2f) return 1f
    return (1f - (t - 0.2f) / 0.8f).coerceIn(0f, 1f)
}

private fun gatherMove(t: Float): Float {
    val u = (t / 0.7f).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun gatherParticleAlpha(t: Float): Float {
    val inn = (t / 0.1f).coerceIn(0f, 1f)
    val out = (1f - ((t - 0.58f) / 0.42f).coerceIn(0f, 1f))
    return inn * out
}

private fun gatherGlyphAlpha(t: Float): Float {
    return ((t - 0.46f) / 0.38f).coerceIn(0f, 1f)
}

private val lyricInkCache = LinkedHashMap<String, LyricInk>()

private fun cachedLyricInk(
    primary: String,
    secondary: String?,
    color: Color,
    fontSizeSp: Float,
    lineHeightSp: Float,
    fontWeight: FontWeight,
    letterSpacingSp: Float,
    secondaryScale: Float,
    maxWidthPx: Int,
    singleLine: Boolean,
    build: () -> LyricInk,
): LyricInk {
    val key = buildString {
        append(primary)
        append('|')
        append(secondary.orEmpty())
        append('|')
        append(fontSizeSp)
        append('|')
        append(lineHeightSp)
        append('|')
        append(fontWeight.weight)
        append('|')
        append(letterSpacingSp)
        append('|')
        append(secondaryScale)
        append('|')
        append(maxWidthPx)
        append('|')
        append(singleLine)
        append('|')
        append(color.value)
    }
    lyricInkCache[key]?.let { return it }
    val ink = build()
    if (lyricInkCache.size >= 48) {
        lyricInkCache.remove(lyricInkCache.keys.first())
    }
    if (ink.seeds.isNotEmpty()) lyricInkCache[key] = ink
    return ink
}

private fun sampleLyricInk(
    measurer: TextMeasurer,
    density: Density,
    primary: String,
    secondary: String?,
    color: Color,
    fontSizeSp: Float,
    lineHeightSp: Float,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
    letterSpacingSp: Float,
    secondaryScale: Float,
    maxWidthPx: Int,
    singleLine: Boolean,
): LyricInk {
    val widthPx = maxWidthPx.coerceIn(1, 1400)
    val primaryLayout = measurer.measure(
        text = primary,
        style = lyricInkStyle(color, fontSizeSp, lineHeightSp, fontWeight, fontStyle, letterSpacingSp),
        overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
        softWrap = !singleLine,
        maxLines = if (singleLine) 1 else 6,
        constraints = Constraints(maxWidth = widthPx),
    )
    val secondaryLayout = secondary?.let { line ->
        measurer.measure(
            text = line,
            style = lyricInkStyle(
                color,
                fontSizeSp * secondaryScale,
                lineHeightSp * secondaryScale,
                FontWeight.Medium,
                fontStyle,
                letterSpacingSp,
            ),
            overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
            softWrap = !singleLine,
            maxLines = if (singleLine) 1 else 4,
            constraints = Constraints(maxWidth = widthPx),
        )
    }
    val gap = if (secondaryLayout != null) 2f * density.density else 0f
    val w = maxOf(primaryLayout.size.width, secondaryLayout?.size?.width ?: 0).coerceAtLeast(1)
    val h = (primaryLayout.size.height + gap + (secondaryLayout?.size?.height ?: 0)).roundToInt()
        .coerceAtLeast(1)
    if (w > 1600 || h > 900) {
        return LyricInk(w.toFloat(), h.toFloat(), 4f, emptyList())
    }
    val bitmap = ImageBitmap(w, h, ImageBitmapConfig.Argb8888)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(bitmap),
        size = androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat()),
    ) {
        val primaryLeft = (w - primaryLayout.size.width) / 2f
        drawText(primaryLayout, topLeft = androidx.compose.ui.geometry.Offset(primaryLeft, 0f))
        if (secondaryLayout != null) {
            val secondaryLeft = (w - secondaryLayout.size.width) / 2f
            drawText(
                secondaryLayout,
                topLeft = androidx.compose.ui.geometry.Offset(
                    secondaryLeft,
                    primaryLayout.size.height + gap,
                ),
            )
        }
    }
    val pixels = IntArray(w * h)
    bitmap.readPixels(pixels)
    val stride = when {
        w * h < 9000 -> 2
        w * h < 24000 -> 3
        else -> 4
    }
    val seeds = ArrayList<InkSeed>(160)
    var y = stride / 2
    while (y < h) {
        var x = stride / 2
        while (x < w) {
            val alpha = (pixels[y * w + x] ushr 24) and 0xFF
            if (alpha > 48) {
                val phase = ((x * 13 + y * 29) % 628) / 100f
                val rise = 0.72f + ((x * 3 + y * 5) % 28) / 100f
                seeds += InkSeed(x.toFloat(), y.toFloat(), phase, rise)
            }
            x += stride
        }
        y += stride
    }
    val kept = if (seeds.size <= 170) {
        seeds
    } else {
        val step = seeds.size / 160
        seeds.filterIndexed { index, _ -> index % step == 0 }
    }
    return LyricInk(w.toFloat(), h.toFloat(), stride * 0.95f, kept)
}

private fun lyricInkStyle(
    color: Color,
    fontSizeSp: Float,
    lineHeightSp: Float,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
    letterSpacingSp: Float,
): TextStyle = TextStyle(
    color = color.copy(alpha = 1f),
    fontFamily = FontFamily.SansSerif,
    fontWeight = fontWeight,
    fontStyle = fontStyle,
    fontSize = fontSizeSp.coerceAtLeast(8f).sp,
    lineHeight = lineHeightSp.coerceAtLeast(fontSizeSp).sp,
    letterSpacing = letterSpacingSp.sp,
    textAlign = TextAlign.Center,
)

private fun lyricParticleSprite(): ImageBitmap {
    val side = 32
    val bitmap = ImageBitmap(side, side, ImageBitmapConfig.Argb8888)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(bitmap),
        size = androidx.compose.ui.geometry.Size(side.toFloat(), side.toFloat()),
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = androidx.compose.ui.geometry.Offset(16f, 16f),
                radius = 16f,
            ),
            radius = 16f,
            center = androidx.compose.ui.geometry.Offset(16f, 16f),
        )
    }
    return bitmap
}
