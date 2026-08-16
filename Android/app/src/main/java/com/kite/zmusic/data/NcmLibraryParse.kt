package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject

internal object NcmLibraryParse {

    fun userProfileFromDetail(json: JSONObject): UserProfileBrief? {
        if (NcmJson.apiCode(json) != 200) return null
        val profile = json.optJSONObject("profile") ?: return null
        val uid = profile.optLong("userId", 0L)
        if (uid <= 0L) return null
        val lv = json.optInt("level", -1).takeIf { it >= 0 }
            ?: profile.optInt("level", -1).takeIf { it >= 0 }
        val listen = json.optLong("listenSongs", -1L).takeIf { it >= 0 }
            ?: profile.optLong("listenSongs", -1L).takeIf { it >= 0 }
        val bgDirect = ncmHttpsImage(
            firstHttpUrl(
                profile.optString("backgroundUrl", ""),
                profile.optString("backgroundImgUrl", ""),
                json.optString("backgroundUrl", ""),
                json.optString("backgroundImgUrl", ""),
            ),
            upgradeParam = "1080y720",
        )
        val bgId = profile.optLong("backgroundImgId", 0L).takeIf { it > 0L }
            ?: profile.optString("backgroundImgIdStr", "").trim().toLongOrNull()?.takeIf { it > 0L }
        val bg = bgDirect ?: bgId?.let { ncmPicUrlFromId(it) }
        val vipType = profile.optInt("vipType", 0)
        val vipKind = when {
            vipType <= 0 -> VipKind.None
            else -> VipKind.Vip
        }
        return UserProfileBrief(
            userId = uid,
            nickname = profile.optString("nickname", "用户").ifBlank { "用户" },
            avatarUrl = ncmHttpsImage(profile.optString("avatarUrl", "")),
            signature = profile.optString("signature", "").takeIf { it.isNotBlank() && it != "null" },
            level = lv,
            listenSongs = listen,
            backgroundUrl = bg,
            vipKind = vipKind,
        )
    }

    fun mergeLevelIntoProfile(profile: UserProfileBrief, json: JSONObject): UserProfileBrief {
        val data = json.optJSONObject("data") ?: json
        val level = data.optInt("level", -1).takeIf { it >= 0 } ?: profile.level
        val nowPlay = data.optLong("nowPlayCount", -1L).takeIf { it >= 0 } ?: profile.nowPlayCount
        val nextPlay = data.optLong("nextPlayCount", -1L).takeIf { it > 0 } ?: profile.nextPlayCount
        val rawProgress = data.optDouble("progress", Double.NaN)
        val progress = when {
            !rawProgress.isNaN() && rawProgress >= 0.0 -> {
                val v = rawProgress.toFloat()
                if (v > 1f) (v / 100f).coerceIn(0f, 1f) else v.coerceIn(0f, 1f)
            }
            nowPlay != null && nextPlay != null && nextPlay > 0L -> {
                (nowPlay.toFloat() / nextPlay.toFloat()).coerceIn(0f, 1f)
            }
            else -> profile.levelProgress
        }
        return profile.copy(
            level = level,
            levelProgress = progress,
            nowPlayCount = nowPlay,
            nextPlayCount = nextPlay,
        )
    }

    /**
     * `/vip/info` 与 `/vip/info/v2`：黑胶 SVIP（redplus）优先于普通 VIP。
     */
    data class VipBrief(
        val kind: VipKind,
        val iconUrl: String? = null,
    )

    fun vipKindFromInfo(json: JSONObject): VipKind = vipBriefFromInfo(json).kind

    fun vipBriefFromInfo(json: JSONObject): VipBrief {
        var data = json.optJSONObject("data") ?: json
        if (data.optJSONObject("associator") == null &&
            data.optJSONObject("redplus") == null &&
            data.optJSONObject("redPlus") == null
        ) {
            data.optJSONObject("data")?.let { data = it }
        }
        val now = System.currentTimeMillis()
        fun expireMillis(raw: Long): Long = when {
            raw >= 1_000_000_000_000L -> raw
            raw >= 1_000_000_000L -> raw * 1000L
            else -> 0L
        }
        fun active(obj: JSONObject?): Boolean {
            if (obj == null) return false
            val exp = expireMillis(obj.optLong("expireTime", 0L))
            return exp > now
        }
        fun iconOf(obj: JSONObject?): String? = ncmHttpsImage(
            firstHttpUrl(
                obj?.optString("iconUrl", ""),
                obj?.optString("dynamicIconUrl", ""),
            ),
        )
        val redplus = data.optJSONObject("redplus") ?: data.optJSONObject("redPlus")
            ?: data.optJSONObject("svip")
        if (active(redplus)) {
            return VipBrief(VipKind.Svip, iconOf(redplus))
        }
        val associator = data.optJSONObject("associator")
        val pack = data.optJSONObject("musicPackage")
        if (active(associator) || active(pack)) {
            return VipBrief(VipKind.Vip, iconOf(associator) ?: iconOf(pack))
        }
        return VipBrief(VipKind.None, null)
    }

