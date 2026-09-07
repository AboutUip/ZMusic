package com.kite.zmusic.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kite.zmusic.BuildConfig
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.ChromeWallpaperStore
import com.kite.zmusic.data.ChromeWallpaperSurface
import com.kite.zmusic.data.SongComment
import com.kite.zmusic.data.WallpaperFrame
import com.kite.zmusic.data.SongCommentsCache
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.ZMusicSongLink
import com.kite.zmusic.plugin.PluginLookPresent
import com.kite.zmusic.ui.common.UrlImageCache
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 分享用歌曲明信片：主页自定义壁纸（铺满裁切）或默认插画，米白纸卡。
 */
internal object ShareSongPoster {
    private const val W = 1080
    private const val H = 1920
    private const val Paper = 0xFFFFF8F1.toInt()
    private const val Ink = 0xFF2C2620.toInt()
    private const val Muted = 0xFF8A8278.toInt()
    private const val Gold = 0xFFD4A05A.toInt()
    private const val Like = 0xFFE25C4A.toInt()
    private const val SongWell = 0xFF3A3642.toInt()
    private const val SongWellHi = 0xFF4A4554.toInt()

    suspend fun prepareShareUri(app: ZMusicApplication, track: TrackRow): Uri? {
        if (track.id <= 0L) return null
        val bmp = render(app, track) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(app.cacheDir, "plugin-share").apply { mkdirs() }
                val file = File(dir, "zmusic-share-${track.id}.png")
                FileOutputStream(file).use { out ->
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        error("compress")
                    }
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

    suspend fun render(app: ZMusicApplication, track: TrackRow): Bitmap? = withContext(Dispatchers.IO) {
        if (track.id <= 0L) return@withContext null
        coroutineScope {
            val session = app.sessionRepository.session.value
            val cookie = session?.cookie.orEmpty()
            val loggedIn = session != null && !session.isGuest
            val coverJob = async { loadUrlBitmap(app, track.coverUrl, 900) }
            val profileJob = async {
                if (loggedIn) app.libraryHomeRepository.peek().profile else null
            }
            val commentJob = async { pickHotComment(app, track.id, cookie) }
            val bgJob = async { loadShareBackground(app) }
            val cover = coverJob.await()
            val profile = profileJob.await()
            val comment = commentJob.await()
            val background = bgJob.await()
            val avatar = if (loggedIn) {
                loadUrlBitmap(app, profile?.avatarUrl, 240)
            } else {
                decodeResourceBitmap(app, R.drawable.ic_logo_vinyl_z, 240)
            }
            val commentAvatar = loadUrlBitmap(app, comment?.avatarUrl, 180)
            val ncmMark = drawableBitmap(app, R.drawable.ic_ncm_mark, 96)
            val zMark = decodeResourceBitmap(app, R.drawable.ic_logo_vinyl_z, 160)
                ?: drawableBitmap(app, R.mipmap.ic_launcher, 96)
            val ncmUrl = NcmShare.songPageUrl(track.id)
            if (ncmUrl == null) {
                cover?.recycleQuiet()
                avatar?.recycleQuiet()
                commentAvatar?.recycleQuiet()
                background?.recycleQuiet()
                ncmMark?.recycleQuiet()
                zMark?.recycleQuiet()
                return@coroutineScope null
            }
            val ncmQr = PlayerDisplayQr.encodeBitmap(ncmUrl, 360, ErrorCorrectionLevel.M)
            val zQr = PlayerDisplayQr.encodeBitmap(
                ZMusicSongLink.format(track.id),
                360,
                ErrorCorrectionLevel.M,
            )
            drawPoster(
                context = app,
                track = track,
                background = background,
                cover = cover,
                comment = comment,
                commentAvatar = commentAvatar,
                nickname = profile?.nickname?.ifBlank { null }
                    ?: session?.displayLabel?.ifBlank { null }
                    ?: "ZMusic",
                avatar = avatar,
                level = if (loggedIn) profile?.level else null,
                signature = profile?.signature?.trim().orEmpty(),
                ncmQr = ncmQr,
                zQr = zQr,
                ncmMark = ncmMark,
                zMark = zMark,
            )
        }
    }

    private suspend fun pickHotComment(
        app: ZMusicApplication,
        songId: Long,
        cookie: String,
    ): SongComment? {
        if (songId <= 0L) return null
        fun hottest(list: List<SongComment>): SongComment? {
            val pool = list.filter { it.content.isNotBlank() }
            return pool.maxByOrNull { it.likedCount } ?: pool.firstOrNull()
        }
        hottest(SongCommentsCache.get(songId, 2)?.comments.orEmpty())?.let { return it }
        val fresh = runCatching {
            app.commentsRepository.pageNew(
                songId = songId,
                cookie = cookie,
                pageNo = 1,
                pageSize = 20,
                sortType = 2,
                cursor = null,
            ).comments
        }.getOrNull().orEmpty()
        hottest(fresh)?.let { return it }
        val legacy = runCatching {
            app.commentsRepository.pageLegacy(
                songId = songId,
                cookie = cookie,
                limit = 20,
                offset = 0,
                before = null,
                includeHotFirst = true,
            ).comments
        }.getOrNull().orEmpty()
        return hottest(legacy)
    }

    private fun drawPoster(
        context: Context,
        track: TrackRow,
        background: Bitmap?,
        cover: Bitmap?,
        comment: SongComment?,
        commentAvatar: Bitmap?,
        nickname: String,
        avatar: Bitmap?,
        level: Int?,
        signature: String,
        ncmQr: Bitmap,
        zQr: Bitmap,
        ncmMark: Bitmap?,
        zMark: Bitmap?,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (background != null) {
            drawCoverFill(canvas, background)
        } else {
            canvas.drawColor(0xFFC9B8C4.toInt())
        }

        val cardW = W * 0.858f
        val cardH = H * 0.628f
        val cardLeft = (W - cardW) / 2f
        val cardTop = (H - cardH) * 0.30f
        val card = RectF(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)
        val paperPath = paperPath(card, 34f)

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Paper
            setShadowLayer(36f, 0f, 16f, 0x3A1A1018)
        }
        canvas.drawPath(paperPath, shadow)
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Paper }
        canvas.drawPath(paperPath, paperPaint)
        canvas.withClip(paperPath) {
            drawPaperGrain(this, card)
        }

