@file:OptIn(ExperimentalMaterial3Api::class)

package com.kite.zmusic.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
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
import com.kite.zmusic.data.VinylColorStyle
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
/** 磨砂壳 / 背景色 */
private val SettingsGlassStyle = HazeStyle(
    backgroundColor = GlassBg,
    tints = listOf(
        HazeTint(Color(0xFF020408).copy(alpha = 0.78f)),
        HazeTint(Color(0xFF060A12).copy(alpha = 0.60f)),
        HazeTint(Color.Black.copy(alpha = 0.42f)),
    ),
    blurRadius = 84.dp,
    noiseFactor = 0.20f,
    fallbackTint = HazeTint(Color(0xCC05080E)),
)
/** 功能行：近不透明实底，与磨砂壳分层。 */
private val SettingsRowBg = Color(0xF0141A24)

/** 拖动歌词布局滑条时，面板其余部分淡出以便预览歌词。 */
private enum class LyricLayoutPreviewKey {
    LineSpacing,
    OffsetX,
}

private const val SettingsPreviewFadeOutMs = 320
private const val SettingsPreviewFadeInMs = 360
/** 预览中当前滑条保持可见但半透明 */
private const val SettingsPreviewFocusAlpha = 0.42f

/**
 * 设置面板内容透明度层：淡出时吞掉点击，避免点到隐形控件。
 */
