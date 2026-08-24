package com.kite.zmusic.plugin

/**
 * 清单 `capabilities` 白名单。未知字符串 → 整包无效。
 * 与 [docs/plugin-engine/VERSIONING.md] 保持一致。
 */
object PluginCapabilities {
    const val THEME = "theme"

    val KNOWN: Set<String> = setOf(THEME)

    /**
     * @return 规范化后的列表；`null` 表示字段非法（非整数组或含未知项）
     */
    fun parse(raw: Any?): List<String>? {
        return when (raw) {
            null -> emptyList()
            is List<*> -> {
                if (raw.any { it !is String }) return null
                val names = raw.map { (it as String).trim() }
                if (names.any { it.isEmpty() || it !in KNOWN }) return null
                names.distinct()
            }
            else -> null
        }
    }

    fun has(capabilities: Collection<String>, name: String): Boolean =
        name in capabilities
}
