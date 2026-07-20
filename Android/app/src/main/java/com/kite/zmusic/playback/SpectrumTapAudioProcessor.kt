package com.kite.zmusic.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 透传 PCM，实时输出低 / 中 / 高三段能量（0..1）。
 *
 * 设计要点（针对「无响应 / 静音乱闪」）：
 * - 绝对能量 + 噪声门限，禁止相对归一把底噪抬成高亮
 * - 频段用「每 bin 平均幅值」，避免中高频因 bin 多而永远压过 kick
 * - 低频以时域包络 / flux 为主（小 FFT 对 40–100Hz 分辨率不够）
 * - 中高频用谱通量 onset，旋律与镲片跟手
 * - 快攻慢放；onset 几乎瞬时叠加上去
 */
@UnstableApi
class SpectrumTapAudioProcessor(
    private val onBands: (AudioSpectrumBands) -> Unit,
) : BaseAudioProcessor() {

    private var channelCount = 2
    private var sampleRate = 44_100

    private val ring = FloatArray(FFT_SIZE)
    private var writePos = 0
    private var samplesQueued = 0
    private var samplesSinceHop = 0

    private var envLow = 0f
    private var envMid = 0f
    private var envHigh = 0f

    private var prevTdBass = 0f
    private var prevMid = 0f
    private var prevHigh = 0f

    /** 简单一阶低通状态：时域「低音」跟踪。 */
    private var lpf = 0f

    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining > 0) {
            analyze(inputBuffer.asReadOnlyBuffer())
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        samplesSinceHop = 0
    }

    override fun onReset() {
        writePos = 0
        samplesQueued = 0
        samplesSinceHop = 0
        envLow = 0f
        envMid = 0f
        envHigh = 0f
        prevTdBass = 0f
        prevMid = 0f
        prevHigh = 0f
        lpf = 0f
        ring.fill(0f)
        onBands(AudioSpectrumBands.ZERO)
    }

    private fun analyze(buf: ByteBuffer) {
        val ch = channelCount
        // 一阶 LPF 系数：~120Hz @ 44.1k
        val lpfA = (1.0 - Math.exp(-2.0 * Math.PI * 120.0 / sampleRate)).toFloat()
        while (buf.remaining() >= 2 * ch) {
            var mono = 0
            for (c in 0 until ch) {
                mono += buf.short.toInt()
            }
            mono /= ch
            val s = mono / 32768f
            ring[writePos] = s
            writePos = (writePos + 1) % FFT_SIZE
            lpf += lpfA * (s - lpf)
            if (samplesQueued < FFT_SIZE) samplesQueued++
            samplesSinceHop++
            if (samplesQueued >= FFT_SIZE && samplesSinceHop >= HOP) {
                samplesSinceHop = 0
                emitBands()
            }
        }
    }

    private fun emitBands() {
        val start = writePos
        for (i in 0 until FFT_SIZE) {
            val s = ring[(start + i) % FFT_SIZE]
            val w = 0.5f * (1f - cos(2.0 * Math.PI * i / (FFT_SIZE - 1)).toFloat())
            re[i] = s * w
            im[i] = 0f
        }
        fft(re, im)

        val hzPerBin = sampleRate.toFloat() / FFT_SIZE
        var lowSum = 0f
        var lowN = 0
        var midSum = 0f
        var midN = 0
        var highSum = 0f
        var highN = 0
        val nyquistBins = FFT_SIZE / 2
        for (i in 1 until nyquistBins) {
            val hz = i * hzPerBin
            val mag = sqrt(re[i] * re[i] + im[i] * im[i])
            when {
                hz < 250f -> {
                    lowSum += mag
                    lowN++
                }
                hz < 2_400f -> {
                    midSum += mag
                    midN++
                }
                hz < 12_000f -> {
                    highSum += mag
                    highN++
                }
            }
        }
        val fftLow = if (lowN > 0) lowSum / lowN else 0f
        val fftMid = if (midN > 0) midSum / midN else 0f
        val fftHigh = if (highN > 0) highSum / highN else 0f

        // 时域：最近 hop 窗 RMS + LPF 能量（鼓点主力）
        var tdE = 0f
        var bassE = 0f
        val tdLen = HOP.coerceAtMost(FFT_SIZE)
        // 用当前 LPF 的瞬时近似：对 ring 末段做短窗
        for (i in 0 until tdLen) {
            val newest = ring[(writePos - tdLen + i + FFT_SIZE) % FFT_SIZE]
            tdE += newest * newest
        }
        val tdRms = sqrt(tdE / tdLen.coerceAtLeast(1))
        bassE = absLpfEnergy()

        // 绝对标定（经验增益）；不做「相对峰值归一」
        val rawBass = squash(bassE * 14f + tdRms * 3.5f + fftLow * 6f)
        val rawMid = squash(fftMid * 18f)
        val rawHigh = squash(fftHigh * 22f)

        // Flux / onset：陡升才加分，平稳段落不会乱闪
        val bassOnset = (rawBass - prevTdBass).coerceAtLeast(0f)
        val midOnset = (rawMid - prevMid).coerceAtLeast(0f)
        val highOnset = (rawHigh - prevHigh).coerceAtLeast(0f)
        prevTdBass = rawBass
        prevMid = rawMid
        prevHigh = rawHigh

        var outLow = (rawBass + bassOnset * 1.8f).coerceIn(0f, 1f)
        var outMid = (rawMid + midOnset * 1.35f).coerceIn(0f, 1f)
        var outHigh = (rawHigh + highOnset * 1.55f).coerceIn(0f, 1f)

        // 噪声门：安静时强制归零，杜绝「没声音随机高亮」
        if (outLow < GATE) outLow = 0f
        if (outMid < GATE) outMid = 0f
        if (outHigh < GATE) outHigh = 0f
        // 整体太静：全灭
        if (outLow + outMid + outHigh < GATE * 1.6f) {
            outLow = 0f
            outMid = 0f
            outHigh = 0f
        }

        envLow = follow(envLow, outLow, ATTACK_LOW, RELEASE_LOW)
        envMid = follow(envMid, outMid, ATTACK_MID, RELEASE_MID)
        envHigh = follow(envHigh, outHigh, ATTACK_HIGH, RELEASE_HIGH)

        // onset 帧允许瞬间顶满包络，避免被 release 拖后腿
        if (bassOnset > 0.08f) envLow = max(envLow, outLow)
        if (midOnset > 0.08f) envMid = max(envMid, outMid)
        if (highOnset > 0.08f) envHigh = max(envHigh, outHigh)

        onBands(
            AudioSpectrumBands(
                low = envLow,
                mid = envMid,
                high = envHigh,
            ),
        )
    }

    /** 环形缓冲末段的低通能量近似。 */
    private fun absLpfEnergy(): Float {
        // 用状态 lpf 的 |y|；再扫一小段确认瞬态
        var e = lpf * lpf
        val n = (HOP / 2).coerceAtLeast(8)
        var acc = 0f
        for (i in 0 until n) {
            val s = ring[(writePos - n + i + FFT_SIZE) % FFT_SIZE]
            acc += s * s
        }
        val rms = sqrt(acc / n)
        return max(sqrt(e), rms * 0.65f)
    }

    /** 平滑压缩到 0..1，保留瞬态尖峰。 */
    private fun squash(v: Float): Float {
        val x = v.coerceAtLeast(0f)
        // 分段：小信号压噪，中段线性，大信号软顶
        return when {
            x < 0.02f -> 0f
            x < 0.35f -> (x - 0.02f) / 0.33f * 0.55f
            x < 1.2f -> 0.55f + (x - 0.35f) / 0.85f * 0.35f
            else -> (0.90f + (1f - 1f / (1f + (x - 1.2f))) * 0.10f).coerceAtMost(1f)
        }
    }

    private fun follow(prev: Float, next: Float, attack: Float, release: Float): Float {
        val a = if (next > prev) attack else release
        return prev * (1f - a) + next * a
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = (-2.0 * Math.PI / len).toFloat()
            val wlenRe = cos(ang.toDouble()).toFloat()
            val wlenIm = sin(ang.toDouble()).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nWRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        /** 1024 @ 44.1k ≈ 43Hz/bin，低频可用。 */
        private const val FFT_SIZE = 1024
        /** ≈ 5.8ms hop，鼓点刷新足够。 */
        private const val HOP = 256
        private const val GATE = 0.06f
        private const val ATTACK_LOW = 0.92f
        private const val RELEASE_LOW = 0.22f
        private const val ATTACK_MID = 0.78f
        private const val RELEASE_MID = 0.18f
        private const val ATTACK_HIGH = 0.88f
        private const val RELEASE_HIGH = 0.14f
    }
}
