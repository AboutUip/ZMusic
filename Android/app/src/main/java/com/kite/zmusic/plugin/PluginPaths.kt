package com.kite.zmusic.plugin

import java.io.File

/**
 * 应用私有目录：`filesDir/plugin-engine/`。
 *
 * - `installed/<id>/` 解压后的包根（注册写入，不扫描发现插件）
 * - `registry.json` 已注册记录
 * - `sentinel/<id>` 入口执行期间的崩溃哨兵
 * - `fault-log/<id>` 弹窗用日志快照（进程崩溃后仍可读）
 * - `store/<id>.json` 插件持久键值
 */
internal class PluginPaths(val root: File) {
    val installed: File get() = File(root, "installed")
    val sentinelDir: File get() = File(root, "sentinel")
    val faultLogDir: File get() = File(root, "fault-log")
    val storeDir: File get() = File(root, "store")
    val registryFile: File get() = File(root, "registry.json")
    val staging: File get() = File(root, "staging")

    fun installedDir(id: String): File = File(installed, id)
    fun sentinelFile(id: String): File = File(sentinelDir, id)

    fun ensure() {
        installed.mkdirs()
        sentinelDir.mkdirs()
        faultLogDir.mkdirs()
        storeDir.mkdirs()
        staging.mkdirs()
    }

    companion object {
        const val DIR_NAME = "plugin-engine"

        fun fromFilesDir(filesDir: File): PluginPaths = PluginPaths(File(filesDir, DIR_NAME))
    }
}
