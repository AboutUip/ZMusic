package com.kite.zmusic.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 横屏播放显示偏好的紧凑编解码，供二维码导入/导出。
 *
 * 载荷格式：`ZMDP1:` + Base64URL(GZIP(binary v1))
 */
object PlayerDisplayPrefsCodec {
    private const val PREFIX = "ZMDP1:"
    private const val VERSION: Byte = 1

    fun encode(prefs: PlayerDisplayPrefs): String {
        val sanitized = prefs.sanitized()
        val raw = ByteArrayOutputStream().use { bytes ->
            GZIPOutputStream(bytes).use { gzip ->
                DataOutputStream(gzip).use { out ->
                    writeV1(out, sanitized)
                }
            }
            bytes.toByteArray()
        }
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        return PREFIX + b64
    }

    fun decode(payload: String): Result<PlayerDisplayPrefs> = runCatching {
        val trimmed = payload.trim()
        require(trimmed.startsWith(PREFIX)) { "不是 ZMusic 横屏配置二维码" }
        val b64 = trimmed.removePrefix(PREFIX)
        val compressed = Base64.getUrlDecoder().decode(b64)
        val prefs = ByteArrayInputStream(compressed).use { bytes ->
            GZIPInputStream(bytes).use { gzip ->
                DataInputStream(gzip).use { input ->
                    val version = input.readByte()
                    require(version == VERSION) { "不支持的配置版本（$version）" }
                    readV1(input)
                }
            }
        }
        prefs.sanitized()
    }

    private fun writeV1(out: DataOutputStream, p: PlayerDisplayPrefs) {
        out.writeByte(VERSION.toInt())
        var flags = 0
        if (p.rainNightEnabled) flags = flags or (1 shl 0)
        if (p.vinylAbsoluteCenter) flags = flags or (1 shl 1)
        if (p.dynamicLyrics) flags = flags or (1 shl 2)
        if (p.vinylFullCover) flags = flags or (1 shl 3)
        if (p.transportAlwaysVisible) flags = flags or (1 shl 4)
        if (p.transportDocked) flags = flags or (1 shl 5)
        if (p.vinylSongPickEnabled) flags = flags or (1 shl 6)
        if (p.activeHalo) flags = flags or (1 shl 7)
        if (p.lyricTapAutoPlay) flags = flags or (1 shl 8)
        out.writeInt(flags)

        out.writeFloat(p.fontScale)
        out.writeFloat(p.lyricLineSpacingDp)
        out.writeByte(p.lyricPlayedCount)
        out.writeByte(p.lyricUpcomingCount)
        out.writeFloat(p.uiScale)
        out.writeFloat(p.vinylOffsetXDp)
        out.writeFloat(p.vinylOffsetYDp)
        out.writeFloat(p.lyricOffsetXDp)
        out.writeFloat(p.vinylSizeScale)
        out.writeFloat(p.vinylOuterScale)
        out.writeFloat(p.vinylCenterRadiusFrac)
        out.writeByte(p.vinylColorStyle.ordinal)
        out.writeInt(p.vinylCustomBaseArgb)
        out.writeInt(p.vinylCustomGrooveArgb)
        out.writeByte(p.vinylCustomPresetIndex)
        out.writeByte(PlayerDisplayPrefs.VINYL_CUSTOM_PRESET_COUNT)
        p.vinylCustomPresets.take(PlayerDisplayPrefs.VINYL_CUSTOM_PRESET_COUNT).forEach {
            out.writeInt(it.baseArgb)
            out.writeInt(it.grooveArgb)
        }
        out.writeFloat(p.transportBottomInsetDp)
        out.writeByte(p.titleAlign.ordinal)
        out.writeFloat(p.titleOffsetYDp)
        out.writeFloat(p.vinylGestureDamping)

        writeLyricRole(out, p.lyricPlayingStyle)
        writeLyricRole(out, p.lyricPlayedStyle)
        writeLyricRole(out, p.lyricUnplayedStyle)
        writeTitleLine(out, p.titleNameStyle)
        writeTitleLine(out, p.titleArtistStyle)
        writeTitleLine(out, p.titleSourceStyle)
    }

