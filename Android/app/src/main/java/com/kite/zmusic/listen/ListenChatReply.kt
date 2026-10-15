package com.kite.zmusic.listen

/**
 * 一起听聊天「回复」仅走 UI + 文本嵌入（社区接口暂无 reply_to）。
 * 本客户端能解析并画 Telegram 风引用条；其它端仍可读正文。
 */
data class ListenChatReplyQuote(
    val msgId: Long,
    val uid: String,
    val nickname: String,
    val snippet: String,
)

data class ListenChatParsedText(
    val reply: ListenChatReplyQuote?,
    val body: String,
)

/** 行首标记：零宽 + 可打印前缀，降低被当正文展示的概率。 */
private const val REPLY_LINE_PREFIX = "\u200B\u200BZM1|"

fun listenChatQuoteFromMsg(msg: ListenChatMsg): ListenChatReplyQuote {
    val visible = listenChatVisibleBody(msg.text)
    return ListenChatReplyQuote(
        msgId = msg.id,
        uid = msg.uid,
        nickname = msg.nickname.ifBlank { msg.uid },
        snippet = visible.lineSequence().firstOrNull().orEmpty().trim().take(120),
    )
}

fun encodeListenChatText(reply: ListenChatReplyQuote?, body: String): String {
    val text = body.trimEnd()
    if (reply == null) return text
    val nick = sanitizeReplyField(reply.nickname).ifBlank { reply.uid }
    val snip = sanitizeReplyField(reply.snippet).ifBlank { "…" }
    val header = buildString {
        append(REPLY_LINE_PREFIX)
        append(reply.msgId)
        append('|')
        append(sanitizeReplyField(reply.uid))
        append('|')
        append(nick)
        append('|')
        append(snip)
    }
    return if (text.isEmpty()) header else "$header\n$text"
}

fun parseListenChatText(raw: String): ListenChatParsedText {
    if (raw.isEmpty()) return ListenChatParsedText(null, raw)
    val firstNl = raw.indexOf('\n')
    val firstLine = if (firstNl >= 0) raw.substring(0, firstNl) else raw
    if (!firstLine.startsWith(REPLY_LINE_PREFIX)) {
        return ListenChatParsedText(null, raw)
    }
    val payload = firstLine.removePrefix(REPLY_LINE_PREFIX)
    val parts = payload.split('|', limit = 4)
    if (parts.size < 4) return ListenChatParsedText(null, raw)
    val id = parts[0].toLongOrNull() ?: return ListenChatParsedText(null, raw)
    val body = if (firstNl >= 0) raw.substring(firstNl + 1) else ""
    return ListenChatParsedText(
        reply = ListenChatReplyQuote(
            msgId = id,
            uid = parts[1],
            nickname = parts[2].ifBlank { parts[1] },
            snippet = parts[3],
        ),
        body = body,
    )
}

/** 复制 / 翻译 / 气泡截断用可见正文。 */
fun listenChatVisibleBody(raw: String): String {
    val body = parseListenChatText(raw).body.ifBlank { raw }
    val pack = parseListenChatPack(body)
    return if (pack != null) listenChatPackLabel(pack) else body
}

private const val PACK_PREFIX = "\u200B\u200BZMpack|"

enum class ListenChatPackKind { Dice, Rps }

data class ListenChatPack(
    val kind: ListenChatPackKind,
    val value: String,
)

fun rollListenChatPack(kind: ListenChatPackKind): String = when (kind) {
    ListenChatPackKind.Dice -> (1..6).random().toString()
    ListenChatPackKind.Rps -> listOf("rock", "scissors", "paper").random()
}

fun encodeListenChatPack(kind: ListenChatPackKind, value: String = rollListenChatPack(kind)): String {
    val token = when (kind) {
        ListenChatPackKind.Dice -> "dice"
        ListenChatPackKind.Rps -> "rps"
    }
    return "$PACK_PREFIX$token|$value"
}

