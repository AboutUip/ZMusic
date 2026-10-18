package com.kite.zmusic.listen

/**
 * 聊天室消息翻译（仅本机可见）。
 * 只挂在消息 id 上，相同原文不共用一份。
 * [visible]=false 时仍保留 [text]，避免重复请求；结束一起听后整体清空。
 * [originKey] 记住乐观 id，回声换号之后请求返回仍写到正式消息上。
 */
data class ListenChatTranslateEntry(
    val text: String = "",
    val visible: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val originKey: String = "",
)

fun listenChatTranslateLookup(
    map: Map<String, ListenChatTranslateEntry>,
    msg: ListenChatMsg,
): ListenChatTranslateEntry? = map[listenChatToastKey(msg)]

/**
 * 本地乐观消息 id 变为服务端 id 时，把翻译缓存键迁过去。
 * 同一句原文有多条时按列表顺序一对一占用，不落到更早的气泡上。
 */
fun retargetListenChatTranslations(
    current: Map<String, ListenChatTranslateEntry>,
    previous: List<ListenChatMsg>,
    merged: List<ListenChatMsg>,
): Map<String, ListenChatTranslateEntry> {
    if (current.isEmpty()) return current
    val out = current.toMutableMap()
    val previousIds = previous.map { it.id }.filter { it > 0L }.toSet()
    val consumed = HashSet<Long>()
    for (prev in previous) {
        if (prev.id >= 0L) continue
        val oldKey = listenChatToastKey(prev)
        val entry = out[oldKey] ?: continue
        val remote = claimListenChatEcho(prev, merged, previousIds, consumed) ?: continue
        if (remote.id <= 0L || remote.id == prev.id) continue
        consumed.add(remote.id)
        val newKey = listenChatToastKey(remote)
        out.remove(oldKey)
        out[newKey] = entry.copy(originKey = entry.originKey.ifBlank { oldKey })
    }
    return out
}

fun putListenChatTranslate(
    current: Map<String, ListenChatTranslateEntry>,
    msg: ListenChatMsg,
    entry: ListenChatTranslateEntry,
): Map<String, ListenChatTranslateEntry> {
    val key = listenChatToastKey(msg)
    val prior = current[key]
    val origin = entry.originKey.ifBlank { prior?.originKey.orEmpty() }.ifBlank { key }
    val next = current.toMutableMap()
    next[key] = entry.copy(originKey = origin)
    return next
}

/**
 * 翻译请求返回时写入当前编号。乐观 id 已经迁走就跟着 [originKey] 走。
 * 用户已经藏起的条目只补正文，不再打开。
 */
fun commitListenChatTranslate(
    current: Map<String, ListenChatTranslateEntry>,
    requested: ListenChatMsg,
    entry: ListenChatTranslateEntry,
): Map<String, ListenChatTranslateEntry> {
    val key = listenChatTranslateCommitKey(current, requested)
    val existing = current[key]
    val origin = existing?.originKey?.ifBlank { null }
        ?: entry.originKey.ifBlank { listenChatToastKey(requested) }
    val stamped = entry.copy(originKey = origin)
    if (existing != null && !existing.visible && !existing.loading && stamped.visible) {
        val next = current.toMutableMap()
        next[key] = existing.copy(
            text = stamped.text.ifBlank { existing.text },
            loading = false,
            error = stamped.error,
            visible = false,
            originKey = origin,
        )
        return next
    }
    val next = current.toMutableMap()
    val requestedKey = listenChatToastKey(requested)
    if (requestedKey != key) next.remove(requestedKey)
    next[key] = stamped
    return next
}

fun listenChatTranslateCommitKey(
    current: Map<String, ListenChatTranslateEntry>,
    requested: ListenChatMsg,
): String {
    val direct = listenChatToastKey(requested)
    if (direct in current) return direct
    if (requested.id > 0L) return direct
    return current.entries.firstOrNull { it.value.originKey == direct }?.key ?: direct
}

fun hideListenChatTranslate(
    current: Map<String, ListenChatTranslateEntry>,
    msg: ListenChatMsg,
): Map<String, ListenChatTranslateEntry> {
    val cur = listenChatTranslateLookup(current, msg) ?: return current
    if (!cur.visible && !cur.loading) return current
    return putListenChatTranslate(
        current,
        msg,
        cur.copy(visible = false, loading = false),
    )
}
