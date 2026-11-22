package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject

data class PodcastShow(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val djName: String?,
    val desc: String?,
    val programCount: Int,
    val subCount: Long,
    val subscribed: Boolean,
    val category: String?,
)

data class PodcastEpisode(
    val programId: Long,
    val trackId: Long,
    val name: String,
    val coverUrl: String?,
    val podcastId: Long,
    val podcastName: String,
    val djName: String?,
    val description: String?,
    val durationMs: Long,
    val listenerCount: Long,
    val createTime: Long,
    val liked: Boolean,
)

data class PodcastBanner(
    val picUrl: String,
    val targetId: Long,
    val title: String,
)

data class PodcastCategory(
    val id: Long,
    val name: String,
)

data class PodcastFeed(
    val banners: List<PodcastBanner> = emptyList(),
    val today: PodcastShow? = null,
    val recommends: List<PodcastShow> = emptyList(),
    val hot: List<PodcastShow> = emptyList(),
    val newest: List<PodcastShow> = emptyList(),
    val popular: List<PodcastShow> = emptyList(),
    val newcomers: List<PodcastShow> = emptyList(),
    val anchorHours: List<PodcastShow> = emptyList(),
    val pay: List<PodcastShow> = emptyList(),
    val payGift: List<PodcastShow> = emptyList(),
    val programChart: List<PodcastEpisode> = emptyList(),
    val programHours: List<PodcastEpisode> = emptyList(),
    val categories: List<PodcastCategory> = emptyList(),
    val subscribed: List<PodcastShow> = emptyList(),
    val recentShows: List<PodcastShow> = emptyList(),
    val recentEpisodes: List<PodcastEpisode> = emptyList(),
    val created: List<PodcastShow> = emptyList(),
)

object NcmPodcastParse {

