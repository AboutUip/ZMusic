package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SponsorEntry(
    val time: String,
    val name: String,
    val amount: String,
)

/**
 * 赞助名单：assets/sponsors.json，进入页面时才读。
 * 字段 time / name / amount；amount 可以是数字或字符串，展示固定两位小数。
 * 数组从上到下就是列表顺序。
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

    internal fun formatYuan(value: Double): String =
        "¥" + String.format(java.util.Locale.US, "%.2f", value)
}
