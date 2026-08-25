package com.kite.zmusic.ui.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassSheetAction

@Composable
fun PluginSheetHost() {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val fault by app.pluginEngine.currentFault.collectAsStateWithLifecycle()
    val update by app.appUpdateCoordinator.ui.collectAsStateWithLifecycle()
    val blocking = update is com.kite.zmusic.data.AppUpdateUiState.Prompt ||
        update is com.kite.zmusic.data.AppUpdateUiState.ReadyToInstall
    val sheet by app.pluginEngine.ui.sheet.collectAsStateWithLifecycle()
    val menu by app.pluginEngine.ui.contextMenu.collectAsStateWithLifecycle()
    val shown = sheet
    if (shown == null || menu != null || fault != null || blocking) return
    GlassActionSheet(
        title = shown.title,
        message = shown.message,
        onDismiss = { app.pluginEngine.ui.dismissSheet("dismiss") },
        contentKey = "plugin-sheet-${shown.pluginId}",
        actions = shown.actions.map { action ->
            GlassSheetAction(
                label = action.label,
                destructive = action.destructive,
            ) {
                app.pluginEngine.ui.dismissSheet(action.id)
            }
        },
    )
}
