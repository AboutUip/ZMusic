package com.kite.zmusic.ui.workshop

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.NetworkPhase
import com.kite.zmusic.plugin.PluginDebugProbe
import com.kite.zmusic.plugin.PluginRecord
import com.kite.zmusic.ui.catalog.CatalogTopBar
import com.kite.zmusic.ui.chrome.ChromeWallpaperBackdrop
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.common.predictiveBackLayer
import com.kite.zmusic.ui.common.rememberPredictiveBackUi
import com.kite.zmusic.ui.community.rememberCommunityLoginOpener
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.LandscapeCoverEnter
import com.kite.zmusic.ui.main.LandscapeCoverExit
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.theme.MainControls
import com.kite.zmusic.workshop.WorkshopApiError
import com.kite.zmusic.workshop.WorkshopPluginCard
import com.kite.zmusic.workshop.WorkshopPluginDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val WorkshopTabs = listOf("浏览社区", "模块")
private val DrillSlideSpec = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)
private val DrillFadeSpec = tween<Float>(durationMillis = 220)

@Composable
fun CreativeWorkshopScreen(
    contentBottomInset: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val auth by app.workshopAuthStore.session.collectAsStateWithLifecycle()
    val net by app.networkMode.state.collectAsStateWithLifecycle()
    val openLogin = rememberCommunityLoginOpener()
    val pager = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val offline = net.phase == NetworkPhase.Offline
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val searchVisible = remember { MutableTransitionState(false) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val detailVisible = remember { MutableTransitionState(false) }

    fun openSearch() {
        searchVisible.targetState = true
    }

    fun closeSearch() {
        searchVisible.targetState = false
    }

    fun openDetail(id: String) {
        detailId = id
        detailVisible.targetState = true
    }

    fun closeDetail() {
        detailVisible.targetState = false
    }

    BackHandler(enabled = detailVisible.targetState) { closeDetail() }
    BackHandler(enabled = searchVisible.targetState && !detailVisible.targetState) {
        closeSearch()
    }

    Box(
        modifier
            .fillMaxSize()
            .chromePage(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            if (auth == null) {
                CatalogTopBar(title = "创意工坊", onBack = onBack)
                WorkshopGate(
                    contentBottomInset = contentBottomInset,
                    onConfirm = openLogin,
                )
            } else {
                WorkshopHomeTopBar(
                    selected = pager.currentPage,
                    onBack = onBack,
                    onSearch = { openSearch() },
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
                        if (offline) {
                            WorkshopEmptyHint(
                                text = "浏览需要网络",
                                contentBottomInset = contentBottomInset,
                            )
                        } else {
                            WorkshopBrowseTab(
                                contentBottomInset = contentBottomInset,
                                onOpenDetail = { openDetail(it) },
                            )
                        }
                    } else {
                        WorkshopModulesTab(contentBottomInset = contentBottomInset)
                    }
                }
            }
        }

        WorkshopDrillLayer(
            visibleState = searchVisible,
            landscape = landscape,
            zIndex = 4f,
            onBack = { closeSearch() },
        ) {
            WorkshopSearchPage(
                contentBottomInset = contentBottomInset,
                offline = offline,
                onBack = { closeSearch() },
                onOpenDetail = { openDetail(it) },
            )
        }

        WorkshopDrillLayer(
            visibleState = detailVisible,
            landscape = landscape,
            zIndex = 5f,
            onBack = { closeDetail() },
        ) {
            val id = detailId
            Column(Modifier.fillMaxSize()) {
                CatalogTopBar(
                    title = "插件详情",
                    onBack = { closeDetail() },
                )
                if (id != null) {
                    WorkshopDetailPage(
                        pluginId = id,
                        contentBottomInset = contentBottomInset,
                        offline = offline,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkshopDrillLayer(
    visibleState: MutableTransitionState<Boolean>,
    landscape: Boolean,
    zIndex: Float,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val covering = visibleState.currentState || visibleState.targetState
    val backUi = rememberPredictiveBackUi(
        enabled = visibleState.targetState,
        onBack = onBack,
    )
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (covering) zIndex else 0f)
            .predictiveBackLayer(backUi),
        enter = if (landscape) {
            LandscapeCoverEnter
        } else {
            slideInHorizontally(DrillSlideSpec) { it } + fadeIn(DrillFadeSpec)
        },
        exit = if (landscape) {
            LandscapeCoverExit
        } else {
            slideOutHorizontally(DrillSlideSpec) { it } + fadeOut(DrillFadeSpec)
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            ChromeWallpaperBackdrop()
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding(),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun WorkshopHomeTopBar(
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
            WorkshopTabs.forEachIndexed { index, label ->
                WorkshopTabLabel(
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
                contentDescription = "搜索插件",
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun WorkshopTabLabel(
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
private fun WorkshopGate(
    contentBottomInset: Dp,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = contentBottomInset + 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "首次确认社区身份",
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "扫一次社区登录码，社区会保存你的资料，之后创意工坊长期可用（含应用更新）。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ),
        )
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MainPalette.Accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onConfirm,
                )
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Text(
                "去扫码确认",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun WorkshopEmptyHint(text: String, contentBottomInset: Dp) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = contentBottomInset),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = MainPalette.Secondary, fontSize = 15.sp))
    }
}

@Composable
private fun WorkshopBrowseTab(
    contentBottomInset: Dp,
    onOpenDetail: (String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val repo = app.workshopRepository
    var items by remember { mutableStateOf<List<WorkshopPluginCard>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun loadFirst(pull: Boolean) {
        scope.launch {
            if (pull) refreshing = true
            runCatching { repo.listPlugins(1) }
                .onSuccess {
                    items = it.entries
                    more = it.more
                    page = 1
                    failed = false
                }
                .onFailure {
                    if (!pull || items.isEmpty()) {
                        items = emptyList()
                        more = false
                    }
                    failed = items.isEmpty()
                }
            ready = true
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { loadFirst(pull = false) }

    LaunchedEffect(listState, more, loadingMore, ready, failed, refreshing) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && more && ready && !failed && !loadingMore && !refreshing) {
                loadingMore = true
                val next = page + 1
                runCatching { repo.listPlugins(next) }
                    .onSuccess {
                        items = items + it.entries
                        more = it.more
                        page = next
                    }
                loadingMore = false
            }
        }
    }

    ZPullRefresh(
        refreshing = refreshing,
        onRefresh = { loadFirst(pull = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            !ready && !failed -> {
                // 与更新日志相同：就绪前不空转圈，失败后直接繁忙文案
                Box(Modifier.fillMaxSize())
            }
            failed && items.isEmpty() -> {
                WorkshopEmptyHint("社区服务器繁忙", contentBottomInset)
            }
            items.isEmpty() -> {
                WorkshopEmptyHint("暂无上架插件", contentBottomInset)
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = contentBottomInset + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { card ->
                        WorkshopCardRow(card) { onOpenDetail(card.id) }
                    }
                    if (loadingMore) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MainPalette.Accent,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkshopSearchPage(
    contentBottomInset: Dp,
    offline: Boolean,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val repo = app.workshopRepository
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<WorkshopPluginCard>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var searching by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var didSearch by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        delay(80)
        runCatching {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    fun runSearch(q: String, reset: Boolean) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            items = emptyList()
            more = false
            didSearch = false
            searchFailed = false
            return
        }
        if (offline) {
            searchFailed = true
            didSearch = true
            items = emptyList()
            more = false
            return
        }
        scope.launch {
            if (reset) searching = true
            val targetPage = if (reset) 1 else page + 1
            runCatching { repo.listPlugins(targetPage, trimmed) }
                .onSuccess {
                    items = if (reset) it.entries else items + it.entries
                    more = it.more
                    page = targetPage
                    didSearch = true
                    searchFailed = false
                }
                .onFailure {
                    if (reset) {
                        items = emptyList()
                        more = false
                        searchFailed = true
                    }
                    didSearch = true
                }
            searching = false
            loadingMore = false
        }
    }

    LaunchedEffect(listState, more, loadingMore, searching, query) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && more && didSearch && !searching && !loadingMore) {
                loadingMore = true
                runSearch(query, reset = false)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
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
                text = "搜索插件",
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
        WorkshopSearchField(
            value = query,
            onValueChange = { query = it },
            onSearch = {
                focus.clearFocus()
                keyboard?.hide()
                runSearch(query, reset = true)
            },
            onClear = {
                query = ""
                items = emptyList()
                more = false
                didSearch = false
                searchFailed = false
                focus.clearFocus()
            },
            focusRequester = searchFocus,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        val status = when {
            offline || searchFailed -> "社区服务器繁忙"
            query.trim().isEmpty() -> "输入插件名或 id"
            searching && items.isEmpty() -> null
            didSearch && items.isEmpty() -> "没有找到相关插件"
            didSearch -> "找到 ${items.size} 个"
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
            !searching && (offline || searchFailed) && items.isEmpty() -> {
                WorkshopEmptyHint("社区服务器繁忙", contentBottomInset)
            }
            searching && items.isEmpty() -> {
                Box(Modifier.fillMaxSize())
            }
            items.isEmpty() -> Spacer(Modifier.weight(1f))
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = contentBottomInset + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.id }) { card ->
                        WorkshopCardRow(card) { onOpenDetail(card.id) }
                    }
                    if (loadingMore) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MainPalette.Accent,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkshopSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Search,
            contentDescription = null,
            tint = MainPalette.Hint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(80)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            textStyle = TextStyle(
                color = MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(MainPalette.Accent),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "搜索插件名或 id",
                            color = MainPalette.Hint,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                imageVector = ZIcons.Close,
                contentDescription = "清除",
                tint = MainPalette.Hint,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClear,
                    ),
            )
        }
    }
}

@Composable
private fun WorkshopCardRow(card: WorkshopPluginCard, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MainPalette.Placeholder),
            contentAlignment = Alignment.Center,
        ) {
            if (card.coverUrl.startsWith("http://") || card.coverUrl.startsWith("https://")) {
                UrlImage(
                    url = card.coverUrl,
                    contentDescription = card.name,
                    modifier = Modifier.fillMaxSize(),
                    maxPx = UrlImageCache.THUMB_MAX_PX,
                )
            } else {
                Icon(
                    ZIcons.Extension,
                    contentDescription = null,
                    tint = MainPalette.Secondary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                card.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                card.description.ifBlank { card.id },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 18.sp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "v${card.version} · ★ ${"%.1f".format(card.ratingAvg)} · ${card.downloads} 次下载",
                style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun WorkshopModulesTab(contentBottomInset: Dp) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val repo = app.workshopRepository
    val context = LocalContext.current
    val modulesRevision by repo.modulesRevision().collectAsStateWithLifecycle()
    val pluginPages by app.pluginEngine.ui.pages.collectAsStateWithLifecycle()
    var modules by remember { mutableStateOf(listModulesOrdered(repo)) }
    var moreTarget by remember { mutableStateOf<PluginRecord?>(null) }
    var confirmDelete by remember { mutableStateOf<PluginRecord?>(null) }
    val switchColors = MainControls.switchColors()

    fun refresh() {
        modules = listModulesOrdered(repo)
    }

    LaunchedEffect(modulesRevision) { refresh() }

    if (modules.isEmpty()) {
        WorkshopEmptyHint("还没有本机模块", contentBottomInset)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = contentBottomInset + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(modules, key = { it.id }) { rec ->
                val probe = rec.id == PluginDebugProbe.ID
                ModuleRow(
                    record = rec,
                    readOnly = probe,
                    switchColors = switchColors,
                    onEnabled = { enabled ->
                        if (probe) return@ModuleRow
                        repo.setModuleEnabled(rec.id, enabled)
                        refresh()
                    },
                    onMore = if (probe) {
                        null
                    } else {
                        { moreTarget = rec }
                    },
                )
            }
        }
    }

    moreTarget?.let { rec ->
        GlassActionSheet(
            title = rec.name,
            message = rec.id,
            onDismiss = { moreTarget = null },
            contentKey = "workshop-module-${rec.id}",
            actions = buildList {
                if (!pluginPages[rec.id].isNullOrEmpty()) {
                    add(
                        GlassSheetAction("打开页面") {
                            app.pluginEngine.ui.openPreferred(rec.id)
                            moreTarget = null
                        },
                    )
                }
                add(
                    GlassSheetAction("删除", destructive = true) {
                        confirmDelete = rec
                        moreTarget = null
                    },
                )
            },
        )
    }
    confirmDelete?.let { rec ->
        GlassAlertDialog(
            title = "删除插件？",
            message = "「${rec.name}」会从本机移除，可之后再从创意工坊安装。",
            confirmLabel = "删除",
            confirmDestructive = true,
            onConfirm = {
                confirmDelete = null
                val ok = repo.uninstallModule(rec.id)
                refresh()
                context.showIslandNotice(
                    if (ok) "已删除「${rec.name}」" else "删除失败",
                )
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

private fun listModulesOrdered(repo: com.kite.zmusic.workshop.WorkshopRepository): List<PluginRecord> {
    val list = repo.modules()
    val probe = list.find { it.id == PluginDebugProbe.ID } ?: PluginRecord(
        id = PluginDebugProbe.ID,
        name = "引擎探针",
        version = 1,
        entry = "index.js",
        engineMin = 1,
        engineMax = null,
        enabled = false,
        quarantined = false,
    )
    val others = list
        .filter { it.id != PluginDebugProbe.ID }
        .sortedBy { it.name.lowercase() }
    return listOf(probe) + others
}

@Composable
private fun ModuleRow(
    record: PluginRecord,
    readOnly: Boolean,
    switchColors: SwitchColors,
    onEnabled: (Boolean) -> Unit,
    onMore: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                record.name,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (readOnly) {
                    "引擎探针 · 仅显示"
                } else {
                    buildString {
                        append(record.id)
                        append(" · v")
                        append(record.version)
                        if (record.quarantined) append(" · 已隔离")
                    }
                },
                style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
            )
        }
        if (readOnly) {
            Text(
                "系统",
                style = TextStyle(
                    color = MainPalette.Hint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        } else {
            Switch(
                checked = record.enabled && !record.quarantined,
                onCheckedChange = onEnabled,
                enabled = !record.quarantined,
                colors = switchColors,
            )
        }
        if (onMore != null) {
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(36.dp)
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
private fun WorkshopDetailPage(
    pluginId: String,
    contentBottomInset: Dp,
    offline: Boolean,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val repo = app.workshopRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<WorkshopPluginDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailedHint by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var myRating by remember { mutableIntStateOf(0) }
    var aboutExpanded by remember { mutableStateOf(false) }
    val modulesRevision by repo.modulesRevision().collectAsStateWithLifecycle()
    val localModule = remember(modulesRevision, pluginId) { repo.findModule(pluginId) }

    LaunchedEffect(pluginId, offline) {
        if (offline) {
            loading = false
            loadFailedHint = null
            return@LaunchedEffect
        }
        loading = true
        loadFailedHint = null
        val result = runCatching { repo.detail(pluginId) }
        val d = result.getOrNull()
        detail = d
        myRating = d?.myRating ?: 0
        if (d == null) {
            loadFailedHint = when (val err = result.exceptionOrNull()) {
                is WorkshopApiError.Missing -> "插件不存在或已下架"
                is WorkshopApiError.Unauthorized -> "需要重新确认社区身份"
                else -> "社区服务器繁忙"
            }
        }
        loading = false
    }

    if (offline) {
        WorkshopEmptyHint("详情需要网络", contentBottomInset)
        return
    }
    if (loading) {
        Box(Modifier.fillMaxSize())
        return
    }
    val d = detail
    if (d == null) {
        WorkshopEmptyHint(loadFailedHint ?: "社区服务器繁忙", contentBottomInset)
        return
    }

    val readmeSource = d.readme
    val aboutPreviewBlocks = 4
    val totalBlocks = remember(readmeSource) { readmeBlockCount(readmeSource) }
    val canExpandAbout = totalBlocks > aboutPreviewBlocks || d.readmeTruncated

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        // 头图区：图标 + 标题 + 作者（Play 货架感）
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MainPalette.Placeholder),
            ) {
                if (d.card.coverUrl.startsWith("http://") || d.card.coverUrl.startsWith("https://")) {
                    UrlImage(
                        url = d.card.coverUrl,
                        contentDescription = d.card.name,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        ZIcons.Extension,
                        contentDescription = null,
                        tint = MainPalette.Secondary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.card.name,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    d.card.author.ifBlank { d.card.id },
                    style = TextStyle(
                        color = MainPalette.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    d.card.id,
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // 指标条：评分 | 下载 | 体积 | 版本
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkshopMetricCell(
                primary = if (d.card.ratingCount > 0) {
                    String.format("%.1f", d.card.ratingAvg)
                } else {
                    "—"
                },
                secondary = if (d.card.ratingCount > 0) {
                    "★ · ${d.card.ratingCount} 人"
                } else {
                    "暂无评分"
                },
            )
            WorkshopMetricDivider()
            WorkshopMetricCell(
                primary = formatWorkshopCount(d.card.downloads),
                secondary = "次下载",
            )
            WorkshopMetricDivider()
            WorkshopMetricCell(
                primary = formatWorkshopBytes(d.sizeBytes),
                secondary = "大小",
            )
            WorkshopMetricDivider()
            WorkshopMetricCell(
                primary = "v${d.card.version}",
                secondary = "引擎 ${d.card.engineMin}" +
                    (d.card.engineMax?.let { "–$it" } ?: "+"),
            )
        }

        Spacer(Modifier.height(18.dp))

        val canInstall = localModule == null || localModule.version < d.card.version
        val installLabel = when {
            busy -> "处理中…"
            localModule == null -> "安装"
            localModule.version < d.card.version -> "更新"
            else -> "已安装"
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    when {
                        busy -> MainPalette.Accent.copy(alpha = 0.45f)
                        canInstall -> MainPalette.Accent
                        else -> MainPalette.Ink.copy(alpha = 0.18f)
                    },
                )
                .clickable(
                    enabled = !busy && canInstall,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    busy = true
                    scope.launch {
                        repo.downloadAndInstall(d)
                        busy = false
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                installLabel,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                localModule != null && localModule.version >= d.card.version ->
                    "已在本机 · 到「模块」开启后才会运行"
                else ->
                    "安装后默认不启用，请到「模块」打开开关。"
            },
            style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
        )

        Spacer(Modifier.height(22.dp))
        Text(
            "关于此插件",
            style = TextStyle(
                color = MainPalette.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            ),
        )
        if (d.card.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                d.card.description,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (readmeSource.isBlank()) {
            Text(
                "暂无说明",
                style = TextStyle(color = MainPalette.Secondary, fontSize = 14.sp),
            )
        } else {
            WorkshopReadmeMarkdown(
                source = readmeSource,
                maxBlocks = if (aboutExpanded) null else aboutPreviewBlocks,
            )
            if (canExpandAbout) {
                Text(
                    if (aboutExpanded) "收起" else "查看更多",
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { aboutExpanded = !aboutExpanded },
                    style = TextStyle(
                        color = MainPalette.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            if (d.readmeTruncated && aboutExpanded) {
                Text(
                    "（服务端已截断）",
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "评分",
            style = TextStyle(
                color = MainPalette.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (d.card.ratingCount > 0) {
                    String.format("%.1f", d.card.ratingAvg)
                } else {
                    "—"
                },
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..5).forEach { star ->
                        Text(
                            if (star <= myRating) "★" else "☆",
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                scope.launch {
                                    runCatching { repo.rate(pluginId, star) }
                                        .onSuccess {
                                            myRating = it.myRating ?: star
                                            detail = d.copy(
                                                card = d.card.copy(
                                                    ratingAvg = it.ratingAvg,
                                                    ratingCount = it.ratingCount,
                                                ),
                                                myRating = it.myRating,
                                            )
                                        }
                                        .onFailure { context.showIslandNotice(it.toUserMessage()) }
                                }
                            },
                            style = TextStyle(color = MainPalette.Accent, fontSize = 22.sp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (myRating > 0) "已评 $myRating 星 · 点击可改" else "点星评分",
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                )
                if (myRating > 0) {
                    Text(
                        "取消评分",
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                scope.launch {
                                    runCatching { repo.clearRating(pluginId) }
                                        .onSuccess {
                                            myRating = 0
                                            detail = d.copy(
                                                card = d.card.copy(
                                                    ratingAvg = it.ratingAvg,
                                                    ratingCount = it.ratingCount,
                                                ),
                                                myRating = null,
                                            )
                                        }
                                        .onFailure { context.showIslandNotice(it.toUserMessage()) }
                                }
                            },
                        style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkshopMetricCell(primary: String, secondary: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            primary,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            secondary,
            style = TextStyle(color = MainPalette.Secondary, fontSize = 11.sp),
        )
    }
}

@Composable
private fun WorkshopMetricDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MainPalette.Ink.copy(alpha = 0.12f)),
    )
}

private fun formatWorkshopBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0fKB", kb)
    val mb = kb / 1024.0
    return if (mb < 10) String.format("%.1fMB", mb) else String.format("%.0fMB", mb)
}

private fun formatWorkshopCount(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 10_000 -> String.format("%.1f千", n / 1000.0)
    else -> String.format("%.1f万", n / 10_000.0)
}

private fun Throwable.toUserMessage(): String = when (this) {
    is WorkshopApiError.Unauthorized -> "需要重新确认社区身份"
    is WorkshopApiError.RateLimited -> "请求太频繁，稍后再试"
    is WorkshopApiError.Missing -> "插件不存在或已下架"
    is WorkshopApiError.Message -> message
    else -> message?.takeIf { it.isNotBlank() } ?: "网络出错"
}
