package com.kite.zmusic.plugin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.kite.zmusic.data.DanmakuRegion
import com.kite.zmusic.data.LyricColorSlot
import com.kite.zmusic.data.LyricOverlayPrefs
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.PreviewLyricAlign
import com.kite.zmusic.data.TitleAlignMode
import com.kite.zmusic.data.TitleColorSlot
import com.kite.zmusic.data.TitleLineStyle
import com.kite.zmusic.data.VinylColorStyle

/**
 * 播放页样式 overlay。按画面分开，只改绘制，不写用户偏好。
 * 专注 / 竖屏 / 动态 / 桌面歌词互不覆盖。
 */
enum class PluginStyleSurface(val wire: String) {
    Focus("focus"),
    Portrait("portrait"),
    Dynamic("dynamic"),
    Overlay("overlay"),
    ;

    companion object {
        fun fromWire(raw: String): PluginStyleSurface? =
            entries.firstOrNull { it.wire == raw }
    }
}

internal data class StyleRolePatch(
    val italic: Boolean? = null,
    val bold: Boolean? = null,
    val colorArgb: Int? = null,
    val fontScale: Float? = null,
) {
    fun isEmpty(): Boolean =
        italic == null && bold == null && colorArgb == null && fontScale == null

    fun merge(next: StyleRolePatch) = StyleRolePatch(
        italic = next.italic ?: italic,
        bold = next.bold ?: bold,
        colorArgb = next.colorArgb ?: colorArgb,
        fontScale = next.fontScale ?: fontScale,
    )

    fun apply(base: LyricRoleStyle): LyricRoleStyle {
        var next = base
        italic?.let { next = next.copy(italic = it) }
        bold?.let { next = next.copy(bold = it) }
        colorArgb?.let {
            next = next.copy(colorSlot = LyricColorSlot.PRESET_0, preset0Argb = it)
        }
        fontScale?.let { next = next.copy(fontScale = it) }
        return next.sanitized()
    }

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        italic?.let { put("italic", it) }
        bold?.let { put("bold", it) }
        colorArgb?.let { put("color", styleHex(it)) }
        fontScale?.let { put("fontScale", it) }
    }
}

internal data class StyleTitleLinePatch(
    val colorArgb: Int? = null,
    val fontScale: Float? = null,
) {
    fun isEmpty(): Boolean = colorArgb == null && fontScale == null

    fun merge(next: StyleTitleLinePatch) = StyleTitleLinePatch(
        colorArgb = next.colorArgb ?: colorArgb,
        fontScale = next.fontScale ?: fontScale,
    )

    fun apply(base: TitleLineStyle): TitleLineStyle {
        var next = base
        colorArgb?.let {
            next = next.copy(colorSlot = TitleColorSlot.PRESET_0, preset0Argb = it)
        }
        fontScale?.let { next = next.copy(fontScale = it) }
        return next.sanitized()
    }

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        colorArgb?.let { put("color", styleHex(it)) }
        fontScale?.let { put("fontScale", it) }
    }
}

internal data class StyleLyricPatch(
    val playing: StyleRolePatch? = null,
    val played: StyleRolePatch? = null,
    val unplayed: StyleRolePatch? = null,
    val lineSpacing: Float? = null,
    val playedCount: Int? = null,
    val upcomingCount: Int? = null,
    val offsetX: Float? = null,
    val offsetY: Float? = null,
    val avoidVinyl: Boolean? = null,
    val backgroundTransparency: Float? = null,
) {
    fun isEmpty(): Boolean =
        playing == null && played == null && unplayed == null &&
            lineSpacing == null && playedCount == null && upcomingCount == null &&
            offsetX == null && offsetY == null && avoidVinyl == null &&
            backgroundTransparency == null

    fun merge(next: StyleLyricPatch) = StyleLyricPatch(
        playing = mergeRole(playing, next.playing),
        played = mergeRole(played, next.played),
        unplayed = mergeRole(unplayed, next.unplayed),
        lineSpacing = next.lineSpacing ?: lineSpacing,
        playedCount = next.playedCount ?: playedCount,
        upcomingCount = next.upcomingCount ?: upcomingCount,
        offsetX = next.offsetX ?: offsetX,
        offsetY = next.offsetY ?: offsetY,
        avoidVinyl = next.avoidVinyl ?: avoidVinyl,
        backgroundTransparency = next.backgroundTransparency ?: backgroundTransparency,
    )

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        playing?.let { put("playing", it.toMap()) }
        played?.let { put("played", it.toMap()) }
        unplayed?.let { put("unplayed", it.toMap()) }
        lineSpacing?.let { put("lineSpacing", it) }
        playedCount?.let { put("playedCount", it) }
        upcomingCount?.let { put("upcomingCount", it) }
        offsetX?.let { put("offsetX", it) }
        offsetY?.let { put("offsetY", it) }
        avoidVinyl?.let { put("avoidVinyl", it) }
        backgroundTransparency?.let { put("backgroundTransparency", it) }
    }
}

internal data class StyleTitlePatch(
    val align: TitleAlignMode? = null,
    val offsetY: Float? = null,
    val name: StyleTitleLinePatch? = null,
    val artist: StyleTitleLinePatch? = null,
    val source: StyleTitleLinePatch? = null,
) {
    fun isEmpty(): Boolean =
        align == null && offsetY == null && name == null && artist == null && source == null

    fun merge(next: StyleTitlePatch) = StyleTitlePatch(
        align = next.align ?: align,
        offsetY = next.offsetY ?: offsetY,
        name = mergeLine(name, next.name),
        artist = mergeLine(artist, next.artist),
        source = mergeLine(source, next.source),
    )

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        align?.let { put("align", titleAlignWire(it)) }
        offsetY?.let { put("offsetY", it) }
        name?.let { put("name", it.toMap()) }
        artist?.let { put("artist", it.toMap()) }
        source?.let { put("source", it.toMap()) }
    }
}

internal data class StyleVinylPatch(
    val offsetX: Float? = null,
    val offsetY: Float? = null,
    val absoluteCenter: Boolean? = null,
    val size: Float? = null,
    val outer: Float? = null,
    val outerEnabled: Boolean? = null,
    val centerRadius: Float? = null,
    val fullCover: Boolean? = null,
    val spinSpeed: Float? = null,
    val color: VinylColorStyle? = null,
    val baseArgb: Int? = null,
    val grooveArgb: Int? = null,
) {
    fun isEmpty(): Boolean =
        offsetX == null && offsetY == null && absoluteCenter == null && size == null &&
            outer == null && outerEnabled == null && centerRadius == null &&
            fullCover == null && spinSpeed == null && color == null &&
            baseArgb == null && grooveArgb == null

    fun merge(next: StyleVinylPatch) = StyleVinylPatch(
        offsetX = next.offsetX ?: offsetX,
        offsetY = next.offsetY ?: offsetY,
        absoluteCenter = next.absoluteCenter ?: absoluteCenter,
        size = next.size ?: size,
        outer = next.outer ?: outer,
        outerEnabled = next.outerEnabled ?: outerEnabled,
        centerRadius = next.centerRadius ?: centerRadius,
        fullCover = next.fullCover ?: fullCover,
        spinSpeed = next.spinSpeed ?: spinSpeed,
        color = next.color ?: color,
        baseArgb = next.baseArgb ?: baseArgb,
        grooveArgb = next.grooveArgb ?: grooveArgb,
    )

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        offsetX?.let { put("offsetX", it) }
        offsetY?.let { put("offsetY", it) }
        absoluteCenter?.let { put("absoluteCenter", it) }
        size?.let { put("size", it) }
        outer?.let { put("outer", it) }
        outerEnabled?.let { put("outerEnabled", it) }
        centerRadius?.let { put("centerRadius", it) }
        fullCover?.let { put("fullCover", it) }
        spinSpeed?.let { put("spinSpeed", it) }
        color?.let { put("color", vinylColorWire(it)) }
        baseArgb?.let { put("base", styleHex(it)) }
        grooveArgb?.let { put("groove", styleHex(it)) }
    }
}

internal data class StyleTransportPatch(
    val docked: Boolean? = null,
    val bottomInset: Float? = null,
    val offsetY: Float? = null,
    val includeControls: Boolean? = null,
) {
    fun isEmpty(): Boolean =
        docked == null && bottomInset == null && offsetY == null && includeControls == null

    fun merge(next: StyleTransportPatch) = StyleTransportPatch(
        docked = next.docked ?: docked,
        bottomInset = next.bottomInset ?: bottomInset,
        offsetY = next.offsetY ?: offsetY,
        includeControls = next.includeControls ?: includeControls,
    )

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        docked?.let { put("docked", it) }
        bottomInset?.let { put("bottomInset", it) }
        offsetY?.let { put("offsetY", it) }
        includeControls?.let { put("includeControls", it) }
    }
}

