package com.kite.zmusic.workshop

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkshopSession(
    val appToken: String,
    val uid: String,
)

/**
 * 首次扫码确认后的长期工坊凭证。跨应用更新保留；清数据才丢。
 */
class WorkshopAuthStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)
    private val _session = MutableStateFlow(read())
    val session: StateFlow<WorkshopSession?> = _session.asStateFlow()

    fun current(): WorkshopSession? = _session.value

    fun hasToken(): Boolean = !current()?.appToken.isNullOrBlank()

    fun save(appToken: String, uid: String) {
        val token = appToken.trim()
        val id = uid.trim()
        require(token.isNotEmpty()) { "empty app_token" }
        require(id.isNotEmpty()) { "empty uid" }
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_UID, id)
            .apply()
        _session.value = WorkshopSession(token, id)
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_UID).apply()
        _session.value = null
    }

    private fun read(): WorkshopSession? {
        val token = prefs.getString(KEY_TOKEN, null)?.trim().orEmpty()
        val uid = prefs.getString(KEY_UID, null)?.trim().orEmpty()
        if (token.isEmpty() || uid.isEmpty()) return null
        return WorkshopSession(token, uid)
    }

    companion object {
        private const val PREFS_NAME = "zmusic_workshop_auth"
        private const val KEY_TOKEN = "app_token"
        private const val KEY_UID = "uid"

        private fun createPrefs(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
