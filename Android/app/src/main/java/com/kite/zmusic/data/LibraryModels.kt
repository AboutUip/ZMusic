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
    val backgroundUrl: String? = null,
    val vipKind: VipKind = VipKind.None,
    val vipIconUrl: String? = null,
    val levelProgress: Float? = null,
    val nowPlayCount: Long? = null,
    val nextPlayCount: Long? = null,
)

enum class VipKind {
    None,
    Vip,
    Svip,
}

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
) {
    /**
     * 列表/弹窗用封面：空歌单或网易默认音符图视为无封面，交给黑胶占位。
     */
    fun resolvedCoverUrl(): String? {
        if (trackCount <= 0) return null
        return coverUrl?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            ?.takeUnless { isDefaultPlaylistCover(it) }
    }
}

/** 网易空歌单常见占位图（灰底音符），不要当成真实封面。 */
fun isDefaultPlaylistCover(url: String?): Boolean {
    val u = url?.trim().orEmpty()
    if (u.isEmpty() || u == "null") return true
    val lower = u.lowercase()
    if (lower.contains("default")) return true
    DEFAULT_PLAYLIST_COVER_MARKERS.forEach { mark ->
        if (lower.contains(mark)) return true
    }
    return false
}

private val DEFAULT_PLAYLIST_COVER_MARKERS = listOf(
    "109951162788538524",
    "109951163250239766",
    "109951165611104740",
    "18686200114669622",
    "uetuwe7pvjbpypwoshnkga",
)

/** 歌单详情里用于收藏按钮的元数据（不含曲目）。 */
data class PlaylistSubscribeMeta(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val playCount: Long,
    val creatorId: Long,
    val specialType: Int,
    val subscribed: Boolean,
    val creatorName: String? = null,
    val creatorAvatarUrl: String? = null,
    val subscribedCount: Int = 0,
) {
    fun isOwned(selfUid: Long): Boolean = selfUid > 0L && creatorId == selfUid

    fun isHeart(selfUid: Long): Boolean =
        isOwned(selfUid) && (
            specialType == 5 ||
                name == "我喜欢的音乐" ||
                name.endsWith("喜欢的音乐")
            )

    /** 自己的歌单（含我喜欢的音乐）不能收藏 / 取消收藏。 */
    fun canSubscribe(selfUid: Long): Boolean = !isOwned(selfUid)

    fun toSummary(selfUid: Long): PlaylistSummary = PlaylistSummary(
        id = id,
        name = name,
        coverUrl = coverUrl,
        trackCount = trackCount,
        isHeartPlaylist = isHeart(selfUid),
        isOwned = isOwned(selfUid),
        isSubscribed = subscribed,
        playCount = playCount,
    )
}

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
    /** 专辑封面，用于播放器与列表展示 */
    val coverUrl: String? = null,
    val artistRefs: List<TrackArtist> = emptyList(),
)
