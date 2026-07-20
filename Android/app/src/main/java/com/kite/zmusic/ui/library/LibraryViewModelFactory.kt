package com.kite.zmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SessionRepository

class LibraryViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val likedPlaylistRepository: LikedPlaylistRepository,
    private val playlistTracksCache: PlaylistTracksCache,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(
                sessionRepository,
                likedPlaylistRepository,
                playlistTracksCache,
            ) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