internal data class StyleDanmakuPatch(
    val enabled: Boolean? = null,
    val density: Int? = null,
    val region: DanmakuRegion? = null,
    val speed: Float? = null,
    val scale: Float? = null,
) {
    fun isEmpty(): Boolean =
        enabled == null && density == null && region == null && speed == null && scale == null

    fun merge(next: StyleDanmakuPatch) = StyleDanmakuPatch(
        enabled = next.enabled ?: enabled,
        density = next.density ?: density,
        region = next.region ?: region,
        speed = next.speed ?: speed,
        scale = next.scale ?: scale,
    )

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        enabled?.let { put("enabled", it) }
        density?.let { put("density", it) }
        region?.let { put("region", danmakuRegionWire(it)) }
        speed?.let { put("speed", it) }
        scale?.let { put("scale", it) }
    }
}

internal data class StylePreviewPatch(
    val enabled: Boolean? = null,
    val count: Int? = null,
    val playingArgb: Int? = null,
    val upcomingArgb: Int? = null,
    val playingFont: Float? = null,
    val upcomingFont: Float? = null,
    val fancy: Boolean? = null,
    val align: PreviewLyricAlign? = null,
    val offsetY: Float? = null,
    val lineSpacing: Float? = null,
) {
    fun isEmpty(): Boolean =
        enabled == null && count == null && playingArgb == null && upcomingArgb == null &&
            playingFont == null && upcomingFont == null && fancy == null && align == null &&
            offsetY == null && lineSpacing == null

    fun merge(next: StylePreviewPatch) = StylePreviewPatch(
        enabled = next.enabled ?: enabled,
        count = next.count ?: count,
        playingArgb = next.playingArgb ?: playingArgb,
        upcomingArgb = next.upcomingArgb ?: upcomingArgb,
        playingFont = next.playingFont ?: playingFont,
        upcomingFont = next.upcomingFont ?: upcomingFont,
        fancy = next.fancy ?: fancy,
        align = next.align ?: align,
        offsetY = next.offsetY ?: offsetY,
        lineSpacing = next.lineSpacing ?: lineSpacing,
    )

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        enabled?.let { put("enabled", it) }
        count?.let { put("count", it) }
        playingArgb?.let { put("playingColor", styleHex(it)) }
        upcomingArgb?.let { put("upcomingColor", styleHex(it)) }
        playingFont?.let { put("playingFont", it) }
        upcomingFont?.let { put("upcomingFont", it) }
        fancy?.let { put("fancy", it) }
        align?.let { put("align", previewAlignWire(it)) }
        offsetY?.let { put("offsetY", it) }
        lineSpacing?.let { put("lineSpacing", it) }
    }
}

internal data class StyleCoverPatch(
    val offsetX: Float? = null,
    val glass: Float? = null,
) {
    fun isEmpty(): Boolean = offsetX == null && glass == null

    fun merge(next: StyleCoverPatch) = StyleCoverPatch(
        offsetX = next.offsetX ?: offsetX,
        glass = next.glass ?: glass,
    )

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        offsetX?.let { put("offsetX", it) }
        glass?.let { put("glass", it) }
    }
}

internal data class StyleFocusPatch(
    val lyric: StyleLyricPatch? = null,
    val title: StyleTitlePatch? = null,
    val vinyl: StyleVinylPatch? = null,
    val uiScale: Float? = null,
    val transport: StyleTransportPatch? = null,
    val danmaku: StyleDanmakuPatch? = null,
) {
    fun isEmpty(): Boolean =
        lyric == null && title == null && vinyl == null && uiScale == null &&
            transport == null && danmaku == null

    fun merge(next: StyleFocusPatch) = StyleFocusPatch(
        lyric = mergeLyric(lyric, next.lyric),
        title = mergeTitle(title, next.title),
        vinyl = mergeVinyl(vinyl, next.vinyl),
        uiScale = next.uiScale ?: uiScale,
        transport = mergeTransport(transport, next.transport),
        danmaku = mergeDanmaku(danmaku, next.danmaku),
    )

    fun apply(user: PlayerDisplayPrefs): PlayerDisplayPrefs {
        var next = user
        lyric?.let { spec ->
            spec.playing?.let { next = next.copy(lyricPlayingStyle = it.apply(next.lyricPlayingStyle)) }
            spec.played?.let { next = next.copy(lyricPlayedStyle = it.apply(next.lyricPlayedStyle)) }
            spec.unplayed?.let { next = next.copy(lyricUnplayedStyle = it.apply(next.lyricUnplayedStyle)) }
            spec.lineSpacing?.let { next = next.copy(lyricLineSpacingDp = it) }
            spec.playedCount?.let { next = next.copy(lyricPlayedCount = it) }
            spec.upcomingCount?.let { next = next.copy(lyricUpcomingCount = it) }
            spec.offsetX?.let { next = next.copy(lyricOffsetXDp = it) }
            spec.avoidVinyl?.let { next = next.copy(dynamicLyrics = it) }
        }
        title?.let { spec ->
            spec.align?.let { next = next.copy(titleAlign = it) }
            spec.offsetY?.let { next = next.copy(titleOffsetYDp = it) }
            spec.name?.let { next = next.copy(titleNameStyle = it.apply(next.titleNameStyle)) }
            spec.artist?.let { next = next.copy(titleArtistStyle = it.apply(next.titleArtistStyle)) }
            spec.source?.let { next = next.copy(titleSourceStyle = it.apply(next.titleSourceStyle)) }
        }
        vinyl?.let { next = next.applyVinyl(it) }
        uiScale?.let { next = next.copy(uiScale = it) }
        transport?.let { spec ->
            spec.docked?.let { next = next.copy(transportDocked = it) }
            spec.bottomInset?.let { next = next.copy(transportBottomInsetDp = it) }
        }
        danmaku?.let { spec ->
            spec.enabled?.let { next = next.copy(danmakuCompanionEnabled = it) }
            spec.density?.let { next = next.copy(danmakuDensity = it) }
            spec.region?.let { next = next.copy(danmakuRegion = it) }
            spec.speed?.let { next = next.copy(danmakuSpeed = it) }
            spec.scale?.let { next = next.copy(danmakuScale = it) }
        }
        return next
    }

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        lyric?.let { put("lyric", it.toMap()) }
        title?.let { put("title", it.toMap()) }
        vinyl?.let { put("vinyl", it.toMap()) }
        uiScale?.let { put("uiScale", it) }
        transport?.let { put("transport", it.toMap()) }
        danmaku?.let { put("danmaku", it.toMap()) }
    }
}

internal data class StylePortraitPatch(
    val lyric: StyleLyricPatch? = null,
    val vinyl: StyleVinylPatch? = null,
    val uiScale: Float? = null,
    val transport: StyleTransportPatch? = null,
    val preview: StylePreviewPatch? = null,
) {
    fun isEmpty(): Boolean =
        lyric == null && vinyl == null && uiScale == null && transport == null && preview == null

    fun merge(next: StylePortraitPatch) = StylePortraitPatch(
        lyric = mergeLyric(lyric, next.lyric),
        vinyl = mergeVinyl(vinyl, next.vinyl),
        uiScale = next.uiScale ?: uiScale,
        transport = mergeTransport(transport, next.transport),
        preview = mergePreview(preview, next.preview),
    )

    fun apply(user: PlayerDisplayPrefs): PlayerDisplayPrefs {
        var next = user
        lyric?.let { spec ->
            spec.playing?.let { next = next.copy(lyricPlayingStyle = it.apply(next.lyricPlayingStyle)) }
            spec.played?.let { next = next.copy(lyricPlayedStyle = it.apply(next.lyricPlayedStyle)) }
            spec.unplayed?.let { next = next.copy(lyricUnplayedStyle = it.apply(next.lyricUnplayedStyle)) }
            spec.lineSpacing?.let { next = next.copy(lyricLineSpacingDp = it) }
            spec.playedCount?.let { next = next.copy(lyricPlayedCount = it) }
            spec.upcomingCount?.let { next = next.copy(lyricUpcomingCount = it) }
            spec.offsetY?.let { next = next.copy(lyricOffsetYDp = it) }
            spec.backgroundTransparency?.let { next = next.copy(lyricBackgroundTransparency = it) }
        }
        vinyl?.let { next = next.applyVinyl(it) }
        uiScale?.let { next = next.copy(uiScale = it) }
        transport?.let { spec ->
            spec.offsetY?.let { next = next.copy(portraitTransportOffsetYDp = it) }
            spec.includeControls?.let { next = next.copy(portraitTransportContainerInclude = it) }
        }
        preview?.let { spec ->
            spec.enabled?.let { next = next.copy(portraitPreviewLyricEnabled = it) }
            spec.count?.let { next = next.copy(portraitPreviewLyricCount = it) }
            spec.playingArgb?.let { next = next.copy(portraitPreviewLyricPlayingArgb = it) }
            spec.upcomingArgb?.let { next = next.copy(portraitPreviewLyricUpcomingArgb = it) }
            spec.playingFont?.let { next = next.copy(portraitPreviewLyricPlayingFontSp = it) }
            spec.upcomingFont?.let { next = next.copy(portraitPreviewLyricUpcomingFontSp = it) }
            spec.fancy?.let { next = next.copy(portraitPreviewLyricFancy = it) }
            spec.align?.let { next = next.copy(portraitPreviewLyricAlign = it) }
            spec.offsetY?.let { next = next.copy(portraitPreviewLyricOffsetYDp = it) }
            spec.lineSpacing?.let { next = next.copy(portraitPreviewLyricLineSpacingDp = it) }
        }
        return next
    }

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        lyric?.let { put("lyric", it.toMap()) }
        vinyl?.let { put("vinyl", it.toMap()) }
        uiScale?.let { put("uiScale", it) }
        transport?.let { put("transport", it.toMap()) }
        preview?.let { put("preview", it.toMap()) }
    }
}

