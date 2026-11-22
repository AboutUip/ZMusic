package com.kite.zmusic.ui.player

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as AndroidPaint
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricWord
import com.kite.zmusic.data.PlayerBackgroundPreset
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.karaokeWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 横屏电影。每句歌词是摆在三维空间里的一块字幕板，彼此错开深度和角度，板子本身不动。
 * 换句时摄像机推、摇、升降到下一块前面，旧句从镜头边擦过去；停留时镜头仍在缓慢推近。
 * 背景是用户的自定义背景（没开就是暗场），点阵立体和几何碎片散在同一空间里各自漂。
 */
@Composable
internal fun LandscapeMovieStage(
    track: TrackRow,
    lines: List<LrcLine>,
    lyricCompanions: List<LrcLine?>,
    originalOnTop: Boolean,
    positionMs: Long,
    durationMs: Long,
    clockRunning: Boolean,
    dotShapes: Boolean,
    spectrumPulse: Boolean,
    upcomingLines: Boolean,
    transportRevealT: Float,
    chromeSidePad: Dp,
    chromeTopPad: Dp,
    customBg: PlayerBackgroundPreset?,
    customBgProgress: Float,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val spectrum = app.playbackBridge.spectrum.collectAsStateWithLifecycle()
    val pulse = rememberUpdatedState(spectrumPulse)
    val clock = remember { mutableLongStateOf(positionMs) }
    MovieClockPump(positionMs, clockRunning, clock)
    val time = rememberMovieTime(clockRunning)

    val noLyrics = lines.isEmpty()
    val stageLines = remember(lines, track.name) {
        lines.ifEmpty { listOf(LrcLine(0L, track.name.ifBlank { " " })) }
    }
    val stageCompanions = remember(noLyrics, lyricCompanions, track.artists) {
        if (noLyrics) {
            listOf(track.artists.takeIf { it.isNotBlank() }?.let { LrcLine(0L, it) })
        } else {
            lyricCompanions
        }
    }
    val focus by remember(stageLines, durationMs) {
        derivedStateOf {
            lyricFocusIndex(stageLines, lyricAnimActiveIndex(stageLines, clock.longValue, durationMs))
                .coerceIn(0, stageLines.lastIndex)
        }
    }

    val backPath = customBg?.imagePath.orEmpty()
    var accent by remember { mutableStateOf(MovieAccentFallback) }
    LaunchedEffect(backPath) {
        accent = if (backPath.isBlank()) {
            MovieAccentFallback
        } else {
            withContext(Dispatchers.IO) {
                decodeBackgroundThumb(backPath, 48)?.let { movieAccent(it) } ?: MovieAccentFallback
            }
        }
    }
    val accentState = animateColorAsState(accent, tween(900), label = "movie_accent")

    val models = remember { movieDotModels() }
    val decoPaths = remember { movieDecoPaths() }
    val dotPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
        }
    }
    val dotBuffers = remember(models) { MovieDotBuffers(models.maxOf { it.count } * MovieDotActors) }
    val measurer = rememberTextMeasurer(cacheSize = 24)

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(MovieNight)) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val f = h * MovieFocalK
        val arts = remember(stageLines, stageCompanions, originalOnTop, w, h, density) {
            MovieArtCache(stageLines, stageCompanions, originalOnTop, measurer, density, w, h)
        }
        val world = remember(arts, track.id) { MovieWorld(arts, stageLines.size, track.id, w, h, f) }
        val rig = remember(world) { MovieRig(world, focus) }
        val swarm = remember(track.id) { MovieSwarm(track.id, models) }
        var introTrack by remember { mutableLongStateOf(Long.MIN_VALUE) }
        LaunchedEffect(rig) {
            if (introTrack == track.id) return@LaunchedEffect
            introTrack = track.id
            rig.intro(time.floatValue)
        }
        LaunchedEffect(rig, focus) {
            rig.go(focus, time.floatValue, movieMoveMs(stageLines, focus, durationMs))
        }

        val backEffect = remember(density) { movieBackEffect(with(density) { MovieBackBlur.toPx() }) }
        val backPose = remember(rig) { MoviePose() }
        val hasBack = customBg != null && customBgProgress > 0.001f
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = time.floatValue
                    rig.pose(backPose, t)
                    movieHandheld(backPose, t, h)
                    val kick = sin(rig.ease() * PI.toFloat())
                    val low = if (pulse.value) spectrum.value.low else 0f
                    val zoom = MovieBackZoom + 0.03f * kick + 0.02f * low + 0.012f * sin(t * MovieTau / 29f)
                    scaleX = zoom
                    scaleY = zoom
                    translationX = -backPose.yaw * h * MovieBackPan
                    translationY = backPose.pitch * h * MovieBackPan
                    rotationZ = -backPose.roll * MovieDeg
                    renderEffect = backEffect
                },
        ) {
            if (hasBack) {
                PlayerCustomBackgroundLayer(
                    preset = customBg,
                    progress = customBgProgress,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        Brush.radialGradient(
                            0f to MovieHazeA,
                            1f to Color.Transparent,
                            center = Offset(w * 0.34f, h * 0.38f),
                            radius = h * 0.8f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            0f to MovieHazeB,
                            1f to Color.Transparent,
                            center = Offset(w * 0.72f, h * 0.66f),
                            radius = h * 0.7f,
                        ),
                    )
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val low = if (pulse.value) spectrum.value.low else 0f
            if (hasBack) drawRect(Color.Black.copy(alpha = MovieBackDim * customBgProgress.coerceIn(0f, 1f)))
            drawRect(
                Brush.radialGradient(
                    0f to accentState.value.copy(alpha = 0.07f + 0.1f * low),
                    1f to Color.Transparent,
                    center = Offset(w * 0.56f, h * 0.5f),
                    radius = h * 0.95f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.78f),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = max(w, h) * 0.66f,
                ),
            )
        }

        val view = remember(rig) { MovieView() }
        val pose = remember(rig) { MoviePose() }
        val plane = remember { MoviePlane() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val e = rig.ease()
            rig.pose(pose, t)
            movieHandheld(pose, t, h)
            view.set(pose, f, size.width, size.height)
            val bands = spectrum.value
            val low = if (pulse.value) bands.low else 0f
            val high = if (pulse.value) bands.high else 0f

            swarm.step(view, t)
            swarm.drawDecos(this, view, decoPaths, plane, t)
            if (dotShapes) swarm.drawDots(this, view, dotBuffers, dotPaint, t, low, high)

            val at = rig.toIndex
            for (k in at - 1..at) {
                val art = world.art(k) ?: continue
                val a = if (k == at) movieStep(0.15f, 1f, e) else 1f - movieStep(0f, 0.6f, e)
                drawMovieGhost(art, world.mark(k), view, plane, MovieGhostAlpha * a)
            }
            val positionMs = clock.longValue
            val accentNow = accentState.value
            for (k in at + 1 downTo at - 1) {
                val art = world.art(k) ?: continue
                val role = when (k) {
                    at - 1 -> 1f - movieStep(0.55f, 1f, e)
                    at -> 1f
                    else -> if (rig.moving) movieStep(0.25f, 0.95f, e) else 1f
                }
                if (role <= 0.004f) continue
                val bright = when {
                    k < at -> 1f
                    k == at -> movieStep(0f, 0.7f, e)
                    else -> 0f
                }
                drawMovieLine(
                    art = art,
                    mark = world.mark(k),
                    view = view,
                    plane = plane,
                    alpha = role,
                    bright = bright,
                    positionMs = positionMs,
                    shortenMs = lyricSweepLeadMs(stageLines, k, durationMs),
                    accent = accentNow,
                )
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val reveal = transportRevealT.coerceIn(0f, 1f)
            if (reveal <= 0.01f) return@Canvas
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.38f * reveal),
                    0.22f to Color.Transparent,
                    0.64f to Color.Transparent,
                    1f to MovieNight.copy(alpha = 0.8f * reveal),
                ),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = chromeSidePad, top = chromeTopPad)
                .widthIn(max = maxWidth * 0.46f)
                .graphicsLayer { alpha = transportRevealT.coerceIn(0f, 1f) },
        ) {
            Text(
                text = track.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                ),
            )
            if (track.artists.isNotBlank()) {
                Text(
                    text = track.artists,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.62f),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                )
            }
        }

        val upcomingMaxW = maxWidth * 0.6f
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .graphicsLayer { alpha = (1f - transportRevealT * 1.6f).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (upcomingLines && !noLyrics) {
                val at = rig.toIndex
                Column(
                    Modifier.graphicsLayer { alpha = movieStep(0.2f, 1f, rig.move.value) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MovieUpcomingText(
                        text = stageLines.getOrNull(at + 1)?.text.orEmpty(),
                        alpha = 0.46f,
                        maxWidth = upcomingMaxW,
                    )
                    MovieUpcomingText(
                        text = stageLines.getOrNull(at + 2)?.text.orEmpty(),
                        alpha = 0.3f,
                        maxWidth = upcomingMaxW,
                    )
                }
                Spacer(Modifier.height(9.dp))
            }
            Canvas(Modifier.size(width = 112.dp, height = 3.dp)) {
                val total = durationMs.coerceAtLeast(1L)
                val p = (clock.longValue.toFloat() / total).coerceIn(0f, 1f)
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = r)
                if (p > 0f) {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.82f),
                        size = Size(size.width * p, size.height),
                        cornerRadius = r,
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieClockPump(positionMs: Long, tracking: Boolean, out: MutableLongState) {
    val smooth = rememberSmoothedLyricPositionMs(positionMs, tracking)
    SideEffect { out.longValue = smooth }
}

/** 舞台时钟。暂停时不停，只放慢，镜头和漂浮物仍在呼吸。 */
@Composable
private fun rememberMovieTime(running: Boolean): FloatState {
    val time = remember { mutableFloatStateOf(0f) }
    val speed = rememberUpdatedState(if (running) 1f else MovieIdleSpeed)
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
            time.floatValue += dt * speed.value
        }
    }
    return time
}

