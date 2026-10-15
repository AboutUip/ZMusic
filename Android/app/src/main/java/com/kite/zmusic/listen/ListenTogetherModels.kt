package com.kite.zmusic.listen

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

data class ListenChatMsg(
    val id: Long,
    val uid: String,
    val nickname: String,
    val avatarUrl: String,
    val text: String,
    val at: Long,
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
    val chat: List<ListenChatMsg> = emptyList(),
    val chatIncluded: Boolean = true,
    val chatReads: Map<String, Long> = emptyMap(),
    val chatReadsIncluded: Boolean = false,
)

data class ListenPeer(
    val uid: String,
    val nickname: String,
    val avatarUrl: String,
)

data class ListenInvite(
    val id: String,
    val roomId: String,
    val from: ListenPeer,
    val to: ListenPeer,
    val status: String,
    val expiresAt: Long,
    val expiresIn: Long,
)

data class ListenInviteBox(
    val incoming: ListenInvite? = null,
    val outgoing: ListenInvite? = null,
)

fun listenInviteIsRejected(status: String): Boolean =
    status == "declined" || status == "timeout"

data class ListenTogetherUi(
    val room: ListenRoomSnapshot? = null,
    val selfUid: String = "",
    val busy: Boolean = false,
    val draftSeats: Int = 2,
    val pendingJoinId: String? = null,
    val lastReadChatId: Long = 0L,
    val chatToasts: List<ListenChatToast> = emptyList(),
    val matching: Boolean = false,
    val matchPeer: ListenPeer? = null,
    val incomingInvite: ListenInvite? = null,
    val rejectedInvite: ListenInvite? = null,
    val outgoingPending: Boolean = false,
) {
    val inRoom: Boolean get() = room != null && !room.closed
    val hosting: Boolean get() = inRoom && room?.hostUid == selfUid
    val memberCount: Int get() = room?.members?.size ?: 0
    val unreadChat: Int
        get() = listenUnreadChatCount(room?.chat.orEmpty(), selfUid, lastReadChatId)
}

object ListenTogetherClock {
    const val SEEK_JUMP_MS = 1_800L
    const val DRIFT_MS = 3_000L
    const val DRIFT_CHECK_MS = 8_000L
    /** 切歌后起始进度宽限：大于此值才可能是旧曲带过来的 origin。 */
    const val NEW_TRACK_ORIGIN_GRACE_MS = 2_000L
    const val NEW_TRACK_TAIL_MS = 1_500L

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

    fun isOwnClock(actor: String, selfUid: String): Boolean {
        val self = selfUid.trim()
        return self.isNotEmpty() && actor.trim() == self
    }

    /** 客人跟远端时钟切歌；房主仍用本机队列在曲末推进。 */
    fun followRemoteAdvance(inRoom: Boolean, hosting: Boolean): Boolean = inRoom && !hosting

    /**
     * 谁改了听谁的：自己的回声永远不落地（含 postOp 尚未写回 lastPostedHlc 的新 HLC）。
     * 更旧的时钟丢弃；同 HLC 且本机还没跟上时允许再对齐一次。
     */
    fun takeRemoteClock(
        remoteHlc: Long,
        appliedHlc: Long,
        isMine: Boolean,
        mismatch: Boolean,
        applyingRemote: Boolean,
    ): Boolean {
        if (isMine) return false
        if (remoteHlc < appliedHlc) return false
        if (remoteHlc > appliedHlc) return true
        return mismatch && !applyingRemote
    }

    fun playerNeedsClock(
        clock: ListenPlaybackClock,
        localTrackId: Long,
        localPlaying: Boolean,
    ): Boolean {
        if (clock.trackId <= 0L) return false
        if (localTrackId != clock.trackId) return true
        return localPlaying != clock.playing
    }

    fun isSeekJump(previous: Long, expectedElapsed: Long, next: Long): Boolean {
        val expected = (previous + expectedElapsed.coerceAtLeast(0L)).coerceAtLeast(0L)
        val delta = kotlin.math.abs(next - expected)
        return delta > SEEK_JUMP_MS
    }

    /**
     * 切到另一首歌时，本机进度条上残留的旧曲位置不能当成新曲 origin。
     * 本机切歌总是从头（或 2s 内缓冲起点）起播；中途加入一起听走 playListenTrack，不会走这条本地上报。
     */
    fun originMsForNewTrack(
        previousTrackId: Long,
        previousPositionMs: Long,
        newTrackId: Long,
        positionMs: Long,
        durationMs: Long,
    ): Long {
        val pos = positionMs.coerceAtLeast(0L)
        if (previousTrackId <= 0L || previousTrackId == newTrackId) {
            return clampOrigin(pos, durationMs)
        }
        if (pos <= NEW_TRACK_ORIGIN_GRACE_MS) return pos
        if (durationMs > 0L && pos >= durationMs - NEW_TRACK_TAIL_MS) return 0L
        if (previousPositionMs > NEW_TRACK_ORIGIN_GRACE_MS &&
            kotlin.math.abs(pos - previousPositionMs) <= NEW_TRACK_ORIGIN_GRACE_MS
        ) {
            return 0L
        }
        return 0L
    }

