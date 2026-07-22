package com.kite.zmusic.ui.player

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TitleColorSlot
import com.kite.zmusic.data.TitleLineStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private val EditorLabel = Color(0xFFFFFFFF)
private val EditorHint = Color(0xFFE8F0F8)
private val EditorAccent = Color(0xFF9AF0F0)
private val EditorRowBg = Color(0xF0141A24)
private val EditorBorder = Color(0xFF9AA3AD).copy(alpha = 0.55f)
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
private val PanelShape = RoundedCornerShape(18.dp)
private val PreviewSlotShape = RoundedCornerShape(14.dp)
private val OpsColumnWidth = 286.dp
private val OpsPreviewGap = 12.dp
private val PanelInnerPadStart = 14.dp
private val PanelInnerPadEnd = 14.dp
private val PanelInnerPadTop = 48.dp
private val PanelInnerPadBottom = 14.dp

/**
 * 打开瞬间冻结的标题信息克隆：相对播放页根的视觉矩形。
 * 开场与真标题重叠，再映射进弹窗右侧预览槽。
 */
data class TitleStyleSnapshot(
    val name: String,
    val artists: String,
    val sourceTitle: String?,
    val sourceLeftDp: Dp,
    val sourceTopDp: Dp,
    val sourceWidthDp: Dp,
    val sourceHeightDp: Dp,
    /** 源位是否为居中对齐族（黑胶/居中/歌词）；左对齐则为 false */
    val centerAligned: Boolean,
)

data class TitleStylePreviewSlot(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp,
)

fun titleStylePreviewWidth(sourceWidth: Dp): Dp =
    sourceWidth.coerceIn(160.dp, 320.dp)

fun titleStyleRestPreviewSlot(
    screenWidth: Dp,
    screenHeight: Dp,
    chromeSidePad: Dp,
    previewWidthDp: Dp,
): TitleStylePreviewSlot {
    val previewW = titleStylePreviewWidth(previewWidthDp)
    val rowInnerW = OpsColumnWidth + OpsPreviewGap + previewW
    val panelContentW = rowInnerW + PanelInnerPadStart + PanelInnerPadEnd
    val contentLeft = screenWidth - chromeSidePad - panelContentW
    val contentTop = chromeSidePad
    val contentHeight = (screenHeight - chromeSidePad * 2).coerceAtLeast(120.dp)
    return TitleStylePreviewSlot(
        left = contentLeft + PanelInnerPadStart + OpsColumnWidth + OpsPreviewGap,
        top = contentTop + PanelInnerPadTop,
        width = previewW,
        height = (contentHeight - PanelInnerPadTop - PanelInnerPadBottom).coerceAtLeast(48.dp),
    )
}

internal fun titleStylePanelContentWidth(previewWidthDp: Dp): Dp {
    val previewW = titleStylePreviewWidth(previewWidthDp)
    val rowInnerW = OpsColumnWidth + OpsPreviewGap + previewW
    return rowInnerW + PanelInnerPadStart + PanelInnerPadEnd
}

enum class TitleStyleLine {
    Name,
    Artist,
    Source,
}

fun TitleLineStyle.resolvedColorFor(line: TitleStyleLine): Color {
    val def = when (line) {
        TitleStyleLine.Name -> TitleLineStyle.DEFAULT_NAME_ARGB
        TitleStyleLine.Artist -> TitleLineStyle.DEFAULT_ARTIST_ARGB
        TitleStyleLine.Source -> TitleLineStyle.DEFAULT_SOURCE_ARGB
    }
    return resolvedColor(def)
}

/**
 * 标题颜色弹窗：左操作 / 右预览槽。
 * 克隆由 [TitleStyleCloneLayer] 映射进 [titleStyleRestPreviewSlot]。
 */
