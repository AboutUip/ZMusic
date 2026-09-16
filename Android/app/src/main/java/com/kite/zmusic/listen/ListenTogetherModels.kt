package com.kite.zmusic.listen

data class ListenPlaybackClock(
    val hlc: Long = 0L,
    val actor: String = "",
    val trackId: Long = 0L,
    val title: String = "",
    val artists: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val originMs: Long = 0L,
    val originAt: Long = 0L,
)

data class ListenMember(
    val uid: String,
    val nickname: String,
    val avatarUrl: String,
    val host: Boolean,
)

data class ListenRoomSnapshot(
    val id: String,
    val hostUid: String,
    val maxMembers: Int,
    val members: List<ListenMember>,
    val clock: ListenPlaybackClock,
    val rev: Long,
    val serverNow: Long,
    val closed: Boolean,
    val qrText: String,
)

data class ListenTogetherUi(
    val room: ListenRoomSnapshot? = null,
    val selfUid: String = "",
    val busy: Boolean = false,
    val draftSeats: Int = 2,
    val pendingJoinId: String? = null,
) {
    val inRoom: Boolean get() = room != null && !room.closed
    val hosting: Boolean get() = inRoom && room?.hostUid == selfUid
    val memberCount: Int get() = room?.members?.size ?: 0
}

object ListenTogetherClock {
    const val SEEK_JUMP_MS = 1_800L
    const val DRIFT_MS = 3_000L
    const val DRIFT_CHECK_MS = 8_000L

    fun positionMs(
        clock: ListenPlaybackClock,
        serverNow: Long,
        recvElapsedMs: Long,
        nowElapsedMs: Long,
    ): Long {
        val skewAdjustedNow = serverNow + (nowElapsedMs - recvElapsedMs)
        return interpolate(clock, skewAdjustedNow)
    }

    fun interpolate(clock: ListenPlaybackClock, nowMs: Long): Long {
        var pos = clock.originMs
        if (clock.playing && nowMs > clock.originAt) {
            pos += nowMs - clock.originAt
        }
        if (pos < 0L) return 0L
        if (clock.durationMs > 0L && pos > clock.durationMs) return clock.durationMs
        return pos
    }

    fun shouldApply(nextHlc: Long, appliedHlc: Long): Boolean = nextHlc > appliedHlc

    fun isSeekJump(previous: Long, expectedElapsed: Long, next: Long): Boolean {
        val expected = (previous + expectedElapsed.coerceAtLeast(0L)).coerceAtLeast(0L)
        val delta = kotlin.math.abs(next - expected)
        return delta > SEEK_JUMP_MS
    }
}
