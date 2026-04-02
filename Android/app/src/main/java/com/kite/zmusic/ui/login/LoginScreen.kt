package com.kite.zmusic.ui.login

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.ui.scifi.SciFiBackdrop
import com.kite.zmusic.ui.scifi.SciFiPanelFrame
import com.kite.zmusic.ui.scifi.SciFiHudChip
import com.kite.zmusic.ui.scifi.SciFiHudCompactButton
import com.kite.zmusic.ui.scifi.SciFiHudPrimaryButton
import com.kite.zmusic.ui.scifi.SciFiHudTextAction

private val Cyan = Color(0xFF00FFD1)
private val Dim = Color(0xFF8FA8B8)
private val Warn = Color(0xFFFFB86C)
private val TermGreen = Color(0xFF39FF9C)

private enum class LoginMethod {
    Qr,
    Sms,
    PhonePwd,
    Email,
    Guest,
}

/**
 * 多方式登录：二维码 / 短信验证码 / 手机密码 / 邮箱 / 游客；科幻终端风动效。
 */
@Composable
fun LoginScreen(
    sessionRepository: SessionRepository,
    onLoggedIn: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: LoginViewModel = viewModel(factory = LoginViewModelFactory(sessionRepository))
    val isBusy = vm.busy
    val err = vm.bannerError

    BackHandler { onNavigateBack() }
    BackHandler(enabled = err != null) { vm.dismissError() }
    var method by remember { mutableStateOf(LoginMethod.Qr) }
    val qrImg = vm.qrImageBase64
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier.fillMaxSize()) {
        SciFiBackdrop(Modifier.fillMaxSize())
        if (isLandscape) {
            LoginLandscapeBody(
                vm = vm,
                method = method,
                onMethod = { method = it },
                onLoggedIn = onLoggedIn,
                err = err,
            )
        } else {
            LoginPortraitBody(
                vm = vm,
                method = method,
                onMethod = { method = it },
                onLoggedIn = onLoggedIn,
                err = err,
            )
        }

        if (isBusy) {
            Box(
                Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Cyan.copy(alpha = 0.85f),
                    strokeWidth = 2.dp,
                )
            }
        }
    }

    LaunchedEffect(method) {
        if (method == LoginMethod.Qr && vm.qrImageBase64 == null) vm.loadQrSession()
    }

    LaunchedEffect(qrImg, method) {
        if (method == LoginMethod.Qr && qrImg != null) vm.runQrPolling(onLoggedIn)
    }
}

@Composable
private fun LoginHeader() {
    Text(
        text = "IDENT // 身份验证",
        style = TextStyle(
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 3.sp,
        ),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "连接网易云账号以同步收藏与歌单",
        style = TextStyle(
            color = Dim.copy(alpha = 0.9f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            lineHeight = 16.sp,
        ),
    )
}

@Composable
private fun LoginErrorBlock(err: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(err != null) {
        Column {
            Spacer(Modifier.height(12.dp))
            Text(
                text = err.orEmpty(),
                style = TextStyle(
                    color = Warn,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                ),
            )
            SciFiHudTextAction(text = "关闭", onClick = onDismiss)
        }
    }
}

@Composable
private fun LoginMethodPanels(
    vm: LoginViewModel,
    method: LoginMethod,
    onLoggedIn: () -> Unit,
    isLandscape: Boolean,
) {
    AnimatedContent(
        targetState = method,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(tween(320)) togetherWith fadeOut(tween(180)))
        },
        label = "login_method",
    ) { m ->
        when (m) {
            LoginMethod.Qr -> QrPanel(vm, onLoggedIn, isLandscape)
            LoginMethod.Sms -> SmsPanel(vm, onLoggedIn)
            LoginMethod.PhonePwd -> PhonePwdPanel(vm, onLoggedIn)
            LoginMethod.Email -> EmailPanel(vm, onLoggedIn)
            LoginMethod.Guest -> GuestPanel(vm, onLoggedIn)
        }
    }
}

