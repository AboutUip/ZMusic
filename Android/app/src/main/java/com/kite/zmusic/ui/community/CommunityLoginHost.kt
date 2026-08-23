package com.kite.zmusic.ui.community

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.CommunityLoginConfig
import com.kite.zmusic.data.CommunityLoginPreview
import com.kite.zmusic.ui.common.QrScannerOverlay
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.player.PlayerDisplayQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class CommunityLoginPhase {
    data object Hidden : CommunityLoginPhase()
    data object Scanner : CommunityLoginPhase()
    data object Loading : CommunityLoginPhase()
    data class Authorize(val preview: CommunityLoginPreview) : CommunityLoginPhase()
}

@Composable
fun rememberCommunityLoginOpener(): () -> Unit {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<CommunityLoginPhase>(CommunityLoginPhase.Hidden) }
    var busy by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<Job?>(null) }

    fun toast(msg: String) = context.showIslandNotice(msg)

    fun handleQr(raw: String): Boolean {
        val sid = CommunityLoginConfig.parseQr(raw)
        if (sid == null) {
            return false
        }
        previewJob?.cancel()
        phase = CommunityLoginPhase.Loading
        previewJob = scope.launch {
            val result = runCatching { app.communityLoginRepository.preview(sid) }
            if (!isActive) return@launch
            result.fold(
                onSuccess = { phase = CommunityLoginPhase.Authorize(it) },
                onFailure = { e ->
                    toast(e.message?.takeIf { it.isNotBlank() } ?: "无法读取当前账号")
                    phase = CommunityLoginPhase.Hidden
                },
            )
        }
        return true
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                PlayerDisplayQr.decodeUri(context, uri)
            }
            if (text.isNullOrBlank()) {
                toast("未识别到二维码，请换一张更清晰的图片")
                return@launch
            }
            if (!handleQr(text)) {
                toast("不是社区登录二维码")
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            phase = CommunityLoginPhase.Scanner
        } else {
            toast("没有相机权限，已改为从相册选取")
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    fun openScanner() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            phase = CommunityLoginPhase.Scanner
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (phase is CommunityLoginPhase.Scanner) {
        Dialog(
            onDismissRequest = { phase = CommunityLoginPhase.Hidden },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            QrScannerOverlay(
                title = "扫描登录二维码",
                subtitle = "对准社区页上的码，也可从相册选取",
                onDetected = { raw -> handleQr(raw) },
                onOpenGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onClose = { phase = CommunityLoginPhase.Hidden },
            )
        }
    }

    val overlayPhase = phase
    if (overlayPhase is CommunityLoginPhase.Loading || overlayPhase is CommunityLoginPhase.Authorize) {
        Dialog(
            onDismissRequest = {
                if (!busy) {
                    previewJob?.cancel()
                    phase = CommunityLoginPhase.Hidden
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            CommunityAuthorizeLayer(
                phase = overlayPhase,
                busy = busy,
                onDismiss = {
                    if (!busy) {
                        previewJob?.cancel()
                        phase = CommunityLoginPhase.Hidden
                    }
                },
                onAllow = {
                    val preview = (phase as? CommunityLoginPhase.Authorize)?.preview
                        ?: return@CommunityAuthorizeLayer
                    if (busy) return@CommunityAuthorizeLayer
                    busy = true
                    scope.launch {
                        val result = runCatching { app.communityLoginRepository.allow(preview) }
                        busy = false
                        result.fold(
                            onSuccess = { ack ->
                                when {
                                    ack.ok -> {
                                        toast("已授权")
                                        phase = CommunityLoginPhase.Hidden
                                    }
                                    ack.status == "forbidden" ->
                                        toast("授权未通过，请刷新社区页二维码后再扫")
                                    ack.status == "expired" ->
                                        toast("二维码已过期，请刷新后再扫")
                                    ack.status == "denied" -> toast("该次授权已结束")
                                    ack.status == "consumed" -> toast("该二维码已经用过")
                                    ack.status == "missing" ->
                                        toast("二维码已失效，请刷新后再扫")
                                    else -> toast(
                                        if (ack.status.isBlank()) "授权未完成"
                                        else "授权未完成（${ack.status}）",
                                    )
                                }
                                if (!ack.ok && ack.status != "forbidden") {
                                    phase = CommunityLoginPhase.Hidden
                                }
                            },
                            onFailure = {
                                toast("网络出错，请稍后重试")
                            },
                        )
                    }
                },
                onDeny = {
                    val preview = (phase as? CommunityLoginPhase.Authorize)?.preview
                        ?: return@CommunityAuthorizeLayer
                    if (busy) return@CommunityAuthorizeLayer
                    busy = true
                    scope.launch {
                        val result = runCatching { app.communityLoginRepository.deny(preview.sid) }
                        busy = false
                        result.fold(
                            onSuccess = {
                                toast("已拒绝授权")
                                phase = CommunityLoginPhase.Hidden
                            },
                            onFailure = {
                                toast("网络出错，请稍后重试")
                            },
                        )
                    }
                },
            )
        }
    }

    return { openScanner() }
}

@Composable
private fun CommunityAuthorizeLayer(
    phase: CommunityLoginPhase,
    busy: Boolean,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.snapTo(0f)
        t.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t.value }
            .background(MainPalette.Page.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    val p = t.value
                    translationY = 28.dp.toPx() * (1f - p)
                    scaleX = 0.96f + 0.04f * p
                    scaleY = scaleX
                }
                .clip(RoundedCornerShape(22.dp))
                .background(MainPalette.Surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (phase) {
                is CommunityLoginPhase.Loading -> {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "正在读取账号…",
                        style = TextStyle(color = MainPalette.Secondary, fontSize = 14.sp),
                    )
                }
                is CommunityLoginPhase.Authorize -> {
                    Text(
                        text = CommunityLoginConfig.DISPLAY_NAME,
                        style = TextStyle(
                            color = MainPalette.Ink,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "将允许该社区识别你的网易云账号，并读取昵称、头像",
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(Modifier.height(22.dp))
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MainPalette.Card),
                    ) {
                        UrlImage(
                            url = phase.preview.avatarUrl,
                            contentDescription = phase.preview.nickname,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = phase.preview.nickname.ifBlank { "未命名" },
                        style = TextStyle(
                            color = MainPalette.Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AuthTextButton(
                            text = "拒绝",
                            filled = false,
                            enabled = !busy,
                            onClick = onDeny,
                            modifier = Modifier.weight(1f),
                        )
                        AuthTextButton(
                            text = if (busy) "提交中" else "允许",
                            filled = true,
                            enabled = !busy,
                            onClick = onAllow,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun AuthTextButton(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (filled) MainPalette.Accent else MainPalette.Card)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = if (filled) androidx.compose.ui.graphics.Color.White else MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
fun HomeCommunityScanButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ZIcons.QrScan,
            contentDescription = "扫描",
            tint = MainPalette.Ink,
            modifier = Modifier.size(22.dp),
        )
    }
}
