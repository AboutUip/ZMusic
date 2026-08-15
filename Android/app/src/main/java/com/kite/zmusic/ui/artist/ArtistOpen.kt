package com.kite.zmusic.ui.artist

import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.TrackArtist
import com.kite.zmusic.data.TrackRow

internal fun TrackRow.namedArtists(): List<TrackArtist> =
    artistRefs.filter { it.name.isNotBlank() && it.name != "—" }

internal suspend fun resolveTrackArtists(track: TrackRow, cookie: String): List<TrackArtist> {
    val local = track.namedArtists().filter { it.id > 0L }
    if (local.isNotEmpty()) return local
    val json = runCatching { NcmUserClient().songDetail(listOf(track.id), cookie) }.getOrNull()
        ?: return emptyList()
    val songs = json.optJSONArray("songs") ?: return emptyList()
    val first = songs.optJSONObject(0) ?: return emptyList()
    val ar = first.optJSONArray("ar") ?: first.optJSONArray("artists") ?: return emptyList()
    return NcmLibraryParse.artistRefsFromArray(ar).filter { it.id > 0L }
}
