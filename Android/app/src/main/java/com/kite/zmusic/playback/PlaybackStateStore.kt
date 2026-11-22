package com.kite.zmusic.playback

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray

/** 播放队列持久化（迷你条恢复 / 续播元数据）。默认同一个文件；音乐和播客另各存一份。 */
class PlaybackStateStore(
    context: Context,
    prefsName: String = PREFS,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun save(state: PlaybackUiState) {
        if (state.queue.isEmpty() || state.index < 0) {
            clear()
            return
        }
        runCatching {
            val arr = JSONArray()
            state.queue.forEach { t ->
                arr.put(com.kite.zmusic.data.NcmLibraryParse.trackToCacheJson(t))
            }
            prefs.edit()
                .putString(KEY_QUEUE, arr.toString())
                .putInt(KEY_INDEX, state.index)
                .putInt(KEY_MODE, modeToInt(state.playbackMode))
                .putLong(KEY_POSITION, state.positionMs)
                .putLong(KEY_SOURCE_ID, state.sourcePlaylistId ?: -1L)
                .putString(KEY_SOURCE_TITLE, state.sourcePlaylistTitle)
                .putBoolean(KEY_FM, state.fmActive)
                .putBoolean(KEY_INTEL, state.intelligenceActive)
                .putBoolean(KEY_PLAY, state.playWhenReady)
                .apply()
        }.onFailure { Log.w(TAG, "save failed", it) }
    }

    fun load(restorePlayIntent: Boolean = false): PlaybackUiState? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            if (arr.length() == 0) return null
            val queue = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    com.kite.zmusic.data.NcmLibraryParse.trackFromCacheJson(o)?.let { add(it) }
                }
            }
            val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, queue.lastIndex)
            val play = restorePlayIntent && prefs.getBoolean(KEY_PLAY, false)
            PlaybackUiState(
                queue = queue,
                index = index,
                isPlaying = false,
                playWhenReady = play,
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
                fmActive = prefs.getBoolean(KEY_FM, false),
                intelligenceActive = prefs.getBoolean(KEY_INTEL, false),
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
        private const val KEY_FM = "fm_active"
        private const val KEY_INTEL = "intelligence_active"
        private const val KEY_PLAY = "play_when_ready"
    }
}
