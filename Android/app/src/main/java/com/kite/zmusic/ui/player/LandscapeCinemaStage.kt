package com.kite.zmusic.ui.player

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.rememberUrlImageBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 横屏影院。临时丢弃：入口已隐藏，见 LandscapePlayerPageType.Cinema。
 *
 * 当前句被放大成这一镜的画面。切到下一句，就是镜头拉远、拐到那一句、再推近。
 */
@Composable
internal fun LandscapeCinemaStage(
    track: TrackRow,
    lines: List<LrcLine>,
    lyricCompanions: List<LrcLine?>,
    originalOnTop: Boolean,
    showCompanionOnOthers: Boolean,
    positionMs: Long,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    buffering: Boolean,
    loadPending: Boolean,
    gesturesEnabled: Boolean,
    cameraMotion: Float,
    lyricDepth: Boolean,
    spectrumLighting: Boolean,
    lyricPlayingStyle: LyricRoleStyle,
    lyricPlayedStyle: LyricRoleStyle,
    lyricUnplayedStyle: LyricRoleStyle,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    transportRevealT: Float,
    transportReserve: Dp,
    floorCovers: List<String?>,
    modifier: Modifier = Modifier,
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as ZMusicApplication
    val spectrum = app.playbackBridge.spectrum.collectAsStateWithLifecycle()
    val bitmap = rememberUrlImageBitmap(track.coverUrl, 720)
    val floorBitmaps = rememberFloorBitmaps(floorCovers)
    var tint by remember { mutableStateOf(CinemaSteel) }
    LaunchedEffect(bitmap) {
        tint = sampleCoverTint(bitmap)
    }

    val motion = cameraMotion.coerceIn(0f, 1f)
    val alive = playWhenReady && isPlaying && !buffering && !loadPending

    val lyricClock = rememberSmoothedLyricPositionMs(positionMs, alive)
    val playCursor = playbackLyricCursor(lines, lyricClock, motion)
    val cursorNow = rememberUpdatedState(playCursor)
    var dragging by remember { mutableStateOf(false) }
    var dragCam by remember { mutableFloatStateOf(0f) }
    var heldCursor by remember { mutableStateOf<Float?>(null) }
    var windowCenter by remember { mutableIntStateOf(0) }
    fun shownCamera(): Float = when {
        dragging -> dragCam
        heldCursor != null -> heldCursor ?: playCursor
        else -> playCursor
    }

    LaunchedEffect(playCursor, heldCursor) {
        val hold = heldCursor ?: return@LaunchedEffect
        if (abs(playCursor - hold) < 0.45f) heldCursor = null
    }

    val seekLines = rememberUpdatedState(lines)
    val seekTo = rememberUpdatedState(onSeek)
    val paint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
    }
    val platePaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
    }
    val heroFrost = remember(bitmap) { bitmap?.let { frostThumb(it) } }
    val floorFrost = rememberFrostThumbs(floorBitmaps)
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(gesturesEnabled, track.id, lines.size) {
                if (!gesturesEnabled || seekLines.value.isEmpty()) return@pointerInput
                val slop = viewConfiguration.touchSlop
                val pxPerLine = 68.dp.toPx()
                val lyricGutter = size.width * 0.30f
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Main,
                    )
                    if (down.position.x < lyricGutter) return@awaitEachGesture
                    val pointerId = down.id
                    var lastY = down.position.y
                    var mode = 0
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.find { it.id == pointerId } ?: break
                        if (mode == 0 && change.isConsumed) break
                        val dy = change.position.y - down.position.y
                        if (mode == 0 && abs(dy) > slop && abs(dy) > abs(change.position.x - down.position.x)) {
                            mode = 1
                            dragCam = cursorNow.value
                            dragging = true
                            heldCursor = null
                            lastY = change.position.y
                            change.consume()
                        } else if (mode == 1) {
                            val last = seekLines.value.lastIndex.toFloat().coerceAtLeast(0f)
                            val step = (lastY - change.position.y) / pxPerLine
                            lastY = change.position.y
                            dragCam = (dragCam + step).coerceIn(-0.15f, last + 0.15f)
                            val center = dragCam.roundToInt().coerceIn(0, last.roundToInt())
                            if (center != windowCenter) windowCenter = center
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (mode == 1) {
                                val current = seekLines.value
                                val picked = dragCam.roundToInt().coerceIn(0, current.lastIndex)
                                heldCursor = picked.toFloat()
                                dragging = false
                                current.getOrNull(picked)?.let { seekTo.value(it.timeMs) }
                            }
                            break
                        }
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val reservePx = with(density) { transportReserve.toPx() }
        val board = remember(widthPx, lines.size) {
            buildLyricBoard(widthPx, lines.size)
        }
        val camera = shownCamera()
        val anchor = Offset(widthPx * FrameX, (heightPx - reservePx) * FrameY)
        val shot = cinemaShot(board, camera, motion)
        val frameW = widthPx.coerceAtLeast(1f)
        val frameH = (heightPx - reservePx).coerceAtLeast(1f)
        val activeIndex = if (lines.isEmpty()) {
            -1
        } else {
            camera.roundToInt().coerceIn(0, lines.lastIndex)
        }
        val activeTile = board.lineTile.getOrNull(activeIndex) ?: -1

        Canvas(Modifier.fillMaxSize()) {
            val low = if (spectrumLighting) spectrum.value.low else 0f
            val high = if (spectrumLighting) spectrum.value.high else 0f
            drawRect(
                brush = Brush.verticalGradient(
                    0.00f to Color(0xFF020308),
                    0.22f to Color(0xFF071018),
                    0.46f to Color(0xFF0A1016),
                    1.00f to Color(0xFF05070B),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.22f + low * 0.28f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.72f, size.height * 0.20f),
                    radius = size.width * 0.42f,
                ),
                radius = size.width * 0.42f,
                center = Offset(size.width * 0.72f, size.height * 0.20f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.05f + high * 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.80f, size.height * 0.16f),
                    radius = size.width * 0.18f,
                ),
                radius = size.width * 0.18f,
                center = Offset(size.width * 0.80f, size.height * 0.16f),
            )
            drawCoverMosaic(
                board = board,
                shot = shot,
                anchor = anchor,
                hero = bitmap,
                others = floorBitmaps,
                paint = paint,
                activeTile = activeTile,
                viewW = widthPx,
                viewH = heightPx,
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color(0xFF020308).copy(alpha = 0.72f),
                    ),
                    center = anchor,
                    radius = widthPx * 0.92f,
                ),
            )
        }

        val fromLine = (camera.toInt() - 1).coerceAtLeast(0)
        val toLine = (camera.toInt() + 1).coerceAtMost(lines.lastIndex)
        for (index in fromLine..toLine) {
            val tileIndex = board.lineTile.getOrNull(index) ?: -1
            val tile = board.tiles.getOrNull(tileIndex) ?: continue
            val line = lines[index]
            if (line.text.isBlank()) continue
            val rect = tileScreenRect(board, tile, shot, anchor)
            if (
                rect.left > widthPx + rect.edge ||
                rect.top > heightPx + rect.edge ||
                rect.left + rect.edge < -rect.edge ||
                rect.top + rect.edge < -rect.edge
            ) {
                continue
            }
            val dist = abs(camera - index.toFloat())
            if (dist > 0.52f) continue
            val presence = (1f - ((dist - 0.18f) / 0.34f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
            val focused = index == activeIndex
            val salt = tileSalt(tile)
            val art = if (focused || floorBitmaps.isEmpty()) {
                bitmap
            } else {
                floorBitmaps[salt.mod(floorBitmaps.size)] ?: bitmap
            }
            val frost = if (focused || floorFrost.isEmpty()) {
                heroFrost
            } else {
                floorFrost[salt.mod(floorFrost.size)]
            }
            androidx.compose.runtime.key(index, line.timeMs, tile.span) {
                CinemaCoverLyric(
                    line = line,
                    companion = lyricCompanions.getOrNull(index),
                    rect = rect,
                    art = art,
                    frost = frost,
                    focused = focused,
                    played = index < camera - 0.5f,
                    lyricDepth = lyricDepth,
                    motion = motion,
                    originalOnTop = originalOnTop,
                    showCompanionOnOthers = showCompanionOnOthers,
                    playingStyle = lyricPlayingStyle,
                    playedStyle = lyricPlayedStyle,
                    unplayedStyle = lyricUnplayedStyle,
                    measurer = measurer,
                    platePaint = platePaint,
                    density = density,
                    frameW = frameW,
                    frameH = frameH,
                    presence = presence,
                )
            }
        }

        val activeRect = board.tiles.getOrNull(activeTile)?.let {
            tileScreenRect(board, it, shot, anchor)
        }
        if (activeRect != null) {
            Box(
                Modifier
                    .offset {
                        IntOffset(activeRect.left.roundToInt(), activeRect.top.roundToInt())
                    }
                    .size(
                        with(density) { activeRect.edge.toDp() },
                        with(density) { activeRect.edge.toDp() },
                    )
                    .tapOnly(enabled = gesturesEnabled, onTap = onTogglePlay),
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val scrim = (0.20f + 0.62f * transportRevealT.coerceIn(0f, 1f)).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.72f to Color.Transparent,
                    1.00f to Color(0xFF05070B).copy(alpha = scrim),
                ),
            )
        }
    }
}

