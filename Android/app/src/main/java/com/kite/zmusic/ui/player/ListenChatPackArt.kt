package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kite.zmusic.listen.ListenChatPack
import com.kite.zmusic.listen.ListenChatPackKind
import com.kite.zmusic.listen.listenChatPackMotionValue
import com.kite.zmusic.listen.listenChatPackPose
import com.kite.zmusic.ui.main.MainPalette

@Composable
internal fun ListenChatPackArt(
    pack: ListenChatPack,
    play: Boolean,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    size: Dp = 68.dp,
    onPlayStart: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    if (preview && !play) {
        ListenChatPackArtPreview(pack = pack, modifier = modifier, size = size)
    } else {
        ListenChatPackArtPlay(
            pack = pack,
            play = play,
            modifier = modifier,
            size = size,
            onPlayStart = onPlayStart,
            onFinished = onFinished,
        )
    }
}

@Composable
private fun ListenChatPackArtPreview(
    pack: ListenChatPack,
    modifier: Modifier,
    size: Dp,
) {
    val transition = rememberInfiniteTransition(label = "packPreview")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "packPreviewT",
    )
    ListenChatPackCanvas(
        pack = pack,
        progress = progress,
        settleAt = 1.01f,
        modifier = modifier,
        size = size,
    )
}

@Composable
private fun ListenChatPackArtPlay(
    pack: ListenChatPack,
    play: Boolean,
    modifier: Modifier,
    size: Dp,
    onPlayStart: () -> Unit,
    onFinished: () -> Unit,
) {
    var progress by remember(pack) { mutableFloatStateOf(if (play) 0f else 1f) }
    LaunchedEffect(pack, play) {
        if (!play) {
            progress = 1f
            return@LaunchedEffect
        }
        onPlayStart()
        val anim = Animatable(0f)
        anim.animateTo(1f, tween(980, easing = FastOutSlowInEasing)) {
            progress = value
        }
        progress = 1f
        onFinished()
    }
    ListenChatPackCanvas(
        pack = pack,
        progress = progress,
        settleAt = 0.84f,
        modifier = modifier,
        size = size,
    )
}

@Composable
private fun ListenChatPackCanvas(
    pack: ListenChatPack,
    progress: Float,
    settleAt: Float,
    modifier: Modifier,
    size: Dp,
) {
    val face = listenChatPackMotionValue(pack.kind, progress, pack.value, settleAt)
    val pose = listenChatPackPose(progress, settleAt)
    val ink = MainPalette.Ink
    val paper = MainPalette.Surface
    val accent = MainPalette.Accent
    Canvas(modifier.size(size)) {
        val c = center
        rotate(pose.rotation, c) {
            scale(pose.scaleX, pose.scaleY, c) {
                when (pack.kind) {
                    ListenChatPackKind.Dice -> if (pose.edgeOn) drawDieEdge(ink, paper) else drawDieFace(face, ink, paper, accent)
                    ListenChatPackKind.Rps -> drawRpsHand(face, ink, accent)
                }
            }
        }
    }
}

private fun DrawScope.drawDieFace(face: String, ink: Color, paper: Color, accent: Color) {
    val side = size.minDimension * 0.78f
    val top = Offset((size.width - side) / 2f, (size.height - side) / 2f)
    val corner = CornerRadius(side * 0.18f, side * 0.18f)
    drawRoundRect(
        color = ink.copy(alpha = 0.16f),
        topLeft = top + Offset(0f, side * 0.06f),
        size = Size(side, side),
        cornerRadius = corner,
    )
    drawRoundRect(color = paper, topLeft = top, size = Size(side, side), cornerRadius = corner)
    drawRoundRect(
        color = ink.copy(alpha = 0.22f),
        topLeft = top,
        size = Size(side, side),
        cornerRadius = corner,
        style = Stroke(width = side * 0.035f),
    )
    val pipColor = if (face == "1") accent else ink
    val pips = diePips(face)
    val radius = side * 0.075f
    pips.forEach { pip ->
        drawCircle(
            color = pipColor,
            radius = radius,
            center = Offset(top.x + pip.x * side, top.y + pip.y * side),
        )
    }
}

private fun DrawScope.drawDieEdge(ink: Color, paper: Color) {
    val side = size.minDimension * 0.78f
    val thickness = side * 0.16f
    val top = Offset((size.width - thickness) / 2f, (size.height - side) / 2f)
    val corner = CornerRadius(thickness / 2f, thickness / 2f)
    drawRoundRect(color = ink.copy(alpha = 0.55f), topLeft = top, size = Size(thickness, side), cornerRadius = corner)
    drawRoundRect(
        color = paper,
        topLeft = top + Offset(thickness * 0.22f, side * 0.04f),
        size = Size(thickness * 0.28f, side * 0.92f),
        cornerRadius = CornerRadius(thickness * 0.14f, thickness * 0.14f),
    )
}

