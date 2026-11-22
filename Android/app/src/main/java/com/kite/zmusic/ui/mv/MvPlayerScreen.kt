package com.kite.zmusic.ui.mv

import android.content.res.Configuration
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.MvArtist
import com.kite.zmusic.data.NcmCommentParse
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmMvParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.SongComment
import com.kite.zmusic.playback.MvPlayback
import com.kite.zmusic.playback.MvUiState
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.LocalChromeBackdrop
import com.kite.zmusic.ui.main.LocalChromeHaze
import com.kite.zmusic.ui.common.PredictiveBackAxis
import com.kite.zmusic.ui.common.predictiveBackLayer
import com.kite.zmusic.ui.common.rememberPredictiveBackUi
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.islandLiquidGlass
import com.kite.zmusic.ui.main.mainLiquidGlass
import com.kite.zmusic.ui.main.playerOverlayGlass
import com.kite.zmusic.ui.orientation.LocalSessionRotationLock
import com.kite.zmusic.ui.orientation.SessionRotationLockStore
import com.kite.zmusic.ui.orientation.rememberSystemAutoRotateEnabled
import com.kite.zmusic.ui.player.NowPlayingRotationLockButton
import com.kite.zmusic.ui.player.PlaybackModeControl
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.kite.zmusic.i18n.t

private val MvSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SkipMs = 5_000L
/** 双击快进/快退只占左右窄边，中间大面积给暂停。 */
private const val SkipEdge = 0.16f
private val MvEase = FastOutSlowInEasing
private val MvChromeTopIn = fadeIn(tween(220, easing = MvEase)) +
    slideInVertically(tween(300, easing = MvEase)) { -it / 3 }
private val MvChromeTopOut = fadeOut(tween(160)) +
    slideOutVertically(tween(220, easing = MvEase)) { -it / 3 }
private val MvChromeBottomIn = fadeIn(tween(220, easing = MvEase)) +
    slideInVertically(tween(300, easing = MvEase)) { it / 3 }
private val MvChromeBottomOut = fadeOut(tween(160)) +
    slideOutVertically(tween(220, easing = MvEase)) { it / 3 }
/** 横屏唤醒时顶/底满宽玻璃条的内容区高度（不含系统 inset）。 */
private val MvLandChromeBarH = 52.dp
private val MvTopFade = Brush.verticalGradient(
    0f to Color(0x66000000),
    1f to Color.Transparent,
)
/** 竖屏 16:9：视频已避开状态栏，顶栏 scrim 只压画面上沿。 */
private val MvWatchTopScrim = Brush.verticalGradient(
    0.00f to Color(0x59000000),
    0.40f to Color(0x29000000),
    1.00f to Color.Transparent,
)
private val MvChipFill = Color(0x66000000)
private val MvChipStroke = Color.White.copy(alpha = 0.22f)

