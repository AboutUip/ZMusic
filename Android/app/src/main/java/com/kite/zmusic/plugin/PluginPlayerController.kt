package com.kite.zmusic.plugin

/**
 * 播放控制。实现必须切到主线程再碰 [com.kite.zmusic.playback.PlaybackBridge]。
 */
interface PluginPlayerController {
    fun play(): Boolean
    fun pause(): Boolean
    fun next(): Boolean
    fun prev(): Boolean
    fun seek(ms: Long): Boolean
    fun setLiked(liked: Boolean): Boolean
    fun setVolume(level: Float): Boolean
    fun volume(): Float
    fun setPlaybackRate(rate: Float): Boolean
    fun playbackRate(): Float
    fun mode(): String
    fun setMode(name: String): Boolean
    fun cycleMode(): Boolean
    fun playAt(index: Int): Boolean
    fun clearQueue(): Boolean
    fun openPlayer(): Boolean
    fun sleep(): Map<String, Any?>
    fun sleepStart(minutes: Int, waitForTrackEnd: Boolean): Boolean
    fun sleepCancel(): Boolean
    fun lyric(): Map<String, Any?>
    fun lyricLine(): Map<String, Any?>?

    companion object {
        val Noop: PluginPlayerController = object : PluginPlayerController {
            override fun play(): Boolean = false
            override fun pause(): Boolean = false
            override fun next(): Boolean = false
            override fun prev(): Boolean = false
            override fun seek(ms: Long): Boolean = false
            override fun setLiked(liked: Boolean): Boolean = false
            override fun setVolume(level: Float): Boolean = false
            override fun volume(): Float = 1f
            override fun setPlaybackRate(rate: Float): Boolean = false
            override fun playbackRate(): Float = 1f
            override fun mode(): String = "order"
            override fun setMode(name: String): Boolean = false
            override fun cycleMode(): Boolean = false
            override fun playAt(index: Int): Boolean = false
            override fun clearQueue(): Boolean = false
            override fun openPlayer(): Boolean = false
            override fun sleep(): Map<String, Any?> = mapOf(
                "active" to false,
                "remainingMs" to 0,
                "waitForTrackEnd" to false,
                "pendingStopAfterTrack" to false,
            )
            override fun sleepStart(minutes: Int, waitForTrackEnd: Boolean): Boolean = false
            override fun sleepCancel(): Boolean = false
            override fun lyric(): Map<String, Any?> = mapOf(
                "index" to -1,
                "text" to null,
                "translation" to null,
                "truncated" to false,
                "lines" to emptyList<Any>(),
                "translations" to emptyList<Any>(),
            )
            override fun lyricLine(): Map<String, Any?>? = null
        }
    }
}
