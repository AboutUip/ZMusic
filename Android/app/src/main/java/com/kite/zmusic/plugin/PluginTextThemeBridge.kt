package com.kite.zmusic.plugin

import androidx.compose.ui.graphics.Color
import com.kite.zmusic.ui.theme.TextTheme
import com.kite.zmusic.ui.theme.TextThemeKeys
import com.kite.zmusic.ui.theme.parseThemeColor
import com.whl.quickjs.wrapper.JSObject

/**
 * 插件 → [com.kite.zmusic.ui.theme.TextTheme] 桥。覆盖文本 / 面色 / 铬 / 控件 / 舞台。
 * 参数校验失败返回 null / false，不部分写入。
 */
object PluginTextThemeBridge {
    fun set(pluginId: String, partial: Any?): Boolean {
        val parsed = parsePartial(partial) ?: return false
        return TextTheme.setOverlay(pluginId, parsed)
    }

    fun clear(pluginId: String): Boolean =
        TextTheme.clearIfOwner(pluginId)

    fun getHexMap(): Map<String, String> =
        TextTheme.effectiveHexMap()

    fun clearIfOwner(pluginId: String) {
        TextTheme.clearIfOwner(pluginId)
    }

    private fun parsePartial(raw: Any?): Map<String, Color>? {
        val map: Map<*, *> = when (raw) {
            null -> return null
            is JSObject -> jsObjectToMap(raw) ?: return null
            is Map<*, *> -> raw
            else -> return null
        }
        if (map.isEmpty()) return null
        val out = LinkedHashMap<String, Color>(map.size)
        for ((k, v) in map) {
            val key = k as? String ?: return null
            if (key !in TextThemeKeys.ALL) return null
            val hex = v as? String ?: return null
            val color = parseThemeColor(hex) ?: return null
            out[key] = color
        }
        return out
    }

    private fun jsObjectToMap(obj: JSObject): Map<*, *>? {
        runCatching { obj.toMap() }.getOrNull()?.let { return it }
        val json = runCatching { obj.stringify() }.getOrNull() ?: return null
        return PluginJson.parseObject(json)
    }
}
