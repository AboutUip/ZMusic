package com.kite.zmusic.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.TuneBands
import com.kite.zmusic.data.TunePrefs
import com.kite.zmusic.data.TunePreset
import com.kite.zmusic.ui.main.MainControls
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.theme.MainSlider
import kotlin.math.abs
import kotlin.math.roundToInt
import com.kite.zmusic.i18n.t

private val TuneRowShape = RoundedCornerShape(14.dp)
private val TuneChipShape = RoundedCornerShape(10.dp)

@Composable
internal fun PortraitTunePanel(
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val prefs by app.tunePrefsStore.prefs.collectAsStateWithLifecycle()
    val quality by app.audioQualityStore.quality.collectAsStateWithLifecycle()
    val listen by app.listenTogether.ui.collectAsStateWithLifecycle()
    val clockLocked = listen.inRoom
    val on = prefs.enabled
    val switchColors = MainControls.switchColors()
    val store = app.tunePrefsStore

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TuneSwitchRow(
            title = t("开启调音"),
            subtitle = if (on) t("已作用到当前播放") else t("关闭时保持原曲"),
            checked = on,
            switchColors = switchColors,
            onCheckedChange = { store.set(prefs.copy(enabled = it)) },
        )
        if (quality.isSpatial) {
            TuneNote(
                t("当前是%s。调音按立体声处理，空间环绕可能会变弱或听不出。", quality.title),
            )
        }
        TuneCard(enabled = on) {
            Text(
                text = t("预设"),
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            TunePresetChips(
                selected = prefs.preset,
                enabled = on,
                onSelect = { store.set(prefs.withPreset(it)) },
            )
        }
        TuneCard(enabled = on) {
            Text(
                text = t("均衡器"),
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = t("拖动柱子，±12 dB"),
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
            Spacer(Modifier.height(10.dp))
            TuneEqBars(
                eq = prefs.eq,
                enabled = on,
                onBand = { index, db -> store.set(prefs.withEq(index, db)) },
            )
        }
        TuneSliderRow(
            title = t("低音增强"),
            valueText = pct(prefs.bass),
            value = prefs.bass,
            enabled = on,
            onChange = { store.set(prefs.copy(bass = it)) },
        )
        TuneSliderRow(
            title = t("响度"),
            valueText = pct(prefs.loudness),
            value = prefs.loudness,
            enabled = on,
            onChange = { store.set(prefs.copy(loudness = it)) },
        )
        TuneSliderRow(
            title = t("压缩"),
            valueText = pct(prefs.compress),
            value = prefs.compress,
            enabled = on,
            onChange = { store.set(prefs.copy(compress = it)) },
        )
        TuneSliderRow(
            title = t("混响"),
            valueText = pct(prefs.reverb),
            value = prefs.reverb,
            enabled = on,
            onChange = { store.set(prefs.copy(reverb = it)) },
        )
        TuneStepperRow(
            title = t("变调"),
            subtitle = if (clockLocked) t("一起听进行中，变调已暂停") else t("独立于速度，单位半音"),
            valueText = formatSemitones(prefs.pitchSemitones),
            enabled = on && !clockLocked,
            onMinus = {
                store.set(prefs.copy(pitchSemitones = prefs.pitchSemitones - 1))
            },
            onPlus = {
                store.set(prefs.copy(pitchSemitones = prefs.pitchSemitones + 1))
            },
        )
        TuneSliderRow(
            title = t("速度"),
            valueText = "%.2f×".format(prefs.speed),
            value = prefs.speed,
            valueRange = TuneBands.MIN_SPEED..TuneBands.MAX_SPEED,
            enabled = on && !clockLocked,
            subtitle = if (clockLocked) t("一起听进行中，变速已暂停") else null,
            onChange = { store.set(prefs.copy(speed = it)) },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(TuneRowShape)
                .background(MainPalette.Card)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { store.reset() },
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t("全部复位"),
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = t("恢复原曲"),
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                ),
            )
        }
        TuneNote(t("仅本机生效，不会同步到一起听。杜比等空间音源上可能被跳过。"))
    }
}

private fun pct(v: Float): String = "${(v * 100f).roundToInt()}%"

