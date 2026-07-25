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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.R
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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
 * 默认预设「歌曲封面图」实时预览卡片。
 * 品牌（ZMusic + icon）+ 封面 + 歌名/制作人 + 所选歌词 + 可选时间/签名。
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
    val palette = remember(tone) { posterCoverPalette(tone) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(palette.ink)
            .border(1.dp, palette.rule, shape),
    ) {
        // 氛围：封面虚化底 + 色调罩
        Box(Modifier.fillMaxSize()) {
            UrlImage(
                url = track.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (tone == PosterCoverTone.Dark) 0.42f else 0.28f
                        scaleX = 1.18f
                        scaleY = 1.18f
                    },
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to palette.veilTop,
                            0.35f to palette.veilMid,
                            1f to palette.ink,
                        ),
                    ),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo_vinyl_z),
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = TextStyle(
                        color = palette.champagne,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                        fontFamily = FontFamily.SansSerif,
                    ),
                )
                Spacer(Modifier.weight(1f))
                if (showTime) {
                    Text(
                        text = timeText,
                        style = TextStyle(
                            color = palette.muted.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.2.sp,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 封面：按宽度定边长，避免嵌套 aspectRatio 在矮卡片内再次撑破约束
            BoxWithConstraints(Modifier.fillMaxWidth(0.78f)) {
                val side = maxWidth
                Box(
                    Modifier
                        .size(side)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, palette.rule, RoundedCornerShape(14.dp)),
                ) {
                    UrlImage(
                        url = track.coverUrl,
                        contentDescription = track.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = track.name.ifBlank { "未知歌曲" },
                style = TextStyle(
                    color = palette.paper,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.artists.ifBlank { "未知制作人" },
                style = TextStyle(
                    color = palette.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.28f)
                    .height(1.dp)
                    .background(palette.champagne.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (lyricLines.isEmpty()) {
                    Text(
                        text = "未选择歌词",
                        style = TextStyle(
                            color = palette.muted.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                        ),
                    )
                } else {
                    lyricLines.forEachIndexed { i, line ->
                        Text(
                            text = line,
                            style = TextStyle(
                                color = palette.paper.copy(alpha = 0.92f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 22.sp,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        if (i != lyricLines.lastIndex) {
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }

            val sig = signature.trim()
            if (sig.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "「$sig」",
                    style = TextStyle(
                        color = palette.champagne.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Serif,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
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
 * 将「歌曲封面图」预设渲染为 PNG 用 Bitmap（与预览版式对齐）。
 * 默认 [PosterExportWidthPx]×[PosterExportHeightPx]（3× 逻辑稿），保证导出足够锐利。
 */
const val PosterExportWidthPx = 3240
const val PosterExportHeightPx = 4860

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
    val wPx = widthPx.coerceAtLeast(1080)
    val hPx = heightPx.coerceAtLeast(1620)
    val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val w = wPx.toFloat()
    val h = hPx.toFloat()

    canvas.drawColor(palette.inkArgb)

    // 封面按海报主视觉边长拉取全分辨率，避免缓存缩略图被放大发糊
    val coverTarget = (w * 0.72f).toInt().coerceAtLeast(1024)
    val coverAndroid = loadCoverFullResolution(context, track.coverUrl, coverTarget)
    if (coverAndroid != null) {
        val src = android.graphics.Rect(0, 0, coverAndroid.width, coverAndroid.height)
        val dst = RectF(0f, 0f, w, h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = palette.bgAlpha
            isFilterBitmap = true
        }
        canvas.drawBitmap(coverAndroid, src, dst, paint)
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

    val pad = w * 0.055f
    var y = pad

    // brand row — 优先高密度解码 logo
    val iconSize = w * 0.055f
    val appIcon = decodeResourceFull(context, R.drawable.ic_logo_vinyl_z, (iconSize * 2).toInt())
        ?: decodeResourceFull(context, R.mipmap.ic_launcher_foreground, (iconSize * 2).toInt())
    if (appIcon != null) {
        val iconDst = RectF(pad, y, pad + iconSize, y + iconSize)
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
        if (!appIcon.isRecycled) {
            runCatching { appIcon.recycle() }
        }
    }
    val brandPaint = sharpTextPaint(palette.brandArgb, w * 0.032f, bold = true).apply {
        letterSpacing = 0.12f
    }
    val brand = context.getString(R.string.app_name)
    canvas.drawText(brand, pad + iconSize + w * 0.02f, y + iconSize * 0.72f, brandPaint)
    if (showTime) {
        val timePaint = sharpTextPaint(palette.timeArgb, w * 0.026f, bold = false)
        val tw = timePaint.measureText(timeText)
        canvas.drawText(timeText, w - pad - tw, y + iconSize * 0.72f, timePaint)
    }
    y += iconSize + w * 0.045f

    // cover
    val coverSide = w * 0.72f
    val coverLeft = (w - coverSide) / 2f
    val coverRect = RectF(coverLeft, y, coverLeft + coverSide, y + coverSide)
    val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = true
    }
    canvas.save()
    val coverPath = android.graphics.Path().apply {
        addRoundRect(coverRect, w * 0.035f, w * 0.035f, android.graphics.Path.Direction.CW)
    }
    canvas.clipPath(coverPath)
    if (coverAndroid != null) {
        canvas.drawBitmap(
            coverAndroid,
            centeredCropSrc(coverAndroid.width, coverAndroid.height),
            coverRect,
            coverPaint,
        )
    } else {
        canvas.drawColor(palette.coverFallback)
    }
    canvas.restore()
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (w * 0.0025f).coerceAtLeast(2f)
        color = if (tone == PosterCoverTone.Dark) 0x33F4F0E8.toInt() else 0x331A1510.toInt()
    }
    canvas.drawRoundRect(coverRect, w * 0.035f, w * 0.035f, stroke)
    y += coverSide + w * 0.04f

    // title / artist
    val titlePaint = sharpTextPaint(palette.titleArgb, w * 0.048f, bold = true)
    val title = track.name.ifBlank { "未知歌曲" }
    val titleLayout = StaticLayout.Builder.obtain(
        title, 0, title.length, titlePaint, (w - pad * 2).toInt(),
    )
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(2)
        .setEllipsize(android.text.TextUtils.TruncateAt.END)
        .build()
    canvas.save()
    canvas.translate(pad, y)
    titleLayout.draw(canvas)
    canvas.restore()
    y += titleLayout.height + w * 0.012f

    val artistPaint = sharpTextPaint(palette.artistArgb, w * 0.032f, bold = false)
    val artist = track.artists.ifBlank { "未知制作人" }
    val artistW = artistPaint.measureText(artist)
    canvas.drawText(artist, (w - artistW) / 2f, y + artistPaint.textSize, artistPaint)
    y += artistPaint.textSize + w * 0.03f

    // rule
    val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.ruleArgb
        strokeWidth = 3f
    }
    val ruleW = w * 0.14f
    canvas.drawLine((w - ruleW) / 2f, y, (w + ruleW) / 2f, y, rulePaint)
    y += w * 0.035f

    // lyrics
    val lyricPaint = sharpTextPaint(palette.lyricArgb, w * 0.038f, bold = false)
    val lyricBottomReserve = pad + w * 0.08f
    val lyricMaxH = (h - lyricBottomReserve - y).coerceAtLeast(w * 0.2f)
    if (lyricLines.isEmpty()) {
        val empty = "未选择歌词"
        val ew = lyricPaint.measureText(empty)
        lyricPaint.color = palette.emptyArgb
        canvas.drawText(empty, (w - ew) / 2f, y + lyricMaxH * 0.4f, lyricPaint)
    } else {
        var ly = y
        val gap = w * 0.012f
        for (line in lyricLines) {
            val layout = StaticLayout.Builder.obtain(
                line, 0, line.length, lyricPaint, (w - pad * 2).toInt(),
            )
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            if (ly + layout.height > y + lyricMaxH) break
            canvas.save()
            canvas.translate(pad, ly)
            layout.draw(canvas)
            canvas.restore()
            ly += layout.height + gap
        }
    }

    val sig = signature.trim()
    if (sig.isNotEmpty()) {
        val sigPaint = sharpTextPaint(palette.sigArgb, w * 0.03f, bold = false).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val shown = "「$sig」"
        val sigLayout = StaticLayout.Builder.obtain(
            shown, 0, shown.length, sigPaint, (w - pad * 2).toInt(),
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        val sigY = h - pad - sigLayout.height
        canvas.save()
        canvas.translate(pad, sigY)
        sigLayout.draw(canvas)
        canvas.restore()
    }

    // 导出用独立位图，封面拷贝可回收
    if (coverAndroid != null && !coverAndroid.isRecycled) {
        runCatching { coverAndroid.recycle() }
    }
    bmp
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
                inDither = false
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
