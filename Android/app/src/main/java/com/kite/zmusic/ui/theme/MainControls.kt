@file:OptIn(ExperimentalMaterial3Api::class)

package com.kite.zmusic.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 跟 [MainPalette] 走的控件配方。首页 / 设置 / 歌单 /「更多」这些会换浅深色的界面，
 * 滑条未激活段和开关关闭槽都必须用这里，不要再写 `0xFFE5E5EA`。
 *
 * 播放页封面上的编辑器、桌面歌词浮层是另一套永远压在暗底上的语言，不要套这套配方。
 */
object MainControls {
    @Composable
    fun sliderColors(): SliderColors = SliderDefaults.colors(
        thumbColor = MainPalette.Accent,
        activeTrackColor = MainPalette.Accent,
        inactiveTrackColor = MainPalette.TrackOff,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        disabledThumbColor = MainPalette.Hint,
        disabledActiveTrackColor = MainPalette.Accent.copy(alpha = 0.28f),
        disabledInactiveTrackColor = MainPalette.TrackOff.copy(alpha = 0.7f),
        disabledActiveTickColor = Color.Transparent,
        disabledInactiveTickColor = Color.Transparent,
    )

    @Composable
    fun switchColors(): SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = MainPalette.Accent,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = MainPalette.TrackOff,
        uncheckedBorderColor = Color.Transparent,
        checkedBorderColor = Color.Transparent,
    )
}

/** 设置页滑条：品牌红已播段 + [MainPalette.TrackOff] 底槽，无末端白点。 */
@Composable
fun MainSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = MainControls.sliderColors()
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                enabled = enabled,
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                enabled = enabled,
                colors = colors,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 0.dp,
                drawStopIndicator = null,
            )
        },
    )
}