    fun banners(json: JSONObject): List<PodcastBanner> {
        val arr = json.optJSONArray("banners")
            ?: json.optJSONObject("data")?.optJSONArray("banners")
            ?: return emptyList()
        val out = ArrayList<PodcastBanner>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pic = httpPic(o) ?: continue
            out.add(
                PodcastBanner(
                    picUrl = pic,
                    targetId = o.optLong("targetId", o.optLong("targetId", 0L)),
                    title = o.optString("typeTitle").ifBlank { o.optString("title") },
                ),
            )
        }
        return out
    }

    fun categories(json: JSONObject): List<PodcastCategory> {
        val arr = json.optJSONArray("categories")
            ?: json.optJSONObject("data")?.optJSONArray("categories")
            ?: return emptyList()
        val out = ArrayList<PodcastCategory>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id")
            val name = o.optString("name").trim()
            if (id <= 0L || name.isEmpty()) continue
            out.add(PodcastCategory(id, name))
        }
        return out
    }

    fun show(json: JSONObject): PodcastShow? {
        val data = json.optJSONObject("data")
        val radio = json.optJSONObject("djRadio")
        return asShow(data ?: radio ?: json)
    }

    fun shows(json: JSONObject): List<PodcastShow> {
        val out = LinkedHashMap<Long, PodcastShow>()
        for (arr in arrays(json, listOf("djRadios", "radios", "toplist", "data", "list"))) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val show = asShow(o) ?: continue
                out.putIfAbsent(show.id, show)
            }
        }
        return out.values.toList()
    }

    fun episodes(json: JSONObject): List<PodcastEpisode> {
        val direct = json.optJSONObject("program")
        if (direct != null) {
            asEpisode(direct)?.let { return listOf(it) }
        }
        val out = LinkedHashMap<Long, PodcastEpisode>()
        for (arr in arrays(json, listOf("programs", "toplist", "list", "voices", "data"))) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val episode = asEpisode(o) ?: continue
                out.putIfAbsent(episode.programId, episode)
            }
        }
        return out.values.toList()
    }

    fun hasMore(json: JSONObject): Boolean {
        if (json.optBoolean("more") || json.optBoolean("hasMore")) return true
        val data = json.optJSONObject("data") ?: return false
        return data.optBoolean("more") || data.optBoolean("hasMore")
    }

    fun userId(json: JSONObject): Long {
        val root = json.optJSONObject("data") ?: json
        val profile = root.optJSONObject("profile") ?: json.optJSONObject("profile")
        val account = root.optJSONObject("account") ?: json.optJSONObject("account")
        val fromProfile = profile?.optLong("userId") ?: 0L
        if (fromProfile > 0L) return fromProfile
        return account?.optLong("id") ?: 0L
    }

    fun lyricText(json: JSONObject): String? {
        val data = json.optJSONObject("data")
        val raw = firstText(
            json.optString("lyric"),
            data?.optString("lyric"),
            json.optJSONObject("lrc")?.optString("lyric"),
            data?.optJSONObject("lrc")?.optString("lyric"),
        ) ?: return null
        val plain = raw.lineSequence()
            .map { it.replace(Regex("""\[[^]]*\]"""), "").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return plain.ifBlank { raw.trim() }.takeIf { it.isNotEmpty() }
    }

    fun ok(json: JSONObject): Boolean {
        val code = NcmJson.apiCode(json)
        return code == 200 || code == 201
    }

    private fun asShow(o: JSONObject): PodcastShow? {
        val nested = o.optJSONObject("djRadio") ?: o.optJSONObject("radio")
        val radio = if (nested != null && nested.optLong("id") > 0L && nested.optString("name").isNotBlank()) {
            nested
        } else {
            o
        }
        if (radio.optLong("mainTrackId") > 0L || radio.has("mainSong")) return null
        val id = radio.optLong("id")
        val name = radio.optString("name").trim()
        if (id <= 0L || name.isEmpty()) return null
        val dj = radio.optJSONObject("dj")
        return PodcastShow(
            id = id,
            name = name,
            coverUrl = httpPic(radio) ?: dj?.let { httpPic(it) },
            djName = dj?.optString("nickname")?.trim()?.ifBlank { null },
            desc = radio.optString("desc").ifBlank { radio.optString("rcmdtext") }.trim().ifBlank { null },
            programCount = radio.optInt("programCount"),
            subCount = radio.optLong("subCount"),
            subscribed = radio.optBoolean("subed") || radio.optBoolean("subscribed"),
            category = radio.optString("category").ifBlank {
                radio.optJSONObject("category")?.optString("name").orEmpty()
            }.trim().ifBlank { null },
        )
    }

    private fun asEpisode(o: JSONObject): PodcastEpisode? {
        val nestedData = o.optJSONObject("data")
        val body = o.optJSONObject("program")
            ?: nestedData?.takeIf { it.has("mainTrackId") || it.has("mainSong") || it.has("radio") || it.has("duration") }
            ?: o
        val programId = firstLong(body, "id", "programId", "voiceId")
        if (programId <= 0L) return null
        val trackId = body.optLong("mainTrackId").takeIf { it > 0L }
            ?: body.optJSONObject("mainSong")?.optLong("id")?.takeIf { it > 0L }
            ?: 0L
        val radio = body.optJSONObject("radio") ?: body.optJSONObject("djRadio")
        val episodeLike = trackId > 0L || body.has("mainSong") || (body.has("duration") && radio != null)
        if (!episodeLike) return null
        val name = body.optString("name").ifBlank { body.optString("title") }.trim()
        if (name.isEmpty()) return null
        val dj = body.optJSONObject("dj") ?: radio?.optJSONObject("dj")
        return PodcastEpisode(
            programId = programId,
            trackId = trackId,
            name = name,
            coverUrl = httpPic(body) ?: radio?.let { httpPic(it) },
            podcastId = radio?.optLong("id") ?: 0L,
            podcastName = radio?.optString("name")?.trim().orEmpty().ifBlank { dj?.optString("nickname").orEmpty() },
            djName = dj?.optString("nickname")?.trim()?.ifBlank { null },
            description = body.optString("description").ifBlank { body.optString("desc") }.trim().ifBlank { null },
            durationMs = body.optLong("duration"),
            listenerCount = body.optLong("listenerCount", body.optLong("adjustedPlayCount", body.optLong("playCount"))),
            createTime = body.optLong("createTime", body.optLong("scheduledPublishTime")),
            liked = body.optBoolean("liked"),
        )
    }

    private fun arrays(json: JSONObject, keys: List<String>): List<JSONArray> {
        val out = ArrayList<JSONArray>()
        fun take(o: JSONObject?) {
            if (o == null) return
            for (key in keys) {
                when (val value = o.opt(key)) {
                    is JSONArray -> out.add(value)
                    is JSONObject -> {
                        for (inner in listOf("list", "djRadios", "radios", "programs", "voices", "toplist")) {
                            value.optJSONArray(inner)?.let { out.add(it) }
                        }
                    }
                }
            }
        }
        take(json)
        take(json.optJSONObject("data"))
        take(json.optJSONObject("result"))
        return out
    }

    private fun httpPic(o: JSONObject): String? {
        for (key in listOf("picUrl", "coverUrl", "pic", "imgUrl", "coverImgUrl", "avatarUrl", "blurCoverUrl")) {
            val raw = o.optString(key)
            val https = NcmLibraryParse.ncmHttpsImage(raw)
            if (!https.isNullOrBlank()) return https
        }
        return null
    }

    private fun firstLong(o: JSONObject, vararg keys: String): Long {
        for (key in keys) {
            val value = o.optLong(key)
            if (value > 0L) return value
        }
        return 0L
    }

    private fun firstText(vararg values: String?): String? {
        for (value in values) {
            val text = value?.trim().orEmpty()
            if (text.isNotEmpty() && text != "null") return text
        }
        return null
    }
}
