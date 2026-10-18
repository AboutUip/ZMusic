package com.kite.zmusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kite.zmusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 社区登录提交入口的主机与端口（明文 HTTP）。
 * 编译期默认来自 `BuildConfig`（`Android/local.properties` 的 community.server.*）；
 * 开源仓库不内置公网地址。
 */
class CommunityServerStore(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    private val _endpoint = MutableStateFlow(readStored() ?: defaultEndpoint())
    val endpoint: StateFlow<ServerConfigRepository.Endpoint> = _endpoint.asStateFlow()

    fun current(): ServerConfigRepository.Endpoint = _endpoint.value

    fun persist(endpoint: ServerConfigRepository.Endpoint) {
        val e = endpoint.normalized()
        require(e.host.isNotEmpty()) { "host empty" }
        require(e.port in 1..65535) { "invalid port: ${e.port}" }
        prefs.edit()
            .putString(KEY_HOST, e.host)
            .putInt(KEY_PORT, e.port)
            .putString(KEY_SCHEME, e.scheme)
            .putString(KEY_PATH, e.path)
            .apply()
        _endpoint.value = e
    }

    fun persist(host: String, port: Int) {
        persist(ServerConfigRepository.Endpoint(host, port))
    }

    fun submitUrl(): String = "${origin()}${CommunityLoginConfig.SUBMIT_PATH}"

    fun appGrantUrl(): String = "${origin()}${CommunityLoginConfig.APP_PATH}"

    fun siteUrl(): String = "${origin()}${CommunityLoginConfig.SITE_PATH}"

    private fun origin(): String = current().toBaseUrl()

    private fun readStored(): ServerConfigRepository.Endpoint? {
        val host = prefs.getString(KEY_HOST, null)?.trim().orEmpty()
        val port = prefs.getInt(KEY_PORT, -1)
        if (host.isEmpty() || port !in 1..65535) return null
        return ServerConfigRepository.Endpoint(
            host = host,
            port = port,
            scheme = prefs.getString(KEY_SCHEME, null).orEmpty(),
            path = prefs.getString(KEY_PATH, null).orEmpty(),
        ).normalized()
    }

    companion object {
        /** 编译期默认；来自 local.properties，仓库内无公网 IP。 */
        val DEFAULT: ServerConfigRepository.Endpoint
            get() = defaultEndpoint()

        fun defaultEndpoint(): ServerConfigRepository.Endpoint {
            val host = BuildConfig.COMMUNITY_SERVER_HOST.trim()
            val port = BuildConfig.COMMUNITY_SERVER_PORT.coerceIn(1, 65535)
            return ServerConfigRepository.Endpoint(host, port)
        }

        private const val PREFS_NAME = "zmusic_community_server"
        private const val KEY_HOST = "community_host"
        private const val KEY_PORT = "community_port"
        private const val KEY_SCHEME = "community_scheme"
        private const val KEY_PATH = "community_path"

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
