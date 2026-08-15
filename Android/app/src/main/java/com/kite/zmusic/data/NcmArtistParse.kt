package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ArtistDetail(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val aliasLine: String?,
    val identify: String?,
    val briefDesc: String?,
    val albumSize: Int,
    val musicSize: Int,
    val mvSize: Int,
    val rankLabel: String?,
)

data class ArtistDynamic(
    val followed: Boolean,
    val fansCount: Long,
    val videoCount: Int,
)

data class ArtistAlbumCard(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val year: String?,
    val size: Int,
)

data class ArtistSimilar(
    val id: Long,
    val name: String,
    val coverUrl: String?,
)

data class ArtistBioBlock(
    val title: String,
    val body: String,
)

data class ArtistBio(
    val brief: String?,
    val blocks: List<ArtistBioBlock>,
)

internal object NcmArtistParse {

    private val YearFmt = SimpleDateFormat("yyyy", Locale.CHINA)

    fun detail(json: JSONObject, fallbackId: Long, fallbackName: String): ArtistDetail? {
        val data = json.optJSONObject("data") ?: return null
        val artist = data.optJSONObject("artist") ?: return null
        val id = artist.optLong("id", fallbackId).takeIf { it > 0L } ?: fallbackId
        if (id <= 0L) return null
        val name = artist.optString("name", fallbackName).ifBlank { fallbackName }.ifBlank { "歌手" }
        val identify = firstNonBlank(
            data.optJSONObject("identify")?.optString("imageDesc"),
            artist.optJSONArray("identifyTag")?.let { stringList(it).firstOrNull() },
            stringList(artist.optJSONArray("identities")).firstOrNull(),
        )
        return ArtistDetail(
            id = id,
            name = name,
            coverUrl = NcmLibraryParse.ncmHttpsImage(
                firstNonBlank(
                    artist.optString("cover"),
                    artist.optString("avatar"),
                    artist.optString("picUrl"),
                    artist.optString("img1v1Url"),
                ),
            ),
            aliasLine = aliasLine(artist),
            identify = identify,
            briefDesc = artist.optString("briefDesc", "")
                .takeIf { it.isNotBlank() && it != "null" },
            albumSize = artist.optInt("albumSize", 0).coerceAtLeast(0),
            musicSize = artist.optInt("musicSize", 0).coerceAtLeast(0),
            mvSize = artist.optInt("mvSize", data.optInt("videoCount", 0)).coerceAtLeast(0),
            rankLabel = rankLabel(artist.optJSONObject("rank")),
        )
    }

    fun dynamic(json: JSONObject): ArtistDynamic? {
        if (NcmJson.apiCode(json) != 200) return null
        val followed = when {
            json.has("followed") -> json.optBoolean("followed")
            json.has("follow") -> json.optBoolean("follow")
            else -> json.optJSONObject("data")?.optBoolean("followed", false) ?: false
        }
        val fans = json.optLong(
            "fansCount",
            json.optLong("followCount", json.optJSONObject("data")?.optLong("fansCount", 0L) ?: 0L),
        )
        val videos = json.optInt(
            "videoCount",
            json.optJSONObject("data")?.optInt("videoCount", 0) ?: 0,
        )
        return ArtistDynamic(
            followed = followed,
            fansCount = fans.coerceAtLeast(0L),
            videoCount = videos.coerceAtLeast(0),
        )
    }

    fun topSongs(json: JSONObject): List<TrackRow> = songsArray(
        json.optJSONArray("songs") ?: json.optJSONArray("hotSongs"),
    )

    fun songsPage(json: JSONObject): Triple<List<TrackRow>, Boolean, Int> {
        val data = json.optJSONObject("data")
        val arr = json.optJSONArray("songs")
            ?: data?.optJSONArray("songs")
            ?: json.optJSONArray("hotSongs")
        val songs = songsArray(arr)
        val more = when {
            json.has("more") -> json.optBoolean("more")
            data?.has("more") == true -> data.optBoolean("more")
            else -> songs.isNotEmpty()
        }
        val total = json.optInt("total", data?.optInt("total", 0) ?: 0).coerceAtLeast(songs.size)
        return Triple(songs, more, total)
    }

