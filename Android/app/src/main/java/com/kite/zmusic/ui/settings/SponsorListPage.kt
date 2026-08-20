package com.kite.zmusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.SponsorEntry
import com.kite.zmusic.data.SponsorRoster
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

@Composable
fun SponsorListPage(
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<SponsorEntry>?>(null) }
    LaunchedEffect(Unit) {
        entries = SponsorRoster.load(context)
    }
    val list = entries.orEmpty()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "谢谢每一位投喂。名单随版本更新。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        if (entries != null && list.isEmpty()) {
            Text(
                text = "暂时还没有记录。",
                style = TextStyle(
                    color = MainPalette.Hint,
                    fontSize = 14.sp,
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        } else if (list.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .wallpaperItemChrome(RoundedCornerShape(16.dp)),
            ) {
                list.forEachIndexed { index, entry ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp)
                                .height(0.5.dp)
                                .background(MainPalette.Hairline),
                        )
                    }
                    SponsorRow(entry)
                }
            }
        }
    }
}

@Composable
private fun SponsorRow(entry: SponsorEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.time.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.time,
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.amount.isNotBlank()) {
            Text(
                text = entry.amount,
                style = TextStyle(
                    color = MainPalette.Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}
