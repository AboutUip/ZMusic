package com.kite.zmusic.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.kite.zmusic.data.TunePrefs
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/**
 * 把 [TuneDsp] 挂进 Media3 用户处理器链（SpectrumTap 之前）。
 * 关闭或全平时透传，避免无谓重配音频通路。
 */
@UnstableApi
class TuneAudioProcessor : BaseAudioProcessor() {

    private val prefsRef = AtomicReference(TunePrefs.Default)
    private val dsp = TuneDsp()
    private var channelCount = 2
    private var work = FloatArray(0)

    fun setPrefs(prefs: TunePrefs) {
        prefsRef.set(prefs.sanitized())
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1).coerceAtMost(8)
        dsp.configure(inputAudioFormat.sampleRate.coerceAtLeast(8_000), channelCount)
        dsp.setPrefs(prefsRef.get())
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        val frameBytes = 2 * channelCount
        if (remaining < frameBytes) return
        val frames = remaining / frameBytes
        dsp.setPrefs(prefsRef.get())
        val out = replaceOutputBuffer(frames * frameBytes)
        if (!dsp.isActive()) {
            val limit = inputBuffer.limit()
            inputBuffer.limit(inputBuffer.position() + frames * frameBytes)
            out.put(inputBuffer)
            inputBuffer.limit(limit)
            out.flip()
            return
        }
        val need = frames * channelCount
        if (work.size < need) work = FloatArray(need)
        var w = 0
        repeat(frames) {
            for (ch in 0 until channelCount) {
                work[w++] = inputBuffer.short / 32768f
            }
        }
        dsp.process(work, frames)
        w = 0
        repeat(frames) {
            for (ch in 0 until channelCount) {
                val s = (work[w++] * 32767f).toInt().coerceIn(-32768, 32767)
                out.putShort(s.toShort())
            }
        }
        out.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        dsp.reset()
    }

    override fun onReset() {
        dsp.reset()
    }
}
