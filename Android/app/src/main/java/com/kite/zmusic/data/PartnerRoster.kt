package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class PartnerEntry(
    val id: String = "",
    val name: String,
    val logo: String,
    val url: String,
    val bio: String,
    val content: String,
) {
    val listKey: String
        get() = id.ifBlank { name }
}

/**
 * 赞助商（社区 vendors）：id / name / logo / url / bio / content。
 * 远程目录走 XAIOP；本地 JSON 解析仍保留给尚未删掉的 assets。
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
                id = o.optString("id").trim(),
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

    fun parseRemote(snapshot: Any?): CommunityCatalogPage<PartnerEntry> =
        parseCatalogArray(snapshot, "vendors", ::parseVendor)

    internal fun parseVendor(raw: Any?): PartnerEntry? {
        val o = raw as? Map<*, *> ?: return null
        val name = catalogString(o["name"])
        if (name.isEmpty()) return null
        return PartnerEntry(
            id = catalogString(o["id"]),
            name = name,
            logo = catalogString(o["logo"]),
            url = catalogString(o["url"]),
            bio = catalogString(o["bio"]),
            content = catalogString(o["content"]),
        )
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
