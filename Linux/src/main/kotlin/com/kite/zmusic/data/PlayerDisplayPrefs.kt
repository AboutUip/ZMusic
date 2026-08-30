package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject

enum class VinylColorStyle {
    BLACK,
    GOLD,
    WHITE,
    CUSTOM,
    ;

    companion object {
        fun fromOrdinal(v: Int): VinylColorStyle = entries.getOrElse(v) { BLACK }
    }
}

data class VinylCustomPreset(val baseArgb: Int, val grooveArgb: Int)

enum class TitleAlignMode {
    LEFT,
    VINYL,
    CENTER,
    LYRICS,
    ;

    companion object {
        fun fromOrdinal(v: Int): TitleAlignMode = entries.getOrElse(v) { VINYL }
    }
}

enum class LyricColorSlot {
    DEFAULT,
    PRESET_0,
    PRESET_1,
    PRESET_2,
    ;

    companion object {
        fun fromOrdinal(v: Int): LyricColorSlot = entries.getOrElse(v) { DEFAULT }
    }
}

data class LyricRoleStyle(
    val italic: Boolean = false,
    val bold: Boolean = false,
    val colorSlot: LyricColorSlot = LyricColorSlot.DEFAULT,
    val preset0Argb: Int = 0xFFF8FAFC.toInt(),
    val preset1Argb: Int = 0xFF9AF0F0.toInt(),
    val preset2Argb: Int = 0xFFE8C4A0.toInt(),
    val fontScale: Float = 1f,
) {
    fun resolvedArgb(defaultArgb: Int): Int = when (colorSlot) {
        LyricColorSlot.DEFAULT -> defaultArgb
        LyricColorSlot.PRESET_0 -> preset0Argb
        LyricColorSlot.PRESET_1 -> preset1Argb
        LyricColorSlot.PRESET_2 -> preset2Argb
    }

    companion object {
        val PlayingDefault = LyricRoleStyle(bold = true, preset0Argb = 0xFFF8FAFC.toInt())
        val PlayedDefault = LyricRoleStyle(
            italic = true,
            preset0Argb = 0xFFB8C0CC.toInt(),
            preset1Argb = 0xFF7EB8FF.toInt(),
            preset2Argb = 0xFFC8E6C9.toInt(),
        )
        val UnplayedDefault = LyricRoleStyle(
            preset0Argb = 0xFFDCE6F0.toInt(),
            preset1Argb = 0xFFD4C4F0.toInt(),
            preset2Argb = 0xFFE8C4A0.toInt(),
        )
        const val DEFAULT_PLAYING_ARGB = 0xFFF8FAFC.toInt()
        const val DEFAULT_PLAYED_ARGB = 0xFFB8C0CC.toInt()
        const val DEFAULT_UNPLAYED_ARGB = 0xFFDCE6F0.toInt()
    }
}

data class TitleLineStyle(
    val colorSlot: Int = 0,
    val preset0Argb: Int = 0xFFF8FAFC.toInt(),
    val preset1Argb: Int = 0xFF9AF0F0.toInt(),
    val fontScale: Float = 1f,
) {
    fun resolvedArgb(defaultArgb: Int): Int = when (colorSlot) {
        0 -> defaultArgb
        1 -> preset0Argb
        else -> preset1Argb
    }

    companion object {
        val DEFAULT_NAME_ARGB: Int = 0xFFF5F7FA.toInt()
        val DEFAULT_ARTIST_ARGB: Int = 0xB86FD4D4.toInt()
        val DEFAULT_SOURCE_ARGB: Int = 0x667A8899.toInt()
        const val BASE_NAME_SP = 16f
        const val BASE_ARTIST_SP = 9.5f
        const val BASE_SOURCE_SP = 8f
        val NameDefault = TitleLineStyle()
        val ArtistDefault = TitleLineStyle()
        val SourceDefault = TitleLineStyle()
    }
}