@Composable
fun TitleStyleEditorOverlay(
    draftName: TitleLineStyle,
    draftArtist: TitleLineStyle,
    draftSource: TitleLineStyle,
    onDraftNameChange: (TitleLineStyle) -> Unit,
    onDraftArtistChange: (TitleLineStyle) -> Unit,
    onDraftSourceChange: (TitleLineStyle) -> Unit,
    hazeState: HazeState,
    progress: Float,
    chromeSidePad: Dp,
    previewWidthDp: Dp,
    onDismiss: () -> Unit,
    onBackToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0.001f) return

    val previewW = titleStylePreviewWidth(previewWidthDp)
    val panelContentW = titleStylePanelContentWidth(previewWidthDp)

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        BoxWithConstraints(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(panelContentW + chromeSidePad)
                .padding(
                    top = chromeSidePad,
                    bottom = chromeSidePad,
                    end = chromeSidePad,
                ),
        ) {
            val panelW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(1f, 0.5f)
                        translationX = (1f - t) * panelW
                        scaleX = 0.88f + 0.12f * t
                        alpha = t
                    }
                    .clip(PanelShape)
                    .hazeEffect(state = hazeState, style = EditorGlassStyle) {
                        blurRadius = 72.dp
                        noiseFactor = 0.10f
                    }
                    .border(width = 1.dp, color = EditorBorder, shape = PanelShape)
                    .background(Color(0x9905080E))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
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

                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = PanelInnerPadStart,
                            end = PanelInnerPadEnd,
                            top = PanelInnerPadTop,
                            bottom = PanelInnerPadBottom,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(OpsPreviewGap),
                ) {
                    Column(
                        Modifier
                            .width(OpsColumnWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "TITLE COLOR",
                            style = TextStyle(
                                color = EditorAccent.copy(alpha = 0.75f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 2.sp,
                            ),
                        )
                        Text(
                            text = "标题颜色",
                            style = TextStyle(
                                color = EditorLabel,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            ),
                        )
                        Text(
                            text = "克隆映射进预览 · 关闭后应用",
                            style = TextStyle(
                                color = EditorHint.copy(alpha = 0.72f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 0.3.sp,
                            ),
                        )

                        TitleLineStyleSection(
                            title = "歌名",
                            line = TitleStyleLine.Name,
                            style = draftName,
                            onChange = onDraftNameChange,
                        )
                        TitleLineStyleSection(
                            title = "制作人",
                            line = TitleStyleLine.Artist,
                            style = draftArtist,
                            onChange = onDraftArtistChange,
                        )
                        TitleLineStyleSection(
                            title = "歌单",
                            line = TitleStyleLine.Source,
                            style = draftSource,
                            onChange = onDraftSourceChange,
                        )
                    }

                    Box(
                        Modifier
                            .width(previewW)
                            .fillMaxHeight()
                            .clip(PreviewSlotShape)
                            .background(Color(0x66070B12))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.10f),
                                shape = PreviewSlotShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun TitleStyleCloneLayer(
    snapshot: TitleStyleSnapshot,
    draftName: TitleLineStyle,
    draftArtist: TitleLineStyle,
    draftSource: TitleLineStyle,
    progress: Float,
    targetSlot: TitleStylePreviewSlot?,
    uiScale: Float = 1f,
    contentAlpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val alpha = contentAlpha.coerceIn(0f, 1f)
    if (alpha <= 0.001f) return
    val t = progress.coerceIn(0f, 1f)
    val morphT = if (targetSlot != null) t else 0f
    val scale = uiScale.coerceIn(PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX)

    val left = if (targetSlot != null) {
        lerpDp(snapshot.sourceLeftDp, targetSlot.left, morphT)
    } else {
        snapshot.sourceLeftDp
    }
    val top = if (targetSlot != null) {
        lerpDp(snapshot.sourceTopDp, targetSlot.top, morphT)
    } else {
        snapshot.sourceTopDp
    }
    val width = if (targetSlot != null) {
        lerpDp(snapshot.sourceWidthDp, targetSlot.width, morphT)
    } else {
        snapshot.sourceWidthDp
    }
    val height = if (targetSlot != null) {
        lerpDp(snapshot.sourceHeightDp, targetSlot.height, morphT)
    } else {
        snapshot.sourceHeightDp
    }
    val corner = lerp(0f, 14f, morphT).dp
    val pad = lerpDp(0.dp, 12.dp, morphT)
    // 源位：居中族保持水平居中，左对齐从左缘起；终点：槽内水平+垂直居中
    val hBias = if (snapshot.centerAligned) 0f else -1f + morphT
    val vBias = -1f + morphT
    val textCentered = snapshot.centerAligned || morphT > 0.5f
    val textAlign = if (textCentered) TextAlign.Center else TextAlign.Start
    val colHAlign = if (textCentered) Alignment.CenterHorizontally else Alignment.Start

    Box(
        modifier
            .offset(x = left, y = top)
            .size(width, height)
            .clip(RoundedCornerShape(corner))
            .graphicsLayer { this.alpha = alpha }
            .padding(pad),
        contentAlignment = BiasAlignment(hBias, vBias),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = colHAlign,
            verticalArrangement = Arrangement.spacedBy(5.dp * scale),
        ) {
            Text(
                text = snapshot.name,
                style = TextStyle(
                    color = draftName.resolvedColorFor(TitleStyleLine.Name),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (16f * scale).sp,
                    letterSpacing = 0.35.sp,
                    textAlign = textAlign,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = snapshot.artists.uppercase(),
                style = TextStyle(
                    color = draftArtist.resolvedColorFor(TitleStyleLine.Artist),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (9.5f * scale).sp,
                    letterSpacing = 1.8.sp,
                    textAlign = textAlign,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!snapshot.sourceTitle.isNullOrBlank()) {
                Spacer(Modifier.height(1.dp * scale))
                Text(
                    text = snapshot.sourceTitle,
                    style = TextStyle(
                        color = draftSource.resolvedColorFor(TitleStyleLine.Source),
                        fontFamily = FontFamily.Monospace,
                        fontSize = (8f * scale).sp,
                        letterSpacing = 0.55.sp,
                        textAlign = textAlign,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TitleLineStyleSection(
    title: String,
    line: TitleStyleLine,
    style: TitleLineStyle,
    onChange: (TitleLineStyle) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EditorRowBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = EditorLabel,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            ),
        )
        Text(
            text = "字体颜色",
            style = TextStyle(
                color = EditorHint.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.4.sp,
            ),
        )
        TitleColorSlotRow(
            line = line,
            style = style,
            onChange = onChange,
        )

        val showPicker = style.colorSlot != TitleColorSlot.DEFAULT
        var retainedPresetIndex by remember { mutableIntStateOf(0) }
        val presetIndex = when (style.colorSlot) {
            TitleColorSlot.PRESET_0 -> 0
            TitleColorSlot.PRESET_1 -> 1
            TitleColorSlot.DEFAULT -> retainedPresetIndex
        }
        SideEffect {
            if (showPicker) retainedPresetIndex = presetIndex
        }
        AnimatedVisibility(
            visible = showPicker,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            ) + expandVertically(
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
                clip = true,
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            ) + shrinkVertically(
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
                clip = true,
            ),
        ) {
            TitleSingleColorEditor(
                argb = style.presetArgb(presetIndex),
                onColorChange = { onChange(style.withPresetArgb(presetIndex, it)) },
            )
        }
    }
}

@Composable
private fun TitleColorSlotRow(
    line: TitleStyleLine,
    style: TitleLineStyle,
    onChange: (TitleLineStyle) -> Unit,
) {
    val defaultColor = when (line) {
        TitleStyleLine.Name -> Color(TitleLineStyle.DEFAULT_NAME_ARGB)
        TitleStyleLine.Artist -> Color(TitleLineStyle.DEFAULT_ARTIST_ARGB)
        TitleStyleLine.Source -> Color(TitleLineStyle.DEFAULT_SOURCE_ARGB)
    }
    val slots = listOf(
        TitleColorSlot.DEFAULT to defaultColor,
        TitleColorSlot.PRESET_0 to Color(style.preset0Argb),
        TitleColorSlot.PRESET_1 to Color(style.preset1Argb),
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.forEach { (slot, color) ->
            val selected = style.colorSlot == slot
            val locked = slot == TitleColorSlot.DEFAULT
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) EditorAccent.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.28f),
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
                        onClick = { onChange(style.withColorSlot(slot)) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                )
                if (locked) {
                    Text(
                        text = "默",
                        style = TextStyle(
                            color = Color.Black.copy(alpha = 0.72f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleSingleColorEditor(
    argb: Int,
    onColorChange: (Int) -> Unit,
) {
    var local by remember { mutableIntStateOf(argb) }
    LaunchedEffect(argb) {
        if (argb != local) local = argb
    }
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(local) {
        val h = FloatArray(3)
        AndroidColor.colorToHSV(local, h)
        hue = h[0]
        sat = h[1]
        value = h[2]
    }
    val onColorChangeState = rememberUpdatedState(onColorChange)

    fun publish(h: Float, s: Float, v: Float) {
        val rgb = AndroidColor.HSVToColor(floatArrayOf(h, s, v)) and 0x00FFFFFF
        val next = (local and 0xFF000000.toInt()) or rgb
        if (next == local) return
        local = next
        onColorChangeState.value(next)
    }

    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val hueSpectrum = remember {
        List(7) { i ->
            Color(AndroidColor.HSVToColor(floatArrayOf(i * 60f, 1f, 1f)))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(hueColor)
                .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
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
