package com.kite.zmusic.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.AppAppearance
import kotlinx.coroutines.flow.MutableStateFlow

/** 不跟系统字体大小走；略小于设计 sp，避免界面发撑。 */
private const val AppFontScale = 0.8f

private fun zMusicColorScheme(colors: MainColors) =
    if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            secondary = colors.secondary,
            onSecondary = Color.White,
            background = colors.page,
            onBackground = colors.ink,
            surface = colors.page,
            onSurface = colors.ink,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.secondary,
            outline = colors.hairline,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            secondary = colors.secondary,
            onSecondary = Color.White,
            background = colors.page,
            onBackground = colors.ink,
            surface = colors.page,
            onSurface = colors.ink,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.secondary,
            outline = colors.hairline,
        )
    }

@Composable
fun ZMusicTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val appearanceFlow = remember(context) {
        (context.applicationContext as? ZMusicApplication)?.themeStore?.appearance
            ?: MutableStateFlow(AppAppearance.Light)
    }
    val appearance by appearanceFlow.collectAsState()
    val dark = appearance.resolveDark(isSystemInDarkTheme())
    val colors = if (dark) MainColors.Dark else MainColors.Light
    MainPalette.bind(colors)

    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = AppFontScale,
        ),
    ) {
        MaterialTheme(
            colorScheme = zMusicColorScheme(colors),
            content = content,
        )
    }
}
