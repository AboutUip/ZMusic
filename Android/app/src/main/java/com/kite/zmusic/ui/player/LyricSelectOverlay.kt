package com.kite.zmusic.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.PlayerDisplayPrefs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** 选句弹窗磨砂玻璃（整块外壳；歌词孔内由歌词层直角玻璃贴合） */
val LyricSelectGlassStyle = HazeStyle(
    backgroundColor = Color(0xFF03060A),
    tints = listOf(
        HazeTint(Color(0xFF070B12).copy(alpha = 0.22f)),
        HazeTint(Color.Black.copy(alpha = 0.14f)),
    ),
    blurRadius = 72.dp,
    noiseFactor = 0.10f,
    fallbackTint = HazeTint(Color(0x9905080E)),
)

private val SelectAccent = Color(0xFF9AF0F0)
private val SelectLabel = Color(0xFFFFFFFF)
private val SelectHint = Color(0xFFE8F0F8)
private val SelectDialogCorner = 10.dp

/**
 * 选句弹窗几何：靠右，右/上/下边距相等；右侧挖孔给「原歌词层」透出。
 */
data class LyricSelectGeom(
    val margin: Dp,
    val dialogLeft: Dp,
    val dialogTop: Dp,
    val dialogW: Dp,
    val dialogH: Dp,
    val opsWidth: Dp,
    val cellWidth: Dp,
    val cellHeight: Dp,
    val listLeft: Dp,
    val listTop: Dp,
    val listWidth: Dp,
    val listHeight: Dp,
    val listCenterX: Dp,
    val listCenterY: Dp,
)

@Composable
fun rememberLyricSelectGeom(
    lines: List<LrcLine>,
    fontScale: Float,
    screenWidth: Dp,
    screenHeight: Dp,
): LyricSelectGeom {
    val density = LocalDensity.current
    val fs = fontScale.coerceIn(PlayerDisplayPrefs.FONT_MIN, PlayerDisplayPrefs.FONT_MAX)
    val textStyle = remember(fs) {
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (16.5f * fs).sp,
            lineHeight = (26f * fs).sp,
            letterSpacing = 0.35.sp,
            textAlign = TextAlign.Center,
        )
    }
    val textMeasurer = rememberTextMeasurer()
    val cellPadH = 14.dp
    val cellPadV = 8.dp
    val cellHeight = ((44f * fs).dp + cellPadV * 2).coerceIn(40.dp, 72.dp)
    val measuredMaxTextPx = remember(lines, textStyle, textMeasurer) {
        lines.maxOfOrNull { line ->
            textMeasurer.measure(
                text = AnnotatedString(line.text.ifBlank { " " }),
                style = textStyle,
                maxLines = 1,
            ).size.width
        } ?: 0
    }
    val measuredMaxTextDp = with(density) { measuredMaxTextPx.toDp() }
    val preferredCellW = (measuredMaxTextDp + cellPadH * 2)
        .coerceIn(96.dp, (screenWidth * 0.52f).coerceAtLeast(120.dp))

    val opsWidth = 68.dp
    val dialogInnerPad = 12.dp
    val gapOpsList = 0.dp
    val margin = 18.dp
    val maxDialogW = (screenWidth - margin * 2).coerceAtLeast(160.dp)
    val preferredContentW = opsWidth + gapOpsList + preferredCellW + dialogInnerPad * 2
    val dialogW = preferredContentW.coerceAtMost(maxDialogW)
    // 宽度被屏宽裁切时，歌词列跟着缩，避免内容撑破弹窗
    val listWidth = (dialogW - dialogInnerPad * 2 - opsWidth - gapOpsList)
        .coerceAtLeast(80.dp)
    val cellWidth = listWidth
    val dialogH = (screenHeight - margin * 2).coerceAtLeast(160.dp)
    val dialogLeft = screenWidth - margin - dialogW
    val dialogTop = margin
    val listHeight = (dialogH - dialogInnerPad * 2).coerceAtLeast(80.dp)
    val listLeft = dialogLeft + dialogInnerPad + opsWidth + gapOpsList
    val listTop = dialogTop + dialogInnerPad
    return LyricSelectGeom(
        margin = margin,
        dialogLeft = dialogLeft,
        dialogTop = dialogTop,
        dialogW = dialogW,
        dialogH = dialogH,
        opsWidth = opsWidth,
        cellWidth = cellWidth,
        cellHeight = cellHeight,
        listLeft = listLeft,
        listTop = listTop,
        listWidth = listWidth,
        listHeight = listHeight,
        listCenterX = listLeft + listWidth / 2,
        listCenterY = listTop + listHeight / 2,
    )
}

