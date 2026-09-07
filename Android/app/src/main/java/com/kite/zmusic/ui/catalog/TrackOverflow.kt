package com.kite.zmusic.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kite.zmusic.data.TrackExportLog
import com.kite.zmusic.data.TrackExportOptions
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.artist.resolveTrackArtists
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.plugin.PluginSurfaces
import com.kite.zmusic.plugin.PluginUiTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
internal fun TrackOverflowMenu(
    track: TrackRow?,
    canRemove: Boolean,
    onDismiss: () -> Unit,
    onDownload: (TrackRow, TrackExportOptions) -> Unit,
    onRemove: (TrackRow) -> Unit,
    showDownload: Boolean = true,
    removeConfirmTitle: String = "从歌单移除这首歌？",
    removeConfirmMessage: String = "这首歌会从当前歌单里拿掉，不会删除已下载的文件。",
    currentPlaylistId: Long = 0L,
    showAddToPlaylist: Boolean = true,
    showSaveToCloud: Boolean = showAddToPlaylist,
    extraActions: List<GlassSheetAction> = emptyList(),
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
    val pluginActions by app.pluginEngine.ui.actions.collectAsStateWithLifecycle()
    val overflowPlugin = remember(pluginActions, current.id) {
        pluginActions.filter { it.surface == PluginSurfaces.TRACK_OVERFLOW }.take(8)
    }
    val trackTarget = remember(current.id, current.coverUrl, current.name, current.artists) {
        PluginUiTarget.track(current)
    }
    LaunchedEffect(current.id) {
        app.pluginEngine.emitUiMenu(PluginSurfaces.TRACK_OVERFLOW, trackTarget)
    }
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
                    if (showDownload) {
                        add(
                            GlassSheetAction("下载") {
                                pickingExport = true
                            },
                        )
                    }
                    if (showAddToPlaylist) {
                        add(
                            GlassSheetAction("添加到歌单") {
                                pickingPlaylist = true
                            },
                        )
                    }
                    if (showSaveToCloud) {
                        add(
                            GlassSheetAction("保存到云盘") {
                                scope.launch {
                                    val msg = app.cloudDiskRepository.importPublicTrack(current)
                                    context.showIslandNotice(msg, current.coverUrl)
                                    onDismiss()
                                }
                            },
                        )
                    }
                    extraActions.forEach { action ->
                        add(
                            GlassSheetAction(
                                label = action.label,
                                destructive = action.destructive,
                                coverUrl = action.coverUrl,
                                showCover = action.showCover,
                            ) {
                                action.onClick()
                                onDismiss()
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
                    overflowPlugin.forEach { action ->
                        add(
                            GlassSheetAction(
                                label = action.title,
                                destructive = action.destructive,
                            ) {
                                app.pluginEngine.ui.activateAction(
                                    action.pluginId,
                                    action.surface,
                                    action.id,
                                    trackTarget.toMap(),
                                )
                                onDismiss()
                            },
                        )
                    }
                },
            )
        }
    }
}

private val trackExportIslandLock = Mutex()

internal fun launchTrackDownload(
    app: ZMusicApplication,
    track: TrackRow,
    options: TrackExportOptions,
) {
    app.appScope.launch {
        trackExportIslandLock.withLock {
            downloadOneTrack(app, track, options)
        }
    }
}

internal suspend fun launchTrackDownloads(
    app: ZMusicApplication,
    tracks: List<TrackRow>,
    options: TrackExportOptions,
) {
    if (tracks.isEmpty()) return
    trackExportIslandLock.withLock {
        var ok = 0
        try {
            tracks.forEachIndexed { i, track ->
                if (downloadOneTrack(
                        app,
                        track,
                        options,
                        notify = false,
                        index = i + 1,
                        totalTracks = tracks.size,
                    )
                ) {
                    ok++
                }
            }
        } finally {
            app.islandNoticeCenter.clearSticky()
        }
        app.islandNoticeCenter.show(
            if (ok == tracks.size) "已保存 ${ok} 首到 Download/ZMusic"
            else "已保存 ${ok}/${tracks.size} 首",
            tracks.lastOrNull()?.coverUrl,
        )
    }
}

private fun exportStickyMessage(
    name: String,
    received: Long,
    total: Long,
    index: Int,
    totalTracks: Int,
): String {
    val head = if (totalTracks > 1) {
        "正在下载 $index/$totalTracks · $name"
    } else {
        "正在下载 $name"
    }
    if (total > 0L) {
        val pct = ((received * 100L) / total).toInt().coerceIn(0, 100)
        return "$head · $pct%"
    }
    return head
}

private suspend fun downloadOneTrack(
    app: ZMusicApplication,
    track: TrackRow,
    options: TrackExportOptions,
    notify: Boolean = true,
    index: Int = 1,
    totalTracks: Int = 1,
): Boolean {
    TrackExportLog.i(
        "ui start id=${track.id} name=${track.name} q=${options.quality.level} " +
            "cover=${options.includeCover} lyrics=${options.includeLyrics} " +
            "meta=${options.includeMetadata}",
    )
    val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
    if (cookie.isBlank()) {
        TrackExportLog.w("ui no cookie id=${track.id}")
        app.islandNoticeCenter.show("请先登录", track.coverUrl)
        return false
    }
    val notices = app.islandNoticeCenter
    var lastPct = -1
    fun publish(received: Long, total: Long) {
        val pct = if (total > 0L) {
            ((received * 100L) / total).toInt().coerceIn(0, 100)
        } else {
            -1
        }
        if (pct == lastPct) return
        lastPct = pct
        notices.setSticky(
            exportStickyMessage(track.name, received, total, index, totalTracks),
            track.coverUrl,
        )
    }
    notices.setSticky(
        exportStickyMessage(track.name, 0L, 0L, index, totalTracks),
        track.coverUrl,
    )
    return try {
        val folder = app.trackExportRepository.export(
            track,
            cookie,
            options,
            onAudioProgress = { received, total -> publish(received, total) },
        )
        TrackExportLog.i("ui ok id=${track.id} folder=$folder")
        if (notify) {
            notices.clearSticky()
            notices.show("已保存到 Download/ZMusic", track.coverUrl)
        }
        true
    } catch (e: CancellationException) {
        notices.clearSticky()
        throw e
    } catch (e: Exception) {
        val msg = (e as? TrackExportException)?.message?.takeIf { it.isNotBlank() }
            ?: "下载失败"
        TrackExportLog.e("ui fail id=${track.id} notice=$msg", e)
        if (notify) {
            notices.clearSticky()
            notices.show(msg, track.coverUrl)
        }
        false
    }
}
