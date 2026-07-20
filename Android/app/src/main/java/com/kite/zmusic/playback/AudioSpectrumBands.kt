package com.kite.zmusic.playback

/**
 * 粗分频能量：低 / 中 / 高，范围约 0..1（快攻慢放包络后）。
 */
data class AudioSpectrumBands(
    val low: Float = 0f,
    val mid: Float = 0f,
    val high: Float = 0f,
) {
    companion object {
        val ZERO = AudioSpectrumBands()
    }
}
