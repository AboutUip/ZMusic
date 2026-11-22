package com.kite.zmusic.ui.podcast

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kite.zmusic.BuildConfig
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.PodcastEpisode
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.player.NcmShare
import com.kite.zmusic.ui.player.PlayerDisplayQr
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 播客分享图。只排节目名、电台名、时长和节目二维码，文字都落在量好的宽度里。
 */
internal object SharePodcastPoster {
    private const val W = 1080
    private const val H = 1920

    suspend fun prepareShareUri(app: ZMusicApplication, episode: PodcastEpisode): Uri? {
        if (episode.programId <= 0L) return null
        val page = NcmShare.programPageUrl(episode.programId) ?: return null
        val copy = PosterCopy(
            kicker = t("播客"),
            listen = t("扫码收听这期"),
            duration = t("时长"),
        )
        val cover = episode.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            UrlImageCache.loadBytes(app, url)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
        val bmp = try {
            withContext(Dispatchers.IO) { draw(episode, page, copy, cover) }
        } finally {
            if (cover != null && !cover.isRecycled) cover.recycle()
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(app.cacheDir, "plugin-share").apply { mkdirs() }
                val file = File(dir, "zmusic-podcast-${episode.programId}.png")
                FileOutputStream(file).use { out ->
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) error("compress")
                }
                FileProvider.getUriForFile(
                    app,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    file,
                )
            }.getOrNull().also {
                if (!bmp.isRecycled) runCatching { bmp.recycle() }
            }
        }
    }

    private fun draw(
        episode: PodcastEpisode,
        pageUrl: String,
        copy: PosterCopy,
        cover: Bitmap?,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF140E16.toInt())
        if (cover != null) {
            val wash = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 78 }
            drawCenterCrop(canvas, cover, RectF(-20f, -20f, W + 20f, H * 0.72f), wash)
        }
        canvas.drawRect(
            0f,
            0f,
            W.toFloat(),
            H.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    H.toFloat(),
                    intArrayOf(0x40140E16, 0xCC140E16.toInt(), 0xFF140E16.toInt()),
                    floatArrayOf(0f, 0.42f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )

        val titlePaint = textPaint(64f, 0xFFFFF4EA.toInt(), bold = true)
        val showPaint = textPaint(36f, 0xFFE7D2C8.toInt(), bold = false)
        val metaPaint = textPaint(30f, 0xFFCDB8B0.toInt(), bold = false)
        val textWidth = W - 160
        val titleLayout = textLayout(episode.name.ifBlank { copy.kicker }, titlePaint, textWidth, 2)
        val showName = episode.podcastName.trim()
        val showLayout = if (showName.isNotEmpty()) textLayout(showName, showPaint, textWidth, 1) else null
        val durationText = if (episode.durationMs > 0L) {
            "${copy.duration}  ${clock(episode.durationMs)}"
        } else {
            ""
        }
        val durationLayout = if (durationText.isNotEmpty()) textLayout(durationText, metaPaint, textWidth, 1) else null
        val gap = 18f
        var block = titleLayout.height.toFloat()
        if (showLayout != null) block += gap + showLayout.height
        if (durationLayout != null) block += gap + durationLayout.height

        val cardH = 360f
        val card = RectF(72f, H - 88f - cardH, W - 72f, H - 88f)
        val textTop = card.top - 40f - block
        val regionTop = 176f
        val regionBottom = textTop - 28f
        val region = (regionBottom - regionTop).coerceAtLeast(360f)
        val coverSize = min(760f, region)
        val coverTop = regionTop + (region - coverSize) / 2f
        val coverRect = RectF((W - coverSize) / 2f, coverTop, (W + coverSize) / 2f, coverTop + coverSize)

        drawKicker(canvas, copy.kicker, 78f)
        val coverPath = roundRect(coverRect, 52f)
        if (cover != null) {
            canvas.withClip(coverPath) {
                drawCenterCrop(this, cover, coverRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
        } else {
            canvas.drawPath(coverPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A2A34.toInt() })
        }
        canvas.drawPath(
            coverPath,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = 0x66FFF4EA
            },
        )

        var y = textTop
        val textLeft = (W - textWidth) / 2f
        canvas.withTranslation(textLeft, y) { titleLayout.draw(this) }
        y += titleLayout.height
        if (showLayout != null) {
            y += gap
            canvas.withTranslation(textLeft, y) { showLayout.draw(this) }
            y += showLayout.height
        }
        if (durationLayout != null) {
            y += gap
            canvas.withTranslation(textLeft, y) { durationLayout.draw(this) }
        }

        canvas.drawRoundRect(card, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F1EA.toInt() })
        val qrSize = 248f
        val qrLeft = card.left + 36f
        val qrTop = card.centerY() - qrSize / 2f
        val qr = PlayerDisplayQr.encodeBitmap(pageUrl, 640, ErrorCorrectionLevel.Q)
        canvas.drawBitmap(
            qr,
            null,
            RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        if (!qr.isRecycled) qr.recycle()

        val columnLeft = qrLeft + qrSize + 32f
        val columnWidth = (card.right - 36f - columnLeft).toInt().coerceAtLeast(1)
        val listenLayout = textLayout(
            copy.listen,
            textPaint(40f, 0xFF2C2420.toInt(), bold = true),
            columnWidth,
            2,
            Layout.Alignment.ALIGN_NORMAL,
        )
        val episodeLayout = textLayout(
            episode.name.ifBlank { copy.kicker },
            textPaint(30f, 0xFF4A403C.toInt(), bold = false),
            columnWidth,
            2,
            Layout.Alignment.ALIGN_NORMAL,
        )
        val hostLayout = textLayout(
            "music.163.com",
            textPaint(26f, 0xFF8A7C76.toInt(), bold = false),
            columnWidth,
            1,
            Layout.Alignment.ALIGN_NORMAL,
        )
        val columnBlock = listenLayout.height + 16f + episodeLayout.height + 14f + hostLayout.height
        var columnY = card.centerY() - columnBlock / 2f
        canvas.withClip(roundRect(card, 40f)) {
            withTranslation(columnLeft, columnY) { listenLayout.draw(this) }
            columnY += listenLayout.height + 16f
            withTranslation(columnLeft, columnY) { episodeLayout.draw(this) }
            columnY += episodeLayout.height + 14f
            withTranslation(columnLeft, columnY) { hostLayout.draw(this) }
        }

        val brand = textPaint(28f, 0x99FFF4EA.toInt(), bold = false).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("ZMusic", W / 2f, card.bottom + 52f, brand)
        return bmp
    }

    private fun drawKicker(canvas: Canvas, label: String, top: Float) {
        val paint = textPaint(30f, 0xFFFFF4EA.toInt(), bold = true)
        val textWidth = paint.measureText(label)
        val pill = RectF((W - textWidth) / 2f - 28f, top, (W + textWidth) / 2f + 28f, top + 64f)
        canvas.drawRoundRect(pill, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
        val baseline = pill.centerY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(label, pill.left + 28f, baseline, paint)
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, dest: RectF, paint: Paint) {
        val bw = bitmap.width.toFloat().coerceAtLeast(1f)
        val bh = bitmap.height.toFloat().coerceAtLeast(1f)
        val scale = max(dest.width() / bw, dest.height() / bh)
        val dw = bw * scale
        val dh = bh * scale
        val left = dest.centerX() - dw / 2f
        val top = dest.centerY() - dh / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + dw, top + dh), paint)
    }

    private fun roundRect(rect: RectF, radius: Float): Path = Path().apply {
        addRoundRect(rect, radius, radius, Path.Direction.CW)
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.SANS_SERIF
    }

    private fun textLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()
    }

    private fun clock(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private data class PosterCopy(
        val kicker: String,
        val listen: String,
        val duration: String,
    )
}
