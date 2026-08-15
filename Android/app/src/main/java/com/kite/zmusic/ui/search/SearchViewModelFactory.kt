package com.kite.zmusic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.SearchHistoryRepository
import com.kite.zmusic.data.SessionRepository

class SearchViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val searchHistory: SearchHistoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(sessionRepository, searchHistory) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
