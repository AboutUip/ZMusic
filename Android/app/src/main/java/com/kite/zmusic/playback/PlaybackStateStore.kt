package com.kite.zmusic.playback

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kite.zmusic.data.TrackRow
import org.json.JSONArray
import org.json.JSONObject

/** 唯一播放队列持久化（迷你条恢复 / 续播元数据）。 */
class PlaybackStateStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(state: PlaybackUiState) {
        if (state.queue.isEmpty() || state.index < 0) {
            clear()
            return
        }
        runCatching {
            val arr = JSONArray()
            state.queue.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("name", t.name)
                        .put("artists", t.artists)
                        .put("album", t.album ?: "")
                        .put("durationMs", t.durationMs)
                        .put("coverUrl", t.coverUrl ?: ""),
                )
            }
            prefs.edit()
                .putString(KEY_QUEUE, arr.toString())
                .putInt(KEY_INDEX, state.index)
                .putInt(KEY_MODE, modeToInt(state.playbackMode))
                .putLong(KEY_POSITION, state.positionMs)
                .putLong(KEY_SOURCE_ID, state.sourcePlaylistId ?: -1L)
                .putString(KEY_SOURCE_TITLE, state.sourcePlaylistTitle)
                .apply()
        }.onFailure { Log.w(TAG, "save failed", it) }
    }

    fun load(): PlaybackUiState? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            if (arr.length() == 0) return null
            val queue = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        TrackRow(
                            id = o.getLong("id"),
                            name = o.getString("name"),
                            artists = o.getString("artists"),
                            album = o.optString("album").ifBlank { null },
                            durationMs = o.getLong("durationMs"),
                            coverUrl = o.optString("coverUrl").ifBlank { null },
                        ),
                    )
                }
            }
            val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, queue.lastIndex)
            PlaybackUiState(
                queue = queue,
                index = index,
                isPlaying = false,
                buffering = false,
                playbackMode = intToMode(prefs.getInt(KEY_MODE, 0)),
                positionMs = prefs.getLong(KEY_POSITION, 0L),
                durationMs = queue[index].durationMs,
                lyricLines = emptyList(),
                error = null,
                loadPending = false,
                hasQueue = true,
                sourcePlaylistId = prefs.getLong(KEY_SOURCE_ID, -1L).takeIf { it > 0 },
                sourcePlaylistTitle = prefs.getString(KEY_SOURCE_TITLE, null),
            ).withHydratedPeeks()
        }.onFailure { Log.w(TAG, "load failed", it) }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun modeToInt(mode: PlaybackMode): Int = when (mode) {
        PlaybackMode.ORDER -> 0
        PlaybackMode.REPEAT_ONE -> 1
        PlaybackMode.SHUFFLE -> 2
    }

    private fun intToMode(v: Int): PlaybackMode = when (v) {
        1 -> PlaybackMode.REPEAT_ONE
        2 -> PlaybackMode.SHUFFLE
        else -> PlaybackMode.ORDER
    }

    companion object {
        private const val TAG = "PlaybackStateStore"
        private const val PREFS = "zmusic_playback_snapshot"
        private const val KEY_QUEUE = "queue_json"
        private const val KEY_INDEX = "index"
        private const val KEY_MODE = "mode"
        private const val KEY_POSITION = "position_ms"
        private const val KEY_SOURCE_ID = "source_id"
        private const val KEY_SOURCE_TITLE = "source_title"
    }
}
