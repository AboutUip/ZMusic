package com.kite.zmusic.ui.player

import androidx.compose.animation.AnimatedVisibility as AnimateVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.TrackExportOptions
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.SleepTimerUi
import com.kite.zmusic.ui.catalog.launchTrackDownload
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.common.predictiveBackLayer
import com.kite.zmusic.ui.common.rememberPredictiveBackUi
import com.kite.zmusic.ui.main.MainControls
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.pageSheetHazeStyle
import com.kite.zmusic.ui.notice.showIslandNotice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch

private val MorePanelShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
private val MoreRowShape = RoundedCornerShape(14.dp)
private val MoreCoverShape = RoundedCornerShape(8.dp)

private enum class MorePage { Root, AddToPlaylist, Download, SleepTimer, Translation, OutputDevice }

private val MoreDrillSlide = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)
private val MoreDrillFade = tween<Float>(durationMillis = 220)
private val MorePlaylistRowH = 64.dp
private val MoreNestedHeaderH = 72.dp
private val MoreSheetChromeH = 42.dp

/**
 * 竖屏「更多」：与音源同壳从下方滑入。
 * 二级页从右侧全覆盖滑入，不用弹窗。
 */
@Composable
fun PortraitMoreSheet(
    track: TrackRow,
    onOpenPoster: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    displayPrefs: PlayerDisplayPrefs,
    onDisplayPrefsChange: (PlayerDisplayPrefs) -> Unit,
    hazeState: HazeState? = null,
    excludePlaylistId: Long = 0L,
    visible: Boolean = true,
    maxHeight: Dp,
    onDragHandleVertical: (Float) -> Unit,
    onDragHandleEnd: () -> Unit,
    onCoverMinFrac: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(MorePage.Root) }
    var heldNested by remember { mutableStateOf(MorePage.AddToPlaylist) }
    val nestedVisible = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) {
        if (visible) page = MorePage.Root
    }
    if (page != MorePage.Root) heldNested = page
    nestedVisible.targetState = page != MorePage.Root
    val covering = nestedVisible.currentState || nestedVisible.targetState

    val app = LocalContext.current.applicationContext as ZMusicApplication
    val playlists by app.playlistCollectionRepository.playlists.collectAsStateWithLifecycle()
    val targets = remember(playlists, excludePlaylistId) {
        playlists
            .filter { it.isOwned && it.id != excludePlaylistId }
            .sortedWith(
                compareByDescending<PlaylistSummary> { it.isHeartPlaylist }
                    .thenBy { it.name },
            )
    }
    var addingId by remember { mutableStateOf<Long?>(null) }
    val sleepTimer by app.playbackBridge.sleepTimer.collectAsStateWithLifecycle()
    val audioOutput by app.audioOutputController.state.collectAsStateWithLifecycle()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dragHandleVertical by rememberUpdatedState(onDragHandleVertical)
    val dragHandleEnd by rememberUpdatedState(onDragHandleEnd)
    val coverMinFracUpdated by rememberUpdatedState(onCoverMinFrac)
    LaunchedEffect(covering, heldNested, targets.size, maxHeight, navInset) {
        if (covering) {
            coverMinFracUpdated(
                moreCoverMinFrac(
                    page = heldNested,
                    playlistCount = targets.size,
                    maxHeight = maxHeight,
                    navInset = navInset,
                ),
            )
        } else {
            coverMinFracUpdated(null)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .fillMaxSize()
            .clip(MorePanelShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        MoreSheetGlass(hazeState = hazeState)
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 14.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                dragHandleVertical(dragAmount)
                            },
                            onDragEnd = { dragHandleEnd() },
                            onDragCancel = { dragHandleEnd() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MainPalette.Hint),
                )
            }
            MorePageStack(
                nestedVisible = nestedVisible,
                heldNested = heldNested,
                track = track,
                targets = targets,
                addingId = addingId,
                sleepTimer = sleepTimer,
                audioOutputSubtitle = audioOutput.moreSubtitle,
                displayPrefs = displayPrefs,
                hazeState = hazeState,
                onOpenAddToPlaylist = { page = MorePage.AddToPlaylist },
                onOpenDownload = { page = MorePage.Download },
                onOpenSleepTimer = { page = MorePage.SleepTimer },
                onOpenTranslation = { page = MorePage.Translation },
                onOpenOutputDevice = { page = MorePage.OutputDevice },
                onOpenPoster = onOpenPoster,
                onOpenSettings = onOpenSettings,
                onDisplayPrefsChange = onDisplayPrefsChange,
                onBack = { page = MorePage.Root },
                onAddingId = { addingId = it },
                onClose = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

private fun moreCoverMinFrac(
    page: MorePage,
    playlistCount: Int,
    maxHeight: Dp,
    navInset: Dp,
): Float {
    val need = when (page) {
        MorePage.AddToPlaylist -> {
            val list = if (playlistCount <= 0) {
                80.dp
            } else {
                MorePlaylistRowH * playlistCount + 8.dp
            }
            MoreSheetChromeH + navInset + MoreNestedHeaderH + list
        }
        MorePage.Download -> {
            MoreSheetChromeH + navInset + MoreNestedHeaderH + 72.dp + 56.dp * 6 + 52.dp
        }
        MorePage.SleepTimer -> maxHeight * (2f / 3f)
        MorePage.OutputDevice -> {
            MoreSheetChromeH + navInset + MoreNestedHeaderH + 72.dp + 56.dp * 6
        }
        MorePage.Translation -> {
            MoreSheetChromeH + navInset + MoreNestedHeaderH + 72.dp + 56.dp * 4 + 28.dp
        }
        MorePage.Root -> maxHeight / 3f
    }
    return (need / maxHeight).coerceIn(1f / 3f, 1f)
}

@Composable
private fun MorePageStack(
    nestedVisible: MutableTransitionState<Boolean>,
    heldNested: MorePage,
    track: TrackRow,
    targets: List<PlaylistSummary>,
    addingId: Long?,
    sleepTimer: SleepTimerUi,
    audioOutputSubtitle: String,
    displayPrefs: PlayerDisplayPrefs,
    hazeState: HazeState?,
    onOpenAddToPlaylist: () -> Unit,
    onOpenDownload: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenTranslation: () -> Unit,
    onOpenOutputDevice: () -> Unit,
    onOpenPoster: () -> Unit,
    onOpenSettings: () -> Unit,
    onDisplayPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onBack: () -> Unit,
    onAddingId: (Long?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clipToBounds()) {
        val covering = nestedVisible.currentState || nestedVisible.targetState
        val backUi = rememberPredictiveBackUi(enabled = covering) {
            onBack()
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = "更多",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.2).sp,
                ),
            )
            Spacer(Modifier.height(14.dp))
            MoreActionRow(
                icon = ZIcons.CollectPlaylist,
                title = "添加到歌单",
                subtitle = "放到自己创建的歌单里",
                onClick = onOpenAddToPlaylist,
            )
            Spacer(Modifier.height(8.dp))
            MoreActionRow(
                icon = ZIcons.GetApp,
                title = "下载",
                subtitle = "保存到 Download/ZMusic",
                onClick = onOpenDownload,
            )
            Spacer(Modifier.height(8.dp))
            MoreActionRow(
                icon = ZIcons.Timer,
                title = "定时停止",
                subtitle = sleepTimerRowSubtitle(sleepTimer),
                onClick = onOpenSleepTimer,
            )
            Spacer(Modifier.height(8.dp))
            MoreActionRow(
                icon = ZIcons.Translate,
                title = "翻译",
                subtitle = translationRowSubtitle(displayPrefs),
                onClick = onOpenTranslation,
            )
            Spacer(Modifier.height(8.dp))
            MoreActionRow(
                icon = ZIcons.Speaker,
                title = "输出设备",
                subtitle = audioOutputSubtitle,
                onClick = onOpenOutputDevice,
            )
            Spacer(Modifier.height(8.dp))
            MoreActionRow(
                icon = ZIcons.Wallpaper,
                title = "海报",
                subtitle = "选歌词做成分享图",
                onClick = onOpenPoster,
            )
            Spacer(Modifier.height(8.dp))
            MoreActionRow(
                icon = ZIcons.Settings,
                title = "播放器设置",
                subtitle = "背景、歌词与布局",
                onClick = onOpenSettings,
            )
        }
        AnimateVisibility(
            visibleState = nestedVisible,
            modifier = Modifier
                .matchParentSize()
                .zIndex(1f)
                .predictiveBackLayer(backUi),
            enter = slideInHorizontally(MoreDrillSlide) { it } + fadeIn(MoreDrillFade),
            exit = slideOutHorizontally(MoreDrillSlide) { it } + fadeOut(MoreDrillFade),
        ) {
            MoreNestedCover(
                page = heldNested,
                track = track,
                targets = targets,
                addingId = addingId,
                sleepTimer = sleepTimer,
                displayPrefs = displayPrefs,
                hazeState = hazeState,
                onBack = onBack,
                onAddingId = onAddingId,
                onDisplayPrefsChange = onDisplayPrefsChange,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun BoxScope.MoreSheetGlass(hazeState: HazeState?) {
    if (hazeState != null) {
        Box(
            Modifier
                .matchParentSize()
                .hazeEffect(state = hazeState, style = pageSheetHazeStyle()),
        )
    } else {
        Box(
            Modifier
                .matchParentSize()
                .background(MainPalette.Page.copy(alpha = 0.96f)),
        )
    }
    Box(
        Modifier
            .matchParentSize()
            .background(MainPalette.SheetWash),
    )
}

@Composable
private fun MoreNestedCover(
    page: MorePage,
    track: TrackRow,
    targets: List<PlaylistSummary>,
    addingId: Long?,
    sleepTimer: SleepTimerUi,
    displayPrefs: PlayerDisplayPrefs,
    hazeState: HazeState?,
    onBack: () -> Unit,
    onAddingId: (Long?) -> Unit,
    onDisplayPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onClose: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        MoreSheetGlass(hazeState = hazeState)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ZIcons.ChevronLeft,
                        contentDescription = "返回",
                        tint = MainPalette.Ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = when (page) {
                        MorePage.AddToPlaylist -> "添加到歌单"
                        MorePage.Download -> "下载"
                        MorePage.SleepTimer -> "定时停止"
                        MorePage.Translation -> "翻译"
                        MorePage.OutputDevice -> "输出设备"
                        MorePage.Root -> "更多"
                    },
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.2).sp,
                    ),
                )
            }
            when (page) {
                MorePage.AddToPlaylist -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = track.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 13.sp,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        if (targets.isEmpty()) {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "还没有可添加的歌单\n先在个人页创建一个",
                                    style = TextStyle(
                                        color = MainPalette.Secondary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                    ),
                                )
                            }
                        } else {
                            val listH = (MorePlaylistRowH * targets.size + 8.dp)
                                .coerceAtMost(maxHeight)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(listH),
                                contentPadding = PaddingValues(bottom = 8.dp),
                            ) {
                                items(targets, key = { it.id }) { pl ->
                                    MorePlaylistRow(
                                        playlist = pl,
                                        enabled = addingId == null,
                                        onClick = {
                                            if (addingId != null) return@MorePlaylistRow
                                            onAddingId(pl.id)
                                            scope.launch {
                                                val msg = app.playlistEditor.addTrack(pl, track)
                                                context.showIslandNotice(msg, track.coverUrl)
                                                onAddingId(null)
                                                if (!isAddTrackFailure(msg)) onClose()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                MorePage.Download -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = track.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 13.sp,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    MoreDownloadPanel(
                        track = track,
                        onClose = onClose,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                    )
                }
                MorePage.SleepTimer -> {
                    Spacer(Modifier.height(12.dp))
                    PortraitSleepTimerPanel(
                        timer = sleepTimer,
                        onStart = { minutes, wait ->
                            app.playbackBridge.startSleepTimer(minutes, wait)
                            context.showIslandNotice(
                                "将在 ${minutes} 分钟后停止播放",
                                track.coverUrl,
                            )
                        },
                        onCancel = {
                            app.playbackBridge.cancelSleepTimer()
                            context.showIslandNotice("已取消定时停止", track.coverUrl)
                        },
                        onWaitChange = { app.playbackBridge.setSleepTimerWaitForTrackEnd(it) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
                MorePage.Translation -> {
                    Spacer(Modifier.height(12.dp))
                    MoreTranslationPanel(
                        prefs = displayPrefs,
                        onPrefsChange = onDisplayPrefsChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                    )
                }
                MorePage.OutputDevice -> {
                    Spacer(Modifier.height(12.dp))
                    PortraitAudioOutputPanel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
                MorePage.Root -> Unit
            }
        }
    }
}

private fun isAddTrackFailure(msg: String): Boolean =
    msg.contains("失败") ||
        msg.startsWith("请先") ||
        msg.startsWith("无法") ||
        msg.startsWith("只能")

private fun translationRowSubtitle(prefs: PlayerDisplayPrefs): String = when {
    !prefs.portraitLyricPreferTranslation -> "有译文时显示翻译歌词"
    prefs.portraitLyricTranslationCoexist -> "已开启 · 原文与译文并存"
    else -> "已开启 · 覆盖原歌词"
}

@Composable
private fun MoreDownloadPanel(
    track: TrackRow,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val initial = remember { app.trackExportRepository.lastOptions() }
    var quality by remember { mutableStateOf(initial.quality) }
    var includeCover by remember { mutableStateOf(initial.includeCover) }
    var includeLyrics by remember { mutableStateOf(initial.includeLyrics) }
    var includeMetadata by remember { mutableStateOf(initial.includeMetadata) }
    var busy by remember { mutableStateOf(false) }
    val switchColors = MainControls.switchColors()
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MoreRowShape)
                .background(MainPalette.Card)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = "音质",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            AudioQualityGrid(
                selected = quality,
                onSelect = { quality = it },
                compact = true,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${quality.title} · ${quality.caption}",
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        MoreSwitchRow(
            title = "封面",
            subtitle = "封面图单独存一份",
            checked = includeCover,
            enabled = true,
            switchColors = switchColors,
            onCheckedChange = { includeCover = it },
        )
        MoreSwitchRow(
            title = "歌词",
            subtitle = "原文和翻译各一份 .lrc",
            checked = includeLyrics,
            enabled = true,
            switchColors = switchColors,
            onCheckedChange = { includeLyrics = it },
        )
        MoreSwitchRow(
            title = "元数据",
            subtitle = "歌名、歌手、专辑写入 music.json",
            checked = includeMetadata,
            enabled = true,
            switchColors = switchColors,
            onCheckedChange = { includeMetadata = it },
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(MoreRowShape)
                .background(MainPalette.Accent)
                .clickable(
                    enabled = !busy,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (busy) return@clickable
                        busy = true
                        val options = TrackExportOptions(
                            quality = quality,
                            includeCover = includeCover,
                            includeLyrics = includeLyrics,
                            includeMetadata = includeMetadata,
                        )
                        app.trackExportRepository.rememberOptions(options)
                        launchTrackDownload(app, track, options)
                        onClose()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "下载",
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
        }
    }
}

@Composable
private fun MoreTranslationPanel(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    modifier: Modifier = Modifier,
) {
    val transOn = prefs.portraitLyricPreferTranslation
    val coexist = prefs.portraitLyricTranslationCoexist
    val coexistEnabled = transOn
    val pairEnabled = transOn && coexist
    val switchColors = MainControls.switchColors()
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MoreSwitchRow(
            title = "显示翻译歌词",
            subtitle = "开启后，有译文的歌曲按下方方式显示",
            checked = transOn,
            enabled = true,
            switchColors = switchColors,
            onCheckedChange = { on ->
                onPrefsChange(prefs.copy(portraitLyricPreferTranslation = on))
            },
        )
        MoreChoiceRow(
            title = "显示方式",
            subtitle = if (coexist) "播放中显示原文和译文两行" else "有译文时只显示翻译",
            labels = listOf("覆盖原歌词", "与原文并存"),
            selectedIndex = if (coexist) 1 else 0,
            enabled = coexistEnabled,
            onSelect = { index ->
                onPrefsChange(prefs.copy(portraitLyricTranslationCoexist = index == 1))
            },
        )
        MoreChoiceRow(
            title = "两行顺序",
            subtitle = if (prefs.portraitLyricOriginalOnTop) "原文在上 · 译文在下" else "译文在上 · 原文在下",
            labels = listOf("原文在上", "原文在下"),
            selectedIndex = if (prefs.portraitLyricOriginalOnTop) 0 else 1,
            enabled = pairEnabled,
            onSelect = { index ->
                onPrefsChange(prefs.copy(portraitLyricOriginalOnTop = index == 0))
            },
        )
        MoreSwitchRow(
            title = "其余歌词显示译文",
            subtitle = if (prefs.portraitLyricOthersShowTranslation) {
                "已播和待播行也显示译文"
            } else {
                "只有播放中显示译文"
            },
            checked = prefs.portraitLyricOthersShowTranslation,
            enabled = pairEnabled,
            switchColors = switchColors,
            onCheckedChange = { on ->
                onPrefsChange(prefs.copy(portraitLyricOthersShowTranslation = on))
            },
        )
    }
}

@Composable
private fun MoreSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    switchColors: androidx.compose.material3.SwitchColors,
    onCheckedChange: (Boolean) -> Unit,
) {
    val enT by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "moreSwitchEn",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(MoreRowShape)
            .background(MainPalette.Card)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = switchColors,
        )
    }
}

@Composable
private fun MoreChoiceRow(
    title: String,
    subtitle: String,
    labels: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val enT by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "moreChoiceEn",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(MoreRowShape)
            .background(MainPalette.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = MainPalette.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MainPalette.TrackOff),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                val on = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (on) MainPalette.Accent.copy(alpha = 0.18f) else Color.Transparent,
                        )
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            color = if (on) MainPalette.Accent else MainPalette.Secondary,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MoreRowShape)
            .background(MainPalette.Card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MainPalette.Ink,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        Icon(
            imageVector = ZIcons.ChevronRight,
            contentDescription = null,
            tint = MainPalette.Hint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MorePlaylistRow(
    playlist: PlaylistSummary,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MoreRowShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            url = playlist.resolvedCoverUrl(),
            contentDescription = playlist.name,
            modifier = Modifier
                .size(48.dp)
                .clip(MoreCoverShape)
                .background(MainPalette.Placeholder),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (playlist.isHeartPlaylist) {
                    "喜欢的音乐"
                } else {
                    "${playlist.trackCount} 首"
                },
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}
