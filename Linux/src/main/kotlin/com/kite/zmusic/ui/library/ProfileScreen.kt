package com.kite.zmusic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.StoredSession
import com.kite.zmusic.ui.theme.MainPalette

@Composable
fun ProfileScreen(
    session: StoredSession?,
    onOpenLikedArtists: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = com.kite.zmusic.ui.main.mainContentPadH())
            .padding(top = com.kite.zmusic.ui.main.MainContentPadTop),
    ) {
        Text("个人", style = TextStyle(color = MainPalette.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(12.dp))
        Text(
            session?.displayLabel?.ifBlank { null } ?: "已登录",
            style = TextStyle(color = MainPalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(8.dp))
        Text("收藏与资料会从账号同步。", style = TextStyle(color = MainPalette.Secondary, fontSize = 14.sp))
        Spacer(Modifier.height(16.dp))
        Text(
            "喜欢的歌手",
            style = TextStyle(color = MainPalette.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenLikedArtists,
            ),
        )
    }
}
