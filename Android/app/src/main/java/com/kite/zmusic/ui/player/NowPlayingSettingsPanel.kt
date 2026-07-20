@file:OptIn(ExperimentalMaterial3Api::class)

package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TitleAlignMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

private val LabelColor = Color(0xFFFFFFFF)
private val HintColor = Color(0xFFE8F0F8)
private val Accent = Color(0xFF9AF0F0)
private val IconTint = Color(0xFFD5DEE8)
/** 右上 chrome 图标尺寸；间距取宽度 1/3。 */
internal val NowPlayingChromeIconWidth = 40.dp
internal val NowPlayingChromeIconHeight = 34.dp
internal val NowPlayingChromeIconGap = NowPlayingChromeIconWidth / 3
/** 比播放条整条圆角更轻 */
private val ChromeShape = RoundedCornerShape(8.dp)
private val PanelShape = RoundedCornerShape(18.dp)
private val RowShape = RoundedCornerShape(12.dp)
private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 10f)
private val GlassBg = Color(0xFF03060A)
/** 与底部播放条一致：半透明黑底，无描边。 */
private val ChromeBarBg = Color.Black.copy(alpha = 0.22f)
/** 弹窗壳：更深、更糊的暗色磨砂玻璃。 */
private val SettingsGlassStyle = HazeStyle(
    backgroundColor = GlassBg,
    tints = listOf(
        HazeTint(Color(0xFF020408).copy(alpha = 0.88f)),
        HazeTint(Color(0xFF060A12).copy(alpha = 0.72f)),
        HazeTint(Color.Black.copy(alpha = 0.52f)),
    ),
    blurRadius = 56.dp,
    noiseFactor = 0.22f,
    fallbackTint = HazeTint(Color(0xF205080E)),
)
/** 功能行：近不透明实底，与磨砂壳分层。 */
private val SettingsRowBg = Color(0xF0141A24)

/**
 * 与底部播放条同风格的圆角 chrome 按钮底。
 */
@Composable
private fun ChromeIconShell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(width = NowPlayingChromeIconWidth, height = NowPlayingChromeIconHeight)
            .clip(ChromeShape)
            .background(ChromeBarBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/**
 * 与底部播放条同风格：圆角矩形 + 简约矢量「调节滑块」图标。
 */
@Composable
fun NowPlayingSettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChromeIconShell(onClick = onClick, modifier = modifier) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = Stroke(
                width = size.minDimension * 0.11f,
                cap = StrokeCap.Round,
            )
            val w = size.width
            val h = size.height
            val trackLen = w * 0.72f
            val left = (w - trackLen) / 2f
            val knobR = size.minDimension * 0.11f
            val rows = floatArrayOf(0.22f, 0.50f, 0.78f)
            val knobs = floatArrayOf(0.62f, 0.32f, 0.74f)
            for (i in rows.indices) {
                val y = h * rows[i]
                drawLine(
                    color = IconTint,
                    start = Offset(left, y),
                    end = Offset(left + trackLen, y),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = IconTint,
                    radius = knobR,
                    center = Offset(left + trackLen * knobs[i], y),
                )
            }
        }
    }
}

/**
 * 旋转锁定：对齐 Google Material「自动旋转」磁贴。
 * - 自动：竖屏手机 + 环绕旋转弧（screen_rotation）
 * - 锁定：竖屏手机 + 右下锁标（screen_lock_rotation）——轮廓完全不同
 */
