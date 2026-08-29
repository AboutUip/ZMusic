package com.kite.zmusic.playback

data class AudioSpectrumBands(
    val low: Float = 0f,
    val mid: Float = 0f,
    val high: Float = 0f,
) {
    companion object {
        val ZERO = AudioSpectrumBands()
    }
}

/** 桌面暂无 FFT：用播放进度合成低/中/高包络，光球仍能跟节奏呼吸。 */
fun pulseSpectrum(playing: Boolean, positionMs: Long): AudioSpectrumBands {
    if (!playing) return AudioSpectrumBands(0.08f, 0.06f, 0.05f)
    val t = positionMs / 1000.0
    fun band(speedA: Double, speedB: Double, phase: Double, floor: Float, span: Float): Float {
        val u = 0.5 + 0.5 * kotlin.math.sin(t * speedA) * kotlin.math.sin(t * speedB + phase)
        return (floor + span * u.toFloat()).coerceIn(0f, 1f)
    }
    return AudioSpectrumBands(
        low = band(2.15, 0.41, 0.0, 0.22f, 0.72f),
        mid = band(3.70, 0.93, 1.2, 0.18f, 0.62f),
        high = band(8.20, 1.17, 0.4, 0.12f, 0.55f),
    )
}
