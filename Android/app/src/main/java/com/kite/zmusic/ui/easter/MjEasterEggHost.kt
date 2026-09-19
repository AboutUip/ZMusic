@file:Suppress("DEPRECATION")

package com.kite.zmusic.ui.easter

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Choreographer
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import kotlin.coroutines.resume
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val GifAsset = "easter/mj.gif"
private const val AudioAsset = "easter/mj.mp3"
private val DropEasing = CubicBezierEasing(0.18f, 1.22f, 0.28f, 1f)
private val DismissEasing = CubicBezierEasing(0.4f, 0.02f, 0.16f, 1f)

private data class MjGifSpec(
    val movie: Movie,
    val width: Int,
    val height: Int,
    val durationMs: Int,
)

@Composable
fun MjEasterEggHost(modifier: Modifier = Modifier) {
    val generation by MjEasterEgg.generation.collectAsStateWithLifecycle()
    val reveal = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    var clockMs by remember { mutableIntStateOf(0) }
    var spec by remember { mutableStateOf<MjGifSpec?>(null) }
    var panelH by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(generation) {
        if (generation <= 0) return@LaunchedEffect
        mounted = true
        clockMs = 0
        val loaded = runCatching { loadGif(context.applicationContext) }.getOrNull()
        spec = loaded
        coroutineScope {
            launch {
                if (reveal.value < 0.99f) {
                    reveal.snapTo(0f)
                    reveal.animateTo(1f, tween(420, easing = DropEasing))
                } else {
                    reveal.snapTo(1f)
                }
            }
            if (loaded != null) {
                playLinked(context.applicationContext, onClock = { clockMs = it })
            }
        }
        if (!isActive) return@LaunchedEffect
        reveal.animateTo(0f, tween(240, easing = DismissEasing))
        if (isActive && generation == MjEasterEgg.generation.value) {
            mounted = false
            spec = null
            clockMs = 0
        }
    }

    if (!mounted && reveal.value <= 0.01f) return

    val ratio = spec?.let { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) } ?: 1f
    val movie = spec?.movie
    val gifDur = spec?.durationMs?.coerceAtLeast(1) ?: 1
    Box(
        modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .zIndex(550f)
            .onSizeChanged { panelH = it.height }
            .graphicsLayer {
                val h = if (panelH > 0) panelH.toFloat() else size.height
                translationY = -(1f - reveal.value) * h
            },
    ) {
        AndroidView(
            factory = { ctx -> LinkedMjView(ctx) },
            update = { view ->
                view.bind(movie, gifDur, clockMs)
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio),
        )
    }
}

private class LinkedMjView(context: Context) : View(context) {
    private var movie: Movie? = null
    private var durationMs: Int = 1
    private var clockMs: Int = 0

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun bind(movie: Movie?, durationMs: Int, clockMs: Int) {
        this.movie = movie
        this.durationMs = durationMs.coerceAtLeast(1)
        this.clockMs = clockMs.coerceAtLeast(0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val m = movie ?: return
        if (width <= 0 || height <= 0 || m.width() <= 0 || m.height() <= 0) return
        m.setTime(clockMs % durationMs)
        val sx = width / m.width().toFloat()
        val sy = height / m.height().toFloat()
        canvas.save()
        canvas.scale(sx, sy)
        m.draw(canvas, 0f, 0f)
        canvas.restore()
    }
}

private fun loadGif(context: Context): MjGifSpec {
    val bytes = context.assets.open(GifAsset).use { it.readBytes() }
    val movie = Movie.decodeByteArray(bytes, 0, bytes.size)
        ?: error("mj gif")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val w = movie.width().takeIf { it > 0 } ?: bounds.outWidth.coerceAtLeast(1)
    val h = movie.height().takeIf { it > 0 } ?: bounds.outHeight.coerceAtLeast(1)
    val dur = movie.duration().takeIf { it > 0 } ?: 200
    return MjGifSpec(movie, w, h, dur)
}

private suspend fun playLinked(
    context: Context,
    onClock: (Int) -> Unit,
) = coroutineScope {
    val bridge = (context.applicationContext as? ZMusicApplication)?.playbackBridge
    val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    val mp = MediaPlayer()
    try {
        bridge?.duckMusicVolume(0.1f)
        mp.setAudioAttributes(attrs)
        context.assets.openFd(AudioAsset).use { fd ->
            mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        }
        mp.prepare()
        mp.seekTo(0)
        onClock(0)
        val finished = async {
            suspendCancellableCoroutine { cont ->
                mp.setOnCompletionListener {
                    if (cont.isActive) cont.resume(Unit)
                }
                mp.setOnErrorListener { _, _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                Choreographer.getInstance().postFrameCallback {
                    if (!cont.isActive) return@postFrameCallback
                    onClock(0)
                    mp.start()
                }
                cont.invokeOnCancellation {
                    runCatching { if (mp.isPlaying) mp.stop() }
                }
            }
        }
        val ticker = launch {
            while (isActive) {
                withFrameNanos {
                    if (mp.isPlaying) onClock(mp.currentPosition)
                }
            }
        }
        finished.await()
        ticker.cancel()
    } finally {
        onClock(0)
        runCatching { mp.release() }
        bridge?.duckMusicVolume(null)
    }
}
