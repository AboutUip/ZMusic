package com.kite.zmusic.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.AlbumCollectionRepository
import com.kite.zmusic.data.AlbumTracksCache
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.PlaylistCollectionRepository
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.ui.notice.IslandNoticeCenter

class CatalogViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val playlistTracksCache: PlaylistTracksCache,
    private val albumTracksCache: AlbumTracksCache,
    private val homeFeed: HomeFeedRepository,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val playlistCollection: PlaylistCollectionRepository,
    private val albumCollection: AlbumCollectionRepository,
    private val islandNotices: IslandNoticeCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CatalogViewModel::class.java)) {
            return CatalogViewModel(
                sessionRepository,
                playlistTracksCache,
                albumTracksCache,
                homeFeed,
                likedPlaylistRepository,
                playlistCollection,
                albumCollection,
                islandNotices,
            ) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
