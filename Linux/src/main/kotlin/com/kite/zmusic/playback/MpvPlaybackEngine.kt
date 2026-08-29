package com.kite.zmusic.playback

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * libmpv 进程内绑定。库不在时 [create] 返回 null，由容器改用 [FakePlaybackEngine]。
 */
class MpvPlaybackEngine private constructor(
    private val lib: MpvLib,
    private val handle: Pointer,
) : PlaybackEngine {
    private var onEnded: (() -> Unit)? = null

    init {
        lib.mpv_observe_property(handle, 0, "eof-reached", MPV_FORMAT_FLAG)
    }

    override fun play(url: String, startMs: Long) {
        command("loadfile", url, "replace")
        if (startMs > 0L) {
            command("seek", (startMs / 1000.0).toString(), "absolute")
        }
        command("set", "pause", "no")
    }

    override fun pause() {
        command("set", "pause", "yes")
    }

    override fun resume() {
        command("set", "pause", "no")
    }

    override fun seek(ms: Long) {
        command("seek", (ms / 1000.0).toString(), "absolute")
    }

    override fun stop() {
        command("stop")
    }

    override fun setVolume(volume: Float) {
        command("set", "volume", (volume * 100f).toInt().toString())
    }

    override fun positionMs(): Long = (doubleProp("time-pos") * 1000.0).toLong()

    override fun durationMs(): Long = (doubleProp("duration") * 1000.0).toLong()

    override fun isPlaying(): Boolean {
        val paused = flagProp("pause")
        return !paused && doubleProp("duration") > 0
    }

    override fun setOnEnded(block: () -> Unit) {
        onEnded = block
    }

    override fun pump() = pollEvents()

    fun pollEvents() {
        val event = lib.mpv_wait_event(handle, 0.0) ?: return
        if (event.id == MPV_EVENT_END_FILE) {
            onEnded?.invoke()
        }
    }

    private fun command(vararg args: String) {
        val withNull = args.toList() + null
        lib.mpv_command(handle, withNull.toTypedArray())
    }

    private fun doubleProp(name: String): Double {
        val buf = Memory(8)
        val err = lib.mpv_get_property(handle, name, MPV_FORMAT_DOUBLE, buf)
        if (err != 0) return 0.0
        return buf.getDouble(0)
    }

    private fun flagProp(name: String): Boolean {
        val buf = Memory(4)
        val err = lib.mpv_get_property(handle, name, MPV_FORMAT_FLAG, buf)
        if (err != 0) return false
        return buf.getInt(0) != 0
    }

    fun destroy() {
        lib.mpv_terminate_destroy(handle)
    }

    companion object {
        private const val MPV_FORMAT_FLAG = 3
        private const val MPV_FORMAT_DOUBLE = 5
        private const val MPV_EVENT_END_FILE = 7

        fun create(): MpvPlaybackEngine? = runCatching {
            val lib = Native.load("mpv", MpvLib::class.java)
            val handle = lib.mpv_create() ?: return null
            if (lib.mpv_initialize(handle) != 0) {
                lib.mpv_terminate_destroy(handle)
                return null
            }
            lib.mpv_set_option_string(handle, "vid", "no")
            lib.mpv_set_option_string(handle, "audio-display", "no")
            MpvPlaybackEngine(lib, handle)
        }.getOrNull()
    }
}

internal interface MpvLib : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, value: String): Int
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_observe_property(ctx: Pointer, userdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): MpvEvent?
    fun mpv_terminate_destroy(ctx: Pointer)
}

internal class MpvEvent : com.sun.jna.Structure() {
    @JvmField var id: Int = 0
    @JvmField var error: Int = 0
    @JvmField var reply: Long = 0
    @JvmField var data: Pointer? = null
    override fun getFieldOrder() = listOf("id", "error", "reply", "data")
}
