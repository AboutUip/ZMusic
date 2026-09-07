package com.kite.zmusic.data

import android.net.Uri

/**
 * 分享海报上的 ZMusic 歌曲码：`zmusic://song/{id}`。
 * 首页扫码同时认网易云歌曲页，扫到海报上任一码都能问是否播放。
 */
internal object ZMusicSongLink {
    private const val SCHEME = "zmusic"
    private const val HOST = "song"

    fun format(songId: Long): String = "$SCHEME://$HOST/$songId"

    fun parse(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        parseZmusic(text)?.let { return it }
        return parseNcmSongUrl(text)
    }

    private fun parseZmusic(text: String): Long? {
        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(HOST, ignoreCase = true)) return null
        val fromQuery = uri.getQueryParameter("id")?.toLongOrNull()
        if (fromQuery != null && fromQuery > 0L) return fromQuery
        val last = uri.pathSegments.lastOrNull()?.toLongOrNull()
        return last?.takeIf { it > 0L }
    }

    private fun parseNcmSongUrl(text: String): Long? {
        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase()
        if (
            host != "music.163.com" &&
            host != "y.music.163.com" &&
            host != "www.music.163.com"
        ) {
            return null
        }
        val fromQuery = uri.getQueryParameter("id")?.toLongOrNull()
        if (fromQuery != null && fromQuery > 0L) return fromQuery
        val fromFrag = uri.fragment
            ?.substringAfter("id=", "")
            ?.substringBefore('&')
            ?.toLongOrNull()
        if (fromFrag != null && fromFrag > 0L) return fromFrag
        val segs = uri.pathSegments
        val songIdx = segs.indexOfFirst { it.equals("song", ignoreCase = true) }
        if (songIdx >= 0) {
            segs.getOrNull(songIdx + 1)?.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
        }
        return null
    }
}
