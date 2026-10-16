package com.kite.zmusic.plugin

import com.kite.zmusic.data.LyricRepository
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.playback.PlaybackBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

internal object PluginCatalogOps {
    val ALL: List<String> = listOf(
        "song.detail",
        "song.wiki",
        "song.wikiInfo",
        "song.creators",
        "song.ugc",
        "song.like",
        "song.likedIds",
        "song.likedCheck",
        "search.query",
        "search.suggest",
        "search.hot",
        "search.cloud",
        "album.get",
        "album.dynamic",
        "album.subscribe",
        "album.followed",
        "album.play",
        "artist.detail",
        "artist.dynamic",
        "artist.desc",
        "artist.songs",
        "artist.top",
        "artist.albums",
        "artist.similar",
        "artist.follow",
        "artist.followed",
        "artist.hot",
        "artist.mvs",
        "artist.play",
        "playlist.detail",
        "playlist.tracks",
        "playlist.dynamic",
        "playlist.create",
        "playlist.delete",
        "playlist.rename",
        "playlist.add",
        "playlist.remove",
        "playlist.subscribe",
        "playlist.list",
        "playlist.play",
        "comment.list",
        "comment.legacy",
        "comment.floor",
        "comment.post",
        "comment.like",
        "comment.hug",
        "comment.hugs",
        "mv.detail",
        "mv.related",
        "mv.byArtist",
        "mv.first",
        "mv.exclusive",
        "mv.all",
        "user.account",
        "user.detail",
        "user.level",
        "user.follows",
        "user.followers",
        "user.follow",
        "user.record",
        "user.subcount",
        "user.medal",
        "user.vip",
        "recommend.songs",
        "recommend.playlists",
        "recommend.resource",
        "recommend.newSongs",
        "recommend.mv",
        "home.banner",
        "home.charts",
        "home.playlists",
        "cloud.list",
        "cloud.detail",
        "play.songs",
        "lyric.load",
        "fm.start",
    )

    val SET: Set<String> = ALL.toSet()

    init {
        check(ALL.size == SET.size) { "duplicate catalog op" }
        check(ALL.size >= 76) { "catalog surface too small: ${ALL.size}" }
    }
}

interface PluginCatalog {
    fun enqueue(
        op: String,
        args: Map<String, Any?>,
        deliver: (Map<String, Any?>) -> Unit,
    ): Boolean

    companion object {
        val Noop: PluginCatalog = object : PluginCatalog {
            override fun enqueue(
                op: String,
                args: Map<String, Any?>,
                deliver: (Map<String, Any?>) -> Unit,
            ): Boolean = false
        }
    }
}

