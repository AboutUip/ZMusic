package com.kite.zmusic.config

/**
 * 网易云兼容 API 基地址。编译默认与 Android 线上一致；
 * 环境变量 `ZMUSIC_NCM_API_BASE_URL` 或运行期设置可覆盖。文档不写死服务地址给用户文档。
 */
object NcmApiConfig {
    const val PRODUCT_VERSION = "0.1.0"

    val defaultBaseUrl: String =
        System.getenv("ZMUSIC_NCM_API_BASE_URL")?.trim()?.trimEnd('/')
            ?.takeIf { it.isNotEmpty() }
            ?: "http://120.27.244.170:3000"

    @Volatile
    private var runtimeBaseUrl: String? = null

    val baseUrl: String
        get() = runtimeBaseUrl?.takeIf { it.isNotBlank() } ?: defaultBaseUrl

    fun setRuntimeBaseUrl(url: String) {
        runtimeBaseUrl = url.trim().trimEnd('/').takeIf { it.isNotEmpty() }
    }

    fun clearRuntimeBaseUrl() {
        runtimeBaseUrl = null
    }
}
