package com.kite.zmusic.ui.common

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 全屏内容时隐藏状态栏与导航栏，边缘滑动可临时唤出；退出组合件时恢复。
 */
@Composable
fun ImmersiveSystemUi(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }
}
