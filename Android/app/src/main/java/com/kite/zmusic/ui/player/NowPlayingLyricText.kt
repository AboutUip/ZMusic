@file:Suppress("UnusedBoxWithConstraintsScope")

package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kite.zmusic.data.LyricWord
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


@Composable
internal fun StableCenterLyricText(
    focus: Int,
    text: String,
    animMs: Int,
    lineSpanMs: Long,
    modifier: Modifier = Modifier,
    lineStartMs: Long = 0L,
    style: TextStyle,
    maxLines: Int = 6,
    fillWidth: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    /** 为 true 时首次合成直接亮起，跳过入场（选句退出恢复时防闪烁） */
    instantAppear: Boolean = false,
    /** 选句态：焦点/文案可更新，但禁止翻页入场/出场 */
    freezeTransitions: Boolean = false,
    words: List<LyricWord> = emptyList(),
    positionMs: Long = 0L,
    unplayedColor: Color? = null,
    tracking: Boolean = false,
    /** 并存译文：与主句同一入场层，切句两行一起抬起。 */
    secondaryText: String? = null,
    secondaryWords: List<LyricWord> = emptyList(),
    secondaryStyle: TextStyle? = null,
    secondaryMaxLines: Int = 3,
) {
    val pairKey = secondaryText.orEmpty()
    var shownFocus by remember {
        mutableIntStateOf(if (instantAppear || freezeTransitions) focus else -1)
    }
    var shownText by remember {
        mutableStateOf(if (instantAppear || freezeTransitions) text else "")
    }
    var shownSecondary by remember {
        mutableStateOf(if (instantAppear || freezeTransitions) pairKey else "")
    }
    val enterAlpha = remember {
        Animatable(if (instantAppear || freezeTransitions) 1f else 0f)
    }
    val enterLift = remember { Animatable(0f) }
    val density = LocalDensity.current
    // 入场时长仅在切句时采样；勿把 animMs/lineSpanMs 放进 key，
    // 否则 lead 窗口切到真实行时会重启 Effect，把未走完的 lift 瞬间 snap 成位置突变。
    LaunchedEffect(focus, text, pairKey, instantAppear, freezeTransitions) {
        if (freezeTransitions) {
            shownFocus = focus
            shownText = text
            shownSecondary = pairKey
            enterAlpha.snapTo(1f)
            enterLift.snapTo(0f)
            return@LaunchedEffect
        }
        // 同句：入场进行中或已结束都不要打断
        if (focus == shownFocus && shownText == text && shownSecondary == pairKey) {
            return@LaunchedEffect
        }
        val phaseMs = animMs.coerceIn(90, 420)
        val liftPx = with(density) { 10.dp.toPx() }
        // 选句退出后首次挂回：不要再播一遍翻页入场
        if (shownFocus < 0 && instantAppear) {
            shownFocus = focus
            shownText = text
            shownSecondary = pairKey
            enterAlpha.snapTo(1f)
            enterLift.snapTo(0f)
            return@LaunchedEffect
        }
        // 切句只播入场：先等出场再入场会把耗时拉成两倍，相对提前量会明显滞后。
        shownFocus = focus
        shownText = text
        shownSecondary = pairKey
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
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
        .graphicsLayer {
            alpha = enterAlpha.value
            translationY = enterLift.value
        }
    val pairStyle = secondaryStyle
    if (!secondaryText.isNullOrEmpty() && pairStyle != null) {
        Column(
            layerModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            LongLyricText(
                text = shownText,
                words = words,
                positionMs = positionMs,
                style = style,
                unplayedColor = unplayedColor,
                tracking = tracking && !freezeTransitions,
                maxLines = maxLines,
                overflow = overflow,
                lineActive = true,
                lineStartMs = lineStartMs,
                lineSpanMs = lineSpanMs,
            )
            LongLyricText(
                text = shownSecondary,
                words = secondaryWords,
                positionMs = positionMs,
                style = pairStyle,
                unplayedColor = unplayedColor,
                tracking = tracking && !freezeTransitions,
                maxLines = secondaryMaxLines,
                overflow = overflow,
                lineActive = true,
                lineStartMs = lineStartMs,
                lineSpanMs = lineSpanMs,
            )
        }
    } else if (words.isNotEmpty() && unplayedColor != null) {
        LongLyricText(
            text = shownText,
            words = words,
            positionMs = positionMs,
            unplayedColor = unplayedColor,
            tracking = tracking && !freezeTransitions,
            style = style,
            modifier = layerModifier,
            maxLines = maxLines,
            overflow = overflow,
            lineActive = true,
            lineStartMs = lineStartMs,
            lineSpanMs = lineSpanMs,
        )
    } else {
        LongLyricText(
            text = shownText,
            style = style,
            modifier = layerModifier,
            maxLines = maxLines,
            overflow = overflow,
            lineActive = true,
            lineStartMs = lineStartMs,
            lineSpanMs = lineSpanMs,
        )
    }
}

internal fun formatTimeMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}

