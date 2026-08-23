package com.kite.zmusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 社区登录提交入口的主机与端口（明文 HTTP）。默认公网 IP:80。
 */
class CommunityServerStore(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    private val _endpoint = MutableStateFlow(readStored() ?: DEFAULT)
    val endpoint: StateFlow<ServerConfigRepository.Endpoint> = _endpoint.asStateFlow()

    fun current(): ServerConfigRepository.Endpoint = _endpoint.value

    fun persist(host: String, port: Int) {
        val h = host.trim()
        require(h.isNotEmpty()) { "host empty" }
        require(port in 1..65535) { "invalid port: $port" }
        prefs.edit()
            .putString(KEY_HOST, h)
            .putInt(KEY_PORT, port)
            .apply()
        _endpoint.value = ServerConfigRepository.Endpoint(h, port)
    }

    fun submitUrl(): String {
        val e = current()
        val authority = if (e.port == 80) e.host.trim() else "${e.host.trim()}:${e.port}"
        return "http://$authority${CommunityLoginConfig.SUBMIT_PATH}"
    }

    private fun readStored(): ServerConfigRepository.Endpoint? {
        val host = prefs.getString(KEY_HOST, null)?.trim().orEmpty()
        val port = prefs.getInt(KEY_PORT, -1)
        if (host.isEmpty() || port !in 1..65535) return null
        return ServerConfigRepository.Endpoint(host, port)
    }

    companion object {
        val DEFAULT = ServerConfigRepository.Endpoint("114.215.189.208", 80)
        private const val PREFS_NAME = "zmusic_community_server"
        private const val KEY_HOST = "community_host"
        private const val KEY_PORT = "community_port"

        suspend fun probe(host: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host.trim(), port), 5_000)
                }
            }
        }

        private fun createPrefs(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