@Composable
private fun CinemaCoverLyric(
    line: LrcLine,
    companion: LrcLine?,
    rect: CoverTileRect,
    art: ImageBitmap?,
    frost: ImageBitmap?,
    focused: Boolean,
    played: Boolean,
    lyricDepth: Boolean,
    motion: Float,
    originalOnTop: Boolean,
    showCompanionOnOthers: Boolean,
    playingStyle: LyricRoleStyle,
    playedStyle: LyricRoleStyle,
    unplayedStyle: LyricRoleStyle,
    measurer: TextMeasurer,
    platePaint: AndroidPaint,
    density: Density,
    frameW: Float,
    frameH: Float,
    presence: Float,
) {
    val role = when {
        focused -> playingStyle
        played -> playedStyle
        else -> unplayedStyle
    }
    val fallback = when {
        focused -> Color(0xFFF6F3EC)
        played -> Color(0xFFC3CCD6)
        else -> Color(0xFFE6EDF4)
    }
    val subText = companion?.text?.takeIf { text ->
        text.isNotBlank() && (focused || showCompanionOnOthers)
    }
    val fitted = remember(line.text, subText, frameW, frameH, role, focused, fallback) {
        fitLyric(
            measurer = measurer,
            density = density,
            text = line.text,
            sub = subText,
            frameW = frameW,
            frameH = frameH,
            style = role,
            focused = focused,
            fallback = fallback,
        )
    }
    val plateW = fitted.plateW
    val plateH = fitted.plateH
    val lift = plateLiftPx(min(plateW, plateH), focused, lyricDepth, motion)
    val plateShape = RoundedCornerShape(percent = 14)
    val shadowPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = 0xC0000000.toInt() }
    }
    val depthScale = when {
        !lyricDepth -> 1f
        focused -> 1f
        else -> 0.94f
    }
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.edge.toDp() })
            .graphicsLayer { alpha = presence },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val left = rect.edge * PlateX - plateW / 2f
            val top = rect.edge * PlateY - plateH / 2f
            val rad = min(plateW, plateH) * 0.16f
            shadowPaint.maskFilter = BlurMaskFilter(lift.coerceIn(4f, 22f), BlurMaskFilter.Blur.NORMAL)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRoundRect(
                    RectF(left, top, left + plateW, top + plateH),
                    rad,
                    rad,
                    shadowPaint,
                )
            }
            shadowPaint.maskFilter = null
        }
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (rect.edge * PlateX - plateW / 2f).roundToInt(),
                        (rect.edge * PlateY - plateH / 2f).roundToInt(),
                    )
                }
                .size(
                    with(density) { plateW.toDp() },
                    with(density) { plateH.toDp() },
                )
                .graphicsLayer {
                    scaleX = depthScale
                    scaleY = depthScale
                    shadowElevation = lift
                    shape = plateShape
                    clip = false
                    ambientShadowColor = Color.Black
                    spotShadowColor = Color.Black
                }
                .clip(plateShape),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawFrostPlate(
                    art = art,
                    frost = frost,
                    paint = platePaint,
                    focused = focused,
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = with(density) { fitted.padX.toDp() }),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val mainText: @Composable () -> Unit = {
                    Text(
                        text = line.text,
                        maxLines = fitted.mainLines,
                        overflow = TextOverflow.Ellipsis,
                        style = fitted.main,
                    )
                }
                val subBlock: @Composable () -> Unit = {
                    val subStyle = fitted.sub
                    if (subText != null && subStyle != null) {
                        Text(
                            text = subText,
                            maxLines = fitted.subLines.coerceAtLeast(1),
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                            style = subStyle,
                        )
                    }
                }
                if (originalOnTop) {
                    mainText()
                    subBlock()
                } else {
                    subBlock()
                    mainText()
                }
            }
        }
    }
}

