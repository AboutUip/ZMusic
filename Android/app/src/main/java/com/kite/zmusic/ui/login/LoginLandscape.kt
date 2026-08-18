package com.kite.zmusic.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.R

private val Page = Color(0xFFFFFFFF)
private val PageSoft = Color(0xFFFAFAFA)
private val Ink = Color(0xFF333333)
private val InkSecondary = Color(0xFF888888)
private val CloudRed = Color(0xFFEC4141)
private val Hairline = Color(0xFFE6E6E6)

private enum class LandscapeStep {
    Landing,
    Sms,
    Qr,
    PhonePwd,
    Email,
}

private data class LandscapeRight(
    val register: Boolean,
    val step: LandscapeStep,
)

/**
 * 横屏登录：与竖屏同一套网易云视觉，左右分栏——左侧品牌，右侧落地或表单。
 */
@Composable
internal fun LoginLandscapeHost(
    vm: LoginViewModel,
    onMethod: (LoginMethod) -> Unit,
    onQrVisible: (Boolean) -> Unit,
    onLoggedIn: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenRegister: () -> Unit,
    registerOpen: Boolean,
    registerVm: RegisterViewModel,
    onCloseRegister: () -> Unit,
    onRegistered: () -> Unit,
    onNeedSmsLogin: (String) -> Unit,
    resumeSms: Boolean,
    onResumeSmsConsumed: () -> Unit,
    err: String?,
) {
    var step by remember { mutableStateOf(LandscapeStep.Landing) }
    var agreed by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var legalKind by remember { mutableStateOf<LoginLegalKind?>(null) }
    var smsCodeStage by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val wideQr = LocalConfiguration.current.screenWidthDp >= 840

    LoginLightSystemBars()

    DisposableEffect(step) {
        onQrVisible(step == LandscapeStep.Qr)
        onDispose { onQrVisible(false) }
    }

    fun go(next: LandscapeStep, method: LoginMethod) {
        onMethod(method)
        step = next
        if (next != LandscapeStep.Sms) smsCodeStage = false
    }

    fun guarded(block: () -> Unit) {
        if (agreed) {
            block()
        } else {
            pending = block
        }
    }

    BackHandler(enabled = !registerOpen) {
        when {
            err != null -> vm.dismissError()
            step != LandscapeStep.Landing -> {
                keyboard?.hide()
                step = LandscapeStep.Landing
                smsCodeStage = false
            }
            else -> onNavigateBack()
        }
    }

    LaunchedEffect(vm.smsCaptchaHint, vm.captchaCooldownSec) {
        smsCodeStage = vm.smsCaptchaHint.isNotEmpty() || vm.captchaCooldownSec > 0
    }

    LaunchedEffect(resumeSms) {
        if (!resumeSms) return@LaunchedEffect
        go(LandscapeStep.Sms, LoginMethod.Sms)
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
            Row(Modifier.fillMaxSize()) {
                LoginBrandRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.38f),
                    caption = if (registerOpen) {
                        "注册仅用于在 ZMusic 内登录，账号受网易云服务约束。"
                    } else {
                        "登录网易云账号，同步收藏与歌单"
                    },
                )
                Box(
                    Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .background(Page),
                ) {
                    val right = LandscapeRight(registerOpen, if (resumeSms) LandscapeStep.Sms else step)
                    AnimatedContent(
                        targetState = right,
                        transitionSpec = {
                            val forward = when {
                                targetState.register && !initialState.register -> true
                                !targetState.register && initialState.register -> false
                                else -> targetState.step.ordinal > initialState.step.ordinal
                            }
                            (slideInHorizontally(tween(280)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(220)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(160)))
                        },
                        label = "landscape_login_step",
                        modifier = Modifier.fillMaxSize(),
                    ) { current ->
                        if (current.register) {
                            RegisterFlowContent(
                                vm = registerVm,
                                onClose = onCloseRegister,
                                onLoggedIn = onRegistered,
                                onNeedSmsLogin = onNeedSmsLogin,
                            )
                        } else when (current.step) {
                            LandscapeStep.Landing -> LandscapeLandingPane(
                                onPhone = { guarded { go(LandscapeStep.Sms, LoginMethod.Sms) } },
                                onQr = { guarded { go(LandscapeStep.Qr, LoginMethod.Qr) } },
                                onPassword = { guarded { go(LandscapeStep.PhonePwd, LoginMethod.PhonePwd) } },
                                onEmail = { guarded { go(LandscapeStep.Email, LoginMethod.Email) } },
                                onRegister = { guarded { onOpenRegister() } },
                                agreed = agreed,
                                onToggleAgree = { agreed = !agreed },
                                onOpenTerms = { legalKind = LoginLegalKind.Terms },
                                onOpenPrivacy = { legalKind = LoginLegalKind.Privacy },
                            )
                            LandscapeStep.Sms -> LoginSmsPane(
                                vm = vm,
                                err = err,
                                codeStage = smsCodeStage,
                                onBack = {
                                    keyboard?.hide()
                                    step = LandscapeStep.Landing
                                    smsCodeStage = false
                                },
                                onSwitchPassword = { go(LandscapeStep.PhonePwd, LoginMethod.PhonePwd) },
                                onSendCode = { vm.sendCaptcha() },
                                onLogin = { vm.loginSms(onLoggedIn) },
                            )
                            LandscapeStep.Qr -> LoginQrPane(
                                vm = vm,
                                err = err,
                                onBack = { step = LandscapeStep.Landing },
                                wide = wideQr,
                            )
                            LandscapeStep.PhonePwd -> LoginPasswordPane(
                                vm = vm,
                                err = err,
                                onBack = { step = LandscapeStep.Landing },
                                onSwitchSms = { go(LandscapeStep.Sms, LoginMethod.Sms) },
                                onLogin = { vm.loginPhonePassword(onLoggedIn) },
                            )
                            LandscapeStep.Email -> LoginEmailPane(
                                vm = vm,
                                err = err,
                                onBack = { step = LandscapeStep.Landing },
                                onLogin = { vm.loginEmail(onLoggedIn) },
                            )
                        }
                    }
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
internal fun LoginBrandRail(
    modifier: Modifier = Modifier,
    caption: String = "登录网易云账号，同步收藏与歌单",
) {
    Box(
        modifier.background(PageSoft),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.dp)
                .background(Hairline),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_vinyl_z),
                contentDescription = "ZMusic",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "ZMusic",
                style = TextStyle(
                    color = Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "把世界调小一点  ·  把歌开大一点",
                style = TextStyle(
                    color = InkSecondary,
                    fontSize = 13.sp,
                    letterSpacing = 0.2.sp,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = caption,
                style = TextStyle(
                    color = InkSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

@Composable
private fun LandscapeLandingPane(
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
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "登录",
                style = TextStyle(
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "使用网易云账号继续",
                style = TextStyle(
                    color = InkSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(Modifier.height(22.dp))
            CloudPillButton(text = "手机号登录", onClick = onPhone)
            Spacer(Modifier.height(12.dp))
            CloudOutlinePillButton(text = "扫码登录", onClick = onQr)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LandscapeTextAction("密码登录", onClick = onPassword)
                Box(
                    Modifier
                        .padding(horizontal = 14.dp)
                        .width(1.dp)
                        .height(12.dp)
                        .background(Hairline),
                )
                LandscapeTextAction("邮箱登录", onClick = onEmail)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        .padding(horizontal = 4.dp, vertical = 6.dp),
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
                compact = true,
                centered = true,
            )
        }
    }
}

@Composable
private fun LandscapeTextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        style = TextStyle(
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}
