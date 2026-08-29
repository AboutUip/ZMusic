package com.kite.zmusic

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kite.zmusic.config.NcmApiConfig
import com.kite.zmusic.data.AppAppearance
import com.kite.zmusic.ui.login.LoginScreen
import com.kite.zmusic.ui.main.MainShell
import com.kite.zmusic.ui.main.SplashScreen
import com.kite.zmusic.ui.theme.MainColors
import com.kite.zmusic.ui.theme.MainPalette
import java.awt.Dimension

fun main(args: Array<String>) {
    if (args.contains("--version")) {
        println(NcmApiConfig.PRODUCT_VERSION)
        return
    }
    if (args.contains("--smoke")) {
        println("ok")
        return
    }
    val app = runCatching { AppContainer() }.getOrElse { err ->
        System.err.println("ZMusic failed to start: ${err.message}")
        err.printStackTrace()
        throw err
    }
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        System.err.println("ZMusic crash: ${e.message}")
        e.printStackTrace()
    }
    MainPalette.bind(
        if (app.prefs.current().appearance == AppAppearance.Dark) MainColors.Dark else MainColors.Light,
    )
    application {
        val state = rememberWindowState(width = 1280.dp, height = 800.dp)
        Window(
            onCloseRequest = {
                app.coordinator.close()
                exitApplication()
            },
            title = "ZMusic",
            state = state,
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(1100, 700)
            }
            MaterialTheme {
            var phase by remember { mutableStateOf("splash") }
            when (phase) {
                "splash" -> SplashScreen(app) { loggedIn ->
                    phase = if (loggedIn) "main" else "login"
                }
                "login" -> LoginScreen(
                    auth = app.auth,
                    sessions = app.sessions,
                    notices = app.notices,
                    onLoggedIn = { phase = "main" },
                )
                else -> MainShell(app, onLogout = { phase = "login" })
            }
            }
        }
    }
}
