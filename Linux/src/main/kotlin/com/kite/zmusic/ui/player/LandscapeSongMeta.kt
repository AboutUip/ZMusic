package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.TitleAlignMode
import com.kite.zmusic.data.TitleLineStyle
import com.kite.zmusic.data.TrackRow

@Composable
internal fun LandscapeAlignedSongMeta(
    track: TrackRow,
    sourceTitle: String?,
    titleAlign: TitleAlignMode,
    songMetaTopPad: Dp,
    titleOffsetYDp: Float,
    titleNameColor: Color,
    titleArtistColor: Color,
    titleSourceColor: Color,
    titleNameFontScale: Float,
    titleArtistFontScale: Float,
    titleSourceFontScale: Float,
    chromeSidePad: Dp,
    vinylCenterX: Dp,
    lyricsCenterX: Dp,
    screenCenterX: Dp,
    titleMaxWidth: Dp,
    contentAlpha: Float = 1f,
    onRevealControls: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val centerModes = titleAlign == TitleAlignMode.VINYL ||
        titleAlign == TitleAlignMode.CENTER ||
        titleAlign == TitleAlignMode.LYRICS
    val textAlign = if (centerModes) TextAlign.Center else TextAlign.Start
    fun targetXForWidth(widthPx: Float): Float {
        if (widthPx <= 0.5f) return 0f
        return with(density) {
            when (titleAlign) {
                TitleAlignMode.LEFT -> chromeSidePad.toPx()
                TitleAlignMode.VINYL -> vinylCenterX.toPx() - widthPx / 2f
                TitleAlignMode.CENTER -> screenCenterX.toPx() - widthPx / 2f
                TitleAlignMode.LYRICS -> lyricsCenterX.toPx() - widthPx / 2f
            }
        }
    }
    Box(modifier.graphicsLayer { alpha = contentAlpha.coerceIn(0f, 1f) }) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = songMetaTopPad)
                .offset(y = titleOffsetYDp.dp)
                .widthIn(max = titleMaxWidth)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            AlignedMetaLine(
                text = track.name,
                textAlign = textAlign,
                titleAlign = titleAlign,
                targetXForWidth = ::targetXForWidth,
                style = TextStyle(
                    color = titleNameColor,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (TitleLineStyle.BASE_NAME_SP * titleNameFontScale).sp,
                    letterSpacing = 0.35.sp,
                    textAlign = textAlign,
                ),
                maxLines = 2,
                onClick = onRevealControls,
            )
            Spacer(Modifier.height(5.dp))
            AlignedMetaLine(
                text = track.artists.uppercase(),
                textAlign = textAlign,
                titleAlign = titleAlign,
                targetXForWidth = ::targetXForWidth,
                style = TextStyle(
                    color = titleArtistColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (TitleLineStyle.BASE_ARTIST_SP * titleArtistFontScale).sp,
                    letterSpacing = 1.8.sp,
                    textAlign = textAlign,
                ),
                maxLines = 1,
            )
            if (!sourceTitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                AlignedMetaLine(
                    text = sourceTitle,
                    textAlign = textAlign,
                    titleAlign = titleAlign,
                    targetXForWidth = ::targetXForWidth,
                    style = TextStyle(
                        color = titleSourceColor,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = (TitleLineStyle.BASE_SOURCE_SP * titleSourceFontScale).sp,
                        letterSpacing = 0.4.sp,
                        textAlign = textAlign,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AlignedMetaLine(
    text: String,
    textAlign: TextAlign,
    titleAlign: TitleAlignMode,
    targetXForWidth: (Float) -> Float,
    style: TextStyle,
    maxLines: Int,
    onClick: (() -> Unit)? = null,
) {
    var widthPx by remember(titleAlign, text) { mutableFloatStateOf(0f) }
    val x = remember { Animatable(0f) }
    val target = targetXForWidth(widthPx)
    LaunchedEffect(target, titleAlign) {
        x.animateTo(target, tween(320, easing = FastOutSlowInEasing))
    }
    Text(
        text = text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = Modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .onSizeChanged { widthPx = it.width.toFloat() }
            .graphicsLayer { translationX = x.value },
    )
}
