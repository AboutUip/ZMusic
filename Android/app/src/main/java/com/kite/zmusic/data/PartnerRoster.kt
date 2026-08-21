package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class PartnerEntry(
    val name: String,
    val logo: String,
    val url: String,
    val bio: String,
    val content: String,
)

/**
 * 赞助商：assets/partners.json，进入页面时才读。
 * 字段 name / logo / url / bio / content；logo 与 url 均为 http(s) 地址。
 * bio 为简介，content 为赞助内容。数组从上到下就是列表顺序。
 */
object PartnerRoster {
    private const val ASSET = "partners.json"

    suspend fun load(context: Context): List<PartnerEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            parse(raw)
        }.getOrDefault(emptyList())
    }

    internal fun parse(raw: String): List<PartnerEntry> {
        val arr = JSONArray(raw.trim().ifBlank { "[]" })
        val out = ArrayList<PartnerEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            out += PartnerEntry(
                name = name,
                logo = o.optString("logo").trim(),
                url = o.optString("url").trim().ifEmpty { o.optString("website").trim() },
                bio = firstNonBlank(
                    o.optString("bio"),
                    o.optString("intro"),
                    o.optString("summary"),
                ),
                content = firstNonBlank(
                    o.optString("content"),
                    o.optString("offer"),
                    o.optString("sponsorship"),
                ),
            )
        }
        return out
    }

    private fun firstNonBlank(vararg values: String): String {
        for (value in values) {
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) return trimmed
        }
        return ""
    }

    internal fun browseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return if (
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
