package com.kite.zmusic.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.FileProvider
import com.kite.zmusic.BuildConfig
import com.kite.zmusic.playback.PlaybackBridge
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

private enum class InstallWait {
    None,
    Permission,
    Installer,
}

class AppUpdateCoordinator(
    context: Context,
    catalog: AppUpdateCatalogSource,
    prefs: AppUpdatePrefs,
    downloader: AppUpdateDownloader,
    files: AppUpdateFiles,
    notices: IslandNoticeCenter,
    private val playbackBridge: PlaybackBridge,
    localVersion: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private var installWait = InstallWait.None
    private val wakeLock: PowerManager.WakeLock? = runCatching {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zmusic:apk-update").also {
            it.setReferenceCounted(false)
        }
    }.getOrNull()

    val engine = AppUpdateEngine(
        localVersion = localVersion,
        catalog = catalog,
        prefs = prefs,
        downloader = downloader,
        files = files,
        onSticky = { msg ->
            if (msg == null) notices.clearSticky() else notices.setSticky(msg)
        },
        onToast = { notices.show(it) },
        onKeepAlive = {
            playbackBridge.maybeWarmMediaNotificationOnColdStart()
        },
        hasInstallPermission = { canInstall(appContext) },
    )

    val ui get() = engine.ui

    fun start() {
        scope.launch { engine.check() }
    }

    fun markSplashFinished() {
        engine.markSplashFinished()
    }

    fun ignoreCurrent() {
        engine.ignoreCurrent()
    }

    fun later() {
        engine.later()
    }

    fun startUpdate() {
        scope.launch {
            try {
                wakeLock?.acquire(WakeLockMs)
                engine.startUpdate()
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
            }
        }
    }

    fun tryInstall(activity: Activity) {
        val ready = engine.ui.value as? AppUpdateUiState.ReadyToInstall ?: return
        if (!canInstall(activity)) {
            engine.markNeedsPermission(true)
            installWait = InstallWait.Permission
            openInstallPermission(activity)
            return
        }
        engine.markNeedsPermission(false)
        if (installWait == InstallWait.Installer) return
        installWait = InstallWait.Installer
        launchInstaller(activity, ready.file)
    }

    fun onHostResumed(activity: Activity) {
        when (installWait) {
            InstallWait.Permission -> {
                if (canInstall(activity)) {
                    tryInstall(activity)
                } else {
                    engine.markNeedsPermission(true)
                }
            }
            InstallWait.Installer -> {
                installWait = InstallWait.None
                engine.abandonInstall()
            }
            InstallWait.None -> Unit
        }
    }

    companion object {
        val FILE_PROVIDER_AUTHORITY: String
            get() = BuildConfig.APPLICATION_ID + ".fileprovider"
        private const val WakeLockMs = 30L * 60L * 1000L

        fun canInstall(context: Context): Boolean =
            context.packageManager.canRequestPackageInstalls()

        fun installPermissionIntent(context: Context): Intent =
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.fromParts("package", context.packageName, null),
            )
    }

    private fun openInstallPermission(activity: Activity) {
        val intent = installPermissionIntent(activity)
        runCatching { activity.startActivity(intent) }
    }

    private fun launchInstaller(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, FILE_PROVIDER_AUTHORITY, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activity.startActivity(intent) }
    }
}
