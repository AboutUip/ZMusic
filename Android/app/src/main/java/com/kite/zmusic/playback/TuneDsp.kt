package com.kite.zmusic.playback

import com.kite.zmusic.data.TuneBands
import com.kite.zmusic.data.TunePrefs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 本机播放器级 DSP：多段峰值均衡、低架低音、链路压缩、响度、轻混响。
 * 仅处理交织 float PCM；变速变调交给 ExoPlayer Sonic。
 */
class TuneDsp {

    private var sampleRate = 44_100
    private var channelCount = 2
    private var prefs = TunePrefs.Default
    private var active = false

    private val peak = Array(MAX_CH) { Array(TuneBands.COUNT) { Biquad() } }
    private val shelf = Array(MAX_CH) { Biquad() }
    private var env = 0f
    private var makeup = 1f
    private var thresh = 0.22f
    private var ratio = 1f
    private var attack = 0.01f
    private var release = 0.08f
    private var wet = 0f
    private var combFb = 0.55f

    private var combL = emptyArray<DelayLine>()
    private var combR = emptyArray<DelayLine>()
    private var apL = emptyArray<DelayLine>()
    private var apR = emptyArray<DelayLine>()
    private var combDelayL = IntArray(0)
    private var combDelayR = IntArray(0)
    private var apDelayL = IntArray(0)
    private var apDelayR = IntArray(0)

    fun configure(sampleRateHz: Int, channels: Int) {
        sampleRate = sampleRateHz.coerceIn(8_000, 192_000)
        channelCount = channels.coerceIn(1, MAX_CH)
        allocateReverb()
        applyPrefs(prefs, force = true)
        reset()
    }

    fun setPrefs(next: TunePrefs) {
        applyPrefs(next.sanitized(), force = false)
    }

    fun reset() {
        for (ch in 0 until MAX_CH) {
            for (b in peak[ch]) b.reset()
            shelf[ch].reset()
        }
        env = 0f
        combL.forEach { it.clear() }
        combR.forEach { it.clear() }
        apL.forEach { it.clear() }
        apR.forEach { it.clear() }
    }

    fun isActive(): Boolean = active

    /** [samples] 交织，长度至少 [frames] * channelCount。 */
    fun process(samples: FloatArray, frames: Int) {
        if (!active || frames <= 0) return
        val chN = channelCount
        val useReverb = wet > 0.001f && combL.isNotEmpty()
        var i = 0
        for (f in 0 until frames) {
            var peakAbs = 0f
            for (ch in 0 until chN) {
                var s = samples[i + ch]
                val peaking = peak[ch]
                for (b in peaking.indices) {
                    if (abs(prefs.eq.getOrElse(b) { 0f }) >= 0.05f) {
                        s = peaking[b].process(s)
                    }
                }
                if (prefs.bass >= 0.01f) {
                    s = shelf[ch].process(s)
                }
                samples[i + ch] = s
                val a = abs(s)
                if (a > peakAbs) peakAbs = a
            }

            val coeff = if (peakAbs > env) attack else release
            env += coeff * (peakAbs - env)
            var gain = 1f
            if (ratio > 1.01f && env > thresh && env > 1e-6f) {
                val over = env / thresh
                val compressed = over.pow(1f / ratio)
                gain = (compressed * thresh) / env
            }
            gain *= makeup

            for (ch in 0 until chN) {
                var s = samples[i + ch] * gain
                if (useReverb && ch < 2) {
                    val wetS = if (ch == 0) reverbLeft(s) else reverbRight(s)
                    s = s * (1f - wet) + wetS * wet
                }
                samples[i + ch] = softClip(s)
            }
            i += chN
        }
    }

    private fun applyPrefs(next: TunePrefs, force: Boolean) {
        val rebuildEq = force || next.eq != prefs.eq || next.bass != prefs.bass
        prefs = next
        active = next.dspActive()
        if (!active) {
            makeup = 1f
            ratio = 1f
            wet = 0f
            return
        }
        if (rebuildEq) {
            val fs = sampleRate.toFloat()
            for (ch in 0 until channelCount) {
                for (b in 0 until TuneBands.COUNT) {
                    val hz = TuneBands.HZ[b]
                    val db = next.eq.getOrElse(b) { 0f }
                    peak[ch][b].peaking(fs, min(hz, fs * 0.45f), 1.15f, db)
                }
                val bassDb = next.bass * 12f
                shelf[ch].lowShelf(fs, 85f, 0.72f, bassDb)
            }
        }
        updateDynamics(next)
    }

    private fun updateDynamics(next: TunePrefs) {
        compressToCoeffs(next.compress)
        makeup = 10.0.pow((next.loudness * 8.0) / 20.0).toFloat()
        wet = next.reverb * 0.34f
        combFb = 0.42f + next.reverb * 0.28f
        val fs = sampleRate.toFloat()
        attack = (1.0 - kotlin.math.exp(-2.0 * PI * 80.0 / fs)).toFloat()
        release = (1.0 - kotlin.math.exp(-2.0 * PI * 8.0 / fs)).toFloat()
    }

    private fun compressToCoeffs(amount: Float) {
        if (amount < 0.01f) {
            ratio = 1f
            thresh = 1f
            return
        }
        ratio = 1f + amount * 5f
        thresh = 0.28f - amount * 0.12f
    }

