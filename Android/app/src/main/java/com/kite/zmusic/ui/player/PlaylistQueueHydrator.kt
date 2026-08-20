package com.kite.zmusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.PlaylistTrackLoader

/**
 * 全屏播放器曲谱 / 黑胶选歌：按歌单分页缓存把播放队列补到 [demandMinCount]
 *（并始终比当前曲多预留一页，避免切歌卡在首屏 40 首）。
 */
@Composable
fun PlaylistQueueHydrator(
    playlistId: Long?,
    currentIndex: Int,
    loadedCount: Int,
    demandMinCount: Int = 0,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    LaunchedEffect(playlistId, currentIndex, loadedCount, demandMinCount) {
        val pid = playlistId ?: return@LaunchedEffect
        if (pid <= 0L) return@LaunchedEffect
        val want = maxOf(
            demandMinCount,
            currentIndex + PlaylistTrackLoader.PAGE + 8,
        )
        if (want <= loadedCount && app.isSourcePlaylistComplete(pid)) return@LaunchedEffect
        app.hydrateSourcePlaylist(pid, want)
    }
}
