package com.kite.zmusic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kite.zmusic.data.CommentHugUser
import com.kite.zmusic.data.CommentPostResult
import com.kite.zmusic.data.CommentSelfProfile
import com.kite.zmusic.data.CommentsRepository
import com.kite.zmusic.data.SongCommentPage

class CommentsViewModel(
    private val comments: CommentsRepository,
) : ViewModel() {
    suspend fun selfProfile(cookie: String): CommentSelfProfile = comments.selfProfile(cookie)

    suspend fun pageNew(
        songId: Long,
        cookie: String,
        pageNo: Int,
        pageSize: Int,
        sortType: Int,
        cursor: String?,
    ): SongCommentPage = comments.pageNew(songId, cookie, pageNo, pageSize, sortType, cursor)

    suspend fun pageLegacy(
        songId: Long,
        cookie: String,
        limit: Int,
        offset: Int,
        before: Long?,
        includeHotFirst: Boolean,
    ): SongCommentPage = comments.pageLegacy(
        songId, cookie, limit, offset, before, includeHotFirst,
    )

    suspend fun post(
        songId: Long,
        content: String,
        cookie: String,
        replyCommentId: Long?,
    ): CommentPostResult = comments.post(songId, content, cookie, replyCommentId)

    suspend fun like(
        songId: Long,
        commentId: Long,
        like: Boolean,
        cookie: String,
    ): Boolean = comments.like(songId, commentId, like, cookie)

    suspend fun hug(
        targetUid: Long,
        commentId: Long,
        songId: Long,
        cookie: String,
    ) = comments.hug(targetUid, commentId, songId, cookie)

    suspend fun hugUsers(
        targetUid: Long,
        commentId: Long,
        songId: Long,
        cookie: String,
    ): List<CommentHugUser> = comments.hugUsers(targetUid, commentId, songId, cookie)

    suspend fun floor(
        songId: Long,
        parentCommentId: Long,
        cookie: String,
        time: Long?,
        limit: Int,
    ): SongCommentPage = comments.floor(songId, parentCommentId, cookie, time, limit)
}

class CommentsViewModelFactory(
    private val comments: CommentsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CommentsViewModel::class.java)) {
            return CommentsViewModel(comments) as T
        }
        error("Unknown ViewModel $modelClass")
    }
}
