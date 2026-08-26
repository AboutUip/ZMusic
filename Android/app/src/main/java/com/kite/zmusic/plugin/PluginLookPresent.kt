package com.kite.zmusic.plugin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kite.zmusic.data.AppAppearance
import com.kite.zmusic.data.ChromeGlassMode
import com.kite.zmusic.data.ChromeGlassStyle
import com.kite.zmusic.data.ChromeWallpaperState
import com.kite.zmusic.data.ChromeWallpaperStore
import com.kite.zmusic.data.ChromeWallpaperSurface
import com.kite.zmusic.data.PlayerBackgroundPreset
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.WallpaperFrame
import com.kite.zmusic.ui.player.VinylPlateColors
import com.kite.zmusic.ui.theme.colorToHex
import com.kite.zmusic.ui.theme.parseThemeColor
import java.io.File

data class LookPackFrame(
    val pack: String,
    val path: String,
    val offsetX: Float = 0.5f,
    val offsetY: Float = 0.5f,
    val scale: Float = 1f,
) {
    fun toWallpaperFrame(): WallpaperFrame =
        WallpaperFrame(
            imagePath = path,
            offsetX = offsetX,
            offsetY = offsetY,
            scale = scale,
            coverBaseline = true,
        )

    fun toPlayerPreset(): PlayerBackgroundPreset =
        PlayerBackgroundPreset(
            imagePath = path,
            offsetX = offsetX,
            offsetY = offsetY,
            scale = scale,
            locked = true,
            coverFill = true,
        )

    fun asMap(): Map<String, Any?> = linkedMapOf(
        "pack" to pack,
        "offsetX" to offsetX,
        "offsetY" to offsetY,
        "scale" to scale,
    )
}

data class LookGlassPartial(
    val mode: ChromeGlassMode? = null,
    val refraction: Float? = null,
    val blur: Float? = null,
) {
    fun isEmpty(): Boolean = mode == null && refraction == null && blur == null

    fun merge(next: LookGlassPartial) = LookGlassPartial(
        mode = next.mode ?: mode,
        refraction = next.refraction ?: refraction,
        blur = next.blur ?: blur,
    )

    fun applyTo(user: ChromeGlassStyle) = ChromeGlassStyle(
        mode = mode ?: user.mode,
        refraction = refraction ?: user.refraction,
        blur = blur ?: user.blur,
    )
}

data class LookWallpaperPartial(
    val enabled: Boolean? = null,
    val itemChrome: ChromeGlassMode? = null,
    val coverage: Set<ChromeWallpaperSurface>? = null,
    val genericPortrait: LookPackFrame? = null,
    val genericLandscape: LookPackFrame? = null,
    val portraits: Map<ChromeWallpaperSurface, LookPackFrame> = emptyMap(),
    val landscapes: Map<ChromeWallpaperSurface, LookPackFrame> = emptyMap(),
) {
    fun isEmpty(): Boolean =
        enabled == null && itemChrome == null && coverage == null &&
            genericPortrait == null && genericLandscape == null &&
            portraits.isEmpty() && landscapes.isEmpty()

    fun hasImage(): Boolean =
        genericPortrait != null || genericLandscape != null ||
            portraits.isNotEmpty() || landscapes.isNotEmpty()

    fun merge(next: LookWallpaperPartial) = LookWallpaperPartial(
        enabled = next.enabled ?: enabled,
        itemChrome = next.itemChrome ?: itemChrome,
        coverage = next.coverage ?: coverage,
        genericPortrait = next.genericPortrait ?: genericPortrait,
        genericLandscape = next.genericLandscape ?: genericLandscape,
        portraits = portraits + next.portraits,
        landscapes = landscapes + next.landscapes,
    )

    fun applyTo(user: ChromeWallpaperState): ChromeWallpaperState {
        val images = hasImage()
        return if (images) {
            ChromeWallpaperState(
                enabled = enabled ?: true,
                coverage = coverage ?: ChromeWallpaperSurface.entries.toSet(),
                itemChrome = itemChrome ?: user.itemChrome,
                genericPortrait = genericPortrait?.toWallpaperFrame() ?: WallpaperFrame(),
                genericLandscape = genericLandscape?.toWallpaperFrame() ?: WallpaperFrame(),
                portraits = portraits.mapValues { it.value.toWallpaperFrame() },
                landscapes = landscapes.mapValues { it.value.toWallpaperFrame() },
            )
        } else {
            ChromeWallpaperState(
                enabled = enabled ?: user.enabled,
                coverage = coverage ?: user.coverage,
                itemChrome = itemChrome ?: user.itemChrome,
                genericPortrait = user.genericPortrait,
                genericLandscape = user.genericLandscape,
                portraits = user.portraits,
                landscapes = user.landscapes,
            )
        }
    }
}

