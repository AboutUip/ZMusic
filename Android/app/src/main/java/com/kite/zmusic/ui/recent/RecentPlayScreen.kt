package com.kite.zmusic.ui.recent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.RecentMv
import com.kite.zmusic.data.RecentPageSize
import com.kite.zmusic.data.RecentSong
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.recentMvMatches
import com.kite.zmusic.data.recentSlice
import com.kite.zmusic.data.recentSongMatches
import com.kite.zmusic.data.toTrackRow
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.common.PlayingEqualizer
import com.kite.zmusic.ui.common.SongTitleLine
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.common.LiquidUnderlineTabs
import com.kite.zmusic.ui.common.animateLiquidTabTo
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.i18n.t
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RecentPlayScreen(
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenMv: (RecentMv) -> Unit,
    onSearch: (Boolean) -> Unit,
    playingTrackId: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val store = app.recentPlayStore
    val songs by store.songs.collectAsStateWithLifecycle()
    val mvs by store.mvs.collectAsStateWithLifecycle()
    val ready by store.ready.collectAsStateWithLifecycle()
    var songLoaded by rememberSaveable { mutableIntStateOf(RecentPageSize) }
    var mvLoaded by rememberSaveable { mutableIntStateOf(RecentPageSize) }
    val pager = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val songSlice = remember(songs, songLoaded) {
        recentSlice(songs, "", songLoaded, ::recentSongMatches)
    }
    val mvSlice = remember(mvs, mvLoaded) {
        recentSlice(mvs, "", mvLoaded, ::recentMvMatches)
    }

    Column(
        modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding(),
    ) {
        RecentPlayTopBar(
            selected = pager.currentPage,
            position = pager.currentPage + pager.currentPageOffsetFraction,
            onBack = onBack,
            onSearch = { onSearch(pager.currentPage == 1) },
            onSelect = { page ->
                scope.launch { pager.animateLiquidTabTo(page) }
            },
        )
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            if (!ready) {
                RecentCenter {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else if (page == 0) {
                RecentSongList(
                    sliceShown = songSlice.shown,
                    playable = songSlice.playable,
                    hasMore = songSlice.hasMore,
                    contentBottomInset = contentBottomInset,
                    playingTrackId = playingTrackId,
                    isPlaying = isPlaying,
                    onLoadMore = { songLoaded += RecentPageSize },
                    onPlay = { index ->
                        val queue = songSlice.playable.map { it.toTrackRow() }
                        if (index in queue.indices) {
                            onPlayTracks(queue, index, null, t("最近播放"))
                        }
                    },
                )
            } else {
                RecentMvList(
                    shown = mvSlice.shown,
                    hasMore = mvSlice.hasMore,
                    contentBottomInset = contentBottomInset,
                    onLoadMore = { mvLoaded += RecentPageSize },
                    onOpen = onOpenMv,
                )
            }
        }
    }
}

@Composable
private fun RecentPlayTopBar(
    selected: Int,
    position: Float,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var tabsWidthPx by remember { mutableIntStateOf(-1) }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        val tabsWidth = if (tabsWidthPx < 0) 148.dp else with(density) { tabsWidthPx.toDp() }
        val side = ((maxWidth - tabsWidth) / 2).coerceAtLeast(48.dp)
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .width(side)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Back,
                    contentDescription = t("返回"),
                    tint = MainPalette.Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = t("最近播放"),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        LiquidUnderlineTabs(
            labels = listOf(t("音乐"), t("MV")),
            selectedIndex = selected,
            position = position,
            onSelect = onSelect,
            fontSize = 16.sp,
            tabPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            indicatorGap = 5.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .onSizeChanged { tabsWidthPx = it.width },
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSearch,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.Search,
                contentDescription = t("搜索"),
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun RecentSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
    allowFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(MainPalette.Placeholder)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Search,
            contentDescription = null,
            tint = MainPalette.Secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(MainPalette.Accent),
            textStyle = TextStyle(color = MainPalette.Ink, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onDone() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .focusProperties { canFocus = allowFocus },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = TextStyle(color = MainPalette.Hint, fontSize = 15.sp),
                    )
                }
                inner()
            },
        )
        if (value.isNotEmpty()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClear,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Close,
                    contentDescription = t("清空"),
                    tint = MainPalette.Secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentSongList(
    sliceShown: List<RecentSong>,
    playable: List<RecentSong>,
    hasMore: Boolean,
    contentBottomInset: Dp,
    playingTrackId: Long,
    isPlaying: Boolean,
    onLoadMore: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    if (sliceShown.isEmpty()) {
        RecentCenter {
            Text(
                text = t("还没有最近播放的歌曲"),
                color = MainPalette.Secondary,
                fontSize = 14.sp,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd, hasMore, sliceShown.size) {
        if (nearEnd && hasMore && sliceShown.isNotEmpty()) onLoadMore()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            bottom = contentBottomInset + 16.dp,
        ),
    ) {
        itemsIndexed(sliceShown, key = { _, song -> song.id }) { index, song ->
            val current = song.id == playingTrackId && playingTrackId > 0L
            RecentSongRow(
                song = song,
                index = index + 1,
                current = current,
                playing = current && isPlaying,
                onClick = {
                    val at = playable.indexOfFirst { it.id == song.id }
                    if (at >= 0) onPlay(at)
                },
            )
        }
    }
}

@Composable
private fun RecentMvList(
    shown: List<RecentMv>,
    hasMore: Boolean,
    contentBottomInset: Dp,
    onLoadMore: () -> Unit,
    onOpen: (RecentMv) -> Unit,
) {
    if (shown.isEmpty()) {
        RecentCenter {
            Text(
                text = t("还没有最近播放的 MV"),
                color = MainPalette.Secondary,
                fontSize = 14.sp,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd, hasMore, shown.size) {
        if (nearEnd && hasMore && shown.isNotEmpty()) onLoadMore()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            bottom = contentBottomInset + 16.dp,
        ),
    ) {
        itemsIndexed(shown, key = { _, mv -> mv.id }) { _, mv ->
            RecentMvRow(mv = mv, onClick = { onOpen(mv) })
        }
    }
}

@Composable
private fun RecentSongRow(
    song: RecentSong,
    index: Int,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (current) MainPalette.Accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (current) {
                PlayingEqualizer(
                    playing = playing,
                    color = MainPalette.Accent,
                    modifier = Modifier.size(16.dp, 14.dp),
                )
            } else {
                Text(
                    text = index.toString(),
                    color = MainPalette.Hint,
                    fontSize = 13.sp,
                )
            }
        }
        UrlImage(
            url = song.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            maxPx = UrlImageCache.THUMB_MAX_PX,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SongTitleLine(
                name = song.name,
                vip = song.fee == 1,
                color = if (current) MainPalette.Accent else MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
            )
            Text(
                text = song.artists,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = if (current) MainPalette.Accent.copy(alpha = 0.72f) else MainPalette.Secondary,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

@Composable
private fun RecentMvRow(
    mv: RecentMv,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            url = mv.coverUrl,
            contentDescription = mv.name,
            modifier = Modifier
                .size(width = 86.dp, height = 48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            maxPx = UrlImageCache.THUMB_MAX_PX,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = mv.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = mv.artist.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

@Composable
fun RecentPlaySearchScreen(
    mvs: Boolean,
    contentBottomInset: Dp,
    isTop: Boolean,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenMv: (RecentMv) -> Unit,
    playingTrackId: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val store = app.recentPlayStore
    val songs by store.songs.collectAsStateWithLifecycle()
    val videos by store.mvs.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    val songHits = remember(songs, query, mvs) {
        val q = query.trim()
        if (mvs || q.isEmpty()) emptyList() else songs.filter { recentSongMatches(it, q) }
    }
    val mvHits = remember(videos, query, mvs) {
        val q = query.trim()
        if (!mvs || q.isEmpty()) emptyList() else videos.filter { recentMvMatches(it, q) }
    }
    DisposableEffect(Unit) {
        onDispose {
            keyboard?.hide()
            focus.clearFocus(force = true)
        }
    }
    LaunchedEffect(Unit) {
        if (!isTop) return@LaunchedEffect
        delay(80)
        runCatching {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(isTop) {
        if (!isTop) {
            keyboard?.hide()
            focus.clearFocus(force = true)
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            keyboard?.hide()
                            focus.clearFocus(force = true)
                            onBack()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Back,
                    contentDescription = t("返回"),
                    tint = MainPalette.Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = t("搜索最近播放"),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        RecentSearchField(
            value = query,
            placeholder = if (mvs) t("搜索最近播放的 MV") else t("搜索最近播放的歌曲"),
            onValueChange = { query = it },
            onClear = {
                focus.clearFocus()
                query = ""
            },
            onDone = { focus.clearFocus() },
            focusRequester = searchFocus,
            allowFocus = isTop,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        val q = query.trim()
        val status = when {
            q.isEmpty() -> if (mvs) t("输入 MV 名或歌手") else t("输入歌名、歌手或专辑")
            mvs && mvHits.isNotEmpty() -> t("找到 %s 个", mvHits.size)
            !mvs && songHits.isNotEmpty() -> t("找到 %s 首", songHits.size)
            else -> if (mvs) t("没有找到相关 MV") else t("没有找到相关歌曲")
        }
        Text(
            text = status,
            color = MainPalette.Secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
        )
        when {
            q.isEmpty() -> Unit
            mvs && mvHits.isEmpty() -> Unit
            !mvs && songHits.isEmpty() -> Unit
            mvs -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = contentBottomInset + 16.dp,
                    ),
                ) {
                    itemsIndexed(mvHits, key = { _, mv -> mv.id }) { _, mv ->
                        RecentMvRow(mv = mv, onClick = { onOpenMv(mv) })
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = contentBottomInset + 16.dp,
                    ),
                ) {
                    itemsIndexed(songHits, key = { _, song -> song.id }) { index, song ->
                        val current = song.id == playingTrackId && playingTrackId > 0L
                        RecentSongRow(
                            song = song,
                            index = index + 1,
                            current = current,
                            playing = current && isPlaying,
                            onClick = {
                                onPlayTracks(
                                    songHits.map { it.toTrackRow() },
                                    index,
                                    null,
                                    t("最近播放"),
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
private fun RecentCenter(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