internal class PluginAndroidCatalog(
    private val user: NcmUserClient,
    private val auth: NcmAuthClient,
    private val session: SessionRepository,
    private val playback: PlaybackBridge,
) : PluginCatalog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handlers: Map<String, suspend (PluginCallArgs) -> Map<String, Any?>>

    init {
        val map = HashMap<String, suspend (PluginCallArgs) -> Map<String, Any?>>()
        map.reg("song.detail") { a -> idJson(a) { user.songDetail(listOf(it), cookie()) } }
        map.reg("song.wiki") { a -> idJson(a) { user.songWikiSummary(it, cookie()) } }
        map.reg("song.wikiInfo") { a -> idJson(a) { user.songWikiInfo(it, cookie()) } }
        map.reg("song.creators") { a -> idJson(a) { user.songCreators(it, cookie()) } }
        map.reg("song.ugc") { a ->
            needLogin() ?: idJson(a) { user.ugcSongGet(it, cookie()) }
        }
        map.reg("song.like") { a ->
            needLogin() ?: run {
                val id = a.id() ?: return@run fail("bad_args")
                val liked = a.bool("liked") ?: return@run fail("bad_args")
                json { user.likeSong(id, liked, cookie()) }
            }
        }
        map.reg("song.likedIds") { a ->
            needLogin() ?: run {
                val uid = a.id("uid") ?: return@run fail("bad_args")
                json { user.likeList(uid, cookie()) }
            }
        }
        map.reg("song.likedCheck") { a ->
            needLogin() ?: run {
                val ids = a.longs("ids", 50) ?: return@run fail("bad_args")
                json { user.songLikeCheck(ids, cookie()) }
            }
        }
        map.reg("search.query") { a -> search(a, cloud = false) }
        map.reg("search.cloud") { a -> search(a, cloud = true) }
        map.reg("search.suggest") { a ->
            val q = a.text("keywords", 80) ?: throw CatalogBadArgs()
            json { user.searchSuggest(q, cookie(), mobile = false) }
        }
        map.reg("search.hot") { json { user.searchHotDetail(cookie()) } }
        map.reg("album.get") { a -> idJson(a) { user.album(it, cookie()) } }
        map.reg("album.dynamic") { a -> idJson(a) { user.albumDetailDynamic(it, cookie()) } }
        map.reg("album.subscribe") { a ->
            needLogin() ?: run {
                val id = a.id() ?: return@run fail("bad_args")
                val on = a.bool("subscribe") ?: return@run fail("bad_args")
                json { user.albumSub(id, on, cookie()) }
            }
        }
        map.reg("album.followed") { a ->
            needLogin() ?: json {
                user.albumSublist(cookie(), a.limit(20, 50), a.offset())
            }
        }
        map.reg("album.play") { a ->
            val id = a.id() ?: throw CatalogBadArgs()
            playParsed(a) { user.album(id, cookie()) }
        }
        map.reg("artist.detail") { a -> idJson(a) { user.artistDetail(it, cookie()) } }
        map.reg("artist.dynamic") { a -> idJson(a) { user.artistDetailDynamic(it, cookie()) } }
        map.reg("artist.desc") { a -> idJson(a) { user.artistDesc(it, cookie()) } }
        map.reg("artist.songs") { a ->
            val id = a.id() ?: throw CatalogBadArgs()
            val order = a.text("order", 16) ?: "hot"
            if (order != "hot" && order != "time") throw CatalogBadArgs()
            json { user.artistSongs(id, cookie(), order, a.limit(50, 100), a.offset()) }
        }
        map.reg("artist.top") { a -> idJson(a) { user.artistTopSongs(it, cookie()) } }
        map.reg("artist.albums") { a ->
            val id = a.id() ?: throw CatalogBadArgs()
            json { user.artistAlbums(id, cookie(), a.limit(20, 50), a.offset()) }
        }
        map.reg("artist.similar") { a -> idJson(a) { user.simiArtist(it, cookie()) } }
        map.reg("artist.follow") { a ->
            needLogin() ?: run {
                val id = a.id() ?: return@run fail("bad_args")
                val on = a.bool("follow") ?: return@run fail("bad_args")
                json { user.artistSub(id, on, cookie()) }
            }
        }
        map.reg("artist.followed") { a ->
            needLogin() ?: json { user.artistSublist(cookie(), a.limit(30, 50), a.offset()) }
        }
        map.reg("artist.hot") { a -> idJson(a) { user.artistHotSongs(it, cookie()) } }
        map.reg("artist.mvs") { a ->
            val id = a.id() ?: throw CatalogBadArgs()
            json { user.artistMv(id, cookie(), a.limit(30, 50), a.offset()) }
        }
        map.reg("artist.play") { a ->
            val id = a.id() ?: throw CatalogBadArgs()
            playParsed(a) { user.artistTopSongs(id, cookie()) }
        }
        map.reg("playlist.detail") { a ->
            val id = a.id() ?: throw CatalogBadArgs()
            json { user.playlistDetail(id, cookie(), a.limit(100, 500)) }
        }
        map.reg("playlist.tracks") { a ->
            val id = a.id() ?: throw CatalogBadArgs()
            json { user.playlistTrackAll(id, cookie(), a.limit(50, 200), a.offset()) }
        }
        map.reg("playlist.dynamic") { a -> idJson(a) { user.playlistDetailDynamic(it, cookie()) } }
        map.reg("playlist.create") { a ->
            needLogin() ?: run {
                val name = a.text("name", 40) ?: return@run fail("bad_args")
                json { user.playlistCreate(name, cookie()) }
            }
        }
        map.reg("playlist.delete") { a ->
            needLogin() ?: idJson(a) { user.playlistDelete(it, cookie()) }
        }
        map.reg("playlist.rename") { a ->
            needLogin() ?: run {
                val id = a.id() ?: return@run fail("bad_args")
                val name = a.text("name", 40) ?: return@run fail("bad_args")
                json { user.playlistNameUpdate(id, name, cookie()) }
            }
        }
        map.reg("playlist.add") { a -> playlistTracks(a, "add") }
        map.reg("playlist.remove") { a -> playlistTracks(a, "del") }
        map.reg("playlist.subscribe") { a ->
            needLogin() ?: run {
                val id = a.id() ?: return@run fail("bad_args")
                val on = a.bool("subscribe") ?: return@run fail("bad_args")
                json { user.playlistSubscribe(id, on, cookie()) }
            }
        }
        map.reg("playlist.list") { a ->
            val uid = a.id("uid") ?: throw CatalogBadArgs()
            json { user.userPlaylist(uid, cookie(), a.limit(30, 60), a.offset()) }
        }
        map.reg("playlist.play") { a ->
            val id = a.id() ?: throw CatalogBadArgs()
            playParsed(a) { user.playlistTrackAll(id, cookie(), 300, 0) }
        }
        map.reg("comment.list") { a ->
            val id = a.id("songId") ?: a.id() ?: throw CatalogBadArgs()
            val sort = a.int("sort", 2, 1, 3)
            val cursor = a.text("cursor", 40)
            json {
                user.commentNew(id, cookie(), a.int("page", 1, 1, 200), a.limit(20, 50), sort, cursor)
            }
        }
        map.reg("comment.legacy") { a ->
            val id = a.id("songId") ?: a.id() ?: throw CatalogBadArgs()
            json {
                user.commentMusic(
                    id,
                    cookie(),
                    a.limit(20, 50),
                    a.offset(),
                    a.long("before")?.takeIf { it > 0L },
                )
            }
        }
        map.reg("comment.floor") { a ->
            val song = a.id("songId") ?: throw CatalogBadArgs()
            val parent = a.id("parentCommentId") ?: throw CatalogBadArgs()
            json {
                user.commentFloor(song, parent, cookie(), a.limit(20, 50), a.long("time")?.takeIf { it > 0L })
            }
        }
        map.reg("comment.post") { a ->
            needLogin() ?: run {
                val song = a.id("songId") ?: return@run fail("bad_args")
                val content = a.text("content", 300) ?: return@run fail("bad_args")
                json { user.commentPost(song, content, cookie(), a.id("replyCommentId")) }
            }
        }
        map.reg("comment.like") { a ->
            needLogin() ?: run {
                val song = a.id("songId") ?: return@run fail("bad_args")
                val cid = a.id("commentId") ?: return@run fail("bad_args")
                val liked = a.bool("liked") ?: return@run fail("bad_args")
                json { user.commentLike(song, cid, liked, cookie()) }
            }
        }
        map.reg("comment.hug") { a ->
            needLogin() ?: run {
                val uid = a.id("uid") ?: return@run fail("bad_args")
                val cid = a.id("commentId") ?: return@run fail("bad_args")
                val song = a.id("songId") ?: return@run fail("bad_args")
                json { user.hugComment(uid, cid, song, cookie()) }
            }
        }
        map.reg("comment.hugs") { a ->
            val uid = a.id("uid") ?: throw CatalogBadArgs()
            val cid = a.id("commentId") ?: throw CatalogBadArgs()
            val song = a.id("songId") ?: throw CatalogBadArgs()
            json {
                user.commentHugList(
                    uid,
                    cid,
                    song,
                    cookie(),
                    a.int("page", 1, 1, 50),
                    a.limit(20, 50),
                    a.text("cursor", 40),
                    a.text("idCursor", 40),
                )
            }
        }
        map.reg("mv.detail") { a -> idJson(a) { user.mvDetail(it, cookie()) } }
        map.reg("mv.related") { a -> idJson(a) { user.simiMv(it, cookie()) } }
        map.reg("mv.byArtist") { a ->
            val id = a.id() ?: throw CatalogBadArgs()
            json { user.artistMv(id, cookie(), a.limit(30, 50), a.offset()) }
        }
        map.reg("mv.first") { a -> json { user.mvFirst(cookie(), a.limit(12, 30)) } }
        map.reg("mv.exclusive") { a -> json { user.mvExclusive(cookie(), a.limit(30, 50), a.offset()) } }
        map.reg("mv.all") { a -> json { user.mvAll(cookie(), a.limit(30, 50), a.offset()) } }
        map.reg("user.account") { account() }
        map.reg("user.detail") { a -> idJson(a) { user.userDetail(it, cookie()) } }
        map.reg("user.level") { needLogin() ?: json { user.userLevel(cookie()) } }
        map.reg("user.follows") { a ->
            val uid = a.id("uid") ?: throw CatalogBadArgs()
            json { user.userFollows(uid, cookie(), a.limit(30, 50), a.offset()) }
        }
        map.reg("user.followers") { a ->
            val uid = a.id("uid") ?: throw CatalogBadArgs()
            json { user.userFolloweds(uid, cookie(), a.limit(20, 50), a.offset()) }
        }
        map.reg("user.follow") { a ->
            needLogin() ?: run {
                val id = a.id() ?: return@run fail("bad_args")
                val on = a.bool("follow") ?: return@run fail("bad_args")
                json { user.userFollow(id, on, cookie()) }
            }
        }
        map.reg("user.record") { a ->
            needLogin() ?: run {
                val uid = a.id("uid") ?: return@run fail("bad_args")
                val type = a.int("type", 0, 0, 1)
                json { user.userRecord(uid, cookie(), type) }
            }
        }
        map.reg("user.subcount") { needLogin() ?: json { user.userSubcount(cookie()) } }
        map.reg("user.medal") { a -> idJson(a) { user.userMedal(it, cookie()) } }
        map.reg("user.vip") { a ->
            needLogin() ?: json { user.vipInfo(cookie(), a.id("uid")) }
        }
        map.reg("recommend.songs") { needLogin() ?: json { user.recommendSongs(cookie()) } }
        map.reg("recommend.playlists") { a ->
            json { user.personalizedPlaylists(cookie(), a.limit(12, 30)) }
        }
        map.reg("recommend.resource") { needLogin() ?: json { user.recommendResource(cookie()) } }
        map.reg("recommend.newSongs") { a ->
            json { user.personalizedNewSongs(cookie(), a.limit(10, 30)) }
        }
        map.reg("recommend.mv") { json { user.personalizedMv(cookie()) } }
        map.reg("home.banner") { json { user.banner(cookie()) } }
        map.reg("home.charts") { json { user.toplistDetail(cookie()) } }
        map.reg("home.playlists") { a ->
            json { user.topPlaylists(cookie(), a.limit(15, 50), a.offset()) }
        }
        map.reg("cloud.list") { a ->
            needLogin() ?: json { user.userCloud(cookie(), a.limit(50, 100), a.offset()) }
        }
        map.reg("cloud.detail") { a ->
            needLogin() ?: run {
                val ids = a.longs("ids", 50) ?: return@run fail("bad_args")
                json { user.userCloudDetail(ids, cookie()) }
            }
        }
        map.reg("play.songs") { a ->
            val ids = a.longs("ids", 100) ?: throw CatalogBadArgs()
            playParsed(a) { user.songDetail(ids, cookie()) }
        }
        map.reg("lyric.load") { a -> lyric(a) }
        map.reg("fm.start") { a ->
            needLogin() ?: run {
                playback.startPersonalFm()
                ok(mapOf("started" to true))
            }
        }
        val missing = PluginCatalogOps.ALL.filter { it !in map }
        val extra = map.keys.filter { it !in PluginCatalogOps.SET }
        check(missing.isEmpty() && extra.isEmpty()) { "catalog drift missing=$missing extra=$extra" }
        handlers = map
    }

    override fun enqueue(
        op: String,
        args: Map<String, Any?>,
        deliver: (Map<String, Any?>) -> Unit,
    ): Boolean {
        if (op !in handlers) return false
        scope.launch {
            val result = try {
                handlers.getValue(op)(PluginCallArgs(args))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: CatalogBadArgs) {
                fail("bad_args")
            } catch (_: Exception) {
                fail("request_failed")
            }
            deliver(result)
        }
        return true
    }

    private fun MutableMap<String, suspend (PluginCallArgs) -> Map<String, Any?>>.reg(
        name: String,
        block: suspend (PluginCallArgs) -> Map<String, Any?>,
    ) {
        put(name, block)
    }

    private fun cookie(): String = session.session.value?.cookie?.trim().orEmpty()

    private fun needLogin(): Map<String, Any?>? {
        val stored = session.session.value ?: return fail("need_login")
        if (stored.isGuest || stored.cookie.isBlank()) return fail("need_login")
        return null
    }

    private suspend fun idJson(args: PluginCallArgs, block: suspend (Long) -> JSONObject): Map<String, Any?> {
        val id = args.id() ?: return fail("bad_args")
        return json { block(id) }
    }

    private suspend fun json(block: suspend () -> JSONObject): Map<String, Any?> {
        val cleaned = PluginJsonSanitize.clean(block())
        if (PluginJson.stringify(cleaned).length > MAX_JSON) return fail("too_large")
        return ok(cleaned)
    }

    private suspend fun search(args: PluginCallArgs, cloud: Boolean): Map<String, Any?> {
        val q = args.text("keywords", 80) ?: return fail("bad_args")
        val type = args.int("type", 1, 1, 1009)
        val limit = args.limit(20, 50)
        val offset = args.offset()
        return json {
            if (cloud) user.cloudSearch(q, cookie(), type, limit, offset)
            else user.search(q, cookie(), type, limit, offset)
        }
    }

    private suspend fun playlistTracks(args: PluginCallArgs, op: String): Map<String, Any?> {
        needLogin()?.let { return it }
        val id = args.id() ?: return fail("bad_args")
        val ids = args.longs("ids", 50) ?: return fail("bad_args")
        return json { user.playlistTracks(op, id, ids, cookie()) }
    }

    private suspend fun playParsed(
        args: PluginCallArgs,
        load: suspend () -> JSONObject,
    ): Map<String, Any?> {
        val tracks = NcmLibraryParse.tracksFromSongDetail(load())
        if (tracks.isEmpty()) return fail("empty")
        val index = args.int("index", 0, 0, tracks.lastIndex)
        playback.playQueue(tracks, index)
        return ok(mapOf("count" to tracks.size, "index" to index))
    }

    private suspend fun lyric(args: PluginCallArgs): Map<String, Any?> {
        val current = playback.ui.value.currentTrack
        val id = args.id() ?: current?.id ?: return fail("no_song")
        val lyrics: LyricRepository = playback.lyricRepository()
        val track = current?.takeIf { it.id == id }
        val pack = lyrics.loadBestEffort(id, cookie(), track)
        val pos = if (track != null) playback.ui.value.positionMs else 0L
        return ok(PluginLyricMaps.pack(pack.original, pack.translated, pos))
    }

    private suspend fun account(): Map<String, Any?> {
        needLogin()?.let { return it }
        val json = auth.loginStatus(cookie())
        val uid = NcmJson.userIdFromLoginStatus(json)
        val profile = json.optJSONObject("data")?.optJSONObject("profile")
            ?: json.optJSONObject("profile")
        val nickname = profile?.optString("nickname")?.trim()?.takeIf { it.isNotEmpty() }
        return ok(mapOf("uid" to uid, "nickname" to nickname))
    }

    private fun ok(data: Any?): Map<String, Any?> = mapOf("ok" to true, "data" to data)

    private fun fail(error: String): Map<String, Any?> = mapOf("ok" to false, "error" to error)

    private companion object {
        const val MAX_JSON = 900_000
    }
}

