package com.kite.zmusic.plugin

internal object PluginPackageRules {
    val ALLOWED_EXTENSIONS: Set<String> = setOf(
        "js", "json",
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg",
        "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav",
        "mp4", "webm", "mkv", "mov",
        "txt", "md", "csv", "tsv", "lrc", "srt", "vtt",
        "yml", "yaml", "toml", "ini", "cue", "m3u", "m3u8",
    )

    fun entryPathOk(entry: String): Boolean {
        val rel = normalizeRel(entry) ?: return false
        return rel.endsWith(".js") && extensionOf(rel) == "js"
    }

    /**
     * 包内相对路径：去空白、去掉前导 `./`，禁止 `..` / 绝对路径 / 反斜杠。
     */
    fun normalizeRel(path: String): String? {
        var p = path.trim()
        while (p.startsWith("./")) {
            p = p.removePrefix("./")
        }
        if (p.isEmpty() || p.startsWith('/') || p.contains('\\') || p.contains('\u0000')) {
            return null
        }
        if (p.endsWith('/')) return null
        val parts = p.split('/')
        if (parts.any { it == ".." || it.isEmpty() }) return null
        return p
    }

    fun requirePathOk(path: String): Boolean {
        val rel = normalizeRel(path) ?: return false
        return rel.endsWith(".js") && extensionOf(rel) == "js"
    }

    fun packFilePathOk(path: String): Boolean {
        val rel = normalizeRel(path) ?: return false
        val ext = extensionOf(rel) ?: return false
        return ext in ALLOWED_EXTENSIONS
    }

    /**
     * ZIP 条目相对包根。目录以 `/` 结尾时视为目录，允许无扩展名。
     * 文件必须有白名单内的**小写**扩展名。
     */
    fun zipEntryOk(relative: String, directory: Boolean): Boolean {
        if (relative.isEmpty()) return !directory
        if (relative.contains('\\') || relative.startsWith('/') || relative.contains('\u0000')) {
            return false
        }
        val parts = relative.removeSuffix("/").split('/')
        if (parts.any { it == ".." || it.isEmpty() }) return false
        if (directory) return true
        val ext = extensionOf(relative)
        return ext != null && ext in ALLOWED_EXTENSIONS
    }

    fun extensionOf(path: String): String? {
        val name = path.substringAfterLast('/').removeSuffix("/")
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return null
        return name.substring(dot + 1)
    }
}