    fun clampOrigin(positionMs: Long, durationMs: Long): Long {
        val pos = positionMs.coerceAtLeast(0L)
        if (durationMs > 0L && pos > durationMs) return durationMs
        return pos
    }

    fun looksLikeCarryOver(positionMs: Long, carryOverMs: Long): Boolean {
        if (carryOverMs < 0L) return false
        return kotlin.math.abs(positionMs - carryOverMs) <= NEW_TRACK_ORIGIN_GRACE_MS
    }
}

/** 播放页头像簇：客人在后，发起人永远叠在最前（右侧最上）。 */
data class ListenAvatarLayout(
    val behind: List<ListenMember>,
    val host: ListenMember?,
    val waitingSlot: Boolean,
    val overflow: Int,
)

fun listenAvatarLayout(
    members: List<ListenMember>,
    maxBehind: Int = 2,
): ListenAvatarLayout {
    val host = members.firstOrNull { it.host } ?: members.firstOrNull()
    if (host == null) {
        return ListenAvatarLayout(emptyList(), null, false, 0)
    }
    val guests = members.filter { it.uid != host.uid }
    val cap = maxBehind.coerceAtLeast(0)
    val behind = guests.take(cap)
    val overflow = (guests.size - behind.size).coerceAtLeast(0)
    return ListenAvatarLayout(
        behind = behind,
        host = host,
        waitingSlot = members.size <= 1,
        overflow = overflow,
    )
}

/** 空房间（仅房主等待加入）结束一起听时不暂停本机播放；有同伴则仍暂停。 */
fun listenShouldPauseOnRoomEnd(memberCount: Int): Boolean = memberCount > 1

fun ListenMember.ncmUserId(): Long? =
    uid.trim().toLongOrNull()?.takeIf { it > 0L }

fun listenChatUidEquals(left: String, right: String): Boolean {
    val a = left.trim()
    val b = right.trim()
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b) return true
    val na = a.toLongOrNull()
    val nb = b.toLongOrNull()
    return na != null && na == nb
}

fun listenChatIsSelf(msg: ListenChatMsg, selfUid: String): Boolean {
    if (msg.id < 0L) return true
    return listenChatUidEquals(msg.uid, selfUid)
}

private val listenChatClockFmt = DateTimeFormatter.ofPattern("HH:mm")
private val listenChatDayFmt = DateTimeFormatter.ofPattern("M/d")

/** 今天只显示 HH:mm，更早的消息带上月/日。 */
fun listenChatClockLabel(
    atMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (atMs <= 0L) return ""
    val msg = Instant.ofEpochMilli(atMs).atZone(zone)
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val clock = msg.format(listenChatClockFmt)
    return if (msg.toLocalDate() == now.toLocalDate()) {
        clock
    } else {
        msg.format(listenChatDayFmt) + " " + clock
    }
}

/**
 * 除发送者以外的在场成员都把已读游标推过这条消息。
 * 房间里只有发送者自己时没有可核对的读者，不显示已读。
 */
fun listenChatReadByPeers(
    msgId: Long,
    senderUid: String,
    members: List<ListenMember>,
    reads: Map<String, Long>,
): Boolean {
    if (msgId <= 0L) return false
    val others = members.filter { it.uid.isNotBlank() && !listenChatUidEquals(it.uid, senderUid) }
    if (others.isEmpty()) return false
    return others.all { peer ->
        val cursor = reads.entries.firstOrNull { listenChatUidEquals(it.key, peer.uid) }?.value ?: 0L
        cursor >= msgId
    }
}

fun mergeListenChatReads(
    previous: Map<String, Long>,
    incoming: Map<String, Long>,
    incomingIncluded: Boolean,
): Map<String, Long> {
    if (!incomingIncluded) return previous
    if (previous.isEmpty()) return incoming
    val out = LinkedHashMap<String, Long>(incoming.size)
    for ((key, value) in incoming) {
        val prev = previous.entries.firstOrNull { listenChatUidEquals(it.key, key) }?.value ?: 0L
        val merged = maxOf(prev, value)
        if (merged > 0L) out[key] = merged
    }
    return out
}

fun retargetListenChatToast(current: ListenChatMsg?, merged: List<ListenChatMsg>): ListenChatMsg? {
    if (current == null) return null
    if (current.id > 0L) {
        return merged.firstOrNull { it.id == current.id } ?: current
    }
    return merged.lastOrNull { remote ->
        remote.id > 0L &&
            listenChatUidEquals(remote.uid, current.uid) &&
            remote.text == current.text
    } ?: current
}

const val LISTEN_CHAT_TOAST_MAX = 2

data class ListenChatToast(
    val key: String,
    val msg: ListenChatMsg,
)

fun listenChatToastKey(msg: ListenChatMsg): String =
    if (msg.id < 0L) "l${msg.id}" else "r${msg.id}"

