package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** 调音偏好：本机 SharedPreferences，不进一起听协议。 */
class TunePrefsStore(context: Context) {

    private val disk = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(read())
    val prefs: StateFlow<TunePrefs> = _prefs.asStateFlow()

    fun current(): TunePrefs = _prefs.value

    fun set(next: TunePrefs) {
        val clean = next.sanitized()
        if (clean == _prefs.value) return
        write(clean)
    }

    fun reset() {
        set(TunePrefs.Default)
    }

    private fun read(): TunePrefs {
        val eqRaw = disk.getString(KEY_EQ, null)
        val eq = if (eqRaw.isNullOrBlank()) {
            TuneBands.flat()
        } else {
            eqRaw.split(',')
                .mapNotNull { it.trim().toFloatOrNull() }
                .let { TuneBands.normalize(it) }
        }
        return TunePrefs(
            enabled = disk.getBoolean(KEY_ENABLED, false),
            preset = TunePreset.fromId(disk.getString(KEY_PRESET, null)),
            eq = eq,
            bass = disk.getFloat(KEY_BASS, 0f),
            loudness = disk.getFloat(KEY_LOUDNESS, 0f),
            compress = disk.getFloat(KEY_COMPRESS, 0f),
            reverb = disk.getFloat(KEY_REVERB, 0f),
            pitchSemitones = disk.getInt(KEY_PITCH, 0),
            speed = disk.getFloat(KEY_SPEED, 1f),
        ).sanitized()
    }

    private fun write(next: TunePrefs) {
        disk.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_PRESET, next.preset.id)
            .putString(KEY_EQ, next.eq.joinToString(",") { "%.2f".format(Locale.US, it) })
            .putFloat(KEY_BASS, next.bass)
            .putFloat(KEY_LOUDNESS, next.loudness)
            .putFloat(KEY_COMPRESS, next.compress)
            .putFloat(KEY_REVERB, next.reverb)
            .putInt(KEY_PITCH, next.pitchSemitones)
            .putFloat(KEY_SPEED, next.speed)
            .apply()
        _prefs.value = next
    }

    companion object {
        const val PREFS = "zmusic_tune"
        const val KEY_ENABLED = "enabled"
        const val KEY_PRESET = "preset"
        const val KEY_EQ = "eq"
        const val KEY_BASS = "bass"
        const val KEY_LOUDNESS = "loudness"
        const val KEY_COMPRESS = "compress"
        const val KEY_REVERB = "reverb"
        const val KEY_PITCH = "pitch"
        const val KEY_SPEED = "speed"
    }
}
