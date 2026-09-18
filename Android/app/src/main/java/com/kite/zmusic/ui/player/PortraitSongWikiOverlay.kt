package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.SongWikiCoverItem
import com.kite.zmusic.data.SongWikiPage
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.plugin.PluginSurfaces
import com.kite.zmusic.plugin.PluginUiTarget
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.plugin.pluginSurface
import com.kite.zmusic.ui.theme.MainPalette
import com.kite.zmusic.ui.theme.TextTheme

/** 少于此数量时双行横滑会空、不齐，改单行。 */
private const val WikiDualRowMinCount = 4
private val WikiCoverSize = 86.dp

/**
 * 竖屏黑胶上滑盖住的歌曲百科。舞台色 + onPhoto / player token，跟随插件主题。
 * 顶部为 `<` 顺时针 90° 的向上尖角，点按或系统返回回到黑胶。
 */
@Composable
internal fun PortraitSongWikiOverlay(
    track: TrackRow,
    cookie: String,
    dismissSwipeThresholdPx: Float,
    onClose: () -> Unit,
    onPlayInsertSong: (Long) -> Unit,
    onOpenPlaylist: (Long, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as ZMusicApplication).songRepository
    var loading by remember(track.id) { mutableStateOf(true) }
    var page by remember(track.id) { mutableStateOf<SongWikiPage?>(null) }
    var failed by remember(track.id) { mutableStateOf(false) }

    LaunchedEffect(track.id, cookie) {
        loading = true
        failed = false
        page = null
        val result = runCatching { repo.wiki(track.id, cookie) }
        page = result.getOrNull()
        failed = result.isFailure
        loading = false
    }

    val title = TextTheme.OnPhotoTitle
    val subtitle = TextTheme.OnPhotoSubtitle
    val meta = TextTheme.OnPhotoMeta
    val stage = TextTheme.PlayerStage
    val card = TextTheme.PlayerPlayFill
    val accent = TextTheme.Accent

    Column(
        modifier
            .fillMaxSize()
            .background(stage)
            .nowPlayingBlankGestures(
                dismissThresholdPx = dismissSwipeThresholdPx,
                onTap = null,
                onSwipeDown = onClose,
            ),
    ) {
        Spacer(
            Modifier.height(
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NowPlayingDismissIconButton(
                onClick = onClose,
                chromeBackground = false,
                tint = title,
                pointingUp = true,
            )
            Text(
                text = "百科",
                style = TextStyle(
                    color = title,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    letterSpacing = 0.2.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            loading -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
            failed -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    WikiEmptyHint(text = "百科加载失败", color = meta)
                }
            }
            else -> {
                val wiki = page
                if (wiki == null || wiki.isEmpty) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        WikiEmptyHint(text = "暂无这首歌的百科", color = meta)
                    }
                    return@Column
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 28.dp + WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item(key = "wiki-header-${track.id}") {
                        WikiTrackHeader(
                            track = track,
                            titleColor = title,
                            subtitleColor = subtitle,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    if (wiki.hasInfo) {
                        item(key = "wiki-info-${track.id}") {
                            WikiInfoCard(
                                page = wiki,
                                titleColor = title,
                                bodyColor = subtitle,
                                metaColor = meta,
                                cardColor = card,
                                accent = accent,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                    if (wiki.similar.isNotEmpty()) {
                        item(key = "wiki-similar-${track.id}") {
                            WikiCoverStripSection(
                                title = "相似歌曲",
                                items = wiki.similar,
                                titleColor = title,
                                subtitleColor = subtitle,
                                onOpen = {
                                    onClose()
                                    onPlayInsertSong(it.id)
                                },
                                coverKind = WikiCoverKind.Track,
                            )
                        }
                    }
                    if (wiki.playlists.isNotEmpty()) {
                        item(key = "wiki-playlist-${track.id}") {
                            WikiCoverStripSection(
                                title = "相关歌单",
                                items = wiki.playlists,
                                titleColor = title,
                                subtitleColor = subtitle,
                                onOpen = { onOpenPlaylist(it.id, it.title, it.coverUrl) },
                                coverKind = WikiCoverKind.Playlist,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WikiEmptyHint(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        fontFamily = FontFamily.SansSerif,
    )
}

@Composable
private fun WikiTrackHeader(
    track: TrackRow,
    titleColor: Color,
    subtitleColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UrlImage(
            url = track.coverUrl,
            contentDescription = track.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MainPalette.Placeholder),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                color = titleColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artists.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track.artists,
                    color = subtitleColor,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            track.album?.takeIf { it.isNotBlank() }?.let { album ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = album,
                    color = subtitleColor.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WikiInfoCard(
    page: SongWikiPage,
    titleColor: Color,
    bodyColor: Color,
    metaColor: Color,
    cardColor: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "歌曲信息",
            color = titleColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
        )
        page.facts.forEach { fact ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = fact.label,
                    color = metaColor,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(0.28f),
                )
                Text(
                    text = fact.value,
                    color = bodyColor,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(0.72f),
                )
            }
        }
        if (page.chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                page.chips.forEach { chip ->
                    Text(
                        text = chip,
                        color = accent,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        page.notes.forEach { note ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = note.label,
                    color = metaColor,
                    fontSize = 12.sp,
                )
                Text(
                    text = note.value,
                    color = bodyColor,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
        page.paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                color = bodyColor,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontFamily = FontFamily.SansSerif,
            )
        }
    }
}

private enum class WikiCoverKind { Track, Playlist }

private fun wikiCoverSubtitle(item: SongWikiCoverItem, coverKind: WikiCoverKind): String {
    if (item.subtitle.isNotBlank()) return item.subtitle
    if (coverKind == WikiCoverKind.Playlist && item.playCount > 0L) {
        return "${NcmHomeParse.formatPlayCount(item.playCount)}次播放"
    }
    return " "
}

@Composable
private fun WikiCoverStripSection(
    title: String,
    items: List<SongWikiCoverItem>,
    titleColor: Color,
    subtitleColor: Color,
    onOpen: (SongWikiCoverItem) -> Unit,
    coverKind: WikiCoverKind,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = titleColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        val dual = items.size >= WikiDualRowMinCount
        if (!dual) {
            WikiCoverRow(
                items = items,
                subtitleColor = subtitleColor,
                onOpen = onOpen,
                coverKind = coverKind,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WikiCoverRow(
                    items = items.filterIndexed { index, _ -> index % 2 == 0 },
                    subtitleColor = subtitleColor,
                    onOpen = onOpen,
                    coverKind = coverKind,
                )
                WikiCoverRow(
                    items = items.filterIndexed { index, _ -> index % 2 == 1 },
                    subtitleColor = subtitleColor,
                    onOpen = onOpen,
                    coverKind = coverKind,
                )
            }
        }
    }
}

@Composable
private fun WikiCoverRow(
    items: List<SongWikiCoverItem>,
    subtitleColor: Color,
    onOpen: (SongWikiCoverItem) -> Unit,
    coverKind: WikiCoverKind,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        items(items, key = { it.id }) { item ->
            WikiCoverTile(
                item = item,
                subtitleColor = subtitleColor,
                onOpen = { onOpen(item) },
                coverKind = coverKind,
            )
        }
    }
}

@Composable
private fun WikiCoverTile(
    item: SongWikiCoverItem,
    subtitleColor: Color,
    onOpen: () -> Unit,
    coverKind: WikiCoverKind,
) {
    val target = when (coverKind) {
        WikiCoverKind.Track -> PluginUiTarget.track(
            TrackRow(
                id = item.id,
                name = item.title,
                artists = item.subtitle,
                album = null,
                durationMs = 0L,
                coverUrl = item.coverUrl,
            ),
        )
        WikiCoverKind.Playlist -> PluginUiTarget.playlist(
            id = item.id,
            name = item.title,
            coverUrl = item.coverUrl,
            subtitle = item.subtitle,
        )
    }
    val surface = when (coverKind) {
        WikiCoverKind.Track -> PluginSurfaces.TRACK_COVER
        WikiCoverKind.Playlist -> PluginSurfaces.PLAYLIST_COVER
    }
    Column(
        Modifier
            .width(WikiCoverSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
    ) {
        UrlImage(
            url = item.coverUrl,
            contentDescription = item.title,
            modifier = Modifier
                .size(WikiCoverSize)
                .clip(RoundedCornerShape(10.dp))
                .background(MainPalette.Placeholder)
                .pluginSurface(surface, target),
            contentScale = ContentScale.Crop,
            maxPx = UrlImageCache.THUMB_MAX_PX,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            color = TextTheme.OnPhotoTitle,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = wikiCoverSubtitle(item, coverKind),
            color = subtitleColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