private fun DrawScope.drawFrostPlate(
    art: ImageBitmap?,
    frost: ImageBitmap?,
    paint: AndroidPaint,
    focused: Boolean,
) {
    val rad = size.minDimension * 0.12f
    val dst = RectF(0f, 0f, size.width, size.height)
    val blurred = frost ?: art
    val bmp = blurred?.let { runCatching { it.asAndroidBitmap() }.getOrNull() }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val checkpoint = native.save()
        val clip = android.graphics.Path().apply {
            addRoundRect(dst, rad, rad, android.graphics.Path.Direction.CW)
        }
        native.clipPath(clip)
        if (bmp != null && !bmp.isRecycled && bmp.width > 1 && bmp.height > 1) {
            paint.alpha = 255
            paint.maskFilter = null
            runCatching { native.drawBitmap(bmp, null, dst, paint) }
        }
        native.restoreToCount(checkpoint)
    }
    drawRoundRect(
        color = Color(0xFF0C1016).copy(alpha = if (focused) 0.55f else 0.62f),
        cornerRadius = CornerRadius(rad, rad),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = if (focused) 0.16f else 0.08f),
        cornerRadius = CornerRadius(rad, rad),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = if (focused) 0.34f else 0.14f),
        cornerRadius = CornerRadius(rad, rad),
        style = Stroke(width = if (focused) 1.5f else 1f),
    )
}

