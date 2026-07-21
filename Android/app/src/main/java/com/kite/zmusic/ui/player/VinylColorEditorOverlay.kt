package com.kite.zmusic.ui.player

import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.VinylPlateColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private val EditorLabel = Color(0xFFFFFFFF)
private val EditorHint = Color(0xFFE8F0F8)
private val EditorAccent = Color(0xFF9AF0F0)
private val EditorRowBg = Color(0xF0141A24)
/** 灰色描边 */
private val EditorBorder = Color(0xFF9AA3AD).copy(alpha = 0.55f)
/**
 * 高模糊、低遮罩的磨砂玻璃（非整块单色）。
 */
private val EditorGlassStyle = HazeStyle(
    backgroundColor = Color(0xFF03060A),
    tints = listOf(
        HazeTint(Color(0xFF070B12).copy(alpha = 0.22f)),
        HazeTint(Color.Black.copy(alpha = 0.14f)),
    ),
    blurRadius = 72.dp,
    noiseFactor = 0.10f,
    fallbackTint = HazeTint(Color(0x6605080E)),
)

/**
 * 弹窗外形：圆角矩形挖去左侧黑胶圆孔，使真实黑胶透出。
 * 圆心 / 半径为弹窗局部坐标（px）。
 */
private class VinylEditorHoleShape(
    private val holeCenter: Offset,
    private val holeRadius: Float,
    private val cornerPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                ),
            )
            addOval(
                Rect(
                    left = holeCenter.x - holeRadius,
                    top = holeCenter.y - holeRadius,
                    right = holeCenter.x + holeRadius,
                    bottom = holeCenter.y + holeRadius,
                ),
            )
        }
        return Outline.Generic(path)
    }
}

/** 盘面色过渡动画（切换预设等）。 */
@Composable
fun rememberAnimatedVinylPlateColors(target: VinylPlateColors): VinylPlateColors {
    val spec = tween<Color>(durationMillis = 420, easing = FastOutSlowInEasing)
    val baseInner by animateColorAsState(target.baseInner, spec, label = "vinylBaseInner")
    val baseMid by animateColorAsState(target.baseMid, spec, label = "vinylBaseMid")
    val baseOuter by animateColorAsState(target.baseOuter, spec, label = "vinylBaseOuter")
    val baseEdge by animateColorAsState(target.baseEdge, spec, label = "vinylBaseEdge")
    val groove by animateColorAsState(target.groove, spec, label = "vinylGroove")
    val rim by animateColorAsState(target.rim, spec, label = "vinylRim")
    val holeLight by animateColorAsState(target.holeLight, spec, label = "vinylHoleLight")
    val holeDark by animateColorAsState(target.holeDark, spec, label = "vinylHoleDark")
    return VinylPlateColors(
        baseInner = baseInner,
        baseMid = baseMid,
        baseOuter = baseOuter,
        baseEdge = baseEdge,
        groove = groove,
        rim = rim,
        holeLight = holeLight,
        holeDark = holeDark,
    )
}

/**
 * 自选黑胶颜色编辑层。
 *
 * 几何：左/上/下外侧留白 = 黑胶到屏幕边距的 2/3；右侧外边距 = 左侧的 3 倍。
 * [progress] 0..1 控制渐显 / 缩放（应与黑胶居中同曲线）。
 */
