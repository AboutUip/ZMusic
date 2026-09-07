package com.kite.zmusic.data

data class CloudDiskSong(
    val track: TrackRow,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val bitrate: Int = 0,
    val addTime: Long = 0L,
    /** 已匹配到网易云公开曲目。 */
    val matched: Boolean = false,
)

data class CloudDiskPage(
    val songs: List<CloudDiskSong>,
    val count: Int,
    val sizeBytes: Long,
    val maxSizeBytes: Long,
    val hasMore: Boolean,
)

data class CloudUploadToken(
    val needUpload: Boolean,
    val songId: String,
    val uploadToken: String,
    val uploadUrl: String,
    val resourceId: String,
)

data class CloudSongFileMeta(
    val md5: String,
    val size: Long,
    val bitrate: Int,
    val type: String,
)
