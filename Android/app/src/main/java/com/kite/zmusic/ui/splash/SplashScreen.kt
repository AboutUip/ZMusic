package com.kite.zmusic.ui.splash

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import com.kite.zmusic.plugin.PluginSplashCopy
import com.kite.zmusic.ui.theme.MainPalette

private val Page get() = MainPalette.Page
private val Ink get() = MainPalette.Ink
private val InkSecondary = Color(0xFF6B7A82)
private val Bloom get() = MainPalette.Accent
private val MoteA = Color(0xFFC45B5B)
private val MoteB = Color(0xFF8A9AA3)

private const val TaglineAt = 2870f
private const val TaglineDur = 720f
private const val HoldMs = 780f
private const val FadeMs = 360f
private const val FadeAt = TaglineAt + TaglineDur + HoldMs

private val taglineStyle = TextStyle(
    color = InkSecondary,
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.2.sp,
    textAlign = TextAlign.Center,
)

private data class Pose(
    val x: Float = 0f,
    val y: Float = 0f,
    val rot: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    val alpha: Float = 0f,
)

/**
 * 启动页：字母生命感动画，并在同一画面里完成 API 连通性探测，
 * 避免再切一层「正在检测」loading。
 *
 * [checkReady] 与动画并行；动画播到定格后若探测未完，停在定格等待，再淡出。
 * [onFinished] 的参数为探测是否成功。
 * 定格后若仍有插件未就绪，在画面下方转圈提示，不加遮罩。
 */
