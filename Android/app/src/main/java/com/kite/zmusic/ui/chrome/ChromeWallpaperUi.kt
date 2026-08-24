package com.kite.zmusic.ui.chrome

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kite.zmusic.data.ChromeWallpaperState
import com.kite.zmusic.data.ChromeWallpaperStore
import com.kite.zmusic.data.ChromeWallpaperSurface
import com.kite.zmusic.data.WallpaperFrame
import com.kite.zmusic.ui.main.MainDestination
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.main.MainPalette
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalChromeWallpaperPainted = compositionLocalOf { false }
val LocalChromeWallpaperFrame = compositionLocalOf<WallpaperFrame?> { null }

/** 主壳整屏视口。叠层按窗口对齐画同一张图，横屏才能铺进左侧栏。 */
data class WallpaperViewport(
    val width: Float,
    val height: Float,
    val originInWindow: Offset,
)

val LocalWallpaperViewport = compositionLocalOf<WallpaperViewport?> { null }

fun chromeWallpaperSurface(
    overlay: MainOverlay?,
    destination: MainDestination,
): ChromeWallpaperSurface? {
    return when (overlay) {
        null,
        is MainOverlay.Mv,
        -> destination.wallpaperSurface()
        MainOverlay.Settings -> ChromeWallpaperSurface.Settings
        MainOverlay.Search, MainOverlay.CreativeWorkshop -> ChromeWallpaperSurface.Search
        MainOverlay.Daily, MainOverlay.Fm, MainOverlay.Charts, MainOverlay.CachedSongs ->
            ChromeWallpaperSurface.Playlist
        is MainOverlay.Playlist, is MainOverlay.PlaylistSearch -> ChromeWallpaperSurface.Playlist
        is MainOverlay.Album -> ChromeWallpaperSurface.Album
        is MainOverlay.Artist,
        is MainOverlay.ArtistSongs,
        is MainOverlay.ArtistAlbums,
        is MainOverlay.ArtistMvs,
        MainOverlay.LikedArtists,
        is MainOverlay.LikedArtistsSearch,
        is MainOverlay.User,
        is MainOverlay.UserRelations,
        -> ChromeWallpaperSurface.Artist
    }
}

fun MainDestination.wallpaperSurface(): ChromeWallpaperSurface = when (this) {
    MainDestination.Home -> ChromeWallpaperSurface.Home
    MainDestination.Features -> ChromeWallpaperSurface.Features
    MainDestination.Profile -> ChromeWallpaperSurface.Profile
}

fun ChromeWallpaperState.paints(surface: ChromeWallpaperSurface?, landscape: Boolean): Boolean =
    frame(surface, landscape) != null

fun Modifier.chromePage(): Modifier = composed {
    val painted = LocalChromeWallpaperPainted.current
    background(if (painted) Color.Transparent else MainPalette.Page)
}

@Composable
fun ChromeWallpaperBackdrop(modifier: Modifier = Modifier) {
    val frame = LocalChromeWallpaperFrame.current
    if (frame != null) {
        ChromeWallpaperLayer(frame = frame, modifier = modifier)
    } else {
        Box(modifier.fillMaxSize().background(MainPalette.Page))
    }
}

internal object WallpaperBitmapCache {
    private val lock = Any()
    private val map = mutableMapOf<String, ImageBitmap>()

    fun get(path: String): ImageBitmap? = synchronized(lock) { map[path] }

    fun put(path: String, bitmap: ImageBitmap) {
        if (path.isBlank()) return
        synchronized(lock) { map[path] = bitmap }
    }
}

internal suspend fun preloadWallpaperBitmap(path: String) {
    if (path.isBlank() || WallpaperBitmapCache.get(path) != null) return
    val decoded = withContext(Dispatchers.IO) {
        runCatching {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }.getOrNull()
    }
    if (decoded != null) WallpaperBitmapCache.put(path, decoded)
}

/**
 * 只画用户的图，不跟主题盖遮罩。见 [wallpaperThemeScrimColor]。
 */
