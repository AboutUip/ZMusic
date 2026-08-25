package com.kite.zmusic.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.ui.theme.MainPalette

private enum class RegisterStep {
    Phone,
    Captcha,
    Profile,
}

private val Page get() = MainPalette.Surface
private val Ink get() = MainPalette.Ink
private val InkSecondary get() = MainPalette.Secondary
private val InkHint get() = MainPalette.Hint
private val CloudRed get() = MainPalette.Accent

/**
 * 竖屏：整页滑入注册。横屏请把 [RegisterFlowContent] 放进右侧栏，避免带动左侧品牌区。
 */
@Composable
internal fun RegisterOverlay(
    visible: Boolean,
    vm: RegisterViewModel,
    onClose: () -> Unit,
    onLoggedIn: () -> Unit,
    onNeedSmsLogin: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(3f),
        enter = slideInHorizontally(
            animationSpec = tween(340, easing = FastOutSlowInEasing),
            initialOffsetX = { it },
        ) + fadeIn(tween(220)),
        exit = slideOutHorizontally(
            animationSpec = tween(280, easing = FastOutLinearInEasing),
            targetOffsetX = { it },
        ) + fadeOut(tween(180)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Page)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            RegisterFlowContent(
                vm = vm,
                onClose = onClose,
                onLoggedIn = onLoggedIn,
                onNeedSmsLogin = onNeedSmsLogin,
            )
        }
    }
}

@Composable
internal fun RegisterFlowContent(
    vm: RegisterViewModel,
    onClose: () -> Unit,
    onLoggedIn: () -> Unit,
    onNeedSmsLogin: (String) -> Unit,
) {
    var step by remember { mutableStateOf(RegisterStep.Phone) }

    LaunchedEffect(Unit) {
        step = RegisterStep.Phone
    }

    BackHandler {
        when {
            vm.bannerError != null -> vm.dismissError()
            step == RegisterStep.Profile -> step = RegisterStep.Captcha
            step == RegisterStep.Captcha -> step = RegisterStep.Phone
            else -> onClose()
        }
    }

    Box(Modifier.fillMaxSize().background(Page)) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(280)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(220)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(160)))
            },
            label = "register_step",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                RegisterStep.Phone -> RegisterPhonePane(
                    vm = vm,
                    onBack = onClose,
                    onNext = { vm.checkPhoneAndSendCaptcha { step = RegisterStep.Captcha } },
                )
                RegisterStep.Captcha -> RegisterCaptchaPane(
                    vm = vm,
                    onBack = { step = RegisterStep.Phone },
                    onNext = { vm.goProfileAfterCaptcha { step = RegisterStep.Profile } },
                )
                RegisterStep.Profile -> RegisterProfilePane(
                    vm = vm,
                    onBack = { step = RegisterStep.Captcha },
                    onSubmit = { vm.register(onLoggedIn = onLoggedIn, onNeedSmsLogin = onNeedSmsLogin) },
                )
            }
        }
        if (vm.busy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = CloudRed,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun RegisterPhonePane(
    vm: RegisterViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val phoneOk = LoginPhoneRegex.matches(vm.phone.trim())
    Column(Modifier.fillMaxSize()) {
        LoginDrillTopBar(title = "注册", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "创建网易云账号",
                style = TextStyle(color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "注册仅用于在 ZMusic 内登录。该账号受网易云服务约束。",
                style = TextStyle(color = InkSecondary, fontSize = 13.sp, lineHeight = 20.sp),
            )
            Spacer(Modifier.height(28.dp))
            LoginUnderlineField(
                value = vm.phone,
                onValueChange = { next ->
                    val filtered = next.filter { it.isDigit() }.take(11)
                    if (filtered != vm.phone) vm.onPhoneChanged()
                    vm.phone = filtered
                },
                hint = "请输入手机号",
                leading = {
                    Text(
                        text = "+86",
                        style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 12.dp)
                            .size(width = 1.dp, height = 16.dp)
                            .background(MainPalette.Placeholder),
                    )
                },
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                onIme = { if (phoneOk) onNext() },
            )
            LoginErrorLine(vm.bannerError, vm::dismissError)
            Spacer(Modifier.height(32.dp))
            CloudPillButton(
                text = if (vm.captchaSending) "发送中…" else "下一步",
                enabled = phoneOk && !vm.captchaSending && !vm.busy,
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun RegisterCaptchaPane(
    vm: RegisterViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LoginDrillTopBar(title = "填写验证码", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "验证码已发送至 ${maskPhone(vm.phone)}",
                style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(20.dp))
            LoginUnderlineField(
                value = vm.captcha,
                onValueChange = { vm.captcha = it.filter { ch -> ch.isDigit() }.take(8) },
                hint = "请输入验证码",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                onIme = onNext,
                trailing = {
                    val label = when {
                        vm.captchaSending -> "发送中"
                        vm.captchaCooldownSec > 0 -> "${vm.captchaCooldownSec}s"
                        else -> "重获验证码"
                    }
                    val can = !vm.captchaSending && vm.captchaCooldownSec == 0
                    Text(
                        text = label,
                        modifier = Modifier
                            .clickable(enabled = can, onClick = { vm.resendCaptcha() })
                            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                        style = TextStyle(
                            color = if (can) CloudRed else InkHint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                },
            )
            LoginErrorLine(vm.bannerError, vm::dismissError)
            Spacer(Modifier.height(32.dp))
            CloudPillButton(
                text = "下一步",
                enabled = vm.captcha.isNotBlank() && !vm.busy,
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun RegisterProfilePane(
    vm: RegisterViewModel,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val pwdOk = RegisterViewModel.passwordError(vm.password) == null
    val nickOk = vm.nickname.trim().isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        LoginDrillTopBar(title = "设置账号", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "取一个昵称，并设置登录密码",
                style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(20.dp))
            LoginUnderlineField(
                value = vm.nickname,
                onValueChange = { vm.nickname = it.take(30) },
                hint = "昵称",
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(8.dp))
            LoginUnderlineField(
                value = vm.password,
                onValueChange = { vm.password = it },
                hint = "密码（8–20 位）",
                password = true,
                imeAction = ImeAction.Done,
                onIme = onSubmit,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "密码不能包含空格，须包含字母、数字、符号中至少两种。",
                style = TextStyle(color = InkHint, fontSize = 12.sp, lineHeight = 18.sp),
            )
            LoginErrorLine(vm.bannerError, vm::dismissError)
            Spacer(Modifier.height(32.dp))
            CloudPillButton(
                text = "完成注册",
                enabled = pwdOk && nickOk && !vm.busy,
                onClick = onSubmit,
            )
        }
    }
}

private fun maskPhone(phone: String): String {
    val p = phone.trim()
    if (p.length < 7) return p
    return p.take(3) + "****" + p.takeLast(4)
}