data class LookVinylPartial(
    val style: CollectionVinylStyle? = null,
    val base: Color? = null,
    val groove: Color? = null,
) {
    fun isEmpty(): Boolean = style == null && base == null && groove == null

    fun merge(next: LookVinylPartial): LookVinylPartial {
        val nextStyle = next.style ?: style
        val custom = nextStyle == CollectionVinylStyle.Custom
        return LookVinylPartial(
            style = nextStyle,
            base = if (custom) (next.base ?: base) else null,
            groove = if (custom) (next.groove ?: groove) else null,
        )
    }

    fun plateColors(): VinylPlateColors = when (style ?: CollectionVinylStyle.Black) {
        CollectionVinylStyle.Black -> VinylPlateColors.Black
        CollectionVinylStyle.Gold -> VinylPlateColors.Gold
        CollectionVinylStyle.White -> VinylPlateColors.White
        CollectionVinylStyle.Custom -> VinylPlateColors.custom(
            (base ?: Color(0xFF101012)).toArgb(),
            (groove ?: Color.White).toArgb(),
        )
    }
}

data class LookAtmospherePartial(
    val rainNight: Boolean? = null,
    val halo: Boolean? = null,
) {
    fun isEmpty(): Boolean = rainNight == null && halo == null

    fun merge(next: LookAtmospherePartial) = LookAtmospherePartial(
        rainNight = next.rainNight ?: rainNight,
        halo = next.halo ?: halo,
    )
}

sealed class LookPatch {
    data class Appearance(val mode: AppAppearance) : LookPatch()
    data class Glass(val spec: LookGlassPartial) : LookPatch()
    data class Wallpaper(val spec: LookWallpaperPartial) : LookPatch()
    data class Vinyl(val spec: LookVinylPartial) : LookPatch()
    data class Atmosphere(val spec: LookAtmospherePartial) : LookPatch()
    data class PlayerBackground(val frame: LookPackFrame) : LookPatch()
    data class Profile(val frame: LookPackFrame) : LookPatch()

    fun merge(next: LookPatch): LookPatch = when {
        this is Glass && next is Glass -> Glass(spec.merge(next.spec))
        this is Wallpaper && next is Wallpaper -> Wallpaper(spec.merge(next.spec))
        this is Vinyl && next is Vinyl -> Vinyl(spec.merge(next.spec))
        this is Atmosphere && next is Atmosphere -> Atmosphere(spec.merge(next.spec))
        else -> next
    }
}

private data class LookRegionOverlay(
    val pluginId: String,
    val patch: LookPatch,
)

/**
 * 外观 overlay。每区一份，Compose 读 [appearance] / [glass] / [wallpaper] 等会订阅。
 */
object PluginLookPresent {
    private var overlays by mutableStateOf<Map<String, LookRegionOverlay>>(emptyMap())

    fun ownerOf(region: String): String? = overlays[region]?.pluginId

    fun appearance(): AppAppearance? =
        (overlays[PluginLookRegions.APPEARANCE]?.patch as? LookPatch.Appearance)?.mode