@Composable
fun VinylColorEditorOverlay(
    prefs: PlayerDisplayPrefs,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    hazeState: HazeState,
    progress: Float,
    vinylCenterX: Dp,
    vinylCenterY: Dp,
    vinylRadius: Dp,
    screenWidth: Dp,
    screenHeight: Dp,
    onDismiss: () -> Unit,
    onBackToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0.001f) return

    val density = LocalDensity.current
    val vinylLeft = vinylCenterX - vinylRadius
    val vinylTop = vinylCenterY - vinylRadius
    val vinylBottom = vinylCenterY + vinylRadius

    // 左/上/下：外侧留白 = 黑胶到屏幕边距的 2/3（更靠近黑胶）
    // 右侧：外边距 = 左侧外边距 × 3 → 整体变窄
    val marginL = vinylLeft.coerceAtLeast(0.dp)
    val marginT = vinylTop.coerceAtLeast(0.dp)
    val marginB = (screenHeight - vinylBottom).coerceAtLeast(0.dp)

    val dialogLeft = marginL * (2f / 3f)
    val dialogTop = marginT * (2f / 3f)
    val dialogRight = (screenWidth - dialogLeft * 3f).coerceAtLeast(dialogLeft + 120.dp)
    val dialogBottom = screenHeight - marginB * (2f / 3f)
    val dialogW = (dialogRight - dialogLeft).coerceAtLeast(120.dp)
    val dialogH = (dialogBottom - dialogTop).coerceAtLeast(120.dp)

    val holeCenterLocal = with(density) {
        Offset(
            x = (vinylCenterX - dialogLeft).toPx(),
            y = (vinylCenterY - dialogTop).toPx(),
        )
    }
    val holeRadiusPx = with(density) { (vinylRadius + 4.dp).toPx() }
    val cornerPx = with(density) { 18.dp.toPx() }
    val holeShape = remember(holeCenterLocal, holeRadiusPx, cornerPx) {
        VinylEditorHoleShape(holeCenterLocal, holeRadiusPx, cornerPx)
    }

    // 操作区靠右、限宽，避免色盘被横向拉扁
    val opsMaxWidth = 300.dp
    val opsEndPad = 14.dp
    val holeRightLocal = with(density) {
        (holeCenterLocal.x + holeRadiusPx).toDp()
    }
    val opsAvailable = (dialogW - holeRightLocal - opsEndPad - 12.dp).coerceAtLeast(160.dp)
    val opsWidth = minOf(opsMaxWidth, opsAvailable)

    val t = progress.coerceIn(0f, 1f)
    val scale = 0.94f + 0.06f * t

    Box(modifier.fillMaxSize()) {
        // 外部点击 → 回播放页
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Box(
            Modifier
                .offset(x = dialogLeft, y = dialogTop)
                .size(dialogW, dialogH)
                .graphicsLayer {
                    alpha = t
                    scaleX = scale
                    scaleY = scale
                }
                .clip(holeShape)
                .hazeEffect(state = hazeState, style = EditorGlassStyle) {
                    blurRadius = 72.dp
                    noiseFactor = 0.10f
                }
                .border(width = 1.dp, color = EditorBorder, shape = holeShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            // 左上角返回：回设置页
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 10.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBackToSettings,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(18.dp)) {
                    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round)
                    val cx = size.width * 0.58f
                    val cy = size.height / 2f
                    val arm = size.minDimension * 0.28f
                    drawLine(
                        color = Color.White.copy(alpha = 0.92f),
                        start = Offset(cx, cy - arm),
                        end = Offset(cx - arm * 1.15f, cy),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.92f),
                        start = Offset(cx - arm * 1.15f, cy),
                        end = Offset(cx, cy + arm),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }

            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(opsWidth)
                    .padding(
                        end = opsEndPad,
                        top = 14.dp,
                        bottom = 14.dp,
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "VINYL COLOR",
                    style = TextStyle(
                        color = EditorAccent.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp,
                    ),
                )
                Text(
                    text = "自选配色",
                    style = TextStyle(
                        color = EditorLabel,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
                )
                Text(
                    text = "5 个预设位 · 改色即保存",
                    style = TextStyle(
                        color = EditorHint.copy(alpha = 0.72f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 0.3.sp,
                    ),
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    prefs.vinylCustomPresets.forEachIndexed { index, preset ->
                        val selected = prefs.vinylCustomPresetIndex == index
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) {
                                        EditorAccent.copy(alpha = 0.22f)
                                    } else {
                                        EditorRowBg
                                    },
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) {
                                        EditorAccent.copy(alpha = 0.55f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        onPrefsChange(prefs.withCustomPresetIndex(index))
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.baseArgb))
                                    .border(
                                        1.5.dp,
                                        Color(preset.grooveArgb).copy(alpha = 0.9f),
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                }

                VinylLiveColorEditor(
                    baseArgb = prefs.vinylCustomBaseArgb,
                    grooveArgb = prefs.vinylCustomGrooveArgb,
                    onColorsChange = { base, groove ->
                        onPrefsChange(prefs.withActiveCustomColors(base, groove))
                    },
                )
            }
        }
    }
}

@Composable
private fun VinylLiveColorEditor(
    baseArgb: Int,
    grooveArgb: Int,
    onColorsChange: (baseArgb: Int, grooveArgb: Int) -> Unit,
) {
    var target by remember { mutableIntStateOf(0) } // 0=盘面 1=纹理
    var base by remember { mutableIntStateOf(baseArgb) }
    var groove by remember { mutableIntStateOf(grooveArgb) }

    // 外部切换预设时同步；拖动过程中由本地状态驱动，避免回写抖动
    LaunchedEffect(baseArgb, grooveArgb) {
        if (baseArgb != base) base = baseArgb
        if (grooveArgb != groove) groove = grooveArgb
    }

    val editing = if (target == 0) base else groove
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(editing, target) {
        val h = FloatArray(3)
        AndroidColor.colorToHSV(editing, h)
        hue = h[0]
        sat = h[1]
        value = h[2]
    }

    val onColorsChangeState = rememberUpdatedState(onColorsChange)
    val baseState = rememberUpdatedState(base)
    val grooveState = rememberUpdatedState(groove)
    val targetState = rememberUpdatedState(target)

    fun publish(h: Float, s: Float, v: Float) {
        val argb = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
        if (targetState.value == 0) {
            if (argb == baseState.value) return
            base = argb
            onColorsChangeState.value(argb, grooveState.value)
        } else {
            if (argb == grooveState.value) return
            groove = argb
            onColorsChangeState.value(baseState.value, argb)
        }
    }

    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val hueSpectrum = remember {
        List(7) { i ->
            Color(AndroidColor.HSVToColor(floatArrayOf(i * 60f, 1f, 1f)))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("盘面", "纹理").forEachIndexed { i, label ->
                val on = target == i
                val swatch = if (i == 0) base else groove
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (on) EditorAccent.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.35f),
                        )
                        .border(
                            1.dp,
                            if (on) EditorAccent.copy(alpha = 0.5f) else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { target = i },
                        )
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                        )
                        Text(
                            text = label,
                            style = TextStyle(
                                color = if (on) EditorAccent else EditorHint,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }

        // HSV 平面：渐变叠加，禁止逐像素绘制（大弹窗会卡死主线程）
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(hueColor)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.White, Color.Transparent),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                    ),
                )
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val s = (offset.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        sat = s
                        value = v
                        publish(hue, s, v)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val s = (change.position.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        sat = s
                        value = v
                        publish(hue, s, v)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = sat * size.width
                val cy = (1f - value) * size.height
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5f),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f),
                    radius = 9.5f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.2f),
                )
            }
        }

        Text(
            text = "色相",
            style = TextStyle(
                color = EditorHint.copy(alpha = 0.75f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(colors = hueSpectrum))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val h = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                        hue = h
                        publish(h, sat, value)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val h = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                        hue = h
                        publish(h, sat, value)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val x = (hue / 360f) * size.width
                drawCircle(
                    color = Color.White,
                    radius = size.height * 0.42f,
                    center = Offset(x, size.height / 2f),
                    style = Stroke(width = 2.5f),
                )
            }
        }
    }
}