@Composable
private fun MovieUpcomingText(text: String, alpha: Float, maxWidth: Dp) {
    Text(
        text = text.ifBlank { " " },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = maxWidth),
        style = TextStyle(
            color = Color.White.copy(alpha = alpha),
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.6.sp,
            textAlign = TextAlign.Center,
        ),
    )
}

private fun movieMoveMs(lines: List<LrcLine>, index: Int, durationMs: Long): Int {
    if (index !in lines.indices) return 900
    val window = if (index > 0) {
        min(lyricLineSpanMs(lines, index - 1, durationMs), lyricLineSpanMs(lines, index, durationMs))
    } else {
        lyricLineSpanMs(lines, index, durationMs)
    }
    return (window * 0.6f).toInt().coerceIn(MovieMoveMinMs, MovieMoveMaxMs)
}

/** 3D 平台用：模糊 + 去饱和一起做。31 以下没有 RenderEffect，只靠压暗。 */
private fun movieBackEffect(blurPx: Float): RenderEffect? {
    if (Build.VERSION.SDK_INT < 31) return null
    val desat = AndroidColorMatrix().apply { setSaturation(MovieBackSaturation) }
    val color = AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(desat))
    if (blurPx < 0.5f) return color.asComposeRenderEffect()
    val blur = AndroidRenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
    return AndroidRenderEffect.createChainEffect(color, blur).asComposeRenderEffect()
}

// ---- 摄像机 ----

/** 世界坐标单位是像素；角度是弧度。相机空间 x 向右、y 向下、z 朝画面里。 */
private class MoviePose {
    var x = 0f
    var y = 0f
    var z = 0f
    var yaw = 0f
    var pitch = 0f
    var roll = 0f

    fun set(o: MoviePose) {
        x = o.x
        y = o.y
        z = o.z
        yaw = o.yaw
        pitch = o.pitch
        roll = o.roll
    }

    fun blend(a: MoviePose, b: MoviePose, t: Float) {
        x = lerp(a.x, b.x, t)
        y = lerp(a.y, b.y, t)
        z = lerp(a.z, b.z, t)
        yaw = lerp(a.yaw, b.yaw, t)
        pitch = lerp(a.pitch, b.pitch, t)
        roll = lerp(a.roll, b.roll, t)
    }
}

/**
 * 偏航 · 俯仰 · 滚转合成的旋转（行主序）。三列分别是右、下、前在世界里的方向。
 * 偏航为正镜头右转，俯仰为正镜头抬头。
 */
private fun movieBasis(yaw: Float, pitch: Float, roll: Float, out: FloatArray) {
    val ca = cos(yaw)
    val sa = sin(yaw)
    val cb = cos(pitch)
    val sb = sin(pitch)
    val cc = cos(roll)
    val sc = sin(roll)
    out[0] = ca * cc + sa * sb * sc
    out[1] = -ca * sc + sa * sb * cc
    out[2] = sa * cb
    out[3] = cb * sc
    out[4] = cb * cc
    out[5] = -sb
    out[6] = -sa * cc + ca * sb * sc
    out[7] = sa * sc + ca * sb * cc
    out[8] = ca * cb
}

/** 极轻的手持晃动。几组互不整除的频率叠在一起，看不出周期。 */
private fun movieHandheld(p: MoviePose, t: Float, h: Float) {
    p.x += h * 0.006f * (sin(t * 0.41f) + 0.5f * sin(t * 1.13f + 0.7f))
    p.y += h * 0.005f * (sin(t * 0.33f + 1.1f) + 0.5f * sin(t * 0.97f + 2.1f))
    p.yaw += 0.004f * (sin(t * 0.27f + 0.3f) + 0.6f * sin(t * 0.73f + 1.9f))
    p.pitch += 0.003f * (sin(t * 0.31f + 2.4f) + 0.6f * sin(t * 0.89f + 0.4f))
    p.roll += 0.0025f * (sin(t * 0.23f + 1.3f) + 0.5f * sin(t * 0.67f + 2.7f))
}

