package com.kite.zmusic.plugin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kite.zmusic.ui.player.VinylPlateColors
import com.kite.zmusic.ui.theme.colorToHex
import com.kite.zmusic.ui.theme.parseThemeColor

enum class CollectionFlow {
    List,
    Grid,
}

enum class CollectionItemKind {
    Row,
    Tile,
}

enum class CollectionCoverKind {
    Round,
    Circle,
    Vinyl,
}

enum class CollectionVinylStyle {
    Black,
    Gold,
    White,
    Custom,
}

data class CollectionPresentPartial(
    val flow: CollectionFlow? = null,
    val columns: Int? = null,
    val item: CollectionItemKind? = null,
    val cover: CollectionCoverKind? = null,
    val vinylStyle: CollectionVinylStyle? = null,
    val vinylBase: Color? = null,
    val vinylGroove: Color? = null,
    val gap: Int? = null,
) {
    fun isEmpty(): Boolean =
        flow == null && columns == null && item == null && cover == null &&
            vinylStyle == null && vinylBase == null && vinylGroove == null && gap == null
}

data class CollectionPresentSpec(
    val flow: CollectionFlow,
    val columns: Int,
    val item: CollectionItemKind,
    val cover: CollectionCoverKind,
    val vinylStyle: CollectionVinylStyle,
    val vinylBase: Color?,
    val vinylGroove: Color?,
    val gap: Int,
) {
    fun merge(partial: CollectionPresentPartial): CollectionPresentSpec {
        val nextStyle = partial.vinylStyle ?: vinylStyle
        val leftCustom = nextStyle == CollectionVinylStyle.Custom
        return CollectionPresentSpec(
            flow = partial.flow ?: flow,
            columns = partial.columns ?: columns,
            item = partial.item ?: item,
            cover = partial.cover ?: cover,
            vinylStyle = nextStyle,
            vinylBase = if (leftCustom) (partial.vinylBase ?: vinylBase) else null,
            vinylGroove = if (leftCustom) (partial.vinylGroove ?: vinylGroove) else null,
            gap = partial.gap ?: gap,
        )
    }

    fun plateColors(): VinylPlateColors = when (vinylStyle) {
        CollectionVinylStyle.Black -> VinylPlateColors.Black
        CollectionVinylStyle.Gold -> VinylPlateColors.Gold
        CollectionVinylStyle.White -> VinylPlateColors.White
        CollectionVinylStyle.Custom -> VinylPlateColors.custom(
            (vinylBase ?: Color(0xFF101012)).toArgb(),
            (vinylGroove ?: Color.White).toArgb(),
        )
    }

    fun asMap(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["flow"] = if (flow == CollectionFlow.Grid) "grid" else "list"
        out["columns"] = columns
        out["item"] = if (item == CollectionItemKind.Tile) "tile" else "row"
        out["cover"] = when (cover) {
            CollectionCoverKind.Round -> "round"
            CollectionCoverKind.Circle -> "circle"
            CollectionCoverKind.Vinyl -> "vinyl"
        }
        out["gap"] = gap
        if (cover == CollectionCoverKind.Vinyl) {
            val vinyl = LinkedHashMap<String, Any?>()
            vinyl["style"] = vinylStyleWire(vinylStyle)
            if (vinylStyle == CollectionVinylStyle.Custom) {
                vinylBase?.let { vinyl["base"] = colorToHex(it) }
                vinylGroove?.let { vinyl["groove"] = colorToHex(it) }
            }
            out["vinyl"] = vinyl
        }
        return out
    }

    companion object {
        val PlaylistDefault = CollectionPresentSpec(
            flow = CollectionFlow.List,
            columns = 3,
            item = CollectionItemKind.Row,
            cover = CollectionCoverKind.Round,
            vinylStyle = CollectionVinylStyle.Black,
            vinylBase = null,
            vinylGroove = null,
            gap = 12,
        )
        val AlbumDefault = CollectionPresentSpec(
            flow = CollectionFlow.Grid,
            columns = 3,
            item = CollectionItemKind.Tile,
            cover = CollectionCoverKind.Round,
            vinylStyle = CollectionVinylStyle.Black,
            vinylBase = null,
            vinylGroove = null,
            gap = 12,
        )

        fun hostDefault(region: String): CollectionPresentSpec =
            if (region == PluginCollections.LIBRARY_COLLECTED_ALBUMS) AlbumDefault else PlaylistDefault
    }
}

private data class RegionOverlay(
    val pluginId: String,
    val spec: CollectionPresentSpec,
)

/**
 * 陈列 overlay。每区一份，Compose 读 [of] 会订阅。
 */
object PluginCollectionPresent {
    private var overlays by mutableStateOf<Map<String, RegionOverlay>>(emptyMap())

    fun of(region: String): CollectionPresentSpec {
        val base = CollectionPresentSpec.hostDefault(region)
        return overlays[region]?.spec ?: base
    }

    fun ownerOf(region: String): String? = overlays[region]?.pluginId

