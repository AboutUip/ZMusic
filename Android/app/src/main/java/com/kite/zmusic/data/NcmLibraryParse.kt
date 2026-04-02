package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject

internal object NcmLibraryParse {

    fun userProfileFromDetail(json: JSONObject): UserProfileBrief? {
        if (NcmJson.apiCode(json) != 200) return null
        val profile = json.optJSONObject("profile") ?: return null
        val uid = profile.optLong("userId", 0L)
        if (uid <= 0L) return null
        val lv = json.optInt("level", -1)
        return UserProfileBrief(
            userId = uid,
            nickname = profile.optString("nickname", "用户").ifBlank { "用户" },
            avatarUrl = profile.optString("avatarUrl", "").takeIf { it.isNotBlank() },
            signature = profile.optString("signature", "").takeIf { it.isNotBlank() },
            level = lv.takeIf { it >= 0 },
            listenSongs = profile.optLong("listenSongs", -1L).takeIf { it >= 0 },
        )
    }

    fun subcountFromJson(json: JSONObject): SubcountBrief? {
        if (NcmJson.apiCode(json) != 200) return null
        return SubcountBrief(
            subPlaylistCount = json.optInt("subPlaylistCount", 0),
            createdPlaylistCount = json.optInt("createdPlaylistCount", 0),
            subArtistCount = json.optInt("artistCount", json.optInt("subArtistCount", 0)),
            subAlbumCount = json.optInt("albumCount", json.optInt("subAlbumCount", 0)),
        )
    }

    fun likeIdsCount(json: JSONObject): Int {
        if (NcmJson.apiCode(json) != 200) return 0
        val ids = json.optJSONArray("ids") ?: return 0
        return ids.length()
    }

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
        val isHeart = specialType == 5 || name == "我喜欢的音乐"
        val creator = o.optJSONObject("creator")
        val creatorId = creator?.optLong("userId", -1L) ?: -1L
        val subscribed = o.optBoolean("subscribed", false)
        val isOwned = creatorId == selfUserId
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
            parseTrackObject(t)?.let { out.add(it) }
        }
        return out
    }

    fun trackIdsFromPlaylistDetail(json: JSONObject): List<Long> {
        val pl = json.optJSONObject("playlist") ?: return emptyList()
        val ids = pl.optJSONArray("trackIds") ?: return emptyList()
        return buildList {
            for (i in 0 until ids.length()) {
                try {
                    val id = ids.getLong(i)
                    if (id > 0L) add(id)
                } catch (_: Exception) {
                    val o = ids.optJSONObject(i)
                    if (o != null) {
                        val id = o.optLong("id", 0L)
                        if (id > 0L) add(id)
                    } else {
                        ids.optString(i, "").toLongOrNull()?.let { if (it > 0L) add(it) }
                    }
                }
            }
        }
    }

    fun tracksFromSongDetail(json: JSONObject): List<TrackRow> {
        if (NcmJson.apiCode(json) != 200) return emptyList()
        val songs = json.optJSONArray("songs") ?: return emptyList()
        return buildList {
            for (i in 0 until songs.length()) {
                val t = songs.optJSONObject(i) ?: continue
                parseTrackObject(t)?.let { add(it) }
            }
        }
    }

    private fun parseTrackObject(t: JSONObject): TrackRow? {
        val id = t.optLong("id", 0L)
        if (id <= 0L) return null
        val name = t.optString("name", "").ifBlank { return null }
        val ar = t.optJSONArray("ar") ?: JSONArray()
        val artists = buildList {
            for (i in 0 until ar.length()) {
                val n = ar.optJSONObject(i)?.optString("name", "")?.trim().orEmpty()
                if (n.isNotEmpty()) add(n)
            }
        }.joinToString(" / ")
        val al = t.optJSONObject("al")
        val album = al?.optString("name", "")?.takeIf { it.isNotBlank() }
        val cover = al?.optString("picUrl", "")?.takeIf { it.isNotBlank() }
        val dt = t.optLong("dt", 0L)
        return TrackRow(
            id = id,
            name = name,
            artists = artists.ifBlank { "—" },
            album = album,
            durationMs = dt,
            coverUrl = cover,
        )
    }
}