private class CatalogBadArgs : RuntimeException()

internal class PluginCallArgs(private val raw: Map<String, Any?>) {
    fun id(key: String = "id"): Long? = long(key)?.takeIf { it > 0L }

    fun long(key: String): Long? {
        val n = number(raw[key]) ?: return null
        if (!n.isFinite() || n % 1.0 != 0.0 || n < 0.0 || n > Long.MAX_VALUE.toDouble()) return null
        return n.toLong()
    }

    fun int(key: String, default: Int, min: Int, max: Int): Int {
        val n = number(raw[key]) ?: return default
        if (!n.isFinite() || n % 1.0 != 0.0) return default
        return n.toLong().toInt().coerceIn(min, max)
    }

    fun limit(default: Int, max: Int): Int = int("limit", default, 1, max)

    fun offset(): Int = int("offset", 0, 0, 5_000)

    fun text(key: String, max: Int): String? {
        val value = raw[key] as? String ?: return null
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > max) return null
        return trimmed
    }

    fun bool(key: String): Boolean? = raw[key] as? Boolean

    fun longs(key: String, max: Int): List<Long>? {
        val list = raw[key] as? List<*> ?: return null
        if (list.isEmpty() || list.size > max) return null
        return list.map { item ->
            val n = number(item) ?: return null
            if (!n.isFinite() || n % 1.0 != 0.0 || n <= 0.0) return null
            n.toLong()
        }
    }

    private fun number(value: Any?): Double? = when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        is Number -> value.toDouble()
        else -> null
    }
}
