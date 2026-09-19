package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject
import com.kite.zmusic.i18n.t

internal object AnnualReportParse {
    private val SkipKeys = setOf(
        "cookie", "token", "csrf", "code", "msg", "message", "url", "qrurl", "qrimg",
    )
    private val CoverKeys = listOf(
        "picUrl", "coverUrl", "coverImgUrl", "imgUrl", "imageUrl", "avatarUrl",
        "img1v1Url", "picurl", "cover",
    )
    private val IdKeys = listOf("id", "songId", "songid", "resourceId", "resId")
    private val ArtistIdKeys = listOf("id", "artistId", "artistid")
    private val NameKeys = listOf("name", "songName", "songname", "title")
    private val ArtistNameKeys = listOf("name", "artistName", "artistname", "nickname")
    private val PlayKeys = listOf(
        "playCount", "playTimes", "playcount", "count", "score", "listenCount", "num",
    )

    fun fromJson(year: Int, json: JSONObject): AnnualReport {
        val code = NcmJson.apiCode(json)
        if (code != 200 && code != 201 && code != -1) {
            if (!json.has("data") && !json.has("userdata")) return AnnualReport(year)
        }
        val acc = Acc(year)
        val root = json.optJSONObject("data")
            ?: json.optJSONObject("userdata")
            ?: json
        walk(root, acc, 0, "data")
        return acc.toReport()
    }

    private class Acc(val year: Int) {
        var listenDurationMs: Long? = null
        var playCount: Long? = null
        var songCount: Long? = null
        var artistCount: Long? = null
        var keyword: String? = null
        val songs = ArrayList<AnnualSong>()
        val artists = ArrayList<AnnualArtist>()
        val styles = ArrayList<AnnualStyle>()
        val hours = ArrayList<AnnualHourSlot>()
        val facts = ArrayList<AnnualFact>()

        fun toReport(): AnnualReport = AnnualReport(
            year = year,
            listenDurationMs = listenDurationMs,
            playCount = playCount,
            songCount = songCount,
            artistCount = artistCount,
            keyword = keyword,
            songs = songs.distinctBy { if (it.id > 0L) "id:${it.id}" else "n:${it.name}|${it.artists}" }
                .sortedByDescending { it.playCount }
                .take(30),
            artists = artists.distinctBy { if (it.id > 0L) "id:${it.id}" else "n:${it.name}" }
                .sortedByDescending { it.playCount }
                .take(20),
            styles = styles.distinctBy { it.name }
                .sortedByDescending { it.playCount }
                .take(16),
            hours = hours
                .groupBy { it.hour }
                .map { (h, list) -> AnnualHourSlot(h, list.maxOf { it.playCount }) }
                .sortedBy { it.hour },
            facts = facts.distinctBy { it.label }.take(8),
        )
    }

