package com.kite.zmusic.data

enum class ChromeGlassMode {
    Liquid,
    Frosted,
    Solid,
    ;

    val title: String
        get() = when (this) {
            Liquid -> "液态"
            Frosted -> "磨砂"
            Solid -> "纯色"
        }

    val caption: String
        get() = when (this) {
            Liquid -> "折射背后的画面"
            Frosted -> "只做模糊，不折射"
            Solid -> "不透明底，不再透出背景"
        }

    companion object {
        fun fromStored(raw: String?): ChromeGlassMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Liquid
    }
}

/**
 * 主界面液体玻璃主题。折射率是相对产品默认的倍率，模糊是 0–1 强度。
 */
data class ChromeGlassStyle(
    val mode: ChromeGlassMode = ChromeGlassMode.Liquid,
    val refraction: Float = REFRACTION_DEFAULT,
    val blur: Float = BLUR_DEFAULT,
) {
    val settingsSubtitle: String
        get() = when (mode) {
            ChromeGlassMode.Liquid ->
                "液态 · 折射率 ${formatRefraction(refraction)} · 模糊 ${formatBlurPercent(blur)}"
            ChromeGlassMode.Frosted ->
                "磨砂 · 模糊 ${formatBlurPercent(blur)}"
            ChromeGlassMode.Solid -> "纯色，不透明"
        }

    companion object {
        const val REFRACTION_MIN = 0f
        const val REFRACTION_MAX = 2f
        const val REFRACTION_DEFAULT = 1f
        const val BLUR_DEFAULT = 0.4f

        val Default = ChromeGlassStyle()

        fun formatRefraction(value: Float): String =
            String.format(java.util.Locale.US, "%.2f", value.coerceIn(REFRACTION_MIN, REFRACTION_MAX))

        fun formatBlurPercent(value: Float): String =
            "${(value.coerceIn(0f, 1f) * 100f).toInt()}%"
    }
}