private class MovieView {
    val b = FloatArray(9)
    val q = FloatArray(3)
    var x = 0f
    var y = 0f
    var z = 0f
    var f = 1f
    var cx = 0f
    var cy = 0f

    fun set(p: MoviePose, focal: Float, w: Float, h: Float) {
        movieBasis(p.yaw, p.pitch, p.roll, b)
        x = p.x
        y = p.y
        z = p.z
        f = focal
        cx = w / 2f
        cy = h / 2f
    }

    /** 世界点 → 相机空间，写进 [q]。 */
    fun point(wx: Float, wy: Float, wz: Float) = dir(wx - x, wy - y, wz - z)

    fun dir(dx: Float, dy: Float, dz: Float) {
        q[0] = b[0] * dx + b[3] * dy + b[6] * dz
        q[1] = b[1] * dx + b[4] * dy + b[7] * dz
        q[2] = b[2] * dx + b[5] * dy + b[8] * dz
    }

    fun toWorld(qx: Float, qy: Float, qz: Float, out: FloatArray) {
        out[0] = x + b[0] * qx + b[1] * qy + b[2] * qz
        out[1] = y + b[3] * qx + b[4] * qy + b[5] * qz
        out[2] = z + b[6] * qx + b[7] * qy + b[8] * qz
    }

    /**
     * 把一块平面投成屏幕上的透视矩阵：局部坐标（像素，原点在板中心）直接画进去即可。
     * 板子任何一角贴近镜头或整个甩出画面时返回 -1。
     */
    fun plane(
        px: Float,
        py: Float,
        pz: Float,
        basis: FloatArray,
        scale: Float,
        halfW: Float,
        halfH: Float,
        out: MoviePlane,
    ): Float {
        point(px, py, pz)
        val pcx = q[0]
        val pcy = q[1]
        val pcz = q[2]
        if (pcz <= 0f) return -1f
        dir(basis[0], basis[3], basis[6])
        val ux = q[0] * scale
        val uy = q[1] * scale
        val uz = q[2] * scale
        dir(basis[1], basis[4], basis[7])
        val vx = q[0] * scale
        val vy = q[1] * scale
        val vz = q[2] * scale
        val nearest = pcz - abs(uz) * halfW - abs(vz) * halfH
        if (nearest < f * MovieNearCut) return -1f
        if (abs(pcx * f / pcz) > cx * 3f || abs(pcy * f / pcz) > cy * 3f) return -1f
        val v = out.values
        v[0] = (f * ux + cx * uz) / pcz
        v[1] = (f * vx + cx * vz) / pcz
        v[2] = (f * pcx + cx * pcz) / pcz
        v[3] = (f * uy + cy * uz) / pcz
        v[4] = (f * vy + cy * vz) / pcz
        v[5] = (f * pcy + cy * pcz) / pcz
        v[6] = uz / pcz
        v[7] = vz / pcz
        v[8] = 1f
        out.matrix.setValues(v)
        return pcz
    }
}

private class MoviePlane {
    val matrix = AndroidMatrix()
    val values = FloatArray(9)
}

/**
 * 每句歌词在世界里的位置和朝向，以及到这一句时镜头的机位。
 * 下一句总摆在当前镜头看出去更深的地方、主句上方或下方，于是它先以小字出现，镜头推过去后长成主字幕。
 */
private class MovieMark(
    val px: Float,
    val py: Float,
    val pz: Float,
    val basis: FloatArray,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val ox: Float,
    val oy: Float,
    val dist: Float,
    val drift: Float,
    val gx: Float,
    val gy: Float,
    val gz: Float,
    val ghostScale: Float,
)

private class MovieWorld(
    private val arts: MovieArtCache,
    private val count: Int,
    private val seed: Long,
    val w: Float,
    val h: Float,
    val f: Float,
) {
    private val marks = ArrayList<MovieMark>()
    private val camBasis = FloatArray(9)
    private val buildPose = MoviePose()
    private val buildBasis = FloatArray(9)

    fun art(i: Int): MovieLineArt? = arts.line(i)

    fun mark(i: Int): MovieMark {
        val k = i.coerceIn(0, (count - 1).coerceAtLeast(0))
        while (marks.size <= k) marks += build(marks.size)
        return marks[k]
    }

    /** 到第 [i] 句、停留 [holdSec] 秒后的机位：慢慢推近，同时绕字幕板轻轻转一点。 */
    fun camera(i: Int, holdSec: Float, out: MoviePose) {
        val m = mark(i)
        val k = 1f - exp(-holdSec.coerceAtLeast(0f) / MovieDriftSec)
        val d = m.dist * (1f - MovieDriftPush * k)
        out.yaw = m.yaw + m.drift * MovieDriftOrbit * k
        out.pitch = m.pitch
        out.roll = m.roll
        movieBasis(out.yaw, out.pitch, out.roll, camBasis)
        val qx = m.ox * d / f
        val qy = m.oy * d / f
        out.x = m.px - (camBasis[0] * qx + camBasis[1] * qy + camBasis[2] * d)
        out.y = m.py - (camBasis[3] * qx + camBasis[4] * qy + camBasis[5] * d)
        out.z = m.pz - (camBasis[6] * qx + camBasis[7] * qy + camBasis[8] * d)
    }

    private fun build(i: Int): MovieMark {
        val rnd = Random(seed * 1_000_003L + i * 7_919L + 17L)
        val art = arts.line(i)
        val halfW = (art?.inkW ?: 0f) / 2f
        val top = art?.top ?: (-h * 0.05f)
        val bottom = art?.bottom ?: (h * 0.05f)

        val planeYaw = rnd.range(-0.12f, 0.12f)
        val planePitch = rnd.range(-0.06f, 0.06f)
        val planeRoll = rnd.range(-0.04f, 0.04f)
        val side = if (rnd.nextBoolean()) 1f else -1f
        val yaw = planeYaw + side * rnd.range(0.12f, 0.24f)
        val pitch = planePitch + rnd.range(-0.11f, 0.11f)
        val roll = rnd.range(-0.045f, 0.045f)
        val dist = f * rnd.range(0.94f, 1.06f)
        val oxRoom = (w * 0.44f - halfW * 1.15f).coerceAtLeast(0f)
        val ox = rnd.range(-1f, 1f) * min(w * 0.08f, oxRoom)
        val oyLo = -h * 0.36f - top
        val oyHi = h * 0.3f - bottom
        val oy = if (oyLo < oyHi) (rnd.range(-0.08f, 0.04f) * h).coerceIn(oyLo, oyHi) else (oyLo + oyHi) / 2f
        val basis = FloatArray(9).also { movieBasis(planeYaw, planePitch, planeRoll, it) }

        var px = 0f
        var py = 0f
        var pz = 0f
        if (i > 0) {
            val prev = mark(i - 1)
            camera(i - 1, 0f, buildPose)
            movieBasis(buildPose.yaw, buildPose.pitch, buildPose.roll, buildBasis)
            val prevArt = arts.line(i - 1)
            val prevTop = prevArt?.top ?: (-h * 0.05f)
            val prevBottom = prevArt?.bottom ?: (h * 0.05f)
            val zNext = prev.dist + f * rnd.range(1.2f, 1.5f)
            val sp = f / zNext
            val gap = h * 0.032f
            var above = if (prevArt?.companion != null) !prevArt.companionBelow else rnd.nextBoolean()
            fun previewY(up: Boolean): Float = if (up) {
                prev.oy + prevTop - gap - bottom * sp
            } else {
                prev.oy + prevBottom + gap - top * sp
            }
            var sy = previewY(above)
            val outOfFrame = if (above) sy + top * sp < -h * 0.45f else sy + bottom * sp > h * 0.45f
            if (outOfFrame) {
                above = !above
                sy = previewY(above)
            }
            val room = (w * 0.45f - halfW * sp).coerceAtLeast(0f)
            val sx = (prev.ox + rnd.range(-0.1f, 0.1f) * w).coerceIn(-room, room)
            val qx = sx * zNext / f
            val qy = sy * zNext / f
            px = buildPose.x + buildBasis[0] * qx + buildBasis[1] * qy + buildBasis[2] * zNext
            py = buildPose.y + buildBasis[3] * qx + buildBasis[4] * qy + buildBasis[5] * zNext
            pz = buildPose.z + buildBasis[6] * qx + buildBasis[7] * qy + buildBasis[8] * zNext
        }

        val gd = f * MovieGhostDepth
        val gk = (dist + gd) / f
        val gxs = -side * rnd.range(0.06f, 0.2f) * w * gk
        val gys = rnd.range(-0.2f, -0.08f) * h * gk
        return MovieMark(
            px = px,
            py = py,
            pz = pz,
            basis = basis,
            yaw = yaw,
            pitch = pitch,
            roll = roll,
            ox = ox,
            oy = oy,
            dist = dist,
            drift = if (rnd.nextBoolean()) 1f else -1f,
            gx = px + basis[0] * gxs + basis[1] * gys + basis[2] * gd,
            gy = py + basis[3] * gxs + basis[4] * gys + basis[5] * gd,
            gz = pz + basis[6] * gxs + basis[7] * gys + basis[8] * gd,
            ghostScale = MovieGhostSize * gk,
        )
    }
}