private data class FittedLyric(
    val main: TextStyle,
    val sub: TextStyle?,
    val mainLines: Int,
    val subLines: Int,
    val plateW: Float,
    val plateH: Float,
    val padX: Float,
)

/**
 * 当前句按这一屏来排，字要大到能当这一镜。长句缩小并折行，板子包住文字。
 */
private fun fitLyric(
    measurer: TextMeasurer,
    density: Density,
    text: String,
    sub: String?,
    frameW: Float,
    frameH: Float,
    style: LyricRoleStyle,
    focused: Boolean,
    fallback: Color,
): FittedLyric {
    val scale = style.sanitizedFontScale()
    val padX = (frameH * 0.04f).coerceIn(with(density) { 14.dp.toPx() }, with(density) { 28.dp.toPx() })
    val padY = (frameH * 0.03f).coerceIn(with(density) { 10.dp.toPx() }, with(density) { 20.dp.toPx() })
    val maxPlateW = frameW * 0.58f
    val maxPlateH = frameH * 0.52f
    val innerW = (maxPlateW - padX * 2f).roundToInt().coerceAtLeast(1)
    val innerH = (maxPlateH - padY * 2f).coerceAtLeast(1f)
    val minPx = frameH * 0.09f
    val maxPx = frameH * (if (focused) 0.20f else 0.16f) * scale
    val start = (frameH * (if (focused) 0.18f else 0.15f) * scale).coerceIn(minPx, maxPx)
    val wantSub = !sub.isNullOrBlank()

    fun plateOf(textW: Float, textH: Float): Pair<Float, Float> {
        val w = (textW + padX * 2f).coerceIn(frameW * 0.28f, maxPlateW)
        val h = (textH + padY * 2f).coerceIn(frameH * 0.18f, maxPlateH)
        return w to h
    }

    fun attempt(sizePx: Float, includeSub: Boolean, allowOverflow: Boolean): FittedLyric? {
        val linePx = sizePx * 1.16f
        val subPx = sizePx * 0.72f
        val subLinePx = subPx * 1.1f
        val useSub = includeSub && wantSub
        val subLines = if (useSub) 2 else 0
        val gap = if (useSub) with(density) { 2.dp.toPx() } else 0f
        val subBlock = if (useSub) subLinePx * subLines + gap else 0f
        val mainBudget = (innerH - subBlock).coerceAtLeast(linePx)
        val mainLines = (mainBudget / linePx).toInt().coerceIn(1, 3)
        val mainStyle = lyricInk(style, fallback, focused, sizePx, linePx, density, 1f)
        val mainLayout = measurer.measure(
            text = text,
            style = mainStyle,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            maxLines = mainLines,
            constraints = Constraints(maxWidth = innerW),
            density = density,
        )
        val subStyle = if (!useSub) {
            null
        } else {
            lyricInk(style, fallback, focused = false, subPx, subLinePx, density, 0.74f)
        }
        val subLayout = if (subStyle == null || sub == null) {
            null
        } else {
            measurer.measure(
                text = sub,
                style = subStyle,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                maxLines = subLines,
                constraints = Constraints(maxWidth = innerW),
                density = density,
            )
        }
        val textW = maxOf(mainLayout.size.width, subLayout?.size?.width ?: 0).toFloat()
        val textH = mainLayout.size.height + gap + (subLayout?.size?.height ?: 0)
        val overflow = mainLayout.hasVisualOverflow ||
            subLayout?.hasVisualOverflow == true ||
            textH > innerH + 1f
        if (overflow && !allowOverflow) return null
        val (plateW, plateH) = plateOf(textW, textH)
        return FittedLyric(mainStyle, subStyle, mainLines, subLines, plateW, plateH, padX)
    }

    var includeSub = wantSub
    var sizePx = start
    repeat(12) {
        attempt(sizePx, includeSub, allowOverflow = false)?.let { return it }
        val next = sizePx * 0.86f
        if (next < minPx) {
            if (includeSub) {
                includeSub = false
                sizePx = start
            } else {
                return attempt(minPx, includeSub = false, allowOverflow = true)
                    ?: run {
                        val (plateW, plateH) = plateOf(innerW.toFloat(), minPx * 1.16f)
                        FittedLyric(
                            lyricInk(style, fallback, focused, minPx, minPx * 1.16f, density, 1f),
                            null,
                            1,
                            0,
                            plateW,
                            plateH,
                            padX,
                        )
                    }
            }
        } else {
            sizePx = next
        }
    }
    return attempt(minPx, includeSub = false, allowOverflow = true)
        ?: run {
            val (plateW, plateH) = plateOf(innerW.toFloat(), minPx * 1.16f)
            FittedLyric(
                lyricInk(style, fallback, focused, minPx, minPx * 1.16f, density, 1f),
                null,
                1,
                0,
                plateW,
                plateH,
                padX,
            )
        }
}

