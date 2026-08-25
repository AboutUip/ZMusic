package com.kite.zmusic.plugin

import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.PlaybackUiState

/**
 * 给 `Xuan.player.get` 与 `player.*` 钩子用的只读快照。
 * [playing] 对应宿主 `playWhenReady`，不是瞬时 `isPlaying`。
 */
data class PluginPlaybackSnapshot(
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val liked: Boolean? = null,
    val track: PluginTrackSnap? = null,
    val queueIndex: Int = -1,
    val queueLength: Int = 0,
    val queueTracks: List<PluginTrackSnap> = emptyList(),
    val queueIds: List<Long> = emptyList(),
    val truncated: Boolean = false,
) {
    val trackId: Long? get() = track?.id

    fun toGetMap(): Map<String, Any?> = mapOf(
        "playing" to playing,
        "positionMs" to positionMs,
        "durationMs" to durationMs,
        "liked" to liked,
        "track" to track?.toMap(liked),
        "queue" to queueMap(),
    )

    fun trackArg(): Any? = track?.toMap(liked)

    fun queueMap(): Map<String, Any?> = mapOf(
        "length" to queueLength,
        "index" to queueIndex,
        "tracks" to queueTracks.map { it.toQueueMap() },
        "truncated" to truncated,
    )

    fun likedMap(): Map<String, Any?> = mapOf("liked" to liked)

    fun stateMap(): Map<String, Any?> = mapOf("playing" to playing)

    companion object {
        const val QUEUE_TRACK_CAP = 100

        val EMPTY = PluginPlaybackSnapshot()

        fun from(ui: PlaybackUiState, liked: Boolean?): PluginPlaybackSnapshot {
            val current = ui.currentTrack
            val ids = ui.queue.map { it.id }
            val cap = QUEUE_TRACK_CAP
            val shown = ui.queue.take(cap).map { it.toPluginSnap() }
            return PluginPlaybackSnapshot(
                playing = ui.playWhenReady,
                positionMs = ui.positionMs.coerceAtLeast(0L),
                durationMs = when {
                    ui.durationMs > 0L -> ui.durationMs
                    current != null -> current.durationMs.coerceAtLeast(0L)
                    else -> 0L
                },
                liked = if (current == null) null else liked,
                track = current?.toPluginSnap(),
                queueIndex = if (ui.hasQueue && ui.index in ui.queue.indices) ui.index else -1,
                queueLength = ui.queue.size,
                queueTracks = shown,
                queueIds = ids,
                truncated = ui.queue.size > cap,
            )
        }
    }
}

data class PluginTrackSnap(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String?,
    val durationMs: Long,
    val coverUrl: String?,
) {
    fun toMap(liked: Boolean?): Map<String, Any?> {
        val map = toQueueMap()
        map["liked"] = liked
        return map
    }

    fun toQueueMap(): MutableMap<String, Any?> = linkedMapOf(
        "id" to id,
        "name" to name,
        "artists" to artists,
        "album" to album,
        "durationMs" to durationMs,
        "coverUrl" to coverUrl,
    )
}

internal fun TrackRow.toPluginSnap(): PluginTrackSnap = PluginTrackSnap(
    id = id,
    name = name,
    artists = artists,
    album = album,
    durationMs = durationMs.coerceAtLeast(0L),
    coverUrl = coverUrl,
)
