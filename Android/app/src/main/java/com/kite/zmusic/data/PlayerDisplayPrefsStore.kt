package com.kite.zmusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

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

/** 黑胶盘面配色预设。 */
enum class VinylColorStyle {
    /** 黑底浅纹（默认） */
    BLACK,
    /** 金底白纹 */
    GOLD,
    /** 白底黑纹 */
    WHITE,
    /** 自定义：盘面色 + 纹理色 */
    CUSTOM,
    ;

    companion object {
        fun fromOrdinal(v: Int): VinylColorStyle =
            entries.getOrElse(v) { BLACK }
    }
}

/** 自选配色的单个预设位（盘面 + 纹理）。 */
data class VinylCustomPreset(
    val baseArgb: Int,
    val grooveArgb: Int,
)

/** 黑胶盘绘制用色（径向底 + 纹路）。 */
data class VinylPlateColors(
    val baseInner: Color,
    val baseMid: Color,
    val baseOuter: Color,
    val baseEdge: Color,
    val groove: Color,
    val rim: Color,
    val holeLight: Color,
    val holeDark: Color,
) {
    companion object {
        val Black = VinylPlateColors(
            baseInner = Color(0xFF101012),
            baseMid = Color(0xFF161618),
            baseOuter = Color(0xFF121214),
            baseEdge = Color(0xFF080809),
            groove = Color.White,
            rim = Color.White,
            holeLight = Color.White,
            holeDark = Color.Black,
        )
        val Gold = VinylPlateColors(
            baseInner = Color(0xFFC9A227),
            baseMid = Color(0xFFB8860B),
            baseOuter = Color(0xFF8B6914),
            baseEdge = Color(0xFF5C4A0E),
            groove = Color(0xFFFFF8E7),
            rim = Color(0xFFFFF8E7),
            holeLight = Color(0xFFFFF8E7),
            holeDark = Color(0xFF3D2E08),
        )
        val White = VinylPlateColors(
            baseInner = Color(0xFFF4F4F6),
            baseMid = Color(0xFFE8E8EC),
            baseOuter = Color(0xFFD8D8DE),
            baseEdge = Color(0xFFC0C0C8),
            groove = Color(0xFF1A1A1E),
            rim = Color(0xFF2A2A30),
            holeLight = Color(0xFF3A3A42),
            holeDark = Color(0xFF0A0A0C),
        )

        fun custom(baseArgb: Int, grooveArgb: Int): VinylPlateColors {
            val base = Color(baseArgb)
            val groove = Color(grooveArgb)
            return VinylPlateColors(
                baseInner = base.lighten(0.12f),
                baseMid = base,
                baseOuter = base.darken(0.12f),
                baseEdge = base.darken(0.28f),
                groove = groove,
                rim = groove,
                holeLight = groove.lighten(0.15f),
                holeDark = base.darken(0.45f),
            )
        }

        private fun Color.lighten(amount: Float): Color {
            val t = amount.coerceIn(0f, 1f)
            return Color(
                red = red + (1f - red) * t,
                green = green + (1f - green) * t,
                blue = blue + (1f - blue) * t,
                alpha = alpha,
            )
        }

        private fun Color.darken(amount: Float): Color {
            val t = (1f - amount.coerceIn(0f, 1f))
            return Color(red = red * t, green = green * t, blue = blue * t, alpha = alpha)
        }
    }
}

/** 默认 5 档自选预设；[slot0] 可被旧版单组自定义色覆盖。 */
fun defaultVinylCustomPresets(
    slot0BaseArgb: Int = Color(0xFF2A2A32).toArgb(),
    slot0GrooveArgb: Int = Color(0xFFE8E8F0).toArgb(),
): List<VinylCustomPreset> = listOf(
    VinylCustomPreset(slot0BaseArgb, slot0GrooveArgb),
    VinylCustomPreset(Color(0xFF1A2744).toArgb(), Color(0xFF7EB8FF).toArgb()),
    VinylCustomPreset(Color(0xFF3D1F24).toArgb(), Color(0xFFE8C4A0).toArgb()),
    VinylCustomPreset(Color(0xFF1E3328).toArgb(), Color(0xFFC8E6C9).toArgb()),
    VinylCustomPreset(Color(0xFF2A1F3D).toArgb(), Color(0xFFD4C4F0).toArgb()),
)

fun encodeVinylCustomPresets(presets: List<VinylCustomPreset>): String =
    presets.take(PlayerDisplayPrefs.VINYL_CUSTOM_PRESET_COUNT).joinToString(";") {
        "${it.baseArgb},${it.grooveArgb}"
    }