private fun lyricInk(
    style: LyricRoleStyle,
    fallback: Color,
    focused: Boolean,
    sizePx: Float,
    linePx: Float,
    density: Density,
    alpha: Float,
): TextStyle {
    return TextStyle(
        color = Color(style.resolvedArgb(fallback.toArgb())).copy(alpha = alpha),
        fontFamily = FontFamily.SansSerif,
        fontWeight = when {
            focused -> FontWeight.SemiBold
            style.bold -> FontWeight.Medium
            else -> FontWeight.Normal
        },
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = with(density) { sizePx.toSp() },
        lineHeight = with(density) { linePx.toSp() },
        textAlign = TextAlign.Center,
    )
}

private fun plateLiftPx(plate: Float, focused: Boolean, lyricDepth: Boolean, motion: Float): Float {
    val bias = if (focused) 1f else if (lyricDepth) 0.42f else 0.72f
    return (plate * (if (focused) 0.11f else 0.06f) * bias * (0.55f + 0.45f * motion)).coerceIn(10f, 46f)
}

private fun Modifier.tapOnly(enabled: Boolean, onTap: () -> Unit): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val origin = down.position
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.find { it.id == down.id } ?: break
            if ((change.position - origin).getDistance() > slop) moved = true
            if (!change.pressed) {
                if (!moved) {
                    change.consume()
                    onTap()
                }
                break
            }
        }
    }
}

private data class CoverTile(val x: Int, val y: Int, val span: Int)

private data class CoverTileRect(val left: Float, val top: Float, val edge: Float)

private class LyricBoard(
    val cell: Float,
    val gap: Float,
    val margin: Float,
    val tiles: List<CoverTile>,
    val lineTile: IntArray,
)

private fun tileSalt(tile: CoverTile): Int = tile.x * 17 + tile.y * 13 + tile.span * 3

private data class CinemaShot(val lookX: Float, val lookY: Float, val zoom: Float)

private fun tileScreenRect(
    board: LyricBoard,
    tile: CoverTile,
    shot: CinemaShot,
    anchor: Offset,
): CoverTileRect {
    val worldLeft = tile.x * board.cell + board.gap / 2f
    val worldTop = tile.y * board.cell + board.gap / 2f
    val worldEdge = tile.span * board.cell - board.gap
    return CoverTileRect(
        left = anchor.x + (worldLeft - shot.lookX) * shot.zoom,
        top = anchor.y + (worldTop - shot.lookY) * shot.zoom,
        edge = worldEdge * shot.zoom,
    )
}

