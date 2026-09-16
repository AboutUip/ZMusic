package com.kite.zmusic.data

/**
 * 一起听邀请码：`ZMLISTEN1:{roomId}`，也认 `zmusic://listen/{id}`。
 */
internal object ZMusicListenLink {
    const val PREFIX = "ZMLISTEN1:"
    const val SCHEME = "zmusic"
    const val HOST = "listen"
    const val ID_LEN = 22
    private val IdChars = Regex("^[A-Za-z0-9_-]+$")

    fun format(roomId: String): String = PREFIX + roomId.trim()

    fun deeplink(roomId: String): String = "$SCHEME://$HOST/${roomId.trim()}"

    fun parse(raw: String): String? {
        val text = raw.trim().trim('\uFEFF')
        if (text.isEmpty()) return null
        idAfterPrefix(text)?.let { return it }
        val lower = text.lowercase()
        val scheme = "$SCHEME://$HOST/"
        val idx = lower.indexOf(scheme)
        if (idx >= 0) {
            val rest = text.substring(idx + scheme.length)
                .substringBefore('?')
                .substringBefore('#')
                .substringBefore('/')
                .trim()
            return rest.takeIf(::validId)
        }
        val roomIdx = lower.indexOf("room=")
        if (roomIdx >= 0) {
            val rest = text.substring(roomIdx + 5)
                .substringBefore('&')
                .substringBefore('#')
                .trim()
            return rest.takeIf(::validId)
        }
        return null
    }

    fun validId(id: String): Boolean =
        id.length == ID_LEN && IdChars.matches(id)

    private fun idAfterPrefix(source: String): String? {
        val idx = source.indexOf(PREFIX, ignoreCase = true)
        if (idx < 0) return null
        val id = source.substring(idx + PREFIX.length)
            .trim()
            .substringBefore('&')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore('/')
            .trim()
        return id.takeIf(::validId)
    }
}
