package com.kite.zmusic.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OledBlack = Color(0xFF000000)
private val LabelPrimary = Color(0xFFF2F2F7)
private val LabelSecondary = Color(0xFF8E8E93)

private val ZMusicDarkColors = darkColorScheme(
    primary = LabelPrimary,
    onPrimary = OledBlack,
    secondary = LabelSecondary,
    onSecondary = OledBlack,
    background = OledBlack,
    onBackground = LabelPrimary,
    surface = OledBlack,
    onSurface = LabelPrimary,
)

@Composable
fun ZMusicTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        // 透明系统栏：背景可铺满全屏，避免「留白条」；内容由 windowInsetsPadding 避让刘海/状态栏/导航条
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = ZMusicDarkColors,
        content = content,
    )
}
