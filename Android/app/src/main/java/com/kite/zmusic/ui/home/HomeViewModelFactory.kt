package com.kite.zmusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.SessionRepository

class HomeViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val homeFeedRepository: HomeFeedRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(sessionRepository, homeFeedRepository) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
