package com.kite.zmusic.data

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
 * 远程目录走 XAIOP。
 */
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
