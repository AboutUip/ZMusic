package com.kite.zmusic.plugin

/**
 * 插件本次运行失败，供宿主弹窗。不含 Compose。
 */
data class PluginFault(
    val id: String,
    val name: String,
    val kind: PluginFaultKind,
    val log: String,
)

enum class PluginFaultKind {
    /** 脚本 `Error`、入口抛错、未按协议注册。不隔离。 */
    Error,
    /** 进程在入口执行期间退出（哨兵残留），已隔离。 */
    Crash,
}
