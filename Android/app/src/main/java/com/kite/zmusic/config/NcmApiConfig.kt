package com.kite.zmusic.config

import com.kite.zmusic.BuildConfig

/**
 * 网易云兼容 API（NeteaseCloudMusicApi）基地址。
 *
 * 编译期默认：`BuildConfig.NCM_API_BASE_URL`（可由 `Android/local.properties` 的
 * `ncm.api.base.url` 覆盖）。运行期可由用户配置覆盖并持久化。
 */
object NcmApiConfig {
    val defaultBaseUrl: String = BuildConfig.NCM_API_BASE_URL.trimEnd('/')

    @Volatile
    private var runtimeBaseUrl: String? = null

    val baseUrl: String
        get() = runtimeBaseUrl?.takeIf { it.isNotBlank() } ?: defaultBaseUrl

    fun setRuntimeBaseUrl(url: String) {
        runtimeBaseUrl = url.trim().trimEnd('/').takeIf { it.isNotEmpty() }
    }
}
