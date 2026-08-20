package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知栏歌词悬浮窗外观与开关。颜色用 ARGB，不引用 Compose Color。
 */
data class LyricOverlayPrefs(
    /** 通知栏「歌词显示」开关；仅在应用外真正展示。 */
    val enabled: Boolean = false,
    val locked: Boolean = false,
    /** 当前行之前已播行数，0 表示不显示已播。 */
    val playedLines: Int = 1,
    /** 当前行之后未播行数，0 表示不显示未播。 */
    val upcomingLines: Int = 1,
    val windowBackground: Boolean = true,
    /** 悬浮窗背景开启时的窗内磨砂强度（px 档）。不使用系统 FLAG_BLUR_BEHIND。 */
    val blurRadiusPx: Int = BLUR_DEFAULT,
    val lyricBackground: Boolean = false,
    val playedColorArgb: Int = 0x99FFFFFF.toInt(),
    val currentColorArgb: Int = 0xFFFFFFFF.toInt(),
    val upcomingColorArgb: Int = 0x66FFFFFF.toInt(),
    val fontSizeSp: Float = 16f,
    val dynamicWidth: Boolean = true,
    val widthDp: Int = 280,
    /** true：侵入状态栏 / 刘海，便于横屏真正居中。 */
    val ignoreCutout: Boolean = false,
    /** 歌词在窗内的水平对齐：0 左 / 1 中 / 2 右。 */
    val textAlign: Int = ALIGN_LEFT,
    val posX: Int = UNSET,
    val posY: Int = UNSET,
    val posRefW: Int = 0,
    val posRefH: Int = 0,
) {
    val lineCount: Int get() = playedLines + 1 + upcomingLines

    companion object {
        const val UNSET = Int.MIN_VALUE
        const val LINES_MIN = 0
        const val LINES_MAX = 6
        const val FONT_MIN = 12f
        const val FONT_MAX = 28f
        const val WIDTH_MIN_DP = 160
        const val WIDTH_MAX_DP = 420
        const val BLUR_MIN = 0
        const val BLUR_MAX = 40
        const val BLUR_DEFAULT = 16
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2
    }
}

class LyricOverlayStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(load())
    val prefsFlow: StateFlow<LyricOverlayPrefs> = _prefs.asStateFlow()

    fun current(): LyricOverlayPrefs = _prefs.value

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setLocked(locked: Boolean) = update { it.copy(locked = locked) }

    fun update(block: (LyricOverlayPrefs) -> LyricOverlayPrefs) {
        val next = sanitize(block(_prefs.value))
        if (next == _prefs.value) return
        persist(next)
        _prefs.value = next
    }

    private fun load(): LyricOverlayPrefs = sanitize(
        LyricOverlayPrefs(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            locked = prefs.getBoolean(KEY_LOCKED, false),
            playedLines = prefs.getInt(KEY_PLAYED, 1),
            upcomingLines = prefs.getInt(KEY_UPCOMING, 1),
            windowBackground = prefs.getBoolean(KEY_WINDOW_BG, true),
            blurRadiusPx = prefs.getInt(KEY_BLUR, LyricOverlayPrefs.BLUR_DEFAULT),
            lyricBackground = prefs.getBoolean(KEY_LYRIC_BG, false),
            playedColorArgb = prefs.getInt(KEY_COLOR_PLAYED, 0x99FFFFFF.toInt()),
            currentColorArgb = prefs.getInt(KEY_COLOR_CURRENT, 0xFFFFFFFF.toInt()),
            upcomingColorArgb = prefs.getInt(KEY_COLOR_UPCOMING, 0x66FFFFFF.toInt()),
            fontSizeSp = prefs.getFloat(KEY_FONT, 16f),
            dynamicWidth = prefs.getBoolean(KEY_DYNAMIC_W, true),
            widthDp = prefs.getInt(KEY_WIDTH, 280),
            ignoreCutout = prefs.getBoolean(KEY_CUTOUT, false),
            textAlign = prefs.getInt(KEY_ALIGN, LyricOverlayPrefs.ALIGN_LEFT),
            posX = prefs.getInt(KEY_X, LyricOverlayPrefs.UNSET),
            posY = prefs.getInt(KEY_Y, LyricOverlayPrefs.UNSET),
            posRefW = prefs.getInt(KEY_REF_W, 0),
            posRefH = prefs.getInt(KEY_REF_H, 0),
        ),
    )

    private fun persist(p: LyricOverlayPrefs) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, p.enabled)
            .putBoolean(KEY_LOCKED, p.locked)
            .putInt(KEY_PLAYED, p.playedLines)
            .putInt(KEY_UPCOMING, p.upcomingLines)
            .putBoolean(KEY_WINDOW_BG, p.windowBackground)
            .putInt(KEY_BLUR, p.blurRadiusPx)
            .putBoolean(KEY_LYRIC_BG, p.lyricBackground)
            .putInt(KEY_COLOR_PLAYED, p.playedColorArgb)
            .putInt(KEY_COLOR_CURRENT, p.currentColorArgb)
            .putInt(KEY_COLOR_UPCOMING, p.upcomingColorArgb)
            .putFloat(KEY_FONT, p.fontSizeSp)
            .putBoolean(KEY_DYNAMIC_W, p.dynamicWidth)
            .putInt(KEY_WIDTH, p.widthDp)
            .putBoolean(KEY_CUTOUT, p.ignoreCutout)
            .putInt(KEY_ALIGN, p.textAlign)
            .putInt(KEY_X, p.posX)
            .putInt(KEY_Y, p.posY)
            .putInt(KEY_REF_W, p.posRefW)
            .putInt(KEY_REF_H, p.posRefH)
            .apply()
    }

    private fun sanitize(p: LyricOverlayPrefs): LyricOverlayPrefs = p.copy(
        playedLines = p.playedLines.coerceIn(LyricOverlayPrefs.LINES_MIN, LyricOverlayPrefs.LINES_MAX),
        upcomingLines = p.upcomingLines.coerceIn(LyricOverlayPrefs.LINES_MIN, LyricOverlayPrefs.LINES_MAX),
        fontSizeSp = p.fontSizeSp.coerceIn(LyricOverlayPrefs.FONT_MIN, LyricOverlayPrefs.FONT_MAX),
        widthDp = p.widthDp.coerceIn(LyricOverlayPrefs.WIDTH_MIN_DP, LyricOverlayPrefs.WIDTH_MAX_DP),
        blurRadiusPx = p.blurRadiusPx.coerceIn(LyricOverlayPrefs.BLUR_MIN, LyricOverlayPrefs.BLUR_MAX),
        textAlign = p.textAlign.coerceIn(LyricOverlayPrefs.ALIGN_LEFT, LyricOverlayPrefs.ALIGN_RIGHT),
    )

    companion object {
        private const val PREFS = "zmusic_lyric_overlay"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LOCKED = "locked"
        private const val KEY_PLAYED = "played_lines"
        private const val KEY_UPCOMING = "upcoming_lines"
        private const val KEY_WINDOW_BG = "window_bg"
        private const val KEY_BLUR = "blur_px"
        private const val KEY_LYRIC_BG = "lyric_bg"
        private const val KEY_COLOR_PLAYED = "color_played"
        private const val KEY_COLOR_CURRENT = "color_current"
        private const val KEY_COLOR_UPCOMING = "color_upcoming"
        private const val KEY_FONT = "font_sp"
        private const val KEY_DYNAMIC_W = "dynamic_w"
        private const val KEY_WIDTH = "width_dp"
        private const val KEY_CUTOUT = "ignore_cutout"
        private const val KEY_ALIGN = "text_align"
        private const val KEY_X = "pos_x"
        private const val KEY_Y = "pos_y"
        private const val KEY_REF_W = "pos_ref_w"
        private const val KEY_REF_H = "pos_ref_h"
    }
}
