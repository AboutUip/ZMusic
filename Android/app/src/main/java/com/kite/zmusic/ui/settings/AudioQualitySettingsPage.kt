package com.kite.zmusic.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.player.AudioQualityGroupedList

@Composable
fun AudioQualitySettingsPage(
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "之后点播、切歌都按这个档去拉音源。账号到不了的档会自动降到能播的最高一档。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        AudioQualityGroupedList(
            selected = selected,
            onSelect = onSelect,
        )
    }
}
