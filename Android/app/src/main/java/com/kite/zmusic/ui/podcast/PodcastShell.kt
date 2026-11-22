package com.kite.zmusic.ui.podcast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.AppMode
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmPodcastParse
import com.kite.zmusic.data.PodcastEpisode
import com.kite.zmusic.data.PodcastFeed
import com.kite.zmusic.data.PodcastSessionCache
import com.kite.zmusic.data.PodcastShow
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.platform.MusicPlatform
import com.kite.zmusic.data.toTrackRow
import com.kite.zmusic.i18n.t
import com.kite.zmusic.playback.PlaybackViewModel
import com.kite.zmusic.ui.community.HomeCommunityScanButton
import com.kite.zmusic.ui.community.rememberCommunityLoginOpener
import com.kite.zmusic.ui.home.HomeRotationControl
import com.kite.zmusic.ui.home.LocalHomeStageGate
import com.kite.zmusic.ui.home.LocalHomeStagePagerBlock
import com.kite.zmusic.ui.home.homeStageHorizontalContent
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainLightSystemBars
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.settings.SettingsAudience
import com.kite.zmusic.ui.settings.SettingsScreen
import com.kite.zmusic.ui.theme.MainPalette
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign
import androidx.compose.material3.Text

sealed interface PodcastRoute {
    data class Show(val id: Long, val title: String) : PodcastRoute
    data object Search : PodcastRoute
    data class Category(val id: Long, val name: String) : PodcastRoute
    data class Chart(val kind: PodcastChart) : PodcastRoute
    data object Recent : PodcastRoute
    data object Created : PodcastRoute
    data class Comments(val programId: Long, val title: String) : PodcastRoute
    data object Settings : PodcastRoute
}

private enum class PodcastTab(
    val label: String,
    val icon: ImageVector,
) {
    Discover("发现", ZIcons.Home),
    Category("分类", ZIcons.Radio),
    Library("订阅", ZIcons.Profile),
}

private data class PodcastFrame(val token: Int, val route: PodcastRoute)

private data class PendingPodcastPlay(
    val tracks: List<com.kite.zmusic.data.TrackRow>,
    val index: Int,
    val podcastId: Long?,
    val title: String?,
)