    fun set(pluginId: String, region: String, partial: CollectionPresentPartial): Boolean {
        if (region !in PluginCollections.KNOWN) return false
        if (partial.isEmpty()) return false
        val current = of(region)
        val next = current.merge(partial)
        if (next.cover == CollectionCoverKind.Vinyl &&
            next.vinylStyle == CollectionVinylStyle.Custom &&
            (next.vinylBase == null || next.vinylGroove == null)
        ) {
            return false
        }
        overlays = overlays + (region to RegionOverlay(pluginId, next))
        Snapshot.sendApplyNotifications()
        return true
    }

    fun clear(pluginId: String, region: String?): Boolean {
        if (region != null) {
            if (region !in PluginCollections.KNOWN) return false
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
        PluginCollections.KNOWN.associateWith { of(it).asMap() }

    internal fun resetForTests() {
        overlays = emptyMap()
    }
}

internal object PluginCollectionParams {
    private val SPEC_KEYS = setOf("flow", "columns", "item", "cover", "vinyl", "gap")
    private val VINYL_KEYS = setOf("style", "base", "groove")

    fun parsePartial(raw: Any?): CollectionPresentPartial? {
        val map = raw as? Map<*, *> ?: return null
        if (map.isEmpty()) return null
        for (key in map.keys) {
            val name = key as? String ?: return null
            if (name !in SPEC_KEYS) return null
        }
        val flow = when (val v = map["flow"]) {
            null -> null
            is String -> when (v.trim()) {
                "list" -> CollectionFlow.List
                "grid" -> CollectionFlow.Grid
                else -> return null
            }
            else -> return null
        }
        val columns = when (val v = map["columns"]) {
            null -> null
            is Int -> v.takeIf { it in 1..4 } ?: return null
            is Long -> v.toInt().takeIf { it in 1..4 && v == it.toLong() } ?: return null
            is Double -> {
                val n = v.toInt()
                if (v != n.toDouble() || n !in 1..4) return null
                n
            }
            else -> return null
        }
        val item = when (val v = map["item"]) {
            null -> null
            is String -> when (v.trim()) {
                "row" -> CollectionItemKind.Row
                "tile" -> CollectionItemKind.Tile
                else -> return null
            }
            else -> return null
        }
        var cover = when (val v = map["cover"]) {
            null -> null
            is String -> when (v.trim()) {
                "round" -> CollectionCoverKind.Round
                "circle" -> CollectionCoverKind.Circle
                "vinyl" -> CollectionCoverKind.Vinyl
                else -> return null
            }
            else -> return null
        }
        val vinylRaw = map["vinyl"]
        var vinylStyle: CollectionVinylStyle? = null
        var vinylBase: Color? = null
        var vinylGroove: Color? = null
        if (vinylRaw != null) {
            val vinyl = vinylRaw as? Map<*, *> ?: return null
            if (vinyl.isEmpty()) return null
            for (key in vinyl.keys) {
                val name = key as? String ?: return null
                if (name !in VINYL_KEYS) return null
            }
            vinylStyle = when (val s = vinyl["style"]) {
                null -> return null
                is String -> when (s.trim()) {
                    "black" -> CollectionVinylStyle.Black
                    "gold" -> CollectionVinylStyle.Gold
                    "white" -> CollectionVinylStyle.White
                    "custom" -> CollectionVinylStyle.Custom
                    else -> return null
                }
                else -> return null
            }
            vinylBase = when (val b = vinyl["base"]) {
                null -> null
                is String -> parseThemeColor(b) ?: return null
                else -> return null
            }
            vinylGroove = when (val g = vinyl["groove"]) {
                null -> null
                is String -> parseThemeColor(g) ?: return null
                else -> return null
            }
            if (vinylStyle == CollectionVinylStyle.Custom && (vinylBase == null || vinylGroove == null)) {
                return null
            }
            if (vinylStyle != CollectionVinylStyle.Custom && (vinylBase != null || vinylGroove != null)) {
                return null
            }
            if (cover == null) cover = CollectionCoverKind.Vinyl
            if (cover != CollectionCoverKind.Vinyl) return null
        }
        val gap = when (val v = map["gap"]) {
            null -> null
            is Int -> v.takeIf { it in 8..24 } ?: return null
            is Long -> v.toInt().takeIf { it in 8..24 && v == it.toLong() } ?: return null
            is Double -> {
                val n = v.toInt()
                if (v != n.toDouble() || n !in 8..24) return null
                n
            }
            else -> return null
        }
        val partial = CollectionPresentPartial(
            flow = flow,
            columns = columns,
            item = item,
            cover = cover,
            vinylStyle = vinylStyle,
            vinylBase = vinylBase,
            vinylGroove = vinylGroove,
            gap = gap,
        )
        if (partial.isEmpty()) return null
        return partial
    }
}

private fun vinylStyleWire(style: CollectionVinylStyle): String = when (style) {
    CollectionVinylStyle.Black -> "black"
    CollectionVinylStyle.Gold -> "gold"
    CollectionVinylStyle.White -> "white"
    CollectionVinylStyle.Custom -> "custom"
}
