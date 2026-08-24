package com.kite.zmusic.plugin

import android.os.Handler
import android.os.Looper
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSException
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 一插件一 Context。所有 JS 调用必须在它线程上。
 * 宿主 API 状态门控：[hostApiAllowed] —— 未 `Running` 则拒绝（只读版本与 `register` 除外）。
 * 可选能力另须清单声明（如 `theme`）。
 */
internal class PluginSession(
    val record: PluginRecord,
    private val extractDir: File,
    private val debug: () -> Boolean,
    private val appVersionName: String,
    private val showNotice: (message: String, coverUrl: String?) -> Unit,
    private val journal: PluginFaultJournal,
    private val onState: (PluginJsState) -> Unit,
    private val onEvalFinished: (PluginJsState) -> Unit,
    private val onSessionEnded: (pluginId: String) -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var worker: Thread? = null
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "zmusic-plugin-${record.id}").apply {
            isDaemon = true
            worker = this
        }
    }
    private val contextRef = AtomicReference<QuickJSContext?>(null)
    @Volatile
    var state: PluginJsState = PluginJsState.Uninitialized
        private set
    @Volatile
    private var ended = false
    private var endNotified = false

    fun hostApiAllowed(): Boolean = state == PluginJsState.Running

    fun start(sentinel: PluginCrashSentinel, gate: Semaphore? = null) {
        executor.execute {
            gate?.acquireUninterruptibly()
            try {
                journal.begin(record.id)
                sentinel.mark(record.id)
                PluginQuickJs.ensure()
                runEntry()
            } catch (t: Throwable) {
                if (state != PluginJsState.Error) {
                    setState(PluginJsState.Error)
                }
                journal.append(record.id, "入口失败: ${t.stackTraceToString().trim()}")
                PluginLog.e(debug(), "插件 ${record.id} 入口失败", t)
                destroyContext()
            } finally {
                sentinel.clear(record.id)
                gate?.release()
                onEvalFinished(state)
            }
        }
    }

    fun stop() {
        ended = true
        worker?.interrupt()
        val done = CountDownLatch(1)
        try {
            executor.execute {
                try {
                    ended = true
                    destroyContext()
                } finally {
                    done.countDown()
                }
            }
            done.await(8, TimeUnit.SECONDS)
        } catch (_: Throwable) {
            // 停机尽力而为
        } finally {
            notifySessionEnded()
            executor.shutdownNow()
        }
    }

    private fun notifySessionEnded() {
        if (endNotified) return
        endNotified = true
        onSessionEnded(record.id)
    }

    private fun runEntry() {
        val sourceFile = File(extractDir, record.entry)
        val source = sourceFile.readText(Charsets.UTF_8)
        val ctx = QuickJSContext.create()
        contextRef.set(ctx)
        injectXuan(ctx)
        installConsole(ctx)
        try {
            ctx.evaluate(source, record.entry)
            if (state == PluginJsState.Uninitialized) {
                PluginLog.w(debug(), "插件 ${record.id} 未注册状态，跳过")
                journal.append(record.id, "未按协议注册运行状态")
                setState(PluginJsState.Error)
                destroyContext()
            } else if (state == PluginJsState.Error) {
                destroyContext()
            }
            // Initializing / Running：保留 Context。Initializing 仍算未就绪。
        } catch (e: QuickJSException) {
            if (state != PluginJsState.Error) {
                journal.append(record.id, "脚本异常: ${e.message ?: e.toString()}")
                PluginLog.e(debug(), "插件 ${record.id} 脚本异常", e)
                setState(PluginJsState.Error)
            } else {
                journal.append(record.id, "插件注册 Error，结束本次运行")
            }
            destroyContext()
        }
    }

    private fun injectXuan(ctx: QuickJSContext) {
        val global = ctx.getGlobalObject()
        val xuan = ctx.createNewJSObject()
        val zmusic = ctx.createNewJSObject()
        zmusic.setProperty("version", appVersionName)
        zmusic.setProperty("versionNumber", PluginEngineVersion.encodeAppVersionName(appVersionName))
        val engine = ctx.createNewJSObject()
        engine.setProperty("version", PluginEngineVersion.DISPLAY)
        engine.setProperty("versionNumber", PluginEngineVersion.number)
        val runtime = ctx.createNewJSObject()
        val states = ctx.createNewJSObject()
        states.setProperty("Initializing", STATE_INITIALIZING)
        states.setProperty("Running", STATE_RUNNING)
        states.setProperty("Error", STATE_ERROR)
        runtime.setProperty("State", states)
        runtime.setProperty("register", JSCallFunction { args -> register(args) })
        val notice = ctx.createNewJSObject()
        notice.setProperty("show", JSCallFunction { args -> noticeShow(args) })
        val theme = ctx.createNewJSObject()
        theme.setProperty("set", JSCallFunction { args -> themeSet(args) })
        theme.setProperty("clear", JSCallFunction { themeClear() })
        theme.setProperty("get", JSCallFunction { themeGet(ctx) })
        xuan.setProperty("zmusic", zmusic)
        xuan.setProperty("engine", engine)
        xuan.setProperty("runtime", runtime)
        xuan.setProperty("notice", notice)
        xuan.setProperty("theme", theme)
        xuan.setProperty("delay", JSCallFunction { args -> delayMs(args) })
        global.setProperty("Xuan", xuan)
        theme.release()
        notice.release()
        states.release()
        runtime.release()
        engine.release()
        zmusic.release()
        xuan.release()
        ctx.evaluate(
            """
            Object.freeze(Xuan.zmusic);
            Object.freeze(Xuan.engine);
            Object.freeze(Xuan.runtime.State);
            Object.freeze(Xuan.notice);
            Object.freeze(Xuan.theme);
            """.trimIndent(),
        )
    }

    private fun installConsole(ctx: QuickJSContext) {
        val global = ctx.getGlobalObject()
        global.setProperty(
            "__zmusicPluginLog",
            JSCallFunction { args ->
                val msg = args.firstOrNull()?.toString().orEmpty()
                journal.append(record.id, msg)
                PluginLog.d(debug(), "[${record.id}] $msg")
                null
            },
        )
        ctx.evaluate(
            """
            var console = {
              log: function() { __zmusicPluginLog(Array.prototype.join.call(arguments, ' ')); },
              info: function() { __zmusicPluginLog(Array.prototype.join.call(arguments, ' ')); },
              warn: function() { __zmusicPluginLog(Array.prototype.join.call(arguments, ' ')); },
              error: function() { __zmusicPluginLog(Array.prototype.join.call(arguments, ' ')); }
            };
            """.trimIndent(),
        )
    }

    private fun register(args: Array<out Any?>): Any {
        if (ended) return java.lang.Boolean.FALSE
        val token = args.firstOrNull() as? String ?: return java.lang.Boolean.FALSE
        when (token) {
            STATE_INITIALIZING -> {
                if (state != PluginJsState.Uninitialized) return java.lang.Boolean.FALSE
                setState(PluginJsState.Initializing)
                return java.lang.Boolean.TRUE
            }
            STATE_RUNNING -> {
                if (state != PluginJsState.Initializing) return java.lang.Boolean.FALSE
                setState(PluginJsState.Running)
                return java.lang.Boolean.TRUE
            }
            STATE_ERROR -> {
                setState(PluginJsState.Error)
                ended = true
                throw QuickJSException("Xuan.runtime.State.Error")
            }
            else -> return java.lang.Boolean.FALSE
        }
    }

    private fun noticeShow(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val message = args.firstOrNull() as? String ?: return java.lang.Boolean.FALSE
        val text = message.trim()
        if (text.isEmpty()) return java.lang.Boolean.FALSE
        val cover = (args.getOrNull(1) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        mainHandler.post { showNotice(text, cover) }
        return java.lang.Boolean.TRUE
    }

    private fun delayMs(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val ms = PluginDelay.parseMs(args.firstOrNull()) ?: return java.lang.Boolean.FALSE
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return java.lang.Boolean.FALSE
        }
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        return java.lang.Boolean.TRUE
    }

    private fun themeCapabilityOk(): Boolean =
        record.hasCapability(PluginCapabilities.THEME)

    private fun themeSet(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed() || !themeCapabilityOk()) return java.lang.Boolean.FALSE
        val partial = args.firstOrNull()
        val ok = PluginTextThemeBridge.set(record.id, partial)
        return if (ok) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
    }

    private fun themeClear(): Any {
        if (ended || !hostApiAllowed() || !themeCapabilityOk()) return java.lang.Boolean.FALSE
        return if (PluginTextThemeBridge.clear(record.id)) {
            java.lang.Boolean.TRUE
        } else {
            java.lang.Boolean.FALSE
        }
    }

    private fun themeGet(ctx: QuickJSContext): Any {
        if (ended || !hostApiAllowed() || !themeCapabilityOk()) {
            return ctx.parseJSON("{}")
        }
        val map = PluginTextThemeBridge.getHexMap()
        val json = PluginJson.stringify(map)
        return ctx.parseJSON(json)
    }

    private fun setState(next: PluginJsState) {
        state = next
        onState(next)
        if (next == PluginJsState.Error) {
            PluginTextThemeBridge.clearIfOwner(record.id)
        }
    }

    private fun destroyContext() {
        val ctx = contextRef.getAndSet(null) ?: return
        try {
            ctx.destroy()
        } catch (_: Throwable) {
            // 已销毁或线程问题
        }
    }

    companion object {
        const val STATE_INITIALIZING = "Initializing"
        const val STATE_RUNNING = "Running"
        const val STATE_ERROR = "Error"
    }
}
