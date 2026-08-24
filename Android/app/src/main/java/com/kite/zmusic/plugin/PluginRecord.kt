package com.kite.zmusic.plugin

data class PluginRecord(
    val id: String,
    val name: String,
    val version: Int,
    val entry: String,
    val engineMin: Int,
    val engineMax: Int?,
    val enabled: Boolean,
    val quarantined: Boolean,
    val capabilities: List<String> = emptyList(),
) {
    fun canRun(engineNumber: Int): Boolean {
        if (!enabled || quarantined) return false
        if (engineMin > engineNumber) return false
        val max = engineMax
        return max == null || engineNumber <= max
    }

    fun hasCapability(name: String): Boolean =
        PluginCapabilities.has(capabilities, name)
}
