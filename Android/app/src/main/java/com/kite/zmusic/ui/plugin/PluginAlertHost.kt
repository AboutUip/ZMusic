package com.kite.zmusic.ui.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.ui.common.GlassAlertDialog

@Composable
fun PluginAlertHost() {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val fault by app.pluginEngine.currentFault.collectAsStateWithLifecycle()
    val update by app.appUpdateCoordinator.ui.collectAsStateWithLifecycle()
    val blocking = update is com.kite.zmusic.data.AppUpdateUiState.Prompt ||
        update is com.kite.zmusic.data.AppUpdateUiState.ReadyToInstall
    val alert by app.pluginEngine.ui.alert.collectAsStateWithLifecycle()
    val shown = alert
    if (shown == null || fault != null || blocking) return
    GlassAlertDialog(
        title = shown.title,
        message = shown.message,
        confirmLabel = shown.confirm,
        onConfirm = { app.pluginEngine.ui.dismissAlert("confirm") },
        onDismiss = {
            app.pluginEngine.ui.dismissAlert(if (shown.cancel != null) "cancel" else "dismiss")
        },
        cancelLabel = shown.cancel,
        confirmDestructive = shown.destructive,
    )
}
