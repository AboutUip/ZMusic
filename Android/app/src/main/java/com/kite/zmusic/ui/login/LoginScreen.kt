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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.platform.MusicPlatform
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.player.PlayerDisplayQr
import com.kite.zmusic.ui.theme.MainPalette
import com.kite.zmusic.i18n.t
import kotlinx.coroutines.delay

internal enum class LoginMethod {
    Qr,
    Sms,
    PhonePwd,
    Email,
}

internal class LoginQrExternal(
    val image: ImageBitmap?,
    val hint: String,
    val caption: String,
    val onRefresh: () -> Unit,
)

/**
 * 多方式登录：竖屏、横屏均对齐网易云落地页（白底、品牌红胶囊、协议后下钻）。
 */
@Composable
fun LoginScreen(
    sessionRepository: SessionRepository,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val platform by app.musicPlatformStore.currentFlow.collectAsStateWithLifecycle()
    val vm: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(sessionRepository, app.ncmAuthClient),
    )
    val registerVm: RegisterViewModel = viewModel(
        factory = RegisterViewModelFactory(sessionRepository, app.ncmAuthClient),
    )
    val context = LocalContext.current
    val isBusy = vm.busy
    val err = vm.bannerError

    var method by remember { mutableStateOf(LoginMethod.Qr) }
    var qrVisible by remember { mutableStateOf(false) }
    var registerOpen by remember { mutableStateOf(false) }
    var resumeSms by remember { mutableStateOf(false) }
    val qrImg = vm.qrImageBase64
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val qrActive = method == LoginMethod.Qr && qrVisible
    val qishuiLogin = platform == MusicPlatform.QISHUI
    var qishuiImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var qishuiHint by remember { mutableStateOf("") }
    var qishuiRefresh by remember { mutableIntStateOf(0) }
    val qishuiQr = if (!qishuiLogin) {
        null
    } else {
        LoginQrExternal(
            image = qishuiImage,
            hint = qishuiHint,
            caption = t("请用汽水音乐扫码"),
            onRefresh = { qishuiRefresh += 1 },
        )
    }

    fun selectPlatform(next: MusicPlatform) {
        if (next == platform) return
        app.musicPlatformStore.set(next)
        if (!next.reloginOnSwitch) onLoggedIn()
    }

    fun continueSmsAfterRegister(phone: String) {
        vm.prepareSmsAfterRegister(phone)
        registerOpen = false
        registerVm.reset()
        method = LoginMethod.Sms
        resumeSms = true
        context.showIslandNotice(t("注册成功，请用验证码登录"))
    }

    Box(modifier.fillMaxSize()) {
        if (isLandscape) {
            LoginLandscapeHost(
                vm = vm,
                onMethod = { method = it },
                onQrVisible = { qrVisible = it },
                onLoggedIn = onLoggedIn,
                onOpenRegister = { registerOpen = true; registerVm.reset() },
                registerOpen = registerOpen,
                registerVm = registerVm,
                onCloseRegister = {
                    registerOpen = false
                    registerVm.reset()
                },
                onRegistered = onLoggedIn,
                onNeedSmsLogin = ::continueSmsAfterRegister,
                resumeSms = resumeSms,
                onResumeSmsConsumed = { resumeSms = false },
                err = err,
                platform = platform,
                onSelectPlatform = ::selectPlatform,
                qrExternal = qishuiQr,
            )
        } else {
            LoginPortraitHost(
                vm = vm,
                onMethod = { method = it },
                onQrVisible = { qrVisible = it },
                onLoggedIn = onLoggedIn,
                onOpenRegister = { registerOpen = true; registerVm.reset() },
                resumeSms = resumeSms,
                onResumeSmsConsumed = { resumeSms = false },
                err = err,
                platform = platform,
                onSelectPlatform = ::selectPlatform,
                qrExternal = qishuiQr,
            )
            RegisterOverlay(
                visible = registerOpen,
                vm = registerVm,
                onClose = {
                    registerOpen = false
                    registerVm.reset()
                },
                onLoggedIn = onLoggedIn,
                onNeedSmsLogin = ::continueSmsAfterRegister,
            )
        }

        if (isBusy && !qrActive && !vm.qrRescue) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MainPalette.Accent,
                    strokeWidth = 2.dp,
                )
            }
        }

        if (vm.qrRescue) {
            NeteaseLoginQrOverlay(
                loginUrl = vm.qrLoginUrl,
                hint = vm.qrHint,
                onRefresh = { vm.loadQrSession() },
                onDismiss = { vm.dismissQrRescue() },
            )
        }
    }

    LaunchedEffect(registerVm.wantsQrFallback) {
        if (registerVm.wantsQrFallback) {
            registerVm.consumeQrFallback()
            registerOpen = false
            vm.beginQrFallback()
        }
    }

    LaunchedEffect(qrActive, platform) {
        if (qrActive && platform == MusicPlatform.NETEASE && vm.qrImageBase64 == null) vm.loadQrSession()
    }

    LaunchedEffect(qrImg, qrActive, platform, vm.qrRescue, vm.qrLoginUrl) {
        if (platform != MusicPlatform.NETEASE) return@LaunchedEffect
        if ((qrActive || vm.qrRescue) && (qrImg != null || vm.qrLoginUrl.isNotEmpty())) {
            vm.runQrPolling(onLoggedIn)
        }
    }

    LaunchedEffect(qrActive, qishuiLogin, qishuiRefresh) {
        if (!qrActive || !qishuiLogin) return@LaunchedEffect
        qishuiImage = null
        qishuiHint = t("正在获取二维码")
        val next = runCatching { app.qishuiCatalog.startQr() }.getOrElse {
            qishuiHint = it.message ?: t("二维码请求失败")
            return@LaunchedEffect
        }
        qishuiImage = PlayerDisplayQr.encodeBitmap(next.scanUrl, 720).asImageBitmap()
        qishuiHint = t("请用汽水音乐扫码")
        val deadline = System.currentTimeMillis() + 120_000
        var waitMs = 3_000L
        while (System.currentTimeMillis() < deadline) {
            delay(waitMs)
            val poll = runCatching { app.qishuiCatalog.pollQr(next) }.getOrNull() ?: continue
            val cookie = poll.sessionCookie
            if (!cookie.isNullOrBlank()) {
                app.qishuiSessionStore.persist(cookie, t("汽水音乐"))
                onLoggedIn()
                return@LaunchedEffect
            }
            val status = poll.status
            when {
                status.equals("scanned", ignoreCase = true) || status == "2" -> {
                    qishuiHint = t("已扫码，请在汽水音乐中确认")
                    waitMs = 3_000L
                }
                status.contains("expire", ignoreCase = true) ||
                    status.equals("refused", ignoreCase = true) ||
                    status == "4" || status == "5" -> break
                poll.errorCode == 7 -> {
                    qishuiHint = poll.description.ifBlank { t("访问太频繁，请稍后再试") }
                    waitMs = 8_000L
                }
                poll.errorCode != 0 -> {
                    qishuiHint = poll.description.ifBlank { t("二维码请求失败") }
                    if (poll.errorCode == 1049) return@LaunchedEffect
                    waitMs = 3_000L
                }
                else -> waitMs = 3_000L
            }
        }
        qishuiImage = null
        qishuiHint = t("二维码已失效")
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
