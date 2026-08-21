@file:OptIn(ExperimentalMaterial3Api::class)

package com.kite.zmusic.ui.player

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PreviewLyricAlign
import com.kite.zmusic.data.TitleAlignMode
import com.kite.zmusic.data.VinylColorStyle
import com.kite.zmusic.ui.main.MainControls
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.pageSheetHazeStyle
import com.kite.zmusic.ui.theme.MainSlider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

private val IconTint = Color(0xFFD5DEE8)
/** 右上 chrome 图标尺寸；间距取宽度 1/3。 */
internal val NowPlayingChromeIconWidth = 40.dp
internal val NowPlayingChromeIconHeight = 34.dp
internal val NowPlayingChromeIconGap = NowPlayingChromeIconWidth / 3
/** 比播放条整条圆角更轻 */
private val ChromeShape = RoundedCornerShape(8.dp)
private val PanelShape = RoundedCornerShape(18.dp)
private val RowShape = RoundedCornerShape(14.dp)
/** 与底部播放条一致：半透明黑底，无描边。 */
private val ChromeBarBg = Color.Black.copy(alpha = 0.22f)

private data class SettingsPanelChrome(
    val light: Boolean,
    val label: Color,
    val hint: Color,
    val accent: Color,
    val rowBg: Color,
    val titleShadow: Shadow?,
)

private fun sheetSettingsChrome() = SettingsPanelChrome(
    light = true,
    label = MainPalette.Ink,
    hint = MainPalette.Secondary,
    accent = MainPalette.Accent,
    rowBg = MainPalette.Card,
    titleShadow = null,
)

private val LocalSettingsChrome = staticCompositionLocalOf { sheetSettingsChrome() }

