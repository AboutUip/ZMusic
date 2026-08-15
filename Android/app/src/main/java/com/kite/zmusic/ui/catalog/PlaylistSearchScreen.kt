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
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import kotlinx.coroutines.delay

@Composable
fun PlaylistSearchScreen(
    overlay: MainOverlay.PlaylistSearch,
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    playingTrackId: Long,
    isPlaying: Boolean,
    playingSourceId: Long = 0L,
    isTop: Boolean = true,
    onOpenArtist: ((Long, String, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val vm: PlaylistSearchViewModel = viewModel(
        key = overlay.stackKey(),
        factory = PlaylistSearchViewModelFactory(
            overlay.playlistId,
            overlay.title,
            overlay.heart,
            sessionRepository,
            app.playlistTracksCache,
            app.likedPlaylistRepository,
            app.islandNoticeCenter,
        ),
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var moreTrack by remember { mutableStateOf<TrackRow?>(null) }
    val canRemove = overlay.heart || overlay.owned

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
                text = "搜索歌单",
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
        PlaylistSearchField(
            value = ui.query,
            onValueChange = vm::onQueryChange,
            onSearch = { focus.clearFocus() },
            onClear = {
                focus.clearFocus()
                vm.clearQuery()
            },
            focusRequester = searchFocus,
            canFocus = isTop,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        val q = ui.query.trim()
        val status = when {
            q.isEmpty() -> {
                val loaded = ui.loaded.size
                val expected = ui.expectedCount
                if (expected > loaded && loaded > 0) {
                    "输入歌名、歌手或专辑 · 已加载 $loaded / $expected 首"
                } else if (loaded > 0) {
                    "输入歌名、歌手或专辑 · 共 $loaded 首"
                } else {
                    "输入歌名、歌手或专辑"
                }
            }
            ui.scanning -> {
                val extra = if (ui.hits.isNotEmpty()) " · 已找到 ${ui.hits.size} 首" else ""
                "正在搜索剩余歌曲$extra"
            }
            ui.hits.isNotEmpty() -> "找到 ${ui.hits.size} 首"
            ui.complete || ui.loaded.isNotEmpty() -> "歌单中没有找到相关歌曲"
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
        val errorText = ui.error
        when {
            errorText != null && ui.loaded.isEmpty() -> {
                Text(
                    text = errorText,
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
                    itemsIndexed(ui.hits, key = { idx, t -> "${t.id}-$idx" }) { _, t ->
                        val current = isPlaybackCurrent(
                            trackId = t.id,
                            contextId = overlay.playlistId,
                            playingTrackId = playingTrackId,
                            playingSourceId = playingSourceId,
                        )
                        val playlistIndex = ui.loaded.indexOfFirst { it.id == t.id }
                        CatalogTrackRow(
                            index = if (playlistIndex >= 0) playlistIndex + 1 else 0,
                            track = t,
                            current = current,
                            playing = current && isPlaying,
                            onClick = {
                                val idx = vm.indexInPlaylist(t.id)
                                if (idx >= 0 && ui.loaded.isNotEmpty()) {
                                    onPlayTracks(ui.loaded, idx, overlay.playlistId, ui.title)
                                } else {
                                    onPlayTracks(listOf(t), 0, overlay.playlistId, ui.title)
                                }
                            },
                            onMore = { moreTrack = t },
                        )
                    }
                    if (ui.scanning) {
                        item(key = "playlist-search-scan") {
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
            canRemove = canRemove,
            onDismiss = { moreTrack = null },
            onDownload = { launchTrackDownload(scope, app, it) },
            onRemove = { vm.removeTrack(it, overlay.owned) },
            removeConfirmTitle = if (overlay.heart) {
                "从我喜欢的音乐移除？"
            } else {
                "从歌单移除这首歌？"
            },
            currentPlaylistId = overlay.playlistId,
            onOpenArtist = onOpenArtist,
        )
    }
}

@Composable
private fun PlaylistSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    canFocus: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(Color(0xFFF0F0F2))
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
                        "搜索歌单内的歌曲",
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
