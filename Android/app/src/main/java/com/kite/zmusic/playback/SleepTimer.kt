package com.kite.zmusic.playback

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SleepTimerUi(
    val active: Boolean = false,
    val remainingMs: Long = 0L,
    val waitForTrackEnd: Boolean = false,
    val pendingStopAfterTrack: Boolean = false,
) {
    val running: Boolean get() = active || pendingStopAfterTrack
}

/**
 * 进程内定时停止。倒计时挂在 [PlaybackBridge] 上，离开播放器页仍有效；
 * 不写入 [PlaybackStateStore]。
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val onStopNow: () -> Unit,
    private val onWaitDeadline: () -> Unit,
) {
    private val _ui = MutableStateFlow(SleepTimerUi())
    val ui: StateFlow<SleepTimerUi> = _ui.asStateFlow()

    private var tickJob: Job? = null
    private var deadlineElapsed: Long = 0L

    fun start(minutes: Int, waitForTrackEnd: Boolean) {
        val mins = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        tickJob?.cancel()
        deadlineElapsed = SystemClock.elapsedRealtime() + mins * 60_000L
        _ui.value = SleepTimerUi(
            active = true,
            remainingMs = mins * 60_000L,
            waitForTrackEnd = waitForTrackEnd,
            pendingStopAfterTrack = false,
        )
        startTicker()
    }

    fun cancel() {
        tickJob?.cancel()
        tickJob = null
        deadlineElapsed = 0L
        val wait = _ui.value.waitForTrackEnd
        _ui.value = SleepTimerUi(waitForTrackEnd = wait)
    }

    fun setWaitForTrackEnd(wait: Boolean) {
        val cur = _ui.value
        if (cur.pendingStopAfterTrack && !wait) {
            fireStop()
            return
        }
        _ui.update { it.copy(waitForTrackEnd = wait) }
    }

    /** @return true 表示已按定时停止，调用方勿再切歌 / 循环。 */
    fun onTrackEnded(): Boolean {
        if (!_ui.value.pendingStopAfterTrack) return false
        fireStop()
        return true
    }

    /** 等待本首结束期间用户离开当前曲。 */
    fun onLeavingTrack(): Boolean {
        if (!_ui.value.pendingStopAfterTrack) return false
        fireStop()
        return true
    }

    private fun fireStop() {
        tickJob?.cancel()
        tickJob = null
        deadlineElapsed = 0L
        val wait = _ui.value.waitForTrackEnd
        _ui.value = SleepTimerUi(waitForTrackEnd = wait)
        onStopNow()
    }

    private fun startTicker() {
        tickJob = scope.launch {
            while (true) {
                val left = deadlineElapsed - SystemClock.elapsedRealtime()
                if (left <= 0L) {
                    val wait = _ui.value.waitForTrackEnd
                    if (wait) {
                        _ui.value = SleepTimerUi(
                            active = true,
                            remainingMs = 0L,
                            waitForTrackEnd = true,
                            pendingStopAfterTrack = true,
                        )
                        onWaitDeadline()
                    } else {
                        fireStop()
                    }
                    return@launch
                }
                _ui.update { it.copy(remainingMs = left) }
                delay(left.coerceAtMost(TICK_MS))
            }
        }
    }

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 60
        private const val TICK_MS = 250L
        val PRESETS = intArrayOf(15, 30, 45, 60)
    }
}
