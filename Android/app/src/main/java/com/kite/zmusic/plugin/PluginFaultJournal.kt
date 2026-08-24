package com.kite.zmusic.plugin

import java.io.File

/**
 * 按插件留下最近若干行，供弹窗展示。始终记录，与调试开关无关。
 * 同步写到 [dir]，以便进程崩溃后下次启动仍能读到上次快照。
 * 这不是完整 logcat 落盘。
 */
internal class PluginFaultJournal(private val dir: File) {
    private val lock = Any()
    private val lines = HashMap<String, ArrayDeque<String>>()

    fun begin(pluginId: String) {
        synchronized(lock) {
            val q = ArrayDeque<String>()
            lines[pluginId] = q
            persistLocked(pluginId, q)
        }
    }

    fun append(pluginId: String, message: String) {
        if (message.isEmpty()) return
        val incoming = message.replace("\r\n", "\n").split('\n')
        synchronized(lock) {
            val q = queueLocked(pluginId)
            for (line in incoming) {
                q.addLast(line)
                while (q.size > MaxLines) q.removeFirst()
            }
            trimCharsLocked(q)
            persistLocked(pluginId, q)
        }
    }

    fun snapshot(pluginId: String): String {
        synchronized(lock) {
            return queueLocked(pluginId).joinToString("\n")
        }
    }

    fun readPersisted(pluginId: String): String = readFile(pluginId)

    fun clear(pluginId: String) {
        synchronized(lock) {
            lines.remove(pluginId)
            runCatching { fileFor(pluginId).delete() }
        }
    }

    private fun queueLocked(pluginId: String): ArrayDeque<String> {
        lines[pluginId]?.let { return it }
        val q = ArrayDeque<String>()
        val persisted = readFile(pluginId)
        if (persisted.isNotEmpty()) {
            persisted.split('\n').forEach { q.addLast(it) }
        }
        lines[pluginId] = q
        return q
    }

    private fun readFile(pluginId: String): String {
        val file = fileFor(pluginId)
        if (!file.isFile) return ""
        return runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("").trimEnd()
    }

    private fun persistLocked(pluginId: String, q: ArrayDeque<String>) {
        dir.mkdirs()
        val file = fileFor(pluginId)
        runCatching { file.writeText(q.joinToString("\n"), Charsets.UTF_8) }
    }

    private fun fileFor(pluginId: String): File = File(dir, pluginId)

    private fun trimCharsLocked(q: ArrayDeque<String>) {
        var total = q.sumOf { it.length + 1 }
        while (q.size > 1 && total > MaxChars) {
            val removed = q.removeFirst()
            total -= removed.length + 1
        }
    }

    companion object {
        const val MaxLines = 100
        const val MaxChars = 12_000
    }
}
