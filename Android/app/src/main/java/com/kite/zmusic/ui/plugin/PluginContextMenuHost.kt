package com.kite.zmusic.ui.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.ui.common.GlassActionSheet
import com.kite.zmusic.ui.common.GlassSheetAction

@Composable
fun PluginContextMenuHost() {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val fault by app.pluginEngine.currentFault.collectAsStateWithLifecycle()
    val update by app.appUpdateCoordinator.ui.collectAsStateWithLifecycle()
    val blocking = update is com.kite.zmusic.data.AppUpdateUiState.Prompt ||
        update is com.kite.zmusic.data.AppUpdateUiState.ReadyToInstall
    val menu by app.pluginEngine.ui.contextMenu.collectAsStateWithLifecycle()
    val shown = menu
    if (shown == null || fault != null || blocking) return
    val hostLabel = shown.hostDefaultLabel
    GlassActionSheet(
        title = shown.title,
        message = shown.message,
        coverUrl = shown.coverUrl,
        onDismiss = { app.pluginEngine.ui.dismissSurfaceMenu(null) },
        contentKey = "plugin-surface-${shown.surface}",
        actions = buildList {
            if (hostLabel != null) {
                add(
                    GlassSheetAction(hostLabel) {
                        app.pluginEngine.ui.runSurfaceHostDefault()
                    },
                )
            }
            shown.pluginActions.forEach { action ->
                add(
                    GlassSheetAction(
                        label = action.title,
                        destructive = action.destructive,
                    ) {
                        app.pluginEngine.ui.dismissSurfaceMenu(action)
                    },
                )
            }
        },
    )
}