/** 停在当前句。离开时先拉远，沿封面绕到下一句，再推近。每一句换一种幅度。 */
private fun cinemaShot(board: LyricBoard, camera: Float, motion: Float): CinemaShot {
    if (board.lineTile.isEmpty()) return CinemaShot(0f, 0f, ShotRest)
    val last = board.lineTile.lastIndex
    val cursor = camera.coerceIn(0f, last.toFloat().coerceAtLeast(0f))
    val i0 = cursor.toInt().coerceIn(0, last)
    val i1 = (i0 + if (cursor > i0) 1 else 0).coerceAtMost(last)
    val p0 = plateAnchor(board, i0)
    val p1 = plateAnchor(board, i1)
    val t = if (i0 == i1) 0f else (cursor - i0).coerceIn(0f, 1f)
    val verb = i0 and 3
    val pullScale = when (verb) {
        0 -> 0.82f
        1 -> 1.12f
        2 -> 0.96f
        else -> 1.06f
    }
    val motionT = motion.coerceIn(0f, 1f)
    val wide = ShotRest - (0.18f + 0.28f * motionT) * pullScale
    val rise = smoothstep(0.04f, 0.28f, t)
    val settle = smoothstep(0.62f, 0.96f, t)
    val zoom = (ShotRest + (wide - ShotRest) * (rise * (1f - settle))).coerceIn(0.55f, 1.22f)
    val dx = p1.x - p0.x
    val dy = p1.y - p0.y
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val bowAmp = board.cell * (if (verb == 3) 1.15f else 0.62f) * (0.45f + 0.55f * motionT)
    val bow = sin(t * PI.toFloat()) * bowAmp * if (verb % 2 == 0) 1f else -1f
    return CinemaShot(
        lookX = p0.x + dx * t + (-dy / len) * bow,
        lookY = p0.y + dy * t + (dx / len) * bow,
        zoom = zoom,
    )
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun plateAnchor(board: LyricBoard, line: Int): Offset {
    val tile = board.tiles.getOrNull(board.lineTile.getOrNull(line) ?: -1)
        ?: return Offset(board.margin, board.margin)
    val left = tile.x * board.cell + board.gap / 2f
    val top = tile.y * board.cell + board.gap / 2f
    val edge = tile.span * board.cell - board.gap
    return Offset(left + edge * PlateX, top + edge * PlateY)
}

/** 播放进度走到下一句前才开镜，落点在画面偏左上，不在正中。 */
private fun playbackLyricCursor(lines: List<LrcLine>, positionMs: Long, motion: Float): Float {
    if (lines.isEmpty()) return 0f
    val pos = positionMs.coerceAtLeast(0L)
    var index = 0
    val last = lines.lastIndex
    while (index < last && lines[index + 1].timeMs <= pos) index++
    if (index >= last) return index.toFloat()
    val remain = lines[index + 1].timeMs - pos
    val travelMs = 420f + motion.coerceIn(0f, 1f) * 680f
    if (remain >= travelMs) return index.toFloat()
    val u = (1f - remain / travelMs).coerceIn(0f, 1f)
    val eased = u * u * (3f - 2f * u)
    return index + eased
}

private fun buildLyricBoard(
    width: Float,
    lineCount: Int,
): LyricBoard {
    val edge = width * 1.12f
    val gap = edge * 0.010f
    val cell = (edge + gap) / 2f
    if (lineCount <= 0) {
        return LyricBoard(cell, gap, 0f, emptyList(), IntArray(0))
    }
    val tiles = ArrayList<CoverTile>()
    val at = HashMap<Long, Int>()
    fun ensure(cx: Int, cy: Int): Int {
        val key = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)
        at[key]?.let { return it }
        val index = tiles.size
        tiles += CoverTile(cx * 2, cy * 2, 2)
        at[key] = index
        return index
    }
    var minX = 2
    var maxX = 2
    var minY = 2
    var maxY = 2
    val lineTile = IntArray(lineCount)
    for (i in 0 until lineCount) {
        val cx = 2 + i * 2
        val cy = if ((i and 1) == 0) 2 else 3
        lineTile[i] = ensure(cx, cy)
        if (cx < minX) minX = cx
        if (cx > maxX) maxX = cx
        if (cy < minY) minY = cy
        if (cy > maxY) maxY = cy
    }
    for (y in (minY - 1)..(maxY + 1)) {
        for (x in (minX - 1)..(maxX + 1)) {
            ensure(x, y)
        }
    }
    return LyricBoard(cell, gap, 0f, tiles, lineTile)
}

