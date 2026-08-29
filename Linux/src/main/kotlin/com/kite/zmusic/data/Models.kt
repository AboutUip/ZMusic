package com.kite.zmusic.data

data class TrackArtist(
    val id: Long,
    val name: String,
)

data class TrackRow(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String?,
    val durationMs: Long,
    val coverUrl: String? = null,
    val artistRefs: List<TrackArtist> = emptyList(),
    val localAudioUri: String? = null,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val isHeartPlaylist: Boolean,
    val isOwned: Boolean,
    val isSubscribed: Boolean,
    val playCount: Long,
)

data class HomeBanner(
    val picUrl: String,
    val title: String?,
    val targetId: Long,
    val targetType: Int,
    val url: String?,
)

data class RecommendPlaylistCard(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
)

data class RecommendMvCard(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val artist: String?,
    val playCount: Long,
)

data class UserProfileBrief(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val signature: String?,
    val backgroundUrl: String? = null,
    val follows: Long? = null,
    val followeds: Long? = null,
)

data class CollectedAlbum(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val artist: String?,
)

data class ChartSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val updateFrequency: String?,
    val playCount: Long,
)

fun isSelfHeartPlaylist(
    selfUid: Long,
    creatorId: Long,
    subscribed: Boolean,
    specialType: Int,
    name: String,
): Boolean {
    val owned = selfUid > 0L && creatorId == selfUid
    if (subscribed && !owned) return false
    if (owned) {
        return specialType == 5 || isLikedMusicPlaylistName(name)
    }
    return specialType == 5 && creatorId <= 0L
}

fun isLikedMusicPlaylistName(name: String): Boolean {
    val n = name.trim()
    return n == "我喜欢的音乐" || n.endsWith("喜欢的音乐")
}