    /**
     * 网易图床：https；仅当原地址已有 `param=` 时才换成大图，避免给无参/签名 URL 追加参数导致 403。
     */
    internal fun ncmHttpsImage(url: String?, upgradeParam: String? = null): String? {
        val raw = firstHttpUrl(url) ?: return null
        var u = if (raw.startsWith("http://")) "https://" + raw.substring(7) else raw
        if (!upgradeParam.isNullOrBlank() && Regex("""param=\d+y\d+""").containsMatchIn(u)) {
            u = u.replace(Regex("""param=\d+y\d+"""), "param=$upgradeParam")
        }
        return u
    }

    /** 用 backgroundImgId 拼封面，算法与网易云 picUrl 一致。 */
    internal fun ncmPicUrlFromId(id: Long): String? {
        if (id <= 0L) return null
        val idStr = id.toString()
        val magic = "3go8&$8*3*3h0k(2)2".toByteArray(Charsets.UTF_8)
        val raw = idStr.toByteArray(Charsets.UTF_8)
        val mixed = ByteArray(raw.size) { i ->
            (raw[i].toInt() xor magic[i % magic.size].toInt()).toByte()
        }
        val md5 = java.security.MessageDigest.getInstance("MD5").digest(mixed)
        val enc = android.util.Base64.encodeToString(md5, android.util.Base64.NO_WRAP)
            .replace('/', '_')
            .replace('+', '-')
        return "https://p1.music.126.net/$enc/$id.jpg"
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

    fun likeIdsCount(json: JSONObject): Int = tryLikeIdsInOrder(json)?.size ?: 0

    /**
     * `/likelist`：解析失败返回 null（不要当成「0 首喜欢」）。
     * 官方文档写明此列表无序，只做红心集合，不能当「我喜欢的音乐」展示序。
     * 兼容根级 / data 包装、JSONArray、逗号分隔字符串。
     */
    fun tryLikeIdsInOrder(json: JSONObject): List<Long>? {
        val code = NcmJson.apiCode(json)
        if (code != 200 && code != -1) return null
        val arr = likeIdsArray(json) ?: return null
        return longIdsInOrder(arr)
    }

    /** `/likelist` 的 id（无序）。解析失败时为空列表。 */
    fun likeIdsInOrder(json: JSONObject): List<Long> = tryLikeIdsInOrder(json).orEmpty()

    fun likeIdsFromLikeList(json: JSONObject): Set<Long> = likeIdsInOrder(json).toSet()

    private fun likeIdsArray(json: JSONObject): JSONArray? {
        json.optJSONArray("ids")?.let { return it }
        json.optJSONObject("data")?.optJSONArray("ids")?.let { return it }
        json.optJSONArray("checkIds")?.let { return it }
        json.optJSONObject("data")?.optJSONArray("checkIds")?.let { return it }
        val raw = json.optString("ids", "").trim().ifBlank {
            json.optJSONObject("data")?.optString("ids", "")?.trim().orEmpty()
        }
        if (raw.isNotEmpty() && raw != "null") {
            val parts = raw.removePrefix("[").removeSuffix("]").split(',')
            val arr = JSONArray()
            for (p in parts) {
                p.trim().toLongOrNull()?.takeIf { it > 0L }?.let { arr.put(it) }
            }
            if (arr.length() > 0) return arr
        }
        if (json.has("ids") || json.optJSONObject("data")?.has("ids") == true) {
            return JSONArray()
        }
        return null
    }

    /**
     * `/song/like/check`：返回被喜爱的 id 子集（字段可能是 `data` 或 `ids`）。
     */
    fun likedIdsFromLikeCheck(json: JSONObject): Set<Long> {
        if (NcmJson.apiCode(json) != 200) return emptySet()
        val fromData = longIdsFromArray(json.optJSONArray("data"))
        if (fromData.isNotEmpty()) return fromData
        return longIdsFromArray(json.optJSONArray("ids"))
    }

    fun isTrackLiked(json: JSONObject, trackId: Long): Boolean =
        trackId > 0L && likedIdsFromLikeCheck(json).contains(trackId)

    private fun longIdsInOrder(arr: JSONArray?): List<Long> {
        if (arr == null || arr.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                when (val v = arr.opt(i)) {
                    is Number -> v.toLong().takeIf { it > 0L }?.let { add(it) }
                    is String -> v.toLongOrNull()?.takeIf { it > 0L }?.let { add(it) }
                    is JSONObject -> v.optLong("id", 0L).takeIf { it > 0L }?.let { add(it) }
                }
            }
        }
    }