/**
 * 镜头调度。换句时从当下的机位（含推近的进度）插值到新机位，中途略微升起、侧倾。
 * 跳句或回拖时假装从前一句过来，画面上看就是一次普通的换句。
 */
private class MovieRig(private val world: MovieWorld, start: Int) {
    val move = Animatable(1f)
    var toIndex by mutableIntStateOf(start)
        private set
    private val from = MoviePose()
    private val target = MoviePose()
    private val scratch = MoviePose()
    private val basis = FloatArray(9)
    private var holdStart = 0f
    private var swing = 0f

    val moving: Boolean get() = move.value < 1f

    fun ease(): Float = MovieCameraEase.transform(move.value.coerceIn(0f, 1f))

    fun pose(out: MoviePose, t: Float) {
        world.camera(toIndex, t - holdStart, target)
        val m = move.value
        if (m >= 1f) {
            out.set(target)
            return
        }
        val e = MovieCameraEase.transform(m.coerceIn(0f, 1f))
        out.blend(from, target, e)
        val arc = sin(e * PI.toFloat())
        out.y -= arc * world.h * MovieMoveRise
        out.roll += arc * swing
    }

    suspend fun intro(t: Float) {
        holdStart = t
        pullBack(toIndex)
        swing = MovieMoveSwing
        move.snapTo(0f)
        move.animateTo(1f, tween(MovieIntroMs, easing = LinearEasing))
    }

    suspend fun go(index: Int, t: Float, durMs: Int) {
        if (index == toIndex) return
        when {
            index == toIndex + 1 -> {
                pose(scratch, t)
                from.set(scratch)
            }
            index > 0 -> world.camera(index - 1, MovieDriftSec * 3f, from)
            else -> pullBack(index)
        }
        swing = if (index % 2 == 0) MovieMoveSwing else -MovieMoveSwing
        toIndex = index
        holdStart = t
        world.mark(index + 1)
        move.snapTo(0f)
        move.animateTo(1f, tween(durMs, easing = LinearEasing))
    }

    /** 第一句或开场：从后上方往前推进来。 */
    private fun pullBack(index: Int) {
        world.camera(index, 0f, from)
        movieBasis(from.yaw, from.pitch, from.roll, basis)
        val back = world.f * 0.9f
        from.x -= basis[2] * back
        from.y -= basis[5] * back + world.h * 0.06f
        from.z -= basis[8] * back
        from.yaw -= 0.1f
    }
}

// ---- 歌词 ----

private fun DrawScope.drawMovieLine(
    art: MovieLineArt,
    mark: MovieMark,
    view: MovieView,
    plane: MoviePlane,
    alpha: Float,
    bright: Float,
    positionMs: Long,
    shortenMs: Long,
    accent: Color,
) {
    val z = view.plane(mark.px, mark.py, mark.pz, mark.basis, 1f, art.halfW, art.halfH, plane)
    if (z < 0f) return
    val zf = z / view.f
    val a = alpha * movieStep(0.24f, 0.62f, zf) * (1f - MovieFogFar * movieStep(1.3f, 2.8f, zf))
    if (a <= 0.004f) return
    val native = drawContext.canvas.nativeCanvas
    native.save()
    native.concat(plane.matrix)
    val topLeft = Offset(-art.layout.size.width / 2f, -art.layout.size.height / 2f)
    drawMovieKaraoke(art, topLeft, positionMs, shortenMs, accent, bright, a)
    val comp = art.companion
    if (comp != null) {
        drawText(
            comp,
            color = Color.White.copy(alpha = MovieCompanionAlpha * a),
            topLeft = Offset(-comp.size.width / 2f, art.companionY - comp.size.height / 2f),
        )
    }
    native.restore()
}

/** 背后放大的残影，摆在字幕板后面很远的地方，镜头一动就看得出它在更深处。 */
private fun DrawScope.drawMovieGhost(
    art: MovieLineArt,
    mark: MovieMark,
    view: MovieView,
    plane: MoviePlane,
    alpha: Float,
) {
    if (alpha <= 0.002f) return
    val halfW = art.layout.size.width / 2f
    val halfH = art.layout.size.height / 2f
    val z = view.plane(mark.gx, mark.gy, mark.gz, mark.basis, mark.ghostScale, halfW, halfH, plane)
    if (z < 0f) return
    val native = drawContext.canvas.nativeCanvas
    native.save()
    native.concat(plane.matrix)
    drawText(art.layout, color = Color.White.copy(alpha = alpha), topLeft = Offset(-halfW, -halfH))
    native.restore()
}

/**
 * 逐字：已唱亮白，未唱压暗，唱到的位置跟着一团强调色的光。
 * 没有逐字时间的歌词按 [bright] 整句亮起，不按行时长假扫。
 */
