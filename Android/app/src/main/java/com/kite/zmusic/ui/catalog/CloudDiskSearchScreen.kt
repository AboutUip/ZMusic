package com.kite.zmusic.ui.catalog

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
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
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.main.MainPalette
import kotlinx.coroutines.delay

@Composable
internal fun CloudDiskSearchScreen(
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    isTop: Boolean,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onPushOverlay: (MainOverlay) -> Unit,
    playingTrackId: Long,
    playingSourceId: Long,
    isPlaying: Boolean,
    onOpenArtist: ((Long, String, String?) -> Unit)?,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val vm: CloudDiskViewModel = viewModel(
        key = "cloud-disk",
        factory = CloudDiskViewModelFactory(
            sessionRepository,
            app.cloudDiskRepository,
            app.islandNoticeCenter,
        ),
    )
    LaunchedEffect(Unit) {
        vm.loadRemaining()
    }
    val ui by vm.list.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    var moreTrack by remember { mutableStateOf<TrackRow?>(null) }
    val q = query.trim()
    val hits = remember(q, ui.tracks) {
        if (q.isEmpty()) emptyList()
        else ui.tracks.filter { t ->
            t.name.contains(q, true) ||
                t.artists.contains(q, true) ||
                (t.album?.contains(q, true) == true)
        }
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
        Modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding()
            .imePadding(),
    ) {
        CloudSearchBar(
            title = "搜索云盘",
            onBack = {
                keyboard?.hide()
                focus.clearFocus(force = true)
                onBack()
            },
        )
        CloudSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索云盘里的歌曲",
            onSearch = { focus.clearFocus() },
            onClear = {
                query = ""
                focus.clearFocus()
            },
            focusRequester = searchFocus,
            canFocus = isTop,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        val scanning = !ui.complete && ui.tracks.isNotEmpty()
        val status = when {
            q.isEmpty() -> {
                val n = ui.tracks.size
                if (scanning) "输入歌名或歌手 · 已加载 $n 首"
                else if (n > 0) "输入歌名或歌手 · 共 $n 首"
                else "输入歌名或歌手"
            }
            scanning -> {
                val extra = if (hits.isNotEmpty()) " · 已找到 ${hits.size} 首" else ""
                "正在搜索剩余歌曲$extra"
            }
            hits.isNotEmpty() -> "找到 ${hits.size} 首"
            else -> "云盘中没有找到相关歌曲"
        }
        Text(
            text = status,
            color = MainPalette.Secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
        )
        if (q.isEmpty()) {
            Spacer(Modifier.weight(1f))
        } else {
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
                itemsIndexed(hits, key = { _, t -> t.id }) { _, t ->
                    val current = isPlaybackCurrent(
                        trackId = t.id,
                        contextId = 0L,
                        playingTrackId = playingTrackId,
                        playingSourceId = playingSourceId,
                    )
                    val idx = ui.tracks.indexOfFirst { it.id == t.id }
                    CatalogTrackRow(
                        index = if (idx >= 0) idx + 1 else 0,
                        track = t,
                        current = current,
                        playing = current && isPlaying,
                        onClick = {
                            val i = ui.tracks.indexOfFirst { it.id == t.id }
                            if (i >= 0) onPlayTracks(ui.tracks, i, null, "音乐云盘")
                            else onPlayTracks(listOf(t), 0, null, "音乐云盘")
                        },
                        onMore = { moreTrack = t },
                    )
                }
                if (scanning) {
                    item(key = "cloud-search-scan") {
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
        canRemove = true,
        onDismiss = { moreTrack = null },
        onDownload = { track, options -> launchTrackDownload(app, track, options) },
        onRemove = { vm.removeTrack(it) },
        showSaveToCloud = false,
        extraActions = moreTrack?.let { t -> cloudOverflowExtras(t, vm, onPushOverlay) }.orEmpty(),
        removeConfirmTitle = "从云盘删除？",
        removeConfirmMessage = "会从网易云云盘删掉，不可恢复。",
        onOpenArtist = onOpenArtist,
    )
    CloudLyricDialog(vm)
}

@Composable
internal fun CloudMatchScreen(
    overlay: MainOverlay.CloudMatch,
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    isTop: Boolean,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val diskVm: CloudDiskViewModel = viewModel(
        key = "cloud-disk",
        factory = CloudDiskViewModelFactory(
            sessionRepository,
            app.cloudDiskRepository,
            app.islandNoticeCenter,
        ),
    )
    val vm: CloudMatchViewModel = viewModel(
        key = overlay.stackKey(),
        factory = CloudMatchViewModelFactory(
            overlay.songId,
            overlay.title,
            overlay.artists,
            sessionRepository,
            app.searchRepository,
            app.cloudDiskRepository,
            app.islandNoticeCenter,
        ),
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    var pending by remember { mutableStateOf<TrackRow?>(null) }
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
        Modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding()
            .imePadding(),
    ) {
        CloudSearchBar(
            title = "匹配歌曲",
            onBack = {
                keyboard?.hide()
                focus.clearFocus(force = true)
                onBack()
            },
        )
        CloudSearchField(
            value = ui.query,
            onValueChange = vm::onQuery,
            placeholder = "搜索网易云曲库",
            onSearch = {
                focus.clearFocus()
                vm.search()
            },
            onClear = vm::clear,
            focusRequester = searchFocus,
            canFocus = isTop,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            text = "把「${overlay.title}」匹配到一首公开歌曲，封面和歌手会跟着更新",
            color = MainPalette.Secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
        )
        when {
            ui.loading -> {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            ui.error != null && ui.hits.isEmpty() -> {
                Text(
                    text = ui.error ?: "",
                    color = MainPalette.Secondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp),
                )
            }
            ui.query.trim().isEmpty() -> Spacer(Modifier.weight(1f))
            ui.hits.isEmpty() -> {
                Text(
                    text = "没有找到相关歌曲",
                    color = MainPalette.Secondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp),
                )
            }
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
                    itemsIndexed(ui.hits, key = { _, t -> t.id }) { i, t ->
                        CatalogTrackRow(
                            index = i + 1,
                            track = t,
                            current = false,
                            playing = false,
                            onClick = { pending = t },
                            onMore = { pending = t },
                        )
                    }
                }
            }
        }
    }
    pending?.let { pick ->
        GlassAlertDialog(
            title = "用这首歌匹配？",
            message = "「${pick.name}」· ${pick.artists}\n云盘文件的展示信息会改成这一首。",
            confirmLabel = "匹配",
            onConfirm = {
                val t = pick
                pending = null
                vm.match(t) { ok ->
                    if (ok) {
                        diskVm.load(force = true)
                        onBack()
                    }
                }
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun CloudSearchBar(title: String, onBack: () -> Unit) {
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
        Text(
            text = title,
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
}

@Composable
private fun CloudSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    canFocus: Boolean,
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
                    Text(placeholder, style = TextStyle(color = MainPalette.Hint, fontSize = 15.sp))
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
