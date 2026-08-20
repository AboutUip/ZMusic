package com.kite.zmusic.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.ArtistRepository
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.ui.notice.IslandNoticeCenter

class ArtistViewModelFactory(
    private val artistId: Long,
    private val seedName: String,
    private val seedCover: String?,
    private val sessionRepository: SessionRepository,
    private val islandNotices: IslandNoticeCenter,
    private val artists: ArtistRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ArtistViewModel::class.java)) {
            return ArtistViewModel(
                artistId,
                seedName,
                seedCover,
                sessionRepository,
                islandNotices,
                artists,
            ) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
