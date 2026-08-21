package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RealtimeCacheMode(val title: String, val available: Boolean) {
    Cautious("谨慎", true),
    Realtime("实时", true),
    Aggressive("激进", true),
    ;

    companion object {
        fun fromName(raw: String?): RealtimeCacheMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Cautious
    }
}

enum class RealtimeCacheSpaceUnit(val title: String, val multiplier: Long) {
    MB("MB", 1024L * 1024L),
    GB("GB", 1024L * 1024L * 1024L),
    ;

    companion object {
        fun fromName(raw: String?): RealtimeCacheSpaceUnit =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MB
    }
}

internal const val REALTIME_CACHE_DEFAULT_SPACE = 512.0
internal const val REALTIME_CACHE_MIN_LIMIT_BYTES = 1024L * 1024L

data class RealtimeCachePrefs(
    val enabled: Boolean = false,
    val mode: RealtimeCacheMode = RealtimeCacheMode.Cautious,
    val spaceValue: Double = REALTIME_CACHE_DEFAULT_SPACE,
    val spaceUnit: RealtimeCacheSpaceUnit = RealtimeCacheSpaceUnit.MB,
    val firstEnabledAt: Long = 0L,
    val lastRefreshEpochDay: Long = Long.MIN_VALUE,
    val lastAggressiveSweepEpochDay: Long = Long.MIN_VALUE,
) {
    val limitBytes: Long
        get() {
            val raw = (spaceValue * spaceUnit.multiplier.toDouble()).toLong()
            return raw.coerceAtLeast(REALTIME_CACHE_MIN_LIMIT_BYTES)
        }

    val cachePlaybackEnabled: Boolean
        get() = enabled && mode.available

    val liveDownloadEnabled: Boolean
        get() = enabled &&
            (mode == RealtimeCacheMode.Realtime || mode == RealtimeCacheMode.Aggressive)
}

class RealtimeCacheStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<RealtimeCachePrefs> = _state.asStateFlow()

    fun current(): RealtimeCachePrefs = _state.value

    fun setEnabled(enabled: Boolean) {
        val cur = _state.value
        if (enabled == cur.enabled) return
        val first = when {
            enabled && cur.firstEnabledAt <= 0L -> System.currentTimeMillis()
            else -> cur.firstEnabledAt
        }
        write(cur.copy(enabled = enabled, firstEnabledAt = first))
    }

    fun setMode(mode: RealtimeCacheMode) {
        if (!mode.available) return
        val cur = _state.value
        if (mode == cur.mode) return
        write(cur.copy(mode = mode))
    }

    fun setSpace(value: Double, unit: RealtimeCacheSpaceUnit) {
        val clamped = value.coerceAtLeast(0.0)
        val cur = _state.value
        if (clamped == cur.spaceValue && unit == cur.spaceUnit) return
        write(cur.copy(spaceValue = clamped, spaceUnit = unit))
    }

    fun markRefreshed(epochDay: Long) {
        val cur = _state.value
        if (epochDay == cur.lastRefreshEpochDay) return
        write(cur.copy(lastRefreshEpochDay = epochDay))
    }

    fun markAggressiveSwept(epochDay: Long) {
        val cur = _state.value
        if (epochDay == cur.lastAggressiveSweepEpochDay) return
        write(cur.copy(lastAggressiveSweepEpochDay = epochDay))
    }

    private fun write(next: RealtimeCachePrefs) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_MODE, next.mode.name)
            .putString(KEY_SPACE_VALUE, next.spaceValue.toString())
            .putString(KEY_SPACE_UNIT, next.spaceUnit.name)
            .putLong(KEY_FIRST_ENABLED, next.firstEnabledAt)
            .putLong(KEY_LAST_REFRESH_DAY, next.lastRefreshEpochDay)
            .putLong(KEY_LAST_AGGRESSIVE_SWEEP, next.lastAggressiveSweepEpochDay)
            .apply()
        _state.value = next
    }

    private fun load(): RealtimeCachePrefs {
        val value = prefs.getString(KEY_SPACE_VALUE, null)?.toDoubleOrNull()
            ?: REALTIME_CACHE_DEFAULT_SPACE
        return RealtimeCachePrefs(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            mode = RealtimeCacheMode.fromName(prefs.getString(KEY_MODE, null)),
            spaceValue = value,
            spaceUnit = RealtimeCacheSpaceUnit.fromName(prefs.getString(KEY_SPACE_UNIT, null)),
            firstEnabledAt = prefs.getLong(KEY_FIRST_ENABLED, 0L),
            lastRefreshEpochDay = prefs.getLong(KEY_LAST_REFRESH_DAY, Long.MIN_VALUE),
            lastAggressiveSweepEpochDay = prefs.getLong(KEY_LAST_AGGRESSIVE_SWEEP, Long.MIN_VALUE),
        )
    }

    companion object {
        const val DEFAULT_SPACE_VALUE = REALTIME_CACHE_DEFAULT_SPACE
        const val MIN_LIMIT_BYTES = REALTIME_CACHE_MIN_LIMIT_BYTES
        private const val PREFS = "zmusic_realtime_cache"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MODE = "mode"
        private const val KEY_SPACE_VALUE = "space_value"
        private const val KEY_SPACE_UNIT = "space_unit"
        private const val KEY_FIRST_ENABLED = "first_enabled_at"
        private const val KEY_LAST_REFRESH_DAY = "last_refresh_epoch_day"
        private const val KEY_LAST_AGGRESSIVE_SWEEP = "last_aggressive_sweep_day"
    }
}