    private fun longIdsFromArray(arr: JSONArray?): Set<Long> {
        if (arr == null || arr.length() == 0) return emptySet()
        return longIdsInOrder(arr).toSet()
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

    fun playlistFromCreate(json: JSONObject): PlaylistSummary? {
        if (NcmJson.apiCode(json) != 200) return null
        val pl = json.optJSONObject("playlist") ?: json.optJSONObject("data")
        val id = pl?.optLong("id", 0L)?.takeIf { it > 0L }
            ?: json.optLong("id", 0L).takeIf { it > 0L }
            ?: return null
        val name = listOf(
            pl?.optString("name", ""),
            json.optString("name", ""),
        ).firstOrNull { !it.isNullOrBlank() && it != "null" }.orEmpty()
        val cover = pl?.optString("coverImgUrl", pl.optString("coverUrl", ""))
            ?.takeIf { it.isNotBlank() && it != "null" }
        return PlaylistSummary(
            id = id,
            name = name.ifBlank { "新建歌单" },
            coverUrl = cover,
            trackCount = pl?.optInt("trackCount", 0) ?: 0,
            isHeartPlaylist = false,
            isOwned = true,
            isSubscribed = false,
            playCount = pl?.optLong("playCount", 0L) ?: 0L,
        )
    }

    fun isPlaylistTrackDuplicate(json: JSONObject): Boolean {
        val blob = buildString {
            append(json.optString("message", ""))
            append(' ')
            append(json.optString("msg", ""))
            json.optJSONObject("data")?.let { data ->
                append(' ')
                append(data.optString("message", ""))
                append(' ')
                append(data.optString("msg", ""))
            }
        }
        return blob.contains("重复") ||
            blob.contains("已存在") ||
            blob.contains("已经在") ||
            blob.contains("已在歌单")
    }

    fun playlistMetaFromDetail(json: JSONObject): PlaylistSubscribeMeta? {
        if (NcmJson.apiCode(json) != 200) return null
        val pl = json.optJSONObject("playlist") ?: return null
        val id = pl.optLong("id", 0L)
        if (id <= 0L) return null
        val name = pl.optString("name", "歌单").ifBlank { "歌单" }
        val cover = pl.optString("coverImgUrl", pl.optString("coverUrl", ""))
            .takeIf { it.isNotBlank() }
        val creator = pl.optJSONObject("creator")
        val creatorId = creator?.optLong("userId", 0L)?.takeIf { it > 0L }
            ?: pl.optLong("userId", 0L)
        val creatorName = listOf(
            creator?.optString("nickname", ""),
            creator?.optString("nickName", ""),
        ).firstOrNull { !it.isNullOrBlank() && it != "null" }
        val creatorAvatar = listOf(
            creator?.optString("avatarUrl", ""),
            creator?.optString("avatarImgUrl", ""),
        ).firstOrNull { !it.isNullOrBlank() && it != "null" }
        return PlaylistSubscribeMeta(
            id = id,
            name = name,
            coverUrl = cover,
            trackCount = pl.optInt("trackCount", 0),
            playCount = pl.optLong("playCount", 0L),
            creatorId = creatorId,
            creatorName = creatorName,
            creatorAvatarUrl = creatorAvatar,
            subscribedCount = pl.optInt("subscribedCount", 0),
            specialType = pl.optInt("specialType", 0),
            subscribed = pl.optBoolean("subscribed", false),
        )
    }

    fun subscribedFromDynamic(json: JSONObject): Boolean? {
        if (NcmJson.apiCode(json) != 200) return null
        if (!json.has("subscribed")) return null
        return json.optBoolean("subscribed", false)
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
                parseTrackObject(t)?.let { add(it) }
            }
        }
    }

    private fun parseTrackObject(t: JSONObject): TrackRow? = trackFromSongObject(t)

    /**
     * 兼容 `/song/detail`、日推、云搜索：`ar`/`artists`、`al`/`album`、`dt`/`duration`。
     */
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

    fun trackToCacheJson(t: TrackRow): JSONObject {
        val o = JSONObject()
            .put("id", t.id)
            .put("name", t.name)
            .put("artists", t.artists)
            .put("album", t.album ?: "")
            .put("durationMs", t.durationMs)
            .put("coverUrl", t.coverUrl ?: "")
        if (t.artistRefs.isNotEmpty()) {
            val arr = JSONArray()
            t.artistRefs.forEach { a ->
                arr.put(JSONObject().put("id", a.id).put("name", a.name))
            }
            o.put("artistRefs", arr)
        }
        return o
    }

    fun trackFromCacheJson(o: JSONObject): TrackRow? {
        val id = o.optLong("id", 0L)
        if (id <= 0L) return null
        val refsArr = o.optJSONArray("artistRefs")
        val refs = if (refsArr != null) artistRefsFromArray(refsArr) else emptyList()
        return TrackRow(
            id = id,
            name = o.optString("name", "—"),
            artists = o.optString("artists", "—"),
            album = o.optString("album", "").takeIf { it.isNotBlank() },
            durationMs = o.optLong("durationMs", 0L),
            coverUrl = o.optString("coverUrl", "").takeIf { it.isNotBlank() },
            artistRefs = refs,
        )
    }

    fun preferExistingCover(old: TrackRow?, incoming: TrackRow): TrackRow {
        if (old == null) return incoming
        val cover = old.coverUrl.takeIf { !it.isNullOrBlank() } ?: incoming.coverUrl
        if (cover == incoming.coverUrl &&
            incoming.name == old.name &&
            incoming.artists == old.artists &&
            incoming.album == old.album &&
            incoming.durationMs == old.durationMs &&
            cover == old.coverUrl &&
            incoming.artistRefs == old.artistRefs
        ) {
            return old
        }
        return incoming.copy(
            name = incoming.name.ifBlank { old.name },
            artists = if (incoming.artists == "—" && old.artists != "—") old.artists else incoming.artists,
            album = incoming.album ?: old.album,
            durationMs = incoming.durationMs.takeIf { it > 0L } ?: old.durationMs,
            coverUrl = cover,
            artistRefs = incoming.artistRefs.ifEmpty { old.artistRefs },
        )
    }

    /**
     * 按 [ids] 重排，在第一个尚未加载的 id 处停下。
     * 「我喜欢的音乐」必须用心形歌单 trackIds，不能用无序的 `/likelist`。
     */
    fun tracksUntilIdGap(ids: List<Long>, tracks: List<TrackRow>): List<TrackRow> {
        if (tracks.isEmpty()) return emptyList()
        if (ids.isEmpty()) return tracks
        val byId = LinkedHashMap<Long, TrackRow>(tracks.size)
        for (t in tracks) {
            if (t.id !in byId) byId[t.id] = t
        }
        val out = ArrayList<TrackRow>(minOf(tracks.size, ids.size))
        for (id in ids) {
            val row = byId[id] ?: break
            out.add(row)
        }
        return out.ifEmpty { tracks }
    }

    /**
     * 刷新首屏时保留已经翻过的页：用新 id 序重排，已加载曲目不丢。
     */
    fun mergeLoadedInOrder(
        ids: List<Long>,
        fresh: List<TrackRow>,
        previous: List<TrackRow>,
    ): List<TrackRow> {
        if (previous.isEmpty()) return tracksUntilIdGap(ids, fresh).ifEmpty { fresh }
        if (fresh.isEmpty()) return tracksUntilIdGap(ids, previous).ifEmpty { previous }
        val byId = LinkedHashMap<Long, TrackRow>(previous.size + fresh.size)
        for (t in previous) byId[t.id] = t
        for (t in fresh) byId[t.id] = preferExistingCover(byId[t.id], t)
        return tracksUntilIdGap(ids, byId.values.toList()).ifEmpty { fresh }
    }

    /**
     * 翻页合并：同一首歌保留已有封面，避免下一页把前一页 picUrl 冲掉后重拉图片。
     */
    fun mergeTrackRows(previous: List<TrackRow>, incoming: List<TrackRow>): List<TrackRow> {
        if (previous.isEmpty()) return incoming
        if (incoming.isEmpty()) return previous
        val prevById = previous.associateBy { it.id }
        return incoming.map { n -> preferExistingCover(prevById[n.id], n) }
    }

    private fun firstHttpUrl(vararg raw: String?): String? {
        for (s in raw) {
            val v = s?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: continue
            if (v.startsWith("http://") || v.startsWith("https://")) return v
        }
        return null
    }
}