fun retargetListenChatToasts(
    current: List<ListenChatToast>,
    merged: List<ListenChatMsg>,
): List<ListenChatToast> = current.map { item ->
    item.copy(msg = retargetListenChatToast(item.msg, merged) ?: item.msg)
}

@Suppress("UNUSED_PARAMETER")
fun listenChatKeepToastsWhileReading(
    toasts: List<ListenChatToast>,
    selfUid: String,
): List<ListenChatToast> = emptyList()

fun pushListenChatToasts(
    current: List<ListenChatToast>,
    incoming: List<ListenChatMsg>,
    max: Int = LISTEN_CHAT_TOAST_MAX,
): List<ListenChatToast> {
    if (incoming.isEmpty()) return current
    val out = retargetListenChatToasts(current, incoming).toMutableList()
    for (msg in incoming) {
        if (out.any { it.msg.id == msg.id && msg.id != 0L }) continue
        if (
            msg.id > 0L &&
            out.any {
                it.msg.id < 0L &&
                    listenChatUidEquals(it.msg.uid, msg.uid) &&
                    it.msg.text == msg.text
            }
        ) {
            continue
        }
        out += ListenChatToast(listenChatToastKey(msg), msg)
    }
    return if (out.size <= max) out else out.takeLast(max)
}

fun dismissListenChatToast(
    current: List<ListenChatToast>,
    key: String,
): List<ListenChatToast> = current.filter { it.key != key }

fun dismissListenChatToastById(
    current: List<ListenChatToast>,
    id: Long,
): List<ListenChatToast> = current.filter { it.msg.id != id && it.key != "l$id" && it.key != "r$id" }

@Suppress("UNUSED_PARAMETER")
fun listenChatKeepToastWhileReading(toast: ListenChatMsg?, selfUid: String): ListenChatMsg? = null

fun listenChatOverlayToken(msg: ListenChatMsg): String {
    if (msg.id > 0L) return "r${msg.id}"
    val uid = msg.uid.trim().toLongOrNull()?.toString() ?: msg.uid.trim()
    return "p$uid|${msg.text}"
}

fun listenChatOverlayedTokens(
    msgs: Iterable<ListenChatMsg>,
    current: Set<String> = emptySet(),
): Set<String> = current + msgs.map(::listenChatOverlayToken)

fun listenChatIsOverlayed(msg: ListenChatMsg, overlayed: Set<String>): Boolean {
    if (listenChatOverlayToken(msg) in overlayed) return true
    val uid = msg.uid.trim().toLongOrNull()?.toString() ?: msg.uid.trim()
    return "p$uid|${msg.text}" in overlayed
}

fun listenChatIncomingOverlay(
    msgs: List<ListenChatMsg>,
    overlayed: Set<String>,
): List<ListenChatMsg> = msgs.filterNot { listenChatIsOverlayed(it, overlayed) }

@Suppress("UNUSED_PARAMETER")
fun listenChatSelfToastForClosedSheet(
    lastSelf: ListenChatMsg?,
    current: List<ListenChatToast>,
    overlayed: Set<String>,
    nowMs: Long,
    maxAgeMs: Long = 12_000L,
): ListenChatMsg? = null

fun listenUnreadChatCount(
    chat: List<ListenChatMsg>,
    selfUid: String,
    lastReadId: Long,
): Int {
    val self = selfUid.trim()
    return chat.count { !listenChatUidEquals(it.uid, self) && it.id > lastReadId }
}

fun mergeListenRoomChat(
    previous: List<ListenChatMsg>,
    incoming: List<ListenChatMsg>,
    incomingIncluded: Boolean,
    selfUid: String,
): List<ListenChatMsg> {
    if (!incomingIncluded) return previous
    if (previous.isEmpty()) return incoming
    val self = selfUid.trim()
    val seenIds = HashSet<Long>()
    val out = ArrayList<ListenChatMsg>(incoming.size + previous.size)
    for (msg in incoming) {
        if (msg.id > 0L && !seenIds.add(msg.id)) continue
        out += msg
    }
    val consumed = BooleanArray(incoming.size)
    for (msg in previous) {
        if (msg.id > 0L) {
            if (msg.id in seenIds) continue
            seenIds.add(msg.id)
            out += msg
            continue
        }
        var echoed = false
        for (i in incoming.indices) {
            if (consumed[i]) continue
            val remote = incoming[i]
            val uidHit = listenChatUidEquals(remote.uid, msg.uid) ||
                listenChatUidEquals(remote.uid, self)
            if (uidHit && remote.text == msg.text) {
                consumed[i] = true
                echoed = true
                break
            }
        }
        if (!echoed) out += msg
    }
    return out.sortedWith(compareBy<ListenChatMsg> { if (it.at > 0L) it.at else it.id }.thenBy { it.id })
}

fun listenChatBubbleText(text: String, maxRunes: Int = 20): String {
    val t = listenChatVisibleBody(text).trim()
    if (t.isEmpty() || maxRunes <= 0) return ""
    val n = t.codePointCount(0, t.length)
    if (n <= maxRunes) return t
    val end = t.offsetByCodePoints(0, maxRunes)
    return t.substring(0, end) + "..."
}
