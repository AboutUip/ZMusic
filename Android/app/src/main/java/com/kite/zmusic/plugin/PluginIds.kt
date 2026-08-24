package com.kite.zmusic.plugin

internal object PluginIds {
    private val SEGMENT = Regex("^[a-z][a-z0-9_]*$")

    fun isValid(id: String): Boolean {
        if (id != id.lowercase()) return false
        val parts = id.split('.')
        if (parts.size < 2) return false
        return parts.all { SEGMENT.matches(it) }
    }
}
