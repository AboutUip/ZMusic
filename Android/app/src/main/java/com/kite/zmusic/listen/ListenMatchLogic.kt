package com.kite.zmusic.listen

const val ListenDeclineCoolMs = 5 * 60 * 1000L

data class ListenMatchGate(
    val matching: Boolean,
    val quietUntilMs: Long,
    val skipUntil: Map<String, Long>,
)

fun listenSkipUntil(nowMs: Long, coolMs: Long = ListenDeclineCoolMs): Long = nowMs + coolMs

fun listenUidSkipped(uid: String, skipUntil: Map<String, Long>, nowMs: Long): Boolean {
    val id = uid.trim()
    if (id.isEmpty()) return false
    val until = skipUntil[id] ?: return false
    return nowMs < until
}

fun listenIncomingVisible(
    invite: ListenInvite?,
    nowMs: Long,
    quietUntilMs: Long,
    skipUntil: Map<String, Long>,
): ListenInvite? {
    if (invite == null || invite.status != "pending") return null
    if (nowMs < quietUntilMs) return null
    if (listenUidSkipped(invite.from.uid, skipUntil, nowMs)) return null
    return invite
}

fun listenActiveSkipUids(skipUntil: Map<String, Long>, nowMs: Long): List<String> =
    skipUntil.mapNotNull { (uid, until) ->
        val id = uid.trim()
        if (id.isNotEmpty() && nowMs < until) id else null
    }

/** 房主取消发出邀请：停止匹配，5 分钟内不再配这个人。 */
fun listenHostCancel(
    peerUid: String,
    nowMs: Long,
    skipUntil: Map<String, Long> = emptyMap(),
): ListenMatchGate = ListenMatchGate(
    matching = false,
    quietUntilMs = 0L,
    skipUntil = listenRememberSkip(peerUid, nowMs, skipUntil),
)

/** 客人拒绝邀请：5 分钟内不弹下一张邀请；当日拒绝的配对封锁由服务端另算。 */
fun listenGuestReject(
    fromUid: String,
    nowMs: Long,
    skipUntil: Map<String, Long> = emptyMap(),
): ListenMatchGate = ListenMatchGate(
    matching = false,
    quietUntilMs = listenSkipUntil(nowMs),
    skipUntil = listenRememberSkip(fromUid, nowMs, skipUntil),
)

fun listenOfferedPeer(
    matching: Boolean,
    candidates: List<String>,
    skipUntil: Map<String, Long>,
    nowMs: Long,
): String? {
    if (!matching) return null
    return candidates.firstOrNull { !listenUidSkipped(it, skipUntil, nowMs) }
}

private fun listenRememberSkip(
    uid: String,
    nowMs: Long,
    skipUntil: Map<String, Long>,
): Map<String, Long> {
    val id = uid.trim()
    if (id.isEmpty()) return skipUntil
    return skipUntil + (id to listenSkipUntil(nowMs))
}
