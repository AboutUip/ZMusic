package com.kite.zmusic.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.BuildConfig
import com.kite.zmusic.R
import com.kite.zmusic.data.ServerConfigRepository
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassPromptField
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.legal.AboutLegalGlassBody
import com.kite.zmusic.ui.legal.aboutLegalTitle
import com.kite.zmusic.ui.login.LoginLegalKind
import com.kite.zmusic.ui.main.LandscapeCoverEnter
import com.kite.zmusic.ui.main.LandscapeCoverExit
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.server.ServerConfigViewModel
import com.kite.zmusic.ui.server.ServerConfigViewModelFactory

private val AboutSlideSpec = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)
private val AboutFadeSpec = tween<Float>(durationMillis = 220)

@Composable
fun SettingsScreen(
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val serverConfig = remember { ServerConfigRepository(context.applicationContext) }
    val vm: ServerConfigViewModel = viewModel(
        key = "settings-server",
        factory = ServerConfigViewModelFactory(serverConfig),
    )
    var endpointLabel by remember {
        mutableStateOf(maskEndpoint(serverConfig.currentEndpoint()))
    }
    var editServer by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val aboutVisible = remember { MutableTransitionState(false) }
    var showAppreciate by remember { mutableStateOf(false) }
    var legalKind by remember { mutableStateOf<LoginLegalKind?>(null) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    BackHandler(enabled = aboutVisible.targetState && legalKind == null && !showAppreciate) {
        aboutVisible.targetState = false
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MainPalette.Page),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            SettingsTopBar(
                title = "设置",
                onBack = onBack,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = contentBottomInset + 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                SettingsGroup(
                    title = "连接",
                    reveal = reveal.value,
                    delay = 0f,
                ) {
                    SettingsRow(
                        title = "服务器",
                        subtitle = endpointLabel,
                        icon = ZIcons.Server,
                        tint = Color(0xFF5070F0),
                        onClick = {
                            vm.reloadFromStore()
                            editServer = true
                        },
                    )
                }
                Spacer(Modifier.height(22.dp))
                SettingsGroup(
                    title = "ZMusic",
                    reveal = reveal.value,
                    delay = 0.10f,
                ) {
                    SettingsRow(
                        title = "关于",
                        subtitle = "版本、开发者与协议",
                        icon = ZIcons.Info,
                        tint = Color(0xFF5B7CFA),
                        onClick = { aboutVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "赞赏",
                        subtitle = "请小萱喝一口热乎的",
                        icon = ZIcons.Favorite,
                        tint = Color(0xFFE85D75),
                        onClick = { showAppreciate = true },
                    )
                }
                Spacer(Modifier.height(22.dp))
                SettingsGroup(
                    title = "账号",
                    reveal = reveal.value,
                    delay = 0.18f,
                ) {
                    SettingsRow(
                        title = "退出登录",
                        subtitle = "当前账号会退出，播放也会停止",
                        icon = ZIcons.Logout,
                        tint = MainPalette.Accent,
                        destructive = true,
                        onClick = { confirmLogout = true },
                    )
                }
            }
        }
        AnimatedVisibility(
            visibleState = aboutVisible,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
            enter = if (landscape) LandscapeCoverEnter else slideInHorizontally(AboutSlideSpec) { it } + fadeIn(AboutFadeSpec),
            exit = if (landscape) LandscapeCoverExit else slideOutHorizontally(AboutSlideSpec) { it } + fadeOut(AboutFadeSpec),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MainPalette.Page)
                    .statusBarsPadding(),
            ) {
                SettingsTopBar(
                    title = "关于",
                    onBack = { aboutVisible.targetState = false },
                )
                AboutPage(
                    contentBottomInset = contentBottomInset,
                    onOpenTerms = { legalKind = LoginLegalKind.Terms },
                    onOpenPrivacy = { legalKind = LoginLegalKind.Privacy },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }

    if (editServer) {
        GlassAlertDialog(
            title = "服务器",
            message = "测试通过后才会保存",
            confirmLabel = "保存",
            confirmEnabled = !vm.busy,
            onConfirm = {
                vm.saveAndConnect {
                    endpointLabel = maskEndpoint(serverConfig.currentEndpoint())
                    editServer = false
                    context.showIslandNotice("服务器已更新")
                }
            },
            onDismiss = { if (!vm.busy) editServer = false },
            extraContent = {
                GlassPromptField(
                    value = vm.host,
                    onValueChange = vm::onHostChange,
                    placeholder = "主机 / IP",
                    maxLength = 253,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                GlassPromptField(
                    value = vm.portText,
                    onValueChange = vm::onPortChange,
                    placeholder = "端口",
                    maxLength = 5,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
                vm.bannerError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = TextStyle(
                            color = MainPalette.Accent,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                vm.statusHint?.let { hint ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = hint,
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
    if (confirmLogout) {
        GlassAlertDialog(
            title = "退出登录",
            message = "当前账号会退出，播放也会停止。",
            confirmLabel = "退出",
            confirmDestructive = true,
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
    legalKind?.let { kind ->
        GlassAlertDialog(
            title = aboutLegalTitle(kind),
            message = null,
            confirmLabel = "我知道了",
            cancelLabel = null,
            onConfirm = { legalKind = null },
            onDismiss = { legalKind = null },
            extraContent = { AboutLegalGlassBody(kind) },
        )
    }
    if (showAppreciate) {
        GlassAlertDialog(
            title = "感谢投喂小小萱哦～",
            message = "扫一扫这份微信赞赏码，就像把热乎的奶茶递到小萱手边。不投喂也没关系，你愿意听，他就已经很开心了。",
            confirmLabel = "收下这份心意",
            cancelLabel = null,
            onConfirm = { showAppreciate = false },
            onDismiss = { showAppreciate = false },
            extraContent = {
                val landscape = LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                Image(
                    painter = painterResource(R.drawable.img_wechat_appreciate),
                    contentDescription = "小萱baibai 的微信赞赏码",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (landscape) 148.dp else 260.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit,
                )
            },
        )
    }
}

@Composable
private fun AboutPage(
    contentBottomInset: Dp,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val version = BuildConfig.VERSION_NAME
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(bottom = contentBottomInset + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Image(
            painter = painterResource(R.drawable.ic_logo_vinyl_z),
            contentDescription = "ZMusic",
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(22.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "ZMusic",
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "给认真听歌的人",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
            ),
        )
        Spacer(Modifier.height(28.dp))
        AboutMetaCard(
            rows = listOf(
                "版本" to version,
                "开发者" to "小萱baibai",
                "开源协议" to "GNU GPL-2.0",
            ),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = "使用本软件，即表示你了解并同意下列约定。点开可阅读完整说明。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AboutLegalLink("《服务条款》", onClick = onOpenTerms)
            AboutLegalLink("《隐私政策》", onClick = onOpenPrivacy)
        }
    }
}

@Composable
private fun AboutMetaCard(rows: List<Pair<String, String>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MainPalette.Hairline),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 14.sp,
                    ),
                    modifier = Modifier.width(88.dp),
                )
                Text(
                    text = value,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun AboutLegalLink(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        style = TextStyle(
            color = MainPalette.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.Back,
                contentDescription = "返回",
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    reveal: Float,
    delay: Float,
    content: @Composable () -> Unit,
) {
    val t = ((reveal - delay) / (1f - delay).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
    Column(
        Modifier.graphicsLayer {
            alpha = t
            translationY = 14.dp.toPx() * (1f - t)
        },
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = if (destructive) MainPalette.Accent else MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        if (!destructive) {
            Icon(
                imageVector = ZIcons.ChevronRight,
                contentDescription = null,
                tint = MainPalette.Hint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun maskEndpoint(endpoint: ServerConfigRepository.Endpoint): String =
    ServerConfigRepository.maskEndpoint(endpoint)
