package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject

data class HomeBanner(
    val picUrl: String,
    val title: String?,
    val targetId: Long,
    val targetType: Int,
    val url: String?,
)

data class RecommendPlaylistCard(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
)

data class RecommendMvCard(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val artist: String?,
    val playCount: Long,
)

data class SearchPlaylistHit(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
    val trackCount: Int,
    val creator: String?,
    val creatorId: Long = 0L,
)

data class SearchArtistHit(
    val id: Long,
    val name: String,
    val coverUrl: String?,
)

data class SearchUserHit(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val signature: String?,
)

data class HotSearchWord(
    val word: String,
    val content: String?,
    val highlighted: Boolean,
)

data class ChartSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val updateFrequency: String?,
    val playCount: Long,
)

data class AlbumBrief(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val artist: String?,
    val artistId: Long = 0L,
    val artistCoverUrl: String? = null,
    val songs: List<TrackRow>,
    val publishTime: Long = 0L,
    val company: String? = null,
    val description: String? = null,
    val type: String? = null,
    val alias: String? = null,
    val size: Int = 0,
)

internal object NcmHomeParse {

    fun banners(json: JSONObject): List<HomeBanner> {
        val arr = json.optJSONArray("banners") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pic = o.optString("pic", "")
                    .ifBlank { o.optString("imageUrl", "") }
                    .ifBlank { o.optString("picUrl", "") }
                    .takeIf { it.isNotBlank() } ?: continue
                add(
                    HomeBanner(
                        picUrl = pic,
                        title = o.optString("typeTitle", "").takeIf { it.isNotBlank() },
                        targetId = o.optLong("targetId", 0L),
                        targetType = o.optInt("targetType", 0),
                        url = o.optString("url", "").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    fun personalizedPlaylists(json: JSONObject): List<RecommendPlaylistCard> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val arr = json.optJSONArray("result") ?: return emptyList()
        return playlistCardsFromArray(arr, picKey = "picUrl", playKey = "playCount")
    }

    fun topPlaylists(json: JSONObject): List<RecommendPlaylistCard> {
        val arr = json.optJSONArray("playlists")
            ?: json.optJSONObject("data")?.optJSONArray("playlists")
            ?: return emptyList()
        return playlistCardsFromArray(arr, picKey = "coverImgUrl", playKey = "playCount")
    }

    fun recommendResourcePlaylists(json: JSONObject): List<RecommendPlaylistCard> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val arr = json.optJSONArray("recommend") ?: return emptyList()
        return playlistCardsFromArray(arr, picKey = "picUrl", playKey = "playcount")
    }

    fun personalizedNewSongs(json: JSONObject): List<TrackRow> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val arr = json.optJSONArray("result") ?: json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val song = o.optJSONObject("song") ?: o
                val track = NcmLibraryParse.trackFromSongObject(song) ?: continue
                val cover = track.coverUrl
                    ?: o.optString("picUrl", "").takeIf { it.isNotBlank() }
                add(if (cover != null && cover != track.coverUrl) track.copy(coverUrl = cover) else track)
            }
        }
    }

    fun personalizedMvs(json: JSONObject): List<RecommendMvCard> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val arr = json.optJSONArray("result") ?: json.optJSONArray("data") ?: return emptyList()
        return mvsFromArray(arr)
    }

    fun latestMvs(json: JSONObject): List<RecommendMvCard> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val arr = json.optJSONArray("data")
            ?: json.optJSONObject("data")?.optJSONArray("mvList")
            ?: json.optJSONArray("result")
            ?: return emptyList()
        return mvsFromArray(arr)
    }

    fun searchHasMore(json: JSONObject, loaded: Int, pageSize: Int, countKey: String): Boolean {
        val result = json.optJSONObject("result") ?: json.optJSONObject("data")
        if (result != null) {
            if (result.has("hasMore")) return result.optBoolean("hasMore")
            val total = result.optInt(countKey, -1)
            if (total >= 0) return loaded < total
        }
        return loaded > 0 && loaded % pageSize == 0
    }

    fun searchTracks(json: JSONObject): List<TrackRow> {
        val result = json.optJSONObject("result") ?: json.optJSONObject("data") ?: return emptyList()
        val arr = result.optJSONArray("songs") ?: return emptyList()
        return tracksFromArray(arr)
    }

    fun searchPlaylists(json: JSONObject): List<SearchPlaylistHit> {
        val result = json.optJSONObject("result") ?: json.optJSONObject("data") ?: return emptyList()
        val arr = result.optJSONArray("playlists") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                if (id <= 0L) continue
                add(
                    SearchPlaylistHit(
                        id = id,
                        name = o.optString("name", "歌单").ifBlank { "歌单" },
                        coverUrl = o.optString("coverImgUrl", "")
                            .ifBlank { o.optString("picUrl", "") }
                            .takeIf { it.isNotBlank() },
                        playCount = o.optLong("playCount", o.optLong("playcount", 0L)),
                        trackCount = o.optInt("trackCount", 0),
                        creator = o.optJSONObject("creator")
                            ?.optString("nickname", "")
                            ?.takeIf { it.isNotBlank() && it != "null" },
                        creatorId = o.optJSONObject("creator")
                            ?.optLong("userId", 0L)
                            ?: 0L,
                    ),
                )
            }
        }
    }

    fun searchMvs(json: JSONObject): List<RecommendMvCard> {
        val result = json.optJSONObject("result") ?: json.optJSONObject("data") ?: return emptyList()
        val arr = result.optJSONArray("mvs") ?: result.optJSONArray("mv") ?: return emptyList()
        return mvsFromArray(arr)
    }

    fun searchArtists(json: JSONObject): List<SearchArtistHit> {
        val result = json.optJSONObject("result") ?: json.optJSONObject("data") ?: return emptyList()
        val arr = result.optJSONArray("artists") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                if (id <= 0L) continue
                add(
                    SearchArtistHit(
                        id = id,
                        name = o.optString("name", "歌手").ifBlank { "歌手" },
                        coverUrl = o.optString("picUrl", "")
                            .ifBlank { o.optString("img1v1Url", "") }
                            .takeIf { it.isNotBlank() && !it.contains("default") },
                    ),
                )
            }
        }
    }

    fun searchUsers(json: JSONObject): List<SearchUserHit> {
        val result = json.optJSONObject("result") ?: json.optJSONObject("data") ?: return emptyList()
        val arr = result.optJSONArray("userprofiles")
            ?: result.optJSONArray("users")
            ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("userId", 0L).takeIf { it > 0L }
                    ?: o.optLong("id", 0L)
                if (id <= 0L) continue
                add(
                    SearchUserHit(
                        id = id,
                        name = o.optString("nickname", "")
                            .ifBlank { o.optString("name", "用户") }
                            .ifBlank { "用户" },
                        avatarUrl = o.optString("avatarUrl", "").takeIf { it.isNotBlank() },
                        signature = o.optString("signature", "").takeIf { it.isNotBlank() && it != "null" },
                    ),
                )
            }
        }
    }

    fun artistHot(json: JSONObject, fallbackName: String): AlbumBrief? {
        val songs = json.optJSONArray("hotSongs")?.let { tracksFromArray(it) }.orEmpty()
        val artist = json.optJSONObject("artist")
        val name = artist?.optString("name", "")?.ifBlank { null } ?: fallbackName
        val cover = artist?.optString("picUrl", "")
            ?.ifBlank { artist.optString("img1v1Url", "") }
            ?.takeIf { it.isNotBlank() }
            ?: songs.firstOrNull()?.coverUrl
        if (songs.isEmpty() && NcmJson.apiCode(json) != 200) return null
        return AlbumBrief(
            id = artist?.optLong("id", 0L) ?: 0L,
            name = name,
            coverUrl = cover,
            artist = name,
            songs = songs,
        )
    }

    fun dailySongs(json: JSONObject): List<TrackRow> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val data = json.optJSONObject("data") ?: json
        val arr = data.optJSONArray("dailySongs") ?: return emptyList()
        return tracksFromArray(arr)
    }

    fun personalFmTracks(json: JSONObject): List<TrackRow> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val arr = json.optJSONArray("data") ?: return emptyList()
        return tracksFromArray(arr)
    }

    /** `/playmode/intelligence/list`：条目多为 `{ songInfo }`，也兼容直接歌曲对象。 */
    fun intelligenceTracks(json: JSONObject): List<TrackRow> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val arr = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val song = o.optJSONObject("songInfo")
                    ?: o.optJSONObject("song")
                    ?: o.optJSONObject("songData")
                    ?: o
                NcmLibraryParse.trackFromSongObject(song)?.let { add(it) }
            }
        }
    }

    /** `/search/suggest`：优先 mobile 的 allMatch，其次网页端歌曲/歌手/歌单名。 */
    fun searchSuggestKeywords(json: JSONObject): List<String> {
        val result = json.optJSONObject("result") ?: json.optJSONObject("data") ?: return emptyList()
        val seen = linkedSetOf<String>()
        val allMatch = result.optJSONArray("allMatch")
        if (allMatch != null) {
            for (i in 0 until allMatch.length()) {
                val o = allMatch.optJSONObject(i) ?: continue
                val word = o.optString("keyword", "").trim()
                if (word.isNotEmpty()) seen.add(word)
            }
        }
        fun takeNames(arr: JSONArray?, key: String = "name") {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val word = o.optString(key, "").trim()
                if (word.isNotEmpty()) seen.add(word)
            }
        }
        takeNames(result.optJSONArray("songs"))
        takeNames(result.optJSONArray("artists"))
        takeNames(result.optJSONArray("albums"))
        takeNames(result.optJSONArray("playlists"))
        return seen.toList()
    }

    fun hotSearchWords(json: JSONObject): List<HotSearchWord> {
        val arr = json.optJSONArray("data")
            ?: json.optJSONObject("result")?.optJSONArray("hots")
            ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val word = o.optString("searchWord", "")
                    .ifBlank { o.optString("first", "") }
                    .ifBlank { o.optJSONObject("searchWord")?.optString("word", "").orEmpty() }
                    .trim()
                if (word.isEmpty()) continue
                add(
                    HotSearchWord(
                        word = word,
                        content = o.optString("content", "").takeIf { it.isNotBlank() },
                        highlighted = o.optInt("iconType", 0) > 0 ||
                            o.optInt("score", 0) >= 90_000,
                    ),
                )
            }
        }
    }

    fun albumBrief(json: JSONObject, albumId: Long): AlbumBrief? {
        val songs = json.optJSONArray("songs")?.let { tracksFromArray(it) }.orEmpty()
        val album = json.optJSONObject("album")
        val name = album?.optString("name", "")?.ifBlank { null }
            ?: json.optString("name", "").takeIf { it.isNotBlank() }
            ?: "专辑"
        val cover = NcmLibraryParse.ncmHttpsImage(
            album?.optString("picUrl", "")?.takeIf { it.isNotBlank() }
                ?: album?.optString("blurPicUrl", "")?.takeIf { it.isNotBlank() },
        )
        val artistObj = album?.optJSONObject("artist")
        val artistsArr = album?.optJSONArray("artists")
        val artist = artistObj?.optString("name", "")?.takeIf { it.isNotBlank() }
            ?: artistNames(artistsArr)
        val artistId = artistObj?.optLong("id", 0L)?.takeIf { it > 0L }
            ?: artistsArr?.optJSONObject(0)?.optLong("id", 0L)
            ?: 0L
        val artistCover = NcmLibraryParse.ncmHttpsImage(
            firstNonBlank(
                artistObj?.optString("picUrl"),
                artistObj?.optString("img1v1Url"),
                artistsArr?.optJSONObject(0)?.optString("picUrl"),
            ),
        )
        if (songs.isEmpty() && NcmJson.apiCode(json) != 200) return null
        val size = album?.optInt("size", 0)?.takeIf { it > 0 } ?: songs.size
        return AlbumBrief(
            id = album?.optLong("id", albumId) ?: albumId,
            name = name,
            coverUrl = cover,
            artist = artist,
            artistId = artistId,
            artistCoverUrl = artistCover,
            songs = songs,
            publishTime = album?.optLong("publishTime", 0L) ?: 0L,
            company = album?.optString("company", "")?.trim()
                ?.takeIf { it.isNotEmpty() && it != "null" },
            description = firstNonBlank(
                album?.optString("description"),
                album?.optString("briefDesc"),
            ),
            type = album?.optString("type", "")?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
            alias = firstAlias(album?.optJSONArray("alias"), album?.optJSONArray("transNames")),
            size = size,
        )
    }

    fun albumDynamic(json: JSONObject): AlbumDynamic? {
        if (NcmJson.apiCode(json) != 200) return null
        return AlbumDynamic(
            isSub = json.optBoolean("isSub", json.optBoolean("subed", false)),
            subCount = json.optInt("subCount", json.optInt("subscribedCount", 0)).coerceAtLeast(0),
            commentCount = json.optInt("commentCount", 0).coerceAtLeast(0),
            shareCount = json.optInt("shareCount", 0).coerceAtLeast(0),
        )
    }

    fun collectedAlbumPage(json: JSONObject, pageSize: Int): Triple<List<CollectedAlbum>, Int, Boolean> {
        if (NcmJson.apiCode(json) != 200) {
            return Triple(emptyList(), 0, false)
        }
        val arr = json.optJSONArray("data")
            ?: json.optJSONArray("albums")
            ?: json.optJSONObject("data")?.optJSONArray("albums")
            ?: org.json.JSONArray()
        val albums = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                collectedAlbum(o)?.let { add(it) }
            }
        }
        val total = json.optInt("count", json.optInt("total", albums.size)).coerceAtLeast(albums.size)
        val more = when {
            json.has("hasMore") -> json.optBoolean("hasMore")
            json.has("more") -> json.optBoolean("more")
            else -> albums.size >= pageSize && albums.size < total
        }
        return Triple(albums, total, more)
    }

    fun searchAlbums(json: JSONObject): List<CollectedAlbum> {
        val result = json.optJSONObject("result") ?: json.optJSONObject("data") ?: return emptyList()
        val arr = result.optJSONArray("albums") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                collectedAlbum(o)?.let { add(it) }
            }
        }
    }

    fun collectedAlbum(o: JSONObject): CollectedAlbum? {
        val id = o.optLong("id", 0L)
        if (id <= 0L) return null
        val artistObj = o.optJSONObject("artist")
        val artistsArr = o.optJSONArray("artists")
        val artist = artistObj?.optString("name", "")?.takeIf { it.isNotBlank() && it != "null" }
            ?: artistNames(artistsArr)
        val artistId = artistObj?.optLong("id", 0L)?.takeIf { it > 0L }
            ?: artistsArr?.optJSONObject(0)?.optLong("id", 0L)
            ?: 0L
        return CollectedAlbum(
            id = id,
            name = o.optString("name", "专辑").ifBlank { "专辑" },
            coverUrl = NcmLibraryParse.ncmHttpsImage(
                firstNonBlank(
                    o.optString("picUrl"),
                    o.optString("blurPicUrl"),
                    o.optString("cover"),
                    o.optString("coverImgUrl"),
                ),
            ),
            artist = artist,
            artistId = artistId,
            artistCoverUrl = NcmLibraryParse.ncmHttpsImage(
                firstNonBlank(
                    artistObj?.optString("picUrl"),
                    artistObj?.optString("img1v1Url"),
                    artistsArr?.optJSONObject(0)?.optString("picUrl"),
                ),
            ),
            size = o.optInt("size", o.optInt("size", 0)).coerceAtLeast(0),
            publishTime = o.optLong("publishTime", 0L),
            company = o.optString("company", "").trim().takeIf { it.isNotEmpty() && it != "null" },
            type = o.optString("type", "").trim().takeIf { it.isNotEmpty() && it != "null" },
            alias = firstAlias(o.optJSONArray("alias"), o.optJSONArray("transNames")),
        )
    }

    private fun firstAlias(alias: JSONArray?, trans: JSONArray?): String? {
        fun first(arr: JSONArray?): String? {
            if (arr == null || arr.length() == 0) return null
            val s = arr.optString(0, "").trim()
            return s.takeIf { it.isNotEmpty() && it != "null" }
        }
        return first(alias) ?: first(trans)
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (v in values) {
            val s = v?.trim().orEmpty()
            if (s.isNotEmpty() && s != "null") return s
        }
        return null
    }

    fun charts(json: JSONObject): List<ChartSummary> {
        val arr = json.optJSONArray("list") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                if (id <= 0L) continue
                add(
                    ChartSummary(
                        id = id,
                        name = o.optString("name", "榜单").ifBlank { "榜单" },
                        coverUrl = o.optString("coverImgUrl", "").takeIf { it.isNotBlank() },
                        updateFrequency = o.optString("updateFrequency", "").takeIf { it.isNotBlank() },
                        playCount = o.optLong("playCount", 0L),
                    ),
                )
            }
        }
    }

    fun formatPlayCount(n: Long): String = when {
        n >= 100_000_000L -> {
            val v = n / 100_000_000.0
            if (v >= 10) "${v.toInt()}亿" else String.format("%.1f亿", v).replace(".0亿", "亿")
        }
        n >= 10_000L -> {
            val v = n / 10_000.0
            if (v >= 10) "${v.toInt()}万" else String.format("%.1f万", v).replace(".0万", "万")
        }
        else -> n.toString()
    }

    private fun artistNames(arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        val names = buildList {
            for (i in 0 until arr.length()) {
                val n = arr.optJSONObject(i)?.optString("name", "")?.trim().orEmpty()
                if (n.isNotEmpty()) add(n)
            }
        }
        return names.takeIf { it.isNotEmpty() }?.joinToString(" / ")
    }

    private fun playlistCardsFromArray(
        arr: JSONArray,
        picKey: String,
        playKey: String,
    ): List<RecommendPlaylistCard> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            if (id <= 0L) continue
            add(
                RecommendPlaylistCard(
                    id = id,
                    name = o.optString("name", "歌单").ifBlank { "歌单" },
                    coverUrl = o.optString(picKey, "")
                        .ifBlank { o.optString("coverImgUrl", "") }
                        .ifBlank { o.optString("picUrl", "") }
                        .takeIf { it.isNotBlank() },
                    playCount = o.optLong(playKey, o.optLong("playCount", 0L)),
                ),
            )
        }
    }

    private fun mvsFromArray(arr: JSONArray): List<RecommendMvCard> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val src = o.optJSONObject("mv") ?: o
            val id = src.optLong("id", 0L)
            if (id <= 0L) continue
            add(
                RecommendMvCard(
                    id = id,
                    name = src.optString("name", "MV").ifBlank { "MV" },
                    coverUrl = src.optString("picUrl", "")
                        .ifBlank { src.optString("cover", "") }
                        .ifBlank { src.optString("imgurl", "") }
                        .takeIf { it.isNotBlank() },
                    artist = src.optString("artistName", "")
                        .ifBlank { artistNames(src.optJSONArray("artists")).orEmpty() }
                        .takeIf { it.isNotBlank() },
                    playCount = src.optLong("playCount", 0L),
                ),
            )
        }
    }

    private fun tracksFromArray(arr: JSONArray): List<TrackRow> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            NcmLibraryParse.trackFromSongObject(o)?.let { add(it) }
        }
    }
}