/** 横屏播放页显示偏好（对齐 Android `PlayerDisplayPrefs` 横屏字段）。 */
data class PlayerDisplayPrefs(
    val rainNightEnabled: Boolean = true,
    val lyricLineSpacingDp: Float = LINE_SPACING_DEFAULT,
    val lyricPlayedCount: Int = LYRIC_AROUND_DEFAULT,
    val lyricUpcomingCount: Int = LYRIC_AROUND_DEFAULT,
    val uiScale: Float = 1f,
    val vinylOffsetXDp: Float = 0f,
    val vinylOffsetYDp: Float = 0f,
    val vinylAbsoluteCenter: Boolean = false,
    val lyricOffsetXDp: Float = 0f,
    val dynamicLyrics: Boolean = false,
    val vinylFullCover: Boolean = false,
    val vinylSizeScale: Float = 1f,
    val vinylOuterScale: Float = 1f,
    val vinylCenterRadiusFrac: Float = 0.20f,
    val vinylColorStyle: VinylColorStyle = VinylColorStyle.BLACK,
    val vinylCustomBaseArgb: Int = 0xFF2A2A32.toInt(),
    val vinylCustomGrooveArgb: Int = 0xFFE8E8F0.toInt(),
    val vinylCustomPresets: List<VinylCustomPreset> = defaultVinylCustomPresets(),
    val vinylCustomPresetIndex: Int = 0,
    val transportAlwaysVisible: Boolean = false,
    val transportDocked: Boolean = true,
    val transportBottomInsetDp: Float = 16f,
    val vinylSongPickEnabled: Boolean = false,
    val activeHalo: Boolean = false,
    val lyricTapAutoPlay: Boolean = false,
    val titleAlign: TitleAlignMode = TitleAlignMode.VINYL,
    val titleOffsetYDp: Float = 0f,
    val titleNameStyle: TitleLineStyle = TitleLineStyle.NameDefault,
    val titleArtistStyle: TitleLineStyle = TitleLineStyle.ArtistDefault,
    val titleSourceStyle: TitleLineStyle = TitleLineStyle.SourceDefault,
    val vinylGestureDamping: Float = 0.5f,
    val lyricPlayingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    val lyricPlayedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    val lyricUnplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
) {
    fun withActiveCustomColors(baseArgb: Int, grooveArgb: Int): PlayerDisplayPrefs {
        val i = vinylCustomPresetIndex.coerceIn(0, VINYL_CUSTOM_PRESET_COUNT - 1)
        val presets = vinylCustomPresets.toMutableList()
        while (presets.size < VINYL_CUSTOM_PRESET_COUNT) {
            presets.add(VinylCustomPreset(baseArgb, grooveArgb))
        }
        presets[i] = VinylCustomPreset(baseArgb, grooveArgb)
        return copy(
            vinylCustomPresets = presets,
            vinylCustomBaseArgb = baseArgb,
            vinylCustomGrooveArgb = grooveArgb,
            vinylColorStyle = VinylColorStyle.CUSTOM,
        )
    }

    companion object {
        const val LINE_SPACING_MIN = 0f
        const val LINE_SPACING_MAX = 28f
        const val LINE_SPACING_DEFAULT = 10f
        const val LYRIC_AROUND_MIN = 0
        const val LYRIC_AROUND_MAX = 3
        const val LYRIC_AROUND_DEFAULT = 2
        const val UI_MIN = 0.80f
        const val UI_MAX = 1.25f
        const val VINYL_OFFSET_MIN = -56f
        const val VINYL_OFFSET_MAX = 56f
        const val VINYL_OFFSET_Y_MIN = -84f
        const val VINYL_OFFSET_Y_MAX = 84f
        const val LYRIC_OFFSET_MIN = -72f
        const val LYRIC_OFFSET_MAX = 72f
        const val TITLE_OFFSET_Y_MIN = -40f
        const val TITLE_OFFSET_Y_MAX = 72f
        const val TRANSPORT_BOTTOM_INSET_MIN = 8f
        const val TRANSPORT_BOTTOM_INSET_MAX = 48f
        const val VINYL_CUSTOM_PRESET_COUNT = 5
        const val VINYL_SIZE_SCALE_MIN = 0.75f
        const val VINYL_SIZE_SCALE_MAX = 1.35f
        const val VINYL_OUTER_SCALE_MIN = 0.88f
        const val VINYL_OUTER_SCALE_MAX = 1.35f
        const val VINYL_CENTER_RADIUS_MIN = 0.10f
        const val VINYL_CENTER_RADIUS_MAX = 0.42f
        const val VINYL_GESTURE_DAMPING_MIN = 0.15f
        const val VINYL_GESTURE_DAMPING_MAX = 1.00f
        const val FONT_MIN = 0.75f
        const val FONT_MAX = 1.50f
    }
}

