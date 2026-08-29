package com.kite.zmusic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.main.LandscapeLyricsWeight
import com.kite.zmusic.ui.main.LandscapeVinylWeight
import com.kite.zmusic.ui.main.landscapeVinylDiscDp
import kotlin.math.cos
import kotlin.math.sin

private val PlayerPage = Color(0xFF0E0E10)
private val PlayerInk = Color.White
private val PlayerMuted = Color.White.copy(alpha = 0.55f)
private val ChromePad = 28.dp

@Composable
fun LandscapePlayerBody(
    state: PlaybackUiState,
    wordByWord: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onMode: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack
    val lines = if (wordByWord && state.wordLyricLines.isNotEmpty()) {
        state.wordLyricLines
    } else {
        state.lyricLines
    }
    val focus = lines.indexOfLast { it.timeMs <= state.positionMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(focus, lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(focus)
    }
    Box(modifier.fillMaxSize().background(PlayerPage)) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = ChromePad, end = ChromePad, top = 56.dp, bottom = 88.dp),
        ) {
            BoxWithConstraints(
                Modifier
                    .weight(LandscapeVinylWeight)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val disc = landscapeVinylDiscDp(maxWidth)
                VinylDisc(playing = state.isPlaying, hue = state.vinylHue, size = disc)
            }
            Spacer(Modifier.width(12.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(LandscapeLyricsWeight)
                    .fillMaxHeight()
                    .padding(end = 4.dp),
            ) {
                if (lines.isEmpty()) {
                    item {
                        Text("暂无歌词", color = PlayerMuted, fontSize = 15.sp)
                    }
                }
                itemsIndexed(lines, key = { i, l -> "${l.timeMs}-$i" }) { i, line ->
                    val on = i == focus
                    if (wordByWord && on && line.words.isNotEmpty()) {
                        WordLine(line, state.positionMs)
                    } else {
                        Text(
                            line.text,
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = TextStyle(
                                color = if (on) PlayerInk else PlayerMuted,
                                fontSize = if (on) 22.sp else 16.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                lineHeight = 28.sp,
                            ),
                        )
                    }
                }
            }
        }
        Icon(
            Icons.Outlined.ArrowBack,
            contentDescription = "返回",
            tint = PlayerInk,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = ChromePad, top = 18.dp)
                .size(28.dp)
                .zIndex(50f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = ChromePad, end = ChromePad, top = 16.dp)
                .zIndex(40f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                track?.name ?: "未在播放",
                style = TextStyle(color = PlayerInk, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track?.artists.orEmpty(),
                style = TextStyle(color = PlayerMuted, fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val source = state.sourcePlaylistTitle
            if (!source.isNullOrBlank()) {
                Text(source, style = TextStyle(color = PlayerMuted.copy(alpha = 0.8f), fontSize = 12.sp), maxLines = 1)
            }
        }
        LandscapeTransportBar(
            state = state,
            onToggle = onToggle,
            onSeek = onSeek,
            onMode = onMode,
            onNext = onNext,
            onPrev = onPrev,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(40f)
                .padding(start = ChromePad, end = ChromePad, bottom = 16.dp),
        )
    }
}

@Composable
private fun LandscapeTransportBar(
    state: PlaybackUiState,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onMode: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dur = state.durationMs.coerceAtLeast(1L)
    val pos = state.positionMs.coerceIn(0L, dur)
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ModeIcon(state.playbackMode, onMode)
        TransportHit(Icons.Filled.SkipPrevious, "上一首", onPrev)
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.playWhenReady) "暂停" else "播放",
                tint = Color(0xFF111111),
                modifier = Modifier.size(16.dp),
            )
        }
        TransportHit(Icons.Filled.SkipNext, "下一首", onNext)
        Text(
            formatMs(pos),
            style = TextStyle(
                color = PlayerInk.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 0.3.sp,
            ),
        )
        Slider(
            value = (pos.toFloat() / dur).coerceIn(0f, 1f),
            onValueChange = { onSeek((it * dur).toLong()) },
            modifier = Modifier.weight(1f).height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f),
            ),
        )
        Text(
            formatMs(dur),
            style = TextStyle(
                color = PlayerInk.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}

@Composable
private fun TransportHit(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = PlayerInk, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ModeIcon(mode: PlaybackMode, onClick: () -> Unit) {
    val icon = when (mode) {
        PlaybackMode.ORDER -> Icons.Outlined.Repeat
        PlaybackMode.REPEAT_ONE -> Icons.Outlined.Repeat
        PlaybackMode.SHUFFLE -> Icons.Outlined.Shuffle
    }
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = "播放模式", tint = PlayerInk, modifier = Modifier.size(16.dp))
            if (mode == PlaybackMode.REPEAT_ONE) {
                Text("1", color = Color(0xFFEC4141), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WordLine(line: com.kite.zmusic.data.LrcLine, positionMs: Long) {
    val active = line.words.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    androidx.compose.foundation.text.BasicText(
        text = androidx.compose.ui.text.buildAnnotatedString {
            line.words.forEachIndexed { i, w ->
                pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = if (i == active) PlayerInk else PlayerMuted,
                        fontSize = if (i == active) 22.sp else 18.sp,
                        fontWeight = if (i == active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
                append(w.text)
                pop()
            }
        },
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun VinylDisc(playing: Boolean, hue: Float, size: androidx.compose.ui.unit.Dp) {
    val accent = Color(0xFFEC4141)
    Canvas(Modifier.size(size).clip(CircleShape).background(Color(0xFF1A1A1C))) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f
        for (i in 1..8) {
            drawCircle(color = Color.White.copy(alpha = 0.06f), radius = r * (i / 9f), center = c)
        }
        val spin = if (playing) hue else 0.15f
        drawCircle(color = accent.copy(alpha = 0.9f), radius = r * 0.28f, center = c)
        val hole = Offset(
            c.x + cos(spin.toDouble()).toFloat() * 4f,
            c.y + sin(spin.toDouble()).toFloat() * 4f,
        )
        drawCircle(color = Color(0xFF111111), radius = 8f, center = hole)
    }
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