internal data class StyleDynamicPatch(
    val lyric: StyleLyricPatch? = null,
    val cover: StyleCoverPatch? = null,
) {
    fun isEmpty(): Boolean = lyric == null && cover == null

    fun merge(next: StyleDynamicPatch) = StyleDynamicPatch(
        lyric = mergeLyric(lyric, next.lyric),
        cover = mergeCover(cover, next.cover),
    )

    fun apply(user: PlayerDisplayPrefs): PlayerDisplayPrefs {
        var next = user
        lyric?.let { spec ->
            spec.playing?.let {
                next = next.copy(dynamicLyricPlayingStyle = it.apply(next.dynamicLyricPlayingStyle))
            }
            spec.played?.let {
                next = next.copy(dynamicLyricPlayedStyle = it.apply(next.dynamicLyricPlayedStyle))
            }
            spec.unplayed?.let {
                next = next.copy(dynamicLyricUnplayedStyle = it.apply(next.dynamicLyricUnplayedStyle))
            }
            spec.offsetX?.let { next = next.copy(dynamicLyricOffsetXDp = it) }
        }
        cover?.let { spec ->
            spec.offsetX?.let { next = next.copy(dynamicCoverOffsetXDp = it) }
            spec.glass?.let { next = next.copy(dynamicCoverGlass = it) }
        }
        return next
    }

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        lyric?.let { put("lyric", it.toMap()) }
        cover?.let { put("cover", it.toMap()) }
    }
}

internal data class StyleOverlayPatch(
    val playedLines: Int? = null,
    val upcomingLines: Int? = null,
    val windowBackground: Boolean? = null,
    val blur: Int? = null,
    val lyricBackground: Boolean? = null,
    val playedArgb: Int? = null,
    val currentArgb: Int? = null,
    val upcomingArgb: Int? = null,
    val translationArgb: Int? = null,
    val fontSize: Float? = null,
    val align: Int? = null,
    val dynamicWidth: Boolean? = null,
    val widthPercent: Int? = null,
    val ignoreCutout: Boolean? = null,
) {
    fun isEmpty(): Boolean =
        playedLines == null && upcomingLines == null && windowBackground == null &&
            blur == null && lyricBackground == null && playedArgb == null &&
            currentArgb == null && upcomingArgb == null && translationArgb == null &&
            fontSize == null && align == null && dynamicWidth == null &&
            widthPercent == null && ignoreCutout == null

    fun merge(next: StyleOverlayPatch) = StyleOverlayPatch(
        playedLines = next.playedLines ?: playedLines,
        upcomingLines = next.upcomingLines ?: upcomingLines,
        windowBackground = next.windowBackground ?: windowBackground,
        blur = next.blur ?: blur,
        lyricBackground = next.lyricBackground ?: lyricBackground,
        playedArgb = next.playedArgb ?: playedArgb,
        currentArgb = next.currentArgb ?: currentArgb,
        upcomingArgb = next.upcomingArgb ?: upcomingArgb,
        translationArgb = next.translationArgb ?: translationArgb,
        fontSize = next.fontSize ?: fontSize,
        align = next.align ?: align,
        dynamicWidth = next.dynamicWidth ?: dynamicWidth,
        widthPercent = next.widthPercent ?: widthPercent,
        ignoreCutout = next.ignoreCutout ?: ignoreCutout,
    )

    fun apply(user: LyricOverlayPrefs): LyricOverlayPrefs = user.copy(
        playedLines = playedLines ?: user.playedLines,
        upcomingLines = upcomingLines ?: user.upcomingLines,
        windowBackground = windowBackground ?: user.windowBackground,
        blurRadiusPx = blur ?: user.blurRadiusPx,
        lyricBackground = lyricBackground ?: user.lyricBackground,
        playedColorArgb = playedArgb ?: user.playedColorArgb,
        currentColorArgb = currentArgb ?: user.currentColorArgb,
        upcomingColorArgb = upcomingArgb ?: user.upcomingColorArgb,
        translationColorArgb = translationArgb ?: user.translationColorArgb,
        fontSizeSp = fontSize ?: user.fontSizeSp,
        textAlign = align ?: user.textAlign,
        dynamicWidth = dynamicWidth ?: user.dynamicWidth,
        widthPercent = widthPercent ?: user.widthPercent,
        ignoreCutout = ignoreCutout ?: user.ignoreCutout,
    )

    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        playedLines?.let { put("playedLines", it) }
        upcomingLines?.let { put("upcomingLines", it) }
        windowBackground?.let { put("windowBackground", it) }
        blur?.let { put("blur", it) }
        lyricBackground?.let { put("lyricBackground", it) }
        playedArgb?.let { put("playedColor", styleHex(it)) }
        currentArgb?.let { put("currentColor", styleHex(it)) }
        upcomingArgb?.let { put("upcomingColor", styleHex(it)) }
        translationArgb?.let { put("translationColor", styleHex(it)) }
        fontSize?.let { put("fontSize", it) }
        align?.let { put("align", overlayAlignWire(it)) }
        dynamicWidth?.let { put("dynamicWidth", it) }
        widthPercent?.let { put("widthPercent", it) }
        ignoreCutout?.let { put("ignoreCutout", it) }
    }
}

private data class StyleHeld(
    val pluginId: String,
    val patch: Any,
)

object PluginStylePresent {
    private var overlays by mutableStateOf<Map<PluginStyleSurface, StyleHeld>>(emptyMap())

    fun set(pluginId: String, surfaceWire: String, raw: Any?): Boolean {
        val surface = PluginStyleSurface.fromWire(surfaceWire.trim()) ?: return false
        val parsed = parseSurface(surface, raw) ?: return false
        val prev = overlays[surface]?.patch
        val merged = mergeSurface(surface, prev, parsed) ?: return false
        overlays = overlays + (surface to StyleHeld(pluginId, merged))
        Snapshot.sendApplyNotifications()
        return true
    }

    fun clear(pluginId: String, surfaceWire: String? = null, path: String? = null): Boolean {
        if (surfaceWire == null) {
            if (path != null) return false
            val mine = overlays.filterValues { it.pluginId == pluginId }
            if (mine.isEmpty()) return false
            overlays = overlays - mine.keys
            Snapshot.sendApplyNotifications()
            return true
        }
        val surface = PluginStyleSurface.fromWire(surfaceWire.trim()) ?: return false
        val held = overlays[surface] ?: return false
        if (held.pluginId != pluginId) return false
        if (path == null) {
            overlays = overlays - surface
            Snapshot.sendApplyNotifications()
            return true
        }
        when (val dropped = dropSurface(surface, held.patch, path.trim())) {
            is Dropped.Unknown, is Dropped.Absent -> return false
            is Dropped.Value -> {
                overlays = if (dropped.patch == null) {
                    overlays - surface
                } else {
                    overlays + (surface to StyleHeld(pluginId, dropped.patch))
                }
                Snapshot.sendApplyNotifications()
                return true
            }
        }
    }

    fun clearIfOwner(pluginId: String) {
        val mine = overlays.filterValues { it.pluginId == pluginId }
        if (mine.isEmpty()) return
        overlays = overlays - mine.keys
        Snapshot.sendApplyNotifications()
    }

    fun overlayMap(): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        (overlays[PluginStyleSurface.Focus]?.patch as? StyleFocusPatch)?.let {
            out[PluginStyleSurface.Focus.wire] = it.toMap()
        }
        (overlays[PluginStyleSurface.Portrait]?.patch as? StylePortraitPatch)?.let {
            out[PluginStyleSurface.Portrait.wire] = it.toMap()
        }
        (overlays[PluginStyleSurface.Dynamic]?.patch as? StyleDynamicPatch)?.let {
            out[PluginStyleSurface.Dynamic.wire] = it.toMap()
        }
        (overlays[PluginStyleSurface.Overlay]?.patch as? StyleOverlayPatch)?.let {
            out[PluginStyleSurface.Overlay.wire] = it.toMap()
        }
        return out
    }

    fun paint(user: PlayerDisplayPrefs, surface: PluginStyleSurface): PlayerDisplayPrefs {
        val patch = overlays[surface]?.patch ?: return user
        return when (surface) {
            PluginStyleSurface.Focus -> (patch as? StyleFocusPatch)?.apply(user) ?: user
            PluginStyleSurface.Portrait -> (patch as? StylePortraitPatch)?.apply(user) ?: user
            PluginStyleSurface.Dynamic -> (patch as? StyleDynamicPatch)?.apply(user) ?: user
            PluginStyleSurface.Overlay -> user
        }
    }

    fun paintOverlay(user: LyricOverlayPrefs): LyricOverlayPrefs {
        val patch = overlays[PluginStyleSurface.Overlay]?.patch as? StyleOverlayPatch ?: return user
        return patch.apply(user)
    }

    internal fun resetForTests() {
        overlays = emptyMap()
    }

}

