package com.kite.zmusic.plugin

/**
 * [Xuan.delay] 的毫秒参数。单次必须是 `1`～[MAX_MS] 的整数（含 1 分钟）。
 */
internal object PluginDelay {
    const val MAX_MS = 60_000L

    fun parseMs(value: Any?): Long? {
        val n = when (value) {
            is Number -> value.toDouble()
            else -> return null
        }
        if (n.isNaN() || n.isInfinite()) return null
        if (n < 1.0 || n > MAX_MS.toDouble()) return null
        val ms = n.toLong()
        if (ms.toDouble() != n) return null
        return ms
    }
}
