package com.kite.zmusic.listen

/**
 * 一起听本机播放 → 房间 op 的纯决策。
 * 切歌时 ExoPlayer 会先 pause 旧曲，再换 MediaItem；这两拍若直接上报，
 * 空房间也会把「旧曲曲末暂停」打到新曲上。
 */
data class ListenLocalSnap(
    val trackId: Long = 0L,
    val title: String = "",
    val artists: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playWhenReady: Boolean = false,
    val loadPending: Boolean = false,
    val hasQueue: Boolean = false,
)

data class ListenLocalMemory(
    val lastTrackId: Long = 0L,
    val lastPlayWhenReady: Boolean = false,
    val lastPosMs: Long = 0L,
    val lastPosAt: Long = 0L,
    val lastHasQueue: Boolean = false,
    /** 切歌时被丢掉的旧进度；后续 play/seek 若仍停在附近则当成 0。 */
    val carryOverMs: Long = -1L,
)

data class ListenPostedOp(
    val kind: String,
    val trackId: Long = 0L,
    val title: String = "",
    val artists: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val originMs: Long = 0L,
    val playing: Boolean = true,
) {
    companion object {
        fun track(
            trackId: Long,
            title: String,
            artists: String,
            coverUrl: String,
            durationMs: Long,
            originMs: Long,
            playing: Boolean,
        ) = ListenPostedOp(
            kind = "track",
            trackId = trackId,
            title = title,
            artists = artists,
            coverUrl = coverUrl,
            durationMs = durationMs,
            originMs = originMs,
            playing = playing,
        )

        fun play(trackId: Long, originMs: Long) = ListenPostedOp(
            kind = "play",
            trackId = trackId,
            originMs = originMs,
            playing = true,
        )

        fun pause(trackId: Long, originMs: Long) = ListenPostedOp(
            kind = "pause",
            trackId = trackId,
            originMs = originMs,
            playing = false,
        )

        fun seek(trackId: Long, originMs: Long, playing: Boolean) = ListenPostedOp(
            kind = "seek",
            trackId = trackId,
            originMs = originMs,
            playing = playing,
        )
    }
}

sealed class ListenLocalEffect {
    data object Hold : ListenLocalEffect()
    data object EndHostRoom : ListenLocalEffect()
    data class Post(val op: ListenPostedOp) : ListenLocalEffect()
}

