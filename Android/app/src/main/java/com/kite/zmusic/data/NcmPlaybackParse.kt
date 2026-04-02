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

    fun lrcText(json: JSONObject): String? {
        if (NcmJson.apiCode(json) != 200) return null
        val lrc = json.optJSONObject("lrc") ?: return null
        return lrc.optString("lyric").takeIf { it.isNotBlank() }
    }
}
