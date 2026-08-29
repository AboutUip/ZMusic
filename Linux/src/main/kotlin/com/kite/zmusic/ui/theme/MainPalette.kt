package com.kite.zmusic.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

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
    val Card: Color get() = active.card
    val SheetTint: Color get() = active.sheetTint
    val SheetWash: Color get() = active.sheetWash
    val Placeholder: Color get() = active.placeholder
    val PageHeader: Color get() = active.ink
    val MiniPlayerTitle: Color get() = active.ink
    val MiniPlayerSubtitle: Color get() = active.secondary
    val MiniPlayerIcon: Color get() = active.ink
    val TrackOff: Color get() = active.trackOff
    val DockActive: Color get() = active.ink
    val DockInactive: Color get() = active.secondary
}
