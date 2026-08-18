package com.kite.zmusic.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.R
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImageCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** 默认预设 id / 显示名 */
const val PosterPresetCoverId = "song_cover"
const val PosterPresetCoverTitle = "歌曲封面图"

/**
 * 预设目录项：封面为「样式示意」，不是当前歌曲封面；[thumbAspectRatio] 可弹性，便于日后瀑布流。
 * ratio = 宽 / 高。
 */
data class PosterPresetDef(
    val id: String,
    val title: String,
    val description: String,
    val thumbAspectRatio: Float,
)

val PosterPresetCatalog: List<PosterPresetDef> = listOf(
    PosterPresetDef(
        id = PosterPresetCoverId,
        title = PosterPresetCoverTitle,
        description = "封面为主视觉，展示所选歌词、歌名、制作人与 ZMusic 品牌。",
        // 略偏竖版，瀑布流中可与其他比例卡片混排
        thumbAspectRatio = 0.78f,
    ),
)

/**
 * 「歌曲封面图」预设的样式示意封面（非当前歌曲封面）。
 * [aspectRatio] 弹性，供瀑布流卡片使用。
 */
@Composable
fun PosterSongCoverStyleThumb(
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.78f,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .aspectRatio(aspectRatio.coerceIn(0.55f, 1.35f))
            .clip(shape)
            .background(Color(0xFF0B0C0F))
            .border(1.dp, Color(0x33F4F0E8), shape),
    ) {
        // 风格化氛围（抽象封面光晕，不是真实曲目）
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF2A2430),
                        0.45f to Color(0xFF12141A),
                        1f to Color(0xFF0B0C0F),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFD4C4A8)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "ZMusic",
                    style = TextStyle(
                        color = Color(0xFFD4C4A8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF5A4A3A), Color(0xFF2A221C), Color(0xFF3A3028)),
                        ),
                    )
                    .border(1.dp, Color(0x33F4F0E8), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // 抽象「唱片/封面」纹样
                Box(
                    Modifier
                        .fillMaxSize(0.42f)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x331A1510))
                        .border(1.dp, Color(0x44D4C4A8), RoundedCornerShape(50)),
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFF4F0E8).copy(alpha = 0.88f)),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.38f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFB8B0A4).copy(alpha = 0.7f)),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.22f)
                    .height(1.dp)
                    .background(Color(0xFFD4C4A8).copy(alpha = 0.4f)),
            )
            Spacer(Modifier.height(8.dp))
            repeat(3) { i ->
                Box(
                    Modifier
                        .fillMaxWidth(0.72f - i * 0.08f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFF4F0E8).copy(alpha = 0.55f - i * 0.08f)),
                )
                if (i < 2) Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "样式预览",
                style = TextStyle(
                    color = Color(0xFFD4C4A8).copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    letterSpacing = 0.6.sp,
                ),
            )
        }
    }
}

/** 默认预设色调 */
enum class PosterCoverTone {
    Dark,
    Light,
}

private data class PosterCoverPalette(
    val ink: Color,
    val paper: Color,
    val muted: Color,
    val champagne: Color,
    val rule: Color,
    val veilTop: Color,
    val veilMid: Color,
    val coverFallback: Int,
    val brandArgb: Int,
    val timeArgb: Int,
    val titleArgb: Int,
    val artistArgb: Int,
    val lyricArgb: Int,
    val emptyArgb: Int,
    val sigArgb: Int,
    val ruleArgb: Int,
    val inkArgb: Int,
    val veilTopArgb: Int,
    val veilMidArgb: Int,
    val veilBotArgb: Int,
    val bgAlpha: Int,
)

