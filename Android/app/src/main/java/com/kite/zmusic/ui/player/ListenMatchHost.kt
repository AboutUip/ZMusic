package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.listen.ListenPeer
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.main.MainPalette
import kotlinx.coroutines.delay

@Composable
fun ListenMatchHost() {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val listen = app.listenTogether
    val ui by listen.ui.collectAsStateWithLifecycle()
    val incoming = ui.incomingInvite
    val rejected = ui.rejectedInvite
    val peer = ui.matchPeer
    when {
        incoming != null && incoming.status == "pending" -> {
            LaunchedEffect(incoming.id, incoming.expiresAt) {
                val waitMs = if (incoming.expiresAt > 0L) {
                    (incoming.expiresAt - System.currentTimeMillis()).coerceIn(0L, 60_000L)
                } else {
                    (incoming.expiresIn.coerceIn(0L, 60L)) * 1_000L
                }.coerceAtLeast(0L)
                delay(waitMs)
                if (listen.ui.value.incomingInvite?.id == incoming.id) {
                    listen.respondInvite(accept = false, today = false)
                }
            }
            GlassAlertDialog(
                title = "一起听邀请",
                message = "邀请你加入一起听",
                confirmLabel = "接受",
                onConfirm = { listen.respondInvite(accept = true, today = false) },
                onDismiss = { listen.respondInvite(accept = false, today = false) },
                cancelLabel = "拒绝",
                tertiaryLabel = "当日拒绝",
                onTertiary = { listen.respondInvite(accept = false, today = true) },
                extraContent = { ListenMatchPeerCard(incoming.from) },
                scrimDismiss = false,
                backDismiss = true,
            )
        }
        rejected != null -> {
            GlassAlertDialog(
                title = "邀请被拒绝",
                message = "是否继续匹配一起听？",
                confirmLabel = "继续匹配",
                onConfirm = { listen.continueMatch() },
                onDismiss = { listen.dismissRejected() },
                cancelLabel = "取消",
                extraContent = { ListenMatchPeerCard(rejected.to) },
                scrimDismiss = false,
            )
        }
        peer != null -> {
            GlassAlertDialog(
                title = "匹配到一起听",
                message = "是否向对方发出邀请？",
                confirmLabel = "发出邀请",
                onConfirm = { listen.inviteMatchedPeer() },
                onDismiss = { listen.dismissMatchPeer() },
                cancelLabel = "取消",
                extraContent = { ListenMatchPeerCard(peer) },
                scrimDismiss = false,
            )
        }
    }
}

@Composable
private fun ListenMatchPeerCard(peer: ListenPeer) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MainPalette.Hint.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            if (peer.avatarUrl.isNotBlank()) {
                UrlImage(
                    url = peer.avatarUrl,
                    contentDescription = peer.nickname,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = peer.nickname.ifBlank { peer.uid },
            style = TextStyle(
                color = MainPalette.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
