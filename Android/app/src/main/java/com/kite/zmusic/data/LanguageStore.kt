package com.kite.zmusic.data

import android.content.Context
import android.telephony.TelephonyManager
import com.kite.zmusic.i18n.AppLanguage
import com.kite.zmusic.i18n.LanguageChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 界面语言。未选择时为自动，按国家决定；选定后固定为该语言。
 */
class LanguageStore(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _choice = MutableStateFlow(loadChoice())
    val choice: StateFlow<LanguageChoice> = _choice.asStateFlow()

    private val _language = MutableStateFlow(resolve(_choice.value))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun current(): AppLanguage = _language.value

    fun set(next: LanguageChoice) {
        if (next == _choice.value) return
        prefs.edit().putString(KEY_LANGUAGE, next.name).commit()
        _choice.value = next
        _language.value = resolve(next)
    }

    private fun loadChoice(): LanguageChoice =
        LanguageChoice.fromStored(prefs.getString(KEY_LANGUAGE, null))

    private fun resolve(choice: LanguageChoice): AppLanguage =
        choice.resolve(deviceCountryIso(appContext))

    companion object {
        private const val PREFS = "zmusic_language"
        private const val KEY_LANGUAGE = "language"

        fun peek(context: Context): AppLanguage {
            val store = context.applicationContext ?: context
            val prefs = store.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val choice = LanguageChoice.fromStored(prefs.getString(KEY_LANGUAGE, null))
            return choice.resolve(deviceCountryIso(store))
        }
    }
}

internal fun deviceCountryIso(context: Context): String {
    val fromLocale = runCatching {
        val locales = context.resources.configuration.locales
        if (locales.isEmpty) "" else locales[0].country
    }.getOrDefault("")
    if (fromLocale.length == 2) return fromLocale
    val telephony = runCatching {
        context.getSystemService(TelephonyManager::class.java)
    }.getOrNull()
    val sim = telephony?.simCountryIso?.trim().orEmpty()
    if (sim.length == 2) return sim
    val network = runCatching { telephony?.networkCountryIso?.trim().orEmpty() }.getOrDefault("")
    if (network.length == 2) return network
    return Locale.getDefault().country.trim()
}
