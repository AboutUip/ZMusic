package com.kite.zmusic.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.AlbumCollectionRepository
import com.kite.zmusic.data.AlbumTracksCache
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.CatalogRepository
import com.kite.zmusic.data.PlaylistCollectionRepository
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.notice.IslandNoticeCenter

class CatalogViewModelFactory(
    private val overlay: MainOverlay,
    private val sessionRepository: SessionRepository,
    private val playlistTracksCache: PlaylistTracksCache,
    private val albumTracksCache: AlbumTracksCache,
    private val homeFeed: HomeFeedRepository,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val playlistCollection: PlaylistCollectionRepository,
    private val albumCollection: AlbumCollectionRepository,
    private val islandNotices: IslandNoticeCenter,
    private val catalog: CatalogRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm = when (overlay) {
            MainOverlay.Daily, MainOverlay.Fm -> DailyFmViewModel(
                sessionRepository,
                playlistTracksCache,
                albumTracksCache,
                homeFeed,
                likedPlaylistRepository,
                playlistCollection,
                albumCollection,
                islandNotices,
                catalog,
            )
            is MainOverlay.Playlist, is MainOverlay.ArtistSongs -> PlaylistDetailViewModel(
                sessionRepository,
                playlistTracksCache,
                albumTracksCache,
                homeFeed,
                likedPlaylistRepository,
                playlistCollection,
                albumCollection,
                islandNotices,
                catalog,
            )
            is MainOverlay.Album -> AlbumDetailViewModel(
                sessionRepository,
                playlistTracksCache,
                albumTracksCache,
                homeFeed,
                likedPlaylistRepository,
                playlistCollection,
                albumCollection,
                islandNotices,
                catalog,
            )
            MainOverlay.Charts -> ChartsCatalogViewModel(
                sessionRepository,
                playlistTracksCache,
                albumTracksCache,
                homeFeed,
                likedPlaylistRepository,
                playlistCollection,
                albumCollection,
                islandNotices,
                catalog,
            )
            else -> CatalogViewModel(
                sessionRepository,
                playlistTracksCache,
                albumTracksCache,
                homeFeed,
                likedPlaylistRepository,
                playlistCollection,
                albumCollection,
                islandNotices,
                catalog,
            )
        }
        if (!modelClass.isAssignableFrom(vm.javaClass)) {
            error("Unknown ViewModel $modelClass")
        }
        return vm as T
    }
}
