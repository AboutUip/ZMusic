package com.kite.zmusic.ui.update

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.AppUpdateLogic
import com.kite.zmusic.data.AppUpdateUiState
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.settings.ChangelogUpdateDialogBody

@Composable
fun AppUpdateHost() {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val coordinator = app.appUpdateCoordinator
    val activity = context.findActivity()
    val ui by coordinator.ui.collectAsStateWithLifecycle()
    when (val s = ui) {
        is AppUpdateUiState.Prompt -> {
            GlassAlertDialog(
                title = AppUpdateLogic.dialogTitle(s.offer.version),
                onDismiss = { coordinator.later() },
                confirmLabel = "立即更新",
                onConfirm = { coordinator.startUpdate() },
                cancelLabel = "下次再说",
                tertiaryLabel = "忽略该版本",
                onTertiary = { coordinator.ignoreCurrent() },
                extraContent = { ChangelogUpdateDialogBody(s.offer.entry) },
            )
        }
        is AppUpdateUiState.ReadyToInstall -> {
            GlassAlertDialog(
                title = "安装 ZMusic v${s.offer.version}",
                message = if (s.needsPermission) {
                    "需要允许安装应用"
                } else {
                    "下载完成，即将安装"
                },
                confirmLabel = if (s.needsPermission) "去开启" else "去安装",
                onConfirm = { activity?.let { coordinator.tryInstall(it) } },
                onDismiss = {},
                cancelLabel = null,
                scrimDismiss = false,
                backDismiss = false,
            )
        }
        else -> Unit
    }
    LaunchedEffect(ui) {
        if (ui is AppUpdateUiState.ReadyToInstall && activity != null) {
            coordinator.tryInstall(activity)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
