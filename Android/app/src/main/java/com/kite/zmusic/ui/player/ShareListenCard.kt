package com.kite.zmusic.ui.player

import android.content.Context
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
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kite.zmusic.BuildConfig
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.ZMusicListenLink
import com.kite.zmusic.listen.ListenRoomSnapshot
import com.kite.zmusic.ui.common.UrlImageCache
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ShareListenCard {
    private const val W = 1080
    private const val H = 1440
    private const val Ink = 0xFFF7F1E8.toInt()
    private const val Muted = 0xFFC4B8A8.toInt()
    private const val Gold = 0xFFE2B86A.toInt()
    private const val Paper = 0xFF1B1714.toInt()

    suspend fun prepareShareUri(
        app: ZMusicApplication,
        room: ListenRoomSnapshot,
    ): Uri? {
        val bmp = render(app, room) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(app.cacheDir, "plugin-share").apply { mkdirs() }
                val file = File(dir, "zmusic-listen-${room.id}.png")
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

    suspend fun render(
        app: ZMusicApplication,
        room: ListenRoomSnapshot,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val qrText = room.qrText.ifBlank { ZMusicListenLink.format(room.id) }
        if (qrText.isBlank()) return@withContext null
        val host = room.members.firstOrNull { it.host }
        val hostName = host?.nickname?.ifBlank { null } ?: "好友"
        val avatar = loadUrlBitmap(app, resolveUrl(app, host?.avatarUrl.orEmpty()), 240)
        val qr = PlayerDisplayQr.encodeBitmap(qrText, 500, ErrorCorrectionLevel.M)
        val logo = runCatching {
            BitmapFactory.decodeResource(app.resources, R.mipmap.ic_launcher)
        }.getOrNull()
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        try {
            val bg = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, H.toFloat(),
                    intArrayOf(0xFF2A221C.toInt(), Paper, 0xFF12100E.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
            val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
            c.drawRoundRect(RectF(72f, 72f, W - 72f, H - 72f), 48f, 48f, card)

            val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Gold
                textSize = 46f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                letterSpacing = 0.04f
            }
            if (logo != null) {
                val icon = 96
                val scaled = Bitmap.createScaledBitmap(logo, icon, icon, true)
                c.drawBitmap(scaled, 120f, 118f, null)
                if (scaled !== logo) scaled.recycle()
                c.drawText("ZMusic", 236f, 182f, brand)
            } else {
                c.drawText("ZMusic", 120f, 182f, brand)
            }

            val avSize = 168f
            val avLeft = 120f
            val avTop = 248f
            drawAvatar(c, avatar, RectF(avLeft, avTop, avLeft + avSize, avTop + avSize))
            val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Ink
                textSize = 44f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            c.drawText(hostName, avLeft + avSize + 28f, avTop + 72f, namePaint)
            val rolePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Muted
                textSize = 30f
            }
            c.drawText("发起一起听", avLeft + avSize + 28f, avTop + 118f, rolePaint)

            val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Ink
                textSize = 72f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            c.drawText("一起听", 120f, 520f, title)
            layout(
                c,
                "「$hostName」邀请你进入一起听，用 ZMusic 扫码即可加入",
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Muted
                    textSize = 34f
                },
                120f,
                548f,
                W - 240,
            )

            val qrLeft = ((W - qr.width) / 2f)
            val qrTop = 690f
            val well = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
            c.drawRoundRect(
                RectF(qrLeft - 28f, qrTop - 28f, qrLeft + qr.width + 28f, qrTop + qr.height + 28f),
                28f,
                28f,
                well,
            )
            c.drawBitmap(qr, qrLeft, qrTop, null)

            layout(
                c,
                "最多 ${room.maxMembers} 人 · 歌曲会一起切换",
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Muted
                    textSize = 30f
                },
                120f,
                H - 168f,
                W - 240,
            )
            bmp
        } finally {
            qr.recycle()
            if (logo != null && !logo.isRecycled) logo.recycle()
            if (avatar != null && !avatar.isRecycled) avatar.recycle()
        }
    }

    private fun drawAvatar(canvas: Canvas, avatar: Bitmap?, rect: RectF) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8DFD4.toInt() }
        canvas.drawOval(rect, fill)
        val inner = RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f)
        val path = Path().apply { addOval(inner, Path.Direction.CW) }
        canvas.withClip(path) {
            if (avatar != null) {
                drawBitmap(avatar, null, inner, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Gold
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawOval(rect, ring)
    }

    private fun layout(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
    ) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun resolveUrl(app: ZMusicApplication, raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        if (t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true)
        ) {
            return t
        }
        if (t.startsWith("//")) return "http:$t"
        val e = app.communityServerStore.current()
        val authority = if (e.port == 80) e.host.trim() else "${e.host.trim()}:${e.port}"
        return if (t.startsWith("/")) "http://$authority$t" else t
    }

    private suspend fun loadUrlBitmap(context: Context, url: String, minSide: Int): Bitmap? {
        val key = UrlImageCache.normalizeKey(url) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = UrlImageCache.diskFile(context, key)
                if (!file.exists()) UrlImageCache.prefetch(context, key)
                if (!file.exists()) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val shortest = minOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                var sample = 1
                while (shortest / (sample * 2) >= minSide * 2) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            }.getOrNull()
        }
    }
}
