package com.kite.zmusic.data

import org.json.JSONObject
import com.kite.zmusic.i18n.t

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
            .removeSuffix(t("元"))
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

    internal fun parseYuan(text: String): Double {
        val plain = text.trim()
            .removePrefix("¥")
            .removePrefix("￥")
            .removeSuffix(t("元"))
            .replace(",", "")
            .trim()
        return plain.toDoubleOrNull() ?: 0.0
    }
}

/** 同一名字在全部时间里的打赏合计。金额高的在前。 */
internal data class SponsorPodiumPlace(
    val rank: Int,
    val name: String,
    val amountLabel: String,
)

internal fun sponsorPodium(entries: List<SponsorEntry>, limit: Int = 3): List<SponsorPodiumPlace> {
    if (entries.isEmpty() || limit <= 0) return emptyList()
    val totals = LinkedHashMap<String, Double>()
    val display = HashMap<String, String>()
    for (entry in entries) {
        val shown = entry.name.trim()
        if (shown.isEmpty()) continue
        val key = shown.lowercase()
        totals[key] = (totals[key] ?: 0.0) + SponsorRoster.parseYuan(entry.amount)
        if (key !in display) display[key] = shown
    }
    return totals.entries
        .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
        .take(limit)
        .mapIndexed { index, (key, total) ->
            SponsorPodiumPlace(
                rank = index + 1,
                name = display[key].orEmpty(),
                amountLabel = SponsorRoster.formatYuan(total),
            )
        }
}