@Composable
fun NowPlayingRotationLockButton(
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChromeIconShell(onClick = onClick, modifier = modifier) {
        Canvas(Modifier.size(19.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val sw = w * 0.092f
            val stroke = Stroke(width = sw, cap = StrokeCap.Round)

            if (locked) {
                // 锁定：更大机身 + 清晰锁（无旋转弧，一眼可辨）
                val phoneW = w * 0.38f
                val phoneH = h * 0.62f
                val phoneLeft = cx - phoneW / 2f - w * 0.06f
                val phoneTop = cy - phoneH / 2f
                drawRoundRect(
                    color = IconTint,
                    topLeft = Offset(phoneLeft, phoneTop),
                    size = androidx.compose.ui.geometry.Size(phoneW, phoneH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke,
                )
                drawLine(
                    IconTint,
                    Offset(phoneLeft + phoneW * 0.28f, phoneTop + phoneH * 0.15f),
                    Offset(phoneLeft + phoneW * 0.72f, phoneTop + phoneH * 0.15f),
                    sw * 0.9f,
                    StrokeCap.Round,
                )
                // 锁：实心感更强
                val lx = cx + w * 0.28f
                val bodyTop = cy + h * 0.02f
                val lockW = w * 0.32f
                val lockH = h * 0.26f
                drawRoundRect(
                    color = IconTint,
                    topLeft = Offset(lx - lockW / 2f, bodyTop),
                    size = androidx.compose.ui.geometry.Size(lockW, lockH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.045f, w * 0.045f),
                    style = stroke,
                )
                drawArc(
                    color = IconTint,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(lx - lockW * 0.34f, bodyTop - lockH * 0.92f),
                    size = androidx.compose.ui.geometry.Size(lockW * 0.68f, lockH * 1.15f),
                    style = stroke,
                )
                drawCircle(IconTint, radius = w * 0.035f, center = Offset(lx, bodyTop + lockH * 0.38f))
                drawLine(
                    IconTint,
                    Offset(lx, bodyTop + lockH * 0.42f),
                    Offset(lx, bodyTop + lockH * 0.68f),
                    sw * 0.85f,
                    StrokeCap.Round,
                )
            } else {
                // 自动：机身略小 + 粗旋转双弧（系统 screen_rotation）
                val phoneW = w * 0.30f
                val phoneH = h * 0.50f
                val phoneLeft = cx - phoneW / 2f
                val phoneTop = cy - phoneH / 2f
                drawRoundRect(
                    color = IconTint,
                    topLeft = Offset(phoneLeft, phoneTop),
                    size = androidx.compose.ui.geometry.Size(phoneW, phoneH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.065f, w * 0.065f),
                    style = stroke,
                )
                drawLine(
                    IconTint,
                    Offset(cx - phoneW * 0.2f, phoneTop + phoneH * 0.16f),
                    Offset(cx + phoneW * 0.2f, phoneTop + phoneH * 0.16f),
                    sw * 0.85f,
                    StrokeCap.Round,
                )
                val arcR = w * 0.44f
                drawArc(
                    color = IconTint,
                    startAngle = -48f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(cx - arcR, cy - arcR),
                    size = androidx.compose.ui.geometry.Size(arcR * 2f, arcR * 2f),
                    style = stroke,
                )
                drawArc(
                    color = IconTint,
                    startAngle = 132f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(cx - arcR, cy - arcR),
                    size = androidx.compose.ui.geometry.Size(arcR * 2f, arcR * 2f),
                    style = stroke,
                )
                val a1 = Math.toRadians(52.0)
                val t1 = Offset(cx + arcR * cos(a1).toFloat(), cy + arcR * sin(a1).toFloat())
                drawRotationArrowHead(t1, angleDeg = 52f + 90f, color = IconTint, sizePx = sw * 2.4f)
                val a2 = Math.toRadians(232.0)
                val t2 = Offset(cx + arcR * cos(a2).toFloat(), cy + arcR * sin(a2).toFloat())
                drawRotationArrowHead(t2, angleDeg = 232f + 90f, color = IconTint, sizePx = sw * 2.4f)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRotationArrowHead(
    tip: Offset,
    angleDeg: Float,
    color: Color,
    sizePx: Float,
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val back = sizePx * 0.9f
    val spread = sizePx * 0.55f
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val px = -dy
    val py = dx
    val bx = tip.x - dx * back
    val by = tip.y - dy * back
    drawLine(color, tip, Offset(bx + px * spread, by + py * spread), sizePx * 0.4f, StrokeCap.Round)
    drawLine(color, tip, Offset(bx - px * spread, by - py * spread), sizePx * 0.4f, StrokeCap.Round)
}

/**
 * 右侧设置面板：暗色磨砂（Haze）+ 高对比文字；分类 + 一行一功能；可垂直滚动。
 */
@Composable
fun NowPlayingSettingsSheet(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color(0xFFF8FAFC),
        activeTrackColor = Accent.copy(alpha = 0.62f),
        inactiveTrackColor = Color.White.copy(alpha = 0.16f),
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        disabledThumbColor = Color(0xFFD0D8E2).copy(alpha = 0.45f),
        disabledActiveTrackColor = Accent.copy(alpha = 0.28f),
        disabledInactiveTrackColor = Color.White.copy(alpha = 0.08f),
        disabledActiveTickColor = Color.Transparent,
        disabledInactiveTickColor = Color.Transparent,
    )
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color(0xFFF8FAFC),
        checkedTrackColor = Accent.copy(alpha = 0.62f),
        uncheckedThumbColor = Color(0xFFD0D8E2),
        uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
        uncheckedBorderColor = Color.White.copy(alpha = 0.14f),
        checkedBorderColor = Color.Transparent,
    )

    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(PanelShape)
            // 消费卡片内全部点击，避免穿透到外部 dismiss 层
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .hazeEffect(state = hazeState, style = SettingsGlassStyle) {
                blurRadius = 56.dp
                noiseFactor = 0.22f
            },
    ) {
        // 加深罩层，强化暗色磨砂
        Box(
            Modifier
                .matchParentSize()
                .background(Color(0xB005080E)),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    ),
                    shape = PanelShape,
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = "SETTINGS",
                style = TextStyle(
                    color = Accent.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    shadow = TextShadow,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "播放显示",
                style = TextStyle(
                    color = LabelColor,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    letterSpacing = 0.3.sp,
                    shadow = TextShadow,
                ),
            )
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SettingsCategory(title = "氛围") {
                    SettingsSwitchRow(
                        title = "雨夜效果",
                        subtitle = "斜雨磨砂玻璃氛围",
                        checked = prefs.rainNightEnabled,
                        colors = switchColors,
                        onCheckedChange = { onPrefsChange(prefs.copy(rainNightEnabled = it)) },
                    )
                    SettingsSwitchRow(
                        title = "活跃光晕",
                        subtitle = "低/中/高互斥高亮，同时仅一球发光，运动略加快",
                        checked = prefs.activeHalo,
                        colors = switchColors,
                        onCheckedChange = { onPrefsChange(prefs.copy(activeHalo = it)) },
                    )
                }

                SettingsCategory(title = "文字") {
                    SettingsSwitchRow(
                        title = "动态歌词",
                        subtitle = "宽度避开黑胶，左右对称保持中心",
                        checked = prefs.dynamicLyrics,
                        colors = switchColors,
                        onCheckedChange = { onPrefsChange(prefs.copy(dynamicLyrics = it)) },
                    )
                    SettingsTitleAlignRow(
                        selected = prefs.titleAlign,
                        onSelect = { onPrefsChange(prefs.copy(titleAlign = it)) },
                    )
                    SettingsSliderRow(
                        title = "字体大小",
                        valueLabel = String.format("%.0f%%", prefs.fontScale * 100f),
                        value = prefs.fontScale,
                        valueRange = PlayerDisplayPrefs.FONT_MIN..PlayerDisplayPrefs.FONT_MAX,
                        colors = sliderColors,
                        onValueChange = { onPrefsChange(prefs.copy(fontScale = it)) },
                    )
                    SettingsSliderRow(
                        title = "歌词行间距",
                        valueLabel = String.format("%.0f", prefs.lyricLineSpacingDp),
                        value = prefs.lyricLineSpacingDp,
                        valueRange = PlayerDisplayPrefs.LINE_SPACING_MIN..PlayerDisplayPrefs.LINE_SPACING_MAX,
                        colors = sliderColors,
                        onValueChange = { onPrefsChange(prefs.copy(lyricLineSpacingDp = it)) },
                    )
                    SettingsSliderRow(
                        title = "已播放歌词数",
                        valueLabel = prefs.lyricPlayedCount.toString(),
                        value = prefs.lyricPlayedCount.toFloat(),
                        valueRange = PlayerDisplayPrefs.LYRIC_AROUND_MIN.toFloat()..PlayerDisplayPrefs.LYRIC_AROUND_MAX.toFloat(),
                        steps = PlayerDisplayPrefs.LYRIC_AROUND_MAX - PlayerDisplayPrefs.LYRIC_AROUND_MIN - 1,
                        colors = sliderColors,
                        onValueChange = {
                            onPrefsChange(
                                prefs.copy(
                                    lyricPlayedCount = it.roundToInt().coerceIn(
                                        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
                                        PlayerDisplayPrefs.LYRIC_AROUND_MAX,
                                    ),
                                ),
                            )
                        },
                    )
                    SettingsSliderRow(
                        title = "待播放歌词数",
                        valueLabel = prefs.lyricUpcomingCount.toString(),
                        value = prefs.lyricUpcomingCount.toFloat(),
                        valueRange = PlayerDisplayPrefs.LYRIC_AROUND_MIN.toFloat()..PlayerDisplayPrefs.LYRIC_AROUND_MAX.toFloat(),
                        steps = PlayerDisplayPrefs.LYRIC_AROUND_MAX - PlayerDisplayPrefs.LYRIC_AROUND_MIN - 1,
                        colors = sliderColors,
                        onValueChange = {
                            onPrefsChange(
                                prefs.copy(
                                    lyricUpcomingCount = it.roundToInt().coerceIn(
                                        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
                                        PlayerDisplayPrefs.LYRIC_AROUND_MAX,
                                    ),
                                ),
                            )
                        },
                    )
                    SettingsSliderRow(
                        title = "歌词水平位置",
                        valueLabel = String.format("%+.0f", prefs.lyricOffsetXDp),
                        value = prefs.lyricOffsetXDp,
                        valueRange = PlayerDisplayPrefs.LYRIC_OFFSET_MIN..PlayerDisplayPrefs.LYRIC_OFFSET_MAX,
                        colors = sliderColors,
                        onValueChange = { onPrefsChange(prefs.copy(lyricOffsetXDp = it)) },
                    )
                }

                SettingsCategory(title = "布局") {
                    SettingsSliderRow(
                        title = "整体 UI 缩放",
                        valueLabel = String.format("%.0f%%", prefs.uiScale * 100f),
                        value = prefs.uiScale,
                        valueRange = PlayerDisplayPrefs.UI_MIN..PlayerDisplayPrefs.UI_MAX,
                        colors = sliderColors,
                        onValueChange = { onPrefsChange(prefs.copy(uiScale = it)) },
                    )
                    SettingsSwitchRow(
                        title = "播放组件常显",
                        subtitle = "底部控件保持展开",
                        checked = prefs.transportAlwaysVisible,
                        colors = switchColors,
                        onCheckedChange = { onPrefsChange(prefs.copy(transportAlwaysVisible = it)) },
                    )
                    SettingsSwitchRow(
                        title = "黑胶绝对居中",
                        subtitle = "垂直对齐屏幕中心，忽略垂直偏移",
                        checked = prefs.vinylAbsoluteCenter,
                        colors = switchColors,
                        onCheckedChange = { onPrefsChange(prefs.copy(vinylAbsoluteCenter = it)) },
                    )
                    SettingsSwitchRow(
                        title = "完整封面",
                        subtitle = "封面铺满中心，隐藏轴心镂空",
                        checked = prefs.vinylFullCover,
                        colors = switchColors,
                        onCheckedChange = { onPrefsChange(prefs.copy(vinylFullCover = it)) },
                    )
                    SettingsSliderRow(
                        title = "黑胶水平位置",
                        valueLabel = String.format("%+.0f", prefs.vinylOffsetXDp),
                        value = prefs.vinylOffsetXDp,
                        valueRange = PlayerDisplayPrefs.VINYL_OFFSET_MIN..PlayerDisplayPrefs.VINYL_OFFSET_MAX,
                        colors = sliderColors,
                        onValueChange = { onPrefsChange(prefs.copy(vinylOffsetXDp = it)) },
                    )
                    SettingsSliderRow(
                        title = "黑胶垂直位置",
                        valueLabel = String.format("%+.0f", prefs.vinylOffsetYDp),
                        value = prefs.vinylOffsetYDp,
                        valueRange = PlayerDisplayPrefs.VINYL_OFFSET_MIN..PlayerDisplayPrefs.VINYL_OFFSET_MAX,
                        colors = sliderColors,
                        enabled = !prefs.vinylAbsoluteCenter,
                        onValueChange = { onPrefsChange(prefs.copy(vinylOffsetYDp = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTitleAlignRow(
    selected: TitleAlignMode,
    onSelect: (TitleAlignMode) -> Unit,
) {
    val modes = TitleAlignMode.entries
    val labels = listOf("左对齐", "黑胶", "居中", "歌词")
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val indicator = remember { Animatable(selected.ordinal.toFloat()) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(selected) {
        indicator.animateTo(
            targetValue = selected.ordinal.toFloat(),
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(SettingsRowBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "标题对齐位置",
            style = TextStyle(
                color = LabelColor,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                shadow = TextShadow,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "歌名 / 制作人 / 歌单 · 滑动或点选切换",
            style = TextStyle(
                color = HintColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.3.sp,
                shadow = TextShadow,
            ),
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(modes.size) {
                    val segW = size.width / modes.size.toFloat()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragOffsetPx = 0f
                            scope.launch { indicator.stop() }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffsetPx += dragAmount
                            val live = (selected.ordinal + dragOffsetPx / segW)
                                .coerceIn(0f, (modes.lastIndex).toFloat())
                            scope.launch { indicator.snapTo(live) }
                        },
                        onDragEnd = {
                            val segWPx = size.width / modes.size.toFloat()
                            val idx = (selected.ordinal + dragOffsetPx / segWPx)
                                .roundToInt()
                                .coerceIn(0, modes.lastIndex)
                            dragOffsetPx = 0f
                            val next = modes[idx]
                            if (next != selected) {
                                onSelect(next)
                            } else {
                                scope.launch {
                                    indicator.animateTo(
                                        targetValue = selected.ordinal.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = 0.82f,
                                            stiffness = 380f,
                                        ),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            dragOffsetPx = 0f
                            scope.launch {
                                indicator.animateTo(
                                    targetValue = selected.ordinal.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = 380f,
                                    ),
                                )
                            }
                        },
                    )
                },
        ) {
            val segW = maxWidth / modes.size
            val thumbPad = 3.dp
            Box(
                Modifier
                    .offset {
                        val x = with(density) {
                            (segW * indicator.value + thumbPad).roundToPx()
                        }
                        IntOffset(x, 0)
                    }
                    .padding(vertical = thumbPad)
                    .width(segW - thumbPad * 2)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent.copy(alpha = 0.28f))
                    .border(
                        width = 1.dp,
                        color = Accent.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                    ),
            )
            Row(Modifier.fillMaxSize()) {
                modes.forEachIndexed { index, mode ->
                    val active = selected == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(mode) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = labels[index],
                            style = TextStyle(
                                color = if (active) {
                                    Accent.copy(alpha = 0.98f)
                                } else {
                                    HintColor.copy(alpha = 0.72f)
                                },
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 11.sp,
                                letterSpacing = 0.2.sp,
                                textAlign = TextAlign.Center,
                                shadow = TextShadow,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = TextStyle(
                color = Accent.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    colors: androidx.compose.material3.SwitchColors,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(SettingsRowBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = LabelColor,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    shadow = TextShadow,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = HintColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 0.3.sp,
                    shadow = TextShadow,
                ),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = colors,
        )
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    colors: androidx.compose.material3.SliderColors,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    val titleColor = if (enabled) LabelColor else LabelColor.copy(alpha = 0.38f)
    val valueColor = if (enabled) Accent.copy(alpha = 0.95f) else Accent.copy(alpha = 0.35f)
    val thumbIx = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(SettingsRowBg.copy(alpha = if (enabled) 1f else 0.72f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = titleColor,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    shadow = TextShadow,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = TextStyle(
                    color = valueColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 0.4.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = TextShadow,
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = thumbIx,
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
}

/**
 * 点击外部收回设置：无蒙版、无变暗，仅透明命中层。
 */
@Composable
fun NowPlayingSettingsOutsideDismiss(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}
