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
    /** 0 保密 / 1 男 / 2 女，与 `/user/update` 约定一致。 */
    val gender: Int = 0,
    val follows: Long? = null,
    val followeds: Long? = null,
    val expertTags: List<String> = emptyList(),
    val medalCount: Int? = null,
    /** `/user/subcount` 收藏歌手数，与 `follows` 合计为资料上的「关注」。 */
    val artistFollows: Long = 0L,
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

data class FollowedUser(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val signature: String?,
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

/**
 * 是否为「当前账号」的红心歌单。
 * 他人「喜欢的音乐」也是 specialType=5，收藏后绝不能算进「我喜欢的音乐」。
 */
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

    fun isHeart(selfUid: Long): Boolean = isSelfHeartPlaylist(
        selfUid = selfUid,
        creatorId = creatorId,
        subscribed = subscribed,
        specialType = specialType,
        name = name,
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

/**
 * 收藏的专辑。封面是作品身份，年份 / 类型 / 公司是专辑才有的出版信息。
 */
data class CollectedAlbum(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val artist: String?,
    val artistId: Long = 0L,
    val artistCoverUrl: String? = null,
    val size: Int = 0,
    val publishTime: Long = 0L,
    val company: String? = null,
    val type: String? = null,
    val alias: String? = null,
) {
    val yearLabel: String?
        get() = formatAlbumYear(publishTime)

    val typeLabel: String?
        get() = formatAlbumType(type)

    fun metaLine(): String = buildList {
        yearLabel?.let { add(it) }
        typeLabel?.let { add(it) }
        if (size > 0) add("${size}首")
    }.joinToString(" · ")
}

fun formatAlbumYear(publishTime: Long): String? {
    if (publishTime <= 0L) return null
    val year = java.util.Calendar.getInstance().apply { timeInMillis = publishTime }
        .get(java.util.Calendar.YEAR)
    return year.takeIf { it in 1900..2100 }?.toString()
}

fun formatAlbumType(raw: String?): String? {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty() || t == "null") return null
    return when (t.lowercase()) {
        "ep" -> "EP"
        "single", "单曲" -> "单曲"
        "album", "专辑" -> "专辑"
        else -> t
    }
}

data class AlbumDynamic(
    val isSub: Boolean,
    val subCount: Int = 0,
    val commentCount: Int = 0,
    val shareCount: Int = 0,
)

data class TrackRow(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String?,
    val durationMs: Long,
    /** 专辑封面，用于播放器与列表展示；缓存曲目可为 content:// */
    val coverUrl: String? = null,
    val artistRefs: List<TrackArtist> = emptyList(),
    /** 本机导出音频（MediaStore / file URI），有则播放走本地，不请求音源 */
    val localAudioUri: String? = null,
    /** `Download/ZMusic/{文件夹}/`，删除缓存时清整夹 */
    val localFolder: String? = null,
    val localLyricUri: String? = null,
    val localTransLyricUri: String? = null,
)
