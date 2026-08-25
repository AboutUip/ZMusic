package com.kite.zmusic.plugin

/**
 * 宿主给钩子用的当前快照。
 */
data class PluginHostFacts(
    val online: Boolean,
    val dark: Boolean,
    val loggedIn: Boolean,
    val foreground: Boolean = false,
)

internal object PluginHookEvents {
    const val APP_ONLINE = "app.online"
    const val APP_OFFLINE = "app.offline"
    const val APP_APPEARANCE = "app.appearance"
    const val USER_SESSION = "user.session"
    const val PLAYER_TRACK = "player.track"
    const val PLAYER_STATE = "player.state"
    const val PLAYER_QUEUE = "player.queue"
    const val PLAYER_LIKED = "player.liked"
    const val APP_FOREGROUND = "app.foreground"
    const val APP_BACKGROUND = "app.background"
    const val UI_PRESS = "ui.press"
    const val UI_LONG_PRESS = "ui.longPress"
    const val UI_MENU = "ui.menu"

    fun nonEmptyName(raw: Any?): String? {
        val s = raw as? String ?: return null
        val t = s.trim()
        return t.takeIf { it.isNotEmpty() }
    }

    /**
     * `null`：不要在 `add` 时补当前值。
     * 空列表：无参调用。
     * 单元素：一个参数（普通对象或 `null`）。
     */
    fun syncPayload(
        name: String,
        facts: PluginHostFacts,
        playback: PluginPlaybackSnapshot = PluginPlaybackSnapshot.EMPTY,
    ): List<Any?>? = when (name) {
        APP_ONLINE -> if (facts.online) emptyList() else null
        APP_OFFLINE -> if (!facts.online) emptyList() else null
        APP_APPEARANCE -> listOf(mapOf("dark" to facts.dark))
        USER_SESSION -> listOf(mapOf("loggedIn" to facts.loggedIn))
        PLAYER_TRACK -> listOf(playback.trackArg())
        PLAYER_STATE -> listOf(playback.stateMap())
        PLAYER_QUEUE -> listOf(playback.queueMap())
        PLAYER_LIKED -> listOf(playback.likedMap())
        APP_FOREGROUND -> if (facts.foreground) emptyList() else null
        APP_BACKGROUND -> if (!facts.foreground) emptyList() else null
        else -> null
    }
}