/**
 * 选句已完全展开时，歌单切歌导致几何跳变则动画过渡；进入/退出 morph 阶段用瞬时目标值。
 */
@Composable
fun rememberAnimatedLyricSelectGeom(
    target: LyricSelectGeom,
    animateChanges: Boolean,
): LyricSelectGeom {
    val curve = remember { CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f) }
    val spec = if (animateChanges) {
        tween<Dp>(durationMillis = 420, easing = curve)
    } else {
        snap()
    }
    val dialogW by animateDpAsState(target.dialogW, spec, label = "lsDialogW")
    val dialogLeft by animateDpAsState(target.dialogLeft, spec, label = "lsDialogLeft")
    val dialogH by animateDpAsState(target.dialogH, spec, label = "lsDialogH")
    val dialogTop by animateDpAsState(target.dialogTop, spec, label = "lsDialogTop")
    val listWidth by animateDpAsState(target.listWidth, spec, label = "lsListW")
    val listLeft by animateDpAsState(target.listLeft, spec, label = "lsListLeft")
    val listHeight by animateDpAsState(target.listHeight, spec, label = "lsListH")
    val listTop by animateDpAsState(target.listTop, spec, label = "lsListTop")
    val cellWidth by animateDpAsState(target.cellWidth, spec, label = "lsCellW")
    val cellHeight by animateDpAsState(target.cellHeight, spec, label = "lsCellH")
    val margin by animateDpAsState(target.margin, spec, label = "lsMargin")
    val opsWidth by animateDpAsState(target.opsWidth, spec, label = "lsOpsW")
    return LyricSelectGeom(
        margin = margin,
        dialogLeft = dialogLeft,
        dialogTop = dialogTop,
        dialogW = dialogW,
        dialogH = dialogH,
        opsWidth = opsWidth,
        cellWidth = cellWidth,
        cellHeight = cellHeight,
        listLeft = listLeft,
        listTop = listTop,
        listWidth = listWidth,
        listHeight = listHeight,
        listCenterX = listLeft + listWidth / 2,
        listCenterY = listTop + listHeight / 2,
    )
}

/** 圆角外壳挖去右侧歌词孔；孔为直角，与歌词层直角玻璃贴合 */
private class LyricSelectHoleShape(
    private val hole: Rect,
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
            addRect(hole)
        }
        return Outline.Generic(path)
    }
}

/**
 * 选句弹窗：整块圆角磨砂壳 + 左侧操作区；右侧挖孔由原歌词层填入。
 *
 * 打开后无视触发长按的那次松手；松手被吞掉后才允许点外部关闭。
 * 退出：外壳 alpha 跟随 [progress]，与歌词收窗同步。
 */