@OptIn(UnstableApi::class)
@Composable
fun MvPlayerScreen(
    overlay: MainOverlay.Mv,
    playback: MvPlayback,
    ui: MvUiState,
    onBack: () -> Unit,
    onOpenMv: (MainOverlay.Mv) -> Unit,
    onOpenArtist: (MvArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalActivity.current
    val rotationLock = LocalSessionRotationLock.current
    val rotationLocked = SessionRotationLockStore.locked
    val systemAutoRotate = rememberSystemAutoRotateEnabled()
    val backdrop = rememberLayerBackdrop()
    var chrome by remember { mutableStateOf(true) }
    var sidePanel by remember { mutableStateOf(false) }
    var speedSheet by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    /** 仅「竖屏点全屏」产生的临时横屏；不根据进页时的方向记忆，避免中途改旋转后逻辑滞后。 */
    var tempLandscape by rememberSaveable { mutableStateOf(false) }
    val ratio = remember(ui.videoWidth, ui.videoHeight) {
        val w = ui.videoWidth
        val h = ui.videoHeight
        if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 16f / 9f
    }

    val openRelated: (RecommendMvCard) -> Unit = { card ->
        playback.play(card.id, card.name, card.coverUrl, card.artist, fromSkip = true)
        onOpenMv(card.toOverlay())
    }
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val mvId = ui.mvId.takeIf { it > 0L } ?: overlay.id
    val leadArtist = ui.artists.firstOrNull { it.id > 0L }
    // 侧栏跟作者走，不跟当前 MV。同一作者点进去只拿掉被点的那条，不重新拉取。
    var authorMvs by remember { mutableStateOf<List<RecommendMvCard>>(emptyList()) }
    var authorLoading by remember { mutableStateOf(false) }
    var authorForArtist by remember { mutableLongStateOf(0L) }
    var authorReady by remember { mutableStateOf(false) }
    var authorHidden by remember { mutableStateOf(emptySet<Long>()) }
    val playingNow by rememberUpdatedState(mvId)
    val artistNow by rememberUpdatedState(leadArtist?.id)
    var comments by remember(mvId) { mutableStateOf<List<SongComment>>(emptyList()) }
    var commentsHasMore by remember(mvId) { mutableStateOf(false) }
    var commentsLoading by remember(mvId) { mutableStateOf(true) }
    var commentsLoadingMore by remember(mvId) { mutableStateOf(false) }
    var commentError by remember(mvId) { mutableStateOf<String?>(null) }
    var commentOffset by remember(mvId) { mutableIntStateOf(0) }
    var commentPageTick by remember(mvId) { mutableIntStateOf(0) }
    var showAllComments by remember(mvId) { mutableStateOf(false) }
    LaunchedEffect(leadArtist?.id) {
        val artistId = leadArtist?.id ?: return@LaunchedEffect
        if (artistId == authorForArtist && authorReady) return@LaunchedEffect
        val artistChanged = authorForArtist != 0L && authorForArtist != artistId
        if (artistChanged) {
            authorMvs = emptyList()
            authorHidden = emptySet()
            authorReady = false
        }
        authorForArtist = artistId
        authorHidden = authorHidden + playingNow
        if (authorMvs.isEmpty()) authorLoading = true
        val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
        val loaded = runCatching {
            loadAuthorMvs(app.ncmUserClient, artistId, cookie, currentMvId = playingNow)
        }.getOrDefault(emptyList())
        if (artistNow != artistId) return@LaunchedEffect
        if (!authorReady) {
            val hide = authorHidden + playingNow
            authorHidden = hide
            authorMvs = loaded.filter { it.id !in hide }
            authorReady = true
        }
        authorLoading = false
    }
    LaunchedEffect(mvId) {
        if (mvId <= 0L) {
            commentsLoading = false
            return@LaunchedEffect
        }
        commentsLoading = true
        commentError = null
        val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
        runCatching { fetchMvCommentPage(app.ncmUserClient, mvId, cookie, 0, includeHot = true) }
            .onSuccess { page ->
                comments = page.comments
                commentsHasMore = page.hasMore
                commentOffset = MvCommentPageSize
            }
            .onFailure { commentError = it.message?.ifBlank { null } ?: t("评论加载失败") }
        commentsLoading = false
    }
    LaunchedEffect(mvId, commentPageTick) {
        if (commentPageTick <= 0 || mvId <= 0L || !commentsHasMore) return@LaunchedEffect
        commentsLoadingMore = true
        val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
        val offset = commentOffset
        runCatching { fetchMvCommentPage(app.ncmUserClient, mvId, cookie, offset, includeHot = false) }
            .onSuccess { page ->
                val seen = comments.mapTo(HashSet()) { it.commentId }
                val fresh = page.comments.filter { seen.add(it.commentId) }
                comments = comments + fresh
                commentsHasMore = page.hasMore && fresh.isNotEmpty()
                commentOffset = offset + MvCommentPageSize
            }
        commentsLoadingMore = false
    }
    LaunchedEffect(overlay.id) {
        val st = playback.ui.value
        if (st.mvId == overlay.id && st.active) return@LaunchedEffect
        playback.play(overlay.id, overlay.title, overlay.coverUrl, overlay.artist)
    }
    LaunchedEffect(landscape) {
        if (!landscape) {
            sidePanel = false
            tempLandscape = false
        }
    }
    LaunchedEffect(chrome, ui.playWhenReady, seeking, ui.boosting, speedSheet) {
        if (!chrome || !ui.playWhenReady || seeking || ui.boosting || speedSheet) {
            return@LaunchedEffect
        }
        delay(3_200)
        chrome = false
    }

    val duration = ui.durationMs.coerceAtLeast(0L)
    val pos = if (seeking) seekValue.toLong() else ui.positionMs
    val metaLine = buildList {
        if (ui.playCount > 0L) add(t("%s播放", NcmHomeParse.formatPlayCount(ui.playCount)))
        ui.publishTime?.let { add(it) }
    }.joinToString("  ·  ")
    val artists = ui.artists.ifEmpty {
        overlay.artist?.takeIf { it.isNotBlank() }?.let { listOf(MvArtist(0L, it)) }.orEmpty()
    }
    val title = ui.title.ifBlank { overlay.title }
    val enterTempLandscape = {
        if (!landscape) {
            tempLandscape = true
            rotationLock.forceOrientation(activity, landscape = true)
        }
    }
    val leaveTempLandscape = {
        if (tempLandscape) {
            tempLandscape = false
            rotationLock.forceOrientation(activity, landscape = false)
        }
    }
    val handleBack = {
        if (tempLandscape) leaveTempLandscape() else onBack()
    }
    val backUi = rememberPredictiveBackUi(
        enabled = !tempLandscape && (!showAllComments || landscape),
        onBack = {
            if (!landscape && showAllComments) showAllComments = false else onBack()
        },
    )
    BackHandler(enabled = tempLandscape, onBack = { leaveTempLandscape() })
    val rootMod = modifier.predictiveBackLayer(backUi, PredictiveBackAxis.Vertical)

    val rotation: @Composable () -> Unit = {
        if (landscape && !tempLandscape) {
            NowPlayingRotationLockButton(
                locked = rotationLocked,
                forceToLandscape = when {
                    systemAutoRotate -> null
                    else -> false
                },
                chromeBackground = false,
                onClick = {
                    if (systemAutoRotate) {
                        rotationLock.toggle(activity)
                    } else {
                        rotationLock.forceOrientation(activity, landscape = false)
                    }
                },
            )
        } else {
            MvFullscreenButton(
                exit = tempLandscape,
                onClick = {
                    if (tempLandscape) leaveTempLandscape() else enterTempLandscape()
                },
            )
        }
    }

    val seekStart = {
        seeking = true
        seekValue = ui.positionMs.toFloat()
    }
    val seekChange: (Float) -> Unit = { seekValue = it }
    val seekEnd = {
        seeking = false
        playback.seekTo(seekValue.toLong())
    }

    val mvHaze = remember { HazeState() }
    CompositionLocalProvider(
        LocalChromeHaze provides mvHaze,
        LocalChromeBackdrop provides backdrop,
    ) {
    if (landscape) {
        LandscapeMvScene(
            playback = playback,
            ui = ui,
            pos = pos,
            duration = duration,
            seeking = seeking,
            chrome = chrome,
            sidePanel = sidePanel,
            backdrop = backdrop,
            ratio = ratio,
            title = title,
            metaLine = metaLine,
            artists = artists,
            coverUrl = ui.coverUrl ?: overlay.coverUrl,
            rotation = rotation,
            onBackToPortrait = handleBack,
            onSeekStart = seekStart,
            onSeek = seekChange,
            onSeekEnd = seekEnd,
            onToggleChrome = { chrome = !chrome },
            onToggleSplit = {
                sidePanel = !sidePanel
                chrome = true
            },
            onSpeed = { speedSheet = true },
            onOpenMv = { mv ->
                authorHidden = authorHidden + mv.id
                authorMvs = authorMvs.filter { it.id != mv.id }
                playback.play(mv.id, mv.title, mv.coverUrl, mv.artist, fromSkip = true)
                onOpenMv(mv)
            },
            authorMvs = authorMvs,
            authorLoading = authorLoading,
            authorTitle = leadArtist?.name?.let { t("%s 的 MV", it) } ?: t("作者的 MV"),
            onArtist = onOpenArtist,
            modifier = rootMod,
        )
    } else {
        Column(rootMod.fillMaxSize()) {
            // 状态栏区域留空：透出主壳壁纸/页面底色，与主页一致；视频从状态栏下开始。
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars),
            )
            MvVideoStage(
                playback = playback,
                ui = ui,
                overlay = overlay,
                pos = pos,
                duration = duration,
                seeking = seeking,
                chrome = chrome,
                liquid = false,
                backdrop = backdrop,
                metaLine = metaLine,
                sitOnNav = false,
                onVideoMeta = false,
                onBack = {
                    if (showAllComments) showAllComments = false else onBack()
                },
                rotation = rotation,
                onSeekStart = seekStart,
                onSeek = seekChange,
                onSeekEnd = seekEnd,
                onToggleChrome = { chrome = !chrome },
                onSpeed = { speedSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clipToBounds()
                    .background(Color.Black),
            )
            MvPortraitColumn(
                related = ui.related,
                relatedHasMore = ui.relatedHasMore,
                relatedLoading = ui.relatedLoading,
                onLoadMoreRelated = { playback.loadMoreRelated() },
                onOpenRelated = openRelated,
                comments = comments,
                commentsHasMore = commentsHasMore,
                commentsLoading = commentsLoading,
                commentsLoadingMore = commentsLoadingMore,
                commentError = commentError,
                showAllComments = showAllComments,
                onShowAllComments = { showAllComments = true },
                onExitComments = { showAllComments = false },
                mvId = mvId,
                onLoadMoreComments = { if (!commentsLoadingMore && commentsHasMore) commentPageTick += 1 },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MainPalette.Page)
                    .navigationBarsPadding(),
                header = {
                    MvWatchHeader(
                        title = title,
                        artists = artists,
                        metaLine = metaLine,
                        onArtist = onOpenArtist,
                    )
                },
            )
        }
    }
    }
    if (speedSheet) {
        GlassActionSheet(
            title = t("播放速度"),
            onDismiss = { speedSheet = false },
            actions = MvSpeeds.map { speed ->
                val label = if (speed == 1f) t("1.0x 正常") else "${trimSpeed(speed)}x"
                GlassSheetAction(label) {
                    playback.setSpeed(speed)
                    speedSheet = false
                }
            },
        )
    }
}

