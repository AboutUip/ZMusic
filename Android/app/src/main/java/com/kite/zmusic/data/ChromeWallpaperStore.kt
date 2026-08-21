package com.kite.zmusic.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class ChromeWallpaperSurface(
    val title: String,
    val caption: String,
) {
    Home("主页", "默认铺这一页"),
    Features("功能", "听歌模式和入口"),
    Profile("个人", "默认不铺；勾选后不再用用户空间图"),
    Search("搜索", "搜歌曲、歌单和歌手"),
    Settings("设置", "设置根页和子页；自定义背景编辑页除外"),
    Playlist("歌单", "歌单、日推、漫游列表和榜"),
    Album("专辑", "专辑详情"),
    Artist("歌手", "歌手页和收藏的歌手"),
}

data class WallpaperFrame(
    val imagePath: String = "",
    val offsetX: Float = 0.5f,
    val offsetY: Float = 0.5f,
    val scale: Float = 1f,
    val locked: Boolean = false,
) {
    val hasImage: Boolean get() = imagePath.isNotBlank()
}

data class ChromeWallpaperState(
    val enabled: Boolean = false,
    val coverage: Set<ChromeWallpaperSurface> = ChromeWallpaperStore.DEFAULT_COVERAGE,
    val itemChrome: ChromeGlassMode = ChromeGlassMode.Solid,
    val genericPortrait: WallpaperFrame = WallpaperFrame(),
    val genericLandscape: WallpaperFrame = WallpaperFrame(),
    val portraits: Map<ChromeWallpaperSurface, WallpaperFrame> = emptyMap(),
    val landscapes: Map<ChromeWallpaperSurface, WallpaperFrame> = emptyMap(),
) {
    fun frame(surface: ChromeWallpaperSurface?, landscape: Boolean): WallpaperFrame? {
        if (!enabled || surface == null) return null
        if (surface !in coverage) return null
        val specific = if (landscape) landscapes[surface] else portraits[surface]
        if (specific?.hasImage == true) return specific
        val generic = if (landscape) genericLandscape else genericPortrait
        return generic.takeIf { it.hasImage }
    }

    fun editorFrame(surface: ChromeWallpaperSurface?, landscape: Boolean): WallpaperFrame {
        if (surface == null) {
            return if (landscape) genericLandscape else genericPortrait
        }
        val map = if (landscape) landscapes else portraits
        return map[surface] ?: WallpaperFrame()
    }

    fun resolvedEditorFrame(surface: ChromeWallpaperSurface?, landscape: Boolean): WallpaperFrame {
        val own = editorFrame(surface, landscape)
        if (own.hasImage || surface == null) return own
        val generic = if (landscape) genericLandscape else genericPortrait
        return generic.takeIf { it.hasImage } ?: own
    }

    fun editorInherited(surface: ChromeWallpaperSurface?, landscape: Boolean): Boolean {
        if (surface == null) return false
        return !editorFrame(surface, landscape).hasImage &&
            (if (landscape) genericLandscape else genericPortrait).hasImage
    }

    val settingsSubtitle: String
        get() {
            if (!enabled) return "已关闭 · 各页用主题底色"
            val n = coverage.size
            return if (n == 1 && ChromeWallpaperSurface.Home in coverage) {
                "已启用 · 目前只铺主页"
            } else {
                "已启用 · ${n} 处页面"
            }
        }
}

/**
 * 主界面自定义背景（不含弹窗、通知、播放页）。
 * 浅色 / 深色只改字色和卡片，不和这套图互斥。
 */
class ChromeWallpaperStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir = File(app.filesDir, DIR).also { it.mkdirs() }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ChromeWallpaperState> = _state.asStateFlow()

    fun current(): ChromeWallpaperState = _state.value

    fun helpSeen(): Boolean = prefs.getBoolean(KEY_HELP_SEEN, false)

    fun markHelpSeen() {
        prefs.edit().putBoolean(KEY_HELP_SEEN, true).apply()
    }

    fun setEnabled(enabled: Boolean) {
        commit(_state.value.copy(enabled = enabled))
    }

    fun setItemChrome(mode: ChromeGlassMode) {
        commit(_state.value.copy(itemChrome = mode))
    }

    fun setCoverage(coverage: Set<ChromeWallpaperSurface>) {
        commit(_state.value.copy(coverage = coverage))
    }

    fun toggleCoverage(surface: ChromeWallpaperSurface) {
        val cur = _state.value.coverage
        setCoverage(
            if (surface in cur) cur - surface else cur + surface,
        )
    }

    fun setEditorFrame(
        surface: ChromeWallpaperSurface?,
        landscape: Boolean,
        frame: WallpaperFrame,
    ) {
        val s = _state.value
        commit(
            if (surface == null) {
                if (landscape) s.copy(genericLandscape = frame) else s.copy(genericPortrait = frame)
            } else if (landscape) {
                s.copy(landscapes = s.landscapes + (surface to frame))
            } else {
                s.copy(portraits = s.portraits + (surface to frame))
            },
        )
    }

    suspend fun import(
        surface: ChromeWallpaperSurface?,
        landscape: Boolean,
        uri: Uri,
    ): WallpaperFrame? = withContext(Dispatchers.IO) {
        val key = slotFileKey(surface, landscape)
        runCatching {
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            val bmp = decodeSampled(bytes, MAX_PX) ?: return@runCatching null
            val imgW = bmp.width
            val imgH = bmp.height
            dir.mkdirs()
            val out = File(dir, "${key}_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { stream ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            }
            if (!bmp.isRecycled) bmp.recycle()
            if (!out.isFile || out.length() <= 0L) {
                runCatching { out.delete() }
                return@runCatching null
            }
            clearFiles(key, keep = out)
            WallpaperFrame(
                imagePath = out.absolutePath,
                offsetX = 0.5f,
                offsetY = 0.5f,
                scale = defaultCanvasScale(imgW, imgH, landscape),
                locked = false,
            )
        }.onFailure {
            Log.w(TAG, "import chrome wallpaper failed")
        }.getOrNull()?.also { imported ->
            setEditorFrame(surface, landscape, imported)
        }
    }

    private fun defaultCanvasScale(imgW: Int, imgH: Int, landscape: Boolean): Float {
        val dm = app.resources.displayMetrics
        val sw = dm.widthPixels.toFloat().coerceAtLeast(1f)
        val sh = dm.heightPixels.toFloat().coerceAtLeast(1f)
        val short = kotlin.math.min(sw, sh)
        val long = kotlin.math.max(sw, sh)
        val vw = if (landscape) long else short
        val vh = if (landscape) short else long
        return ChromeWallpaperStore.defaultCanvasScale(imgW, imgH, vw, vh)
    }

    fun clearEditorFrame(surface: ChromeWallpaperSurface?, landscape: Boolean) {
        clearFiles(slotFileKey(surface, landscape), keep = null)
        setEditorFrame(surface, landscape, WallpaperFrame())
    }

    private fun commit(next: ChromeWallpaperState) {
        _state.value = next
        persist(next)
    }

    private fun persist(state: ChromeWallpaperState) {
        val e = prefs.edit()
            .putBoolean(KEY_ENABLED, state.enabled)
            .putString(KEY_ITEM_CHROME, state.itemChrome.name)
            .putString(KEY_COVERAGE, state.coverage.joinToString(",") { it.name })
            .putString(KEY_GENERIC_P, encode(state.genericPortrait))
            .putString(KEY_GENERIC_L, encode(state.genericLandscape))
        ChromeWallpaperSurface.entries.forEach { surface ->
            e.putString(frameKey(surface, false), encode(state.portraits[surface]))
            e.putString(frameKey(surface, true), encode(state.landscapes[surface]))
        }
        e.apply()
    }

    private fun load(): ChromeWallpaperState {
        val coverageRaw = prefs.getString(KEY_COVERAGE, null)
        val parsed = coverageRaw
            ?.split(',')
            ?.mapNotNull { name -> ChromeWallpaperSurface.entries.find { it.name == name } }
            ?.toSet()
            .orEmpty()
        val coverage = when {
            coverageRaw == null -> DEFAULT_COVERAGE
            parsed == LEGACY_DEFAULT_COVERAGE -> DEFAULT_COVERAGE
            else -> parsed
        }
        val portraits = mutableMapOf<ChromeWallpaperSurface, WallpaperFrame>()
        val landscapes = mutableMapOf<ChromeWallpaperSurface, WallpaperFrame>()
        ChromeWallpaperSurface.entries.forEach { surface ->
            decode(prefs.getString(frameKey(surface, false), null))?.let { portraits[surface] = it }
            decode(prefs.getString(frameKey(surface, true), null))?.let { landscapes[surface] = it }
        }
        return ChromeWallpaperState(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            coverage = coverage,
            itemChrome = ChromeGlassMode.fromStored(prefs.getString(KEY_ITEM_CHROME, null)).let { stored ->
                if (prefs.contains(KEY_ITEM_CHROME)) stored else ChromeGlassMode.Solid
            },
            genericPortrait = decode(prefs.getString(KEY_GENERIC_P, null)) ?: WallpaperFrame(),
            genericLandscape = decode(prefs.getString(KEY_GENERIC_L, null)) ?: WallpaperFrame(),
            portraits = portraits,
            landscapes = landscapes,
        )
    }

    private fun clearFiles(key: String, keep: File?) {
        val prefix = "${key}_"
        dir.listFiles()?.forEach { file ->
            if (keep != null && file.absolutePath == keep.absolutePath) return@forEach
            if (file.name.startsWith(prefix)) {
                runCatching { file.delete() }
            }
        }
    }

    companion object {
        private const val TAG = "ChromeWallpaper"
        private const val PREFS = "zmusic_chrome_wallpaper"
        private const val DIR = "chrome_wallpaper"
        private const val MAX_PX = 2048
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ITEM_CHROME = "item_chrome"
        private const val KEY_COVERAGE = "coverage"
        private const val KEY_GENERIC_P = "generic_p"
        private const val KEY_GENERIC_L = "generic_l"
        private const val KEY_HELP_SEEN = "help_seen"

        val DEFAULT_COVERAGE: Set<ChromeWallpaperSurface> =
            setOf(ChromeWallpaperSurface.Home)

        private val LEGACY_DEFAULT_COVERAGE: Set<ChromeWallpaperSurface> =
            ChromeWallpaperSurface.entries.toSet() - ChromeWallpaperSurface.Profile

        const val OFFSET_MIN = 0f
        const val OFFSET_MAX = 1f
        /** 相对 contain：1 = 整张图刚好进画布，可更小留边，也可放大。 */
        const val SCALE_MIN = 0.2f
        const val SCALE_MAX = 8f
        private const val SCALE_COVER_BOOST = 1.5f

        fun defaultCanvasScale(imgW: Int, imgH: Int, viewW: Float, viewH: Float): Float {
            if (imgW <= 0 || imgH <= 0 || viewW < 1f || viewH < 1f) {
                return SCALE_COVER_BOOST.coerceIn(SCALE_MIN, SCALE_MAX)
            }
            val fit = kotlin.math.min(viewW / imgW.toFloat(), viewH / imgH.toFloat())
            val cover = kotlin.math.max(viewW / imgW.toFloat(), viewH / imgH.toFloat())
            if (fit < 1e-6f) return SCALE_COVER_BOOST.coerceIn(SCALE_MIN, SCALE_MAX)
            return (cover / fit * SCALE_COVER_BOOST).coerceIn(SCALE_MIN, SCALE_MAX)
        }

        private fun frameKey(surface: ChromeWallpaperSurface, landscape: Boolean) =
            "f_${surface.name}_${if (landscape) "l" else "p"}"

        private fun slotFileKey(surface: ChromeWallpaperSurface?, landscape: Boolean): String {
            val side = if (landscape) "l" else "p"
            return if (surface == null) "g_$side" else "s_${surface.name}_$side"
        }

        private fun encode(frame: WallpaperFrame?): String {
            if (frame == null || !frame.hasImage) return ""
            return JSONObject()
                .put("path", frame.imagePath)
                .put("ox", frame.offsetX.toDouble())
                .put("oy", frame.offsetY.toDouble())
                .put("scale", frame.scale.toDouble())
                .put("locked", frame.locked)
                .toString()
        }

        private fun decode(raw: String?): WallpaperFrame? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                val path = o.optString("path", "").trim()
                if (path.isEmpty() || !File(path).isFile) return@runCatching null
                WallpaperFrame(
                    imagePath = path,
                    offsetX = o.optDouble("ox", 0.5).toFloat().coerceIn(OFFSET_MIN, OFFSET_MAX),
                    offsetY = o.optDouble("oy", 0.5).toFloat().coerceIn(OFFSET_MIN, OFFSET_MAX),
                    scale = o.optDouble("scale", 1.0).toFloat().coerceIn(SCALE_MIN, SCALE_MAX),
                    locked = o.optBoolean("locked", false),
                )
            }.getOrNull()
        }

        private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (w / sample > maxPx || h / sample > maxPx) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }
}
