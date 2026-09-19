package com.kite.zmusic.plugin

import com.kite.zmusic.i18n.t

class PluginDebugApiDeniedException : IllegalStateException(t("插件引擎调试已关闭"))

sealed class PluginRegisterResult {
    data class Installed(val record: PluginRecord) : PluginRegisterResult()
    data class Replaced(val record: PluginRecord) : PluginRegisterResult()
    data class Skipped(val reason: String) : PluginRegisterResult()
}
