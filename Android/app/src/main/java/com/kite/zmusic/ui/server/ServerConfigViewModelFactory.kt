package com.kite.zmusic.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.ServerConfigRepository

class ServerConfigViewModelFactory(
    private val serverConfigRepository: ServerConfigRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ServerConfigViewModel::class.java)) {
            return ServerConfigViewModel(serverConfigRepository) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