@Composable
fun PodcastShell(
    sessionRepository: SessionRepository,
    playback: PlaybackViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val platform by app.musicPlatformStore.currentFlow.collectAsStateWithLifecycle()
    val playbackUi by playback.ui.collectAsStateWithLifecycle()
    val memoryRev by app.podcastPlayMemory.revision.collectAsStateWithLifecycle()
    val backdrop = rememberLayerBackdrop()
    val cache = app.podcastSessionCache
    val communityQr = rememberCommunityLoginOpener()
    var feed by remember { mutableStateOf(cache.homeFeed() ?: PodcastFeed()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stack by remember { mutableStateOf(listOf<PodcastFrame>()) }
    var nextToken by remember { mutableIntStateOf(1) }
    val pagerState = rememberPagerState(pageCount = { PodcastTab.entries.size })
    var playerOpen by remember { mutableStateOf(false) }
    var bubble by remember { mutableStateOf(app.podcastPlayMemory.bubble()) }
    var pendingPlay by remember { mutableStateOf<PendingPodcastPlay?>(null) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun cookie(): String = sessionRepository.session.value?.cookie.orEmpty()

    fun push(route: PodcastRoute) {
        if (stack.lastOrNull()?.route == route) return
        val token = nextToken
        nextToken = token + 1
        stack = stack + PodcastFrame(token, route)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack = stack.dropLast(1)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingPlay
        pendingPlay = null
        if (pending != null) {
            playback.playQueue(pending.tracks, pending.index, pending.podcastId, pending.title)
            playerOpen = true
            if (!granted) {
                context.showIslandNotice(t("未开启通知时，系统可能在息屏后限制后台播放"))
            }
        }
    }

    fun playEpisodes(episodes: List<PodcastEpisode>, index: Int) {
        val playable = episodes.filter { it.trackId > 0L }
        if (playable.isEmpty()) {
            context.showIslandNotice(t("这一期暂时无法播放"))
            return
        }
        val wanted = episodes.getOrNull(index)?.programId
        val start = playable.indexOfFirst { it.programId == wanted }.let { found ->
            if (found >= 0) found else index.coerceIn(0, playable.lastIndex)
        }
        app.podcastPlayMemory.remember(playable)
        val tracks = playable.map { it.toTrackRow() }
        val head = playable[start]
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingPlay = PendingPodcastPlay(
                tracks,
                start,
                head.podcastId.takeIf { it > 0L },
                head.podcastName.ifBlank { null },
            )
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        playback.playQueue(
            tracks,
            start,
            head.podcastId.takeIf { it > 0L },
            head.podcastName.ifBlank { null },
        )
        playerOpen = true
    }

    fun playShow(show: PodcastShow) {
        scope.launch {
            val json = app.podcastClient.djProgram(cookie(), show.id, limit = 40)
            val episodes = NcmPodcastParse.episodes(json)
            if (episodes.isEmpty()) {
                context.showIslandNotice(t("这一期暂时无法播放"))
            } else {
                playEpisodes(episodes, 0)
            }
        }
    }

    fun reload(force: Boolean = false) {
        if (!force && platform == MusicPlatform.NETEASE) {
            cache.homeFeed()?.let { cached ->
                feed = cached
                loading = false
                error = null
                return
            }
        }
        scope.launch {
            loading = true
            error = null
            if (platform != MusicPlatform.NETEASE) {
                feed = PodcastFeed()
                error = t("播客使用网易云音乐服务器")
                loading = false
                return@launch
            }
            val loaded = runCatching {
                coroutineScope {
                    val cookie = cookie()
                    val banner = async { app.podcastClient.djBanner(cookie) }
                    val today = async { app.podcastClient.djToday(cookie) }
                    val personal = async { app.podcastClient.djPersonalize(cookie, 16) }
                    val recommend = async { app.podcastClient.djRecommend(cookie) }
                    val hot = async { app.podcastClient.djHot(cookie, 36) }
                    val newest = async { app.podcastClient.djToplist(cookie, "new", 30) }
                    val popular = async { app.podcastClient.djToplistPopular(cookie, 30) }
                    val newcomers = async { app.podcastClient.djToplistNewcomer(cookie, 30) }
                    val hours = async { app.podcastClient.djToplistHours(cookie, 30) }
                    val pay = async { app.podcastClient.djToplistPay(cookie, 30) }
                    val gift = async { app.podcastClient.djPaygift(cookie, 24) }
                    val programs = async { app.podcastClient.djProgramToplist(cookie, 30) }
                    val programHours = async { app.podcastClient.djProgramToplistHours(cookie, 30) }
                    val cats = async { app.podcastClient.djCatelist(cookie) }
                    val subs = async { app.podcastClient.djSublist(cookie, 50) }
                    val recentShows = async { app.podcastClient.recentDj(cookie) }
                    val recentVoices = async { app.podcastClient.recentVoice(cookie) }
                    val account = async { app.podcastClient.userAccount(cookie) }
                    val uid = NcmPodcastParse.userId(account.await())
                    val createdJson = if (uid > 0L) app.podcastClient.userAudio(cookie, uid) else null
                    val todayJson = today.await()
                    val personalShows = NcmPodcastParse.shows(personal.await())
                    PodcastFeed(
                        banners = NcmPodcastParse.banners(banner.await()),
                        today = NcmPodcastParse.shows(todayJson).firstOrNull() ?: NcmPodcastParse.show(todayJson),
                        recommends = personalShows.ifEmpty { NcmPodcastParse.shows(recommend.await()) },
                        hot = NcmPodcastParse.shows(hot.await()),
                        newest = NcmPodcastParse.shows(newest.await()),
                        popular = NcmPodcastParse.shows(popular.await()),
                        newcomers = NcmPodcastParse.shows(newcomers.await()),
                        anchorHours = NcmPodcastParse.shows(hours.await()),
                        pay = NcmPodcastParse.shows(pay.await()),
                        payGift = NcmPodcastParse.shows(gift.await()),
                        programChart = NcmPodcastParse.episodes(programs.await()),
                        programHours = NcmPodcastParse.episodes(programHours.await()),
                        categories = NcmPodcastParse.categories(cats.await()),
                        subscribed = NcmPodcastParse.shows(subs.await()),
                        recentShows = NcmPodcastParse.shows(recentShows.await()),
                        recentEpisodes = NcmPodcastParse.episodes(recentVoices.await()),
                        created = createdJson?.let { NcmPodcastParse.shows(it) }.orEmpty(),
                    )
                }
            }
            loaded.onSuccess { next ->
                feed = next
                val empty = next.banners.isEmpty() && next.today == null &&
                    next.recommends.isEmpty() && next.hot.isEmpty() &&
                    next.categories.isEmpty() && next.subscribed.isEmpty() &&
                    next.newest.isEmpty() && next.programChart.isEmpty()
                if (empty) {
                    error = t("播客暂时没有内容")
                } else {
                    cache.saveHomeFeed(next)
                }
            }.onFailure { error = NcmJson.userFacingThrowable(it, "播客加载失败") }
            loading = false
        }
    }

    LaunchedEffect(platform) { reload() }
    LaunchedEffect(Unit) { app.mvPlayback.stop() }
    LaunchedEffect(playbackUi.currentTrack?.id, playbackUi.playWhenReady) {
        if (app.appModeStore.current() != AppMode.Podcast) return@LaunchedEffect
        val id = playbackUi.currentTrack?.id ?: return@LaunchedEffect
        if (app.podcastPlayMemory.contains(id)) return@LaunchedEffect
        if (playbackUi.playWhenReady) playback.togglePlayPause()
    }
    val pendingOpen by playback.pendingOpenPlayer.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen, playbackUi.currentTrack?.id) {
        if (!pendingOpen) return@LaunchedEffect
        if (app.appModeStore.current() != AppMode.Podcast) return@LaunchedEffect
        val id = playback.ui.value.currentTrack?.id ?: return@LaunchedEffect
        if (!app.podcastPlayMemory.contains(id)) return@LaunchedEffect
        playback.consumeOpenPlayerRequest()
        playerOpen = true
    }

    val episode = remember(playbackUi.currentTrack?.id, memoryRev) {
        playbackUi.currentTrack?.id?.let { app.podcastPlayMemory.episode(it) }
    }
    val likedEpisodes = remember(memoryRev) { app.podcastPlayMemory.likedEpisodes() }
    BackHandler(enabled = playerOpen || stack.isNotEmpty()) {
        val commentsOnTop = stack.lastOrNull()?.route is PodcastRoute.Comments
        when {
            commentsOnTop -> pop()
            playerOpen -> playerOpen = false
            stack.isNotEmpty() -> pop()
        }
    }
    val stageGate = LocalHomeStageGate.current
    val stageBlocked = playerOpen || stack.isNotEmpty()
    val navPage = pagerState.currentPage
    val navOffset = pagerState.currentPageOffsetFraction
    val navCanGoBack = stageBlocked || navPage > 0 || abs(navOffset) >= 0.02f
    val subscriptionRev = cache.subscriptionRevision
    val shownFeed = if (subscriptionRev == 0) feed else cache.withSubscriptions(feed)
    SideEffect {
        val gate = stageGate ?: return@SideEffect
        gate.floorLocked = stageBlocked
        gate.navCanScrollBackward = navCanGoBack
    }
    LaunchedEffect(stageBlocked) {
        if (stageBlocked) stageGate?.requestClose()
    }
    MainLightSystemBars()
            Box(
                modifier
                    .fillMaxSize()
                    .background(MainPalette.Page)
                    .layerBackdrop(backdrop),
            ) {
                if (landscape) {
                    Row(Modifier.fillMaxSize()) {
                        PodcastSideRail(
                            pagerState = pagerState,
                            onSelect = { index ->
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .navigationBarsPadding(),
                        ) {
                            PodcastMasthead(
                                landscape = true,
                                onScan = communityQr.openScanner,
                                onSettings = { push(PodcastRoute.Settings) },
                            )
                            PodcastTabPager(
                                pagerState = pagerState,
                                feed = shownFeed,
                                loading = loading,
                                error = error,
                                onRefresh = { reload(force = true) },
                                onOpenSearch = { push(PodcastRoute.Search) },
                                onOpenShow = { push(PodcastRoute.Show(it.id, it.name)) },
                                onOpenCategory = { push(PodcastRoute.Category(it.id, it.name)) },
                                onOpenChart = { push(PodcastRoute.Chart(it)) },
                                onPlayShow = ::playShow,
                                onPlayEpisodes = ::playEpisodes,
                                liked = likedEpisodes,
                                pageScrollEnabled = !stageBlocked,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                    }
                } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    PodcastMasthead(
                        landscape = false,
                        onScan = communityQr.openScanner,
                        onSettings = { push(PodcastRoute.Settings) },
                    )
                    PodcastTabPager(
                        pagerState = pagerState,
                        feed = shownFeed,
                        loading = loading,
                        error = error,
                        onRefresh = { reload(force = true) },
                        onOpenSearch = { push(PodcastRoute.Search) },
                        onOpenShow = { push(PodcastRoute.Show(it.id, it.name)) },
                        onOpenCategory = { push(PodcastRoute.Category(it.id, it.name)) },
                        onOpenChart = { push(PodcastRoute.Chart(it)) },
                        onPlayShow = ::playShow,
                        onPlayEpisodes = ::playEpisodes,
                        liked = likedEpisodes,
                        pageScrollEnabled = !stageBlocked,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    PodcastBottomNav(pagerState = pagerState)
                }
                }
                val commentFrame = stack.lastOrNull()?.takeIf { it.route is PodcastRoute.Comments }
                val pageStack = if (commentFrame != null) stack.dropLast(1) else stack
                var commentHold by remember { mutableStateOf<PodcastFrame?>(null) }
                SideEffect {
                    if (commentFrame != null) commentHold = commentFrame
                }
                val shownComment = commentFrame ?: commentHold
                PodcastRouteHost(
                    stack = pageStack,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(14f),
                ) { page ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MainPalette.Page)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .statusBarsPadding(),
                    ) {
                        PodcastRouteBody(
                            route = page,
                            client = app.podcastClient,
                            cookie = cookie(),
                            cache = cache,
                            feed = shownFeed,
                            navBottom = navBottom,
                            onBack = ::pop,
                            onLogout = onLogout,
                            onOpenShow = { push(PodcastRoute.Show(it.id, it.name)) },
                            onPlay = ::playEpisodes,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = !playerOpen && episode != null,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.82f),
                    exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.82f),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(22f),
                ) {
                    PodcastBubble(
                        coverUrl = episode?.coverUrl,
                        playing = playbackUi.playWhenReady && !playbackUi.buffering,
                        progress = run {
                            val duration = playbackUi.durationMs.takeIf { it > 0L }
                                ?: episode?.durationMs
                                ?: 0L
                            if (duration <= 0L) 0f
                            else playbackUi.positionMs.toFloat() / duration.toFloat()
                        },
                        originX = bubble.first,
                        originY = bubble.second,
                        onMove = { x, y ->
                            bubble = x to y
                            app.podcastPlayMemory.saveBubble(x, y)
                        },
                        onOpen = { playerOpen = true },
                    )
                }
                AnimatedVisibility(
                    visible = playerOpen,
                    enter = fadeIn(tween(220)) +
                        slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 6 } +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(320)),
                    exit = fadeOut(tween(180)) +
                        slideOutVertically(tween(240)) { it / 8 } +
                        scaleOut(targetScale = 0.96f),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(30f),
                ) {
                    PodcastPlayerScreen(
                        ui = playbackUi,
                        episode = episode,
                        onClose = { playerOpen = false },
                        onToggle = { playback.togglePlayPause() },
                        onSeek = { playback.seekTo(it) },
                        onPrev = { playback.skipPrevious() },
                        onNext = { playback.skipNext() },
                        onLike = {
                            val current = episode
                            if (current == null || current.trackId <= 0L) {
                                context.showIslandNotice(t("这一期暂时无法喜欢"))
                                return@PodcastPlayerScreen
                            }
                            scope.launch {
                                val next = !current.liked
                                app.podcastPlayMemory.updateLiked(current.trackId, next)
                                var json = app.podcastClient.resourceLike(
                                    cookie(),
                                    current.programId,
                                    next,
                                    type = 4,
                                )
                                if (!NcmPodcastParse.ok(json) && current.trackId > 0L) {
                                    json = app.podcastClient.resourceLike(
                                        cookie(),
                                        current.trackId,
                                        next,
                                        type = 0,
                                    )
                                }
                                if (NcmPodcastParse.ok(json)) {
                                    context.showIslandNotice(if (next) t("已喜欢") else t("已取消喜欢"))
                                } else {
                                    app.podcastPlayMemory.updateLiked(current.trackId, !next)
                                    context.showIslandNotice(t("操作失败"))
                                }
                            }
                        },
                        onComments = {
                            val current = episode ?: return@PodcastPlayerScreen
                            push(PodcastRoute.Comments(current.programId, current.name))
                        },
                        onOpenShow = {
                            val current = episode ?: return@PodcastPlayerScreen
                            val id = current.podcastId
                            if (id <= 0L) return@PodcastPlayerScreen
                            playerOpen = false
                            push(PodcastRoute.Show(id, current.podcastName.ifBlank { t("播客") }))
                        },
                        modifier = Modifier.zIndex(30f),
                    )
                }
                AnimatedVisibility(
                    visible = commentFrame != null,
                    enter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } +
                        fadeIn(tween(220)),
                    exit = slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } +
                        fadeOut(tween(180)),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(40f),
                ) {
                    val frame = shownComment
                    if (frame != null) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MainPalette.Page)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                )
                                .statusBarsPadding(),
                        ) {
                            PodcastRouteBody(
                                route = frame.route,
                                client = app.podcastClient,
                                cookie = cookie(),
                                cache = cache,
                                feed = shownFeed,
                                navBottom = navBottom,
                                onBack = ::pop,
                                onLogout = onLogout,
                                onOpenShow = { push(PodcastRoute.Show(it.id, it.name)) },
                                onPlay = ::playEpisodes,
                            )
                        }
                    }
                }
            }
}