fun decodeVinylCustomPresets(
    raw: String?,
    fallbackBase: Int,
    fallbackGroove: Int,
): List<VinylCustomPreset> {
    val defaults = defaultVinylCustomPresets(fallbackBase, fallbackGroove)
    if (raw.isNullOrBlank()) return defaults
    val parsed = raw.split(';').mapNotNull { part ->
        val bits = part.split(',')
        if (bits.size != 2) return@mapNotNull null
        val base = bits[0].toIntOrNull() ?: return@mapNotNull null
        val groove = bits[1].toIntOrNull() ?: return@mapNotNull null
        VinylCustomPreset(base, groove)
    }
    if (parsed.isEmpty()) return defaults
    return List(PlayerDisplayPrefs.VINYL_CUSTOM_PRESET_COUNT) { i ->
        parsed.getOrElse(i) { defaults[i] }
    }
}

/** 歌词颜色槽：不可改默认 + 3 个可写预设。 */
enum class LyricColorSlot {
    DEFAULT,
    PRESET_0,
    PRESET_1,
    PRESET_2,
    ;

    companion object {
        fun fromOrdinal(v: Int): LyricColorSlot =
            entries.getOrElse(v) { DEFAULT }
    }
}

/** 横屏歌词某一角色（播放中 / 已播放 / 未播放）的样式。 */
data class LyricRoleStyle(
    val italic: Boolean = false,
    val bold: Boolean = false,
    val colorSlot: LyricColorSlot = LyricColorSlot.DEFAULT,
    val preset0Argb: Int = Color(0xFFF8FAFC).toArgb(),
    val preset1Argb: Int = Color(0xFF9AF0F0).toArgb(),
    val preset2Argb: Int = Color(0xFFE8C4A0).toArgb(),
    /** 相对基准字号倍率：0.75 .. 1.50 */
    val fontScale: Float = 1f,
) {
    fun resolvedColor(defaultArgb: Int): Color = when (colorSlot) {
        LyricColorSlot.DEFAULT -> Color(defaultArgb)
        LyricColorSlot.PRESET_0 -> Color(preset0Argb)
        LyricColorSlot.PRESET_1 -> Color(preset1Argb)
        LyricColorSlot.PRESET_2 -> Color(preset2Argb)
    }

    fun presetArgb(index: Int): Int = when (index) {
        0 -> preset0Argb
        1 -> preset1Argb
        else -> preset2Argb
    }

    fun withPresetArgb(index: Int, argb: Int): LyricRoleStyle = when (index) {
        0 -> copy(preset0Argb = argb, colorSlot = LyricColorSlot.PRESET_0)
        1 -> copy(preset1Argb = argb, colorSlot = LyricColorSlot.PRESET_1)
        else -> copy(preset2Argb = argb, colorSlot = LyricColorSlot.PRESET_2)
    }

    fun withColorSlot(slot: LyricColorSlot): LyricRoleStyle = copy(colorSlot = slot)

    fun withFontScale(scale: Float): LyricRoleStyle = copy(fontScale = scale)

    fun sanitizedFontScale(
        min: Float = PlayerDisplayPrefs.FONT_MIN,
        max: Float = PlayerDisplayPrefs.FONT_MAX,
    ): Float = fontScale.let { if (it.isFinite()) it.coerceIn(min, max) else 1f }

    fun sanitized(): LyricRoleStyle = copy(fontScale = sanitizedFontScale())

    companion object {
        val PlayingDefault = LyricRoleStyle(
            italic = false,
            bold = true,
            colorSlot = LyricColorSlot.DEFAULT,
            preset0Argb = Color(0xFFF8FAFC).toArgb(),
            preset1Argb = Color(0xFF9AF0F0).toArgb(),
            preset2Argb = Color(0xFFE8C4A0).toArgb(),
        )
        val PlayedDefault = LyricRoleStyle(
            italic = true,
            bold = false,
            colorSlot = LyricColorSlot.DEFAULT,
            preset0Argb = Color(0xFFB8C0CC).toArgb(),
            preset1Argb = Color(0xFF7EB8FF).toArgb(),
            preset2Argb = Color(0xFFC8E6C9).toArgb(),
        )
        val UnplayedDefault = LyricRoleStyle(
            italic = false,
            bold = false,
            colorSlot = LyricColorSlot.DEFAULT,
            preset0Argb = Color(0xFFDCE6F0).toArgb(),
            preset1Argb = Color(0xFFD4C4F0).toArgb(),
            preset2Argb = Color(0xFFE8C4A0).toArgb(),
        )

        /** 内置不可改默认色（与历史硬编码一致）。 */
        val DEFAULT_PLAYING_ARGB: Int = Color(0xFFF8FAFC).toArgb()
        val DEFAULT_PLAYED_ARGB: Int = Color(0xFFB8C0CC).toArgb()
        val DEFAULT_UNPLAYED_ARGB: Int = Color(0xFFDCE6F0).toArgb()
    }
}

/** 编码：italic,bold,slot,p0,p1,p2[,fontScale] */
fun encodeLyricRoleStyle(style: LyricRoleStyle): String =
    listOf(
        if (style.italic) 1 else 0,
        if (style.bold) 1 else 0,
        style.colorSlot.ordinal,
        style.preset0Argb,
        style.preset1Argb,
        style.preset2Argb,
        style.sanitizedFontScale(),
    ).joinToString(",")

