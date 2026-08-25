package com.kite.zmusic.plugin

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 每插件一份 JSON 对象。文件名即插件 id。
 */
internal class PluginKvStore(private val dir: File) {
    private val lock = Any()

    fun get(pluginId: String, key: String): Any? {
        val k = normalizeKey(key) ?: return null
        synchronized(lock) {
            return load(pluginId)[k]
        }
    }

    fun set(pluginId: String, key: String, value: Any?): Boolean {
        val k = normalizeKey(key) ?: return false
        val encoded = PluginJson.stringify(value)
        if (encoded.toByteArray(StandardCharsets.UTF_8).size > MAX_VALUE_BYTES) return false
        synchronized(lock) {
            val table = load(pluginId)
            val next = LinkedHashMap(table)
            next[k] = value
            if (next.size > MAX_KEYS) return false
            return persist(pluginId, next)
        }
    }

    fun remove(pluginId: String, key: String): Boolean {
        val k = normalizeKey(key) ?: return false
        synchronized(lock) {
            val table = load(pluginId)
            if (!table.containsKey(k)) return true
            val next = LinkedHashMap(table)
            next.remove(k)
            return persist(pluginId, next)
        }
    }

    fun keys(pluginId: String): List<String> {
        synchronized(lock) {
            return load(pluginId).keys.toList()
        }
    }

    fun clear(pluginId: String): Boolean {
        synchronized(lock) {
            return persist(pluginId, emptyMap())
        }
    }

    fun erase(pluginId: String) {
        synchronized(lock) {
            file(pluginId).delete()
            tmp(pluginId).delete()
        }
    }

    private fun load(pluginId: String): Map<String, Any?> {
        val f = file(pluginId)
        if (!f.isFile) return emptyMap()
        val text = runCatching { f.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return emptyMap()
        val obj = PluginJson.parseObject(text) ?: return emptyMap()
        return obj
    }

    private fun persist(pluginId: String, table: Map<String, Any?>): Boolean {
        dir.mkdirs()
        val json = PluginJson.stringify(table)
        if (json.toByteArray(StandardCharsets.UTF_8).size > MAX_FILE_BYTES) return false
        val target = file(pluginId)
        val staging = tmp(pluginId)
        return try {
            staging.writeText(json, StandardCharsets.UTF_8)
            if (target.exists() && !target.delete()) {
                staging.delete()
                return false
            }
            if (!staging.renameTo(target)) {
                staging.copyTo(target, overwrite = true)
                staging.delete()
            }
            true
        } catch (_: Exception) {
            runCatching { staging.delete() }
            false
        }
    }

    private fun file(pluginId: String) = File(dir, "$pluginId.json")
    private fun tmp(pluginId: String) = File(dir, "$pluginId.json.tmp")

    companion object {
        const val MAX_KEY = 256
        const val MAX_KEYS = 512
        const val MAX_VALUE_BYTES = 262_144
        const val MAX_FILE_BYTES = 2_097_152

        fun normalizeKey(raw: String): String? {
            val t = raw.trim()
            return t.takeIf { it.isNotEmpty() && it.length <= MAX_KEY }
        }
    }
}
