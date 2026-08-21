package com.kite.zmusic.data

/**
 * 按偏好音质解析播放 / 导出 URL。
 * 先 `/song/url/v1`，空链则沿档位回退，最后才走旧 `/song/url`。
 */
internal object PlayUrlResolver {

    suspend fun resolve(
        userClient: NcmUserClient,
        trackId: Long,
        cookie: String,
        quality: AudioQuality,
    ): String? {
        val cookieForUrl = if (quality.needsPcOs) cookieWithPlaybackOs(cookie) else cookie
        songUrlV1(userClient, trackId, cookieForUrl, quality)?.let { return it }
        for (fallback in quality.fallbacks()) {
            val fbCookie = if (fallback.needsPcOs) cookieWithPlaybackOs(cookie) else cookie
            songUrlV1(userClient, trackId, fbCookie, fallback)?.let { return it }
        }
        val legacy = userClient.songUrl(listOf(trackId), cookie, br = quality.legacyBr)
        return NcmPlaybackParse.songUrlForId(legacy, trackId)
    }

    /** 只拉指定档，不回退。实时缓存按 (歌, 音质) 存，降档会串档。 */
    suspend fun resolveExact(
        userClient: NcmUserClient,
        trackId: Long,
        cookie: String,
        quality: AudioQuality,
    ): String? {
        val cookieForUrl = if (quality.needsPcOs) cookieWithPlaybackOs(cookie) else cookie
        return songUrlV1(userClient, trackId, cookieForUrl, quality)
    }

    private suspend fun songUrlV1(
        userClient: NcmUserClient,
        trackId: Long,
        cookie: String,
        quality: AudioQuality,
    ): String? {
        val json = userClient.songUrlV1(
            ids = listOf(trackId),
            cookie = cookie,
            level = quality.level,
            encodeType = quality.encodeType,
        )
        return NcmPlaybackParse.songUrlForId(json, trackId)
    }
}
