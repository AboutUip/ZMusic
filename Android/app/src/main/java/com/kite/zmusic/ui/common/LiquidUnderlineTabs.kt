package com.kite.zmusic.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.kite.zmusic.ui.main.MainPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

internal val LiquidTabScroll: AnimationSpec<Float> = tween(
    durationMillis = 460,
    easing = FastOutSlowInEasing,
)

/** 跨页越多走得稍久，让下划线能经过中间每一档，而不是一闪到终点。 */
internal fun liquidTabScrollMillis(pageDistance: Float): Int {
    val hops = abs(pageDistance).coerceAtLeast(0f)
    if (hops < 0.001f) return 0
    return (340 + 160 * (hops - 1f)).toInt().coerceIn(340, 920)
}

/**
 * 点 Tab 时自己把页滚过去。系统的 animateScrollToPage 在跨过 3 页以上时会先跳到终点附近，
 * 中间的页和下划线都会被跳过。
 */
internal suspend fun PagerState.animateLiquidTabTo(page: Int) {
    if (pageCount <= 0) return
    val target = page.coerceIn(0, pageCount - 1)
    val pageSpan = (layoutInfo.pageSize + layoutInfo.pageSpacing).toFloat()
    if (pageSpan <= 1f) {
        animateScrollToPage(target, animationSpec = LiquidTabScroll)
        return
    }
    scroll {
        val from = currentPage + currentPageOffsetFraction
        val pages = target - from
        if (abs(pages) < 0.001f) return@scroll
        val distance = pages * pageSpan
        val spec = tween<Float>(liquidTabScrollMillis(pages), easing = FastOutSlowInEasing)
        var previous = 0f
        animate(0f, distance, animationSpec = spec) { current, _ ->
            val consumed = scrollBy(current - previous)
            previous += consumed
        }
    }
}

private val LiquidTabLine = 2.dp
private const val LiquidTabBulge = 0.5f

internal data class LiquidTabSlot(
    val left: Float,
    val right: Float,
) {
    val center: Float get() = (left + right) / 2f
}

/**
 * 下划线在两个标签之间移动时从中点向两边同样拉长，再收回目标文字的宽度。
 * 返回的左右端到中心的距离相等。
 */
internal fun liquidTabSpan(
    slots: List<LiquidTabSlot>,
    position: Float,
    bulgeFraction: Float = LiquidTabBulge,
): LiquidTabSlot? {
    if (slots.isEmpty()) return null
    val last = slots.lastIndex.toFloat()
    val p = position.coerceIn(0f, last)
    val from = p.toInt().coerceIn(0, slots.lastIndex)
    val to = (from + 1).coerceAtMost(slots.lastIndex)
    val t = (p - from).coerceIn(0f, 1f)
    val a = slots[from]
    val b = slots[to]
    val left = a.left + (b.left - a.left) * t
    val right = a.right + (b.right - a.right) * t
    val travel = abs(b.center - a.center)
    val bulge = sin(t * PI).toFloat() * travel * bulgeFraction
    val mid = (left + right) / 2f
    val half = (right - left) / 2f + bulge / 2f
    return LiquidTabSlot(mid - half, mid + half)
}

/** 汉字占满字身。拉丁大写不是。 */
internal fun labelHasIdeograph(text: String): Boolean {
    return text.any { ch ->
        val code = ch.code
        code in 0x2E80..0x9FFF || code in 0xF900..0xFAFF || code in 0xFF00..0xFFEF
    }
}

/**
 * 顶对齐时，把拉丁标签的笔画中心下移到同行汉字的笔画中心。
 * 汉字本身不动，下移量不超过文字和下划线之间的空隙。
 */
internal fun labelOpticalShiftPx(
    baselineFromTop: FloatArray,
    inkCenterFromBaseline: FloatArray,
    ideograph: BooleanArray,
    maxShift: Int,
): IntArray {
    val count = baselineFromTop.size
    if (count == 0 || inkCenterFromBaseline.size != count || ideograph.size != count) {
        return IntArray(count)
    }
    if (baselineFromTop.any { it <= 0f }) return IntArray(count)
    val centers = FloatArray(count) { baselineFromTop[it] + inkCenterFromBaseline[it] }
    var sum = 0.0
    var ideoCount = 0
    for (index in centers.indices) {
        if (!ideograph[index]) continue
        sum += centers[index]
        ideoCount += 1
    }
    val reference = if (ideoCount > 0) {
        (sum / ideoCount).toFloat()
    } else {
        centers.max()
    }
    return IntArray(count) { index ->
        if (ideoCount > 0 && ideograph[index]) {
            0
        } else {
            (reference - centers[index]).roundToInt().coerceIn(0, maxShift.coerceAtLeast(0))
        }
    }
}

@Composable
private fun defaultTypeface(weight: FontWeight): android.graphics.Typeface {
    val resolved = LocalFontFamilyResolver.current.resolve(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontStyle = FontStyle.Normal,
        fontSynthesis = FontSynthesis.All,
    ).value
    return resolved as? android.graphics.Typeface ?: android.graphics.Typeface.SANS_SERIF
}

