package com.kite.zmusic.config

import com.kite.zmusic.BuildConfig

/**
 * 网易云兼容 API（NeteaseCloudMusicApi）基地址。
 * 编译期注入，可在 `Android/local.properties` 中设置 `ncm.api.base.url` 覆盖默认值。
 */
object NcmApiConfig {
    val baseUrl: String = BuildConfig.NCM_API_BASE_URL
}
