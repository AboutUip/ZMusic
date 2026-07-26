package com.kite.zmusic.data

/**
 * 歌曲评论条目。
 */
data class SongComment(
    val commentId: Long,
    val content: String,
    val timeMs: Long,
    val timeLabel: String,
    val likedCount: Int,
    val liked: Boolean = false,
    val replyCount: Int,
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    /** 被回复的原文摘要（若有） */
    val repliedContent: String?,
    val repliedNickname: String?,
)

data class SongCommentPage(
    val comments: List<SongComment>,
    val total: Long,
    val hasMore: Boolean,
    /** 时间序分页游标；热度序可为空 */
    val cursor: String?,
)

/** 抱抱列表中的用户。 */
data class CommentHugUser(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
)

data class CommentHugPage(
    val users: List<CommentHugUser>,
    val cursor: String?,
    val idCursor: String?,
    val hasMore: Boolean,
)
