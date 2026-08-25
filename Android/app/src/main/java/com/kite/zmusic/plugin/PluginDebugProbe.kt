package com.kite.zmusic.plugin

import android.content.res.AssetManager
import java.io.File

/**
 * 内置引擎探针 `dev.zmusic.probe`。
 * 源码在 `src/main/plugin-probe/`，编译时打成 assets 里的 `.zpp`。
 * 调试开启：每次启动必定安装并运行（投放目录不能用同 id 覆盖）；宿主 Dock 把「调优」当作一页切换。
 * 调试关闭：忽略此包，即使登记里仍是启用。
 */
internal object PluginDebugProbe {
    const val ID = "dev.zmusic.probe"
    const val PAGE = "tune"
    const val DOCK_LABEL = "调优"
    const val ASSET_PATH = "plugin-engine/probe.zpp"

    fun shouldLaunch(id: String, debug: Boolean): Boolean = id != ID || debug

    fun copyFromAssets(assets: AssetManager, dest: File): File? =
        runCatching {
            dest.parentFile?.mkdirs()
            assets.open(ASSET_PATH).use { input ->
                dest.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            dest.takeIf { it.isFile && it.length() > 0L }
        }.getOrNull()
}