private fun formatSemitones(n: Int): String = when {
    n == 0 -> t("原调")
    n > 0 -> "+$n"
    else -> "$n"
}

@Composable
private fun TuneNote(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = MainPalette.Secondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun TuneCard(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val enT by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "tuneCardEn",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(TuneRowShape)
            .background(MainPalette.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TunePresetChips(
    selected: TunePreset,
    enabled: Boolean,
    onSelect: (TunePreset) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TunePreset.entries.forEach { preset ->
            val active = selected == preset
            Box(
                Modifier
                    .clip(TuneChipShape)
                    .background(
                        if (active) MainPalette.Accent.copy(alpha = 0.18f) else MainPalette.TrackOff,
                    )
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(preset) },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = preset.title,
                    style = TextStyle(
                        color = if (active) MainPalette.Accent else MainPalette.Ink,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TuneEqBars(
    eq: List<Float>,
    enabled: Boolean,
    onBand: (Int, Float) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(148.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(TuneBands.COUNT) { index ->
            val db = eq.getOrElse(index) { 0f }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TuneEqBar(
                    db = db,
                    enabled = enabled,
                    onChange = { onBand(index, it) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = TuneBands.LABELS[index],
                    style = TextStyle(
                        color = MainPalette.Hint,
                        fontSize = 10.sp,
                    ),
                )
            }
        }
    }
}

private val TuneEqThumb = 10.dp

private fun eqYToDb(y: Float, heightPx: Float): Float {
    val h = heightPx.coerceAtLeast(1f)
    val frac = (1f - (y / h)).coerceIn(0f, 1f)
    return TuneBands.MIN_DB + frac * (TuneBands.MAX_DB - TuneBands.MIN_DB)
}

@Composable
private fun TuneEqBar(
    db: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChangeUpdated by rememberUpdatedState(onChange)
    val density = LocalDensity.current
    var barHeightPx by remember { mutableFloatStateOf(1f) }
    val t = ((db - TuneBands.MIN_DB) / (TuneBands.MAX_DB - TuneBands.MIN_DB)).coerceIn(0f, 1f)
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MainPalette.TrackOff)
            .onSizeChanged { barHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onChangeUpdated(eqYToDb(down.position.y, size.height.toFloat()))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        onChangeUpdated(eqYToDb(change.position.y, size.height.toFloat()))
                    }
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(1.dp)
                .background(MainPalette.Hairline.copy(alpha = 0.7f)),
        )
        val fillFromMid = abs(t - 0.5f) * 2f
        if (db >= 0f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.58f)
                    .fillMaxHeight(0.5f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillFromMid.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(MainPalette.Accent.copy(alpha = if (enabled) 0.88f else 0.4f)),
                )
            }
        } else {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.58f)
                    .fillMaxHeight(0.5f),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillFromMid.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(MainPalette.Accent.copy(alpha = if (enabled) 0.88f else 0.4f)),
                )
            }
        }
        val thumbPx = with(density) { TuneEqThumb.toPx() }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(0, ((1f - t) * barHeightPx - thumbPx / 2f).roundToInt())
                }
                .size(TuneEqThumb)
                .clip(CircleShape)
                .background(if (enabled) MainPalette.Accent else MainPalette.Hint),
        )
    }
}

@Composable
private fun TuneSliderRow(
    title: String,
    valueText: String,
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    subtitle: String? = null,
) {
    val enT by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "tuneSliderEn",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(TuneRowShape)
            .background(MainPalette.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = valueText,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                ),
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        MainSlider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onChange,
            enabled = enabled,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TuneStepperRow(
    title: String,
    subtitle: String,
    valueText: String,
    enabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val enT by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "tuneStepEn",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(TuneRowShape)
            .background(MainPalette.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        TuneStepButton("−", enabled, onMinus)
        Spacer(Modifier.width(4.dp))
        Text(
            text = valueText,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = MainPalette.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
        )
        Spacer(Modifier.width(4.dp))
        TuneStepButton("+", enabled, onPlus)
    }
}

@Composable
private fun TuneStepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MainPalette.TrackOff)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = MainPalette.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
        )
    }
}

@Composable
private fun TuneSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    switchColors: androidx.compose.material3.SwitchColors,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TuneRowShape)
            .background(MainPalette.Card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = switchColors,
        )
    }
}