@Composable
private fun LoginPortraitBody(
    vm: LoginViewModel,
    method: LoginMethod,
    onMethod: (LoginMethod) -> Unit,
    onLoggedIn: () -> Unit,
    err: String?,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        LoginHeader()
        Spacer(Modifier.height(20.dp))
        MethodTabs(selected = method, onSelect = onMethod)
        Spacer(Modifier.height(16.dp))
        LoginMethodPanels(vm, method, onLoggedIn, isLandscape = false)
        LoginErrorBlock(err, vm::dismissError)
    }
}

@Composable
private fun LoginLandscapeBody(
    vm: LoginViewModel,
    method: LoginMethod,
    onMethod: (LoginMethod) -> Unit,
    onLoggedIn: () -> Unit,
    err: String?,
) {
    Row(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 22.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier
                .weight(0.34f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LoginHeader()
            GameStyleMethodList(selected = method, onSelect = onMethod)
            LoginErrorBlock(err, vm::dismissError)
        }
        Box(
            Modifier
                .weight(0.66f)
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(start = 12.dp, end = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            SciFiPanelFrame(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LoginMethodPanels(vm, method, onLoggedIn, isLandscape = true)
                }
            }
        }
    }
}

/** 横屏左侧：游戏菜单式纯文字切换；留足边距与最小触控高度，避免贴边难点。 */
@Composable
private fun GameStyleMethodList(
    selected: LoginMethod,
    onSelect: (LoginMethod) -> Unit,
) {
    val methods = listOf(
        LoginMethod.Qr to "二维码",
        LoginMethod.Sms to "短信",
        LoginMethod.PhonePwd to "手机密码",
        LoginMethod.Email to "邮箱",
        LoginMethod.Guest to "游客",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "MODE // SELECT",
            modifier = Modifier.padding(start = 4.dp),
            style = TextStyle(
                color = Dim.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            ),
        )
        Spacer(Modifier.height(6.dp))
        methods.forEach { (m, label) ->
            val on = m == selected
            val interaction = remember(m) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(m) },
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = (if (on) "› " else "  ") + label,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    style = TextStyle(
                        color = if (on) Cyan else Dim.copy(alpha = 0.52f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (on) 14.sp else 13.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MethodTabs(
    selected: LoginMethod,
    onSelect: (LoginMethod) -> Unit,
) {
    val methods = listOf(
        LoginMethod.Qr to "二维码",
        LoginMethod.Sms to "短信",
        LoginMethod.PhonePwd to "手机密码",
        LoginMethod.Email to "邮箱",
        LoginMethod.Guest to "游客",
    )
    val row1 = methods.take(3)
    val row2 = methods.drop(3)
    val gap = 10.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            row1.forEach { (m, label) ->
                SciFiHudChip(
                    text = label,
                    selected = m == selected,
                    onClick = { onSelect(m) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    tabDense = true,
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row2.forEach { (m, label) ->
                    SciFiHudChip(
                        text = label,
                        selected = m == selected,
                        onClick = { onSelect(m) },
                        modifier = Modifier.widthIn(min = 118.dp),
                        tabDense = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun QrPanel(
    vm: LoginViewModel,
    onLoggedIn: () -> Unit,
    isLandscape: Boolean = false,
) {
    val b64 = vm.qrImageBase64
    val hint = vm.qrHint
    val qrSize = if (isLandscape) 168.dp else 200.dp
    val bmp = rememberQrBitmap(b64)
    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(qrSize).padding(6.dp),
                    )
                } else {
                    ScanPlaceholder(Modifier.size(qrSize))
                }
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = hint.ifEmpty { "正在准备二维码…" },
                    style = TextStyle(
                        color = Dim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    ),
                )
                SciFiHudPrimaryButton(
                    text = "刷新二维码",
                    enabled = !vm.busy,
                    onClick = { vm.loadQrSession() },
                )
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "登录二维码",
                    modifier = Modifier
                        .size(qrSize)
                        .padding(8.dp),
                )
            } else {
                ScanPlaceholder(Modifier.size(qrSize))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = hint.ifEmpty { "正在准备二维码…" },
                style = TextStyle(
                    color = Dim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SciFiHudPrimaryButton(
                text = "刷新二维码",
                enabled = !vm.busy,
                onClick = { vm.loadQrSession() },
            )
        }
    }
}

@Composable
private fun rememberQrBitmap(b64: String?): ImageBitmap? {
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

@Composable
private fun ScanPlaceholder(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "qr_ph")
    val a by t.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "QR // LOADING",
            style = TextStyle(
                color = Cyan.copy(alpha = 0.35f + 0.25f * a),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
            ),
        )
    }
}

@Composable
private fun SmsPanel(vm: LoginViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SciFiField(
            value = vm.phone,
            onChange = { n ->
                if (n != vm.phone) vm.onSmsPhoneChanged()
                vm.phone = n
            },
            label = "手机号",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SciFiField(
                value = vm.captcha,
                onChange = { vm.captcha = it },
                label = "验证码",
                modifier = Modifier.weight(1f),
            )
            val sendLabel = when {
                vm.captchaSending -> "发送中…"
                vm.captchaCooldownSec > 0 -> "${vm.captchaCooldownSec}s"
                else -> "发送"
            }
            val canSend = !vm.captchaSending && vm.captchaCooldownSec == 0
            SciFiHudCompactButton(
                text = sendLabel,
                enabled = canSend,
                onClick = { vm.sendCaptcha() },
                minWidth = 88.dp,
            )
        }
        if (vm.smsCaptchaHint.isNotEmpty()) {
            Text(
                text = vm.smsCaptchaHint,
                style = TextStyle(
                    color = TermGreen.copy(alpha = 0.88f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 0.4.sp,
                ),
            )
        }
        if (vm.captchaCooldownSec > 0) {
            Text(
                text = "防重复请求 · ${vm.captchaCooldownSec}s 后可再次获取验证码",
                style = TextStyle(
                    color = Dim.copy(alpha = 0.82f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                ),
            )
        }
        PrimaryAction(
            text = "短信登录",
            enabled = !vm.busy,
            onClick = { vm.loginSms(onDone) },
        )
    }
}

@Composable
private fun PhonePwdPanel(vm: LoginViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "密码登录可能触发风控，建议优先使用短信或二维码。",
            style = TextStyle(color = Dim.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp),
        )
        SciFiField(
            value = vm.phone,
            onChange = { n ->
                if (n != vm.phone) vm.onSmsPhoneChanged()
                vm.phone = n
            },
            label = "手机号",
        )
        SciFiField(
            vm.password,
            { vm.password = it },
            "密码",
            password = true,
        )
        PrimaryAction(
            text = "手机密码登录",
            enabled = !vm.busy,
            onClick = { vm.loginPhonePassword(onDone) },
        )
    }
}

@Composable
private fun EmailPanel(vm: LoginViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SciFiField(vm.email, { vm.email = it }, "网易邮箱")
        SciFiField(vm.emailPassword, { vm.emailPassword = it }, "密码", password = true)
        PrimaryAction(
            text = "邮箱登录",
            enabled = !vm.busy,
            onClick = { vm.loginEmail(onDone) },
        )
    }
}

@Composable
private fun GuestPanel(vm: LoginViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "游客身份功能受限，仅用于降低未登录接口报错。",
            style = TextStyle(color = Dim.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp),
        )
        PrimaryAction(
            text = "游客进入",
            enabled = !vm.busy,
            onClick = { vm.loginGuest(onDone) },
        )
    }
}

@Composable
private fun SciFiField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = TextStyle(
            color = Color.White.copy(alpha = 0.92f),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White.copy(alpha = 0.95f),
            unfocusedTextColor = Color.White.copy(alpha = 0.88f),
            focusedBorderColor = Cyan.copy(alpha = 0.75f),
            unfocusedBorderColor = Cyan.copy(alpha = 0.28f),
            cursorColor = Cyan,
            focusedLabelColor = Cyan.copy(alpha = 0.85f),
            unfocusedLabelColor = Dim,
        ),
    )
}

@Composable
private fun PrimaryAction(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SciFiHudPrimaryButton(text = text, enabled = enabled, onClick = onClick)
}