    fun glass(user: ChromeGlassStyle): ChromeGlassStyle {
        val spec = (overlays[PluginLookRegions.CHROME_GLASS]?.patch as? LookPatch.Glass)?.spec
            ?: return user
        return spec.applyTo(user)
    }

    fun wallpaper(user: ChromeWallpaperState): ChromeWallpaperState {
        val spec = (overlays[PluginLookRegions.CHROME_WALLPAPER]?.patch as? LookPatch.Wallpaper)?.spec
            ?: return user
        return spec.applyTo(user)
    }

    fun playerVinyl(): LookVinylPartial? =
        (overlays[PluginLookRegions.PLAYER_VINYL]?.patch as? LookPatch.Vinyl)?.spec

    fun atmosphereRain(user: Boolean): Boolean =
        (overlays[PluginLookRegions.PLAYER_ATMOSPHERE]?.patch as? LookPatch.Atmosphere)
            ?.spec?.rainNight ?: user

    fun atmosphereHalo(user: Boolean): Boolean =
        (overlays[PluginLookRegions.PLAYER_ATMOSPHERE]?.patch as? LookPatch.Atmosphere)
            ?.spec?.halo ?: user

    fun playerBackground(): PlayerBackgroundPreset? =
        (overlays[PluginLookRegions.PLAYER_BACKGROUND]?.patch as? LookPatch.PlayerBackground)
            ?.frame?.toPlayerPreset()

    fun profilePath(): String? =
        (overlays[PluginLookRegions.LIBRARY_PROFILE]?.patch as? LookPatch.Profile)?.frame?.path

    fun set(pluginId: String, region: String, patch: LookPatch): Boolean {
        if (region !in PluginLookRegions.KNOWN) return false
        if (!patchMatches(region, patch)) return false
        val nextPatch = overlays[region]?.patch?.merge(patch) ?: patch
        if (nextPatch is LookPatch.Vinyl) {
            val spec = nextPatch.spec
            if (spec.style == CollectionVinylStyle.Custom &&
                (spec.base == null || spec.groove == null)
            ) {
                return false
            }
        }
        overlays = overlays + (region to LookRegionOverlay(pluginId, nextPatch))
        Snapshot.sendApplyNotifications()
        return true
    }

    fun clear(pluginId: String, region: String?): Boolean {
        if (region != null) {
            if (region !in PluginLookRegions.KNOWN) return false
            val cur = overlays[region] ?: return false
            if (cur.pluginId != pluginId) return false
            overlays = overlays - region
            Snapshot.sendApplyNotifications()
            return true
        }
        val mine = overlays.filterValues { it.pluginId == pluginId }
        if (mine.isEmpty()) return false
        overlays = overlays - mine.keys
        Snapshot.sendApplyNotifications()
        return true
    }

    fun clearIfOwner(pluginId: String) {
        val mine = overlays.filterValues { it.pluginId == pluginId }
        if (mine.isEmpty()) return
        overlays = overlays - mine.keys
        Snapshot.sendApplyNotifications()
    }

    fun effectiveMap(): Map<String, Map<String, Any?>> =
        PluginLookRegions.KNOWN.associateWith { describe(it) }

    internal fun resetForTests() {
        overlays = emptyMap()
    }

    private fun patchMatches(region: String, patch: LookPatch): Boolean = when (region) {
        PluginLookRegions.APPEARANCE -> patch is LookPatch.Appearance
        PluginLookRegions.CHROME_GLASS -> patch is LookPatch.Glass
        PluginLookRegions.CHROME_WALLPAPER -> patch is LookPatch.Wallpaper
        PluginLookRegions.PLAYER_VINYL -> patch is LookPatch.Vinyl
        PluginLookRegions.PLAYER_ATMOSPHERE -> patch is LookPatch.Atmosphere
        PluginLookRegions.PLAYER_BACKGROUND -> patch is LookPatch.PlayerBackground
        PluginLookRegions.LIBRARY_PROFILE -> patch is LookPatch.Profile
        else -> false
    }

