package com.kite.zmusic.ui.player

import androidx.compose.ui.graphics.Color
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.VinylColorStyle

/** 黑胶盘绘制用色（径向底 + 纹路）。data 层只存 ARGB。 */
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

fun PlayerDisplayPrefs.vinylPlateColors(): VinylPlateColors = when (vinylColorStyle) {
    VinylColorStyle.BLACK -> VinylPlateColors.Black
    VinylColorStyle.GOLD -> VinylPlateColors.Gold
    VinylColorStyle.WHITE -> VinylPlateColors.White
    VinylColorStyle.CUSTOM -> VinylPlateColors.custom(
        vinylCustomBaseArgb,
        vinylCustomGrooveArgb,
    )
}