fun decodeLyricRoleStyle(raw: String?, fallback: LyricRoleStyle): LyricRoleStyle {
    if (raw.isNullOrBlank()) return fallback
    val bits = raw.split(',')
    if (bits.size < 6) return fallback
    val italic = bits[0].toIntOrNull() == 1
    val bold = bits[1].toIntOrNull() == 1
    val slot = LyricColorSlot.fromOrdinal(bits[2].toIntOrNull() ?: 0)
    val p0 = bits[3].toIntOrNull() ?: fallback.preset0Argb
    val p1 = bits[4].toIntOrNull() ?: fallback.preset1Argb
    val p2 = bits[5].toIntOrNull() ?: fallback.preset2Argb
    val fontScale = bits.getOrNull(6)?.toFloatOrNull()?.takeIf { it.isFinite() }
        ?: fallback.fontScale
    return LyricRoleStyle(
        italic = italic,
        bold = bold,
        colorSlot = slot,
        preset0Argb = p0,
        preset1Argb = p1,
        preset2Argb = p2,
        fontScale = fontScale,
    ).sanitized()
}

/** 横屏标题信息行颜色槽：不可改默认 + 2 个可写预设。 */
enum class TitleColorSlot {
    DEFAULT,
    PRESET_0,
    PRESET_1,
    ;

    companion object {
        fun fromOrdinal(v: Int): TitleColorSlot =
            entries.getOrElse(v) { DEFAULT }
    }
}

/** 歌名 / 制作人 / 歌单一行的颜色与字号样式。 */
data class TitleLineStyle(
    val colorSlot: TitleColorSlot = TitleColorSlot.DEFAULT,
    val preset0Argb: Int = Color(0xFFF8FAFC).toArgb(),
    val preset1Argb: Int = Color(0xFF9AF0F0).toArgb(),
    /** 相对基准字号倍率：0.75 .. 1.50 */
    val fontScale: Float = 1f,
) {
    fun resolvedColor(defaultArgb: Int): Color = when (colorSlot) {
        TitleColorSlot.DEFAULT -> Color(defaultArgb)
        TitleColorSlot.PRESET_0 -> Color(preset0Argb)
        TitleColorSlot.PRESET_1 -> Color(preset1Argb)
    }

    fun presetArgb(index: Int): Int = when (index) {
        0 -> preset0Argb
        else -> preset1Argb
    }

    fun withPresetArgb(index: Int, argb: Int): TitleLineStyle = when (index) {
        0 -> copy(preset0Argb = argb, colorSlot = TitleColorSlot.PRESET_0)
        else -> copy(preset1Argb = argb, colorSlot = TitleColorSlot.PRESET_1)
    }

    fun withColorSlot(slot: TitleColorSlot): TitleLineStyle = copy(colorSlot = slot)

    fun withFontScale(scale: Float): TitleLineStyle = copy(fontScale = scale)

    fun sanitizedFontScale(
        min: Float = PlayerDisplayPrefs.FONT_MIN,
        max: Float = PlayerDisplayPrefs.FONT_MAX,
    ): Float = fontScale.let { if (it.isFinite()) it.coerceIn(min, max) else 1f }

    fun sanitized(): TitleLineStyle = copy(fontScale = sanitizedFontScale())

    companion object {
        /** 内置不可改默认色（与历史硬编码一致）。 */
        val DEFAULT_NAME_ARGB: Int = Color(0xFFF5F7FA).toArgb()
        val DEFAULT_ARTIST_ARGB: Int = Color(0xFF6FD4D4).copy(alpha = 0.72f).toArgb()
        val DEFAULT_SOURCE_ARGB: Int = Color(0xFF7A8899).copy(alpha = 0.4f).toArgb()

        const val BASE_NAME_SP = 16f
        const val BASE_ARTIST_SP = 9.5f
        const val BASE_SOURCE_SP = 8f

        val NameDefault = TitleLineStyle(
            colorSlot = TitleColorSlot.DEFAULT,
            preset0Argb = Color(0xFFF8FAFC).toArgb(),
            preset1Argb = Color(0xFF9AF0F0).toArgb(),
        )
        val ArtistDefault = TitleLineStyle(
            colorSlot = TitleColorSlot.DEFAULT,
            preset0Argb = Color(0xFF6FD4D4).toArgb(),
            preset1Argb = Color(0xFF7EB8FF).toArgb(),
        )
        val SourceDefault = TitleLineStyle(
            colorSlot = TitleColorSlot.DEFAULT,
            preset0Argb = Color(0xFF7A8899).toArgb(),
            preset1Argb = Color(0xFFB8C0CC).toArgb(),
        )
    }
}

/** 编码：slot,p0,p1[,fontScale] */
fun encodeTitleLineStyle(style: TitleLineStyle): String =
    listOf(
        style.colorSlot.ordinal,
        style.preset0Argb,
        style.preset1Argb,
        style.sanitizedFontScale(),
    ).joinToString(",")

