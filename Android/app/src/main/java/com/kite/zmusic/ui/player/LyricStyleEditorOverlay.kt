package com.kite.zmusic.ui.player

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricColorSlot
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerDisplayPrefs
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
/** 左侧操作区宽度 */
private val OpsColumnWidth = 286.dp
private val OpsPreviewGap = 12.dp
private val PanelInnerPadStart = 14.dp
private val PanelInnerPadEnd = 14.dp
private val PanelInnerPadTop = 48.dp
private val PanelInnerPadBottom = 14.dp

/**
 * 打开瞬间冻结的歌词克隆：相对播放页根的视觉矩形（boundsInRoot）。
 * 开场与真歌词重叠，再映射进弹窗右侧预览槽（含裁剪）。
 */
data class LyricStyleSnapshot(
    val lines: List<LrcLine>,
    val focusIndex: Int,
    val playedCount: Int,
    val upcomingCount: Int,
    val fontScale: Float,
    val lineSpacingDp: Float,
    val sourceLeftDp: Dp,
    val sourceTopDp: Dp,
    val sourceWidthDp: Dp,
    val sourceHeightDp: Dp,
)

/** 预览槽相对播放页根的静止矩形（不含入场 graphicsLayer） */
data class LyricStylePreviewSlot(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp,
)

fun lyricStylePreviewWidth(sourceWidth: Dp): Dp =
    sourceWidth.coerceIn(200.dp, 360.dp)

/**
 * 弹窗完全展开（t=1、无 translation/scale）时右侧预览槽相对播放页根的几何。
 * morph 必须用此静止终点；不可用动画中 boundsInRoot（会飞向角落）。
 */
fun lyricStyleRestPreviewSlot(
    screenWidth: Dp,
    screenHeight: Dp,
    chromeSidePad: Dp,
    previewWidthDp: Dp,
): LyricStylePreviewSlot {
    val previewW = lyricStylePreviewWidth(previewWidthDp)
    val rowInnerW = OpsColumnWidth + OpsPreviewGap + previewW
    val panelContentW = rowInnerW + PanelInnerPadStart + PanelInnerPadEnd
    // 外框 align End，width = panelContentW + chromeSidePad，padding(end=chromeSidePad)
    val contentLeft = screenWidth - chromeSidePad - panelContentW
    val contentTop = chromeSidePad
    val contentHeight = (screenHeight - chromeSidePad * 2).coerceAtLeast(120.dp)
    return LyricStylePreviewSlot(
        left = contentLeft + PanelInnerPadStart + OpsColumnWidth + OpsPreviewGap,
        top = contentTop + PanelInnerPadTop,
        width = previewW,
        height = (contentHeight - PanelInnerPadTop - PanelInnerPadBottom).coerceAtLeast(48.dp),
    )
}

/** 与 [lyricStyleRestPreviewSlot] 同源的面板内容宽（不含右侧 chrome pad） */
internal fun lyricStylePanelContentWidth(previewWidthDp: Dp): Dp {
    val previewW = lyricStylePreviewWidth(previewWidthDp)
    val rowInnerW = OpsColumnWidth + OpsPreviewGap + previewW
    return rowInnerW + PanelInnerPadStart + PanelInnerPadEnd
}

enum class LyricStyleRole {
    Playing,
    Played,
    Unplayed,
}

fun LyricRoleStyle.resolvedFontWeight(role: LyricStyleRole): FontWeight = when {
    bold -> FontWeight.SemiBold
    role == LyricStyleRole.Played && colorSlot == LyricColorSlot.DEFAULT -> FontWeight.Light
    else -> FontWeight.Normal
}

fun LyricRoleStyle.resolvedFontStyle(): FontStyle =
    if (italic) FontStyle.Italic else FontStyle.Normal

fun LyricRoleStyle.resolvedColorFor(role: LyricStyleRole): Color {
    val def = when (role) {
        LyricStyleRole.Playing -> LyricRoleStyle.DEFAULT_PLAYING_ARGB
        LyricStyleRole.Played -> LyricRoleStyle.DEFAULT_PLAYED_ARGB
        LyricStyleRole.Unplayed -> LyricRoleStyle.DEFAULT_UNPLAYED_ARGB
    }
    return resolvedColor(def)
}