private fun posterCoverPalette(tone: PosterCoverTone): PosterCoverPalette = when (tone) {
    PosterCoverTone.Dark -> PosterCoverPalette(
        ink = Color(0xFF0B0C0F),
        paper = Color(0xFFF4F0E8),
        muted = Color(0xFFB8B0A4),
        champagne = Color(0xFFD4C4A8),
        rule = Color(0x33F4F0E8),
        veilTop = Color(0xCC0B0C0F),
        veilMid = Color(0xE60B0C0F),
        coverFallback = 0xFF1A1C22.toInt(),
        brandArgb = 0xFFD4C4A8.toInt(),
        timeArgb = 0xD9B8B0A4.toInt(),
        titleArgb = 0xFFF4F0E8.toInt(),
        artistArgb = 0xFFB8B0A4.toInt(),
        lyricArgb = 0xEBFFF4F0E8.toInt(),
        emptyArgb = 0x66B8B0A4.toInt(),
        sigArgb = 0xC7D4C4A8.toInt(),
        ruleArgb = 0x59D4C4A8.toInt(),
        inkArgb = 0xFF0B0C0F.toInt(),
        veilTopArgb = 0xCC0B0C0F.toInt(),
        veilMidArgb = 0xE60B0C0F.toInt(),
        veilBotArgb = 0xFF0B0C0F.toInt(),
        bgAlpha = 70,
    )
    PosterCoverTone.Light -> PosterCoverPalette(
        ink = Color(0xFFF3EEE4),
        paper = Color(0xFF1A1510),
        muted = Color(0xFF6E6458),
        champagne = Color(0xFF8B7355),
        rule = Color(0x331A1510),
        veilTop = Color(0xE6F3EEE4),
        veilMid = Color(0xF2F3EEE4),
        coverFallback = 0xFFE6E0D6.toInt(),
        brandArgb = 0xFF8B7355.toInt(),
        timeArgb = 0xD96E6458.toInt(),
        titleArgb = 0xFF1A1510.toInt(),
        artistArgb = 0xFF6E6458.toInt(),
        lyricArgb = 0xEB1A1510.toInt(),
        emptyArgb = 0x666E6458.toInt(),
        sigArgb = 0xC78B7355.toInt(),
        ruleArgb = 0x598B7355.toInt(),
        inkArgb = 0xFFF3EEE4.toInt(),
        veilTopArgb = 0xE6F3EEE4.toInt(),
        veilMidArgb = 0xF2F3EEE4.toInt(),
        veilBotArgb = 0xFFF3EEE4.toInt(),
        bgAlpha = 55,
    )
}

/**
 * 「歌曲封面图」实时预览：与导出共用 [renderPosterCoverPresetBitmap]，避免 Compose / Canvas 两套排版。
 */