/** 拖动布局相关滑条时，面板其余部分淡出以便预览真实效果。 */
private enum class SettingsPreviewKey {
    LineSpacing,
    OffsetX,
    LyricOffsetY,
    UiScale,
    VinylSize,
    VinylOffsetY,
    TransportOffsetY,
    LyricBackgroundTransparency,
    PreviewLyric,
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
 * [chromeBackground] 为 false 时：细、小巧、中空，无圆角底（竖屏底栏）。
 */
@Composable
fun NowPlayingSettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chromeBackground: Boolean = true,
) {
    val iconSize = if (chromeBackground) 18.dp else 15.dp
    val icon: @Composable () -> Unit = {
        Canvas(Modifier.size(iconSize)) {
            val strokeW = size.minDimension * if (chromeBackground) 0.11f else 0.075f
            val stroke = Stroke(
                width = strokeW,
                cap = StrokeCap.Round,
            )
            val w = size.width
            val h = size.height
            val trackLen = w * if (chromeBackground) 0.72f else 0.68f
            val left = (w - trackLen) / 2f
            val knobR = size.minDimension * if (chromeBackground) 0.11f else 0.095f
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
                val knobCenter = Offset(left + trackLen * knobs[i], y)
                if (chromeBackground) {
                    drawCircle(color = IconTint, radius = knobR, center = knobCenter)
                } else {
                    drawCircle(
                        color = IconTint,
                        radius = knobR,
                        center = knobCenter,
                        style = Stroke(width = strokeW * 0.92f, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
    if (chromeBackground) {
        ChromeIconShell(onClick = onClick, modifier = modifier, content = icon)
    } else {
        Box(
            modifier
                .size(width = 32.dp, height = 28.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
            content = { icon() },
        )
    }
}

/**
 * 退出全屏播放：向下尖角（类似「>」顺时针 90°），夹角略开于直角以便辨认。
 * [chromeBackground] 为 false 时仅保留图标，不绘制圆角矩形底。
 */
@Composable
fun NowPlayingDismissIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chromeBackground: Boolean = true,
) {
    val icon: @Composable () -> Unit = {
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
    if (chromeBackground) {
        ChromeIconShell(onClick = onClick, modifier = modifier, content = icon)
    } else {
        Box(
            modifier
                .size(width = NowPlayingChromeIconWidth, height = NowPlayingChromeIconHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
            content = { icon() },
        )
    }
}

/**
 * 旋转控制图标（对齐 Material Symbols：screen_rotation / screen_lock_rotation / screen_rotation_alt）：
 * - [forceToLandscape] == null：会话锁（自动双弧 / 锁定+锁标）
 * - true / false：确定旋转到横/竖（目标机身 + 对称双弧）
 * [chromeBackground] 为 false 时仅保留图标，不绘制圆角矩形底。
 * [tint] 默认播放器浅色；浅底首页传入深色。
 */
@Composable
fun NowPlayingRotationLockButton(
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    forceToLandscape: Boolean? = null,
    chromeBackground: Boolean = true,
    tint: Color = IconTint,
) {
    val icon: @Composable () -> Unit = {
        Canvas(Modifier.size(22.dp)) {
            val stroke = Stroke(
                width = size.minDimension * 0.085f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            when (forceToLandscape) {
                true -> drawMdForceRotateIcon(toLandscape = true, color = tint, stroke = stroke)
                false -> drawMdForceRotateIcon(toLandscape = false, color = tint, stroke = stroke)
                null -> if (locked) {
                    drawMdSessionLockIcon(locked = true, color = tint, stroke = stroke)
                } else {
                    drawMdSessionLockIcon(locked = false, color = tint, stroke = stroke)
                }
            }
        }
    }
    if (chromeBackground) {
        ChromeIconShell(onClick = onClick, modifier = modifier, content = icon)
    } else {
        Box(
            modifier
                .size(width = NowPlayingChromeIconWidth, height = NowPlayingChromeIconHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
            content = { icon() },
        )
    }
}

/**
 * 会话锁图标：
 * - 自动（locked=false）：恢复智能分支前的原版（直立机身 + 对角双弧），勿用 A 情况新图渗入
 * - 锁定（locked=true）：直立机身 + 右下锁
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMdSessionLockIcon(
    locked: Boolean,
    color: Color,
    stroke: Stroke,
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val sw = stroke.width

    if (locked) {
        val s = size.minDimension
        val phoneW = s * 0.34f
        val phoneH = s * 0.56f
        val left = cx - phoneW / 2f - s * 0.05f
        val top = cy - phoneH / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(phoneW, phoneH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.06f, s * 0.06f),
            style = stroke,
        )
        drawLine(
            color,
            Offset(left + phoneW * 0.30f, top + phoneH * 0.13f),
            Offset(left + phoneW * 0.70f, top + phoneH * 0.13f),
            sw * 0.9f,
            StrokeCap.Round,
        )
        val lx = cx + s * 0.28f
        val ly = cy + s * 0.04f
        val bw = s * 0.26f
        val bh = s * 0.20f
        drawRoundRect(
            color = color,
            topLeft = Offset(lx - bw / 2f, ly),
            size = androidx.compose.ui.geometry.Size(bw, bh),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.035f, s * 0.035f),
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(lx - bw * 0.32f, ly - bh * 0.82f),
            size = androidx.compose.ui.geometry.Size(bw * 0.64f, bh),
            style = stroke,
        )
    } else {
        // B·自动：修改智能分支前的原版 screen_rotation（直立小机身 + 粗双弧）
        val phoneW = w * 0.30f
        val phoneH = h * 0.50f
        val phoneLeft = cx - phoneW / 2f
        val phoneTop = cy - phoneH / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(phoneLeft, phoneTop),
            size = androidx.compose.ui.geometry.Size(phoneW, phoneH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.065f, w * 0.065f),
            style = stroke,
        )
        drawLine(
            color,
            Offset(cx - phoneW * 0.2f, phoneTop + phoneH * 0.16f),
            Offset(cx + phoneW * 0.2f, phoneTop + phoneH * 0.16f),
            sw * 0.85f,
            StrokeCap.Round,
        )
        val arcR = w * 0.44f
        drawArc(
            color = color,
            startAngle = -48f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(cx - arcR, cy - arcR),
            size = androidx.compose.ui.geometry.Size(arcR * 2f, arcR * 2f),
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = 132f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(cx - arcR, cy - arcR),
            size = androidx.compose.ui.geometry.Size(arcR * 2f, arcR * 2f),
            style = stroke,
        )
        val a1 = Math.toRadians(52.0)
        val t1 = Offset(cx + arcR * cos(a1).toFloat(), cy + arcR * sin(a1).toFloat())
        drawRotationArrowHead(t1, angleDeg = 52f + 90f, color = color, sizePx = sw * 2.4f)
        val a2 = Math.toRadians(232.0)
        val t2 = Offset(cx + arcR * cos(a2).toFloat(), cy + arcR * sin(a2).toFloat())
        drawRotationArrowHead(t2, angleDeg = 232f + 90f, color = color, sizePx = sw * 2.4f)
    }
}

/** A 情况图标内容相对画布的等比缩放（触控/外框尺寸不变）。 */
private const val ForceRotateIconContentScale = 0.78f

/**
 * Material screen_rotation_alt 语义：目标方向直立/横置机身 + 对称双弧。
 * 内容等比缩小，Canvas / chrome 外壳尺寸不变。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMdForceRotateIcon(
    toLandscape: Boolean,
    color: Color,
    stroke: Stroke,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    scale(scale = ForceRotateIconContentScale, pivot = Offset(cx, cy)) {
        val s = size.minDimension
        val sw = stroke.width
        val phoneW = if (toLandscape) s * 0.50f else s * 0.30f
        val phoneH = if (toLandscape) s * 0.32f else s * 0.48f
        drawRoundRect(
            color = color,
            topLeft = Offset(cx - phoneW / 2f, cy - phoneH / 2f),
            size = androidx.compose.ui.geometry.Size(phoneW, phoneH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.055f, s * 0.055f),
            style = stroke,
        )
        if (toLandscape) {
            drawLine(
                color,
                Offset(cx - phoneW * 0.38f, cy - phoneH * 0.18f),
                Offset(cx - phoneW * 0.38f, cy + phoneH * 0.18f),
                sw * 0.85f,
                StrokeCap.Round,
            )
        } else {
            drawLine(
                color,
                Offset(cx - phoneW * 0.22f, cy - phoneH * 0.32f),
                Offset(cx + phoneW * 0.22f, cy - phoneH * 0.32f),
                sw * 0.85f,
                StrokeCap.Round,
            )
        }
        drawMdRotationArcs(color = color, stroke = stroke, radiusFrac = 0.48f)
    }
}

/** Material 对角双旋转弧 + 箭头尖。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMdRotationArcs(
    color: Color,
    stroke: Stroke,
    radiusFrac: Float,
) {
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = s * radiusFrac
    val box = androidx.compose.ui.geometry.Size(r * 2f, r * 2f)
    val origin = Offset(cx - r, cy - r)

    // 右上弧
    drawArc(
        color = color,
        startAngle = -70f,
        sweepAngle = 95f,
        useCenter = false,
        topLeft = origin,
        size = box,
        style = stroke,
    )
    val a1 = Math.toRadians(25.0)
    drawRotationArrowHead(
        tip = Offset(cx + r * cos(a1).toFloat(), cy + r * sin(a1).toFloat()),
        angleDeg = 25f + 90f,
        color = color,
        sizePx = stroke.width * 2.35f,
    )

    // 左下弧
    drawArc(
        color = color,
        startAngle = 110f,
        sweepAngle = 95f,
        useCenter = false,
        topLeft = origin,
        size = box,
        style = stroke,
    )
    val a2 = Math.toRadians(205.0)
    drawRotationArrowHead(
        tip = Offset(cx + r * cos(a2).toFloat(), cy + r * sin(a2).toFloat()),
        angleDeg = 205f + 90f,
        color = color,
        sizePx = stroke.width * 2.35f,
    )
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
 * 播放设置面板：与「更多」同一套磨砂壳 + 卡片行色。
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
    transferDismissGate: PlayerDisplayTransferDismissGate? = null,
    /** 横屏：导入/导出；竖屏底部面板关闭。 */
    showTransferActions: Boolean = true,
    /** 竖屏：仅悬浮标题名，无 SETTINGS 眉题。 */
    titleOnlyHeader: Boolean = false,
    /** 面板主标题（竖屏可改为「竖屏显示」以示隔离）。 */
    headerTitle: String = "播放显示",
    panelShape: RoundedCornerShape = PanelShape,
    /** 磨砂模糊半径；竖屏可加大以增强玻璃感。 */
    glassBlurRadius: Dp = 84.dp,
    /** 是否启用实时磨砂；关闭时用静态玻璃层（竖屏弹出更流畅）。 */
    enableRealtimeHaze: Boolean = true,
    /** 横屏悬浮卡与曲谱同壳画 Hairline；竖屏底栏面板不要外描边。 */
    showPanelBorder: Boolean = false,
    /** 竖屏内容：仅竖屏相关项，不含横屏氛围/黑胶/标题对齐等。 */
    portraitContent: Boolean = false,
    /** 竖屏：打开自定义背景编辑器 */
    onOpenCustomBackgroundEditor: () -> Unit = {},
    /** 顶部中央拉取条；竖屏底部面板开启。 */
    showDragHandle: Boolean = false,
    onDragHandleVertical: ((dragAmountPx: Float) -> Unit)? = null,
    onDragHandleEnd: (() -> Unit)? = null,
) {
    val resolvedTransferGate = transferDismissGate ?: remember { PlayerDisplayTransferDismissGate() }
    val transferHost = rememberPlayerDisplayTransferHost(
        prefs = prefs,
        onPrefsChange = onPrefsChange,
        hazeState = hazeState,
        dismissGate = resolvedTransferGate,
    )
    val chrome = sheetSettingsChrome()
    val switchColors = MainControls.switchColors()

    var previewKey by remember { mutableStateOf<SettingsPreviewKey?>(null) }
    var focusKey by remember { mutableStateOf<SettingsPreviewKey?>(null) }
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
    fun rowAlpha(key: SettingsPreviewKey): Float =
        if (key == focusKey) focusAlpha.value else dim

    fun onPreviewDrag(key: SettingsPreviewKey, active: Boolean) {
        if (active) {
            previewKey = key
        } else if (previewKey == key) {
            previewKey = null
        }
    }

    val scrollState = rememberScrollState()

    // hazeNonce：仅重挂磨砂层，保留滚动与控件状态
    CompositionLocalProvider(LocalSettingsChrome provides chrome) {
    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(panelShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        key(hazeNonce) {
            if (enableRealtimeHaze) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = dim }
                        .hazeEffect(state = hazeState, style = pageSheetHazeStyle()) {
                            blurRadius = glassBlurRadius
                        },
                )
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = dim }
                        .background(MainPalette.Page.copy(alpha = 0.96f)),
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = dim }
                .background(MainPalette.SheetWash),
        )
        if (showPanelBorder) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(1.dp, MainPalette.Hairline, panelShape),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (showDragHandle) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    onDragHandleVertical?.invoke(dragAmount)
                                },
                                onDragEnd = { onDragHandleEnd?.invoke() },
                                onDragCancel = { onDragHandleEnd?.invoke() },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MainPalette.Hint),
                    )
                }
            }
            if (!titleOnlyHeader) {
                Text(
                    text = "SETTINGS",
                    modifier = Modifier.graphicsLayer { alpha = dim },
                    style = TextStyle(
                        color = chrome.accent.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp,
                    ),
                )
                Spacer(Modifier.height(4.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = dim },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headerTitle,
                    style = TextStyle(
                        color = chrome.label,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.2).sp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (showTransferActions) {
                    PlayerDisplayTransferHeaderIcons(
                        host = transferHost,
                        iconTint = chrome.label,
                    )
                }
            }
            Spacer(Modifier.height(if (portraitContent) 12.dp else 14.dp))

            if (portraitContent) {
                // 竖屏专用项（与横屏设置隔离）；预览淡出与横屏同一套 chromeAlpha / focusAlpha
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
                                    title = "自定义背景",
                                    subtitle = "开启后可配置并启用全屏沉浸背景",
                                    checked = prefs.customBackgroundEnabled,
                                    colors = switchColors,
                                    onCheckedChange = {
                                        onPrefsChange(prefs.copy(customBackgroundEnabled = it))
                                    },
                                )
                                SettingsActionRow(
                                    title = "背景调控",
                                    subtitle = if (prefs.customBackgroundEnabled) {
                                        "5 预设 · 上传 / 定位 / 锁定"
                                    } else {
                                        "先开启自定义背景"
                                    },
                                    actionLabel = "编辑",
                                    enabled = prefs.customBackgroundEnabled,
                                    onClick = onOpenCustomBackgroundEditor,
                                )
                                SettingsActionRow(
                                    title = "歌词样式",
                                    subtitle = "字号 / 斜体 / 粗体 / 颜色 · 条数与间距",
                                    actionLabel = "编辑",
                                    onClick = onOpenLyricStyleEditor,
                                )
                                SettingsSwitchRow(
                                    title = "自动播放",
                                    subtitle = "点选歌词跳转后自动开始播放；播放中切歌始终播放",
                                    checked = prefs.lyricTapAutoPlay,
                                    colors = switchColors,
                                    onCheckedChange = {
                                        onPrefsChange(prefs.copy(lyricTapAutoPlay = it))
                                    },
                                )
                                SettingsSwitchRow(
                                    title = "启用预览歌词",
                                    subtitle = "封面态进度条上方显示当前与待播歌词",
                                    checked = prefs.portraitPreviewLyricEnabled,
                                    colors = switchColors,
                                    onCheckedChange = {
                                        onPrefsChange(prefs.copy(portraitPreviewLyricEnabled = it))
                                    },
                                )
                            }
                        }
                        val previewOn = prefs.portraitPreviewLyricEnabled
                        SettingsAlpha(rowAlpha(SettingsPreviewKey.PreviewLyric)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSliderRow(
                                    title = "预览歌词数",
                                    valueLabel = prefs.portraitPreviewLyricCount.toString(),
                                    value = prefs.portraitPreviewLyricCount.toFloat(),
                                    valueRange = PlayerDisplayPrefs.PREVIEW_LYRIC_COUNT_MIN.toFloat()..
                                        PlayerDisplayPrefs.PREVIEW_LYRIC_COUNT_MAX.toFloat(),
                                    steps = PlayerDisplayPrefs.PREVIEW_LYRIC_COUNT_MAX -
                                        PlayerDisplayPrefs.PREVIEW_LYRIC_COUNT_MIN - 1,
                                    enabled = previewOn,
                                    onValueChange = {
                                        onPrefsChange(
                                            prefs.copy(
                                                portraitPreviewLyricCount = it.roundToInt()
                                                    .coerceIn(
                                                        PlayerDisplayPrefs.PREVIEW_LYRIC_COUNT_MIN,
                                                        PlayerDisplayPrefs.PREVIEW_LYRIC_COUNT_MAX,
                                                    ),
                                            ),
                                        )
                                    },
                                    onPreviewDragActiveChange = {
                                        onPreviewDrag(SettingsPreviewKey.PreviewLyric, it)
                                    },
                                )
                                SettingsSliderRow(
                                    title = "播放中字号",
                                    valueLabel = String.format(
                                        "%.0f",
                                        prefs.portraitPreviewLyricPlayingFontSp,
                                    ),
                                    value = prefs.portraitPreviewLyricPlayingFontSp,
                                    valueRange = PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MIN..
                                        PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MAX,
                                    enabled = previewOn,
                                    onValueChange = {
                                        onPrefsChange(
                                            prefs.copy(portraitPreviewLyricPlayingFontSp = it),
                                        )
                                    },
                                    onPreviewDragActiveChange = {
                                        onPreviewDrag(SettingsPreviewKey.PreviewLyric, it)
                                    },
                                )
                                SettingsSliderRow(
                                    title = "待播放字号",
                                    valueLabel = String.format(
                                        "%.0f",
                                        prefs.portraitPreviewLyricUpcomingFontSp,
                                    ),
                                    value = prefs.portraitPreviewLyricUpcomingFontSp,
                                    valueRange = PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MIN..
                                        PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MAX,
                                    enabled = previewOn,
                                    onValueChange = {
                                        onPrefsChange(
                                            prefs.copy(portraitPreviewLyricUpcomingFontSp = it),
                                        )
                                    },
                                    onPreviewDragActiveChange = {
                                        onPreviewDrag(SettingsPreviewKey.PreviewLyric, it)
                                    },
                                )
                                SettingsPreviewColorRow(
                                    title = "播放中颜色",
                                    argb = prefs.portraitPreviewLyricPlayingArgb,
                                    enabled = previewOn,
                                    onArgbChange = {
                                        onPrefsChange(
                                            prefs.copy(portraitPreviewLyricPlayingArgb = it),
                                        )
                                    },
                                )
                                SettingsPreviewColorRow(
                                    title = "待播放歌词颜色",
                                    argb = prefs.portraitPreviewLyricUpcomingArgb,
                                    enabled = previewOn,
                                    onArgbChange = {
                                        onPrefsChange(
                                            prefs.copy(portraitPreviewLyricUpcomingArgb = it),
                                        )
                                    },
                                )
                                SettingsSwitchRow(
                                    title = "精美动画",
                                    subtitle = "开启后切句动画与歌词页一致，并尊重逐字渲染",
                                    checked = prefs.portraitPreviewLyricFancy,
                                    colors = switchColors,
                                    enabled = previewOn,
                                    onCheckedChange = {
                                        onPrefsChange(
                                            prefs.copy(portraitPreviewLyricFancy = it),
                                        )
                                    },
                                )
                                SettingsPreviewAlignRow(
                                    selected = prefs.portraitPreviewLyricAlign,
                                    enabled = previewOn,
                                    onSelect = {
                                        onPrefsChange(
                                            prefs.copy(portraitPreviewLyricAlign = it),
                                        )
                                    },
                                    onPreviewActiveChange = {
                                        onPreviewDrag(SettingsPreviewKey.PreviewLyric, it)
                                    },
                                )
                                SettingsSliderRow(
                                    title = "预览歌词垂直位置",
                                    valueLabel = String.format(
                                        "%+.0f",
                                        prefs.portraitPreviewLyricOffsetYDp,
                                    ),
                                    value = prefs.portraitPreviewLyricOffsetYDp,
                                    valueRange = PlayerDisplayPrefs.PREVIEW_LYRIC_OFFSET_Y_MIN..
                                        PlayerDisplayPrefs.PREVIEW_LYRIC_OFFSET_Y_MAX,
                                    enabled = previewOn,
                                    onValueChange = {
                                        onPrefsChange(
                                            prefs.copy(portraitPreviewLyricOffsetYDp = it),
                                        )
                                    },
                                    onPreviewDragActiveChange = {
                                        onPreviewDrag(SettingsPreviewKey.PreviewLyric, it)
                                    },
                                )
                                SettingsSliderRow(
                                    title = "预览歌词行间距",
                                    valueLabel = String.format(
                                        "%.0f",
                                        prefs.portraitPreviewLyricLineSpacingDp,
                                    ),
                                    value = prefs.portraitPreviewLyricLineSpacingDp,
                                    valueRange = PlayerDisplayPrefs.PREVIEW_LYRIC_LINE_SPACING_MIN..
                                        PlayerDisplayPrefs.PREVIEW_LYRIC_LINE_SPACING_MAX,
                                    enabled = previewOn,
                                    onValueChange = {
                                        onPrefsChange(
                                            prefs.copy(portraitPreviewLyricLineSpacingDp = it),
                                        )
                                    },
                                    onPreviewDragActiveChange = {
                                        onPreviewDrag(SettingsPreviewKey.PreviewLyric, it)
                                    },
                                )
                            }
                        }
                        SettingsAlpha(rowAlpha(SettingsPreviewKey.LyricOffsetY)) {
                            SettingsSliderRow(
                                title = "歌词垂直位置",
                                valueLabel = String.format("%+.0f", prefs.lyricOffsetYDp),
                                value = prefs.lyricOffsetYDp,
                                valueRange = PlayerDisplayPrefs.LYRIC_OFFSET_MIN..
                                    PlayerDisplayPrefs.LYRIC_OFFSET_MAX,
                                onValueChange = {
                                    onPrefsChange(prefs.copy(lyricOffsetYDp = it))
                                },
                                onPreviewDragActiveChange = {
                                    onPreviewDrag(SettingsPreviewKey.LyricOffsetY, it)
                                },
                            )
                        }
                        SettingsAlpha(rowAlpha(SettingsPreviewKey.LyricBackgroundTransparency)) {
                            SettingsSliderRow(
                                title = "歌词页背景透明度",
                                valueLabel = String.format(
                                    "%.0f%%",
                                    prefs.lyricBackgroundTransparency * 100f,
                                ),
                                value = prefs.lyricBackgroundTransparency,
                                valueRange = PlayerDisplayPrefs.LYRIC_BG_TRANSPARENCY_MIN..
                                    PlayerDisplayPrefs.LYRIC_BG_TRANSPARENCY_MAX,
                                onValueChange = {
                                    onPrefsChange(prefs.copy(lyricBackgroundTransparency = it))
                                },
                                onPreviewDragActiveChange = {
                                    onPreviewDrag(SettingsPreviewKey.LyricBackgroundTransparency, it)
                                },
                            )
                        }
                        SettingsAlpha(dim) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSwitchRow(
                                    title = "活跃光晕",
                                    subtitle = if (prefs.customBackgroundEnabled) {
                                        "自定义背景开启时不可用"
                                    } else {
                                        "低/中/高互斥高亮，同时仅一球发光，运动略加快"
                                    },
                                    checked = prefs.activeHalo,
                                    colors = switchColors,
                                    enabled = !prefs.customBackgroundEnabled,
                                    onCheckedChange = {
                                        onPrefsChange(prefs.copy(activeHalo = it))
                                    },
                                )
                            }
                        }
                    }
                    SettingsCategory(title = "个性化", titleAlpha = dim) {
                        SettingsAlpha(dim) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSwitchRow(
                                    title = "歌词页自动清屏",
                                    subtitle = "无操作后隐藏所选区域；清屏时歌词会在整屏垂直居中",
                                    checked = prefs.portraitLyricAutoClear,
                                    colors = switchColors,
                                    onCheckedChange = { on ->
                                        onPrefsChange(prefs.copy(portraitLyricAutoClear = on))
                                    },
                                )
                                SettingsSliderRow(
                                    title = "清屏时间",
                                    valueLabel = "${prefs.portraitLyricAutoClearSeconds} 秒",
                                    value = prefs.portraitLyricAutoClearSeconds.toFloat(),
                                    valueRange = PlayerDisplayPrefs.AUTO_CLEAR_SECONDS_MIN.toFloat()..
                                        PlayerDisplayPrefs.AUTO_CLEAR_SECONDS_MAX.toFloat(),
                                    steps = PlayerDisplayPrefs.AUTO_CLEAR_SECONDS_MAX -
                                        PlayerDisplayPrefs.AUTO_CLEAR_SECONDS_MIN - 1,
                                    enabled = prefs.portraitLyricAutoClear,
                                    onValueChange = {
                                        onPrefsChange(
                                            prefs.copy(
                                                portraitLyricAutoClearSeconds = it.roundToInt(),
                                            ),
                                        )
                                    },
                                )
                                SettingsAutoClearTargetsRow(
                                    top = prefs.portraitLyricAutoClearTop,
                                    transport = prefs.portraitLyricAutoClearTransport,
                                    toolbar = prefs.portraitLyricAutoClearToolbar,
                                    enabled = prefs.portraitLyricAutoClear,
                                    onChange = { top, transport, toolbar ->
                                        onPrefsChange(
                                            prefs.copy(
                                                portraitLyricAutoClearTop = top,
                                                portraitLyricAutoClearTransport = transport,
                                                portraitLyricAutoClearToolbar = toolbar,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    SettingsCategory(title = "黑胶", titleAlpha = dim) {
                        SettingsAlpha(dim) {
                            SettingsSwitchRow(
                                title = "完整封面",
                                subtitle = "封面铺满中心，隐藏轴心镂空",
                                checked = prefs.vinylFullCover,
                                colors = switchColors,
                                onCheckedChange = {
                                    onPrefsChange(prefs.copy(vinylFullCover = it))
                                },
                            )
                        }
                        SettingsAlpha(rowAlpha(SettingsPreviewKey.VinylSize)) {
                            SettingsSliderRow(
                                title = "黑胶大小（整体）",
                                valueLabel = String.format("%.0f%%", prefs.vinylSizeScale * 100f),
                                value = prefs.vinylSizeScale,
                                valueRange = PlayerDisplayPrefs.VINYL_SIZE_SCALE_MIN..
                                    PlayerDisplayPrefs.VINYL_SIZE_SCALE_MAX,
                                onValueChange = {
                                    onPrefsChange(prefs.copy(vinylSizeScale = it))
                                },
                                onPreviewDragActiveChange = {
                                    onPreviewDrag(SettingsPreviewKey.VinylSize, it)
                                },
                            )
                        }
                        SettingsAlpha(rowAlpha(SettingsPreviewKey.VinylOffsetY)) {
                            SettingsSliderRow(
                                title = "黑胶垂直位置",
                                valueLabel = String.format("%+.0f", prefs.vinylOffsetYDp),
                                value = prefs.vinylOffsetYDp,
                                valueRange = PlayerDisplayPrefs.VINYL_OFFSET_Y_MIN..
                                    PlayerDisplayPrefs.VINYL_OFFSET_Y_MAX,
                                onValueChange = {
                                    onPrefsChange(prefs.copy(vinylOffsetYDp = it))
                                },
                                onPreviewDragActiveChange = {
                                    onPreviewDrag(SettingsPreviewKey.VinylOffsetY, it)
                                },
                            )
                        }
                    }
                    SettingsCategory(title = "布局", titleAlpha = dim) {
                        SettingsAlpha(rowAlpha(SettingsPreviewKey.UiScale)) {
                            SettingsSliderRow(
                                title = "整体 UI 缩放",
                                valueLabel = String.format("%.0f%%", prefs.uiScale * 100f),
                                value = prefs.uiScale,
                                valueRange = PlayerDisplayPrefs.UI_MIN..PlayerDisplayPrefs.UI_MAX,
                                onValueChange = {
                                    onPrefsChange(prefs.copy(uiScale = it))
                                },
                                onPreviewDragActiveChange = {
                                    onPreviewDrag(SettingsPreviewKey.UiScale, it)
                                },
                            )
                        }
                        SettingsAlpha(rowAlpha(SettingsPreviewKey.TransportOffsetY)) {
                            SettingsSliderRow(
                                title = "播放控件垂直位置",
                                valueLabel = String.format("%+.0f", prefs.portraitTransportOffsetYDp),
                                value = prefs.portraitTransportOffsetYDp,
                                valueRange = PlayerDisplayPrefs.PORTRAIT_TRANSPORT_OFFSET_Y_MIN..
                                    PlayerDisplayPrefs.PORTRAIT_TRANSPORT_OFFSET_Y_MAX,
                                onValueChange = {
                                    onPrefsChange(prefs.copy(portraitTransportOffsetYDp = it))
                                },
                                onPreviewDragActiveChange = {
                                    onPreviewDrag(SettingsPreviewKey.TransportOffsetY, it)
                                },
                            )
                        }
                        SettingsAlpha(dim) {
                            SettingsSwitchRow(
                                title = "容器包含",
                                subtitle = "半透明底包裹控件；留边与内边距，避免贴边",
                                checked = prefs.portraitTransportContainerInclude,
                                colors = switchColors,
                                onCheckedChange = {
                                    onPrefsChange(
                                        prefs.copy(portraitTransportContainerInclude = it),
                                    )
                                },
                            )
                        }
                    }
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .consumeUnclaimedVerticalDrag()
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
                                onValueChange = { onPrefsChange(prefs.copy(titleOffsetYDp = it)) },
                            )
                        }
                    }
                    SettingsAlpha(rowAlpha(SettingsPreviewKey.LineSpacing)) {
                        SettingsSliderRow(
                            title = "歌词行间距",
                            valueLabel = String.format("%.0f", prefs.lyricLineSpacingDp),
                            value = prefs.lyricLineSpacingDp,
                            valueRange = PlayerDisplayPrefs.LINE_SPACING_MIN..PlayerDisplayPrefs.LINE_SPACING_MAX,
                            onValueChange = { onPrefsChange(prefs.copy(lyricLineSpacingDp = it)) },
                            onPreviewDragActiveChange = {
                                onPreviewDrag(SettingsPreviewKey.LineSpacing, it)
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
                    SettingsAlpha(rowAlpha(SettingsPreviewKey.OffsetX)) {
                        SettingsSliderRow(
                            title = "歌词水平位置",
                            valueLabel = String.format("%+.0f", prefs.lyricOffsetXDp),
                            value = prefs.lyricOffsetXDp,
                            valueRange = PlayerDisplayPrefs.LYRIC_OFFSET_MIN..
                                PlayerDisplayPrefs.LYRIC_OFFSET_MAX,
                            onValueChange = { onPrefsChange(prefs.copy(lyricOffsetXDp = it)) },
                            onPreviewDragActiveChange = {
                                onPreviewDrag(SettingsPreviewKey.OffsetX, it)
                            },
                        )
                    }
                }

                SettingsCategory(title = "布局", titleAlpha = dim) {
                    SettingsAlpha(rowAlpha(SettingsPreviewKey.UiScale)) {
                        SettingsSliderRow(
                            title = "整体 UI 缩放",
                            valueLabel = String.format("%.0f%%", prefs.uiScale * 100f),
                            value = prefs.uiScale,
                            valueRange = PlayerDisplayPrefs.UI_MIN..PlayerDisplayPrefs.UI_MAX,
                            onValueChange = { onPrefsChange(prefs.copy(uiScale = it)) },
                            onPreviewDragActiveChange = {
                                onPreviewDrag(SettingsPreviewKey.UiScale, it)
                            },
                        )
                    }
                    SettingsAlpha(dim) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                onValueChange = { onPrefsChange(prefs.copy(vinylSizeScale = it)) },
                            )
                            SettingsSliderRow(
                                title = "外圈黑胶半径",
                                valueLabel = String.format("%.0f%%", prefs.vinylOuterScale * 100f),
                                value = prefs.vinylOuterScale,
                                valueRange = PlayerDisplayPrefs.VINYL_OUTER_SCALE_MIN..
                                    PlayerDisplayPrefs.VINYL_OUTER_SCALE_MAX,
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
                                onValueChange = { onPrefsChange(prefs.copy(vinylOffsetXDp = it)) },
                            )
                            SettingsSliderRow(
                                title = "黑胶垂直位置",
                                valueLabel = String.format("%+.0f", prefs.vinylOffsetYDp),
                                value = prefs.vinylOffsetYDp,
                                valueRange = PlayerDisplayPrefs.VINYL_OFFSET_Y_MIN..
                                    PlayerDisplayPrefs.VINYL_OFFSET_Y_MAX,
                                enabled = !prefs.vinylAbsoluteCenter,
                                onValueChange = { onPrefsChange(prefs.copy(vinylOffsetYDp = it)) },
                            )
                        }
                    }
                }
            }
            } // if (portraitContent) else landscape
        }

        if (showTransferActions) {
            transferHost.Overlay()
        }
    }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun SettingsVinylColorRow(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onOpenCustomEditor: () -> Unit,
) {
    val chrome = LocalSettingsChrome.current
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
            .background(chrome.rowBg)
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
                        color = chrome.label,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "滑动切换预设 · 自选时点色环编辑",
                    style = TextStyle(
                        color = chrome.hint,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
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
                .background(MainPalette.TrackOff)
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
                    .background(chrome.accent.copy(alpha = 0.16f))
                    .border(
                        width = 1.dp,
                        color = chrome.accent.copy(alpha = 0.35f),
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
                                    chrome.accent
                                } else {
                                    chrome.hint
                                },
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 12.sp,
                                letterSpacing = 0.2.sp,
                                textAlign = TextAlign.Center,
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
    val chrome = LocalSettingsChrome.current
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
            .background(chrome.rowBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "标题对齐位置",
            style = TextStyle(
                color = chrome.label,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "歌名 / 制作人 / 歌单 · 滑动或点选切换",
            style = TextStyle(
                color = chrome.hint,
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MainPalette.TrackOff)
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
                    .background(chrome.accent.copy(alpha = 0.16f))
                    .border(
                        width = 1.dp,
                        color = chrome.accent.copy(alpha = 0.35f),
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
                                color = if (active) chrome.accent else chrome.hint,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 12.sp,
                                letterSpacing = 0.2.sp,
                                textAlign = TextAlign.Center,
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
    val chrome = LocalSettingsChrome.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (chrome.light) title else title.uppercase(),
            modifier = Modifier
                .graphicsLayer { alpha = titleAlpha.coerceIn(0f, 1f) }
                .padding(start = if (chrome.light) 4.dp else 0.dp),
            style = if (chrome.light) {
                TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                )
            } else {
                TextStyle(
                    color = chrome.accent.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.Medium,
                )
            },
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
    enabled: Boolean = true,
) {
    val chrome = LocalSettingsChrome.current
    val enT by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (enabled) 1f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "settingsActionEn",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(RowShape)
            .background(chrome.rowBg)
            .clickable(
                enabled = enabled,
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
                    color = chrome.label,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (chrome.light) 15.sp else 14.sp,
                    shadow = chrome.titleShadow,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = chrome.hint,
                    fontFamily = if (chrome.light) FontFamily.SansSerif else FontFamily.Monospace,
                    fontSize = if (chrome.light) 12.sp else 9.sp,
                    letterSpacing = if (chrome.light) 0.sp else 0.3.sp,
                    lineHeight = if (chrome.light) 16.sp else 12.sp,
                    shadow = chrome.titleShadow,
                ),
            )
        }
        Text(
            text = actionLabel,
            style = TextStyle(
                color = chrome.accent.copy(alpha = 0.95f),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            ),
        )
    }
}

