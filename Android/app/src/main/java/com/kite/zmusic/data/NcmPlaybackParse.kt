package com.kite.zmusic.data

import org.json.JSONObject

internal object NcmPlaybackParse {

    fun songUrlForId(json: JSONObject, id: Long): String? {
        if (NcmJson.apiCode(json) != 200) return null
        val arr = json.optJSONArray("data") ?: return null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") != id) continue
            val u = o.optString("url")
            return u.takeIf { it.isNotBlank() }
        }
        return null
    }

    /** 优先读 `lrc.lyric`；无 code/非 200 时仍尝试解析（部分代理响应缺顶层 code）。 */
    fun lrcText(json: JSONObject): String? {
        val code = NcmJson.apiCode(json)
        if (code != 200 && code != -1 && !json.has("lrc")) return null
        val lrc = json.optJSONObject("lrc") ?: return null
        return lrc.optString("lyric").takeIf { it.isNotBlank() }
    }
}
