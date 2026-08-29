package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp
import com.kite.zmusic.playback.pulseSpectrum
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

internal val LyricCurrent = Color(0xFFF2EDE6)
internal val LyricDim = Color(0xFF7A8899)
internal val AccentRose = Color(0xFFE8B4BC)
internal val CyanSoft = Color(0xFF6FD4D4)
internal val OrbInk = Color(0xFF090B12)
internal val PlayerPlayFill = Color.White
internal val PlayerPlayIcon = Color(0xFF111111)

/**
 * 横屏 Gemini 式透光光球：从 Android [GeminiOrbsBackdrop] 复制。
 * 蔷薇=低音、淡紫=中音、青蓝=高音；开关与切歌/拖动有可打断过渡。
 */
@Composable
internal fun GeminiOrbsBackdrop(
    modifier: Modifier = Modifier,
    activeHalo: Boolean = true,
    playWhenReady: Boolean = false,
    positionMs: Long = 0L,
    scrubbing: Boolean = false,
    trackId: Long = 0L,
    loadPending: Boolean = false,
    motionEnabled: Boolean = true,
) {
    val haloEase = CubicBezierEasing(0.22f, 0.8f, 0.28f, 1f)
    val crossEase = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1f)

    val haloGate = remember { Animatable(if (activeHalo) 1f else 0f) }
    LaunchedEffect(activeHalo) {
        val target = if (activeHalo) 1f else 0f
        val distance = abs(target - haloGate.value).coerceIn(0f, 1f)
        val durationMs = (640f * distance).toInt().coerceIn(220, 640)
        haloGate.animateTo(target, tween(durationMs, easing = haloEase))
    }

    val energyGate = remember {
        Animatable(if (playWhenReady && !loadPending) 1f else 0f)
    }
    LaunchedEffect(playWhenReady, loadPending) {
        val target = if (playWhenReady && !loadPending) 1f else 0f
        val down = target < energyGate.value
        energyGate.animateTo(
            target,
            tween(if (down) 520 else 380, easing = haloEase),
        )
    }

    val trackCross = remember { Animatable(1f) }
    var lastTrackId by remember { mutableLongStateOf(trackId) }
    LaunchedEffect(trackId) {
        if (trackId == 0L) return@LaunchedEffect
        if (lastTrackId == 0L) {
            lastTrackId = trackId
            trackCross.snapTo(1f)
            return@LaunchedEffect
        }
        if (trackId == lastTrackId) return@LaunchedEffect
        lastTrackId = trackId
        trackCross.animateTo(0.05f, tween(340, easing = crossEase))
        trackCross.animateTo(1f, tween(860, easing = crossEase))
    }

    val seekCross = remember { Animatable(1f) }
    var lastSeekPos by remember { mutableLongStateOf(positionMs) }
    var lastSeekTrack by remember { mutableLongStateOf(trackId) }
    val scrubbingRef = rememberUpdatedState(scrubbing)
    val posRef = rememberUpdatedState(positionMs)
    val playingRef = rememberUpdatedState(playWhenReady && !loadPending)
    LaunchedEffect(trackId) {
        if (trackId != lastSeekTrack) {
            lastSeekTrack = trackId
            lastSeekPos = posRef.value
            seekCross.snapTo(1f)
        }
        snapshotFlow { posRef.value }.collect { pos ->
            if (trackId != lastSeekTrack) {
                lastSeekTrack = trackId
                lastSeekPos = pos
                seekCross.snapTo(1f)
                return@collect
            }
            val jump = abs(pos - lastSeekPos)
            lastSeekPos = pos
            if (scrubbingRef.value || jump < 800L) return@collect
            val dip = (0.38f + (1f - (jump / 45_000f).coerceIn(0f, 1f)) * 0.28f)
                .coerceIn(0.38f, 0.66f)
                .coerceAtMost(seekCross.value)
            seekCross.animateTo(dip, tween(200, easing = crossEase))
            seekCross.animateTo(1f, tween(640, easing = crossEase))
        }
    }

    val scrubGate = remember { Animatable(1f) }
    LaunchedEffect(scrubbing) {
        if (scrubbing) {
            scrubGate.animateTo(0.48f, tween(220, easing = haloEase))
        } else {
            scrubGate.animateTo(1f, tween(560, easing = haloEase))
        }
    }

    val orbSim = remember { floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f) }
    val lagBands = remember { floatArrayOf(0f, 0f, 0f) }
    var drawGen by remember { mutableIntStateOf(0) }

    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        if (System.getProperty("zmusic.test") == "true") return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L) {
                    val dt = ((now - last).coerceIn(0L, 48L)) / 1000f
                    val gate = haloGate.value
                    val speed = lerp(1f, 1.28f, gate)
                    orbSim[0] = (orbSim[0] + dt / 22f * speed) % 1f
                    orbSim[1] = (orbSim[1] + dt / 31f * speed) % 1f
                    orbSim[2] = (orbSim[2] + dt / 17f * speed) % 1f
                    val presence = (
                        gate *
                            energyGate.value *
                            trackCross.value *
                            seekCross.value *
                            scrubGate.value
                        ).coerceIn(0f, 1f)
                    val sp = pulseSpectrum(playingRef.value, posRef.value)
                    fun lag(cur: Float, target: Float, tau: Float): Float {
                        val a = (1f - exp(-dt / tau)).coerceIn(0f, 1f)
                        return cur + (target - cur) * a
                    }
                    lagBands[0] = lag(lagBands[0], sp.low.coerceIn(0f, 1f), 0.11f)
                    lagBands[1] = lag(lagBands[1], sp.mid.coerceIn(0f, 1f), 0.13f)
                    lagBands[2] = lag(lagBands[2], sp.high.coerceIn(0f, 1f), 0.10f)
                    fun follow(cur: Float, target: Float): Float {
                        val tau = if (target > cur) 0.18f else 0.48f
                        return lag(cur, target, tau)
                    }
                    orbSim[3] = follow(orbSim[3], (lagBands[0] * presence).coerceIn(0f, 1f))
                    orbSim[4] = follow(orbSim[4], (lagBands[1] * presence).coerceIn(0f, 1f))
                    orbSim[5] = follow(orbSim[5], (lagBands[2] * presence).coerceIn(0f, 1f))
                    drawGen++
                }
                last = now
            }
        }
    }

    Canvas(modifier.background(OrbInk)) {
        drawGen
        val phaseA = orbSim[0]
        val phaseB = orbSim[1]
        val phaseC = orbSim[2]
        val lowT = orbSim[3]
        val midT = orbSim[4]
        val highT = orbSim[5]
        val gate = haloGate.value
        val presence = (
            gate *
                energyGate.value *
                trackCross.value *
                seekCross.value *
                scrubGate.value
            ).coerceIn(0f, 1f)
        val baseScale = if (gate > 0.05f) lerp(1f, 0.55f, presence) else 1f
        val w = size.width
        val h = size.height
        val twoPi = (Math.PI * 2).toFloat()
        fun orb(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = alpha),
                    0.55f to color.copy(alpha = alpha * 0.38f),
                    1f to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
        val a = phaseA * twoPi
        val b = phaseB * twoPi
        val c = phaseC * twoPi
        val pulse = 1f + 0.12f * sin(a)
        val pulseInv = 1f + 0.10f * sin(a + Math.PI.toFloat())
        orb(
            cx = w * (0.22f + 0.14f * cos(a)),
            cy = h * (0.32f + 0.16f * sin(a)),
            radius = minOf(w, h) * 0.5f * pulse * (1f + 0.18f * lowT),
            color = Color(0xFFE8A0C8),
            alpha = (0.22f * baseScale + 0.58f * lowT).coerceIn(0f, 0.92f),
        )
        orb(
            cx = w * (0.72f + 0.12f * cos(b + 1.2f)),
            cy = h * (0.68f + 0.14f * sin(b + 0.4f)),
            radius = minOf(w, h) * 0.58f * (0.96f + 0.04f * sin(b)) * (1f + 0.16f * highT),
            color = Color(0xFF6EB8FF),
            alpha = (0.20f * baseScale + 0.55f * highT).coerceIn(0f, 0.90f),
        )
        orb(
            cx = w * (0.58f + 0.11f * sin(c)),
            cy = h * (0.40f + 0.17f * cos(c)),
            radius = minOf(w, h) * 0.44f * pulseInv * (1f + 0.14f * midT),
            color = Color(0xFFB8A0FF),
            alpha = (0.18f * baseScale + 0.52f * midT).coerceIn(0f, 0.88f),
        )
        val ambience = maxOf(lowT, midT, highT)
        orb(
            cx = w * (0.38f + 0.26f * sin(b)),
            cy = h * (0.88f + 0.04f * cos(b * 2f)),
            radius = w * 0.46f * (0.94f + 0.06f * sin(c)) * (1f + 0.08f * ambience),
            color = Color(0xFFFFC9A8),
            alpha = 0.18f * baseScale + 0.22f * ambience,
        )
    }
}
