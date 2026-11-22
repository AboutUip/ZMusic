package com.kite.zmusic.playback

/**
 * 歌词跟播放器里正在响的那一首，不跟房间时钟，也不跟已经先跳走的队列下标。
 * 进度对齐仍走播放器写进界面的那一个 positionMs；这里只决定歌词属于哪一首。
 */
object LyricPlaybackAlign {
    fun playingTrackId(mediaId: String?): Long =
        mediaId?.toLongOrNull()?.takeIf { it > 0L } ?: 0L

    /** 播放器还没给出曲目时不改歌词。给出之后，歌词必须属于这一首。 */
    fun needsRepair(playingTrackId: Long, lyricTrackId: Long): Boolean {
        if (playingTrackId <= 0L) return false
        return lyricTrackId != playingTrackId
    }

    /**
     * 播放器曲目不在本机队列里时先别清歌词，否则会把还对的词清掉又补不回来。
     */
    fun shouldReplaceLyrics(
        playingTrackId: Long,
        lyricTrackId: Long,
        playingTrackKnown: Boolean,
    ): Boolean = playingTrackKnown && needsRepair(playingTrackId, lyricTrackId)

    /**
     * 播放器已经有曲目时，只允许把这一首的歌词写上界面。
     * 播放器还空着（冷启动）时，才允许先绑队列里的当前曲。
     */
    fun mayBindLyrics(playingTrackId: Long, queueTrackId: Long, lyricTargetId: Long): Boolean {
        if (lyricTargetId <= 0L) return false
        if (playingTrackId > 0L) return playingTrackId == lyricTargetId
        return queueTrackId == lyricTargetId
    }

    /**
     * 歌词和进度条是同一首时，用进度条的时钟。
     * 进度条已经切到下一首、歌词还留在正在响的那首上时，用那首自己的进度。
     */
    fun lyricClockMs(
        positionMs: Long,
        lyricPositionMs: Long,
        lyricTrackId: Long,
        queueTrackId: Long,
    ): Long {
        if (lyricTrackId <= 0L || queueTrackId <= 0L || lyricTrackId == queueTrackId) {
            return positionMs
        }
        return lyricPositionMs
    }

    /**
     * 单曲循环把同一首从后段拉回开头。拖动进度条不走这里：界面进度会先写成落点，
     * 随后的播放器回报前后已经一样，不会被当成播完重播。
     */
    fun sameTrackLooped(
        repeatOne: Boolean,
        previousMs: Long,
        nextMs: Long,
        durationMs: Long,
    ): Boolean {
        if (!repeatOne) return false
        if (nextMs < 0L || previousMs <= nextMs) return false
        if (nextMs > LOOP_RESTART_MAX_MS) return false
        if (previousMs - nextMs < LOOP_RESTART_MIN_JUMP_MS) return false
        if (durationMs > 0L) {
            val tailStartsAt = durationMs - maxOf(LOOP_TAIL_MS, durationMs / 4)
            if (previousMs < tailStartsAt) return false
        } else if (previousMs < LOOP_TAIL_MS) {
            return false
        }
        return true
    }

    private const val LOOP_RESTART_MAX_MS = 1_500L
    private const val LOOP_RESTART_MIN_JUMP_MS = 800L
    private const val LOOP_TAIL_MS = 4_000L
}
