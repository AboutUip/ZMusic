package com.kite.zmusic.data

import org.json.JSONObject

internal object NcmCloudParse {

    fun page(json: JSONObject): CloudDiskPage {
        val songs = songs(json)
        val count = json.optInt("count", songs.size)
        return CloudDiskPage(
            songs = songs,
            count = count.coerceAtLeast(songs.size),
            sizeBytes = longField(json, "size"),
            maxSizeBytes = longField(json, "maxSize"),
            hasMore = when {
                json.has("hasMore") -> json.optBoolean("hasMore")
                else -> songs.isNotEmpty() && songs.size < count
            },
        )
    }

    fun songs(json: JSONObject): List<CloudDiskSong> {
        val arr = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                parseSong(o)?.let { add(it) }
            }
        }
    }

    fun parseSong(o: JSONObject): CloudDiskSong? {
        val simple = o.optJSONObject("simpleSong")
        val id = o.optLong("songId", simple?.optLong("id", 0L) ?: 0L)
        if (id <= 0L) return null
        val fromSimple = simple?.let { NcmLibraryParse.trackFromSongObject(it) }
        val name = o.optString("songName").ifBlank {
            fromSimple?.name ?: o.optString("fileName").ifBlank { "云盘歌曲" }
        }
        val artist = o.optString("artist").ifBlank { fromSimple?.artists.orEmpty() }
        val album = o.optString("album").ifBlank { fromSimple?.album.orEmpty() }.takeIf { it.isNotBlank() }
        val pc = simple?.optJSONObject("pc")
        val cover = fromSimple?.coverUrl
            ?: firstUrl(
                pc?.optString("picUrl"),
                pc?.optString("albumPicUrl"),
                pc?.optString("pic"),
            )
        val dt = fromSimple?.durationMs
            ?: simple?.optLong("dt", 0L)
            ?: 0L
        val t = simple?.optInt("t", 0) ?: 0
        val matched = t == 2
        val track = TrackRow(
            id = id,
            name = name,
            artists = artist.ifBlank { "—" },
            album = album,
            durationMs = dt,
            coverUrl = cover,
            artistRefs = fromSimple?.artistRefs.orEmpty(),
        )
        return CloudDiskSong(
            track = track,
            fileName = o.optString("fileName").ifBlank { pc?.optString("fn").orEmpty() },
            fileSize = o.optLong("fileSize", 0L),
            bitrate = o.optInt("bitrate", 0),
            addTime = o.optLong("addTime", 0L),
            matched = matched,
        )
    }

    fun uploadToken(json: JSONObject): CloudUploadToken? {
        if (NcmJson.apiCode(json) != 200) return null
        val data = json.optJSONObject("data") ?: json
        val url = data.optString("uploadUrl").ifBlank { data.optString("outerUrl") }
        return CloudUploadToken(
            needUpload = data.optBoolean("needUpload", url.isNotBlank()),
            songId = data.optString("songId").ifBlank { data.opt("songId")?.toString().orEmpty() },
            uploadToken = data.optString("uploadToken").ifBlank { data.optString("token") },
            uploadUrl = url,
            resourceId = data.optString("resourceId").ifBlank {
                data.opt("docId")?.toString().orEmpty()
            },
        )
    }

    fun songFileMeta(json: JSONObject, id: Long): CloudSongFileMeta? {
        if (NcmJson.apiCode(json) != 200) return null
        val arr = json.optJSONArray("data")
        val o = if (arr != null) {
            (0 until arr.length()).firstNotNullOfOrNull { i ->
                val item = arr.optJSONObject(i) ?: return@firstNotNullOfOrNull null
                if (item.optLong("id") == id) item else null
            } ?: arr.optJSONObject(0)
        } else {
            json.optJSONObject("data")
        } ?: return null
        val md5 = o.optString("md5").trim()
        val size = o.optLong("size", 0L)
        if (md5.isEmpty() || size <= 0L) return null
        val br = o.optInt("br", 0)
        val type = o.optString("type").ifBlank { o.optString("encodeType") }.ifBlank { "mp3" }
        return CloudSongFileMeta(
            md5 = md5,
            size = size,
            bitrate = br,
            type = type.substringAfterLast('.').lowercase().ifBlank { "mp3" },
        )
    }

    fun downloadUrl(json: JSONObject, id: Long): String? {
        NcmPlaybackParse.songUrlForId(json, id)?.let { return it }
        val data = json.optJSONObject("data") ?: return null
        val u = data.optString("url")
        return u.takeIf { it.isNotBlank() }
    }

    fun lyricText(json: JSONObject): String? {
        NcmPlaybackParse.lrcText(json)?.let { return it }
        val data = json.optJSONObject("data") ?: json
        listOf("lyric", "lrc", "LYRICS", "lyrics").forEach { key ->
            data.optString(key).takeIf { it.isNotBlank() }?.let { return it }
            data.optJSONObject(key)?.optString("lyric")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return json.optString("lyric").takeIf { it.isNotBlank() }
    }

    fun apiMessage(json: JSONObject, fallback: String): String {
        val data = json.optJSONObject("data")
        val msg = listOf(
            json.optString("message"),
            json.optString("msg"),
            data?.optString("message").orEmpty(),
            data?.optString("msg").orEmpty(),
        ).firstOrNull { it.isNotBlank() && it != "null" }
        return msg ?: fallback
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val gb = bytes / 1073741824.0
        if (gb >= 1.0) return String.format("%.1f GB", gb)
        val mb = bytes / 1048576.0
        if (mb >= 1.0) return String.format("%.0f MB", mb)
        return String.format("%.0f KB", bytes / 1024.0)
    }

    private fun longField(json: JSONObject, key: String): Long {
        if (!json.has(key) || json.isNull(key)) return 0L
        val raw = json.opt(key)
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun firstUrl(vararg values: String?): String? {
        for (v in values) {
            val t = v?.trim().orEmpty()
            if (t.startsWith("http")) return t
        }
        return null
    }
}