    private fun describe(region: String): Map<String, Any?> {
        val patch = overlays[region]?.patch
        return when (region) {
            PluginLookRegions.APPEARANCE -> linkedMapOf(
                "mode" to appearanceWire(
                    (patch as? LookPatch.Appearance)?.mode ?: AppAppearance.System,
                ),
            )
            PluginLookRegions.CHROME_GLASS -> {
                val spec = (patch as? LookPatch.Glass)?.spec
                val applied = spec?.applyTo(ChromeGlassStyle.Default) ?: ChromeGlassStyle.Default
                linkedMapOf(
                    "mode" to glassWire(applied.mode),
                    "refraction" to applied.refraction,
                    "blur" to applied.blur,
                )
            }
            PluginLookRegions.CHROME_WALLPAPER -> wallpaperMap(
                (patch as? LookPatch.Wallpaper)?.spec ?: LookWallpaperPartial(),
            )
            PluginLookRegions.PLAYER_VINYL -> vinylMap(
                (patch as? LookPatch.Vinyl)?.spec ?: LookVinylPartial(style = CollectionVinylStyle.Black),
            )
            PluginLookRegions.PLAYER_ATMOSPHERE -> {
                val spec = (patch as? LookPatch.Atmosphere)?.spec
                linkedMapOf(
                    "rainNight" to (spec?.rainNight ?: false),
                    "halo" to (spec?.halo ?: false),
                )
            }
            PluginLookRegions.PLAYER_BACKGROUND ->
                (patch as? LookPatch.PlayerBackground)?.frame?.asMap() ?: emptyMap()
            PluginLookRegions.LIBRARY_PROFILE ->
                (patch as? LookPatch.Profile)?.frame?.asMap() ?: emptyMap()
            else -> emptyMap()
        }
    }

    private fun wallpaperMap(spec: LookWallpaperPartial): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["enabled"] = spec.enabled ?: spec.hasImage()
        out["itemChrome"] = glassWire(spec.itemChrome ?: ChromeGlassMode.Solid)
        out["coverage"] = (spec.coverage ?: ChromeWallpaperStore.DEFAULT_COVERAGE)
            .map { surfaceWire(it) }
        val generic = LinkedHashMap<String, Any?>()
        spec.genericPortrait?.let { generic["portrait"] = it.asMap() }
        spec.genericLandscape?.let { generic["landscape"] = it.asMap() }
        if (generic.isNotEmpty()) out["generic"] = generic
        ChromeWallpaperSurface.entries.forEach { surface ->
            val slot = LinkedHashMap<String, Any?>()
            spec.portraits[surface]?.let { slot["portrait"] = it.asMap() }
            spec.landscapes[surface]?.let { slot["landscape"] = it.asMap() }
            if (slot.isNotEmpty()) out[surfaceWire(surface)] = slot
        }
        return out
    }

    private fun vinylMap(spec: LookVinylPartial): Map<String, Any?> {
        val style = spec.style ?: CollectionVinylStyle.Black
        val vinyl = LinkedHashMap<String, Any?>()
        vinyl["style"] = when (style) {
            CollectionVinylStyle.Black -> "black"
            CollectionVinylStyle.Gold -> "gold"
            CollectionVinylStyle.White -> "white"
            CollectionVinylStyle.Custom -> "custom"
        }
        if (style == CollectionVinylStyle.Custom) {
            spec.base?.let { vinyl["base"] = colorToHex(it) }
            spec.groove?.let { vinyl["groove"] = colorToHex(it) }
        }
        return vinyl
    }
}

