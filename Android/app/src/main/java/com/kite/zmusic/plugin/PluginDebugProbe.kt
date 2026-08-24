package com.kite.zmusic.plugin

import android.content.res.AssetManager
import java.io.File

/**
 * 内置引擎探针 `dev.zmusic.probe`。
 * 源码在 `src/main/plugin-probe/`，编译时打成 assets 里的 `.zpp`。
 * 调试开启：每次启动必定安装并运行（投放目录里的同 id 可覆盖）。
 * 调试关闭：忽略此包，即使登记里仍是启用。
 * 当前只做引擎基础准备；后续改为原生调试插件。
 */
internal object PluginDebugProbe {
    const val ID = "dev.zmusic.probe"
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
