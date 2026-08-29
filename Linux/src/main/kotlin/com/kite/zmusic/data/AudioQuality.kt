package com.kite.zmusic.data

enum class AudioQuality(
    val level: String,
    val title: String,
    val compactTitle: String,
    val caption: String,
    val encodeType: String,
    val needsPcOs: Boolean,
    val legacyBr: Int,
) {
    STANDARD("standard", "标准", "标准", "128 kbps", "mp3", false, 128_000),
    HIGHER("higher", "较高", "较高", "192 kbps", "mp3", false, 192_000),
    EXHIGH("exhigh", "极高", "极高", "320 kbps", "mp3", false, 320_000),
    LOSSLESS("lossless", "无损", "无损", "FLAC", "flac", true, 999_000),
    HIRES("hires", "Hi-Res", "Hi-Res", "高解析度", "flac", true, 999_000),
    JYEFFECT("jyeffect", "高清环绕", "环绕", "空间音频", "flac", true, 999_000),
    SKY("sky", "沉浸环绕", "沉浸", "空间音频", "flac", true, 999_000),
    DOLBY("dolby", "杜比全景声", "杜比", "Atmos", "flac", true, 999_000),
    JYMASTER("jymaster", "超清母带", "母带", "Master", "flac", true, 999_000),
    ;

    fun fallbacks(): List<AudioQuality> = when (this) {
        JYMASTER, DOLBY, SKY, JYEFFECT -> listOf(HIRES, LOSSLESS, EXHIGH)
        HIRES -> listOf(LOSSLESS, EXHIGH)
        LOSSLESS -> listOf(EXHIGH)
        EXHIGH -> listOf(HIGHER, STANDARD)
        HIGHER -> listOf(STANDARD)
        STANDARD -> emptyList()
    }

    companion object {
        val Default: AudioQuality = EXHIGH

        fun fromLevel(raw: String?): AudioQuality =
            entries.firstOrNull { it.level.equals(raw?.trim(), ignoreCase = true) } ?: Default
    }
}

fun cookieWithPlaybackOs(cookie: String): String {
    val parts = cookie.split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it.startsWith("os=", ignoreCase = true) }
    return (parts + "os=pc").joinToString("; ")
}
