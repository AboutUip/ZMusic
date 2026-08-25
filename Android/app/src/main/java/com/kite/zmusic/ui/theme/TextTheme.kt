package com.kite.zmusic.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color

/**
 * 插件可覆盖的宿主色 token。默认来自 [MainColors]；舞台 / onPhoto / 玻璃公式另有默认。
 * 契约见 docs/plugin-engine/THEME.md。
 */
object TextThemeKeys {
    const val TITLE = "text.title"
    const val BODY = "text.body"
    const val SUBTITLE = "text.subtitle"
    const val META = "text.meta"
    const val HINT = "text.hint"
    const val ACCENT = "text.accent"
    const val DESTRUCTIVE = "text.destructive"
    const val DOCK_ACTIVE = "text.dock.active"
    const val DOCK_INACTIVE = "text.dock.inactive"
    const val PAGE_HEADER = "text.pageHeader"
    const val CATALOG_TITLE = "text.catalogTitle"
    const val CATALOG_ACTION = "text.catalogAction"
    const val MINI_PLAYER_TITLE = "text.miniPlayer.title"
    const val MINI_PLAYER_SUBTITLE = "text.miniPlayer.subtitle"
    const val MINI_PLAYER_ICON = "text.miniPlayer.icon"
    const val ISLAND = "text.island"
    const val PLAYER_TRANSPORT = "text.player.transport"
    const val PLAYER_TRANSPORT_LOCKED = "text.player.transportLocked"
    const val PLAYER_TIME = "text.player.time"
    const val ON_PHOTO_TITLE = "text.onPhoto.title"
    const val ON_PHOTO_SUBTITLE = "text.onPhoto.subtitle"
    const val ON_PHOTO_META = "text.onPhoto.meta"

    const val FACE_PAGE = "face.page"
    const val FACE_SURFACE = "face.surface"
    const val FACE_ACCENT = "face.accent"
    const val FACE_HAIRLINE = "face.hairline"
    const val FACE_CARD = "face.card"
    const val FACE_PLACEHOLDER = "face.placeholder"
    const val FACE_TRACK_OFF = "face.trackOff"

    const val CHROME_DOCK_GLASS = "chrome.dockGlass"
    const val CHROME_DOCK_STROKE = "chrome.dockStroke"
    const val CHROME_SHEET_TINT = "chrome.sheetTint"
    const val CHROME_SHEET_WASH = "chrome.sheetWash"
    const val CHROME_GLASS_FILL = "chrome.glassFill"

    const val CONTROL_THUMB = "control.thumb"

    const val PLAYER_STAGE = "player.stage"
    const val PLAYER_PROGRESS_THUMB = "player.progress.thumb"
    const val PLAYER_PROGRESS_ACTIVE = "player.progress.active"
    const val PLAYER_PROGRESS_OFF = "player.progress.off"
    const val PLAYER_PLAY_FILL = "player.playFill"
    const val PLAYER_PLAY_ICON = "player.playIcon"

    val ALL: Set<String> = setOf(
        TITLE, BODY, SUBTITLE, META, HINT, ACCENT, DESTRUCTIVE,
        DOCK_ACTIVE, DOCK_INACTIVE,
        PAGE_HEADER, CATALOG_TITLE, CATALOG_ACTION,
        MINI_PLAYER_TITLE, MINI_PLAYER_SUBTITLE, MINI_PLAYER_ICON,
        ISLAND,
        PLAYER_TRANSPORT, PLAYER_TRANSPORT_LOCKED, PLAYER_TIME,
        ON_PHOTO_TITLE, ON_PHOTO_SUBTITLE, ON_PHOTO_META,
        FACE_PAGE, FACE_SURFACE, FACE_ACCENT, FACE_HAIRLINE,
        FACE_CARD, FACE_PLACEHOLDER, FACE_TRACK_OFF,
        CHROME_DOCK_GLASS, CHROME_DOCK_STROKE, CHROME_SHEET_TINT,
        CHROME_SHEET_WASH, CHROME_GLASS_FILL,
        CONTROL_THUMB,
        PLAYER_STAGE, PLAYER_PROGRESS_THUMB, PLAYER_PROGRESS_ACTIVE,
        PLAYER_PROGRESS_OFF, PLAYER_PLAY_FILL, PLAYER_PLAY_ICON,
    )

    /** `accent` → `text.accent`；已带族前缀的全名原样匹配。 */
    fun normalize(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s in ALL) return s
        val asText = "text.$s"
        return asText.takeIf { it in ALL }
    }
}

internal data class ThemePalette(private val colors: Map<String, Color>) {
    fun colorOf(key: String): Color =
        colors[key] ?: error("missing theme default: $key")

    fun asHexMap(): Map<String, String> =
        TextThemeKeys.ALL.associateWith { colorToHex(colorOf(it)) }