@Composable
private fun PodcastTabPager(
    pagerState: PagerState,
    feed: PodcastFeed,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenShow: (PodcastShow) -> Unit,
    onOpenCategory: (com.kite.zmusic.data.PodcastCategory) -> Unit,
    onOpenChart: (PodcastChart) -> Unit,
    onPlayShow: (PodcastShow) -> Unit,
    onPlayEpisodes: (List<PodcastEpisode>, Int) -> Unit,
    liked: List<PodcastEpisode>,
    pageScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val stageBlock = LocalHomeStagePagerBlock.current
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 1,
        userScrollEnabled = pageScrollEnabled && stageBlock?.blocked != true,
    ) { page ->
        when (PodcastTab.entries[page]) {
            PodcastTab.Discover -> PodcastHome(
                feed = feed,
                loading = loading,
                error = error,
                onRefresh = onRefresh,
                onOpenSearch = onOpenSearch,
                onOpenShow = onOpenShow,
                onOpenChart = onOpenChart,
                onPlayShow = onPlayShow,
                onPlayEpisodes = onPlayEpisodes,
            )
            PodcastTab.Category -> PodcastCategoryBoard(
                categories = feed.categories,
                onOpen = onOpenCategory,
            )
            PodcastTab.Library -> PodcastLibraryBoard(
                feed = feed,
                liked = liked,
                onOpenShow = onOpenShow,
                onPlay = onPlayEpisodes,
            )
        }
    }
}

