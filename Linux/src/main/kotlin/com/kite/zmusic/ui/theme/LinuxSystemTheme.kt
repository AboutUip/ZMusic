package com.kite.zmusic.ui.theme

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * OS dark-theme probe that never touches Skiko JNI / D-Bus.
 *
 * Compose Desktop's [androidx.compose.foundation.isSystemInDarkTheme] ends in
 * `org.jetbrains.skiko.SystemThemeHelper.getCurrentSystemTheme`, which SIGSEGVs
 * on some Linux sessions (Kali, dual libdbus, missing session bus mutex).
 */
object LinuxSystemTheme {
    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var cachedDark: Boolean = false

    fun isDark(): Boolean {
        val now = System.currentTimeMillis()
        if (now - cachedAtMs in 0 until CACHE_MS) return cachedDark
        val dark = runCatching { detect() }.getOrDefault(false)
        cachedDark = dark
        cachedAtMs = now
        return dark
    }

    internal fun parseGtkIni(text: String): Boolean? {
        val prefer = iniValue(text, "gtk-application-prefer-dark-theme")
        if (prefer != null) {
            val v = prefer.lowercase()
            if (v == "1" || v == "true" || v == "yes") return true
            if (v == "0" || v == "false" || v == "no") {
                parseThemeName(iniValue(text, "gtk-theme-name").orEmpty())?.let { return it }
                return false
            }
        }
        return parseThemeName(iniValue(text, "gtk-theme-name").orEmpty())
    }

    internal fun parseGsettingsColorScheme(raw: String): Boolean? {
        val s = raw.lowercase()
        if (s.contains("prefer-dark")) return true
        if (s.contains("prefer-light")) return false
        return null
    }

    internal fun parseThemeName(name: String): Boolean? {
        val n = name.trim().trim('\'', '"')
        if (n.isEmpty()) return null
        if (n.contains("dark", ignoreCase = true)) return true
        return null
    }

    internal fun parseKdeGlobals(text: String): Boolean? {
        val scheme = iniValue(text, "ColorScheme") ?: iniValue(text, "LookAndFeelPackage")
        return scheme?.let { parseThemeName(it) }
    }

    private fun detect(): Boolean {
        System.getenv("GTK_THEME")?.let { parseThemeName(it) }?.let { return it }
        gtkIniPaths().forEach { path ->
            if (path.exists()) {
                parseGtkIni(path.readText())?.let { return it }
            }
        }
        homeConfig("kdeglobals").takeIf { it.exists() }?.let { path ->
            parseKdeGlobals(path.readText())?.let { return it }
        }
        query("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
            ?.let { parseGsettingsColorScheme(it) }
            ?.let { return it }
        query("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
            ?.let { parseThemeName(it) }
            ?.let { return it }
        return false
    }

    private fun gtkIniPaths(): List<Path> {
        val home = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home").orEmpty() + "/.config")
        return listOf(
            Path.of(home, "gtk-4.0", "settings.ini"),
            Path.of(home, "gtk-3.0", "settings.ini"),
        )
    }

    private fun homeConfig(file: String): Path {
        val home = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home").orEmpty() + "/.config")
        return Path.of(home, file)
    }

    private fun iniValue(text: String, key: String): String? {
        val prefix = "$key="
        return text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix, ignoreCase = true) && !it.startsWith("#") }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotEmpty() }
    }

    private fun query(vararg cmd: String): String? = runCatching {
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val finished = proc.waitFor(400, TimeUnit.MILLISECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return@runCatching null
        }
        if (proc.exitValue() != 0) return@runCatching null
        proc.inputStream.bufferedReader().use { it.readText() }.trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private const val CACHE_MS = 2_500L
}
