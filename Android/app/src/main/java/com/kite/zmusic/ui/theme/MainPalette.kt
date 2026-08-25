package com.kite.zmusic.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 应用色板。浅色保持原网易云灰底白卡；深色按流媒体常见分层：
 * 画布近黑而非 #000（Spotify #121212 / Pocket Casts #1A1A1A），卡片略抬亮。
 * Compose 读取下列 getter 会订阅浅深切换与插件 overlay。
 *
 * 跟外观走的界面只用这里的 getter；滑条/开关走 [MainControls]。
 * 插件 `Xuan.theme` 改 getter，不改 [snapshot] 里的宿主默认。
 * 歌词用户样式、桌面歌词浮层、黑胶用户配色是另一套语言。
 */
data class MainColors(
    val page: Color,
    val surface: Color,
    val ink: Color,
    val secondary: Color,
    val hint: Color,
    val accent: Color,
    val hairline: Color,
    val dockGlass: Color,
    val dockStroke: Color,
    val card: Color,
    val sheetTint: Color,
    val sheetWash: Color,
    val placeholder: Color,
    val trackOff: Color,
    val isDark: Boolean,
) {
    /**
     * 玻璃白膜。深浅同一套薄纱，不跟主题加厚。
     * 浅色若按 [lightAlpha] 原样铺白，采到的图会被漂白。
     */
    fun glassFill(lightAlpha: Float): Color =
        Color.White.copy(alpha = (lightAlpha * 0.38f).coerceIn(0.06f, 0.18f))

    companion object {
        val Light = MainColors(
            page = Color(0xFFF6F7F9),
            surface = Color(0xFFFFFFFF),
            ink = Color(0xFF2C2C2E),
            secondary = Color(0xFF8E8E93),
            hint = Color(0xFFC7C7CC),
            accent = Color(0xFFEC4141),
            hairline = Color(0x14000000),
            dockGlass = Color(0xE6FFFFFF),
            dockStroke = Color(0x33FFFFFF),
            card = Color.White.copy(alpha = 0.62f),
            sheetTint = Color.White.copy(alpha = 0.78f),
            sheetWash = Color.White.copy(alpha = 0.22f),
            placeholder = Color(0xFFE8E8ED),
            trackOff = Color(0xFFE5E5EA),
            isDark = false,
        )

        /**
         * 深色：#121214 画布（比纯黑暖一度、比 Spotify #121212 略冷），
         * #1C1C1E 抬升面，正文 #F2F2F7，辅色略提亮以保证对比。
         */
        val Dark = MainColors(
            page = Color(0xFF121214),
            surface = Color(0xFF1C1C1E),
            ink = Color(0xFFF2F2F7),
            secondary = Color(0xFF98989F),
            hint = Color(0xFF636366),
            accent = Color(0xFFEC4141),
            hairline = Color(0x29FFFFFF),
            dockGlass = Color(0xE61C1C1E),
            dockStroke = Color(0x28FFFFFF),
            card = Color.White.copy(alpha = 0.08f),
            sheetTint = Color.White.copy(alpha = 0.10f),
            sheetWash = Color.White.copy(alpha = 0.06f),
            placeholder = Color(0xFF2C2C2E),
            trackOff = Color(0xFF3A3A3C),
            isDark = true,
        )
    }
}

object MainPalette {
    private var active by mutableStateOf(MainColors.Light)

    fun bind(colors: MainColors) {
        if (active == colors) return
        active = colors
        TextTheme.bindDefaults(colors)
    }

    val snapshot: MainColors get() = active
    val isDark: Boolean get() = active.isDark

    val Page: Color get() = TextTheme.resolve(TextThemeKeys.FACE_PAGE)
    val Surface: Color get() = TextTheme.resolve(TextThemeKeys.FACE_SURFACE)
    /** 正文主色；转发 [TextTheme.Title]，便于插件 `text.title` 覆盖全 App。 */
    val Ink: Color get() = TextTheme.Title
    /** 辅文；转发 [TextTheme.Subtitle]。 */
    val Secondary: Color get() = TextTheme.Subtitle
    /** 弱提示字色；转发 [TextTheme.Hint]。 */
    val Hint: Color get() = TextTheme.Hint
    /** 面色/控件强调。文本强调用 [TextTheme.Accent]。 */
    val Accent: Color get() = TextTheme.resolve(TextThemeKeys.FACE_ACCENT)
    val Hairline: Color get() = TextTheme.resolve(TextThemeKeys.FACE_HAIRLINE)
    val DockGlass: Color get() = TextTheme.resolve(TextThemeKeys.CHROME_DOCK_GLASS)
    val DockStroke: Color get() = TextTheme.resolve(TextThemeKeys.CHROME_DOCK_STROKE)
    val Card: Color get() = TextTheme.resolve(TextThemeKeys.FACE_CARD)
    val SheetTint: Color get() = TextTheme.resolve(TextThemeKeys.CHROME_SHEET_TINT)
    val SheetWash: Color get() = TextTheme.resolve(TextThemeKeys.CHROME_SHEET_WASH)
    /** 封面/头像未加载底。不要再写 `0xFFEDEDED`。 */
    val Placeholder: Color get() = TextTheme.resolve(TextThemeKeys.FACE_PLACEHOLDER)
    /** 滑条未激活段、开关关闭槽、分段选择器底槽。不要再写 `0xFFE5E5EA`。 */
    val TrackOff: Color get() = TextTheme.resolve(TextThemeKeys.FACE_TRACK_OFF)

    /**
     * 玻璃白膜。未覆盖 [TextThemeKeys.CHROME_GLASS_FILL] 时按 [lightAlpha] 走宿主公式；
     * 覆盖后所有调用返回该色（透明度写进 hex）。
     */
    fun glassFill(lightAlpha: Float): Color =
        TextTheme.overlayOf(TextThemeKeys.CHROME_GLASS_FILL) ?: active.glassFill(lightAlpha)

    internal fun resetForTests() {
        active = MainColors.Light
        TextTheme.resetForTests()
    }
}
