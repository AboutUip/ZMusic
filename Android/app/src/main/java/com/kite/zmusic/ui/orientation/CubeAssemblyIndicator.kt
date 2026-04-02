package com.kite.zmusic.ui.orientation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val VTop = Color(0xFF4FFFE8)
private val VLeft = Color(0xFF00B89A)
private val VRight = Color(0xFF008F7A)

/**
 * 8 个小立方体从外散状态滑移拼成一个大立方体（等轴测绘制），用于方向切换蒙版。
 */
@Composable
fun CubeAssemblyIndicator(
    modifier: Modifier = Modifier.size(120.dp),
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        )
    }
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val unit = kotlin.math.min(size.width, size.height) * 0.14f
        val spread = 2.35f * (1f - progress.value) + 1f
        val voxelScale = 0.42f + 0.58f * progress.value

        val centers = voxelCenters2x2x2()
        val screen = centers.map { (x, y, z) ->
            val sx = x * spread
            val sy = y * spread
            val sz = z * spread
            isoProject(sx, sy, sz, unit * 1.15f).let { (ix, iy) ->
                Offset(cx + ix, cy + iy)
            }
        }
        val depths = centers.map { (x, y, z) -> x + y + z }
        val order = depths.indices.sortedBy { depths[it] }
        for (i in order) {
            drawVoxel(
                center = screen[i],
                s = unit * voxelScale,
                top = VTop.copy(alpha = 0.92f),
                left = VLeft.copy(alpha = 0.88f),
                right = VRight.copy(alpha = 0.88f),
            )
        }
    }
}

private fun voxelCenters2x2x2(): List<Triple<Float, Float, Float>> {
    val o = listOf(-0.5f, 0.5f)
    val out = ArrayList<Triple<Float, Float, Float>>(8)
    for (x in o) for (y in o) for (z in o) out.add(Triple(x, y, z))
    return out
}

/** 等轴测投影到屏幕像素偏移（相对立方体中心）。 */
private fun isoProject(x: Float, y: Float, z: Float, scale: Float): Pair<Float, Float> {
    val ix = (x - z) * 0.8660254f * scale
    val iy = ((x + z) * 0.5f - y * 1.15f) * scale
    return ix to iy
}

private fun DrawScope.drawVoxel(
    center: Offset,
    s: Float,
    top: Color,
    left: Color,
    right: Color,
) {
    val w = s * 0.52f
    val h = s * 0.38f
    val cx = center.x
    val cy = center.y

    val topPath = Path().apply {
        moveTo(cx, cy - h)
        lineTo(cx + w, cy)
        lineTo(cx, cy + h)
        lineTo(cx - w, cy)
        close()
    }
    drawPath(topPath, top)

    val rightPath = Path().apply {
        moveTo(cx + w, cy)
        lineTo(cx + w + w * 0.15f, cy + h * 0.22f)
        lineTo(cx + w * 0.15f, cy + h + h * 0.22f)
        lineTo(cx, cy + h)
        close()
    }
    drawPath(rightPath, right)

    val leftPath = Path().apply {
        moveTo(cx - w, cy)
        lineTo(cx, cy + h)
        lineTo(cx - w * 0.15f, cy + h + h * 0.22f)
        lineTo(cx - w - w * 0.15f, cy + h * 0.22f)
        close()
    }
    drawPath(leftPath, left)

    val edge = Color.White.copy(alpha = 0.12f)
    drawPath(topPath, edge, style = Stroke(width = 0.8f))
}