fun defaultVinylCustomPresets(): List<VinylCustomPreset> = List(5) {
    VinylCustomPreset(0xFF2A2A32.toInt(), 0xFFE8E8F0.toInt())
}

fun PlayerDisplayPrefs.toJson(): JSONObject {
    fun role(s: LyricRoleStyle) = JSONObject()
        .put("italic", s.italic)
        .put("bold", s.bold)
        .put("slot", s.colorSlot.ordinal)
        .put("p0", s.preset0Argb)
        .put("p1", s.preset1Argb)
        .put("p2", s.preset2Argb)
        .put("font", s.fontScale.toDouble())
    fun title(s: TitleLineStyle) = JSONObject()
        .put("slot", s.colorSlot)
        .put("p0", s.preset0Argb)
        .put("p1", s.preset1Argb)
        .put("font", s.fontScale.toDouble())
    val presets = JSONArray()
    vinylCustomPresets.forEach {
        presets.put(JSONObject().put("base", it.baseArgb).put("groove", it.grooveArgb))
    }
    return JSONObject()
        .put("rainNightEnabled", rainNightEnabled)
        .put("lyricLineSpacingDp", lyricLineSpacingDp.toDouble())
        .put("lyricPlayedCount", lyricPlayedCount)
        .put("lyricUpcomingCount", lyricUpcomingCount)
        .put("uiScale", uiScale.toDouble())
        .put("vinylOffsetXDp", vinylOffsetXDp.toDouble())
        .put("vinylOffsetYDp", vinylOffsetYDp.toDouble())
        .put("vinylAbsoluteCenter", vinylAbsoluteCenter)
        .put("lyricOffsetXDp", lyricOffsetXDp.toDouble())
        .put("dynamicLyrics", dynamicLyrics)
        .put("vinylFullCover", vinylFullCover)
        .put("vinylSizeScale", vinylSizeScale.toDouble())
        .put("vinylOuterScale", vinylOuterScale.toDouble())
        .put("vinylCenterRadiusFrac", vinylCenterRadiusFrac.toDouble())
        .put("vinylColorStyle", vinylColorStyle.ordinal)
        .put("vinylCustomBaseArgb", vinylCustomBaseArgb)
        .put("vinylCustomGrooveArgb", vinylCustomGrooveArgb)
        .put("vinylCustomPresets", presets)
        .put("vinylCustomPresetIndex", vinylCustomPresetIndex)
        .put("transportAlwaysVisible", transportAlwaysVisible)
        .put("transportDocked", transportDocked)
        .put("transportBottomInsetDp", transportBottomInsetDp.toDouble())
        .put("vinylSongPickEnabled", vinylSongPickEnabled)
        .put("activeHalo", activeHalo)
        .put("lyricTapAutoPlay", lyricTapAutoPlay)
        .put("titleAlign", titleAlign.ordinal)
        .put("titleOffsetYDp", titleOffsetYDp.toDouble())
        .put("titleName", title(titleNameStyle))
        .put("titleArtist", title(titleArtistStyle))
        .put("titleSource", title(titleSourceStyle))
        .put("vinylGestureDamping", vinylGestureDamping.toDouble())
        .put("lyricPlaying", role(lyricPlayingStyle))
        .put("lyricPlayed", role(lyricPlayedStyle))
        .put("lyricUnplayed", lyricUnplayedStyle.let(::role))
}