private const val FrameX = 0.32f
private const val FrameY = 0.30f
private const val PlateX = 0.34f
private const val PlateY = 0.30f
private const val ShotRest = 1.10f

private fun DrawScope.drawCoverMosaic(
    board: LyricBoard,
    shot: CinemaShot,
    anchor: Offset,
    hero: ImageBitmap?,
    others: List<ImageBitmap?>,
    paint: AndroidPaint,
    activeTile: Int,
    viewW: Float,
    viewH: Float,
) {
    fun drawTile(index: Int) {
        val tile = board.tiles[index]
        val rect = tileScreenRect(board, tile, shot, anchor)
        if (
            rect.left > viewW + 8f ||
            rect.top > viewH + 8f ||
            rect.left + rect.edge < -8f ||
            rect.top + rect.edge < -8f
        ) {
            return
        }
        val heroTile = index == activeTile
        val salt = if (heroTile) 0 else tileSalt(tile)
        val pooled = if (others.isEmpty()) {
            hero
        } else {
            others[salt.mod(others.size)] ?: hero
        }
        drawCoverSquare(
            bitmap = if (heroTile) hero else pooled,
            paint = paint,
            left = rect.left,
            top = rect.top,
            edge = rect.edge,
            salt = salt,
            shade = if (heroTile) 0.02f else 0.05f + (salt.mod(5)) * 0.018f,
            heroTile = heroTile,
        )
    }
    for (index in board.tiles.indices) {
        if (index != activeTile) drawTile(index)
    }
    if (activeTile in board.tiles.indices) drawTile(activeTile)
}

@Composable
private fun rememberFrostThumbs(bitmaps: List<ImageBitmap?>): List<ImageBitmap?> {
    val out = ArrayList<ImageBitmap?>(bitmaps.size)
    for (i in bitmaps.indices) {
        val src = bitmaps[i]
        out += remember(src) { src?.let { frostThumb(it) } }
    }
    return out
}

private fun frostThumb(src: ImageBitmap): ImageBitmap? {
    val raw = runCatching { src.asAndroidBitmap() }.getOrNull() ?: return null
    if (raw.isRecycled || raw.width < 2 || raw.height < 2) return null
    val base = if (raw.config == Bitmap.Config.HARDWARE) {
        runCatching { raw.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return null
    } else {
        raw
    }
    val tiny = runCatching { Bitmap.createScaledBitmap(base, 32, 32, true) }.getOrNull() ?: return null
    val work = if (tiny === raw || tiny === base) {
        tiny.copy(Bitmap.Config.ARGB_8888, true) ?: return null
    } else {
        tiny
    }
    if (base !== raw && base !== work) base.recycle()
    val pixels = IntArray(32 * 32)
    if (!runCatching { work.getPixels(pixels, 0, 32, 0, 0, 32, 32) }.isSuccess) return null
    var blurred = boxBlur(pixels, 32, 32, 3)
    blurred = boxBlur(blurred, 32, 32, 2)
    work.setPixels(blurred, 0, 32, 0, 0, 32, 32)
    return work.asImageBitmap()
}

private fun boxBlur(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
    val out = IntArray(src.size)
    val span = radius * 2 + 1
    for (y in 0 until height) {
        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (ky in -radius..radius) {
                val yy = (y + ky).coerceIn(0, height - 1)
                val row = yy * width
                for (kx in -radius..radius) {
                    val c = src[row + (x + kx).coerceIn(0, width - 1)]
                    a += c ushr 24
                    r += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                }
            }
            val n = span * span
            out[y * width + x] = (a / n shl 24) or (r / n shl 16) or (g / n shl 8) or (b / n)
        }
    }
    return out
}