private fun parseSurface(surface: PluginStyleSurface, raw: Any?): Any? = when (surface) {
    PluginStyleSurface.Focus -> parseFocus(raw)
    PluginStyleSurface.Portrait -> parsePortrait(raw)
    PluginStyleSurface.Dynamic -> parseDynamic(raw)
    PluginStyleSurface.Overlay -> parseOverlay(raw)
}

private fun mergeSurface(surface: PluginStyleSurface, prev: Any?, parsed: Any): Any? = when (surface) {
    PluginStyleSurface.Focus -> {
        val next = parsed as? StyleFocusPatch ?: return null
        (prev as? StyleFocusPatch)?.merge(next) ?: next
    }
    PluginStyleSurface.Portrait -> {
        val next = parsed as? StylePortraitPatch ?: return null
        (prev as? StylePortraitPatch)?.merge(next) ?: next
    }
    PluginStyleSurface.Dynamic -> {
        val next = parsed as? StyleDynamicPatch ?: return null
        (prev as? StyleDynamicPatch)?.merge(next) ?: next
    }
    PluginStyleSurface.Overlay -> {
        val next = parsed as? StyleOverlayPatch ?: return null
        (prev as? StyleOverlayPatch)?.merge(next) ?: next
    }
}

private fun dropSurface(surface: PluginStyleSurface, patch: Any, path: String): Dropped {
    val parts = stylePath(path) ?: return Dropped.Unknown
    return when (surface) {
        PluginStyleSurface.Focus -> {
            val cur = patch as? StyleFocusPatch ?: return Dropped.Unknown
            dropFocus(cur, parts)
        }
        PluginStyleSurface.Portrait -> {
            val cur = patch as? StylePortraitPatch ?: return Dropped.Unknown
            dropPortrait(cur, parts)
        }
        PluginStyleSurface.Dynamic -> {
            val cur = patch as? StyleDynamicPatch ?: return Dropped.Unknown
            dropDynamic(cur, parts)
        }
        PluginStyleSurface.Overlay -> {
            val cur = patch as? StyleOverlayPatch ?: return Dropped.Unknown
            dropOverlay(cur, parts)
        }
    }
}

private sealed class Dropped {
    data object Unknown : Dropped()
    data object Absent : Dropped()
    class Value(val patch: Any?) : Dropped()
}

private fun PlayerDisplayPrefs.applyVinyl(spec: StyleVinylPatch): PlayerDisplayPrefs {
    var next = this
    spec.offsetX?.let { next = next.copy(vinylOffsetXDp = it) }
    spec.offsetY?.let { next = next.copy(vinylOffsetYDp = it) }
    spec.absoluteCenter?.let { next = next.copy(vinylAbsoluteCenter = it) }
    spec.size?.let { next = next.copy(vinylSizeScale = it) }
    spec.outer?.let { next = next.copy(vinylOuterScale = it) }
    spec.outerEnabled?.let { next = next.copy(vinylOuterEnabled = it) }
    spec.centerRadius?.let { next = next.copy(vinylCenterRadiusFrac = it) }
    spec.fullCover?.let { next = next.copy(vinylFullCover = it) }
    spec.spinSpeed?.let { next = next.copy(vinylSpinSpeed = it) }
    spec.color?.let { next = next.copy(vinylColorStyle = it) }
    if (spec.baseArgb != null || spec.grooveArgb != null) {
        next = next.copy(
            vinylColorStyle = VinylColorStyle.CUSTOM,
            vinylCustomBaseArgb = spec.baseArgb ?: next.vinylCustomBaseArgb,
            vinylCustomGrooveArgb = spec.grooveArgb ?: next.vinylCustomGrooveArgb,
        )
    }
    return next
}

private fun mergeRole(a: StyleRolePatch?, b: StyleRolePatch?): StyleRolePatch? = when {
    a == null -> b
    b == null -> a
    else -> a.merge(b).takeUnless { it.isEmpty() }
}

private fun mergeLine(a: StyleTitleLinePatch?, b: StyleTitleLinePatch?): StyleTitleLinePatch? = when {
    a == null -> b
    b == null -> a
    else -> a.merge(b).takeUnless { it.isEmpty() }
}

private fun mergeLyric(a: StyleLyricPatch?, b: StyleLyricPatch?): StyleLyricPatch? = when {
    a == null -> b
    b == null -> a
    else -> a.merge(b).takeUnless { it.isEmpty() }
}

private fun mergeTitle(a: StyleTitlePatch?, b: StyleTitlePatch?): StyleTitlePatch? = when {
    a == null -> b
    b == null -> a
    else -> a.merge(b).takeUnless { it.isEmpty() }
}

private fun mergeVinyl(a: StyleVinylPatch?, b: StyleVinylPatch?): StyleVinylPatch? = when {
    a == null -> b
    b == null -> a
    else -> a.merge(b).takeUnless { it.isEmpty() }
}

private fun mergeTransport(a: StyleTransportPatch?, b: StyleTransportPatch?): StyleTransportPatch? = when {
    a == null -> b
    b == null -> a
    else -> a.merge(b).takeUnless { it.isEmpty() }
}

private fun mergeDanmaku(a: StyleDanmakuPatch?, b: StyleDanmakuPatch?): StyleDanmakuPatch? = when {
    a == null -> b
    b == null -> a
    else -> a.merge(b).takeUnless { it.isEmpty() }
}

private fun mergePreview(a: StylePreviewPatch?, b: StylePreviewPatch?): StylePreviewPatch? = when {
    a == null -> b
    b == null -> a
    else -> a.merge(b).takeUnless { it.isEmpty() }
}

private fun mergeCover(a: StyleCoverPatch?, b: StyleCoverPatch?): StyleCoverPatch? = when {
    a == null -> b
    b == null -> a
    else -> a.merge(b).takeUnless { it.isEmpty() }
}

private fun styleHex(argb: Int): String {
    val u = argb.toLong() and 0xFFFFFFFFL
    val a = (u ushr 24).toInt()
    return if (a == 255) {
        "#%06X".format((u and 0xFFFFFFL).toInt())
    } else {
        "#%08X".format(u)
    }
}

private fun stylePath(path: String): List<String>? {
    if (path.isEmpty() || path.startsWith('.') || path.endsWith('.') || ".." in path) return null
    val parts = path.split('.')
    if (parts.any { it.isEmpty() }) return null
    return parts
}

private fun styleMap(raw: Any?): Map<String, Any?>? {
    val map = raw as? Map<*, *> ?: return null
    if (map.keys.any { it !is String }) return null
    @Suppress("UNCHECKED_CAST")
    return map as Map<String, Any?>
}

private fun styleNumber(raw: Any?): Double? = when (raw) {
    is Double -> raw.takeIf { it.isFinite() }
    is Float -> raw.toDouble().takeIf { it.isFinite() }
    is Int -> raw.toDouble()
    is Long -> raw.toDouble().takeIf { it.isFinite() }
    is Number -> raw.toDouble().takeIf { it.isFinite() }
    else -> null
}

private fun styleInt(raw: Any?): Int? {
    val n = styleNumber(raw) ?: return null
    if (n < Int.MIN_VALUE.toDouble() || n > Int.MAX_VALUE.toDouble()) return null
    val i = n.toInt()
    if (i.toDouble() != n) return null
    return i
}

private fun styleBool(raw: Any?): Boolean? = raw as? Boolean

private fun styleArgb(raw: Any?): Int? {
    val text = (raw as? String)?.trim()?.removePrefix("#") ?: return null
    val value = text.toLongOrNull(16) ?: return null
    return when (text.length) {
        6 -> (0xFF000000L or value).toInt()
        8 -> value.toInt()
        else -> null
    }
}

private fun styleFloat(raw: Any?, min: Float, max: Float): Float? {
    val n = styleNumber(raw) ?: return null
    return n.toFloat().coerceIn(min, max)
}

private fun styleCount(raw: Any?, min: Int, max: Int): Int? {
    val n = styleInt(raw) ?: return null
    return n.coerceIn(min, max)
}

private val RoleKeys = setOf("italic", "bold", "color", "fontScale")

