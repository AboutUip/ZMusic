package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 记住正在听的节目，方便进程被杀掉后灵动岛和播放页还能对上这一期。
 * 键是歌曲接口用的 mainTrackId。
 */
class PodcastPlayMemory(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val byTrack = LinkedHashMap<Long, PodcastEpisode>()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    init {
        restore()
    }

    fun contains(trackId: Long): Boolean = byTrack.containsKey(trackId)

    fun episode(trackId: Long): PodcastEpisode? = byTrack[trackId]

    fun remember(episodes: List<PodcastEpisode>) {
        if (episodes.isEmpty()) return
        val next = LinkedHashMap<Long, PodcastEpisode>()
        for (episode in episodes) {
            if (episode.trackId <= 0L) continue
            val previous = byTrack[episode.trackId]
            next[episode.trackId] = if (previous != null) {
                episode.copy(liked = previous.liked)
            } else {
                episode
            }
        }
        for ((id, episode) in byTrack) {
            if (next.size >= CAP) break
            next.putIfAbsent(id, episode)
        }
        byTrack.clear()
        byTrack.putAll(next)
        persist()
        _revision.value += 1
    }

    fun bubble(): Pair<Float, Float> {
        val x = prefs.getFloat(KEY_X, 0.04f)
        val y = prefs.getFloat(KEY_Y, 0.46f)
        return x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
    }

    fun saveBubble(x: Float, y: Float) {
        prefs.edit()
            .putFloat(KEY_X, x.coerceIn(0f, 1f))
            .putFloat(KEY_Y, y.coerceIn(0f, 1f))
            .apply()
    }

    fun likedEpisodes(): List<PodcastEpisode> = byTrack.values.filter { it.liked }

    fun updateLiked(trackId: Long, liked: Boolean) {
        val current = byTrack[trackId] ?: return
        if (!liked) {
            byTrack[trackId] = current.copy(liked = false)
        } else {
            val next = LinkedHashMap<Long, PodcastEpisode>()
            next[trackId] = current.copy(liked = true)
            for ((id, episode) in byTrack) {
                if (id != trackId) next[id] = episode
            }
            byTrack.clear()
            byTrack.putAll(next)
        }
        persist()
        _revision.value += 1
    }

    private fun restore() {
        val raw = prefs.getString(KEY, null) ?: return
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val episode = decode(o) ?: continue
            byTrack[episode.trackId] = episode
        }
    }

    private fun persist() {
        val arr = JSONArray()
        for (episode in byTrack.values) {
            arr.put(encode(episode))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun encode(episode: PodcastEpisode): JSONObject = JSONObject()
        .put("trackId", episode.trackId)
        .put("programId", episode.programId)
        .put("name", episode.name)
        .put("coverUrl", episode.coverUrl)
        .put("podcastId", episode.podcastId)
        .put("podcastName", episode.podcastName)
        .put("djName", episode.djName)
        .put("description", episode.description?.take(500))
        .put("durationMs", episode.durationMs)
        .put("listenerCount", episode.listenerCount)
        .put("createTime", episode.createTime)
        .put("liked", episode.liked)

    private fun decode(o: JSONObject): PodcastEpisode? {
        val trackId = o.optLong("trackId")
        val programId = o.optLong("programId")
        val name = o.optString("name").trim()
        if (trackId <= 0L || programId <= 0L || name.isEmpty()) return null
        return PodcastEpisode(
            programId = programId,
            trackId = trackId,
            name = name,
            coverUrl = o.optString("coverUrl").trim().ifBlank { null },
            podcastId = o.optLong("podcastId"),
            podcastName = o.optString("podcastName"),
            djName = o.optString("djName").trim().ifBlank { null },
            description = o.optString("description").trim().ifBlank { null },
            durationMs = o.optLong("durationMs"),
            listenerCount = o.optLong("listenerCount"),
            createTime = o.optLong("createTime"),
            liked = o.optBoolean("liked"),
        )
    }

    companion object {
        private const val PREFS = "zmusic_podcast_play"
        private const val KEY = "episodes"
        private const val KEY_X = "bubble_x"
        private const val KEY_Y = "bubble_y"
        private const val CAP = 180
    }
}

fun PodcastEpisode.toTrackRow(): TrackRow = TrackRow(
    id = trackId,
    name = name,
    artists = djName?.takeIf { it.isNotBlank() } ?: podcastName.ifBlank { name },
    album = podcastName.ifBlank { null },
    durationMs = durationMs,
    coverUrl = coverUrl,
)
