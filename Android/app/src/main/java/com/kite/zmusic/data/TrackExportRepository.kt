package com.kite.zmusic.data

import android.content.ContentUris
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
    private val userClient: NcmUserClient,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var onLibraryChanged: (() -> Unit)? = null
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun lastOptions(): TrackExportOptions = TrackExportOptions(
        quality = AudioQuality.fromLevel(prefs.getString(KEY_QUALITY, null))
            .takeIf { prefs.contains(KEY_QUALITY) }
            ?: audioQualityStore.current(),
        includeCover = prefs.getBoolean(KEY_COVER, true),
        includeLyrics = prefs.getBoolean(KEY_LYRICS, true),
        includeMetadata = prefs.getBoolean(KEY_META, true),
    )

    fun rememberOptions(options: TrackExportOptions) {
        prefs.edit()
            .putString(KEY_QUALITY, options.quality.level)
            .putBoolean(KEY_COVER, options.includeCover)
            .putBoolean(KEY_LYRICS, options.includeLyrics)
            .putBoolean(KEY_META, options.includeMetadata)
            .apply()
    }

    suspend fun export(
        track: TrackRow,
        cookie: String,
        options: TrackExportOptions = lastOptions(),
    ): String = withContext(Dispatchers.IO) {
        if (track.id <= 0L) throw TrackExportException("无法下载这首歌")
        val folder = folderName(track)
        val relative = "$ROOT/$folder/"
        val audioUrl = resolveAudioUrl(track.id, cookie, options.quality)
            ?: throw TrackExportException("暂时没有可下载的音源")
        val audioBytes = downloadBytes(audioUrl)
            ?: throw TrackExportException("音频下载失败")
        clearFolder(relative)
        val (audioName, audioMime) = audioFileOf(audioBytes)
        writeFile(relative, audioName, audioMime, audioBytes)

        var coverName: String? = null
        if (options.includeCover) {
            track.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
                val bytes = downloadBytes(url)
                if (bytes != null && bytes.isNotEmpty()) {
                    val (name, mime) = coverFileOf(bytes)
                    writeFile(relative, name, mime, bytes)
                    coverName = name
                }
            }
        }

        var lyricName: String? = null
        var transName: String? = null
        if (options.includeLyrics) {
            runCatching {
                val json = userClient.lyric(track.id, cookie)
                NcmPlaybackParse.lrcText(json)?.let { lrc ->
                    writeFile(relative, "lyrics.lrc", MIME_LRC, lrc.toByteArray(Charsets.UTF_8))
                    lyricName = "lyrics.lrc"
                }
                NcmPlaybackParse.translatedLrcText(json)?.let { lrc ->
                    writeFile(relative, "lyrics.trans.lrc", MIME_LRC, lrc.toByteArray(Charsets.UTF_8))
                    transName = "lyrics.trans.lrc"
                }
            }
        }

        if (options.includeMetadata) {
            val meta = JSONObject()
                .put("schema", SCHEMA)
                .put("id", track.id)
                .put("name", track.name)
                .put("artists", track.artists)
                .put("album", track.album ?: JSONObject.NULL)
                .put("durationMs", track.durationMs)
                .put("quality", options.quality.level)
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
        }
        notifyLibraryChanged()
        folder
    }

    /** 扫描 `Download/ZMusic` 下符合 [TRACK_EXPORT_SCHEMA] 的单曲文件夹。 */
    suspend fun scanCachedTracks(): List<TrackRow> = withContext(Dispatchers.IO) {
        queryExportFolders().mapNotNull { folder -> toCachedTrack(folder) }
            .sortedByDescending { it.exportedAt }
            .map { it.track }
    }

    suspend fun deleteCachedFolder(relativeDir: String?): Boolean = withContext(Dispatchers.IO) {
        val folder = relativeDir?.takeIf { it.isNotBlank() } ?: return@withContext false
        clearFolder(folder)
        notifyLibraryChanged()
        true
    }

    private fun notifyLibraryChanged() {
        onLibraryChanged?.invoke()
    }

    private fun toCachedTrack(folder: ExportFolder): CachedScanHit? {
        val jsonUri = folder.file("music.json") ?: return null
        val raw = readText(jsonUri) ?: return null
        val meta = parseTrackExportJson(raw) ?: return null
        if (!exportFolderQualified(meta, folder.names)) return null
        val audioUri = folder.file(meta.audioFile.orEmpty()) ?: return null
        val coverUri = meta.coverFile?.let { folder.file(it) }
        return CachedScanHit(
            exportedAt = meta.exportedAt,
            track = TrackRow(
                id = meta.id,
                name = meta.name,
                artists = meta.artists,
                album = meta.album,
                durationMs = meta.durationMs,
                coverUrl = coverUri?.toString(),
                localAudioUri = audioUri.toString(),
                localFolder = folder.key,
                localLyricUri = meta.lyricsFile?.let { folder.file(it)?.toString() },
                localTransLyricUri = meta.lyricsTranslatedFile?.let { folder.file(it)?.toString() },
            ),
        )
    }

    private fun queryExportFolders(): List<ExportFolder> {
        val resolver = appContext.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.RELATIVE_PATH,
        )
        val grouped = linkedMapOf<String, MutableMap<String, Uri>>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf("$ROOT/%"),
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val rel = cursor.getString(pathCol) ?: continue
                val key = exportSongFolderKey(rel) ?: continue
                val uri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idCol),
                )
                grouped.getOrPut(key) { linkedMapOf() }[name] = uri
            }
        }
        return grouped.map { ExportFolder(it.key, it.value) }
    }

    private fun readText(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun resolveAudioUrl(
        trackId: Long,
        cookie: String,
        quality: AudioQuality,
    ): String? {
        return PlayUrlResolver.resolve(
            userClient = userClient,
            trackId = trackId,
            cookie = cookie,
            quality = quality,
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
            put(MediaStore.Downloads.RELATIVE_PATH, normalizeRelativeDir(relativeDir))
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return appContext.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw TrackExportException("无法创建下载文件")
    }

    private fun clearFolder(relativeDir: String) {
        val resolver = appContext.contentResolver
        val variants = relativePathVariants(relativeDir)
        val sel = variants.joinToString(" OR ") { "${MediaStore.Downloads.RELATIVE_PATH}=?" }
        runCatching {
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, sel, variants)
        }.onFailure {
            Log.w(TAG, "clear folder failed", it)
        }
    }

    private fun findExisting(relativeDir: String, displayName: String): Uri? {
        val resolver = appContext.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
        )
        val variants = relativePathVariants(relativeDir)
        val sel = "(" + variants.joinToString(" OR ") { "${MediaStore.Downloads.RELATIVE_PATH}=?" } +
            ") AND ${MediaStore.Downloads.DISPLAY_NAME}=?"
        val args = variants + displayName
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            sel,
            args,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val gotName = cursor.getString(1)
                if (gotName == displayName) {
                    return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                }
            }
        }
        return null
    }

    private fun relativePathVariants(relativeDir: String): Array<String> {
        val trimmed = relativeDir.trimEnd('/')
        return arrayOf("$trimmed/", trimmed)
    }

    private fun normalizeRelativeDir(relativeDir: String): String =
        relativeDir.trimEnd('/') + "/"

    private data class ExportFolder(
        val key: String,
        val files: Map<String, Uri>,
    ) {
        val names: Set<String> get() = files.keys
        fun file(name: String): Uri? {
            if (name.isBlank()) return null
            return files.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        }
    }

    private data class CachedScanHit(val exportedAt: Long, val track: TrackRow)

    companion object {
        private const val TAG = "TrackExport"
        const val SCHEMA = TRACK_EXPORT_SCHEMA
        const val ROOT = TRACK_EXPORT_ROOT
        private const val PREFS = "zmusic_track_export"
        private const val KEY_QUALITY = "quality"
        private const val KEY_COVER = "cover"
        private const val KEY_LYRICS = "lyrics"
        private const val KEY_META = "meta"
        /** 不用 text/plain：MediaStore 会按 MIME 再拼 .txt，变成 lyrics.lrc.txt。 */
        private const val MIME_LRC = "application/octet-stream"

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
            if (bytes.size >= 4) {
                val head4 = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
                if (head4 == "fLaC") return "audio.flac" to "audio/flac"
                if (head4 == "OggS") return "audio.ogg" to "audio/ogg"
            }
            if (bytes.size >= 12) {
                val brand = bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII)
                if (brand == "ftyp") return "audio.m4a" to "audio/mp4"
                val riff = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
                val wave = bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)
                if (riff == "RIFF" && wave == "WAVE") return "audio.wav" to "audio/wav"
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