private fun DrawScope.drawMovieKaraoke(
    art: MovieLineArt,
    topLeft: Offset,
    positionMs: Long,
    shortenMs: Long,
    accent: Color,
    bright: Float,
    alpha: Float,
) {
    val layout = art.layout
    val glowPx = art.sizePx * 0.3f
    val unsung = MovieUnsungInk.copy(alpha = MovieUnsungInk.alpha * alpha)
    if (art.words.isEmpty()) {
        val b = bright.coerceIn(0f, 1f)
        if (b < 0.999f) drawText(layout, color = unsung.copy(alpha = unsung.alpha * (1f - b)), topLeft = topLeft)
        if (b > 0.001f) {
            drawText(
                layout,
                color = MovieSungInk.copy(alpha = alpha * b),
                topLeft = topLeft,
                shadow = Shadow(MovieGlowInk.copy(alpha = MovieGlowInk.alpha * alpha * b), blurRadius = glowPx),
            )
        }
        return
    }
    var sung = 0f
    var activeStart = -1
    val last = art.words.lastIndex
    for (i in art.words.indices) {
        val start = art.wordRanges[i * 2]
        val end = art.wordRanges[i * 2 + 1]
        if (start < 0) continue
        val p = lyricSweepProgress(art.words[i], positionMs, if (i == last) shortenMs else 0L)
        if (p <= 0f) break
        if (p >= 1f) {
            sung = end.toFloat()
            continue
        }
        sung = start + (end - start) * p
        activeStart = start
        break
    }
    if (activeStart >= 0) {
        val head = sung.toInt().coerceIn(0, art.rects.lastIndex)
        val box = art.rects[head]
        val frac = (sung - head).coerceIn(0f, 1f)
        val hx = topLeft.x + box.left + box.width * frac
        val hy = topLeft.y + box.center.y
        val radius = box.height.coerceAtLeast(1f) * 0.95f
        drawCircle(
            Brush.radialGradient(
                0f to accent.copy(alpha = 0.42f * alpha),
                1f to Color.Transparent,
                center = Offset(hx, hy),
                radius = radius,
            ),
            radius = radius,
            center = Offset(hx, hy),
        )
    }
    drawText(layout, color = unsung, topLeft = topLeft)
    if (sung <= 0f) return
    clipPath(movieSungPath(art, sung, topLeft, from = 0, bleed = glowPx)) {
        drawText(
            layout,
            color = MovieSungInk.copy(alpha = alpha),
            topLeft = topLeft,
            shadow = Shadow(MovieGlowInk.copy(alpha = MovieGlowInk.alpha * alpha), blurRadius = glowPx),
        )
    }
    if (activeStart >= 0) {
        clipPath(movieSungPath(art, sung, topLeft, from = activeStart, bleed = glowPx)) {
            drawText(
                layout,
                color = Color.White.copy(alpha = alpha),
                topLeft = topLeft,
                shadow = Shadow(accent.copy(alpha = 0.95f * alpha), blurRadius = glowPx * 1.6f),
            )
        }
    }
}

private fun movieSungPath(
    art: MovieLineArt,
    sung: Float,
    topLeft: Offset,
    from: Int,
    bleed: Float,
): Path {
    val path = Path()
    val rects = art.rects
    val full = sung.toInt().coerceIn(0, rects.size)
    for (i in from.coerceAtLeast(0) until full) {
        val r = rects[i]
        path.addRect(
            Rect(r.left + topLeft.x, r.top + topLeft.y - bleed, r.right + topLeft.x, r.bottom + topLeft.y + bleed),
        )
    }
    val frac = sung - full
    if (full < rects.size && full >= from && frac > 0f) {
        val r = rects[full]
        path.addRect(
            Rect(
                r.left + topLeft.x,
                r.top + topLeft.y - bleed,
                r.left + topLeft.x + r.width * frac,
                r.bottom + topLeft.y + bleed,
            ),
        )
    }
    return path
}

/** 一句排好的字。坐标以主句中心为原点；[top]/[bottom] 含翻译行。 */
private class MovieLineArt(
    val layout: TextLayoutResult,
    val rects: Array<Rect>,
    val words: List<LyricWord>,
    val wordRanges: IntArray,
    val inkW: Float,
    val sizePx: Float,
    val companion: TextLayoutResult?,
    val companionBelow: Boolean,
    val companionY: Float,
    val top: Float,
    val bottom: Float,
) {
    val halfH: Float get() = max(-top, bottom)
    val halfW: Float get() = max(layout.size.width, companion?.size?.width ?: 0) / 2f
}

private class MovieArtCache(
    private val lines: List<LrcLine>,
    private val companions: List<LrcLine?>,
    private val originalOnTop: Boolean,
    private val measurer: TextMeasurer,
    private val density: Density,
    private val w: Float,
    private val h: Float,
) {
    private val built = HashMap<Int, MovieLineArt?>()

    fun line(index: Int): MovieLineArt? {
        if (index !in lines.indices) return null
        if (built.containsKey(index)) return built[index]
        val art = buildMovieLineArt(
            line = lines[index],
            companion = companions.getOrNull(index),
            companionBelow = originalOnTop,
            measurer = measurer,
            density = density,
            w = w,
            h = h,
        )
        built[index] = art
        return art
    }
}

private fun buildMovieLineArt(
    line: LrcLine,
    companion: LrcLine?,
    companionBelow: Boolean,
    measurer: TextMeasurer,
    density: Density,
    w: Float,
    h: Float,
): MovieLineArt? {
    val text = line.text
    if (text.isBlank()) return null
    val sizePx = fitMovieLineSize(measurer, text, w, h, density)
    val layout = measureMovieLine(measurer, text, sizePx, w, density)
    val rects = Array(text.length) { i ->
        runCatching { layout.getBoundingBox(i) }.getOrDefault(Rect.Zero)
    }
    var inkW = 0f
    for (l in 0 until layout.lineCount) {
        inkW = max(inkW, layout.getLineRight(l) - layout.getLineLeft(l))
    }

    var words = if (line.words.isNotEmpty()) line.karaokeWords() else emptyList()
    var ranges = IntArray(0)
    if (words.isNotEmpty()) {
        ranges = mapMovieWordRanges(text, words)
        var mapped = 0
        for (i in words.indices) if (ranges[i * 2] >= 0) mapped++
        if (mapped * 2 < words.size) {
            words = emptyList()
            ranges = IntArray(0)
        }
    }
    val companionLayout = companion?.text
        ?.takeIf { it.isNotBlank() && it != text }
        ?.let { measureMovieCompanion(measurer, it, sizePx, w, h, density) }
    val halfMain = layout.size.height / 2f
    var top = -halfMain
    var bottom = halfMain
    var companionY = 0f
    if (companionLayout != null) {
        val reach = halfMain + h * 0.016f + companionLayout.size.height / 2f
        companionY = if (companionBelow) reach else -reach
        if (companionBelow) {
            bottom = reach + companionLayout.size.height / 2f
        } else {
            top = -reach - companionLayout.size.height / 2f
        }
    }
    return MovieLineArt(
        layout = layout,
        rects = rects,
        words = words,
        wordRanges = ranges,
        inkW = inkW,
        sizePx = sizePx,
        companion = companionLayout,
        companionBelow = companionBelow,
        companionY = companionY,
        top = top,
        bottom = bottom,
    )
}

