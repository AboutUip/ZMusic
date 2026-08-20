package com.kite.zmusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.FollowedUser
import com.kite.zmusic.data.LikedArtist
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.notice.showIslandNotice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LikedArtistsVmKey = "liked-artists"

@Composable
fun LikedArtistsScreen(
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onSearch: (users: Boolean) -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = rememberLikedArtistsViewModel(sessionRepository)
    LaunchedEffect(Unit) {
        vm.load()
        vm.loadUsers()
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var moreArtist by remember { mutableStateOf<LikedArtist?>(null) }
    var confirmUnfollow by remember { mutableStateOf<LikedArtist?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding()
            .imePadding(),
    ) {
        FollowsTopBar(
            selected = pager.currentPage,
            onBack = onBack,
            onSearch = { onSearch(pager.currentPage == 1) },
            onSelect = { page ->
                scope.launch { pager.animateScrollToPage(page) }
            },
        )
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            if (page == 0) {
                FollowArtistsPane(
                    ui = ui,
                    contentBottomInset = contentBottomInset,
                    onRefresh = vm::refresh,
                    onRetry = { vm.load(force = true) },
                    onLoadMore = vm::loadMore,
                    onOpenArtist = onOpenArtist,
                    onMore = { moreArtist = it },
                )
            } else {
                FollowUsersPane(
                    ui = ui,
                    contentBottomInset = contentBottomInset,
                    onRefresh = vm::refreshUsers,
                    onRetry = { vm.loadUsers(force = true) },
                    onLoadMore = vm::loadMoreUsers,
                    onOpenUser = { user ->
                        context.showIslandNotice("暂未支持查看用户", user.avatarUrl)
                    },
                )
            }
        }
    }

    LikedArtistOverflow(
        moreArtist = moreArtist,
        confirmUnfollow = confirmUnfollow,
        onDismissMore = { moreArtist = null },
        onOpenArtist = onOpenArtist,
        onAskUnfollow = { artist ->
            confirmUnfollow = artist
            moreArtist = null
        },
        onConfirmUnfollow = { artist ->
            confirmUnfollow = null
            vm.unfollow(artist)
        },
        onDismissConfirm = { confirmUnfollow = null },
    )
}

private val FollowTabs = listOf("歌手", "用户")

@Composable
private fun FollowsTopBar(
    selected: Int,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
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
                contentDescription = "返回",
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FollowTabs.forEachIndexed { index, label ->
                FollowTab(
                    label = label,
                    selected = selected == index,
                    onClick = { onSelect(index) },
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
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
                contentDescription = "搜索",
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun FollowTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (selected) MainPalette.Accent else MainPalette.Secondary,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(18.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) MainPalette.Accent else Color.Transparent),
        )
    }
}

