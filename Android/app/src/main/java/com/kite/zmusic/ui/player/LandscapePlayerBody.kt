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


/** 右侧悬浮板离场：滑过面板宽 + 右缝，避免缝里还留一条再被卸掉。 */
internal fun landscapeSideSheetSlideX(
    progress: Float,
    panelWidthPx: Float,
    endPadPx: Float,
): Float = (1f - progress.coerceIn(0f, 1f)) * (panelWidthPx + endPadPx)

@Composable
internal fun LandscapePlayerBody(
    track: TrackRow,
    lines: List<LrcLine>,
    positionMs: Long,
    seekPositionMs: Long,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    buffering: Boolean,
    loadPending: Boolean,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    durationMs: Long,
    sourceTitle: String?,
    onSourceClick: (() -> Unit)?,
    onArtistClick: (() -> Unit)? = null,
    sliderDragging: Boolean,
    sliderValue: Float,
    onSliderDragStart: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderDragEnd: (Float) -> Unit,
    playbackMode: PlaybackMode,
    onCyclePlaybackMode: () -> Unit,
    trackLiked: Boolean,
    onToggleLike: () -> Unit,
    onDismiss: () -> Unit,
    dismissSwipeThresholdPx: Float,
    displayPrefs: PlayerDisplayPrefs,
    onDisplayPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onDisplayPrefsFlush: () -> Unit,
    settingsHazeState: HazeState,
    peekNextTrack: TrackRow?,
    peekPrevTrack: TrackRow?,
    onSeek: (Long) -> Unit,
    queue: List<TrackRow>,
    queueIndex: Int,
    onPlayQueueIndex: (Int) -> Unit,
    onNeedQueueThrough: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    notice: PlaybackNotice? = null,
    transportWakeToken: Int = 0,
) {
    val activity = LocalActivity.current
    val rotationLock = com.kite.zmusic.ui.orientation.LocalSessionRotationLock.current
    // 直接读 Store，保证锁状态变化触发重组（不依赖 @Stable 门面推断）
    val rotationLocked = com.kite.zmusic.ui.orientation.SessionRotationLockStore.locked
    val systemAutoRotate =
        com.kite.zmusic.ui.orientation.rememberSystemAutoRotateEnabled()
    // 沉浸默认隐藏；仅纯点击唤出，滑动不唤出（常显时恒定展开）
    var controlsVisible by remember { mutableStateOf(displayPrefs.transportAlwaysVisible) }
    var settingsOpen by remember { mutableStateOf(false) }
    val settingsTransferDismissGate = remember { PlayerDisplayTransferDismissGate() }
    var scoreOpen by remember { mutableStateOf(false) }
    /** 曲谱展开：强制黑胶垂直居中（忽略个性化 Y，保留 X）；收窗动画结束后再松开 */
    var scoreVinylCentered by remember { mutableStateOf(false) }
    /** 曲谱加宽覆盖黑胶：左/右边距对称 = chromeSidePad */
    var scoreCoverExpanded by remember { mutableStateOf(false) }
    /** 每次打开曲谱递增，网格首帧直接落在当前曲 */
    var scoreOpenGeneration by remember { mutableIntStateOf(0) }
    var scoreFlight by remember { mutableStateOf<ScoreVinylFlight?>(null) }
    var suppressVinylEnter by remember { mutableStateOf(false) }
    var mainVinylCenterRoot by remember { mutableStateOf(Offset.Zero) }
    var mainVinylSizePx by remember { mutableFloatStateOf(0f) }
    /** 切歌时递增，重挂 hazeEffect 恢复模糊采样（设置/选句用；曲谱不再 remount） */
    var hazeNonce by remember { mutableIntStateOf(0) }
    var vinylColorEditorOpen by remember { mutableStateOf(false) }
    /** 编辑态黑胶居中锁：进入时立刻开启；退出时等弹窗收完再关，与弹窗错开 */
    var editorVinylCentered by remember { mutableStateOf(false) }
    /** 黑胶选歌：强制 Y 居中（保留 X），与曲谱/自选编辑互斥 */
    var pickerVinylCentered by remember { mutableStateOf(false) }
    var vinylSongPickOpen by remember { mutableStateOf(false) }
    var vinylSongPickPhase by remember { mutableStateOf(VinylSongPickPhase.Entering) }
    /** 选歌叠层是否已盖住主黑胶（取消退场期间保持，直至交接） */
    var pickOverlayCoversMain by remember { mutableStateOf(false) }
    var pickTargetIndex by remember { mutableIntStateOf(0) }
    /** 每次长按打开递增，强制选歌层按当前播放位置重建，避免沿用上次会话 */
    var pickSessionGeneration by remember { mutableIntStateOf(0) }
    /** 打开瞬间冻结的歌单/锚点（勿跟随后续切歌/手势） */
    var pickSessionQueue by remember { mutableStateOf<List<TrackRow>>(emptyList()) }
    var pickSessionAnchor by remember { mutableIntStateOf(0) }
    LaunchedEffect(queue.size, vinylSongPickOpen) {
        if (vinylSongPickOpen && queue.size > pickSessionQueue.size) {
            pickSessionQueue = mergePlaylistQueue(pickSessionQueue, queue)
        }
    }
    var pendingPickPlayIndex by remember { mutableStateOf<Int?>(null) }
    /** 确认切歌后：主黑胶已换新曲但仍被选歌交接盘盖住，用于预热封面避免闪旧曲 */
    var pickRevealMainUnderHandoff by remember { mutableStateOf(false) }
    /** 选歌吸附锚点：主黑胶实测中心/尺寸（含个性化），进入居中后锁定 */
    var pickAnchorCenterRoot by remember { mutableStateOf(Offset.Zero) }
    var pickAnchorSizePx by remember { mutableFloatStateOf(0f) }
    /** 黑胶舞台当前落定曲（手势提交后可能比 playback index 更早） */
    var vinylSettledTrack by remember { mutableStateOf(track) }
    LaunchedEffect(track.id) {
        // 外部切歌：舞台回调前先对齐；若手势已领先到同曲则保持
        if (vinylSettledTrack.id != track.id) {
            vinylSettledTrack = track
        }
    }
    var reopenSettingsAfterEditor by remember { mutableStateOf(false) }
    var lyricStyleEditorOpen by remember { mutableStateOf(false) }
    var reopenSettingsAfterLyricStyle by remember { mutableStateOf(false) }
    var lyricStyleSnapshot by remember { mutableStateOf<LyricStyleSnapshot?>(null) }
    var draftLyricPlaying by remember { mutableStateOf(LyricRoleStyle.PlayingDefault) }
    var draftLyricPlayed by remember { mutableStateOf(LyricRoleStyle.PlayedDefault) }
    var draftLyricUnplayed by remember { mutableStateOf(LyricRoleStyle.UnplayedDefault) }
    /** 编辑期间冻结真歌词进度，关闭交接时与克隆同位 */
    var lyricStyleFrozenPositionMs by remember { mutableLongStateOf(0L) }
    var titleStyleEditorOpen by remember { mutableStateOf(false) }
    var reopenSettingsAfterTitleStyle by remember { mutableStateOf(false) }
    var titleStyleSnapshot by remember { mutableStateOf<TitleStyleSnapshot?>(null) }
    var draftTitleName by remember { mutableStateOf(TitleLineStyle.NameDefault) }
    var draftTitleArtist by remember { mutableStateOf(TitleLineStyle.ArtistDefault) }
    var draftTitleSource by remember { mutableStateOf(TitleLineStyle.SourceDefault) }
    var playerRootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lyricsBandCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var songMetaVisualBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var idleBump by remember { mutableIntStateOf(0) }
    var vinylSkipDir by remember { mutableStateOf(VinylSkipDirection.Next) }
    var vinylBusy by remember { mutableStateOf(false) }
    var lyricResumeToken by remember { mutableIntStateOf(0) }
    var lyricSelectOpen by remember { mutableStateOf(false) }
    var lyricSelectOutsideArmed by remember { mutableStateOf(false) }
    val lyricSelectSelected: SnapshotStateSet<Int> = remember { mutableStateSetOf() }
    // 未预加载 / URL 解析中：控件锁定，显示加载
    val controlsLocked = loadPending
    val transportBuffering = buffering || loadPending
    val transportPinned = displayPrefs.transportAlwaysVisible
    val forceVinylYCentered = editorVinylCentered || scoreVinylCentered || scoreFlight != null ||
        pickerVinylCentered
    // 设置 / 曲谱 / 自选编辑 / 选句 / 黑胶选歌与底部播放条互斥；编辑时强制隐藏（忽略常显）
    val showBar = (controlsVisible || sliderDragging || transportPinned) &&
        !settingsOpen &&
        !scoreOpen &&
        !forceVinylYCentered &&
        !lyricSelectOpen &&
        !lyricStyleEditorOpen &&
        !titleStyleEditorOpen &&
        !vinylSongPickOpen
    val density = LocalDensity.current
    val uiScale = displayPrefs.uiScale.coerceIn(PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX)
    val settingsCurve = remember { CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f) }
    val vinylCenterEasing = remember { CubicBezierEasing(0.33f, 0f, 0.2f, 1f) }
    val vinylCenterMs = 480
    val pickScope = rememberCoroutineScope()

    LaunchedEffect(transportPinned, forceVinylYCentered) {
        if (transportPinned && !forceVinylYCentered) controlsVisible = true
    }

    // 0 = 沉浸（黑胶放大）→ 1 = 控件可见（黑胶缩小让位）；Animatable 可中途改目标打断
    val chrome = remember { Animatable(if (transportPinned) 1f else 0f) }
    LaunchedEffect(showBar) {
        chrome.animateTo(
            targetValue = if (showBar) 1f else 0f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
    }
    val chromeT = chrome.value

    val settingsPanel = remember { Animatable(0f) }
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) {
            // 走 showBar → chrome.animateTo，保留播放组件消失与黑胶缩放动画
            controlsVisible = false
            settingsPanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 460, easing = settingsCurve),
            )
        } else {
            settingsPanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 460, easing = settingsCurve),
            )
            // 面板收完再恢复常显 chrome，避免图标露在 OutsideDismiss 下面点不中
            if (transportPinned &&
                !forceVinylYCentered &&
                !lyricSelectOpen &&
                !scoreOpen &&
                !lyricStyleEditorOpen &&
                !titleStyleEditorOpen &&
                !vinylSongPickOpen
            ) {
                controlsVisible = true
            }
        }
    }
    val settingsT = settingsPanel.value

    // 曲谱：与设置同曲线展开；黑胶 Y 居中与弹窗同开同收，全程有动画
    val scorePanel = remember { Animatable(0f) }
    val scoreCoverAnim = remember { Animatable(0f) }
    LaunchedEffect(scoreOpen) {
        if (scoreOpen) {
            scorePanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 460, easing = settingsCurve),
            )
        } else {
            scoreCoverExpanded = false
            scorePanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 460, easing = settingsCurve),
            )
            scoreCoverAnim.snapTo(0f)
            // 飞入进行中保持黑胶居中，飞完再松
            if (scoreFlight == null) {
                scoreVinylCentered = false
                if (transportPinned &&
                    !settingsOpen &&
                    !editorVinylCentered &&
                    !lyricSelectOpen &&
                    !lyricStyleEditorOpen &&
                    !titleStyleEditorOpen
                ) {
                    controlsVisible = true
                }
            }
        }
    }
    val scoreT = scorePanel.value
    // 勿在此读取 scoreCoverAnim.value：加宽动画会整页重组合；宽度读数下沉到曲谱面板子树

    // 切歌 / 加载结束：设置等面板重挂磨砂，避免源内容突变后退回纯色 fallback
    fun hazePanelsOpen(): Boolean =
        settingsOpen ||
            lyricSelectOpen ||
            vinylColorEditorOpen ||
            editorVinylCentered ||
            lyricStyleEditorOpen ||
            titleStyleEditorOpen ||
            scoreOpen ||
            vinylSongPickOpen

    LaunchedEffect(track.id) {
        if (lyricSelectOpen) {
            lyricSelectSelected.clear()
        }
        if (!hazePanelsOpen()) return@LaunchedEffect
        delay(64)
        hazeNonce++
        delay(280)
        hazeNonce++
    }

    var mediaWasLoading by remember { mutableStateOf(loadPending || buffering) }
    LaunchedEffect(loadPending, buffering) {
        val loading = loadPending || buffering
        val finishedLoading = mediaWasLoading && !loading
        mediaWasLoading = loading
        if (!finishedLoading || !hazePanelsOpen()) return@LaunchedEffect
        // 封面/黑胶等源层绘制就绪后再采样
        delay(48)
        hazeNonce++
        delay(220)
        hazeNonce++
    }

    val editorPanel = remember { Animatable(0f) }
    LaunchedEffect(vinylColorEditorOpen) {
        if (vinylColorEditorOpen) {
            // 先等黑胶居中就位，再渐显弹窗
            delay(vinylCenterMs.toLong())
            if (!vinylColorEditorOpen) return@LaunchedEffect
            editorPanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = vinylCenterMs, easing = vinylCenterEasing),
            )
        } else {
            // 先收弹窗，再在完成后松开黑胶居中（见下方）
            editorPanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = vinylCenterMs, easing = vinylCenterEasing),
            )
            editorVinylCentered = false
            if (reopenSettingsAfterEditor) {
                reopenSettingsAfterEditor = false
                controlsVisible = false
                settingsOpen = true
            } else if (transportPinned) {
                controlsVisible = true
            }
        }
    }
    val editorT = editorPanel.value

    val lyricStylePanel = remember { Animatable(0f) }
    LaunchedEffect(lyricStyleEditorOpen) {
        if (lyricStyleEditorOpen) {
            // 1) 钉源位 2) 等克隆盖住 3) 再关设置（避免关面板触发的绝对居中跳动露馅）
            lyricStylePanel.snapTo(0f)
            yield()
            yield()
            if (!lyricStyleEditorOpen) return@LaunchedEffect
            settingsOpen = false
            controlsVisible = false
            // 设置收起动画期间继续钉在源位，再启程飞入预览槽
            delay(280)
            if (!lyricStyleEditorOpen) return@LaunchedEffect
            lyricStylePanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 920, easing = LyricStylePanelEasing),
            )
        } else {
            lyricStylePanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 780, easing = LyricStylePanelEasing),
            )
            // 回位后短暂停在重叠态，完成交叉淡出再卸克隆
            if (lyricStyleSnapshot != null) {
                delay(280)
            }
            if (reopenSettingsAfterLyricStyle) {
                reopenSettingsAfterLyricStyle = false
                controlsVisible = false
                settingsOpen = true
            } else if (transportPinned &&
                !settingsOpen &&
                !editorVinylCentered &&
                !lyricSelectOpen &&
                !scoreOpen
            ) {
                controlsVisible = true
            }
            if (!lyricStyleEditorOpen) {
                lyricStyleSnapshot = null
            }
        }
    }
    val lyricStyleT = lyricStylePanel.value
    // 关闭回位末段：真歌词与克隆更长交叉淡出
    val lyricStyleHandoffT = if (
        !lyricStyleEditorOpen &&
        lyricStyleSnapshot != null
    ) {
        ((0.45f - lyricStyleT) / 0.45f).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 开场：克隆立刻盖住源位（alpha=1），真歌词隐藏；morph 前段钉源位，盖住关设置后的布局空缺
    val liveLyricAlpha = when {
        lyricStyleSnapshot == null -> 1f
        lyricStyleEditorOpen -> 0f
        else -> lyricStyleHandoffT
    }
    val styleCloneAlpha = when {
        lyricStyleSnapshot == null -> 0f
        lyricStyleEditorOpen -> 1f
        else -> (1f - lyricStyleHandoffT).coerceIn(0f, 1f)
    }

    val titleStylePanel = remember { Animatable(0f) }
    LaunchedEffect(titleStyleEditorOpen) {
        if (titleStyleEditorOpen) {
            // 先钉在源位，再慢启程，避免开场插值跳位/瞬移
            titleStylePanel.snapTo(0f)
            delay(90)
            if (!titleStyleEditorOpen) return@LaunchedEffect
            titleStylePanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 860, easing = TitleStylePanelEasing),
            )
        } else {
            titleStylePanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 780, easing = TitleStylePanelEasing),
            )
            if (titleStyleSnapshot != null) {
                delay(280)
            }
            if (reopenSettingsAfterTitleStyle) {
                reopenSettingsAfterTitleStyle = false
                controlsVisible = false
                settingsOpen = true
            } else if (transportPinned &&
                !settingsOpen &&
                !editorVinylCentered &&
                !lyricSelectOpen &&
                !scoreOpen &&
                !lyricStyleEditorOpen
            ) {
                controlsVisible = true
            }
            if (!titleStyleEditorOpen) {
                titleStyleSnapshot = null
            }
        }
    }
    val titleStyleT = titleStylePanel.value
    // 关闭回位末段：真标题与克隆更长交叉淡出，避免回弹突兀
    val titleStyleHandoffT = if (
        !titleStyleEditorOpen &&
        titleStyleSnapshot != null
    ) {
        ((0.45f - titleStyleT) / 0.45f).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 开场：克隆先淡入钉在源位，真标题慢消；morph 行程见 titleStyleMorphT
    val liveTitleMetaAlpha = when {
        titleStyleSnapshot == null -> 1f
        titleStyleEditorOpen -> (1f - titleStyleT / 0.24f).coerceIn(0f, 1f)
        else -> titleStyleHandoffT
    }
    val titleStyleCloneAlpha = when {
        titleStyleSnapshot == null -> 0f
        titleStyleEditorOpen -> (titleStyleT / 0.14f).coerceIn(0f, 1f)
        else -> (1f - titleStyleHandoffT).coerceIn(0f, 1f)
    }

    val vinylSongPickPanel = remember { Animatable(0f) }
    var vinylSongPickEverOpen by remember { mutableStateOf(false) }
    LaunchedEffect(vinylSongPickOpen) {
        if (vinylSongPickOpen) {
            vinylSongPickEverOpen = true
            // Entering 期间不挂雾气叠层，主黑胶保持清晰居中；Stacking 起再淡入蒙版
        } else {
            if (!vinylSongPickEverOpen) return@LaunchedEffect
            vinylSongPickPanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            )
            // 确认切歌已在离场结束时提前执行；此处仅兜底（若仍挂着 pending）
            val play = pendingPickPlayIndex
            pendingPickPlayIndex = null
            if (play != null) {
                onPlayQueueIndex(play)
            }
            pickerVinylCentered = false
            delay(120)
            suppressVinylEnter = false
            pickRevealMainUnderHandoff = false
            vinylSongPickEverOpen = false
            vinylSongPickPhase = VinylSongPickPhase.Entering
            if (transportPinned &&
                !settingsOpen &&
                !editorVinylCentered &&
                !lyricSelectOpen &&
                !scoreOpen &&
                !lyricStyleEditorOpen &&
                !titleStyleEditorOpen
            ) {
                controlsVisible = true
            }
        }
    }
    // Stacking 起再挂蒙版：盘在蒙版上，Entering 主盘不被盖住
    LaunchedEffect(vinylSongPickOpen, vinylSongPickPhase) {
        if (!vinylSongPickOpen) return@LaunchedEffect
        if (vinylSongPickPhase == VinylSongPickPhase.Entering) return@LaunchedEffect
        vinylSongPickPanel.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 380, easing = vinylCenterEasing),
        )
    }
    // Entering → Stacking：等 Y 居中 + 旋转归正，再堆叠（避免角/位瞬移）
    LaunchedEffect(vinylSongPickOpen, vinylSongPickPhase) {
        if (!vinylSongPickOpen || vinylSongPickPhase != VinylSongPickPhase.Entering) return@LaunchedEffect
        delay(maxOf(vinylCenterMs, 520).toLong() + 48L)
        if (vinylSongPickOpen && vinylSongPickPhase == VinylSongPickPhase.Entering) {
            if (mainVinylCenterRoot.x > 1f && mainVinylSizePx > 8f) {
                pickAnchorCenterRoot = mainVinylCenterRoot
                pickAnchorSizePx = mainVinylSizePx
            }
            vinylSongPickPhase = VinylSongPickPhase.Stacking
        }
    }
    // 叠层盖住主盘后保持隐藏标记，取消退场时不提前露主盘
    LaunchedEffect(vinylSongPickPhase) {
        when (vinylSongPickPhase) {
            VinylSongPickPhase.Entering -> pickOverlayCoversMain = false
            VinylSongPickPhase.Stacking,
            VinylSongPickPhase.FanOut,
            VinylSongPickPhase.Browsing,
            VinylSongPickPhase.Confirming,
            -> pickOverlayCoversMain = true
            VinylSongPickPhase.Canceling -> Unit
        }
    }
    val vinylSongPickT = vinylSongPickPanel.value
    // 选歌打开：非黑胶 UI 随雾气淡出
    val pickUiFade = (1f - vinylSongPickT).coerceIn(0f, 1f)

    val lyricSelectPanel = remember { Animatable(0f) }
    var lyricSelectEverOpen by remember { mutableStateOf(false) }
    LaunchedEffect(lyricSelectOpen) {
        if (lyricSelectOpen) {
            lyricSelectEverOpen = true
            lyricSelectPanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = vinylCenterMs, easing = vinylCenterEasing),
            )
        } else {
            // 一开始就触发回滚，与收窗/样式 morph 并行，避免收完再突然跳位
            if (lyricSelectEverOpen) {
                lyricResumeToken++
            }
            lyricSelectPanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = vinylCenterMs, easing = vinylCenterEasing),
            )
                if (lyricSelectEverOpen) {
                    lyricSelectEverOpen = false
                    if (transportPinned && !settingsOpen && !editorVinylCentered && !scoreOpen &&
                        !lyricStyleEditorOpen && !titleStyleEditorOpen
                    ) {
                        controlsVisible = true
                    }
                }
        }
    }
    val lyricSelectT = lyricSelectPanel.value

    fun closeSettings() {
        onDisplayPrefsFlush()
        settingsOpen = false
        // 常显 chrome 改由 settingsPanel 收完后再亮，见 LaunchedEffect(settingsOpen)
    }

    fun commitLyricStyleDraft() {
        val next = displayPrefs.copy(
            lyricPlayingStyle = draftLyricPlaying,
            lyricPlayedStyle = draftLyricPlayed,
            lyricUnplayedStyle = draftLyricUnplayed,
        )
        if (next != displayPrefs) {
            onDisplayPrefsChange(next)
        }
    }

    fun commitTitleStyleDraft() {
        val next = displayPrefs.copy(
            titleNameStyle = draftTitleName,
            titleArtistStyle = draftTitleArtist,
            titleSourceStyle = draftTitleSource,
        )
        if (next != displayPrefs) {
            onDisplayPrefsChange(next)
        }
    }

    fun closeLyricStyleEditor() {
        reopenSettingsAfterLyricStyle = false
        commitLyricStyleDraft()
        lyricStyleEditorOpen = false
    }

    fun closeLyricStyleEditorToSettings() {
        reopenSettingsAfterLyricStyle = true
        commitLyricStyleDraft()
        lyricStyleEditorOpen = false
    }

    fun closeTitleStyleEditor() {
        reopenSettingsAfterTitleStyle = false
        commitTitleStyleDraft()
        titleStyleEditorOpen = false
    }

    fun closeTitleStyleEditorToSettings() {
        reopenSettingsAfterTitleStyle = true
        commitTitleStyleDraft()
        titleStyleEditorOpen = false
    }

    fun openLyricStyleEditor() {
        if (vinylColorEditorOpen || editorVinylCentered || lyricSelectOpen ||
            scoreOpen || scoreVinylCentered || scoreFlight != null || titleStyleEditorOpen ||
            vinylSongPickOpen || pickerVinylCentered
        ) {
            return
        }
        val root = playerRootCoords
        val lyric = lyricsBandCoords
        if (root == null || lyric == null || !root.isAttached || !lyric.isAttached) return
        // 与弹窗同坐标系：用视觉 bounds（含 uiScale），开场必能盖住真歌词
        val bandBounds = lyric.boundsInRoot()
        val rootBounds = root.boundsInRoot()
        val srcLeft = with(density) { (bandBounds.left - rootBounds.left).toDp() }
        val srcTop = with(density) { (bandBounds.top - rootBounds.top).toDp() }
        val srcW = with(density) { bandBounds.width.toDp() }
        val srcH = with(density) { bandBounds.height.toDp() }
        val animActive = lyricAnimActiveIndex(lines, positionMs, durationMs)
        val focus = lyricFocusIndex(lines, animActive)
        // 先冻结源位几何并挂上克隆，再关设置，避免关面板瞬间布局跳动露馅
        lyricStyleFrozenPositionMs = positionMs
        draftLyricPlaying = displayPrefs.lyricPlayingStyle
        draftLyricPlayed = displayPrefs.lyricPlayedStyle
        draftLyricUnplayed = displayPrefs.lyricUnplayedStyle
        reopenSettingsAfterLyricStyle = false
        lyricStyleSnapshot = LyricStyleSnapshot(
            lines = lines,
            focusIndex = focus,
            playedCount = displayPrefs.lyricPlayedCount,
            upcomingCount = displayPrefs.lyricUpcomingCount,
            lineSpacingDp = displayPrefs.lyricLineSpacingDp,
            sourceLeftDp = srcLeft,
            sourceTopDp = srcTop,
            sourceWidthDp = srcW.coerceAtLeast(48.dp),
            sourceHeightDp = srcH.coerceAtLeast(48.dp),
        )
        lyricStyleEditorOpen = true
        // 关设置推迟到克隆盖住源位之后（见 LaunchedEffect），避免瞬间绝对居中
    }

    fun openTitleStyleEditor() {
        if (vinylColorEditorOpen || editorVinylCentered || lyricSelectOpen ||
            scoreOpen || scoreVinylCentered || scoreFlight != null || lyricStyleEditorOpen ||
            vinylSongPickOpen || pickerVinylCentered
        ) {
            return
        }
        val root = playerRootCoords
        val meta = songMetaVisualBoundsInRoot
        if (root == null || meta == null || !root.isAttached) return
        if (meta.width <= 1f || meta.height <= 1f) return
        val rootBounds = root.boundsInRoot()
        val srcLeft = with(density) { (meta.left - rootBounds.left).toDp() }
        val srcTop = with(density) { (meta.top - rootBounds.top).toDp() }
        val srcW = with(density) { meta.width.toDp() }
        val srcH = with(density) { meta.height.toDp() }
        settingsOpen = false
        controlsVisible = false
        reopenSettingsAfterTitleStyle = false
        draftTitleName = displayPrefs.titleNameStyle
        draftTitleArtist = displayPrefs.titleArtistStyle
        draftTitleSource = displayPrefs.titleSourceStyle
        titleStyleSnapshot = TitleStyleSnapshot(
            name = track.name,
            artists = track.artists,
            sourceTitle = sourceTitle,
            sourceLeftDp = srcLeft,
            sourceTopDp = srcTop,
            sourceWidthDp = srcW.coerceAtLeast(48.dp),
            sourceHeightDp = srcH.coerceAtLeast(32.dp),
            centerAligned = displayPrefs.titleAlign != TitleAlignMode.LEFT,
        )
        titleStyleEditorOpen = true
    }

    fun closeScore() {
        scoreOpen = false
        scoreCoverExpanded = false
    }

    fun openScore() {
        if (editorVinylCentered || vinylColorEditorOpen || lyricSelectOpen || settingsOpen ||
            scoreFlight != null || lyricStyleEditorOpen || titleStyleEditorOpen ||
            vinylSongPickOpen || pickerVinylCentered
        ) {
            return
        }
        controlsVisible = false
        scoreCoverExpanded = false
        scoreOpenGeneration++
        scoreVinylCentered = true
        scoreOpen = true
    }

    fun startScoreFlight(index: Int, center: Offset, sizePx: Float) {
        val t = queue.getOrNull(index) ?: return
        if (index == queueIndex) {
            closeScore()
            return
        }
        // 拒绝无效起点，避免从左上角 (0,0) 飞入
        if (center.x < 1f || center.y < 1f || sizePx < 8f) return
        suppressVinylEnter = true
        scoreFlight = ScoreVinylFlight(
            track = t,
            queueIndex = index,
            startCenter = center,
            startSizePx = sizePx,
        )
        closeScore()
    }

    fun finishScoreFlight() {
        scoreFlight = null
        scoreVinylCentered = false
        suppressVinylEnter = false
        if (transportPinned && !settingsOpen && !editorVinylCentered && !lyricSelectOpen &&
            !lyricStyleEditorOpen && !titleStyleEditorOpen
        ) {
            controlsVisible = true
        }
    }

    fun closeVinylColorEditor() {
        reopenSettingsAfterEditor = false
        vinylColorEditorOpen = false
    }

    /** 左上角返回：弹窗收完后再打开设置页 */
    fun closeVinylColorEditorToSettings() {
        reopenSettingsAfterEditor = true
        vinylColorEditorOpen = false
    }

    fun openVinylColorEditor() {
        // 收回设置与播放条（忽略常显）；保留黑胶 X，Y 由编辑态强制垂直居中
        if (lyricSelectOpen || scoreOpen || scoreVinylCentered ||
            lyricStyleEditorOpen || titleStyleEditorOpen ||
            vinylSongPickOpen || pickerVinylCentered
        ) {
            return
        }
        settingsOpen = false
        controlsVisible = false
        reopenSettingsAfterEditor = false
        if (displayPrefs.vinylColorStyle != VinylColorStyle.CUSTOM) {
            onDisplayPrefsChange(displayPrefs.copy(vinylColorStyle = VinylColorStyle.CUSTOM))
        }
        editorVinylCentered = true
        vinylColorEditorOpen = true
    }

    fun closeLyricSelect() {
        lyricSelectSelected.clear()
        lyricSelectOpen = false
        lyricSelectOutsideArmed = false
    }

    @Suppress("UNUSED_PARAMETER")
    fun openLyricSelect(index: Int) {
        if (vinylColorEditorOpen || editorVinylCentered || scoreOpen || scoreVinylCentered ||
            lyricStyleEditorOpen || vinylSongPickOpen || pickerVinylCentered
        ) {
            return
        }
        settingsOpen = false
        controlsVisible = false
        // 进入时不预选；由用户再点选
        lyricSelectSelected.clear()
        lyricSelectOutsideArmed = false
        lyricSelectOpen = true
    }

    fun openSettings() {
        // 互斥：收回下方播放组件
        if (editorVinylCentered || vinylColorEditorOpen || lyricSelectOpen ||
            scoreOpen || scoreVinylCentered || scoreFlight != null ||
            lyricStyleEditorOpen || titleStyleEditorOpen ||
            vinylSongPickOpen || pickerVinylCentered
        ) {
            return
        }
        // 先开面板；chrome 收回见上方 LaunchedEffect(settingsOpen)
        settingsOpen = true
    }

    fun openVinylSongPick() {
        if (!displayPrefs.vinylSongPickEnabled) return
        if (vinylSongPickOpen || pickerVinylCentered) return
        if (editorVinylCentered || vinylColorEditorOpen || lyricSelectOpen || settingsOpen ||
            scoreOpen || scoreVinylCentered || scoreFlight != null ||
            lyricStyleEditorOpen || titleStyleEditorOpen
        ) {
            return
        }
        controlsVisible = false
        settingsOpen = false
        pendingPickPlayIndex = null
        pickRevealMainUnderHandoff = false
        // 打开瞬间快照：优先按舞台已落定黑胶（手势切歌会先于 queueIndex 更新）
        val snapQueue = queue.toList()
        val bySettled = snapQueue.indexOfFirst { it.id == vinylSettledTrack.id }
        val snapIndex = when {
            bySettled >= 0 -> bySettled
            else -> queueIndex.coerceIn(0, (snapQueue.size - 1).coerceAtLeast(0))
        }
        pickSessionQueue = snapQueue
        pickSessionAnchor = snapIndex
        pickTargetIndex = snapIndex
        pickSessionGeneration++
        // 先记下当前实测位置；居中动画结束后再刷新一次
        if (mainVinylCenterRoot.x > 1f) {
            pickAnchorCenterRoot = mainVinylCenterRoot
            pickAnchorSizePx = mainVinylSizePx
        }
        vinylSongPickPhase = VinylSongPickPhase.Entering
        // 长按判定当下立刻锁 Y，避免松手误唤醒播放条
        pickerVinylCentered = true
        vinylSongPickOpen = true
    }

    fun cancelVinylSongPick() {
        if (!vinylSongPickOpen && vinylSongPickT <= 0.001f) return
        if (vinylSongPickPhase == VinylSongPickPhase.Confirming) return
        if (vinylSongPickPhase == VinylSongPickPhase.Canceling) return
        // 先播退场动画，结束后再消雾 + 解除居中（与确认路径对称）
        vinylSongPickPhase = VinylSongPickPhase.Canceling
        pendingPickPlayIndex = null
        pickRevealMainUnderHandoff = false
    }

    fun confirmVinylSongPick() {
        if (!vinylSongPickOpen || vinylSongPickPhase != VinylSongPickPhase.Browsing) return
        // 先播离场动画，结束后再消雾 + 切歌
        vinylSongPickPhase = VinylSongPickPhase.Confirming
    }

    fun finishVinylSongPickCancelExit() {
        if (vinylSongPickPhase != VinylSongPickPhase.Canceling) return
        // 交接盘保持不透明到叠层卸完；主盘先在其下就位，再消雾
        pickScope.launch {
            pickRevealMainUnderHandoff = true
            withFrameNanos { }
            withFrameNanos { }
            withFrameNanos { }
            if (vinylSongPickPhase != VinylSongPickPhase.Canceling) return@launch
            vinylSongPickOpen = false
        }
    }

    fun finishVinylSongPickConfirmExit() {
        if (vinylSongPickPhase != VinylSongPickPhase.Confirming) return
        val play = pickTargetIndex
        pendingPickPlayIndex = null
        suppressVinylEnter = true
        onPlayQueueIndex(play)
        pickScope.launch {
            withFrameNanos { }
            withFrameNanos { }
            if (vinylSongPickPhase != VinylSongPickPhase.Confirming) return@launch
            pickRevealMainUnderHandoff = true
            withFrameNanos { }
            withFrameNanos { }
            withFrameNanos { }
            if (vinylSongPickPhase != VinylSongPickPhase.Confirming) return@launch
            vinylSongPickOpen = false
        }
    }

    fun revealControls() {
        if (settingsOpen || forceVinylYCentered || vinylColorEditorOpen ||
            lyricSelectOpen || scoreOpen || lyricStyleEditorOpen || titleStyleEditorOpen ||
            vinylSongPickOpen
        ) {
            return
        }
        controlsVisible = true
        idleBump++
    }

    fun toggleControls() {
        // 只用 Open 意图拦截；Animatable 收起尾帧的 *T 残留不再把单击变成空操作
        if (vinylSongPickOpen) {
            return
        }
        if (lyricSelectOpen) {
            if (lyricSelectOutsideArmed) closeLyricSelect()
            return
        }
        if (lyricStyleEditorOpen) {
            closeLyricStyleEditor()
            return
        }
        if (titleStyleEditorOpen) {
            closeTitleStyleEditor()
            return
        }
        if (vinylColorEditorOpen || editorVinylCentered) {
            closeVinylColorEditor()
            return
        }
        if (scoreOpen) {
            closeScore()
            return
        }
        if (settingsOpen) {
            closeSettings()
            return
        }
        if (transportPinned) {
            // 常显：刷新空闲计时，避免「点了没反应」的体感
            idleBump++
            return
        }
        if (controlsVisible) {
            controlsVisible = false
        } else {
            revealControls()
        }
    }

    // 播放失败重试：唤醒底部播放组件
    LaunchedEffect(transportWakeToken) {
        if (transportWakeToken > 0) {
            if (lyricSelectOpen) {
                lyricSelectSelected.clear()
                lyricSelectOpen = false
                lyricSelectOutsideArmed = false
                lyricSelectPanel.snapTo(0f)
            }
            if (lyricStyleEditorOpen || lyricStyleT > 0.001f) {
                reopenSettingsAfterLyricStyle = false
                lyricStyleEditorOpen = false
                lyricStylePanel.snapTo(0f)
                lyricStyleSnapshot = null
            }
            if (titleStyleEditorOpen || titleStyleT > 0.001f) {
                reopenSettingsAfterTitleStyle = false
                titleStyleEditorOpen = false
                titleStylePanel.snapTo(0f)
                titleStyleSnapshot = null
            }
            if (vinylColorEditorOpen || editorVinylCentered) {
                reopenSettingsAfterEditor = false
                vinylColorEditorOpen = false
                editorVinylCentered = false
                editorPanel.snapTo(0f)
            }
            if (scoreOpen || scoreVinylCentered || scoreFlight != null) {
                scoreOpen = false
                scoreVinylCentered = false
                scoreFlight = null
                suppressVinylEnter = false
                scorePanel.snapTo(0f)
            }
            if (vinylSongPickOpen || pickerVinylCentered || vinylSongPickT > 0.001f) {
                vinylSongPickOpen = false
                pickerVinylCentered = false
                pendingPickPlayIndex = null
                pickRevealMainUnderHandoff = false
                pickOverlayCoversMain = false
                vinylSongPickEverOpen = false
                vinylSongPickPhase = VinylSongPickPhase.Entering
                vinylSongPickPanel.snapTo(0f)
                suppressVinylEnter = false
            }
            if (settingsOpen) {
                settingsOpen = false
                if (transportPinned) controlsVisible = true
            }
            controlsVisible = true
            idleBump++
        }
    }

    BackHandler(enabled = vinylSongPickOpen || vinylSongPickT > 0.001f) {
        cancelVinylSongPick()
    }
    BackHandler(enabled = lyricSelectOpen || lyricSelectT > 0.001f) {
        closeLyricSelect()
    }
    BackHandler(enabled = lyricStyleEditorOpen || lyricStyleT > 0.001f) {
        closeLyricStyleEditor()
    }
    BackHandler(enabled = titleStyleEditorOpen || titleStyleT > 0.001f) {
        closeTitleStyleEditor()
    }
    BackHandler(enabled = vinylColorEditorOpen || editorVinylCentered || editorT > 0.001f) {
        closeVinylColorEditor()
    }
    BackHandler(enabled = scoreOpen || scoreT > 0.001f) {
        closeScore()
    }
    BackHandler(
        enabled = settingsOpen &&
            !vinylColorEditorOpen &&
            !editorVinylCentered &&
            !lyricSelectOpen &&
            !lyricStyleEditorOpen &&
            !titleStyleEditorOpen &&
            !scoreOpen &&
            !scoreVinylCentered &&
            !vinylSongPickOpen &&
            !pickerVinylCentered,
    ) {
        if (!settingsTransferDismissGate.requestDismissTop()) {
            closeSettings()
        }
    }

    LaunchedEffect(
        idleBump,
        sliderDragging,
        track.id,
        settingsOpen,
        scoreOpen,
        transportPinned,
        forceVinylYCentered,
        lyricSelectOpen,
        lyricStyleEditorOpen,
        titleStyleEditorOpen,
        vinylSongPickOpen,
    ) {
        if (settingsOpen || scoreOpen || transportPinned || forceVinylYCentered ||
            lyricSelectOpen || lyricStyleEditorOpen || titleStyleEditorOpen || vinylSongPickOpen
        ) {
            return@LaunchedEffect
        }
        if (sliderDragging) {
            controlsVisible = true
            return@LaunchedEffect
        }
        if (!controlsVisible) return@LaunchedEffect
        delay(3_500)
        controlsVisible = false
    }

    val barSlidePx = with(density) { 52.dp.toPx() }
    // 左右对称外边距：取导航条左右 inset 较大者，避免仅右侧避让导致播放条偏左
    val chromeLayoutDir = LocalLayoutDirection.current
    val navPads = WindowInsets.navigationBars.asPaddingValues()
    val navSideBalance = maxOf(
        navPads.calculateStartPadding(chromeLayoutDir),
        navPads.calculateEndPadding(chromeLayoutDir),
    )
    val chromeSidePad = navSideBalance + 28.dp
    val chromeSidePadPx = with(density) { chromeSidePad.toPx() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { playerRootCoords = it },
    ) {
        // 与左侧歌曲信息同一上边距（按左栏 discExpanded / edgeInset 推算）
        val rootMaxW = maxWidth
        val rootMaxH = maxHeight
        val rowGap = 4.dp
        val leftColW = (rootMaxW - rowGap) * 0.36f
        val discBaseForPad = (leftColW * 0.92f).coerceIn(132.dp, 252.dp)
        val discExpandedForPad = (discBaseForPad * 1.14f)
            .coerceAtMost(leftColW * 0.99f)
            .coerceAtMost(286.dp)
        val songMetaTopPad = ((leftColW - discExpandedForPad) / 2).coerceAtLeast(6.dp)

        // 空白手势只包内容/叠层；右上 chrome 与底栏是兄弟节点，
        // 点击设置等按钮时 blankGestures 不在命中祖先链上，避免 open 后又被 toggle 关掉。
        Box(
            Modifier
                .fillMaxSize()
                .nowPlayingBlankGestures(
                    dismissThresholdPx = dismissSwipeThresholdPx,
                    onTap = {
                        lyricResumeToken++
                        toggleControls()
                    },
                    onSwipeDown = {
                        when {
                            vinylSongPickOpen || vinylSongPickT > 0.001f -> Unit
                            lyricSelectOpen || lyricSelectT > 0.001f -> {
                                if (lyricSelectOutsideArmed) closeLyricSelect()
                            }
                            lyricStyleEditorOpen || lyricStyleT > 0.001f ->
                                closeLyricStyleEditor()
                            titleStyleEditorOpen || titleStyleT > 0.001f ->
                                closeTitleStyleEditor()
                            vinylColorEditorOpen || editorVinylCentered || editorT > 0.001f ->
                                closeVinylColorEditor()
                            scoreOpen || scoreT > 0.001f -> closeScore()
                            // 设置列表要垂直滚动；点外侧 / 返回 / 齿轮关闭
                            settingsOpen -> Unit
                            else -> onDismiss()
                        }
                    },
                ),
        ) {

        // 与左栏同源的黑胶几何：供动态歌词计算右缘侵入
        // 自选编辑 / 曲谱态强制垂直居中（忽略个性化 Y，保留 X）
        val vinylAbsT by animateFloatAsState(
            targetValue = if (displayPrefs.vinylAbsoluteCenter || forceVinylYCentered) 1f else 0f,
            animationSpec = tween(
                durationMillis = vinylCenterMs,
                easing = vinylCenterEasing,
            ),
            label = "vinylAbsCenterOuter",
        )
        val discCompactForPad = (discBaseForPad * 0.86f).coerceAtLeast(118.dp)
        val discForLyric = lerpDp(
            lerpDp(discExpandedForPad, discCompactForPad, chromeT),
            discExpandedForPad,
            vinylAbsT,
        )
        val compactRatioForLyric = if (discExpandedForPad.value > 0.1f) {
            discCompactForPad / discExpandedForPad
        } else {
            1f
        }
        val vinylScaleForLyric = androidx.compose.ui.util.lerp(
            1f,
            androidx.compose.ui.util.lerp(1f, compactRatioForLyric, chromeT),
            vinylAbsT,
        )
        val vinylOx = displayPrefs.vinylOffsetXDp.dp
        val vinylSizeScale = displayPrefs.vinylSizeScale
            .coerceIn(PlayerDisplayPrefs.VINYL_SIZE_SCALE_MIN, PlayerDisplayPrefs.VINYL_SIZE_SCALE_MAX)
        val vinylOuterScale = displayPrefs.vinylOuterScale
            .coerceIn(PlayerDisplayPrefs.VINYL_OUTER_SCALE_MIN, PlayerDisplayPrefs.VINYL_OUTER_SCALE_MAX)
        // 右缘按「整体 × 外圈」可视外缘（外圈可大于 100% 溢出）
        val vinylVisualScale = vinylSizeScale * maxOf(vinylOuterScale, 1f)
        // 左栏黑胶中心（相对 Row）≈ 左栏右缘 - metaWidth/2 + ox
        val vinylCenterX = leftColW - discExpandedForPad / 2 + vinylOx
        val vinylRightEdge = vinylCenterX + discForLyric * vinylScaleForLyric * vinylVisualScale / 2f
        val lyricsColStart = leftColW + rowGap
        val lyricsColWidth = (rootMaxW - leftColW - rowGap - 4.dp).coerceAtLeast(0.dp)
        val lyricsCenterX = lyricsColStart + lyricsColWidth / 2 + displayPrefs.lyricOffsetXDp.dp
        val screenCenterX = rootMaxW / 2
        val titleMaxWidth = (discExpandedForPad * 1.08f).coerceAtMost(rootMaxW * 0.52f)
        // 黑胶右缘越过歌词栏左缘的部分 + 间隙
        val vinylLyricClearance = 10.dp
        val vinylLeftInset = (vinylRightEdge + vinylLyricClearance - lyricsColStart)
            .coerceAtLeast(0.dp)
        val lyricSelectGeomTarget = rememberLyricSelectGeom(
            lines = lines,
            playingStyle = displayPrefs.lyricPlayingStyle,
            playedStyle = displayPrefs.lyricPlayedStyle,
            unplayedStyle = displayPrefs.lyricUnplayedStyle,
            screenWidth = rootMaxW,
            screenHeight = rootMaxH,
        )
        val lyricSelectGeom = rememberAnimatedLyricSelectGeom(
            target = lyricSelectGeomTarget,
            animateChanges = lyricSelectOpen && lyricSelectT > 0.98f,
        )

        // 播放内容作磨砂源（不含设置面板本身）
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(state = settingsHazeState, zIndex = 1f)
                .graphicsLayer { clip = false },
        ) {
        // 黑胶 / 歌词 / 标题同一缩放层，保证对齐坐标一致
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp)
                .graphicsLayer {
                    scaleX = uiScale
                    scaleY = uiScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    clip = false
                },
        ) {
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            // 左侧：黑胶可偏移 / 绝对垂直居中（动画过渡）
            // 左栏铺满至挖孔侧，黑胶离场/入场可画进摄像头区域
            BoxWithConstraints(
                Modifier
                    .weight(0.36f)
                    .fillMaxHeight()
                    .graphicsLayer { clip = false },
                contentAlignment = Alignment.TopEnd,
            ) {
                val discBase = (maxWidth * 0.92f).coerceIn(132.dp, 252.dp)
                val discExpanded = (discBase * 1.14f)
                    .coerceAtMost(maxWidth * 0.99f)
                    .coerceAtMost(286.dp)
                val discCompact = (discBase * 0.86f).coerceAtLeast(118.dp)
                val absT by animateFloatAsState(
                    targetValue = if (displayPrefs.vinylAbsoluteCenter || forceVinylYCentered) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = vinylCenterMs,
                        easing = vinylCenterEasing,
                    ),
                    label = "vinylAbsCenter",
                )
                // 非绝对居中：尺寸随 chrome 缩；绝对居中：尺寸固定，chrome 只缩放
                val disc = lerpDp(
                    lerpDp(discExpanded, discCompact, chromeT),
                    discExpanded,
                    absT,
                )
                val chromePad = lerpDp(2.dp, 66.dp, chromeT)
                val vinylBottomPad = lerpDp(0.dp, chromePad, 1f - absT)
                val compactRatio = if (discExpanded.value > 0.1f) {
                    discCompact / discExpanded
                } else {
                    1f
                }
                val vinylScale = androidx.compose.ui.util.lerp(
                    1f,
                    androidx.compose.ui.util.lerp(1f, compactRatio, chromeT),
                    absT,
                )
                val metaWidth = discExpanded
                val edgeInset = (maxWidth - metaWidth).coerceAtLeast(0.dp)
                val ox = displayPrefs.vinylOffsetXDp.dp
                val oy = (displayPrefs.vinylOffsetYDp * (1f - absT)).dp
                // 从「剩余区居中」过渡到「整栏垂直居中」：约半个信息块高度
                val layoutShiftY = with(density) {
                    (((edgeInset / 2) + 72.dp).toPx() * 0.5f +
                        chromePad.toPx() * 0.35f) * (1f - absT)
                }
                val bottomPadPx = with(density) { vinylBottomPad.toPx() }
                // 上一首入场：相对「实际静止中心」（含水平偏移 / 缩放）整盘离开左栏左缘
                val prevEnterSlidePx = with(density) {
                    val centerFromLeft =
                        (maxWidth - metaWidth / 2).toPx() + ox.toPx()
                    val radius = disc.toPx() * 0.5f * vinylScale * vinylSizeScale *
                        maxOf(vinylOuterScale, 1f)
                    centerFromLeft + radius + 6.dp.toPx()
                }

                @Composable
                fun VinylDisc(mod: Modifier) {
                    VinylTransitionStage(
                        track = track,
                        peekNext = peekNextTrack,
                        peekPrev = peekPrevTrack,
                        spinning = isPlaying && !transportBuffering && !vinylBusy &&
                            scoreFlight == null && !vinylSongPickOpen,
                        direction = vinylSkipDir,
                        gesturesEnabled = !settingsOpen && !forceVinylYCentered &&
                            !scoreOpen && scoreFlight == null &&
                            !lyricStyleEditorOpen && !titleStyleEditorOpen &&
                            !vinylSongPickOpen,
                        onTransitionRunningChange = { vinylBusy = it },
                        onCommitSkip = { dir ->
                            vinylSkipDir = dir
                            when (dir) {
                                VinylSkipDirection.Next -> onSkipNext()
                                VinylSkipDirection.Previous -> onSkipPrev()
                            }
                        },
                        onSettledTrackChange = { vinylSettledTrack = it },
                        modifier = mod.onGloballyPositioned { coords ->
                            val b = coords.boundsInRoot()
                            mainVinylCenterRoot = Offset(
                                b.left + b.width / 2f,
                                b.top + b.height / 2f,
                            )
                            mainVinylSizePx = minOf(b.width, b.height)
                        },
                        fullCover = displayPrefs.vinylFullCover,
                        centerRadiusFrac = displayPrefs.vinylCenterRadiusFrac,
                        outerScale = vinylOuterScale,
                        plateColors = rememberAnimatedVinylPlateColors(
                            displayPrefs.vinylPlateColors(),
                        ),
                        prevEnterSlidePx = prevEnterSlidePx,
                        suppressEnterTransition = suppressVinylEnter,
                        gestureDamping = displayPrefs.vinylGestureDamping,
                        settleSpinUpright = vinylSongPickOpen || pickerVinylCentered,
                    )
                }

                val canLongPressPick = displayPrefs.vinylSongPickEnabled &&
                    !vinylSongPickOpen &&
                    !pickerVinylCentered &&
                    !settingsOpen &&
                    !scoreOpen &&
                    !scoreVinylCentered &&
                    scoreFlight == null &&
                    !vinylColorEditorOpen &&
                    !editorVinylCentered &&
                    !lyricSelectOpen &&
                    !lyricStyleEditorOpen &&
                    !titleStyleEditorOpen
                // Entering：无叠层，主盘清晰居中；Stacking 起叠层盘在蒙版上接替，主盘隐藏
                val hideMainForPickTarget = when {
                    pickRevealMainUnderHandoff -> false
                    vinylSongPickPhase == VinylSongPickPhase.Entering -> false
                    vinylSongPickPhase == VinylSongPickPhase.Stacking ->
                        pickOverlayCoversMain && vinylSongPickT > 0.001f
                    vinylSongPickPhase == VinylSongPickPhase.Canceling ->
                        pickOverlayCoversMain && vinylSongPickT > 0.04f
                    else -> vinylSongPickT > 0.04f
                }
                // 交接期禁止交叉淡入：主盘瞬时显隐，由交接盘不透明盖住
                val pickMainAlpha = if (hideMainForPickTarget) 0f else 1f

                // 单一黑胶：宿主尺寸固定，整体/外圈均绕圆心缩放，避免改 size 导致圆心漂移
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = false },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(metaWidth)
                            .graphicsLayer { clip = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        VinylDisc(
                            Modifier
                                .offset(x = ox, y = oy)
                                .graphicsLayer {
                                    // 原 bottom padding 会把缩放原点抬离圆心；改为平移，原点保持盘心
                                    translationY = layoutShiftY - bottomPadPx * 0.5f
                                    scaleX = vinylScale * vinylSizeScale
                                    scaleY = vinylScale * vinylSizeScale
                                    transformOrigin = TransformOrigin.Center
                                    clip = false
                                    alpha = pickMainAlpha
                                }
                                .size(disc)
                                .then(
                                    if (canLongPressPick) {
                                        // 不 consume down：单击交回空白手势；长按选歌
                                        Modifier.vinylLightTapGestures(
                                            onLongPress = { openVinylSongPick() },
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }

            LandscapeProjectionLyrics(
                lines = lines,
                positionMs = if (lyricStyleSnapshot != null) {
                    lyricStyleFrozenPositionMs
                } else {
                    positionMs
                },
                trackDurationMs = durationMs,
                lineSpacingDp = displayPrefs.lyricLineSpacingDp,
                playedCount = displayPrefs.lyricPlayedCount,
                upcomingCount = displayPrefs.lyricUpcomingCount,
                offsetXDp = displayPrefs.lyricOffsetXDp,
                dynamicLyrics = displayPrefs.dynamicLyrics,
                vinylLeftInset = vinylLeftInset,
                onSeekToMs = { ms ->
                    onSeek(ms.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                    if (displayPrefs.lyricTapAutoPlay && !playWhenReady) {
                        onTogglePlay()
                    }
                },
                resumeScrollToken = lyricResumeToken,
                selectProgress = lyricSelectT,
                selectGeom = lyricSelectGeom,
                lyricsColStartDp = lyricsColStart,
                selectedIndices = lyricSelectSelected,
                onToggleSelect = { index ->
                    if (index in lyricSelectSelected) {
                        lyricSelectSelected.remove(index)
                    } else {
                        lyricSelectSelected.add(index)
                    }
                },
                onLongPressLine = { index -> openLyricSelect(index) },
                onBandCenterPx = null,
                selectHazeState = settingsHazeState,
                selectOpen = lyricSelectOpen,
                playingStyle = displayPrefs.lyricPlayingStyle,
                playedStyle = displayPrefs.lyricPlayedStyle,
                unplayedStyle = displayPrefs.lyricUnplayedStyle,
                onLyricBandCoords = { lyricsBandCoords = it },
                scrollFrozen = lyricStyleSnapshot != null,
                modifier = Modifier
                    .weight(0.64f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        clip = false
                        alpha = liveLyricAlpha * pickUiFade
                    }
                    .padding(start = 0.dp, end = 4.dp),
            )
        }

        // 标题信息层：歌名 / 制作人 / 歌单；水平对齐可切换且可打断
        LandscapeAlignedSongMeta(
            track = track,
            sourceTitle = sourceTitle,
            onSourceClick = onSourceClick,
            onArtistClick = onArtistClick,
            onRevealControls = { revealControls() },
            titleAlign = displayPrefs.titleAlign,
            songMetaTopPad = songMetaTopPad,
            titleOffsetYDp = displayPrefs.titleOffsetYDp,
            titleNameColor = displayPrefs.titleNameColor(),
            titleArtistColor = displayPrefs.titleArtistColor(),
            titleSourceColor = displayPrefs.titleSourceColor(),
            titleNameFontScale = displayPrefs.titleNameStyle.sanitizedFontScale(),
            titleArtistFontScale = displayPrefs.titleArtistStyle.sanitizedFontScale(),
            titleSourceFontScale = displayPrefs.titleSourceStyle.sanitizedFontScale(),
            chromeSidePad = chromeSidePad,
            vinylCenterX = vinylCenterX,
            lyricsCenterX = lyricsCenterX,
            screenCenterX = screenCenterX,
            titleMaxWidth = titleMaxWidth,
            contentAlpha = liveTitleMetaAlpha * pickUiFade,
            onMetaVisualBoundsInRoot = { songMetaVisualBoundsInRoot = it },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart),
        )
        } // uiScale 内容层
        } // hazeSource：仅播放内容作磨砂源（chrome 提到叠层之上，避免被 OutsideDismiss 盖住）

        // 设置层：命中层仅在打开意图时启用；收起过程不吞右上角设置
        if (settingsT > 0.001f || settingsOpen) {
            NowPlayingSettingsOutsideDismiss(
                onDismiss = {
                    if (!settingsTransferDismissGate.requestDismissTop()) {
                        closeSettings()
                    }
                },
                enabled = settingsOpen,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(8f)
                    .graphicsLayer { alpha = settingsT.coerceIn(0f, 1f) },
            )
            BoxWithConstraints(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f)
                    .zIndex(9f)
                    .clipToBounds()
                    .padding(
                        top = chromeSidePad,
                        bottom = chromeSidePad,
                        end = chromeSidePad,
                    ),
            ) {
                val panelW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                NowPlayingSettingsSheet(
                    prefs = displayPrefs,
                    onPrefsChange = onDisplayPrefsChange,
                    hazeState = settingsHazeState,
                    onOpenVinylColorEditor = { openVinylColorEditor() },
                    onOpenLyricStyleEditor = { openLyricStyleEditor() },
                    onOpenTitleStyleEditor = { openTitleStyleEditor() },
                    hazeNonce = hazeNonce,
                    enableRealtimeHaze = true,
                    showPanelBorder = true,
                    glassBlurRadius = 56.dp,
                    transferDismissGate = settingsTransferDismissGate,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = landscapeSideSheetSlideX(
                                progress = settingsT,
                                panelWidthPx = panelW,
                                endPadPx = chromeSidePadPx,
                            )
                        },
                )
            }
        }

        // PLACEHOLDER_SCORE_AND_EDITORS_KEEP_EXISTING
        // 吸附：贴底、仅上方圆角；悬浮：离底间距、四角圆角

        // 曲谱层：玻璃/间距同设置；左边界使黑胶两侧留白相等（1:1 构图）
        // 「<」加宽后左/右边距对称 = chromeSidePad，动画覆盖黑胶
        if (scoreT > 0.001f || scoreOpen) {
            val scaleOriginX = rootMaxW / 2
            val visualVinylCx = scaleOriginX + (vinylCenterX - scaleOriginX) * uiScale
            val visualVinylR = discExpandedForPad / 2 * uiScale * vinylSizeScale *
                maxOf(vinylOuterScale, 1f)
            val vinylLeftVisual = visualVinylCx - visualVinylR
            val equalGap = vinylLeftVisual.coerceAtLeast(0.dp)
            val scoreCollapsedStart = visualVinylCx + visualVinylR + equalGap
            val scoreCollapsedWidth = (rootMaxW - scoreCollapsedStart).coerceAtLeast(96.dp)
            // 加宽：左缘 = chromeSidePad，与右缘对称
            val scoreExpandedWidth = (rootMaxW - chromeSidePad).coerceAtLeast(96.dp)
            NowPlayingSettingsOutsideDismiss(
                onDismiss = { closeScore() },
                enabled = scoreOpen,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(8f)
                    .graphicsLayer { alpha = scoreT.coerceIn(0f, 1f) },
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .zIndex(9f)
                    .padding(
                        top = chromeSidePad,
                        bottom = chromeSidePad,
                        end = chromeSidePad,
                    ),
            ) {
                // 仅此子树订阅 cover 进度，避免 LandscapePlayerBody 每帧重组合
                ScoreCoverWidthHost(
                    coverAnim = scoreCoverAnim,
                    collapsedWidth = scoreCollapsedWidth,
                    expandedWidth = scoreExpandedWidth,
                    endPad = chromeSidePad,
                ) { panelW ->
                    ScoreSheetOverlay(
                        coverExpanded = scoreCoverExpanded,
                        onToggleCoverExpand = { scoreCoverExpanded = !scoreCoverExpanded },
                        onCommitCoverWidth = { expanded ->
                            scoreCoverAnim.animateTo(
                                targetValue = if (expanded) 1f else 0f,
                                animationSpec = tween(
                                    durationMillis = 460,
                                    easing = settingsCurve,
                                ),
                            )
                        },
                        tracks = queue,
                        currentIndex = queueIndex,
                        plateColors = displayPrefs.vinylPlateColors(),
                        onPlayTrack = { idx, center, sizePx ->
                            startScoreFlight(idx, center, sizePx)
                        },
                        openGeneration = scoreOpenGeneration,
                        onApproachEnd = onNeedQueueThrough,
                        hazeState = settingsHazeState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                translationX = landscapeSideSheetSlideX(
                                    progress = scoreT,
                                    panelWidthPx = panelW,
                                    endPadPx = chromeSidePadPx,
                                )
                                alpha = scoreT
                            },
                    )
                }
            }
        }

        // 曲谱飞入：曲线落到主黑胶；覆盖后开播并抑制常规切歌位移动画
        val flight = scoreFlight
        if (flight != null) {
            ScoreVinylFlightLayer(
                flight = flight,
                targetCenter = mainVinylCenterRoot,
                targetSizePx = mainVinylSizePx,
                plateColors = displayPrefs.vinylPlateColors(),
                fullCover = displayPrefs.vinylFullCover,
                centerRadiusFrac = displayPrefs.vinylCenterRadiusFrac,
                outerScale = vinylOuterScale,
                // 目标尺寸来自主黑胶 bounds（已含 sizeScale / uiScale / 外圈视觉）
                onCoverTarget = {
                    onPlayQueueIndex(flight.queueIndex)
                },
                onFinished = { finishScoreFlight() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 自选黑胶颜色编辑：黑胶先居中就位，再渐显弹窗
        if (editorT > 0.001f) {
            // 与内容层同源：bottom pad + uiScale（绕内容中心）后的视觉几何
            val contentH = (rootMaxH - 6.dp).coerceAtLeast(1.dp)
            val layoutVinylCy = contentH / 2
            val scaleOriginX = rootMaxW / 2
            val editorVinylRadius = discExpandedForPad / 2 * uiScale * vinylSizeScale *
                maxOf(vinylOuterScale, 1f)
            val editorVinylCx = scaleOriginX + (vinylCenterX - scaleOriginX) * uiScale
            val editorVinylCy = layoutVinylCy
            VinylColorEditorOverlay(
                prefs = displayPrefs,
                onPrefsChange = onDisplayPrefsChange,
                hazeState = settingsHazeState,
                progress = editorT,
                vinylCenterX = editorVinylCx,
                vinylCenterY = editorVinylCy,
                vinylRadius = editorVinylRadius,
                screenWidth = rootMaxW,
                screenHeight = rootMaxH,
                onDismiss = { closeVinylColorEditor() },
                onBackToSettings = { closeVinylColorEditorToSettings() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 歌词样式：面板与克隆分离；克隆 morph 终点用静止槽几何（不含入场 layer）
        val styleSnap = lyricStyleSnapshot
        if (styleSnap != null &&
            (lyricStyleEditorOpen || lyricStyleT > 0.001f || styleCloneAlpha > 0.001f)
        ) {
            val restSlot = lyricStyleRestPreviewSlot(
                screenWidth = rootMaxW,
                screenHeight = rootMaxH,
                chromeSidePad = chromeSidePad,
                previewWidthDp = styleSnap.sourceWidthDp,
            )
            LyricStyleEditorOverlay(
                draftPlaying = draftLyricPlaying,
                draftPlayed = draftLyricPlayed,
                draftUnplayed = draftLyricUnplayed,
                onDraftPlayingChange = { draftLyricPlaying = it },
                onDraftPlayedChange = { draftLyricPlayed = it },
                onDraftUnplayedChange = { draftLyricUnplayed = it },
                hazeState = settingsHazeState,
                progress = lyricStyleT,
                chromeSidePad = chromeSidePad,
                previewWidthDp = styleSnap.sourceWidthDp,
                onDismiss = { closeLyricStyleEditor() },
                onBackToSettings = { closeLyricStyleEditorToSettings() },
                hazeNonce = hazeNonce,
                modifier = Modifier.fillMaxSize(),
            )
            LyricStyleCloneLayer(
                snapshot = styleSnap,
                draftPlaying = draftLyricPlaying,
                draftPlayed = draftLyricPlayed,
                draftUnplayed = draftLyricUnplayed,
                progress = lyricStyleT,
                targetSlot = restSlot,
                contentAlpha = styleCloneAlpha,
                uiScale = uiScale,
            )
        }

        // 标题样式：面板与克隆分离；歌曲信息柔缓 morph 进右侧预览槽
        val titleSnap = titleStyleSnapshot
        if (titleSnap != null &&
            (titleStyleEditorOpen || titleStyleT > 0.001f || titleStyleCloneAlpha > 0.001f)
        ) {
            val titleRestSlot = titleStyleRestPreviewSlot(
                screenWidth = rootMaxW,
                screenHeight = rootMaxH,
                chromeSidePad = chromeSidePad,
                previewWidthDp = titleSnap.sourceWidthDp,
            )
            TitleStyleEditorOverlay(
                draftName = draftTitleName,
                draftArtist = draftTitleArtist,
                draftSource = draftTitleSource,
                onDraftNameChange = { draftTitleName = it },
                onDraftArtistChange = { draftTitleArtist = it },
                onDraftSourceChange = { draftTitleSource = it },
                hazeState = settingsHazeState,
                progress = titleStyleT,
                chromeSidePad = chromeSidePad,
                previewWidthDp = titleSnap.sourceWidthDp,
                onDismiss = { closeTitleStyleEditor() },
                onBackToSettings = { closeTitleStyleEditorToSettings() },
                hazeNonce = hazeNonce,
                modifier = Modifier.fillMaxSize(),
            )
            TitleStyleCloneLayer(
                snapshot = titleSnap,
                draftName = draftTitleName,
                draftArtist = draftTitleArtist,
                draftSource = draftTitleSource,
                progress = titleStyleT,
                targetSlot = titleRestSlot,
                contentAlpha = titleStyleCloneAlpha,
                uiScale = uiScale,
            )
        }

        // 长按歌词选句：磨砂壳 + 挖孔；原歌词层位移动画填入，非另起列表
        if (lyricSelectT > 0.001f) {
            val ctx = LocalContext.current
            LyricSelectOverlay(
                selectedCount = lyricSelectSelected.size,
                hazeState = settingsHazeState,
                progress = lyricSelectT,
                selectOpen = lyricSelectOpen,
                geom = lyricSelectGeom,
                hazeNonce = hazeNonce,
                onDismiss = { closeLyricSelect() },
                onClearSelection = { lyricSelectSelected.clear() },
                onCopy = {
                    copyLyricSelection(ctx, lines, lyricSelectSelected.toSet())
                    closeLyricSelect()
                },
                onOutsideDismissArmed = { lyricSelectOutsideArmed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 黑胶选歌：全屏雾气 + 堆叠/散开/横滑吸附
        if (vinylSongPickT > 0.001f) {
            val contentH = (rootMaxH - 6.dp).coerceAtLeast(1.dp)
            val scaleOriginX = rootMaxW / 2
            // 优先用主黑胶实测锚点（个性化 X/尺寸）；否则回退几何推算
            val rootBounds = playerRootCoords?.takeIf { it.isAttached }?.boundsInRoot()
            val measuredOk = rootBounds != null &&
                pickAnchorCenterRoot.x > 1f &&
                pickAnchorSizePx > 8f
            val pickSnapCx = if (measuredOk) {
                with(density) { (pickAnchorCenterRoot.x - rootBounds!!.left).toDp() }
            } else {
                scaleOriginX + (vinylCenterX - scaleOriginX) * uiScale
            }
            val pickSnapCy = if (measuredOk) {
                with(density) { (pickAnchorCenterRoot.y - rootBounds!!.top).toDp() }
            } else {
                contentH / 2
            }
            val pickDiscSize = if (measuredOk) {
                with(density) { pickAnchorSizePx.toDp() }
            } else {
                discExpandedForPad * uiScale * vinylSizeScale * maxOf(vinylOuterScale, 1f)
            }
            VinylSongPickOverlay(
                phase = vinylSongPickPhase,
                progress = vinylSongPickT,
                queue = pickSessionQueue.ifEmpty { queue },
                queueIndex = if (pickSessionQueue.isEmpty()) {
                    queueIndex
                } else {
                    pickSessionAnchor
                },
                focusedIndex = pickTargetIndex,
                onFocusedIndexChange = { pickTargetIndex = it },
                snapCenterX = pickSnapCx,
                snapCenterY = pickSnapCy,
                discSize = pickDiscSize,
                plateColors = displayPrefs.vinylPlateColors(),
                fullCover = displayPrefs.vinylFullCover,
                centerRadiusFrac = displayPrefs.vinylCenterRadiusFrac,
                outerScale = vinylOuterScale,
                hazeState = settingsHazeState,
                titleNameStyle = displayPrefs.titleNameStyle,
                uiScale = uiScale,
                sessionKey = pickSessionGeneration,
                onBack = { cancelVinylSongPick() },
                onConfirmFocused = { confirmVinylSongPick() },
                onCancelExitFinished = { finishVinylSongPickCancelExit() },
                onConfirmExitFinished = { finishVinylSongPickConfirmExit() },
                onStackingFinished = {
                    if (vinylSongPickOpen && vinylSongPickPhase == VinylSongPickPhase.Stacking) {
                        vinylSongPickPhase = VinylSongPickPhase.FanOut
                    }
                },
                onFanOutFinished = {
                    if (vinylSongPickOpen && vinylSongPickPhase == VinylSongPickPhase.FanOut) {
                        // 散开结束：焦点锁回本会话打开时的当前曲，勿用可能已变的 live queueIndex
                        pickTargetIndex = pickSessionAnchor.coerceIn(
                            0,
                            (pickSessionQueue.size - 1).coerceAtLeast(0),
                        )
                        vinylSongPickPhase = VinylSongPickPhase.Browsing
                    }
                },
                onApproachEnd = onNeedQueueThrough,
                modifier = Modifier.fillMaxSize(),
            )
        }

        } // blankGestures：播放内容 + 设置/曲谱等叠层

        if (showBar || chromeT > 0.001f) {
            val transportDocked = displayPrefs.transportDocked
            val insetDp = displayPrefs.transportBottomInsetDp
                .takeIf { it.isFinite() }
                ?.coerceIn(
                    PlayerDisplayPrefs.TRANSPORT_BOTTOM_INSET_MIN,
                    PlayerDisplayPrefs.TRANSPORT_BOTTOM_INSET_MAX,
                )
                ?: 16f
            val transportBottomPad = if (transportDocked) 0.dp else insetDp.dp
            val transportShape = if (transportDocked) {
                RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
            } else {
                RoundedCornerShape(14.dp)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(40f)
                    .graphicsLayer {
                        translationY = (1f - chromeT) * barSlidePx
                        scaleX = uiScale
                        scaleY = uiScale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    // alpha()：淡出到 0 时不命中，避免挡住下方空白手势 / 设置层
                    .alpha(chromeT)
                    .padding(
                        start = chromeSidePad,
                        end = chromeSidePad,
                        bottom = transportBottomPad,
                    )
                    .clip(transportShape)
                    .background(Color.Black.copy(alpha = 0.22f))
                    .clickable(
                        enabled = chromeT > 0.2f,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { revealControls() },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                PlayerTransport(
                    isPlaying = playWhenReady,
                    buffering = loadPending,
                    controlsLocked = controlsLocked,
                    onTogglePlay = {
                        if (!controlsLocked) {
                            revealControls()
                            onTogglePlay()
                        }
                    },
                    onSkipNext = {
                        if (!controlsLocked) {
                            revealControls()
                            vinylSkipDir = VinylSkipDirection.Next
                            onSkipNext()
                        }
                    },
                    onSkipPrev = {
                        if (!controlsLocked) {
                            revealControls()
                            vinylSkipDir = VinylSkipDirection.Previous
                            onSkipPrev()
                        }
                    },
                    durationMs = durationMs,
                    positionMs = seekPositionMs,
                    sliderDragging = sliderDragging,
                    sliderValue = sliderValue,
                    onSliderDragStart = {
                        if (!controlsLocked) {
                            revealControls()
                            onSliderDragStart()
                        }
                    },
                    onSliderChange = onSliderChange,
                    onSliderDragEnd = {
                        revealControls()
                        onSliderDragEnd(it)
                    },
                    playbackMode = playbackMode,
                    onCyclePlaybackMode = {
                        if (!controlsLocked) {
                            revealControls()
                            onCyclePlaybackMode()
                        }
                    },
                    trackLiked = trackLiked,
                    onToggleLike = {
                        if (!controlsLocked) {
                            revealControls()
                            onToggleLike()
                        }
                    },
                    portraitSlim = false,
                    landscapeDense = true,
                    onOpenScore = {
                        if (!controlsLocked) openScore()
                    },
                )
            }

            // 右上：退出 | 旋转锁定 | 设置（叠在 OutsideDismiss 之上，保证可点）
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(40f)
                    .padding(top = songMetaTopPad, end = chromeSidePad)
                    .graphicsLayer {
                        scaleX = uiScale
                        scaleY = uiScale
                        transformOrigin = TransformOrigin(1f, 0f)
                    }
                    .alpha(chromeT),
                horizontalArrangement = Arrangement.spacedBy(NowPlayingChromeIconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NowPlayingDismissIconButton(
                    onClick = onDismiss,
                )
                NowPlayingRotationLockButton(
                    locked = rotationLocked,
                    forceToLandscape = if (systemAutoRotate) null else false,
                    onClick = {
                        if (systemAutoRotate) {
                            rotationLock.toggle(activity)
                        } else {
                            rotationLock.forceOrientation(activity, landscape = false)
                        }
                    },
                )
                NowPlayingSettingsIconButton(
                    onClick = {
                        // 直接开设置；勿依赖空白手势链路
                        openSettings()
                    },
                )
            }
        }

        // 右上短通知：避让 chrome 图标；入场/出场/Y 位移可打断
        // 通知不要盖住设置按钮命中区：往下错开一档 chrome 高度
        PlaybackCornerNotice(
            notice = notice,
            chromeProgress = chromeT,
            topBase = songMetaTopPad + NowPlayingChromeIconHeight + 8.dp,
            endPad = chromeSidePad,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(35f)
                .graphicsLayer {
                    scaleX = uiScale
                    scaleY = uiScale
                    transformOrigin = TransformOrigin(1f, 0f)
                },
        )
    }
}