/**
 * 歌词样式弹窗：左操作 / 右预览槽（空槽仅作落点与包含框）。
 * 克隆歌词由 [LyricStyleCloneLayer] 画在最上层，并映射进 [lyricStyleRestPreviewSlot] 静止终点。
 */
@Composable
fun LyricStyleEditorOverlay(
    draftPlaying: LyricRoleStyle,
    draftPlayed: LyricRoleStyle,
    draftUnplayed: LyricRoleStyle,
    onDraftPlayingChange: (LyricRoleStyle) -> Unit,
    onDraftPlayedChange: (LyricRoleStyle) -> Unit,
    onDraftUnplayedChange: (LyricRoleStyle) -> Unit,
    hazeState: HazeState,
    progress: Float,
    chromeSidePad: Dp,
    previewWidthDp: Dp,
    onDismiss: () -> Unit,
    onBackToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    hazeNonce: Int = 0,
) {
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0.001f) return

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color(0xFFF8FAFC),
        checkedTrackColor = EditorAccent.copy(alpha = 0.62f),
        uncheckedThumbColor = Color(0xFFD0D8E2),
        uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
        uncheckedBorderColor = Color.White.copy(alpha = 0.14f),
        checkedBorderColor = Color.Transparent,
    )

    val previewW = lyricStylePreviewWidth(previewWidthDp)
    val panelContentW = lyricStylePanelContentWidth(previewWidthDp)

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
                            text = "LYRIC STYLE",
                            style = TextStyle(
                                color = EditorAccent.copy(alpha = 0.75f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 2.sp,
                            ),
                        )
                        Text(
                            text = "歌词样式",
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

                        LyricRoleStyleSection(
                            title = "播放中歌词",
                            role = LyricStyleRole.Playing,
                            style = draftPlaying,
                            switchColors = switchColors,
                            onChange = onDraftPlayingChange,
                        )
                        LyricRoleStyleSection(
                            title = "已播放歌词",
                            role = LyricStyleRole.Played,
                            style = draftPlayed,
                            switchColors = switchColors,
                            onChange = onDraftPlayedChange,
                        )
                        LyricRoleStyleSection(
                            title = "未播放歌词",
                            role = LyricStyleRole.Unplayed,
                            style = draftUnplayed,
                            switchColors = switchColors,
                            onChange = onDraftUnplayedChange,
                        )
                    }

                    // 右侧预览槽：仅框架；克隆用静止几何 morph，不在此上报动画 bounds
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

/**
 * 歌词克隆（最上层）：开场钉在源位，随 progress 映射进预览槽；槽内裁剪包含。
 * 只占歌词矩形（不 fillMaxSize），以免挡住左侧操作；父级须为根 Box 以便 offset 相对根。
 */
