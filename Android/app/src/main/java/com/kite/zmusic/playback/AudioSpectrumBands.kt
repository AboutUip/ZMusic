package com.kite.zmusic.playback

/**
 * 粗分频能量：低 / 中 / 高，范围约 0..1（快攻慢放包络后）。
 */
data class AudioSpectrumBands(
    val low: Float = 0f,
    val mid: Float = 0f,
    val high: Float = 0f,
) {
    fun quantized(steps: Int = 32): AudioSpectrumBands {
        fun q(v: Float): Float = (v.coerceIn(0f, 1f) * steps).toInt() / steps.toFloat()
        return AudioSpectrumBands(q(low), q(mid), q(high))
    }

    companion object {
        val ZERO = AudioSpectrumBands()
    }
}