@Composable
private fun FollowArtistsPane(
    ui: LikedArtistsUi,
    contentBottomInset: Dp,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
    onMore: (LikedArtist) -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 1 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd, ui.artists.size, ui.hasMore, ui.loadingMore) {
        if (nearEnd && ui.hasMore && !ui.loadingMore && ui.artists.isNotEmpty()) {
            onLoadMore()
        }
    }
    ZPullRefresh(
        refreshing = ui.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            ui.loading && ui.artists.isEmpty() -> FollowLoading()
            ui.error != null && ui.artists.isEmpty() -> FollowMessage(
                text = ui.error ?: "加载失败",
                onClick = onRetry,
            )
            ui.artists.isEmpty() -> FollowMessage(text = "还没有喜欢的歌手")
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = contentBottomInset + 16.dp,
                    ),
                ) {
                    items(ui.artists, key = { it.id }) { artist ->
                        val sub = buildList {
                            if (artist.musicSize > 0) add("${artist.musicSize} 首")
                            if (artist.albumSize > 0) add("${artist.albumSize} 张专辑")
                        }.joinToString(" · ")
                        FollowPersonRow(
                            name = artist.name,
                            coverUrl = artist.coverUrl,
                            subtitle = sub,
                            onClick = { onOpenArtist(artist.id, artist.name, artist.coverUrl) },
                            onMore = { onMore(artist) },
                        )
                    }
                    if (ui.loadingMore) {
                        item(key = "liked-artists-more") { FollowMoreSpinner() }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUsersPane(
    ui: LikedArtistsUi,
    contentBottomInset: Dp,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenUser: (FollowedUser) -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 1 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd, ui.users.size, ui.usersHasMore, ui.usersLoadingMore) {
        if (nearEnd && ui.usersHasMore && !ui.usersLoadingMore && ui.users.isNotEmpty()) {
            onLoadMore()
        }
    }
    ZPullRefresh(
        refreshing = ui.usersRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            ui.usersLoading && ui.users.isEmpty() -> FollowLoading()
            ui.usersError != null && ui.users.isEmpty() -> FollowMessage(
                text = ui.usersError ?: "加载失败",
                onClick = onRetry,
            )
            ui.users.isEmpty() -> FollowMessage(text = "还没有关注的用户")
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = contentBottomInset + 16.dp,
                    ),
                ) {
                    items(ui.users, key = { it.id }) { user ->
                        FollowPersonRow(
                            name = user.name,
                            coverUrl = user.avatarUrl,
                            subtitle = user.signature.orEmpty(),
                            onClick = { onOpenUser(user) },
                        )
                    }
                    if (ui.usersLoadingMore) {
                        item(key = "liked-users-more") { FollowMoreSpinner() }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MainPalette.Accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun FollowMessage(
    text: String,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text = text,
        color = MainPalette.Secondary,
        fontSize = 14.sp,
        modifier = Modifier
            .padding(24.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun FollowMoreSpinner() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MainPalette.Accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun LikedArtistsSearchScreen(
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
    searchUsers: Boolean = false,
    isTop: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val vm = rememberLikedArtistsViewModel(sessionRepository)
    LaunchedEffect(searchUsers) {
        if (searchUsers) vm.loadUsers() else vm.load()
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    val context = LocalContext.current
    var moreArtist by remember { mutableStateOf<LikedArtist?>(null) }
    var confirmUnfollow by remember { mutableStateOf<LikedArtist?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            keyboard?.hide()
            focus.clearFocus(force = true)
            vm.resetQuery()
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

    val noun = if (searchUsers) "用户" else "歌手"
    val loaded = if (searchUsers) ui.users.size else ui.artists.size
    val hasMore = if (searchUsers) ui.usersHasMore else ui.hasMore
    val hitsSize = if (searchUsers) ui.userHits.size else ui.hits.size
    val loadError = if (searchUsers) ui.usersError else ui.error
    val emptySource = if (searchUsers) ui.users.isEmpty() else ui.artists.isEmpty()

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
                    contentDescription = "返回",
                    tint = MainPalette.Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = "搜索$noun",
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
        LikedArtistSearchField(
            value = ui.query,
            onValueChange = { vm.onQueryChange(it, searchUsers) },
            onSearch = { focus.clearFocus() },
            onClear = {
                focus.clearFocus()
                vm.clearQuery()
            },
            focusRequester = searchFocus,
            canFocus = isTop,
            placeholder = if (searchUsers) "搜索关注的用户" else "搜索收藏的歌手",
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        val q = ui.query.trim()
        val status = when {
            q.isEmpty() -> {
                if (hasMore && loaded > 0) "输入${noun}名 · 已加载 $loaded 位"
                else if (loaded > 0) "输入${noun}名 · 共 $loaded 位"
                else "输入${noun}名"
            }
            ui.scanning -> {
                val extra = if (hitsSize > 0) " · 已找到 $hitsSize 位" else ""
                "正在搜索剩余$noun$extra"
            }
            hitsSize > 0 -> "找到 $hitsSize 位"
            !hasMore || loaded > 0 -> "没有找到相关$noun"
            else -> null
        }
        if (status != null) {
            Text(
                text = status,
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        when {
            loadError != null && emptySource -> {
                Text(
                    text = loadError,
                    color = MainPalette.Secondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp),
                )
            }
            q.isEmpty() -> Spacer(Modifier.weight(1f))
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = contentBottomInset + 16.dp,
                    ),
                ) {
                    if (searchUsers) {
                        items(ui.userHits, key = { "u-${it.id}" }) { user ->
                            FollowPersonRow(
                                name = user.name,
                                coverUrl = user.avatarUrl,
                                subtitle = user.signature.orEmpty(),
                                onClick = {
                                    context.showIslandNotice("暂未支持查看用户", user.avatarUrl)
                                },
                            )
                        }
                    } else {
                        items(ui.hits, key = { "a-${it.id}" }) { artist ->
                            val sub = buildList {
                                if (artist.musicSize > 0) add("${artist.musicSize} 首")
                                if (artist.albumSize > 0) add("${artist.albumSize} 张专辑")
                            }.joinToString(" · ")
                            FollowPersonRow(
                                name = artist.name,
                                coverUrl = artist.coverUrl,
                                subtitle = sub,
                                onClick = { onOpenArtist(artist.id, artist.name, artist.coverUrl) },
                                onMore = { moreArtist = artist },
                            )
                        }
                    }
                    if (ui.scanning) {
                        item(key = "liked-follow-search-scan") {
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
    }

    if (!searchUsers) {
        LikedArtistOverflow(
            moreArtist = moreArtist,
            confirmUnfollow = confirmUnfollow,
            onDismissMore = { moreArtist = null },
            onOpenArtist = onOpenArtist,
            onAskUnfollow = { artist ->
                confirmUnfollow = artist
                moreArtist = null
            },
            onConfirmUnfollow = { artist ->
                confirmUnfollow = null
                vm.unfollow(artist)
            },
            onDismissConfirm = { confirmUnfollow = null },
        )
    }
}

@Composable
private fun rememberLikedArtistsViewModel(
    sessionRepository: SessionRepository,
): LikedArtistsViewModel {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    return viewModel(
        key = LikedArtistsVmKey,
        factory = LikedArtistsViewModelFactory(
            sessionRepository,
            app.islandNoticeCenter,
            app.artistRepository,
        ),
    )
}

@Composable
private fun LikedArtistOverflow(
    moreArtist: LikedArtist?,
    confirmUnfollow: LikedArtist?,
    onDismissMore: () -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
    onAskUnfollow: (LikedArtist) -> Unit,
    onConfirmUnfollow: (LikedArtist) -> Unit,
    onDismissConfirm: () -> Unit,
) {
    moreArtist?.let { artist ->
        GlassActionSheet(
            title = artist.name,
            coverUrl = artist.coverUrl,
            onDismiss = onDismissMore,
            actions = listOf(
                GlassSheetAction("查看歌手") {
                    onDismissMore()
                    onOpenArtist(artist.id, artist.name, artist.coverUrl)
                },
                GlassSheetAction("取消收藏", destructive = true) {
                    onAskUnfollow(artist)
                },
            ),
        )
    }
    confirmUnfollow?.let { artist ->
        GlassAlertDialog(
            title = "取消收藏这位歌手？",
            message = "「${artist.name}」将从你的收藏中移除",
            confirmLabel = "取消收藏",
            confirmDestructive = true,
            onConfirm = { onConfirmUnfollow(artist) },
            onDismiss = onDismissConfirm,
        )
    }
}

@Composable
private fun LikedArtistSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    canFocus: Boolean = true,
    placeholder: String = "搜索收藏的歌手",
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
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .focusProperties { this.canFocus = canFocus },
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
                    contentDescription = "清空",
                    tint = MainPalette.Secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FollowPersonRow(
    name: String,
    coverUrl: String?,
    subtitle: String,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
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
                url = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                maxPx = UrlImageCache.THUMB_MAX_PX,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 12.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