private const val PodcastNavCommitFraction = 0.22f
private const val PodcastNavFlingPerSec = 3.2f

@Composable
private fun PodcastBottomNav(
    pagerState: PagerState,
) {
    val tabs = PodcastTab.entries
    val scope = rememberCoroutineScope()
    val settle = remember { PodcastNavSettle() }
    Column(
        Modifier
            .navigationBarsPadding()
            .homeStageHorizontalContent(),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            val cell = maxWidth / tabs.size
            val selection = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (tabs.lastIndex).toFloat())
            Box(
                Modifier
                    .offset(x = cell * selection)
                    .width(cell)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
                    .clip(CircleShape)
                    .background(MainPalette.Accent.copy(alpha = 0.16f)),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, item ->
                    val on = abs(selection - index) < 0.5f
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = t(item.label),
                            tint = if (on) MainPalette.Accent else MainPalette.Hint,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = t(item.label),
                            color = if (on) MainPalette.Accent else MainPalette.Hint,
                            fontSize = 11.sp,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(pagerState) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            settle.job?.cancel()
                            val startPage = pagerState.currentPage
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                            }
                            val tabW = size.width.toFloat() / tabs.size
                            if (tabW <= 0f) return@awaitEachGesture
                            if (slop == null) {
                                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                if (up != null && up.changedToUpIgnoreConsumed()) {
                                    val travel = (up.position - down.position).getDistance()
                                    if (travel < viewConfiguration.touchSlop * 2) {
                                        val index = (down.position.x / tabW)
                                            .toInt()
                                            .coerceIn(0, tabs.lastIndex)
                                        settle.job = scope.launch {
                                            pagerState.animateScrollToPage(
                                                index,
                                                animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
                                            )
                                        }
                                    }
                                }
                                return@awaitEachGesture
                            }
                            val stride = pagerStride(pagerState)
                            val slopDx = slop.positionChange().x
                            if (slopDx != 0f && stride > 0f) {
                                pagerState.dispatchRawDelta(slopDx / tabW * stride)
                            }
                            horizontalDrag(slop.id) { change ->
                                val dx = change.positionChange().x
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val pageStride = pagerStride(pagerState)
                                if (dx != 0f && pageStride > 0f) {
                                    pagerState.dispatchRawDelta(dx / tabW * pageStride)
                                }
                            }
                            settle.job = scope.launch {
                                settlePodcastNav(
                                    pagerState,
                                    tracker.calculateVelocity().x / tabW,
                                    startPage,
                                )
                            }
                        }
                    },
            )
        }
    }
}

