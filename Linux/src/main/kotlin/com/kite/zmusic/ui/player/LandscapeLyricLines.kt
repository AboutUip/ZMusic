package com.kite.zmusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.karaokeWords
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LandscapeScrollLyricLine(
    text: String,
    lineKey: Int,
    isPlayingLine: Boolean,
    isBrowseCenter: Boolean,
    live: Boolean,
    played: Boolean,
    distanceFromPlay: Int,
    lines: List<LrcLine>,
    focus: Int,
    trackDurationMs: Long,
    lineSpacing: Dp,
    slotHeight: Dp,
    animMs: Int,
    browsing: Boolean,
    positionMs: Long,
    wordByWord: Boolean,
    onSeekClick: (() -> Unit)?,
    onLongPress: (() -> Unit)? = null,
    playingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    playedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
) {
    val ix = remember { MutableInteractionSource() }
    val press = when {
        onSeekClick != null || onLongPress != null -> Modifier.combinedClickable(
            interactionSource = ix,
            indication = null,
            onClick = { onSeekClick?.invoke() },
            onLongClick = { onLongPress?.invoke() },
        )
        else -> Modifier
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = slotHeight)
            .wrapContentHeight()
            .then(press),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlayingLine) {
            LandscapeCenterLyricLine(
                lines = lines,
                focus = focus,
                live = live,
                trackDurationMs = trackDurationMs,
                animMs = animMs.coerceAtLeast(280),
                lineSpacing = lineSpacing,
                positionMs = positionMs,
                wordByWord = wordByWord,
                playingStyle = playingStyle,
            )
            return@Box
        }
        val visualMode = if (isBrowseCenter) 1 else 2
        @Composable
        fun LyricBody(mode: Int) {
            when (mode) {
                1 -> {
                    Text(
                        text = text,
                        style = TextStyle(
                            color = LyricBrowseSelect.copy(alpha = 0.88f),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.5.sp,
                            lineHeight = 26.sp,
                            letterSpacing = 0.35.sp,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 4,
                        softWrap = true,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = lineSpacing, horizontal = 10.dp),
                    )
                }
                else -> {
                    LandscapeSideLyricLine(
                        text = text,
                        lineKey = lineKey,
                        played = played,
                        distance = distanceFromPlay.coerceAtMost(3).coerceAtLeast(1),
                        animMs = animMs.coerceAtLeast(240),
                        verticalPadding = lineSpacing,
                        playedStyle = playedStyle,
                        unplayedStyle = unplayedStyle,
                    )
                }
            }
        }
        if (browsing) {
            LyricBody(visualMode)
        } else {
            AnimatedContent(
                targetState = visualMode,
                transitionSpec = {
                    (
                        fadeIn(tween(260, easing = LyricSoftEasing)) +
                            slideInVertically(tween(260, easing = LyricSoftEasing)) { it / 5 }
                        ) togetherWith (
                        fadeOut(tween(180, easing = LyricSofterEasing)) +
                            slideOutVertically(tween(180, easing = LyricSofterEasing)) { -it / 6 }
                        ) using SizeTransform(clip = false)
                },
                label = "landLyricVisual",
            ) { mode -> LyricBody(mode) }
        }
    }
}

