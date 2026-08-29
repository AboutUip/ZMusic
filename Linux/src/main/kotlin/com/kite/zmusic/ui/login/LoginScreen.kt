package com.kite.zmusic.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kite.zmusic.data.Md5Util
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.SessionStore
import com.kite.zmusic.ui.main.LandscapeCloudPillHeight
import com.kite.zmusic.ui.main.rememberLogoBitmap
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val Page get() = MainPalette.Surface
private val PageSoft get() = MainPalette.Page
private val Ink get() = MainPalette.Ink
private val InkSecondary get() = MainPalette.Secondary
private val CloudRed get() = MainPalette.Accent
private val Hairline get() = MainPalette.Hairline
private val CloudRedPressed get() = Color(
    red = (MainPalette.Accent.red * 0.84f).coerceIn(0f, 1f),
    green = (MainPalette.Accent.green * 0.83f).coerceIn(0f, 1f),
    blue = (MainPalette.Accent.blue * 0.83f).coerceIn(0f, 1f),
    alpha = 1f,
)
private val CloudRedDisabled get() = MainPalette.Accent.copy(alpha = 0.42f)

private enum class LandscapeStep { Landing, Sms, Qr, PhonePwd, Email, Register }

@Composable
fun LoginScreen(
    auth: NcmAuthClient,
    sessions: SessionStore,
    notices: IslandNoticeCenter,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(LandscapeStep.Landing) }
    var agreed by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var legalKind by remember { mutableStateOf<String?>(null) }

    fun guarded(block: () -> Unit) {
        if (agreed) block() else pending = block
    }

    Box(modifier.fillMaxSize().background(Page)) {
        Row(Modifier.fillMaxSize()) {
            LoginBrandRail(
                Modifier.weight(0.38f).fillMaxHeight(),
                caption = if (step == LandscapeStep.Register) {
                    "注册仅用于在 ZMusic 内登录，账号受网易云服务约束。"
                } else {
                    "登录网易云账号，同步收藏与歌单"
                },
            )
            Box(Modifier.weight(0.62f).fillMaxHeight().background(Page)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally(tween(280)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(220)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(160)))
                    },
                    label = "landscape_login_step",
                    modifier = Modifier.fillMaxSize(),
                ) { current ->
                    when (current) {
                        LandscapeStep.Landing -> LandscapeLandingPane(
                            onPhone = { guarded { step = LandscapeStep.Sms } },
                            onQr = { guarded { step = LandscapeStep.Qr } },
                            onPassword = { guarded { step = LandscapeStep.PhonePwd } },
                            onEmail = { guarded { step = LandscapeStep.Email } },
                            onRegister = { guarded { step = LandscapeStep.Register } },
                            agreed = agreed,
                            onToggleAgree = { agreed = !agreed },
                            onOpenTerms = { legalKind = "terms" },
                            onOpenPrivacy = { legalKind = "privacy" },
                        )
                        LandscapeStep.Sms -> SmsPane(auth, sessions, notices, onLoggedIn) { step = LandscapeStep.Landing }
                        LandscapeStep.Qr -> QrPane(auth, sessions, notices, onLoggedIn) { step = LandscapeStep.Landing }
                        LandscapeStep.PhonePwd -> PasswordPane(auth, sessions, notices, onLoggedIn) { step = LandscapeStep.Landing }
                        LandscapeStep.Email -> EmailPane(auth, sessions, notices, onLoggedIn) { step = LandscapeStep.Landing }
                        LandscapeStep.Register -> RegisterPane(auth, sessions, notices, onLoggedIn) { step = LandscapeStep.Landing }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = pending != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
        ) {
            val action = pending
            if (action != null) {
                AgreeFirstDialog(
                    onDismiss = { pending = null },
                    onAgree = {
                        agreed = true
                        pending = null
                        action()
                    },
                    onOpenTerms = { legalKind = "terms" },
                    onOpenPrivacy = { legalKind = "privacy" },
                )
            }
        }
        if (legalKind != null) {
            LegalOverlay(kind = legalKind!!) { legalKind = null }
        }
    }
}

