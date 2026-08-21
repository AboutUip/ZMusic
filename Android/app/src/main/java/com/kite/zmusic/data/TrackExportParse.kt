package com.kite.zmusic.data

import org.json.JSONObject

internal const val TRACK_EXPORT_SCHEMA = "zmusic.track.v1"
internal const val TRACK_EXPORT_ROOT = "Download/ZMusic"

internal data class TrackExportMeta(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String?,
    val durationMs: Long,
    val audioFile: String?,
    val coverFile: String?,
    val lyricsFile: String?,
    val lyricsTranslatedFile: String?,
    val exportedAt: Long = 0L,
)

internal fun parseTrackExportJson(raw: String): TrackExportMeta? {
    val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (o.optString("schema") != TRACK_EXPORT_SCHEMA) return null
    val id = o.optLong("id", 0L)
    if (id <= 0L) return null
    val files = o.optJSONObject("files")
    return TrackExportMeta(
        id = id,
        name = o.optString("name", "—").ifBlank { "—" },
        artists = o.optString("artists", "—").ifBlank { "—" },
        album = jsonStringOrNull(o, "album"),
        durationMs = o.optLong("durationMs", 0L),
        audioFile = jsonStringOrNull(files, "audio"),
        coverFile = jsonStringOrNull(files, "cover"),
        lyricsFile = jsonStringOrNull(files, "lyrics"),
        lyricsTranslatedFile = jsonStringOrNull(files, "lyricsTranslated"),
        exportedAt = o.optLong("exportedAt", 0L),
    )
}

internal fun exportFolderQualified(meta: TrackExportMeta, fileNames: Set<String>): Boolean {
    val audio = meta.audioFile?.trim().orEmpty()
    if (audio.isEmpty()) return false
    return fileNames.any { it.equals(audio, ignoreCase = true) }
}

/** `Download/ZMusic/{单曲文件夹}/`；根目录或更深嵌套不算规范曲目。 */
internal fun exportSongFolderKey(relativePath: String): String? {
    val trimmed = relativePath.replace('\\', '/').trim().trim('/')
    if (!trimmed.startsWith(TRACK_EXPORT_ROOT, ignoreCase = true)) return null
    val rest = trimmed.drop(TRACK_EXPORT_ROOT.length).trim('/')
    if (rest.isEmpty() || rest.contains('/')) return null
    return "$TRACK_EXPORT_ROOT/$rest/"
}

private fun jsonStringOrNull(o: JSONObject?, key: String): String? {
    if (o == null || !o.has(key) || o.isNull(key)) return null
    return o.optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }
}
