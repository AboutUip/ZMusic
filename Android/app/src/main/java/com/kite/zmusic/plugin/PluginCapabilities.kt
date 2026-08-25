package com.kite.zmusic.plugin

/**
 * 清单 `capabilities`。展示用已知名见 [docs/plugin-engine/VERSIONING.md]。
 * 未知字符串忽略；非字符串数组则清单无效。
 */
object PluginCapabilities {
    const val THEME = "theme"
    const val PLAYER = "player"
    const val HTTP = "http"
    const val STORE = "store"
    const val UI = "ui"
    const val MEDIA = "media"
    const val SHARE = "share"
    const val CLIPBOARD = "clipboard"

    val KNOWN: Set<String> = setOf(THEME, PLAYER, HTTP, STORE, UI, MEDIA, SHARE, CLIPBOARD)

    /**
     * @return 规范化后的**已知**能力列表；`null` 表示字段非法（不是数组，或含非字符串）
     */
    fun parse(raw: Any?): List<String>? {
        return when (raw) {
            null -> emptyList()
            is List<*> -> {
                if (raw.any { it !is String }) return null
                raw.map { (it as String).trim() }
                    .filter { it.isNotEmpty() && it in KNOWN }
                    .distinct()
            }
            else -> null
        }
    }

    fun has(capabilities: Collection<String>, name: String): Boolean =
        name in capabilities
}
