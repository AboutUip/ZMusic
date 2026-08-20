package com.kite.zmusic.ui.artist

import com.kite.zmusic.data.SongRepository
import com.kite.zmusic.data.TrackArtist
import com.kite.zmusic.data.TrackRow

internal fun TrackRow.namedArtists(): List<TrackArtist> =
    artistRefs.filter { it.name.isNotBlank() && it.name != "—" }

internal suspend fun resolveTrackArtists(
    track: TrackRow,
    cookie: String,
    songs: SongRepository,
): List<TrackArtist> {
    val local = track.namedArtists().filter { it.id > 0L }
    if (local.isNotEmpty()) return local
    return songs.resolveTrackArtists(track, cookie)
}
