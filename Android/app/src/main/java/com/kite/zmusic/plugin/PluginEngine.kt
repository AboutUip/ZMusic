package com.kite.zmusic.plugin

import com.kite.zmusic.BuildConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 插件引擎。产品路径是 [registerFromZpp] / [installWorkshopZpp] **注册**，不扫描目录发现插件。
 * 解压位置：`filesDir/plugin-engine/installed/<id>/`。
 *
 * 调试例外：开关开启时，启动从 [debugDropDir] 收取 `.zpp`（其它 id 可覆盖同包），
 * 再装入内置探针 [PluginDebugProbe]。投放目录中的 `dev.zmusic.probe` 会被忽略，
 * 探针始终以 APK 内 `plugin-probe/` 为准。关闭调试则不看投放目录，也不运行探针。
 *
 * 离线：不启动任何插件会话；恢复在线后再按启用位加载。
 *
 * 调试 API（[ping]、[registerFromZpp]、[setEnabled]、[clearQuarantine]、[dumpRecords]）
 * 在调试开关关闭时抛 [PluginDebugApiDeniedException]。
 * 产品路径：[listModules]、[installWorkshopZpp]、[setModuleEnabled]、[uninstallModule]
 * 不依赖调试开关。探针不可由用户开关或删除。
 */