/** 能一行就一行，字大到能当这一镜；太长才折成两行再缩。 */
private fun fitMovieLineSize(
    measurer: TextMeasurer,
    text: String,
    w: Float,
    h: Float,
    density: Density,
): Float {
    var size = h * 0.108f
    val singleFloor = h * 0.066f
    while (size >= singleFloor) {
        val layout = measureMovieLine(measurer, text, size, w, density, maxLines = 1)
        if (!layout.hasVisualOverflow) return size
        size *= 0.92f
    }
    size = h * 0.074f
    val floor = h * 0.05f
    while (size * 0.92f >= floor) {
        val layout = measureMovieLine(measurer, text, size, w, density, maxLines = 2)
        if (!layout.hasVisualOverflow) return size
        size *= 0.92f
    }
    return size
}

private fun measureMovieLine(
    measurer: TextMeasurer,
    text: String,
    sizePx: Float,
    w: Float,
    density: Density,
    maxLines: Int = 2,
): TextLayoutResult = measurer.measure(
    text = text,
    style = TextStyle(
        color = Color.White,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = with(density) { sizePx.toSp() },
        lineHeight = with(density) { (sizePx * 1.2f).toSp() },
        textAlign = TextAlign.Center,
    ),
    overflow = TextOverflow.Ellipsis,
    softWrap = true,
    maxLines = maxLines,
    constraints = Constraints(maxWidth = (w * MovieLineWidthFrac).roundToInt().coerceAtLeast(1)),
    density = density,
)

private fun measureMovieCompanion(
    measurer: TextMeasurer,
    text: String,
    mainSizePx: Float,
    w: Float,
    h: Float,
    density: Density,
): TextLayoutResult {
    val sizePx = minOf(h * 0.042f, mainSizePx * 0.46f)
    return measurer.measure(
        text = text,
        style = TextStyle(
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = with(density) { sizePx.toSp() },
            lineHeight = with(density) { (sizePx * 1.25f).toSp() },
            textAlign = TextAlign.Center,
        ),
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        maxLines = 2,
        constraints = Constraints(maxWidth = (w * 0.66f).roundToInt().coerceAtLeast(1)),
        density = density,
    )
}

private fun mapMovieWordRanges(text: String, words: List<LyricWord>): IntArray {
    val out = IntArray(words.size * 2) { -1 }
    var cursor = 0
    for (i in words.indices) {
        val token = words[i].text
        if (token.isEmpty()) continue
        var at = text.indexOf(token, cursor)
        var len = token.length
        if (at < 0) {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) continue
            at = text.indexOf(trimmed, cursor)
            len = trimmed.length
        }
        if (at < 0) continue
        out[i * 2] = at
        out[i * 2 + 1] = at + len
        cursor = at + len
    }
    return out
}

// ---- 漂浮物 ----

/** 单位尺寸的十字、三角、菱形、方框，中心在原点。 */
private fun movieDecoPaths(): Array<Path> {
    val plus = Path().apply {
        addRect(Rect(-0.5f, -0.14f, 0.5f, 0.14f))
        addRect(Rect(-0.14f, -0.5f, 0.14f, 0.5f))
    }
    val triangle = Path().apply {
        moveTo(0f, -0.5f)
        lineTo(0.5f, 0.38f)
        lineTo(-0.5f, 0.38f)
        close()
    }
    val diamond = Path().apply {
        moveTo(0f, -0.5f)
        lineTo(0.5f, 0f)
        lineTo(0f, 0.5f)
        lineTo(-0.5f, 0f)
        close()
    }
    val frame = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(-0.5f, -0.5f, 0.5f, 0.5f))
        addRect(Rect(-0.36f, -0.36f, 0.36f, 0.36f))
    }
    return arrayOf(plus, triangle, diamond, frame)
}

private class MovieDotModel(val pts: FloatArray) {
    val count: Int get() = pts.size / 3
}

/**
 * 点阵立体和几何碎片，都放在世界坐标里，由同一台摄像机投影，所以远近视差是真的。
 * 每个各自缓慢漂移、变速自转；飘出画面或被镜头穿过就在前方远处悄悄重生。
 * 没有队形，也没有统一的节拍。
 */
