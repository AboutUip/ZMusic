package com.kite.zmusic.plugin

/**
 * 引擎可读版本是唯一源。整数按 [docs/plugin-engine/VERSIONING.md] 换算，禁止另存一份数字。
 * 展示为 `0.1.0`，没有 `v`、不补零。换算时每段仍按两位对齐，避免 `0.1.12` 与 `0.11.2` 冲突。
 */
object PluginEngineVersion {
    const val DISPLAY = "0.1.0"

    private val ENGINE_DISPLAY = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{1,2})$""")

    val number: Int = encodeEngine(DISPLAY)

    /**
     * 引擎展示 `major.minor.patch` → `major * 10000 + minor * 100 + patch`。
     */
    fun encodeEngine(display: String): Int {
        val m = ENGINE_DISPLAY.matchEntire(display.trim())
            ?: error("非法引擎版本展示: $display")
        return triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    /**
     * 宿主 [versionName]（例如 `1.2.3`）用同一套三段规则。忽略 `-` / `+` 后的后缀。
     * 缺段按 0。超过 99 的段视为 0（不把 versionCode 混进来）。
     */
    fun encodeAppVersionName(versionName: String): Int {
        val core = versionName.trim()
            .removePrefix("v")
            .removePrefix("V")
            .trim()
            .substringBefore('-')
            .substringBefore('+')
        val parts = core.split('.')
        fun seg(i: Int): Int {
            val n = parts.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            return n.coerceIn(0, 99)
        }
        return triple(seg(0), seg(1), seg(2))
    }

    fun encodePlugin(major: Int, minor: Int): Int {
        require(major in 0..99 && minor in 0..99)
        return major * 100 + minor
    }

    private fun triple(major: Int, minor: Int, patch: Int): Int {
        require(major in 0..99 && minor in 0..99 && patch in 0..99)
        return major * 10000 + minor * 100 + patch
    }
}