private fun labelInkCenterFromBaseline(
    text: String,
    sizePx: Float,
    typeface: android.graphics.Typeface,
): Float {
    if (text.isEmpty() || sizePx <= 0f) return 0f
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        this.typeface = typeface
    }
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    return (bounds.top + bounds.bottom) / 2f
}

@Composable
internal fun LiquidUnderlineTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    position: Float? = null,
    fontSize: TextUnit = 15.sp,
    tabPadding: PaddingValues = PaddingValues(end = 18.dp, top = 4.dp, bottom = 2.dp),
    indicatorGap: Dp = 6.dp,
) {
    val motion = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    LaunchedEffect(selectedIndex, position) {
        if (position != null) return@LaunchedEffect
        val target = selectedIndex.coerceAtLeast(0).toFloat()
        if (motion.value == target) return@LaunchedEffect
        motion.animateTo(target, LiquidTabScroll)
    }
    val travel = position ?: motion.value
    var slots by remember { mutableStateOf<List<LiquidTabSlot>>(emptyList()) }
    var lineCenterY by remember { mutableFloatStateOf(0f) }
    val baselineFromTop = remember(labels.size) { FloatArray(labels.size) }
    val opticalShift = remember(labels.size) { IntArray(labels.size) }
    val mediumFace = defaultTypeface(FontWeight.Medium)
    val semiFace = defaultTypeface(FontWeight.SemiBold)
    Layout(
        modifier = modifier.drawWithContent {
            drawContent()
            val span = liquidTabSpan(slots, travel) ?: return@drawWithContent
            val stroke = LiquidTabLine.toPx()
            val width = (span.right - span.left).coerceAtLeast(stroke)
            val center = (span.left + span.right) / 2f
            val half = ((width - stroke) / 2f).coerceAtLeast(0f)
            drawLine(
                color = MainPalette.Accent,
                start = Offset(center - half, lineCenterY),
                end = Offset(center + half, lineCenterY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        },
        content = {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Column(
                    Modifier
                        .clickable(
                            interactionSource = remember(index) { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
                        )
                        .padding(tabPadding),
                ) {
                    Text(
                        text = label,
                        maxLines = 1,
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val line = placeable[FirstBaseline]
                            if (index < baselineFromTop.size) {
                                baselineFromTop[index] = if (line == AlignmentLine.Unspecified) 0f else line.toFloat()
                            }
                            layout(placeable.width, placeable.height) {
                                val shift = opticalShift.getOrElse(index) { 0 }
                                placeable.placeRelative(0, shift)
                            }
                        },
                        style = TextStyle(
                            color = if (selected) MainPalette.Accent else MainPalette.Secondary,
                            fontSize = fontSize,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    )
                    Spacer(Modifier.height(indicatorGap + LiquidTabLine))
                }
            }
        },
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            layout(0, 0) {}
        } else {
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val children = measurables.map { it.measure(loose) }
            val sizePx = fontSize.toPx()
            val maxShift = indicatorGap.roundToPx()
            val ink = FloatArray(children.size)
            val ideograph = BooleanArray(children.size)
            val baselines = FloatArray(children.size)
            for (index in children.indices) {
                val label = labels.getOrElse(index) { "" }
                ink[index] = labelInkCenterFromBaseline(
                    label,
                    sizePx,
                    if (index == selectedIndex) semiFace else mediumFace,
                )
                ideograph[index] = labelHasIdeograph(label)
                baselines[index] = baselineFromTop.getOrElse(index) { 0f }
            }
            val shifts = labelOpticalShiftPx(baselines, ink, ideograph, maxShift)
            for (index in shifts.indices) {
                if (index < opticalShift.size) opticalShift[index] = shifts[index]
            }
            val padStart = tabPadding.calculateStartPadding(layoutDirection).roundToPx()
            val padEnd = tabPadding.calculateEndPadding(layoutDirection).roundToPx()
            val padBottom = tabPadding.calculateBottomPadding().roundToPx()
            val line = LiquidTabLine.roundToPx()
            val rowHeight = children.maxOf { it.height }
            val contentWidth = children.sumOf { it.width }
            val width = contentWidth.coerceAtLeast(constraints.minWidth).let { raw ->
                if (constraints.maxWidth == Constraints.Infinity) raw else raw.coerceAtMost(constraints.maxWidth)
            }
            val computed = ArrayList<LiquidTabSlot>(children.size)
            var cursor = 0
            val origins = ArrayList<Int>(children.size)
            children.forEach { child ->
                origins += cursor
                val childLeft = if (layoutDirection == LayoutDirection.Ltr) {
                    cursor
                } else {
                    contentWidth - cursor - child.width
                }
                val textLeft = if (layoutDirection == LayoutDirection.Ltr) {
                    childLeft + padStart
                } else {
                    childLeft + padEnd
                }
                val textWidth = (child.width - padStart - padEnd).coerceAtLeast(0)
                computed += LiquidTabSlot(textLeft.toFloat(), (textLeft + textWidth).toFloat())
                cursor += child.width
            }
            val centerY = rowHeight - padBottom - line / 2f
            if (computed != slots) slots = computed
            if (centerY != lineCenterY) lineCenterY = centerY
            layout(width, rowHeight.coerceAtLeast(constraints.minHeight)) {
                children.forEachIndexed { index, child ->
                    child.placeRelative(origins[index], 0)
                }
            }
        }
    }
}