@Composable
fun LyricSelectOverlay(
    selectedCount: Int,
    hazeState: HazeState,
    progress: Float,
    selectOpen: Boolean,
    geom: LyricSelectGeom,
    onDismiss: () -> Unit,
    onClearSelection: () -> Unit,
    onCopy: () -> Unit,
    onOutsideDismissArmed: () -> Unit,
    modifier: Modifier = Modifier,
    hazeNonce: Int = 0,
) {
    val shellSoft = remember { CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f) }
    val enterAlpha = remember { Animatable(0f) }
    val t = progress.coerceIn(0f, 1f)

    LaunchedEffect(selectOpen) {
        if (selectOpen) {
            enterAlpha.snapTo(0f)
            delay(160)
            enterAlpha.animateTo(1f, tween(durationMillis = 280, easing = shellSoft))
        }
    }
    LaunchedEffect(selectOpen, t) {
        if (!selectOpen && t <= 0.001f) {
            enterAlpha.snapTo(0f)
        }
    }

    val surfaceA = if (selectOpen) enterAlpha.value else t
    if (t <= 0.001f && surfaceA <= 0.001f) return

    val density = LocalDensity.current
    val onDismissUpdated by rememberUpdatedState(onDismiss)
    val onArmedUpdated by rememberUpdatedState(onOutsideDismissArmed)
    var outsideDismissArmed by remember { mutableStateOf(false) }

    val dialogInnerPad = 12.dp
    val cornerPx = with(density) { SelectDialogCorner.toPx() }
    val hole = with(density) {
        val left = (dialogInnerPad + geom.opsWidth).toPx()
        val top = dialogInnerPad.toPx()
        Rect(
            left = left,
            top = top,
            right = left + geom.listWidth.toPx(),
            bottom = top + geom.listHeight.toPx(),
        )
    }
    val holeShape = remember(hole, cornerPx) { LyricSelectHoleShape(hole, cornerPx) }

    Box(modifier.fillMaxSize()) {
        if (!outsideDismissArmed && selectOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = withTimeoutOrNull(64L) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                }
                                if (event == null) break
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                            outsideDismissArmed = true
                            onArmedUpdated()
                        }
                    },
            )
        }

        if (outsideDismissArmed && selectOpen) {
            val dismissMod = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissUpdated,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(geom.dialogTop)
                    .then(dismissMod),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(geom.margin)
                    .then(dismissMod),
            )
            Box(
                Modifier
                    .offset(y = geom.dialogTop)
                    .width(geom.dialogLeft)
                    .height(geom.dialogH)
                    .then(dismissMod),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = geom.dialogTop)
                    .width(geom.margin)
                    .height(geom.dialogH)
                    .then(dismissMod),
            )
        }

        if (surfaceA > 0.01f) {
            key(hazeNonce) {
            Box(
                Modifier
                    .offset(x = geom.dialogLeft, y = geom.dialogTop)
                    .size(geom.dialogW, geom.dialogH)
                    .graphicsLayer { alpha = surfaceA }
                    .clip(holeShape)
                    .hazeEffect(state = hazeState, style = LyricSelectGlassStyle) {
                        blurRadius = 72.dp
                        noiseFactor = 0.10f
                        fallbackTint = HazeTint(Color(0x9905080E))
                    },
            ) {
                Column(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(geom.opsWidth)
                        .fillMaxHeight()
                        .padding(
                            start = dialogInnerPad,
                            top = dialogInnerPad,
                            bottom = dialogInnerPad,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "已选",
                        style = TextStyle(
                            color = SelectAccent.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 1.2.sp,
                        ),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = "$selectedCount",
                        style = TextStyle(
                            color = SelectLabel,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                        ),
                    )
                    Text(
                        text = "条",
                        style = TextStyle(
                            color = SelectHint.copy(alpha = 0.55f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                        ),
                    )

                    Spacer(Modifier.weight(1f))

                    SelectActionButton(
                        label = "退出",
                        onClick = onDismissUpdated,
                        icon = { SelectExitIcon() },
                    )
                    Spacer(Modifier.height(4.dp))
                    SelectActionButton(
                        label = "全部取消",
                        onClick = onClearSelection,
                        icon = { SelectClearIcon() },
                    )
                    Spacer(Modifier.height(4.dp))
                    SelectActionButton(
                        label = "复制",
                        onClick = onCopy,
                        enabled = selectedCount > 0,
                        icon = { SelectCopyIcon() },
                    )
                }
            }
            } // key(hazeNonce)
        }
    }
}

fun copyLyricSelection(
    context: Context,
    lines: List<LrcLine>,
    selected: Set<Int>,
) {
    if (selected.isEmpty()) return
    val text = selected
        .sorted()
        .mapNotNull { i -> lines.getOrNull(i)?.text }
        .joinToString("\n")
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("lyrics", text))
}

@Composable
private fun SelectActionButton(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(
        Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .graphicsLayer { this.alpha = alpha },
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            text = label,
            style = TextStyle(
                color = SelectLabel.copy(alpha = 0.88f * alpha),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}

@Composable
private fun SelectExitIcon() {
    Canvas(Modifier.size(18.dp)) {
        val arm = size.minDimension * 0.16f
        val inset = size.minDimension * 0.22f
        // 实心 X：两臂用粗线近似填充感
        drawLine(
            SelectLabel,
            Offset(inset, inset),
            Offset(size.width - inset, size.height - inset),
            arm,
            StrokeCap.Round,
        )
        drawLine(
            SelectLabel,
            Offset(size.width - inset, inset),
            Offset(inset, size.height - inset),
            arm,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun SelectClearIcon() {
    Canvas(Modifier.size(18.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.38f
        drawCircle(SelectLabel, radius = r, center = Offset(cx, cy))
        drawLine(
            Color(0xFF0A0E14),
            Offset(cx - r * 0.45f, cy),
            Offset(cx + r * 0.45f, cy),
            size.minDimension * 0.14f,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun SelectCopyIcon() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        // 后层纸
        drawRoundRect(
            color = SelectLabel.copy(alpha = 0.55f),
            topLeft = Offset(w * 0.30f, h * 0.14f),
            size = Size(w * 0.48f, h * 0.58f),
            cornerRadius = CornerRadius(2.2f, 2.2f),
        )
        // 前层纸（实心）
        drawRoundRect(
            color = SelectLabel,
            topLeft = Offset(w * 0.18f, h * 0.28f),
            size = Size(w * 0.48f, h * 0.58f),
            cornerRadius = CornerRadius(2.2f, 2.2f),
        )
    }
}