    companion object {
        private val PlayerTransportDefault = Color(0xFFB8C5D4)
        private val PlayerTransportLockedDefault = Color(0xFF7A8796)
        private val PlayerTimeDefault = Color(0xFFE8EEF5)
        private val PlayerStageDefault = Color(0xFF05070C)
        private val PlayerProgressThumbDefault = Color(0xFFE8EEF5)
        private val PlayerProgressActiveDefault = Color(0xFFD5DEE8).copy(alpha = 0.9f)
        private val PlayerProgressOffDefault = Color.White.copy(alpha = 0.14f)
        private val PlayerPlayFillDefault = Color.White.copy(alpha = 0.12f)
        private val PlayerPlayIconDefault = Color(0xFFF5F7FA)

        fun from(colors: MainColors): ThemePalette {
            val m = LinkedHashMap<String, Color>(TextThemeKeys.ALL.size)
            m[TextThemeKeys.TITLE] = colors.ink
            m[TextThemeKeys.BODY] = colors.ink
            m[TextThemeKeys.SUBTITLE] = colors.secondary
            m[TextThemeKeys.META] = colors.secondary
            m[TextThemeKeys.HINT] = colors.hint
            m[TextThemeKeys.ACCENT] = colors.accent
            m[TextThemeKeys.DESTRUCTIVE] = colors.accent
            m[TextThemeKeys.DOCK_ACTIVE] = colors.ink
            m[TextThemeKeys.DOCK_INACTIVE] = colors.secondary
            m[TextThemeKeys.PAGE_HEADER] = colors.ink
            m[TextThemeKeys.CATALOG_TITLE] = colors.ink
            m[TextThemeKeys.CATALOG_ACTION] = colors.accent
            m[TextThemeKeys.MINI_PLAYER_TITLE] = colors.ink
            m[TextThemeKeys.MINI_PLAYER_SUBTITLE] = colors.secondary
            m[TextThemeKeys.MINI_PLAYER_ICON] = colors.ink
            m[TextThemeKeys.ISLAND] = colors.ink
            m[TextThemeKeys.PLAYER_TRANSPORT] = PlayerTransportDefault
            m[TextThemeKeys.PLAYER_TRANSPORT_LOCKED] = PlayerTransportLockedDefault
            m[TextThemeKeys.PLAYER_TIME] = PlayerTimeDefault
            m[TextThemeKeys.ON_PHOTO_TITLE] = Color.White
            m[TextThemeKeys.ON_PHOTO_SUBTITLE] = Color.White.copy(alpha = 0.88f)
            m[TextThemeKeys.ON_PHOTO_META] = Color.White.copy(alpha = 0.62f)
            m[TextThemeKeys.FACE_PAGE] = colors.page
            m[TextThemeKeys.FACE_SURFACE] = colors.surface
            m[TextThemeKeys.FACE_ACCENT] = colors.accent
            m[TextThemeKeys.FACE_HAIRLINE] = colors.hairline
            m[TextThemeKeys.FACE_CARD] = colors.card
            m[TextThemeKeys.FACE_PLACEHOLDER] = colors.placeholder
            m[TextThemeKeys.FACE_TRACK_OFF] = colors.trackOff
            m[TextThemeKeys.CHROME_DOCK_GLASS] = colors.dockGlass
            m[TextThemeKeys.CHROME_DOCK_STROKE] = colors.dockStroke
            m[TextThemeKeys.CHROME_SHEET_TINT] = colors.sheetTint
            m[TextThemeKeys.CHROME_SHEET_WASH] = colors.sheetWash
            m[TextThemeKeys.CHROME_GLASS_FILL] = colors.glassFill(0.62f)
            m[TextThemeKeys.CONTROL_THUMB] = Color.White
            m[TextThemeKeys.PLAYER_STAGE] = PlayerStageDefault
            m[TextThemeKeys.PLAYER_PROGRESS_THUMB] = PlayerProgressThumbDefault
            m[TextThemeKeys.PLAYER_PROGRESS_ACTIVE] = PlayerProgressActiveDefault
            m[TextThemeKeys.PLAYER_PROGRESS_OFF] = PlayerProgressOffDefault
            m[TextThemeKeys.PLAYER_PLAY_FILL] = PlayerPlayFillDefault
            m[TextThemeKeys.PLAYER_PLAY_ICON] = PlayerPlayIconDefault
            check(m.keys == TextThemeKeys.ALL) {
                "theme defaults missing ${TextThemeKeys.ALL - m.keys}"
            }
            return ThemePalette(m)
        }
    }
}

/**
 * 宿主主题。Compose 读取 getter 会订阅 overlay / 默认变更。
 * 面色走 [MainPalette] 同名 getter，以便全 App 已有调用点自动覆盖。
 */
object TextTheme {
    private var defaults by mutableStateOf(ThemePalette.from(MainColors.Light))
    private var overlay by mutableStateOf<Map<String, Color>>(emptyMap())
    private var ownerId by mutableStateOf<String?>(null)

    val ownerPluginId: String? get() = ownerId

    fun bindDefaults(colors: MainColors) {
        val next = ThemePalette.from(colors)
        if (defaults != next) defaults = next
    }

