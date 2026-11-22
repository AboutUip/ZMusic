package com.kite.zmusic.ui.artist

import com.kite.zmusic.data.SongRepository
import com.kite.zmusic.data.TrackArtist
import com.kite.zmusic.data.TrackRow

internal fun TrackRow.namedArtists(): List<TrackArtist> =
    artistRefs.filter { it.name.isNotBlank() && it.name != "—" }

/** 竖屏标题用的歌手列表。优先带 id 的引用，否则按展示名拆开。 */
internal fun TrackRow.portraitArtistTokens(): List<TrackArtist> {
    val named = namedArtists()
    if (named.isNotEmpty()) return named
    val raw = artists.trim()
    if (raw.isBlank() || raw == "—") return emptyList()
    return raw.split(Regex("\\s*/\\s*|\\s*、\\s*|\\s*，\\s*|\\s*,\\s*"))
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "—" }
        .distinct()
        .map { TrackArtist(0L, it) }
}

internal suspend fun resolveTrackArtists(
    track: TrackRow,
    cookie: String,
    songs: SongRepository,
): List<TrackArtist> {
    val local = track.namedArtists().filter { it.id > 0L }
    if (local.isNotEmpty()) return local
    return songs.resolveTrackArtists(track, cookie)
}