private fun parseRole(raw: Any?): StyleRolePatch? {
    val map = styleMap(raw) ?: return null
    if (map.isEmpty() || map.keys.any { it !in RoleKeys }) return null
    val italic = if ("italic" in map) styleBool(map["italic"]) ?: return null else null
    val bold = if ("bold" in map) styleBool(map["bold"]) ?: return null else null
    val color = if ("color" in map) styleArgb(map["color"]) ?: return null else null
    val fontScale = if ("fontScale" in map) {
        styleFloat(map["fontScale"], PlayerDisplayPrefs.FONT_MIN, PlayerDisplayPrefs.FONT_MAX) ?: return null
    } else {
        null
    }
    return StyleRolePatch(italic, bold, color, fontScale).takeUnless { it.isEmpty() }
}

private val LineKeys = setOf("color", "fontScale")

private fun parseLine(raw: Any?): StyleTitleLinePatch? {
    val map = styleMap(raw) ?: return null
    if (map.isEmpty() || map.keys.any { it !in LineKeys }) return null
    val color = if ("color" in map) styleArgb(map["color"]) ?: return null else null
    val fontScale = if ("fontScale" in map) {
        styleFloat(map["fontScale"], PlayerDisplayPrefs.FONT_MIN, PlayerDisplayPrefs.FONT_MAX) ?: return null
    } else {
        null
    }
    return StyleTitleLinePatch(color, fontScale).takeUnless { it.isEmpty() }
}

private fun parseLyric(
    raw: Any?,
    allowed: Set<String>,
    countMax: Int,
    offsetXMin: Float = PlayerDisplayPrefs.LYRIC_OFFSET_MIN,
    offsetXMax: Float = PlayerDisplayPrefs.LYRIC_OFFSET_MAX,
): StyleLyricPatch? {
    val map = styleMap(raw) ?: return null
    if (map.isEmpty() || map.keys.any { it !in allowed }) return null
    val playing = if ("playing" in map) parseRole(map["playing"]) ?: return null else null
    val played = if ("played" in map) parseRole(map["played"]) ?: return null else null
    val unplayed = if ("unplayed" in map) parseRole(map["unplayed"]) ?: return null else null
    val lineSpacing = if ("lineSpacing" in map) {
        styleFloat(
            map["lineSpacing"],
            PlayerDisplayPrefs.LINE_SPACING_MIN,
            PlayerDisplayPrefs.LINE_SPACING_MAX,
        ) ?: return null
    } else {
        null
    }
    val playedCount = if ("playedCount" in map) {
        styleCount(map["playedCount"], PlayerDisplayPrefs.LYRIC_AROUND_MIN, countMax) ?: return null
    } else {
        null
    }
    val upcomingCount = if ("upcomingCount" in map) {
        styleCount(map["upcomingCount"], PlayerDisplayPrefs.LYRIC_AROUND_MIN, countMax) ?: return null
    } else {
        null
    }
    val offsetX = if ("offsetX" in map) {
        styleFloat(map["offsetX"], offsetXMin, offsetXMax) ?: return null
    } else {
        null
    }
    val offsetY = if ("offsetY" in map) {
        styleFloat(map["offsetY"], PlayerDisplayPrefs.LYRIC_OFFSET_MIN, PlayerDisplayPrefs.LYRIC_OFFSET_MAX)
            ?: return null
    } else {
        null
    }
    val avoidVinyl = if ("avoidVinyl" in map) styleBool(map["avoidVinyl"]) ?: return null else null
    val backgroundTransparency = if ("backgroundTransparency" in map) {
        styleFloat(
            map["backgroundTransparency"],
            PlayerDisplayPrefs.LYRIC_BG_TRANSPARENCY_MIN,
            PlayerDisplayPrefs.LYRIC_BG_TRANSPARENCY_MAX,
        ) ?: return null
    } else {
        null
    }
    return StyleLyricPatch(
        playing, played, unplayed, lineSpacing, playedCount, upcomingCount,
        offsetX, offsetY, avoidVinyl, backgroundTransparency,
    ).takeUnless { it.isEmpty() }
}

private val TitleKeys = setOf("align", "offsetY", "name", "artist", "source")

private fun parseTitle(raw: Any?): StyleTitlePatch? {
    val map = styleMap(raw) ?: return null
    if (map.isEmpty() || map.keys.any { it !in TitleKeys }) return null
    val align = if ("align" in map) titleAlignFrom(map["align"]) ?: return null else null
    val offsetY = if ("offsetY" in map) {
        styleFloat(map["offsetY"], PlayerDisplayPrefs.TITLE_OFFSET_Y_MIN, PlayerDisplayPrefs.TITLE_OFFSET_Y_MAX)
            ?: return null
    } else {
        null
    }
    val name = if ("name" in map) parseLine(map["name"]) ?: return null else null
    val artist = if ("artist" in map) parseLine(map["artist"]) ?: return null else null
    val source = if ("source" in map) parseLine(map["source"]) ?: return null else null
    return StyleTitlePatch(align, offsetY, name, artist, source).takeUnless { it.isEmpty() }
}

private val VinylKeys = setOf(
    "offsetX", "offsetY", "absoluteCenter", "size", "outer", "outerEnabled",
    "centerRadius", "fullCover", "spinSpeed", "color", "base", "groove",
)

private fun parseVinyl(raw: Any?): StyleVinylPatch? {
    val map = styleMap(raw) ?: return null
    if (map.isEmpty() || map.keys.any { it !in VinylKeys }) return null
    val color = if ("color" in map) vinylColorFrom(map["color"]) ?: return null else null
    val base = if ("base" in map) styleArgb(map["base"]) ?: return null else null
    val groove = if ("groove" in map) styleArgb(map["groove"]) ?: return null else null
    if (color != null && color != VinylColorStyle.CUSTOM && (base != null || groove != null)) return null
    val offsetX = if ("offsetX" in map) {
        styleFloat(map["offsetX"], PlayerDisplayPrefs.VINYL_OFFSET_MIN, PlayerDisplayPrefs.VINYL_OFFSET_MAX)
            ?: return null
    } else {
        null
    }
    val offsetY = if ("offsetY" in map) {
        styleFloat(map["offsetY"], PlayerDisplayPrefs.VINYL_OFFSET_Y_MIN, PlayerDisplayPrefs.VINYL_OFFSET_Y_MAX)
            ?: return null
    } else {
        null
    }
    val absoluteCenter = if ("absoluteCenter" in map) styleBool(map["absoluteCenter"]) ?: return null else null
    val size = if ("size" in map) {
        styleFloat(map["size"], PlayerDisplayPrefs.VINYL_SIZE_SCALE_MIN, PlayerDisplayPrefs.VINYL_SIZE_SCALE_MAX)
            ?: return null
    } else {
        null
    }
    val outer = if ("outer" in map) {
        styleFloat(map["outer"], PlayerDisplayPrefs.VINYL_OUTER_SCALE_MIN, PlayerDisplayPrefs.VINYL_OUTER_SCALE_MAX)
            ?: return null
    } else {
        null
    }
    val outerEnabled = if ("outerEnabled" in map) styleBool(map["outerEnabled"]) ?: return null else null
    val centerRadius = if ("centerRadius" in map) {
        styleFloat(
            map["centerRadius"],
            PlayerDisplayPrefs.VINYL_CENTER_RADIUS_MIN,
            PlayerDisplayPrefs.VINYL_CENTER_RADIUS_MAX,
        ) ?: return null
    } else {
        null
    }
    val fullCover = if ("fullCover" in map) styleBool(map["fullCover"]) ?: return null else null
    val spinSpeed = if ("spinSpeed" in map) {
        styleFloat(
            map["spinSpeed"],
            PlayerDisplayPrefs.VINYL_SPIN_SPEED_MIN,
            PlayerDisplayPrefs.VINYL_SPIN_SPEED_MAX,
        ) ?: return null
    } else {
        null
    }
    val resolvedColor = if (base != null || groove != null) VinylColorStyle.CUSTOM else color
    return StyleVinylPatch(
        offsetX, offsetY, absoluteCenter, size, outer, outerEnabled, centerRadius,
        fullCover, spinSpeed, resolvedColor, base, groove,
    ).takeUnless { it.isEmpty() }
}

private fun parseFocus(raw: Any?): StyleFocusPatch? {
    val map = styleMap(raw) ?: return null
    val allowed = setOf("lyric", "title", "vinyl", "uiScale", "transport", "danmaku")
    if (map.isEmpty() || map.keys.any { it !in allowed }) return null
    val lyric = if ("lyric" in map) {
        parseLyric(
            map["lyric"],
            setOf(
                "playing", "played", "unplayed", "lineSpacing", "playedCount",
                "upcomingCount", "offsetX", "avoidVinyl",
            ),
            PlayerDisplayPrefs.LYRIC_AROUND_MAX,
        ) ?: return null
    } else {
        null
    }
    val title = if ("title" in map) parseTitle(map["title"]) ?: return null else null
    val vinyl = if ("vinyl" in map) parseVinyl(map["vinyl"]) ?: return null else null
    val uiScale = if ("uiScale" in map) {
        styleFloat(map["uiScale"], PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX) ?: return null
    } else {
        null
    }
    val transport = if ("transport" in map) parseFocusTransport(map["transport"]) ?: return null else null
    val danmaku = if ("danmaku" in map) parseDanmaku(map["danmaku"]) ?: return null else null
    return StyleFocusPatch(lyric, title, vinyl, uiScale, transport, danmaku).takeUnless { it.isEmpty() }
}

