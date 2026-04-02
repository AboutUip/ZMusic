package com.kite.zmusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 持久化 NCM API 返回的 `cookie`（加密存储），供后续请求携带。
 */
class SessionRepository(context: Context) {

    data class StoredSession(
        val cookie: String,
        val displayLabel: String?,
        /** 游客 cookie 在 `/login/status` 中常无 `account`，勿按正式账号校验。 */
        val isGuest: Boolean = false,
    )

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    private val _session = MutableStateFlow<StoredSession?>(null)
    val session: StateFlow<StoredSession?> = _session.asStateFlow()

    init {
        _session.value = readStored()
    }

    fun persist(cookie: String, displayLabel: String?, isGuest: Boolean = false) {
        val c = cookie.trim()
        prefs.edit()
            .putString(KEY_COOKIE, c)
            .putString(KEY_LABEL, displayLabel)
            .putBoolean(KEY_GUEST, isGuest)
            .apply()
        _session.value = StoredSession(c, displayLabel, isGuest)
    }

    fun clear() {
        prefs.edit().remove(KEY_COOKIE).remove(KEY_LABEL).apply()
        _session.value = null
    }

    private fun readStored(): StoredSession? {
        val c = prefs.getString(KEY_COOKIE, null)?.trim().orEmpty()
        if (c.isEmpty()) return null
        return StoredSession(
            c,
            prefs.getString(KEY_LABEL, null),
            prefs.getBoolean(KEY_GUEST, false),
        )
    }

    companion object {
        private const val PREFS_NAME = "zmusic_ncm_session"
        private const val KEY_COOKIE = "ncm_cookie"
        private const val KEY_LABEL = "ncm_label"
        private const val KEY_GUEST = "ncm_guest"

        private fun createPrefs(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
