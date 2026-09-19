package com.kite.zmusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.listen.ListenChatMsg
import com.kite.zmusic.listen.ListenMember
import com.kite.zmusic.listen.ListenRoomSnapshot
import com.kite.zmusic.listen.listenAvatarLayout
import com.kite.zmusic.listen.listenChatBubbleText
import com.kite.zmusic.listen.ncmUserId
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.notice.showIslandNotice
import kotlinx.coroutines.delay
import com.kite.zmusic.i18n.t

private val ClusterRing = Color(0x66F2EDE6)
private val ClusterFill = Color(0x33000000)
private val ListenClusterAnim = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
private val ListenClusterDpAnim = tween<Dp>(durationMillis = 320, easing = FastOutSlowInEasing)

@Composable
internal fun PortraitListenTogetherAvatars(
    compact: Boolean,
    onOpenUser: (Long, String, String?) -> Unit,
    onOpenListenTogether: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val ui by app.listenTogether.ui.collectAsStateWithLifecycle()
    var held by remember { mutableStateOf<ListenRoomSnapshot?>(null) }
    val live = ui.room
    SideEffect {
        if (live != null && !live.closed) {
            held = live
        }
    }
    val room = live ?: held
    AnimatedVisibility(
        visible = ui.inRoom && room != null,
        modifier = modifier.playerExpandListenCluster(),
        enter = fadeIn(ListenClusterAnim) +
            scaleIn(
                initialScale = 0.90f,
                animationSpec = ListenClusterAnim,
                transformOrigin = TransformOrigin(0.5f, 0f),
            ) +
            slideInVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) { -it / 3 },
        exit = fadeOut(tween(220, easing = FastOutSlowInEasing)) +
            scaleOut(
                targetScale = 0.90f,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                transformOrigin = TransformOrigin(0.5f, 0f),
            ) +
            slideOutVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { -it / 3 },
        label = "listenTogetherCluster",
    ) {
        val shown = room ?: return@AnimatedVisibility
        ListenTogetherAvatarCluster(
            room = shown,
            compact = compact,
            toast = ui.chatToast,
            onOpenUser = onOpenUser,
            onOpenListenTogether = onOpenListenTogether,
            onClearToast = { app.listenTogether.clearChatToast(it) },
        )
    }
}

