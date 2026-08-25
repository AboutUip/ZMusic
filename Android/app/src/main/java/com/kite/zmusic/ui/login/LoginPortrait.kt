package com.kite.zmusic.ui.login

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kite.zmusic.R
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.theme.MainPalette

/** 登录主色；跟随 [MainPalette.Accent]。 */
private val CloudRed get() = MainPalette.Accent
private val CloudRedPressed get() = Color(
    red = (MainPalette.Accent.red * 0.84f).coerceIn(0f, 1f),
    green = (MainPalette.Accent.green * 0.83f).coerceIn(0f, 1f),
    blue = (MainPalette.Accent.blue * 0.83f).coerceIn(0f, 1f),
    alpha = 1f,
)
private val CloudRedDisabled get() = MainPalette.Accent.copy(alpha = 0.42f)
private val Ink get() = MainPalette.Ink
private val InkSecondary get() = MainPalette.Secondary
private val InkHint get() = MainPalette.Hint
private val Hairline get() = MainPalette.Hairline
private val Page get() = MainPalette.Surface
private val PageSoft get() = MainPalette.Page
private val Danger = Color(0xFFE23D3D)

internal val LoginPhoneRegex = Regex("^1[3-9]\\d{9}$")

private enum class PortraitStep {
    Landing,
    Sms,
    Qr,
    PhonePwd,
    Email,
}

/**
 * 竖屏登录：对齐网易云近年落地页结构——白底、品牌红胶囊、Logo 居中、主按钮下钻、底栏次要方式。
 * 不做运营商一键登录（无号码认证 SDK）；主 CTA 改为手机号验证码。
 */
@Composable
internal fun LoginPortraitHost(
    vm: LoginViewModel,
    onMethod: (LoginMethod) -> Unit,
    onQrVisible: (Boolean) -> Unit,
    onLoggedIn: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenRegister: () -> Unit,
    resumeSms: Boolean,
    onResumeSmsConsumed: () -> Unit,
    err: String?,
) {
    var step by remember { mutableStateOf(PortraitStep.Landing) }
    var agreed by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var legalKind by remember { mutableStateOf<LoginLegalKind?>(null) }
    var smsCodeStage by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    LoginLightSystemBars()

    DisposableEffect(step) {
        onQrVisible(step == PortraitStep.Qr)
        onDispose { onQrVisible(false) }
    }

    fun go(next: PortraitStep, method: LoginMethod) {
        onMethod(method)
        step = next
        if (next != PortraitStep.Sms) smsCodeStage = false
    }

    fun guarded(block: () -> Unit) {
        if (agreed) {
            block()
        } else {
            pending = block
        }
    }

    BackHandler(enabled = err != null || step != PortraitStep.Landing) {
        when {
            err != null -> vm.dismissError()
            else -> {
                keyboard?.hide()
                step = PortraitStep.Landing
                smsCodeStage = false
            }
        }
    }

    LaunchedEffect(vm.smsCaptchaHint, vm.captchaCooldownSec) {
        smsCodeStage = vm.smsCaptchaHint.isNotEmpty() || vm.captchaCooldownSec > 0
    }

    LaunchedEffect(resumeSms) {
        if (!resumeSms) return@LaunchedEffect
        go(PortraitStep.Sms, LoginMethod.Sms)
        onResumeSmsConsumed()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Page),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(tween(280)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(180)))
                },
                label = "portrait_login_step",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                when (current) {
                    PortraitStep.Landing -> LandingPane(
                        onPhone = { guarded { go(PortraitStep.Sms, LoginMethod.Sms) } },
                        onQr = { guarded { go(PortraitStep.Qr, LoginMethod.Qr) } },
                        onPassword = { guarded { go(PortraitStep.PhonePwd, LoginMethod.PhonePwd) } },
                        onEmail = { guarded { go(PortraitStep.Email, LoginMethod.Email) } },
                        onRegister = { guarded { onOpenRegister() } },
                        agreed = agreed,
                        onToggleAgree = { agreed = !agreed },
                        onOpenTerms = { legalKind = LoginLegalKind.Terms },
                        onOpenPrivacy = { legalKind = LoginLegalKind.Privacy },
                    )
                    PortraitStep.Sms -> LoginSmsPane(
                        vm = vm,
                        err = err,
                        codeStage = smsCodeStage,
                        onBack = {
                            keyboard?.hide()
                            step = PortraitStep.Landing
                            smsCodeStage = false
                        },
                        onSwitchPassword = { go(PortraitStep.PhonePwd, LoginMethod.PhonePwd) },
                        onSendCode = { vm.sendCaptcha() },
                        onLogin = { vm.loginSms(onLoggedIn) },
                    )
                    PortraitStep.Qr -> LoginQrPane(
                        vm = vm,
                        err = err,
                        onBack = { step = PortraitStep.Landing },
                    )
                    PortraitStep.PhonePwd -> LoginPasswordPane(
                        vm = vm,
                        err = err,
                        onBack = { step = PortraitStep.Landing },
                        onSwitchSms = { go(PortraitStep.Sms, LoginMethod.Sms) },
                        onLogin = { vm.loginPhonePassword(onLoggedIn) },
                    )
                    PortraitStep.Email -> LoginEmailPane(
                        vm = vm,
                        err = err,
                        onBack = { step = PortraitStep.Landing },
                        onLogin = { vm.loginEmail(onLoggedIn) },
                    )
                }
            }

            var lastPending by remember { mutableStateOf<(() -> Unit)?>(null) }
            if (pending != null) lastPending = pending
            AnimatedVisibility(
                visible = pending != null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
            ) {
                lastPending?.let { action ->
                    LoginAgreeFirstDialog(
                        onDismiss = { pending = null },
                        onAgree = {
                            agreed = true
                            pending = null
                            action()
                        },
                        onOpenTerms = { legalKind = LoginLegalKind.Terms },
                        onOpenPrivacy = { legalKind = LoginLegalKind.Privacy },
                    )
                }
            }
        }

        LoginLegalOverlay(
            kind = legalKind,
            onDismiss = { legalKind = null },
        )
    }
}

