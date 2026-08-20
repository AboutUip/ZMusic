package com.kite.zmusic.ui.main

sealed class MainOverlay {
    data object Daily : MainOverlay()
    data object Fm : MainOverlay()
    data object Charts : MainOverlay()
    data object Search : MainOverlay()
    data object Settings : MainOverlay()
    data class Playlist(
        val id: Long,
        val title: String,
        val coverUrl: String? = null,
        val owned: Boolean = false,
        val heart: Boolean = false,
        val collected: Boolean? = null,
    ) : MainOverlay()
    data class PlaylistSearch(
        val playlistId: Long,
        val title: String,
        val heart: Boolean = false,
        val owned: Boolean = false,
    ) : MainOverlay()
    data class Album(val id: Long, val title: String) : MainOverlay()
    data class Mv(
        val id: Long,
        val title: String,
        val coverUrl: String? = null,
        val artist: String? = null,
    ) : MainOverlay()
    data class Artist(
        val id: Long,
        val name: String,
        val coverUrl: String? = null,
    ) : MainOverlay()
    data class ArtistSongs(
        val artistId: Long,
        val name: String,
        val coverUrl: String? = null,
    ) : MainOverlay()
    data class ArtistAlbums(
        val artistId: Long,
        val name: String,
        val coverUrl: String? = null,
    ) : MainOverlay()
    data class ArtistMvs(
        val artistId: Long,
        val name: String,
        val coverUrl: String? = null,
    ) : MainOverlay()
    data object LikedArtists : MainOverlay()
    data class LikedArtistsSearch(val users: Boolean = false) : MainOverlay()

    fun stackKey(): String = when (this) {
        Daily -> "daily"
        Fm -> "fm"
        Charts -> "charts"
        Search -> "search"
        Settings -> "settings"
        is Playlist -> "playlist-$id"
        is PlaylistSearch -> "playlist-search-$playlistId"
        is Album -> "album-$id"
        is Mv -> "mv-$id"
        is Artist -> "artist-$id"
        is ArtistSongs -> "artist-songs-$artistId"
        is ArtistAlbums -> "artist-albums-$artistId"
        is ArtistMvs -> "artist-mvs-$artistId"
        LikedArtists -> "liked-artists"
        is LikedArtistsSearch -> if (users) "liked-artists-search-users" else "liked-artists-search-artists"
    }
}