@Composable
private fun PodcastSideRail(
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    val tabs = PodcastTab.entries
    val selection = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, tabs.lastIndex.toFloat())
    Column(
        Modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(MainPalette.Surface.copy(alpha = 0.72f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tabs.forEachIndexed { index, item ->
            val on = abs(selection - index) < 0.5f
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) MainPalette.Accent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = t(item.label),
                    tint = if (on) MainPalette.Accent else MainPalette.Hint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = t(item.label),
                    color = if (on) MainPalette.Accent else MainPalette.Hint,
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

private class PodcastNavSettle {
    var job: Job? = null
}

private fun pagerStride(pagerState: PagerState): Float {
    val info = pagerState.layoutInfo
    return (info.pageSize + info.pageSpacing).toFloat()
}

private suspend fun settlePodcastNav(
    pagerState: PagerState,
    velocityTabsPerSec: Float,
    startPage: Int,
) {
    val last = pagerState.pageCount - 1
    if (last < 0) return
    val origin = startPage.coerceIn(0, last)
    val pos = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, last.toFloat())
    val travel = pos - origin
    val direction = when {
        abs(travel) > 0.001f -> sign(travel).toInt()
        abs(velocityTabsPerSec) > 0.001f -> sign(velocityTabsPerSec).toInt()
        else -> 0
    }
    val distanceSteps = if (abs(travel) < PodcastNavCommitFraction) {
        0
    } else {
        floor(abs(travel) + (1f - PodcastNavCommitFraction)).toInt()
    }
    val flingStep = if (distanceSteps == 0 && abs(velocityTabsPerSec) >= PodcastNavFlingPerSec) 1 else 0
    val steps = (distanceSteps + flingStep).coerceAtLeast(0)
    val target = if (direction == 0 || steps == 0) {
        origin
    } else {
        (origin + direction * steps).coerceIn(0, last)
    }
    if (target == pagerState.currentPage && abs(pagerState.currentPageOffsetFraction) < 0.002f) {
        return
    }
    pagerState.animateScrollToPage(
        target,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
    )
}

@Composable
private fun PodcastMasthead(
    landscape: Boolean,
    onScan: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_vinyl_z),
            contentDescription = null,
            modifier = Modifier
                .size(if (landscape) 30.dp else 34.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "ZMusic",
                color = MainPalette.Ink,
                fontSize = if (landscape) 20.sp else 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = t("播客模式"),
                color = MainPalette.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        HomeRotationControl(landscape = landscape)
        HomeCommunityScanButton(onClick = onScan)
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSettings,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.Settings,
                contentDescription = t("设置"),
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PodcastRouteHost(
    stack: List<PodcastFrame>,
    modifier: Modifier = Modifier,
    page: @Composable (PodcastRoute) -> Unit,
) {
    var held by remember { mutableStateOf<List<PodcastFrame>>(emptyList()) }
    val pushing = stack.size >= held.size
    LaunchedEffect(stack) {
        if (stack.size >= held.size) {
            held = stack
        } else {
            delay(340)
            held = stack
        }
    }
    val render = if (pushing) stack else held
    if (render.isEmpty()) return
    val slide = tween<androidx.compose.ui.unit.IntOffset>(320, easing = FastOutSlowInEasing)
    val fade = tween<Float>(220)
    Box(modifier) {
        render.forEachIndexed { index, frame ->
            key(frame.token) {
                val visible = remember { MutableTransitionState(false) }
                visible.targetState = stack.any { it.token == frame.token }
                AnimatedVisibility(
                    visibleState = visible,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(index.toFloat()),
                    enter = slideInHorizontally(slide) { it } + fadeIn(fade),
                    exit = slideOutHorizontally(slide) { it } + fadeOut(fade),
                ) {
                    page(frame.route)
                }
            }
        }
    }
}

@Composable
private fun PodcastRouteBody(
    route: PodcastRoute,
    client: com.kite.zmusic.data.NcmPodcastClient,
    cookie: String,
    cache: PodcastSessionCache,
    feed: PodcastFeed,
    navBottom: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenShow: (PodcastShow) -> Unit,
    onPlay: (List<PodcastEpisode>, Int) -> Unit,
) {
    when (route) {
        is PodcastRoute.Show -> PodcastShowPage(
            client = client,
            cookie = cookie,
            showId = route.id,
            title = route.title,
            cache = cache,
            onBack = onBack,
            onPlay = onPlay,
        )
        PodcastRoute.Search -> PodcastSearchPage(
            client = client,
            cookie = cookie,
            cache = cache,
            onBack = onBack,
            onOpenShow = onOpenShow,
        )
        is PodcastRoute.Category -> PodcastRemoteList(
            cacheKey = "cat:${route.id}",
            cache = cache,
            title = route.name,
            load = { offset ->
                val json = client.djRecommendType(cookie, route.id, offset = offset)
                NcmPodcastParse.shows(json) to emptyList()
            },
            more = { _, page -> page.size >= 30 },
            onBack = onBack,
            onOpenShow = onOpenShow,
            onPlay = onPlay,
        )
        is PodcastRoute.Chart -> {
            val slice = podcastChartSlice(route.kind, feed)
            PodcastRemoteList(
            cacheKey = "chart2:${route.kind.name}",
            cache = cache,
            title = slice.title,
            load = { _ ->
                val remote = loadPodcastChart(client, cookie, route.kind)
                val shows = remote.first.ifEmpty { slice.shows }
                val episodes = remote.second.ifEmpty { slice.episodes }
                shows to episodes
            },
            more = { _, _ -> false },
            onBack = onBack,
            onOpenShow = onOpenShow,
            onPlay = onPlay,
            )
        }
        PodcastRoute.Recent -> PodcastRemoteList(
            cacheKey = "recent",
            cache = cache,
            title = t("最近播放"),
            load = { _ ->
                val radios = client.recentDj(cookie)
                val voices = client.recentVoice(cookie)
                val shows = NcmPodcastParse.shows(radios)
                val episodes = NcmPodcastParse.episodes(radios) + NcmPodcastParse.episodes(voices)
                shows to episodes.distinctBy { it.programId }
            },
            more = { _, _ -> false },
            onBack = onBack,
            onOpenShow = onOpenShow,
            onPlay = onPlay,
        )
        PodcastRoute.Created -> PodcastRemoteList(
            cacheKey = "created",
            cache = cache,
            title = t("我创建的"),
            load = { _ ->
                val uid = NcmPodcastParse.userId(client.userAccount(cookie))
                if (uid <= 0L) {
                    emptyList<PodcastShow>() to emptyList()
                } else {
                    NcmPodcastParse.shows(client.userAudio(cookie, uid)) to emptyList()
                }
            },
            more = { _, _ -> false },
            onBack = onBack,
            onOpenShow = onOpenShow,
            onPlay = onPlay,
        )
        is PodcastRoute.Comments -> PodcastCommentsPage(
            client = client,
            cookie = cookie,
            programId = route.programId,
            title = route.title,
            cache = cache,
            onBack = onBack,
        )
        PodcastRoute.Settings -> SettingsScreen(
            contentBottomInset = navBottom,
            onBack = onBack,
            onLogout = onLogout,
            audience = SettingsAudience.Podcast,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun podcastChartSlice(kind: PodcastChart, feed: PodcastFeed): PodcastChartSlice {
    return when (kind) {
        PodcastChart.Recommend -> PodcastChartSlice(t("推荐"), feed.recommends, emptyList())
        PodcastChart.Hot -> PodcastChartSlice(t("热门电台"), feed.hot, emptyList())
        PodcastChart.New -> PodcastChartSlice(t("新晋电台"), feed.newest, emptyList())
        PodcastChart.Popular -> PodcastChartSlice(t("最热主播"), feed.popular, emptyList())
        PodcastChart.Newcomers -> PodcastChartSlice(t("主播新人榜"), feed.newcomers, emptyList())
        PodcastChart.AnchorHours -> PodcastChartSlice(t("24 小时主播"), feed.anchorHours, emptyList())
        PodcastChart.Pay -> PodcastChartSlice(t("付费精品"), feed.pay, emptyList())
        PodcastChart.PayGift -> PodcastChartSlice(t("付费精选"), feed.payGift, emptyList())
        PodcastChart.Programs -> PodcastChartSlice(t("节目榜"), emptyList(), feed.programChart)
        PodcastChart.ProgramHours -> PodcastChartSlice(t("24 小时节目"), emptyList(), feed.programHours)
    }
}

private suspend fun loadPodcastChart(
    client: com.kite.zmusic.data.NcmPodcastClient,
    cookie: String,
    kind: PodcastChart,
): Pair<List<PodcastShow>, List<PodcastEpisode>> {
    return when (kind) {
        PodcastChart.Recommend -> NcmPodcastParse.shows(client.djRecommend(cookie)) to emptyList()
        PodcastChart.Hot -> {
            val chart = NcmPodcastParse.shows(client.djToplist(cookie, "hot", 80))
            val shows = chart.ifEmpty { NcmPodcastParse.shows(client.djHot(cookie, 80)) }
            shows to emptyList()
        }
        PodcastChart.New -> NcmPodcastParse.shows(client.djToplist(cookie, "new", 50)) to emptyList()
        PodcastChart.Popular -> NcmPodcastParse.shows(client.djToplistPopular(cookie, 50)) to emptyList()
        PodcastChart.Newcomers -> NcmPodcastParse.shows(client.djToplistNewcomer(cookie, 50)) to emptyList()
        PodcastChart.AnchorHours -> NcmPodcastParse.shows(client.djToplistHours(cookie, 50)) to emptyList()
        PodcastChart.Pay -> NcmPodcastParse.shows(client.djToplistPay(cookie, 50)) to emptyList()
        PodcastChart.PayGift -> NcmPodcastParse.shows(client.djPaygift(cookie, 40)) to emptyList()
        PodcastChart.Programs -> emptyList<PodcastShow>() to NcmPodcastParse.episodes(client.djProgramToplist(cookie, 50))
        PodcastChart.ProgramHours ->
            emptyList<PodcastShow>() to NcmPodcastParse.episodes(client.djProgramToplistHours(cookie, 50))
    }
}

private data class PodcastChartSlice(
    val title: String,
    val shows: List<PodcastShow>,
    val episodes: List<PodcastEpisode>,
)

@Composable
private fun PodcastRemoteList(
    cacheKey: String,
    cache: PodcastSessionCache,
    title: String,
    load: suspend (offset: Int) -> Pair<List<PodcastShow>, List<PodcastEpisode>>,
    more: suspend (offset: Int, pageShows: List<PodcastShow>) -> Boolean,
    onBack: () -> Unit,
    onOpenShow: (PodcastShow) -> Unit,
    onPlay: (List<PodcastEpisode>, Int) -> Unit,
) {
    val seeded = cache.remote(cacheKey)
    var shows by remember(cacheKey) { mutableStateOf(seeded?.shows ?: emptyList()) }
    var episodes by remember(cacheKey) { mutableStateOf(seeded?.episodes ?: emptyList()) }
    var loading by remember(cacheKey) { mutableStateOf(seeded == null) }
    var loadingMore by remember(cacheKey) { mutableStateOf(false) }
    var error by remember(cacheKey) { mutableStateOf<String?>(null) }
    var hasMore by remember(cacheKey) { mutableStateOf(seeded?.hasMore == true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(cacheKey) {
        if (cache.remote(cacheKey) != null) {
            loading = false
            return@LaunchedEffect
        }
        loading = shows.isEmpty() && episodes.isEmpty()
        runCatching { load(0) }
            .onSuccess { (nextShows, nextEpisodes) ->
                shows = nextShows
                episodes = nextEpisodes
                hasMore = if (nextShows.isNotEmpty()) more(0, nextShows) else nextEpisodes.size >= 30 && more(0, nextShows)
                cache.saveRemote(cacheKey, shows, episodes, hasMore)
                error = null
            }
            .onFailure { error = NcmJson.userFacingThrowable(it, "播客加载失败") }
        loading = false
    }
    PodcastCatalogPage(
        title = title,
        shows = shows,
        episodes = episodes,
        loading = loading,
        error = error,
        onBack = onBack,
        onOpenShow = onOpenShow,
        onPlay = onPlay,
        onLoadMore = if (hasMore && !loadingMore) {
            {
                if (loadingMore || !hasMore) return@PodcastCatalogPage
                loadingMore = true
                scope.launch {
                    val offset = if (shows.isNotEmpty()) shows.size else episodes.size
                    runCatching { load(offset) }.onSuccess { (nextShows, nextEpisodes) ->
                        val freshShows = nextShows.filter { show -> shows.none { it.id == show.id } }
                        val freshEpisodes = nextEpisodes.filter { episode ->
                            episodes.none { it.programId == episode.programId }
                        }
                        shows = shows + freshShows
                        episodes = episodes + freshEpisodes
                        hasMore = when {
                            freshShows.isEmpty() && freshEpisodes.isEmpty() -> false
                            nextShows.isNotEmpty() -> more(offset, freshShows)
                            else -> freshEpisodes.size >= 30
                        }
                        cache.saveRemote(cacheKey, shows, episodes, hasMore)
                    }
                    loadingMore = false
                }
            }
        } else {
            null
        },
    )
}
