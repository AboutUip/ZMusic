package com.kite.zmusic.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 启动检查 / 弹窗 / 下载状态。无 Android 依赖，便于 JVM 单测。
 */
class AppUpdateEngine(
    private val localVersion: String,
    private val catalog: AppUpdateCatalogSource,
    private val prefs: AppUpdatePrefs,
    private val downloader: AppUpdateDownloader,
    private val files: AppUpdateFiles,
    private val onSticky: (String?) -> Unit,
    private val onToast: (String) -> Unit,
    private val onKeepAlive: () -> Unit = {},
    private val hasInstallPermission: () -> Boolean = { true },
) {
    private val mutex = Mutex()
    private val checkOnce = AtomicBoolean(false)
    private val _ui = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val ui: StateFlow<AppUpdateUiState> = _ui.asStateFlow()

    @Volatile var splashFinished: Boolean = false
        private set

    @Volatile var snoozed: Boolean = false
        private set

    @Volatile private var pending: AppUpdateOffer? = null

    suspend fun check() {
        if (!checkOnce.compareAndSet(false, true)) return
        val offer = runCatching { findOffer() }.getOrNull() ?: return
        pending = offer
        maybePrompt()
    }

    fun markSplashFinished() {
        splashFinished = true
        maybePrompt()
    }

    fun ignoreCurrent() {
        val offer = offerOf(_ui.value) ?: pending ?: return
        prefs.ignoredVersion = offer.version
        hidePrompt()
    }

    fun later() {
        snoozed = true
        hidePrompt()
    }

    fun abandonInstall() {
        files.deleteAll()
        onSticky(null)
        later()
    }

    fun markNeedsPermission(needs: Boolean) {
        val cur = _ui.value as? AppUpdateUiState.ReadyToInstall ?: return
        if (cur.needsPermission == needs) return
        _ui.value = cur.copy(needsPermission = needs)
    }

    suspend fun startUpdate() {
        val offer = when (val s = _ui.value) {
            is AppUpdateUiState.Prompt -> s.offer
            is AppUpdateUiState.ReadyToInstall -> return
            is AppUpdateUiState.Downloading -> return
            AppUpdateUiState.Idle -> pending ?: return
        }
        mutex.withLock {
            if (_ui.value is AppUpdateUiState.Downloading) return
            if (_ui.value is AppUpdateUiState.ReadyToInstall) return
            onKeepAlive()
            val dest = files.apkFile(offer.version)
            _ui.value = AppUpdateUiState.Downloading(offer, 0L, offer.apk.sizeBytes)
            onSticky(AppUpdateLogic.progressMessage(offer.version, 0L, offer.apk.sizeBytes))
            var lastPct = -1
            val result = downloader.download(
                url = offer.apk.url,
                dest = dest,
                expectedSize = offer.apk.sizeBytes,
                expectedSha256 = offer.apk.sha256,
            ) { received, total ->
                _ui.value = AppUpdateUiState.Downloading(offer, received, total)
                val pct = if (total > 0L) ((received * 100L) / total).toInt() else -1
                if (pct != lastPct) {
                    lastPct = pct
                    onSticky(AppUpdateLogic.progressMessage(offer.version, received, total))
                }
            }
            result.fold(
                onSuccess = { file ->
                    onSticky(null)
                    _ui.value = AppUpdateUiState.ReadyToInstall(
                        offer = offer,
                        file = file,
                        needsPermission = !hasInstallPermission(),
                    )
                },
                onFailure = {
                    files.deleteApk(offer.version)
                    onSticky(null)
                    onToast("更新下载失败")
                    pending = offer
                    _ui.value = AppUpdateUiState.Prompt(offer)
                },
            )
        }
    }

    private fun maybePrompt() {
        if (!splashFinished || snoozed) return
        if (_ui.value !is AppUpdateUiState.Idle) return
        val offer = pending ?: return
        if (!AppUpdateLogic.canPrompt(offer, localVersion, prefs.testPlan, prefs.ignoredVersion)) {
            return
        }
        _ui.value = AppUpdateUiState.Prompt(offer)
    }

    private fun hidePrompt() {
        pending = null
        if (_ui.value is AppUpdateUiState.Downloading) return
        _ui.value = AppUpdateUiState.Idle
    }

    private suspend fun findOffer(): AppUpdateOffer? {
        val latest = catalog.latest()
        return when (
            AppUpdateLogic.decideFromLatest(
                localVersion = localVersion,
                testPlan = prefs.testPlan,
                ignored = prefs.ignoredVersion,
                latest = latest,
            )
        ) {
            LatestDecision.None -> null
            LatestDecision.FetchExact -> {
                val exact = latest?.let { catalog.exact(it.version) }
                val offer = exact?.takeIf {
                    AppUpdateLogic.canPrompt(it, localVersion, prefs.testPlan, prefs.ignoredVersion)
                }
                offer ?: AppUpdateLogic.pickFromOffers(
                    localVersion,
                    prefs.testPlan,
                    prefs.ignoredVersion,
                    catalog.rangePage(),
                )
            }
            LatestDecision.ScanRange -> AppUpdateLogic.pickFromOffers(
                localVersion,
                prefs.testPlan,
                prefs.ignoredVersion,
                catalog.rangePage(),
            )
        }
    }

    private fun offerOf(state: AppUpdateUiState): AppUpdateOffer? = when (state) {
        is AppUpdateUiState.Prompt -> state.offer
        is AppUpdateUiState.Downloading -> state.offer
        is AppUpdateUiState.ReadyToInstall -> state.offer
        AppUpdateUiState.Idle -> null
    }
}