@Composable
fun PosterCoverPresetCard(
    track: TrackRow,
    lyricLines: List<String>,
    showTime: Boolean,
    signature: String,
    tone: PosterCoverTone = PosterCoverTone.Dark,
    timeText: String = rememberPosterTimeText(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = remember(tone) { posterCoverPalette(tone) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        track.id,
        track.name,
        track.artists,
        track.coverUrl,
        lyricLines,
        showTime,
        signature,
        tone,
        timeText,
    ) {
        delay(48)
        var produced: Bitmap? = null
        try {
            produced = renderPosterCoverPresetBitmap(
                context = context,
                track = track,
                lyricLines = lyricLines,
                showTime = showTime,
                signature = signature,
                tone = tone,
                timeText = timeText,
                widthPx = PosterPreviewWidthPx,
                heightPx = 0,
            )
            if (!isActive) return@LaunchedEffect
            val prev = preview
            preview = produced
            produced = null
            delay(80)
            if (prev != null && prev !== preview && !prev.isRecycled) {
                runCatching { prev.recycle() }
            }
        } finally {
            if (produced != null && !produced.isRecycled) {
                runCatching { produced.recycle() }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val held = preview
            preview = null
            if (held != null && !held.isRecycled) {
                runCatching { held.recycle() }
            }
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(palette.ink)
            .border(1.dp, palette.rule, shape),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = preview
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun rememberPosterTimeText(nowMs: Long = System.currentTimeMillis()): String {
    return remember(nowMs) {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.CHINA).format(Date(nowMs))
    }
}

/**
 * 将「歌曲封面图」预设渲染为 PNG。
 * [heightPx] ≤ 0 时高度至少为 3/2 宽，歌词放不下再加高，**不丢行**。
 */
const val PosterExportWidthPx = 3240
const val PosterExportHeightPx = 4860
const val PosterPreviewWidthPx = 900

suspend fun renderPosterCoverPresetBitmap(
    context: Context,
    track: TrackRow,
    lyricLines: List<String>,
    showTime: Boolean,
    signature: String,
    tone: PosterCoverTone = PosterCoverTone.Dark,
    timeText: String = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.CHINA).format(Date()),
    widthPx: Int = PosterExportWidthPx,
    heightPx: Int = PosterExportHeightPx,
): Bitmap = withContext(Dispatchers.IO) {
    val palette = posterCoverPalette(tone)
    val wPx = widthPx.coerceAtLeast(720)
    val minH = if (heightPx <= 0) {
        (wPx * 3 / 2)
    } else {
        heightPx.coerceAtLeast((wPx * 3 + 1) / 2)
    }
    val coverTarget = (wPx * 0.68f).toInt().coerceAtLeast(640)
    val coverAndroid = loadCoverFullResolution(context, track.coverUrl, coverTarget)
    val iconSizePx = (wPx * 0.052f).toInt().coerceAtLeast(28)
    val appIcon = decodeResourceFull(context, R.drawable.ic_logo_vinyl_z, iconSizePx * 2)
        ?: decodeResourceFull(context, R.mipmap.ic_launcher_foreground, iconSizePx * 2)
    val brand = context.getString(R.string.app_name)
    val plan = buildPosterCoverPlan(
        wPx = wPx,
        minH = minH,
        palette = palette,
        track = track,
        lyricLines = lyricLines,
        showTime = showTime,
        signature = signature,
        timeText = timeText,
        brand = brand,
        iconSize = iconSizePx.toFloat(),
    )
    val bmp = Bitmap.createBitmap(plan.w, plan.h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawPosterCoverPlan(canvas, plan, coverAndroid, appIcon, tone)
    if (appIcon != null && !appIcon.isRecycled) {
        runCatching { appIcon.recycle() }
    }
    if (coverAndroid != null && !coverAndroid.isRecycled) {
        runCatching { coverAndroid.recycle() }
    }
    bmp
}

private class PosterCoverPlan(
    val w: Int,
    val h: Int,
    val pad: Float,
    val iconSize: Float,
    val coverRect: RectF,
    val titleLayout: StaticLayout,
    val titleTop: Float,
    val artistLayout: StaticLayout,
    val artistTop: Float,
    val ruleY: Float,
    val ruleW: Float,
    val lyricLayouts: List<Pair<Float, StaticLayout>>,
    val emptyLyric: String?,
    val emptyLyricPaint: TextPaint?,
    val emptyLyricY: Float,
    val sigLayout: StaticLayout?,
    val sigTop: Float,
    val palette: PosterCoverPalette,
    val showTime: Boolean,
    val timeText: String,
    val brand: String,
    val brandPaint: TextPaint,
    val timePaint: TextPaint,
)

private fun buildPosterCoverPlan(
    wPx: Int,
    minH: Int,
    palette: PosterCoverPalette,
    track: TrackRow,
    lyricLines: List<String>,
    showTime: Boolean,
    signature: String,
    timeText: String,
    brand: String,
    iconSize: Float,
): PosterCoverPlan {
    val w = wPx.toFloat()
    val pad = w * 0.055f
    val textW = (wPx - (pad * 2f).toInt()).coerceAtLeast(1)
    val brandPaint = sharpTextPaint(palette.brandArgb, w * 0.030f, bold = true).apply {
        letterSpacing = 0.10f
    }
    val timePaint = sharpTextPaint(palette.timeArgb, w * 0.024f, bold = false)
    val artistPaint = sharpTextPaint(palette.artistArgb, w * 0.029f, bold = false)
    val sigPaint = sharpTextPaint(palette.sigArgb, w * 0.027f, bold = false).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }
    val titleText = track.name.ifBlank { "未知歌曲" }
    val artistText = track.artists.ifBlank { "未知制作人" }
    val sigText = signature.trim().let { if (it.isEmpty()) "" else "「$it」" }
    val lines = lyricLines.map { it.trim() }.filter { it.isNotEmpty() }

    var titleSize = w * 0.042f
    var lyricSize = w * 0.034f
    var coverSide = w * 0.60f
    val coverMin = w * 0.36f
    val lyricMin = w * 0.022f
    val titleMin = w * 0.032f

    fun titleLayout(size: Float) = posterStaticLayout(
        titleText,
        sharpTextPaint(palette.titleArgb, size, bold = true),
        textW,
        maxLines = 2,
        ellipsize = android.text.TextUtils.TruncateAt.END,
        spacingMult = 1.08f,
    )

    fun lyricLayouts(size: Float): List<StaticLayout> {
        val paint = sharpTextPaint(palette.lyricArgb, size, bold = false)
        return lines.map { line ->
            posterStaticLayout(
                line,
                paint,
                textW,
                maxLines = Int.MAX_VALUE,
                ellipsize = null,
                spacingMult = 1.14f,
            )
        }
    }

    val artistLayout = posterStaticLayout(
        artistText,
        artistPaint,
        textW,
        maxLines = 1,
        ellipsize = android.text.TextUtils.TruncateAt.END,
        spacingMult = 1f,
    )
    val sigLayout = if (sigText.isEmpty()) {
        null
    } else {
        posterStaticLayout(
            sigText,
            sigPaint,
            textW,
            maxLines = 2,
            ellipsize = android.text.TextUtils.TruncateAt.END,
            spacingMult = 1.08f,
        )
    }

    var titleL = titleLayout(titleSize)
    var lyrics = lyricLayouts(lyricSize)

    fun stackHeight(cover: Float, title: StaticLayout, lyric: List<StaticLayout>): Float {
        var y = pad + iconSize + w * 0.036f
        y += cover + w * 0.030f
        y += title.height + w * 0.010f
        y += artistLayout.height + w * 0.024f
        y += w * 0.028f
        if (lyric.isEmpty()) {
            y += lyricSize * 1.4f
        } else {
            lyric.forEachIndexed { i, layout ->
                y += layout.height
                if (i != lyric.lastIndex) y += w * 0.010f
            }
        }
        if (sigLayout != null) y += w * 0.022f + sigLayout.height
        y += pad
        return y
    }

    var contentH = stackHeight(coverSide, titleL, lyrics)
    while (contentH > minH + 1f && coverSide > coverMin) {
        coverSide = (coverSide - w * 0.018f).coerceAtLeast(coverMin)
        contentH = stackHeight(coverSide, titleL, lyrics)
    }
    while (contentH > minH + 1f && lyricSize > lyricMin) {
        lyricSize = (lyricSize - w * 0.0014f).coerceAtLeast(lyricMin)
        lyrics = lyricLayouts(lyricSize)
        contentH = stackHeight(coverSide, titleL, lyrics)
    }
    while (contentH > minH + 1f && titleSize > titleMin) {
        titleSize = (titleSize - w * 0.0012f).coerceAtLeast(titleMin)
        titleL = titleLayout(titleSize)
        contentH = stackHeight(coverSide, titleL, lyrics)
    }
    val hPx = max(minH, ceil(contentH).toInt())
    val h = hPx.toFloat()

    var y = pad
    val coverLeft = (w - coverSide) / 2f
    val coverTop = y + iconSize + w * 0.036f
    val coverRect = RectF(coverLeft, coverTop, coverLeft + coverSide, coverTop + coverSide)
    y = coverRect.bottom + w * 0.030f
    val titleTop = y
    y += titleL.height + w * 0.010f
    val artistTop = y
    y += artistLayout.height + w * 0.024f
    val ruleY = y
    y += w * 0.028f

    val sigH = sigLayout?.height?.toFloat() ?: 0f
    val sigTop = if (sigLayout != null) h - pad - sigH else h - pad
    val lyricBottom = if (sigLayout != null) sigTop - w * 0.022f else h - pad

    val lyricBlockH = if (lyrics.isEmpty()) {
        lyricSize * 1.4f
    } else {
        lyrics.foldIndexed(0f) { i, acc, layout ->
            acc + layout.height + if (i == lyrics.lastIndex) 0f else w * 0.010f
        }
    }
    val lyricBand = (lyricBottom - y).coerceAtLeast(lyricBlockH)
    var ly = y + ((lyricBand - lyricBlockH) / 2f).coerceAtLeast(0f)
    val placedLyrics = ArrayList<Pair<Float, StaticLayout>>(lyrics.size)
    for ((i, layout) in lyrics.withIndex()) {
        placedLyrics.add(ly to layout)
        ly += layout.height
        if (i != lyrics.lastIndex) ly += w * 0.010f
    }
    val emptyPaint = if (lyrics.isEmpty()) {
        sharpTextPaint(palette.emptyArgb, lyricSize, bold = false)
    } else {
        null
    }
    val emptyY = if (lyrics.isEmpty()) {
        y + lyricBand * 0.42f
    } else {
        0f
    }

    return PosterCoverPlan(
        w = wPx,
        h = hPx,
        pad = pad,
        iconSize = iconSize,
        coverRect = coverRect,
        titleLayout = titleL,
        titleTop = titleTop,
        artistLayout = artistLayout,
        artistTop = artistTop,
        ruleY = ruleY,
        ruleW = w * 0.14f,
        lyricLayouts = placedLyrics,
        emptyLyric = if (lyrics.isEmpty()) "未选择歌词" else null,
        emptyLyricPaint = emptyPaint,
        emptyLyricY = emptyY,
        sigLayout = sigLayout,
        sigTop = sigTop,
        palette = palette,
        showTime = showTime,
        timeText = timeText,
        brand = brand,
        brandPaint = brandPaint,
        timePaint = timePaint,
    )
}

private fun drawPosterCoverPlan(
    canvas: Canvas,
    plan: PosterCoverPlan,
    coverAndroid: Bitmap?,
    appIcon: Bitmap?,
    tone: PosterCoverTone,
) {
    val w = plan.w.toFloat()
    val h = plan.h.toFloat()
    val palette = plan.palette
    canvas.drawColor(palette.inkArgb)
    if (coverAndroid != null) {
        val src = android.graphics.Rect(0, 0, coverAndroid.width, coverAndroid.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = palette.bgAlpha
            isFilterBitmap = true
        }
        canvas.drawBitmap(coverAndroid, src, RectF(0f, 0f, w, h), paint)
    }
    val veil = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(palette.veilTopArgb, palette.veilMidArgb, palette.veilBotArgb),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, w, h, veil)

    val pad = plan.pad
    val yBrand = pad
    val iconSize = plan.iconSize
    if (appIcon != null) {
        val iconDst = RectF(pad, yBrand, pad + iconSize, yBrand + iconSize)
        val round = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isFilterBitmap = true
        }
        canvas.save()
        val path = android.graphics.Path().apply {
            addRoundRect(iconDst, iconSize * 0.22f, iconSize * 0.22f, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(path)
        canvas.drawBitmap(
            appIcon,
            android.graphics.Rect(0, 0, appIcon.width, appIcon.height),
            iconDst,
            round,
        )
        canvas.restore()
    }
    canvas.drawText(
        plan.brand,
        pad + iconSize + w * 0.018f,
        yBrand + iconSize * 0.72f,
        plan.brandPaint,
    )
    if (plan.showTime) {
        val tw = plan.timePaint.measureText(plan.timeText)
        canvas.drawText(plan.timeText, w - pad - tw, yBrand + iconSize * 0.72f, plan.timePaint)
    }

    val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = true
    }
    canvas.save()
    val coverPath = android.graphics.Path().apply {
        addRoundRect(plan.coverRect, w * 0.032f, w * 0.032f, android.graphics.Path.Direction.CW)
    }
    canvas.clipPath(coverPath)
    if (coverAndroid != null) {
        canvas.drawBitmap(
            coverAndroid,
            centeredCropSrc(coverAndroid.width, coverAndroid.height),
            plan.coverRect,
            coverPaint,
        )
    } else {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.coverFallback }
        canvas.drawRect(plan.coverRect, fill)
    }
    canvas.restore()
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (w * 0.0025f).coerceAtLeast(2f)
        color = if (tone == PosterCoverTone.Dark) 0x33F4F0E8.toInt() else 0x331A1510.toInt()
    }
    canvas.drawRoundRect(plan.coverRect, w * 0.032f, w * 0.032f, stroke)

    canvas.save()
    canvas.translate(pad, plan.titleTop)
    plan.titleLayout.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.translate(pad, plan.artistTop)
    plan.artistLayout.draw(canvas)
    canvas.restore()

    val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.ruleArgb
        strokeWidth = (w * 0.0016f).coerceAtLeast(2f)
    }
    canvas.drawLine(
        (w - plan.ruleW) / 2f,
        plan.ruleY,
        (w + plan.ruleW) / 2f,
        plan.ruleY,
        rulePaint,
    )

    if (plan.emptyLyric != null && plan.emptyLyricPaint != null) {
        val ew = plan.emptyLyricPaint.measureText(plan.emptyLyric)
        canvas.drawText(plan.emptyLyric, (w - ew) / 2f, plan.emptyLyricY, plan.emptyLyricPaint)
    } else {
        for ((top, layout) in plan.lyricLayouts) {
            canvas.save()
            canvas.translate(pad, top)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    val sig = plan.sigLayout
    if (sig != null) {
        canvas.save()
        canvas.translate(pad, plan.sigTop)
        sig.draw(canvas)
        canvas.restore()
    }
}

private fun posterStaticLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    maxLines: Int,
    ellipsize: android.text.TextUtils.TruncateAt?,
    spacingMult: Float,
): StaticLayout {
    val builder = StaticLayout.Builder.obtain(
        text,
        0,
        text.length,
        paint,
        width.coerceAtLeast(1),
    )
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setIncludePad(false)
        .setLineSpacing(0f, spacingMult)
    if (maxLines < Int.MAX_VALUE) {
        builder.setMaxLines(maxLines)
    }
    if (ellipsize != null) {
        builder.setEllipsize(ellipsize)
    }
    return builder.build()
}

