package com.kite.zmusic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.SearchHistoryRepository
import com.kite.zmusic.data.SearchRepository
import com.kite.zmusic.data.SessionRepository

class SearchViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val searchHistory: SearchHistoryRepository,
    private val search: SearchRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(sessionRepository, searchHistory, search) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