private fun RecommendMvCard.toOverlay() = MainOverlay.Mv(
    id = id,
    title = name,
    coverUrl = coverUrl,
    artist = artist,
)

@Composable
private fun LandscapeMvScene(
    playback: MvPlayback,
    ui: MvUiState,
    pos: Long,
    duration: Long,
    seeking: Boolean,
    chrome: Boolean,
    sidePanel: Boolean,
    backdrop: LayerBackdrop,
    ratio: Float,
    title: String,
    metaLine: String,
    artists: List<MvArtist>,
    coverUrl: String?,
    rotation: @Composable () -> Unit,
    onBackToPortrait: () -> Unit,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    onToggleChrome: () -> Unit,
    onToggleSplit: () -> Unit,
    onSpeed: () -> Unit,
    onOpenMv: (MainOverlay.Mv) -> Unit,
    authorMvs: List<RecommendMvCard>,
    authorLoading: Boolean,
    authorTitle: String,
    onArtist: (MvArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showChrome = chrome || seeking
    val mvHaze = LocalChromeHaze.current
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val t by animateFloatAsState(
                targetValue = if (sidePanel) 1f else 0f,
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                label = "mvSplit",
            )
            // 侧栏与左侧舞台互补占满全宽，动画全程贴边，不另做位移以免露缝。
            val panelW = lerp(0.dp, maxWidth * 0.38f, t)
            val stageW = maxWidth - panelW
            val videoW = minOf(stageW, maxHeight * ratio)
            val videoH = videoW / ratio
            val videoX = (stageW - videoW) / 2
            val videoY = (maxHeight - videoH) / 2
            val chromeW = stageW
            // 封面 + 视频必须落在同一 layerBackdrop / hazeSource 内，顶底玻璃才能采到真画面。
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .then(
                        if (mvHaze != null) {
                            Modifier.hazeSource(state = mvHaze, zIndex = 0f)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1C1C1E)),
                ) {
                    UrlImage(
                        url = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        showPlaceholder = false,
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                )
                Box(
                    Modifier
                        .offset(videoX, videoY)
                        .size(videoW, videoH),
                ) {
                    MvSurface(playback = playback, modifier = Modifier.fillMaxSize())
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(chromeW)
                    .fillMaxHeight(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .mvGestures(
                            onTap = {
                                if (sidePanel) onToggleSplit() else onToggleChrome()
                            },
                            onDoubleTap = { fraction ->
                                when {
                                    fraction < SkipEdge -> playback.skipBy(-SkipMs)
                                    fraction > 1f - SkipEdge -> playback.skipBy(SkipMs)
                                    else -> playback.togglePlayPause()
                                }
                            },
                            onBoost = playback::setBoosting,
                        ),
                )
                AnimatedVisibility(
                    visible = showChrome,
                    modifier = Modifier.align(Alignment.TopStart),
                    enter = MvChromeTopIn,
                    exit = MvChromeTopOut,
                ) {
                    MvLandTop(
                        title = title,
                        metaLine = metaLine,
                        artists = artists,
                        haze = mvHaze,
                        backdrop = backdrop,
                        onBack = onBackToPortrait,
                        onArtist = onArtist,
                    )
                }
                AnimatedVisibility(
                    visible = showChrome,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = MvChromeBottomIn,
                    exit = MvChromeBottomOut,
                ) {
                    MvLandBottom(
                        pos = pos,
                        duration = duration,
                        ui = ui,
                        haze = mvHaze,
                        onSeekStart = onSeekStart,
                        onSeek = onSeek,
                        onSeekEnd = onSeekEnd,
                        onToggle = { playback.togglePlayPause() },
                        onSpeed = onSpeed,
                        onCycleMode = { playback.cyclePlaybackMode() },
                        rotation = rotation,
                        relatedOpen = sidePanel,
                        onRelated = onToggleSplit,
                    )
                }
                MvStageStatus(ui = ui)
            }
            if (panelW > 0.5.dp) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(panelW)
                        .mainLiquidGlass(
                            backdrop,
                            RectangleShape,
                            Color.White.copy(alpha = 0.46f),
                        ),
                ) {
                    if (authorLoading && authorMvs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = MainPalette.Accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    } else {
                        RelatedMvColumn(
                            items = authorMvs,
                            hasMore = false,
                            loadingMore = false,
                            onLoadMore = {},
                            onOpen = { onOpenMv(it.toOverlay()) },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding(),
                            header = {
                                Text(
                                    text = if (authorMvs.isEmpty()) t("还没有其他 MV") else authorTitle,
                                    color = MainPalette.Ink,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MvLandTop(
    title: String,
    metaLine: String,
    artists: List<MvArtist>,
    haze: HazeState?,
    backdrop: LayerBackdrop,
    onBack: () -> Unit,
    onArtist: (MvArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .playerOverlayGlass(
                    shape = RectangleShape,
                    haze = haze,
                    solidColor = Color(0xE612141C),
                    liquidSurface = MainPalette.glassFill(0.22f),
                ),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(MvLandChromeBarH)
                    .padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MvPlainIcon(onClick = onBack) {
                    Icon(
                        imageVector = ZIcons.Back,
                        contentDescription = t("返回"),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (metaLine.isNotEmpty()) {
                        Text(
                            text = metaLine,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (artists.isNotEmpty()) {
            LandArtistCapsules(
                artists = artists,
                backdrop = backdrop,
                onArtist = onArtist,
                modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 10.dp),
            )
        }
    }
}

@Composable
private fun MvLandBottom(
    pos: Long,
    duration: Long,
    ui: MvUiState,
    haze: HazeState?,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    onToggle: () -> Unit,
    onSpeed: () -> Unit,
    onCycleMode: () -> Unit,
    rotation: @Composable () -> Unit,
    relatedOpen: Boolean,
    onRelated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .playerOverlayGlass(
                shape = RectangleShape,
                haze = haze,
                solidColor = Color(0xE612141C),
                liquidSurface = MainPalette.glassFill(0.22f),
            ),
    ) {
        // 高度只包控件；底对齐屏幕自然侵入小白条，勿把 nav inset 加进条高（会在控件上方留空）。
        Row(
            Modifier
                .fillMaxWidth()
                .height(MvLandChromeBarH)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MvPlainIcon(onClick = onToggle) {
                Icon(
                    imageVector = if (ui.playWhenReady) ZIcons.Pause else ZIcons.Play,
                    contentDescription = if (ui.playWhenReady) t("暂停") else t("播放"),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            ) {
                MvScrubber(
                    positionMs = pos,
                    durationMs = duration,
                    onSeekStart = onSeekStart,
                    onSeek = onSeek,
                    onSeekEnd = onSeekEnd,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatMs(pos), color = Color.White.copy(alpha = 0.88f), fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatMs(duration), color = Color.White.copy(alpha = 0.88f), fontSize = 10.sp)
                }
            }
            Text(
                text = if (ui.speed == 1f) t("倍速") else "${trimSpeed(ui.speed)}x",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSpeed,
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            PlaybackModeControl(
                mode = ui.playbackMode,
                onClick = onCycleMode,
                circleSize = 32.dp,
                tint = Color.White,
                glyphFraction = 0.68f,
            )
            rotation()
            MvPlainIcon(onClick = onRelated) {
                Icon(
                    imageVector = if (relatedOpen) ZIcons.Close else ZIcons.RelatedMv,
                    contentDescription = if (relatedOpen) t("收起作者 MV") else t("作者的 MV"),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LandArtistCapsules(
    artists: List<MvArtist>,
    backdrop: LayerBackdrop,
    onArtist: (MvArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        artists.forEach { artist ->
            Row(
                modifier = Modifier
                    .islandLiquidGlass(backdrop, RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onArtist(artist) },
                    )
                    .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlImage(
                    url = artist.avatarUrl,
                    contentDescription = artist.name,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = artist.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

private const val MvCommentPageSize = 20
private const val MvCommentPreviewCount = 4
private val MvCommentTitleHeight = 40.dp
private val MvCommentRowHeight = 52.dp
private val MvCommentPreviewBlock = MvCommentTitleHeight + MvCommentRowHeight * MvCommentPreviewCount
private val MvCommentLine = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

@Composable
private fun MvPortraitColumn(
    related: List<RecommendMvCard>,
    relatedHasMore: Boolean,
    relatedLoading: Boolean,
    onLoadMoreRelated: () -> Unit,
    onOpenRelated: (RecommendMvCard) -> Unit,
    comments: List<SongComment>,
    commentsHasMore: Boolean,
    commentsLoading: Boolean,
    commentsLoadingMore: Boolean,
    commentError: String?,
    showAllComments: Boolean,
    onShowAllComments: () -> Unit,
    onExitComments: () -> Unit,
    onLoadMoreComments: () -> Unit,
    mvId: Long,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
) {
    val relatedListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    // 评论未打开时，评论预览和相关 MV 是同一条列表，一起被带走。
    val feedState = rememberLazyListState()
    val density = LocalDensity.current
    val previewPx = with(density) { MvCommentPreviewBlock.roundToPx() }
    var fullPx by remember { mutableIntStateOf(0) }
    val commentExpand = remember(mvId) { Animatable(if (showAllComments) 1f else 0f) }
    var restoreTick by remember(mvId) { mutableIntStateOf(0) }
    // 评论区单独占一个返回节点，手势先把高度收回来，再交给播放页。
    val commentBack = rememberPredictiveBackUi(
        enabled = showAllComments,
        onGestureCancel = { restoreTick += 1 },
        onBack = onExitComments,
    )
    LaunchedEffect(showAllComments, mvId) {
        commentExpand.animateTo(
            if (showAllComments) 1f else 0f,
            tween(360, easing = MvEase),
        )
    }
    LaunchedEffect(commentBack.progress) {
        if (showAllComments && commentBack.progress > 0f) {
            commentExpand.snapTo((1f - commentBack.progress).coerceIn(0f, 1f))
        }
    }
    LaunchedEffect(restoreTick) {
        if (restoreTick > 0 && showAllComments) {
            commentExpand.animateTo(1f, tween(220, easing = MvEase))
        }
    }
    val expand = commentExpand.value
    val commentsClosed = !showAllComments && expand <= 0.001f
    val feedNearEnd by remember {
        derivedStateOf {
            val last = feedState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = feedState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    val commentNearEnd by remember {
        derivedStateOf {
            val last = commentListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = commentListState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(mvId) {
        relatedListState.scrollToItem(0)
        commentListState.scrollToItem(0)
        feedState.scrollToItem(0)
    }
    LaunchedEffect(showAllComments) {
        if (showAllComments) feedState.scrollToItem(0)
    }
    LaunchedEffect(feedNearEnd, showAllComments, related.size, relatedHasMore, relatedLoading, expand) {
        if (!showAllComments && expand < 0.05f && feedNearEnd && relatedHasMore && related.isNotEmpty() && !relatedLoading) {
            onLoadMoreRelated()
        }
    }
    LaunchedEffect(relatedHasMore, related.size, relatedLoading, showAllComments) {
        if (!showAllComments && relatedHasMore && !relatedLoading && related.isNotEmpty() && related.size < 8) {
            onLoadMoreRelated()
        }
    }
    LaunchedEffect(commentNearEnd, showAllComments, comments.size, commentsHasMore, commentsLoadingMore) {
        if (showAllComments && commentNearEnd && commentsHasMore && comments.isNotEmpty() && !commentsLoadingMore) {
            onLoadMoreComments()
        }
    }
    val revealAll = showAllComments || expand > 0.001f
    val endPx = fullPx.takeIf { it > 0 } ?: previewPx
    val startPx = previewPx.coerceAtMost(endPx)
    val heightPx = startPx + (endPx - startPx) * expand
    Column(modifier.clipToBounds()) {
        header()
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { fullPx = it.height },
        ) {
            if (commentsClosed) {
                LazyColumn(
                    state = feedState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    item(key = "comment-preview") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(MainPalette.Page),
                        ) {
                            MvCommentTitle(
                                expanded = false,
                                onShowAll = onShowAllComments,
                                onExit = onExitComments,
                            )
                            MvCommentPreview(
                                comments = comments,
                                loading = comments.isEmpty() && commentsLoading,
                                error = commentError,
                            )
                        }
                    }
                    items(related, key = { "m${it.id}" }) { card ->
                        RelatedRow(card, onOpen = { onOpenRelated(card) })
                    }
                    if (relatedLoading) {
                        item(key = "related-more") { MvListSpinner() }
                    }
                }
            } else if (expand < 0.995f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = with(density) { heightPx.toDp() })
                        .clipToBounds(),
                ) {
                    LazyColumn(
                        state = relatedListState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false,
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(related, key = { "m${it.id}" }) { card ->
                            RelatedRow(card, onOpen = { onOpenRelated(card) })
                        }
                        if (relatedLoading) {
                            item(key = "related-more") { MvListSpinner() }
                        }
                    }
                }
            }
            if (!commentsClosed) Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(with(density) { heightPx.coerceAtLeast(1f).toDp() })
                    .clipToBounds()
                    .background(MainPalette.Page),
            ) {
                MvCommentTitle(
                    expanded = showAllComments,
                    onShowAll = onShowAllComments,
                    onExit = onExitComments,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
                ) {
                    if (revealAll && comments.isNotEmpty()) {
                        MvCommentFull(
                            comments = comments,
                            loadingMore = commentsLoadingMore,
                            listState = commentListState,
                            scrollable = showAllComments && expand > 0.98f && commentBack.progress == 0f,
                        )
                    } else {
                        MvCommentPreview(
                            comments = comments,
                            loading = comments.isEmpty() && commentsLoading,
                            error = commentError,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MvListSpinner() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MainPalette.Accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MvCommentTitle(
    expanded: Boolean,
    onShowAll: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(MvCommentTitleHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = expanded,
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
            transitionSpec = {
                (fadeIn(tween(180, easing = MvEase)) +
                    slideInHorizontally(tween(260, easing = MvEase)) { -it / 4 })
                    .togetherWith(
                        fadeOut(tween(140)) +
                            slideOutHorizontally(tween(220, easing = MvEase)) { -it / 5 },
                    )
            },
            label = "mvCommentTitle",
        ) { open ->
            if (open) {
                Row(
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExit,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = ZIcons.ChevronLeft,
                        contentDescription = null,
                        tint = MainPalette.Ink,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = t("退出评论"),
                        color = MainPalette.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Text(
                    text = t("评论"),
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(tween(180, easing = MvEase)),
            exit = fadeOut(tween(120)),
        ) {
            Text(
                text = t("全部评论"),
                color = MainPalette.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onShowAll,
                    )
                    .padding(vertical = 8.dp, horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun MvCommentPreview(
    comments: List<SongComment>,
    loading: Boolean,
    error: String?,
) {
    when {
        loading -> MvCommentPlaceholders()
        error != null && comments.isEmpty() -> MvCommentNote(error)
        comments.isEmpty() -> MvCommentNote(t("还没有评论"))
        else -> Column {
            repeat(MvCommentPreviewCount) { index ->
                val comment = comments.getOrNull(index)
                if (comment != null) MvCommentRow(comment) else Spacer(Modifier.height(MvCommentRowHeight))
            }
        }
    }
}

@Composable
private fun MvCommentFull(
    comments: List<SongComment>,
    loadingMore: Boolean,
    listState: LazyListState,
    scrollable: Boolean,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = scrollable,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(comments, key = { "c${it.commentId}" }) { comment ->
            MvCommentRow(comment)
        }
        if (loadingMore) {
            item(key = "comment-more") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MvCommentRowHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MvCommentNote(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(MvCommentPreviewBlock - MvCommentTitleHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MainPalette.Secondary,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun MvCommentPlaceholders() {
    val transition = rememberInfiniteTransition(label = "mvCommentShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mvCommentShimmerShift",
    )
    Column(Modifier.mvDiagonalShimmer(progress)) {
        repeat(MvCommentPreviewCount) { index ->
            MvCommentPlaceholderRow(index)
        }
    }
}

private fun Modifier.mvDiagonalShimmer(progress: Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val span = size.width + size.height
            if (span <= 0f) return@drawWithContent
            val head = -span + span * 2f * progress
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.38f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.5f),
                        0.62f to Color.Transparent,
                    ),
                    start = Offset(head, 0f),
                    end = Offset(head + size.height, size.height),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }

@Composable
private fun MvCommentPlaceholderRow(index: Int) {
    val body = when (index % 4) {
        0 -> 0.92f
        1 -> 0.64f
        2 -> 0.8f
        else -> 0.5f
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(MvCommentRowHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MainPalette.Placeholder),
        )
        Column(
            Modifier
                .padding(start = 10.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .width(72.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MainPalette.Placeholder),
            )
            Box(
                Modifier
                    .fillMaxWidth(body)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MainPalette.Placeholder),
            )
        }
    }
}

@Composable
private fun MvCommentRow(comment: SongComment) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(MvCommentRowHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            url = comment.avatarUrl,
            contentDescription = comment.nickname,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MainPalette.Placeholder),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier
                .padding(start = 10.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.nickname,
                    color = MainPalette.Ink.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MvCommentLine,
                    modifier = Modifier.weight(1f),
                )
                if (comment.timeLabel.isNotBlank()) {
                    Text(
                        text = comment.timeLabel,
                        color = MainPalette.Secondary.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        style = MvCommentLine,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(
                text = comment.content.ifBlank { " " },
                color = MainPalette.Ink.copy(alpha = 0.88f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MvCommentLine,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private suspend fun fetchMvCommentPage(
    client: NcmUserClient,
    mvId: Long,
    cookie: String,
    offset: Int,
    includeHot: Boolean,
) = NcmCommentParse.pageFromCommentMusic(
    client.commentMv(mvId, cookie, limit = MvCommentPageSize, offset = offset),
    includeHotFirst = includeHot,
)

private suspend fun loadAuthorMvs(
    client: NcmUserClient,
    artistId: Long,
    cookie: String,
    currentMvId: Long,
): List<RecommendMvCard> {
    val pageSize = 50
    val all = ArrayList<RecommendMvCard>()
    var offset = 0
    var page = 0
    while (page < 6) {
        val json = client.artistMv(artistId, cookie, pageSize, offset)
        val raw = NcmMvParse.similar(json)
        all += raw
        if (!NcmMvParse.hasMore(json, raw.size, pageSize) || raw.isEmpty()) break
        offset += pageSize
        page += 1
    }
    return all
        .filter { it.id != currentMvId }
        .distinctBy { it.id }
        .sortedByDescending { it.playCount }
}

@Composable
private fun RelatedMvColumn(
    items: List<RecommendMvCard>,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (RecommendMvCard) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd, items.size, hasMore, loadingMore) {
        if (nearEnd && hasMore && items.isNotEmpty() && !loadingMore) {
            onLoadMore()
        }
    }
    LaunchedEffect(hasMore, items.size, loadingMore) {
        if (hasMore && !loadingMore && items.isNotEmpty() && items.size < 8) {
            onLoadMore()
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (header != null) {
            item(key = "info") { header() }
        }
        items(items, key = { it.id }) { card ->
            Box(
                Modifier.animateItem(
                    fadeInSpec = tween(180, easing = FastOutSlowInEasing),
                    placementSpec = tween<IntOffset>(280, easing = FastOutSlowInEasing),
                    fadeOutSpec = tween(160, easing = FastOutSlowInEasing),
                ),
            ) {
                RelatedRow(
                    card = card,
                    onOpen = { onOpen(card) },
                )
            }
        }
        if (loadingMore) {
            item(key = "more") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MvStageStatus(ui: MvUiState) {
    if (ui.loading && ui.positionMs <= 0L && ui.error == null) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.Center),
        )
    }
    ui.error?.let { err ->
        Text(
            text = err,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
        )
    }
    var shownHint by remember { mutableStateOf<String?>(null) }
    if (ui.hint != null) shownHint = ui.hint
    AnimatedVisibility(
        visible = ui.hint != null,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.72f, animationSpec = tween(140)),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.86f, animationSpec = tween(160)),
    ) {
        Text(
            text = shownHint.orEmpty(),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MvVideoStage(
    playback: MvPlayback,
    ui: MvUiState,
    overlay: MainOverlay.Mv,
    pos: Long,
    duration: Long,
    seeking: Boolean,
    chrome: Boolean,
    liquid: Boolean,
    backdrop: LayerBackdrop,
    metaLine: String,
    sitOnNav: Boolean,
    onVideoMeta: Boolean,
    onBack: () -> Unit,
    rotation: @Composable () -> Unit,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    onToggleChrome: () -> Unit,
    onSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        val mvHaze = LocalChromeHaze.current
        Box(
            Modifier
                .fillMaxSize()
                .then(if (liquid) Modifier.layerBackdrop(backdrop) else Modifier)
                .then(
                    if (mvHaze != null) {
                        Modifier.hazeSource(state = mvHaze, zIndex = 0f)
                    } else {
                        Modifier
                    },
                ),
        ) {
            MvSurface(playback = playback, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .mvGestures(
                    onTap = onToggleChrome,
                            onDoubleTap = { fraction ->
                                when {
                                    fraction < SkipEdge -> playback.skipBy(-SkipMs)
                                    fraction > 1f - SkipEdge -> playback.skipBy(SkipMs)
                                    else -> playback.togglePlayPause()
                                }
                            },
                    onBoost = playback::setBoosting,
                ),
        )
        AnimatedVisibility(
            visible = chrome && onVideoMeta,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = MvChromeTopIn,
            exit = MvChromeTopOut,
        ) {
            MvTopBar(
                title = ui.title.ifBlank { overlay.title },
                metaLine = metaLine,
                liquid = liquid,
                backdrop = backdrop,
                onBack = onBack,
                rotation = rotation,
            )
        }
        AnimatedVisibility(
            visible = chrome && !onVideoMeta,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = MvChromeTopIn,
            exit = MvChromeTopOut,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(MvWatchTopScrim),
                )
                MvWatchTopBar(onBack = onBack)
            }
        }
        if (onVideoMeta) {
            AnimatedVisibility(
                visible = chrome,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = MvChromeBottomIn,
                exit = MvChromeBottomOut,
            ) {
                MvBottomChrome(
                    pos = pos,
                    duration = duration,
                    ui = ui,
                    liquid = liquid,
                    backdrop = backdrop,
                    onSeekStart = onSeekStart,
                    onSeek = onSeek,
                    onSeekEnd = onSeekEnd,
                    onToggle = { playback.togglePlayPause() },
                    onSpeed = onSpeed,
                    onCycleMode = { playback.cyclePlaybackMode() },
                    sitOnNav = sitOnNav,
                )
            }
        } else {
            // 竖屏：进度条与自定义控件同进同出，拖动时保持可见。
            AnimatedVisibility(
                visible = chrome || seeking,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = MvChromeBottomIn,
                exit = MvChromeBottomOut,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    MvWatchDock(
                        pos = pos,
                        duration = duration,
                        playing = ui.playWhenReady,
                        speed = ui.speed,
                        playbackMode = ui.playbackMode,
                        onToggle = { playback.togglePlayPause() },
                        onSpeed = onSpeed,
                        onCycleMode = { playback.cyclePlaybackMode() },
                        rotation = rotation,
                    )
                    MvScrubber(
                        positionMs = pos,
                        durationMs = duration,
                        onSeekStart = onSeekStart,
                        onSeek = onSeek,
                        onSeekEnd = onSeekEnd,
                    )
                }
            }
        }
        if (ui.loading && ui.positionMs <= 0L && ui.error == null) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.Center),
            )
        }
        ui.error?.let { err ->
            Text(
                text = err,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )
        }
        ui.hint?.let { hint ->
            Text(
                text = hint,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun MvWatchTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MvPlainIcon(onClick = onBack) {
            Icon(
                imageVector = ZIcons.Back,
                contentDescription = t("返回"),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun MvWatchDock(
    pos: Long,
    duration: Long,
    playing: Boolean,
    speed: Float,
    playbackMode: PlaybackMode,
    onToggle: () -> Unit,
    onSpeed: () -> Unit,
    onCycleMode: () -> Unit,
    rotation: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MvPlainIcon(onClick = onToggle) {
            Icon(
                imageVector = if (playing) ZIcons.Pause else ZIcons.Play,
                contentDescription = if (playing) t("暂停") else t("播放"),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = "${formatMs(pos)} / ${formatMs(duration)}",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 2.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (speed == 1f) t("倍速") else "${trimSpeed(speed)}x",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSpeed,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        PlaybackModeControl(
            mode = playbackMode,
            onClick = onCycleMode,
            circleSize = 32.dp,
            tint = Color.White,
            glyphFraction = 0.68f,
        )
        rotation()
    }
}

@Composable
private fun MvWatchHeader(
    title: String,
    artists: List<MvArtist>,
    metaLine: String,
    onArtist: (MvArtist) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MainPalette.Page)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (artists.isNotEmpty()) {
            val lead = artists.first()
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onArtist(lead) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlImage(
                    url = lead.avatarUrl,
                    contentDescription = lead.name,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = artists.joinToString(" / ") { it.name },
                    color = MainPalette.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = title,
            color = MainPalette.Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (metaLine.isNotEmpty()) {
            Text(
                text = metaLine,
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun RelatedRow(
    card: RecommendMvCard,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .width(148.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(MainPalette.Placeholder),
        ) {
            UrlImage(
                url = card.coverUrl,
                contentDescription = card.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (card.playCount > 0L) {
                Text(
                    text = NcmHomeParse.formatPlayCount(card.playCount),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp, top = 2.dp),
        ) {
            Text(
                text = card.name,
                color = MainPalette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!card.artist.isNullOrBlank()) {
                Text(
                    text = card.artist,
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun MvTopBar(
    title: String,
    metaLine: String,
    liquid: Boolean,
    backdrop: LayerBackdrop,
    onBack: () -> Unit,
    rotation: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MvTopFade)
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MvChromeIcon(
            onClick = onBack,
            liquid = liquid,
            backdrop = backdrop,
            size = 28.dp,
        ) {
            Icon(
                imageVector = ZIcons.Back,
                contentDescription = t("返回"),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (metaLine.isNotEmpty()) {
                Text(
                    text = metaLine,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        rotation()
    }
}

@Composable
private fun MvBottomChrome(
    pos: Long,
    duration: Long,
    ui: MvUiState,
    liquid: Boolean,
    backdrop: LayerBackdrop,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    onToggle: () -> Unit,
    onSpeed: () -> Unit,
    onCycleMode: () -> Unit,
    sitOnNav: Boolean,
    modifier: Modifier = Modifier,
    rotation: (@Composable () -> Unit)? = null,
    related: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (sitOnNav) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MvChromeIcon(
            onClick = onToggle,
            liquid = liquid,
            backdrop = backdrop,
            size = 28.dp,
            small = true,
        ) {
            Icon(
                imageVector = if (ui.playWhenReady) ZIcons.Pause else ZIcons.Play,
                contentDescription = if (ui.playWhenReady) t("暂停") else t("播放"),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            MvScrubber(
                positionMs = pos,
                durationMs = duration,
                onSeekStart = onSeekStart,
                onSeek = onSeek,
                onSeekEnd = onSeekEnd,
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatMs(pos), color = Color.White.copy(alpha = 0.88f), fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text(formatMs(duration), color = Color.White.copy(alpha = 0.88f), fontSize = 10.sp)
            }
        }
        MvTextChip(
            text = if (ui.speed == 1f) t("倍速") else "${trimSpeed(ui.speed)}x",
            liquid = liquid,
            backdrop = backdrop,
            onClick = onSpeed,
        )
        PlaybackModeControl(
            mode = ui.playbackMode,
            onClick = onCycleMode,
            circleSize = 28.dp,
            tint = Color.White,
            glyphFraction = 0.68f,
        )
        if (rotation != null) {
            Box(Modifier.padding(start = 2.dp)) { rotation() }
        }
        if (related != null) {
            Box(Modifier.padding(start = 2.dp)) { related() }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MvSurface(
    playback: MvPlayback,
    modifier: Modifier = Modifier,
) {
    val player = playback.player
    AndroidView(
        factory = { ctx ->
            val texture = TextureView(ctx)
            val frame = AspectRatioFrameLayout(ctx).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addView(
                    texture,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    ),
                )
            }
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    applyMvAspect(frame, videoSize)
                }
            }
            frame.tag = MvSurfaceBind(texture, listener)
            player.addListener(listener)
            player.setVideoTextureView(texture)
            applyMvAspect(frame, player.videoSize)
            frame
        },
        update = { frame ->
            applyMvAspect(frame, player.videoSize)
        },
        onRelease = { frame ->
            val bind = frame.tag as? MvSurfaceBind
            if (bind != null) {
                player.removeListener(bind.listener)
                player.clearVideoTextureView(bind.texture)
            }
        },
        modifier = modifier,
    )
}

private class MvSurfaceBind(
    val texture: TextureView,
    val listener: Player.Listener,
)

@OptIn(UnstableApi::class)
private fun applyMvAspect(frame: AspectRatioFrameLayout, size: VideoSize) {
    val w = size.width
    val h = size.height
    if (w > 0 && h > 0) {
        frame.setAspectRatio(w * size.pixelWidthHeightRatio / h)
    }
}

@Composable
private fun MvScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier,
    showThumb: Boolean = true,
) {
    val max = durationMs.toFloat().coerceAtLeast(1f)
    val fraction = (positionMs / max).coerceIn(0f, 1f)
    var barPx by remember { mutableIntStateOf(0) }
    val thumb = 7.dp
    val thumbPx = with(LocalDensity.current) { thumb.toPx() }

    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .onSizeChanged { barPx = it.width }
            .pointerInput(max) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onSeekStart()
                    fun at(x: Float): Float {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        return (x / w).coerceIn(0f, 1f) * max
                    }
                    onSeek(at(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        onSeek(at(change.position.x))
                        change.consume()
                        if (!change.pressed) break
                    }
                    onSeekEnd()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.28f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White),
        )
        if (showThumb && barPx > 0) {
            Box(
                Modifier
                    .offset {
                        val x = (fraction * barPx - thumbPx / 2f).roundToInt()
                            .coerceIn(0, (barPx - thumbPx).roundToInt().coerceAtLeast(0))
                        IntOffset(x, 0)
                    }
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun MvPlainIcon(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun MvTextChip(
    text: String,
    liquid: Boolean,
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .mvChromeSurface(liquid, backdrop, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun MvFullscreenButton(
    exit: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(width = 40.dp, height = 34.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (exit) ZIcons.FullscreenExit else ZIcons.Fullscreen,
            contentDescription = if (exit) t("退出全屏") else t("全屏"),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun MvChromeIcon(
    onClick: () -> Unit,
    liquid: Boolean,
    backdrop: LayerBackdrop,
    size: Dp = 28.dp,
    small: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .mvChromeSurface(liquid, backdrop, CircleShape, small)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun Modifier.mvChromeSurface(
    liquid: Boolean,
    backdrop: LayerBackdrop,
    shape: Shape,
    small: Boolean = false,
): Modifier = if (liquid) {
    if (small) {
        islandLiquidGlass(backdrop, shape)
    } else {
        mainLiquidGlass(backdrop, shape, Color.White.copy(alpha = 0.14f))
    }
} else {
    clip(shape)
        .background(MvChipFill, shape)
        .border(0.4.dp, MvChipStroke, shape)
}

private fun Modifier.mvGestures(
    onTap: () -> Unit,
    onDoubleTap: (fractionX: Float) -> Unit,
    onBoost: (Boolean) -> Unit,
): Modifier = composed {
    val tap = rememberUpdatedState(onTap)
    val doubleTap = rememberUpdatedState(onDoubleTap)
    val boost = rememberUpdatedState(onBoost)
    pointerInput(Unit) {
        val longMs = viewConfiguration.longPressTimeoutMillis
        val doubleMs = viewConfiguration.doubleTapTimeoutMillis
        awaitEachGesture {
            awaitFirstDown()
            val width = size.width.toFloat().coerceAtLeast(1f)
            val held = withTimeoutOrNull(longMs) { waitForUpOrCancellation() }
            if (held == null) {
                boost.value(true)
                try {
                    waitForUpOrCancellation()
                } finally {
                    boost.value(false)
                }
                return@awaitEachGesture
            }
            val second = withTimeoutOrNull(doubleMs) { awaitFirstDown(requireUnconsumed = false) }
            if (second != null) {
                waitForUpOrCancellation()
                doubleTap.value(second.position.x / width)
            } else {
                tap.value()
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = (ms / 1000L).toInt()
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

private fun trimSpeed(speed: Float): String {
    val i = speed.toInt()
    return if (speed == i.toFloat()) i.toString() else speed.toString()
}