class PluginEngine(
    filesDir: File,
    private val debugEnabled: () -> Boolean,
    private val appVersionName: String = BuildConfig.VERSION_NAME,
    private val debugDropDir: File? = null,
    private val showNotice: (message: String, coverUrl: String?) -> Unit = { _, _ -> },
    private val bundledDebugProbe: () -> File? = { null },
    host: PluginHostBindings = PluginHostBindings(),
) {
    constructor(
        filesDir: File,
        debugStore: PluginDebugStore,
        appVersionName: String = BuildConfig.VERSION_NAME,
        debugDropDir: File? = null,
        showNotice: (message: String, coverUrl: String?) -> Unit = { _, _ -> },
        bundledDebugProbe: () -> File? = { null },
        host: PluginHostBindings = PluginHostBindings(),
    ) : this(
        filesDir = filesDir,
        debugEnabled = { debugStore.current() },
        appVersionName = appVersionName,
        debugDropDir = debugDropDir,
        showNotice = showNotice,
        bundledDebugProbe = bundledDebugProbe,
        host = host,
    )
    private val paths = PluginPaths.fromFilesDir(filesDir)
    private val registryStore = PluginRegistryStore(paths.registryFile)
    private val sentinel = PluginCrashSentinel(paths)
    private val journal = PluginFaultJournal(paths.faultLogDir)
    private val faultCenter = PluginFaultCenter()
    private val hookRegistry = PluginHookRegistry()
    private val kvStore = PluginKvStore(paths.storeDir)
    val ui = PluginUiBridge()
    private val player: PluginPlayerController = host.player
    private val http: PluginHttpClient? = host.httpClient?.let { PluginHttpClient(it) }
    private val device: PluginDeviceHost = host.device
    private val timerScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "zmusic-plugin-timer").apply { isDaemon = true }
    }
    @Volatile
    private var hostFacts: PluginHostFacts = PluginHostFacts(
        online = true,
        dark = false,
        loggedIn = false,
    )
    @Volatile
    private var playback: PluginPlaybackSnapshot = PluginPlaybackSnapshot.EMPTY
    private val lock = Any()
    private var records: List<PluginRecord> = emptyList()
    private val sessions = ConcurrentHashMap<String, PluginSession>()
    private val splashNames = MutableStateFlow<List<String>>(emptyList())
    val splashPendingNames: StateFlow<List<String>> = splashNames.asStateFlow()
    val currentFault: StateFlow<PluginFault?> = faultCenter.current

    private val _modulesRevision = MutableStateFlow(0)
    /** 本机模块登记变化（安装 / 卸载 / 开关），UI 据此刷新。 */
    val modulesRevision: StateFlow<Int> = _modulesRevision.asStateFlow()

    fun dismissFault() = faultCenter.dismiss()

    /**
     * 更新宿主快照并投递对应钩子。离线变化应在 [setOffline] 停会话之前调用。
     */
    fun setHostFacts(next: PluginHostFacts) {
        val prev = hostFacts
        if (prev == next) return
        hostFacts = next
        if (prev.online && !next.online) {
            broadcastHook(PluginHookEvents.APP_OFFLINE, emptyList(), wait = true)
        } else if (!prev.online && next.online) {
            broadcastHook(PluginHookEvents.APP_ONLINE, emptyList(), wait = false)
        }
        if (prev.dark != next.dark) {
            broadcastHook(
                PluginHookEvents.APP_APPEARANCE,
                listOf(mapOf("dark" to next.dark)),
                wait = false,
            )
        }
        if (prev.loggedIn != next.loggedIn) {
            broadcastHook(
                PluginHookEvents.USER_SESSION,
                listOf(mapOf("loggedIn" to next.loggedIn)),
                wait = false,
            )
        }
        if (prev.foreground != next.foreground) {
            broadcastHook(
                if (next.foreground) PluginHookEvents.APP_FOREGROUND else PluginHookEvents.APP_BACKGROUND,
                emptyList(),
                wait = false,
            )
        }
    }

    fun hostFacts(): PluginHostFacts = hostFacts

    fun playbackSnapshot(): PluginPlaybackSnapshot = playback

    /**
     * 更新播放快照。进度变化只写入缓存；曲目 / 播放意图 / 队列 / 喜欢变化才投钩子。
     */
    fun setPlaybackSnapshot(next: PluginPlaybackSnapshot) {
        val prev = playback
        playback = next
        if (prev.trackId != next.trackId) {
            broadcastHook(PluginHookEvents.PLAYER_TRACK, listOf(next.trackArg()), wait = false)
        }
        if (prev.playing != next.playing) {
            broadcastHook(PluginHookEvents.PLAYER_STATE, listOf(next.stateMap()), wait = false)
        }
        if (prev.queueIndex != next.queueIndex ||
            prev.queueLength != next.queueLength ||
            prev.queueIds != next.queueIds
        ) {
            broadcastHook(PluginHookEvents.PLAYER_QUEUE, listOf(next.queueMap()), wait = false)
        }
        if (prev.liked != next.liked) {
            broadcastHook(PluginHookEvents.PLAYER_LIKED, listOf(next.likedMap()), wait = false)
        }
    }

    internal fun broadcastHook(
        name: String,
        args: List<Any?>,
        wait: Boolean = false,
    ): Boolean {
        val refs = hookRegistry.listeners(name)
        for (ref in refs) {
            sessions[ref.pluginId]?.deliverHook(ref.event, ref.listenerId, args, wait)
        }
        return true
    }

    fun emitUiGesture(type: String, surface: String, target: PluginUiTarget) {
        val name = when (type) {
            "press" -> PluginHookEvents.UI_PRESS
            "longPress" -> PluginHookEvents.UI_LONG_PRESS
            "menu" -> PluginHookEvents.UI_MENU
            else -> return
        }
        val payload = mapOf(
            "type" to type,
            "surface" to surface,
            "target" to target.toMap(),
        )
        broadcastHook(name, listOf(payload), wait = false)
    }

    fun handleSurfaceLongPress(
        surface: String,
        target: PluginUiTarget,
        hostDefaultLabel: String? = null,
        onHostDefault: (() -> Unit)? = null,
    ) {
        emitUiGesture("longPress", surface, target)
        if (!ui.presentSurfaceMenu(surface, target, hostDefaultLabel, onHostDefault)) {
            onHostDefault?.invoke()
        }
    }

    fun emitUiMenu(surface: String, target: PluginUiTarget) {
        emitUiGesture("menu", surface, target)
    }

    private var started = false
    private var offline = false
    private var ready = CompletableDeferred<Unit>().also { it.complete(Unit) }
    private val awaiting = linkedSetOf<String>()

    /**
     * 插件之间同时执行入口的上限。`null` 或不大于 0 表示不限制（默认）。
     */
    var maxParallelSessions: Int? = null

    fun start(offline: Boolean = false) {
        synchronized(lock) {
            if (started) return
            started = true
            this.offline = offline
            paths.ensure()
            var snap = registryStore.load()
            if (snap.lastEngineNumber < PluginEngineVersion.number) {
                snap = snap.copy(
                    lastEngineNumber = PluginEngineVersion.number,
                    plugins = snap.plugins.map { it.copy(quarantined = false) },
                )
                PluginLog.d(debugEnabled(), "引擎版本上升，已解除隔离")
            }
            val debug = debugEnabled()
            val dirty = sentinel.dirtyIds()
            if (dirty.isNotEmpty()) {
                snap = snap.copy(
                    plugins = snap.plugins.map { rec ->
                        if (rec.id in dirty) rec.copy(quarantined = true) else rec
                    },
                )
                dirty.forEach { id ->
                    sentinel.clear(id)
                    if (id == PluginDebugProbe.ID && !debug) return@forEach
                    PluginLog.w(debug, "哨兵残留，隔离 $id")
                    val rec = snap.plugins.find { it.id == id }
                    journal.append(id, "进程在插件入口执行期间退出（哨兵残留）")
                    reportFault(
                        PluginFault(
                            id = id,
                            name = rec?.name ?: id,
                            kind = PluginFaultKind.Crash,
                            log = journal.snapshot(id),
                        ),
                    )
                }
            }
            snap = snap.copy(lastEngineNumber = PluginEngineVersion.number)
            records = snap.plugins
            if (debug) {
                ingestDebugDropLocked()
                ingestBundledProbeLocked()
            }
            registryStore.save(
                PluginRegistrySnapshot(
                    lastEngineNumber = PluginEngineVersion.number,
                    plugins = records,
                ),
            )
            ready = CompletableDeferred()
            awaiting.clear()
            if (offline) {
                PluginLog.d(debug, "离线：不加载插件")
                refreshSplashLocked()
                ready.complete(Unit)
                return
            }
            val toRun = eligibleToRunLocked()
            awaiting.addAll(toRun.map { it.id })
            refreshSplashLocked()
            if (toRun.isEmpty()) {
                ready.complete(Unit)
                return
            }
            val gate = maxParallelSessions?.takeIf { it > 0 }?.let { Semaphore(it) }
            toRun.forEach { rec -> launchSessionLocked(rec, gate) }
        }
    }

    /**
     * 离线时停掉全部会话且不再启动；恢复在线后按启用位重新加载。
     */
    fun setOffline(offline: Boolean) {
        val toStop: List<PluginSession>
        synchronized(lock) {
            if (this.offline == offline) return
            this.offline = offline
            if (!started) return
            if (offline) {
                PluginLog.d(debugEnabled(), "进入离线，停止全部插件")
                toStop = sessions.values.toList()
                sessions.clear()
                awaiting.clear()
                refreshSplashLocked()
                maybeCompleteReadyLocked()
            } else {
                PluginLog.d(debugEnabled(), "恢复在线，加载已启用插件")
                relaunchEligibleLocked()
                return
            }
        }
        toStop.forEach { it.stop() }
    }

    fun stop() {
        val toStop: List<PluginSession>
        synchronized(lock) {
            started = false
            toStop = sessions.values.toList()
            sessions.clear()
            awaiting.clear()
            splashNames.value = emptyList()
            if (!ready.isCompleted) ready.complete(Unit)
        }
        toStop.forEach { it.stop() }
    }

    suspend fun awaitReady() {
        val deferred = synchronized(lock) { ready }
        deferred.await()
    }

    fun stateOf(id: String): PluginJsState? {
        sessions[id]?.let { return it.state }
        synchronized(lock) {
            if (records.none { it.id == id }) return null
        }
        return PluginJsState.Uninitialized
    }

    fun hostApiAllowed(pluginId: String): Boolean =
        sessions[pluginId]?.hostApiAllowed() == true

    fun resolvePackFile(pluginId: String, relative: String): File? {
        val src = relative.trim()
        if (src.startsWith("http://") || src.startsWith("https://")) return null
        return PluginPackFiles.resolve(paths.installedDir(pluginId), src)?.takeIf { it.isFile }
    }

    fun ping(): String {
        requireDebug()
        return PluginEngineVersion.DISPLAY
    }

    fun dumpRecords(): List<PluginRecord> {
        requireDebug()
        return listModules()
    }

    /** 本机已注册模块（含未启用）。产品与调试共用。 */
    fun listModules(): List<PluginRecord> {
        synchronized(lock) { return records.toList() }
    }

    fun registerFromZpp(zpp: File): PluginRegisterResult {
        requireDebug()
        return installWorkshopZpp(zpp, replaceExisting = false)
    }

    /**
     * 工坊 / 产品注册：解压校验后写入引擎目录。
     * 新装默认禁用；升级保留原启用位。不要求调试开关。
     */
    fun installWorkshopZpp(
        zpp: File,
        replaceExisting: Boolean = true,
    ): PluginRegisterResult {
        synchronized(lock) {
            paths.ensure()
            val result = installLocked(
                zpp = zpp,
                replaceExisting = replaceExisting,
                enable = null,
                clearQuarantine = false,
            )
            if (result is PluginRegisterResult.Installed || result is PluginRegisterResult.Replaced) {
                persistLocked()
                bumpModulesRevisionLocked()
            }
            return result
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        requireDebug()
        setModuleEnabled(id, enabled)
    }

    /** 产品：模块启用开关。不要求调试。探针禁止操作。 */
    fun setModuleEnabled(id: String, enabled: Boolean) {
        if (id == PluginDebugProbe.ID) return
        synchronized(lock) {
            val rec = records.find { it.id == id } ?: return
            if (rec.enabled == enabled) return
            val next = rec.copy(enabled = enabled)
            records = records.map { if (it.id == id) next else it }
            persistLocked()
            bumpModulesRevisionLocked()
            if (!started) return
            if (enabled) {
                if (!offline &&
                    next.canRun(PluginEngineVersion.number) &&
                    PluginDebugProbe.shouldLaunch(next.id, debugEnabled()) &&
                    sessions[id] == null
                ) {
                    launchSessionLocked(next, null)
                }
                return
            }
            val running = sessions.remove(id)
            awaiting.remove(id)
            refreshSplashLocked()
            maybeCompleteReadyLocked()
            running
        }?.stop()
    }

    /**
     * 产品：从本机卸载模块。停止会话、删登记与解压目录。探针禁止。
     * @return 是否删掉了已登记项
     */
    fun uninstallModule(id: String): Boolean {
        if (id == PluginDebugProbe.ID) return false
        val running = synchronized(lock) {
            if (records.none { it.id == id }) return false
            records = records.filterNot { it.id == id }
            persistLocked()
            bumpModulesRevisionLocked()
            val session = sessions.remove(id)
            awaiting.remove(id)
            refreshSplashLocked()
            maybeCompleteReadyLocked()
            paths.installedDir(id).deleteRecursively()
            sentinel.clear(id)
            journal.clear(id)
            kvStore.erase(id)
            ui.dropPlugin(id)
            http?.cancel(id)
            session
        }
        running?.stop()
        return true
    }

    private fun bumpModulesRevisionLocked() {
        _modulesRevision.value = _modulesRevision.value + 1
    }

    fun clearQuarantine(id: String) {
        requireDebug()
        synchronized(lock) {
            val rec = records.find { it.id == id } ?: return
            if (!rec.quarantined) return
            records = records.map { if (it.id == id) it.copy(quarantined = false) else it }
            persistLocked()
        }
    }

    private fun eligibleToRunLocked(): List<PluginRecord> {
        if (offline) return emptyList()
        val debug = debugEnabled()
        return records.filter {
            it.canRun(PluginEngineVersion.number) &&
                PluginDebugProbe.shouldLaunch(it.id, debug) &&
                sessions[it.id] == null
        }
    }

    private fun relaunchEligibleLocked() {
        val toRun = eligibleToRunLocked()
        if (toRun.isEmpty()) return
        val gate = maxParallelSessions?.takeIf { it > 0 }?.let { Semaphore(it) }
        toRun.forEach { rec ->
            awaiting.add(rec.id)
            launchSessionLocked(rec, gate)
        }
        refreshSplashLocked()
    }

    private fun launchSessionLocked(rec: PluginRecord, gate: Semaphore?) {
        val session = PluginSession(
            record = rec,
            extractDir = paths.installedDir(rec.id),
            debug = { debugEnabled() },
            appVersionName = appVersionName,
            showNotice = showNotice,
            journal = journal,
            hooks = hookRegistry,
            hostFacts = { hostFacts },
            playback = { playback },
            player = player,
            http = http,
            device = device,
            store = kvStore,
            ui = ui,
            faultBusy = { faultCenter.current.value != null },
            broadcastHook = { name, args -> broadcastHook(name, args, wait = false) },
            timerScheduler = timerScheduler,
            onState = { state ->
                if (state == PluginJsState.Running) {
                    sentinel.clear(rec.id)
                }
                if (state == PluginJsState.Running || state == PluginJsState.Error) {
                    synchronized(lock) { settleLocked(rec.id) }
                }
            },
            onEvalFinished = { state ->
                if (state == PluginJsState.Error) {
                    reportFault(
                        PluginFault(
                            id = rec.id,
                            name = rec.name,
                            kind = PluginFaultKind.Error,
                            log = journal.snapshot(rec.id),
                        ),
                    )
                }
            },
            onSessionEnded = { id ->
                hookRegistry.dropPlugin(id)
                PluginTextThemeBridge.clearIfOwner(id)
                ui.dropPlugin(id)
                http?.cancel(id)
                device.cancel(id)
            },
        )
        ui.bind(rec.id) { event -> session.deliverUiEvent(event) }
        sessions[rec.id] = session
        session.start(sentinel, gate)
    }

    private fun maybeCompleteReadyLocked() {
        if (awaiting.isEmpty() && !ready.isCompleted) {
            ready.complete(Unit)
        }
    }

    private fun settleLocked(id: String) {
        if (!awaiting.remove(id)) return
        refreshSplashLocked()
        maybeCompleteReadyLocked()
    }

    private fun refreshSplashLocked() {
        val names = records.filter { it.id in awaiting }.map { it.name }
        splashNames.value = names
    }

    private fun ingestBundledProbeLocked() {
        val zpp = bundledDebugProbe()
        if (zpp == null || !zpp.isFile) {
            PluginLog.w(true, "内置探针缺失")
            return
        }
        when (val result = installLocked(zpp, replaceExisting = true, enable = true, clearQuarantine = true)) {
            is PluginRegisterResult.Installed ->
                PluginLog.d(true, "内置探针已注册 ${result.record.id}")
            is PluginRegisterResult.Replaced ->
                PluginLog.d(true, "内置探针已覆盖 ${result.record.id}")
            is PluginRegisterResult.Skipped ->
                PluginLog.w(true, "内置探针跳过: ${result.reason}")
        }
    }

    private fun ingestDebugDropLocked() {
        val dir = debugDropDir ?: return
        dir.mkdirs()
        val packages = PluginDebugDrop.listPackages(dir)
        if (packages.isEmpty()) {
            PluginLog.d(true, "调试投放目录为空: ${dir.absolutePath}")
            return
        }
        PluginLog.d(true, "调试投放 ${packages.size} 个 .zpp: ${dir.absolutePath}")
        for (zpp in packages) {
            if (ZppUnpacker.peekId(zpp) == PluginDebugProbe.ID) {
                PluginLog.d(true, "投放忽略内置探针 ${zpp.name}，以 APK 为准")
                continue
            }
            val result = installLocked(
                zpp = zpp,
                replaceExisting = true,
                enable = true,
                clearQuarantine = true,
            )
            when (result) {
                is PluginRegisterResult.Installed ->
                    PluginLog.d(true, "投放已注册 ${result.record.id}")
                is PluginRegisterResult.Replaced ->
                    PluginLog.d(true, "投放已覆盖 ${result.record.id}")
                is PluginRegisterResult.Skipped ->
                    PluginLog.w(true, "投放跳过 ${zpp.name}: ${result.reason}")
            }
        }
    }

    /**
     * @param enable `null` 表示新装禁用、升级保留原启用位
     */
    private fun installLocked(
        zpp: File,
        replaceExisting: Boolean,
        enable: Boolean?,
        clearQuarantine: Boolean,
    ): PluginRegisterResult {
        val staging = File(paths.staging, "in-${System.nanoTime()}")
        staging.deleteRecursively()
        when (val unpacked = ZppUnpacker.unpack(zpp, staging)) {
            is UnpackResult.Invalid -> {
                staging.deleteRecursively()
                PluginLog.w(debugEnabled(), "跳过无效包: ${unpacked.reason}")
                return PluginRegisterResult.Skipped(unpacked.reason)
            }
            is UnpackResult.Ok -> {
                val manifest = unpacked.manifest
                if (!manifest.compatibleWith(PluginEngineVersion.number)) {
                    staging.deleteRecursively()
                    PluginLog.w(debugEnabled(), "跳过不兼容引擎的包 ${manifest.id}")
                    return PluginRegisterResult.Skipped("引擎版本不兼容")
                }
                val existing = records.find { it.id == manifest.id }
                if (!replaceExisting && existing != null && existing.version >= manifest.version) {
                    staging.deleteRecursively()
                    return PluginRegisterResult.Skipped("已安装相同或更新版本")
                }
                val dest = paths.installedDir(manifest.id)
                dest.deleteRecursively()
                if (!staging.renameTo(dest)) {
                    staging.copyRecursively(dest, overwrite = true)
                    staging.deleteRecursively()
                }
                val rec = PluginRecord(
                    id = manifest.id,
                    name = manifest.name,
                    version = manifest.version,
                    entry = manifest.entry,
                    engineMin = manifest.engineMin,
                    engineMax = manifest.engineMax,
                    enabled = enable ?: (existing?.enabled ?: false),
                    quarantined = if (clearQuarantine) false else (existing?.quarantined ?: false),
                    capabilities = manifest.capabilities,
                )
                records = records.filter { it.id != rec.id } + rec
                return if (existing == null) {
                    PluginRegisterResult.Installed(rec)
                } else {
                    PluginRegisterResult.Replaced(rec)
                }
            }
        }
    }

    private fun persistLocked() {
        registryStore.save(
            PluginRegistrySnapshot(
                lastEngineNumber = PluginEngineVersion.number,
                plugins = records,
            ),
        )
    }

    private fun reportFault(fault: PluginFault) {
        faultCenter.report(fault)
    }

    private fun requireDebug() {
        if (!debugEnabled()) throw PluginDebugApiDeniedException()
    }
}
