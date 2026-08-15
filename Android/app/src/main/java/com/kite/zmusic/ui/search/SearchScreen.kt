package com.kite.zmusic.ui.search

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.SearchArtistHit
import com.kite.zmusic.data.SearchPlaylistHit
import com.kite.zmusic.data.SearchUserHit
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.catalog.TrackOverflowMenu
import com.kite.zmusic.ui.catalog.launchTrackDownload
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.notice.showIslandNotice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenPlaylist: (Long, String, String?) -> Unit,
    onOpenMv: (Long, String, String?, String?) -> Unit,
    onOpenArtist: (SearchArtistHit) -> Unit,
    onHint: (String) -> Unit,
    isTop: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val vm: SearchViewModel = viewModel(
        factory = SearchViewModelFactory(sessionRepository, app.searchHistoryRepository),
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val searchFocus = remember { FocusRequester() }

    fun dismissIme() {
        keyboard?.hide()
        focus.clearFocus(force = true)
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
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
        if (!isTop) dismissIme()
    }
    LaunchedEffect(ui.phase) {
        if (ui.phase == SearchPhase.Results) dismissIme()
    }
    DisposableEffect(Unit) {
        onDispose { dismissIme() }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MainPalette.Page)
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
                            dismissIme()
                            onBack()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Back,
                    contentDescription = "返回",
                    tint = MainPalette.Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = "搜索",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        SearchField(
            value = ui.query,
            phase = ui.phase,
            onValueChange = vm::onQueryChange,
            onSearch = {
                dismissIme()
                vm.submitSearch()
            },
            onClear = {
                dismissIme()
                vm.resetToIdle()
            },
            focusRequester = searchFocus,
            canFocus = isTop,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
        when (ui.phase) {
            SearchPhase.Idle -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = contentBottomInset + 12.dp),
                ) {
                    HistoryBlock(
                        history = ui.history,
                        onWord = {
                            dismissIme()
                            vm.applyHistory(it)
                        },
                        onRemove = vm::removeHistory,
                        onClear = vm::clearHistory,
                    )
                    HotBlock(ui = ui, onWord = {
                        dismissIme()
                        vm.applyHotWord(it)
                    })
                }
            }
            SearchPhase.Suggest -> {
                SuggestList(
                    query = ui.query.trim(),
                    suggesting = ui.suggesting,
                    suggestions = ui.suggestions,
                    contentBottomInset = contentBottomInset,
                    onSearchQuery = {
                        dismissIme()
                        vm.submitSearch()
                    },
                    onPick = { word ->
                        dismissIme()
                        vm.applySuggestion(word)
                    },
                )
            }
            SearchPhase.Results -> {
                SearchResults(
                    ui = ui,
                    contentBottomInset = contentBottomInset,
                    onKind = vm::setKind,
                    onLoadMore = vm::loadMore,
                    onPlay = { list, i -> onPlayTracks(list, i, null, "搜索") },
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenMv = { mv -> onOpenMv(mv.id, mv.name, mv.coverUrl, mv.artist) },
                    onOpenUser = { onHint("暂未支持查看用户") },
                    onOpenArtist = onOpenArtist,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    phase: SearchPhase,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    canFocus: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val showLeadingSearch = phase != SearchPhase.Suggest
    val showTrailingSearch = phase == SearchPhase.Suggest && value.isNotBlank()
    val showTrailingClear = phase == SearchPhase.Results
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(Color(0xFFF0F0F2))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLeadingSearch) {
            Icon(
                imageVector = ZIcons.Search,
                contentDescription = null,
                tint = MainPalette.Secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(MainPalette.Accent),
            textStyle = TextStyle(color = MainPalette.Ink, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .focusProperties { this.canFocus = canFocus },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "搜索歌曲、歌单、MV、歌手",
                        style = TextStyle(color = MainPalette.Hint, fontSize = 15.sp),
                    )
                }
                inner()
            },
        )
        if (showTrailingSearch) {
            FieldIconButton(
                icon = ZIcons.Search,
                contentDescription = "搜索",
                tint = MainPalette.Accent,
                onClick = onSearch,
            )
        }
        if (showTrailingClear) {
            FieldIconButton(
                icon = ZIcons.Close,
                contentDescription = "清空",
                tint = MainPalette.Secondary,
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun FieldIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HistoryBlock(
    history: List<String>,
    onWord: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) return
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "搜索历史",
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "清空",
            color = MainPalette.Secondary,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClear,
                )
                .padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        history.forEach { word ->
            HistoryCapsule(
                word = word,
                onWord = { onWord(word) },
                onRemove = { onRemove(word) },
            )
        }
    }
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun HistoryCapsule(
    word: String,
    onWord: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MainPalette.Hairline, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onWord,
            )
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = word,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp,
            ),
            modifier = Modifier.widthIn(max = 148.dp),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.Close,
                contentDescription = "删除",
                tint = MainPalette.Hint,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotBlock(
    ui: SearchUiState,
    onWord: (String) -> Unit,
) {
    Text(
        text = "热搜",
        style = TextStyle(
            color = MainPalette.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
    Spacer(Modifier.height(12.dp))
    if (ui.hotWords.isEmpty()) {
        Text(
            text = "热搜加载后会出现在这里",
            style = TextStyle(color = MainPalette.Hint, fontSize = 13.sp),
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ui.hotWords.forEach { w ->
                Text(
                    text = w.word,
                    style = TextStyle(
                        color = if (w.highlighted) MainPalette.Accent else MainPalette.Ink,
                        fontSize = 13.sp,
                        fontWeight = if (w.highlighted) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, MainPalette.Hairline, RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onWord(w.word) },
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun SuggestList(
    query: String,
    suggesting: Boolean,
    suggestions: List<String>,
    contentBottomInset: Dp,
    onSearchQuery: () -> Unit,
    onPick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = contentBottomInset + 12.dp,
        ),
    ) {
        if (query.isNotEmpty()) {
            item(key = "search-self") {
                SuggestRow(
                    text = "搜索 “$query”",
                    onClick = onSearchQuery,
                )
            }
        }
        items(suggestions, key = { it }) { word ->
            SuggestRow(text = word, onClick = { onPick(word) })
        }
        if (suggesting && suggestions.isEmpty()) {
            item(key = "suggest-loading") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestRow(
    text: String,
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
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Search,
            contentDescription = null,
            tint = MainPalette.Secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun SearchResults(
    ui: SearchUiState,
    contentBottomInset: Dp,
    onKind: (SearchKind) -> Unit,
    onLoadMore: (SearchKind) -> Unit,
    onPlay: (List<TrackRow>, Int) -> Unit,
    onOpenPlaylist: (Long, String, String?) -> Unit,
    onOpenMv: (RecommendMvCard) -> Unit,
    onOpenUser: (SearchUserHit) -> Unit,
    onOpenArtist: (SearchArtistHit) -> Unit,
) {
    val kinds = SearchKind.entries
    val pager = rememberPagerState(initialPage = ui.kind.ordinal, pageCount = { kinds.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.settledPage) {
        onKind(kinds[pager.settledPage])
    }
    Column(Modifier.fillMaxSize()) {
        SearchKindTabs(
            selected = kinds[pager.currentPage],
            onKind = { kind ->
                scope.launch { pager.animateScrollToPage(kind.ordinal) }
            },
        )
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val kind = kinds[page]
            SearchKindPage(
                kind = kind,
                ui = ui,
                contentBottomInset = contentBottomInset,
                onLoadMore = { onLoadMore(kind) },
                onPlay = onPlay,
                onOpenPlaylist = onOpenPlaylist,
                onOpenMv = onOpenMv,
                onOpenUser = onOpenUser,
                onOpenArtist = onOpenArtist,
            )
        }
    }
}

@Composable
private fun SearchKindPage(
    kind: SearchKind,
    ui: SearchUiState,
    contentBottomInset: Dp,
    onLoadMore: () -> Unit,
    onPlay: (List<TrackRow>, Int) -> Unit,
    onOpenPlaylist: (Long, String, String?) -> Unit,
    onOpenMv: (RecommendMvCard) -> Unit,
    onOpenUser: (SearchUserHit) -> Unit,
    onOpenArtist: (SearchArtistHit) -> Unit,
) {
    val empty = ui.empty(kind)
    val loading = ui.loading(kind)
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 4
        }
    }
    val itemCount = when (kind) {
        SearchKind.Song -> ui.results.size
        SearchKind.Playlist -> ui.playlists.size
        SearchKind.Mv -> ui.mvs.size
        SearchKind.Artist -> ui.artists.size
        SearchKind.User -> ui.users.size
    }
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var moreTrack by remember { mutableStateOf<TrackRow?>(null) }
    var morePlaylist by remember { mutableStateOf<SearchPlaylistHit?>(null) }
    var confirmUnsub by remember { mutableStateOf<PlaylistSummary?>(null) }
    val selfUid by app.playlistCollectionRepository.selfUserId.collectAsStateWithLifecycle()
    val collectionPlaylists by app.playlistCollectionRepository.playlists.collectAsStateWithLifecycle()
    LaunchedEffect(nearEnd, itemCount, ui.hasMore(kind), kind) {
        if (nearEnd && ui.hasMore(kind) && itemCount > 0 && !loading) {
            onLoadMore()
        }
    }
    when {
        loading && empty -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                CircularProgressIndicator(
                    color = MainPalette.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = contentBottomInset + 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (ui.searchError != null && empty && ui.kind == kind) {
                    item(key = "search-error") {
                        Text(
                            text = ui.searchError,
                            style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp),
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                when (kind) {
                    SearchKind.Song -> itemsIndexed(
                        ui.results,
                        key = { _, t -> "song-${t.id}" },
                    ) { idx, t ->
                        SearchSongRow(
                            track = t,
                            onClick = { onPlay(ui.results, idx) },
                            onMore = { moreTrack = t },
                        )
                    }
                    SearchKind.Playlist -> items(
                        ui.playlists,
                        key = { "pl-${it.id}" },
                    ) { pl ->
                        val known = collectionPlaylists.find { it.id == pl.id }
                        val summary = pl.toSummary(known, selfUid)
                        val canCollect = !summary.isOwned && !summary.isHeartPlaylist
                        SearchPlaylistRow(
                            hit = pl,
                            onClick = { onOpenPlaylist(pl.id, pl.name, pl.coverUrl) },
                            onMore = if (canCollect) {
                                { morePlaylist = pl }
                            } else {
                                null
                            },
                        )
                    }
                    SearchKind.Mv -> items(
                        ui.mvs,
                        key = { "mv-${it.id}" },
                    ) { mv ->
                        SearchMvRow(mv = mv, onClick = { onOpenMv(mv) })
                    }
                    SearchKind.Artist -> items(
                        ui.artists,
                        key = { "ar-${it.id}" },
                    ) { artist ->
                        SearchArtistRow(hit = artist, onClick = { onOpenArtist(artist) })
                    }
                    SearchKind.User -> items(
                        ui.users,
                        key = { "u-${it.id}" },
                    ) { user ->
                        SearchUserRow(hit = user, onClick = { onOpenUser(user) })
                    }
                }
                if (ui.loadingMore(kind)) {
                    item(key = "search-more") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MainPalette.Accent.copy(alpha = 0.7f),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    TrackOverflowMenu(
        track = moreTrack,
        canRemove = false,
        onDismiss = { moreTrack = null },
        onDownload = { launchTrackDownload(scope, app, it) },
        onRemove = {},
        onOpenArtist = { id, name, cover ->
            onOpenArtist(SearchArtistHit(id, name, cover))
        },
    )
    morePlaylist?.let { hit ->
        val summary = hit.toSummary(
            known = collectionPlaylists.find { it.id == hit.id },
            selfUid = selfUid,
        )
        GlassActionSheet(
            title = hit.name,
            message = buildList {
                if (hit.trackCount > 0) add("${hit.trackCount} 首")
                hit.creator?.let { add(it) }
            }.joinToString(" · ").takeIf { it.isNotBlank() },
            coverUrl = summary.resolvedCoverUrl() ?: hit.coverUrl,
            onDismiss = { morePlaylist = null },
            actions = buildList {
                if (!summary.isOwned && !summary.isHeartPlaylist) {
                    if (summary.isSubscribed) {
                        add(
                            GlassSheetAction("取消收藏", destructive = true) {
                                confirmUnsub = summary
                                morePlaylist = null
                            },
                        )
                    } else {
                        add(
                            GlassSheetAction("收藏") {
                                morePlaylist = null
                                scope.launch {
                                    context.showIslandNotice(
                                        app.playlistEditor.subscribe(summary),
                                        hit.coverUrl,
                                    )
                                }
                            },
                        )
                    }
                }
            },
        )
    }
    confirmUnsub?.let { pl ->
        GlassAlertDialog(
            title = "取消收藏？",
            message = "不再收藏「${pl.name}」。",
            confirmLabel = "取消收藏",
            confirmDestructive = true,
            onConfirm = {
                confirmUnsub = null
                scope.launch {
                    context.showIslandNotice(
                        app.playlistEditor.unsubscribe(pl),
                        pl.coverUrl,
                    )
                }
            },
            onDismiss = { confirmUnsub = null },
        )
    }
}

@Composable
private fun SearchKindTabs(
    selected: SearchKind,
    onKind: (SearchKind) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchKind.entries.forEach { kind ->
            val on = kind == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onKind(kind) },
                    )
                    .padding(end = 18.dp, top = 4.dp, bottom = 2.dp),
            ) {
                Text(
                    text = kind.label,
                    style = TextStyle(
                        color = if (on) MainPalette.Accent else MainPalette.Secondary,
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(18.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (on) MainPalette.Accent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SearchSongRow(
    track: TrackRow,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrlImage(
                url = track.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = track.artists,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                )
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMore,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.More,
                contentDescription = "更多",
                tint = MainPalette.Hint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SearchPlaylistRow(
    hit: SearchPlaylistHit,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    val meta = buildList {
        if (hit.trackCount > 0) add("${hit.trackCount}首")
        if (hit.playCount > 0L) add("${NcmHomeParse.formatPlayCount(hit.playCount)}次播放")
        hit.creator?.let { add(it) }
    }.joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrlImage(
                url = hit.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = hit.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                    )
                }
            }
        }
        if (onMore != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMore,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.More,
                    contentDescription = "更多",
                    tint = MainPalette.Hint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchMvRow(
    mv: RecommendMvCard,
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            url = mv.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = mv.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            val sub = buildList {
                mv.artist?.let { add(it) }
                if (mv.playCount > 0L) add("${NcmHomeParse.formatPlayCount(mv.playCount)}次播放")
            }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                )
            }
        }
    }
}

@Composable
private fun SearchArtistRow(
    hit: SearchArtistHit,
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            url = hit.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = hit.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun SearchUserRow(
    hit: SearchUserHit,
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            url = hit.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = hit.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            hit.signature?.let { sig ->
                Text(
                    text = sig,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                )
            }
        }
    }
}

private fun SearchPlaylistHit.toSummary(
    known: PlaylistSummary?,
    selfUid: Long,
): PlaylistSummary {
    val owned = known?.isOwned == true ||
        known?.isHeartPlaylist == true ||
        (creatorId > 0L && selfUid > 0L && creatorId == selfUid)
    return PlaylistSummary(
        id = id,
        name = name,
        coverUrl = known?.coverUrl ?: coverUrl,
        trackCount = known?.trackCount?.takeIf { it > 0 } ?: trackCount,
        isHeartPlaylist = known?.isHeartPlaylist == true,
        isOwned = owned,
        isSubscribed = known?.isSubscribed == true,
        playCount = known?.playCount?.takeIf { it > 0L } ?: playCount,
    )
}
