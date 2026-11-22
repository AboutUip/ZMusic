package com.kite.zmusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.AppMode
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.icons.ZIcons

@Composable
internal fun AppModePanel(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val mode by app.appModeStore.mode.collectAsStateWithLifecycle()
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 64.dp, end = 22.dp, top = 28.dp, bottom = 28.dp),
    ) {
        Text(
            text = t("模式"),
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(14.dp))
        ModeCard(
            icon = ZIcons.MusicNote,
            title = t("音乐"),
            caption = t("歌曲、歌单和播放"),
            selected = mode == AppMode.Music,
            onClick = { app.appModeStore.set(AppMode.Music) },
        )
        Spacer(Modifier.height(10.dp))
        ModeCard(
            icon = ZIcons.Radio,
            title = t("播客"),
            caption = t("电台、声音和订阅"),
            selected = mode == AppMode.Podcast,
            onClick = { app.appModeStore.set(AppMode.Podcast) },
        )
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = if (selected) 0.34f else 0.18f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (selected) 0.72f else 0.16f),
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxIcon(icon = icon, selected = selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                color = Color.White.copy(alpha = 0.74f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxIcon(
    icon: ImageVector,
    selected: Boolean,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (selected) 0.2f else 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}
