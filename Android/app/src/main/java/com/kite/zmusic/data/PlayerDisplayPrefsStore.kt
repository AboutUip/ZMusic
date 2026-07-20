package com.kite.zmusic.data

import android.content.Context
import android.content.SharedPreferences

/** 横屏歌名信息块水平对齐方式。 */
enum class TitleAlignMode {
    /** 对齐底部播放条左缘 */
    LEFT,
    /** 对齐动态黑胶中心 */
    VINYL,
    /** 屏幕水平居中 */
    CENTER,
    /** 对齐动态歌词中心 */
    LYRICS,
    ;

    companion object {
        fun fromOrdinal(v: Int): TitleAlignMode =
            entries.getOrElse(v) { VINYL }
    }
}

/** 全屏播放页显示偏好（雨夜 / 字号 / UI 缩放 / 黑胶位置等），客户端持久化。 */
data class PlayerDisplayPrefs(
    val rainNightEnabled: Boolean = true,
    /** 歌词字号倍率：0.75 .. 1.50 */
    val fontScale: Float = 1f,
    /** 歌词行间距（单侧 padding，dp）：0 .. 28；计入行槽高度 */
    val lyricLineSpacingDp: Float = 10f,
    /** 播放行上方展示的「已播放」句数：0 .. 3（不含当前播放行） */
    val lyricPlayedCount: Int = 2,
    /** 播放行下方展示的「待播放」句数：0 .. 3（不含当前播放行） */
    val lyricUpcomingCount: Int = 2,
    /** 整体 UI 缩放：0.80 .. 1.25 */
    val uiScale: Float = 1f,
    /** 黑胶水平偏移（dp），负左正右 */
    val vinylOffsetXDp: Float = 0f,
    /** 黑胶垂直偏移（dp），负上正下；绝对居中开启时忽略 */
    val vinylOffsetYDp: Float = 0f,
    /** 黑胶绝对垂直居中（相对整屏）；开启后忽略垂直偏移 */
    val vinylAbsoluteCenter: Boolean = false,
    /** 歌词水平偏移（dp），负左正右 */
    val lyricOffsetXDp: Float = 0f,
    /**
     * 动态歌词：按黑胶右缘收缩歌词可用宽度；
     * 左右对称伸缩，保持中心（含 [lyricOffsetXDp]）不变。
     */
    val dynamicLyrics: Boolean = false,
    /** 完整封面：封面铺满中心，无轴心镂空 */
    val vinylFullCover: Boolean = false,
    /** 底部播放组件常显 */
    val transportAlwaysVisible: Boolean = false,
    /**
     * 活跃光晕：三光球对应低/中/高音，随频谱增强发光；
     * 开启时背景光球运动略加快。
     */
    val activeHalo: Boolean = false,
    /** 标题信息（歌名/制作人/歌单）水平对齐 */
    val titleAlign: TitleAlignMode = TitleAlignMode.VINYL,
) {
    fun sanitized(): PlayerDisplayPrefs = copy(
        fontScale = fontScale.coerceIn(FONT_MIN, FONT_MAX),
        lyricLineSpacingDp = lyricLineSpacingDp.coerceIn(LINE_SPACING_MIN, LINE_SPACING_MAX),
        lyricPlayedCount = lyricPlayedCount.coerceIn(LYRIC_AROUND_MIN, LYRIC_AROUND_MAX),
        lyricUpcomingCount = lyricUpcomingCount.coerceIn(LYRIC_AROUND_MIN, LYRIC_AROUND_MAX),
        uiScale = uiScale.coerceIn(UI_MIN, UI_MAX),
        vinylOffsetXDp = vinylOffsetXDp.coerceIn(VINYL_OFFSET_MIN, VINYL_OFFSET_MAX),
        vinylOffsetYDp = vinylOffsetYDp.coerceIn(VINYL_OFFSET_MIN, VINYL_OFFSET_MAX),
        lyricOffsetXDp = lyricOffsetXDp.coerceIn(LYRIC_OFFSET_MIN, LYRIC_OFFSET_MAX),
    )

    companion object {
        const val FONT_MIN = 0.75f
        const val FONT_MAX = 1.50f
        const val LINE_SPACING_MIN = 0f
        const val LINE_SPACING_MAX = 28f
        const val LYRIC_AROUND_MIN = 0
        const val LYRIC_AROUND_MAX = 3
        const val UI_MIN = 0.80f
        const val UI_MAX = 1.25f
        const val VINYL_OFFSET_MIN = -56f
        const val VINYL_OFFSET_MAX = 56f
        const val LYRIC_OFFSET_MIN = -72f
        const val LYRIC_OFFSET_MAX = 72f
    }
}

class PlayerDisplayPrefsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): PlayerDisplayPrefs = PlayerDisplayPrefs(
        rainNightEnabled = prefs.getBoolean(KEY_RAIN, true),
        fontScale = prefs.getFloat(KEY_FONT, 1f),
        lyricLineSpacingDp = prefs.getFloat(KEY_LINE_SPACING, 10f),
        lyricPlayedCount = prefs.getInt(KEY_PLAYED_COUNT, 2),
        lyricUpcomingCount = prefs.getInt(KEY_UPCOMING_COUNT, 2),
        uiScale = prefs.getFloat(KEY_UI, 1f),
        vinylOffsetXDp = prefs.getFloat(KEY_VINYL_X, 0f),
        vinylOffsetYDp = prefs.getFloat(KEY_VINYL_Y, 0f),
        vinylAbsoluteCenter = prefs.getBoolean(KEY_VINYL_ABS, false),
        lyricOffsetXDp = prefs.getFloat(KEY_LYRIC_X, 0f),
        dynamicLyrics = prefs.getBoolean(KEY_DYNAMIC_LYRICS, false),
        vinylFullCover = prefs.getBoolean(KEY_VINYL_FULL_COVER, false),
        transportAlwaysVisible = prefs.getBoolean(KEY_TRANSPORT_ALWAYS, false),
        activeHalo = prefs.getBoolean(KEY_ACTIVE_HALO, false),
        titleAlign = TitleAlignMode.fromOrdinal(
            prefs.getInt(KEY_TITLE_ALIGN, TitleAlignMode.VINYL.ordinal),
        ),
    ).sanitized()

    fun save(value: PlayerDisplayPrefs) {
        val v = value.sanitized()
        prefs.edit()
            .putBoolean(KEY_RAIN, v.rainNightEnabled)
            .putFloat(KEY_FONT, v.fontScale)
            .putFloat(KEY_LINE_SPACING, v.lyricLineSpacingDp)
            .putInt(KEY_PLAYED_COUNT, v.lyricPlayedCount)
            .putInt(KEY_UPCOMING_COUNT, v.lyricUpcomingCount)
            .putFloat(KEY_UI, v.uiScale)
            .putFloat(KEY_VINYL_X, v.vinylOffsetXDp)
            .putFloat(KEY_VINYL_Y, v.vinylOffsetYDp)
            .putBoolean(KEY_VINYL_ABS, v.vinylAbsoluteCenter)
            .putFloat(KEY_LYRIC_X, v.lyricOffsetXDp)
            .putBoolean(KEY_DYNAMIC_LYRICS, v.dynamicLyrics)
            .putBoolean(KEY_VINYL_FULL_COVER, v.vinylFullCover)
            .putBoolean(KEY_TRANSPORT_ALWAYS, v.transportAlwaysVisible)
            .putBoolean(KEY_ACTIVE_HALO, v.activeHalo)
            .putInt(KEY_TITLE_ALIGN, v.titleAlign.ordinal)
            .apply()
    }

    companion object {
        private const val PREFS = "zmusic_player_display"
        private const val KEY_RAIN = "rain_night"
        private const val KEY_FONT = "font_scale"
        private const val KEY_LINE_SPACING = "lyric_line_spacing_dp"
        private const val KEY_PLAYED_COUNT = "lyric_played_count"
        private const val KEY_UPCOMING_COUNT = "lyric_upcoming_count"
        private const val KEY_UI = "ui_scale"
        private const val KEY_VINYL_X = "vinyl_offset_x_dp"
        private const val KEY_VINYL_Y = "vinyl_offset_y_dp"
        private const val KEY_VINYL_ABS = "vinyl_absolute_center"
        private const val KEY_LYRIC_X = "lyric_offset_x_dp"
        private const val KEY_DYNAMIC_LYRICS = "dynamic_lyrics"
        private const val KEY_VINYL_FULL_COVER = "vinyl_full_cover"
        private const val KEY_TRANSPORT_ALWAYS = "transport_always_visible"
        private const val KEY_ACTIVE_HALO = "active_halo"
        private const val KEY_TITLE_ALIGN = "title_align"
    }
}
