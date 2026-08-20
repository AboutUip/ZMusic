package com.kite.zmusic.data

/**
 * 单次导出：音质必选；封面 / 歌词 / 元数据各自独立。
 */
data class TrackExportOptions(
    val quality: AudioQuality,
    val includeCover: Boolean = true,
    val includeLyrics: Boolean = true,
    val includeMetadata: Boolean = true,
)