@Composable
internal fun LandscapeCenterLyricLine(
    lines: List<LrcLine>,
    focus: Int,
    live: Boolean,
    trackDurationMs: Long,
    animMs: Int,
    lineSpacing: Dp = 10.dp,
    positionMs: Long = 0L,
    wordByWord: Boolean = false,
    playingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
) {
    val emphasis by animateFloatAsState(
        targetValue = if (live) 1f else 0f,
        animationSpec = tween(
            durationMillis = (animMs * 1.25f).toInt().coerceIn(280, 520),
            easing = LyricSoftEasing,
        ),
        label = "landLyricEmphasis",
    )
    val textAlpha = 0.58f + 0.42f * emphasis
    val playColor = argbColor(playingStyle.resolvedArgb(LyricRoleStyle.DEFAULT_PLAYING_ARGB))
    val unplayedBase = argbColor(playingStyle.resolvedArgb(LyricRoleStyle.DEFAULT_UNPLAYED_ARGB))
    val textColor = playColor.copy(alpha = textAlpha)
    val span = lyricLineSpanMs(lines, focus, trackDurationMs)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = lineSpacing, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        StableCenterLyricText(
            focus = focus,
            text = lines.getOrNull(focus)?.text.orEmpty(),
            animMs = animMs,
            lineSpanMs = span,
            words = if (wordByWord) {
                lines.getOrNull(focus)?.karaokeWords(positionMs).orEmpty()
            } else {
                emptyList()
            },
            positionMs = positionMs,
            unplayedColor = unplayedBase.copy(alpha = 0.46f),
            tracking = live,
            style = TextStyle(
                color = textColor,
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (playingStyle.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (playingStyle.italic) FontStyle.Italic else FontStyle.Normal,
                fontSize = (26f * playingStyle.fontScale.coerceIn(0.75f, 1.5f)).sp,
                lineHeight = 38.sp,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
internal fun LandscapeSideLyricLine(
    text: String,
    lineKey: Int,
    played: Boolean,
    distance: Int,
    animMs: Int,
    verticalPadding: Dp = 11.dp,
    playedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    unplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
) {
    val unplayedAlpha = (0.46f - distance.coerceAtMost(2) * 0.06f)
    val targetAlpha = if (played) lerp(unplayedAlpha, 0.32f, 1f) else unplayedAlpha
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = (animMs * 1.1f).toInt().coerceIn(240, 480),
            easing = LyricSoftEasing,
        ),
        label = "landSideA",
    )
    Crossfade(
        targetState = lineKey to text,
        animationSpec = tween(
            durationMillis = animMs.coerceIn(200, 420),
            easing = LyricSoftEasing,
        ),
        label = "landSideCrossfade",
        modifier = Modifier.fillMaxWidth(),
    ) { (_, shown) ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = verticalPadding, horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            val role = if (played) playedStyle else unplayedStyle
            val base = argbColor(
                role.resolvedArgb(
                    if (played) LyricRoleStyle.DEFAULT_PLAYED_ARGB else LyricRoleStyle.DEFAULT_UNPLAYED_ARGB,
                ),
            )
            Text(
                text = shown,
                style = TextStyle(
                    color = base.copy(alpha = alpha),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (role.bold) FontWeight.Bold else if (played) LyricPlayedWeight else LyricUnplayedWeight,
                    fontStyle = if (role.italic) FontStyle.Italic else FontStyle.Normal,
                    fontSize = (16.5f * role.fontScale.coerceIn(0.75f, 1.5f)).sp,
                    lineHeight = 26.sp,
                    letterSpacing = 0.35.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 4,
                softWrap = true,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun StableCenterLyricText(
    focus: Int,
    text: String,
    animMs: Int,
    lineSpanMs: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 6,
    overflow: TextOverflow = TextOverflow.Clip,
    words: List<com.kite.zmusic.data.LyricWord> = emptyList(),
    positionMs: Long = 0L,
    unplayedColor: Color = LyricUnplayedColor,
    tracking: Boolean = false,
) {
    var shownFocus by remember { mutableIntStateOf(-1) }
    var shownText by remember { mutableStateOf("") }
    val enterAlpha = remember { Animatable(0f) }
    val enterLift = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(focus, text) {
        if (focus == shownFocus && shownText == text) return@LaunchedEffect
        val phaseMs = animMs.coerceIn(220, 420)
        val liftPx = with(density) { 10.dp.toPx() }
        shownFocus = focus
        shownText = text
        enterAlpha.snapTo(0f)
        enterLift.snapTo(liftPx)
        coroutineScope {
            launch {
                enterAlpha.animateTo(1f, tween(phaseMs, easing = LyricSoftEasing))
            }
            launch {
                enterLift.animateTo(0f, tween(phaseMs, easing = LyricSoftEasing))
            }
        }
    }
    val layerModifier = modifier
        .fillMaxWidth()
        .graphicsLayer {
            alpha = enterAlpha.value
            translationY = enterLift.value
        }
    if (words.isNotEmpty()) {
        KaraokeLyricText(
            words = words,
            positionMs = positionMs,
            playingColor = style.color,
            unplayedColor = unplayedColor,
            tracking = tracking,
            style = style,
            modifier = layerModifier,
            maxLines = maxLines,
            overflow = overflow,
        )
    } else {
        Text(
            text = shownText,
            maxLines = maxLines,
            softWrap = true,
            overflow = overflow,
            style = style,
            modifier = layerModifier,
        )
    }
}
