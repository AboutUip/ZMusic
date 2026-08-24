package com.kite.zmusic.ui.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.AppUpdateUiState
import com.kite.zmusic.plugin.PluginFault
import com.kite.zmusic.plugin.PluginFaultKind
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.theme.MainPalette

@Composable
fun PluginFaultHost() {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val update by app.appUpdateCoordinator.ui.collectAsStateWithLifecycle()
    val blocking = update is AppUpdateUiState.Prompt || update is AppUpdateUiState.ReadyToInstall
    val fault by app.pluginEngine.currentFault.collectAsStateWithLifecycle()
    val shown = fault
    if (shown == null || blocking) return
    val log = shown.log.trim()
    GlassAlertDialog(
        title = if (shown.kind == PluginFaultKind.Crash) "插件崩溃" else "插件错误",
        message = faultMessage(shown),
        confirmLabel = "我知道了",
        onConfirm = { app.pluginEngine.dismissFault() },
        onDismiss = { app.pluginEngine.dismissFault() },
        cancelLabel = null,
        tertiaryLabel = if (log.isNotEmpty()) "复制日志" else null,
        onTertiary = {
            copyLog(context, log)
            context.showIslandNotice("已复制日志")
        },
        extraContent = if (log.isNotEmpty()) {
            {
                SelectionContainer {
                    Text(
                        text = log,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MainPalette.Card)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        } else {
            null
        },
    )
}

private fun faultMessage(fault: PluginFault): String {
    val name = fault.name.ifBlank { fault.id }
    return when (fault.kind) {
        PluginFaultKind.Error ->
            "「$name」发生错误，本次已停止运行。其他插件不受影响。"
        PluginFaultKind.Crash ->
            "「$name」上次运行时崩溃，已暂停该插件，以免再次导致应用退出。"
    }
}

private fun copyLog(context: Context, log: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("插件日志", log))
}