@Composable
private fun ListenTogetherAvatarCluster(
    room: ListenRoomSnapshot,
    compact: Boolean,
    toast: ListenChatMsg?,
    onOpenUser: (Long, String, String?) -> Unit,
    onOpenListenTogether: () -> Unit,
    onClearToast: (Long) -> Unit,
) {
    val context = LocalContext.current
    val layout = remember(room.members, room.hostUid) {
        listenAvatarLayout(room.members)
    }
    val host = layout.host ?: return
    val avatar by animateDpAsState(
        targetValue = if (compact) 33.6.dp else 48.dp,
        animationSpec = ListenClusterDpAnim,
        label = "listenAvatarSize",
    )
    val overlap by animateDpAsState(
        targetValue = if (compact) 12.dp else 16.8.dp,
        animationSpec = ListenClusterDpAnim,
        label = "listenAvatarOverlap",
    )
    val captionSp by animateFloatAsState(
        targetValue = if (compact) 12f else 13.2f,
        animationSpec = ListenClusterAnim,
        label = "listenCaptionSize",
    )
    val step = avatar - overlap
    val extra = (if (layout.overflow > 0) 1 else 0) + (if (layout.waitingSlot) 1 else 0)
    val count = layout.behind.size + 1 + extra
    val stackW = avatar + step * (count - 1).coerceAtLeast(0)
    val caption = when {
        layout.waitingSlot -> t("等待加入 · 1/%s", room.maxMembers.coerceAtLeast(2))
        else -> t("一起听 · %s人", room.members.size)
    }
    val toastIndex = remember(toast?.uid, layout.host?.uid, layout.behind, layout.overflow) {
        val uid = toast?.uid ?: return@remember -1
        var i = 0
        if (layout.overflow > 0) i++
        layout.behind.forEach { member ->
            if (member.uid == uid) return@remember i
            i++
        }
        if (host.uid == uid) return@remember i
        -1
    }
    LaunchedEffect(toast?.id) {
        val id = toast?.id ?: return@LaunchedEffect
        delay(3_000)
        onClearToast(id)
    }

    Column(
        Modifier
            .wrapContentWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = stackW, height = avatar)) {
            var slot = 0
            if (layout.overflow > 0) {
                OverflowDot(
                    extra = layout.overflow,
                    size = avatar,
                    modifier = Modifier
                        .offset(x = step * slot)
                        .zIndex(0f)
                        .clickableNoRipple(onOpenListenTogether),
                )
                slot++
            }
            layout.behind.forEachIndexed { i, member ->
                MemberDot(
                    member = member,
                    size = avatar,
                    host = false,
                    modifier = Modifier
                        .offset(x = step * slot)
                        .zIndex((i + 1).toFloat())
                        .clickableNoRipple {
                            openMemberSpace(context, member, onOpenUser)
                        },
                )
                slot++
            }
            MemberDot(
                member = host,
                size = avatar,
                host = true,
                modifier = Modifier
                    .offset(x = step * slot)
                    .zIndex(20f)
                    .clickableNoRipple {
                        openMemberSpace(context, host, onOpenUser)
                    },
            )
            slot++
            if (layout.waitingSlot) {
                WaitingDot(
                    size = avatar,
                    modifier = Modifier
                        .offset(x = step * slot)
                        .zIndex(1f)
                        .clickableNoRipple(onOpenListenTogether),
                )
            }
        }
        if (toast != null && toastIndex >= 0) {
            val shift = step * toastIndex + avatar / 2 - stackW / 2
            Spacer(Modifier.height(6.dp))
            ListenChatToastBubble(
                text = listenChatBubbleText(toast.text),
                modifier = Modifier.offset(x = shift),
            )
            Spacer(Modifier.height(4.dp))
        } else {
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = caption,
            style = TextStyle(
                color = LyricCurrent.copy(alpha = 0.78f),
                fontWeight = FontWeight.Medium,
                fontSize = captionSp.sp,
                letterSpacing = 0.2.sp,
            ),
            modifier = Modifier.clickableNoRipple(onOpenListenTogether),
        )
    }
}

@Composable
private fun ListenChatToastBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xF2FFF7F0))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = Color(0xFF1A1512),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
private fun MemberDot(
    member: ListenMember,
    size: Dp,
    host: Boolean,
    modifier: Modifier = Modifier,
) {
    val ring = if (host) LyricCurrent.copy(alpha = 0.95f) else ClusterRing
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(if (host) 2.4.dp else 1.8.dp, ring, CircleShape)
            .background(ClusterFill),
    ) {
        if (member.avatarUrl.isNotBlank()) {
            UrlImage(
                url = member.avatarUrl,
                contentDescription = member.nickname,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = member.nickname.trim().take(1).ifBlank { "?" },
                color = LyricCurrent,
                fontSize = 15.6.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun WaitingDot(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, ClusterRing, CircleShape)
            .background(ClusterFill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            color = LyricCurrent.copy(alpha = 0.85f),
            fontSize = 19.2.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun OverflowDot(
    extra: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, ClusterRing, CircleShape)
            .background(ClusterFill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+${extra.coerceAtMost(9)}",
            color = LyricCurrent,
            fontSize = 13.2.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )

private fun openMemberSpace(
    context: android.content.Context,
    member: ListenMember,
    onOpenUser: (Long, String, String?) -> Unit,
) {
    val id = member.ncmUserId()
    if (id == null) {
        context.showIslandNotice(t("无法打开主页"))
        return
    }
    onOpenUser(id, member.nickname, member.avatarUrl.takeIf { it.isNotBlank() })
}
