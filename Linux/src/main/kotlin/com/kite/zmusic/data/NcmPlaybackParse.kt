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
        val code = NcmJson.apiCode(json)
        if (code != 200 && code != -1 && !json.has("lrc")) return null
        val lrc = json.optJSONObject("lrc") ?: return null
        return lrc.optString("lyric").takeIf { it.isNotBlank() }
    }

    fun translatedLrcText(json: JSONObject): String? {
        val tlyric = json.optJSONObject("tlyric") ?: return null
        return tlyric.optString("lyric").takeIf { it.isNotBlank() }
    }

    fun yrcText(json: JSONObject): String? {
        val yrcObj = json.optJSONObject("yrc")
        if (yrcObj != null) {
            return yrcObj.optString("lyric").takeIf { it.isNotBlank() }
        }
        val raw = json.optString("yrc")
        return raw.takeIf { it.isNotBlank() && it.contains('[') }
    }

    fun ytlrcText(json: JSONObject): String? {
        val ytlrcObj = json.optJSONObject("ytlrc")
        if (ytlrcObj != null) {
            return ytlrcObj.optString("lyric").takeIf { it.isNotBlank() }
        }
        val raw = json.optString("ytlrc")
        return raw.takeIf { it.isNotBlank() && it.contains('[') }
    }
}

internal object PlayUrlResolver {

    suspend fun resolve(
        userClient: NcmUserClient,
        trackId: Long,
        cookie: String,
        quality: AudioQuality,
    ): String? {
        val cookieForUrl = if (quality.needsPcOs) cookieWithPlaybackOs(cookie) else cookie
        songUrlV1(userClient, trackId, cookieForUrl, quality)?.let { return it }
        for (fallback in quality.fallbacks()) {
            val fbCookie = if (fallback.needsPcOs) cookieWithPlaybackOs(cookie) else cookie
            songUrlV1(userClient, trackId, fbCookie, fallback)?.let { return it }
        }
        val legacy = userClient.songUrl(listOf(trackId), cookie, br = quality.legacyBr)
        return NcmPlaybackParse.songUrlForId(legacy, trackId)
    }

    private suspend fun songUrlV1(
        userClient: NcmUserClient,
        trackId: Long,
        cookie: String,
        quality: AudioQuality,
    ): String? {
        val json = userClient.songUrlV1(
            ids = listOf(trackId),
            cookie = cookie,
            level = quality.level,
            encodeType = quality.encodeType,
        )
        return NcmPlaybackParse.songUrlForId(json, trackId)
    }
}
