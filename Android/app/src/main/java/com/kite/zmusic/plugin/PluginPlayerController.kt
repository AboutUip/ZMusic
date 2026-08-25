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

    companion object {
        val Noop: PluginPlayerController = object : PluginPlayerController {
            override fun play(): Boolean = false
            override fun pause(): Boolean = false
            override fun next(): Boolean = false
            override fun prev(): Boolean = false
            override fun seek(ms: Long): Boolean = false
            override fun setLiked(liked: Boolean): Boolean = false
        }
    }
}
