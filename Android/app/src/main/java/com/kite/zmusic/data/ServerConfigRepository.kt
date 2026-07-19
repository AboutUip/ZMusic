package com.kite.zmusic.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kite.zmusic.config.NcmApiConfig

/**
 * 持久化 NCM API 服务器主机与端口；启动时应用到 [NcmApiConfig]。
 */
class ServerConfigRepository(context: Context) {

    data class Endpoint(
        val host: String,
        val port: Int,
    ) {
        fun toBaseUrl(scheme: String = "http"): String =
            "${scheme.trimEnd('/')}://${host.trim()}:$port"
    }

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    init {
        applyToRuntime()
    }

    /** 已保存配置，否则回退到编译期默认基址解析结果。 */
    fun currentEndpoint(): Endpoint {
        val host = prefs.getString(KEY_HOST, null)?.trim().orEmpty()
        val port = prefs.getInt(KEY_PORT, -1)
        if (host.isNotEmpty() && port in 1..65535) {
            return Endpoint(host, port)
        }
        return parseEndpoint(NcmApiConfig.defaultBaseUrl)
    }

    fun hasPersistedConfig(): Boolean {
        val host = prefs.getString(KEY_HOST, null)?.trim().orEmpty()
        val port = prefs.getInt(KEY_PORT, -1)
        return host.isNotEmpty() && port in 1..65535
    }

    fun persist(host: String, port: Int) {
        val h = host.trim()
        require(h.isNotEmpty()) { "host empty" }
        require(port in 1..65535) { "invalid port: $port" }
        prefs.edit()
            .putString(KEY_HOST, h)
            .putInt(KEY_PORT, port)
            .apply()
        NcmApiConfig.setRuntimeBaseUrl(Endpoint(h, port).toBaseUrl())
    }

    fun applyToRuntime() {
        NcmApiConfig.setRuntimeBaseUrl(currentEndpoint().toBaseUrl())
    }

    companion object {
        private const val PREFS_NAME = "zmusic_server_config"
        private const val KEY_HOST = "server_host"
        private const val KEY_PORT = "server_port"

        fun parseEndpoint(baseUrl: String): Endpoint {
            val uri = Uri.parse(baseUrl.trim())
            val host = uri.host?.trim().orEmpty().ifEmpty {
                // 兜底：去掉 scheme 后取主机段
                baseUrl.substringAfter("://", baseUrl)
                    .substringBefore('/')
                    .substringBefore(':')
                    .trim()
            }
            val port = when {
                uri.port > 0 -> uri.port
                uri.scheme.equals("https", ignoreCase = true) -> 443
                else -> 80
            }
            return Endpoint(
                host = host.ifEmpty { "127.0.0.1" },
                port = port.coerceIn(1, 65535),
            )
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
