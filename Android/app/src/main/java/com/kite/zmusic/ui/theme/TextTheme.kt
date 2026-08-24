package com.kite.zmusic.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 插件可覆盖的细粒度文本色。默认来自 [MainColors]；舞台 / onPhoto 默认固定。
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

    val ALL: Set<String> = setOf(
        TITLE, BODY, SUBTITLE, META, HINT, ACCENT, DESTRUCTIVE,
        DOCK_ACTIVE, DOCK_INACTIVE,
        PAGE_HEADER, CATALOG_TITLE, CATALOG_ACTION,
        MINI_PLAYER_TITLE, MINI_PLAYER_SUBTITLE, MINI_PLAYER_ICON,
        ISLAND,
        PLAYER_TRANSPORT, PLAYER_TRANSPORT_LOCKED, PLAYER_TIME,
        ON_PHOTO_TITLE, ON_PHOTO_SUBTITLE, ON_PHOTO_META,
    )
}

data class TextThemeTokens(
    val title: Color,
    val body: Color,
    val subtitle: Color,
    val meta: Color,
    val hint: Color,
    val accent: Color,
    val destructive: Color,
    val dockActive: Color,
    val dockInactive: Color,
    val pageHeader: Color,
    val catalogTitle: Color,
    val catalogAction: Color,
    val miniPlayerTitle: Color,
    val miniPlayerSubtitle: Color,
    val miniPlayerIcon: Color,
    val island: Color,
    val playerTransport: Color,
    val playerTransportLocked: Color,
    val playerTime: Color,
    val onPhotoTitle: Color,
    val onPhotoSubtitle: Color,
    val onPhotoMeta: Color,
) {
    fun colorOf(key: String): Color = when (key) {
        TextThemeKeys.TITLE -> title
        TextThemeKeys.BODY -> body
        TextThemeKeys.SUBTITLE -> subtitle
        TextThemeKeys.META -> meta
        TextThemeKeys.HINT -> hint
        TextThemeKeys.ACCENT -> accent
        TextThemeKeys.DESTRUCTIVE -> destructive
        TextThemeKeys.DOCK_ACTIVE -> dockActive
        TextThemeKeys.DOCK_INACTIVE -> dockInactive
        TextThemeKeys.PAGE_HEADER -> pageHeader
        TextThemeKeys.CATALOG_TITLE -> catalogTitle
        TextThemeKeys.CATALOG_ACTION -> catalogAction
        TextThemeKeys.MINI_PLAYER_TITLE -> miniPlayerTitle
        TextThemeKeys.MINI_PLAYER_SUBTITLE -> miniPlayerSubtitle
        TextThemeKeys.MINI_PLAYER_ICON -> miniPlayerIcon
        TextThemeKeys.ISLAND -> island
        TextThemeKeys.PLAYER_TRANSPORT -> playerTransport
        TextThemeKeys.PLAYER_TRANSPORT_LOCKED -> playerTransportLocked
        TextThemeKeys.PLAYER_TIME -> playerTime
        TextThemeKeys.ON_PHOTO_TITLE -> onPhotoTitle
        TextThemeKeys.ON_PHOTO_SUBTITLE -> onPhotoSubtitle
        TextThemeKeys.ON_PHOTO_META -> onPhotoMeta
        else -> title
    }

    fun asHexMap(): Map<String, String> =
        TextThemeKeys.ALL.associateWith { colorToHex(colorOf(it)) }

    companion object {
        private val PlayerTransportDefault = Color(0xFFB8C5D4)
        private val PlayerTransportLockedDefault = Color(0xFF7A8796)
        private val PlayerTimeDefault = Color(0xFFE8EEF5)

        fun from(colors: MainColors): TextThemeTokens = TextThemeTokens(
            title = colors.ink,
            body = colors.ink,
            subtitle = colors.secondary,
            meta = colors.secondary,
            hint = colors.hint,
            accent = colors.accent,
            destructive = colors.accent,
            dockActive = colors.ink,
            dockInactive = colors.secondary,
            pageHeader = colors.ink,
            catalogTitle = colors.ink,
            catalogAction = colors.accent,
            miniPlayerTitle = colors.ink,
            miniPlayerSubtitle = colors.secondary,
            miniPlayerIcon = colors.ink,
            island = colors.ink,
            playerTransport = PlayerTransportDefault,
            playerTransportLocked = PlayerTransportLockedDefault,
            playerTime = PlayerTimeDefault,
            onPhotoTitle = Color.White,
            onPhotoSubtitle = Color.White.copy(alpha = 0.88f),
            onPhotoMeta = Color.White.copy(alpha = 0.62f),
        )
    }
}

/**
 * 宿主文本主题。Compose 读取 getter 会订阅 overlay / 默认变更。
 */
object TextTheme {
    private var defaults by mutableStateOf(TextThemeTokens.from(MainColors.Light))
    private var overlay by mutableStateOf<Map<String, Color>>(emptyMap())
    private var ownerId by mutableStateOf<String?>(null)

    val ownerPluginId: String? get() = ownerId

    fun bindDefaults(colors: MainColors) {
        val next = TextThemeTokens.from(colors)
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
        return true
    }

    /** 仅 owner 可清空。 */
    fun clearIfOwner(pluginId: String): Boolean {
        if (ownerId != pluginId) return false
        overlay = emptyMap()
        ownerId = null
        return true
    }

    /** 单元测试复位全局状态。 */
    internal fun resetForTests() {
        defaults = TextThemeTokens.from(MainColors.Light)
        overlay = emptyMap()
        ownerId = null
    }

    fun effectiveHexMap(): Map<String, String> {
        val base = defaults.asHexMap().toMutableMap()
        overlay.forEach { (k, c) -> base[k] = colorToHex(c) }
        return base
    }

    private fun resolve(key: String): Color =
        overlay[key] ?: defaults.colorOf(key)

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
