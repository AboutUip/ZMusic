package com.kite.zmusic.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 应用色板。浅色保持原网易云灰底白卡；深色按流媒体常见分层：
 * 画布近黑而非 #000（Spotify #121212 / Pocket Casts #1A1A1A），卡片略抬亮，
 * 品牌红 [Accent] 不变。Compose 读取下列 getter 会订阅切换。
 *
 * 跟外观走的界面（首页、设置、歌单、「更多」）只用这里的色，滑条/开关走
 * [MainControls]。播放页封面编辑器、桌面歌词浮层是永远压在暗底上的另一套语言。
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
        if (active != colors) active = colors
    }

    val snapshot: MainColors get() = active
    val isDark: Boolean get() = active.isDark

    val Page: Color get() = active.page
    val Surface: Color get() = active.surface
    val Ink: Color get() = active.ink
    val Secondary: Color get() = active.secondary
    val Hint: Color get() = active.hint
    val Accent: Color get() = active.accent
    val Hairline: Color get() = active.hairline
    val DockGlass: Color get() = active.dockGlass
    val DockStroke: Color get() = active.dockStroke
    val Card: Color get() = active.card
    val SheetTint: Color get() = active.sheetTint
    val SheetWash: Color get() = active.sheetWash
    /** 封面/头像未加载底。不要再写 `0xFFEDEDED`。 */
    val Placeholder: Color get() = active.placeholder
    /** 滑条未激活段、开关关闭槽、分段选择器底槽。不要再写 `0xFFE5E5EA`。 */
    val TrackOff: Color get() = active.trackOff

    fun glassFill(lightAlpha: Float): Color = active.glassFill(lightAlpha)
}
