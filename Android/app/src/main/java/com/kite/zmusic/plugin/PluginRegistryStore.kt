package com.kite.zmusic.plugin

import java.io.File

internal data class PluginRegistrySnapshot(
    val lastEngineNumber: Int,
    val plugins: List<PluginRecord>,
)

internal class PluginRegistryStore(private val file: File) {
    fun load(): PluginRegistrySnapshot {
        if (!file.isFile) {
            return PluginRegistrySnapshot(PluginEngineVersion.number, emptyList())
        }
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            ?: return PluginRegistrySnapshot(PluginEngineVersion.number, emptyList())
        val obj = PluginJson.parseObject(text)
            ?: return PluginRegistrySnapshot(PluginEngineVersion.number, emptyList())
        val engine = when (val e = obj["engine"]) {
            is Int -> e
            is Long -> e.toInt()
            else -> PluginEngineVersion.number
        }
        val plugins = (obj["plugins"] as? List<*>).orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            val name = m["name"] as? String ?: return@mapNotNull null
            val version = (m["version"] as? Number)?.toInt() ?: return@mapNotNull null
            val entry = m["entry"] as? String ?: return@mapNotNull null
            val engineMin = (m["engineMin"] as? Number)?.toInt() ?: 1
            val engineMax = (m["engineMax"] as? Number)?.toInt()
            val enabled = m["enabled"] as? Boolean ?: false
            val quarantined = m["quarantined"] as? Boolean ?: false
            val capabilities = (m["capabilities"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?.let { PluginCapabilities.parse(it) }
                ?: emptyList()
            PluginRecord(
                id = id,
                name = name,
                version = version,
                entry = entry,
                engineMin = engineMin,
                engineMax = engineMax,
                enabled = enabled,
                quarantined = quarantined,
                capabilities = capabilities,
            )
        }
        return PluginRegistrySnapshot(engine, plugins)
    }

    fun save(snapshot: PluginRegistrySnapshot) {
        file.parentFile?.mkdirs()
        val payload = mapOf(
            "engine" to snapshot.lastEngineNumber,
            "plugins" to snapshot.plugins.map { p ->
                linkedMapOf<String, Any?>(
                    "id" to p.id,
                    "name" to p.name,
                    "version" to p.version,
                    "entry" to p.entry,
                    "engineMin" to p.engineMin,
                    "engineMax" to p.engineMax,
                    "enabled" to p.enabled,
                    "quarantined" to p.quarantined,
                    "capabilities" to p.capabilities,
                )
            },
        )
        file.writeText(PluginJson.stringify(payload), Charsets.UTF_8)
    }
}