@Composable
private fun LoginBrandRail(modifier: Modifier = Modifier, caption: String) {
    Box(modifier.background(PageSoft), contentAlignment = Alignment.Center) {
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
                bitmap = rememberLogoBitmap(),
                contentDescription = "ZMusic",
                modifier = Modifier.size(96.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "ZMusic",
                style = TextStyle(
                    color = Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "把世界调小一点  ·  把歌开大一点",
                style = TextStyle(color = InkSecondary, fontSize = 13.sp, letterSpacing = 0.2.sp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                caption,
                style = TextStyle(color = InkSecondary, fontSize = 12.sp, lineHeight = 18.sp),
                textAlign = TextAlign.Center,
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
        Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 300.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "登录",
                style = TextStyle(
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "使用网易云账号继续",
                style = TextStyle(color = InkSecondary, fontSize = 13.sp, textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(22.dp))
            CloudPillButton("手机号登录", onClick = onPhone)
            Spacer(Modifier.height(12.dp))
            CloudOutlinePillButton("扫码登录", onQr)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LandscapeTextAction("密码登录", onPassword)
                Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(12.dp).background(Hairline))
                LandscapeTextAction("邮箱登录", onEmail)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("没有账号？", style = TextStyle(color = InkSecondary, fontSize = 13.sp))
                Text(
                    "注册",
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRegister,
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    style = TextStyle(color = CloudRed, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
            LoginAgreementRow(agreed, onToggleAgree, onOpenTerms, onOpenPrivacy)
        }
    }
}

@Composable
private fun LandscapeTextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun LoginAgreementRow(
    agreed: Boolean,
    onToggle: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Row(
        Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.dp, if (agreed) CloudRed else Hairline, CircleShape)
                .background(if (agreed) CloudRed else Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text("我已阅读并同意", style = TextStyle(color = InkSecondary, fontSize = 11.sp))
        Text(
            "服务条款",
            color = CloudRed,
            fontSize = 11.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenTerms,
            ).padding(horizontal = 4.dp),
        )
        Text("与", style = TextStyle(color = InkSecondary, fontSize = 11.sp))
        Text(
            "隐私政策",
            color = CloudRed,
            fontSize = 11.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenPrivacy,
            ).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun QrPane(
    auth: NcmAuthClient,
    sessions: SessionStore,
    notices: IslandNoticeCenter,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
) {
    var hint by remember { mutableStateOf("正在获取二维码…") }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        val keyJson = runCatching { auth.loginQrKey() }.getOrNull()
        val key = keyJson?.let { NcmJson.qrKey(it) }
        if (key == null) {
            hint = "无法获取二维码"
            return@LaunchedEffect
        }
        val created = runCatching { auth.loginQrCreate(key) }.getOrNull()
        val b64 = created?.let { NcmJson.qrImgBase64(it) }
        qrBitmap = b64?.let { decodeQrBitmap(it) }
        hint = if (qrBitmap != null) "请用网易云扫码" else "二维码已就绪，请用网易云扫码"
        while (true) {
            delay(1500)
            val check = runCatching { auth.loginQrCheck(key, noCookie = false) }.getOrNull() ?: continue
            when (NcmJson.qrCheckCode(check)) {
                803 -> {
                    finishLogin(check, auth, sessions, notices, onLoggedIn, "登录成功但未返回 cookie")
                    return@LaunchedEffect
                }
                800 -> {
                    hint = "二维码已过期，返回重试"
                    return@LaunchedEffect
                }
                802 -> hint = "请在手机上确认"
                else -> hint = "请用网易云扫码"
            }
        }
    }
    FormColumn("扫码登录", onBack) {
        val bmp = qrBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "登录二维码",
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Page),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(hint, style = TextStyle(color = InkSecondary, fontSize = 14.sp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SmsPane(
    auth: NcmAuthClient,
    sessions: SessionStore,
    notices: IslandNoticeCenter,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    FormColumn("验证码登录", onBack) {
        Field("手机号", phone) { phone = it.filter { ch -> ch.isDigit() }.take(11) }
        Spacer(Modifier.height(10.dp))
        Field("验证码", code) { code = it.take(8) }
        Spacer(Modifier.height(16.dp))
        CloudPillButton("发送验证码") {
            scope.launch {
                runCatching { auth.captchaSent(phone) }
                notices.show("验证码已发送")
            }
        }
        Spacer(Modifier.height(10.dp))
        CloudPillButton("登录") {
            scope.launch {
                val json = runCatching { auth.loginCellphone(phone, captcha = code) }.getOrNull()
                finishLogin(json, auth, sessions, notices, onLoggedIn)
            }
        }
    }
}

@Composable
private fun PasswordPane(
    auth: NcmAuthClient,
    sessions: SessionStore,
    notices: IslandNoticeCenter,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    FormColumn("密码登录", onBack) {
        Text(
            "密码登录可能触发风控，建议优先使用验证码或扫码。",
            style = TextStyle(color = InkSecondary, fontSize = 13.sp),
        )
        Spacer(Modifier.height(16.dp))
        Field("手机号", phone) { phone = it.filter { ch -> ch.isDigit() }.take(11) }
        Spacer(Modifier.height(10.dp))
        Field("密码", password, secret = true) { password = it.take(32) }
        Spacer(Modifier.height(16.dp))
        CloudPillButton("登录") {
            scope.launch {
                val json = runCatching {
                    auth.loginCellphone(phone, md5Password = Md5Util.md5Hex(password))
                }.getOrNull()
                finishLogin(json, auth, sessions, notices, onLoggedIn)
            }
        }
    }
}

@Composable
private fun EmailPane(
    auth: NcmAuthClient,
    sessions: SessionStore,
    notices: IslandNoticeCenter,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    FormColumn("邮箱登录", onBack) {
        Field("邮箱", email) { email = it.trim().take(64) }
        Spacer(Modifier.height(10.dp))
        Field("密码", password, secret = true) { password = it.take(32) }
        Spacer(Modifier.height(16.dp))
        CloudPillButton("登录") {
            scope.launch {
                val json = runCatching {
                    auth.loginEmail(email, md5Password = Md5Util.md5Hex(password))
                }.getOrNull()
                finishLogin(json, auth, sessions, notices, onLoggedIn)
            }
        }
    }
}

@Composable
private fun RegisterPane(
    auth: NcmAuthClient,
    sessions: SessionStore,
    notices: IslandNoticeCenter,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
) {
    var nickname by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    FormColumn("注册", onBack) {
        Field("昵称", nickname) { nickname = it.take(20) }
        Spacer(Modifier.height(10.dp))
        Field("手机号", phone) { phone = it.filter { ch -> ch.isDigit() }.take(11) }
        Spacer(Modifier.height(10.dp))
        Field("验证码", code) { code = it.take(8) }
        Spacer(Modifier.height(10.dp))
        Field("密码", password, secret = true) { password = it.take(32) }
        Spacer(Modifier.height(16.dp))
        CloudOutlinePillButton("发送验证码") {
            scope.launch {
                runCatching { auth.captchaSent(phone) }
                notices.show("验证码已发送")
            }
        }
        Spacer(Modifier.height(10.dp))
        CloudPillButton("注册并登录") {
            scope.launch {
                val json = runCatching {
                    auth.registerCellphone(phone, code, password, nickname.ifBlank { "ZMusic" })
                }.getOrNull()
                finishLogin(json, auth, sessions, notices, onLoggedIn, "注册失败")
            }
        }
    }
}

@Composable
private fun FormColumn(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().widthIn(max = 320.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(16.dp))
                content()
                Spacer(Modifier.height(16.dp))
                LandscapeTextAction("返回") { onBack() }
            }
        }
    }
}

private suspend fun finishLogin(
    json: JSONObject?,
    auth: NcmAuthClient,
    sessions: SessionStore,
    notices: IslandNoticeCenter,
    onLoggedIn: () -> Unit,
    emptyMsg: String = "登录失败",
) {
    val cookie = json?.let { NcmJson.extractCookie(it) }
    if (cookie.isNullOrBlank()) {
        notices.show(json?.let { NcmJson.userFacingMessage(it, emptyMsg) } ?: emptyMsg)
        return
    }
    val status = runCatching { auth.loginStatus(cookie) }.getOrNull()
    sessions.persist(cookie, status?.let { NcmJson.displayLabelFromLogin(it) })
    notices.show("已登录")
    onLoggedIn()
}

@Composable
private fun Field(placeholder: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MainPalette.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = MainPalette.Hint, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = Ink, fontSize = 15.sp),
            cursorBrush = SolidColor(CloudRed),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CloudPillButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.98f else 1f, tween(90), label = "pill_scale")
    val bg = when {
        !enabled -> CloudRedDisabled
        pressed -> CloudRedPressed
        else -> CloudRed
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(LandscapeCloudPillHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun CloudOutlinePillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(LandscapeCloudPillHeight)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Hairline, RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
}

@Composable
private fun AgreeFirstDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Page)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(22.dp),
        ) {
            Text("请先同意条款", style = TextStyle(color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            Text("登录前需要阅读并同意服务条款与隐私政策。", style = TextStyle(color = InkSecondary, fontSize = 13.sp, lineHeight = 18.sp))
            Spacer(Modifier.height(10.dp))
            Row {
                Text("服务条款", color = CloudRed, fontSize = 13.sp, modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenTerms,
                ))
                Spacer(Modifier.width(16.dp))
                Text("隐私政策", color = CloudRed, fontSize = 13.sp, modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenPrivacy,
                ))
            }
            Spacer(Modifier.height(16.dp))
            CloudPillButton("同意并继续", onClick = onAgree)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LandscapeTextAction("取消", onDismiss)
            }
        }
    }
}

@Composable
private fun LegalOverlay(kind: String, onDismiss: () -> Unit) {
    val title = if (kind == "privacy") "隐私政策" else "服务条款"
    val body = if (kind == "privacy") {
        "账号 Cookie 仅保存在本机加密文件中，不会上传到社区目录。播放与歌单请求发往你配置的音乐服务器。"
    } else {
        "使用本应用即表示你同意以 GPL-2.0 使用客户端源码。网易云账号受网易云服务条款约束。"
    }
    Box(
        Modifier.fillMaxSize().background(Page).padding(32.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            LandscapeTextAction("返回", onDismiss)
            Spacer(Modifier.height(12.dp))
            Text(title, style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(12.dp))
            Text(body, style = TextStyle(color = Ink, fontSize = 14.sp, lineHeight = 22.sp))
        }
    }
}

private fun decodeQrBitmap(b64: String): ImageBitmap? = runCatching {
    val bytes = java.util.Base64.getDecoder().decode(b64)
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()