private fun diePips(face: String): List<Offset> = when (face) {
    "1" -> listOf(Offset(0.5f, 0.5f))
    "2" -> listOf(Offset(0.28f, 0.28f), Offset(0.72f, 0.72f))
    "3" -> listOf(Offset(0.28f, 0.28f), Offset(0.5f, 0.5f), Offset(0.72f, 0.72f))
    "4" -> listOf(Offset(0.28f, 0.28f), Offset(0.72f, 0.28f), Offset(0.28f, 0.72f), Offset(0.72f, 0.72f))
    "5" -> listOf(
        Offset(0.28f, 0.28f),
        Offset(0.72f, 0.28f),
        Offset(0.5f, 0.5f),
        Offset(0.28f, 0.72f),
        Offset(0.72f, 0.72f),
    )
    else -> listOf(
        Offset(0.30f, 0.24f),
        Offset(0.70f, 0.24f),
        Offset(0.30f, 0.5f),
        Offset(0.70f, 0.5f),
        Offset(0.30f, 0.76f),
        Offset(0.70f, 0.76f),
    )
}

private fun DrawScope.drawRpsHand(value: String, ink: Color, accent: Color) {
    val s = size.minDimension
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    fun point(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    drawOval(
        color = accent.copy(alpha = 0.18f),
        topLeft = point(0.16f, 0.62f),
        size = Size(s * 0.68f, s * 0.22f),
    )
    when (value) {
        "scissors" -> drawScissors(::point, s, ink)
        "paper" -> drawPaper(::point, s, ink)
        else -> drawRock(::point, s, ink)
    }
}

private fun DrawScope.drawRock(point: (Float, Float) -> Offset, s: Float, ink: Color) {
    val fist = Path().apply {
        moveTo(point(0.32f, 0.40f).x, point(0.32f, 0.40f).y)
        curve(point(0.30f, 0.22f), point(0.72f, 0.20f), point(0.74f, 0.42f))
        curve(point(0.84f, 0.50f), point(0.82f, 0.74f), point(0.66f, 0.84f))
        curve(point(0.52f, 0.94f), point(0.34f, 0.92f), point(0.24f, 0.78f))
        curve(point(0.12f, 0.64f), point(0.16f, 0.48f), point(0.32f, 0.40f))
        close()
    }
    val thumb = Path().apply {
        moveTo(point(0.28f, 0.58f).x, point(0.28f, 0.58f).y)
        curve(point(0.10f, 0.56f), point(0.08f, 0.78f), point(0.28f, 0.80f))
        curve(point(0.36f, 0.80f), point(0.38f, 0.66f), point(0.28f, 0.58f))
        close()
    }
    drawPath(fist, ink)
    drawPath(thumb, ink)
    val crease = ink.copy(alpha = 0.35f)
    listOf(0.40f, 0.52f, 0.64f).forEach { x ->
        drawLine(
            color = crease,
            start = point(x, 0.36f),
            end = point(x, 0.48f),
            strokeWidth = s * 0.035f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawScissors(point: (Float, Float) -> Offset, s: Float, ink: Color) {
    val palm = Path().apply {
        moveTo(point(0.30f, 0.62f).x, point(0.30f, 0.62f).y)
        curve(point(0.22f, 0.70f), point(0.24f, 0.90f), point(0.48f, 0.92f))
        curve(point(0.72f, 0.94f), point(0.82f, 0.76f), point(0.74f, 0.62f))
        curve(point(0.64f, 0.54f), point(0.40f, 0.54f), point(0.30f, 0.62f))
        close()
    }
    drawFinger(point(0.46f, 0.58f), s * 0.13f, s * 0.50f, -22f, ink)
    drawFinger(point(0.58f, 0.58f), s * 0.13f, s * 0.48f, 18f, ink)
    drawPath(palm, ink)
}

private fun DrawScope.drawPaper(point: (Float, Float) -> Offset, s: Float, ink: Color) {
    val palm = Path().apply {
        moveTo(point(0.28f, 0.48f).x, point(0.28f, 0.48f).y)
        curve(point(0.20f, 0.62f), point(0.22f, 0.90f), point(0.50f, 0.94f))
        curve(point(0.78f, 0.96f), point(0.86f, 0.70f), point(0.78f, 0.50f))
        lineTo(point(0.28f, 0.48f).x, point(0.28f, 0.48f).y)
        close()
    }
    listOf(0.30f to 0f, 0.44f to -3f, 0.58f to 2f, 0.72f to 6f).forEach { (x, deg) ->
        drawFinger(point(x, 0.46f), s * 0.12f, s * 0.40f, deg.toFloat(), ink)
    }
    drawFinger(point(0.22f, 0.58f), s * 0.13f, s * 0.28f, -48f, ink)
    drawPath(palm, ink)
}

private fun DrawScope.drawFinger(base: Offset, width: Float, length: Float, degrees: Float, ink: Color) {
    rotate(degrees, base) {
        drawRoundRect(
            color = ink,
            topLeft = Offset(base.x - width / 2f, base.y - length),
            size = Size(width, length),
            cornerRadius = CornerRadius(width / 2f, width / 2f),
        )
    }
}

private fun Path.curve(a: Offset, b: Offset, c: Offset) {
    cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
}
