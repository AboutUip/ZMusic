package com.kite.zmusic.data

/**
 * 「我的」页与歌单详情展示用模型（无播放逻辑）。
 */
data class UserProfileBrief(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val signature: String?,
    val level: Int?,
    val listenSongs: Long?,
)

data class SubcountBrief(
    val subPlaylistCount: Int,
    val createdPlaylistCount: Int,
    val subArtistCount: Int,
    val subAlbumCount: Int,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    /** 我喜欢的音乐等特殊歌单 */
    val isHeartPlaylist: Boolean,
    /** 自己创建（含心歌单） */
    val isOwned: Boolean,
    /** 收藏他人的歌单 */
    val isSubscribed: Boolean,
    val playCount: Long,
)

data class TrackRow(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String?,
    val durationMs: Long,
    /** 专辑封面，用于播放器与列表展示 */
    val coverUrl: String? = null,
)