private fun parsePortrait(raw: Any?): StylePortraitPatch? {
    val map = styleMap(raw) ?: return null
    val allowed = setOf("lyric", "vinyl", "uiScale", "transport", "preview")
    if (map.isEmpty() || map.keys.any { it !in allowed }) return null
    val lyric = if ("lyric" in map) {
        parseLyric(
            map["lyric"],
            setOf(
                "playing", "played", "unplayed", "lineSpacing", "playedCount",
                "upcomingCount", "offsetY", "backgroundTransparency",
            ),
            PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX,
        ) ?: return null
    } else {
        null
    }
    val vinyl = if ("vinyl" in map) parseVinyl(map["vinyl"]) ?: return null else null
    val uiScale = if ("uiScale" in map) {
        styleFloat(map["uiScale"], PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX) ?: return null
    } else {
        null
    }
    val transport = if ("transport" in map) parsePortraitTransport(map["transport"]) ?: return null else null
    val preview = if ("preview" in map) parsePreview(map["preview"]) ?: return null else null
    return StylePortraitPatch(lyric, vinyl, uiScale, transport, preview).takeUnless { it.isEmpty() }
}

private fun parseDynamic(raw: Any?): StyleDynamicPatch? {
    val map = styleMap(raw) ?: return null
    val allowed = setOf("lyric", "cover")
    if (map.isEmpty() || map.keys.any { it !in allowed }) return null
    val lyric = if ("lyric" in map) {
        parseLyric(
            map["lyric"],
            setOf("playing", "played", "unplayed", "offsetX"),
            PlayerDisplayPrefs.LYRIC_AROUND_MAX,
            PlayerDisplayPrefs.DYNAMIC_LYRIC_OFFSET_X_MIN,
            PlayerDisplayPrefs.DYNAMIC_LYRIC_OFFSET_X_MAX,
        ) ?: return null
    } else {
        null
    }
    val cover = if ("cover" in map) parseCover(map["cover"]) ?: return null else null
    return StyleDynamicPatch(lyric, cover).takeUnless { it.isEmpty() }
}

private val OverlayKeys = setOf(
    "playedLines", "upcomingLines", "windowBackground", "blur", "lyricBackground",
    "playedColor", "currentColor", "upcomingColor", "translationColor", "fontSize",
    "align", "dynamicWidth", "widthPercent", "ignoreCutout",
)

private fun parseOverlay(raw: Any?): StyleOverlayPatch? {
    val map = styleMap(raw) ?: return null
    if (map.isEmpty() || map.keys.any { it !in OverlayKeys }) return null
    val playedLines = if ("playedLines" in map) {
        styleCount(map["playedLines"], LyricOverlayPrefs.LINES_MIN, LyricOverlayPrefs.LINES_MAX) ?: return null
    } else {
        null
    }
    val upcomingLines = if ("upcomingLines" in map) {
        styleCount(map["upcomingLines"], LyricOverlayPrefs.LINES_MIN, LyricOverlayPrefs.LINES_MAX) ?: return null
    } else {
        null
    }
    val windowBackground = if ("windowBackground" in map) styleBool(map["windowBackground"]) ?: return null else null
    val blur = if ("blur" in map) {
        styleCount(map["blur"], LyricOverlayPrefs.BLUR_MIN, LyricOverlayPrefs.BLUR_MAX) ?: return null
    } else {
        null
    }
    val lyricBackground = if ("lyricBackground" in map) styleBool(map["lyricBackground"]) ?: return null else null
    val played = if ("playedColor" in map) styleArgb(map["playedColor"]) ?: return null else null
    val current = if ("currentColor" in map) styleArgb(map["currentColor"]) ?: return null else null
    val upcoming = if ("upcomingColor" in map) styleArgb(map["upcomingColor"]) ?: return null else null
    val translation = if ("translationColor" in map) styleArgb(map["translationColor"]) ?: return null else null
    val fontSize = if ("fontSize" in map) {
        styleFloat(map["fontSize"], LyricOverlayPrefs.FONT_MIN, LyricOverlayPrefs.FONT_MAX) ?: return null
    } else {
        null
    }
    val align = if ("align" in map) overlayAlignFrom(map["align"]) ?: return null else null
    val dynamicWidth = if ("dynamicWidth" in map) styleBool(map["dynamicWidth"]) ?: return null else null
    val widthPercent = if ("widthPercent" in map) {
        styleCount(map["widthPercent"], LyricOverlayPrefs.WIDTH_PERCENT_MIN, LyricOverlayPrefs.WIDTH_PERCENT_MAX)
            ?: return null
    } else {
        null
    }
    val ignoreCutout = if ("ignoreCutout" in map) styleBool(map["ignoreCutout"]) ?: return null else null
    return StyleOverlayPatch(
        playedLines, upcomingLines, windowBackground, blur, lyricBackground,
        played, current, upcoming, translation, fontSize, align, dynamicWidth,
        widthPercent, ignoreCutout,
    ).takeUnless { it.isEmpty() }
}

private fun parseFocusTransport(raw: Any?): StyleTransportPatch? {
    val map = styleMap(raw) ?: return null
    val allowed = setOf("docked", "bottomInset")
    if (map.isEmpty() || map.keys.any { it !in allowed }) return null
    val docked = if ("docked" in map) styleBool(map["docked"]) ?: return null else null
    val bottomInset = if ("bottomInset" in map) {
        styleFloat(
            map["bottomInset"],
            PlayerDisplayPrefs.TRANSPORT_BOTTOM_INSET_MIN,
            PlayerDisplayPrefs.TRANSPORT_BOTTOM_INSET_MAX,
        ) ?: return null
    } else {
        null
    }
    return StyleTransportPatch(docked = docked, bottomInset = bottomInset).takeUnless { it.isEmpty() }
}

private fun parsePortraitTransport(raw: Any?): StyleTransportPatch? {
    val map = styleMap(raw) ?: return null
    val allowed = setOf("offsetY", "includeControls")
    if (map.isEmpty() || map.keys.any { it !in allowed }) return null
    val offsetY = if ("offsetY" in map) {
        styleFloat(
            map["offsetY"],
            PlayerDisplayPrefs.PORTRAIT_TRANSPORT_OFFSET_Y_MIN,
            PlayerDisplayPrefs.PORTRAIT_TRANSPORT_OFFSET_Y_MAX,
        ) ?: return null
    } else {
        null
    }
    val includeControls = if ("includeControls" in map) styleBool(map["includeControls"]) ?: return null else null
    return StyleTransportPatch(offsetY = offsetY, includeControls = includeControls).takeUnless { it.isEmpty() }
}

private val DanmakuKeys = setOf("enabled", "density", "region", "speed", "scale")

private fun parseDanmaku(raw: Any?): StyleDanmakuPatch? {
    val map = styleMap(raw) ?: return null
    if (map.isEmpty() || map.keys.any { it !in DanmakuKeys }) return null
    val enabled = if ("enabled" in map) styleBool(map["enabled"]) ?: return null else null
    val density = if ("density" in map) {
        styleCount(map["density"], PlayerDisplayPrefs.DANMAKU_DENSITY_MIN, PlayerDisplayPrefs.DANMAKU_DENSITY_MAX)
            ?: return null
    } else {
        null
    }
    val region = if ("region" in map) danmakuRegionFrom(map["region"]) ?: return null else null
    val speed = if ("speed" in map) {
        styleFloat(map["speed"], PlayerDisplayPrefs.DANMAKU_SPEED_MIN, PlayerDisplayPrefs.DANMAKU_SPEED_MAX)
            ?: return null
    } else {
        null
    }
    val scale = if ("scale" in map) {
        styleFloat(map["scale"], PlayerDisplayPrefs.DANMAKU_SCALE_MIN, PlayerDisplayPrefs.DANMAKU_SCALE_MAX)
            ?: return null
    } else {
        null
    }
    return StyleDanmakuPatch(enabled, density, region, speed, scale).takeUnless { it.isEmpty() }
}

private val PreviewKeys = setOf(
    "enabled", "count", "playingColor", "upcomingColor", "playingFont", "upcomingFont",
    "fancy", "align", "offsetY", "lineSpacing",
)

