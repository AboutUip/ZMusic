package com.kite.zmusic.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.ZMusicListenLink
import com.kite.zmusic.listen.ListenMember
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.main.MainControls
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.pageSheetHazeStyle
import com.kite.zmusic.ui.notice.showIslandNotice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch

private val ListenPanelShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
private val ListenCardShape = RoundedCornerShape(14.dp)

@Composable
internal fun PortraitListenTogetherSheet(
    onClose: () -> Unit,
    onNeedLogin: () -> Unit,
    onScanJoin: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val listen = app.listenTogether
    val ui by listen.ui.collectAsStateWithLifecycle()
    val loggedIn = app.workshopAuthStore.hasToken()
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var shareUri by remember { mutableStateOf<Uri?>(null) }
    var shareOpen by remember { mutableStateOf(false) }
    val sharePanel = remember { Animatable(0f) }
    val fallbackShareHPx = with(LocalDensity.current) { rememberPortraitShareSheetHeight().toPx() }
    var measuredShareHPx by remember { mutableFloatStateOf(0f) }
    val shareSheetHPx = measuredShareHPx.takeIf { it > 1f } ?: fallbackShareHPx
    val switchColors = MainControls.switchColors()
    LaunchedEffect(shareOpen) {
        if (shareOpen) {
            sharePanel.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f),
                ),
            )
        } else {
            sharePanel.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 360,
                    easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                ),
            )
        }
    }
    fun closeShare() {
        shareOpen = false
    }
    BackHandler(enabled = shareOpen) { closeShare() }

    Box(
        modifier
            .fillMaxSize()
            .clip(ListenPanelShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        if (hazeState != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState, style = pageSheetHazeStyle()),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MainPalette.Page.copy(alpha = 0.96f)),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(MainPalette.SheetWash),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 14.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MainPalette.Hint),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            ) {
                Text(
                    text = "一起听",
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.2).sp,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (ui.inRoom) {
                        if (ui.hosting) "已开启 · 清空播放列表后会自动结束"
                        else "已加入 · 进度按各自时钟对齐，不会互相拖跳"
                    } else {
                        "邀请朋友进入一起听。仅本次有效，没有歌曲时会自动结束。"
                    },
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                )
                Spacer(Modifier.height(16.dp))
                if (!loggedIn) {
                    ListenPrimaryButton(title = "登录社区后开启", enabled = !ui.busy) {
                        onNeedLogin()
                    }
                    Spacer(Modifier.height(10.dp))
                    ListenGhostButton(title = "扫描一起听邀请", enabled = !ui.busy) {
                        onScanJoin()
                    }
                } else if (!ui.inRoom) {
                    ListenSwitchRow(
                        title = "开启一起听",
                        subtitle = "仅本次有效，没有歌曲时会自动结束",
                        checked = false,
                        enabled = !ui.busy,
                        switchColors = switchColors,
                        onCheckedChange = { on ->
                            if (!on || ui.busy) return@ListenSwitchRow
                            scope.launch { listen.enable() }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    ListenSeatsRow(
                        seats = ui.draftSeats,
                        enabled = !ui.busy,
                        onChange = { listen.setDraftSeats(it) },
                    )
                    Spacer(Modifier.height(12.dp))
                    ListenGhostButton(title = "扫描邀请加入", enabled = !ui.busy) {
                        onScanJoin()
                    }
                } else {
                    val room = ui.room
                    ListenSwitchRow(
                        title = if (ui.hosting) "一起听进行中" else "正在一起听",
                        subtitle = if (ui.hosting) "关闭后房间立刻结束" else "离开不影响其他人",
                        checked = true,
                        enabled = !ui.busy && ui.hosting,
                        switchColors = switchColors,
                        onCheckedChange = { on ->
                            if (on || ui.busy) return@ListenSwitchRow
                            scope.launch { listen.stop(hostEnd = true) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    ListenSeatsRow(
                        seats = room?.maxMembers ?: ui.draftSeats,
                        enabled = false,
                        onChange = {},
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "在听 ${ui.memberCount}/${room?.maxMembers ?: 2}",
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    room?.members.orEmpty().forEach { member ->
                        ListenMemberRow(member)
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (ui.hosting) {
                        ListenPrimaryButton(
                            title = if (sharing) "正在生成邀请卡…" else "分享一起听",
                            enabled = !ui.busy && !sharing,
                        ) {
                            val snap = ui.room ?: return@ListenPrimaryButton
                            sharing = true
                            scope.launch {
                                context.showIslandNotice("正在生成邀请卡")
                                val uri = ShareListenCard.prepareShareUri(app, snap)
                                sharing = false
                                if (uri == null) {
                                    context.showIslandNotice("邀请卡生成失败")
                                    return@launch
                                }
                                shareUri = uri
                                shareOpen = true
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        ListenPrimaryButton(
                            title = when {
                                ui.outgoingPending -> "等待回应…"
                                ui.matching -> "正在匹配…"
                                else -> "匹配一起听"
                            },
                            enabled = !ui.busy && !sharing && ui.matchPeer == null && !ui.outgoingPending,
                        ) {
                            listen.toggleMatch()
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    ListenGhostButton(
                        title = if (ui.hosting) "结束一起听" else "离开一起听",
                        enabled = !ui.busy,
                    ) {
                        scope.launch { listen.stop(hostEnd = ui.hosting) }
                    }
                }
            }
        }
        val shareT = sharePanel.value
        if (shareT > 0.001f || shareOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = shareT }
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { closeShare() },
                    ),
            )
            PortraitShareSheet(
                onPick = { target ->
                    val uri = shareUri
                    closeShare()
                    val room = ui.room
                    if (target == NcmShareTarget.CopyLink) {
                        val text = room?.qrText?.ifBlank { null }
                            ?: room?.id?.let(ZMusicListenLink::format)
                        if (text != null && copyText(context, text)) {
                            context.showIslandNotice("已复制邀请码")
                        } else {
                            context.showIslandNotice("复制失败")
                        }
                        return@PortraitShareSheet
                    }
                    if (uri == null) {
                        context.showIslandNotice("邀请卡还没准备好")
                        return@PortraitShareSheet
                    }
                    when (val result = NcmShare.sendImage(context, uri, target)) {
                        NcmShareResult.Opened -> Unit
                        NcmShareResult.Failed -> context.showIslandNotice("分享失败")
                        is NcmShareResult.MissingApp ->
                            context.showIslandNotice("未安装${result.appName}")
                        else -> context.showIslandNotice("分享失败")
                    }
                },
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { measuredShareHPx = it.height.toFloat() }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = (1f - shareT) * shareSheetHPx
                        alpha = shareT
                    },
            )
        }
        if (ui.busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MainPalette.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun ListenMemberRow(member: ListenMember) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ListenCardShape)
            .background(MainPalette.Card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MainPalette.Hint.copy(alpha = 0.35f)),
        ) {
            if (member.avatarUrl.isNotBlank()) {
                UrlImage(
                    url = member.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = member.nickname,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (member.host) "发起人" else "一起听",
                style = TextStyle(color = MainPalette.Secondary, fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun ListenSeatsRow(
    seats: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ListenCardShape)
            .background(MainPalette.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "听歌最大人数",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "默认 2 人，开启后不可再改",
                style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListenStep(enabled && seats > 2, "−") { onChange(seats - 1) }
            Text(
                text = "$seats",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
            )
            ListenStep(enabled && seats < 8, "+") { onChange(seats + 1) }
        }
    }
}

@Composable
private fun ListenStep(enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (enabled) MainPalette.Accent.copy(alpha = 0.18f) else MainPalette.Hint.copy(alpha = 0.12f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (enabled) MainPalette.Ink else MainPalette.Hint,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun ListenSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    switchColors: androidx.compose.material3.SwitchColors,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ListenCardShape)
            .background(MainPalette.Card)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = switchColors,
        )
    }
}

@Composable
private fun ListenPrimaryButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(ListenCardShape)
            .background(if (enabled) MainPalette.Accent else MainPalette.Hint.copy(alpha = 0.35f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
        )
    }
}

@Composable
private fun ListenGhostButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(ListenCardShape)
            .background(MainPalette.Card)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = MainPalette.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
        )
    }
}

private fun copyText(context: Context, text: String): Boolean {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false
    cm.setPrimaryClip(ClipData.newPlainText("ZMusic一起听", text))
    return true
}
