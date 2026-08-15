package com.kite.zmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.LibraryHomeRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.PlaylistCollectionRepository
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SessionRepository

class LibraryViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val playlistTracksCache: PlaylistTracksCache,
    private val playlistCollection: PlaylistCollectionRepository,
    private val libraryHome: LibraryHomeRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(
                sessionRepository,
                likedPlaylistRepository,
                playlistTracksCache,
                playlistCollection,
                libraryHome,
            ) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
