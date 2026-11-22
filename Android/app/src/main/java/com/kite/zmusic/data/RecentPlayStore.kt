package com.kite.zmusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * 最近播放。不请求云端，写在应用私有目录。
 * 歌曲最多 [RecentSongCap] 首，MV 最多 [RecentMvCap] 条。
 */
class RecentPlayStore(
    context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val gate = Mutex()

    private val _songs = MutableStateFlow<List<RecentSong>>(emptyList())
    val songs: StateFlow<List<RecentSong>> = _songs.asStateFlow()

    private val _mvs = MutableStateFlow<List<RecentMv>>(emptyList())
    val mvs: StateFlow<List<RecentMv>> = _mvs.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val disk = read()
            _songs.update { recentMergeFront(it, disk.songs, RecentSongCap) { song -> song.id } }
            _mvs.update { recentMergeFront(it, disk.mvs, RecentMvCap) { mv -> mv.id } }
            _ready.value = true
            persist()
        }
    }

    fun recordSong(track: TrackRow, playedAt: Long = System.currentTimeMillis()) {
        if (track.id <= 0L) return
        _songs.value = recentPush(
            _songs.value,
            track.toRecentSong(playedAt),
            RecentSongCap,
        ) { left, right -> left.id == right.id }
        persist()
    }

    fun recordMv(card: RecommendMvCard, playedAt: Long = System.currentTimeMillis()) {
        if (card.id <= 0L) return
        _mvs.value = recentPush(
            _mvs.value,
            card.toRecentMv(playedAt),
            RecentMvCap,
        ) { left, right -> left.id == right.id }
        persist()
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) {
            gate.withLock {
                runCatching {
                    file.writeText(
                        recentPlayToJson(_songs.value, _mvs.value),
                        Charsets.UTF_8,
                    )
                }.onFailure { Log.w(TAG, "persist recent play failed", it) }
            }
        }
    }

    private fun read(): RecentPlaySnapshot {
        if (!file.exists()) return RecentPlaySnapshot()
        return runCatching {
            recentPlayFromJson(file.readText(Charsets.UTF_8))
        }.getOrElse { RecentPlaySnapshot() }
    }

    private companion object {
        const val TAG = "RecentPlayStore"
        const val FILE_NAME = "zmusic_recent_play.json"
    }
}
