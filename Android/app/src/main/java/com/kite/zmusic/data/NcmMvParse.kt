package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MvArtist(
    val id: Long,
    val name: String,
    val avatarUrl: String? = null,
)

data class MvDetail(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val artists: List<MvArtist>,
    val playCount: Long,
    val durationMs: Long,
    val publishTime: String?,
    val desc: String?,
    val brs: List<Int>,
)

internal object NcmMvParse {

    private val PublishFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun detail(json: JSONObject, fallbackId: Long): MvDetail? {
        val data = json.optJSONObject("data") ?: json.optJSONObject("mv") ?: json
        val id = data.optLong("id", fallbackId).takeIf { it > 0L } ?: fallbackId
        if (id <= 0L) return null
        val name = data.optString("name", "MV").ifBlank { "MV" }
        val cover = NcmLibraryParse.ncmHttpsImage(
            firstNonBlank(
                data.optString("cover"),
                data.optString("coverUrl"),
                data.optString("imgurl"),
                data.optString("picUrl"),
            ),
        )
        val artists = artistsOf(data)
        val durationRaw = data.optLong("duration", data.optLong("durationMs", 0L))
        val durationMs = if (durationRaw in 1 until 10_000L) durationRaw * 1000L else durationRaw
        val published = data.optLong("publishTime", 0L).takeIf { it > 0L }?.let { ms ->
            runCatching { PublishFmt.format(Date(ms)) }.getOrNull()
        } ?: data.optString("publishTime", "").takeIf { it.isNotBlank() && it != "null" }
        return MvDetail(
            id = id,
            name = name,
            coverUrl = cover,
            artists = artists,
            playCount = data.optLong("playCount", 0L),
            durationMs = durationMs.coerceAtLeast(0L),
            publishTime = published,
            desc = data.optString("desc", "").ifBlank { data.optString("briefDesc", "") }
                .takeIf { it.isNotBlank() && it != "null" },
            brs = brsOf(data),
        )
    }

    fun playUrl(json: JSONObject): String? {
        val data = json.opt("data")
        val url = when (data) {
            is JSONObject -> data.optString("url", "")
            is JSONArray -> data.optJSONObject(0)?.optString("url", "").orEmpty()
            else -> json.optString("url", "")
        }
        return url.trim().takeIf { it.startsWith("http") }
    }

    fun hasMore(json: JSONObject, got: Int, pageSize: Int): Boolean {
        if (json.has("hasMore")) return json.optBoolean("hasMore")
        val data = json.optJSONObject("data")
        if (data != null && data.has("hasMore")) return data.optBoolean("hasMore")
        val count = json.optInt("count", data?.optInt("count", -1) ?: -1)
        val offset = json.optInt("offset", data?.optInt("offset", -1) ?: -1)
        if (count >= 0 && offset >= 0) return offset + got < count
        return got >= pageSize
    }

    fun similar(json: JSONObject): List<RecommendMvCard> {
        val arr = json.optJSONArray("mvs")
            ?: json.optJSONObject("data")?.optJSONArray("mvs")
            ?: json.optJSONArray("data")
            ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val src = o.optJSONObject("mv") ?: o
                val id = src.optLong("id", 0L)
                if (id <= 0L) continue
                add(
                    RecommendMvCard(
                        id = id,
                        name = src.optString("name", "MV").ifBlank { "MV" },
                        coverUrl = NcmLibraryParse.ncmHttpsImage(
                            firstNonBlank(
                                src.optString("imgurl16v9"),
                                src.optString("cover"),
                                src.optString("picUrl"),
                                src.optString("imgurl"),
                            ),
                        ),
                        artist = src.optString("artistName", "")
                            .ifBlank { artistsOf(src).joinToString(" / ") { it.name } }
                            .takeIf { it.isNotBlank() },
                        playCount = src.optLong("playCount", 0L),
                    ),
                )
            }
        }
    }

    private fun artistsOf(o: JSONObject): List<MvArtist> {
        val arr = o.optJSONArray("artists") ?: o.optJSONArray("ar")
        if (arr != null && arr.length() > 0) {
            return buildList {
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    val name = a.optString("name", "").trim()
                    if (name.isEmpty() || name == "null") continue
                    add(
                        MvArtist(
                            id = a.optLong("id", 0L),
                            name = name,
                            avatarUrl = NcmLibraryParse.ncmHttpsImage(
                                firstNonBlank(
                                    a.optString("img1v1Url"),
                                    a.optString("picUrl"),
                                    a.optString("cover"),
                                    a.optString("avatar"),
                                ),
                            ),
                        ),
                    )
                }
            }
        }
        val name = o.optString("artistName", "").trim()
        val id = o.optLong("artistId", 0L)
        return if (name.isNotEmpty() && name != "null") {
            listOf(
                MvArtist(
                    id = id,
                    name = name,
                    avatarUrl = NcmLibraryParse.ncmHttpsImage(
                        firstNonBlank(
                            o.optString("artistImgUrl"),
                            o.optString("img1v1Url"),
                            o.optString("picUrl"),
                        ),
                    ),
                ),
            )
        } else {
            emptyList()
        }
    }

    private fun brsOf(data: JSONObject): List<Int> {
        val brs = data.optJSONObject("brs") ?: return emptyList()
        val keys = brs.keys()
        val out = mutableListOf<Int>()
        while (keys.hasNext()) {
            keys.next().toIntOrNull()?.let { out.add(it) }
        }
        return out.sorted()
    }

    private fun firstNonBlank(vararg values: String): String? =
        values.firstOrNull { it.isNotBlank() && it != "null" }
}
