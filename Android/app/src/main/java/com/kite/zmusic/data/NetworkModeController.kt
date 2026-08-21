package com.kite.zmusic.data

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 设备网络：在线 / 波动（1 分钟宽限）/ 离线。
 * 宽限结束仍不通 → 回主页并进入离线页；恢复后逐步刷新仓库。
 * 灵动岛文案由主界面订阅相位后再发，避免启动页期间被消费掉。
 */
class NetworkModeController(
    app: Application,
    private val homeFeed: HomeFeedRepository,
    private val libraryHome: LibraryHomeRepository,
    private val liked: LikedPlaylistRepository,
) {
    private val appContext = app.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val initialOnline = appContext.isNetworkOnline()
    private val _state = MutableStateFlow(
        NetworkUiState(
            phase = if (initialOnline) NetworkPhase.Online else NetworkPhase.Offline,
            online = initialOnline,
        ),
    )
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    private val _commands = MutableSharedFlow<NetworkCommand>(extraBufferCapacity = 1)
    val commands: SharedFlow<NetworkCommand> = _commands.asSharedFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var graceJob: Job? = null
    private var restoreJob: Job? = null
    private var watchdogJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = postLink()
            override fun onLost(network: Network) = postLink()
            override fun onUnavailable() = postLink()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = postLink()
        }
        callback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        applyLink(appContext.isNetworkOnline())
        startWatchdog()
    }

    private fun postLink() {
        appContext.mainExecutor.execute {
            applyLink(appContext.isNetworkOnline())
        }
    }

    private fun applyLink(online: Boolean) {
        val prev = _state.value
        val nextPhase = NetworkPhaseLogic.onLinkChanged(prev.phase, online)
        if (nextPhase == prev.phase && online == prev.online) return
        commit(prev.phase, nextPhase, online)
    }

    private fun commit(from: NetworkPhase, to: NetworkPhase, online: Boolean) {
        _state.value = NetworkUiState(phase = to, online = online)
        when {
            from == NetworkPhase.Online && to == NetworkPhase.Fluctuating -> {
                startGraceWatch()
            }
            from == NetworkPhase.Fluctuating && to == NetworkPhase.Online -> {
                graceJob?.cancel()
                graceJob = null
                restoreServices()
            }
            from == NetworkPhase.Fluctuating && to == NetworkPhase.Offline -> {
                graceJob?.cancel()
                graceJob = null
                _commands.tryEmit(NetworkCommand.ForceHome)
            }
            from == NetworkPhase.Offline && to == NetworkPhase.Online -> {
                restoreServices()
            }
        }
        if (to == NetworkPhase.Online) {
            watchdogJob?.cancel()
            watchdogJob = null
        } else {
            startWatchdog()
        }
    }

    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            while (isActive && _state.value.phase != NetworkPhase.Online) {
                delay(2_000)
                applyLink(appContext.isNetworkOnline())
            }
        }
    }

    private fun startGraceWatch() {
        graceJob?.cancel()
        graceJob = scope.launch {
            val deadline = SystemClock.elapsedRealtime() + NetworkPhaseLogic.GRACE_MS
            while (isActive) {
                val online = appContext.isNetworkOnline()
                if (online) {
                    applyLink(true)
                    return@launch
                }
                if (SystemClock.elapsedRealtime() >= deadline) {
                    val prev = _state.value.phase
                    val next = NetworkPhaseLogic.onGraceExpired(prev, false)
                    if (next != prev) commit(prev, next, false)
                    return@launch
                }
                delay(1_000)
            }
        }
    }

    private fun restoreServices() {
        restoreJob?.cancel()
        restoreJob = scope.launch {
            delay(280)
            runCatching { homeFeed.refresh(force = true) }
            delay(420)
            runCatching { libraryHome.refresh(force = true) }
            delay(420)
            runCatching { liked.prefetchOnAppReady() }
        }
    }
}
