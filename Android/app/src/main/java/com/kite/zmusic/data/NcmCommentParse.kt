package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 解析 `/comment/new` 与旧版 `/comment/music` 响应（字段兼容实测差异）。
 */
object NcmCommentParse {

    fun pageFromCommentNew(json: JSONObject): SongCommentPage {
        ensureOk(json)
        val data = json.optJSONObject("data")
            ?: return SongCommentPage(emptyList(), 0, false, null)
        val list = parseCommentArray(data.optJSONArray("comments"))
        val total = data.optLong(
            "totalCount",
            data.optLong("total", list.size.toLong()),
        )
        val hasMore = when {
            data.has("hasMore") -> data.optBoolean("hasMore")
            list.size >= 20 -> true
            else -> list.isNotEmpty() && list.size.toLong() < total
        }
        val cursor = data.optString("cursor").takeIf { it.isNotBlank() && it != "null" }
        return SongCommentPage(list, total, hasMore, cursor)
    }

    fun pageFromCommentMusic(json: JSONObject, includeHotFirst: Boolean): SongCommentPage {
        ensureOk(json)
        val hot = if (includeHotFirst) {
            parseCommentArray(json.optJSONArray("hotComments"))
        } else {
            emptyList()
        }
        val normal = parseCommentArray(json.optJSONArray("comments"))
        // 热评去重后置顶
        val seen = HashSet<Long>()
        val merged = ArrayList<SongComment>(hot.size + normal.size)
        for (c in hot + normal) {
            if (seen.add(c.commentId)) merged.add(c)
        }
        val total = json.optLong("total", merged.size.toLong())
        val more = when {
            json.has("more") -> json.optBoolean("more")
            normal.size >= 20 -> true
            else -> merged.size.toLong() < total
        }
        val lastTime = normal.lastOrNull()?.timeMs?.toString()
            ?: merged.lastOrNull()?.timeMs?.toString()
        return SongCommentPage(merged, total, more, lastTime)
    }

    /** 楼层回复列表。 */
    fun pageFromCommentFloor(json: JSONObject): SongCommentPage {
        ensureOk(json)
        val data = json.optJSONObject("data")
        val arr = data?.optJSONArray("comments") ?: json.optJSONArray("comments")
        val list = parseCommentArray(arr)
        val total = data?.optLong("totalCount", data.optLong("total", list.size.toLong()))
            ?: json.optLong("total", list.size.toLong())
        val hasMore = when {
            data?.has("hasMore") == true -> data.optBoolean("hasMore")
            json.has("more") -> json.optBoolean("more")
            else -> list.size >= 20
        }
        val lastTime = list.lastOrNull()?.timeMs?.toString()
        return SongCommentPage(list, total, hasMore, lastTime)
    }

    /** 对外暴露单条解析，供楼层复用。 */
    fun commentsFromArray(arr: JSONArray?): List<SongComment> = parseCommentArray(arr)

    /**
     * 解析 `/comment` 发评/回复成功后的单条评论。
     * 兼容根级 / data 下的 comment、comments[0] 等字段差异。
     */
    fun commentFromPostResponse(json: JSONObject): SongComment? {
        if (json.optInt("code", -1) != 200) return null
        val data = json.optJSONObject("data")
        val direct = json.optJSONObject("comment")
            ?: data?.optJSONObject("comment")
            ?: data?.optJSONObject("newComment")
        if (direct != null) return parseOne(direct)
        val arr = json.optJSONArray("comments")
            ?: data?.optJSONArray("comments")
        if (arr != null && arr.length() > 0) {
            return parseOne(arr.optJSONObject(0) ?: return null)
        }
        return null
    }

    private fun ensureOk(json: JSONObject) {
        val code = json.optInt("code", -1)
        if (code != 200) {
            throw IllegalStateException("评论接口失败 code=$code")
        }
    }

    private fun parseCommentArray(arr: JSONArray?): List<SongComment> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<SongComment>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseOne(o)?.let { out.add(it) }
        }
        return out
    }

    private fun parseOne(o: JSONObject): SongComment? {
        val id = o.optLong("commentId", o.optLong("commentid", 0L))
        if (id <= 0L) return null
        val user = o.optJSONObject("user") ?: o.optJSONObject("userInfo")
        val nickname = user?.optString("nickname").orEmpty().ifBlank { "用户" }
        val avatar = user?.optString("avatarUrl")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: user?.optString("avatarUrlStr")?.takeIf { it.isNotBlank() }
        val userId = user?.optLong("userId", user.optLong("id", 0L)) ?: 0L
        val content = o.optString("content").orEmpty()
        val timeMs = o.optLong("time", 0L)
        val timeLabel = o.optString("timeStr").takeIf { it.isNotBlank() && it != "null" }
            ?: formatTime(timeMs)
        val likedCount = o.optInt("likedCount", o.optInt("likeCount", 0))
        val liked = o.optBoolean("liked", false)
        val floorObj = o.optJSONObject("showFloorComment")
        val replyCount = maxOf(
            o.optInt("replyCount", 0),
            floorObj?.optInt("replyCount", 0) ?: 0,
        )
        val beReplied = o.optJSONArray("beReplied")
        var repliedContent: String? = null
        var repliedNick: String? = null
        if (beReplied != null && beReplied.length() > 0) {
            val first = beReplied.optJSONObject(0)
            if (first != null) {
                repliedContent = first.optString("content").takeIf { it.isNotBlank() }
                repliedNick = first.optJSONObject("user")?.optString("nickname")
                    ?.takeIf { it.isNotBlank() }
            }
        }
        return SongComment(
            commentId = id,
            content = content,
            timeMs = timeMs,
            timeLabel = timeLabel,
            likedCount = likedCount.coerceAtLeast(0),
            liked = liked,
            replyCount = replyCount.coerceAtLeast(0),
            userId = userId,
            nickname = nickname,
            avatarUrl = avatar,
            repliedContent = repliedContent,
            repliedNickname = repliedNick,
        )
    }

    fun pageFromHugList(json: JSONObject): CommentHugPage {
        ensureOk(json)
        val data = json.optJSONObject("data") ?: json
        val arr = data.optJSONArray("comments")
            ?: data.optJSONArray("list")
            ?: data.optJSONArray("hugList")
            ?: data.optJSONArray("userList")
        val users = ArrayList<CommentHugUser>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val user = o.optJSONObject("user") ?: o
                val uid = user.optLong("userId", user.optLong("id", 0L))
                if (uid <= 0L) continue
                users.add(
                    CommentHugUser(
                        userId = uid,
                        nickname = user.optString("nickname").orEmpty().ifBlank { "用户" },
                        avatarUrl = user.optString("avatarUrl")
                            .takeIf { it.isNotBlank() && it != "null" },
                    ),
                )
            }
        }
        val cursor = data.optString("cursor").takeIf { it.isNotBlank() && it != "null" && it != "-1" }
        val idCursor = data.optString("idCursor").takeIf { it.isNotBlank() && it != "null" && it != "-1" }
        val hasMore = when {
            data.has("hasMore") -> data.optBoolean("hasMore")
            else -> users.size >= 20
        }
        return CommentHugPage(users, cursor, idCursor, hasMore)
    }

    private fun formatTime(timeMs: Long): String {
        if (timeMs <= 0L) return ""
        val now = System.currentTimeMillis()
        val diff = (now - timeMs).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> "刚刚"
            diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
            diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
            diff < 86_400_000L * 7 -> "${diff / 86_400_000L} 天前"
            else -> {
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                fmt.format(Date(timeMs))
            }
        }
    }
}
