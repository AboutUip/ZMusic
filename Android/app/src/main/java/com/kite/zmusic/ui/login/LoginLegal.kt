package com.kite.zmusic.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.ui.theme.MainPalette

internal enum class LoginLegalKind {
    Terms,
    Privacy,
}

private val Page get() = MainPalette.Surface
private val Ink get() = MainPalette.Ink
private val InkSecondary get() = MainPalette.Secondary
private val InkHint get() = MainPalette.Hint
private val CloudRed = Color(0xFFEC4141)

private data class LegalSection(
    val heading: String,
    val body: String,
)

internal fun loginLegalTitle(kind: LoginLegalKind): String = when (kind) {
    LoginLegalKind.Terms -> "服务条款"
    LoginLegalKind.Privacy -> "隐私政策"
}

private fun loginLegalSections(kind: LoginLegalKind): List<LegalSection> = when (kind) {
    LoginLegalKind.Terms -> listOf(
        LegalSection(
            heading = "开源与作者",
            body = "ZMusic 以 GNU GPL-2.0 协议开源。使用、复制、修改或再分发本软件时，必须遵守该开源协议，保留许可证文本与版权声明，不得剥离开源义务。\n\n" +
                "作者为小萱baibai。请尊重作者的署名、劳动与本项目的原创性。",
        ),
        LegalSection(
            heading = "服务性质",
            body = "本应用默认向你提供听歌相关服务。该服务由作者出于自愿、以个人公益方式提供。服务器性能、可用性、延迟与稳定性不作任何保证，也可能随时调整、中断或停止。使用即表示你接受这一前提。",
        ),
        LegalSection(
            heading = "使用范围",
            body = "本应用所连接的音乐服务，仅限在 ZMusic 内使用。任何人不得将上述服务私自用于 ZMusic 之外的软件、脚本、接口调用或其他用途。",
        ),
        LegalSection(
            heading = "公益服务器",
            body = "为个人公益服务器。任何人不得对其进行压力测试、漏洞试探、恶意扫描或其他测试行为，也不得就其性能、稳定或提供方式提出无端质疑或干扰。",
        ),
        LegalSection(
            heading = "账号与责任",
            body = "登录即连接你的网易云账号，用于在 ZMusic 内同步收藏与歌单。请妥善保管账号。违反本条款造成的后果，由使用者自行承担。",
        ),
    )
    LoginLegalKind.Privacy -> listOf(
        LegalSection(
            heading = "我们保存什么",
            body = "ZMusic 只会保存登录凭证（加密存放于本机）以及你在本应用内的个性化数据，例如播放队列快照、显示偏好、服务器地址配置。",
        ),
        LegalSection(
            heading = "其余数据在哪",
            body = "歌曲、歌单、歌词、评论、账号资料等其余数据均存储于网易云官方服务器。ZMusic 不另建用户内容库，也不把这些数据另存为可对外传播的副本。",
        ),
        LegalSection(
            heading = "如何传递",
            body = "仅在本地与远程服务器之间传递登录和使用所必需的个人信息参数（例如请求所需的登录凭证，以及你主动填写的手机号、邮箱等登录字段）。我们保证不把这些数据对外传播、出售或提供给无关第三方。",
        ),
        LegalSection(
            heading = "你能做什么",
            body = "你可以在应用内退出登录以清除本地会话。卸载或清除应用数据，将同时移除本机个性化数据。",
        ),
    )
}

@Composable
internal fun LoginLegalOverlay(
    kind: LoginLegalKind?,
    onDismiss: () -> Unit,
) {
    var lastKind by remember { mutableStateOf<LoginLegalKind?>(null) }
    if (kind != null) lastKind = kind
    val shown = lastKind

    BackHandler(enabled = kind != null, onBack = onDismiss)

    AnimatedVisibility(
        visible = kind != null && shown != null,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(2f),
        enter = slideInHorizontally(
            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
            initialOffsetX = { it },
        ) + fadeIn(animationSpec = tween(220)),
        exit = slideOutHorizontally(
            animationSpec = tween(durationMillis = 280, easing = FastOutLinearInEasing),
            targetOffsetX = { it },
        ) + fadeOut(animationSpec = tween(180)),
    ) {
        if (shown != null) {
            LegalPage(kind = shown, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun LegalPage(
    kind: LoginLegalKind,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Page)
            .statusBarsPadding()
            .navigationBarsPadding(),
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
                        onClick = onDismiss,
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
                text = loginLegalTitle(kind),
                style = TextStyle(
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                loginLegalSections(kind).forEachIndexed { index, section ->
                    if (index > 0) Spacer(Modifier.height(28.dp))
                    Text(
                        text = "${index + 1}. ${section.heading}",
                        style = TextStyle(
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = section.body,
                        style = TextStyle(
                            color = InkSecondary,
                            fontSize = 14.sp,
                            lineHeight = 24.sp,
                        ),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 视觉内容（圆点 + 文案）按自身宽度居中；勾选热区不参与测量，避免 44dp 框把整行挤偏。
 */
@Composable
internal fun LoginAgreementRow(
    agreed: Boolean,
    onToggle: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    compact: Boolean = false,
    centered: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (compact) Modifier.padding(vertical = 2.dp)
                else Modifier.heightIn(min = 44.dp),
            ),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .semantics { role = Role.Checkbox }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 1.3.dp.toPx()
                    if (agreed) {
                        drawCircle(CloudRed)
                        val p = Path().apply {
                            moveTo(size.width * 0.22f, size.height * 0.52f)
                            lineTo(size.width * 0.42f, size.height * 0.72f)
                            lineTo(size.width * 0.78f, size.height * 0.30f)
                        }
                        drawPath(
                            p,
                            Color.White,
                            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                        )
                    } else {
                        drawCircle(color = InkHint, style = Stroke(stroke))
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = "同意",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
                style = TextStyle(color = InkSecondary, fontSize = 12.sp),
            )
            LegalNameLink("《服务条款》", onClick = onOpenTerms)
            LegalNameLink("《隐私政策》", onClick = onOpenPrivacy)
        }
    }
}

@Composable
internal fun LoginLegalNameLinks(
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    color: Color = CloudRed,
    fontSize: TextUnit = 14.sp,
) {
    Row {
        LegalNameLink("《服务条款》", onClick = onOpenTerms, color = color, fontSize = fontSize)
        LegalNameLink("《隐私政策》", onClick = onOpenPrivacy, color = color, fontSize = fontSize)
    }
}

@Composable
private fun LegalNameLink(
    text: String,
    onClick: () -> Unit,
    color: Color = Ink,
    fontSize: TextUnit = 12.sp,
) {
    Text(
        text = text,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        style = TextStyle(color = color, fontSize = fontSize),
    )
}
