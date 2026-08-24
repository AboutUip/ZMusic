package com.kite.zmusic.plugin

internal data class PluginManifest(
    val id: String,
    val name: String,
    val version: Int,
    val entry: String,
    val engineMin: Int,
    val engineMax: Int?,
    val description: String?,
    val author: String?,
    val homepage: String?,
    val capabilities: List<String>,
) {
    fun compatibleWith(engineNumber: Int): Boolean {
        if (engineMin > engineNumber) return false
        val max = engineMax
        return max == null || engineNumber <= max
    }
}

internal object PluginManifestParser {
    fun parse(text: String): PluginManifest? {
        val obj = PluginJson.parseObject(text) ?: return null
        val zpp = intField(obj, "zpp") ?: return null
        if (zpp != 1) return null
        val id = stringField(obj, "id")?.takeIf { PluginIds.isValid(it) } ?: return null
        val name = stringField(obj, "name")?.takeIf { it.isNotEmpty() } ?: return null
        val version = intField(obj, "version") ?: return null
        if (version < 0) return null
        val entry = stringField(obj, "entry")?.takeIf { PluginPackageRules.entryPathOk(it) } ?: return null
        val engine = obj["engine"] as? Map<*, *> ?: return null
        val engineMin = intField(engine, "min") ?: return null
        val engineMax = if (engine.containsKey("max") && engine["max"] != null) {
            intField(engine, "max") ?: return null
        } else {
            null
        }
        if (engineMax != null && engineMax < engineMin) return null
        val capabilities = PluginCapabilities.parse(obj["capabilities"]) ?: return null
        val description = optionalString(obj, "description")
        val author = optionalString(obj, "author")
        val homepage = optionalString(obj, "homepage")
        if (homepage != null && !homepage.startsWith("http://") && !homepage.startsWith("https://")) {
            return null
        }
        return PluginManifest(
            id = id,
            name = name,
            version = version,
            entry = entry,
            engineMin = engineMin,
            engineMax = engineMax,
            description = description,
            author = author,
            homepage = homepage,
            capabilities = capabilities,
        )
    }

    private fun stringField(obj: Map<*, *>, key: String): String? =
        obj[key] as? String

    private fun optionalString(obj: Map<*, *>, key: String): String? {
        val v = obj[key] ?: return null
        val s = v as? String ?: return null
        return s.takeIf { it.isNotEmpty() }
    }

    private fun intField(obj: Map<*, *>, key: String): Int? {
        val v = obj[key] ?: return null
        return when (v) {
            is Int -> v
            is Long -> v.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
            else -> null
        }
    }
}
