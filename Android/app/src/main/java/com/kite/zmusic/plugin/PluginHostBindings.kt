package com.kite.zmusic.plugin

import okhttp3.OkHttpClient

/**
 * 引擎对应用层的可选依赖。缺省时播放控制、HTTP 与设备能力均失败，UI / KV 仍可用。
 */
class PluginHostBindings(
    val player: PluginPlayerController = PluginPlayerController.Noop,
    val httpClient: OkHttpClient? = null,
    val device: PluginDeviceHost = PluginDeviceHost.Noop,
)