fun decideLocalPlayback(
    inRoom: Boolean,
    applyingRemote: Boolean,
    nowElapsed: Long,
    suppressUntil: Long,
    hosting: Boolean,
    memory: ListenLocalMemory,
    snap: ListenLocalSnap,
): Pair<ListenLocalMemory, ListenLocalEffect> {
    if (!inRoom || applyingRemote || nowElapsed < suppressUntil) {
        return swallowRemoteOrIdle(memory, snap, applyingRemote, nowElapsed, suppressUntil) to
            ListenLocalEffect.Hold
    }
    if (snap.loadPending) {
        return memory.copy(
            lastPosMs = snap.positionMs,
            lastPosAt = nowElapsed,
            lastHasQueue = snap.hasQueue,
        ) to ListenLocalEffect.Hold
    }
    if (hosting && !snap.hasQueue && memory.lastHasQueue) {
        return memory.copy(lastHasQueue = false) to ListenLocalEffect.EndHostRoom
    }
    val queued = memory.copy(lastHasQueue = snap.hasQueue)
    if (snap.trackId > 0L && snap.trackId != queued.lastTrackId) {
        val origin = ListenTogetherClock.originMsForNewTrack(
            previousTrackId = queued.lastTrackId,
            previousPositionMs = queued.lastPosMs,
            newTrackId = snap.trackId,
            positionMs = snap.positionMs,
            durationMs = snap.durationMs,
        )
        val carry = if (origin == 0L && snap.positionMs > ListenTogetherClock.NEW_TRACK_ORIGIN_GRACE_MS) {
            snap.positionMs
        } else {
            -1L
        }
        return queued.copy(
            lastTrackId = snap.trackId,
            lastPlayWhenReady = snap.playWhenReady,
            lastPosMs = snap.positionMs,
            lastPosAt = nowElapsed,
            carryOverMs = carry,
        ) to ListenLocalEffect.Post(
            ListenPostedOp.track(
                trackId = snap.trackId,
                title = snap.title,
                artists = snap.artists,
                coverUrl = snap.coverUrl,
                durationMs = snap.durationMs.coerceAtLeast(0L),
                originMs = origin,
                playing = snap.playWhenReady,
            ),
        )
    }
    val origin = postedOriginMs(queued, snap)
    val carry = nextCarryOver(queued, snap)
    if (snap.playWhenReady != queued.lastPlayWhenReady) {
        return queued.copy(
            lastPlayWhenReady = snap.playWhenReady,
            lastPosMs = snap.positionMs,
            lastPosAt = nowElapsed,
            carryOverMs = carry,
        ) to ListenLocalEffect.Post(
            if (snap.playWhenReady) {
                ListenPostedOp.play(snap.trackId, origin)
            } else {
                ListenPostedOp.pause(snap.trackId, origin)
            },
        )
    }
    val elapsed = if (queued.lastPlayWhenReady) nowElapsed - queued.lastPosAt else 0L
    if (ListenTogetherClock.isSeekJump(queued.lastPosMs, elapsed, snap.positionMs) &&
        !ListenTogetherClock.looksLikeCarryOver(snap.positionMs, queued.carryOverMs)
    ) {
        return queued.copy(
            lastPosMs = snap.positionMs,
            lastPosAt = nowElapsed,
            carryOverMs = carry,
        ) to ListenLocalEffect.Post(
            ListenPostedOp.seek(snap.trackId, origin, snap.playWhenReady),
        )
    }
    return queued.copy(
        lastTrackId = snap.trackId,
        lastPlayWhenReady = snap.playWhenReady,
        lastPosMs = snap.positionMs,
        lastPosAt = nowElapsed,
        carryOverMs = carry,
    ) to ListenLocalEffect.Hold
}

/**
 * 未发出的 op 合成一拍：切歌覆盖旧曲 pause/seek；切歌之后的 pause 若仍是旧 trackId 则丢掉。
 */
fun mergeListenOp(prev: ListenPostedOp?, next: ListenPostedOp): ListenPostedOp {
    if (prev == null) return next
    if (next.kind == "track") return next
    if (prev.kind == "track") {
        if (next.trackId > 0L && prev.trackId > 0L && next.trackId != prev.trackId) {
            return prev
        }
        return when (next.kind) {
            "play" -> prev.copy(playing = true)
            "pause" -> prev.copy(playing = false)
            "seek" -> prev.copy(originMs = next.originMs, playing = next.playing)
            else -> next
        }
    }
    if (prev.trackId > 0L && next.trackId > 0L && prev.trackId != next.trackId) {
        return next
    }
    return next
}

private fun swallowRemoteOrIdle(
    memory: ListenLocalMemory,
    snap: ListenLocalSnap,
    applyingRemote: Boolean,
    nowElapsed: Long,
    suppressUntil: Long,
): ListenLocalMemory {
    val keepQueue = applyingRemote || nowElapsed < suppressUntil
    return memory.copy(
        lastTrackId = snap.trackId,
        lastPlayWhenReady = snap.playWhenReady,
        lastPosMs = snap.positionMs,
        lastPosAt = nowElapsed,
        lastHasQueue = if (keepQueue) memory.lastHasQueue else snap.hasQueue,
        carryOverMs = -1L,
    )
}

private fun postedOriginMs(memory: ListenLocalMemory, snap: ListenLocalSnap): Long {
    val pos = snap.positionMs.coerceAtLeast(0L)
    if (ListenTogetherClock.looksLikeCarryOver(pos, memory.carryOverMs)) return 0L
    return ListenTogetherClock.clampOrigin(pos, snap.durationMs)
}

private fun nextCarryOver(memory: ListenLocalMemory, snap: ListenLocalSnap): Long {
    if (memory.carryOverMs < 0L) return -1L
    val pos = snap.positionMs
    if (pos <= ListenTogetherClock.NEW_TRACK_ORIGIN_GRACE_MS) return -1L
    if (!ListenTogetherClock.looksLikeCarryOver(pos, memory.carryOverMs)) return -1L
    return memory.carryOverMs
}
