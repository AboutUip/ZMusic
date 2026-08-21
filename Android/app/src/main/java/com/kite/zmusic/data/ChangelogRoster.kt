package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class ChangelogItemType {
    Add,
    Support,
    Improve,
    Fix,
}

data class ChangelogItem(
    val type: ChangelogItemType,
    val text: String,
)

data class ChangelogEntry(
    val version: String,
    val kind: String,
    val notice: String,
    val items: List<ChangelogItem>,
) {
    val versionLabel: String
        get() = "V $version"
}

data class ChangelogDocument(
    val title: String,
    val entries: List<ChangelogEntry>,
)

/**
 * 更新日志：assets/changelog.json，进入页面时才读。
 * 字段固定：version / kind / notice / items[{type,text}]。
 */
object ChangelogRoster {
    private const val ASSET = "changelog.json"
    private const val DefaultTitle = "ZMusic更新预览"

    suspend fun load(context: Context): ChangelogDocument = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            parse(raw)
        }.getOrDefault(ChangelogDocument(DefaultTitle, emptyList()))
    }

    internal fun parse(raw: String): ChangelogDocument {
        val trimmed = raw.trim().ifBlank { "{}" }
        val root = JSONObject(trimmed)
        val title = root.optString("title").trim().ifBlank { DefaultTitle }
        val arr = root.optJSONArray("entries") ?: JSONArray()
        val out = ArrayList<ChangelogEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val version = normalizeVersion(o.optString("version"))
            if (version.isEmpty()) continue
            val items = parseItems(o.optJSONArray("items"))
            if (items.isEmpty()) continue
            out += ChangelogEntry(
                version = version,
                kind = o.optString("kind").trim().ifBlank { "Release" },
                notice = o.optString("notice").trim(),
                items = items,
            )
        }
        return ChangelogDocument(title = title, entries = out)
    }

    fun filter(entries: List<ChangelogEntry>, query: String): List<ChangelogEntry> {
        val q = query.trim().lowercase()
            .removePrefix("v")
            .trim()
        if (q.isEmpty()) return entries
        return entries.filter { entry ->
            val version = entry.version.lowercase()
            version.contains(q) ||
                entry.versionLabel.lowercase().contains(query.trim().lowercase())
        }
    }

    private fun parseItems(arr: JSONArray?): List<ChangelogItem> {
        if (arr == null) return emptyList()
        val out = ArrayList<ChangelogItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = parseType(o.optString("type")) ?: continue
            val text = o.optString("text").trim()
            if (text.isEmpty()) continue
            out += ChangelogItem(type = type, text = text)
        }
        return out
    }

    private fun parseType(raw: String): ChangelogItemType? = when (raw.trim().lowercase()) {
        "add", "new" -> ChangelogItemType.Add
        "support", "feat", "feature" -> ChangelogItemType.Support
        "improve", "opt", "optimize" -> ChangelogItemType.Improve
        "fix", "bugfix" -> ChangelogItemType.Fix
        else -> null
    }

    private fun normalizeVersion(raw: String): String =
        raw.trim()
            .removePrefix("V")
            .removePrefix("v")
            .trim()
}
