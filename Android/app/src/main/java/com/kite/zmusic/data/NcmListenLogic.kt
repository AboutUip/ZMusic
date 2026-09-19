package com.kite.zmusic.data

/**
 * 听歌打卡门槛与累计，不依赖 Android 时钟。
 * 短于 40 秒的歌听一半（至少 3 秒）；否则满 20 秒即可上报，不必等切歌。
 */
internal object NcmListenLogic {
    const val MIN_LISTEN_MS = 3_000L
    const val DEFAULT_NEED_MS = 20_000L
    const val SHORT_SONG_MS = 40_000L
    const val MAX_ACCUM_GAP_MS = 2_000L

    fun qualifies(listenedMs: Long, durationMs: Long): Boolean {
        if (listenedMs < MIN_LISTEN_MS) return false
        val need = if (durationMs in 1L until SHORT_SONG_MS) {
            (durationMs / 2L).coerceAtLeast(MIN_LISTEN_MS)
        } else {
            DEFAULT_NEED_MS
        }
        return listenedMs >= need
    }

    fun accumulate(listenedMs: Long, accumAt: Long, now: Long): Long {
        if (accumAt <= 0L || now < accumAt) return listenedMs
        return listenedMs + (now - accumAt).coerceIn(0L, MAX_ACCUM_GAP_MS)
    }

    fun wrapped(
        sameTrack: Boolean,
        lastPos: Long,
        durationMs: Long,
        positionMs: Long,
        listenedMs: Long,
    ): Boolean = sameTrack &&
        lastPos > 0L &&
        durationMs > 0L &&
        lastPos > durationMs * 3 / 4 &&
        positionMs < durationMs / 10 &&
        listenedMs >= 1_000L

    fun shouldSubmit(alreadySubmitted: Boolean, listenedMs: Long, durationMs: Long): Boolean =
        !alreadySubmitted && qualifies(listenedMs, durationMs)
}
