package com.kite.zmusic.data.platform

import android.util.Log
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 系统 DNS 有时会把播放接口指到错误地址，证书对不上。
 * 先问公共 DNS，失败再退回系统解析。
 */
internal class PlaybackDns : Dns {
    private val bootstrap = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    private val cache = ConcurrentHashMap<String, Cached>()

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.isBlank()) throw UnknownHostException(hostname)
        cache[hostname]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.addresses }
        val resolved = runCatching { query(hostname) }.getOrElse {
            Log.w(TAG, "dns query failed $hostname ${it.message}")
            emptyList()
        }
        if (resolved.isNotEmpty()) {
            cache[hostname] = Cached(resolved, System.currentTimeMillis() + 60_000)
            return resolved
        }
        return Dns.SYSTEM.lookup(hostname)
    }

    private fun query(hostname: String): List<InetAddress> {
        val name = URLEncoder.encode(hostname, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("https://dns.alidns.com/resolve?name=$name&type=A")
            .header("Accept", "application/dns-json")
            .get()
            .build()
        bootstrap.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val answers = JSONObject(response.body?.string().orEmpty()).optJSONArray("Answer") ?: return emptyList()
            val addresses = ArrayList<InetAddress>()
            for (i in 0 until answers.length()) {
                val item = answers.optJSONObject(i) ?: continue
                if (item.optInt("type") != 1) continue
                val ip = item.optString("data")
                val parts = ip.split('.')
                if (parts.size != 4) continue
                val bytes = ByteArray(4) { index -> parts[index].toInt().toByte() }
                addresses += InetAddress.getByAddress(hostname, bytes)
            }
            return addresses
        }
    }

    private data class Cached(val addresses: List<InetAddress>, val expiresAt: Long)

    companion object {
        private const val TAG = "ZMusicSource"
    }
}