    /**
     * 校验并合并 overlay。未知 key / 非法色 → `false`，不改状态。
     * @param colors 已解析的合法 map（调用方保证 key∈ALL）
     */
    fun setOverlay(pluginId: String, colors: Map<String, Color>): Boolean {
        if (colors.isEmpty()) return false
        if (colors.keys.any { it !in TextThemeKeys.ALL }) return false
        overlay = overlay + colors
        ownerId = pluginId
        Snapshot.sendApplyNotifications()
        return true
    }

    /** 仅 owner 可清空。 */
    fun clearIfOwner(pluginId: String): Boolean {
        if (ownerId != pluginId) return false
        overlay = emptyMap()
        ownerId = null
        Snapshot.sendApplyNotifications()
        return true
    }

    /** 单元测试复位全局状态。 */
    internal fun resetForTests() {
        defaults = ThemePalette.from(MainColors.Light)
        overlay = emptyMap()
        ownerId = null
    }

    fun effectiveHexMap(): Map<String, String> {
        val base = defaults.asHexMap().toMutableMap()
        overlay.forEach { (k, c) -> base[k] = colorToHex(c) }
        return base
    }

    internal fun overlayOf(key: String): Color? = overlay[key]

    fun resolve(key: String): Color =
        overlay[key] ?: defaults.colorOf(key)

    fun namedColor(raw: String): Color? {
        val key = TextThemeKeys.normalize(raw) ?: return null
        return resolve(key)
    }

    val Title: Color get() = resolve(TextThemeKeys.TITLE)
    val Body: Color get() = resolve(TextThemeKeys.BODY)
    val Subtitle: Color get() = resolve(TextThemeKeys.SUBTITLE)
    val Meta: Color get() = resolve(TextThemeKeys.META)
    val Hint: Color get() = resolve(TextThemeKeys.HINT)
    val Accent: Color get() = resolve(TextThemeKeys.ACCENT)
    val Destructive: Color get() = resolve(TextThemeKeys.DESTRUCTIVE)
    val DockActive: Color get() = resolve(TextThemeKeys.DOCK_ACTIVE)
    val DockInactive: Color get() = resolve(TextThemeKeys.DOCK_INACTIVE)
    val PageHeader: Color get() = resolve(TextThemeKeys.PAGE_HEADER)
    val CatalogTitle: Color get() = resolve(TextThemeKeys.CATALOG_TITLE)
    val CatalogAction: Color get() = resolve(TextThemeKeys.CATALOG_ACTION)
    val MiniPlayerTitle: Color get() = resolve(TextThemeKeys.MINI_PLAYER_TITLE)
    val MiniPlayerSubtitle: Color get() = resolve(TextThemeKeys.MINI_PLAYER_SUBTITLE)
    val MiniPlayerIcon: Color get() = resolve(TextThemeKeys.MINI_PLAYER_ICON)
    val Island: Color get() = resolve(TextThemeKeys.ISLAND)
    val PlayerTransport: Color get() = resolve(TextThemeKeys.PLAYER_TRANSPORT)
    val PlayerTransportLocked: Color get() = resolve(TextThemeKeys.PLAYER_TRANSPORT_LOCKED)
    val PlayerTime: Color get() = resolve(TextThemeKeys.PLAYER_TIME)
    val OnPhotoTitle: Color get() = resolve(TextThemeKeys.ON_PHOTO_TITLE)
    val OnPhotoSubtitle: Color get() = resolve(TextThemeKeys.ON_PHOTO_SUBTITLE)
    val OnPhotoMeta: Color get() = resolve(TextThemeKeys.ON_PHOTO_META)
    val ControlThumb: Color get() = resolve(TextThemeKeys.CONTROL_THUMB)
    val PlayerStage: Color get() = resolve(TextThemeKeys.PLAYER_STAGE)
    val PlayerProgressThumb: Color get() = resolve(TextThemeKeys.PLAYER_PROGRESS_THUMB)
    val PlayerProgressActive: Color get() = resolve(TextThemeKeys.PLAYER_PROGRESS_ACTIVE)
    val PlayerProgressOff: Color get() = resolve(TextThemeKeys.PLAYER_PROGRESS_OFF)
    val PlayerPlayFill: Color get() = resolve(TextThemeKeys.PLAYER_PLAY_FILL)
    val PlayerPlayIcon: Color get() = resolve(TextThemeKeys.PLAYER_PLAY_ICON)
}

internal fun parseThemeColor(raw: String): Color? {
    val s = raw.trim().removePrefix("#")
    val value = s.toLongOrNull(16) ?: return null
    return when (s.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> null
    }
}

internal fun colorToHex(color: Color): String {
    val a = (color.alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
    val r = (color.red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (color.green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (color.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return if (a >= 255) {
        "#%02X%02X%02X".format(r, g, b)
    } else {
        "#%02X%02X%02X%02X".format(a, r, g, b)
    }
}
