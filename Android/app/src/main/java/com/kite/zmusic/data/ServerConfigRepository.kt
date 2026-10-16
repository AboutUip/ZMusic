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
        val scheme: String = "http",
        val path: String = "",
    ) {
        fun normalized(): Endpoint {
            val nextScheme = if (scheme.equals("https", ignoreCase = true)) "https" else "http"
            val nextHost = host.trim().removePrefix("[").removeSuffix("]")
            val cut = path.substringBefore('?').substringBefore('#')
            val nextPath = when {
                cut.isBlank() || cut == "/" -> ""
                cut.startsWith("/") -> cut.trimEnd('/')
                else -> "/${cut.trimEnd('/')}"
            }
            return copy(
                host = nextHost,
                port = port.coerceIn(1, 65535),
                scheme = nextScheme,
                path = nextPath,
            )
        }

        fun toBaseUrl(): String {
            val e = normalized()
            val authority = if (e.host.contains(':')) "[${e.host}]" else e.host
            val omitPort = (e.scheme == "https" && e.port == 443) ||
                (e.scheme == "http" && e.port == 80)
            val root = if (omitPort) {
                "${e.scheme}://$authority"
            } else {
                "${e.scheme}://$authority:${e.port}"
            }
            return root + e.path
        }
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
            return Endpoint(
                host = host,
                port = port,
                scheme = prefs.getString(KEY_SCHEME, null).orEmpty(),
                path = prefs.getString(KEY_PATH, null).orEmpty(),
            ).normalized()
        }
        return parseEndpoint(NcmApiConfig.defaultBaseUrl)
    }

    fun hasPersistedConfig(): Boolean {
        val host = prefs.getString(KEY_HOST, null)?.trim().orEmpty()
        val port = prefs.getInt(KEY_PORT, -1)
        return host.isNotEmpty() && port in 1..65535
    }

    fun persist(endpoint: Endpoint) {
        val e = endpoint.normalized()
        require(e.host.isNotEmpty()) { "host empty" }
        require(e.port in 1..65535) { "invalid port: ${e.port}" }
        prefs.edit()
            .putString(KEY_HOST, e.host)
            .putInt(KEY_PORT, e.port)
            .putString(KEY_SCHEME, e.scheme)
            .putString(KEY_PATH, e.path)
            .apply()
        NcmApiConfig.setRuntimeBaseUrl(e.toBaseUrl())
    }

    fun persist(host: String, port: Int) {
        persist(Endpoint(host, port))
    }

    fun applyToRuntime() {
        NcmApiConfig.setRuntimeBaseUrl(currentEndpoint().toBaseUrl())
    }

    companion object {
        private const val PREFS_NAME = "zmusic_server_config"
        private const val KEY_HOST = "server_host"
        private const val KEY_PORT = "server_port"
        private const val KEY_SCHEME = "server_scheme"
        private const val KEY_PATH = "server_path"

        fun parseEndpoint(baseUrl: String): Endpoint =
            parseServerAddress(baseUrl, fallbackPort = -1)
                ?: Endpoint(host = "127.0.0.1", port = 80)

        /**
         * 接受 IP、域名，或带 http/https 的网址。
         * 网址里写了端口就用它；只写域名或 IP 时用 [fallbackPort]。
         */
        fun parseServerAddress(raw: String, fallbackPort: Int): Endpoint? {
            val text = raw.trim().trimEnd('/')
            if (text.isEmpty() || looksMasked(text)) return null
            val hasScheme = text.contains("://")
            val uri = Uri.parse(if (hasScheme) text else "http://$text")
            val scheme = if (hasScheme) {
                uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
            } else {
                "http"
            }
            if (!uri.userInfo.isNullOrEmpty()) return null
            val host = uri.host?.trim().orEmpty().removePrefix("[").removeSuffix("]")
            if (!isAcceptableHost(host)) return null
            val port = when {
                uri.port in 1..65535 -> uri.port
                hasScheme && scheme == "https" -> 443
                hasScheme -> 80
                fallbackPort in 1..65535 -> fallbackPort
                else -> return null
            }
            return Endpoint(
                host = host,
                port = port,
                scheme = scheme,
                path = uri.path.orEmpty(),
            ).normalized()
        }

        private fun isAcceptableHost(host: String): Boolean {
            if (host.isEmpty() || host.length > 253) return false
            if (host.any { it.isWhitespace() || it == '*' || it == '/' || it == '\\' || it == '@' }) {
                return false
            }
            if (host.count { it == ':' } >= 2) {
                return host.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }
            }
            val labels = host.trim('.').split('.')
            if (labels.isEmpty() || labels.any { it.isEmpty() || it.length > 63 }) return false
            return labels.all { label ->
                !label.startsWith('-') &&
                    !label.endsWith('-') &&
                    label.all { it.isLetterOrDigit() || it == '-' || it == '_' }
            }
        }

        fun maskHost(host: String): String {
            val h = host.trim()
            val ipv4 = h.split('.')
            if (ipv4.size == 4 && ipv4.all { part -> part.toIntOrNull() != null }) {
                return "${ipv4[0]}.***.***.${ipv4[3]}"
            }
            if (h.contains(':')) return "****"
            val labels = h.split('.').filter { it.isNotEmpty() }
            return when {
                labels.size >= 3 -> "${labels.first()}.***.${labels.last()}"
                labels.size == 2 -> "${labels.first()}.***"
                else -> "***"
            }
        }

        fun maskEndpoint(endpoint: Endpoint): String {
            val e = endpoint.normalized()
            val authority = if (e.host.contains(':')) "[${maskHost(e.host)}]" else maskHost(e.host)
            val omitPort = (e.scheme == "https" && e.port == 443) ||
                (e.scheme == "http" && e.port == 80)
            val root = if (omitPort) {
                "${e.scheme}://$authority"
            } else {
                "${e.scheme}://$authority:${e.port}"
            }
            return root + e.path
        }

        fun looksMasked(host: String): Boolean {
            val h = host.trim()
            if (h.isEmpty()) return false
            return h.contains("***") || h == "****" || h.all { it == '*' }
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