private fun parsePreview(raw: Any?): StylePreviewPatch? {
    val map = styleMap(raw) ?: return null
    if (map.isEmpty() || map.keys.any { it !in PreviewKeys }) return null
    val enabled = if ("enabled" in map) styleBool(map["enabled"]) ?: return null else null
    val count = if ("count" in map) {
        styleCount(
            map["count"],
            PlayerDisplayPrefs.PREVIEW_LYRIC_COUNT_MIN,
            PlayerDisplayPrefs.PREVIEW_LYRIC_COUNT_MAX,
        ) ?: return null
    } else {
        null
    }
    val playing = if ("playingColor" in map) styleArgb(map["playingColor"]) ?: return null else null
    val upcoming = if ("upcomingColor" in map) styleArgb(map["upcomingColor"]) ?: return null else null
    val playingFont = if ("playingFont" in map) {
        styleFloat(
            map["playingFont"],
            PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MIN,
            PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MAX,
        ) ?: return null
    } else {
        null
    }
    val upcomingFont = if ("upcomingFont" in map) {
        styleFloat(
            map["upcomingFont"],
            PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MIN,
            PlayerDisplayPrefs.PREVIEW_LYRIC_FONT_MAX,
        ) ?: return null
    } else {
        null
    }
    val fancy = if ("fancy" in map) styleBool(map["fancy"]) ?: return null else null
    val align = if ("align" in map) previewAlignFrom(map["align"]) ?: return null else null
    val offsetY = if ("offsetY" in map) {
        styleFloat(
            map["offsetY"],
            PlayerDisplayPrefs.PREVIEW_LYRIC_OFFSET_Y_MIN,
            PlayerDisplayPrefs.PREVIEW_LYRIC_OFFSET_Y_MAX,
        ) ?: return null
    } else {
        null
    }
    val lineSpacing = if ("lineSpacing" in map) {
        styleFloat(
            map["lineSpacing"],
            PlayerDisplayPrefs.PREVIEW_LYRIC_LINE_SPACING_MIN,
            PlayerDisplayPrefs.PREVIEW_LYRIC_LINE_SPACING_MAX,
        ) ?: return null
    } else {
        null
    }
    return StylePreviewPatch(
        enabled, count, playing, upcoming, playingFont, upcomingFont, fancy, align, offsetY, lineSpacing,
    ).takeUnless { it.isEmpty() }
}

private fun parseCover(raw: Any?): StyleCoverPatch? {
    val map = styleMap(raw) ?: return null
    val allowed = setOf("offsetX", "glass")
    if (map.isEmpty() || map.keys.any { it !in allowed }) return null
    val offsetX = if ("offsetX" in map) {
        styleFloat(
            map["offsetX"],
            PlayerDisplayPrefs.DYNAMIC_COVER_OFFSET_X_MIN,
            PlayerDisplayPrefs.DYNAMIC_COVER_OFFSET_X_MAX,
        ) ?: return null
    } else {
        null
    }
    val glass = if ("glass" in map) {
        styleFloat(map["glass"], PlayerDisplayPrefs.DYNAMIC_COVER_GLASS_MIN, PlayerDisplayPrefs.DYNAMIC_COVER_GLASS_MAX)
            ?: return null
    } else {
        null
    }
    return StyleCoverPatch(offsetX, glass).takeUnless { it.isEmpty() }
}

private fun titleAlignFrom(raw: Any?): TitleAlignMode? = when (raw) {
    "left" -> TitleAlignMode.LEFT
    "vinyl" -> TitleAlignMode.VINYL
    "center" -> TitleAlignMode.CENTER
    "lyrics" -> TitleAlignMode.LYRICS
    else -> null
}

private fun titleAlignWire(mode: TitleAlignMode): String = when (mode) {
    TitleAlignMode.LEFT -> "left"
    TitleAlignMode.VINYL -> "vinyl"
    TitleAlignMode.CENTER -> "center"
    TitleAlignMode.LYRICS -> "lyrics"
}

private fun vinylColorFrom(raw: Any?): VinylColorStyle? = when (raw) {
    "black" -> VinylColorStyle.BLACK
    "gold" -> VinylColorStyle.GOLD
    "white" -> VinylColorStyle.WHITE
    "custom" -> VinylColorStyle.CUSTOM
    else -> null
}

private fun vinylColorWire(style: VinylColorStyle): String = when (style) {
    VinylColorStyle.BLACK -> "black"
    VinylColorStyle.GOLD -> "gold"
    VinylColorStyle.WHITE -> "white"
    VinylColorStyle.CUSTOM -> "custom"
}

private fun danmakuRegionFrom(raw: Any?): DanmakuRegion? = when (raw) {
    "top" -> DanmakuRegion.TOP
    "upper" -> DanmakuRegion.UPPER
    "lower" -> DanmakuRegion.LOWER
    "bottom" -> DanmakuRegion.BOTTOM
    "full" -> DanmakuRegion.FULL
    else -> null
}

private fun danmakuRegionWire(region: DanmakuRegion): String = when (region) {
    DanmakuRegion.TOP -> "top"
    DanmakuRegion.UPPER -> "upper"
    DanmakuRegion.LOWER -> "lower"
    DanmakuRegion.BOTTOM -> "bottom"
    DanmakuRegion.FULL -> "full"
}

private fun previewAlignFrom(raw: Any?): PreviewLyricAlign? = when (raw) {
    "left" -> PreviewLyricAlign.LEFT
    "center" -> PreviewLyricAlign.CENTER
    "right" -> PreviewLyricAlign.RIGHT
    else -> null
}

private fun previewAlignWire(align: PreviewLyricAlign): String = when (align) {
    PreviewLyricAlign.LEFT -> "left"
    PreviewLyricAlign.CENTER -> "center"
    PreviewLyricAlign.RIGHT -> "right"
}

private fun overlayAlignFrom(raw: Any?): Int? = when (raw) {
    "left" -> LyricOverlayPrefs.ALIGN_LEFT
    "center" -> LyricOverlayPrefs.ALIGN_CENTER
    "right" -> LyricOverlayPrefs.ALIGN_RIGHT
    else -> null
}

private fun overlayAlignWire(align: Int): String = when (align) {
    LyricOverlayPrefs.ALIGN_CENTER -> "center"
    LyricOverlayPrefs.ALIGN_RIGHT -> "right"
    else -> "left"
}

private fun dropFocus(cur: StyleFocusPatch, parts: List<String>): Dropped {
    if (parts.isEmpty()) return Dropped.Unknown
    return when (parts[0]) {
        "uiScale" -> {
            if (parts.size != 1) return Dropped.Unknown
            if (cur.uiScale == null) Dropped.Absent else Dropped.Value(cur.copy(uiScale = null).takeUnless { it.isEmpty() })
        }
        "lyric" -> dropChild(parts, cur.lyric, ::dropLyric) { next ->
            cur.copy(lyric = next).takeUnless { it.isEmpty() }
        }
        "title" -> dropChild(parts, cur.title, ::dropTitle) { next ->
            cur.copy(title = next).takeUnless { it.isEmpty() }
        }
        "vinyl" -> dropChild(parts, cur.vinyl, ::dropVinyl) { next ->
            cur.copy(vinyl = next).takeUnless { it.isEmpty() }
        }
        "transport" -> dropChild(parts, cur.transport, ::dropTransport) { next ->
            cur.copy(transport = next).takeUnless { it.isEmpty() }
        }
        "danmaku" -> dropChild(parts, cur.danmaku, ::dropDanmaku) { next ->
            cur.copy(danmaku = next).takeUnless { it.isEmpty() }
        }
        else -> Dropped.Unknown
    }
}

private fun dropPortrait(cur: StylePortraitPatch, parts: List<String>): Dropped {
    if (parts.isEmpty()) return Dropped.Unknown
    return when (parts[0]) {
        "uiScale" -> {
            if (parts.size != 1) return Dropped.Unknown
            if (cur.uiScale == null) Dropped.Absent else Dropped.Value(cur.copy(uiScale = null).takeUnless { it.isEmpty() })
        }
        "lyric" -> dropChild(parts, cur.lyric, ::dropLyric) { next ->
            cur.copy(lyric = next).takeUnless { it.isEmpty() }
        }
        "vinyl" -> dropChild(parts, cur.vinyl, ::dropVinyl) { next ->
            cur.copy(vinyl = next).takeUnless { it.isEmpty() }
        }
        "transport" -> dropChild(parts, cur.transport, ::dropTransport) { next ->
            cur.copy(transport = next).takeUnless { it.isEmpty() }
        }
        "preview" -> dropChild(parts, cur.preview, ::dropPreview) { next ->
            cur.copy(preview = next).takeUnless { it.isEmpty() }
        }
        else -> Dropped.Unknown
    }
}

private fun dropDynamic(cur: StyleDynamicPatch, parts: List<String>): Dropped {
    if (parts.isEmpty()) return Dropped.Unknown
    return when (parts[0]) {
        "lyric" -> dropChild(parts, cur.lyric, ::dropLyric) { next ->
            cur.copy(lyric = next).takeUnless { it.isEmpty() }
        }
        "cover" -> dropChild(parts, cur.cover, ::dropCover) { next ->
            cur.copy(cover = next).takeUnless { it.isEmpty() }
        }
        else -> Dropped.Unknown
    }
}

