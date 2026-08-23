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
    val id: String,
    val version: String,
    val kind: String,
    val notice: String,
    val items: List<ChangelogItem>,
) {
    val versionLabel: String
        get() = "V $version"

    val listKey: String
        get() = id.ifBlank { version }
}

data class ChangelogDocument(
    val title: String,
    val entries: List<ChangelogEntry>,
)

/**
 * 更新日志字段：version / kind / notice / items[{type,text}]。
 * 远程目录走 XAIOP 树；本地 JSON 解析仍保留给尚未删掉的 assets。
 */
object ChangelogRoster {
    private const val ASSET = "changelog.json"
    const val DefaultTitle = "ZMusic更新预览"

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
                id = o.optString("id").trim(),
                version = version,
                kind = o.optString("kind").trim().ifBlank { "Release" },
                notice = o.optString("notice").trim(),
                items = items,
            )
        }
        return ChangelogDocument(title = title, entries = out)
    }

    fun filter(entries: List<ChangelogEntry>, query: String): List<ChangelogEntry> {
        val q = normalizeQuery(query)
        if (q.isEmpty()) return entries
        return entries.filter { entry ->
            val version = entry.version.lowercase()
            version.contains(q) ||
                entry.versionLabel.lowercase().contains(query.trim().lowercase())
        }
    }

    fun normalizeQuery(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val peeled = if (trimmed.length >= 2 &&
            (trimmed[0] == 'v' || trimmed[0] == 'V') &&
            trimmed[1].isDigit()
        ) {
            trimmed.substring(1).trim()
        } else {
            trimmed
        }
        return peeled.lowercase()
    }

    fun parseRemote(snapshot: Any?): CommunityCatalogPage<ChangelogEntry> =
        parseCatalogArray(snapshot, "releases", ::parseRelease)

    internal fun parseRelease(raw: Any?, requireItems: Boolean = true): ChangelogEntry? {
        val o = raw as? Map<*, *> ?: return null
        val notes = o["notes"] as? Map<*, *>
        val version = normalizeVersion(
            catalogString(notes?.get("version") ?: o["version"]),
        )
        if (version.isEmpty()) return null
        val items = parseItemList(notes?.get("items") ?: o["items"])
        if (requireItems && items.isEmpty()) return null
        return ChangelogEntry(
            id = catalogString(o["id"]),
            version = version,
            kind = catalogString(notes?.get("kind") ?: o["kind"]).ifBlank { "Release" },
            notice = catalogString(notes?.get("notice") ?: o["notice"]),
            items = items,
        )
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

    private fun parseItemList(raw: Any?): List<ChangelogItem> {
        val arr = raw as? List<*> ?: return emptyList()
        val out = ArrayList<ChangelogItem>(arr.size)
        for (item in arr) {
            val o = item as? Map<*, *> ?: continue
            val type = parseType(catalogString(o["type"])) ?: continue
            val text = catalogString(o["text"])
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

    internal fun normalizeVersion(raw: String): String =
        raw.trim()
            .removePrefix("V")
            .removePrefix("v")
            .trim()

}