    private fun songsArray(arr: JSONArray?): List<TrackRow> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                NcmLibraryParse.trackFromSongObject(o)?.let { add(it) }
            }
        }
    }

    fun albums(json: JSONObject): Pair<List<ArtistAlbumCard>, Boolean> {
        val arr = json.optJSONArray("hotAlbums") ?: json.optJSONArray("albums") ?: JSONArray()
        val cards = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                if (id <= 0L) continue
                add(
                    ArtistAlbumCard(
                        id = id,
                        name = o.optString("name", "专辑").ifBlank { "专辑" },
                        coverUrl = NcmLibraryParse.ncmHttpsImage(
                            firstNonBlank(
                                o.optString("picUrl"),
                                o.optString("blurPicUrl"),
                                o.optString("cover"),
                            ),
                        ),
                        year = yearOf(o.optLong("publishTime", 0L)),
                        size = o.optInt("size", 0).coerceAtLeast(0),
                    ),
                )
            }
        }
        val more = if (json.has("more")) json.optBoolean("more") else cards.isNotEmpty()
        return cards to more
    }

    fun similar(json: JSONObject): List<ArtistSimilar> {
        val arr = json.optJSONArray("artists") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                val name = o.optString("name", "").trim()
                if (id <= 0L || name.isEmpty() || name == "null") continue
                add(
                    ArtistSimilar(
                        id = id,
                        name = name,
                        coverUrl = NcmLibraryParse.ncmHttpsImage(
                            firstNonBlank(
                                o.optString("picUrl"),
                                o.optString("img1v1Url"),
                                o.optString("cover"),
                                o.optString("avatar"),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    fun bio(json: JSONObject): ArtistBio {
        val brief = json.optString("briefDesc", "")
            .takeIf { it.isNotBlank() && it != "null" }
        val arr = json.optJSONArray("introduction")
        val blocks = if (arr == null) {
            emptyList()
        } else {
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val title = o.optString("ti", "").trim()
                    val body = o.optString("txt", "").trim()
                    if (body.isEmpty() || body == "null") continue
                    add(
                        ArtistBioBlock(
                            title = title.takeIf { it.isNotEmpty() && it != "null" } ?: "简介",
                            body = body,
                        ),
                    )
                }
            }
        }
        return ArtistBio(brief = brief, blocks = blocks)
    }

    private fun aliasLine(artist: JSONObject): String? {
        val names = (stringList(artist.optJSONArray("transNames")) +
            stringList(artist.optJSONArray("alias")))
            .distinct()
        return names.joinToString(" / ").takeIf { it.isNotBlank() }
    }

    private fun rankLabel(rank: JSONObject?): String? {
        if (rank == null) return null
        val n = rank.optInt("rank", 0)
        if (n <= 0) return null
        val type = when (rank.optInt("type", 0)) {
            1 -> "华语榜"
            2 -> "欧美榜"
            3 -> "韩国榜"
            4 -> "日本榜"
            else -> "歌手榜"
        }
        return "$type #$n"
    }

    private fun yearOf(ms: Long): String? {
        if (ms <= 0L) return null
        return runCatching { YearFmt.format(Date(ms)) }.getOrNull()
    }

    private fun stringList(arr: JSONArray?): List<String> {
        if (arr == null || arr.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val raw = arr.opt(i) ?: continue
                val s = when (raw) {
                    is String -> raw.trim()
                    is JSONObject -> raw.optString("name", "").trim()
                    else -> raw.toString().trim()
                }
                if (s.isNotEmpty() && s != "null") add(s)
            }
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }
}