fun decodeTitleLineStyle(raw: String?, fallback: TitleLineStyle): TitleLineStyle {
    if (raw.isNullOrBlank()) return fallback
    val bits = raw.split(',')
    if (bits.size < 3) return fallback
    val slot = TitleColorSlot.fromOrdinal(bits[0].toIntOrNull() ?: 0)
    val p0 = bits[1].toIntOrNull() ?: fallback.preset0Argb
    val p1 = bits[2].toIntOrNull() ?: fallback.preset1Argb
    val fontScale = bits.getOrNull(3)?.toFloatOrNull()?.takeIf { it.isFinite() }
        ?: fallback.fontScale
    return TitleLineStyle(
        colorSlot = slot,
        preset0Argb = p0,
        preset1Argb = p1,
        fontScale = fontScale,
    ).sanitized()
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
    /**
     * 黑胶整体大小（绕中心缩放盘面+封面）：[VINYL_SIZE_SCALE_MIN] .. [VINYL_SIZE_SCALE_MAX]
     */
    val vinylSizeScale: Float = 1f,
    /**
     * 外圈黑胶倍率：只放大/缩小黑圈纹路面，封面绝对尺寸不变（仍只随 [vinylSizeScale]）。
     */
    val vinylOuterScale: Float = 1f,
    /**
     * 中心黑胶半径：相对「整体大小 = 100%」时的基准盘比例；
     * 与 [vinylOuterScale] 解耦。完整封面开启时忽略。
     */
    val vinylCenterRadiusFrac: Float = 0.20f,
    /** 黑胶配色预设 */
    val vinylColorStyle: VinylColorStyle = VinylColorStyle.BLACK,
    /** 当前生效的自定义盘面色 ARGB（与活动预设位同步） */
    val vinylCustomBaseArgb: Int = Color(0xFF2A2A32).toArgb(),
    /** 当前生效的自定义纹理色 ARGB（与活动预设位同步） */
    val vinylCustomGrooveArgb: Int = Color(0xFFE8E8F0).toArgb(),
    /** 自选 5 档预设 */
    val vinylCustomPresets: List<VinylCustomPreset> = defaultVinylCustomPresets(),
    /** 当前自选预设位：0 .. 4 */
    val vinylCustomPresetIndex: Int = 0,
    /** 底部播放组件常显 */
    val transportAlwaysVisible: Boolean = false,
    /**
     * 吸附式播放组件：开启贴底（仅上方圆角）；
     * 关闭则悬浮，四角圆角，并用 [transportBottomInsetDp] 离底。
     */
    val transportDocked: Boolean = true,
    /**
     * 悬浮态离底距离（dp）；吸附开启时忽略且设置项不可编辑。
     */
    val transportBottomInsetDp: Float = 16f,
    /**
     * 黑胶选歌：横屏长按黑胶进入扑克牌式队列选歌。
     */
    val vinylSongPickEnabled: Boolean = false,
    /**
     * 活跃光晕：三光球对应低/中/高音，随频谱增强发光；
     * 开启时背景光球运动略加快。
     */
    val activeHalo: Boolean = false,
    /**
     * 点选歌词后自动播放：开启则从选中句开始播放；
     * 关闭则仅跳转进度，不改变播放/暂停状态。
     */
    val lyricTapAutoPlay: Boolean = false,
    /** 标题信息（歌名/制作人/歌单）水平对齐 */
    val titleAlign: TitleAlignMode = TitleAlignMode.VINYL,
    /** 标题垂直偏移（dp），负上正下；叠在默认上边距之上 */
    val titleOffsetYDp: Float = 0f,
    /** 歌名颜色样式 */
    val titleNameStyle: TitleLineStyle = TitleLineStyle.NameDefault,
    /** 制作人颜色样式 */
    val titleArtistStyle: TitleLineStyle = TitleLineStyle.ArtistDefault,
    /** 歌单/来源颜色样式 */
    val titleSourceStyle: TitleLineStyle = TitleLineStyle.SourceDefault,
    /**
     * 黑胶手势阻尼（切歌灵敏度）：默认 0.5 与历史阈值一致；
     * 值越高越灵敏（提交位移/甩速阈值越低）。
     */
    val vinylGestureDamping: Float = 0.5f,
    /** 横屏「播放中」歌词样式 */
    val lyricPlayingStyle: LyricRoleStyle = LyricRoleStyle.PlayingDefault,
    /** 横屏「已播放」歌词样式 */
    val lyricPlayedStyle: LyricRoleStyle = LyricRoleStyle.PlayedDefault,
    /** 横屏「未播放」歌词样式 */
    val lyricUnplayedStyle: LyricRoleStyle = LyricRoleStyle.UnplayedDefault,
) {
    fun lyricPlayingColor(): Color =
        lyricPlayingStyle.resolvedColor(LyricRoleStyle.DEFAULT_PLAYING_ARGB)

    fun lyricPlayedColor(): Color =
        lyricPlayedStyle.resolvedColor(LyricRoleStyle.DEFAULT_PLAYED_ARGB)

    fun lyricUnplayedColor(): Color =
        lyricUnplayedStyle.resolvedColor(LyricRoleStyle.DEFAULT_UNPLAYED_ARGB)

    fun titleNameColor(): Color =
        titleNameStyle.resolvedColor(TitleLineStyle.DEFAULT_NAME_ARGB)

    fun titleArtistColor(): Color =
        titleArtistStyle.resolvedColor(TitleLineStyle.DEFAULT_ARTIST_ARGB)

    fun titleSourceColor(): Color =
        titleSourceStyle.resolvedColor(TitleLineStyle.DEFAULT_SOURCE_ARGB)

    fun vinylPlateColors(): VinylPlateColors = when (vinylColorStyle) {
        VinylColorStyle.BLACK -> VinylPlateColors.Black
        VinylColorStyle.GOLD -> VinylPlateColors.Gold
        VinylColorStyle.WHITE -> VinylPlateColors.White
        VinylColorStyle.CUSTOM -> VinylPlateColors.custom(
            vinylCustomBaseArgb,
            vinylCustomGrooveArgb,
        )
    }

    fun activeCustomPreset(): VinylCustomPreset =
        vinylCustomPresets.getOrElse(vinylCustomPresetIndex.coerceIn(0, VINYL_CUSTOM_PRESET_COUNT - 1)) {
            VinylCustomPreset(vinylCustomBaseArgb, vinylCustomGrooveArgb)
        }

    /** 切换自选预设位，并同步当前生效色。 */
    fun withCustomPresetIndex(index: Int): PlayerDisplayPrefs {
        val i = index.coerceIn(0, VINYL_CUSTOM_PRESET_COUNT - 1)
        val presets = sanitizeCustomPresets(vinylCustomPresets, vinylCustomBaseArgb, vinylCustomGrooveArgb)
        val p = presets[i]
        return copy(
            vinylCustomPresets = presets,
            vinylCustomPresetIndex = i,
            vinylCustomBaseArgb = p.baseArgb,
            vinylCustomGrooveArgb = p.grooveArgb,
            vinylColorStyle = VinylColorStyle.CUSTOM,
        )
    }

    /** 更新当前预设位颜色并即时生效（自动保存路径用）。 */
    fun withActiveCustomColors(baseArgb: Int, grooveArgb: Int): PlayerDisplayPrefs {
        val i = vinylCustomPresetIndex.coerceIn(0, VINYL_CUSTOM_PRESET_COUNT - 1)
        val presets = sanitizeCustomPresets(vinylCustomPresets, vinylCustomBaseArgb, vinylCustomGrooveArgb)
            .toMutableList()
        presets[i] = VinylCustomPreset(baseArgb, grooveArgb)
        return copy(
            vinylCustomPresets = presets,
            vinylCustomPresetIndex = i,
            vinylCustomBaseArgb = baseArgb,
            vinylCustomGrooveArgb = grooveArgb,
            vinylColorStyle = VinylColorStyle.CUSTOM,
        )
    }

    fun sanitized(): PlayerDisplayPrefs {
        val presets = sanitizeCustomPresets(
            vinylCustomPresets,
            vinylCustomBaseArgb,
            vinylCustomGrooveArgb,
        )
        val index = vinylCustomPresetIndex.coerceIn(0, VINYL_CUSTOM_PRESET_COUNT - 1)
        val active = presets[index]
        return copy(
            fontScale = fontScale.finiteCoerceIn(FONT_MIN, FONT_MAX, 1f),
            lyricLineSpacingDp = lyricLineSpacingDp.finiteCoerceIn(
                LINE_SPACING_MIN,
                LINE_SPACING_MAX,
                10f,
            ),
            lyricPlayedCount = lyricPlayedCount.coerceIn(LYRIC_AROUND_MIN, LYRIC_AROUND_MAX),
            lyricUpcomingCount = lyricUpcomingCount.coerceIn(LYRIC_AROUND_MIN, LYRIC_AROUND_MAX),
            uiScale = uiScale.finiteCoerceIn(UI_MIN, UI_MAX, 1f),
            vinylOffsetXDp = vinylOffsetXDp.finiteCoerceIn(VINYL_OFFSET_MIN, VINYL_OFFSET_MAX, 0f),
            vinylOffsetYDp = vinylOffsetYDp.finiteCoerceIn(VINYL_OFFSET_MIN, VINYL_OFFSET_MAX, 0f),
            lyricOffsetXDp = lyricOffsetXDp.finiteCoerceIn(LYRIC_OFFSET_MIN, LYRIC_OFFSET_MAX, 0f),
            titleOffsetYDp = titleOffsetYDp.finiteCoerceIn(
                TITLE_OFFSET_Y_MIN,
                TITLE_OFFSET_Y_MAX,
                0f,
            ),
            transportBottomInsetDp = transportBottomInsetDp.finiteCoerceIn(
                TRANSPORT_BOTTOM_INSET_MIN,
                TRANSPORT_BOTTOM_INSET_MAX,
                16f,
            ),
            vinylSizeScale = vinylSizeScale.finiteCoerceIn(
                VINYL_SIZE_SCALE_MIN,
                VINYL_SIZE_SCALE_MAX,
                1f,
            ),
            vinylOuterScale = vinylOuterScale.finiteCoerceIn(
                VINYL_OUTER_SCALE_MIN,
                VINYL_OUTER_SCALE_MAX,
                1f,
            ),
            vinylCenterRadiusFrac = vinylCenterRadiusFrac.finiteCoerceIn(
                VINYL_CENTER_RADIUS_MIN,
                VINYL_CENTER_RADIUS_MAX,
                0.20f,
            ),
            vinylCustomPresets = presets,
            vinylCustomPresetIndex = index,
            vinylCustomBaseArgb = active.baseArgb,
            vinylCustomGrooveArgb = active.grooveArgb,
            vinylGestureDamping = vinylGestureDamping.finiteCoerceIn(
                VINYL_GESTURE_DAMPING_MIN,
                VINYL_GESTURE_DAMPING_MAX,
                0.5f,
            ),
            lyricPlayingStyle = lyricPlayingStyle.sanitized(),
            lyricPlayedStyle = lyricPlayedStyle.sanitized(),
            lyricUnplayedStyle = lyricUnplayedStyle.sanitized(),
            titleNameStyle = titleNameStyle.sanitized(),
            titleArtistStyle = titleArtistStyle.sanitized(),
            titleSourceStyle = titleSourceStyle.sanitized(),
        )
    }

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
        /** 标题信息垂直偏移 */
        const val TITLE_OFFSET_Y_MIN = -40f
        const val TITLE_OFFSET_Y_MAX = 72f
        /** 悬浮播放组件离底距离 */
        const val TRANSPORT_BOTTOM_INSET_MIN = 8f
        const val TRANSPORT_BOTTOM_INSET_MAX = 48f
        const val VINYL_CUSTOM_PRESET_COUNT = 5
        const val VINYL_SIZE_SCALE_MIN = 0.75f
        const val VINYL_SIZE_SCALE_MAX = 1.35f
        const val VINYL_OUTER_SCALE_MIN = 0.88f
        const val VINYL_OUTER_SCALE_MAX = 1.35f
        /** 中心黑胶挖孔（相对基准整体盘）；须大于轴心镂空、小于封面外缘 */
        const val VINYL_CENTER_RADIUS_MIN = 0.10f
        const val VINYL_CENTER_RADIUS_MAX = 0.42f
        /** 黑胶切歌手势阻尼（灵敏度）：0.15 最钝 … 1.0 最灵敏；0.5 = 历史默认 */
        const val VINYL_GESTURE_DAMPING_MIN = 0.15f
        const val VINYL_GESTURE_DAMPING_MAX = 1.00f
    }
}

