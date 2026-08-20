package com.kite.zmusic.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.ArtistAlbumCard
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.ui.catalog.CatalogTopBar
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.main.MainPalette

internal fun artistViewModelKey(artistId: Long) = "artist-$artistId"

@Composable
internal fun rememberArtistViewModel(
    artistId: Long,
    seedName: String,
    seedCover: String?,
    sessionRepository: SessionRepository,
): ArtistViewModel {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    return viewModel(
        key = artistViewModelKey(artistId),
        factory = ArtistViewModelFactory(
            artistId = artistId,
            seedName = seedName,
            seedCover = seedCover,
            sessionRepository = sessionRepository,
            islandNotices = app.islandNoticeCenter,
            artists = app.artistRepository,
        ),
    )
}

@Composable
fun ArtistAlbumsScreen(
    overlay: MainOverlay.ArtistAlbums,
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = rememberArtistViewModel(
        artistId = overlay.artistId,
        seedName = overlay.name,
        seedCover = overlay.coverUrl,
        sessionRepository = sessionRepository,
    )
    LaunchedEffect(overlay.artistId) { vm.load() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 1 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd, ui.albums.size, ui.albumsMore, ui.albumsLoading) {
        if (nearEnd && ui.albumsMore && !ui.albumsLoading && ui.albums.isNotEmpty()) {
            vm.loadMoreAlbums()
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding(),
    ) {
        CatalogTopBar(title = "专辑", onBack = onBack)
        when {
            ui.error != null && ui.albums.isEmpty() && !ui.loading -> {
                Text(
                    text = ui.error ?: "暂时没有专辑",
                    color = MainPalette.Secondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { vm.load(force = true) },
                        ),
                )
            }
            else -> {
                ZPullRefresh(
                    refreshing = ui.refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (ui.loading && ui.albums.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = MainPalette.Accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    } else {
                        val cols = 3
                        val rows = (ui.albums.size + cols - 1) / cols
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                bottom = contentBottomInset + 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(rows) { row ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    repeat(cols) { col ->
                                        val i = row * cols + col
                                        if (i < ui.albums.size) {
                                            AlbumGridTile(
                                                album = ui.albums[i],
                                                onOpen = { onOpenAlbum(ui.albums[i].id, ui.albums[i].name) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistMvsScreen(
    overlay: MainOverlay.ArtistMvs,
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onOpenMv: (Long, String, String?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = rememberArtistViewModel(
        artistId = overlay.artistId,
        seedName = overlay.name,
        seedCover = overlay.coverUrl,
        sessionRepository = sessionRepository,
    )
    LaunchedEffect(overlay.artistId) { vm.load() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 1 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd, ui.mvs.size, ui.mvsMore, ui.mvsLoading) {
        if (nearEnd && ui.mvsMore && !ui.mvsLoading && ui.mvs.isNotEmpty()) {
            vm.loadMoreMvs()
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding(),
    ) {
        CatalogTopBar(title = "MV", onBack = onBack)
        when {
            ui.error != null && ui.mvs.isEmpty() && !ui.loading -> {
                Text(
                    text = ui.error ?: "暂时没有 MV",
                    color = MainPalette.Secondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { vm.load(force = true) },
                        ),
                )
            }
            else -> {
                ZPullRefresh(
                    refreshing = ui.refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (ui.loading && ui.mvs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = MainPalette.Accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    } else {
                        val cols = 2
                        val rows = (ui.mvs.size + cols - 1) / cols
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                bottom = contentBottomInset + 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(rows) { row ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    repeat(cols) { col ->
                                        val i = row * cols + col
                                        if (i < ui.mvs.size) {
                                            MvGridTile(
                                                mv = ui.mvs[i],
                                                onOpen = {
                                                    val mv = ui.mvs[i]
                                                    onOpenMv(mv.id, mv.name, mv.coverUrl, mv.artist)
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGridTile(
    album: ArtistAlbumCard,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpen,
        ),
    ) {
        UrlImage(
            url = album.coverUrl,
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        val sub = buildList {
            album.year?.let { add(it) }
            if (album.size > 0) add("${album.size}首")
        }.joinToString(" · ")
        if (sub.isNotEmpty()) {
            Text(
                text = sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = MainPalette.Secondary, fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun MvGridTile(
    mv: RecommendMvCard,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpen,
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MainPalette.Placeholder),
        ) {
            UrlImage(
                url = mv.coverUrl,
                contentDescription = mv.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (mv.playCount > 0L) {
                Text(
                    text = NcmHomeParse.formatPlayCount(mv.playCount),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = mv.name,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
