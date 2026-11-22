package com.kite.zmusic.plugin

import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ZppTestSupport {
    val PNG_1X1: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    fun writePng(file: File) {
        file.parentFile?.mkdirs()
        file.writeBytes(PNG_1X1)
    }

    fun writeMinimalPlugin(
        root: File,
        id: String = "com.example.demo",
        name: String = "示例插件",
        version: Int = 1,
        entry: String = "index.js",
        engineMin: Int = 1,
        engineMax: Int? = null,
        extraFiles: Map<String, String> = emptyMap(),
        extraBytes: Map<String, ByteArray> = emptyMap(),
        entryJs: String = """
            Xuan.runtime.register(Xuan.runtime.State.Initializing);
            Xuan.runtime.register(Xuan.runtime.State.Running);
        """.trimIndent(),
        capabilitiesJson: String? = "[]",
        wrapFolder: String? = null,
    ) {
        val packRoot = if (wrapFolder != null) File(root, wrapFolder) else root
        packRoot.mkdirs()
        val engineMaxLine = if (engineMax != null) ",\"max\":$engineMax" else ""
        val capabilitiesLine = if (capabilitiesJson != null) {
            ",\"capabilities\":$capabilitiesJson"
        } else {
            ""
        }
        File(packRoot, "plugin.json").writeText(
            """
            {
              "zpp": 1,
              "id": "$id",
              "name": "$name",
              "version": $version,
              "entry": "$entry",
              "engine": { "min": $engineMin$engineMaxLine }
              $capabilitiesLine
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        File(packRoot, "README.md").writeText("# $name\n", Charsets.UTF_8)
        writePng(File(packRoot, "plugin.png"))
        val entryFile = File(packRoot, entry)
        entryFile.parentFile?.mkdirs()
        entryFile.writeText(entryJs, Charsets.UTF_8)
        extraFiles.forEach { (path, content) ->
            val f = File(packRoot, path)
            f.parentFile?.mkdirs()
            f.writeText(content, Charsets.UTF_8)
        }
        extraBytes.forEach { (path, bytes) ->
            val f = File(packRoot, path)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
    }

    fun zipTo(sourceDir: File, zpp: File) {
        zpp.parentFile?.mkdirs()
        ZipOutputStream(zpp.outputStream()).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                val rel = file.toRelativeString(sourceDir).replace('\\', '/')
                if (rel.isEmpty() || rel == ".") return@forEach
                if (file.isDirectory) {
                    zos.putNextEntry(ZipEntry("$rel/"))
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(ZipEntry(rel))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    fun packZpp(
        dest: File,
        id: String,
        entryJs: String,
        extraFiles: Map<String, String> = emptyMap(),
        extraBytes: Map<String, ByteArray> = emptyMap(),
        capabilitiesJson: String? = "[]",
        engineMin: Int = 1,
        engineMax: Int? = null,
        version: Int = 1,
        name: String = id,
    ): File {
        val src = File(dest.parentFile, dest.name + ".src")
        src.deleteRecursively()
        writeMinimalPlugin(
            root = src,
            id = id,
            name = name,
            version = version,
            engineMin = engineMin,
            engineMax = engineMax,
            extraFiles = extraFiles,
            extraBytes = extraBytes,
            entryJs = entryJs,
            capabilitiesJson = capabilitiesJson,
        )
        zipTo(src, dest)
        return dest
    }
}