    private fun readV1(input: DataInputStream): PlayerDisplayPrefs {
        val flags = input.readInt()
        fun flag(bit: Int): Boolean = (flags and (1 shl bit)) != 0

        val fontScale = input.readFloat()
        val lyricLineSpacingDp = input.readFloat()
        val lyricPlayedCount = input.readUnsignedByte()
        val lyricUpcomingCount = input.readUnsignedByte()
        val uiScale = input.readFloat()
        val vinylOffsetXDp = input.readFloat()
        val vinylOffsetYDp = input.readFloat()
        val lyricOffsetXDp = input.readFloat()
        val vinylSizeScale = input.readFloat()
        val vinylOuterScale = input.readFloat()
        val vinylCenterRadiusFrac = input.readFloat()
        val vinylColorStyle = VinylColorStyle.fromOrdinal(input.readUnsignedByte())
        val vinylCustomBaseArgb = input.readInt()
        val vinylCustomGrooveArgb = input.readInt()
        val vinylCustomPresetIndex = input.readUnsignedByte()
        val presetCount = input.readUnsignedByte().coerceIn(0, 16)
        val presets = buildList {
            repeat(presetCount) {
                add(VinylCustomPreset(input.readInt(), input.readInt()))
            }
        }
        val transportBottomInsetDp = input.readFloat()
        val titleAlign = TitleAlignMode.fromOrdinal(input.readUnsignedByte())
        val titleOffsetYDp = input.readFloat()
        val vinylGestureDamping = input.readFloat()

        val lyricPlayingStyle = readLyricRole(input, LyricRoleStyle.PlayingDefault)
        val lyricPlayedStyle = readLyricRole(input, LyricRoleStyle.PlayedDefault)
        val lyricUnplayedStyle = readLyricRole(input, LyricRoleStyle.UnplayedDefault)
        val titleNameStyle = readTitleLine(input, TitleLineStyle.NameDefault)
        val titleArtistStyle = readTitleLine(input, TitleLineStyle.ArtistDefault)
        val titleSourceStyle = readTitleLine(input, TitleLineStyle.SourceDefault)

        return PlayerDisplayPrefs(
            rainNightEnabled = flag(0),
            fontScale = fontScale,
            lyricLineSpacingDp = lyricLineSpacingDp,
            lyricPlayedCount = lyricPlayedCount,
            lyricUpcomingCount = lyricUpcomingCount,
            uiScale = uiScale,
            vinylOffsetXDp = vinylOffsetXDp,
            vinylOffsetYDp = vinylOffsetYDp,
            vinylAbsoluteCenter = flag(1),
            lyricOffsetXDp = lyricOffsetXDp,
            dynamicLyrics = flag(2),
            vinylFullCover = flag(3),
            vinylSizeScale = vinylSizeScale,
            vinylOuterScale = vinylOuterScale,
            vinylCenterRadiusFrac = vinylCenterRadiusFrac,
            vinylColorStyle = vinylColorStyle,
            vinylCustomBaseArgb = vinylCustomBaseArgb,
            vinylCustomGrooveArgb = vinylCustomGrooveArgb,
            vinylCustomPresets = sanitizeImportedPresets(
                presets,
                vinylCustomBaseArgb,
                vinylCustomGrooveArgb,
            ),
            vinylCustomPresetIndex = vinylCustomPresetIndex,
            transportAlwaysVisible = flag(4),
            transportDocked = flag(5),
            transportBottomInsetDp = transportBottomInsetDp,
            vinylSongPickEnabled = flag(6),
            activeHalo = flag(7),
            lyricTapAutoPlay = flag(8),
            titleAlign = titleAlign,
            titleOffsetYDp = titleOffsetYDp,
            titleNameStyle = titleNameStyle,
            titleArtistStyle = titleArtistStyle,
            titleSourceStyle = titleSourceStyle,
            vinylGestureDamping = vinylGestureDamping,
            lyricPlayingStyle = lyricPlayingStyle,
            lyricPlayedStyle = lyricPlayedStyle,
            lyricUnplayedStyle = lyricUnplayedStyle,
        )
    }

    private fun writeLyricRole(out: DataOutputStream, style: LyricRoleStyle) {
        var bits = 0
        if (style.italic) bits = bits or 1
        if (style.bold) bits = bits or 2
        out.writeByte(bits)
        out.writeByte(style.colorSlot.ordinal)
        out.writeInt(style.preset0Argb)
        out.writeInt(style.preset1Argb)
        out.writeInt(style.preset2Argb)
        out.writeFloat(style.sanitizedFontScale())
    }