    private fun allocateReverb() {
        val scale = sampleRate / 48_000f
        combDelayL = COMB_MS_L.map { msToDelay(it, scale) }.toIntArray()
        combDelayR = COMB_MS_R.map { msToDelay(it, scale) }.toIntArray()
        apDelayL = AP_MS_L.map { msToDelay(it, scale) }.toIntArray()
        apDelayR = AP_MS_R.map { msToDelay(it, scale) }.toIntArray()
        val maxComb = max(combDelayL.maxOrNull() ?: 1, combDelayR.maxOrNull() ?: 1) + 8
        val maxAp = max(apDelayL.maxOrNull() ?: 1, apDelayR.maxOrNull() ?: 1) + 8
        combL = Array(COMB_MS_L.size) { DelayLine(maxComb) }
        combR = Array(COMB_MS_R.size) { DelayLine(maxComb) }
        apL = Array(AP_MS_L.size) { DelayLine(maxAp) }
        apR = Array(AP_MS_R.size) { DelayLine(maxAp) }
    }

    private fun msToDelay(ms: Float, scale: Float): Int =
        max(2, (ms * 48f * scale).toInt())

    private fun reverbLeft(x: Float): Float {
        var acc = 0f
        for (i in combL.indices) {
            acc += combL[i].comb(x, combDelayL[i], combFb)
        }
        acc /= combL.size.coerceAtLeast(1)
        for (i in apL.indices) {
            acc = apL[i].allpass(acc, apDelayL[i], 0.6f)
        }
        return acc
    }

    private fun reverbRight(x: Float): Float {
        var acc = 0f
        for (i in combR.indices) {
            acc += combR[i].comb(x, combDelayR[i], combFb)
        }
        acc /= combR.size.coerceAtLeast(1)
        for (i in apR.indices) {
            acc = apR[i].allpass(acc, apDelayR[i], 0.6f)
        }
        return acc
    }

    private fun softClip(x: Float): Float {
        val y = tanh(x.toDouble()).toFloat()
        return y.coerceIn(-1f, 1f)
    }

    private class Biquad {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        fun peaking(fs: Float, f: Float, q: Float, gainDb: Float) {
            if (abs(gainDb) < 0.05f) {
                bypass()
                return
            }
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f / fs
            val alpha = sin(w0) / (2.0 * q)
            val cosw = cos(w0)
            val b0n = 1.0 + alpha * a
            val b1n = -2.0 * cosw
            val b2n = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1n = -2.0 * cosw
            val a2n = 1.0 - alpha / a
            setNorm(b0n, b1n, b2n, a0, a1n, a2n)
        }

        fun lowShelf(fs: Float, f: Float, q: Float, gainDb: Float) {
            if (abs(gainDb) < 0.05f) {
                bypass()
                return
            }
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f / fs
            val cosw = cos(w0)
            val sinw = sin(w0)
            val alpha = sinw / (2.0 * q)
            val twoSqrtAAlpha = 2.0 * kotlin.math.sqrt(a) * alpha
            val b0n = a * ((a + 1.0) - (a - 1.0) * cosw + twoSqrtAAlpha)
            val b1n = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosw)
            val b2n = a * ((a + 1.0) - (a - 1.0) * cosw - twoSqrtAAlpha)
            val a0 = (a + 1.0) + (a - 1.0) * cosw + twoSqrtAAlpha
            val a1n = -2.0 * ((a - 1.0) + (a + 1.0) * cosw)
            val a2n = (a + 1.0) + (a - 1.0) * cosw - twoSqrtAAlpha
            setNorm(b0n, b1n, b2n, a0, a1n, a2n)
        }

        private fun bypass() {
            b0 = 1f
            b1 = 0f
            b2 = 0f
            a1 = 0f
            a2 = 0f
        }

        private fun setNorm(
            b0n: Double,
            b1n: Double,
            b2n: Double,
            a0: Double,
            a1n: Double,
            a2n: Double,
        ) {
            val inv = 1.0 / a0
            b0 = (b0n * inv).toFloat()
            b1 = (b1n * inv).toFloat()
            b2 = (b2n * inv).toFloat()
            a1 = (a1n * inv).toFloat()
            a2 = (a2n * inv).toFloat()
        }
    }

    private class DelayLine(size: Int) {
        private val buf = FloatArray(size.coerceAtLeast(4))
        private var w = 0

        fun clear() {
            buf.fill(0f)
            w = 0
        }

        fun comb(x: Float, delay: Int, fb: Float): Float {
            val y = x + fb * read(delay)
            write(y)
            return y
        }

        fun allpass(x: Float, delay: Int, g: Float): Float {
            val bufS = read(delay)
            val y = -g * x + bufS
            write(x + g * y)
            return y
        }

        private fun read(delay: Int): Float {
            val n = buf.size
            val d = delay.coerceIn(1, n - 1)
            val i = w - d
            return buf[if (i >= 0) i else i + n]
        }

        private fun write(v: Float) {
            buf[w] = v
            val n = w + 1
            w = if (n >= buf.size) 0 else n
        }
    }

    companion object {
        private const val MAX_CH = 8
        private val COMB_MS_L = floatArrayOf(29.7f, 37.1f, 43.7f, 53.3f)
        private val COMB_MS_R = floatArrayOf(31.3f, 39.5f, 44.9f, 55.1f)
        private val AP_MS_L = floatArrayOf(5.1f, 12.6f)
        private val AP_MS_R = floatArrayOf(5.8f, 13.3f)
    }
}
