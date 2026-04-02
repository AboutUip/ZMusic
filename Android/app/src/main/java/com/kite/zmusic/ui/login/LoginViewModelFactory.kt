package com.kite.zmusic.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.SessionRepository

class LoginViewModelFactory(
    private val sessionRepository: SessionRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(sessionRepository) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
