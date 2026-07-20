package com.kite.zmusic.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kite.zmusic.data.ServerConfigRepository
import com.kite.zmusic.ui.login.LoginScreen
import com.kite.zmusic.ui.main.MainPlaceholderScreen
import com.kite.zmusic.ui.server.ServerBootGate
import com.kite.zmusic.ui.server.ServerConfigScreen
import com.kite.zmusic.ui.splash.SplashScreen
import com.kite.zmusic.ZMusicApplication

private object Routes {
    const val Splash = "splash"
    const val ServerBoot = "server_boot"
    const val ServerConfig = "server_config"
    const val MainPlaceholder = "main_placeholder"
    const val Login = "login"
}

@Composable
fun ZMusicNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val app = appContext as ZMusicApplication
    val sessionRepository = remember { app.sessionRepository }
    val serverConfigRepository = remember { ServerConfigRepository(appContext) }

    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
        modifier = modifier,
    ) {
        composable(Routes.Splash) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.ServerBoot) {
                        popUpTo(Routes.Splash) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.ServerBoot) {
            ServerBootGate(
                serverConfigRepository = serverConfigRepository,
                onReady = {
                    navController.navigate(Routes.MainPlaceholder) {
                        popUpTo(Routes.ServerBoot) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNeedConfig = {
                    navController.navigate(Routes.ServerConfig) {
                        popUpTo(Routes.ServerBoot) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Routes.ServerConfig,
            enterTransition = {
                fadeIn(animationSpec = tween(380)) + slideInVertically(
                    animationSpec = tween(380),
                    initialOffsetY = { it / 12 },
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(280)) + slideOutVertically(
                    animationSpec = tween(280),
                    targetOffsetY = { it / 16 },
                )
            },
        ) {
            ServerConfigScreen(
                serverConfigRepository = serverConfigRepository,
                onConfigured = {
                    navController.navigate(Routes.MainPlaceholder) {
                        popUpTo(Routes.ServerConfig) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Routes.MainPlaceholder,
            enterTransition = {
                fadeIn(animationSpec = tween(420)) + slideInVertically(
                    animationSpec = tween(420),
                    initialOffsetY = { it / 10 },
                )
            },
        ) {
            MainPlaceholderScreen(
                sessionRepository = sessionRepository,
                onRequireLogin = {
                    navController.navigate(Routes.Login) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Routes.Login,
            enterTransition = {
                fadeIn(animationSpec = tween(380)) + slideInVertically(
                    animationSpec = tween(380),
                    initialOffsetY = { it / 12 },
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(280)) + slideOutVertically(
                    animationSpec = tween(280),
                    targetOffsetY = { it / 16 },
                )
            },
        ) {
            LoginScreen(
                sessionRepository = sessionRepository,
                onLoggedIn = {
                    navController.navigate(Routes.MainPlaceholder) {
                        popUpTo(Routes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
