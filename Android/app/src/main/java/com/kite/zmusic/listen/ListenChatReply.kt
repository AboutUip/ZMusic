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

private const val QUOTE_STICKER_MARK = "\u200Bstk:"

fun listenChatQuoteFromMsg(msg: ListenChatMsg): ListenChatReplyQuote {
    return ListenChatReplyQuote(
        msgId = msg.id,
        uid = msg.uid,
        nickname = msg.nickname.ifBlank { msg.uid },
        snippet = listenChatQuoteSnippet(msg.text),
    )
}

/** 引用条正文。表情包把 id 藏在零宽标记后，界面画图，其它端仍能读到「表情包」。 */
fun listenChatQuoteSnippet(raw: String): String {
    val body = parseListenChatText(raw).body.ifBlank { raw }
    val pack = parseListenChatPack(body.trim())
    if (pack?.kind == ListenChatPackKind.Sticker) {
        return "表情包$QUOTE_STICKER_MARK${pack.value}"
    }
    val visible = if (pack != null) listenChatPackLabel(pack) else body
    return visible.lineSequence().firstOrNull().orEmpty().trim().take(120)
}

/** 长按这条消息时可以收进表情包的 id。正文优先，其次是引用里的表情包。 */
fun listenChatActionStickerId(raw: String): String? {
    val parsed = parseListenChatText(raw)
    val body = parsed.body.ifBlank { raw }
    parseListenChatPack(body.trim())
        ?.takeIf { it.kind == ListenChatPackKind.Sticker }
        ?.let { return it.value }
    return parsed.reply?.snippet?.let(::listenChatQuoteStickerId)
}

fun listenChatQuoteStickerId(snippet: String): String? {
    val mark = snippet.indexOf(QUOTE_STICKER_MARK)
    if (mark < 0) return null
    val id = snippet.substring(mark + QUOTE_STICKER_MARK.length)
        .takeWhile { it.isLetterOrDigit() || it == '_' || it == '-' }
    return id.takeIf(ListenStickerIdPattern::matches)
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

enum class ListenChatPackKind { Dice, Rps, Sticker }

data class ListenChatPack(
    val kind: ListenChatPackKind,
    val value: String,
)

data class ListenSticker(
    val id: String,
    val mime: String,
    val animated: Boolean,
)

/** 与服务端 ListenStickerMaxBytes 一致。超过的文件不上传。 */
const val ListenStickerMaxBytes = 4 * 1024 * 1024

private val ListenStickerIdPattern = Regex("^[A-Za-z0-9_-]{16,64}$")

fun rollListenChatPack(kind: ListenChatPackKind): String = when (kind) {
    ListenChatPackKind.Dice -> (1..6).random().toString()
    ListenChatPackKind.Rps -> listOf("rock", "scissors", "paper").random()
    ListenChatPackKind.Sticker -> ""
}

fun encodeListenChatPack(kind: ListenChatPackKind, value: String = rollListenChatPack(kind)): String {
    val token = when (kind) {
        ListenChatPackKind.Dice -> "dice"
        ListenChatPackKind.Rps -> "rps"
        ListenChatPackKind.Sticker -> return encodeListenChatSticker(value)
    }
    return "$PACK_PREFIX$token|$value"
}

fun encodeListenChatSticker(id: String): String {
    val clean = id.trim()
    require(ListenStickerIdPattern.matches(clean))
    return "${PACK_PREFIX}sticker|$clean"
}

internal fun listenChatStickerIdOk(id: String): Boolean = ListenStickerIdPattern.matches(id.trim())

/** 只认文件头。真正的解码在服务端，这里用来挡住明显不是图片的上传。 */
fun listenChatStickerMime(bytes: ByteArray): String? {
    if (bytes.size < 12) return null
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte() && bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
        bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
    ) {
        return "image/png"
    }
    val gif = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
    if (gif == "GIF87a" || gif == "GIF89a") return "image/gif"
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
        return "image/jpeg"
    }
    if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
    ) {
        return "image/webp"
    }
    return null
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
        "sticker" -> parts[1].trim().takeIf(ListenStickerIdPattern::matches)?.let {
            ListenChatPack(ListenChatPackKind.Sticker, it)
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
    ListenChatPackKind.Sticker -> "🎭"
}

fun listenChatPackCaption(pack: ListenChatPack): String = when (pack.kind) {
    ListenChatPackKind.Dice -> pack.value
    ListenChatPackKind.Rps -> when (pack.value) {
        "rock" -> "石头"
        "scissors" -> "剪刀"
        else -> "布"
    }
    ListenChatPackKind.Sticker -> ""
}

fun listenChatPackLabel(pack: ListenChatPack): String = when (pack.kind) {
    ListenChatPackKind.Dice -> "🎲 ${pack.value}"
    ListenChatPackKind.Rps -> "${listenChatPackGlyph(pack)} ${listenChatPackCaption(pack)}"
    ListenChatPackKind.Sticker -> "表情包"
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
        ListenChatPackKind.Sticker -> result
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
