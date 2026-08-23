package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
 * 远程目录走 XAIOP；本地 JSON 解析仍保留给尚未删掉的 assets。
 */
object SponsorRoster {
    private const val ASSET = "sponsors.json"

    suspend fun load(context: Context): List<SponsorEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            parse(raw)
        }.getOrDefault(emptyList())
    }

    internal fun parse(raw: String): List<SponsorEntry> {
        val arr = JSONArray(raw.trim().ifBlank { "[]" })
        val out = ArrayList<SponsorEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            out += SponsorEntry(
                id = o.optString("id").trim(),
                time = o.optString("time").trim(),
                name = name,
                amount = readAmount(o.opt("amount")),
            )
        }
        return out
    }

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