@Composable
private fun SettingsAlpha(
    alpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val a = alpha.coerceIn(0f, 1f)
    val blockInput = a < 0.35f
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = a }
            .then(
                if (blockInput) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

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
 * 退出全屏播放：向下尖角（类似「>」顺时针 90°），夹角略开于直角以便辨认。
 */
@Composable
fun NowPlayingDismissIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChromeIconShell(onClick = onClick, modifier = modifier) {
        Canvas(Modifier.size(18.dp)) {
            val sw = size.minDimension * 0.12f
            val cx = size.width / 2f
            val cy = size.height / 2f
            // 相对竖直各偏 ~48° → 尖角约 96°，比锐利 chevron 更舒展
            val halfRad = Math.toRadians(48.0)
            val arm = size.minDimension * 0.34f
            val tipY = cy + arm * 0.42f
            val topY = tipY - arm * cos(halfRad).toFloat()
            val dx = arm * sin(halfRad).toFloat()
            drawLine(
                color = IconTint,
                start = Offset(cx - dx, topY),
                end = Offset(cx, tipY),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = IconTint,
                start = Offset(cx + dx, topY),
                end = Offset(cx, tipY),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
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
 * 拖动歌词字体 / 行距 / 水平位置时，其余面板淡出以便预览。
 */
@Composable
fun NowPlayingSettingsSheet(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onOpenVinylColorEditor: () -> Unit = {},
    onOpenLyricStyleEditor: () -> Unit = {},
    onOpenTitleStyleEditor: () -> Unit = {},
    hazeNonce: Int = 0,
    transferDismissGate: PlayerDisplayTransferDismissGate,
) {
    val transferHost = rememberPlayerDisplayTransferHost(
        prefs = prefs,
        onPrefsChange = onPrefsChange,
        hazeState = hazeState,
        dismissGate = transferDismissGate,
    )
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

    var previewKey by remember { mutableStateOf<LyricLayoutPreviewKey?>(null) }
    var focusKey by remember { mutableStateOf<LyricLayoutPreviewKey?>(null) }
    val chromeAlpha = remember { Animatable(1f) }
    val focusAlpha = remember { Animatable(1f) }
    // 预览键变化时中断上一跳，背景与焦点行各自动画到目标透明度
    LaunchedEffect(previewKey) {
        if (previewKey != null) {
            focusKey = previewKey
            launch {
                chromeAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = SettingsPreviewFadeOutMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            launch {
                focusAlpha.animateTo(
                    targetValue = SettingsPreviewFocusAlpha,
                    animationSpec = tween(
                        durationMillis = SettingsPreviewFadeOutMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        } else {
            launch {
                chromeAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = SettingsPreviewFadeInMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            launch {
                focusAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = SettingsPreviewFadeInMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }
    val dim = chromeAlpha.value
    fun rowAlpha(key: LyricLayoutPreviewKey): Float =
        if (key == focusKey) focusAlpha.value else dim

    fun onPreviewDrag(key: LyricLayoutPreviewKey, active: Boolean) {
        if (active) {
            previewKey = key
        } else if (previewKey == key) {
            previewKey = null
        }
    }

    val scrollState = rememberScrollState()

    // hazeNonce：仅重挂磨砂层，保留滚动与控件状态
    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(PanelShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        key(hazeNonce) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = dim }
                    .hazeEffect(state = hazeState, style = SettingsGlassStyle) {
                        blurRadius = 84.dp
                        noiseFactor = 0.20f
                        fallbackTint = HazeTint(Color(0xCC05080E))
                    },
            )
        }
        // 半透罩层：切歌 blur 短暂失效时也不致死实色
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = dim }
                .background(Color(0x9905080E)),
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = dim }
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
                .graphicsLayer { alpha = dim }
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
                modifier = Modifier.graphicsLayer { alpha = dim },
                style = TextStyle(
                    color = Accent.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    shadow = TextShadow,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = dim },
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    modifier = Modifier.weight(1f),
                )
                PlayerDisplayTransferHeaderIcons(host = transferHost)
            }
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState, enabled = previewKey == null),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SettingsCategory(title = "氛围", titleAlpha = dim) {
                    SettingsAlpha(dim) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    }
                }

                SettingsCategory(title = "文字", titleAlpha = dim) {
                    SettingsAlpha(dim) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsSwitchRow(
                                title = "动态歌词",
                                subtitle = "宽度避开黑胶，左右对称保持中心",
                                checked = prefs.dynamicLyrics,
                                colors = switchColors,
                                onCheckedChange = { onPrefsChange(prefs.copy(dynamicLyrics = it)) },
                            )
                            SettingsSwitchRow(
                                title = "自动播放",
                                subtitle = "点选歌词跳转后自动开始播放；播放中切歌始终播放",
                                checked = prefs.lyricTapAutoPlay,
                                colors = switchColors,
                                onCheckedChange = { onPrefsChange(prefs.copy(lyricTapAutoPlay = it)) },
                            )
                            SettingsActionRow(
                                title = "歌词样式",
                                subtitle = "斜体 / 粗体 / 颜色 / 字号",
                                actionLabel = "编辑",
                                onClick = onOpenLyricStyleEditor,
                            )
                            SettingsActionRow(
                                title = "标题样式",
                                subtitle = "歌名 / 制作人 / 歌单 · 颜色与字号",
                                actionLabel = "编辑",
                                onClick = onOpenTitleStyleEditor,
                            )
                            SettingsTitleAlignRow(
                                selected = prefs.titleAlign,
                                onSelect = { onPrefsChange(prefs.copy(titleAlign = it)) },
                            )
                            SettingsSliderRow(
                                title = "标题垂直位置",
                                valueLabel = String.format("%+.0f", prefs.titleOffsetYDp),
                                value = prefs.titleOffsetYDp,
                                valueRange = PlayerDisplayPrefs.TITLE_OFFSET_Y_MIN..
                                    PlayerDisplayPrefs.TITLE_OFFSET_Y_MAX,
                                colors = sliderColors,
                                onValueChange = { onPrefsChange(prefs.copy(titleOffsetYDp = it)) },
                            )
                        }
                    }
                    SettingsAlpha(rowAlpha(LyricLayoutPreviewKey.LineSpacing)) {
                        SettingsSliderRow(
                            title = "歌词行间距",
                            valueLabel = String.format("%.0f", prefs.lyricLineSpacingDp),
                            value = prefs.lyricLineSpacingDp,
                            valueRange = PlayerDisplayPrefs.LINE_SPACING_MIN..PlayerDisplayPrefs.LINE_SPACING_MAX,
                            colors = sliderColors,
                            onValueChange = { onPrefsChange(prefs.copy(lyricLineSpacingDp = it)) },
                            onPreviewDragActiveChange = {
                                onPreviewDrag(LyricLayoutPreviewKey.LineSpacing, it)
                            },
                        )
                    }
                    SettingsAlpha(dim) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsSliderRow(
                                title = "已播放歌词数",
                                valueLabel = prefs.lyricPlayedCount.toString(),
                                value = prefs.lyricPlayedCount.toFloat(),
                                valueRange = PlayerDisplayPrefs.LYRIC_AROUND_MIN.toFloat()..
                                    PlayerDisplayPrefs.LYRIC_AROUND_MAX.toFloat(),
                                steps = PlayerDisplayPrefs.LYRIC_AROUND_MAX -
                                    PlayerDisplayPrefs.LYRIC_AROUND_MIN - 1,
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
                                valueRange = PlayerDisplayPrefs.LYRIC_AROUND_MIN.toFloat()..
                                    PlayerDisplayPrefs.LYRIC_AROUND_MAX.toFloat(),
                                steps = PlayerDisplayPrefs.LYRIC_AROUND_MAX -
                                    PlayerDisplayPrefs.LYRIC_AROUND_MIN - 1,
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
                        }
                    }
                    SettingsAlpha(rowAlpha(LyricLayoutPreviewKey.OffsetX)) {
                        SettingsSliderRow(
                            title = "歌词水平位置",
                            valueLabel = String.format("%+.0f", prefs.lyricOffsetXDp),
                            value = prefs.lyricOffsetXDp,
                            valueRange = PlayerDisplayPrefs.LYRIC_OFFSET_MIN..
                                PlayerDisplayPrefs.LYRIC_OFFSET_MAX,
                            colors = sliderColors,
                            onValueChange = { onPrefsChange(prefs.copy(lyricOffsetXDp = it)) },
                            onPreviewDragActiveChange = {
                                onPreviewDrag(LyricLayoutPreviewKey.OffsetX, it)
                            },
                        )
                    }
                }

                SettingsCategory(title = "布局", titleAlpha = dim) {
                    SettingsAlpha(dim) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                onCheckedChange = {
                                    onPrefsChange(prefs.copy(transportAlwaysVisible = it))
                                },
                            )
                            SettingsSwitchRow(
                                title = "吸附式播放组件",
                                subtitle = "贴底吸附；关闭后悬浮并四角圆角",
                                checked = prefs.transportDocked,
                                colors = switchColors,
                                onCheckedChange = {
                                    onPrefsChange(prefs.copy(transportDocked = it))
                                },
                            )
                            SettingsSliderRow(
                                title = "播放组件离底距离",
                                valueLabel = String.format("%.0f", prefs.transportBottomInsetDp),
                                value = prefs.transportBottomInsetDp,
                                valueRange = PlayerDisplayPrefs.TRANSPORT_BOTTOM_INSET_MIN..
                                    PlayerDisplayPrefs.TRANSPORT_BOTTOM_INSET_MAX,
                                colors = sliderColors,
                                enabled = !prefs.transportDocked,
                                onValueChange = {
                                    onPrefsChange(prefs.copy(transportBottomInsetDp = it))
                                },
                            )
                            SettingsSwitchRow(
                                title = "黑胶选歌",
                                subtitle = "横屏长按黑胶进入扑克牌式选歌",
                                checked = prefs.vinylSongPickEnabled,
                                colors = switchColors,
                                onCheckedChange = {
                                    onPrefsChange(prefs.copy(vinylSongPickEnabled = it))
                                },
                            )
                            SettingsSwitchRow(
                                title = "黑胶绝对居中",
                                subtitle = "垂直对齐屏幕中心，忽略垂直偏移",
                                checked = prefs.vinylAbsoluteCenter,
                                colors = switchColors,
                                onCheckedChange = {
                                    onPrefsChange(prefs.copy(vinylAbsoluteCenter = it))
                                },
                            )
                            SettingsSwitchRow(
                                title = "完整封面",
                                subtitle = "封面铺满中心，隐藏轴心镂空",
                                checked = prefs.vinylFullCover,
                                colors = switchColors,
                                onCheckedChange = {
                                    onPrefsChange(prefs.copy(vinylFullCover = it))
                                },
                            )
                            SettingsSliderRow(
                                title = "黑胶大小（整体）",
                                valueLabel = String.format("%.0f%%", prefs.vinylSizeScale * 100f),
                                value = prefs.vinylSizeScale,
                                valueRange = PlayerDisplayPrefs.VINYL_SIZE_SCALE_MIN..
                                    PlayerDisplayPrefs.VINYL_SIZE_SCALE_MAX,
                                colors = sliderColors,
                                onValueChange = { onPrefsChange(prefs.copy(vinylSizeScale = it)) },
                            )
                            SettingsSliderRow(
                                title = "外圈黑胶半径",
                                valueLabel = String.format("%.0f%%", prefs.vinylOuterScale * 100f),
                                value = prefs.vinylOuterScale,
                                valueRange = PlayerDisplayPrefs.VINYL_OUTER_SCALE_MIN..
                                    PlayerDisplayPrefs.VINYL_OUTER_SCALE_MAX,
                                colors = sliderColors,
                                onValueChange = { onPrefsChange(prefs.copy(vinylOuterScale = it)) },
                            )
                            SettingsSliderRow(
                                title = "中心黑胶半径",
                                valueLabel = String.format(
                                    "基准 %.0f%%",
                                    prefs.vinylCenterRadiusFrac * 100f,
                                ),
                                value = prefs.vinylCenterRadiusFrac,
                                valueRange = PlayerDisplayPrefs.VINYL_CENTER_RADIUS_MIN..
                                    PlayerDisplayPrefs.VINYL_CENTER_RADIUS_MAX,
                                colors = sliderColors,
                                enabled = !prefs.vinylFullCover,
                                onValueChange = {
                                    onPrefsChange(prefs.copy(vinylCenterRadiusFrac = it))
                                },
                            )
                            SettingsVinylColorRow(
                                prefs = prefs,
                                onPrefsChange = onPrefsChange,
                                onOpenCustomEditor = onOpenVinylColorEditor,
                            )
                            SettingsSliderRow(
                                title = "黑胶阻尼",
                                valueLabel = String.format("%.2f", prefs.vinylGestureDamping),
                                value = prefs.vinylGestureDamping,
                                valueRange = PlayerDisplayPrefs.VINYL_GESTURE_DAMPING_MIN..
                                    PlayerDisplayPrefs.VINYL_GESTURE_DAMPING_MAX,
                                colors = sliderColors,
                                onValueChange = {
                                    onPrefsChange(prefs.copy(vinylGestureDamping = it))
                                },
                            )
                            SettingsSliderRow(
                                title = "黑胶水平位置",
                                valueLabel = String.format("%+.0f", prefs.vinylOffsetXDp),
                                value = prefs.vinylOffsetXDp,
                                valueRange = PlayerDisplayPrefs.VINYL_OFFSET_MIN..
                                    PlayerDisplayPrefs.VINYL_OFFSET_MAX,
                                colors = sliderColors,
                                onValueChange = { onPrefsChange(prefs.copy(vinylOffsetXDp = it)) },
                            )
                            SettingsSliderRow(
                                title = "黑胶垂直位置",
                                valueLabel = String.format("%+.0f", prefs.vinylOffsetYDp),
                                value = prefs.vinylOffsetYDp,
                                valueRange = PlayerDisplayPrefs.VINYL_OFFSET_MIN..
                                    PlayerDisplayPrefs.VINYL_OFFSET_MAX,
                                colors = sliderColors,
                                enabled = !prefs.vinylAbsoluteCenter,
                                onValueChange = { onPrefsChange(prefs.copy(vinylOffsetYDp = it)) },
                            )
                        }
                    }
                }
            }
        }

        transferHost.Overlay()
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun SettingsVinylColorRow(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onOpenCustomEditor: () -> Unit,
) {
    val styles = VinylColorStyle.entries
    val labels = listOf("黑色", "金色", "白色", "自选")
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val selected = prefs.vinylColorStyle
    val indicator = remember { Animatable(selected.ordinal.toFloat()) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    fun selectStyle(style: VinylColorStyle) {
        if (style != selected) {
            onPrefsChange(prefs.copy(vinylColorStyle = style))
        }
    }

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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "黑胶颜色",
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
                    text = "滑动切换预设 · 自选时点色环编辑",
                    style = TextStyle(
                        color = HintColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 0.3.sp,
                        shadow = TextShadow,
                    ),
                )
            }
            val previewBase = when (selected) {
                VinylColorStyle.BLACK -> Color(0xFF121214)
                VinylColorStyle.GOLD -> Color(0xFFB8860B)
                VinylColorStyle.WHITE -> Color(0xFFE8E8EC)
                VinylColorStyle.CUSTOM -> Color(prefs.vinylCustomBaseArgb)
            }
            val previewGroove = when (selected) {
                VinylColorStyle.BLACK -> Color.White.copy(alpha = 0.55f)
                VinylColorStyle.GOLD -> Color(0xFFFFF8E7)
                VinylColorStyle.WHITE -> Color(0xFF1A1A1E)
                VinylColorStyle.CUSTOM -> Color(prefs.vinylCustomGrooveArgb)
            }
            val customActive = selected == VinylColorStyle.CUSTOM
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(previewBase)
                    .border(1.5.dp, previewGroove.copy(alpha = 0.85f), CircleShape)
                    .then(
                        if (customActive) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenCustomEditor,
                            )
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(18.dp)) {
                    drawCircle(
                        color = previewGroove.copy(alpha = 0.35f),
                        radius = size.minDimension * 0.42f,
                        style = Stroke(width = 1.2f),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(styles.size) {
                    val segW = size.width / styles.size.toFloat()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragOffsetPx = 0f
                            scope.launch { indicator.stop() }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffsetPx += dragAmount
                            val live = (selected.ordinal + dragOffsetPx / segW)
                                .coerceIn(0f, (styles.lastIndex).toFloat())
                            scope.launch { indicator.snapTo(live) }
                        },
                        onDragEnd = {
                            val segWPx = size.width / styles.size.toFloat()
                            val idx = (selected.ordinal + dragOffsetPx / segWPx)
                                .roundToInt()
                                .coerceIn(0, styles.lastIndex)
                            dragOffsetPx = 0f
                            val next = styles[idx]
                            if (next != selected) {
                                onPrefsChange(prefs.copy(vinylColorStyle = next))
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
            val segW = maxWidth / styles.size
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
                styles.forEachIndexed { index, style ->
                    val active = selected == style
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { selectStyle(style) },
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

@SuppressLint("UnusedBoxWithConstraintsScope")
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
    titleAlpha: Float = 1f,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.graphicsLayer { alpha = titleAlpha.coerceIn(0f, 1f) },
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
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(SettingsRowBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
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
        Text(
            text = actionLabel,
            style = TextStyle(
                color = Accent.copy(alpha = 0.95f),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            ),
        )
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
    /** 非空时：交互期间通知预览态（松手 / 点选结束） */
    onPreviewDragActiveChange: ((Boolean) -> Unit)? = null,
) {
    val titleColor = if (enabled) LabelColor else LabelColor.copy(alpha = 0.38f)
    val valueColor = if (enabled) Accent.copy(alpha = 0.95f) else Accent.copy(alpha = 0.35f)
    val sliderIx = remember { MutableInteractionSource() }
    val safeValue = value
        .takeIf { it.isFinite() }
        ?.coerceIn(valueRange.start, valueRange.endInclusive)
        ?: valueRange.start
    // Material3 Slider 的 dragged 态不一定可靠；用 value 变化 + finished 驱动预览淡出
    var previewArmed by remember { mutableStateOf(false) }
    val onPreviewUpdated by rememberUpdatedState(onPreviewDragActiveChange)

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
            value = safeValue,
            onValueChange = { next ->
                val clamped = next
                    .takeIf { it.isFinite() }
                    ?.coerceIn(valueRange.start, valueRange.endInclusive)
                    ?: return@Slider
                val previewCb = onPreviewUpdated
                if (previewCb != null && !previewArmed) {
                    previewArmed = true
                    previewCb(true)
                }
                onValueChange(clamped)
            },
            onValueChangeFinished = {
                if (previewArmed) {
                    previewArmed = false
                    onPreviewUpdated?.invoke(false)
                }
            },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = colors,
            interactionSource = sliderIx,
            modifier = Modifier.fillMaxWidth(),
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = sliderIx,
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