internal object PluginLookParams {
    private val GLASS_KEYS = setOf("mode", "refraction", "blur")
    private val WALLPAPER_KEYS = setOf("enabled", "itemChrome", "coverage", "generic") +
        ChromeWallpaperSurface.entries.map { surfaceWire(it) }.toSet()
    private val FRAME_KEYS = setOf("portrait", "landscape")
    private val IMAGE_KEYS = setOf("pack", "offsetX", "offsetY", "scale")
    private val VINYL_KEYS = setOf("style", "base", "groove")
    private val ATMOSPHERE_KEYS = setOf("rainNight", "halo")
    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    fun parse(region: String, raw: Any?, extractDir: File): LookPatch? {
        if (region !in PluginLookRegions.KNOWN) return null
        val map = raw as? Map<*, *> ?: return null
        if (map.isEmpty()) return null
        return when (region) {
            PluginLookRegions.APPEARANCE -> parseAppearance(map)
            PluginLookRegions.CHROME_GLASS -> parseGlass(map)
            PluginLookRegions.CHROME_WALLPAPER -> parseWallpaper(map, extractDir)
            PluginLookRegions.PLAYER_VINYL -> parseVinyl(map)
            PluginLookRegions.PLAYER_ATMOSPHERE -> parseAtmosphere(map)
            PluginLookRegions.PLAYER_BACKGROUND -> parseImageRegion(map, extractDir, playerScale = true)
                ?.let { LookPatch.PlayerBackground(it) }
            PluginLookRegions.LIBRARY_PROFILE -> parseImageRegion(map, extractDir, playerScale = false)
                ?.let { LookPatch.Profile(it) }
            else -> null
        }
    }

    private fun parseAppearance(map: Map<*, *>): LookPatch.Appearance? {
        if (!keysOk(map, setOf("mode"))) return null
        val mode = when (val v = map["mode"]) {
            is String -> when (v.trim()) {
                "light" -> AppAppearance.Light
                "dark" -> AppAppearance.Dark
                "system" -> AppAppearance.System
                else -> return null
            }
            else -> return null
        }
        return LookPatch.Appearance(mode)
    }

    private fun parseGlass(map: Map<*, *>): LookPatch.Glass? {
        if (!keysOk(map, GLASS_KEYS)) return null
        val mode = when (val v = map["mode"]) {
            null -> null
            is String -> glassMode(v) ?: return null
            else -> return null
        }
        val refraction = when (val v = map["refraction"]) {
            null -> null
            else -> parseFloat(v, ChromeGlassStyle.REFRACTION_MIN, ChromeGlassStyle.REFRACTION_MAX)
                ?: return null
        }
        val blur = when (val v = map["blur"]) {
            null -> null
            else -> parseFloat(v, 0f, 1f) ?: return null
        }
        val spec = LookGlassPartial(mode, refraction, blur)
        if (spec.isEmpty()) return null
        return LookPatch.Glass(spec)
    }

    private fun parseWallpaper(map: Map<*, *>, extractDir: File): LookPatch.Wallpaper? {
        if (!keysOk(map, WALLPAPER_KEYS)) return null
        val enabled = when (val v = map["enabled"]) {
            null -> null
            is Boolean -> v
            else -> return null
        }
        val itemChrome = when (val v = map["itemChrome"]) {
            null -> null
            is String -> glassMode(v) ?: return null
            else -> return null
        }
        val coverage = when (val v = map["coverage"]) {
            null -> null
            is List<*> -> {
                if (v.isEmpty()) return null
                val set = LinkedHashSet<ChromeWallpaperSurface>()
                for (item in v) {
                    val name = item as? String ?: return null
                    val surface = surfaceOf(name) ?: return null
                    set.add(surface)
                }
                set
            }
            else -> return null
        }
        var genericPortrait: LookPackFrame? = null
        var genericLandscape: LookPackFrame? = null
        when (val generic = map["generic"]) {
            null -> Unit
            is Map<*, *> -> {
                if (!keysOk(generic, FRAME_KEYS)) return null
                genericPortrait = generic["portrait"]?.let { parseFrame(it, extractDir, false) }
                    ?: if (generic.containsKey("portrait")) return null else null
                genericLandscape = generic["landscape"]?.let { parseFrame(it, extractDir, false) }
                    ?: if (generic.containsKey("landscape")) return null else null
                if (genericPortrait == null && genericLandscape == null) return null
            }
            else -> return null
        }
        val portraits = LinkedHashMap<ChromeWallpaperSurface, LookPackFrame>()
        val landscapes = LinkedHashMap<ChromeWallpaperSurface, LookPackFrame>()
        for (surface in ChromeWallpaperSurface.entries) {
            when (val slot = map[surfaceWire(surface)]) {
                null -> Unit
                is Map<*, *> -> {
                    if (!keysOk(slot, FRAME_KEYS)) return null
                    slot["portrait"]?.let {
                        portraits[surface] = parseFrame(it, extractDir, false) ?: return null
                    }
                    slot["landscape"]?.let {
                        landscapes[surface] = parseFrame(it, extractDir, false) ?: return null
                    }
                    if (slot.containsKey("portrait") && surface !in portraits) return null
                    if (slot.containsKey("landscape") && surface !in landscapes) return null
                    if (surface !in portraits && surface !in landscapes) return null
                }
                else -> return null
            }
        }
        val spec = LookWallpaperPartial(
            enabled = enabled,
            itemChrome = itemChrome,
            coverage = coverage,
            genericPortrait = genericPortrait,
            genericLandscape = genericLandscape,
            portraits = portraits,
            landscapes = landscapes,
        )
        if (spec.isEmpty()) return null
        return LookPatch.Wallpaper(spec)
    }