        val pad = 42f
        val inner = RectF(card.left + pad, card.top + 36f, card.right - pad, card.bottom - 34f)
        val innerW = inner.width().toInt()
        val qrSize = 128f
        val qrBlockH = 40f + qrSize + 36f
        val userH = 82f
        val commentH = if (comment != null) 208f else 0f
        var songH = 352f
        val minGapUser = 26f
        val minGapSong = if (comment != null) 22f else 0f
        val minGapQr = 20f
        val split = if (comment != null) 3f else 2f
        val occupied = userH + songH + commentH + qrBlockH + minGapUser + minGapSong + minGapQr
        var extra = (inner.height() - occupied).coerceAtLeast(0f)
        val gapAdd = min(extra / split, 36f)
        extra -= gapAdd * split
        songH += extra
        val gapUserSong = minGapUser + gapAdd
        val gapSongComment = if (comment != null) minGapSong + gapAdd else 0f
        val gapCommentQr = minGapQr + gapAdd

        var y = inner.top
        drawUserRow(
            canvas = canvas,
            left = inner.left,
            top = y,
            width = innerW,
            height = userH,
            nickname = nickname,
            avatar = avatar,
            level = level,
            signature = signature,
        )
        y += userH + gapUserSong

        drawSongWell(
            canvas = canvas,
            bounds = RectF(inner.left, y, inner.right, y + songH),
            track = track,
            cover = cover,
        )
        y += songH + gapSongComment

        if (comment != null) {
            drawCommentCard(
                canvas = canvas,
                bounds = RectF(inner.left, y, inner.right, y + commentH),
                comment = comment,
                avatar = commentAvatar,
            )
            y += commentH + gapCommentQr
        } else {
            y += gapCommentQr
        }

