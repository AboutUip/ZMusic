package com.kite.zmusic.plugin

import android.os.Handler
import android.os.Looper
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSException
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 一插件一 Context。所有 JS 调用必须在它线程上。
 * 宿主 API 状态门控：[hostApiAllowed] —— 未 `Running` 则拒绝（只读版本与 `register` 除外）。
 */
internal class PluginSession(
    val record: PluginRecord,
    private val extractDir: File,
    private val debug: () -> Boolean,
    private val appVersionName: String,
    private val showNotice: (message: String, coverUrl: String?) -> Unit,
    private val journal: PluginFaultJournal,
    private val hooks: PluginHookRegistry,
    private val hostFacts: () -> PluginHostFacts,
    private val playback: () -> PluginPlaybackSnapshot,
    private val player: PluginPlayerController,
    private val http: PluginHttpClient?,
    private val device: PluginDeviceHost,
    private val store: PluginKvStore,
    private val ui: PluginUiBridge,
    private val faultBusy: () -> Boolean,
    private val broadcastHook: (name: String, args: List<Any?>) -> Boolean,
    private val timerScheduler: ScheduledExecutorService,
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
    private var sentinel: PluginCrashSentinel? = null

    private val hookFns = HashMap<String, LinkedHashMap<String, JSFunction>>()
    private val pageFns = HashMap<String, JSFunction>()
    private val slotFns = HashMap<String, JSFunction>()
    private val actionFns = HashMap<String, JSFunction>()
    private val requireCache = LinkedHashMap<String, Any?>()
    private val timersLock = Any()
    private val namedTimers = HashMap<String, TimerSlot>()
    private val simpleTimers = ArrayList<TimerSlot>()

    fun hostApiAllowed(): Boolean = state == PluginJsState.Running

    fun start(sentinel: PluginCrashSentinel, gate: Semaphore? = null) {
        this.sentinel = sentinel
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
        http?.cancel(record.id)
        device.cancel(record.id)
        synchronized(timersLock) {
            namedTimers.values.forEach { it.future?.cancel(false) }
            simpleTimers.forEach { it.future?.cancel(false) }
        }
        worker?.interrupt()
        val done = CountDownLatch(1)
        try {
            executor.execute {
                try {
                    ended = true
                    cancelAllTimers()
                    ui.dropPlugin(record.id)
                    dropHooksLocked()
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

    /**
     * 在插件线程上调用已登记的监听器。[wait] 仅供宿主离线投递，JS 的 `run` 不得等待其它插件。
     */
    fun deliverHook(event: String, listenerId: String, args: List<Any?>, wait: Boolean) {
        if (ended || !hostApiAllowed()) return
        val task = Runnable {
            if (ended || !hostApiAllowed()) return@Runnable
            withJsGuard("hook") { ctx ->
                val fn = hookFns[event]?.get(listenerId) ?: return@withJsGuard
                invokeFn(ctx, fn, args)
            }
        }
        runOnWorker(task, wait)
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
        val hook = ctx.createNewJSObject()
        hook.setProperty("add", JSCallFunction { args -> hookAdd(args) })
        hook.setProperty("remove", JSCallFunction { args -> hookRemove(args) })
        hook.setProperty("run", JSCallFunction { args -> hookRun(args) })
        val timer = ctx.createNewJSObject()
        timer.setProperty("simple", JSCallFunction { args -> timerSimple(args) })
        timer.setProperty("create", JSCallFunction { args -> timerCreate(args) })
        timer.setProperty("remove", JSCallFunction { args -> timerRemove(args) })
        timer.setProperty("exists", JSCallFunction { args -> timerExists(args) })
        val pack = ctx.createNewJSObject()
        pack.setProperty("exists", JSCallFunction { args -> packExists(args) })
        pack.setProperty("text", JSCallFunction { args -> packText(args) })
        pack.setProperty("bytes", JSCallFunction { args -> packBytes(ctx, args) })
        val playerObj = ctx.createNewJSObject()
        playerObj.setProperty("get", JSCallFunction { playerGet(ctx) })
        playerObj.setProperty("play", JSCallFunction { playerPlay() })
        playerObj.setProperty("pause", JSCallFunction { playerPause() })
        playerObj.setProperty("next", JSCallFunction { playerNext() })
        playerObj.setProperty("prev", JSCallFunction { playerPrev() })
        playerObj.setProperty("seek", JSCallFunction { args -> playerSeek(args) })
        playerObj.setProperty("liked", JSCallFunction { playerLiked() })
        playerObj.setProperty("setLiked", JSCallFunction { args -> playerSetLiked(args) })
        val httpObj = ctx.createNewJSObject()
        httpObj.setProperty("request", JSCallFunction { args -> httpRequest(args) })
        val storeObj = ctx.createNewJSObject()
        storeObj.setProperty("get", JSCallFunction { args -> storeGet(ctx, args) })
        storeObj.setProperty("set", JSCallFunction { args -> storeSet(args) })
        storeObj.setProperty("remove", JSCallFunction { args -> storeRemove(args) })
        storeObj.setProperty("keys", JSCallFunction { storeKeys(ctx) })
        storeObj.setProperty("clear", JSCallFunction { storeClear() })
        val uiObj = ctx.createNewJSObject()
        uiObj.setProperty("alert", JSCallFunction { args -> uiAlert(args) })
        uiObj.setProperty("sheet", JSCallFunction { args -> uiSheet(args) })
        val pageObj = ctx.createNewJSObject()
        pageObj.setProperty("define", JSCallFunction { args -> pageDefine(args) })
        pageObj.setProperty("set", JSCallFunction { args -> pageSet(args) })
        pageObj.setProperty("get", JSCallFunction { args -> pageGet(ctx, args) })
        pageObj.setProperty("patch", JSCallFunction { args -> pagePatch(args) })
        pageObj.setProperty("clear", JSCallFunction { args -> pageClear(args) })
        pageObj.setProperty("open", JSCallFunction { args -> pageOpen(args) })
        pageObj.setProperty("back", JSCallFunction { pageBack() })
        pageObj.setProperty("on", JSCallFunction { args -> pageOn(args) })
        val slotObj = ctx.createNewJSObject()
        slotObj.setProperty("set", JSCallFunction { args -> slotSet(args) })
        slotObj.setProperty("remove", JSCallFunction { args -> slotRemove(args) })
        slotObj.setProperty("clear", JSCallFunction { slotClear() })
        slotObj.setProperty("on", JSCallFunction { args -> slotOn(args) })
        val actionObj = ctx.createNewJSObject()
        actionObj.setProperty("set", JSCallFunction { args -> actionSet(args) })
        actionObj.setProperty("remove", JSCallFunction { args -> actionRemove(args) })
        actionObj.setProperty("clear", JSCallFunction { actionClear() })
        actionObj.setProperty("on", JSCallFunction { args -> actionOn(args) })
        uiObj.setProperty("page", pageObj)
        uiObj.setProperty("slot", slotObj)
        uiObj.setProperty("action", actionObj)
        val mediaObj = ctx.createNewJSObject()
        mediaObj.setProperty("saveImage", JSCallFunction { args -> mediaSaveImage(args) })
        val shareObj = ctx.createNewJSObject()
        shareObj.setProperty("send", JSCallFunction { args -> shareSend(args) })
        val clipboardObj = ctx.createNewJSObject()
        clipboardObj.setProperty("set", JSCallFunction { args -> clipboardSet(args) })
        clipboardObj.setProperty("get", JSCallFunction { clipboardGet() })
        xuan.setProperty("zmusic", zmusic)
        xuan.setProperty("engine", engine)
        xuan.setProperty("runtime", runtime)
        xuan.setProperty("notice", notice)
        xuan.setProperty("theme", theme)
        xuan.setProperty("hook", hook)
        xuan.setProperty("timer", timer)
        xuan.setProperty("pack", pack)
        xuan.setProperty("player", playerObj)
        xuan.setProperty("http", httpObj)
        xuan.setProperty("store", storeObj)
        xuan.setProperty("ui", uiObj)
        xuan.setProperty("media", mediaObj)
        xuan.setProperty("share", shareObj)
        xuan.setProperty("clipboard", clipboardObj)
        xuan.setProperty("delay", JSCallFunction { args -> delayMs(args) })
        xuan.setProperty("require", JSCallFunction { args -> requireJs(ctx, args) })
        global.setProperty("Xuan", xuan)
        clipboardObj.release()
        shareObj.release()
        mediaObj.release()
        actionObj.release()
        slotObj.release()
        pageObj.release()
        uiObj.release()
        storeObj.release()
        httpObj.release()
        playerObj.release()
        pack.release()
        timer.release()
        hook.release()
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
            Object.freeze(Xuan.hook);
            Object.freeze(Xuan.timer);
            Object.freeze(Xuan.pack);
            Object.freeze(Xuan.player);
            Object.freeze(Xuan.http);
            Object.freeze(Xuan.store);
            Object.freeze(Xuan.ui.page);
            Object.freeze(Xuan.ui.slot);
            Object.freeze(Xuan.ui.action);
            Object.freeze(Xuan.ui);
            Object.freeze(Xuan.media);
            Object.freeze(Xuan.share);
            Object.freeze(Xuan.clipboard);
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

    private fun themeSet(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) {
            rejectHost("theme.set")
            return java.lang.Boolean.FALSE
        }
        val ok = PluginTextThemeBridge.set(record.id, args.firstOrNull())
        if (!ok) {
            journal.append(record.id, "Xuan.theme.set 失败: 参数非法")
            PluginLog.w(debug(), "插件 ${record.id} theme.set 失败: 参数非法")
        }
        return if (ok) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
    }

    private fun themeClear(): Any {
        if (ended || !hostApiAllowed()) {
            rejectHost("theme.clear")
            return java.lang.Boolean.FALSE
        }
        return if (PluginTextThemeBridge.clear(record.id)) {
            java.lang.Boolean.TRUE
        } else {
            java.lang.Boolean.FALSE
        }
    }

    private fun rejectHost(op: String) {
        val why = when {
            ended -> "会话已结束"
            !hostApiAllowed() -> "尚未 Running"
            else -> "拒绝"
        }
        journal.append(record.id, "Xuan.$op 失败: $why")
        PluginLog.w(debug(), "插件 ${record.id} $op 失败: $why")
    }

    private fun themeGet(ctx: QuickJSContext): Any {
        if (ended || !hostApiAllowed()) {
            return ctx.parse("{}")
        }
        val map = PluginTextThemeBridge.getHexMap()
        val json = PluginJson.stringify(map)
        return ctx.parse(json)
    }

    private fun hookAdd(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val name = PluginHookEvents.nonEmptyName(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        val id = PluginHookEvents.nonEmptyName(args.getOrNull(1)) ?: return java.lang.Boolean.FALSE
        val fn = args.getOrNull(2) as? JSFunction ?: return java.lang.Boolean.FALSE
        fn.hold()
        val prev = hookFns.getOrPut(name) { LinkedHashMap() }.put(id, fn)
        prev?.release()
        hooks.add(record.id, name, id)
        PluginHookEvents.syncPayload(name, hostFacts(), playback())?.let { payload ->
            val ctx = contextRef.get()
            if (ctx != null) invokeFn(ctx, fn, payload)
        }
        return java.lang.Boolean.TRUE
    }

    private fun hookRemove(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val name = PluginHookEvents.nonEmptyName(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        val id = PluginHookEvents.nonEmptyName(args.getOrNull(1)) ?: return java.lang.Boolean.FALSE
        hooks.remove(record.id, name, id)
        hookFns[name]?.remove(id)?.release()
        return java.lang.Boolean.TRUE
    }

    private fun hookRun(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val name = PluginHookEvents.nonEmptyName(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        val copied = ArrayList<Any?>(args.size.coerceAtLeast(1) - 1)
        for (i in 1 until args.size) {
            when (val item = PluginJsonable.copy(args[i])) {
                PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
                is PluginJsonCopy.Ok -> copied.add(item.value)
            }
        }
        return if (broadcastHook(name, copied)) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
    }

    private fun timerSimple(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val ms = PluginTimerParams.parseMs(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        val fn = args.getOrNull(1) as? JSFunction ?: return java.lang.Boolean.FALSE
        fn.hold()
        val slot = TimerSlot(name = null, ms = ms, remaining = 1, fn = fn)
        synchronized(timersLock) { simpleTimers.add(slot) }
        scheduleSlot(slot)
        return java.lang.Boolean.TRUE
    }

    private fun timerCreate(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val name = PluginHookEvents.nonEmptyName(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        val ms = PluginTimerParams.parseMs(args.getOrNull(1)) ?: return java.lang.Boolean.FALSE
        val reps = PluginTimerParams.parseReps(args.getOrNull(2)) ?: return java.lang.Boolean.FALSE
        val fn = args.getOrNull(3) as? JSFunction ?: return java.lang.Boolean.FALSE
        fn.hold()
        val slot = TimerSlot(name = name, ms = ms, remaining = reps, fn = fn)
        synchronized(timersLock) {
            namedTimers.remove(name)?.cancelAndRelease()
            namedTimers[name] = slot
        }
        scheduleSlot(slot)
        return java.lang.Boolean.TRUE
    }

    private fun timerRemove(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val name = PluginHookEvents.nonEmptyName(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        synchronized(timersLock) {
            namedTimers.remove(name)?.cancelAndRelease()
        }
        return java.lang.Boolean.TRUE
    }

    private fun timerExists(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val name = PluginHookEvents.nonEmptyName(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        val exists = synchronized(timersLock) { name in namedTimers }
        return if (exists) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
    }

    private fun scheduleSlot(slot: TimerSlot) {
        val future = timerScheduler.schedule(
            {
                executor.execute { fireTimer(slot) }
            },
            slot.ms,
            TimeUnit.MILLISECONDS,
        )
        slot.future = future
    }

    private fun fireTimer(slot: TimerSlot) {
        if (ended || !hostApiAllowed()) return
        val still = synchronized(timersLock) {
            if (slot.name != null) namedTimers[slot.name] === slot else simpleTimers.contains(slot)
        }
        if (!still) return
        withJsGuard("timer") { ctx ->
            invokeFn(ctx, slot.fn, emptyList())
        }
        if (ended || !hostApiAllowed()) return
        synchronized(timersLock) {
            val current = if (slot.name != null) namedTimers[slot.name] else slot
            if (current !== slot) return
            if (slot.remaining == 0) {
                scheduleSlot(slot)
                return
            }
            if (slot.remaining > 1) {
                slot.remaining -= 1
                scheduleSlot(slot)
                return
            }
            if (slot.name != null) namedTimers.remove(slot.name)
            else simpleTimers.remove(slot)
            slot.cancelAndRelease()
        }
    }

    private fun cancelAllTimers() {
        synchronized(timersLock) {
            namedTimers.values.forEach { it.cancelAndRelease() }
            namedTimers.clear()
            simpleTimers.forEach { it.cancelAndRelease() }
            simpleTimers.clear()
        }
    }

    private fun packExists(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val rel = (args.firstOrNull() as? String) ?: return java.lang.Boolean.FALSE
        if (!PluginPackageRules.packFilePathOk(rel)) return java.lang.Boolean.FALSE
        val file = PluginPackFiles.resolve(extractDir, rel) ?: return java.lang.Boolean.FALSE
        return if (file.isFile) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
    }

    private fun packText(args: Array<out Any?>): Any? {
        if (ended || !hostApiAllowed()) return null
        val rel = args.firstOrNull() as? String ?: return null
        if (!PluginPackageRules.packFilePathOk(rel)) return null
        val file = PluginPackFiles.resolve(extractDir, rel) ?: return null
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return PluginUtf8.decodeOrNull(bytes)
    }

    private fun packBytes(ctx: QuickJSContext, args: Array<out Any?>): Any? {
        if (ended || !hostApiAllowed()) return null
        val rel = args.firstOrNull() as? String ?: return null
        if (!PluginPackageRules.packFilePathOk(rel)) return null
        val file = PluginPackFiles.resolve(extractDir, rel) ?: return null
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val arr = ctx.createNewJSArray()
        bytes.forEachIndexed { i, b -> arr.set(b.toInt() and 0xFF, i) }
        return arr
    }

    private fun playerGet(ctx: QuickJSContext): Any? {
        if (ended || !hostApiAllowed()) return null
        return ctx.parse(PluginJson.stringify(playback().toGetMap()))
    }

    private fun playerPlay(): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        return box(player.play())
    }

    private fun playerPause(): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        return box(player.pause())
    }

    private fun playerNext(): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        return box(player.next())
    }

    private fun playerPrev(): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        return box(player.prev())
    }

    private fun playerSeek(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val ms = PluginInts.nonNegMs(args.firstOrNull()) ?: return java.lang.Boolean.FALSE
        return box(player.seek(ms))
    }

    private fun playerLiked(): Any? {
        if (ended || !hostApiAllowed()) return null
        return playback().liked
    }

    private fun playerSetLiked(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val liked = args.firstOrNull() as? Boolean ?: return java.lang.Boolean.FALSE
        return box(player.setLiked(liked))
    }

    private fun httpRequest(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val client = http ?: return java.lang.Boolean.FALSE
        val opts = when (val copied = PluginJsonable.copy(args.getOrNull(0))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> PluginHttpParams.parse(copied.value) ?: return java.lang.Boolean.FALSE
        }
        val fn = args.getOrNull(1) as? JSFunction ?: return java.lang.Boolean.FALSE
        fn.hold()
        val accepted = client.enqueue(record.id, opts) { result ->
            val task = Runnable {
                try {
                    if (!ended && hostApiAllowed()) {
                        withJsGuard("http") { ctx -> invokeFn(ctx, fn, listOf(result)) }
                    }
                } finally {
                    runCatching { fn.release() }
                }
            }
            runOnWorker(task, wait = false, evenIfEnded = true)
        }
        if (!accepted) {
            runCatching { fn.release() }
            return java.lang.Boolean.FALSE
        }
        return java.lang.Boolean.TRUE
    }

    private fun storeGet(ctx: QuickJSContext, args: Array<out Any?>): Any? {
        if (ended || !hostApiAllowed()) return null
        val key = args.firstOrNull() as? String ?: return null
        val value = store.get(record.id, key) ?: return null
        return ctx.parse(PluginJson.stringify(value))
    }

    private fun storeSet(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val key = args.getOrNull(0) as? String ?: return java.lang.Boolean.FALSE
        val value = when (val copied = PluginJsonable.copy(args.getOrNull(1))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> copied.value
        }
        return box(store.set(record.id, key, value))
    }

    private fun storeRemove(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val key = args.firstOrNull() as? String ?: return java.lang.Boolean.FALSE
        return box(store.remove(record.id, key))
    }

    private fun storeKeys(ctx: QuickJSContext): Any {
        if (ended || !hostApiAllowed()) return ctx.parse("[]")
        return ctx.parse(PluginJson.stringify(store.keys(record.id)))
    }

    private fun storeClear(): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        return box(store.clear(record.id))
    }

    fun deliverUiEvent(event: Map<String, Any?>) {
        val leave = event["type"] == "leave"
        val task = Runnable {
            if (ended && !leave) return@Runnable
            if (!leave && !hostApiAllowed()) return@Runnable
            withJsGuard("ui") { ctx ->
                val slot = event["slot"] as? String
                if (slot != null) {
                    slotFns[slot]?.let { invokeFn(ctx, it, listOf(event)) }
                    return@withJsGuard
                }
                val surface = event["surface"] as? String
                if (surface != null && event["page"] == null) {
                    actionFns[surface]?.let { invokeFn(ctx, it, listOf(event)) }
                    return@withJsGuard
                }
                val page = event["page"] as? String ?: return@withJsGuard
                pageFns[page]?.let { invokeFn(ctx, it, listOf(event)) }
            }
        }
        runOnWorker(task, wait = false, evenIfEnded = leave)
    }

    private fun uiAlert(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val spec = when (val copied = PluginJsonable.copy(args.getOrNull(0))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> PluginUiBridge.parseAlert(copied.value) ?: return java.lang.Boolean.FALSE
        }
        val fn = args.getOrNull(1) as? JSFunction
        if (args.size > 1 && fn == null) return java.lang.Boolean.FALSE
        fn?.hold()
        val accepted = ui.presentAlert(
            pluginId = record.id,
            pluginName = record.name,
            spec = spec,
            blocked = faultBusy(),
            onResult = { action ->
                val task = Runnable {
                    try {
                        if (fn != null && !ended && hostApiAllowed()) {
                            withJsGuard("ui") { ctx -> invokeFn(ctx, fn, listOf(action)) }
                        }
                    } finally {
                        runCatching { fn?.release() }
                    }
                }
                runOnWorker(task, wait = false, evenIfEnded = true)
            },
        )
        if (!accepted) {
            runCatching { fn?.release() }
            return java.lang.Boolean.FALSE
        }
        return java.lang.Boolean.TRUE
    }

    private fun uiSheet(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val spec = when (val copied = PluginJsonable.copy(args.getOrNull(0))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> PluginUiBridge.parseSheet(copied.value) ?: return java.lang.Boolean.FALSE
        }
        val fn = args.getOrNull(1) as? JSFunction
        if (args.size > 1 && fn == null) return java.lang.Boolean.FALSE
        fn?.hold()
        val accepted = ui.presentSheet(
            pluginId = record.id,
            pluginName = record.name,
            spec = spec,
            blocked = faultBusy(),
            onResult = { action ->
                val task = Runnable {
                    try {
                        if (fn != null && !ended && hostApiAllowed()) {
                            withJsGuard("ui") { ctx -> invokeFn(ctx, fn, listOf(action)) }
                        }
                    } finally {
                        runCatching { fn?.release() }
                    }
                }
                runOnWorker(task, wait = false, evenIfEnded = true)
            },
        )
        if (!accepted) {
            runCatching { fn?.release() }
            return java.lang.Boolean.FALSE
        }
        return java.lang.Boolean.TRUE
    }

    private fun pageDefine(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val name = PluginUiTree.pageName(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        val spec = when (val copied = PluginJsonable.copy(args.getOrNull(1))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> copied.value
        }
        val parsed = PluginUiTree.parseDefine(spec) ?: return java.lang.Boolean.FALSE
        return box(ui.definePage(record.id, record.name, name, parsed.first, parsed.second))
    }

    private fun pageSet(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val spec = when (val copied = PluginJsonable.copy(args.getOrNull(0))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> copied.value
        }
        val parsed = PluginUiTree.parseLegacySet(spec) ?: return java.lang.Boolean.FALSE
        return box(
            ui.definePage(
                record.id,
                record.name,
                PluginUiTree.DEFAULT_PAGE,
                parsed.first,
                parsed.second,
            ),
        )
    }

    private fun pageGet(ctx: QuickJSContext, args: Array<out Any?>): Any? {
        if (ended || !hostApiAllowed()) return null
        val name = when (val raw = args.getOrNull(0)) {
            null -> PluginUiTree.DEFAULT_PAGE
            else -> PluginUiTree.pageName(raw) ?: return null
        }
        val page = ui.pageOf(record.id, name) ?: return null
        return ctx.parse(PluginJson.stringify(page.toJson()))
    }

    private fun pagePatch(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val name = PluginUiTree.pageName(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        val spec = when (val copied = PluginJsonable.copy(args.getOrNull(1))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> copied.value
        }
        val patch = PluginUiTree.parsePatch(spec) ?: return java.lang.Boolean.FALSE
        return box(ui.patchPage(record.id, name, patch.first, patch.second, patch.third))
    }

    private fun pageClear(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val raw = args.getOrNull(0)
        if (raw == null) return box(ui.clearPages(record.id))
        val name = PluginUiTree.pageName(raw) ?: return java.lang.Boolean.FALSE
        return box(ui.clearPage(record.id, name))
    }

    private fun pageOpen(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val a0 = args.getOrNull(0)
        val a1 = args.getOrNull(1)
        val name: String
        val paramsRaw: Any?
        when (a0) {
            null -> {
                name = PluginUiTree.DEFAULT_PAGE
                paramsRaw = a1
            }
            is String -> {
                name = PluginUiTree.pageName(a0) ?: return java.lang.Boolean.FALSE
                paramsRaw = a1
            }
            else -> {
                name = PluginUiTree.DEFAULT_PAGE
                paramsRaw = a0
            }
        }
        val paramsCopied = when (paramsRaw) {
            null -> PluginJsonCopy.Ok(null)
            else -> PluginJsonable.copy(paramsRaw)
        }
        val params = when (paramsCopied) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> PluginUiTree.parseParams(paramsCopied.value)
                ?: return java.lang.Boolean.FALSE
        }
        return box(ui.openPage(record.id, name, params))
    }

    private fun pageBack(): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        return box(ui.back(record.id))
    }

    private fun pageOn(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val name = PluginUiTree.pageName(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        val fn = args.getOrNull(1) as? JSFunction ?: return java.lang.Boolean.FALSE
        fn.hold()
        pageFns.put(name, fn)?.let { runCatching { it.release() } }
        return java.lang.Boolean.TRUE
    }

    private fun slotSet(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val slot = args.getOrNull(0) as? String ?: return java.lang.Boolean.FALSE
        val spec = when (val copied = PluginJsonable.copy(args.getOrNull(1))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> PluginUiTree.parseSlot(copied.value) ?: return java.lang.Boolean.FALSE
        }
        return box(ui.setSlot(record.id, record.name, slot, spec))
    }

    private fun slotRemove(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val slot = args.getOrNull(0) as? String ?: return java.lang.Boolean.FALSE
        val id = args.getOrNull(1) as? String ?: return java.lang.Boolean.FALSE
        return box(ui.removeSlot(record.id, slot, id))
    }

    private fun slotClear(): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        return box(ui.clearSlots(record.id))
    }

    private fun slotOn(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val slot = args.getOrNull(0) as? String ?: return java.lang.Boolean.FALSE
        if (slot !in PluginUiTree.SLOTS) return java.lang.Boolean.FALSE
        val fn = args.getOrNull(1) as? JSFunction ?: return java.lang.Boolean.FALSE
        fn.hold()
        slotFns.put(slot, fn)?.let { runCatching { it.release() } }
        return java.lang.Boolean.TRUE
    }

    private fun actionSet(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val surface = args.getOrNull(0) as? String ?: return java.lang.Boolean.FALSE
        val spec = when (val copied = PluginJsonable.copy(args.getOrNull(1))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> PluginUiTree.parseAction(copied.value) ?: return java.lang.Boolean.FALSE
        }
        return box(ui.setAction(record.id, record.name, surface, spec))
    }

    private fun actionRemove(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val surface = args.getOrNull(0) as? String ?: return java.lang.Boolean.FALSE
        val id = args.getOrNull(1) as? String ?: return java.lang.Boolean.FALSE
        return box(ui.removeAction(record.id, surface, id))
    }

    private fun actionClear(): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        return box(ui.clearActions(record.id))
    }

    private fun actionOn(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val surface = args.getOrNull(0) as? String ?: return java.lang.Boolean.FALSE
        if (surface !in PluginSurfaces.KNOWN) return java.lang.Boolean.FALSE
        val fn = args.getOrNull(1) as? JSFunction ?: return java.lang.Boolean.FALSE
        fn.hold()
        actionFns.put(surface, fn)?.let { runCatching { it.release() } }
        return java.lang.Boolean.TRUE
    }

    private fun mediaSaveImage(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val opts = when (val copied = PluginJsonable.copy(args.getOrNull(0))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> PluginDeviceParams.parseSave(copied.value) ?: return java.lang.Boolean.FALSE
        }
        val fn = args.getOrNull(1) as? JSFunction ?: return java.lang.Boolean.FALSE
        val packBytes = opts.pack?.let { readPackImage(it) ?: return java.lang.Boolean.FALSE }
        fn.hold()
        val accepted = device.saveImage(record.id, opts.url, packBytes, opts.filename) { result ->
            deliverDeviceResult(fn, result)
        }
        if (!accepted) {
            runCatching { fn.release() }
            return java.lang.Boolean.FALSE
        }
        return java.lang.Boolean.TRUE
    }

    private fun shareSend(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val opts = when (val copied = PluginJsonable.copy(args.getOrNull(0))) {
            PluginJsonCopy.Fail -> return java.lang.Boolean.FALSE
            is PluginJsonCopy.Ok -> PluginDeviceParams.parseShare(copied.value) ?: return java.lang.Boolean.FALSE
        }
        val fn = args.getOrNull(1) as? JSFunction
        if (args.size > 1 && fn == null) return java.lang.Boolean.FALSE
        val packBytes = opts.pack?.let { readPackImage(it) ?: return java.lang.Boolean.FALSE }
        fn?.hold()
        val accepted = device.share(record.id, opts, packBytes) { result ->
            if (fn != null) {
                deliverDeviceResult(fn, result)
            }
        }
        if (!accepted) {
            runCatching { fn?.release() }
            return java.lang.Boolean.FALSE
        }
        return java.lang.Boolean.TRUE
    }

    private fun clipboardSet(args: Array<out Any?>): Any {
        if (ended || !hostApiAllowed()) return java.lang.Boolean.FALSE
        val text = PluginDeviceParams.parseClipboardSet(args.getOrNull(0)) ?: return java.lang.Boolean.FALSE
        return box(device.clipboardSet(text))
    }

    private fun clipboardGet(): Any? {
        if (ended || !hostApiAllowed()) return null
        return device.clipboardGet()
    }

    private fun readPackImage(rel: String): ByteArray? {
        val file = PluginPackFiles.resolve(extractDir, rel) ?: return null
        if (!file.isFile) return null
        val size = file.length()
        if (size <= 0L) return null
        if (size > PluginDeviceParams.MAX_BYTES) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    private fun deliverDeviceResult(fn: JSFunction, result: Map<String, Any?>) {
        val task = Runnable {
            try {
                if (!ended && hostApiAllowed()) {
                    withJsGuard("device") { ctx -> invokeFn(ctx, fn, listOf(result)) }
                }
            } finally {
                runCatching { fn.release() }
            }
        }
        runOnWorker(task, wait = false, evenIfEnded = true)
    }

    private fun box(ok: Boolean): Any =
        if (ok) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE

    private fun requireJs(ctx: QuickJSContext, args: Array<out Any?>): Any? {
        if (ended || !hostApiAllowed()) return null
        val raw = args.firstOrNull() as? String ?: return null
        val rel = PluginPackageRules.normalizeRel(raw) ?: return null
        if (!PluginPackageRules.requirePathOk(rel)) return null
        if (requireCache.containsKey(rel)) return requireCache[rel]
        val file = PluginPackFiles.resolve(extractDir, rel) ?: return null
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val source = PluginUtf8.decodeOrNull(bytes) ?: return null
        val exportsObj = ctx.createNewJSObject()
        val moduleObj = ctx.createNewJSObject()
        moduleObj.setProperty("exports", exportsObj)
        requireCache[rel] = exportsObj
        val wrapped = "(function(exports, module, Xuan) {\n$source\n;return module.exports;})"
        return try {
            val fn = ctx.evaluate(wrapped, rel) as? JSFunction ?: run {
                requireCache.remove(rel)
                exportsObj.release()
                moduleObj.release()
                return null
            }
            val xuan = ctx.getGlobalObject().getJSObject("Xuan")
            val result = try {
                if (xuan != null) fn.call(exportsObj, moduleObj, xuan) else fn.call(exportsObj, moduleObj)
            } finally {
                fn.release()
            }
            requireCache[rel] = result
            moduleObj.release()
            result
        } catch (e: QuickJSException) {
            requireCache.remove(rel)
            journal.append(record.id, "Xuan.require 失败 $rel: ${e.message ?: e}")
            PluginLog.w(debug(), "插件 ${record.id} require $rel 失败: ${e.message}")
            runCatching { exportsObj.release() }
            runCatching { moduleObj.release() }
            null
        }
    }

    private fun invokeFn(ctx: QuickJSContext, fn: JSFunction, args: List<Any?>) {
        val created = ArrayList<Any>()
        try {
            val jsArgs = Array(args.size) { i ->
                toJsArg(ctx, args[i], created)
            }
            fn.call(*jsArgs)
        } catch (e: QuickJSException) {
            journal.append(record.id, "回调异常: ${e.message ?: e}")
            PluginLog.w(debug(), "插件 ${record.id} 回调异常: ${e.message}")
        } finally {
            created.forEach { v ->
                if (v is com.whl.quickjs.wrapper.JSObject) {
                    runCatching { v.release() }
                }
            }
        }
    }

    private fun toJsArg(ctx: QuickJSContext, value: Any?, created: MutableList<Any>): Any? {
        return when (value) {
            null, is Boolean, is String, is Int, is Long, is Double -> value
            is Map<*, *>, is List<*> -> {
                val parsed = ctx.parse(PluginJson.stringify(value))
                created.add(parsed)
                parsed
            }
            else -> value
        }
    }

    private fun withJsGuard(label: String, block: (QuickJSContext) -> Unit) {
        val ctx = contextRef.get() ?: return
        val s = sentinel
        s?.mark(record.id)
        try {
            block(ctx)
        } catch (t: Throwable) {
            journal.append(record.id, "$label 失败: ${t.message ?: t}")
            PluginLog.e(debug(), "插件 ${record.id} $label 失败", t)
        } finally {
            s?.clear(record.id)
        }
    }

    private fun runOnWorker(task: Runnable, wait: Boolean, evenIfEnded: Boolean = false) {
        if (ended && !evenIfEnded) return
        if (Thread.currentThread() === worker) {
            task.run()
            return
        }
        if (!wait) {
            try {
                executor.execute(task)
            } catch (_: Throwable) {
                runCatching { task.run() }
            }
            return
        }
        val done = CountDownLatch(1)
        try {
            executor.execute {
                try {
                    task.run()
                } finally {
                    done.countDown()
                }
            }
            done.await(2, TimeUnit.SECONDS)
        } catch (_: Throwable) {
            done.countDown()
        }
    }

    private fun setState(next: PluginJsState) {
        state = next
        onState(next)
        if (next == PluginJsState.Error) {
            ended = true
            cancelAllTimers()
            ui.dropPlugin(record.id)
            dropHooksLocked()
            http?.cancel(record.id)
            device.cancel(record.id)
            PluginTextThemeBridge.clearIfOwner(record.id)
            destroyContext()
        }
    }

    private fun dropHooksLocked() {
        hooks.dropPlugin(record.id)
        hookFns.values.forEach { ids ->
            ids.values.forEach { fn -> runCatching { fn.release() } }
        }
        hookFns.clear()
        dropUiFnsLocked()
    }

    private fun dropUiFnsLocked() {
        pageFns.values.forEach { fn -> runCatching { fn.release() } }
        pageFns.clear()
        slotFns.values.forEach { fn -> runCatching { fn.release() } }
        slotFns.clear()
        actionFns.values.forEach { fn -> runCatching { fn.release() } }
        actionFns.clear()
    }

    private fun destroyContext() {
        requireCache.clear()
        val ctx = contextRef.getAndSet(null) ?: return
        try {
            ctx.destroy()
        } catch (_: Throwable) {
            // 已销毁或线程问题
        }
    }

    private class TimerSlot(
        val name: String?,
        val ms: Long,
        var remaining: Int,
        val fn: JSFunction,
    ) {
        var future: ScheduledFuture<*>? = null

        fun cancelAndRelease() {
            future?.cancel(false)
            future = null
            runCatching { fn.release() }
        }
    }

    companion object {
        const val STATE_INITIALIZING = "Initializing"
        const val STATE_RUNNING = "Running"
        const val STATE_ERROR = "Error"
    }
}
