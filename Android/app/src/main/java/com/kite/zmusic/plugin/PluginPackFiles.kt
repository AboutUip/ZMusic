package com.kite.zmusic.plugin

import java.io.File

/**
 * 只读本插件解压根。路径相对包根。
 */
internal object PluginPackFiles {
    fun resolve(root: File, relative: String): File? {
        val rel = PluginPackageRules.normalizeRel(relative) ?: return null
        val base = root.canonicalFile
        val file = File(root, rel).canonicalFile
        val prefix = base.path + File.separator
        if (file != base && !file.path.startsWith(prefix)) return null
        return file
    }
}
