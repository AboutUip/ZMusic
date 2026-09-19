package com.kite.zmusic.data

import com.kite.zmusic.i18n.t
import kotlin.math.abs
import kotlin.math.pow

/** 竖屏播放页「调音」：本机 PCM 均衡 / 动态 / 混响，以及 Sonic 变速变调。 */
data class TunePrefs(
    val enabled: Boolean = false,
    val preset: TunePreset = TunePreset.FLAT,
    val eq: List<Float> = TuneBands.flat(),
    val bass: Float = 0f,
    val loudness: Float = 0f,
    val compress: Float = 0f,
    val reverb: Float = 0f,
    val pitchSemitones: Int = 0,
    val speed: Float = 1f,
) {
    fun sanitized(): TunePrefs {
        val bands = TuneBands.normalize(eq)
        val preset = preset.let { p ->
            TunePreset.entries.firstOrNull { it == p } ?: TunePreset.FLAT
        }
        return copy(
            preset = if (preset == TunePreset.CUSTOM) TunePreset.CUSTOM else preset,
            eq = bands,
            bass = bass.coerceIn(0f, 1f),
            loudness = loudness.coerceIn(0f, 1f),
            compress = compress.coerceIn(0f, 1f),
            reverb = reverb.coerceIn(0f, 1f),
            pitchSemitones = pitchSemitones.coerceIn(TuneBands.MIN_SEMITONES, TuneBands.MAX_SEMITONES),
            speed = speed.coerceIn(TuneBands.MIN_SPEED, TuneBands.MAX_SPEED),
        )
    }

    fun withPreset(next: TunePreset): TunePrefs {
        if (next == TunePreset.CUSTOM) return copy(preset = TunePreset.CUSTOM).sanitized()
        return copy(preset = next, eq = next.bands()).sanitized()
    }

    fun withEq(index: Int, db: Float): TunePrefs {
        if (index !in 0 until TuneBands.COUNT) return this
        val next = eq.toMutableList()
        while (next.size < TuneBands.COUNT) next.add(0f)
        next[index] = db.coerceIn(TuneBands.MIN_DB, TuneBands.MAX_DB)
        return copy(eq = next, preset = TunePreset.CUSTOM).sanitized()
    }

    fun dspActive(): Boolean {
        if (!enabled) return false
        if (eq.any { abs(it) >= 0.05f }) return true
        if (bass >= 0.01f || loudness >= 0.01f || compress >= 0.01f || reverb >= 0.01f) return true
        return false
    }

    fun playbackPitch(): Float {
        if (!enabled || pitchSemitones == 0) return 1f
        return 2.0.pow(pitchSemitones / 12.0).toFloat()
    }

    fun playbackSpeed(): Float {
        if (!enabled) return 1f
        return speed.coerceIn(TuneBands.MIN_SPEED, TuneBands.MAX_SPEED)
    }

    companion object {
        val Default: TunePrefs = TunePrefs()
    }
}

enum class TunePreset(val id: String, private val titleZh: String) {
    FLAT("flat", "原声"),
    POP("pop", "流行"),
    DANCE("dance", "电子"),
    VOCAL("vocal", "人声"),
    BASS("bass", "低音"),
    TREBLE("treble", "明亮"),
    WARM("warm", "温暖"),
    CUSTOM("custom", "自定义"),
    ;

    val title: String get() = t(titleZh)

    fun bands(): List<Float> = when (this) {
        FLAT, CUSTOM -> TuneBands.flat()
        POP -> listOf(3f, 1.5f, -1f, 0.5f, 1.5f, 2.5f, 2f, 1f)
        DANCE -> listOf(4.5f, 2.5f, -1.5f, 0f, 1f, 2.5f, 3.5f, 2.5f)
        VOCAL -> listOf(-2.5f, -1f, 1.5f, 3.5f, 3f, 1f, 0f, -1f)
        BASS -> listOf(6.5f, 4.5f, 1.5f, 0f, -1f, -1.5f, 0f, 0.5f)
        TREBLE -> listOf(-2f, -1f, 0f, 1f, 2.5f, 4.5f, 5.5f, 4.5f)
        WARM -> listOf(2.5f, 3.5f, 2f, 0.5f, -1f, -2f, -2.5f, -1.5f)
    }

    companion object {
        fun fromId(raw: String?): TunePreset =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: FLAT
    }
}

object TuneBands {
    const val COUNT = 8
    const val MIN_DB = -12f
    const val MAX_DB = 12f
    const val MIN_SEMITONES = -12
    const val MAX_SEMITONES = 12
    const val MIN_SPEED = 0.5f
    const val MAX_SPEED = 1.5f
    val HZ = floatArrayOf(60f, 150f, 400f, 1_000f, 2_400f, 6_000f, 10_000f, 14_000f)
    val LABELS = listOf("60", "150", "400", "1k", "2.4k", "6k", "10k", "14k")

    fun flat(): List<Float> = List(COUNT) { 0f }

    fun normalize(eq: List<Float>): List<Float> {
        val out = MutableList(COUNT) { 0f }
        val n = eq.size.coerceAtMost(COUNT)
        for (i in 0 until n) {
            out[i] = eq[i].coerceIn(MIN_DB, MAX_DB)
        }
        return out
    }
}

fun tuneRowSubtitle(prefs: TunePrefs): String {
    if (!prefs.enabled) return t("均衡、响度、变速")
    val bits = buildList {
        when {
            prefs.preset != TunePreset.FLAT && prefs.preset != TunePreset.CUSTOM ->
                add(prefs.preset.title)
            prefs.eq.any { abs(it) >= 0.05f } -> add(t("均衡"))
        }
        if (prefs.pitchSemitones != 0) {
            val n = prefs.pitchSemitones
            add(if (n > 0) t("+%s 半音", n) else t("%s 半音", n))
        }
        if (abs(prefs.speed - 1f) >= 0.02f) {
            add("%.2f×".format(prefs.speed))
        }
        if (prefs.loudness >= 0.2f) add(t("响度"))
        if (prefs.reverb >= 0.2f) add(t("混响"))
    }
    return if (bits.isEmpty()) t("已开启") else bits.joinToString(" · ")
}