@Composable
fun LyricStyleCloneLayer(
    snapshot: LyricStyleSnapshot,
    draftPlaying: LyricRoleStyle,
    draftPlayed: LyricRoleStyle,
    draftUnplayed: LyricRoleStyle,
    progress: Float,
    targetSlot: LyricStylePreviewSlot?,
    uiScale: Float = 1f,
    contentAlpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val alpha = contentAlpha.coerceIn(0f, 1f)
    if (alpha <= 0.001f) return
    val t = progress.coerceIn(0f, 1f)
    // 终点应为静止槽；缺省时钉在源位（不应再出现动画中 bounds）
    val morphT = if (targetSlot != null) t else 0f
    val scale = uiScale.coerceIn(PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX)
    val contentFontScale = snapshot.fontScale * scale
    // 真歌词在 uiScale 层内，行距布局值也会被放大；克隆在层外需乘同一系数
    val linePadDp = snapshot.lineSpacingDp
        .coerceIn(PlayerDisplayPrefs.LINE_SPACING_MIN, PlayerDisplayPrefs.LINE_SPACING_MAX)
    val contentLineSpacing = (linePadDp * scale).dp

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

    Box(
        modifier
            .offset(x = left, y = top)
            .size(width, height)
            .clip(RoundedCornerShape(corner))
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        LyricStyleCloneContent(
            snapshot = snapshot,
            playing = draftPlaying,
            played = draftPlayed,
            unplayed = draftUnplayed,
            contentFontScale = contentFontScale,
            contentLineSpacing = contentLineSpacing,
            contentPad = lerpDp(0.dp, 8.dp, morphT),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LyricRoleStyleSection(
    title: String,
    role: LyricStyleRole,
    style: LyricRoleStyle,
    switchColors: androidx.compose.material3.SwitchColors,
    onChange: (LyricRoleStyle) -> Unit,
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "斜体",
                style = TextStyle(color = EditorHint, fontSize = 12.sp),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = style.italic,
                onCheckedChange = { onChange(style.copy(italic = it)) },
                colors = switchColors,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "粗体",
                style = TextStyle(color = EditorHint, fontSize = 12.sp),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = style.bold,
                onCheckedChange = { onChange(style.copy(bold = it)) },
                colors = switchColors,
            )
        }

        Text(
            text = "字体颜色",
            style = TextStyle(
                color = EditorHint.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.4.sp,
            ),
        )
        LyricColorSlotRow(
            role = role,
            style = style,
            onChange = onChange,
        )

        val showPicker = style.colorSlot != LyricColorSlot.DEFAULT
        var retainedPresetIndex by remember { mutableIntStateOf(0) }
        val presetIndex = when (style.colorSlot) {
            LyricColorSlot.PRESET_0 -> 0
            LyricColorSlot.PRESET_1 -> 1
            LyricColorSlot.PRESET_2 -> 2
            LyricColorSlot.DEFAULT -> retainedPresetIndex
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
            LyricSingleColorEditor(
                argb = style.presetArgb(presetIndex),
                onColorChange = { onChange(style.withPresetArgb(presetIndex, it)) },
            )
        }
    }
}

