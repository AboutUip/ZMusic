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
import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerBackgroundPreset
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PlayerDisplayPrefsStore
import com.kite.zmusic.data.PlaylistTrackLoader
import com.kite.zmusic.data.TitleAlignMode
import com.kite.zmusic.data.TitleLineStyle
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.VinylColorStyle
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    state: PlaybackUiState,
    isLandscape: Boolean,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onOpenSourcePlaylist: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    onPlayQueueIndex: (Int) -> Unit = {},
    /** 竖屏评论打开时挂起曲末自动下一首 */
    onHoldAutoAdvanceChange: (Boolean) -> Unit = {},
    /** 横屏额外左侧 inset（本页已无 Dock，通常为 0） */
    landscapeStartInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    var portraitLyricsOpen by rememberSaveable { mutableStateOf(false) }
    var portraitPosterOpen by remember { mutableStateOf(false) }
    var portraitMoreOpen by remember { mutableStateOf(false) }
    var portraitPosterFrozenPositionMs by remember { mutableLongStateOf(0L) }
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val displayPrefsStore = remember { PlayerDisplayPrefsStore(context) }
    var displayPrefs by remember { mutableStateOf(displayPrefsStore.load()) }
    var displayPrefsSaveJob by remember { mutableStateOf<Job?>(null) }
    val displayPrefsLatest by rememberUpdatedState(displayPrefs)
    // 竖屏设置独立持久化，与横屏互不覆盖
    val portraitDisplayPrefsStore = remember {
        PlayerDisplayPrefsStore(context, PlayerDisplayPrefsStore.PREFS_PORTRAIT)
    }
    var portraitDisplayPrefs by remember { mutableStateOf(portraitDisplayPrefsStore.load()) }
    var portraitDisplayPrefsSaveJob by remember { mutableStateOf<Job?>(null) }
    val portraitDisplayPrefsLatest by rememberUpdatedState(portraitDisplayPrefs)
    val prefsPersistScope = rememberCoroutineScope()
    // 滑条拖动只改内存态；磁盘防抖写入，避免澎湃等机型高频 apply 损坏偏好导致无法再进
    fun updateDisplayPrefs(next: PlayerDisplayPrefs) {
        val sanitized = next.sanitized()
        displayPrefs = sanitized
        displayPrefsSaveJob?.cancel()
        displayPrefsSaveJob = prefsPersistScope.launch {
            delay(200)
            displayPrefsStore.save(sanitized)
        }
    }
    fun flushDisplayPrefs() {
        displayPrefsSaveJob?.cancel()
        displayPrefsSaveJob = null
        displayPrefsStore.save(displayPrefsLatest)
    }
    fun updatePortraitDisplayPrefs(next: PlayerDisplayPrefs) {
        val sanitized = next.sanitized()
        portraitDisplayPrefs = sanitized
        portraitDisplayPrefsSaveJob?.cancel()
        portraitDisplayPrefsSaveJob = prefsPersistScope.launch {
            delay(200)
            portraitDisplayPrefsStore.save(sanitized)
        }
    }
    fun flushPortraitDisplayPrefs() {
        portraitDisplayPrefsSaveJob?.cancel()
        portraitDisplayPrefsSaveJob = null
        portraitDisplayPrefsStore.save(portraitDisplayPrefsLatest)
    }
    DisposableEffect(Unit) {
        onDispose {
            flushDisplayPrefs()
            flushPortraitDisplayPrefs()
        }
    }

    val app = context.applicationContext as ZMusicApplication
    val audioQuality by app.audioQualityStore.quality.collectAsStateWithLifecycle()
    var queueDemand by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.sourcePlaylistId) { queueDemand = 0 }
    fun needQueueThrough(index: Int) {
        val want = index + PlaylistTrackLoader.PAGE + 8
        if (want > queueDemand) queueDemand = want
    }
    PlaylistQueueHydrator(
        playlistId = state.sourcePlaylistId,
        currentIndex = state.index,
        loadedCount = state.queue.size,
        demandMinCount = queueDemand,
    )
    val songs = app.songRepository
    val likedRepo = app.likedPlaylistRepository
    val likeScope = rememberCoroutineScope()
    // 首帧即读缓存，避免切歌先闪「未喜欢」
    var trackLiked by remember(track.id) {
        mutableStateOf(likedRepo.isLiked(track.id) ?: false)
    }
    var likeBusy by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        likedRepo.isLiked(track.id)?.let { trackLiked = it }

        launch {
            likedRepo.snapshot.collect {
                likedRepo.isLiked(track.id)?.let { trackLiked = it }
            }
        }

        val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isNotEmpty()) {
            try {
                val liked = songs.isTrackLiked(track.id, cookie)
                if (liked != null) {
                    trackLiked = liked
                    likedRepo.recordLikeStatus(track, liked)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun toggleTrackLike() {
        if (likeBusy || state.loadPending) return
        val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isEmpty()) return
        val likedTrack = track
        val next = !trackLiked
        trackLiked = next
        likedRepo.applyLocalLike(likedTrack, liked = next)
        likeBusy = true
        likeScope.launch {
            try {
                val ack = songs.likeSong(likedTrack.id, like = next, cookie = cookie)
                if (!ack.ok) {
                    trackLiked = !next
                    likedRepo.applyLocalLike(likedTrack, liked = !next, scheduleSync = false)
                }
            } catch (_: Exception) {
                trackLiked = !next
                likedRepo.applyLocalLike(likedTrack, liked = !next, scheduleSync = false)
            } finally {
                likeBusy = false
            }
        }
    }

    LaunchedEffect(track.id) {
        portraitLyricsOpen = false
        portraitPosterOpen = false
        sliderDragging = false
    }

    val duration = state.durationMs.coerceAtLeast(1L)
    // 进度条/时间：切歌时快速动画归零；歌词仍用真实 position，避免回 scrub
    val seekDisplayPos = rememberSeekDisplayPositionMs(
        trackId = track.id,
        positionMs = state.positionMs,
        loadPending = state.loadPending,
        seeking = sliderDragging,
        scrubPositionMs = sliderValue.toLong(),
    )
    val displayPos = if (sliderDragging) sliderValue.toLong() else seekDisplayPos
    val lyricPos = if (sliderDragging) sliderValue.toLong() else state.positionMs
    // 歌词选择抽到小函数：避免在超大 Composable 里混用 List/Boolean 的 remember key（ART VerifyError）
    val lyricLines = rememberDisplayLyricLines(
        state = state,
        isLandscape = isLandscape,
        portraitPrefs = portraitDisplayPrefs,
    )

    val openSourcePlaylist = onOpenSourcePlaylist
    // 下滑退出阈值：交给竖/横屏 body 的空白手势识别
    val dismissSwipeThresholdPx = with(LocalDensity.current) {
        (if (isLandscape) 120.dp else 112.dp).toPx()
    }
    // Animatable：连点开关会取消上一跳，从当前强度反向；时长按剩余路程缩放，打断可预测
    val rainProgress = remember {
        Animatable(if (displayPrefs.rainNightEnabled) 1f else 0f)
    }
    LaunchedEffect(displayPrefs.rainNightEnabled) {
        val target = if (displayPrefs.rainNightEnabled) 1f else 0f
        val distance = kotlin.math.abs(target - rainProgress.value).coerceIn(0f, 1f)
        val durationMs = (1_100f * distance).toInt().coerceIn(280, 1_100)
        rainProgress.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f),
            ),
        )
    }
    val rainIntensity = rainProgress.value
    // 设置面板磨砂源：横竖屏共用视觉源；配置数据彼此隔离
    val settingsHazeState = rememberHazeState()
    var portraitSettingsOpen by remember { mutableStateOf(false) }
    var portraitScoreOpen by remember { mutableStateOf(false) }
    var portraitQualityOpen by remember { mutableStateOf(false) }
    var portraitLyricSelectOpen by remember { mutableStateOf(false) }
    val portraitLyricSelectSelected: SnapshotStateSet<Int> = remember { mutableStateSetOf() }
    val portraitLyricSelectPanel = remember { Animatable(0f) }
    var portraitLyricSelectEverOpen by remember { mutableStateOf(false) }
    var portraitLyricSelectResumeToken by remember { mutableIntStateOf(0) }
    val portraitSelectEasing = remember { CubicBezierEasing(0.33f, 0f, 0.2f, 1f) }
    var portraitBackgroundEditorOpen by remember { mutableStateOf(false) }
    var portraitLyricStyleEditorOpen by remember { mutableStateOf(false) }
    var pendingPortraitLyricStyleEditor by remember { mutableStateOf(false) }
    var reopenSettingsAfterPortraitLyricStyle by remember { mutableStateOf(false) }
    var portraitLyricStyleSnapshot by remember { mutableStateOf<LyricStyleSnapshot?>(null) }
    var portraitLyricStyleFrozenPositionMs by remember { mutableLongStateOf(0L) }
    var draftPortraitLyricPlaying by remember { mutableStateOf(LyricRoleStyle.PlayingDefault) }
    var draftPortraitLyricPlayed by remember { mutableStateOf(LyricRoleStyle.PlayedDefault) }
    var draftPortraitLyricUnplayed by remember { mutableStateOf(LyricRoleStyle.UnplayedDefault) }
    var draftPortraitPlayedCount by remember {
        mutableIntStateOf(portraitDisplayPrefs.lyricPlayedCount)
    }
    var draftPortraitUpcomingCount by remember {
        mutableIntStateOf(portraitDisplayPrefs.lyricUpcomingCount)
    }
    var draftPortraitLineSpacing by remember {
        mutableFloatStateOf(portraitDisplayPrefs.lyricLineSpacingDp)
    }
    var portraitPlayerRootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var portraitLyricsBandCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val portraitLyricStylePanel = remember { Animatable(0f) }
    val portraitSettingsPanel = remember { Animatable(0f) }
    // 面板高度：拖动跟手；松手必吸附到 1/3、2/3、全屏（丝滑弹簧）
    val portraitSheetFrac = remember { Animatable(1f / 3f) }
    val portraitSheetScope = rememberCoroutineScope()
    val portraitSnapPoints = remember { floatArrayOf(1f / 3f, 2f / 3f, 1f) }
    var portraitSheetDragVel by remember { mutableFloatStateOf(0f) }
    val portraitScorePanel = remember { Animatable(0f) }
    val portraitQualityPanel = remember { Animatable(0f) }
    val portraitMorePanel = remember { Animatable(0f) }
    val portraitMoreSheetFrac = remember { Animatable(1f / 3f) }
    var portraitMoreSheetDragVel by remember { mutableFloatStateOf(0f) }
    var portraitMoreSavedFrac by remember { mutableFloatStateOf(1f / 3f) }
    var portraitMoreNested by remember { mutableStateOf(false) }
    val portraitScoreSheetFrac = remember { Animatable(2f / 3f) }
    var portraitScoreSheetDragVel by remember { mutableFloatStateOf(0f) }
    var portraitScoreRevealToken by remember { mutableIntStateOf(0) }
    var portraitCommentsOpen by remember { mutableStateOf(false) }
    val portraitCommentsPanel = remember { Animatable(0f) }
    val portraitCommentsSheetFrac = remember { Animatable(2f / 3f) }
    LaunchedEffect(portraitSettingsOpen) {
        if (portraitSettingsOpen) {
            portraitSheetFrac.snapTo(1f / 3f)
            portraitSettingsPanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f),
                ),
            )
        } else {
            portraitSettingsPanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 360,
                    easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                ),
            )
        }
    }
    LaunchedEffect(portraitScoreOpen) {
        if (portraitScoreOpen) {
            portraitScoreSheetFrac.snapTo(2f / 3f)
            portraitScoreRevealToken++
            portraitScorePanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f),
                ),
            )
        } else {
            portraitScorePanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 360,
                    easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                ),
            )
        }
    }
    LaunchedEffect(portraitQualityOpen) {
        if (portraitQualityOpen) {
            portraitQualityPanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f),
                ),
            )
        } else {
            portraitQualityPanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 360,
                    easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                ),
            )
        }
    }
    LaunchedEffect(portraitMoreOpen) {
        if (portraitMoreOpen) {
            portraitMoreSheetFrac.snapTo(1f / 3f)
            portraitMoreSavedFrac = 1f / 3f
            portraitMoreNested = false
            portraitMorePanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f),
                ),
            )
        } else {
            portraitMorePanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 360,
                    easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                ),
            )
        }
    }
    LaunchedEffect(portraitCommentsOpen) {
        onHoldAutoAdvanceChange(portraitCommentsOpen)
        if (portraitCommentsOpen) {
            portraitCommentsSheetFrac.snapTo(2f / 3f)
            portraitCommentsPanel.animateTo(
                targetValue = 1f,
                animationSpec = CommentSheetOpenSpec,
            )
        } else {
            portraitCommentsPanel.animateTo(
                targetValue = 0f,
                animationSpec = CommentSheetCloseSpec,
            )
        }
    }
    DisposableEffect(onHoldAutoAdvanceChange) {
        onDispose { onHoldAutoAdvanceChange(false) }
    }
    LaunchedEffect(portraitLyricSelectOpen) {
        if (portraitLyricSelectOpen) {
            portraitLyricSelectEverOpen = true
            // 首帧先离开 0，避免 browsing 冻结与 selectT=0 叠出一帧浏览闪烁
            if (portraitLyricSelectPanel.value < 0.001f) {
                portraitLyricSelectPanel.snapTo(0.001f)
            }
            portraitLyricSelectPanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 480,
                    easing = portraitSelectEasing,
                ),
            )
        } else if (portraitLyricSelectEverOpen) {
            // 先播完收窗，再清选 / 回滚跟滚，避免出场闪烁
            portraitLyricSelectPanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 480,
                    easing = portraitSelectEasing,
                ),
            )
            portraitLyricSelectSelected.clear()
            portraitLyricSelectEverOpen = false
            portraitLyricSelectResumeToken++
        }
    }
    LaunchedEffect(track.id) {
        portraitLyricSelectOpen = false
        portraitLyricSelectSelected.clear()
        portraitLyricSelectResumeToken = 0
        portraitCommentsOpen = false
        portraitMoreOpen = false
    }
    // 退出歌词页后清零：否则 token 残留会在下次进入时触发从头动画滚
    LaunchedEffect(portraitLyricsOpen) {
        if (!portraitLyricsOpen) {
            portraitLyricSelectResumeToken = 0
        }
    }
    val portraitSettingsT = portraitSettingsPanel.value
    val portraitScoreT = portraitScorePanel.value
    val portraitQualityT = portraitQualityPanel.value
    val portraitMoreT = portraitMorePanel.value
    val portraitCommentsT = portraitCommentsPanel.value
    val portraitLyricSelectT = portraitLyricSelectPanel.value
    fun closePortraitSettings() {
        flushPortraitDisplayPrefs()
        portraitSettingsOpen = false
    }
    fun closePortraitScore() {
        portraitScoreOpen = false
    }
    fun closePortraitQuality() {
        portraitQualityOpen = false
    }
    fun closePortraitComments() {
        portraitCommentsOpen = false
    }
    fun closePortraitLyricSelect() {
        portraitLyricSelectOpen = false
    }
    fun closePortraitMore() {
        portraitMoreOpen = false
    }

    // 进入横屏：拆除全部竖屏叠层（含 Animatable），避免透明 OutsideDismiss 残留吞单击
    LaunchedEffect(isLandscape) {
        if (!isLandscape) return@LaunchedEffect
        portraitLyricsOpen = false
        portraitPosterOpen = false
        portraitSettingsOpen = false
        portraitScoreOpen = false
        portraitQualityOpen = false
        portraitCommentsOpen = false
        portraitLyricSelectOpen = false
        portraitMoreOpen = false
        portraitLyricSelectSelected.clear()
        portraitLyricSelectResumeToken = 0
        portraitBackgroundEditorOpen = false
        portraitLyricStyleEditorOpen = false
        portraitLyricStyleSnapshot = null
        portraitSettingsPanel.snapTo(0f)
        portraitScorePanel.snapTo(0f)
        portraitQualityPanel.snapTo(0f)
        portraitMorePanel.snapTo(0f)
        portraitCommentsPanel.snapTo(0f)
        portraitCommentsSheetFrac.snapTo(2f / 3f)
        portraitLyricSelectPanel.snapTo(0f)
        portraitLyricStylePanel.snapTo(0f)
    }

    fun openPortraitLyricSelect() {
        if (portraitBackgroundEditorOpen || portraitLyricStyleEditorOpen ||
            portraitPosterOpen
        ) {
            return
        }
        closePortraitSettings()
        closePortraitScore()
        closePortraitQuality()
        closePortraitComments()
        closePortraitMore()
        portraitLyricSelectSelected.clear()
        portraitLyricsOpen = true
        portraitLyricSelectOpen = true
    }
    fun openPortraitScore() {
        closePortraitSettings()
        closePortraitQuality()
        closePortraitLyricSelect()
        closePortraitComments()
        closePortraitMore()
        portraitScoreOpen = true
    }
    fun openPortraitQuality() {
        closePortraitSettings()
        closePortraitScore()
        closePortraitLyricSelect()
        closePortraitComments()
        closePortraitMore()
        portraitQualityOpen = true
    }
    fun openPortraitSettings() {
        closePortraitScore()
        closePortraitQuality()
        closePortraitLyricSelect()
        closePortraitComments()
        closePortraitMore()
        portraitSettingsOpen = true
    }
    fun openPortraitComments() {
        closePortraitSettings()
        closePortraitScore()
        closePortraitQuality()
        closePortraitLyricSelect()
        closePortraitMore()
        portraitCommentsOpen = true
    }

    fun openPortraitPoster() {
        closePortraitSettings()
        closePortraitScore()
        closePortraitQuality()
        closePortraitComments()
        closePortraitLyricSelect()
        closePortraitMore()
        portraitPosterFrozenPositionMs = lyricPos
        portraitLyricsOpen = true
        portraitPosterOpen = true
    }

    fun openPortraitMore() {
        closePortraitSettings()
        closePortraitScore()
        closePortraitQuality()
        closePortraitComments()
        closePortraitLyricSelect()
        portraitMoreOpen = true
    }

    fun closePortraitPoster() {
        portraitPosterOpen = false
        portraitLyricsOpen = false
    }

    fun commitPortraitLyricStyleDraft() {
        updatePortraitDisplayPrefs(
            portraitDisplayPrefs.copy(
                lyricPlayingStyle = draftPortraitLyricPlaying.sanitized(),
                lyricPlayedStyle = draftPortraitLyricPlayed.sanitized(),
                lyricUnplayedStyle = draftPortraitLyricUnplayed.sanitized(),
                lyricPlayedCount = draftPortraitPlayedCount.coerceIn(
                    PlayerDisplayPrefs.LYRIC_AROUND_MIN,
                    PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX,
                ),
                lyricUpcomingCount = draftPortraitUpcomingCount.coerceIn(
                    PlayerDisplayPrefs.LYRIC_AROUND_MIN,
                    PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX,
                ),
                lyricLineSpacingDp = draftPortraitLineSpacing.coerceIn(
                    PlayerDisplayPrefs.LINE_SPACING_MIN,
                    PlayerDisplayPrefs.LINE_SPACING_MAX,
                ),
            ),
        )
        flushPortraitDisplayPrefs()
    }

    fun closePortraitLyricStyleEditor(reopenSettings: Boolean) {
        reopenSettingsAfterPortraitLyricStyle = reopenSettings
        commitPortraitLyricStyleDraft()
        portraitLyricStyleEditorOpen = false
    }

    fun requestPortraitLyricStyleEditor() {
        if (portraitBackgroundEditorOpen || portraitLyricStyleEditorOpen) return
        draftPortraitLyricPlaying = portraitDisplayPrefs.lyricPlayingStyle
        draftPortraitLyricPlayed = portraitDisplayPrefs.lyricPlayedStyle
        draftPortraitLyricUnplayed = portraitDisplayPrefs.lyricUnplayedStyle
        draftPortraitPlayedCount = portraitDisplayPrefs.lyricPlayedCount
        draftPortraitUpcomingCount = portraitDisplayPrefs.lyricUpcomingCount
        draftPortraitLineSpacing = portraitDisplayPrefs.lyricLineSpacingDp
        reopenSettingsAfterPortraitLyricStyle = false
        pendingPortraitLyricStyleEditor = true
        portraitLyricsOpen = true
    }

    fun snapPortraitSheet() {
        val cur = portraitSheetFrac.value
        val vel = portraitSheetDragVel // 高度占比变化速度（上滑为正）
        portraitSheetDragVel = 0f
        // 明显下拉过头：关闭
        if (cur < 0.20f && vel <= 0f) {
            closePortraitSettings()
            return
        }
        // 速度偏向最近吸附档；松手必落到 1/3、2/3、全屏之一
        val projected = (cur + vel * 10f).coerceIn(0f, 1f)
        val target = portraitSnapPoints.minBy { kotlin.math.abs(it - projected) }
        portraitSheetScope.launch {
            portraitSheetFrac.animateTo(
                target,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 380f,
                ),
            )
        }
    }

    fun snapPortraitMoreSheet() {
        val cur = portraitMoreSheetFrac.value
        val vel = portraitMoreSheetDragVel
        portraitMoreSheetDragVel = 0f
        if (cur < 0.20f && vel <= 0f) {
            closePortraitMore()
            return
        }
        val projected = (cur + vel * 10f).coerceIn(0f, 1f)
        val target = portraitSnapPoints.minBy { kotlin.math.abs(it - projected) }
        portraitSheetScope.launch {
            portraitMoreSheetFrac.animateTo(
                target,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 380f,
                ),
            )
        }
    }

    fun snapPortraitScoreSheet() {
        val cur = portraitScoreSheetFrac.value
        val vel = portraitScoreSheetDragVel
        portraitScoreSheetDragVel = 0f
        if (cur < 0.20f && vel <= 0f) {
            closePortraitScore()
            return
        }
        val projected = (cur + vel * 10f).coerceIn(0f, 1f)
        val target = portraitSnapPoints.minBy { kotlin.math.abs(it - projected) }
        portraitSheetScope.launch {
            portraitScoreSheetFrac.animateTo(
                target,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 380f,
                ),
            )
        }
    }
    BackHandler(
        enabled = !isLandscape && (
            portraitLyricStyleEditorOpen || portraitLyricStylePanel.value > 0.001f
            ),
    ) {
        closePortraitLyricStyleEditor(reopenSettings = true)
    }
    BackHandler(
        enabled = !isLandscape &&
            (portraitLyricSelectOpen || portraitLyricSelectT > 0.001f),
    ) {
        closePortraitLyricSelect()
    }
    BackHandler(
        enabled = !isLandscape &&
            portraitLyricsOpen &&
            !portraitLyricSelectOpen &&
            portraitLyricSelectT <= 0.001f &&
            !portraitLyricStyleEditorOpen &&
            !portraitPosterOpen &&
            portraitLyricStylePanel.value <= 0.001f,
    ) {
        portraitLyricsOpen = false
    }
    BackHandler(enabled = !isLandscape && portraitBackgroundEditorOpen) {
        portraitBackgroundEditorOpen = false
    }
    BackHandler(
        enabled = !isLandscape &&
            portraitSettingsOpen &&
            !portraitBackgroundEditorOpen &&
            !portraitLyricStyleEditorOpen,
    ) {
        closePortraitSettings()
    }
    BackHandler(enabled = !isLandscape && portraitScoreOpen) {
        closePortraitScore()
    }
    BackHandler(enabled = !isLandscape && portraitQualityOpen) {
        closePortraitQuality()
    }
    BackHandler(enabled = !isLandscape && portraitMoreOpen) {
        closePortraitMore()
    }
    BackHandler(enabled = !isLandscape && portraitCommentsOpen) {
        if (portraitCommentsSheetFrac.value >= 0.97f) {
            portraitSheetScope.launch {
                portraitCommentsSheetFrac.animateCommentSheetFrac(2f / 3f)
            }
        } else {
            closePortraitComments()
        }
    }

    // 打开歌词样式：先确保歌词页铺开并量到 band，再钉克隆
    val portraitDensity = LocalDensity.current
    LaunchedEffect(
        pendingPortraitLyricStyleEditor,
        portraitLyricsOpen,
        portraitLyricsBandCoords,
        portraitPlayerRootCoords,
    ) {
        if (!pendingPortraitLyricStyleEditor || !portraitLyricsOpen) return@LaunchedEffect
        // 等封面→歌词切换与首帧布局（最多约 1s）
        repeat(24) {
            yield()
            val root = portraitPlayerRootCoords
            val lyric = portraitLyricsBandCoords
            if (root != null && lyric != null && root.isAttached && lyric.isAttached) {
                val bandBounds = lyric.boundsInRoot()
                val rootBounds = root.boundsInRoot()
                if (bandBounds.width > 1f && bandBounds.height > 1f) {
                    val srcLeft = with(portraitDensity) { (bandBounds.left - rootBounds.left).toDp() }
                    val srcTop = with(portraitDensity) { (bandBounds.top - rootBounds.top).toDp() }
                    val srcW = with(portraitDensity) { bandBounds.width.toDp() }
                    val srcH = with(portraitDensity) { bandBounds.height.toDp() }
                    val lines = lyricLines
                    val animActive = lyricAnimActiveIndex(lines, state.positionMs, state.durationMs)
                    val focus = lyricFocusIndex(lines, animActive)
                    portraitLyricStyleFrozenPositionMs = state.positionMs
                    portraitLyricStyleSnapshot = LyricStyleSnapshot(
                        lines = lines,
                        focusIndex = focus,
                        playedCount = draftPortraitPlayedCount,
                        upcomingCount = draftPortraitUpcomingCount,
                        lineSpacingDp = draftPortraitLineSpacing,
                        sourceLeftDp = srcLeft,
                        sourceTopDp = srcTop,
                        sourceWidthDp = srcW.coerceAtLeast(48.dp),
                        sourceHeightDp = srcH.coerceAtLeast(48.dp),
                    )
                    pendingPortraitLyricStyleEditor = false
                    portraitLyricStyleEditorOpen = true
                    return@LaunchedEffect
                }
            }
            delay(40)
        }
        pendingPortraitLyricStyleEditor = false
    }

    LaunchedEffect(portraitLyricStyleEditorOpen) {
        if (portraitLyricStyleEditorOpen) {
            portraitLyricStylePanel.snapTo(0f)
            yield()
            yield()
            if (!portraitLyricStyleEditorOpen) return@LaunchedEffect
            // 克隆已盖住源位后再收设置，避免布局跳动
            portraitSettingsOpen = false
            delay(280)
            if (!portraitLyricStyleEditorOpen) return@LaunchedEffect
            portraitLyricStylePanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 920,
                    easing = PortraitLyricStylePanelEasing,
                ),
            )
        } else {
            portraitLyricStylePanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 780,
                    easing = PortraitLyricStylePanelEasing,
                ),
            )
            if (portraitLyricStyleSnapshot != null) {
                delay(280)
            }
            if (reopenSettingsAfterPortraitLyricStyle) {
                reopenSettingsAfterPortraitLyricStyle = false
                openPortraitSettings()
            }
            if (!portraitLyricStyleEditorOpen) {
                portraitLyricStyleSnapshot = null
            }
        }
    }
    val portraitLyricStyleT = portraitLyricStylePanel.value
    val portraitLyricStyleHandoffT = if (
        !portraitLyricStyleEditorOpen && portraitLyricStyleSnapshot != null
    ) {
        ((0.45f - portraitLyricStyleT) / 0.45f).coerceIn(0f, 1f)
    } else {
        0f
    }
    val portraitLiveLyricAlpha = when {
        portraitPosterOpen -> 0f
        portraitLyricStyleSnapshot == null -> 1f
        portraitLyricStyleEditorOpen -> 0f
        else -> portraitLyricStyleHandoffT
    }
    val portraitStyleCloneAlpha = when {
        portraitLyricStyleSnapshot == null -> 0f
        portraitLyricStyleEditorOpen -> 1f
        else -> (1f - portraitLyricStyleHandoffT).coerceIn(0f, 1f)
    }

    val portraitCustomBg = if (!isLandscape) {
        portraitDisplayPrefs.resolvedCustomBackground()
    } else {
        null
    }
    val portraitCustomBgTarget = if (portraitCustomBg != null) 1f else 0f
    val portraitCustomBgT = remember { Animatable(0f) }
    LaunchedEffect(portraitCustomBgTarget, isLandscape) {
        if (isLandscape) {
            portraitCustomBgT.snapTo(0f)
        } else {
            portraitCustomBgT.animateTo(
                portraitCustomBgTarget,
                animationSpec = tween(
                    durationMillis = 480,
                    easing = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f),
                ),
            )
        }
    }
    // UI 放到独立 Composable：避免 NowPlayingScreen 方法过大被 ART verifier 拒绝
    NowPlayingScreenLayers(
        modifier = modifier,
        isLandscape = isLandscape,
        state = state,
        track = track,
        lyricLines = lyricLines,
        lyricPos = lyricPos,
        displayPos = displayPos,
        seekDisplayPos = seekDisplayPos,
        duration = duration,
        sliderDragging = sliderDragging,
        sliderValue = sliderValue,
        onSliderDraggingChange = { sliderDragging = it },
        onSliderValueChange = { sliderValue = it },
        onTogglePlay = onTogglePlay,
        onSkipNext = onSkipNext,
        onSkipPrev = onSkipPrev,
        onCyclePlaybackMode = onCyclePlaybackMode,
        onSeek = onSeek,
        onDismiss = onDismiss,
        onOpenArtist = onOpenArtist,
        onPlayQueueIndex = onPlayQueueIndex,
        onNeedQueueThrough = ::needQueueThrough,
        trackLiked = trackLiked,
        onToggleLike = ::toggleTrackLike,
        displayPrefs = displayPrefs,
        portraitDisplayPrefs = portraitDisplayPrefs,
        onDisplayPrefsChange = ::updateDisplayPrefs,
        onPortraitDisplayPrefsChange = ::updatePortraitDisplayPrefs,
        onPortraitDisplayPrefsFlush = ::flushPortraitDisplayPrefs,
        onDisplayPrefsFlush = ::flushDisplayPrefs,
        settingsHazeState = settingsHazeState,
        rainIntensity = rainIntensity,
        dismissSwipeThresholdPx = dismissSwipeThresholdPx,
        landscapeStartInset = landscapeStartInset,
        audioQuality = audioQuality,
        app = app,
        portraitLyricsOpen = portraitLyricsOpen,
        onPortraitLyricsOpenChange = { portraitLyricsOpen = it },
        portraitSettingsOpen = portraitSettingsOpen,
        portraitScoreOpen = portraitScoreOpen,
        portraitQualityOpen = portraitQualityOpen,
        portraitCommentsOpen = portraitCommentsOpen,
        portraitMoreOpen = portraitMoreOpen,
        portraitPosterOpen = portraitPosterOpen,
        portraitPosterFrozenPositionMs = portraitPosterFrozenPositionMs,
        portraitBackgroundEditorOpen = portraitBackgroundEditorOpen,
        onPortraitBackgroundEditorOpenChange = { portraitBackgroundEditorOpen = it },
        portraitLyricStyleEditorOpen = portraitLyricStyleEditorOpen,
        portraitLyricSelectOpen = portraitLyricSelectOpen,
        portraitSettingsT = portraitSettingsT,
        portraitScoreT = portraitScoreT,
        portraitQualityT = portraitQualityT,
        portraitCommentsT = portraitCommentsT,
        portraitMoreT = portraitMoreT,
        portraitLyricSelectT = portraitLyricSelectT,
        portraitLyricStyleT = portraitLyricStyleT,
        portraitLiveLyricAlpha = portraitLiveLyricAlpha,
        portraitStyleCloneAlpha = portraitStyleCloneAlpha,
        portraitCustomBg = portraitCustomBg,
        portraitCustomBgProgress = portraitCustomBgT.value,
        portraitSheetFrac = portraitSheetFrac,
        portraitMoreSheetFrac = portraitMoreSheetFrac,
        portraitScoreSheetFrac = portraitScoreSheetFrac,
        portraitCommentsSheetFrac = portraitCommentsSheetFrac,
        portraitSheetDragVel = portraitSheetDragVel,
        onPortraitSheetDragVelChange = { portraitSheetDragVel = it },
        portraitMoreSheetDragVel = portraitMoreSheetDragVel,
        onPortraitMoreSheetDragVelChange = { portraitMoreSheetDragVel = it },
        portraitScoreSheetDragVel = portraitScoreSheetDragVel,
        onPortraitScoreSheetDragVelChange = { portraitScoreSheetDragVel = it },
        portraitMoreNested = portraitMoreNested,
        onPortraitMoreNestedChange = { portraitMoreNested = it },
        portraitMoreSavedFrac = portraitMoreSavedFrac,
        onPortraitMoreSavedFracChange = { portraitMoreSavedFrac = it },
        portraitSheetScope = portraitSheetScope,
        portraitScoreRevealToken = portraitScoreRevealToken,
        onPortraitPlayerRootCoords = { portraitPlayerRootCoords = it },
        onPortraitLyricsBandCoords = { portraitLyricsBandCoords = it },
        portraitLyricSelectSelected = portraitLyricSelectSelected,
        portraitLyricSelectResumeToken = portraitLyricSelectResumeToken,
        onPortraitLyricSelectResumeTokenChange = { portraitLyricSelectResumeToken = it },
        portraitLyricStyleSnapshot = portraitLyricStyleSnapshot,
        portraitLyricStyleFrozenPositionMs = portraitLyricStyleFrozenPositionMs,
        draftPortraitLyricPlaying = draftPortraitLyricPlaying,
        draftPortraitLyricPlayed = draftPortraitLyricPlayed,
        draftPortraitLyricUnplayed = draftPortraitLyricUnplayed,
        draftPortraitPlayedCount = draftPortraitPlayedCount,
        draftPortraitUpcomingCount = draftPortraitUpcomingCount,
        draftPortraitLineSpacing = draftPortraitLineSpacing,
        onDraftPortraitLyricPlayingChange = { draftPortraitLyricPlaying = it },
        onDraftPortraitLyricPlayedChange = { draftPortraitLyricPlayed = it },
        onDraftPortraitLyricUnplayedChange = { draftPortraitLyricUnplayed = it },
        onDraftPortraitPlayedCountChange = { draftPortraitPlayedCount = it },
        onDraftPortraitUpcomingCountChange = { draftPortraitUpcomingCount = it },
        onDraftPortraitLineSpacingChange = { draftPortraitLineSpacing = it },
        closePortraitSettings = ::closePortraitSettings,
        closePortraitScore = ::closePortraitScore,
        closePortraitQuality = ::closePortraitQuality,
        closePortraitComments = ::closePortraitComments,
        closePortraitMore = ::closePortraitMore,
        closePortraitLyricSelect = ::closePortraitLyricSelect,
        closePortraitPoster = ::closePortraitPoster,
        closePortraitLyricStyleEditor = ::closePortraitLyricStyleEditor,
        openPortraitMore = ::openPortraitMore,
        openPortraitScore = ::openPortraitScore,
        openPortraitQuality = ::openPortraitQuality,
        openPortraitComments = ::openPortraitComments,
        openPortraitSettings = ::openPortraitSettings,
        openPortraitPoster = ::openPortraitPoster,
        openPortraitLyricSelect = ::openPortraitLyricSelect,
        requestPortraitLyricStyleEditor = ::requestPortraitLyricStyleEditor,
        snapPortraitSheet = ::snapPortraitSheet,
        snapPortraitMoreSheet = ::snapPortraitMoreSheet,
        snapPortraitScoreSheet = ::snapPortraitScoreSheet,
    )
}

