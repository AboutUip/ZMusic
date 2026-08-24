package com.kite.zmusic.plugin

import java.io.File

/**
 * 调试开关开启时，启动从该外部目录收取 `.zpp` 并注册。
 * 关闭调试则不看这个目录。正式产品路径仍是显式注册，不是扫目录。
 *
 * 设备路径：`Android/data/com.kite.zmusic/files/plugin-drop/`
 */
object PluginDebugDrop {
    const val DIR_NAME = "plugin-drop"

    fun dir(externalFilesDir: File): File = File(externalFilesDir, DIR_NAME)

    fun listPackages(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zpp") }
            ?.sortedBy { it.name }
            .orEmpty()
    }
}