    private fun readLyricRole(input: DataInputStream, fallback: LyricRoleStyle): LyricRoleStyle {
        val bits = input.readUnsignedByte()
        val slot = LyricColorSlot.fromOrdinal(input.readUnsignedByte())
        return LyricRoleStyle(
            italic = bits and 1 != 0,
            bold = bits and 2 != 0,
            colorSlot = slot,
            preset0Argb = input.readInt(),
            preset1Argb = input.readInt(),
            preset2Argb = input.readInt(),
            fontScale = input.readFloat(),
        ).sanitized()
    }

    private fun writeTitleLine(out: DataOutputStream, style: TitleLineStyle) {
        out.writeByte(style.colorSlot.ordinal)
        out.writeInt(style.preset0Argb)
        out.writeInt(style.preset1Argb)
        out.writeFloat(style.sanitizedFontScale())
    }

    private fun readTitleLine(input: DataInputStream, fallback: TitleLineStyle): TitleLineStyle {
        return TitleLineStyle(
            colorSlot = TitleColorSlot.fromOrdinal(input.readUnsignedByte()),
            preset0Argb = input.readInt(),
            preset1Argb = input.readInt(),
            fontScale = input.readFloat(),
        ).sanitized()
    }

    private fun sanitizeImportedPresets(
        presets: List<VinylCustomPreset>,
        fallbackBase: Int,
        fallbackGroove: Int,
    ): List<VinylCustomPreset> {
        val defaults = defaultVinylCustomPresets(fallbackBase, fallbackGroove)
        return List(PlayerDisplayPrefs.VINYL_CUSTOM_PRESET_COUNT) { i ->
            presets.getOrElse(i) { defaults[i] }
        }
    }
}

/** 导入时在数值项间插值，开关/枚举在中点切换，便于动画覆盖。 */
fun lerpPlayerDisplayPrefs(
    from: PlayerDisplayPrefs,
    to: PlayerDisplayPrefs,
    t: Float,
): PlayerDisplayPrefs {
    val u = t.coerceIn(0f, 1f)
    fun lf(a: Float, b: Float): Float = a + (b - a) * u
    fun pick(a: Boolean, b: Boolean): Boolean = if (u < 0.5f) a else b
    fun <T> pick(a: T, b: T): T = if (u < 0.5f) a else b
    fun li(a: Int, b: Int): Int = (a + (b - a) * u).toInt()

    return PlayerDisplayPrefs(
        rainNightEnabled = pick(from.rainNightEnabled, to.rainNightEnabled),
        fontScale = lf(from.fontScale, to.fontScale),
        lyricLineSpacingDp = lf(from.lyricLineSpacingDp, to.lyricLineSpacingDp),
        lyricPlayedCount = li(from.lyricPlayedCount, to.lyricPlayedCount),
        lyricUpcomingCount = li(from.lyricUpcomingCount, to.lyricUpcomingCount),
        uiScale = lf(from.uiScale, to.uiScale),
        vinylOffsetXDp = lf(from.vinylOffsetXDp, to.vinylOffsetXDp),
        vinylOffsetYDp = lf(from.vinylOffsetYDp, to.vinylOffsetYDp),
        vinylAbsoluteCenter = pick(from.vinylAbsoluteCenter, to.vinylAbsoluteCenter),
        lyricOffsetXDp = lf(from.lyricOffsetXDp, to.lyricOffsetXDp),
        dynamicLyrics = pick(from.dynamicLyrics, to.dynamicLyrics),
        vinylFullCover = pick(from.vinylFullCover, to.vinylFullCover),
        vinylSizeScale = lf(from.vinylSizeScale, to.vinylSizeScale),
        vinylOuterScale = lf(from.vinylOuterScale, to.vinylOuterScale),
        vinylCenterRadiusFrac = lf(from.vinylCenterRadiusFrac, to.vinylCenterRadiusFrac),
        vinylColorStyle = pick(from.vinylColorStyle, to.vinylColorStyle),
        vinylCustomBaseArgb = lerpArgb(from.vinylCustomBaseArgb, to.vinylCustomBaseArgb, u),
        vinylCustomGrooveArgb = lerpArgb(from.vinylCustomGrooveArgb, to.vinylCustomGrooveArgb, u),
        vinylCustomPresets = List(PlayerDisplayPrefs.VINYL_CUSTOM_PRESET_COUNT) { i ->
            val a = from.vinylCustomPresets.getOrElse(i) {
                VinylCustomPreset(from.vinylCustomBaseArgb, from.vinylCustomGrooveArgb)
            }
            val b = to.vinylCustomPresets.getOrElse(i) {
                VinylCustomPreset(to.vinylCustomBaseArgb, to.vinylCustomGrooveArgb)
            }
            VinylCustomPreset(
                baseArgb = lerpArgb(a.baseArgb, b.baseArgb, u),
                grooveArgb = lerpArgb(a.grooveArgb, b.grooveArgb, u),
            )
        },
        vinylCustomPresetIndex = pick(from.vinylCustomPresetIndex, to.vinylCustomPresetIndex),
        transportAlwaysVisible = pick(from.transportAlwaysVisible, to.transportAlwaysVisible),
        transportDocked = pick(from.transportDocked, to.transportDocked),
        transportBottomInsetDp = lf(from.transportBottomInsetDp, to.transportBottomInsetDp),
        vinylSongPickEnabled = pick(from.vinylSongPickEnabled, to.vinylSongPickEnabled),
        activeHalo = pick(from.activeHalo, to.activeHalo),
        lyricTapAutoPlay = pick(from.lyricTapAutoPlay, to.lyricTapAutoPlay),
        titleAlign = pick(from.titleAlign, to.titleAlign),
        titleOffsetYDp = lf(from.titleOffsetYDp, to.titleOffsetYDp),
        titleNameStyle = lerpTitleLine(from.titleNameStyle, to.titleNameStyle, u),
        titleArtistStyle = lerpTitleLine(from.titleArtistStyle, to.titleArtistStyle, u),
        titleSourceStyle = lerpTitleLine(from.titleSourceStyle, to.titleSourceStyle, u),
        vinylGestureDamping = lf(from.vinylGestureDamping, to.vinylGestureDamping),
        lyricPlayingStyle = lerpLyricRole(from.lyricPlayingStyle, to.lyricPlayingStyle, u),
        lyricPlayedStyle = lerpLyricRole(from.lyricPlayedStyle, to.lyricPlayedStyle, u),
        lyricUnplayedStyle = lerpLyricRole(from.lyricUnplayedStyle, to.lyricUnplayedStyle, u),
    )
}

