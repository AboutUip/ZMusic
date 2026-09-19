package com.kite.zmusic.playback

/**
 * 切歌时 UI 已经指向新曲，ExoPlayer 仍可能对旧 MediaItem 回 STATE_READY。
 * 这时若清掉 loadPending 并写回 currentPosition，进度条会闪到旧曲 80%，一起听也会把该进度当 origin。
 */
object PlaybackLoadGate {
    fun mediaMatchesTrack(mediaId: String?, trackId: Long?): Boolean {
        if (trackId == null || trackId <= 0L) return mediaId.isNullOrBlank()
        return mediaId == trackId.toString()
    }

    fun canCommitReady(loadPending: Boolean, mediaId: String?, trackId: Long?): Boolean {
        if (!loadPending) return true
        return mediaMatchesTrack(mediaId, trackId)
    }
}
