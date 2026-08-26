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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.AppAppearance
import com.kite.zmusic.plugin.PluginLookPresent
import kotlinx.coroutines.flow.MutableStateFlow

/** 不跟系统字体大小走；略小于设计 sp，避免界面发撑。 */
private const val AppFontScale = 0.8f

private fun zMusicColorScheme(dark: Boolean) =
    if (dark) {
        darkColorScheme(
            primary = MainPalette.Accent,
            onPrimary = TextTheme.ControlThumb,
            secondary = MainPalette.Secondary,
            onSecondary = TextTheme.ControlThumb,
            background = MainPalette.Page,
            onBackground = MainPalette.Ink,
            surface = MainPalette.Page,
            onSurface = MainPalette.Ink,
            surfaceVariant = MainPalette.Surface,
            onSurfaceVariant = MainPalette.Secondary,
            outline = MainPalette.Hairline,
        )
    } else {
        lightColorScheme(
            primary = MainPalette.Accent,
            onPrimary = TextTheme.ControlThumb,
            secondary = MainPalette.Secondary,
            onSecondary = TextTheme.ControlThumb,
            background = MainPalette.Page,
            onBackground = MainPalette.Ink,
            surface = MainPalette.Page,
            onSurface = MainPalette.Ink,
            surfaceVariant = MainPalette.Surface,
            onSurfaceVariant = MainPalette.Secondary,
            outline = MainPalette.Hairline,
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
    val overlayAppearance = PluginLookPresent.appearance()
    val dark = (overlayAppearance ?: appearance).resolveDark(isSystemInDarkTheme())
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
            colorScheme = zMusicColorScheme(MainPalette.isDark),
            content = content,
        )
    }
}