@Composable
private fun LyricColorSlotRow(
    role: LyricStyleRole,
    style: LyricRoleStyle,
    onChange: (LyricRoleStyle) -> Unit,
) {
    val defaultColor = when (role) {
        LyricStyleRole.Playing -> Color(LyricRoleStyle.DEFAULT_PLAYING_ARGB)
        LyricStyleRole.Played -> Color(LyricRoleStyle.DEFAULT_PLAYED_ARGB)
        LyricStyleRole.Unplayed -> Color(LyricRoleStyle.DEFAULT_UNPLAYED_ARGB)
    }
    val slots = listOf(
        LyricColorSlot.DEFAULT to defaultColor,
        LyricColorSlot.PRESET_0 to Color(style.preset0Argb),
        LyricColorSlot.PRESET_1 to Color(style.preset1Argb),
        LyricColorSlot.PRESET_2 to Color(style.preset2Argb),
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.forEach { (slot, color) ->
            val selected = style.colorSlot == slot
            val locked = slot == LyricColorSlot.DEFAULT
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
private fun LyricSingleColorEditor(
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
        val next = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
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

@Composable
private fun LyricStyleCloneContent(
    snapshot: LyricStyleSnapshot,
    playing: LyricRoleStyle,
    played: LyricRoleStyle,
    unplayed: LyricRoleStyle,
    contentFontScale: Float,
    /** 与真歌词相同的视觉行距（已含 uiScale） */
    contentLineSpacing: Dp,
    contentPad: Dp,
    modifier: Modifier = Modifier,
) {
    val lines = snapshot.lines
    val focus = snapshot.focusIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    val playedN = snapshot.playedCount.coerceIn(
        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
        PlayerDisplayPrefs.LYRIC_AROUND_MAX,
    )
    val upcomingN = snapshot.upcomingCount.coerceIn(
        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
        PlayerDisplayPrefs.LYRIC_AROUND_MAX,
    )
    val fs = contentFontScale.coerceIn(
        PlayerDisplayPrefs.FONT_MIN * 0.75f,
        PlayerDisplayPrefs.FONT_MAX * 1.35f,
    )
    // 与 LandscapeProjectionLyrics 一致：槽高 = 字高 + 行距×2
    val linePad = contentLineSpacing
    val slotHeight = (38f * fs).dp + linePad * 2

    val playingColor by animateColorAsState(
        playing.resolvedColorFor(LyricStyleRole.Playing),
        tween(280, easing = FastOutSlowInEasing),
        label = "clonePlaying",
    )
    val playedColor by animateColorAsState(
        played.resolvedColorFor(LyricStyleRole.Played),
        tween(280, easing = FastOutSlowInEasing),
        label = "clonePlayed",
    )
    val unplayedColor by animateColorAsState(
        unplayed.resolvedColorFor(LyricStyleRole.Unplayed),
        tween(280, easing = FastOutSlowInEasing),
        label = "cloneUnplayed",
    )

    Box(
        modifier.padding(contentPad),
        contentAlignment = Alignment.Center,
    ) {
        if (lines.isEmpty()) {
            Text(
                text = "暂无歌词",
                style = TextStyle(
                    color = EditorHint.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
            )
            return@Box
        }

        // 不用 weight 挤扁：固定槽高还原真歌词垂直节奏，超出由外层 clip 包含
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (i in (focus - playedN).coerceAtLeast(0) until focus) {
                val distance = focus - i
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(slotHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    CloneSideLine(
                        text = lines[i].text,
                        color = playedColor.copy(
                            alpha = (0.46f - distance.coerceAtMost(2) * 0.06f)
                                .coerceIn(0.28f, 0.5f),
                        ),
                        style = played,
                        role = LyricStyleRole.Played,
                        fontScale = fs,
                        verticalPad = linePad,
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(slotHeight),
                contentAlignment = Alignment.Center,
            ) {
                CloneCenterLine(
                    text = lines.getOrNull(focus)?.text.orEmpty(),
                    color = playingColor.copy(alpha = 0.96f),
                    style = playing,
                    fontScale = fs,
                    verticalPad = linePad,
                )
            }
            for (i in (focus + 1)..(focus + upcomingN).coerceAtMost(lines.lastIndex)) {
                val distance = i - focus
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(slotHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    CloneSideLine(
                        text = lines[i].text,
                        color = unplayedColor.copy(
                            alpha = (0.46f - distance.coerceAtMost(2) * 0.06f)
                                .coerceIn(0.28f, 0.5f),
                        ),
                        style = unplayed,
                        role = LyricStyleRole.Unplayed,
                        fontScale = fs,
                        verticalPad = linePad,
                    )
                }
            }
        }
    }
}

@Composable
private fun CloneCenterLine(
    text: String,
    color: Color,
    style: LyricRoleStyle,
    fontScale: Float,
    verticalPad: Dp,
) {
    // 对齐 LandscapeCenterLyricLine(compact=true) 的边距
    Text(
        text = text,
        style = TextStyle(
            color = color,
            fontFamily = FontFamily.SansSerif,
            fontWeight = style.resolvedFontWeight(LyricStyleRole.Playing),
            fontStyle = style.resolvedFontStyle(),
            fontSize = (26f * fontScale).sp,
            lineHeight = (38f * fontScale).sp,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
        ),
        maxLines = 4,
        softWrap = true,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPad, horizontal = 4.dp)
            .padding(horizontal = 10.dp),
    )
}

@Composable
private fun CloneSideLine(
    text: String,
    color: Color,
    style: LyricRoleStyle,
    role: LyricStyleRole,
    fontScale: Float,
    verticalPad: Dp,
) {
    // 对齐 LandscapeSideLyricLine 的边距
    Text(
        text = text,
        style = TextStyle(
            color = color,
            fontFamily = FontFamily.SansSerif,
            fontWeight = style.resolvedFontWeight(role),
            fontStyle = style.resolvedFontStyle(),
            fontSize = (16.5f * fontScale).sp,
            lineHeight = (26f * fontScale).sp,
            letterSpacing = 0.35.sp,
            textAlign = TextAlign.Center,
        ),
        maxLines = 3,
        softWrap = true,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPad, horizontal = 10.dp),
    )
}