private fun DrawScope.drawCoverSquare(
    bitmap: ImageBitmap?,
    paint: AndroidPaint,
    left: Float,
    top: Float,
    edge: Float,
    salt: Int,
    shade: Float,
    heroTile: Boolean,
) {
    if (edge <= 1f) return
    val right = left + edge
    val bottom = top + edge
    val bmp = bitmap?.let { runCatching { it.asAndroidBitmap() }.getOrNull() }
    if (bmp != null && !bmp.isRecycled && bmp.width > 2 && bmp.height > 2) {
        val src = sourceRect(bmp, salt)
        val dst = RectF(left, top, right, bottom)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val checkpoint = native.save()
            native.clipRect(left, top, right, bottom)
            paint.alpha = 255
            runCatching { native.drawBitmap(bmp, src, dst, paint) }
            native.restoreToCount(checkpoint)
        }
    } else {
        drawRect(
            color = Color(0xFF141A22),
            topLeft = Offset(left, top),
            size = Size(edge, edge),
        )
    }
    if (shade > 0.01f) {
        drawRect(
            color = Color.Black.copy(alpha = shade),
            topLeft = Offset(left, top),
            size = Size(edge, edge),
        )
    }
    if (heroTile) {
        val inset = 1.2f
        drawRect(
            color = Color.White.copy(alpha = 0.22f),
            topLeft = Offset(left + inset, top + inset),
            size = Size((edge - inset * 2f).coerceAtLeast(1f), (edge - inset * 2f).coerceAtLeast(1f)),
            style = Stroke(width = 1.25f),
        )
    }
}

private fun sourceRect(bmp: Bitmap, salt: Int): Rect {
    val crop = tileCrop(bmp.width, bmp.height, salt)
    val maxL = (bmp.width - 1).coerceAtLeast(0)
    val maxT = (bmp.height - 1).coerceAtLeast(0)
    val left = crop[0].roundToInt().coerceIn(0, maxL)
    val top = crop[1].roundToInt().coerceIn(0, maxT)
    val right = (crop[0] + crop[2]).roundToInt().coerceIn(left + 1, bmp.width)
    val bottom = (crop[1] + crop[3]).roundToInt().coerceIn(top + 1, bmp.height)
    return Rect(left, top, right, bottom)
}

private fun tileCrop(bw: Int, bh: Int, salt: Int): FloatArray {
    if (salt == 0) return floatArrayOf(0f, 0f, bw.toFloat(), bh.toFloat())
    val side = min(bw, bh).toFloat()
    val frac = 0.86f
    val cw = side * frac
    val ch = cw
    val maxX = (bw - cw).coerceAtLeast(0f)
    val maxY = (bh - ch).coerceAtLeast(0f)
    val hx = (salt * 17).mod(100) / 100f
    val hy = (salt * 53).mod(100) / 100f
    return floatArrayOf(maxX * hx, maxY * hy, cw, ch)
}

private fun sampleCoverTint(bitmap: ImageBitmap?): Color {
    if (bitmap == null) return CinemaSteel
    val bmp = runCatching { bitmap.asAndroidBitmap() }.getOrNull() ?: return CinemaSteel
    if (bmp.isRecycled || bmp.config == Bitmap.Config.HARDWARE) return CinemaSteel
    val w = bmp.width
    val h = bmp.height
    if (w < 2 || h < 2) return CinemaSteel
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    val stepX = (w / 8).coerceAtLeast(1)
    val stepY = (h / 8).coerceAtLeast(1)
    val row = IntArray(w)
    var y = h / 8
    while (y < h * 7 / 8) {
        val copied = runCatching {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
        }.isSuccess
        if (!copied) break
        var x = 0
        while (x < w) {
            val c = row[x]
            r += AndroidColor.red(c)
            g += AndroidColor.green(c)
            b += AndroidColor.blue(c)
            n++
            x += stepX
        }
        y += stepY
    }
    if (n == 0L) return CinemaSteel
    val mix = 0.38f
    val rr = ((r / n) * mix + 16f * (1f - mix)).roundToInt().coerceIn(0, 255)
    val gg = ((g / n) * mix + 28f * (1f - mix)).roundToInt().coerceIn(0, 255)
    val bb = ((b / n) * mix + 42f * (1f - mix)).roundToInt().coerceIn(0, 255)
    return Color(rr, gg, bb)
}

@Composable
private fun rememberFloorBitmaps(urls: List<String?>): List<ImageBitmap?> {
    val out = ArrayList<ImageBitmap?>(12)
    for (i in 0 until 12) {
        out += rememberUrlImageBitmap(urls.getOrNull(i), 420)
    }
    return out
}

private val CinemaSteel = Color(0xFF8EA4B5)