private fun lerpArgb(a: Int, b: Int, t: Float): Int {
    val aa = (a ushr 24) and 0xFF
    val ar = (a ushr 16) and 0xFF
    val ag = (a ushr 8) and 0xFF
    val ab = a and 0xFF
    val ba = (b ushr 24) and 0xFF
    val br = (b ushr 16) and 0xFF
    val bg = (b ushr 8) and 0xFF
    val bb = b and 0xFF
    fun c(x: Int, y: Int): Int = (x + (y - x) * t).toInt().coerceIn(0, 255)
    return (c(aa, ba) shl 24) or (c(ar, br) shl 16) or (c(ag, bg) shl 8) or c(ab, bb)
}

private fun lerpLyricRole(a: LyricRoleStyle, b: LyricRoleStyle, t: Float): LyricRoleStyle {
    val snap = t >= 0.5f
    return LyricRoleStyle(
        italic = if (snap) b.italic else a.italic,
        bold = if (snap) b.bold else a.bold,
        colorSlot = if (snap) b.colorSlot else a.colorSlot,
        preset0Argb = lerpArgb(a.preset0Argb, b.preset0Argb, t),
        preset1Argb = lerpArgb(a.preset1Argb, b.preset1Argb, t),
        preset2Argb = lerpArgb(a.preset2Argb, b.preset2Argb, t),
        fontScale = a.fontScale + (b.fontScale - a.fontScale) * t,
    )
}

private fun lerpTitleLine(a: TitleLineStyle, b: TitleLineStyle, t: Float): TitleLineStyle {
    val snap = t >= 0.5f
    return TitleLineStyle(
        colorSlot = if (snap) b.colorSlot else a.colorSlot,
        preset0Argb = lerpArgb(a.preset0Argb, b.preset0Argb, t),
        preset1Argb = lerpArgb(a.preset1Argb, b.preset1Argb, t),
        fontScale = a.fontScale + (b.fontScale - a.fontScale) * t,
    )
}
