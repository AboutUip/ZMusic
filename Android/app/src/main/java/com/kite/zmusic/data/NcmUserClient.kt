package com.kite.zmusic.data

import com.kite.zmusic.config.NcmApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 用户与歌单相关 GET（携带 cookie），与 [NcmAuthClient] 共用同一套 API 基址。
 */
class NcmUserClient(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun userDetail(uid: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/user/detail",
            mapOf("uid" to uid.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun userLevel(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/user/level", mapOf("cookie" to cookie, "timestamp" to ts()))
    }

    suspend fun vipInfo(cookie: String, uid: Long? = null): JSONObject = withContext(Dispatchers.IO) {
        val q = mutableMapOf("cookie" to cookie, "timestamp" to ts())
        if (uid != null && uid > 0L) q["uid"] = uid.toString()
        get("/vip/info/v2", q)
    }

    suspend fun vipInfoLegacy(cookie: String, uid: Long? = null): JSONObject = withContext(Dispatchers.IO) {
        val q = mutableMapOf("cookie" to cookie, "timestamp" to ts())
        if (uid != null && uid > 0L) q["uid"] = uid.toString()
        get("/vip/info", q)
    }

    suspend fun userPlaylist(uid: Long, cookie: String, limit: Int = 60, offset: Int = 0): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/user/playlist",
                mapOf(
                    "uid" to uid.toString(),
                    "limit" to limit.toString(),
                    "offset" to offset.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun userSubcount(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/user/subcount", mapOf("cookie" to cookie, "timestamp" to ts()))
    }

    suspend fun userFollows(
        uid: Long,
        cookie: String,
        limit: Int = 30,
        offset: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/user/follows",
            mapOf(
                "uid" to uid.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun userFolloweds(
        uid: Long,
        cookie: String,
        limit: Int = 20,
        offset: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/user/followeds",
            mapOf(
                "uid" to uid.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    /** `t=1` 关注，其它值取消关注。 */
    suspend fun userFollow(id: Long, follow: Boolean, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/follow",
                mapOf(
                    "id" to id.toString(),
                    "t" to if (follow) "1" else "0",
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    /** `type=1` 近一周，`type=0` 全部。隐私关闭时可能为空。 */
    suspend fun userRecord(uid: Long, cookie: String, type: Int): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/user/record",
                mapOf(
                    "uid" to uid.toString(),
                    "type" to type.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun userMedal(uid: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/user/medal",
            mapOf("uid" to uid.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun likeList(uid: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/likelist", mapOf("uid" to uid.toString(), "cookie" to cookie, "timestamp" to ts()))
    }

    /**
     * 批量检查歌曲是否已喜爱。
     * `ids` 按文档传方括号列表，如 `[2058263032,1497529942]`。
     */
    suspend fun songLikeCheck(ids: List<Long>, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            val idsParam = ids.joinToString(separator = ",", prefix = "[", postfix = "]")
            get(
                "/song/like/check",
                mapOf(
                    "ids" to idsParam,
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    /** 喜欢 / 取消喜欢；[like]=false 为取消。 */
    suspend fun likeSong(id: Long, like: Boolean, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/like",
                mapOf(
                    "id" to id.toString(),
                    "like" to like.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun playlistDetail(playlistId: Long, cookie: String, limit: Int = 500): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/playlist/detail",
                mapOf(
                    "id" to playlistId.toString(),
                    "limit" to limit.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    /** 歌单曲目分页；`offset` 为已加载首数。 */
    suspend fun playlistTrackAll(
        playlistId: Long,
        cookie: String,
        limit: Int,
        offset: Int,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/playlist/track/all",
            mapOf(
                "id" to playlistId.toString(),
                "limit" to limit.coerceAtLeast(1).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun playlistCreate(name: String, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/playlist/create",
                mapOf(
                    "name" to name,
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun playlistDelete(id: Long, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/playlist/delete",
                mapOf(
                    "id" to id.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun playlistNameUpdate(id: Long, name: String, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/playlist/name/update",
                mapOf(
                    "id" to id.toString(),
                    "name" to name,
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    /** [op] 为 `add` 或 `del`。 */
    suspend fun playlistTracks(
        op: String,
        playlistId: Long,
        trackIds: List<Long>,
        cookie: String,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/playlist/tracks",
            mapOf(
                "op" to op,
                "pid" to playlistId.toString(),
                "tracks" to trackIds.joinToString(","),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    /** 评论数 / 是否收藏 / 播放数，比完整详情轻。 */
    suspend fun playlistDetailDynamic(playlistId: Long, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/playlist/detail/dynamic",
                mapOf(
                    "id" to playlistId.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    /** [subscribe]=true 收藏，false 取消收藏。 */
    suspend fun playlistSubscribe(id: Long, subscribe: Boolean, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/playlist/subscribe",
                mapOf(
                    "t" to if (subscribe) "1" else "2",
                    "id" to id.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun songDetail(ids: List<Long>, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        val idStr = ids.joinToString(",")
        get(
            "/song/detail",
            mapOf("ids" to idStr, "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun songUrl(ids: List<Long>, cookie: String, br: Int = 320_000): JSONObject =
        withContext(Dispatchers.IO) {
            val idStr = ids.joinToString(",")
            get(
                "/song/url",
                mapOf(
                    "id" to idStr,
                    "br" to br.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    /** 新版音质接口；旧版 `/song/url` 上游偶发 502 时作回退。 */
    suspend fun songUrlV1(
        ids: List<Long>,
        cookie: String,
        level: String = "exhigh",
        encodeType: String = "mp3",
    ): JSONObject = withContext(Dispatchers.IO) {
        val idStr = ids.joinToString(",")
        get(
            "/song/url/v1",
            mapOf(
                "id" to idStr,
                "level" to level,
                "encodeType" to encodeType,
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun banner(cookie: String, type: Int = 1): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/banner",
            mapOf("type" to type.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun personalizedPlaylists(cookie: String, limit: Int = 12): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/personalized",
                mapOf(
                    "limit" to limit.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun personalizedNewSongs(cookie: String, limit: Int = 10): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/personalized/newsong",
                mapOf(
                    "limit" to limit.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun personalizedMv(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/personalized/mv", mapOf("cookie" to cookie, "timestamp" to ts()))
    }

    suspend fun mvFirst(cookie: String, limit: Int = 12): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/mv/first",
            mapOf(
                "limit" to limit.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    /** 网友精选碟，支持 offset，刷新推荐时用来换一批。 */
    suspend fun topPlaylists(
        cookie: String,
        limit: Int = 15,
        offset: Int = 0,
        order: String = "hot",
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/top/playlist",
            mapOf(
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "order" to order,
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    /** 登录后每日推荐歌单。 */
    suspend fun recommendResource(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/recommend/resource", mapOf("cookie" to cookie, "timestamp" to ts()))
    }

    suspend fun artistHotSongs(id: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/artists",
            mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun artistDetail(id: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/artist/detail",
            mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun artistDetailDynamic(id: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/artist/detail/dynamic",
            mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun artistDesc(id: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/artist/desc",
            mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun artistTopSongs(id: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/artist/top/song",
            mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun artistSongs(
        id: Long,
        cookie: String,
        order: String = "hot",
        limit: Int = 50,
        offset: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/artist/songs",
            mapOf(
                "id" to id.toString(),
                "order" to order,
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun artistAlbums(
        id: Long,
        cookie: String,
        limit: Int = 20,
        offset: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/artist/album",
            mapOf(
                "id" to id.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun simiArtist(id: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/simi/artist",
            mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun artistSublist(
        cookie: String,
        limit: Int = 30,
        offset: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/artist/sublist",
            mapOf(
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    /** `t=1` 收藏，其它值取消收藏。 */
    suspend fun artistSub(id: Long, follow: Boolean, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/artist/sub",
                mapOf(
                    "id" to id.toString(),
                    "t" to if (follow) "1" else "0",
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun recommendSongs(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/recommend/songs", mapOf("cookie" to cookie, "timestamp" to ts()))
    }

    suspend fun personalFm(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/personal_fm", mapOf("cookie" to cookie, "timestamp" to ts()))
    }

    /**
     * 心动模式 / 智能播放。`id` 种子曲，`pid` 所在歌单，`sid` 起播曲。
     */
    suspend fun playmodeIntelligenceList(
        songId: Long,
        playlistId: Long,
        cookie: String,
        startSongId: Long = songId,
        count: Int = 50,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/playmode/intelligence/list",
            mapOf(
                "id" to songId.toString(),
                "pid" to playlistId.toString(),
                "sid" to startSongId.toString(),
                "count" to count.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun searchHotDetail(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/search/hot/detail", mapOf("cookie" to cookie, "timestamp" to ts()))
    }

    /** 搜索联想。`type=mobile` 为移动端关键词建议。 */
    suspend fun searchSuggest(
        keywords: String,
        cookie: String,
        mobile: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val q = mutableMapOf(
            "keywords" to keywords,
            "cookie" to cookie,
            "timestamp" to ts(),
        )
        if (mobile) q["type"] = "mobile"
        get("/search/suggest", q)
    }

    suspend fun cloudSearch(
        keywords: String,
        cookie: String,
        type: Int = 1,
        limit: Int = 30,
        offset: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/cloudsearch",
            mapOf(
                "keywords" to keywords,
                "type" to type.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun search(
        keywords: String,
        cookie: String,
        type: Int = 1,
        limit: Int = 30,
        offset: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/search",
            mapOf(
                "keywords" to keywords,
                "type" to type.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun album(id: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/album",
            mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun albumDetailDynamic(id: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/album/detail/dynamic",
            mapOf("id" to id.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    /** `t=1` 收藏，其它值取消收藏。 */
    suspend fun albumSub(id: Long, subscribe: Boolean, cookie: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/album/sub",
                mapOf(
                    "id" to id.toString(),
                    "t" to if (subscribe) "1" else "0",
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun albumSublist(
        cookie: String,
        limit: Int = 20,
        offset: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/album/sublist",
            mapOf(
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun toplistDetail(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get("/toplist/detail", mapOf("cookie" to cookie, "timestamp" to ts()))
    }

    suspend fun lyric(songId: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/lyric/new",
            mapOf(
                "id" to songId.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    /**
     * 新版评论：`type=0` 歌曲。
     * sortType：1/99 推荐，2 热度，3 时间（对齐 NeteaseCloudMusicApi `comment_new`）。
     * sortType=3 翻页须传上一页 [cursor]（上一条 time）；热度只靠 pageNo。
     */
    suspend fun commentNew(
        songId: Long,
        cookie: String,
        pageNo: Int = 1,
        pageSize: Int = 20,
        sortType: Int = 2,
        cursor: String? = null,
        type: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        val q = linkedMapOf(
            "id" to songId.toString(),
            "type" to type.toString(),
            "pageNo" to pageNo.toString(),
            "pageSize" to pageSize.toString(),
            "sortType" to sortType.toString(),
            "timestamp" to ts(),
        )
        if (cookie.isNotBlank()) q["cookie"] = cookie
        if (!cursor.isNullOrBlank()) q["cursor"] = cursor
        get("/comment/new", q)
    }

    /** 旧版歌曲评论（新版失败时回退）；[before] 为上一页最后一项 time。 */
    suspend fun commentMusic(
        songId: Long,
        cookie: String,
        limit: Int = 20,
        offset: Int = 0,
        before: Long? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val q = linkedMapOf(
            "id" to songId.toString(),
            "limit" to limit.toString(),
            "offset" to offset.toString(),
            "timestamp" to ts(),
        )
        if (cookie.isNotBlank()) q["cookie"] = cookie
        if (before != null && before > 0L) q["before"] = before.toString()
        get("/comment/music", q)
    }

    /** 楼层回复：[parentCommentId] 为一级评论 id。 */
    suspend fun commentFloor(
        songId: Long,
        parentCommentId: Long,
        cookie: String,
        limit: Int = 20,
        time: Long? = null,
        type: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        val q = linkedMapOf(
            "id" to songId.toString(),
            "parentCommentId" to parentCommentId.toString(),
            "type" to type.toString(),
            "limit" to limit.toString(),
            "timestamp" to ts(),
        )
        if (cookie.isNotBlank()) q["cookie"] = cookie
        if (time != null && time > 0L) q["time"] = time.toString()
        get("/comment/floor", q)
    }

    /**
     * 发送 / 回复评论。对齐 `/comment`：
     * [t]=1 发评论，[t]=2 回复（须传 [replyCommentId]）；歌曲 [type]=0。
     */
    suspend fun commentPost(
        songId: Long,
        content: String,
        cookie: String,
        replyCommentId: Long? = null,
        type: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        val isReply = replyCommentId != null && replyCommentId > 0L
        val q = linkedMapOf(
            "t" to if (isReply) "2" else "1",
            "type" to type.toString(),
            "id" to songId.toString(),
            "content" to content,
            "cookie" to cookie,
            "timestamp" to ts(),
        )
        if (isReply) q["commentId"] = replyCommentId.toString()
        get("/comment", q)
    }

    /**
     * 给评论点赞 / 取消。[t]=1 点赞，0 取消；歌曲 [type]=0。
     * 严格对齐网易云 `/comment/like`。
     */
    suspend fun commentLike(
        songId: Long,
        commentId: Long,
        like: Boolean,
        cookie: String,
        type: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/comment/like",
            mapOf(
                "id" to songId.toString(),
                "cid" to commentId.toString(),
                "t" to if (like) "1" else "0",
                "type" to type.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    /**
     * 抱一抱评论。参数严格对齐文档：
     * [uid]=被抱用户（评论作者），[cid]=评论 id，[sid]=资源 id（歌曲 id）。
     */
    suspend fun hugComment(
        targetUid: Long,
        commentId: Long,
        songId: Long,
        cookie: String,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/hug/comment",
            mapOf(
                "uid" to targetUid.toString(),
                "cid" to commentId.toString(),
                "sid" to songId.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    /**
     * 评论抱一抱列表。参数严格对齐文档 uid/cid/sid；分页 cursor、idCursor。
     */
    suspend fun commentHugList(
        targetUid: Long,
        commentId: Long,
        songId: Long,
        cookie: String,
        page: Int = 1,
        pageSize: Int = 50,
        cursor: String? = null,
        idCursor: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val q = linkedMapOf(
            "uid" to targetUid.toString(),
            "cid" to commentId.toString(),
            "sid" to songId.toString(),
            "page" to page.toString(),
            "pageSize" to pageSize.toString(),
            "cookie" to cookie,
            "timestamp" to ts(),
        )
        if (!cursor.isNullOrBlank()) q["cursor"] = cursor
        if (!idCursor.isNullOrBlank()) q["idCursor"] = idCursor
        get("/comment/hug/list", q)
    }

    suspend fun mvDetail(mvid: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/mv/detail",
            mapOf("mvid" to mvid.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    /** [r] 为分辨率，常见 240 / 480 / 720 / 1080。 */
    suspend fun mvUrl(id: Long, cookie: String, r: Int = 720): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/mv/url",
                mapOf(
                    "id" to id.toString(),
                    "r" to r.toString(),
                    "cookie" to cookie,
                    "timestamp" to ts(),
                ),
            )
        }

    suspend fun simiMv(mvid: Long, cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/simi/mv",
            mapOf("mvid" to mvid.toString(), "cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun artistMv(
        artistId: Long,
        cookie: String,
        limit: Int = 30,
        offset: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/artist/mv",
            mapOf(
                "id" to artistId.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun mvAll(
        cookie: String,
        limit: Int = 30,
        offset: Int = 0,
        order: String = "最热",
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/mv/all",
            mapOf(
                "area" to "全部",
                "type" to "全部",
                "order" to order,
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    suspend fun mvExclusive(
        cookie: String,
        limit: Int = 30,
        offset: Int = 0,
    ): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/mv/exclusive/rcmd",
            mapOf(
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "cookie" to cookie,
                "timestamp" to ts(),
            ),
        )
    }

    private fun get(path: String, query: Map<String, String>): JSONObject {
        val url = buildUrl(path, query)
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            return JSONObject(text)
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = NcmApiConfig.baseUrl.trimEnd('/')
        val full = (base + if (path.startsWith("/")) path else "/$path").toHttpUrl()
            .newBuilder()
        query.forEach { (k, v) -> full.addQueryParameter(k, v) }
        return full.build().toString()
    }

    private fun ts() = System.currentTimeMillis().toString()

    companion object {
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
