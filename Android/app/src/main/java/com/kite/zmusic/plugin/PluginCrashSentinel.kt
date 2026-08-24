package com.kite.zmusic.plugin

internal class PluginCrashSentinel(private val paths: PluginPaths) {
    fun mark(id: String) {
        paths.sentinelDir.mkdirs()
        paths.sentinelFile(id).writeText(id, Charsets.UTF_8)
    }

    fun clear(id: String) {
        paths.sentinelFile(id).delete()
    }

    fun dirtyIds(): List<String> {
        val dir = paths.sentinelDir
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            .orEmpty()
    }
}
