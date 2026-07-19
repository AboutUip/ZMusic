package com.kite.zmusic.data

import com.kite.zmusic.config.NcmApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 轻量服务器连通性探测（`GET /inner/version`，无需登录）。
 */
class NcmConnectivityClient(
    private val client: OkHttpClient = probeClient(),
) {

    /**
     * @param baseUrl 探测目标；为 null 时使用当前 [NcmApiConfig.baseUrl]
     * @return 成功时为 Unit；失败时携带可读错误信息
     */
    suspend fun checkReachable(baseUrl: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val base = (baseUrl ?: NcmApiConfig.baseUrl).trim().trimEnd('/')
        if (base.isEmpty()) {
            return@withContext Result.failure(IOException("服务器地址为空"))
        }
        val url = "$base/inner/version"
        val req = Request.Builder().url(url).get().build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${resp.code} ${resp.message}"),
                    )
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message?.takeIf { it.isNotBlank() } ?: "无法连接服务器", e))
        }
    }

    companion object {
        private fun probeClient() = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}