@Composable
fun ChromeWallpaperLayer(
    frame: WallpaperFrame,
    modifier: Modifier = Modifier,
    placeholder: Color = MainPalette.Page,
) {
    val loaded = rememberWallpaperBitmap(frame.imagePath)
    var held by remember { mutableStateOf<Pair<WallpaperFrame, ImageBitmap>?>(null) }
    val ready = loaded?.let { frame to it }
    val draw = wallpaperHoldUntilReady(ready, held)
    SideEffect {
        if (ready != null) held = ready
    }
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .then(
                if (draw == null) {
                    Modifier.background(placeholder)
                } else {
                    Modifier
                },
            ),
    ) {
        if (draw != null) {
            WallpaperCanvas(
                bitmap = draw.second,
                frame = draw.first,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun rememberWallpaperBitmap(path: String): ImageBitmap? {
    if (path.isBlank()) return null
    var bitmap by remember(path) { mutableStateOf(WallpaperBitmapCache.get(path)) }
    LaunchedEffect(path) {
        WallpaperBitmapCache.get(path)?.let {
            bitmap = it
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            }.getOrNull()
        }
        if (decoded != null) {
            WallpaperBitmapCache.put(path, decoded)
            bitmap = decoded
        }
    }
    return bitmap
}

@Composable
fun WallpaperImage(
    frame: WallpaperFrame,
    modifier: Modifier = Modifier,
) {
    if (!frame.hasImage) {
        Box(modifier.background(MainPalette.Page))
        return
    }
    val bmp = rememberWallpaperBitmap(frame.imagePath)
    if (bmp == null) {
        Box(modifier.background(MainPalette.Page))
        return
    }
    WallpaperCanvas(bitmap = bmp, frame = frame, modifier = modifier)
}

@Composable
private fun WallpaperCanvas(
    bitmap: ImageBitmap,
    frame: WallpaperFrame,
    modifier: Modifier = Modifier,
) {
    val host = LocalWallpaperViewport.current
    var originInWindow by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier
            .clipToBounds()
            .then(
                if (host != null) {
                    Modifier.onGloballyPositioned { originInWindow = it.positionInWindow() }
                } else {
                    Modifier
                },
            ),
    ) {
        val viewW = host?.width ?: size.width
        val viewH = host?.height ?: size.height
        val place = wallpaperCanvasPlacement(
            viewW = viewW,
            viewH = viewH,
            imgW = bitmap.width,
            imgH = bitmap.height,
            scale = frame.scale,
            offsetX = frame.offsetX,
            offsetY = frame.offsetY,
        ) ?: return@Canvas
        val dx = if (host != null) host.originInWindow.x - originInWindow.x else 0f
        val dy = if (host != null) host.originInWindow.y - originInWindow.y else 0f
        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(place.x + dx.roundToInt(), place.y + dy.roundToInt()),
            dstSize = IntSize(place.w, place.h),
            filterQuality = FilterQuality.Medium,
        )
    }
}

internal data class WallpaperCanvasPlace(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

/**
 * 画布铺法：[scale] 1 表示整张图刚好放进视口（contain），可小于 1 留边，
 * 也可大于铺满再放大。[offsetX]/[offsetY] 0 对齐图的左/上边，1 对齐右/下边。
 */
internal fun wallpaperCanvasPlacement(
    viewW: Float,
    viewH: Float,
    imgW: Int,
    imgH: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): WallpaperCanvasPlace? {
    if (viewW < 1f || viewH < 1f || imgW <= 0 || imgH <= 0) return null
    val fit = min(viewW / imgW.toFloat(), viewH / imgH.toFloat())
    val s = fit * scale.coerceIn(
        ChromeWallpaperStore.SCALE_MIN,
        ChromeWallpaperStore.SCALE_MAX,
    )
    val drawnW = (imgW * s).coerceAtLeast(1f)
    val drawnH = (imgH * s).coerceAtLeast(1f)
    val left = (viewW - drawnW) * offsetX.coerceIn(0f, 1f)
    val top = (viewH - drawnH) * offsetY.coerceIn(0f, 1f)
    return WallpaperCanvasPlace(
        x = left.roundToInt(),
        y = top.roundToInt(),
        w = drawnW.roundToInt().coerceAtLeast(1),
        h = drawnH.roundToInt().coerceAtLeast(1),
    )
}

/** 画布平移：0 对齐图的左/上边，1 对齐右/下边，与当前缩放无关。 */
internal fun wallpaperAlignPan(
    viewW: Float,
    viewH: Float,
    imgW: Int,
    imgH: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    panX: Float,
    panY: Float,
): Pair<Float, Float> {
    if (imgW <= 0 || imgH <= 0 || viewW < 1f || viewH < 1f) {
        return offsetX to offsetY
    }
    val fit = min(viewW / imgW.toFloat(), viewH / imgH.toFloat())
    val s = fit * scale.coerceIn(
        ChromeWallpaperStore.SCALE_MIN,
        ChromeWallpaperStore.SCALE_MAX,
    )
    val drawnW = (imgW * s).coerceAtLeast(1f)
    val drawnH = (imgH * s).coerceAtLeast(1f)
    val spanX = viewW - drawnW
    val spanY = viewH - drawnH
    val ox = if (kotlin.math.abs(spanX) < 0.5f) {
        offsetX
    } else {
        (offsetX + panX / spanX).coerceIn(0f, 1f)
    }
    val oy = if (kotlin.math.abs(spanY) < 0.5f) {
        offsetY
    } else {
        (offsetY + panY / spanY).coerceIn(0f, 1f)
    }
    return ox to oy
}
