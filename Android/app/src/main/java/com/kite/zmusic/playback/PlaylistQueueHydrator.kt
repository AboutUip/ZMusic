package com.kite.zmusic.playback

import com.kite.zmusic.data.TrackRow

internal fun mergePlaylistQueue(
    frozen: List<TrackRow>,
    live: List<TrackRow>,
): List<TrackRow> {
    if (live.size <= frozen.size) return frozen
    if (frozen.isEmpty()) return live
    val prefix = frozen.indices.all { frozen[it].id == live[it].id }
    if (prefix) return live
    val seen = frozen.mapTo(HashSet()) { it.id }
    return frozen + live.filter { it.id !in seen }
}
