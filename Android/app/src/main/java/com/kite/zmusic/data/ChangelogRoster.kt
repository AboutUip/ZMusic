package com.kite.zmusic.data

import com.kite.zmusic.i18n.t

data class ChangelogItem(
    /** 任意动词标签（删除 / 调整 / 支持…）；旧数据可能是 add/support/fix */
    val type: String,
    val text: String,
) {
    val label: String
        get() = ChangelogRoster.displayLabel(type)
}

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
 * type 为任意标签；社区按「某某了某某」解析写入。远程目录走 XAIOP 树。
 */
object ChangelogRoster {
    val DefaultTitle: String get() = t("ZMusic更新预览")

    fun displayLabel(type: String): String {
        val raw = type.trim()
        if (raw.isEmpty()) return t("说明")
        return when (raw.lowercase()) {
            "add", "new" -> t("新增")
            "support", "feat", "feature" -> t("支持")
            "improve", "opt", "optimize" -> t("优化")
            "fix", "bugfix" -> t("修复")
            "note" -> t("说明")
            else -> raw
        }
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

    private fun parseItemList(raw: Any?): List<ChangelogItem> {
        val arr = raw as? List<*> ?: return emptyList()
        val out = ArrayList<ChangelogItem>(arr.size)
        for (item in arr) {
            val o = item as? Map<*, *> ?: continue
            val type = catalogString(o["type"])
            val text = catalogString(o["text"])
            if (type.isEmpty() || text.isEmpty()) continue
            out += ChangelogItem(type = type, text = text)
        }
        return out
    }

    internal fun normalizeVersion(raw: String): String =
        raw.trim()
            .removePrefix("V")
            .removePrefix("v")
            .trim()
}