        val qrTop = y
        val colW = innerW / 2f
        drawQrEntry(
            canvas = canvas,
            bounds = RectF(inner.left, qrTop, inner.left + colW - 10f, inner.bottom),
            mark = ncmMark,
            qr = ncmQr,
            title = "网易云音乐",
            subtitle = "发现好音乐",
            caption = "识别二维码  打开网易云音乐",
            circularMark = false,
        )
        val midX = inner.left + colW
        val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1A2C2620
            strokeWidth = 1.5f
        }
        canvas.drawLine(midX, qrTop + 8f, midX, inner.bottom - 8f, hair)
        drawQrEntry(
            canvas = canvas,
            bounds = RectF(inner.left + colW + 10f, qrTop, inner.right, inner.bottom),
            mark = zMark,
            qr = zQr,
            title = context.getString(R.string.app_name),
            subtitle = "遇见更多好音乐",
            caption = "识别二维码  打开 ZMusic",
            circularMark = true,
        )

        ncmQr.recycleQuiet()
        zQr.recycleQuiet()
        ncmMark?.recycleQuiet()
        zMark?.recycleQuiet()
        cover?.recycleQuiet()
        avatar?.recycleQuiet()
        commentAvatar?.recycleQuiet()
        background?.recycleQuiet()
        return bmp
    }

    private fun drawUserRow(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Int,
        height: Float,
        nickname: String,
        avatar: Bitmap?,
        level: Int?,
        signature: String,
    ) {
        val av = 74f
        val avRect = RectF(left, top + (height - av) / 2f, left + av, top + (height - av) / 2f + av)
        drawAvatar(canvas, avatar, avRect, ringColor = 0x22C4A06A)
        val lvText = if (level != null && level > 0) "Lv.$level" else null
        if (lvText != null) {
            val lvPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 16f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val lvW = lvPaint.measureText(lvText) + 16f
            val lvRect = RectF(
                avRect.right - lvW + 6f,
                avRect.bottom - 22f,
                avRect.right + 6f,
                avRect.bottom + 4f,
            )
            val lvBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Gold }
            canvas.drawRoundRect(lvRect, 11f, 11f, lvBg)
            canvas.drawText(lvText, lvRect.left + 8f, lvRect.bottom - 8f, lvPaint)
        }
        val textLeft = left + av + 18f
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Ink
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val nameMax = (width - av - 28f).toInt().coerceAtLeast(160)
        val nameLayout = staticLayout(nickname, namePaint, nameMax, 1)
        canvas.withTranslation(textLeft, avRect.top + 8f) {
            nameLayout.draw(this)
        }
        val sig = signature.ifBlank { "和喜欢的音乐不期而遇" }
        val sigPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Muted
            textSize = 22f
        }
        val sigLayout = staticLayout(sig, sigPaint, nameMax, 1)
        canvas.withTranslation(textLeft, avRect.top + 14f + nameLayout.height) {
            sigLayout.draw(this)
        }
    }

    private fun drawSongWell(
        canvas: Canvas,
        bounds: RectF,
        track: TrackRow,
        cover: Bitmap?,
    ) {
        val rr = 22f
        val palette = coverGradientColors(cover)
        val wellPath = Path().apply { addRoundRect(bounds, rr, rr, Path.Direction.CW) }
        val radius = hypot(bounds.width(), bounds.height())
        val well = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                bounds.left,
                bounds.top,
                radius.coerceAtLeast(1f),
                palette,
                floatArrayOf(0f, 0.28f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(bounds, rr, rr, well)
        val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                palette,
                floatArrayOf(0f, 0.28f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
            alpha = 140
        }
        canvas.drawRoundRect(bounds, rr, rr, wash)
        canvas.withClip(wellPath) {
            drawSongDecor(this, bounds)
        }

        val inset = 16f
        val titleReserve = 210f
        val maxCover = (bounds.width() - inset - titleReserve).coerceAtLeast(160f)
        val coverSize = (bounds.height() - inset * 2f).coerceAtMost(maxCover)
        val coverLeft = bounds.left + inset
        val coverTop = bounds.top + (bounds.height() - coverSize) / 2f
        val coverRect = RectF(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize)
        val vinylR = coverSize * 0.46f
        val peek = vinylR * 0.26f
        val vinylCx = coverRect.right - vinylR + peek
        val vinylCy = coverRect.centerY()
        canvas.withClip(wellPath) {
            drawVinyl(this, vinylCx, vinylCy, vinylR)
        }
        drawRoundedBitmap(canvas, cover, coverRect, 16f)

        val titleLeft = coverRect.right + peek + 16f
        val titleRight = bounds.right - 20f
        val titleW = (titleRight - titleLeft).toInt().coerceAtLeast(120)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF7F3EE.toInt()
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = -0.02f
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB8F7F3EE.toInt()
            textSize = 24f
        }
        val title = track.name.trim().ifEmpty { "未知歌曲" }
        val titleLayout = staticLayout(title, titlePaint, titleW, 2)
        val artist = track.artists.trim()
        val artistLayout = if (artist.isEmpty()) {
            null
        } else {
            staticLayout(artist, artistPaint, titleW, 1)
        }
        val blockH = titleLayout.height + if (artistLayout != null) 12f + artistLayout.height else 0f
        val textTop = bounds.centerY() - blockH / 2f
        canvas.withTranslation(titleLeft, textTop) {
            titleLayout.draw(this)
        }
        if (artistLayout != null) {
            val ruleY = textTop + titleLayout.height + 10f
            val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x33F7F3EE
                strokeWidth = 1.5f
            }
            canvas.drawLine(titleLeft, ruleY, min(titleLeft + 72f, titleRight), ruleY, rule)
            canvas.withTranslation(titleLeft, ruleY + 10f) {
                artistLayout.draw(this)
            }
        }
    }

    private fun drawSongDecor(canvas: Canvas, bounds: RectF) {
        val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x14FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        canvas.drawCircle(bounds.right - 38f, bounds.top + 36f, 11f, note)
        canvas.drawLine(
            bounds.right - 27f,
            bounds.top + 36f,
            bounds.right - 27f,
            bounds.top + 18f,
            note,
        )
        val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x18FFFFFF
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        val baseY = bounds.bottom - 28f
        val startX = bounds.right - 148f
        val bars = intArrayOf(8, 14, 22, 11, 26, 16, 9, 20, 12, 7)
        bars.forEachIndexed { i, h ->
            val x = startX + i * 10f
            canvas.drawLine(x, baseY, x, baseY - h, wave)
        }
    }

    private fun drawVinyl(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16151A.toInt() }
        canvas.drawCircle(cx, cy, radius, fill)
        val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2A2930.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
        }
        var r = radius * 0.92f
        while (r > radius * 0.38f) {
            canvas.drawCircle(cx, cy, r, groove)
            r -= 5.5f
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A322C.toInt() }
        canvas.drawCircle(cx, cy, radius * 0.26f, label)
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Gold
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        canvas.drawCircle(cx, cy, radius * 0.26f, gold)
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E0E10.toInt() }
        canvas.drawCircle(cx, cy, radius * 0.055f, hole)
    }

    private fun drawCommentCard(
        canvas: Canvas,
        bounds: RectF,
        comment: SongComment,
        avatar: Bitmap?,
    ) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4EEE6.toInt() }
        canvas.drawRoundRect(bounds, 18f, 18f, bg)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Ink
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerCy = bounds.top + 16f
        drawFlame(canvas, bounds.left + 16f, headerCy - 4f)
        canvas.drawText(
            "最赞评论",
            bounds.left + 36f,
            baselineForCenter(titlePaint, headerCy),
            titlePaint,
        )

        val likeText = formatLiked(comment.likedCount)
        val likePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Like
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        if (comment.likedCount > 0) {
            val likeW = likePaint.measureText(likeText)
            val likeRight = bounds.right - 16f
            canvas.drawText(likeText, likeRight, baselineForCenter(likePaint, headerCy), likePaint)
            val thumbW = 18f
            val thumbGap = 12f
            drawThumb(canvas, likeRight - likeW - thumbGap - thumbW, headerCy - 10.5f)
        }

        val hasTime = comment.timeLabel.isNotBlank()
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Muted
            textSize = 18f
        }
        val timeH = if (hasTime) 22f else 0f
        val av = 46f
        val contentTop = bounds.top + 36f
        val avRect = RectF(
            bounds.left + 16f,
            contentTop,
            bounds.left + 16f + av,
            contentTop + av,
        )
        drawAvatar(canvas, avatar, avRect, ringColor = 0x00000000)
        val textLeft = avRect.right + 14f
        val textW = (bounds.right - 18f - textLeft).toInt().coerceAtLeast(120)
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Ink
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val name = comment.nickname.ifBlank { "网易云用户" }
        val nameLayout = staticLayout(name, namePaint, textW, 1)
        canvas.withTranslation(textLeft, contentTop) {
            nameLayout.draw(this)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5C564E.toInt()
            textSize = 22f
        }
        val bodyTop = contentTop + nameLayout.height + 6f
        val timeReserve = if (hasTime) timeH + 8f else 0f
        val lineH = bodyPaint.textSize + 8f
        var maxLines = ((bounds.bottom - 12f - timeReserve - bodyTop) / lineH).toInt().coerceIn(1, 4)
        val body = comment.content.trim().replace(Regex("\\s+"), " ")
        var bodyLayout = staticLayout(body, bodyPaint, textW, maxLines)
        while (maxLines > 1 && bodyTop + bodyLayout.height + timeReserve > bounds.bottom - 12f) {
            maxLines--
            bodyLayout = staticLayout(body, bodyPaint, textW, maxLines)
        }
        canvas.withTranslation(textLeft, bodyTop) {
            bodyLayout.draw(this)
        }
        if (hasTime) {
            val timeY = bodyTop + bodyLayout.height + 8f +
                (-timePaint.fontMetrics.ascent)
            val timeMaxY = bounds.bottom - 12f
            canvas.drawText(
                comment.timeLabel,
                textLeft,
                min(timeY, timeMaxY),
                timePaint,
            )
        }
    }

    private fun baselineForCenter(paint: TextPaint, centerY: Float): Float {
        val fm = paint.fontMetrics
        return centerY - (fm.ascent + fm.descent) / 2f
    }

    private fun drawQrEntry(
        canvas: Canvas,
        bounds: RectF,
        mark: Bitmap?,
        qr: Bitmap,
        title: String,
        subtitle: String,
        caption: String,
        circularMark: Boolean,
    ) {
        val markSize = 32f
        val markRect = RectF(
            bounds.centerX() - 70f,
            bounds.top,
            bounds.centerX() - 70f + markSize,
            bounds.top + markSize,
        )
        if (mark != null) {
            if (circularMark) {
                val path = Path().apply { addOval(markRect, Path.Direction.CW) }
                canvas.withClip(path) {
                    drawBitmap(mark, null, markRect, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            } else {
                canvas.drawBitmap(mark, null, markRect, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Ink
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Muted
            textSize = 15f
        }
        canvas.drawText(title, markRect.right + 8f, bounds.top + 14f, titlePaint)
        canvas.drawText(subtitle, markRect.right + 8f, bounds.top + 30f, subPaint)

        val qrSize = 128f
        val qrLeft = bounds.centerX() - qrSize / 2f
        val qrTop = bounds.top + 42f
        val pad = 8f
        val plate = RectF(qrLeft - pad, qrTop - pad, qrLeft + qrSize + pad, qrTop + qrSize + pad)
        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        canvas.drawRoundRect(plate, 10f, 10f, platePaint)
        canvas.drawBitmap(
            qr,
            null,
            RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        val capPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Muted
            textSize = 15f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(caption, bounds.centerX(), qrTop + qrSize + 22f, capPaint)
    }

    private fun drawFlame(canvas: Canvas, x: Float, y: Float) {
        val p = Path().apply {
            moveTo(x + 7f, y + 14f)
            cubicTo(x - 2f, y + 8f, x + 1f, y + 1f, x + 7f, y - 6f)
            cubicTo(x + 9f, y + 1f, x + 16f, y + 6f, x + 7f, y + 14f)
            close()
        }
        canvas.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Like })
    }

    private fun drawThumb(canvas: Canvas, x: Float, y: Float) {
        val p = Path().apply {
            addRoundRect(RectF(x, y + 8f, x + 7f, y + 18f), 2f, 2f, Path.Direction.CW)
            moveTo(x + 8f, y + 9f)
            lineTo(x + 8f, y + 18f)
            lineTo(x + 18f, y + 18f)
            lineTo(x + 16.5f, y + 9f)
            lineTo(x + 12f, y + 9f)
            lineTo(x + 13.5f, y + 3f)
            lineTo(x + 10.5f, y + 3f)
            lineTo(x + 9.2f, y + 9f)
            close()
        }
        canvas.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Like })
    }

    private fun paperPath(r: RectF, radius: Float): Path {
        val tearW = 58f
        val tearH = 46f
        return Path().apply {
            moveTo(r.left + radius, r.top)
            lineTo(r.right - tearW - 4f, r.top)
            lineTo(r.right - tearW + 10f, r.top + 11f)
            lineTo(r.right - tearW + 22f, r.top + 5f)
            lineTo(r.right - 20f, r.top + tearH * 0.52f)
            lineTo(r.right - 6f, r.top + 16f)
            lineTo(r.right, r.top + tearH)
            lineTo(r.right, r.bottom - radius)
            arcTo(RectF(r.right - radius * 2, r.bottom - radius * 2, r.right, r.bottom), 0f, 90f, false)
            lineTo(r.left + radius, r.bottom)
            arcTo(RectF(r.left, r.bottom - radius * 2, r.left + radius * 2, r.bottom), 90f, 90f, false)
            lineTo(r.left, r.top + radius)
            arcTo(RectF(r.left, r.top, r.left + radius * 2, r.top + radius * 2), 180f, 90f, false)
            close()
        }
    }

    private fun drawPaperGrain(canvas: Canvas, card: RectF) {
        val grain = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val rnd = java.util.Random(24)
        for (i in 0 until 420) {
            val px = rnd.nextInt(96)
            val py = rnd.nextInt(96)
            val a = 10 + rnd.nextInt(14)
            grain.setPixel(px, py, (a shl 24) or 0x5A4030)
        }
        val paint = Paint().apply {
            shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            alpha = 70
        }
        canvas.drawRect(card, paint)
        grain.recycleQuiet()
    }

    private fun drawCoverFill(canvas: Canvas, srcBmp: Bitmap) {
        val src = Rect(0, 0, srcBmp.width, srcBmp.height)
        val srcW = srcBmp.width
        val srcH = srcBmp.height
        if (srcW > 0 && srcH > 0) {
            val targetRatio = W.toFloat() / H
            val srcRatio = srcW.toFloat() / srcH
            if (srcRatio > targetRatio) {
                val newW = (srcH * targetRatio).toInt().coerceAtLeast(1)
                val left = (srcW - newW) / 2
                src.set(left, 0, left + newW, srcH)
            } else {
                val newH = (srcW / targetRatio).toInt().coerceAtLeast(1)
                val top = (srcH - newH) / 2
                src.set(0, top, srcW, top + newH)
            }
        }
        canvas.drawBitmap(srcBmp, src, Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** 铺满 1080×1920，按主页壁纸的缩放/平移裁切，不留边。 */
    private fun drawWallpaperCover(canvas: Canvas, srcBmp: Bitmap, frame: WallpaperFrame) {
        val imgW = srcBmp.width
        val imgH = srcBmp.height
        if (imgW <= 0 || imgH <= 0) {
            drawCoverFill(canvas, srcBmp)
            return
        }
        val viewW = W.toFloat()
        val viewH = H.toFloat()
        val contain = min(viewW / imgW, viewH / imgH)
        val cover = max(viewW / imgW, viewH / imgH)
        val userScale = frame.scale.coerceIn(
            ChromeWallpaperStore.SCALE_MIN,
            ChromeWallpaperStore.SCALE_MAX,
        )
        val drawnScale = max(cover, contain * userScale)
        val drawnW = imgW * drawnScale
        val drawnH = imgH * drawnScale
        val left = (viewW - drawnW) * frame.offsetX.coerceIn(0f, 1f)
        val top = (viewH - drawnH) * frame.offsetY.coerceIn(0f, 1f)
        canvas.drawBitmap(
            srcBmp,
            null,
            RectF(left, top, left + drawnW, top + drawnH),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
    }

    private fun drawRoundedBitmap(canvas: Canvas, bmp: Bitmap?, rect: RectF, radius: Float) {
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44000000
            setShadowLayer(10f, 0f, 4f, 0x55000000)
        }
        canvas.drawRoundRect(rect, radius, radius, shadow)
        canvas.withClip(path) {
            if (bmp != null) {
                drawBitmap(bmp, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                drawColor(0xFF2A2A30.toInt())
            }
        }
    }

    private fun drawAvatar(canvas: Canvas, avatar: Bitmap?, rect: RectF, ringColor: Int) {
        if (ringColor != 0) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ringColor
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawOval(rect, ring)
        }
        val inner = RectF(rect.left + 1.5f, rect.top + 1.5f, rect.right - 1.5f, rect.bottom - 1.5f)
        val path = Path().apply { addOval(inner, Path.Direction.CW) }
        canvas.withClip(path) {
            if (avatar != null) {
                drawBitmap(avatar, null, inner, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                drawColor(0xFFE8DFD4.toInt())
            }
        }
    }

    private fun staticLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setLineSpacing(3f, 1f)
            .build()
    }

    private fun coverGradientColors(cover: Bitmap?): IntArray {
        val fallback = intArrayOf(
            SongWellHi,
            SongWell,
            0xFF322E38.toInt(),
            0xFF241F28.toInt(),
        )
        if (cover == null || cover.isRecycled || cover.width < 4 || cover.height < 4) {
            return fallback
        }
        val scaled = runCatching {
            Bitmap.createScaledBitmap(cover, 48, 48, true)
        }.getOrNull() ?: return fallback
        return try {
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            val buckets = LinkedHashMap<Int, LongArray>()
            for (c in pixels) {
                if (((c ushr 24) and 0xFF) < 140) continue
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                if (mx < 28) continue
                if (mn > 236 && mx - mn < 16) continue
                val key = (r shr 5 shl 10) or (g shr 5 shl 5) or (b shr 5)
                val acc = buckets.getOrPut(key) { longArrayOf(0L, 0L, 0L, 0L) }
                acc[0] += 1L
                acc[1] += r.toLong()
                acc[2] += g.toLong()
                acc[3] += b.toLong()
            }
            val hsv = FloatArray(3)
            val ranked = buckets.values.mapNotNull { acc ->
                val n = acc[0]
                if (n < 3L) return@mapNotNull null
                val color = Color.rgb(
                    (acc[1] / n).toInt(),
                    (acc[2] / n).toInt(),
                    (acc[3] / n).toInt(),
                )
                Color.colorToHSV(color, hsv)
                Triple(color, n, hsv[0] to (hsv[1] * hsv[2]))
            }.sortedByDescending { it.second * (0.35 + it.third.second) }
            val picked = ArrayList<Int>(4)
            for (item in ranked) {
                Color.colorToHSV(item.first, hsv)
                val hue = hsv[0]
                val far = picked.all { existing ->
                    Color.colorToHSV(existing, hsv)
                    hueDistance(hue, hsv[0]) > 18f ||
                        kotlin.math.abs(item.third.second - hsv[1] * hsv[2]) > 0.22f
                }
                if (picked.isEmpty() || far) picked.add(item.first)
                if (picked.size == 4) break
            }
            if (picked.isEmpty()) return fallback
            while (picked.size < 4) {
                Color.colorToHSV(picked.last(), hsv)
                hsv[0] = (hsv[0] + 22f) % 360f
                hsv[2] = (hsv[2] * 0.82f).coerceIn(0.16f, 1f)
                picked.add(Color.HSVToColor(hsv))
            }
            val darks = floatArrayOf(0.54f, 0.42f, 0.32f, 0.22f)
            IntArray(4) { i -> toneForWell(picked[i], darks[i]) }
        } finally {
            if (scaled !== cover && !scaled.isRecycled) scaled.recycle()
        }
    }

    private fun toneForWell(color: Int, value: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 1.12f).coerceAtMost(1f)
        hsv[2] = value.coerceIn(0.16f, 0.58f)
        return Color.HSVToColor(hsv)
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b)
        return min(d, 360f - d)
    }

    private fun formatLiked(count: Int): String {
        return if (count >= 10_000) {
            val wan = count / 10000f
            if (wan >= 10f) {
                "${wan.toInt()}万"
            } else {
                String.format(Locale.CHINA, "%.1f万", wan)
            }
        } else {
            count.toString()
        }
    }

    private suspend fun loadUrlBitmap(context: Context, url: String?, minSide: Int): Bitmap? {
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

    private fun drawableBitmap(context: Context, resId: Int, sizePx: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return null
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bmp
    }

    private fun loadShareBackground(app: ZMusicApplication): Bitmap? {
        val frame = PluginLookPresent.wallpaper(app.chromeWallpaperStore.current())
            .frame(ChromeWallpaperSurface.Home, landscape = false)
        if (frame != null && frame.imagePath.isNotBlank() && File(frame.imagePath).isFile) {
            val custom = decodeFileBitmap(frame.imagePath, 1920)
            if (custom != null) {
                val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                drawWallpaperCover(Canvas(out), custom, frame)
                custom.recycleQuiet()
                return out
            }
        }
        val src = decodeResourceBitmap(app, R.drawable.share_bg, 1920) ?: return null
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        drawCoverFill(Canvas(out), src)
        src.recycleQuiet()
        return out
    }

    private fun decodeFileBitmap(path: String, minSidePx: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val shortest = minOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            while (shortest / (sample * 2) >= minSidePx * 2) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()
    }

    private fun decodeResourceBitmap(context: Context, resId: Int, minSidePx: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, bounds)
            var sample = 1
            val shortest = minOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            while (shortest / (sample * 2) >= minSidePx * 2) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            BitmapFactory.decodeResource(context.resources, resId, opts)
        }.getOrNull()
    }

    private fun Bitmap.recycleQuiet() {
        if (!isRecycled) runCatching { recycle() }
    }
}
