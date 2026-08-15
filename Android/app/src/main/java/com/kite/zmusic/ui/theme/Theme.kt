package com.kite.zmusic.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Page = Color(0xFFEEF4F7)
private val Ink = Color(0xFF2C2C2E)
private val Accent = Color(0xFFEC4141)

private val ZMusicLightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    background = Page,
    onBackground = Ink,
    surface = Page,
    onSurface = Ink,
)

@Composable
fun ZMusicTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
    MaterialTheme(
        colorScheme = ZMusicLightColors,
        content = content,
    )
}