fun parseListenChatPack(body: String): ListenChatPack? {
    if (!body.startsWith(PACK_PREFIX)) return null
    val parts = body.removePrefix(PACK_PREFIX).split('|', limit = 2)
    if (parts.size < 2) return null
    return when (parts[0]) {
        "dice" -> parts[1].toIntOrNull()?.takeIf { it in 1..6 }?.let {
            ListenChatPack(ListenChatPackKind.Dice, it.toString())
        }
        "rps" -> parts[1].takeIf { it == "rock" || it == "scissors" || it == "paper" }?.let {
            ListenChatPack(ListenChatPackKind.Rps, it)
        }
        else -> null
    }
}

fun listenChatPackGlyph(pack: ListenChatPack): String = when (pack.kind) {
    ListenChatPackKind.Dice -> when (pack.value) {
        "1" -> "⚀"
        "2" -> "⚁"
        "3" -> "⚂"
        "4" -> "⚃"
        "5" -> "⚄"
        else -> "⚅"
    }
    ListenChatPackKind.Rps -> when (pack.value) {
        "rock" -> "✊"
        "scissors" -> "✌️"
        else -> "✋"
    }
}

fun listenChatPackCaption(pack: ListenChatPack): String = when (pack.kind) {
    ListenChatPackKind.Dice -> pack.value
    ListenChatPackKind.Rps -> when (pack.value) {
        "rock" -> "石头"
        "scissors" -> "剪刀"
        else -> "布"
    }
}

fun listenChatPackLabel(pack: ListenChatPack): String = when (pack.kind) {
    ListenChatPackKind.Dice -> "🎲 ${pack.value}"
    ListenChatPackKind.Rps -> "${listenChatPackGlyph(pack)} ${listenChatPackCaption(pack)}"
}

internal data class ListenChatPackPose(
    val rotation: Float,
    val scaleX: Float,
    val scaleY: Float,
    val edgeOn: Boolean,
)

/**
 * 播放过程里的可见面。过了 [settleAt] 就停在掷出的结果上，之前只是翻面。
 * 预览把 settleAt 放到 1 以上，就会一直转、不落地。
 */
internal fun listenChatPackMotionValue(
    kind: ListenChatPackKind,
    progress: Float,
    result: String,
    settleAt: Float = 0.84f,
): String {
    if (progress >= settleAt) return result
    val t = (progress / settleAt).coerceIn(0f, 0.999f)
    return when (kind) {
        ListenChatPackKind.Dice -> ((t * 10f).toInt() % 6 + 1).toString()
        ListenChatPackKind.Rps -> listOf("rock", "scissors", "paper")[(t * 9f).toInt() % 3]
    }
}

/** 翻面靠横向压扁到接近 0，压到最薄时看成骰子的侧棱。落地再轻轻回弹。 */
internal fun listenChatPackPose(progress: Float, settleAt: Float = 0.84f): ListenChatPackPose {
    if (progress >= 1f) return ListenChatPackPose(0f, 1f, 1f, false)
    if (progress >= settleAt) {
        val land = ((progress - settleAt) / (1f - settleAt)).coerceIn(0f, 1f)
        val bounce = kotlin.math.sin(land * Math.PI).toFloat() * (1f - land)
        return ListenChatPackPose(
            rotation = (1f - land) * 8f,
            scaleX = 1f + bounce * 0.16f,
            scaleY = 1f - bounce * 0.05f,
            edgeOn = false,
        )
    }
    val turns = progress / settleAt * 4.5f * Math.PI.toFloat()
    val scaleX = kotlin.math.abs(kotlin.math.cos(turns)).coerceIn(0.07f, 1f)
    val fade = 1f - progress / settleAt
    return ListenChatPackPose(
        rotation = kotlin.math.sin(turns * 0.7f) * 16f * fade,
        scaleX = scaleX,
        scaleY = 1f,
        edgeOn = scaleX < 0.22f,
    )
}

private fun sanitizeReplyField(value: String): String =
    value.replace('\n', ' ')
        .replace('\r', ' ')
        .replace('|', '/')
        .trim()
        .take(120)
