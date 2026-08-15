package com.kite.zmusic.ui.login

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.data.SessionRepository

internal enum class LoginMethod {
    Qr,
    Sms,
    PhonePwd,
    Email,
}

/**
 * 多方式登录：竖屏、横屏均对齐网易云落地页（白底、品牌红胶囊、协议后下钻）。
 */
@Composable
fun LoginScreen(
    sessionRepository: SessionRepository,
    onLoggedIn: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: LoginViewModel = viewModel(factory = LoginViewModelFactory(sessionRepository))
    val registerVm: RegisterViewModel = viewModel(factory = RegisterViewModelFactory(sessionRepository))
    val isBusy = vm.busy
    val err = vm.bannerError

    var method by remember { mutableStateOf(LoginMethod.Qr) }
    var qrVisible by remember { mutableStateOf(false) }
    var registerOpen by remember { mutableStateOf(false) }
    val qrImg = vm.qrImageBase64
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val qrActive = method == LoginMethod.Qr && qrVisible

    Box(modifier.fillMaxSize()) {
        if (isLandscape) {
            LoginLandscapeHost(
                vm = vm,
                onMethod = { method = it },
                onQrVisible = { qrVisible = it },
                onLoggedIn = onLoggedIn,
                onNavigateBack = onNavigateBack,
                onOpenRegister = { registerOpen = true; registerVm.reset() },
                registerOpen = registerOpen,
                registerVm = registerVm,
                onCloseRegister = {
                    registerOpen = false
                    registerVm.reset()
                },
                onRegistered = onLoggedIn,
                err = err,
            )
        } else {
            LoginPortraitHost(
                vm = vm,
                onMethod = { method = it },
                onQrVisible = { qrVisible = it },
                onLoggedIn = onLoggedIn,
                onNavigateBack = onNavigateBack,
                onOpenRegister = { registerOpen = true; registerVm.reset() },
                err = err,
            )
            RegisterOverlay(
                visible = registerOpen,
                vm = registerVm,
                onClose = {
                    registerOpen = false
                    registerVm.reset()
                },
                onRegistered = onLoggedIn,
            )
        }

        if (isBusy && !qrActive) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Color(0xFFEC4141),
                    strokeWidth = 2.dp,
                )
            }
        }
    }

    LaunchedEffect(qrActive) {
        if (qrActive && vm.qrImageBase64 == null) vm.loadQrSession()
    }

    LaunchedEffect(qrImg, qrActive) {
        if (qrActive && qrImg != null) vm.runQrPolling(onLoggedIn)
    }
}

@Composable
internal fun rememberQrBitmap(b64: String?): ImageBitmap? {
    return remember(b64) {
        if (b64.isNullOrBlank()) return@remember null
        try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
