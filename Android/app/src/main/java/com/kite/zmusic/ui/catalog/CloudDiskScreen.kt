package com.kite.zmusic.ui.catalog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassSheetAction
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.main.MainPalette

@Composable
internal fun CloudDiskScreen(
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onPushOverlay: (MainOverlay) -> Unit,
    playingTrackId: Long,
    playingSourceId: Long,
    isPlaying: Boolean,
    manageBridge: PlaylistManageBridge?,
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
    LaunchedEffect(Unit) { vm.load() }
    val ui by vm.list.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) vm.upload(uris)
    }
    TrackCollectionScreen(
        state = ui,
        contentBottomInset = contentBottomInset,
        onBack = onBack,
        onPlayAt = { i ->
            if (ui.tracks.isNotEmpty()) {
                onPlayTracks(ui.tracks, i, null, "音乐云盘")
            }
        },
        onRetry = { vm.load(force = true) },
        extraActionLabel = "上传",
        extraActionIcon = ZIcons.Add,
        onExtraAction = {
            picker.launch(arrayOf("audio/*", "audio/mpeg", "audio/flac", "audio/mp4", "audio/x-wav"))
        },
        onLoadMore = vm::loadMore,
        playingTrackId = playingTrackId,
        playingSourceId = playingSourceId,
        isPlaying = isPlaying,
        onSearch = { onPushOverlay(MainOverlay.CloudDiskSearch) },
        searchContentDescription = "搜索云盘歌曲",
        onRemoveTrack = vm::removeTrack,
        onRemoveTracks = vm::removeTracks,
        manageBridge = manageBridge,
        onOpenArtist = onOpenArtist,
        showSaveToCloud = false,
        onOverflowExtras = { track -> cloudOverflowExtras(track, vm, onPushOverlay) },
        removeConfirmTitle = "从云盘删除？",
        removeConfirmMessage = "会从网易云云盘删掉，不可恢复。",
        removeSelectedTitle = "从云盘删除这些歌？",
        removeSelectedMessage = "将从云盘删除已选歌曲，不可恢复。",
        removeSelectedConfirmLabel = "删除",
        emptyHint = "还没有云盘歌曲。点右上角上传本地音频，或在歌曲菜单里选择保存到云盘。",
    )
    CloudLyricDialog(vm)
}

internal fun cloudOverflowExtras(
    track: TrackRow,
    vm: CloudDiskViewModel,
    onPushOverlay: (MainOverlay) -> Unit,
): List<GlassSheetAction> = buildList {
    add(
        GlassSheetAction("匹配歌曲信息") {
            onPushOverlay(MainOverlay.CloudMatch(track.id, track.name, track.artists))
        },
    )
    if (vm.matched(track.id)) {
        add(GlassSheetAction("取消匹配") { vm.unmatch(track) })
    }
    add(GlassSheetAction("文件歌词") { vm.showLyric(track) })
}

@Composable
internal fun CloudLyricDialog(vm: CloudDiskViewModel) {
    val lyric by vm.lyric.collectAsStateWithLifecycle()
    lyric?.let { (title, text) ->
        GlassAlertDialog(
            title = title,
            confirmLabel = "好",
            onConfirm = { vm.consumeLyric() },
            onDismiss = { vm.consumeLyric() },
            extraContent = {
                Text(
                    text = text,
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    ),
                )
            },
        )
    }
}
