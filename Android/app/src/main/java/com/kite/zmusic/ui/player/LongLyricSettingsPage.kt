package com.kite.zmusic.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.LongLyricAlign
import com.kite.zmusic.data.LongLyricMotion
import com.kite.zmusic.data.LongLyricRender
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

private val PreviewShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(12.dp)

private const val SampleLine = "晚风把整段没说完的话都吹到了街对面，路灯也一盏一盏跟着轻轻地点头"

@Composable
internal fun LongLyricSettingsPage(
    mode: LongLyricRender,
    onModeChange: (LongLyricRender) -> Unit,
    contentBottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = t("播放页的预览歌词和歌词页里，一行放不下时的样子。换行会折成多行；水平自然滚动保持一行，当前句会跟着播放慢慢移到句尾。"),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeChip(
                title = t("换行渲染"),
                selected = mode == LongLyricRender.Wrap,
                onClick = { onModeChange(LongLyricRender.Wrap) },
                modifier = Modifier.weight(1f),
            )
            ModeChip(
                title = t("水平自然滚动"),
                selected = mode == LongLyricRender.Horizontal,
                onClick = { onModeChange(LongLyricRender.Horizontal) },
                modifier = Modifier.weight(1f),
            )
        }
        if (mode == LongLyricRender.Horizontal) {
            ScrollPreview(Modifier.fillMaxWidth())
        } else {
            WrapPreview(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ModeChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .then(
                if (selected) {
                    Modifier.clip(ChipShape).background(MainPalette.Accent.copy(alpha = 0.18f))
                } else {
                    Modifier.wallpaperItemChrome(ChipShape, MainPalette.Card)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = if (selected) MainPalette.Accent else MainPalette.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun WrapPreview(modifier: Modifier) {
    PreviewCard(modifier) {
        Text(
            text = SampleLine,
            style = previewStyle(),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ScrollPreview(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "longLyricPreview")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "longLyricPreviewT",
    )
    PreviewCard(modifier) {
        HorizontalLyricViewport(
            motion = LongLyricMotion.Timed(progress),
            align = LongLyricAlign.Center,
        ) {
            Text(
                text = SampleLine,
                style = previewStyle(),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun PreviewCard(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .wallpaperItemChrome(PreviewShape, MainPalette.Card)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = t("预览"),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

private fun previewStyle() = TextStyle(
    color = MainPalette.Ink,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 26.sp,
    textAlign = TextAlign.Center,
)
