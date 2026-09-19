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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.i18n.t
import java.nio.charset.StandardCharsets

internal enum class AboutLegalKind {
    Terms,
    Privacy,
    License,
    Official,
}

internal data class AboutLegalSection(
    val heading: String,
    val body: String,
)

internal fun aboutLegalTitle(kind: AboutLegalKind): String = when (kind) {
    AboutLegalKind.Terms -> t("服务条款")
    AboutLegalKind.Privacy -> t("隐私政策")
    AboutLegalKind.License -> t("开源许可")
    AboutLegalKind.Official -> t("尊重官方")
}

/**
 * 设置「关于」使用的完整副本；登录页仍用较短摘要。
 */
internal fun aboutLegalSections(kind: AboutLegalKind): List<AboutLegalSection> = when (kind) {
    AboutLegalKind.Terms -> listOf(
        AboutLegalSection(
            heading = t("开源与作者"),
            body = t("ZMusic 以 GNU 通用公共许可证第 2 版（GPL-2.0）开源。你可以使用、复制、修改和再分发本软件，但必须完整保留许可证文本、版权声明与开源义务，不得将本软件闭源化，也不得剥离作者署名。\n\n") +
                t("ZMusic 的作者是小萱baibai。请尊重他的署名、劳动与本项目的原创性。引用、二次开发或再分发时，应清晰标明来源。"),
        ),
        AboutLegalSection(
            heading = t("服务性质"),
            body = t("本应用默认向你提供听歌及相关功能。这些服务由作者出于自愿、以个人公益方式提供，不构成对任何第三方的商业承诺。\n\n") +
                t("服务器的性能、可用性、延迟与稳定性不作保证，也可能随时调整、中断、维护或停止。网络波动、上游接口变更或作者个人安排，都可能导致功能暂时不可用。使用即表示你接受这一前提。"),
        ),
        AboutLegalSection(
            heading = t("使用范围"),
            body = t("本应用所连接的音乐及相关服务，仅限在 ZMusic 内、供你个人欣赏与管理使用。\n\n") +
                t("任何人不得将上述服务私自用于 ZMusic 之外的软件、脚本、批量接口调用、转售或任何其他用途。不得利用本应用绕过或破坏上游服务的合理使用边界。"),
        ),
        AboutLegalSection(
            heading = t("公益服务器"),
            body = t("为作者个人维护的公益服务器。容量与精力都有限，请温柔使用。\n\n") +
                t("任何人不得对其进行压力测试、漏洞试探、恶意扫描、高频刷取或其他测试与攻击行为，也不得就其性能、稳定或提供方式提出无端指责或干扰。发现异常请善意告知作者，而不是把它当成靶场。"),
        ),
        AboutLegalSection(
            heading = t("账号与责任"),
            body = t("登录即连接你的网易云音乐账号，用于在 ZMusic 内同步收藏、歌单与必要的听歌状态。请妥善保管账号与验证信息。\n\n") +
                t("你应对自己的使用行为负责。违反本条款、违反上游平台规则，或因账号泄露、不当使用造成的后果，由使用者自行承担。作者不对由此产生的损失作赔偿承诺。"),
        ),
        AboutLegalSection(
            heading = t("条款更新"),
            body = t("作者可能随软件版本更新本条款。继续使用即视为你了解并接受更新后的内容。若你不同意，请停止使用并卸载本应用。"),
        ),
    )
    AboutLegalKind.Privacy -> listOf(
        AboutLegalSection(
            heading = t("我们在本机保存什么"),
            body = t("ZMusic 只会把登录所必需的凭证加密存放在你的设备上，并保存你在本应用内的个性化数据，例如播放队列快照、显示偏好、服务器地址配置、本地搜索历史等。\n\n") +
                t("这些数据用于让你下次打开时仍能接着听、接着用，而不是用于画像或广告。"),
        ),
        AboutLegalSection(
            heading = t("其余数据在哪"),
            body = t("歌曲、歌单、歌词、评论、账号资料、头像等其余内容，均存储于网易云音乐等上游官方服务器。ZMusic 不另建用户内容库，也不会把这些数据另存为可对外传播的副本。\n\n") +
                t("你在上游平台上的账号权利、内容归属，仍以该平台规则为准。"),
        ),
        AboutLegalSection(
            heading = t("如何传递"),
            body = t("仅在本机与你配置的远程服务之间，传递登录和使用所必需的参数，例如请求所需的登录凭证，以及你主动填写的手机号、邮箱、验证码等登录字段。\n\n") +
                t("我们保证不把这些数据对外传播、出售、交换，或提供给与本应用运行无关的第三方。作者不会用它们做推广或精准营销。"),
        ),
        AboutLegalSection(
            heading = t("你能做什么"),
            body = t("你可以在应用内退出登录，以清除本地会话。卸载应用或清除应用数据，将同时移除本机保存的个性化数据与凭证。\n\n") +
                t("若你希望处理上游平台中的账号或内容，请前往网易云音乐等官方渠道。ZMusic 无法代替你在上游删除账号。"),
        ),
        AboutLegalSection(
            heading = t("政策更新"),
            body = t("隐私相关说明可能随功能调整而更新。继续使用即视为你了解更新后的做法。如有疑问，欢迎通过应用内赞赏页认识作者，或在开源仓库留言。"),
        ),
    )
    AboutLegalKind.License -> listOf(
        AboutLegalSection(
            heading = t("本软件许可证"),
            body = t("ZMusic 的原创源码以 GNU 通用公共许可证第 2 版（GPL-2.0）授权。你可以运行、研究、复制、修改并再分发本软件，但必须完整保留许可证文本、版权声明与相应的开源义务。将本软件闭源化、剥离作者署名，或在未遵守 GPL-2.0 的情况下再分发，均不被允许。\n\n") +
                t("ZMusic 的作者是小萱baibai。二次开发、引用或再分发时，应清晰标明来源。\n\n") +
                t("对应源码要约：本应用二进制所对应的源码公布于 https://github.com/AboutUip/ZMusic 。若你再分发修改后的二进制，须自行提供对应源码的获取方式。"),
        ),
        AboutLegalSection(
            heading = t("完整文本"),
            body = t("GPL-2.0 的完整法律文本以本项目源码仓库中的 LICENSE 文件为准，并写入本应用发行包内的第三方声明文件（assets/legal/NOTICES.txt）。本页摘要便于阅读，不替代完整许可证。若摘要与完整文本冲突，以完整许可证为准。"),
        ),
        AboutLegalSection(
            heading = t("第三方如何授权"),
            body = t("发行包中的第三方开源组件仍按其原许可证授权，本应用不会把它们改成 GPL-2.0。使用或再分发本应用时，你须同时遵守这些许可证。\n\n") +
                t("Android 打包时可能省略依赖中重复的 META-INF/LICENSE 与 NOTICE 文件，以免合并冲突。相应的许可证全文、版权声明与致谢已集中写入上述 NOTICES.txt，并在本页列出组件清单。下列名称仅用于识别组件，不代表其权利人与本项目存在合作、赞助或背书关系。"),
        ),
        AboutLegalSection(
            heading = t("第三方开源组件"),
            body = t("以下为发行包直接依赖，按许可证分组。坐标与版本以构建解析结果为准；测试专用依赖不打进发行包。\n\n") +
                t("Apache License 2.0：AndroidX / Jetpack（Jetpack Compose、Material 3、Activity、Navigation、Lifecycle、CameraX、Security Crypto、AppCompat、Core 及同族模块）；AndroidX Media3（media3-exoplayer、media3-session、media3-ui）；Kotlin 标准库；OkHttp（com.squareup.okhttp3:okhttp）；Google Material Components；ZXing（com.google.zxing:core）；AndroidSVG（com.caverock:androidsvg-aar）；Haze（dev.chrisbanes.haze:haze）；Kyant Backdrop 与 Kyant Shapes（io.github.kyant0:backdrop、io.github.kyant0:shapes）；QuickJS 的 Android / Java 封装（wang.harlon.quickjs:wrapper-android、wrapper-java）。\n\n") +
                t("MIT License：XAIOP（io.github.aboutuip:xaiop），Copyright (c) 2026 小萱baibai；QuickJS 引擎（随上述封装一并分发），Copyright (c) Fabrice Bellard、Charlie Gordon。\n\n") +
                t("Bouncy Castle Licence：Bouncy Castle Provider（org.bouncycastle:bcprov-jdk18on）。\n\n") +
                t("CC0 1.0 Universal：EdDSA Java（net.i2p.crypto:eddsa）。\n\n") +
                t("本应用不把第三方源码谎称为 ZMusic 原创。你有权依照各组件许可证取得源码并行使相应权利。"),
        ),
        AboutLegalSection(
            heading = t("传递依赖"),
            body = t("发行包还包含上述组件的传递依赖。就本构建而言，主要包括（均为 Apache License 2.0，除非其自身另有声明）：Okio、Google Tink、Guava、Gson、Kotlin 协程、Kotlinx Serialization、JetBrains Annotations、JSpecify、Error Prone annotations、Auto Value annotations，以及 Compose、Lifecycle、CameraX、Media3 的其余 AndroidX 同族模块。\n\n") +
                t("未在此逐一展开的传递依赖，以其源码或发行包中的许可证与 NOTICE 为准。本清单可能随版本增减。"),
        ),
        AboutLegalSection(
            heading = t("商标与上游接口"),
            body = t("「网易云音乐」及其他品牌、商标、服务标识归其权利人所有。ZMusic 对上游接口的调用不改变这些权利归属，也不构成官方授权。\n\n") +
                t("本应用通过兼容 NeteaseCloudMusicApi / NeteaseCloudMusicApiEnhanced 约定的 HTTP 接口访问音乐相关数据。本仓库不内置该服务端实现；docs 中的接口说明供开发者对照。再分发那些文档或自行运行兼容服务端时，须遵守其上游许可证。\n\n") +
                t("第三方库的名称、商标归其各自权利人所有，在此列出仅为履行开源披露义务。"),
        ),
        AboutLegalSection(
            heading = t("声明更新"),
            body = t("作者可能随依赖或许可证变化更新本说明与 NOTICES.txt。继续使用即视为你了解更新后的内容。完整义务仍以 GPL-2.0、各第三方许可证原文及发行包内 NOTICES.txt 为准。"),
        ),
    )
    AboutLegalKind.Official -> listOf(
        AboutLegalSection(
            heading = t("与官方的关系"),
            body = t("ZMusic 只是网易云音乐的第三方客户端，由作者个人开发与维护。它不是网易云音乐官方产品，亦未经网易公司授权、赞助或运营。本应用与杭州网易云音乐科技有限公司及其关联公司不存在隶属、代理或合作关系。"),
        ),
        AboutLegalSection(
            heading = t("我们不会做的事"),
            body = t("ZMusic 现在不会、未来也不会提供免 VIP、歌曲解灰、破解数字版权保护、绕过付费墙，或任何用于无偿取得官方会员权益与未授权曲库的功能。\n\n") +
                t("请勿以「代破解」「解锁灰歌」等目的使用、要求或二次修改本应用。若你看到声称与 ZMusic 相关的此类能力，它们不属于本项目。"),
        ),
        AboutLegalSection(
            heading = t("请尊重官方网易云音乐"),
            body = t("请尊重网易云音乐及其权利人的版权、商标与服务规则。会员、购买、下载与正版曲库，请通过官方网易云音乐客户端或官方渠道完成。\n\n") +
                t("ZMusic 只是一个便于个人在本机使用的第三方界面，不能代替官方应用，也不鼓励你脱离官方渠道获取内容。"),
        ),
        AboutLegalSection(
            heading = t("法律风险由你自行承担"),
            body = t("使用本软件即表示你理解：第三方客户端可能与上游规则不完全一致，并可能因账号、地区、版权或接口策略产生风险。你应自行判断是否使用，并自行承担由此带来的法律风险与后果。\n\n") +
                t("作者不对你因使用本应用而与任何第三方发生的纠纷、封号、索赔或处罚作出赔偿或担保承诺。"),
        ),
        AboutLegalSection(
            heading = t("非商业"),
            body = t("ZMusic 不是商业应用。作者不以本应用售卖会员、广告位或破解服务，也不把本应用作为经营性产品向你收费。公益服务器与开源发布不构成商业许诺。"),
        ),
        AboutLegalSection(
            heading = t("声明更新"),
            body = t("作者可能随软件版本更新本声明。继续使用即视为你了解并接受更新后的内容。若你不同意，请停止使用并卸载本应用。"),
        ),
    )
}

@Composable
internal fun AboutLegalGlassBody(kind: AboutLegalKind) {
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val notices = remember(kind, context) {
        if (kind != AboutLegalKind.License) {
            ""
        } else {
            runCatching {
                context.assets.open("legal/NOTICES.txt")
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
            }.getOrDefault("")
        }
    }
    val sections = aboutLegalSections(kind)
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
        sections.forEachIndexed { index, section ->
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
        if (notices.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "${sections.size + 1}. ${t("许可证原文")}",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = notices,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            )
        }
    }
}
