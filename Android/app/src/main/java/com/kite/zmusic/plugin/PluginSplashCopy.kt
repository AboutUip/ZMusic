package com.kite.zmusic.plugin

object PluginSplashCopy {
    const val LOADING = "正在加载插件"

    fun namesLine(pendingNames: List<String>): String {
        if (pendingNames.isEmpty()) return ""
        val shown = pendingNames.take(2).joinToString("、")
        return if (pendingNames.size > 2) "$shown..." else shown
    }
}
