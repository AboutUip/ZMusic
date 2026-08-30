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

    fun profileFromUserDetail(json: JSONObject): UserProfileBrief? {
        val profile = json.optJSONObject("profile") ?: return null
        val userId = profile.optLong("userId", 0L)
        if (userId <= 0L) return null
        return UserProfileBrief(
            userId = userId,
            nickname = profile.optString("nickname", "用户").ifBlank { "用户" },
            avatarUrl = profile.optString("avatarUrl", "").takeIf { it.isNotBlank() },
            signature = profile.optString("signature", "").takeIf { it.isNotBlank() },
            backgroundUrl = profile.optString("backgroundUrl", "").takeIf { it.isNotBlank() },
            follows = profile.optLong("follows", 0L).takeIf { it > 0L },
            followeds = profile.optLong("followeds", 0L).takeIf { it > 0L },
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

    fun likedIdsFromLikeCheck(json: JSONObject): Set<Long> {
        if (NcmJson.apiCode(json) != 200) return emptySet()
        val fromData = longIdsFromArray(json.optJSONArray("data"))
        if (fromData.isNotEmpty()) return fromData
        return longIdsFromArray(json.optJSONArray("ids"))
    }

    fun isTrackLiked(json: JSONObject, trackId: Long): Boolean =
        trackId > 0L && likedIdsFromLikeCheck(json).contains(trackId)

    fun likeIdsFromLikeList(json: JSONObject): Set<Long> {
        val arr = json.optJSONArray("ids")
            ?: json.optJSONObject("data")?.optJSONArray("ids")
            ?: return emptySet()
        return longIdsFromArray(arr)
    }

    fun isSubscribed(json: JSONObject): Boolean =
        json.optBoolean("subscribed", json.optBoolean("isSub", json.optBoolean("followed", false)))

    fun searchHotTerms(json: JSONObject): List<String> {
        val arr = json.optJSONArray("data") ?: json.optJSONArray("hots") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val t = o.optString("searchWord", o.optString("first", o.optString("keyword", ""))).trim()
                if (t.isNotEmpty()) add(t)
            }
        }
    }

    private fun longIdsFromArray(arr: JSONArray?): Set<Long> {
        if (arr == null) return emptySet()
        return buildSet {
            for (i in 0 until arr.length()) {
                val id = when (val v = arr.opt(i)) {
                    is Number -> v.toLong()
                    is String -> v.toLongOrNull() ?: 0L
                    is JSONObject -> v.optLong("id", 0L)
                    else -> arr.optLong(i, 0L)
                }
                if (id > 0L) add(id)
            }
        }
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

    fun topPlaylists(json: JSONObject): List<RecommendPlaylistCard> {
        val arr = json.optJSONArray("playlists")
            ?: json.optJSONObject("data")?.optJSONArray("playlists")
            ?: return emptyList()
        return playlistCardsFromArray(arr)
    }

    fun dailySongs(json: JSONObject): List<TrackRow> {
        val songs = json.optJSONObject("data")?.optJSONArray("dailySongs")
            ?: json.optJSONArray("recommend")
            ?: return emptyList()
        return buildList {
            for (i in 0 until songs.length()) {
                val o = songs.optJSONObject(i) ?: continue
                NcmLibraryParse.trackFromSongObject(o)?.let { add(it) }
            }
        }
    }

    fun recommendResourcePlaylists(json: JSONObject): List<RecommendPlaylistCard> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val arr = json.optJSONArray("recommend") ?: return emptyList()
        return playlistCardsFromArray(arr)
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

    private fun mvsFromArray(arr: org.json.JSONArray): List<RecommendMvCard> = buildList {
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
                    artist = src.optString("artistName", "").takeIf { it.isNotBlank() },
                    playCount = src.optLong("playCount", 0L),
                ),
            )
        }
    }

    fun collectedAlbums(json: JSONObject): List<CollectedAlbum> {
        val arr = json.optJSONArray("data")
            ?: json.optJSONArray("albums")
            ?: json.optJSONArray("hotAlbums")
            ?: json.optJSONObject("data")?.optJSONArray("albums")
            ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                if (id <= 0L) continue
                val artistObj = o.optJSONObject("artist")
                val artistsArr = o.optJSONArray("artists")
                val artist = artistObj?.optString("name", "")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: buildString {
                        if (artistsArr == null) return@buildString
                        for (j in 0 until artistsArr.length()) {
                            val n = artistsArr.optJSONObject(j)?.optString("name", "")?.trim().orEmpty()
                            if (n.isEmpty() || n == "null") continue
                            if (isNotEmpty()) append(" / ")
                            append(n)
                        }
                    }.takeIf { it.isNotBlank() }
                add(
                    CollectedAlbum(
                        id = id,
                        name = o.optString("name", "专辑").ifBlank { "专辑" },
                        coverUrl = o.optString("picUrl", "")
                            .ifBlank { o.optString("blurPicUrl", "") }
                            .ifBlank { o.optString("coverImgUrl", "") }
                            .takeIf { it.isNotBlank() },
                        artist = artist,
                    ),
                )
            }
        }
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

    fun personalFmTracks(json: JSONObject): List<TrackRow> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val arr = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                NcmLibraryParse.trackFromSongObject(o)?.let { add(it) }
            }
        }
    }

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

    fun artistMvs(json: JSONObject): List<RecommendMvCard> {
        val arr = json.optJSONArray("mvs")
            ?: json.optJSONObject("data")?.optJSONArray("mvs")
            ?: json.optJSONArray("mv")
            ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                if (id <= 0L) continue
                add(
                    RecommendMvCard(
                        id = id,
                        name = o.optString("name", "MV").ifBlank { "MV" },
                        coverUrl = o.optString("imgurl", "")
                            .ifBlank { o.optString("cover", "") }
                            .ifBlank { o.optString("picUrl", "") }
                            .takeIf { it.isNotBlank() },
                        artist = o.optString("artistName", "").takeIf { it.isNotBlank() },
                        playCount = o.optLong("playCount", 0L),
                    ),
                )
            }
        }
    }
}