private class MovieSwarm(seed: Long, private val models: Array<MovieDotModel>) {
    private val n = MovieDotActors + MovieDecoKinds.size
    private val kind = IntArray(n)
    private val x = FloatArray(n)
    private val y = FloatArray(n)
    private val z = FloatArray(n)
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)
    private val vz = FloatArray(n)
    private val wander = FloatArray(n)
    private val freq = FloatArray(n)
    private val phase = FloatArray(n)
    private val size = FloatArray(n)
    private val base = FloatArray(n)
    private val rotA = FloatArray(n)
    private val rotB = FloatArray(n)
    private val rotC = FloatArray(n)
    private val spinA = FloatArray(n)
    private val spinB = FloatArray(n)
    private val spinC = FloatArray(n)
    private val born = FloatArray(n)
    private val rnd = Random(seed * 131L + 3L)
    private val spot = FloatArray(3)
    private val m = FloatArray(9)
    private val r = FloatArray(9)
    private var lastT = Float.NaN

    init {
        val dots = IntArray(MovieDotActors) { it % models.size }
        dots.shuffle(rnd)
        for (i in 0 until MovieDotActors) kind[i] = dots[i]
        for (j in MovieDecoKinds.indices) kind[MovieDotActors + j] = MovieDecoBase + MovieDecoKinds[j]
    }

    fun step(view: MovieView, t: Float) {
        if (lastT.isNaN()) {
            for (i in 0 until n) spawn(i, view, t, initial = true)
            lastT = t
            return
        }
        val dt = (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        val f = view.f
        for (i in 0 until n) {
            val tf = t * freq[i]
            x[i] += dt * (vx[i] + wander[i] * sin(tf + phase[i]))
            y[i] += dt * (vy[i] + wander[i] * cos(tf * 0.83f + phase[i] * 1.7f))
            z[i] += dt * (vz[i] + wander[i] * 0.6f * sin(tf * 1.27f + phase[i] * 2.3f))
            val wob = 0.55f + 0.45f * sin(tf * 0.61f + phase[i] * 0.7f)
            rotA[i] += dt * spinA[i] * wob
            rotB[i] += dt * spinB[i] * (1.4f - wob)
            rotC[i] += dt * spinC[i]
            view.point(x[i], y[i], z[i])
            val zc = view.q[2]
            val reachX = zc * view.cx / f * 1.6f + size[i]
            val reachY = zc * view.cy / f * 1.6f + size[i]
            if (zc < f * 0.28f || zc > f * 6.6f || abs(view.q[0]) > reachX || abs(view.q[1]) > reachY) {
                spawn(i, view, t, initial = false)
            }
        }
    }

    private fun spawn(i: Int, view: MovieView, t: Float, initial: Boolean) {
        val f = view.f
        val zc = f * if (initial) rnd.range(0.9f, 5.2f) else rnd.range(2.4f, 5.4f)
        var u = 0f
        var v = 0f
        for (attempt in 0 until 8) {
            u = rnd.range(-1.2f, 1.2f)
            v = rnd.range(-1.05f, 1.05f)
            if (abs(u) > 0.42f || abs(v) > 0.5f) break
        }
        view.toWorld(u * zc * view.cx / f, v * zc * view.cy / f, zc, spot)
        x[i] = spot[0]
        y[i] = spot[1]
        z[i] = spot[2]
        val dot = kind[i] < MovieDecoBase
        size[i] = f * if (dot) rnd.range(0.08f, 0.15f) else rnd.range(0.06f, 0.2f)
        base[i] = if (dot) rnd.range(0.6f, 1f) else rnd.range(0.07f, 0.16f)
        val speed = f * rnd.range(0.008f, 0.03f)
        val heading = rnd.nextFloat() * MovieTau
        vx[i] = cos(heading) * speed
        vy[i] = sin(heading) * speed * 0.7f
        vz[i] = f * rnd.range(-0.03f, 0.015f)
        wander[i] = f * rnd.range(0.01f, 0.035f)
        freq[i] = rnd.range(0.12f, 0.42f)
        phase[i] = rnd.nextFloat() * MovieTau
        rotA[i] = rnd.nextFloat() * MovieTau
        rotB[i] = rnd.range(-0.6f, 0.6f)
        rotC[i] = rnd.nextFloat() * MovieTau
        val turn = if (rnd.nextBoolean()) 1f else -1f
        spinA[i] = turn * if (dot) rnd.range(0.15f, 0.55f) else rnd.range(0.05f, 0.22f)
        spinB[i] = turn * if (dot) rnd.range(0.08f, 0.3f) else rnd.range(0.03f, 0.14f)
        spinC[i] = rnd.range(-0.2f, 0.2f)
        born[i] = if (initial) t - 10f else t
    }

    private fun fade(i: Int, zc: Float, f: Float, t: Float): Float =
        movieStep(0f, 1.2f, t - born[i]) *
            movieStep(0.3f, 0.8f, zc / f) *
            (1f - movieStep(3.4f, 6.4f, zc / f))

    fun drawDecos(scope: DrawScope, view: MovieView, paths: Array<Path>, plane: MoviePlane, t: Float) {
        val native = scope.drawContext.canvas.nativeCanvas
        for (i in 0 until n) {
            if (kind[i] < MovieDecoBase) continue
            movieBasis(rotA[i], rotB[i], rotC[i], m)
            val zc = view.plane(x[i], y[i], z[i], m, size[i], 0.5f, 0.5f, plane)
            if (zc < 0f) continue
            val a = base[i] * fade(i, zc, view.f, t)
            if (a <= 0.003f) continue
            native.save()
            native.concat(plane.matrix)
            scope.drawPath(paths[kind[i] - MovieDecoBase], color = MovieDecoInk.copy(alpha = a))
            native.restore()
        }
    }

    fun drawDots(
        scope: DrawScope,
        view: MovieView,
        buffers: MovieDotBuffers,
        paint: AndroidPaint,
        t: Float,
        low: Float,
        high: Float,
    ) {
        buffers.reset()
        val f = view.f
        val b = view.b
        val boost = 1f + 0.3f * high
        for (i in 0 until n) {
            val km = kind[i]
            if (km >= MovieDecoBase) continue
            view.point(x[i], y[i], z[i])
            val qx = view.q[0]
            val qy = view.q[1]
            val qz = view.q[2]
            if (qz < f * 0.25f) continue
            val a = base[i] * fade(i, qz, f, t)
            if (a <= 0.02f) continue
            val s = size[i] * (1f + 0.12f * low)
            val cA = cos(rotA[i])
            val sA = sin(rotA[i])
            val cB = cos(rotB[i])
            val sB = sin(rotB[i])
            r[0] = cA
            r[1] = 0f
            r[2] = sA
            r[3] = sB * sA
            r[4] = cB
            r[5] = -sB * cA
            r[6] = -cB * sA
            r[7] = sB
            r[8] = cB * cA
            for (row in 0..2) {
                for (col in 0..2) {
                    m[row * 3 + col] = (b[row] * r[col] + b[3 + row] * r[3 + col] + b[6 + row] * r[6 + col]) * s
                }
            }
            val scale = f / qz
            val cls = if (scale < 0.5f) 0 else if (scale < 0.95f) 1 else 2
            val pts = models[km].pts
            var p = 0
            while (p < pts.size) {
                val lx = pts[p]
                val ly = pts[p + 1]
                val lz = pts[p + 2]
                p += 3
                val ox = m[0] * lx + m[1] * ly + m[2] * lz
                val oy = m[3] * lx + m[4] * ly + m[5] * lz
                val oz = m[6] * lx + m[7] * ly + m[8] * lz
                val zz = qz + oz
                if (zz < f * 0.2f) continue
                val front = (0.5f - oz / s * 0.6f).coerceIn(0f, 1f)
                val shine = a * (0.35f + 0.65f * front) * boost
                if (shine < 0.05f) continue
                val level = (shine * MovieDotAlpha.size).toInt().coerceIn(0, MovieDotAlpha.lastIndex)
                val inv = f / zz
                buffers.add(cls * MovieDotAlpha.size + level, view.cx + (qx + ox) * inv, view.cy + (qy + oy) * inv)
            }
        }
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (bucket in 0 until buffers.buckets) {
                val used = buffers.used[bucket]
                if (used == 0) continue
                val level = bucket % MovieDotAlpha.size
                val cls = bucket / MovieDotAlpha.size
                paint.strokeWidth = MovieDotWidthDp[level] * MovieDotDepthWidth[cls] * scope.density
                paint.alpha = (MovieDotAlpha[level] * 255f).roundToInt().coerceIn(0, 255)
                native.drawPoints(buffers.xy[bucket], 0, used, paint)
            }
        }
    }
}

/** 按亮度档 × 远近档分桶，一桶一次 drawPoints。 */
private class MovieDotBuffers(capacity: Int) {
    val buckets = MovieDotAlpha.size * MovieDotDepthWidth.size
    val xy = Array(buckets) { FloatArray(capacity * 2) }
    val used = IntArray(buckets)

    fun reset() = used.fill(0)

    fun add(bucket: Int, x: Float, y: Float) {
        val arr = xy[bucket]
        val i = used[bucket]
        if (i + 1 >= arr.size) return
        arr[i] = x
        arr[i + 1] = y
        used[bucket] = i + 2
    }
}

private fun movieDotModels(): Array<MovieDotModel> =
    arrayOf(dotCube(7), dotPrism(5, 13), dotCylinder(9, 18), dotPyramid(8))

private fun dotCube(n: Int): MovieDotModel {
    val pts = ArrayList<Float>(n * n * 18)
    fun put(x: Float, y: Float, z: Float) {
        pts += x
        pts += y
        pts += z
    }
    for (a in 0 until n) {
        for (b in 0 until n) {
            val u = a / (n - 1f) - 0.5f
            val v = b / (n - 1f) - 0.5f
            put(u, v, -0.5f)
            put(u, v, 0.5f)
            put(u, -0.5f, v)
            put(u, 0.5f, v)
            put(-0.5f, u, v)
            put(0.5f, u, v)
        }
    }
    return MovieDotModel(pts.toFloatArray())
}