    private fun walk(obj: JSONObject, acc: Acc, depth: Int, path: String) {
        if (depth > 8) return
        harvestStats(obj, acc, path, depth)
        harvestKeyword(obj, acc)
        harvestFact(obj, acc, path)
        asSong(obj, path)?.let { acc.songs += it }
        asArtist(obj, path)?.let { acc.artists += it }
        asStyle(obj, path)?.let { acc.styles += it }
        asHour(obj)?.let { acc.hours += it }

        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in SkipKeys) continue
            val childPath = "$path.$key"
            when (val raw = obj.opt(key)) {
                is JSONObject -> walk(raw, acc, depth + 1, childPath)
                is JSONArray -> walkArray(raw, acc, depth + 1, childPath, key)
            }
        }
    }

    private fun walkArray(
        arr: JSONArray,
        acc: Acc,
        depth: Int,
        path: String,
        key: String,
    ) {
        val n = minOf(arr.length(), 40)
        for (i in 0 until n) {
            when (val item = arr.opt(i)) {
                is JSONObject -> {
                    when {
                        looksLikeSongPath(key) -> asSong(item, path)?.let { acc.songs += it }
                        looksLikeArtistPath(key) -> asArtist(item, path)?.let { acc.artists += it }
                        looksLikeStylePath(key) -> asStyle(item, path)?.let { acc.styles += it }
                        looksLikeHourPath(key) -> asHour(item)?.let { acc.hours += it }
                    }
                    walk(item, acc, depth, "$path[]")
                }
                is String -> {
                    val text = item.trim()
                    if (text.isNotEmpty() && looksLikeStylePath(key) && acc.styles.size < 16) {
                        acc.styles += AnnualStyle(text, 0L)
                    }
                    if (text.isNotEmpty() && acc.keyword.isNullOrBlank() &&
                        (key.equals("keyword", true) || key.equals("keywords", true))
                    ) {
                        acc.keyword = text
                    }
                }
            }
        }
    }

    private fun harvestStats(obj: JSONObject, acc: Acc, path: String, depth: Int) {
        if (depth > 3) return
        if (looksLikeItemPath(path)) return
        durationMs(obj)?.let { ms ->
            acc.listenDurationMs = maxOf(acc.listenDurationMs ?: 0L, ms).takeIf { it > 0L }
                ?: acc.listenDurationMs
        }
        longOf(obj, "playCount", "playTimes", "totalPlayCount", "listenSongs")?.let { n ->
            if (n > (acc.playCount ?: 0L)) acc.playCount = n
        }
        longOf(obj, "songCount", "songNum", "musicCount", "uniqueSongCount")?.let { n ->
            if (n > (acc.songCount ?: 0L)) acc.songCount = n
        }
        longOf(obj, "artistCount", "artistNum", "singerCount")?.let { n ->
            if (n > (acc.artistCount ?: 0L)) acc.artistCount = n
        }
        if (acc.playCount == null) {
            longOf(obj, "listenSongs")?.let { acc.playCount = it }
        }
    }

    private fun harvestKeyword(obj: JSONObject, acc: Acc) {
        if (!acc.keyword.isNullOrBlank()) return
        textOf(obj, "keyword", "annualKeyword", "yearKeyword", "word")?.let {
            acc.keyword = it
        }
    }

    private fun harvestFact(obj: JSONObject, acc: Acc, path: String) {
        if (acc.facts.size >= 8 || looksLikeItemPath(path)) return
        textOf(obj, "period")?.let { acc.facts += AnnualFact(t("常听时段"), it) }
        textOf(obj, "style", "musicStyle", "genre")?.let { name ->
            if (acc.styles.none { it.name == name }) {
                acc.styles += AnnualStyle(name, 0L)
            }
        }
        textOf(obj, "city", "cityName")?.let { acc.facts += AnnualFact(t("常听城市"), it) }
        textOf(obj, "firstListen", "firstPlaySong")?.let { acc.facts += AnnualFact(t("年初第一首"), it) }
    }

    private fun asSong(obj: JSONObject, path: String): AnnualSong? {
        val nested = obj.optJSONObject("song")
        if (nested != null && !obj.has("songName") && !obj.has("name")) {
            return asSong(nested, "$path.song")
        }
        val name = textOf(obj, *NameKeys.toTypedArray()) ?: return null
        if (name.length > 80) return null
        val looksSong = looksLikeSongPath(path) ||
            obj.has("songId") || obj.has("songName") || obj.has("songname") ||
            obj.has("al") || obj.has("album") || obj.has("ar") || obj.has("artists")
        val cover = coverOf(obj)
        val id = longOf(obj, *IdKeys.toTypedArray()) ?: 0L
        if (!looksSong && cover == null && id <= 0L) return null
        if (!looksSong && looksLikeArtistPath(path)) return null
        if (obj.has("userId") && obj.has("nickname") && !obj.has("songName")) return null
        val artists = artistLine(obj)
        if (!looksSong && artists.isBlank() && id <= 0L) return null
        return AnnualSong(
            id = id,
            name = name,
            artists = artists,
            coverUrl = cover,
            playCount = longOf(obj, *PlayKeys.toTypedArray()) ?: 0L,
        )
    }

    private fun asArtist(obj: JSONObject, path: String): AnnualArtist? {
        val nested = obj.optJSONObject("artist") ?: obj.optJSONObject("creator")
        if (nested != null && !obj.has("artistName") && looksLikeArtistPath(path)) {
            return asArtist(nested, "$path.artist")
        }
        val name = textOf(obj, *ArtistNameKeys.toTypedArray()) ?: return null
        if (name.length > 40) return null
        val looks = looksLikeArtistPath(path) ||
            obj.has("artistId") || obj.has("artistName") || obj.has("img1v1Url")
        if (!looks && (obj.has("al") || obj.has("songName") || obj.has("songId"))) return null
        if (!looks && !obj.has("picUrl") && !obj.has("img1v1Url")) return null
        if (obj.has("userId") && obj.has("nickname") && !looks) return null
        val id = longOf(obj, *ArtistIdKeys.toTypedArray()) ?: 0L
        if (!looks && id <= 0L) return null
        return AnnualArtist(
            id = id,
            name = name,
            coverUrl = coverOf(obj),
            playCount = longOf(obj, *PlayKeys.toTypedArray()) ?: 0L,
        )
    }

    private fun asStyle(obj: JSONObject, path: String): AnnualStyle? {
        if (!looksLikeStylePath(path) && !obj.has("tag") && !obj.has("genre")) {
            if (!obj.has("name") || obj.has("id") || coverOf(obj) != null) return null
        }
        val name = textOf(obj, "name", "tag", "genre", "style", "title") ?: return null
        if (name.length > 16) return null
        return AnnualStyle(name, longOf(obj, *PlayKeys.toTypedArray()) ?: 0L)
    }

    private fun asHour(obj: JSONObject): AnnualHourSlot? {
        val hour = longOf(obj, "hour", "time", "hourOfDay") ?: return null
        if (hour !in 0L..23L) return null
        return AnnualHourSlot(hour.toInt(), longOf(obj, *PlayKeys.toTypedArray()) ?: 0L)
    }

    private fun artistLine(obj: JSONObject): String {
        textOf(obj, "artistName", "artistsName", "arName")?.let { return it }
        val names = ArrayList<String>()
        collectArtistNames(obj.opt("ar"), names)
        collectArtistNames(obj.opt("artists"), names)
        obj.optJSONObject("artist")?.let { textOf(it, "name") }?.let { names += it }
        return names.distinct().joinToString(" / ")
    }

    private fun collectArtistNames(raw: Any?, out: MutableList<String>) {
        when (raw) {
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    when (val item = raw.opt(i)) {
                        is JSONObject -> textOf(item, "name")?.let { out += it }
                        is String -> if (item.isNotBlank()) out += item.trim()
                    }
                }
            }
            is JSONObject -> textOf(raw, "name")?.let { out += it }
            is String -> if (raw.isNotBlank()) out += raw.trim()
        }
    }

    private fun coverOf(obj: JSONObject): String? {
        for (key in CoverKeys) {
            httpUrl(obj.optString(key, "")).let { if (it != null) return it }
        }
        obj.optJSONObject("al")?.let { album ->
            httpUrl(album.optString("picUrl", "")).let { if (it != null) return it }
            httpUrl(album.optString("coverUrl", "")).let { if (it != null) return it }
        }
        obj.optJSONObject("album")?.let { album ->
            httpUrl(album.optString("picUrl", "")).let { if (it != null) return it }
        }
        obj.optJSONObject("song")?.let { return coverOf(it) }
        return null
    }

    private fun durationMs(obj: JSONObject): Long? {
        val keys = listOf(
            "totalDuration", "totalPlayTime", "totalPlayDuration",
            "listenTime", "listenDuration", "playTime", "playDuration", "duration",
        )
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val raw = when (val v = obj.opt(key)) {
                is Number -> v.toDouble()
                is String -> v.trim().toDoubleOrNull()
                else -> null
            } ?: continue
            if (raw.isNaN() || raw <= 0.0 || raw >= 1.0e12) continue
            val lower = key.lowercase()
            val ms = when {
                lower.contains("hour") -> raw * 3_600_000.0
                lower.contains("minute") || lower.endsWith("min") ->
                    if (raw >= 1_000_000.0) raw * 1_000.0 else raw * 60_000.0
                lower.contains("millis") || lower.endsWith("ms") -> raw
                lower.contains("sec") -> raw * 1_000.0
                raw >= 86_400_000.0 -> raw
                raw >= 86_400.0 -> raw * 1_000.0
                else -> raw * 60_000.0
            }
            if (ms in 1.0..(100_000.0 * 3_600_000.0)) return ms.toLong()
        }
        return null
    }

    private fun looksLikeSongPath(path: String): Boolean {
        val p = path.lowercase()
        return p.contains("song") || p.contains("music") || p.contains("track") ||
            p.contains("top") || p.contains("favorite") || p.contains("favourite")
    }

    private fun looksLikeArtistPath(path: String): Boolean {
        val p = path.lowercase()
        return p.contains("artist") || p.contains("singer")
    }

    private fun looksLikeStylePath(path: String): Boolean {
        val p = path.lowercase()
        return p.contains("genre") || p.contains("style") || p.contains("tag")
    }

    private fun looksLikeHourPath(path: String): Boolean {
        val p = path.lowercase()
        return p.contains("hour") || p.contains("period") || p.contains("timeslot")
    }

    private fun looksLikeItemPath(path: String): Boolean {
        val p = path.lowercase()
        return p.contains("rank") || p.contains("[]") ||
            looksLikeSongPath(path) || looksLikeArtistPath(path)
    }

    private fun longOf(obj: JSONObject, vararg keys: String): Long? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            when (val v = obj.opt(key)) {
                is Number -> {
                    val n = v.toLong()
                    if (n >= 0L) return n
                }
                is String -> v.trim().toLongOrNull()?.takeIf { it >= 0L }?.let { return it }
            }
        }
        return null
    }

    private fun textOf(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val v = obj.opt(key) ?: continue
            if (v is JSONObject || v is JSONArray) continue
            val s = v.toString().trim()
            if (s.isEmpty() || s.equals("null", true) || s.equals("undefined", true)) continue
            if (s.startsWith("http")) continue
            return s
        }
        return null
    }

    private fun httpUrl(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty() || v == "null") return null
        if (v.startsWith("http://") || v.startsWith("https://")) return v
        if (v.startsWith("//")) return "https:$v"
        return null
    }
}