    private fun parseVinyl(map: Map<*, *>): LookPatch.Vinyl? {
        if (!keysOk(map, VINYL_KEYS)) return null
        val style = when (val s = map["style"]) {
            null -> null
            is String -> when (s.trim()) {
                "black" -> CollectionVinylStyle.Black
                "gold" -> CollectionVinylStyle.Gold
                "white" -> CollectionVinylStyle.White
                "custom" -> CollectionVinylStyle.Custom
                else -> return null
            }
            else -> return null
        }
        val base = when (val b = map["base"]) {
            null -> null
            is String -> parseThemeColor(b) ?: return null
            else -> return null
        }
        val groove = when (val g = map["groove"]) {
            null -> null
            is String -> parseThemeColor(g) ?: return null
            else -> return null
        }
        if (style == CollectionVinylStyle.Custom && (base == null || groove == null)) return null
        if (style != null && style != CollectionVinylStyle.Custom && (base != null || groove != null)) {
            return null
        }
        if (style == null && (base != null || groove != null)) return null
        val spec = LookVinylPartial(style, base, groove)
        if (spec.isEmpty()) return null
        return LookPatch.Vinyl(spec)
    }

    private fun parseAtmosphere(map: Map<*, *>): LookPatch.Atmosphere? {
        if (!keysOk(map, ATMOSPHERE_KEYS)) return null
        val rain = when (val v = map["rainNight"]) {
            null -> null
            is Boolean -> v
            else -> return null
        }
        val halo = when (val v = map["halo"]) {
            null -> null
            is Boolean -> v
            else -> return null
        }
        val spec = LookAtmospherePartial(rain, halo)
        if (spec.isEmpty()) return null
        return LookPatch.Atmosphere(spec)
    }

    private fun parseImageRegion(
        map: Map<*, *>,
        extractDir: File,
        playerScale: Boolean,
    ): LookPackFrame? {
        if (!keysOk(map, IMAGE_KEYS)) return null
        return parseFrame(map, extractDir, playerScale)
    }

