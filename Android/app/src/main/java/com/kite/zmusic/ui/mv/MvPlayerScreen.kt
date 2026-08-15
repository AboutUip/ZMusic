package com.kite.zmusic.ui.mv

import android.content.res.Configuration
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.kite.zmusic.data.MvArtist
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.playback.MvPlayback
import com.kite.zmusic.playback.MvUiState
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.ui.catalog.MainOverlay
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.islandLiquidGlass
import com.kite.zmusic.ui.main.mainLiquidGlass
import com.kite.zmusic.ui.orientation.LocalSessionRotationLock
import com.kite.zmusic.ui.orientation.SessionRotationLockStore
import com.kite.zmusic.ui.orientation.rememberSystemAutoRotateEnabled
import com.kite.zmusic.ui.player.NowPlayingRotationLockButton
import com.kite.zmusic.ui.player.PlaybackModeControl
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

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
private val MvTopFade = Brush.verticalGradient(
    0f to Color(0x66000000),
    1f to Color.Transparent,
)
/** 竖屏 16:9：只压状态栏，不到达画面底边，避免黑带画到下方信息区。 */
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
    var immersiveFullscreen by rememberSaveable { mutableStateOf(false) }
    val openedInPortrait = remember { !landscape }
    LaunchedEffect(landscape) {
        if (landscape) immersiveFullscreen = false
    }
    val useLandscapeScene = landscape || immersiveFullscreen
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
    val ratio = remember(ui.videoWidth, ui.videoHeight) {
        val w = ui.videoWidth
        val h = ui.videoHeight
        if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 16f / 9f
    }

    val openRelated: (RecommendMvCard) -> Unit = { card ->
        playback.play(card.id, card.name, card.coverUrl, card.artist, fromSkip = true)
        onOpenMv(card.toOverlay())
    }
    LaunchedEffect(overlay.id) {
        val st = playback.ui.value
        if (st.mvId == overlay.id && st.active) return@LaunchedEffect
        playback.play(overlay.id, overlay.title, overlay.coverUrl, overlay.artist)
    }
    LaunchedEffect(landscape) {
        if (!landscape) sidePanel = false
    }
    LaunchedEffect(immersiveFullscreen) {
        if (!immersiveFullscreen) sidePanel = false
    }
    LaunchedEffect(chrome, ui.playWhenReady, seeking, ui.boosting, speedSheet, useLandscapeScene, sidePanel) {
        if (!chrome || !ui.playWhenReady || seeking || ui.boosting || speedSheet) {
            return@LaunchedEffect
        }
        if (useLandscapeScene && sidePanel) return@LaunchedEffect
        delay(3_200)
        chrome = false
    }

    val duration = ui.durationMs.coerceAtLeast(0L)
    val pos = if (seeking) seekValue.toLong() else ui.positionMs
    val metaLine = buildList {
        if (ui.playCount > 0L) add("${NcmHomeParse.formatPlayCount(ui.playCount)}播放")
        ui.publishTime?.let { add(it) }
    }.joinToString("  ·  ")
    val artists = ui.artists.ifEmpty {
        overlay.artist?.takeIf { it.isNotBlank() }?.let { listOf(MvArtist(0L, it)) }.orEmpty()
    }
    val title = ui.title.ifBlank { overlay.title }
    val backToPortrait = {
        rotationLock.forceOrientation(activity, landscape = false)
    }
    val handleBack = {
        when {
            immersiveFullscreen -> immersiveFullscreen = false
            landscape && openedInPortrait -> backToPortrait()
            else -> onBack()
        }
    }

    BackHandler(onBack = handleBack)

    val rotation: @Composable () -> Unit = {
        if (landscape) {
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
                exit = immersiveFullscreen,
                onClick = { immersiveFullscreen = !immersiveFullscreen },
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

    if (useLandscapeScene) {
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
                playback.play(mv.id, mv.title, mv.coverUrl, mv.artist, fromSkip = true)
                onOpenMv(mv)
            },
            onLoadMore = { playback.loadMoreRelated() },
            onArtist = onOpenArtist,
            modifier = modifier,
        )
    } else {
        Column(
            modifier
                .fillMaxSize()
                .background(MainPalette.Page),
        ) {
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
                onBack = onBack,
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
            RelatedMvColumn(
                items = ui.related,
                hasMore = ui.relatedHasMore,
                loadingMore = ui.relatedLoading,
                onLoadMore = { playback.loadMoreRelated() },
                onOpen = openRelated,
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
    if (speedSheet) {
        GlassActionSheet(
            title = "播放速度",
            onDismiss = { speedSheet = false },
            actions = MvSpeeds.map { speed ->
                val label = if (speed == 1f) "1.0x 正常" else "${trimSpeed(speed)}x"
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
    onLoadMore: () -> Unit,
    onArtist: (MvArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showChrome = chrome || seeking || sidePanel
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
            val closedW = (maxHeight * ratio).coerceAtMost(maxWidth)
            val openW = maxWidth * 0.62f
            val videoW = lerp(closedW, openW, t)
            val videoH = videoW / ratio
            val videoX = lerp((maxWidth - closedW) / 2, 0.dp, t)
            val videoY = (maxHeight - videoH) / 2
            val panelW = lerp(0.dp, maxWidth - openW, t)
            val chromeW = maxWidth - panelW
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
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
                    MvBottomChrome(
                        pos = pos,
                        duration = duration,
                        ui = ui,
                        seeking = seeking,
                        liquid = true,
                        backdrop = backdrop,
                        onSeekStart = onSeekStart,
                        onSeek = onSeek,
                        onSeekEnd = onSeekEnd,
                        onToggle = { playback.togglePlayPause() },
                        onSpeed = onSpeed,
                        onCycleMode = { playback.cyclePlaybackMode() },
                        sitOnNav = true,
                        rotation = rotation,
                        related = {
                            MvRelatedToggle(
                                open = sidePanel,
                                liquid = true,
                                backdrop = backdrop,
                                onClick = onToggleSplit,
                            )
                        },
                    )
                }
                MvStageStatus(ui = ui)
            }
            if (panelW > 4.dp) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(panelW)
                        .padding(top = 10.dp, end = 10.dp, bottom = 10.dp)
                        .graphicsLayer {
                            alpha = t.coerceIn(0f, 1f)
                            translationX = (1f - t) * 72f
                        }
                        .mainLiquidGlass(
                            backdrop,
                            RoundedCornerShape(24.dp),
                            Color.White.copy(alpha = 0.46f),
                        )
                        .clip(RoundedCornerShape(24.dp)),
                ) {
                    RelatedMvColumn(
                        items = ui.related,
                        hasMore = ui.relatedHasMore,
                        loadingMore = ui.relatedLoading,
                        onLoadMore = onLoadMore,
                        onOpen = { onOpenMv(it.toOverlay()) },
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    )
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
    backdrop: LayerBackdrop,
    onBack: () -> Unit,
    onArtist: (MvArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MvChromeIcon(
                onClick = onBack,
                liquid = true,
                backdrop = backdrop,
                size = 28.dp,
                small = true,
            ) {
                Icon(
                    imageVector = ZIcons.Back,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
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
        if (artists.isNotEmpty()) {
            LandArtistCapsules(
                artists = artists,
                backdrop = backdrop,
                onArtist = onArtist,
                modifier = Modifier.padding(top = 10.dp),
            )
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
            RelatedRow(
                card = card,
                onOpen = { onOpen(card) },
            )
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
        Box(
            Modifier
                .fillMaxSize()
                .then(if (liquid) Modifier.layerBackdrop(backdrop) else Modifier),
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
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(topInset + 52.dp)
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
                    seeking = seeking,
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
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                AnimatedVisibility(
                    visible = chrome,
                    enter = MvChromeBottomIn,
                    exit = MvChromeBottomOut,
                ) {
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
                }
                MvScrubber(
                    positionMs = pos,
                    durationMs = duration,
                    onSeekStart = onSeekStart,
                    onSeek = onSeek,
                    onSeekEnd = onSeekEnd,
                    showThumb = chrome || seeking,
                )
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
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MvPlainIcon(onClick = onBack) {
            Icon(
                imageVector = ZIcons.Back,
                contentDescription = "返回",
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
                contentDescription = if (playing) "暂停" else "播放",
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
            text = if (speed == 1f) "倍速" else "${trimSpeed(speed)}x",
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
                .background(Color(0xFFEDEDED)),
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
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
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
    seeking: Boolean,
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
                contentDescription = if (ui.playWhenReady) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
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
            text = if (ui.speed == 1f) "倍速" else "${trimSpeed(ui.speed)}x",
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
private fun MvRelatedToggle(
    open: Boolean,
    liquid: Boolean,
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
) {
    MvChromeIcon(
        onClick = onClick,
        liquid = liquid,
        backdrop = backdrop,
        size = 28.dp,
        small = true,
    ) {
        Icon(
            imageVector = if (open) ZIcons.Close else ZIcons.RelatedMv,
            contentDescription = if (open) "收起相关 MV" else "相关 MV",
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
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
            contentDescription = if (exit) "退出全屏" else "全屏",
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
        val longMs = viewConfiguration.longPressTimeoutMillis.toLong()
        val doubleMs = viewConfiguration.doubleTapTimeoutMillis.toLong()
        awaitEachGesture {
            val down = awaitFirstDown()
            val width = size.width.toFloat().coerceAtLeast(1f)
            val fraction = down.position.x / width
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