fun playerDisplayPrefsFromJson(o: JSONObject?, haloFallback: Boolean): PlayerDisplayPrefs {
    if (o == null) return PlayerDisplayPrefs(activeHalo = haloFallback)
    fun role(key: String, fallback: LyricRoleStyle): LyricRoleStyle {
        val r = o.optJSONObject(key) ?: return fallback
        return LyricRoleStyle(
            italic = r.optBoolean("italic", fallback.italic),
            bold = r.optBoolean("bold", fallback.bold),
            colorSlot = LyricColorSlot.fromOrdinal(r.optInt("slot", fallback.colorSlot.ordinal)),
            preset0Argb = r.optInt("p0", fallback.preset0Argb),
            preset1Argb = r.optInt("p1", fallback.preset1Argb),
            preset2Argb = r.optInt("p2", fallback.preset2Argb),
            fontScale = r.optDouble("font", fallback.fontScale.toDouble()).toFloat(),
        )
    }
    fun title(key: String, fallback: TitleLineStyle): TitleLineStyle {
        val r = o.optJSONObject(key) ?: return fallback
        return TitleLineStyle(
            colorSlot = r.optInt("slot", fallback.colorSlot),
            preset0Argb = r.optInt("p0", fallback.preset0Argb),
            preset1Argb = r.optInt("p1", fallback.preset1Argb),
            fontScale = r.optDouble("font", fallback.fontScale.toDouble()).toFloat(),
        )
    }
    val arr = o.optJSONArray("vinylCustomPresets")
    val presets = if (arr == null || arr.length() == 0) {
        defaultVinylCustomPresets()
    } else {
        buildList {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                add(VinylCustomPreset(p.optInt("base"), p.optInt("groove")))
            }
        }.ifEmpty { defaultVinylCustomPresets() }
    }
    return PlayerDisplayPrefs(
        rainNightEnabled = o.optBoolean("rainNightEnabled", true),
        lyricLineSpacingDp = o.optDouble("lyricLineSpacingDp", 10.0).toFloat(),
        lyricPlayedCount = o.optInt("lyricPlayedCount", 2),
        lyricUpcomingCount = o.optInt("lyricUpcomingCount", 2),
        uiScale = o.optDouble("uiScale", 1.0).toFloat(),
        vinylOffsetXDp = o.optDouble("vinylOffsetXDp", 0.0).toFloat(),
        vinylOffsetYDp = o.optDouble("vinylOffsetYDp", 0.0).toFloat(),
        vinylAbsoluteCenter = o.optBoolean("vinylAbsoluteCenter", false),
        lyricOffsetXDp = o.optDouble("lyricOffsetXDp", 0.0).toFloat(),
        dynamicLyrics = o.optBoolean("dynamicLyrics", false),
        vinylFullCover = o.optBoolean("vinylFullCover", false),
        vinylSizeScale = o.optDouble("vinylSizeScale", 1.0).toFloat(),
        vinylOuterScale = o.optDouble("vinylOuterScale", 1.0).toFloat(),
        vinylCenterRadiusFrac = o.optDouble("vinylCenterRadiusFrac", 0.20).toFloat(),
        vinylColorStyle = VinylColorStyle.fromOrdinal(o.optInt("vinylColorStyle", 0)),
        vinylCustomBaseArgb = o.optInt("vinylCustomBaseArgb", 0xFF2A2A32.toInt()),
        vinylCustomGrooveArgb = o.optInt("vinylCustomGrooveArgb", 0xFFE8E8F0.toInt()),
        vinylCustomPresets = presets,
        vinylCustomPresetIndex = o.optInt("vinylCustomPresetIndex", 0),
        transportAlwaysVisible = o.optBoolean("transportAlwaysVisible", false),
        transportDocked = o.optBoolean("transportDocked", true),
        transportBottomInsetDp = o.optDouble("transportBottomInsetDp", 16.0).toFloat(),
        vinylSongPickEnabled = o.optBoolean("vinylSongPickEnabled", false),
        activeHalo = o.optBoolean("activeHalo", haloFallback),
        lyricTapAutoPlay = o.optBoolean("lyricTapAutoPlay", false),
        titleAlign = TitleAlignMode.fromOrdinal(o.optInt("titleAlign", TitleAlignMode.VINYL.ordinal)),
        titleOffsetYDp = o.optDouble("titleOffsetYDp", 0.0).toFloat(),
        titleNameStyle = title("titleName", TitleLineStyle.NameDefault),
        titleArtistStyle = title("titleArtist", TitleLineStyle.ArtistDefault),
        titleSourceStyle = title("titleSource", TitleLineStyle.SourceDefault),
        vinylGestureDamping = o.optDouble("vinylGestureDamping", 0.5).toFloat(),
        lyricPlayingStyle = role("lyricPlaying", LyricRoleStyle.PlayingDefault),
        lyricPlayedStyle = role("lyricPlayed", LyricRoleStyle.PlayedDefault),
        lyricUnplayedStyle = role("lyricUnplayed", LyricRoleStyle.UnplayedDefault),
    )
}
