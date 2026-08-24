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
        if (entry.isEmpty() || entry.startsWith('/') || entry.contains('\\')) return false
        if (entry.split('/').any { it == ".." || it.isEmpty() }) return false
        return entry.endsWith(".js") && extensionOf(entry) == "js"
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
