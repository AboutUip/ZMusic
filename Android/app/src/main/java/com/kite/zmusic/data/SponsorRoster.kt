package com.kite.zmusic.data

import org.json.JSONObject

data class SponsorEntry(
    val id: String = "",
    val time: String,
    val name: String,
    val amount: String,
) {
    val listKey: String
        get() = id.ifBlank { "$name|$time|$amount" }
}

/**
 * 赞助名单字段：id / time / name / amount。
 * 远程目录走 XAIOP。
 */
object SponsorRoster {
    private fun readAmount(value: Any?): String {
        if (value == null || value == JSONObject.NULL) return ""
        if (value is Number) return formatYuan(value.toDouble())
        val text = value.toString().trim()
        val plain = text
            .removePrefix("¥")
            .removePrefix("￥")
            .removeSuffix("元")
            .trim()
        val n = plain.toDoubleOrNull()
        return if (n != null) formatYuan(n) else text
    }

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
}
