package com.kite.zmusic.ui.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.login.LoginLegalKind
import com.kite.zmusic.ui.main.MainPalette

internal data class AboutLegalSection(
    val heading: String,
    val body: String,
)

internal fun aboutLegalTitle(kind: LoginLegalKind): String = when (kind) {
    LoginLegalKind.Terms -> "服务条款"
    LoginLegalKind.Privacy -> "隐私政策"
}

/**
 * 设置「关于」使用的完整副本；登录页仍用较短摘要。
 */
internal fun aboutLegalSections(kind: LoginLegalKind): List<AboutLegalSection> = when (kind) {
    LoginLegalKind.Terms -> listOf(
        AboutLegalSection(
            heading = "开源与作者",
            body = "ZMusic 以 GNU 通用公共许可证第 2 版（GPL-2.0）开源。你可以使用、复制、修改和再分发本软件，但必须完整保留许可证文本、版权声明与开源义务，不得将本软件闭源化，也不得剥离作者署名。\n\n" +
                "ZMusic 的作者是小萱baibai。请尊重她的署名、劳动与本项目的原创性。引用、二次开发或再分发时，应清晰标明来源。",
        ),
        AboutLegalSection(
            heading = "服务性质",
            body = "本应用默认向你提供听歌及相关功能。这些服务由作者出于自愿、以个人公益方式提供，不构成对任何第三方的商业承诺。\n\n" +
                "服务器的性能、可用性、延迟与稳定性不作保证，也可能随时调整、中断、维护或停止。网络波动、上游接口变更或作者个人安排，都可能导致功能暂时不可用。使用即表示你接受这一前提。",
        ),
        AboutLegalSection(
            heading = "使用范围",
            body = "本应用所连接的音乐及相关服务，仅限在 ZMusic 内、供你个人欣赏与管理使用。\n\n" +
                "任何人不得将上述服务私自用于 ZMusic 之外的软件、脚本、批量接口调用、转售或任何其他用途。不得利用本应用绕过或破坏上游服务的合理使用边界。",
        ),
        AboutLegalSection(
            heading = "公益服务器",
            body = "为作者个人维护的公益服务器。容量与精力都有限，请温柔使用。\n\n" +
                "任何人不得对其进行压力测试、漏洞试探、恶意扫描、高频刷取或其他测试与攻击行为，也不得就其性能、稳定或提供方式提出无端指责或干扰。发现异常请善意告知作者，而不是把它当成靶场。",
        ),
        AboutLegalSection(
            heading = "账号与责任",
            body = "登录即连接你的网易云音乐账号，用于在 ZMusic 内同步收藏、歌单与必要的听歌状态。请妥善保管账号与验证信息。\n\n" +
                "你应对自己的使用行为负责。违反本条款、违反上游平台规则，或因账号泄露、不当使用造成的后果，由使用者自行承担。作者不对由此产生的损失作赔偿承诺。",
        ),
        AboutLegalSection(
            heading = "条款更新",
            body = "作者可能随软件版本更新本条款。继续使用即视为你了解并接受更新后的内容。若你不同意，请停止使用并卸载本应用。",
        ),
    )
    LoginLegalKind.Privacy -> listOf(
        AboutLegalSection(
            heading = "我们在本机保存什么",
            body = "ZMusic 只会把登录所必需的凭证加密存放在你的设备上，并保存你在本应用内的个性化数据，例如播放队列快照、显示偏好、服务器地址配置、本地搜索历史等。\n\n" +
                "这些数据用于让你下次打开时仍能接着听、接着用，而不是用于画像或广告。",
        ),
        AboutLegalSection(
            heading = "其余数据在哪",
            body = "歌曲、歌单、歌词、评论、账号资料、头像等其余内容，均存储于网易云音乐等上游官方服务器。ZMusic 不另建用户内容库，也不会把这些数据另存为可对外传播的副本。\n\n" +
                "你在上游平台上的账号权利、内容归属，仍以该平台规则为准。",
        ),
        AboutLegalSection(
            heading = "如何传递",
            body = "仅在本机与你配置的远程服务之间，传递登录和使用所必需的参数，例如请求所需的登录凭证，以及你主动填写的手机号、邮箱、验证码等登录字段。\n\n" +
                "我们保证不把这些数据对外传播、出售、交换，或提供给与本应用运行无关的第三方。作者不会用它们做推广或精准营销。",
        ),
        AboutLegalSection(
            heading = "你能做什么",
            body = "你可以在应用内退出登录，以清除本地会话。卸载应用或清除应用数据，将同时移除本机保存的个性化数据与凭证。\n\n" +
                "若你希望处理上游平台中的账号或内容，请前往网易云音乐等官方渠道。ZMusic 无法代替你在上游删除账号。",
        ),
        AboutLegalSection(
            heading = "政策更新",
            body = "隐私相关说明可能随功能调整而更新。继续使用即视为你了解更新后的做法。如有疑问，欢迎通过应用内赞赏页认识作者，或在开源仓库留言。",
        ),
    )
}

@Composable
internal fun AboutLegalGlassBody(kind: LoginLegalKind) {
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (landscape) {
                    Modifier
                } else {
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                },
            ),
    ) {
        aboutLegalSections(kind).forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            Text(
                text = "${index + 1}. ${section.heading}",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = section.body,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
            )
        }
    }
}
