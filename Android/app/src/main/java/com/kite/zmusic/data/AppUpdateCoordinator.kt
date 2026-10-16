package com.kite.zmusic.data

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.FileProvider
import com.kite.zmusic.BuildConfig
import com.kite.zmusic.i18n.t
import com.kite.zmusic.playback.PlaybackBridge
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val notices: IslandNoticeCenter,
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
        val offer = engine.beginUpdate() ?: return
        scope.launch {
            try {
                wakeLock?.acquire(WakeLockMs)
                engine.runUpdate(offer)
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
        val source = ready.file
        scope.launch {
            val staged = runCatching { stageInstallFile(source) }.getOrNull()
            if (staged == null) {
                withContext(Dispatchers.Main) {
                    installWait = InstallWait.None
                    notices.show(t("无法打开系统安装器"))
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (activity.isFinishing || activity.isDestroyed) {
                    installWait = InstallWait.None
                    return@withContext
                }
                if (!launchInstaller(activity, staged)) {
                    installWait = InstallWait.None
                    notices.show(t("无法打开系统安装器"))
                }
            }
        }
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
        private const val APK_MIME = "application/vnd.android.package-archive"

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

    /**
     * 下载件留在内部目录。部分系统安装器会把 content URI 还原成真实路径，
     * 读不到 /data/user 下的文件，于是报解析失败或找不到安装包。安装前复制到外部临时目录。
     */
    private fun stageInstallFile(source: File): File {
        if (!source.isFile || source.length() <= 0L) error("missing apk")
        val root = appContext.externalCacheDir ?: appContext.getExternalFilesDir(null) ?: return source
        val dir = File(root, "updates")
        if (!dir.isDirectory && !dir.mkdirs()) return source
        val dest = File(dir, "ZMusic-update.apk")
        if (dest.isFile && dest.length() == source.length() && dest.lastModified() >= source.lastModified()) {
            dest.setReadable(true, false)
            return dest
        }
        val part = File(dir, "ZMusic-update.apk.part")
        return runCatching {
            java.io.FileOutputStream(part).use { output ->
                source.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                output.fd.sync()
            }
            if (part.length() != source.length()) error("stage size")
            if (dest.exists() && !dest.delete()) error("stage replace")
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            dest.setReadable(true, false)
            dest
        }.getOrElse {
            part.delete()
            source
        }
    }

    private fun launchInstaller(activity: Activity, file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val uri = FileProvider.getUriForFile(activity, FILE_PROVIDER_AUTHORITY, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            clipData = ClipData.newRawUri("ZMusic", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val targets = activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in targets) {
            val pkg = info.activityInfo?.packageName ?: continue
            runCatching {
                activity.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return runCatching { activity.startActivity(intent) }.isSuccess
    }
}
