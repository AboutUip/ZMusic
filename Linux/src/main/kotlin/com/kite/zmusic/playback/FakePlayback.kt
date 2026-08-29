package com.kite.zmusic.playback

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class FakePlaybackEngine : PlaybackEngine {
    private val playing = AtomicBoolean(false)
    private val position = AtomicLong(0L)
    private val duration = AtomicLong(180_000L)
    private var onEnded: (() -> Unit)? = null
    var lastUrl: String? = null
        private set
    var volume: Float = 1f
        private set

    override fun play(url: String, startMs: Long) {
        lastUrl = url
        position.set(startMs)
        if (duration.get() <= 0L) duration.set(180_000L)
        playing.set(true)
    }

    override fun pause() {
        playing.set(false)
    }

    override fun resume() {
        if (lastUrl != null) playing.set(true)
    }

    override fun seek(ms: Long) {
        position.set(ms.coerceIn(0L, duration.get().coerceAtLeast(0L)))
    }

    override fun stop() {
        playing.set(false)
        position.set(0L)
        lastUrl = null
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    override fun positionMs(): Long = position.get()

    override fun durationMs(): Long = duration.get()

    override fun isPlaying(): Boolean = playing.get()

    override fun setOnEnded(block: () -> Unit) {
        onEnded = block
    }

    fun setDuration(ms: Long) {
        duration.set(ms)
    }

    fun tick(ms: Long) {
        if (!playing.get()) return
        val next = position.addAndGet(ms)
        if (next >= duration.get()) {
            playing.set(false)
            position.set(duration.get())
            onEnded?.invoke()
        }
    }

    fun emitEnded() {
        playing.set(false)
        onEnded?.invoke()
    }
}

class NoopMprisExporter : MprisExporter {
    var lastState: PlaybackUiState? = null
        private set
    var playPauseCount: Int = 0
        private set
    var nextCount: Int = 0
        private set
    var prevCount: Int = 0
        private set
    private var onPlayPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrev: (() -> Unit)? = null

    override fun publish(state: PlaybackUiState) {
        lastState = state
    }

    override fun bind(onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit) {
        this.onPlayPause = onPlayPause
        this.onNext = onNext
        this.onPrev = onPrev
    }

    override fun close() = Unit

    fun simulatePlayPause() {
        playPauseCount++
        onPlayPause?.invoke()
    }

    fun simulateNext() {
        nextCount++
        onNext?.invoke()
    }

    fun simulatePrev() {
        prevCount++
        onPrev?.invoke()
    }
}