@Composable
internal fun LoginLightSystemBars() {
    val view = LocalView.current
    val light = !MainPalette.isDark
    DisposableEffect(view, light) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = controller?.isAppearanceLightStatusBars
        val prevNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = light
        controller?.isAppearanceLightNavigationBars = light
        onDispose {
            if (prevStatus != null) controller.isAppearanceLightStatusBars = prevStatus
            if (prevNav != null) controller.isAppearanceLightNavigationBars = prevNav
        }
    }
}

@Composable
private fun LandingPane(
    onPhone: () -> Unit,
    onQr: () -> Unit,
    onPassword: () -> Unit,
    onEmail: () -> Unit,
    onRegister: () -> Unit,
    agreed: Boolean,
    onToggleAgree: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_vinyl_z),
                contentDescription = "ZMusic",
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "ZMusic",
                style = TextStyle(
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "把世界调小一点  ·  把歌开大一点",
                style = TextStyle(
                    color = InkSecondary,
                    fontSize = 13.sp,
                    letterSpacing = 0.2.sp,
                ),
            )
        }

        Spacer(Modifier.weight(1f))

        CloudPillButton(
            text = "手机号登录",
            onClick = onPhone,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onQr,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "扫码登录",
                style = TextStyle(
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Spacer(Modifier.height(28.dp))
        OtherMethodsRow(onPassword = onPassword, onEmail = onEmail)

        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "没有账号？",
                style = TextStyle(color = InkSecondary, fontSize = 13.sp),
            )
            Text(
                text = "注册",
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRegister,
                    )
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                style = TextStyle(
                    color = CloudRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        LoginAgreementRow(
            agreed = agreed,
            onToggle = onToggleAgree,
            onOpenTerms = onOpenTerms,
            onOpenPrivacy = onOpenPrivacy,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun LoginSmsPane(
    vm: LoginViewModel,
    err: String?,
    codeStage: Boolean,
    onBack: () -> Unit,
    onSwitchPassword: () -> Unit,
    onSendCode: () -> Unit,
    onLogin: () -> Unit,
) {
    val phoneOk = LoginPhoneRegex.matches(vm.phone.trim())
    Column(Modifier.fillMaxSize()) {
        LoginDrillTopBar(
            title = "手机号登录",
            onBack = onBack,
            trailing = { TextLink("密码登录", onClick = onSwitchPassword) },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            LoginUnderlineField(
                value = vm.phone,
                onValueChange = { next ->
                    val filtered = next.filter { it.isDigit() }.take(11)
                    if (filtered != vm.phone) vm.onSmsPhoneChanged()
                    vm.phone = filtered
                },
                hint = "请输入手机号",
                leading = {
                    Text(
                        text = "+86",
                        style = TextStyle(
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 12.dp)
                            .width(1.dp)
                            .height(16.dp)
                            .background(Hairline),
                    )
                },
                keyboardType = KeyboardType.Phone,
                imeAction = if (codeStage) ImeAction.Next else ImeAction.Done,
            )
            AnimatedVisibility(visible = codeStage) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    LoginUnderlineField(
                        value = vm.captcha,
                        onValueChange = { vm.captcha = it.filter { ch -> ch.isDigit() }.take(8) },
                        hint = "请输入验证码",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                        onIme = onLogin,
                        trailing = {
                            val label = when {
                                vm.captchaSending -> "发送中"
                                vm.captchaCooldownSec > 0 -> "${vm.captchaCooldownSec}s"
                                else -> "重获验证码"
                            }
                            val can = !vm.captchaSending && vm.captchaCooldownSec == 0 && phoneOk
                            Text(
                                text = label,
                                modifier = Modifier
                                    .clickable(enabled = can, onClick = onSendCode)
                                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                                style = TextStyle(
                                    color = if (can) CloudRed else InkHint,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        },
                    )
                }
            }
            LoginErrorLine(err, vm::dismissError)
            Spacer(Modifier.height(32.dp))
            if (!codeStage) {
                CloudPillButton(
                    text = if (vm.captchaSending) "发送中…" else "下一步",
                    enabled = phoneOk && !vm.captchaSending,
                    onClick = onSendCode,
                )
            } else {
                CloudPillButton(
                    text = "登录",
                    enabled = phoneOk && vm.captcha.isNotBlank() && !vm.busy,
                    onClick = onLogin,
                )
            }
            if (codeStage && vm.phone.length == 11) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "验证码已发送至 ${maskPhone(vm.phone)}",
                    style = TextStyle(
                        color = InkHint,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun LoginQrPane(
    vm: LoginViewModel,
    err: String?,
    onBack: () -> Unit,
    wide: Boolean = false,
) {
    val b64 = vm.qrImageBase64
    val hint = vm.qrHint
    val expired = hint.contains("过期")
    val bmp = rememberQrBitmap(b64)
    val qrCard = @Composable {
        Box(
            Modifier
                .size(if (wide) 200.dp else 220.dp)
                .background(PageSoft, RoundedCornerShape(12.dp))
                .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                .clickable(enabled = expired || bmp == null, onClick = { vm.loadQrSession() }),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null && !expired) {
                Image(
                    bitmap = bmp,
                    contentDescription = "登录二维码",
                    modifier = Modifier.size(if (wide) 176.dp else 196.dp),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (expired) "二维码已失效" else "二维码加载中",
                        style = TextStyle(color = InkSecondary, fontSize = 14.sp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "点击刷新",
                        style = TextStyle(
                            color = CloudRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
    val qrCopy = @Composable {
        Column(horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally) {
            Text(
                text = "打开网易云音乐 App 扫一扫登录",
                style = TextStyle(
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint.ifEmpty { "等待扫描…" },
                style = TextStyle(color = InkSecondary, fontSize = 13.sp),
            )
            LoginErrorLine(err, vm::dismissError)
            Spacer(Modifier.height(20.dp))
            TextLink("刷新二维码") { vm.loadQrSession() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        LoginDrillTopBar(title = "扫码登录", onBack = onBack)
        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                qrCard()
                Box(Modifier.weight(1f)) { qrCopy() }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                qrCard()
                Spacer(Modifier.height(20.dp))
                qrCopy()
            }
        }
    }
}

@Composable
internal fun LoginPasswordPane(
    vm: LoginViewModel,
    err: String?,
    onBack: () -> Unit,
    onSwitchSms: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LoginDrillTopBar(
            title = "密码登录",
            onBack = onBack,
            trailing = { TextLink("验证码登录", onClick = onSwitchSms) },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            LoginUnderlineField(
                value = vm.phone,
                onValueChange = { next ->
                    val filtered = next.filter { it.isDigit() }.take(11)
                    if (filtered != vm.phone) vm.onSmsPhoneChanged()
                    vm.phone = filtered
                },
                hint = "请输入手机号",
                leading = {
                    Text("+86", style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium))
                    Box(
                        Modifier
                            .padding(horizontal = 12.dp)
                            .width(1.dp)
                            .height(16.dp)
                            .background(Hairline),
                    )
                },
                keyboardType = KeyboardType.Phone,
            )
            Spacer(Modifier.height(8.dp))
            LoginUnderlineField(
                value = vm.password,
                onValueChange = { vm.password = it },
                hint = "请输入密码",
                password = true,
                imeAction = ImeAction.Done,
                onIme = onLogin,
            )
            LoginErrorLine(err, vm::dismissError)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "密码登录可能触发风控，建议优先使用验证码或扫码。",
                style = TextStyle(color = InkHint, fontSize = 12.sp, lineHeight = 18.sp),
            )
            Spacer(Modifier.height(28.dp))
            CloudPillButton(
                text = "登录",
                enabled = vm.phone.isNotBlank() && vm.password.isNotBlank() && !vm.busy,
                onClick = onLogin,
            )
        }
    }
}

@Composable
internal fun LoginEmailPane(
    vm: LoginViewModel,
    err: String?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LoginDrillTopBar(title = "邮箱登录", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            LoginUnderlineField(
                value = vm.email,
                onValueChange = { vm.email = it },
                hint = "请输入网易邮箱",
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(8.dp))
            LoginUnderlineField(
                value = vm.emailPassword,
                onValueChange = { vm.emailPassword = it },
                hint = "请输入密码",
                password = true,
                imeAction = ImeAction.Done,
                onIme = onLogin,
            )
            LoginErrorLine(err, vm::dismissError)
            Spacer(Modifier.height(32.dp))
            CloudPillButton(
                text = "登录",
                enabled = vm.email.isNotBlank() && vm.emailPassword.isNotBlank() && !vm.busy,
                onClick = onLogin,
            )
        }
    }
}

@Composable
internal fun LoginDrillTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .semantics { role = Role.Button }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val stroke = 2.2.dp.toPx()
                drawLine(
                    color = Ink,
                    start = Offset(size.width * 0.62f, size.height * 0.18f),
                    end = Offset(size.width * 0.28f, size.height * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Ink,
                    start = Offset(size.width * 0.28f, size.height * 0.5f),
                    end = Offset(size.width * 0.62f, size.height * 0.82f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Box(Modifier.padding(end = 12.dp)) { trailing() }
    }
}

@Composable
internal fun OtherMethodsRow(
    onPassword: () -> Unit,
    onEmail: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Hairline),
            )
            Text(
                text = "其他登录方式",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = TextStyle(color = InkHint, fontSize = 12.sp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Hairline),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            RoundMethod(label = "密码", onClick = onPassword) { LockGlyph() }
            RoundMethod(label = "邮箱", onClick = onEmail) { EnvelopeGlyph() }
        }
    }
}

@Composable
private fun RoundMethod(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .border(1.dp, Hairline, CircleShape)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = CloudRed.copy(alpha = 0.16f)),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(8.dp))
        Text(label, style = TextStyle(color = InkSecondary, fontSize = 12.sp))
    }
}

@Composable
internal fun LoginAgreeFirstDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    GlassAlertDialog(
        title = "请阅读并同意以下条款",
        confirmLabel = "同意并继续",
        onConfirm = onAgree,
        onDismiss = onDismiss,
        extraContent = {
            LoginLegalNameLinks(
                onOpenTerms = onOpenTerms,
                onOpenPrivacy = onOpenPrivacy,
            )
        },
    )
}

@Composable
internal fun CloudPillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = tween(90),
        label = "pill_scale",
    )
    val bg = when {
        !enabled -> CloudRedDisabled
        pressed -> CloudRedPressed
        else -> CloudRed
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .semantics { role = Role.Button }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
internal fun CloudOutlinePillButton(
    text: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(90),
        label = "outline_pill_scale",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(if (pressed) CloudRed.copy(alpha = 0.06f) else Color.Transparent)
            .border(1.dp, CloudRed, RoundedCornerShape(24.dp))
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = CloudRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
internal fun LoginUnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onIme: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val line = if (focused) CloudRed else Hairline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = TextStyle(color = Ink, fontSize = 16.sp),
        cursorBrush = SolidColor(CloudRed),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onIme?.invoke() }),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = line,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = if (focused) 1.6.dp.toPx() else 1.dp.toPx(),
                        )
                    }
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(hint, style = TextStyle(color = InkHint, fontSize = 16.sp))
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
internal fun LoginErrorLine(err: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = !err.isNullOrBlank()) {
        Text(
            text = err.orEmpty(),
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable(onClick = onDismiss),
            style = TextStyle(color = Danger, fontSize = 12.sp, lineHeight = 18.sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 12.dp),
        style = TextStyle(color = InkSecondary, fontSize = 14.sp),
    )
}

@Composable
private fun LockGlyph() {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = InkSecondary,
            topLeft = Offset(w * 0.22f, h * 0.42f),
            size = Size(w * 0.56f, h * 0.46f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(stroke),
        )
        drawArc(
            color = InkSecondary,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.30f, h * 0.10f),
            size = Size(w * 0.40f, h * 0.42f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun EnvelopeGlyph() {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.5.dp.toPx()
        val inset = 2.dp.toPx()
        drawRoundRect(
            color = InkSecondary,
            topLeft = Offset(inset, inset + 2.dp.toPx()),
            size = Size(size.width - inset * 2, size.height - inset * 2 - 2.dp.toPx()),
            cornerRadius = CornerRadius(1.6.dp.toPx()),
            style = Stroke(stroke),
        )
        val path = Path().apply {
            moveTo(inset, inset + 2.dp.toPx())
            lineTo(size.width / 2f, size.height * 0.58f)
            lineTo(size.width - inset, inset + 2.dp.toPx())
        }
        drawPath(path, InkSecondary, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

private fun maskPhone(phone: String): String {
    val p = phone.trim()
    if (p.length < 7) return p
    return p.take(3) + "****" + p.takeLast(4)
}