private fun Float.finiteCoerceIn(min: Float, max: Float, fallback: Float): Float {
    if (!isFinite()) return fallback
    return coerceIn(min, max)
}

private fun sanitizeCustomPresets(
    presets: List<VinylCustomPreset>,
    fallbackBase: Int,
    fallbackGroove: Int,
): List<VinylCustomPreset> {
    val defaults = defaultVinylCustomPresets(fallbackBase, fallbackGroove)
    return List(PlayerDisplayPrefs.VINYL_CUSTOM_PRESET_COUNT) { i ->
        presets.getOrElse(i) { defaults[i] }
    }
}

class PlayerDisplayPrefsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): PlayerDisplayPrefs {
        val loaded = runCatching { loadUnchecked().sanitized() }.getOrNull()
        if (loaded != null) return loaded
        // 损坏/类型错乱时回落默认并覆写，避免冷启动反复崩溃
        val fallback = PlayerDisplayPrefs()
        runCatching { save(fallback) }
        return fallback
    }

    private fun loadUnchecked(): PlayerDisplayPrefs {
        val legacyBase = prefs.safeInt(
            KEY_VINYL_CUSTOM_BASE,
            Color(0xFF2A2A32).toArgb(),
        )
        val legacyGroove = prefs.safeInt(
            KEY_VINYL_CUSTOM_GROOVE,
            Color(0xFFE8E8F0).toArgb(),
        )
        val presets = decodeVinylCustomPresets(
            raw = prefs.safeString(KEY_VINYL_CUSTOM_PRESETS, null),
            fallbackBase = legacyBase,
            fallbackGroove = legacyGroove,
        )
        val index = prefs.safeInt(KEY_VINYL_CUSTOM_PRESET_INDEX, 0)
            .coerceIn(0, PlayerDisplayPrefs.VINYL_CUSTOM_PRESET_COUNT - 1)
        val active = presets[index]
        val legacyLyricFont = prefs.safeFloat(KEY_FONT, 1f).let {
            if (it.isFinite()) {
                it.coerceIn(PlayerDisplayPrefs.FONT_MIN, PlayerDisplayPrefs.FONT_MAX)
            } else {
                1f
            }
        }
        return PlayerDisplayPrefs(
            rainNightEnabled = prefs.safeBoolean(KEY_RAIN, true),
            fontScale = legacyLyricFont,
            lyricLineSpacingDp = prefs.safeFloat(KEY_LINE_SPACING, 10f),
            lyricPlayedCount = prefs.safeInt(KEY_PLAYED_COUNT, 2),
            lyricUpcomingCount = prefs.safeInt(KEY_UPCOMING_COUNT, 2),
            uiScale = prefs.safeFloat(KEY_UI, 1f),
            vinylOffsetXDp = prefs.safeFloat(KEY_VINYL_X, 0f),
            vinylOffsetYDp = prefs.safeFloat(KEY_VINYL_Y, 0f),
            vinylAbsoluteCenter = prefs.safeBoolean(KEY_VINYL_ABS, false),
            lyricOffsetXDp = prefs.safeFloat(KEY_LYRIC_X, 0f),
            dynamicLyrics = prefs.safeBoolean(KEY_DYNAMIC_LYRICS, false),
            vinylFullCover = prefs.safeBoolean(KEY_VINYL_FULL_COVER, false),
            vinylSizeScale = prefs.safeFloat(
                KEY_VINYL_SIZE_SCALE,
                prefs.safeFloat(KEY_VINYL_RADIUS_SCALE_LEGACY, 1f),
            ),
            vinylOuterScale = prefs.safeFloat(KEY_VINYL_OUTER_SCALE, 1f),
            vinylCenterRadiusFrac = prefs.safeFloat(KEY_VINYL_CENTER_RADIUS, 0.20f),
            vinylColorStyle = VinylColorStyle.fromOrdinal(prefs.safeInt(KEY_VINYL_COLOR, 0)),
            vinylCustomBaseArgb = active.baseArgb,
            vinylCustomGrooveArgb = active.grooveArgb,
            vinylCustomPresets = presets,
            vinylCustomPresetIndex = index,
            transportAlwaysVisible = prefs.safeBoolean(KEY_TRANSPORT_ALWAYS, false),
            transportDocked = prefs.safeBoolean(KEY_TRANSPORT_DOCKED, true),
            transportBottomInsetDp = prefs.safeFloat(KEY_TRANSPORT_BOTTOM_INSET, 16f),
            vinylSongPickEnabled = prefs.safeBoolean(KEY_VINYL_SONG_PICK, false),
            activeHalo = prefs.safeBoolean(KEY_ACTIVE_HALO, false),
            lyricTapAutoPlay = prefs.safeBoolean(KEY_LYRIC_TAP_AUTO_PLAY, false),
            titleAlign = TitleAlignMode.fromOrdinal(
                prefs.safeInt(KEY_TITLE_ALIGN, TitleAlignMode.VINYL.ordinal),
            ),
            titleOffsetYDp = prefs.safeFloat(KEY_TITLE_OFFSET_Y, 0f),
            titleNameStyle = decodeTitleLineStyle(
                prefs.safeString(KEY_TITLE_NAME_STYLE, null),
                TitleLineStyle.NameDefault,
            ),
            titleArtistStyle = decodeTitleLineStyle(
                prefs.safeString(KEY_TITLE_ARTIST_STYLE, null),
                TitleLineStyle.ArtistDefault,
            ),
            titleSourceStyle = decodeTitleLineStyle(
                prefs.safeString(KEY_TITLE_SOURCE_STYLE, null),
                TitleLineStyle.SourceDefault,
            ),
            vinylGestureDamping = prefs.safeFloat(KEY_VINYL_GESTURE_DAMPING, 0.5f),
            lyricPlayingStyle = decodeLyricRoleStyle(
                prefs.safeString(KEY_LYRIC_PLAYING_STYLE, null),
                // 旧版全局字号迁移到各角色
                LyricRoleStyle.PlayingDefault.copy(fontScale = legacyLyricFont),
            ),
            lyricPlayedStyle = decodeLyricRoleStyle(
                prefs.safeString(KEY_LYRIC_PLAYED_STYLE, null),
                LyricRoleStyle.PlayedDefault.copy(fontScale = legacyLyricFont),
            ),
            lyricUnplayedStyle = decodeLyricRoleStyle(
                prefs.safeString(KEY_LYRIC_UNPLAYED_STYLE, null),
                LyricRoleStyle.UnplayedDefault.copy(fontScale = legacyLyricFont),
            ),
        )
    }

    fun save(value: PlayerDisplayPrefs) {
        val v = value.sanitized()
        runCatching {
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
                .putFloat(KEY_VINYL_SIZE_SCALE, v.vinylSizeScale)
                .putFloat(KEY_VINYL_OUTER_SCALE, v.vinylOuterScale)
                .putFloat(KEY_VINYL_CENTER_RADIUS, v.vinylCenterRadiusFrac)
                .putInt(KEY_VINYL_COLOR, v.vinylColorStyle.ordinal)
                .putInt(KEY_VINYL_CUSTOM_BASE, v.vinylCustomBaseArgb)
                .putInt(KEY_VINYL_CUSTOM_GROOVE, v.vinylCustomGrooveArgb)
                .putString(KEY_VINYL_CUSTOM_PRESETS, encodeVinylCustomPresets(v.vinylCustomPresets))
                .putInt(KEY_VINYL_CUSTOM_PRESET_INDEX, v.vinylCustomPresetIndex)
                .putBoolean(KEY_TRANSPORT_ALWAYS, v.transportAlwaysVisible)
                .putBoolean(KEY_TRANSPORT_DOCKED, v.transportDocked)
                .putFloat(KEY_TRANSPORT_BOTTOM_INSET, v.transportBottomInsetDp)
                .putBoolean(KEY_VINYL_SONG_PICK, v.vinylSongPickEnabled)
                .putBoolean(KEY_ACTIVE_HALO, v.activeHalo)
                .putBoolean(KEY_LYRIC_TAP_AUTO_PLAY, v.lyricTapAutoPlay)
                .putInt(KEY_TITLE_ALIGN, v.titleAlign.ordinal)
                .putFloat(KEY_TITLE_OFFSET_Y, v.titleOffsetYDp)
                .putString(KEY_TITLE_NAME_STYLE, encodeTitleLineStyle(v.titleNameStyle))
                .putString(KEY_TITLE_ARTIST_STYLE, encodeTitleLineStyle(v.titleArtistStyle))
                .putString(KEY_TITLE_SOURCE_STYLE, encodeTitleLineStyle(v.titleSourceStyle))
                .putFloat(KEY_VINYL_GESTURE_DAMPING, v.vinylGestureDamping)
                .putString(KEY_LYRIC_PLAYING_STYLE, encodeLyricRoleStyle(v.lyricPlayingStyle))
                .putString(KEY_LYRIC_PLAYED_STYLE, encodeLyricRoleStyle(v.lyricPlayedStyle))
                .putString(KEY_LYRIC_UNPLAYED_STYLE, encodeLyricRoleStyle(v.lyricUnplayedStyle))
                .apply()
        }
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
        private const val KEY_VINYL_SIZE_SCALE = "vinyl_size_scale"
        /** 旧键：迁移为 [KEY_VINYL_SIZE_SCALE] */
        private const val KEY_VINYL_RADIUS_SCALE_LEGACY = "vinyl_radius_scale"
        private const val KEY_VINYL_OUTER_SCALE = "vinyl_outer_scale"
        private const val KEY_VINYL_CENTER_RADIUS = "vinyl_center_radius_frac"
        private const val KEY_VINYL_COLOR = "vinyl_color_style"
        private const val KEY_VINYL_CUSTOM_BASE = "vinyl_custom_base_argb"
        private const val KEY_VINYL_CUSTOM_GROOVE = "vinyl_custom_groove_argb"
        private const val KEY_VINYL_CUSTOM_PRESETS = "vinyl_custom_presets"
        private const val KEY_VINYL_CUSTOM_PRESET_INDEX = "vinyl_custom_preset_index"
        private const val KEY_TRANSPORT_ALWAYS = "transport_always_visible"
        private const val KEY_TRANSPORT_DOCKED = "transport_docked"
        private const val KEY_TRANSPORT_BOTTOM_INSET = "transport_bottom_inset_dp"
        private const val KEY_VINYL_SONG_PICK = "vinyl_song_pick_enabled"
        private const val KEY_ACTIVE_HALO = "active_halo"
        private const val KEY_LYRIC_TAP_AUTO_PLAY = "lyric_tap_auto_play"
        private const val KEY_TITLE_ALIGN = "title_align"
        private const val KEY_TITLE_OFFSET_Y = "title_offset_y_dp"
        private const val KEY_TITLE_NAME_STYLE = "title_name_style"
        private const val KEY_TITLE_ARTIST_STYLE = "title_artist_style"
        private const val KEY_TITLE_SOURCE_STYLE = "title_source_style"
        private const val KEY_VINYL_GESTURE_DAMPING = "vinyl_gesture_damping"
        private const val KEY_LYRIC_PLAYING_STYLE = "lyric_playing_style"
        private const val KEY_LYRIC_PLAYED_STYLE = "lyric_played_style"
        private const val KEY_LYRIC_UNPLAYED_STYLE = "lyric_unplayed_style"
    }
}

private fun SharedPreferences.safeBoolean(key: String, default: Boolean): Boolean =
    try {
        getBoolean(key, default)
    } catch (_: ClassCastException) {
        default
    }

private fun SharedPreferences.safeFloat(key: String, default: Float): Float =
    try {
        getFloat(key, default)
    } catch (_: ClassCastException) {
        default
    }

private fun SharedPreferences.safeInt(key: String, default: Int): Int =
    try {
        getInt(key, default)
    } catch (_: ClassCastException) {
        default
    }

private fun SharedPreferences.safeString(key: String, default: String?): String? =
    try {
        getString(key, default)
    } catch (_: ClassCastException) {
        default
    }
