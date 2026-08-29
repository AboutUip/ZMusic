package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject

internal object NcmLibraryParse {

    fun playlistsFromUserPlaylist(json: JSONObject, selfUserId: Long): List<PlaylistSummary> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val arr = json.optJSONArray("playlist") ?: return emptyList()
        val raw = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(parsePlaylistItem(o, selfUserId))
            }
        }
        return raw.sortedWith(
            compareBy<PlaylistSummary> { pl ->
                when {
                    pl.isHeartPlaylist -> 0
                    pl.isOwned && !pl.isHeartPlaylist -> 1
                    pl.isSubscribed -> 2
                    else -> 3
                }
            }.thenBy { it.name },
        )
    }

    private fun parsePlaylistItem(o: JSONObject, selfUserId: Long): PlaylistSummary {
        val id = o.optLong("id", 0L)
        val name = o.optString("name", "歌单")
        val cover = o.optString("coverImgUrl", o.optString("coverUrl", "")).takeIf { it.isNotBlank() }
        val trackCount = o.optInt("trackCount", 0)
        val specialType = o.optInt("specialType", 0)
        val creator = o.optJSONObject("creator")
        val creatorId = creator?.optLong("userId", -1L) ?: -1L
        val subscribed = o.optBoolean("subscribed", false)
        val isOwned = selfUserId > 0L && creatorId == selfUserId
        val isHeart = isSelfHeartPlaylist(
            selfUid = selfUserId,
            creatorId = creatorId,
            subscribed = subscribed,
            specialType = specialType,
            name = name,
        )
        val playCount = o.optLong("playCount", 0L)
        return PlaylistSummary(
            id = id,
            name = name,
            coverUrl = cover,
            trackCount = trackCount,
            isHeartPlaylist = isHeart,
            isOwned = isOwned,
            isSubscribed = subscribed,
            playCount = playCount,
        )
    }

    fun tracksFromPlaylistDetail(json: JSONObject): List<TrackRow> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val pl = json.optJSONObject("playlist") ?: return emptyList()
        val tracks = pl.optJSONArray("tracks") ?: return emptyList()
        val out = ArrayList<TrackRow>(tracks.length())
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            trackFromSongObject(t)?.let { out.add(it) }
        }
        return out
    }

    fun tracksFromSongDetail(json: JSONObject): List<TrackRow> {
        if (NcmJson.apiCode(json) != 200 && NcmJson.apiCode(json) != -1) {
            if (!json.has("songs") && json.optJSONArray("tracks") == null) return emptyList()
        }
        val songs = json.optJSONArray("songs")
            ?: json.optJSONObject("data")?.optJSONArray("songs")
            ?: json.optJSONArray("tracks")
            ?: return emptyList()
        return buildList {
            for (i in 0 until songs.length()) {
                val t = songs.optJSONObject(i) ?: continue
                trackFromSongObject(t)?.let { add(it) }
            }
        }
    }

    fun trackFromSongObject(t: JSONObject): TrackRow? {
        val id = t.optLong("id", 0L)
        if (id <= 0L) return null
        val name = t.optString("name", "").ifBlank { return null }
        val ar = t.optJSONArray("ar") ?: t.optJSONArray("artists") ?: JSONArray()
        val artistRefs = artistRefsFromArray(ar)
        val artists = artistRefs.joinToString(" / ") { it.name }
        val al = t.optJSONObject("al") ?: t.optJSONObject("album")
        val album = al?.optString("name", "")?.takeIf { it.isNotBlank() }
        val pc = t.optJSONObject("pc")
        val cover = firstHttpUrl(
            al?.optString("picUrl"),
            al?.optString("blurPicUrl"),
            t.optString("picUrl"),
            t.optString("albumPic"),
            pc?.optString("picUrl"),
            pc?.optString("albumPicUrl"),
            pc?.optString("pic"),
        )
        val dt = t.optLong("dt", t.optLong("duration", 0L))
        return TrackRow(
            id = id,
            name = name,
            artists = artists.ifBlank { "—" },
            album = album,
            durationMs = dt,
            coverUrl = cover,
            artistRefs = artistRefs,
        )
    }

    fun artistRefsFromArray(ar: JSONArray): List<TrackArtist> = buildList {
        for (i in 0 until ar.length()) {
            val o = ar.optJSONObject(i) ?: continue
            val n = o.optString("name", "").trim()
            if (n.isEmpty() || n == "null") continue
            add(TrackArtist(id = o.optLong("id", 0L), name = n))
        }
    }

    private fun firstHttpUrl(vararg raw: String?): String? {
        for (s in raw) {
            val v = s?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: continue
            if (v.startsWith("http://") || v.startsWith("https://")) return v
            if (v.startsWith("//")) return "https:$v"
        }
        return null
    }
}

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
        return playlistCardsFromArray(arr)
    }

    fun charts(json: JSONObject): List<ChartSummary> {
        val arr = json.optJSONArray("list")
            ?: json.optJSONObject("data")?.optJSONArray("list")
            ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                if (id <= 0L) continue
                add(
                    ChartSummary(
                        id = id,
                        name = o.optString("name", "榜单"),
                        coverUrl = o.optString("coverImgUrl", "").takeIf { it.isNotBlank() },
                        updateFrequency = o.optString("updateFrequency", "").takeIf { it.isNotBlank() },
                        playCount = o.optLong("playCount", 0L),
                    ),
                )
            }
        }
    }

    private fun playlistCardsFromArray(arr: JSONArray): List<RecommendPlaylistCard> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            if (id <= 0L) continue
            add(
                RecommendPlaylistCard(
                    id = id,
                    name = o.optString("name", "歌单"),
                    coverUrl = o.optString("picUrl", o.optString("coverImgUrl", "")).takeIf { it.isNotBlank() },
                    playCount = o.optLong("playCount", 0L),
                ),
            )
        }
    }
}
