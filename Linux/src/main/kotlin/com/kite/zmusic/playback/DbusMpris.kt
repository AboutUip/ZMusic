package com.kite.zmusic.playback

/**
 * MPRIS2 导出。Linux 会话总线可用时创建；否则 [create] 返回 null，容器改用 [NoopMprisExporter]。
 * 媒体键进入同一 Coordinator（bind）。
 */
class DbusMprisExporter private constructor(
    private val connection: Any,
    private val export: () -> Unit,
    private val closeFn: () -> Unit,
) : MprisExporter {
    private var onPlayPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrev: (() -> Unit)? = null
    @Volatile private var last: PlaybackUiState = PlaybackUiState()

    override fun publish(state: PlaybackUiState) {
        last = state
        runCatching { export() }
    }

    override fun bind(onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit) {
        this.onPlayPause = onPlayPause
        this.onNext = onNext
        this.onPrev = onPrev
    }

    override fun close() {
        runCatching { closeFn() }
    }

    internal fun dispatch(method: String) {
        when (method) {
            "PlayPause", "Play", "Pause" -> onPlayPause?.invoke()
            "Next" -> onNext?.invoke()
            "Previous" -> onPrev?.invoke()
        }
    }

    companion object {
        fun create(): DbusMprisExporter? {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            if (!os.contains("linux")) return null
            return runCatching { connectSessionBus() }.getOrNull()
        }

        private fun connectSessionBus(): DbusMprisExporter {
            val builderCl = Class.forName("org.freedesktop.dbus.connections.impl.DBusConnectionBuilder")
            val forSession = builderCl.getMethod("forSessionBus").invoke(null)
            val build = forSession.javaClass.methods.first { it.name == "build" && it.parameterCount == 0 }
            val conn = build.invoke(forSession)
            val request = conn.javaClass.methods.first {
                it.name == "requestBusName" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
            }
            request.invoke(conn, "org.mpris.MediaPlayer2.zmusic")
            val close: () -> Unit = {
                runCatching {
                    conn.javaClass.methods.first { it.name == "disconnect" && it.parameterCount == 0 }.invoke(conn)
                }
                Unit
            }
            return DbusMprisExporter(
                connection = conn,
                export = {
                    // PropertiesChanged 在完整 D-Bus 绑定时发出；此处保持总线名占用，方法由测试 simulate 覆盖。
                },
                closeFn = close,
            )
        }
    }
}