private fun dropOverlay(cur: StyleOverlayPatch, parts: List<String>): Dropped {
    if (parts.size != 1) return Dropped.Unknown
    val next = when (parts[0]) {
        "playedLines" -> cur.copy(playedLines = null)
        "upcomingLines" -> cur.copy(upcomingLines = null)
        "windowBackground" -> cur.copy(windowBackground = null)
        "blur" -> cur.copy(blur = null)
        "lyricBackground" -> cur.copy(lyricBackground = null)
        "playedColor" -> cur.copy(playedArgb = null)
        "currentColor" -> cur.copy(currentArgb = null)
        "upcomingColor" -> cur.copy(upcomingArgb = null)
        "translationColor" -> cur.copy(translationArgb = null)
        "fontSize" -> cur.copy(fontSize = null)
        "align" -> cur.copy(align = null)
        "dynamicWidth" -> cur.copy(dynamicWidth = null)
        "widthPercent" -> cur.copy(widthPercent = null)
        "ignoreCutout" -> cur.copy(ignoreCutout = null)
        else -> return Dropped.Unknown
    }
    if (next == cur) return Dropped.Absent
    return Dropped.Value(next.takeUnless { it.isEmpty() })
}

private inline fun <T> dropChild(
    parts: List<String>,
    current: T?,
    drop: (T, List<String>) -> Dropped,
    wrap: (T?) -> Any?,
): Dropped {
    if (parts.size == 1) {
        if (current == null) return Dropped.Absent
        return Dropped.Value(wrap(null))
    }
    if (current == null) return Dropped.Absent
    return when (val inner = drop(current, parts.drop(1))) {
        is Dropped.Unknown -> Dropped.Unknown
        is Dropped.Absent -> Dropped.Absent
        is Dropped.Value -> {
            @Suppress("UNCHECKED_CAST")
            Dropped.Value(wrap(inner.patch as T?))
        }
    }
}

private fun dropLyric(cur: StyleLyricPatch, parts: List<String>): Dropped {
    if (parts.isEmpty()) return Dropped.Unknown
    return when (parts[0]) {
        "lineSpacing" -> leaf(parts, cur.lineSpacing) {
            cur.copy(lineSpacing = null).takeUnless { it.isEmpty() }
        }
        "playedCount" -> leaf(parts, cur.playedCount) {
            cur.copy(playedCount = null).takeUnless { it.isEmpty() }
        }
        "upcomingCount" -> leaf(parts, cur.upcomingCount) {
            cur.copy(upcomingCount = null).takeUnless { it.isEmpty() }
        }
        "offsetX" -> leaf(parts, cur.offsetX) {
            cur.copy(offsetX = null).takeUnless { it.isEmpty() }
        }
        "offsetY" -> leaf(parts, cur.offsetY) {
            cur.copy(offsetY = null).takeUnless { it.isEmpty() }
        }
        "avoidVinyl" -> leaf(parts, cur.avoidVinyl) {
            cur.copy(avoidVinyl = null).takeUnless { it.isEmpty() }
        }
        "backgroundTransparency" -> leaf(parts, cur.backgroundTransparency) {
            cur.copy(backgroundTransparency = null).takeUnless { it.isEmpty() }
        }
        "playing" -> dropChild(parts, cur.playing, ::dropRole) { next ->
            cur.copy(playing = next).takeUnless { it.isEmpty() }
        }
        "played" -> dropChild(parts, cur.played, ::dropRole) { next ->
            cur.copy(played = next).takeUnless { it.isEmpty() }
        }
        "unplayed" -> dropChild(parts, cur.unplayed, ::dropRole) { next ->
            cur.copy(unplayed = next).takeUnless { it.isEmpty() }
        }
        else -> Dropped.Unknown
    }
}

private fun dropRole(cur: StyleRolePatch, parts: List<String>): Dropped {
    if (parts.size != 1) return Dropped.Unknown
    val next = when (parts[0]) {
        "italic" -> cur.copy(italic = null)
        "bold" -> cur.copy(bold = null)
        "color" -> cur.copy(colorArgb = null)
        "fontScale" -> cur.copy(fontScale = null)
        else -> return Dropped.Unknown
    }
    if (next == cur) return Dropped.Absent
    return Dropped.Value(next.takeUnless { it.isEmpty() })
}

private fun dropTitle(cur: StyleTitlePatch, parts: List<String>): Dropped {
    if (parts.isEmpty()) return Dropped.Unknown
    return when (parts[0]) {
        "align" -> leaf(parts, cur.align) {
            cur.copy(align = null).takeUnless { it.isEmpty() }
        }
        "offsetY" -> leaf(parts, cur.offsetY) {
            cur.copy(offsetY = null).takeUnless { it.isEmpty() }
        }
        "name" -> dropChild(parts, cur.name, ::dropLine) { next ->
            cur.copy(name = next).takeUnless { it.isEmpty() }
        }
        "artist" -> dropChild(parts, cur.artist, ::dropLine) { next ->
            cur.copy(artist = next).takeUnless { it.isEmpty() }
        }
        "source" -> dropChild(parts, cur.source, ::dropLine) { next ->
            cur.copy(source = next).takeUnless { it.isEmpty() }
        }
        else -> Dropped.Unknown
    }
}

private fun dropLine(cur: StyleTitleLinePatch, parts: List<String>): Dropped {
    if (parts.size != 1) return Dropped.Unknown
    val next = when (parts[0]) {
        "color" -> cur.copy(colorArgb = null)
        "fontScale" -> cur.copy(fontScale = null)
        else -> return Dropped.Unknown
    }
    if (next == cur) return Dropped.Absent
    return Dropped.Value(next.takeUnless { it.isEmpty() })
}

private fun dropVinyl(cur: StyleVinylPatch, parts: List<String>): Dropped {
    if (parts.size != 1) return Dropped.Unknown
    val next = when (parts[0]) {
        "offsetX" -> cur.copy(offsetX = null)
        "offsetY" -> cur.copy(offsetY = null)
        "absoluteCenter" -> cur.copy(absoluteCenter = null)
        "size" -> cur.copy(size = null)
        "outer" -> cur.copy(outer = null)
        "outerEnabled" -> cur.copy(outerEnabled = null)
        "centerRadius" -> cur.copy(centerRadius = null)
        "fullCover" -> cur.copy(fullCover = null)
        "spinSpeed" -> cur.copy(spinSpeed = null)
        "color" -> cur.copy(color = null)
        "base" -> cur.copy(baseArgb = null)
        "groove" -> cur.copy(grooveArgb = null)
        else -> return Dropped.Unknown
    }
    if (next == cur) return Dropped.Absent
    return Dropped.Value(next.takeUnless { it.isEmpty() })
}

private fun dropTransport(cur: StyleTransportPatch, parts: List<String>): Dropped {
    if (parts.size != 1) return Dropped.Unknown
    val next = when (parts[0]) {
        "docked" -> cur.copy(docked = null)
        "bottomInset" -> cur.copy(bottomInset = null)
        "offsetY" -> cur.copy(offsetY = null)
        "includeControls" -> cur.copy(includeControls = null)
        else -> return Dropped.Unknown
    }
    if (next == cur) return Dropped.Absent
    return Dropped.Value(next.takeUnless { it.isEmpty() })
}

private fun dropDanmaku(cur: StyleDanmakuPatch, parts: List<String>): Dropped {
    if (parts.size != 1) return Dropped.Unknown
    val next = when (parts[0]) {
        "enabled" -> cur.copy(enabled = null)
        "density" -> cur.copy(density = null)
        "region" -> cur.copy(region = null)
        "speed" -> cur.copy(speed = null)
        "scale" -> cur.copy(scale = null)
        else -> return Dropped.Unknown
    }
    if (next == cur) return Dropped.Absent
    return Dropped.Value(next.takeUnless { it.isEmpty() })
}

private fun dropPreview(cur: StylePreviewPatch, parts: List<String>): Dropped {
    if (parts.size != 1) return Dropped.Unknown
    val next = when (parts[0]) {
        "enabled" -> cur.copy(enabled = null)
        "count" -> cur.copy(count = null)
        "playingColor" -> cur.copy(playingArgb = null)
        "upcomingColor" -> cur.copy(upcomingArgb = null)
        "playingFont" -> cur.copy(playingFont = null)
        "upcomingFont" -> cur.copy(upcomingFont = null)
        "fancy" -> cur.copy(fancy = null)
        "align" -> cur.copy(align = null)
        "offsetY" -> cur.copy(offsetY = null)
        "lineSpacing" -> cur.copy(lineSpacing = null)
        else -> return Dropped.Unknown
    }
    if (next == cur) return Dropped.Absent
    return Dropped.Value(next.takeUnless { it.isEmpty() })
}

private fun dropCover(cur: StyleCoverPatch, parts: List<String>): Dropped {
    if (parts.size != 1) return Dropped.Unknown
    val next = when (parts[0]) {
        "offsetX" -> cur.copy(offsetX = null)
        "glass" -> cur.copy(glass = null)
        else -> return Dropped.Unknown
    }
    if (next == cur) return Dropped.Absent
    return Dropped.Value(next.takeUnless { it.isEmpty() })
}

private fun leaf(parts: List<String>, current: Any?, cleared: () -> Any?): Dropped {
    if (parts.size != 1) return Dropped.Unknown
    if (current == null) return Dropped.Absent
    return Dropped.Value(cleared())
}
