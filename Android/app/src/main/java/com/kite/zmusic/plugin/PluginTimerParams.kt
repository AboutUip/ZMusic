package com.kite.zmusic.plugin

/**
 * `Xuan.timer` 的 `ms` / `reps`。`ms` 最小 1，不设上限（有限正整数）。
 */
internal object PluginTimerParams {
    fun parseMs(value: Any?): Long? {
        val n = when (value) {
            is Number -> value.toDouble()
            else -> return null
        }
        if (n.isNaN() || n.isInfinite()) return null
        if (n < 1.0 || n > Long.MAX_VALUE.toDouble()) return null
        val ms = n.toLong()
        if (ms.toDouble() != n) return null
        return ms
    }

    fun parseReps(value: Any?): Int? {
        val n = when (value) {
            is Number -> value.toDouble()
            else -> return null
        }
        if (n.isNaN() || n.isInfinite()) return null
        if (n < 0.0 || n > Int.MAX_VALUE.toDouble()) return null
        val reps = n.toInt()
        if (reps.toDouble() != n) return null
        return reps
    }
}
