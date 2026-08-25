package com.kite.zmusic.plugin

import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

internal sealed class UnpackResult {
    data class Ok(val manifest: PluginManifest) : UnpackResult()
    data class Invalid(val reason: String) : UnpackResult()
}

internal object ZppUnpacker {
    fun unpack(zpp: File, destDir: File): UnpackResult {
        if (!zpp.isFile) return UnpackResult.Invalid("不是文件")
        destDir.mkdirs()
        return try {
            ZipFile(zpp).use { zip -> unpackZip(zip, destDir) }
        } catch (e: ZipException) {
            destDir.deleteRecursively()
            UnpackResult.Invalid("无法打开归档")
        } catch (e: IOException) {
            destDir.deleteRecursively()
            UnpackResult.Invalid("读取归档失败")
        }
    }

    private fun unpackZip(zip: ZipFile, destDir: File): UnpackResult {
        val destCanon = destDir.canonicalFile
        val raw = zip.entries().toList().filterNot { it.name.startsWith("__MACOSX/") }
        if (raw.isEmpty()) return UnpackResult.Invalid("空包")
        val prefix = wrappingPrefix(raw.map { it.name })
        val mapped = ArrayList<Pair<java.util.zip.ZipEntry, String>>()
        for (entry in raw) {
            var rel = entry.name
            if (prefix != null) {
                if (rel == prefix.removeSuffix("/")) continue
                if (!rel.startsWith(prefix)) return UnpackResult.Invalid("包根不一致")
                rel = rel.removePrefix(prefix)
            }
            if (rel.isEmpty()) continue
            val directory = entry.isDirectory || rel.endsWith('/')
            if (!PluginPackageRules.zipEntryOk(rel, directory)) {
                return UnpackResult.Invalid("非法路径或扩展名: $rel")
            }
            mapped.add(entry to rel)
        }
        for ((entry, rel) in mapped) {
            val out = File(destDir, rel).canonicalFile
            if (!out.path.startsWith(destCanon.path + File.separator) && out != destCanon) {
                return UnpackResult.Invalid("路径穿越")
            }
            if (entry.isDirectory || rel.endsWith('/')) {
                out.mkdirs()
                continue
            }
            out.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return validateExtracted(destDir)
    }

    /** 只读清单 `id`，不解压。失败返回 null。 */
    fun peekId(zpp: File): String? {
        if (!zpp.isFile) return null
        return runCatching {
            ZipFile(zpp).use { zip ->
                val names = zip.entries().toList()
                    .map { it.name }
                    .filterNot { it.startsWith("__MACOSX/") }
                if (names.isEmpty()) return@use null
                val prefix = wrappingPrefix(names).orEmpty()
                val jsonName = names.firstOrNull { name ->
                    val rel = if (prefix.isEmpty()) name.trimStart('/') else name.removePrefix(prefix)
                    rel == "plugin.json"
                } ?: return@use null
                val entry = zip.getEntry(jsonName) ?: return@use null
                val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                PluginManifestParser.parse(text)?.id
            }
        }.getOrNull()
    }

    fun validateExtracted(root: File): UnpackResult {
        val jsonFile = File(root, "plugin.json")
        if (!jsonFile.isFile) return UnpackResult.Invalid("缺少 plugin.json")
        val readme = File(root, "README.md")
        if (!readme.isFile) return UnpackResult.Invalid("缺少 README.md")
        val hasPng = File(root, "plugin.png").isFile
        val hasSvg = File(root, "plugin.svg").isFile
        if (!hasPng && !hasSvg) return UnpackResult.Invalid("缺少图标")
        val text = runCatching { jsonFile.readText(Charsets.UTF_8) }.getOrNull()
            ?: return UnpackResult.Invalid("无法读取 plugin.json")
        val manifest = PluginManifestParser.parse(text)
            ?: return UnpackResult.Invalid("plugin.json 无效")
        val entryFile = File(root, manifest.entry)
        if (!entryFile.isFile) return UnpackResult.Invalid("缺少入口")
        return UnpackResult.Ok(manifest)
    }

    private fun wrappingPrefix(names: List<String>): String? {
        val top = LinkedHashSet<String>()
        for (name in names) {
            val trimmed = name.trimStart('/')
            if (trimmed.isEmpty()) continue
            val first = trimmed.substringBefore('/')
            if (first.isEmpty()) return null
            top.add(first)
            if (top.size > 1) return null
        }
        if (top.size != 1) return null
        val folder = top.first()
        val onlyFolder = names.all { name ->
            val trimmed = name.trimStart('/')
            trimmed == folder || trimmed == "$folder/" || trimmed.startsWith("$folder/")
        }
        if (!onlyFolder) return null
        val hasNested = names.any { name ->
            val trimmed = name.trimStart('/')
            trimmed.startsWith("$folder/") && trimmed.length > folder.length + 1
        }
        return if (hasNested) "$folder/" else null
    }
}