private fun sharpTextPaint(colorArgb: Int, textSizePx: Float, bold: Boolean): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        color = colorArgb
        textSize = textSizePx
        typeface = Typeface.create(
            Typeface.SANS_SERIF,
            if (bold) Typeface.BOLD else Typeface.NORMAL,
        )
        isAntiAlias = true
        isSubpixelText = true
        isFilterBitmap = true
        hinting = Paint.HINTING_ON
    }

/** center-crop 源矩形，保证封面铺满目标框且不拉伸变形 */
private fun centeredCropSrc(srcW: Int, srcH: Int): android.graphics.Rect {
    if (srcW <= 0 || srcH <= 0) return android.graphics.Rect(0, 0, srcW.coerceAtLeast(0), srcH.coerceAtLeast(0))
    return if (srcW >= srcH) {
        val left = (srcW - srcH) / 2
        android.graphics.Rect(left, 0, left + srcH, srcH)
    } else {
        val top = (srcH - srcW) / 2
        android.graphics.Rect(0, top, srcW, top + srcW)
    }
}

/**
 * 导出专用：绕开 Compose 内存缩略缓存，从磁盘/网络按全分辨率解码。
 */
private suspend fun loadCoverFullResolution(
    context: Context,
    url: String?,
    minSidePx: Int,
): Bitmap? {
    val key = UrlImageCache.normalizeKey(url) ?: return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val file = UrlImageCache.diskFile(context, key)
            if (!file.exists()) {
                UrlImageCache.prefetch(context, key)
            }
            if (!file.exists()) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return@runCatching null
            // 仅当原图远大于目标时降采样，始终保留 ≥ minSide 的锐度
            var sample = 1
            val shortest = minOf(srcW, srcH)
            while (shortest / (sample * 2) >= minSidePx * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@runCatching null
            if (decoded.config == Bitmap.Config.HARDWARE) {
                decoded.copy(Bitmap.Config.ARGB_8888, false) ?: decoded
            } else {
                decoded
            }
        }.getOrNull()
    }
}

private fun decodeResourceFull(context: Context, resId: Int, minSidePx: Int): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        var sample = 1
        val shortest = minOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (shortest / (sample * 2) >= minSidePx * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        BitmapFactory.decodeResource(context.resources, resId, opts)
    }.getOrNull()
}
