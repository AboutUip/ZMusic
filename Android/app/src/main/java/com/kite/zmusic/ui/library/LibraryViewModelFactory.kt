package com.kite.zmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.SessionRepository

class LibraryViewModelFactory(
    private val sessionRepository: SessionRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(sessionRepository) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
