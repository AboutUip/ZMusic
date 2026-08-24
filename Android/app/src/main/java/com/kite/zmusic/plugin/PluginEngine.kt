package com.kite.zmusic.plugin

import com.kite.zmusic.BuildConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 插件引擎。产品路径是 [registerFromZpp] / [installWorkshopZpp] **注册**，不扫描目录发现插件。
 * 解压位置：`filesDir/plugin-engine/installed/<id>/`。
 *
 * 调试例外：开关开启时，启动必定装入内置探针 [PluginDebugProbe]，再从 [debugDropDir]
 * 收取 `.zpp`（可覆盖探针）。关闭调试则不看投放目录，也不运行探针。
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
    private val debugStore: PluginDebugStore,
    private val appVersionName: String = BuildConfig.VERSION_NAME,
    private val debugDropDir: File? = null,
    private val showNotice: (message: String, coverUrl: String?) -> Unit = { _, _ -> },
    private val bundledDebugProbe: () -> File? = { null },
) {
    private val paths = PluginPaths.fromFilesDir(filesDir)
    private val registryStore = PluginRegistryStore(paths.registryFile)
    private val sentinel = PluginCrashSentinel(paths)
    private val journal = PluginFaultJournal(paths.faultLogDir)
    private val faultCenter = PluginFaultCenter()
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
                PluginLog.d(debugStore.current(), "引擎版本上升，已解除隔离")
            }
            val debug = debugStore.current()
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
                ingestBundledProbeLocked()
                ingestDebugDropLocked()
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
                PluginLog.d(debugStore.current(), "进入离线，停止全部插件")
                toStop = sessions.values.toList()
                sessions.clear()
                awaiting.clear()
                refreshSplashLocked()
                maybeCompleteReadyLocked()
            } else {
                PluginLog.d(debugStore.current(), "恢复在线，加载已启用插件")
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
                    PluginDebugProbe.shouldLaunch(next.id, debugStore.current()) &&
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
        val debug = debugStore.current()
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
            debug = { debugStore.current() },
            appVersionName = appVersionName,
            showNotice = showNotice,
            journal = journal,
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
            onSessionEnded = { id -> PluginTextThemeBridge.clearIfOwner(id) },
        )
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
                PluginLog.w(debugStore.current(), "跳过无效包: ${unpacked.reason}")
                return PluginRegisterResult.Skipped(unpacked.reason)
            }
            is UnpackResult.Ok -> {
                val manifest = unpacked.manifest
                if (!manifest.compatibleWith(PluginEngineVersion.number)) {
                    staging.deleteRecursively()
                    PluginLog.w(debugStore.current(), "跳过不兼容引擎的包 ${manifest.id}")
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
        if (!debugStore.current()) throw PluginDebugApiDeniedException()
    }
}