    private fun parseFrame(raw: Any, extractDir: File, playerScale: Boolean): LookPackFrame? {
        return when (raw) {
            is String -> resolvePack(raw, extractDir, 0.5f, 0.5f, 1f)
            is Map<*, *> -> {
                if (!keysOk(raw, IMAGE_KEYS)) return null
                val pack = raw["pack"] as? String ?: return null
                val ox = when (val v = raw["offsetX"]) {
                    null -> 0.5f
                    else -> parseFloat(v, ChromeWallpaperStore.OFFSET_MIN, ChromeWallpaperStore.OFFSET_MAX)
                        ?: return null
                }
                val oy = when (val v = raw["offsetY"]) {
                    null -> 0.5f
                    else -> parseFloat(v, ChromeWallpaperStore.OFFSET_MIN, ChromeWallpaperStore.OFFSET_MAX)
                        ?: return null
                }
                val scaleRange = if (playerScale) {
                    PlayerDisplayPrefs.BG_SCALE_MIN to PlayerDisplayPrefs.BG_SCALE_MAX
                } else {
                    ChromeWallpaperStore.SCALE_MIN to ChromeWallpaperStore.SCALE_MAX
                }
                val scale = when (val v = raw["scale"]) {
                    null -> 1f
                    else -> parseFloat(v, scaleRange.first, scaleRange.second) ?: return null
                }
                resolvePack(pack, extractDir, ox, oy, scale)
            }
            else -> null
        }
    }

    private fun resolvePack(
        pack: String,
        extractDir: File,
        ox: Float,
        oy: Float,
        scale: Float,
    ): LookPackFrame? {
        val rel = PluginPackageRules.normalizeRel(pack) ?: return null
        val ext = PluginPackageRules.extensionOf(rel)?.lowercase() ?: return null
        if (ext !in IMAGE_EXTS) return null
        val file = PluginPackFiles.resolve(extractDir, rel) ?: return null
        if (!file.isFile || file.length() <= 0L) return null
        return LookPackFrame(pack = rel, path = file.absolutePath, offsetX = ox, offsetY = oy, scale = scale)
    }

    private fun keysOk(map: Map<*, *>, allowed: Set<String>): Boolean {
        if (map.isEmpty()) return false
        for (key in map.keys) {
            val name = key as? String ?: return false
            if (name !in allowed) return false
        }
        return true
    }
}

private fun parseFloat(raw: Any, min: Float, max: Float): Float? {
    val d = when (raw) {
        is Int -> raw.toDouble()
        is Long -> raw.toDouble()
        is Double -> raw
        is Float -> raw.toDouble()
        else -> return null
    }
    if (d.isNaN() || d.isInfinite()) return null
    val f = d.toFloat()
    if (f < min || f > max) return null
    return f
}

private fun glassMode(raw: String): ChromeGlassMode? = when (raw.trim()) {
    "liquid" -> ChromeGlassMode.Liquid
    "frost" -> ChromeGlassMode.Frosted
    "solid" -> ChromeGlassMode.Solid
    else -> null
}

private fun glassWire(mode: ChromeGlassMode): String = when (mode) {
    ChromeGlassMode.Liquid -> "liquid"
    ChromeGlassMode.Frosted -> "frost"
    ChromeGlassMode.Solid -> "solid"
}

private fun appearanceWire(mode: AppAppearance): String = when (mode) {
    AppAppearance.Light -> "light"
    AppAppearance.Dark -> "dark"
    AppAppearance.System -> "system"
}

internal fun surfaceWire(surface: ChromeWallpaperSurface): String = when (surface) {
    ChromeWallpaperSurface.Home -> "home"
    ChromeWallpaperSurface.Features -> "features"
    ChromeWallpaperSurface.Profile -> "profile"
    ChromeWallpaperSurface.Search -> "search"
    ChromeWallpaperSurface.Settings -> "settings"
    ChromeWallpaperSurface.Playlist -> "playlist"
    ChromeWallpaperSurface.Album -> "album"
    ChromeWallpaperSurface.Artist -> "artist"
}

internal fun surfaceOf(raw: String): ChromeWallpaperSurface? = when (raw.trim()) {
    "home" -> ChromeWallpaperSurface.Home
    "features" -> ChromeWallpaperSurface.Features
    "profile" -> ChromeWallpaperSurface.Profile
    "search" -> ChromeWallpaperSurface.Search
    "settings" -> ChromeWallpaperSurface.Settings
    "playlist" -> ChromeWallpaperSurface.Playlist
    "album" -> ChromeWallpaperSurface.Album
    "artist" -> ChromeWallpaperSurface.Artist
    else -> null
}
