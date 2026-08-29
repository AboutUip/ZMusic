package com.kite.zmusic.data

data class CommunityCatalogPage<T>(
    val ok: Boolean,
    val error: String,
    val more: Boolean,
    val entries: List<T>,
)

internal fun catalogString(value: Any?): String =
    value?.toString()?.trim().orEmpty().let { if (it == "null") "" else it }

internal fun catalogLong(value: Any?): Long? = when (value) {
    is Number -> value.toLong()
    is String -> value.trim().toLongOrNull()
    else -> null
}

internal fun jsonToCatalogTree(value: Any?): Any? = when (value) {
    null, org.json.JSONObject.NULL -> null
    is org.json.JSONObject -> {
        val map = LinkedHashMap<String, Any?>()
        for (key in value.keys()) {
            map[key] = jsonToCatalogTree(value.opt(key))
        }
        map
    }
    is org.json.JSONArray -> {
        val list = ArrayList<Any?>(value.length())
        for (i in 0 until value.length()) {
            list += jsonToCatalogTree(value.opt(i))
        }
        list
    }
    else -> value
}

internal fun <T> parseCatalogArray(
    snapshot: Any?,
    arrayKey: String,
    parseItem: (Any?) -> T?,
): CommunityCatalogPage<T> {
    val root = snapshot as? Map<*, *>
        ?: return CommunityCatalogPage(false, "unavailable", false, emptyList())
    val ok = root["ok"] != false
    val error = catalogString(root["error"])
    if (!ok) {
        return CommunityCatalogPage(false, error.ifBlank { "unavailable" }, false, emptyList())
    }
    val more = root["more"] as? Boolean ?: false
    val raw = root[arrayKey] as? List<*> ?: emptyList<Any?>()
    val entries = ArrayList<T>(raw.size)
    for (item in raw) {
        parseItem(item)?.let { entries += it }
    }
    return CommunityCatalogPage(true, "", more, entries)
}

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
    val versionLabel: String get() = "V $version"
    val listKey: String get() = id.ifBlank { version }
}

object ChangelogRoster {
    fun parseRemote(snapshot: Any?): CommunityCatalogPage<ChangelogEntry> =
        parseCatalogArray(snapshot, "releases", ::parseRelease)

    internal fun parseRelease(raw: Any?, requireItems: Boolean = true): ChangelogEntry? {
        val o = raw as? Map<*, *> ?: return null
        val notes = o["notes"] as? Map<*, *>
        val version = catalogString(notes?.get("version") ?: o["version"])
            .trim().removePrefix("V").removePrefix("v").trim()
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
            val type = when (catalogString(o["type"]).trim().lowercase()) {
                "add", "new" -> ChangelogItemType.Add
                "support", "feat", "feature" -> ChangelogItemType.Support
                "improve", "opt", "optimize" -> ChangelogItemType.Improve
                "fix", "bugfix" -> ChangelogItemType.Fix
                else -> null
            } ?: continue
            val text = catalogString(o["text"])
            if (text.isEmpty()) continue
            out += ChangelogItem(type, text)
        }
        return out
    }
}

data class SponsorEntry(
    val id: String = "",
    val time: String,
    val name: String,
    val amount: String,
) {
    val listKey: String get() = id.ifBlank { "$name|$time|$amount" }
}

object SponsorRoster {
    fun parseRemote(snapshot: Any?): CommunityCatalogPage<SponsorEntry> =
        parseCatalogArray(snapshot, "sponsors", ::parseSponsor)

    internal fun parseSponsor(raw: Any?): SponsorEntry? {
        val o = raw as? Map<*, *> ?: return null
        val name = catalogString(o["name"])
        if (name.isEmpty()) return null
        return SponsorEntry(
            id = catalogString(o["id"]),
            time = catalogString(o["time"]),
            name = name,
            amount = readAmount(o["amount"]),
        )
    }

    internal fun formatYuan(value: Double): String =
        "¥" + String.format(java.util.Locale.US, "%.2f", value)

    private fun readAmount(value: Any?): String {
        if (value == null) return ""
        if (value is Number) return formatYuan(value.toDouble())
        val text = value.toString().trim()
        val plain = text.removePrefix("¥").removePrefix("￥").removeSuffix("元").trim()
        val n = plain.toDoubleOrNull()
        return if (n != null) formatYuan(n) else text
    }
}

data class PartnerEntry(
    val id: String = "",
    val name: String,
    val logo: String,
    val url: String,
    val bio: String,
    val content: String,
) {
    val listKey: String get() = id.ifBlank { name }
}

object PartnerRoster {
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
}
