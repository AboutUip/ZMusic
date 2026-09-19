package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PersonalFmModeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _choice = MutableStateFlow(load())
    val choice: StateFlow<PersonalFmModeChoice> = _choice.asStateFlow()

    fun current(): PersonalFmModeChoice = _choice.value

    fun set(next: PersonalFmModeChoice) {
        if (next == _choice.value) return
        prefs.edit()
            .putString(KEY_MODE, next.mode)
            .putString(KEY_SUBMODE, next.submode.orEmpty())
            .apply()
        _choice.value = next
    }

    private fun load(): PersonalFmModeChoice {
        val mode = prefs.getString(KEY_MODE, null)?.trim().orEmpty()
        if (mode.isEmpty()) return PersonalFmModeChoice.Default
        val sub = prefs.getString(KEY_SUBMODE, null)?.trim()?.takeIf { it.isNotEmpty() }
        return PersonalFmModeChoice(mode, sub)
    }

    companion object {
        private const val PREFS = "zmusic_personal_fm_mode"
        private const val KEY_MODE = "mode"
        private const val KEY_SUBMODE = "submode"
    }
}
