package com.kite.zmusic.data

data class CommentSelfProfile(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String?,
)

data class CommentPostResult(
    val ok: Boolean,
    val comment: SongComment?,
    val message: String,
)

/** 歌曲评论 HTTP + 解析。 */
class CommentsRepository(
    private val userClient: NcmUserClient,
    private val authClient: NcmAuthClient,
) {
    suspend fun selfProfile(cookie: String): CommentSelfProfile {
        val status = authClient.loginStatus(cookie)
        val uid = NcmJson.userIdFromLoginStatus(status) ?: 0L
        val nickname = NcmJson.displayLabelFromLogin(status)?.ifBlank { null } ?: "我"
        val profile = status.optJSONObject("profile")
            ?: status.optJSONObject("data")?.optJSONObject("profile")
        val avatar = profile?.optString("avatarUrl")
            ?.takeIf { it.isNotBlank() && it != "null" }
        return CommentSelfProfile(uid, nickname, avatar)
    }

    suspend fun pageNew(
        songId: Long,
        cookie: String,
        pageNo: Int,
        pageSize: Int,
        sortType: Int,
        cursor: String?,
    ): SongCommentPage {
        val json = userClient.commentNew(
            songId = songId,
            cookie = cookie,
            pageNo = pageNo,
            pageSize = pageSize,
            sortType = sortType,
            cursor = cursor,
        )
        return NcmCommentParse.pageFromCommentNew(json)
    }

    suspend fun pageLegacy(
        songId: Long,
        cookie: String,
        limit: Int,
        offset: Int,
        before: Long?,
        includeHotFirst: Boolean,
    ): SongCommentPage {
        val json = userClient.commentMusic(
            songId = songId,
            cookie = cookie,
            limit = limit,
            offset = offset,
            before = before,
        )
        return NcmCommentParse.pageFromCommentMusic(json, includeHotFirst = includeHotFirst)
    }

    suspend fun post(
        songId: Long,
        content: String,
        cookie: String,
        replyCommentId: Long?,
    ): CommentPostResult {
        val json = userClient.commentPost(
            songId = songId,
            content = content,
            cookie = cookie,
            replyCommentId = replyCommentId,
        )
        val failHint = if (replyCommentId != null) "回复失败，请稍后重试" else "评论失败，请稍后重试"
        val code = json.optInt("code", -1)
        if (code != 200) {
            return CommentPostResult(false, null, NcmJson.userFacingMessage(json, failHint))
        }
        return CommentPostResult(true, NcmCommentParse.commentFromPostResponse(json), "")
    }

    suspend fun like(
        songId: Long,
        commentId: Long,
        like: Boolean,
        cookie: String,
    ): Boolean {
        val json = userClient.commentLike(
            songId = songId,
            commentId = commentId,
            like = like,
            cookie = cookie,
        )
        return json.optInt("code", -1) == 200
    }

    suspend fun hug(
        targetUid: Long,
        commentId: Long,
        songId: Long,
        cookie: String,
    ): CatalogApiAck {
        val json = userClient.hugComment(targetUid, commentId, songId, cookie)
        val ok = json.optInt("code", -1) == 200
        return CatalogApiAck(ok, if (ok) "" else NcmJson.userFacingMessage(json, "抱抱失败"))
    }

    suspend fun hugUsers(
        targetUid: Long,
        commentId: Long,
        songId: Long,
        cookie: String,
        page: Int = 1,
        pageSize: Int = 50,
        cursor: String? = null,
        idCursor: String? = null,
    ): List<CommentHugUser> {
        val json = userClient.commentHugList(
            targetUid, commentId, songId, cookie,
            page = page, pageSize = pageSize, cursor = cursor, idCursor = idCursor,
        )
        return NcmCommentParse.pageFromHugList(json).users
    }

    suspend fun floor(
        songId: Long,
        parentCommentId: Long,
        cookie: String,
        time: Long?,
        limit: Int,
    ): SongCommentPage {
        val json = userClient.commentFloor(
            songId = songId,
            parentCommentId = parentCommentId,
            cookie = cookie,
            limit = limit,
            time = time,
        )
        return NcmCommentParse.pageFromCommentFloor(json)
    }
}
