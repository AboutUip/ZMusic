package com.kite.zmusic.data

import android.content.Context
import android.content.SharedPreferences

/** 全屏播放页显示偏好（雨夜 / 字号 / UI 缩放 / 黑胶位置等），客户端持久化。 */
data class PlayerDisplayPrefs(
    val rainNightEnabled: Boolean = true,
    /** 歌词字号倍率：0.75 .. 1.50 */
    val fontScale: Float = 1f,
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
    /** 完整封面：封面铺满中心，无轴心镂空 */
    val vinylFullCover: Boolean = false,
    /** 底部播放组件常显 */
    val transportAlwaysVisible: Boolean = false,
) {
    fun sanitized(): PlayerDisplayPrefs = copy(
        fontScale = fontScale.coerceIn(FONT_MIN, FONT_MAX),
        uiScale = uiScale.coerceIn(UI_MIN, UI_MAX),
        vinylOffsetXDp = vinylOffsetXDp.coerceIn(VINYL_OFFSET_MIN, VINYL_OFFSET_MAX),
        vinylOffsetYDp = vinylOffsetYDp.coerceIn(VINYL_OFFSET_MIN, VINYL_OFFSET_MAX),
        lyricOffsetXDp = lyricOffsetXDp.coerceIn(LYRIC_OFFSET_MIN, LYRIC_OFFSET_MAX),
    )

    companion object {
        const val FONT_MIN = 0.75f
        const val FONT_MAX = 1.50f
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
        uiScale = prefs.getFloat(KEY_UI, 1f),
        vinylOffsetXDp = prefs.getFloat(KEY_VINYL_X, 0f),
        vinylOffsetYDp = prefs.getFloat(KEY_VINYL_Y, 0f),
        vinylAbsoluteCenter = prefs.getBoolean(KEY_VINYL_ABS, false),
        lyricOffsetXDp = prefs.getFloat(KEY_LYRIC_X, 0f),
        vinylFullCover = prefs.getBoolean(KEY_VINYL_FULL_COVER, false),
        transportAlwaysVisible = prefs.getBoolean(KEY_TRANSPORT_ALWAYS, false),
    ).sanitized()

    fun save(value: PlayerDisplayPrefs) {
        val v = value.sanitized()
        prefs.edit()
            .putBoolean(KEY_RAIN, v.rainNightEnabled)
            .putFloat(KEY_FONT, v.fontScale)
            .putFloat(KEY_UI, v.uiScale)
            .putFloat(KEY_VINYL_X, v.vinylOffsetXDp)
            .putFloat(KEY_VINYL_Y, v.vinylOffsetYDp)
            .putBoolean(KEY_VINYL_ABS, v.vinylAbsoluteCenter)
            .putFloat(KEY_LYRIC_X, v.lyricOffsetXDp)
            .putBoolean(KEY_VINYL_FULL_COVER, v.vinylFullCover)
            .putBoolean(KEY_TRANSPORT_ALWAYS, v.transportAlwaysVisible)
            .apply()
    }

    companion object {
        private const val PREFS = "zmusic_player_display"
        private const val KEY_RAIN = "rain_night"
        private const val KEY_FONT = "font_scale"
        private const val KEY_UI = "ui_scale"
        private const val KEY_VINYL_X = "vinyl_offset_x_dp"
        private const val KEY_VINYL_Y = "vinyl_offset_y_dp"
        private const val KEY_VINYL_ABS = "vinyl_absolute_center"
        private const val KEY_LYRIC_X = "lyric_offset_x_dp"
        private const val KEY_VINYL_FULL_COVER = "vinyl_full_cover"
        private const val KEY_TRANSPORT_ALWAYS = "transport_always_visible"
    }
}
