package com.kite.zmusic.plugin

import org.json.JSONArray
import org.json.JSONObject

/**
 * 交给插件之前去掉登录凭证和可直接拉流的音视频地址。
 */
internal object PluginJsonSanitize {
    private val secretKey = Regex("(?i)(cookie|token|csrf|password|secret)")
    private val mediaKey = Regex("(?i)^(url|playurl|mp3url|downloadurl|brs)$")
    private val mediaValue = Regex("(?i)https?://\\S+\\.(mp3|m4a|flac|aac|mp4|m3u8)(\\?.*)?$")
    private const val MAX_DEPTH = 24

    fun clean(value: Any?): Any? = clean(value, 0)

    private fun clean(value: Any?, depth: Int): Any? {
        if (value == null || value === JSONObject.NULL) return null
        if (depth > MAX_DEPTH) return null
        return when (value) {
            is JSONObject -> {
                val map = LinkedHashMap<String, Any?>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (secretKey.containsMatchIn(key) || mediaKey.matches(key)) continue
                    map[key] = clean(value.opt(key), depth + 1)
                }
                map
            }
            is JSONArray -> {
                val list = ArrayList<Any?>(value.length())
                for (i in 0 until value.length()) {
                    list.add(clean(value.opt(i), depth + 1))
                }
                list
            }
            is String -> if (mediaValue.matches(value.trim())) null else value
            is Boolean, is Int, is Long, is Double, is Float -> value
            is Number -> value.toDouble()
            else -> value.toString()
        }
    }
}
