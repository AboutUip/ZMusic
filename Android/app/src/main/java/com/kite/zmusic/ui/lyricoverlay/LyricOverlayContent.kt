package com.kite.zmusic.ui.lyricoverlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricOverlayPrefs
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.player.lyricActiveIndex

private val OverlayAnim = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)

@Composable
fun LyricOverlayContent(
    playback: PlaybackUiState,
    prefs: LyricOverlayPrefs,
    maxWidthPx: Int,
    onPrefs: (LyricOverlayPrefs) -> Unit,
    onLock: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCenterHorizontally: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.locked) {
        if (prefs.locked) settingsOpen = false
    }
    val density = LocalDensity.current
    val maxWidthDp = with(density) { maxWidthPx.toDp() }
    val widthMod = if (prefs.dynamicWidth) {
        Modifier.widthIn(min = 120.dp, max = maxWidthDp)
    } else {
        Modifier.width(prefs.widthDp.dp.coerceAtMost(maxWidthDp))
    }
    val shape = RoundedCornerShape(14.dp)
    val showWindowBg = prefs.windowBackground || settingsOpen
    val blurT = if (prefs.windowBackground) {
        prefs.blurRadiusPx / LyricOverlayPrefs.BLUR_MAX.toFloat()
    } else {
        0f
    }
    val windowBg by animateColorAsState(
        targetValue = if (showWindowBg) {
            Color(
                red = 0.07f + 0.10f * blurT,
                green = 0.07f + 0.10f * blurT,
                blue = 0.09f + 0.10f * blurT,
                alpha = 0.88f - 0.16f * blurT.coerceIn(0f, 1f),
            )
        } else {
            Color.Transparent
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "overlayWindowBg",
    )
    Column(
        modifier = widthMod
            .then(
                if (prefs.locked) Modifier
                else Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    ) { change, drag ->
                        change.consume()
                        onDrag(drag.x, drag.y)
                    }
                },
            )
            .clip(shape)
            .background(windowBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        OverlayLyricLines(
            lines = playback.lyricLines,
            positionMs = playback.positionMs,
            title = playback.currentTrack?.name.orEmpty(),
            prefs = prefs,
        )
        if (!prefs.locked) {
            Spacer(Modifier.height(6.dp))
            OverlayToolbar(
                playing = playback.playWhenReady,
                settingsOpen = settingsOpen,
                onToggleSettings = { settingsOpen = !settingsOpen },
                onTogglePlay = onTogglePlay,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onLock = {
                    settingsOpen = false
                    onLock()
                },
            )
            AnimatedVisibility(
                visible = settingsOpen,
                enter = fadeIn(OverlayAnim) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(OverlayAnim) + shrinkVertically(tween(200, easing = FastOutSlowInEasing)),
            ) {
                LyricOverlaySettingsPanel(
                    prefs = prefs,
                    onChange = onPrefs,
                    onCenterHorizontally = onCenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun OverlayToolbar(
    playing: Boolean,
    settingsOpen: Boolean,
    onToggleSettings: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onLock: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OverlayIconBtn(ZIcons.Settings, if (settingsOpen) "收起设置" else "设置", onToggleSettings)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayIconBtn(ZIcons.SkipPrevious, "上一首", onSkipPrevious)
            OverlayIconBtn(if (playing) ZIcons.Pause else ZIcons.Play, if (playing) "暂停" else "播放", onTogglePlay)
            OverlayIconBtn(ZIcons.SkipNext, "下一首", onSkipNext)
        }
        OverlayIconBtn(ZIcons.Lock, "锁定", onLock)
    }
}

@Composable
private fun OverlayIconBtn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun OverlayLyricLines(
    lines: List<LrcLine>,
    positionMs: Long,
    title: String,
    prefs: LyricOverlayPrefs,
) {
    val active = lyricActiveIndex(lines, positionMs)
    val font = prefs.fontSizeSp.sp
    val lineHeight = (prefs.fontSizeSp * 1.35f).sp
    val slot = Modifier.height(with(LocalDensity.current) { lineHeight.toDp() })
    val boxAlign = prefs.lineBoxAlign()
    val textAlign = prefs.lineTextAlign()
    Column(Modifier.fillMaxWidth()) {
        repeat(prefs.playedLines) { i ->
            val idx = active - prefs.playedLines + i
            OverlayLine(
                text = lines.getOrNull(idx)?.text.orEmpty(),
                color = Color(prefs.playedColorArgb),
                fontSize = font,
                lyricBackground = prefs.lyricBackground,
                current = false,
                boxAlign = boxAlign,
                textAlign = textAlign,
                modifier = slot.fillMaxWidth(),
            )
        }
        OverlayLine(
            text = when {
                active >= 0 -> lines[active].text
                lines.isNotEmpty() -> lines.first().text
                title.isNotBlank() -> title
                else -> "♪"
            },
            color = Color(prefs.currentColorArgb),
            fontSize = font,
            lyricBackground = prefs.lyricBackground,
            current = true,
            boxAlign = boxAlign,
            textAlign = textAlign,
            modifier = slot.fillMaxWidth(),
        )
        repeat(prefs.upcomingLines) { i ->
            val idx = active + 1 + i
            OverlayLine(
                text = lines.getOrNull(idx)?.text.orEmpty(),
                color = Color(prefs.upcomingColorArgb),
                fontSize = font,
                lyricBackground = prefs.lyricBackground,
                current = false,
                boxAlign = boxAlign,
                textAlign = textAlign,
                modifier = slot.fillMaxWidth(),
            )
        }
    }
}

private fun LyricOverlayPrefs.lineBoxAlign(): Alignment = when (textAlign) {
    LyricOverlayPrefs.ALIGN_CENTER -> Alignment.Center
    LyricOverlayPrefs.ALIGN_RIGHT -> Alignment.CenterEnd
    else -> Alignment.CenterStart
}

private fun LyricOverlayPrefs.lineTextAlign(): TextAlign = when (textAlign) {
    LyricOverlayPrefs.ALIGN_CENTER -> TextAlign.Center
    LyricOverlayPrefs.ALIGN_RIGHT -> TextAlign.End
    else -> TextAlign.Start
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverlayLine(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lyricBackground: Boolean,
    current: Boolean,
    boxAlign: Alignment,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val bg = if (lyricBackground && text.isNotBlank()) {
        Color(0x66000000)
    } else {
        Color.Transparent
    }
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = boxAlign,
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = textAlign,
            style = TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .padding(horizontal = 4.dp)
                .basicMarquee(iterations = Int.MAX_VALUE),
        )
    }
}
