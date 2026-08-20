package com.kite.zmusic.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.TrackArtist
import com.kite.zmusic.data.TrackExportException
import com.kite.zmusic.data.TrackExportOptions
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.artist.resolveTrackArtists
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.notice.showIslandNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun TrackOverflowMenu(
    track: TrackRow?,
    canRemove: Boolean,
    onDismiss: () -> Unit,
    onDownload: (TrackRow, TrackExportOptions) -> Unit,
    onRemove: (TrackRow) -> Unit,
    removeConfirmTitle: String = "从歌单移除这首歌？",
    removeConfirmMessage: String = "这首歌会从当前歌单里拿掉，不会删除已下载的文件。",
    currentPlaylistId: Long = 0L,
    showAddToPlaylist: Boolean = true,
    onOpenArtist: ((Long, String, String?) -> Unit)? = null,
) {
    var confirmRemove by remember(track?.id) { mutableStateOf(false) }
    var pickingPlaylist by remember(track?.id) { mutableStateOf(false) }
    var pickingArtist by remember(track?.id) { mutableStateOf(false) }
    var pickingExport by remember(track?.id) { mutableStateOf(false) }
    var artistChoices by remember(track?.id) { mutableStateOf<List<TrackArtist>>(emptyList()) }
    val current = track ?: return
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by app.playlistCollectionRepository.playlists.collectAsStateWithLifecycle()
    when {
        confirmRemove -> {
            GlassAlertDialog(
                title = removeConfirmTitle,
                message = removeConfirmMessage,
                confirmLabel = "删除",
                confirmDestructive = true,
                onConfirm = {
                    onRemove(current)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        pickingExport -> {
            TrackExportOptionsDialog(
                title = "下载",
                message = current.name,
                onConfirm = { options ->
                    onDownload(current, options)
                    onDismiss()
                },
                onDismiss = { pickingExport = false },
            )
        }
        pickingArtist -> {
            GlassActionSheet(
                title = "查看歌手",
                message = current.name,
                coverUrl = current.coverUrl,
                contentKey = "pick-artist-${current.id}",
                onDismiss = onDismiss,
                actions = artistChoices.map { a ->
                    GlassSheetAction(a.name) {
                        onOpenArtist?.invoke(a.id, a.name, current.coverUrl)
                        onDismiss()
                    }
                },
            )
        }
        pickingPlaylist -> {
            val targets = playlists
                .filter { it.isOwned && it.id != currentPlaylistId }
                .sortedWith(
                    compareByDescending<PlaylistSummary> { it.isHeartPlaylist }
                        .thenBy { it.name },
                )
            GlassActionSheet(
                title = "添加到歌单",
                message = if (targets.isEmpty()) {
                    "先在个人页创建歌单"
                } else {
                    current.name
                },
                coverUrl = current.coverUrl,
                contentKey = "add-to-playlist-${current.id}",
                onDismiss = onDismiss,
                actions = targets.map { pl ->
                    GlassSheetAction(
                        label = pl.name,
                        coverUrl = pl.resolvedCoverUrl(),
                        showCover = true,
                    ) {
                        scope.launch {
                            val msg = app.playlistEditor.addTrack(pl, current)
                            context.showIslandNotice(msg, current.coverUrl)
                            onDismiss()
                        }
                    }
                },
            )
        }
        else -> {
            GlassActionSheet(
                title = current.name,
                message = current.artists,
                coverUrl = current.coverUrl,
                contentKey = "track-overflow-${current.id}",
                onDismiss = onDismiss,
                actions = buildList {
                    add(
                        GlassSheetAction("下载") {
                            pickingExport = true
                        },
                    )
                    if (showAddToPlaylist) {
                        add(
                            GlassSheetAction("添加到歌单") {
                                pickingPlaylist = true
                            },
                        )
                    }
                    if (onOpenArtist != null) {
                        add(
                            GlassSheetAction("查看歌手") {
                                scope.launch {
                                    val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
                                    val found = resolveTrackArtists(current, cookie, app.songRepository)
                                    when {
                                        found.isEmpty() -> {
                                            context.showIslandNotice("暂时无法打开这位歌手", current.coverUrl)
                                            onDismiss()
                                        }
                                        found.size == 1 -> {
                                            val a = found.first()
                                            onOpenArtist(a.id, a.name, current.coverUrl)
                                            onDismiss()
                                        }
                                        else -> {
                                            artistChoices = found
                                            pickingArtist = true
                                        }
                                    }
                                }
                            },
                        )
                    }
                    if (canRemove) {
                        add(
                            GlassSheetAction("删除", destructive = true) {
                                confirmRemove = true
                            },
                        )
                    }
                },
            )
        }
    }
}

internal fun launchTrackDownload(
    scope: CoroutineScope,
    app: ZMusicApplication,
    track: TrackRow,
    options: TrackExportOptions,
) {
    scope.launch {
        downloadOneTrack(app, track, options)
    }
}

internal suspend fun launchTrackDownloads(
    app: ZMusicApplication,
    tracks: List<TrackRow>,
    options: TrackExportOptions,
) {
    if (tracks.isEmpty()) return
    var ok = 0
    tracks.forEachIndexed { i, track ->
        app.islandNoticeCenter.show("正在下载 ${i + 1}/${tracks.size}", track.coverUrl)
        if (downloadOneTrack(app, track, options, notify = false)) ok++
    }
    app.islandNoticeCenter.show(
        if (ok == tracks.size) "已保存 ${ok} 首到 Download/ZMusic"
        else "已保存 ${ok}/${tracks.size} 首",
        tracks.lastOrNull()?.coverUrl,
    )
}

private suspend fun downloadOneTrack(
    app: ZMusicApplication,
    track: TrackRow,
    options: TrackExportOptions,
    notify: Boolean = true,
): Boolean {
    if (notify) {
        app.islandNoticeCenter.show("正在下载 ${track.name}", track.coverUrl)
    }
    val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
    if (cookie.isBlank()) {
        app.islandNoticeCenter.show("请先登录", track.coverUrl)
        return false
    }
    return runCatching { app.trackExportRepository.export(track, cookie, options) }
        .onSuccess {
            if (notify) {
                app.islandNoticeCenter.show("已保存到 Download/ZMusic", track.coverUrl)
            }
        }
        .onFailure { e ->
            val msg = (e as? TrackExportException)?.message?.takeIf { it.isNotBlank() }
                ?: "下载失败"
            app.islandNoticeCenter.show(msg, track.coverUrl)
        }
        .isSuccess
}