@Composable
private fun SettingsAutoClearTargetsRow(
    top: Boolean,
    transport: Boolean,
    toolbar: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean, Boolean, Boolean) -> Unit,
) {
    val chrome = LocalSettingsChrome.current
    val enT by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (enabled) 1f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "settingsAutoClearTargetsEn",
    )
    data class Target(val label: String, val on: Boolean, val set: (Boolean) -> Unit)
    val items = listOf(
        Target("顶部区域", top) { next ->
            if (!next && !transport && !toolbar) return@Target
            onChange(next, transport, toolbar)
        },
        Target("播放控件", transport) { next ->
            if (!next && !top && !toolbar) return@Target
            onChange(top, next, toolbar)
        },
        Target("底部工具栏", toolbar) { next ->
            if (!next && !top && !transport) return@Target
            onChange(top, transport, next)
        },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(RowShape)
            .background(chrome.rowBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "清屏包含",
            style = TextStyle(
                color = chrome.label,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (chrome.light) 15.sp else 14.sp,
                shadow = chrome.titleShadow,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "可多选；至少保留一项",
            style = TextStyle(
                color = chrome.hint,
                fontFamily = if (chrome.light) FontFamily.SansSerif else FontFamily.Monospace,
                fontSize = if (chrome.light) 12.sp else 9.sp,
                letterSpacing = if (chrome.light) 0.sp else 0.3.sp,
                lineHeight = if (chrome.light) 16.sp else 12.sp,
                shadow = chrome.titleShadow,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                key(item.label) {
                val selected = item.on
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) {
                                MainPalette.Accent.copy(alpha = 0.22f)
                            } else {
                                MainPalette.TrackOff
                            },
                        )
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { item.set(!item.on) },
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.label,
                        style = TextStyle(
                            color = if (selected) MainPalette.Accent else chrome.hint,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun SettingsPreviewAlignRow(
    selected: PreviewLyricAlign,
    onSelect: (PreviewLyricAlign) -> Unit,
    enabled: Boolean = true,
    onPreviewActiveChange: ((Boolean) -> Unit)? = null,
) {
    val chrome = LocalSettingsChrome.current
    val modes = PreviewLyricAlign.entries
    val labels = listOf("左侧", "居中", "右侧")
    val enT by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (enabled) 1f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "previewAlignEn",
    )
    val onPreviewUpdated by rememberUpdatedState(onPreviewActiveChange)
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(RowShape)
            .background(chrome.rowBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "歌词位置",
            style = TextStyle(
                color = chrome.label,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MainPalette.TrackOff),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            modes.forEachIndexed { index, mode ->
                val on = mode == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) chrome.accent.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onSelect(mode)
                                scope.launch {
                                    onPreviewUpdated?.invoke(true)
                                    kotlinx.coroutines.delay(700)
                                    onPreviewUpdated?.invoke(false)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labels[index],
                        style = TextStyle(
                            color = if (on) chrome.accent else chrome.hint,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPreviewColorRow(
    title: String,
    argb: Int,
    onArgbChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    val chrome = LocalSettingsChrome.current
    var expanded by remember { mutableStateOf(false) }
    var local by remember { mutableIntStateOf(argb) }
    LaunchedEffect(argb) {
        if (argb != local) local = argb
    }
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(local) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(local, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
    }
    val onChangeUpdated by rememberUpdatedState(onArgbChange)
    fun publish(h: Float, s: Float, v: Float) {
        val next = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
        if (next == local) return
        local = next
        onChangeUpdated(next)
    }
    val enT by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (enabled) 1f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "previewColorEn",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(RowShape)
            .background(chrome.rowBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = !expanded },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = chrome.label,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(local))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            )
        }
        AnimatedVisibility(
            visible = expanded && enabled,
            enter = fadeIn(tween(200)) + expandVertically(tween(240)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(200)),
        ) {
            Column(
                Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsPreviewHueBar(
                    hue = hue,
                    onHueChange = {
                        hue = it
                        publish(it, sat, value)
                    },
                )
                MainSlider(
                    value = sat,
                    onValueChange = {
                        sat = it
                        publish(hue, it, value)
                    },
                    valueRange = 0f..1f,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                MainSlider(
                    value = value,
                    onValueChange = {
                        value = it
                        publish(hue, sat, it)
                    },
                    valueRange = 0f..1f,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettingsPreviewHueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
) {
    val spectrum = remember {
        List(7) { i ->
            Color(AndroidColor.HSVToColor(floatArrayOf(i * 60f, 1f, 1f)))
        }
    }
    val onHueUpdated by rememberUpdatedState(onHueChange)
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(spectrum))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val next = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f) * 360f
                    onHueUpdated(next)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val next = (offset.x / size.width.toFloat()).coerceIn(0f, 1f) * 360f
                    onHueUpdated(next)
                }
            },
    ) {
        val fraction = (hue / 360f).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxSize()) {
            val x = fraction * size.width
            drawCircle(
                color = Color.White,
                radius = 7f,
                center = Offset(x, size.height / 2f),
                style = Stroke(width = 2.5f),
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    colors: androidx.compose.material3.SwitchColors,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val chrome = LocalSettingsChrome.current
    val enT by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (enabled) 1f else 0.40f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "settingsSwitchEn",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(RowShape)
            .background(chrome.rowBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = chrome.label,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (chrome.light) 15.sp else 14.sp,
                    shadow = chrome.titleShadow,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = chrome.hint,
                    fontFamily = if (chrome.light) FontFamily.SansSerif else FontFamily.Monospace,
                    fontSize = if (chrome.light) 12.sp else 9.sp,
                    letterSpacing = if (chrome.light) 0.sp else 0.3.sp,
                    lineHeight = if (chrome.light) 16.sp else 12.sp,
                    shadow = chrome.titleShadow,
                ),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
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
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    steps: Int = 0,
    /** 非空时：交互期间通知预览态（松手 / 点选结束） */
    onPreviewDragActiveChange: ((Boolean) -> Unit)? = null,
) {
    val chrome = LocalSettingsChrome.current
    val titleColor = if (enabled) chrome.label else chrome.label.copy(alpha = 0.38f)
    val valueColor = if (enabled) chrome.accent.copy(alpha = 0.95f) else chrome.accent.copy(alpha = 0.35f)
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
            .background(
                if (enabled) chrome.rowBg else chrome.rowBg.copy(alpha = chrome.rowBg.alpha * 0.72f),
            )
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
                    fontSize = if (chrome.light) 15.sp else 14.sp,
                    shadow = chrome.titleShadow,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = TextStyle(
                    color = valueColor,
                    fontFamily = if (chrome.light) FontFamily.SansSerif else FontFamily.Monospace,
                    fontSize = if (chrome.light) 13.sp else 11.sp,
                    letterSpacing = if (chrome.light) 0.sp else 0.4.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = chrome.titleShadow,
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        MainSlider(
            value = safeValue,
            onValueChange = { next ->
                val clamped = next
                    .takeIf { it.isFinite() }
                    ?.coerceIn(valueRange.start, valueRange.endInclusive)
                    ?: return@MainSlider
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
            interactionSource = sliderIx,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
    }
}

/**
 * 点击外部收回设置：无蒙版、无变暗，仅透明命中层。
 * [enabled]=false 时不挂 clickable，避免收起动画尾帧继续吞全屏单击。
 */
@Composable
fun NowPlayingSettingsOutsideDismiss(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxSize()
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                } else {
                    Modifier
                },
            ),
    )
}
