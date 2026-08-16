package com.kite.zmusic.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TrackExportException(message: String) : Exception(message)

/**
 * 导出到公共下载目录 `Download/ZMusic/{可读文件夹}/`，
 * 每首歌一个目录：音频、封面、歌词、`music.json`。
 * 文件夹名带网易云 id，同名不同曲不会互相覆盖。
 */
class TrackExportRepository(
    context: Context,
    private val audioQualityStore: AudioQualityStore,
    private val userClient: NcmUserClient = NcmUserClient(),
) {
    private val appContext = context.applicationContext
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun export(track: TrackRow, cookie: String): String = withContext(Dispatchers.IO) {
        if (track.id <= 0L) throw TrackExportException("无法下载这首歌")
        val folder = folderName(track)
        val relative = "$ROOT/$folder/"
        val audioUrl = resolveAudioUrl(track.id, cookie)
            ?: throw TrackExportException("暂时没有可下载的音源")
        val audioBytes = downloadBytes(audioUrl)
            ?: throw TrackExportException("音频下载失败")
        val (audioName, audioMime) = audioFileOf(audioBytes)
        writeFile(relative, audioName, audioMime, audioBytes)

        var coverName: String? = null
        track.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            val bytes = downloadBytes(url)
            if (bytes != null && bytes.isNotEmpty()) {
                val (name, mime) = coverFileOf(bytes)
                writeFile(relative, name, mime, bytes)
                coverName = name
            }
        }

        var lyricName: String? = null
        var transName: String? = null
        runCatching {
            val json = userClient.lyric(track.id, cookie)
            NcmPlaybackParse.lrcText(json)?.let { lrc ->
                writeFile(relative, "lyrics.lrc", "text/plain", lrc.toByteArray(Charsets.UTF_8))
                lyricName = "lyrics.lrc"
            }
            NcmPlaybackParse.translatedLrcText(json)?.let { lrc ->
                writeFile(relative, "lyrics.trans.lrc", "text/plain", lrc.toByteArray(Charsets.UTF_8))
                transName = "lyrics.trans.lrc"
            }
        }

        val meta = JSONObject()
            .put("schema", SCHEMA)
            .put("id", track.id)
            .put("name", track.name)
            .put("artists", track.artists)
            .put("album", track.album ?: JSONObject.NULL)
            .put("durationMs", track.durationMs)
            .put("source", "ncm")
            .put("folder", folder)
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "files",
                JSONObject()
                    .put("audio", audioName)
                    .put("cover", coverName ?: JSONObject.NULL)
                    .put("lyrics", lyricName ?: JSONObject.NULL)
                    .put("lyricsTranslated", transName ?: JSONObject.NULL),
            )
        writeFile(
            relative,
            "music.json",
            "application/json",
            meta.toString(2).toByteArray(Charsets.UTF_8),
        )
        folder
    }

    private suspend fun resolveAudioUrl(trackId: Long, cookie: String): String? {
        return PlayUrlResolver.resolve(
            userClient = userClient,
            trackId = trackId,
            cookie = cookie,
            quality = audioQualityStore.current(),
        )
    }

    private fun downloadBytes(url: String): ByteArray? {
        val req = Request.Builder().url(url).get().build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.onFailure {
            Log.w(TAG, "download failed", it)
        }.getOrNull()
    }

    private fun writeFile(
        relativeDir: String,
        displayName: String,
        mime: String,
        bytes: ByteArray,
    ) {
        val resolver = appContext.contentResolver
        val existing = findExisting(relativeDir, displayName)
        val uri = existing ?: insertPending(relativeDir, displayName, mime)
        resolver.openOutputStream(uri, "w")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw TrackExportException("写入下载目录失败")
        if (existing == null) {
            val done = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
        }
    }

    private fun insertPending(relativeDir: String, displayName: String, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, relativeDir)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return appContext.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw TrackExportException("无法创建下载文件")
    }

    private fun findExisting(relativeDir: String, displayName: String): Uri? {
        val resolver = appContext.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        val sel = "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?"
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            sel,
            arrayOf(relativeDir, displayName),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(0)
            return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
        }
        return null
    }

    companion object {
        private const val TAG = "TrackExport"
        const val SCHEMA = "zmusic.track.v1"
        const val ROOT = "Download/ZMusic"

        fun folderName(track: TrackRow): String {
            val idPart = "[${track.id}]"
            val base = sanitize("${track.name} - ${track.artists}").ifBlank { "track" }
            val budget = 96 - 1 - idPart.length
            val title = if (base.length > budget) base.take(budget).trimEnd() else base
            return "$title $idPart"
        }

        private fun sanitize(raw: String): String {
            val cleaned = buildString(raw.length) {
                raw.forEach { c ->
                    when {
                        c < ' ' -> Unit
                        c in "\\/:*?\"<>|" -> append(' ')
                        else -> append(c)
                    }
                }
            }.trim().replace(Regex("\\s+"), " ").trim('.', ' ')
            return cleaned
        }

        private fun audioFileOf(bytes: ByteArray): Pair<String, String> {
            if (bytes.size >= 12) {
                val brand = bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII)
                if (brand == "ftyp") return "audio.m4a" to "audio/mp4"
            }
            return "audio.mp3" to "audio/mpeg"
        }

        private fun coverFileOf(bytes: ByteArray): Pair<String, String> {
            if (bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte()
            ) {
                return "cover.png" to "image/png"
            }
            return "cover.jpg" to "image/jpeg"
        }
    }
}
