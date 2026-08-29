package com.kite.zmusic.playback

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import java.io.File
import java.util.Locale

/**
 * libmpv 进程内绑定。库不在时 [create] 返回 null，由容器改用 [FakePlaybackEngine]。
 *
 * libmpv 要求进程 `LC_NUMERIC=C`，否则会打印 Non-C locale 并可能让
 * time-pos / duration / seek 停在 0（黑胶转、进度和歌词不动、没声音）。
 */
class MpvPlaybackEngine private constructor(
    private val lib: MpvLib,
    private val handle: Pointer,
) : PlaybackEngine {
    private val lock = Any()
    private var onEnded: (() -> Unit)? = null

    init {
        synchronized(lock) {
            lib.mpv_observe_property(handle, 0, "eof-reached", MPV_FORMAT_FLAG)
        }
    }

    override fun play(url: String, startMs: Long) {
        synchronized(lock) {
            MpvNumericLocale.apply()
            lib.mpv_set_property_string(handle, "http-header-fields", NETEASE_REFERER_HEADER)
            lib.mpv_set_property_string(handle, "user-agent", USER_AGENT)
            val start = if (startMs > 0L) {
                String.format(Locale.US, "start=%.3f", startMs / 1000.0)
            } else {
                null
            }
            if (start != null) {
                command("loadfile", url, "replace", start)
            } else {
                command("loadfile", url, "replace")
            }
            lib.mpv_set_property_string(handle, "pause", "no")
        }
    }

    override fun pause() {
        synchronized(lock) {
            MpvNumericLocale.apply()
            lib.mpv_set_property_string(handle, "pause", "yes")
        }
    }

    override fun resume() {
        synchronized(lock) {
            MpvNumericLocale.apply()
            lib.mpv_set_property_string(handle, "pause", "no")
        }
    }

    override fun seek(ms: Long) {
        synchronized(lock) {
            MpvNumericLocale.apply()
            val sec = String.format(Locale.US, "%.3f", ms.coerceAtLeast(0L) / 1000.0)
            lib.mpv_set_property_string(handle, "time-pos", sec)
        }
    }

    override fun stop() {
        synchronized(lock) {
            MpvNumericLocale.apply()
            command("stop")
        }
    }

    override fun setVolume(volume: Float) {
        synchronized(lock) {
            MpvNumericLocale.apply()
            val pct = (volume.coerceIn(0f, 1f) * 100f).toInt().toString()
            lib.mpv_set_property_string(handle, "volume", pct)
        }
    }

    override fun positionMs(): Long = synchronized(lock) {
        MpvNumericLocale.apply()
        (doubleProp("time-pos") * 1000.0).toLong()
    }

    override fun durationMs(): Long = synchronized(lock) {
        MpvNumericLocale.apply()
        (doubleProp("duration") * 1000.0).toLong()
    }

    override fun isPlaying(): Boolean = synchronized(lock) {
        MpvNumericLocale.apply()
        if (flagProp("pause") || flagProp("eof-reached") || flagProp("idle-active")) {
            return@synchronized false
        }
        doubleProp("duration") > 0.0 || doubleProp("time-pos") > 0.0
    }

    override fun setOnEnded(block: () -> Unit) {
        synchronized(lock) { onEnded = block }
    }

    override fun pump() {
        val ended = ArrayList<() -> Unit>(1)
        synchronized(lock) {
            MpvNumericLocale.apply()
            while (true) {
                val event = lib.mpv_wait_event(handle, 0.0) ?: break
                when (event.id) {
                    MPV_EVENT_NONE -> break
                    MPV_EVENT_END_FILE -> {
                        val reason = event.data?.getInt(0) ?: -1
                        if (reason == MPV_END_FILE_REASON_EOF) {
                            onEnded?.let { ended.add(it) }
                        }
                    }
                }
            }
        }
        ended.forEach { it.invoke() }
    }

    fun destroy() {
        synchronized(lock) {
            lib.mpv_terminate_destroy(handle)
        }
    }

    private fun command(vararg args: String) {
        val rc = lib.mpv_command(handle, StringArray(arrayOf(*args)))
        if (rc != 0) {
            val msg = runCatching { lib.mpv_error_string(rc) }.getOrNull() ?: rc.toString()
            System.err.println("zmusic: mpv ${args.firstOrNull()}: $msg")
        }
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

    companion object {
        private const val MPV_FORMAT_FLAG = 3
        private const val MPV_FORMAT_DOUBLE = 5
        private const val MPV_EVENT_NONE = 0
        private const val MPV_EVENT_END_FILE = 7
        private const val MPV_END_FILE_REASON_EOF = 0
        private const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val NETEASE_REFERER_HEADER = "Referer: https://music.163.com/"

        fun create(): MpvPlaybackEngine? = runCatching {
            MpvNumericLocale.apply()
            val lib = loadLib()
            MpvNumericLocale.apply()
            val handle = lib.mpv_create() ?: error("mpv_create returned null")
            val options = listOf(
                "config" to "no",
                "terminal" to "no",
                "msg-level" to "all=error",
                "vid" to "no",
                "vo" to "null",
                "audio-display" to "no",
                "force-window" to "no",
                "osc" to "no",
                "osd-level" to "0",
                "ytdl" to "no",
                "idle" to "yes",
                "audio-client-name" to "ZMusic",
                "user-agent" to USER_AGENT,
                "http-header-fields" to NETEASE_REFERER_HEADER,
            )
            for ((name, value) in options) {
                val rc = lib.mpv_set_option_string(handle, name, value)
                if (rc != 0) {
                    val msg = runCatching { lib.mpv_error_string(rc) }.getOrNull() ?: rc.toString()
                    System.err.println("zmusic: mpv option $name: $msg")
                }
            }
            MpvNumericLocale.apply()
            val rc = lib.mpv_initialize(handle)
            if (rc != 0) {
                val msg = runCatching { lib.mpv_error_string(rc) }.getOrNull() ?: rc.toString()
                lib.mpv_terminate_destroy(handle)
                error("mpv_initialize failed: $msg")
            }
            MpvNumericLocale.apply()
            MpvPlaybackEngine(lib, handle)
        }.onFailure { err ->
            System.err.println("zmusic: libmpv unavailable: ${err.message}")
        }.getOrNull()

        private fun loadLib(): MpvLib {
            val tried = LinkedHashSet<String>()
            tried.add("mpv")
            val libPath = System.getProperty("java.library.path").orEmpty()
            for (dir in libPath.split(File.pathSeparator)) {
                if (dir.isBlank()) continue
                for (name in listOf("libmpv.so", "libmpv.so.2", "libmpv.so.1")) {
                    val f = File(dir, name)
                    if (f.isFile) tried.add(f.absolutePath)
                }
            }
            var last: Throwable? = null
            for (name in tried) {
                try {
                    return Native.load(name, MpvLib::class.java)
                } catch (t: Throwable) {
                    last = t
                }
            }
            throw last ?: UnsatisfiedLinkError("libmpv not found")
        }
    }
}

/** glibc/musl `LC_NUMERIC`; Java/AWT 的 `setlocale(LC_ALL,"")` 会把它改回非 C。 */
internal object MpvNumericLocale {
    private const val LC_NUMERIC = 1

    internal interface LibC : Library {
        fun setlocale(category: Int, locale: String?): String?
    }

    @Volatile
    private var libc: LibC? = null

    fun apply() {
        val lib = libc ?: runCatching {
            Native.load("c", LibC::class.java)
        }.getOrNull()?.also { libc = it } ?: return
        runCatching { lib.setlocale(LC_NUMERIC, "C") }
    }
}

internal interface MpvLib : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_command(ctx: Pointer, args: StringArray): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, value: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, value: String): Int
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_observe_property(ctx: Pointer, userdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): MpvEvent?
    fun mpv_error_string(error: Int): String
    fun mpv_terminate_destroy(ctx: Pointer)
}

internal class MpvEvent : com.sun.jna.Structure() {
    @JvmField var id: Int = 0
    @JvmField var error: Int = 0
    @JvmField var reply: Long = 0
    @JvmField var data: Pointer? = null
    override fun getFieldOrder() = listOf("id", "error", "reply", "data")
}
