package com.kite.zmusic.ui.player

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kite.zmusic.data.PlayerDisplayPrefsStore
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.theme.TextTheme

@Composable
private fun rememberFlightLook(expand: PlayerExpandState): PlayerExpandLook {
    val reported = expand.look
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val fallback = remember(landscape) {
        val name = if (landscape) {
            PlayerDisplayPrefsStore.PREFS
        } else {
            PlayerDisplayPrefsStore.PREFS_PORTRAIT
        }
        PlayerExpandLook.from(PlayerDisplayPrefsStore(context, name).load(), landscape)
    }
    // 只接受当前方向的播放页偏好，避免横/竖屏个性化串台。
    return if (reported != null && reported.landscape == landscape) reported else fallback
}

@Composable
internal fun PlayerExpandFlightLayer(
    expand: PlayerExpandState,
    track: TrackRow,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!expand.mounted) return
    val density = LocalDensity.current
    val look = rememberFlightLook(expand)
    val playFill = TextTheme.PlayerPlayFill
    val p = expand.visualProgress
    val titleColor = lerpColor(TextTheme.MiniPlayerTitle, look.titleDestColor, p)
    val playTint = lerpColor(TextTheme.MiniPlayerIcon, TextTheme.PlayerPlayIcon, p)

    val miniCover = expand.toShell(expand.miniCover)
    val miniTitle = expand.toShell(expand.miniTitle)
    val miniPlay = expand.toShell(expand.miniPlay)
    val destVinyl = flightVinylDest(expand, miniCover)
    val styleT = (p / PlayerExpandHandoff).coerceIn(0f, 1f)
    val destCoverT = if (look.vinylFullCover) 1f else 0f
    val coverT = lerp(0f, destCoverT, styleT)
    val destHole = (look.vinylCenterRadiusFrac / VinylCoverFrac).coerceIn(0.08f, 0.95f) *
        (1f - destCoverT)
    val hole = destHole * styleT
    val outer = lerp(1f, look.vinylOuterScale, styleT)
    val coverFrac = lerp(1f, VinylCoverFrac, styleT)
    val spindle = (SpindleHoleFrac / outer.coerceAtLeast(0.01f)).coerceIn(0.02f, 0.35f) *
        (1f - coverT)

    Box(modifier.fillMaxSize()) {
        if (miniCover.isAnchorValid()) {
            val srcW = miniCover.width
            val srcH = miniCover.height
            val corner0 = with(density) { 10.dp.toPx() }
            Box(
                Modifier
                    .size(
                        with(density) { srcW.toDp() },
                        with(density) { srcH.toDp() },
                    )
                    .graphicsLayer {
                        alpha = expand.flightAlpha
                        val move = flightCenterTranslation(miniCover, destVinyl, p)
                        val scale = flightUniformScale(miniCover, destVinyl, p)
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        translationX = move.x
                        translationY = move.y
                        scaleX = scale
                        scaleY = scale
                        clip = false
                        shadowElevation = 0f
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            scaleX = outer
                            scaleY = outer
                            clip = false
                            shadowElevation = 0f
                        }
                        .clip(VinylCircleShape),
                ) {
                    VinylDiscPlate(
                        Modifier.fillMaxSize(),
                        spindleHoleFrac = spindle,
                        colors = look.plateColors,
                        drawRim = true,
                    )
                }
                val coverCorner = lerp(corner0, minOf(srcW, srcH) / 2f, styleT)
                Box(
                    Modifier
                        .fillMaxSize(coverFrac)
                        .clip(
                            if (hole < 0.012f) {
                                RoundedCornerShape(with(density) { coverCorner.toDp() })
                            } else {
                                VinylAnnulusShape(holeFrac = hole)
                            },
                        ),
                ) {
                    UrlImage(
                        url = track.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        if (miniTitle.isAnchorValid()) {
            Text(
                text = track.name,
                style = TextStyle(
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .size(
                        with(density) { miniTitle.width.toDp() },
                        with(density) { miniTitle.height.toDp() },
                    )
                    .graphicsLayer {
                        alpha = expand.flightAlpha
                        val dest = expand.toShell(expand.fullTitle)
                        val end = if (dest.isAnchorValid()) dest else miniTitle
                        val scale = flightTitleScale(p, look.titleFontMul)
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = lerp(miniTitle.left, end.left, p)
                        translationY = lerp(miniTitle.top, end.top, p)
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }

        if (miniPlay.isAnchorValid()) {
            Box(
                Modifier
                    .size(
                        with(density) { miniPlay.width.toDp() },
                        with(density) { miniPlay.height.toDp() },
                    )
                    .graphicsLayer {
                        alpha = expand.flightAlpha
                        val dest = expand.toShell(expand.fullPlay)
                        val move = flightCenterTranslation(miniPlay, dest, p)
                        val scale = flightUniformScale(miniPlay, dest, p)
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        translationX = move.x
                        translationY = move.y
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .drawBehind {
                        drawCircle(lerpColor(Color.Transparent, playFill, p))
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlay,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TransportPlayPauseIcon(
                    playing = isPlaying,
                    buffering = false,
                    size = 22.dp,
                    tint = playTint,
                    modifier = Modifier.graphicsLayer {
                        val dest = expand.toShell(expand.fullPlay)
                        val s = flightUniformScale(miniPlay, dest, p).coerceAtLeast(0.01f)
                        scaleX = 1f / s
                        scaleY = 1f / s
                    },
                )
            }
        }
    }
}

@Composable
internal fun PlayerExpandFlightProgress(
    expand: PlayerExpandState,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    if (!expand.mounted) return
    val mini = expand.toShell(expand.miniProgress)
    if (!mini.isProgressAnchorValid()) return
    val density = LocalDensity.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val p = expand.visualProgress
    val destBar = expand.toShell(expand.fullProgress)
    val barEnd = if (destBar.isAnchorValid()) destBar else mini
    val bar = lerpRect(mini, barEnd, p)
    val timeW = with(density) { 36.dp.toPx() }
    val timeH = with(density) { 14.dp.toPx() }
    val gap = with(density) { 6.dp.toPx() }
    val elapsedDest = timeDestOrFallback(
        reported = expand.toShell(expand.fullElapsedTime),
        bar = barEnd,
        start = true,
        landscape = landscape,
        width = timeW,
        height = timeH,
        gap = gap,
    )
    val durationDest = timeDestOrFallback(
        reported = expand.toShell(expand.fullDurationTime),
        bar = barEnd,
        start = false,
        landscape = landscape,
        width = timeW,
        height = timeH,
        gap = gap,
    )
    val timesAbove = elapsedDest.isTimeAnchorValid() &&
        elapsedDest.center.y < barEnd.top - 1f
    val elapsedSeed = Offset(
        bar.left,
        if (timesAbove) bar.top else bar.center.y,
    )
    val durationSeed = Offset(
        bar.right,
        if (timesAbove) bar.top else bar.center.y,
    )
    val timeColor = TextTheme.PlayerTime.copy(alpha = if (landscape) 0.7f else 0.92f)
    val timeWeight = if (landscape) FontWeight.Normal else FontWeight.Medium
    val active0 = MainPalette.Accent
    val off0 = MainPalette.Hairline
    val active1 = TextTheme.PlayerProgressActive
    val off1 = TextTheme.PlayerProgressOff
    val thumb1 = TextTheme.PlayerProgressThumb
    val destThumbW = with(density) { PlayerProgressHandle.ThumbWidth.toPx() }
    val destGap = with(density) { PlayerProgressHandle.TrackGap.toPx() }
    val destInside = with(density) { PlayerProgressHandle.InsideCorner.toPx() }
    val frac = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = expand.flightAlpha },
        ) {
            drawFlightProgressBar(
                bar = bar,
                frac = frac,
                p = p,
                off = lerpColor(off0, off1, p),
                on = lerpColor(active0, active1, p),
                thumb = lerpColor(active0, thumb1, p),
                destThumbW = destThumbW,
                destGap = destGap,
                destInside = destInside,
            )
        }
        FlightTimeLabel(
            expand = expand,
            text = formatTimeMs(positionMs),
            dest = elapsedDest,
            seed = elapsedSeed,
            color = timeColor,
            fontWeight = timeWeight,
            align = if (landscape) TextAlign.End else TextAlign.Start,
        )
        FlightTimeLabel(
            expand = expand,
            text = formatTimeMs(durationMs),
            dest = durationDest,
            seed = durationSeed,
            color = timeColor,
            fontWeight = timeWeight,
            align = if (landscape) TextAlign.Start else TextAlign.End,
        )
    }
}

private fun DrawScope.drawFlightProgressBar(
    bar: Rect,
    frac: Float,
    p: Float,
    off: Color,
    on: Color,
    thumb: Color,
    destThumbW: Float,
    destGap: Float,
    destInside: Float,
) {
    val h = bar.height.coerceAtLeast(1f)
    val w = bar.width.coerceAtLeast(1f)
    val thumbW = lerp(0f, destThumbW, p)
    val thumbH = h
    val gap = lerp(0f, destGap, p)
    val inside = lerp(h / 2f, destInside.coerceAtMost(h / 2f), p)
    val cx = drawPlayerProgressTracksAt(
        left = bar.left,
        top = bar.top,
        width = w,
        height = h,
        frac = frac,
        thumbWidth = thumbW,
        gap = gap,
        insideCorner = inside,
        active = on,
        inactive = off,
        insetForThumb = true,
    )
    if (thumbW > 0.8f && thumbH > 0.8f) {
        drawRoundRect(
            color = thumb,
            topLeft = Offset(cx - thumbW / 2f, bar.center.y - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f),
        )
    }
}

internal fun DrawScope.drawPlayerProgressTracksAt(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    frac: Float,
    thumbWidth: Float,
    gap: Float,
    insideCorner: Float,
    active: Color,
    inactive: Color,
    insetForThumb: Boolean,
): Float {
    val visualFrac = if (layoutDirection == LayoutDirection.Rtl) 1f - frac else frac
    val outerR = height / 2f
    val insideR = insideCorner.coerceAtMost(outerR)
    val inset = if (insetForThumb) thumbWidth / 2f else 0f
    val travel = (width - if (insetForThumb) thumbWidth else 0f).coerceAtLeast(0f)
    val cx = left + inset + travel * visualFrac
    val split = thumbWidth / 2f + gap
    if (split < 1f) {
        drawRoundRect(
            color = inactive,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(outerR),
        )
        val activeW = (width * visualFrac).coerceIn(0f, width)
        if (activeW > 0.5f) {
            drawRoundRect(
                color = active,
                topLeft = Offset(left, top),
                size = Size(activeW, height),
                cornerRadius = CornerRadius(outerR),
            )
        }
        return cx
    }
    val right = left + width
    val activeEnd = (cx - split).coerceIn(left, right)
    val inactiveStart = (cx + split).coerceIn(left, right)
    drawGappedTrackSegment(
        left = inactiveStart,
        top = top,
        width = right - inactiveStart,
        height = height,
        startRadius = insideR,
        endRadius = outerR,
        color = inactive,
    )
    drawGappedTrackSegment(
        left = left,
        top = top,
        width = activeEnd - left,
        height = height,
        startRadius = outerR,
        endRadius = insideR,
        color = active,
    )
    return cx
}

private fun DrawScope.drawGappedTrackSegment(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    startRadius: Float,
    endRadius: Float,
    color: Color,
) {
    if (width <= 0.5f || height <= 0.5f) return
    val path = Path()
    val sr = startRadius.coerceAtMost(height / 2f).coerceAtLeast(0f)
    val er = endRadius.coerceAtMost(height / 2f).coerceAtLeast(0f)
    path.addRoundRect(
        RoundRect(
            left = left,
            top = top,
            right = left + width,
            bottom = top + height,
            topLeftCornerRadius = CornerRadius(sr),
            topRightCornerRadius = CornerRadius(er),
            bottomRightCornerRadius = CornerRadius(er),
            bottomLeftCornerRadius = CornerRadius(sr),
        )
    )
    drawPath(path, color)
}

private fun timeDestOrFallback(
    reported: Rect,
    bar: Rect,
    start: Boolean,
    landscape: Boolean,
    width: Float,
    height: Float,
    gap: Float,
): Rect {
    if (reported.isTimeAnchorValid()) return reported
    val w = width.coerceAtLeast(8f)
    val h = height.coerceAtLeast(8f)
    return if (landscape) {
        val top = bar.center.y - h / 2f
        if (start) {
            Rect(bar.left - gap - w, top, bar.left - gap, top + h)
        } else {
            Rect(bar.right + gap, top, bar.right + gap + w, top + h)
        }
    } else {
        val top = bar.top - gap - h
        if (start) {
            Rect(bar.left, top, bar.left + w, top + h)
        } else {
            Rect(bar.right - w, top, bar.right, top + h)
        }
    }
}

@Composable
private fun FlightTimeLabel(
    expand: PlayerExpandState,
    text: String,
    dest: Rect,
    seed: Offset,
    color: Color,
    fontWeight: FontWeight,
    align: TextAlign,
) {
    if (!dest.isTimeAnchorValid()) return
    val density = LocalDensity.current
    val p = expand.visualProgress
    Text(
        text = text,
        style = TextStyle(
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
            fontWeight = fontWeight,
            textAlign = align,
        ),
        maxLines = 1,
        modifier = Modifier
            .size(
                with(density) { dest.width.toDp() },
                with(density) { dest.height.toDp() },
            )
            .graphicsLayer {
                alpha = expand.flightAlpha
                val cx = lerp(seed.x, dest.center.x, p)
                val cy = lerp(seed.y, dest.center.y, p)
                val s = lerp(0.16f, 1f, p)
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                translationX = cx - dest.width / 2f
                translationY = cy - dest.height / 2f
                scaleX = s
                scaleY = s
            },
    )
}