@Composable
fun SplashScreen(
    checkReady: suspend () -> Boolean,
    onFinished: (connected: Boolean) -> Unit,
    pluginWaitNames: StateFlow<List<String>>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    var tMs by remember { mutableFloatStateOf(if (reduceMotion) FadeAt else 0f) }
    val density = LocalDensity.current
    val checkReadyState = rememberUpdatedState(checkReady)
    val onFinishedState = rememberUpdatedState(onFinished)

    val emptyPluginWait = remember { MutableStateFlow(emptyList<String>()) }
    val pendingPlugins by (pluginWaitNames ?: emptyPluginWait).collectAsStateWithLifecycle()

    SplashLightSystemBars()

    LaunchedEffect(reduceMotion) {
        val probe = async {
            runCatching { checkReadyState.value() }.getOrDefault(false)
        }
        if (reduceMotion) {
            delay(420)
        } else {
            val start = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val elapsed = (now - start) / 1_000_000f
                tMs = elapsed.coerceAtMost(FadeAt)
                if (elapsed >= FadeAt) break
            }
        }
        val connected = probe.await()
        if (!reduceMotion) {
            val fadeStart = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val elapsed = (now - fadeStart) / 1_000_000f
                tMs = FadeAt + elapsed
                if (elapsed >= FadeMs) break
            }
        }
        onFinishedState.value(connected)
    }

    val fade = if (tMs < FadeAt) 1f else (1f - (tMs - FadeAt) / FadeMs).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxSize()
            .background(Page)
            .systemBarsPadding()
            .graphicsLayer { alpha = fade },
        contentAlignment = Alignment.Center,
    ) {
        SplashMotes(tMs)
        Box(contentAlignment = Alignment.Center) {
            SplashBloom(tMs)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SplashWordmark(tMs = tMs, density = density)
                Spacer(Modifier.height(6.dp))
                val tag = taglinePose(tMs)
                Row(
                    modifier = Modifier.graphicsLayer {
                        alpha = tag.alpha
                        translationY = tag.y
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "把世界调小一点",
                        style = taglineStyle,
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .width(18.dp)
                            .height(1.dp)
                            .background(InkSecondary.copy(alpha = 0.55f)),
                    )
                    Text(
                        text = "把歌开大一点",
                        style = taglineStyle,
                    )
                }
            }
        }
        if (tMs >= FadeAt && pendingPlugins.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp, start = 32.dp, end = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    color = Bloom,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = PluginSplashCopy.LOADING,
                    style = TextStyle(
                        color = InkSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                val names = PluginSplashCopy.namesLine(pendingPlugins)
                if (names.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = names,
                        style = TextStyle(
                            color = InkSecondary.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashBloom(tMs: Float) {
    val op = kf(
        tMs,
        K(40f, 0f),
        K(740f, 0.55f, ::easeOutQuad),
        K(2000f, 0.28f, ::easeInOutSine),
        K(3200f, 0.42f, ::easeInOutSine),
    )
    val sc = kf(tMs, K(40f, 0.38f), K(1440f, 1.12f, ::easeOutCubic), K(3200f, 1.05f, ::easeInOutSine))
    Canvas(
        Modifier
            .size(300.dp, 156.dp)
            .graphicsLayer {
                alpha = op
                scaleX = sc
                scaleY = sc
            },
    ) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Bloom.copy(alpha = 0.34f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.48f),
                radius = size.maxDimension * 0.58f,
            ),
        )
    }
}

@Composable
private fun SplashMotes(tMs: Float) {
    data class Spec(val x: Float, val y: Float, val r: Float, val dx: Float, val dy: Float, val start: Float, val life: Float, val color: Color)
    val density = LocalDensity.current
    val motes = remember(density) {
        with(density) {
            listOf(
                Spec(90.dp.toPx(), 210.dp.toPx(), 5.dp.toPx(), 18.dp.toPx(), (-48).dp.toPx(), 200f, 2400f, MoteA),
                Spec(320.dp.toPx(), 86.dp.toPx(), 3.5.dp.toPx(), (-22).dp.toPx(), 36.dp.toPx(), 480f, 2800f, MoteB),
                Spec(48.dp.toPx(), 64.dp.toPx(), 4.dp.toPx(), 28.dp.toPx(), (-30).dp.toPx(), 900f, 2600f, MoteA.copy(alpha = 0.85f)),
                Spec(340.dp.toPx(), 200.dp.toPx(), 3.dp.toPx(), (-16).dp.toPx(), 40.dp.toPx(), 1600f, 3000f, MoteB),
                Spec(200.dp.toPx(), 240.dp.toPx(), 4.5.dp.toPx(), 8.dp.toPx(), (-55).dp.toPx(), 2400f, 3200f, MoteA),
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f - 200.dp.toPx()
        val cy = size.height / 2f - 160.dp.toPx()
        motes.forEach { m ->
            val local = tMs - m.start
            if (local !in 0f..m.life) return@forEach
            val u = local / m.life
            val fade = when {
                u < 0.12f -> u / 0.12f
                u > 0.78f -> (1f - u) / 0.22f
                else -> 1f
            }.coerceIn(0f, 1f) * 0.7f
            val drift = easeInOutSine(u)
            drawCircle(
                color = m.color.copy(alpha = fade),
                radius = m.r,
                center = Offset(cx + m.x + m.dx * drift, cy + m.y + m.dy * drift),
            )
        }
    }
}

@Composable
private fun SplashWordmark(tMs: Float, density: Density) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(
        color = Ink,
        fontSize = 48.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val stageW = with(density) { 360.dp.toPx() }
    val stageH = with(density) { 88.dp.toPx() }
    val track = with(density) { 1.dp.toPx() }
    fun gw(ch: String) = measurer.measure(ch, style).size.width.toFloat()

    val zW = gw("Z")
    val mW = gw("M")
    val uW = gw("u")
    val sW = gw("s")
    val iW = gw("i")
    val cW = gw("c")
    val xW = gw("X")
    val aW = gw("a")
    val nW = gw("n")

    val music = floatArrayOf(mW, uW, sW, iW, cW)
    val zMusicTotal = zW + music.sum() + track * 5
    val zStart = (stageW - zMusicTotal) / 2f
    val zHome = zStart
    var cursor = zStart + zW + track
    val mHome = cursor
    cursor += mW + track
    val uMHome = cursor
    cursor += uW + track
    val sHome = cursor
    cursor += sW + track
    val iHome = cursor
    cursor += iW + track
    val cHome = cursor

    val xuanTotal = xW + uW + aW + nW
    val xuanX = (stageW - xuanTotal) / 2f
    val xuanU = xuanX + xW
    val xuanA = xuanU + uW
    val xuanN = xuanA + aW

    val off = with(density) { 76.dp.toPx() }
    val stem = stemPose(tMs, xuanX, zHome, off)
    val uanU = xuanJoinThenAbsorb(tMs, 790f, 1790f, xuanU, xuanX, off)
    val uanA = xuanJoinThenAbsorb(tMs, 930f, 1720f, xuanA, xuanX, off)
    val uanN = xuanJoinThenAbsorb(tMs, 1070f, 1650f, xuanN, xuanX, off)
    val gM = musicEnter(tMs, 2150f, fromLeft = true, roll = true, home = mHome, off = off)
    val gU = musicEnter(tMs, 2235f, fromLeft = false, roll = false, home = uMHome, off = off)
    val gS = musicEnter(tMs, 2305f, fromLeft = true, roll = false, home = sHome, off = off)
    val gI = musicEnter(tMs, 2365f, fromLeft = false, roll = true, home = iHome, off = off)
    val gC = musicEnter(tMs, 2415f, fromLeft = true, roll = false, home = cHome, off = off)

    val stemChar = if (tMs < 1750f) "X" else "Z"
    val baseline = stageH * 0.18f

    Box(Modifier.size(360.dp, 88.dp)) {
        SplashGlyph(stemChar, stem.copy(x = stem.x, y = stem.y + baseline), style)
        SplashGlyph("u", uanU.copy(y = uanU.y + baseline), style)
        SplashGlyph("a", uanA.copy(y = uanA.y + baseline), style)
        SplashGlyph("n", uanN.copy(y = uanN.y + baseline), style)
        SplashGlyph("M", gM.copy(y = gM.y + baseline), style)
        SplashGlyph("u", gU.copy(y = gU.y + baseline), style)
        SplashGlyph("s", gS.copy(y = gS.y + baseline), style)
        SplashGlyph("i", gI.copy(y = gI.y + baseline), style)
        SplashGlyph("c", gC.copy(y = gC.y + baseline), style)
    }
}

@Composable
private fun SplashGlyph(ch: String, pose: Pose, style: TextStyle) {
    if (pose.alpha <= 0.01f) return
    Text(
        text = ch,
        modifier = Modifier.graphicsLayer {
            alpha = pose.alpha.coerceIn(0f, 1f)
            translationX = pose.x
            translationY = pose.y
            rotationZ = pose.rot
            scaleX = pose.sx
            scaleY = pose.sy
            transformOrigin = TransformOrigin(0.5f, 0.85f)
        },
        style = style,
    )
}

private fun stemPose(t: Float, xuanX: Float, zHome: Float, off: Float): Pose {
    if (t < 120f) return Pose(x = xuanX - off, rot = -220f, sx = 0.86f, sy = 0.82f, alpha = 0f)
    val enter = enterFirst(t - 120f, off)
    val restX = xuanX
    val invite = if (t in 720f..960f) inviteLean(t - 720f) else 0f
    val breath = breathY(t, 1380f, 2.4f)
    val impactT = t - 1750f
    if (impactT < 0f) {
        return enter.copy(x = restX + enter.x + invite, y = enter.y + breath, alpha = 1f)
    }
    val squashX = kf(impactT, K(0f, 1f), K(70f, 1.14f, ::easeOutQuad), K(150f, 0.94f, ::easeInQuad), K(280f, 1f, ::easeOutBack))
    val squashY = kf(impactT, K(0f, 1f), K(70f, 0.86f, ::easeOutQuad), K(150f, 1.08f, ::easeInQuad), K(280f, 1f, ::easeOutBack))
    val homeT = impactT - 90f
    val home = if (homeT <= 0f) {
        restX
    } else {
        kf(homeT, K(0f, restX), K(160f, (restX + zHome) * 0.5f, ::easeInOutSine), K(280f, zHome, ::easeOutCubic))
    }
    val hop = if (homeT <= 0f) 0f else kf(homeT, K(0f, 0f), K(110f, -9f, ::easeOutQuad), K(280f, 0f, ::easeInOutSine))
    return Pose(x = home, y = hop, rot = 0f, sx = squashX, sy = squashY, alpha = 1f)
}

private fun enterFirst(local: Float, off: Float): Pose {
    val dur = 620f
    val fromX = -off
    val x = kf(
        local,
        K(0f, fromX),
        K(dur * 0.42f, fromX * 0.58f, ::easeInCubic),
        K(dur * 0.8f, 8f, ::easeOutCubic),
        K(dur, 0f, ::easeOutBack),
    )
    val y = kf(local, K(0f, 8f), K(dur * 0.5f, -5f, ::easeOutQuad), K(dur, 0f, ::easeOutCubic))
    val rot = kf(local, K(0f, -200f), K(dur * 0.62f, -28f, ::easeInOutSine), K(dur, 0f, ::easeOutBack))
    val sx = kf(local, K(0f, 0.88f), K(dur * 0.55f, 1.06f, ::easeOutQuad), K(dur, 1f, ::easeOutBack))
    val sy = kf(local, K(0f, 0.82f), K(dur * 0.55f, 1.04f, ::easeOutQuad), K(dur, 1f, ::easeOutBack))
    val alpha = if (local <= 0f) 0f else 1f
    return Pose(x = x, y = y, rot = rot, sx = sx, sy = sy, alpha = alpha)
}

private fun inviteLean(local: Float): Float =
    kf(local, K(0f, 0f), K(90f, 4f, ::easeOutQuad), K(220f, 0f, ::easeOutBack))

private fun xuanJoinThenAbsorb(
    t: Float,
    joinAt: Float,
    absorbAt: Float,
    home: Float,
    stemX: Float,
    off: Float,
): Pose {
    if (t < joinAt) return Pose(x = home + off, y = 8f, rot = 120f, sx = 0.88f, sy = 0.86f, alpha = 0f)
    if (t < absorbAt) {
        val joined = enterJoin(t - joinAt, off)
        val breath = breathY(t, joinAt + 590f, 3f)
        return joined.copy(x = home + joined.x, y = joined.y + breath, alpha = 1f)
    }
    return absorb(t - absorbAt, home, stemX - home + 2f)
}

private fun enterJoin(local: Float, off: Float): Pose {
    val dur = 520f
    val fromX = off
    val x = kf(
        local,
        K(0f, fromX),
        K(dur * 0.38f, fromX * 0.48f, ::easeInCubic),
        K(dur * 0.78f, -4f, ::easeOutCubic),
        K(dur, 0f, ::easeOutBack),
    )
    val y = kf(local, K(0f, 10f), K(dur * 0.4f, -12f, ::easeOutQuad), K(dur * 0.74f, 2f, ::easeInQuad), K(dur, 0f, ::easeOutCubic))
    val rot = kf(local, K(0f, 140f), K(dur * 0.55f, 28f, ::easeInCubic), K(dur, 0f, ::easeOutBack))
    val sx = kf(local, K(0f, 0.88f), K(dur * 0.5f, 1.08f, ::easeOutQuad), K(dur, 1f, ::easeOutCubic))
    val sy = kf(local, K(0f, 0.84f), K(dur * 0.5f, 1.1f, ::easeOutQuad), K(dur, 1f, ::easeOutCubic))
    return Pose(x = x, y = y, rot = rot, sx = sx, sy = sy, alpha = 1f)
}

private fun absorb(local: Float, home: Float, diveX: Float): Pose {
    val travel = 280f
    val alpha = kf(local, K(0f, 1f), K(40f, 1f), K(260f, 0f, ::easeInQuad))
    val x = home + kf(local, K(0f, 0f), K(80f, diveX * 0.08f, ::easeOutQuad), K(travel, diveX, ::easeInCubic))
    val y = kf(local, K(0f, 0f), K(90f, -10f, ::easeOutQuad), K(travel, 4f, ::easeInCubic))
    val rot = kf(local, K(0f, 0f), K(travel, if (diveX < 0f) -28f else 28f, ::easeInQuad))
    val sc = kf(local, K(0f, 1f), K(travel, 0.2f, ::easeInCubic))
    return Pose(x = x, y = y, rot = rot, sx = sc, sy = sc, alpha = alpha)
}

private fun musicEnter(
    t: Float,
    begin: Float,
    fromLeft: Boolean,
    roll: Boolean,
    home: Float,
    off: Float,
): Pose {
    if (t < begin) {
        val fromX = if (fromLeft) -off else off
        return Pose(
            x = home + fromX,
            alpha = 0f,
            rot = if (roll) if (fromLeft) -220f else 220f else if (fromLeft) -18f else 18f,
        )
    }
    val local = t - begin
    val entered = if (roll) enterRoll(local, fromLeft, off) else enterJump(local, fromLeft, off)
    val settle = settleY(t, begin + 620f)
    return entered.copy(x = home + entered.x, y = entered.y + settle, alpha = 1f)
}

private fun enterRoll(local: Float, fromLeft: Boolean, off: Float): Pose {
    val dur = 560f
    val fromX = if (fromLeft) -off else off
    val spin = if (fromLeft) -220f else 220f
    val x = kf(
        local,
        K(0f, fromX),
        K(dur * 0.36f, fromX * 0.46f, ::easeInCubic),
        K(dur * 0.78f, if (fromLeft) 6f else -6f, ::easeOutCubic),
        K(dur, 0f, ::easeOutBack),
    )
    val y = kf(local, K(0f, 10f), K(dur * 0.42f, -8f, ::easeOutQuad), K(dur, 0f, ::easeOutCubic))
    val rot = kf(local, K(0f, spin), K(dur * 0.55f, spin * 0.18f, ::easeInOutSine), K(dur, 0f, ::easeOutBack))
    val sx = kf(local, K(0f, 0.9f), K(dur * 0.5f, 1.05f, ::easeOutQuad), K(dur, 1f, ::easeOutCubic))
    val sy = kf(local, K(0f, 0.84f), K(dur * 0.5f, 1.08f, ::easeOutQuad), K(dur, 1f, ::easeOutCubic))
    return Pose(x = x, y = y, rot = rot, sx = sx, sy = sy, alpha = 1f)
}

private fun enterJump(local: Float, fromLeft: Boolean, off: Float): Pose {
    val dur = 600f
    val fromX = if (fromLeft) -off else off
    val tilt = if (fromLeft) -18f else 18f
    val x = kf(
        local,
        K(0f, fromX),
        K(dur * 0.34f, fromX * 0.38f, ::easeOutQuad),
        K(dur * 0.76f, if (fromLeft) 6f else -6f, ::easeInOutSine),
        K(dur, 0f, ::easeOutBack),
    )
    val y = kf(
        local,
        K(0f, 14f),
        K(dur * 0.28f, -22f, ::easeOutQuad),
        K(dur * 0.5f, 4f, ::easeInQuad),
        K(dur * 0.7f, -12f, ::easeOutQuad),
        K(dur * 0.86f, 2f, ::easeInQuad),
        K(dur, 0f, ::easeOutCubic),
    )
    val rot = kf(
        local,
        K(0f, tilt),
        K(dur * 0.4f, tilt * -0.45f, ::easeInOutSine),
        K(dur * 0.72f, tilt * 0.16f, ::easeInOutSine),
        K(dur, 0f, ::easeOutBack),
    )
    val sx = kf(local, K(0f, 0.9f), K(dur * 0.3f, 1.04f, ::easeOutQuad), K(dur * 0.52f, 0.94f, ::easeInQuad), K(dur, 1f, ::easeOutCubic))
    val sy = kf(local, K(0f, 1.08f), K(dur * 0.3f, 0.92f, ::easeOutQuad), K(dur * 0.52f, 1.06f, ::easeInQuad), K(dur, 1f, ::easeOutCubic))
    return Pose(x = x, y = y, rot = rot, sx = sx, sy = sy, alpha = 1f)
}

private fun settleY(t: Float, begin: Float): Float {
    if (t < begin) return 0f
    return kf(t - begin, K(0f, 0f), K(110f, 2.4f, ::easeInQuad), K(300f, 0f, ::easeOutCubic))
}

private fun breathY(t: Float, begin: Float, amp: Float): Float {
    if (t < begin) return 0f
    val local = t - begin
    if (local > 220f) return 0f
    return kf(local, K(0f, 0f), K(110f, -amp, ::easeInOutSine), K(220f, 0f, ::easeInOutSine))
}

private fun taglinePose(t: Float): Pose {
    if (t < TaglineAt) return Pose(y = 8f, alpha = 0f)
    val local = t - TaglineAt
    val alpha = kf(local, K(0f, 0f), K(TaglineDur, 1f, ::easeOutQuad))
    val y = kf(local, K(0f, 8f), K(TaglineDur, 0f, ::easeOutCubic))
    return Pose(y = y, alpha = alpha)
}

private data class K(
    val t: Float,
    val v: Float,
    val ease: (Float) -> Float = ::easeOutCubic,
)

private fun kf(t: Float, vararg keys: K): Float {
    if (keys.isEmpty()) return 0f
    if (t <= keys.first().t) return keys.first().v
    if (t >= keys.last().t) return keys.last().v
    for (i in 1 until keys.size) {
        val a = keys[i - 1]
        val b = keys[i]
        if (t <= b.t) {
            val u = ((t - a.t) / (b.t - a.t).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
            return a.v + (b.v - a.v) * b.ease(u)
        }
    }
    return keys.last().v
}

private fun easeOutQuad(t: Float) = 1f - (1f - t) * (1f - t)
private fun easeInQuad(t: Float) = t * t
private fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3)
private fun easeInCubic(t: Float) = t * t * t
private fun easeInOutSine(t: Float) = (-(cos(PI * t).toFloat() - 1f) / 2f)
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val p = t - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}

@Composable
private fun SplashLightSystemBars() {
    val view = LocalView.current
    val light = !MainPalette.isDark
    DisposableEffect(view, light) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = controller?.isAppearanceLightStatusBars
        val prevNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = light
        controller?.isAppearanceLightNavigationBars = light
        onDispose {
            if (prevStatus != null) controller.isAppearanceLightStatusBars = prevStatus
            if (prevNav != null) controller.isAppearanceLightNavigationBars = prevNav
        }
    }
}
