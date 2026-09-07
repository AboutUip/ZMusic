package com.kite.zmusic.ui.login

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.ncm.NcmLog
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.player.PlayerDisplayQr
import com.kite.zmusic.ui.theme.MainPalette

@Composable
internal fun NeteaseLoginQrOverlay(
    loginUrl: String,
    hint: String,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val onRefreshState = rememberUpdatedState(onRefresh)
    val onDismissState = rememberUpdatedState(onDismiss)
    val qrBitmap = remember(loginUrl) {
        if (loginUrl.isBlank()) null
        else runCatching { PlayerDisplayQr.encodeBitmap(loginUrl, 720) }.getOrNull()
    }
    val qrImage = remember(qrBitmap) { qrBitmap?.asImageBitmap() }
    var saveBusy by remember(loginUrl) { mutableStateOf(false) }
    var lastJumpAt by remember { mutableStateOf(0L) }
    val expired = hint.contains("过期")

    BackHandler(onBack = { onDismissState.value() })

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(MainPalette.Ink.copy(alpha = 0.46f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onDismissState.value() },
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MainPalette.Surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 20.dp, vertical = 22.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "扫码登录",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(228.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, MainPalette.Hairline, RoundedCornerShape(16.dp))
                    .clickable(enabled = expired || qrImage == null) {
                        onRefreshState.value()
                    }
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (qrImage != null && !expired) {
                    Image(
                        bitmap = qrImage,
                        contentDescription = "登录二维码",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else if (loginUrl.isBlank()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (expired) "点击刷新" else "二维码生成失败",
                        style = TextStyle(color = MainPalette.Accent, fontSize = 13.sp),
                    )
                }
            }
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = hint,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            Spacer(Modifier.height(18.dp))
            CloudOutlinePillButton(
                text = if (saveBusy) "正在保存…" else "保存到相册",
                onClick = {
                    val bmp = qrBitmap
                    if (bmp == null) {
                        context.showIslandNotice("二维码还没准备好")
                        return@CloudOutlinePillButton
                    }
                    if (saveBusy) return@CloudOutlinePillButton
                    saveBusy = true
                    val name = "zmusic-login-${System.currentTimeMillis()}.png"
                    val result = PlayerDisplayQr.saveToGallery(context, bmp, name)
                    saveBusy = false
                    if (result.isSuccess) {
                        NcmLog.i("login qr saved")
                        context.showIslandNotice("已保存")
                    } else {
                        NcmLog.w("login qr save failed", result.exceptionOrNull())
                        context.showIslandNotice("保存失败")
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            CloudOutlinePillButton(
                text = "打开网易云",
                onClick = {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastJumpAt < 800) return@CloudOutlinePillButton
                    lastJumpAt = now
                    when (NeteaseCloudApp.openApp(context)) {
                        NeteaseCloudApp.OpenResult.Opened -> {
                            NcmLog.i("login qr open netease ok")
                        }
                        NeteaseCloudApp.OpenResult.NotInstalled -> {
                            NcmLog.w("login qr netease not installed")
                            context.showIslandNotice("未安装网易云音乐")
                        }
                        NeteaseCloudApp.OpenResult.Failed -> {
                            NcmLog.w("login qr open netease failed")
                            context.showIslandNotice("无法打开网易云")
                        }
                    }
                },
            )
            Text(
                text = "取消",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp)
                    .clickable(onClick = { onDismissState.value() })
                    .padding(vertical = 10.dp),
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

internal object NeteaseCloudApp {
    const val PACKAGE = "com.netease.cloudmusic"

    enum class OpenResult { Opened, NotInstalled, Failed }

    fun openApp(context: Context): OpenResult {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launch != null && start(context, launch)) return OpenResult.Opened
        val fallback = listOf(
            packaged("orpheus://nm/scan"),
            packaged("orpheus://"),
            packaged("neteasemusic://"),
        )
        for (intent in fallback) {
            if (start(context, intent)) return OpenResult.Opened
        }
        if (!isInstalled(context)) {
            if (!start(context, view("market://details?id=$PACKAGE"))) {
                start(context, view("https://music.163.com/download"))
            }
            return OpenResult.NotInstalled
        }
        return OpenResult.Failed
    }

    fun openUrl(context: Context, pageUrl: String): OpenResult {
        if (pageUrl.isBlank()) return OpenResult.Failed
        val encoded = Uri.encode(pageUrl)
        val intents = listOf(
            packaged("orpheus://openurl?url=$encoded"),
            packaged("orpheus://open/?url=$encoded"),
            packaged("neteasemusic://openurl?url=$encoded"),
            packaged(pageUrl),
        )
        for (intent in intents) {
            if (start(context, intent)) return OpenResult.Opened
        }
        if (!isInstalled(context)) return OpenResult.NotInstalled
        return OpenResult.Failed
    }

    private fun packaged(uri: String): Intent = view(uri).setPackage(PACKAGE)

    private fun view(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }

    private fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess
}
