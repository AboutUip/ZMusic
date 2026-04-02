package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextAlign
import com.kite.zmusic.playback.PlaybackMode

private val CyanSoft = Color(0xFF6FD4D4)
private val AccentRose = Color(0xFFE8B4BC)
private val Base = Color(0xFF1A2230)

@Composable
fun PlaybackModeControl(
    mode: PlaybackMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    circleSize: Dp = 30.dp,
    glyphSize: TextUnit = 14.sp,
) {
    val glyph = when (mode) {
        PlaybackMode.ORDER -> "⟲"
        PlaybackMode.REPEAT_ONE -> "1↻"
        PlaybackMode.SHUFFLE -> "⤮"
    }

    Box(
        modifier = modifier
            .size(circleSize)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AccentRose.copy(alpha = 0.32f),
                        Base,
                    ),
                ),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    )
    {
        Text(
            text = glyph,
            style = TextStyle(
                color = CyanSoft.copy(alpha = 0.9f),
                fontFamily = FontFamily.Monospace,
                fontSize = glyphSize,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

