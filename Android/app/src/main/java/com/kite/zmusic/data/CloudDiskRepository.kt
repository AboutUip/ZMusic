package com.kite.zmusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CloudDiskRepository(
    context: Context,
    private val sessionRepository: SessionRepository,
    private val libraryHome: LibraryHomeRepository,
    private val userClient: NcmUserClient,
    httpClient: OkHttpClient,
) {
    private val app = context.applicationContext
    private val uploadClient = NcmUserClient(
        httpClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build(),
    )

    @Volatile
    var lastPage: CloudDiskPage? = null
        private set

    private fun cookieOrNull(): String? {
        val session = sessionRepository.session.value ?: return null
        if (session.isGuest || session.cookie.isBlank()) return null
        return session.cookie
    }

    private fun uid(): Long = libraryHome.snapshot.value.profile?.userId ?: 0L

    private suspend fun requireUid(): Long {
        uid().takeIf { it > 0L }?.let { return it }
        runCatching { libraryHome.refresh(force = false) }
        uid().takeIf { it > 0L }?.let { return it }
        val cookie = cookieOrNull() ?: return 0L
        return com.kite.zmusic.data.ncm.NcmCookie.value(cookie, "uid")
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: 0L
    }

    suspend fun list(offset: Int, limit: Int = PAGE): Result<CloudDiskPage> = withContext(Dispatchers.IO) {
        val cookie = cookieOrNull() ?: return@withContext Result.failure(IllegalStateException("请先登录"))
        runCatching {
            val json = userClient.userCloud(cookie, limit = limit, offset = offset)
            if (NcmJson.apiCode(json) != 200) {
                error(NcmCloudParse.apiMessage(json, "暂时无法打开云盘"))
            }
            val page = NcmCloudParse.page(json)
            lastPage = if (offset <= 0) page else {
                val prev = lastPage
                if (prev == null) page
                else page.copy(songs = prev.songs + page.songs.filter { n -> prev.songs.none { it.track.id == n.track.id } })
            }
            page
        }
    }

    suspend fun delete(ids: List<Long>): String = withContext(Dispatchers.IO) {
        val cookie = cookieOrNull() ?: return@withContext "请先登录"
        if (ids.isEmpty()) return@withContext "请先选择歌曲"
        runCatching {
            val json = userClient.userCloudDel(ids, cookie)
            if (NcmJson.apiCode(json) != 200) {
                NcmCloudParse.apiMessage(json, "删除失败")
            } else {
                lastPage = lastPage?.let { prev ->
                    prev.copy(
                        songs = prev.songs.filter { it.track.id !in ids },
                        count = (prev.count - ids.size).coerceAtLeast(0),
                    )
                }
                if (ids.size == 1) "已从云盘删除" else "已删除 ${ids.size} 首"
            }
        }.getOrElse { "删除失败" }
    }

    suspend fun match(sid: Long, asid: Long): String = withContext(Dispatchers.IO) {
        val cookie = cookieOrNull() ?: return@withContext "请先登录"
        val userId = requireUid()
        if (userId <= 0L) return@withContext "暂时无法匹配"
        runCatching {
            val json = userClient.cloudMatch(userId, sid, asid, cookie)
            if (NcmJson.apiCode(json) != 200) {
                NcmCloudParse.apiMessage(json, if (asid == 0L) "取消匹配失败" else "匹配失败")
            } else if (asid == 0L) {
                "已取消匹配"
            } else {
                "已匹配歌曲信息"
            }
        }.getOrElse { if (asid == 0L) "取消匹配失败" else "匹配失败" }
    }

    suspend fun lyric(sid: Long): String? = withContext(Dispatchers.IO) {
        val cookie = cookieOrNull() ?: return@withContext null
        runCatching {
            val json = userClient.cloudLyric(requireUid(), sid, cookie)
            NcmCloudParse.lyricText(json)
        }.getOrNull()
    }

    suspend fun downloadUrl(id: Long): String? = withContext(Dispatchers.IO) {
        val cookie = cookieOrNull() ?: return@withContext null
        runCatching {
            NcmCloudParse.downloadUrl(userClient.songCloudDownload(id, cookie), id)
        }.getOrNull()
    }

    suspend fun importPublicTrack(track: TrackRow): String = withContext(Dispatchers.IO) {
        val cookie = cookieOrNull() ?: return@withContext "请先登录"
        if (track.id <= 0L) return@withContext "这首歌无法保存到云盘"
        runCatching {
            val urlJson = userClient.songUrlV1(listOf(track.id), cookie)
            val meta = NcmCloudParse.songFileMeta(urlJson, track.id)
                ?: error("暂时拿不到音源信息")
            val br = if (meta.bitrate >= 1000) meta.bitrate / 1000 else meta.bitrate.coerceAtLeast(1)
            val json = userClient.cloudImport(
                cookie = cookie,
                song = track.name,
                fileType = meta.type,
                fileSize = meta.size,
                bitrate = br,
                md5 = meta.md5,
                artist = track.artists.takeIf { it.isNotBlank() && it != "—" },
                album = track.album,
                id = track.id,
            )
            if (NcmJson.apiCode(json) != 200) {
                NcmCloudParse.apiMessage(json, "保存到云盘失败")
            } else {
                "已保存到云盘"
            }
        }.getOrElse { e ->
            e.message?.takeIf { it.isNotBlank() } ?: "保存到云盘失败"
        }
    }

    suspend fun upload(uri: Uri): String = withContext(Dispatchers.IO) {
        val cookie = cookieOrNull() ?: return@withContext "请先登录"
        val tmp = File(app.cacheDir, "cloud-up-${System.nanoTime()}")
        try {
            val name = displayName(uri)
            val mime = app.contentResolver.getType(uri).orEmpty().ifBlank { "audio/mpeg" }
            app.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return@withContext "无法读取文件"
            if (!tmp.isFile || tmp.length() <= 0L) return@withContext "文件是空的"
            val tags = readTags(uri, name)
            val md5 = md5Hex(tmp)
            val tokenJson = runCatching {
                uploadClient.cloudUploadToken(cookie, md5, tmp.length(), name)
            }.getOrNull()
            val token = tokenJson?.let { NcmCloudParse.uploadToken(it) }
            if (token != null) {
                if (token.needUpload && token.uploadUrl.isNotBlank()) {
                    val ok = uploadClient.putUpload(token.uploadUrl, tmp, token.uploadToken, mime)
                    if (!ok) {
                        val fallback = uploadClient.cloudUploadFile(cookie, tmp, name, mime)
                        return@withContext finishUpload(fallback, "上传失败")
                    }
                }
                val done = uploadClient.cloudUploadComplete(
                    cookie = cookie,
                    songId = token.songId,
                    resourceId = token.resourceId,
                    md5 = md5,
                    filename = name,
                    song = tags.title,
                    artist = tags.artist,
                    album = tags.album,
                )
                return@withContext finishUpload(done, "上传失败")
            }
            finishUpload(uploadClient.cloudUploadFile(cookie, tmp, name, mime), "上传失败")
        } finally {
            runCatching { tmp.delete() }
        }
    }

    private fun finishUpload(json: org.json.JSONObject, fallback: String): String {
        return if (NcmJson.apiCode(json) == 200) {
            "已上传到云盘"
        } else {
            NcmCloudParse.apiMessage(json, fallback)
        }
    }

    private fun displayName(uri: Uri): String {
        val cr = app.contentResolver
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "song.mp3"
    }

    private data class Tags(val title: String?, val artist: String?, val album: String?)

    private fun readTags(uri: Uri, filename: String): Tags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(app, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            Tags(
                title = title?.trim()?.ifBlank { null } ?: filename.substringBeforeLast('.'),
                artist = artist?.trim()?.ifBlank { null },
                album = album?.trim()?.ifBlank { null },
            )
        } catch (_: Exception) {
            Tags(filename.substringBeforeLast('.'), null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun md5Hex(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    companion object {
        const val PAGE = 100
    }
}
