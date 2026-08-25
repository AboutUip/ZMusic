package com.kite.zmusic.plugin

internal object PluginInts {
    fun long(value: Any?, min: Long, max: Long): Long? {
        val n = when (value) {
            is Number -> value.toDouble()
            else -> return null
        }
        if (n.isNaN() || n.isInfinite()) return null
        if (n < min.toDouble() || n > max.toDouble()) return null
        val v = n.toLong()
        if (v.toDouble() != n) return null
        return v
    }

    fun nonNegMs(value: Any?): Long? = long(value, 0L, Long.MAX_VALUE)
}
