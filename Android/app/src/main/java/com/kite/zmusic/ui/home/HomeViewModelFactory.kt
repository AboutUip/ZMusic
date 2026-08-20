package com.kite.zmusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.HomeFeedRepository

class HomeViewModelFactory(
    private val homeFeedRepository: HomeFeedRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(homeFeedRepository) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