private fun dotPrism(across: Int, tall: Int): MovieDotModel {
    val half = 0.3f
    val halfY = 0.75f
    val pts = ArrayList<Float>()
    fun put(x: Float, y: Float, z: Float) {
        pts += x
        pts += y
        pts += z
    }
    for (a in 0 until across) {
        val u = -half + 2f * half * a / (across - 1f)
        for (b in 0 until tall) {
            val y = -halfY + 2f * halfY * b / (tall - 1f)
            put(u, y, -half)
            put(u, y, half)
            put(-half, y, u)
            put(half, y, u)
        }
        for (b in 0 until across) {
            val v = -half + 2f * half * b / (across - 1f)
            put(u, -halfY, v)
            put(u, halfY, v)
        }
    }
    return MovieDotModel(pts.toFloatArray())
}

private fun dotCylinder(rings: Int, seg: Int): MovieDotModel {
    val r = 0.42f
    val pts = ArrayList<Float>()
    fun put(x: Float, y: Float, z: Float) {
        pts += x
        pts += y
        pts += z
    }
    for (i in 0 until rings) {
        val y = -0.5f + i / (rings - 1f)
        for (j in 0 until seg) {
            val a = j * MovieTau / seg
            put(r * cos(a), y, r * sin(a))
        }
    }
    for (cap in floatArrayOf(-0.5f, 0.5f)) {
        for (j in 0 until 10) {
            val a = j * MovieTau / 10f
            put(r * 0.5f * cos(a), cap, r * 0.5f * sin(a))
        }
    }
    return MovieDotModel(pts.toFloatArray())
}

private fun dotPyramid(n: Int): MovieDotModel {
    val apexY = -0.55f
    val baseY = 0.45f
    val corners = arrayOf(
        floatArrayOf(-0.5f, -0.5f),
        floatArrayOf(0.5f, -0.5f),
        floatArrayOf(0.5f, 0.5f),
        floatArrayOf(-0.5f, 0.5f),
    )
    val pts = ArrayList<Float>()
    fun put(x: Float, y: Float, z: Float) {
        pts += x
        pts += y
        pts += z
    }
    for (f in 0 until 4) {
        val b0 = corners[f]
        val b1 = corners[(f + 1) % 4]
        for (row in 1..n) {
            val t = row / n.toFloat()
            val y = lerp(apexY, baseY, t)
            for (c in 0..row) {
                val s = c / row.toFloat()
                put(t * lerp(b0[0], b1[0], s), y, t * lerp(b0[1], b1[1], s))
            }
        }
    }
    put(0f, apexY, 0f)
    for (a in 0 until 6) {
        for (b in 0 until 6) {
            put(a / 5f - 0.5f, baseY, b / 5f - 0.5f)
        }
    }
    return MovieDotModel(pts.toFloatArray())
}

private fun movieAccent(image: ImageBitmap): Color {
    val raw = runCatching { image.asAndroidBitmap() }.getOrNull() ?: return MovieAccentFallback
    if (raw.isRecycled || raw.width < 2 || raw.height < 2) return MovieAccentFallback
    val soft = if (raw.config == Bitmap.Config.HARDWARE) {
        runCatching { raw.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return MovieAccentFallback
    } else {
        raw
    }
    val tiny = runCatching { Bitmap.createScaledBitmap(soft, 24, 24, true) }.getOrNull()
    if (tiny == null) {
        if (soft !== raw) soft.recycle()
        return MovieAccentFallback
    }
    val px = IntArray(24 * 24)
    val ok = runCatching { tiny.getPixels(px, 0, 24, 0, 0, 24, 24) }.isSuccess
    if (tiny !== soft && tiny !== raw) tiny.recycle()
    if (soft !== raw) soft.recycle()
    if (!ok) return MovieAccentFallback
    val hsv = FloatArray(3)
    var sx = 0f
    var sy = 0f
    var weight = 0f
    for (c in px) {
        AndroidColor.colorToHSV(c, hsv)
        val wgt = hsv[1] * hsv[1] * hsv[2]
        if (wgt < 0.04f) continue
        val a = (hsv[0] * PI / 180.0).toFloat()
        sx += cos(a) * wgt
        sy += sin(a) * wgt
        weight += wgt
    }
    if (weight < 6f) return MovieAccentFallback
    var hue = (atan2(sy, sx) * 180f / PI.toFloat())
    if (hue < 0f) hue += 360f
    return Color.hsv(hue, 0.5f, 1f)
}

private fun Random.range(lo: Float, hi: Float): Float = lo + nextFloat() * (hi - lo)

private fun movieStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private const val MovieTau = 6.2831855f
private const val MovieDeg = 57.29578f
private const val MovieIdleSpeed = 0.35f

/** 焦距（相对画面高）。越大越像长焦，透视越收敛。 */
private const val MovieFocalK = 1.35f
private const val MovieNearCut = 0.12f
private const val MovieFogFar = 0.3f
private const val MovieLineWidthFrac = 0.72f
private const val MovieCompanionAlpha = 0.74f

private const val MovieMoveMinMs = 560
private const val MovieMoveMaxMs = 1150
private const val MovieIntroMs = 1700
private const val MovieMoveRise = 0.035f
private const val MovieMoveSwing = 0.025f
private const val MovieDriftSec = 9f
private const val MovieDriftPush = 0.06f
private const val MovieDriftOrbit = 0.04f

private const val MovieGhostDepth = 2.4f
private const val MovieGhostSize = 2.1f
private const val MovieGhostAlpha = 0.07f

private val MovieBackBlur = 5.dp
private const val MovieBackZoom = 1.42f
private const val MovieBackPan = 0.4f
private const val MovieBackDim = 0.5f
private const val MovieBackSaturation = 0.55f

private const val MovieDotActors = 9
private const val MovieDecoBase = 4
private val MovieDecoKinds = intArrayOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 3)
private val MovieDotAlpha = floatArrayOf(0.14f, 0.30f, 0.55f, 0.88f)
private val MovieDotWidthDp = floatArrayOf(1.1f, 1.4f, 1.8f, 2.2f)
private val MovieDotDepthWidth = floatArrayOf(0.7f, 1f, 1.4f)

private val MovieNight = Color(0xFF07080C)
private val MovieHazeA = Color(0xFF1B1E28)
private val MovieHazeB = Color(0xFF14171F)
private val MovieDecoInk = Color(0xFFC9CBD3)
private val MovieSungInk = Color(0xFFF7F5F2)
private val MovieUnsungInk = Color.White.copy(alpha = 0.42f)
private val MovieGlowInk = Color.White.copy(alpha = 0.5f)
private val MovieAccentFallback = Color(0xFFF2A7C3)

private val MovieCameraEase = CubicBezierEasing(0.5f, 0f, 0.15f, 1f)
